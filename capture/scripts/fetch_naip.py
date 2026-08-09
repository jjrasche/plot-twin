"""Location -> NAIP orthophoto clip via the USGS NAIP ImageServer (open, GET, no key).

Usage: python capture/scripts/fetch_naip.py 42.6006 -84.6547 [--halfwidth-m 45] [--pixels 900]
TNM Access serves no downloadable NAIP product for this AOI (measured 2026-08-08: total=0),
so the clip comes from imagery.nationalmap.gov's USGSNAIPImagery service as a GeoTIFF export.
"""

import argparse
import json
import urllib.parse

from pyproj import Transformer

from capture_paths import DATA_DIR, http_get, write_json

NAIP_IMAGE_SERVER = "https://imagery.nationalmap.gov/arcgis/rest/services/USGSNAIPImagery/ImageServer"
UTM_ZONE_16N = "EPSG:26916"


def utm_bbox(latitude: float, longitude: float, halfwidth_m: float) -> tuple[float, float, float, float]:
    to_utm = Transformer.from_crs("EPSG:4326", UTM_ZONE_16N, always_xy=True)
    east, north = to_utm.transform(longitude, latitude)
    return (east - halfwidth_m, north - halfwidth_m, east + halfwidth_m, north + halfwidth_m)


def export_image(bbox: tuple[float, float, float, float], pixels: int) -> bytes:
    query = urllib.parse.urlencode(
        {
            "bbox": ",".join(str(edge) for edge in bbox),
            "bboxSR": UTM_ZONE_16N.split(":")[1],
            "imageSR": UTM_ZONE_16N.split(":")[1],
            "size": f"{pixels},{pixels}",
            "format": "tiff",
            "pixelType": "U8",
            "f": "image",
        }
    )
    return http_get(f"{NAIP_IMAGE_SERVER}/exportImage?{query}", timeout=600)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("latitude", type=float)
    parser.add_argument("longitude", type=float)
    parser.add_argument("--halfwidth-m", type=float, default=45.0)
    parser.add_argument("--pixels", type=int, default=900)
    args = parser.parse_args()

    bbox = utm_bbox(args.latitude, args.longitude, args.halfwidth_m)
    naip_dir = DATA_DIR / "naip"
    naip_dir.mkdir(parents=True, exist_ok=True)
    tile_path = naip_dir / "naip_clip.tif"
    tile_path.write_bytes(export_image(bbox, args.pixels))
    print(f"wrote {tile_path} ({tile_path.stat().st_size} bytes)")
    write_json(
        naip_dir / "manifest.json",
        {
            "service_url": NAIP_IMAGE_SERVER,
            "bbox_utm16n": list(bbox),
            "crs": UTM_ZONE_16N,
            "pixels": args.pixels,
            "local_file": tile_path.name,
            "note": "USGS-served NAIP; TNM product download unavailable for this AOI",
        },
    )


if __name__ == "__main__":
    main()
