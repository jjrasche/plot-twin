"""Eaton County parcel polygon -> boundary.json: the true property line, provenanced.

Usage: python capture/scripts/fetch_parcel_boundary.py 04003630009000 42.68317626142 -84.619591093007
GET-only against the county's open ArcGIS layer; no auth, no writes upstream.

Interim source: this does NOT come through the shared parcel-layer seam, so the file says so
in `contract`. The ring is kept three ways - the county's WGS84 vertices unmodified, absolute
EPSG:26916 metres, and plot-local metres against the same origin the 90m grid uses
(site UTM minus half the grid width), which is the frame every log row already speaks.
"""

import argparse
import datetime
import hashlib
import json

from pyproj import Transformer

from capture_paths import DATA_DIR, http_get, write_json
from compile_parcel import CELL_SIZE_METERS, CELLS_PER_SIDE, UTM_ZONE_16N, site_utm

PARCEL_LAYER_URL = (
    "https://services2.arcgis.com/c9l1e4fKpsCnqD7H/arcgis/rest/services/Parcels_AGO/FeatureServer/0"
)
HALF_WIDTH_METERS = CELLS_PER_SIDE * CELL_SIZE_METERS / 2.0
INTERIM_CONTRACT = "interim-county-service"
WGS84 = "EPSG:4326"


def feature_query_url(parcel_id: str) -> str:
    return (
        f"{PARCEL_LAYER_URL}/query?where=PARCELID='{parcel_id}'"
        "&outFields=*&returnGeometry=true&outSR=4326&f=json"
    )


def layer_currency(metadata: dict) -> tuple[str | None, str | None]:
    """The layer's own data-currency stamp, or the reason there is none."""
    field_names = {field["name"] for field in metadata.get("fields", [])}
    per_feature = sorted(field_names & {"LASTUPDATE", "EDIT_DATE", "TAXYEAR", "ASSESSYEAR"})
    if per_feature:
        return None, f"per-feature currency fields {per_feature} exist but are unread by this script"
    edited_at_millis = metadata.get("editingInfo", {}).get("dataLastEditDate")
    if edited_at_millis is None:
        return None, "service exposes neither a per-feature currency field nor editingInfo.dataLastEditDate"
    edited_at = datetime.datetime.fromtimestamp(edited_at_millis / 1000.0, datetime.timezone.utc)
    return edited_at.isoformat().replace("+00:00", "Z"), None


def sole_feature(response: dict, parcel_id: str) -> dict:
    features = response.get("features", [])
    if len(features) != 1:
        raise SystemExit(f"expected exactly one feature for PARCELID {parcel_id}, got {len(features)}")
    rings = features[0]["geometry"]["rings"]
    if len(rings) != 1:
        raise SystemExit(f"expected a single-ring parcel, got {len(rings)} rings")
    return features[0]


def closed_ring_of(feature: dict) -> list[list[float]]:
    ring = [[float(lon), float(lat)] for lon, lat in feature["geometry"]["rings"][0]]
    if ring[0] != ring[-1]:
        raise SystemExit("county ring is not closed; refusing to close it here")
    return ring


def utm_ring_of(closed_ring: list[list[float]]) -> list[list[float]]:
    to_utm = Transformer.from_crs(WGS84, UTM_ZONE_16N, always_xy=True)
    return [list(to_utm.transform(lon, lat)) for lon, lat in closed_ring]


def local_ring_of(utm_ring: list[list[float]], origin_east: float, origin_north: float) -> list[list[float]]:
    return [[east - origin_east, north - origin_north] for east, north in utm_ring]


def ring_area_square_meters(closed_ring: list[list[float]]) -> float:
    """Shoelace in the plot-local frame; absolute UTM coordinates cancel away the millimetres."""
    vertices = closed_ring[:-1]
    twice_area = sum(
        vertices[index][0] * vertices[(index + 1) % len(vertices)][1]
        - vertices[(index + 1) % len(vertices)][0] * vertices[index][1]
        for index in range(len(vertices))
    )
    return abs(twice_area) / 2.0


