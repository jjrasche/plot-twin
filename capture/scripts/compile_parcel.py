"""Compile cached 3DEP + NAIP rasters into the 10cm-grid parcel file the :capture module ingests.

Usage: python capture/scripts/compile_parcel.py 42.68317626142 -84.619591093007 [--time-zone America/Detroit]
Offline by design: reads capture/data/boundary/, capture/data/dem/ and capture/data/naip/, never
the network. The extent is the parcel boundary's bounding box, never a fixed square, and the
grid's south-west corner IS the frame origin the boundary row carries.
Row 0 = southernmost row, column 0 = westernmost column (TerrainGrid convention).
"""

import argparse
import base64
import json
import pathlib

import numpy
import rasterio

from capture_paths import DATA_DIR, write_json
from parcel_frame import CELL_SIZE_METERS, UTM_ZONE_16N, ParcelFrame, frame_of, read_boundary

FIXTURE_CELL_SIZE_METERS = 1.0
DEM_EPOCH = "2017-12-01/2018-04-23"
VERTICAL_DATUM = "NAVD88 (GEOID12B)"


def cell_center_axes(origin: float, cells: int, cell_size: float) -> numpy.ndarray:
    return origin + (numpy.arange(cells) + 0.5) * cell_size


def bilinear_sample(raster, band: numpy.ndarray, easts: numpy.ndarray, norths: numpy.ndarray) -> numpy.ndarray:
    transform = raster.transform
    columns = (easts - transform.c) / transform.a - 0.5
    rows = (norths - transform.f) / transform.e - 0.5
    column_floor = numpy.floor(columns).astype(int)
    row_floor = numpy.floor(rows).astype(int)
    column_frac = columns - column_floor
    row_frac = rows - row_floor
    column_floor = numpy.clip(column_floor, 0, band.shape[1] - 2)
    row_floor = numpy.clip(row_floor, 0, band.shape[0] - 2)
    top_left = band[row_floor, column_floor]
    top_right = band[row_floor, column_floor + 1]
    bottom_left = band[row_floor + 1, column_floor]
    bottom_right = band[row_floor + 1, column_floor + 1]
    top = top_left + (top_right - top_left) * column_frac
    bottom = bottom_left + (bottom_right - bottom_left) * column_frac
    return top + (bottom - top) * row_frac


def sample_frame(raster, band: numpy.ndarray, frame: ParcelFrame) -> numpy.ndarray:
    easts = cell_center_axes(frame.origin_east, frame.columns, frame.cell_size)
    norths = cell_center_axes(frame.origin_north, frame.rows, frame.cell_size)
    east_mesh, north_mesh = numpy.meshgrid(easts, norths)
    return bilinear_sample(raster, band, east_mesh, north_mesh)


def refuse_unless_raster_covers(raster, frame: ParcelFrame, name: str) -> None:
    """bilinear_sample clips indices, so a short raster would smear its edge pixel across the
    uncovered ground instead of failing. The clip is a safety net, never a source of pixels."""
    west, south, east, north = frame.bbox_utm()
    bounds = raster.bounds
    if bounds.left > west or bounds.bottom > south or bounds.right < east or bounds.top < north:
        raise SystemExit(
            "%s raster %s does not cover the parcel bbox %s; re-fetch it over the boundary"
            % (name, (bounds.left, bounds.bottom, bounds.right, bounds.top), (west, south, east, north))
        )


def heights_base64(south_up_heights: numpy.ndarray) -> str:
    return base64.b64encode(south_up_heights.astype("<f4").tobytes()).decode()


def albedo_base64(red: numpy.ndarray, green: numpy.ndarray, blue: numpy.ndarray) -> str:
    stacked = numpy.stack([red, green, blue], axis=-1)
    return base64.b64encode(numpy.rint(stacked).clip(0, 255).astype(numpy.uint8).tobytes()).decode()


