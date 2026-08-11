"""Location -> QL2 lidar point cloud tile(s) via the USGS TNM Access API.

Usage: python capture/scripts/fetch_lidar.py 42.68317626142 -84.619591093007
Downloads every LAZ tile whose footprint covers the point (plus the 90m square around it)
into capture/data/lidar/ and writes a manifest with title, URL, date and byte size.
"""

import argparse
import pathlib
import re
import urllib.parse

from capture_paths import DATA_DIR, http_get, http_get_json, write_json

TNM_URL = "https://tnmaccess.nationalmap.gov/api/v1/products"
DATASET = "Lidar Point Cloud (LPC)"
PARCEL_HALFWIDTH_DEGREES = 0.0006  # ~50m: covers the 90m square around the site point


def query_products(latitude: float, longitude: float) -> list[dict]:
    bbox = (
        f"{longitude - PARCEL_HALFWIDTH_DEGREES},{latitude - PARCEL_HALFWIDTH_DEGREES},"
        f"{longitude + PARCEL_HALFWIDTH_DEGREES},{latitude + PARCEL_HALFWIDTH_DEGREES}"
    )
    query = urllib.parse.urlencode({"datasets": DATASET, "bbox": bbox, "outputFormat": "JSON"})
    return http_get_json(f"{TNM_URL}?{query}")["items"]


def acquisition_year(product: dict) -> int:
    years = [int(match) for match in re.findall(r"(?<!\d)((?:19|20)\d{2})(?!\d)", product.get("title", ""))]
    return max(years, default=0)


def newest_project_tiles(products: list[dict]) -> list[dict]:
    """Newest ACQUISITION wins: TNM publicationDate is unreliable (the QL2 2016 Eaton
    project reports 1899-12-28), so the year in the project title decides."""
    if not products:
        raise SystemExit("no lidar point cloud product covers this point")
    laz_products = [item for item in products if item["downloadURL"].lower().endswith((".laz", ".las"))]
    if not laz_products:
        raise SystemExit("products found but none offers a LAZ/LAS download")
    newest_year = max(acquisition_year(item) for item in laz_products)
    return [item for item in laz_products if acquisition_year(item) == newest_year]


def download_tile(product: dict, lidar_dir: pathlib.Path) -> pathlib.Path:
    url = product["downloadURL"]
    target = lidar_dir / url.rsplit("/", 1)[1]
    if target.exists() and target.stat().st_size == product.get("sizeInBytes"):
        print(f"cached: {target}")
        return target
    lidar_dir.mkdir(parents=True, exist_ok=True)
    print(f"downloading {product.get('sizeInBytes', '?')} bytes: {url}")
    target.write_bytes(http_get(url, timeout=1800))
    return target


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("latitude", type=float)
    parser.add_argument("longitude", type=float)
    args = parser.parse_args()
    tiles = newest_project_tiles(query_products(args.latitude, args.longitude))
    downloaded = [download_tile(product, DATA_DIR / "lidar") for product in tiles]
    write_json(
        DATA_DIR / "lidar" / "manifest.json",
        {
            "dataset": DATASET,
            "source_api": TNM_URL,
            "tiles": [
                {
                    "product_title": product.get("title"),
                    "download_url": product["downloadURL"],
                    "publication_date": product.get("publicationDate"),
                    "size_in_bytes": product.get("sizeInBytes"),
                    "local_file": path.name,
                }
                for product, path in zip(tiles, downloaded)
            ],
        },
    )


if __name__ == "__main__":
    main()
