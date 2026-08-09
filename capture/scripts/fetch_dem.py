"""Location -> best available 3DEP 1m DEM tile via the USGS TNM Access API.

Usage: python capture/scripts/fetch_dem.py 42.6006 -84.6547
Downloads the covering tile GeoTIFF into capture/data/dem/ and writes a manifest with
the product title, URL, publication date and byte size (provenance is fetched, not remembered).
"""

import argparse
import pathlib
import urllib.parse

from capture_paths import DATA_DIR, http_get, http_get_json, write_json

TNM_URL = "https://tnmaccess.nationalmap.gov/api/v1/products"
DATASET = "Digital Elevation Model (DEM) 1 meter"
POINT_HALFWIDTH_DEGREES = 0.001


def query_products(latitude: float, longitude: float) -> list[dict]:
    bbox = (
        f"{longitude - POINT_HALFWIDTH_DEGREES},{latitude - POINT_HALFWIDTH_DEGREES},"
        f"{longitude + POINT_HALFWIDTH_DEGREES},{latitude + POINT_HALFWIDTH_DEGREES}"
    )
    query = urllib.parse.urlencode({"datasets": DATASET, "bbox": bbox, "outputFormat": "JSON"})
    return http_get_json(f"{TNM_URL}?{query}")["items"]


def choose_covering_tile(products: list[dict]) -> dict:
    if not products:
        raise SystemExit("no 1m DEM product covers this point")
    return sorted(products, key=lambda item: item.get("publicationDate", ""), reverse=True)[0]


def download_tile(product: dict, dem_dir: pathlib.Path) -> pathlib.Path:
    url = product["downloadURL"]
    target = dem_dir / url.rsplit("/", 1)[1]
    if target.exists() and target.stat().st_size == product.get("sizeInBytes"):
        print(f"cached: {target}")
        return target
    dem_dir.mkdir(parents=True, exist_ok=True)
    print(f"downloading {product.get('sizeInBytes', '?')} bytes: {url}")
    target.write_bytes(http_get(url, timeout=1800))
    return target


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("latitude", type=float)
    parser.add_argument("longitude", type=float)
    args = parser.parse_args()
    product = choose_covering_tile(query_products(args.latitude, args.longitude))
    tile_path = download_tile(product, DATA_DIR / "dem")
    write_json(
        DATA_DIR / "dem" / "manifest.json",
        {
            "product_title": product.get("title"),
            "download_url": product["downloadURL"],
            "publication_date": product.get("publicationDate"),
            "size_in_bytes": product.get("sizeInBytes"),
            "local_file": tile_path.name,
            "dataset": DATASET,
            "source_api": TNM_URL,
        },
    )


if __name__ == "__main__":
    main()