def compile_grid(dem, naip, dem_band, naip_bands, frame: ParcelFrame, manifest) -> dict:
    surface = sample_frame(dem, dem_band, frame)
    surface_south_up = numpy.flipud(surface)
    rgb_south_up = [numpy.flipud(sample_frame(naip, naip_bands[band], frame)) for band in range(3)]
    return {
        "columns": frame.columns,
        "rows": frame.rows,
        "cell_size_meters": frame.cell_size,
        "frame": {
            "crs": UTM_ZONE_16N,
            "origin_easting_meters": frame.origin_east,
            "origin_northing_meters": frame.origin_north,
        },
        "heights_base64": heights_base64(surface_south_up),
        "albedo_base64": albedo_base64(*rgb_south_up),
        "provenance": {
            "dem_product": manifest["product_title"],
            "dem_url": manifest["download_url"],
            "naip_product": "NAIP via USGS USGSNAIPImagery ImageServer clip",
            "naip_url": "https://imagery.nationalmap.gov/arcgis/rest/services/USGSNAIPImagery/ImageServer",
            "horizontal_crs": UTM_ZONE_16N,
            "vertical_datum": VERTICAL_DATUM,
            "source_epoch": DEM_EPOCH,
            "interpolation": (
                "bilinear from 1m 3DEP DEM to 10cm cells; ~99% of cells are interpolation, "
                "support distance up to ~0.7m (Q-002)"
            ),
            "elevation_min_meters": float(surface.min()),
            "elevation_max_meters": float(surface.max()),
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("latitude", type=float)
    parser.add_argument("longitude", type=float)
    parser.add_argument("--time-zone", default="America/Detroit")
    args = parser.parse_args()

    boundary = read_boundary()
    parcel_frame = frame_of(boundary, CELL_SIZE_METERS)
    fixture_frame = frame_of(boundary, FIXTURE_CELL_SIZE_METERS)

    dem_manifest = json.loads((DATA_DIR / "dem" / "manifest.json").read_text())
    dem = rasterio.open(DATA_DIR / "dem" / dem_manifest["local_file"])
    naip_manifest = json.loads((DATA_DIR / "naip" / "manifest.json").read_text())
    naip = rasterio.open(DATA_DIR / "naip" / naip_manifest["local_file"])
    assert str(dem.crs) == UTM_ZONE_16N and str(naip.crs) == UTM_ZONE_16N
    refuse_unless_raster_covers(dem, parcel_frame, "DEM")
    refuse_unless_raster_covers(naip, parcel_frame, "NAIP")

    dem_band = dem.read(1).astype(numpy.float64)
    naip_bands = [naip.read(band + 1).astype(numpy.float64) for band in range(3)]
    site = {
        "latitude_degrees": args.latitude,
        "longitude_degrees": args.longitude,
        "time_zone_id": args.time_zone,
    }

    parcel = {"site": site} | compile_grid(dem, naip, dem_band, naip_bands, parcel_frame, dem_manifest)
    write_json(DATA_DIR / "compiled" / "parcel.json", parcel)

    fixture = {"site": site} | compile_grid(dem, naip, dem_band, naip_bands, fixture_frame, dem_manifest)
    fixture_path = pathlib.Path(__file__).resolve().parent.parent / "src" / "testFixtures" / "resources" / "real_parcel_1m.json"
    write_json(fixture_path, fixture)

    provenance = parcel["provenance"]
    print(
        "extent receipt: %d columns x %d rows at %.2f m = %d cells, origin %.3f E %.3f N %s"
        % (
            parcel_frame.columns,
            parcel_frame.rows,
            parcel_frame.cell_size,
            parcel_frame.cell_count,
            parcel_frame.origin_east,
            parcel_frame.origin_north,
            UTM_ZONE_16N,
        )
    )
    print("fixture receipt: %d columns x %d rows at %.2f m" % (fixture_frame.columns, fixture_frame.rows, fixture_frame.cell_size))
    print(
        "elevation receipt: min %.3f m, max %.3f m, range %.3f m (%s, %s)"
        % (
            provenance["elevation_min_meters"],
            provenance["elevation_max_meters"],
            provenance["elevation_max_meters"] - provenance["elevation_min_meters"],
            VERTICAL_DATUM,
            DEM_EPOCH,
        )
    )


if __name__ == "__main__":
    main()
