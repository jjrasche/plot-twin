"""Rerun the three capture stages on the owner's geocoded point.

Usage: python capture/scripts/recapture.py
Reads capture/data/geocode.json (produced by geocode.py with the owner address) and runs
fetch_dem, fetch_naip, compile_parcel and extract_features, so one command regenerates the
parcel after the address arms the pipeline. The property line is the extent, so the boundary
pull comes first and is not re-run here: it needs the county's parcel id, which no geocode has.
"""

import json
import pathlib
import subprocess
import sys

from capture_paths import DATA_DIR
from parcel_frame import boundary_path

GEOCODE_PATH = DATA_DIR / "geocode.json"
STAGES = ("fetch_dem.py", "fetch_naip.py", "compile_parcel.py", "extract_features.py")
STAGES_TAKING_NO_POINT = ("fetch_naip.py",)


def owner_point() -> tuple[float, float]:
    if not GEOCODE_PATH.exists():
        raise SystemExit('no capture/data/geocode.json - run: python capture/scripts/geocode.py "<address>"')
    located = json.loads(GEOCODE_PATH.read_text())
    return located["latitude_degrees"], located["longitude_degrees"]


def main() -> None:
    if not boundary_path().exists():
        raise SystemExit(
            f"no {boundary_path()} - the property line is the extent. Run: "
            "python capture/scripts/fetch_parcel_boundary.py <parcel-id> <latitude> <longitude>"
        )
    latitude, longitude = owner_point()
    print(f"recapturing at {latitude}, {longitude}")
    stage_dir = pathlib.Path(__file__).resolve().parent
    for stage in STAGES:
        point = [] if stage in STAGES_TAKING_NO_POINT else [str(latitude), str(longitude)]
        subprocess.run([sys.executable, str(stage_dir / stage), *point], check=True)


if __name__ == "__main__":
    main()
