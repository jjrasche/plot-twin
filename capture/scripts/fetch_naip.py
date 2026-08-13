"""Parcel boundary -> NAIP orthophoto clip via the USGS NAIP ImageServer (open, GET, no key).

Usage: python capture/scripts/fetch_naip.py [--margin-m 2] [--cell-size-m 0.1]
The clip covers the boundary's bounding box plus a margin, because bilinear_sample clips its
indices: a raster that stops short of the grid would smear its last pixel across the uncovered
ground rather than fail. TNM Access serves no downloadable NAIP product for this AOI (measured
2026-08-08: total=0), so the clip comes from imagery.nationalmap.gov as a GeoTIFF export.
"""

import argparse
import urllib.parse

from capture_paths import DATA_DIR, http_get, write_json
from parcel_frame import CELL_SIZE_METERS, UTM_ZONE_16N, cells_spanning, frame_of, read_boundary

NAIP_IMAGE_SERVER = "https://imagery.nationalmap.gov/arcgis/rest/services/USGSNAIPImagery/ImageServer"
MAX_EXPORT_PIXELS = 4096


def export_image(bbox: tuple[float, float, float, float], columns: int, rows: int) -> bytes:
    query = urllib.parse.urlencode(
        {
            "bbox": ",".join(str(edge) for edge in bbox),
            "bboxSR": UTM_ZONE_16N.split(":")[1],
            "imageSR": UTM_ZONE_16N.split(":")[1],
            "size": f"{columns},{rows}",
            "format": "tiff",
            "pixelType": "U8",
            "f": "image",
        }
    )
    return http_get(f"{NAIP_IMAGE_SERVER}/exportImage?{query}", timeout=600)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--margin-m", type=float, default=2.0)
    parser.add_argument("--cell-size-m", type=float, default=CELL_SIZE_METERS)
    args = parser.parse_args()

    frame = frame_of(read_boundary(), args.cell_size_m)
    bbox = frame.bbox_utm(args.margin_m)
    columns = cells_spanning(bbox[2] - bbox[0], args.cell_size_m)
    rows = cells_spanning(bbox[3] - bbox[1], args.cell_size_m)
    if max(columns, rows) > MAX_EXPORT_PIXELS:
        raise SystemExit(f"{columns}x{rows} px exceeds the service's {MAX_EXPORT_PIXELS} px export limit")

    naip_dir = DATA_DIR / "naip"
    naip_dir.mkdir(parents=True, exist_ok=True)
    tile_path = naip_dir / "naip_clip.tif"
    tile_path.write_bytes(export_image(bbox, columns, rows))
    print(f"wrote {tile_path} ({tile_path.stat().st_size} bytes) {columns}x{rows} px over {bbox}")
    write_json(
        naip_dir / "manifest.json",
        {
            "service_url": NAIP_IMAGE_SERVER,
            "bbox_utm16n": list(bbox),
            "crs": UTM_ZONE_16N,
            "pixel_columns": columns,
            "pixel_rows": rows,
            "cell_size_meters": args.cell_size_m,
            "margin_meters": args.margin_m,
            "boundary_derived": True,
            "local_file": tile_path.name,
            "note": "USGS-served NAIP; TNM product download unavailable for this AOI",
        },
    )


if __name__ == "__main__":
    main()