def boundary_of(parcel_id: str, latitude: float, longitude: float) -> dict:
    metadata = json.loads(http_get(f"{PARCEL_LAYER_URL}?f=json"))
    observed_at, observed_at_absent_reason = layer_currency(metadata)
    raw = http_get(feature_query_url(parcel_id))
    pulled_at_utc = datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z")
    feature = sole_feature(json.loads(raw), parcel_id)
    attributes = feature["attributes"]

    closed_ring = closed_ring_of(feature)
    utm_ring = utm_ring_of(closed_ring)
    site_east, site_north = site_utm(latitude, longitude)
    origin_east = site_east - HALF_WIDTH_METERS
    origin_north = site_north - HALF_WIDTH_METERS

    local_ring = local_ring_of(utm_ring, origin_east, origin_north)
    return {
        "parcel_id": attributes["PARCELID"],
        "low_parcel_id": attributes["LPARCEL"],
        "site_address": attributes["SITEADDRESS"],
        "owner_name": attributes["OWNERNME1"],
        "acres_county_stated": attributes["Acreage"],
        "acres_state_equalized": attributes["STATEDAREA"],
        "area_square_meters_derived": ring_area_square_meters(local_ring),
        "ring_wgs84_closed": closed_ring,
        "ring_utm_closed": utm_ring,
        "ring_local_closed": local_ring,
        "plot_local_origin": {
            "crs": UTM_ZONE_16N,
            "easting_meters": origin_east,
            "northing_meters": origin_north,
            "derived_from": (
                f"site {latitude}, {longitude} reprojected to {UTM_ZONE_16N}, "
                f"minus {HALF_WIDTH_METERS} m on each axis (compile_parcel.py grid half-width)"
            ),
        },
        "provenance": {
            "source": f"{PARCEL_LAYER_URL} PARCELID={parcel_id}",
            "pulled_at_utc": pulled_at_utc,
            "observed_at": observed_at,
            "observed_at_absent_reason": observed_at_absent_reason,
            "observed_at_scope": "layer-level editingInfo.dataLastEditDate; no per-feature currency field exists",
            "sha256": hashlib.sha256(raw).hexdigest(),
            "response_bytes": len(raw),
            "contract": INTERIM_CONTRACT,
            "horizontal_crs": UTM_ZONE_16N,
            "source_crs": WGS84,
        },
    }


def receipt_lines(boundary: dict) -> list[str]:
    provenance = boundary["provenance"]
    derived_acres = boundary["area_square_meters_derived"] / 4046.8564224
    return [
        f"parcel {boundary['parcel_id']} ({boundary['low_parcel_id']}) - {boundary['site_address']}",
        f"owner of record: {boundary['owner_name']}",
        f"vertices: {len(boundary['ring_wgs84_closed'])} closed ({len(boundary['ring_wgs84_closed']) - 1} distinct)",
        "area: %.1f m2 derived vs %.8f ac county-stated (%.1f m2), %+.3f%%"
        % (
            boundary["area_square_meters_derived"],
            boundary["acres_county_stated"],
            boundary["acres_county_stated"] * 4046.8564224,
            100.0 * (derived_acres / boundary["acres_county_stated"] - 1.0),
        ),
        f"contract: {provenance['contract']} (NOT the shared parcel-layer seam)",
        f"pulled_at_utc: {provenance['pulled_at_utc']}",
        f"observed_at: {provenance['observed_at']} ({provenance['observed_at_scope']})",
        f"sha256: {provenance['sha256']} over {provenance['response_bytes']} response bytes",
        "plot-local origin: %.3f E, %.3f N %s"
        % (
            boundary["plot_local_origin"]["easting_meters"],
            boundary["plot_local_origin"]["northing_meters"],
            boundary["plot_local_origin"]["crs"],
        ),
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("parcel_id")
    parser.add_argument("latitude", type=float)
    parser.add_argument("longitude", type=float)
    args = parser.parse_args()

    boundary = boundary_of(args.parcel_id, args.latitude, args.longitude)
    write_json(DATA_DIR / "boundary" / "boundary.json", boundary)
    lines = receipt_lines(boundary)
    (DATA_DIR / "boundary" / "receipt.txt").write_text("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
