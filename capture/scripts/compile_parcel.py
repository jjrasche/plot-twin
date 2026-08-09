"""Compile cached 3DEP + NAIP rasters into the 10cm-grid parcel file the :capture module ingests.

Usage: python capture/scripts/compile_parcel.py 42.6006 -84.6547 [--time-zone America/Detroit]
Offline by design: reads capture/data/dem/ and capture/data/naip/, never the network.
Row 0 = southernmost row, column 0 = westernmost column (TerrainGrid convention).
"""

import argparse
import base64
import json
import pathlib

import numpy
import rasterio
from pyproj import Transformer

from capture_paths import DATA_DIR, write_json

UTM_ZONE_16N = "EPSG:26916"
CELL_SIZE_METERS = 0.1
CELLS_PER_SIDE = 900
FIXTURE_CELL_SIZE_METERS = 1.0
FIXTURE_CELLS_PER_SIDE = 90
DEM_EPOCH = "2017-12-01/2018-04-23"
VERTICAL_DATUM = "NAVD88 (GEOID12B)"


def site_utm(latitude: float, longitude: float) -> tuple[float, float]:
    to_utm = Transformer.from_crs("EPSG:4326", UTM_ZONE_16N, always_xy=True)
    return to_utm.transform(longitude, latitude)


def cell_center_axes(center: float, cells: int, cell_size: float) -> numpy.ndarray:
    halfwidth = cells * cell_size / 2.0
    return center - halfwidth + (numpy.arange(cells) + 0.5) * cell_size


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


def sample_grid(raster, band: numpy.ndarray, center_east: float, center_north: float, cells: int, cell_size: float) -> numpy.ndarray:
    easts = cell_center_axes(center_east, cells, cell_size)
    norths = cell_center_axes(center_north, cells, cell_size)
    east_mesh, north_mesh = numpy.meshgrid(easts, norths)
    return bilinear_sample(raster, band, east_mesh, north_mesh)


def heights_base64(south_up_heights: numpy.ndarray) -> str:
    return base64.b64encode(south_up_heights.astype("<f4").tobytes()).decode()


def albedo_base64(red: numpy.ndarray, green: numpy.ndarray, blue: numpy.ndarray) -> str:
    stacked = numpy.stack([red, green, blue], axis=-1)
    return base64.b64encode(numpy.rint(stacked).clip(0, 255).astype(numpy.uint8).tobytes()).decode()


def compile_grid(dem, naip, dem_band, naip_bands, center_east, center_north, cells, cell_size, manifest) -> dict:
    surface = sample_grid(dem, dem_band, center_east, center_north, cells, cell_size)
    surface_south_up = numpy.flipud(surface)
    rgb_south_up = [
        numpy.flipud(sample_grid(naip, naip_bands[band], center_east, center_north, cells, cell_size))
        for band in range(3)
    ]
    return {
        "columns": cells,
        "rows": cells,
        "cell_size_meters": cell_size,
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

    dem_manifest = json.loads((DATA_DIR / "dem" / "manifest.json").read_text())
    dem = rasterio.open(DATA_DIR / "dem" / dem_manifest["local_file"])
    naip_manifest = json.loads((DATA_DIR / "naip" / "manifest.json").read_text())
    naip = rasterio.open(DATA_DIR / "naip" / naip_manifest["local_file"])
    assert str(dem.crs) == UTM_ZONE_16N and str(naip.crs) == UTM_ZONE_16N

    center_east, center_north = site_utm(args.latitude, args.longitude)
    dem_band = dem.read(1).astype(numpy.float64)
    naip_bands = [naip.read(band + 1).astype(numpy.float64) for band in range(3)]
    site = {
        "latitude_degrees": args.latitude,
        "longitude_degrees": args.longitude,
        "time_zone_id": args.time_zone,
    }

    parcel = {"site": site} | compile_grid(
        dem, naip, dem_band, naip_bands, center_east, center_north, CELLS_PER_SIDE, CELL_SIZE_METERS, dem_manifest
    )
    write_json(DATA_DIR / "compiled" / "parcel.json", parcel)

    fixture = {"site": site} | compile_grid(
        dem, naip, dem_band, naip_bands, center_east, center_north, FIXTURE_CELLS_PER_SIDE, FIXTURE_CELL_SIZE_METERS, dem_manifest
    )
    fixture_path = pathlib.Path(__file__).resolve().parent.parent / "src" / "testFixtures" / "resources" / "real_parcel_1m_90x90.json"
    write_json(fixture_path, fixture)

    provenance = parcel["provenance"]
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
