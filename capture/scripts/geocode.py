"""Address -> parcel location via the US Census geocoder (free, GET, no key).

Usage: python capture/scripts/geocode.py "1234 Example Rd, Town, MI 48800" [--out FILE]
"""

import argparse
import urllib.parse

from capture_paths import DATA_DIR, http_get_json, write_json

CENSUS_URL = "https://geocoding.geo.census.gov/geocoder/locations/onelineaddress"
BENCHMARK = "Public_AR_Current"


def geocode(address: str) -> dict:
    query = urllib.parse.urlencode({"address": address, "benchmark": BENCHMARK, "format": "json"})
    result = http_get_json(f"{CENSUS_URL}?{query}")
    matches = result["result"]["addressMatches"]
    if not matches:
        raise SystemExit(f"census geocoder found no match for: {address}")
    best = matches[0]
    return {
        "input_address": address,
        "matched_address": best["matchedAddress"],
        "latitude_degrees": best["coordinates"]["y"],
        "longitude_degrees": best["coordinates"]["x"],
        "source": f"US Census Geocoder, benchmark {BENCHMARK}",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("address")
    parser.add_argument("--out", default=str(DATA_DIR / "geocode.json"))
    args = parser.parse_args()
    located = geocode(args.address)
    print(f"matched: {located['matched_address']}")
    print(f"location: {located['latitude_degrees']}, {located['longitude_degrees']}")
    write_json(__import__("pathlib").Path(args.out), located)


if __name__ == "__main__":
    main()
