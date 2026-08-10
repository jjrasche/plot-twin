"""Rerun the three capture stages on the owner's geocoded point.

Usage: python capture/scripts/recapture.py
Reads capture/data/geocode.json (produced by geocode.py with the owner address) and runs
fetch_dem, fetch_naip and compile_parcel on that point, so one command regenerates the
parcel after the address arms the pipeline.
"""

import json
import pathlib
import subprocess
import sys

from capture_paths import DATA_DIR

GEOCODE_PATH = DATA_DIR / "geocode.json"


def owner_point() -> tuple[float, float]:
    if not GEOCODE_PATH.exists():
        raise SystemExit('no capture/data/geocode.json - run: python capture/scripts/geocode.py "<address>"')
    located = json.loads(GEOCODE_PATH.read_text())
    return located["latitude_degrees"], located["longitude_degrees"]


def main() -> None:
    latitude, longitude = owner_point()
    print(f"recapturing at {latitude}, {longitude}")
    stage_dir = pathlib.Path(__file__).resolve().parent
    for stage in ("fetch_dem.py", "fetch_naip.py", "compile_parcel.py"):
        subprocess.run([sys.executable, str(stage_dir / stage), str(latitude), str(longitude)], check=True)


if __name__ == "__main__":
    main()
