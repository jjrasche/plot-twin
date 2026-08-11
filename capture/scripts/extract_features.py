"""QL2 lidar + DEM + NAIP -> features.json: trees, structures, water, road for the 90m square.

Usage: python capture/scripts/extract_features.py 42.68317626142 -84.619591093007
Offline for rasters (capture/data/dem, capture/data/naip), reads the cached LAZ from
capture/data/lidar/. Canopy height model = first-return surface minus DEM ground, 1m grid.
Coordinates in features.json are parcel-local meters (0..90, row 0 = southernmost).
"""

import argparse
import collections
import json
import pathlib

import laspy
import numpy
import rasterio
from pyproj import Transformer

from capture_paths import DATA_DIR, write_json
from compile_parcel import UTM_ZONE_16N, sample_grid, site_utm

GRID_CELLS = 90
CELL_SIZE_METERS = 1.0
HALF_WIDTH_METERS = GRID_CELLS * CELL_SIZE_METERS / 2.0
FEET_TO_METERS = 0.3048

MIN_TREE_HEIGHT_METERS = 3.0
CANOPY_COVER_HEIGHT_METERS = 2.0
CROWN_EDGE_HEIGHT_SHARE = 0.5
MIN_CROWN_RADIUS_METERS = 1.0
MAX_CROWN_RADIUS_METERS = 6.0

ROAD_BRIGHTNESS_SHARE = 0.75
ROAD_GRAYNESS_FLOOR = 0.5

WATER_NIR_CEILING = 60.0
WATER_BRIGHTNESS_CEILING = 90.0
WATER_MIN_BLOB_CELLS = 40

CLASS_BUILDING = 6
CLASS_WATER = 9
CLASS_NOISE = 7


def read_square_points(laz_path: pathlib.Path, center_east: float, center_north: float) -> dict:
    las = laspy.read(laz_path)
    crs = las.header.parse_crs()
    horizontal = crs.sub_crs_list[0] if crs.is_compound else crs
    to_utm = Transformer.from_crs(horizontal, UTM_ZONE_16N, always_xy=True)
    east, north = to_utm.transform(numpy.asarray(las.x), numpy.asarray(las.y))
    inside = (
        (numpy.abs(east - center_east) <= HALF_WIDTH_METERS)
        & (numpy.abs(north - center_north) <= HALF_WIDTH_METERS)
    )
    classification = numpy.asarray(las.classification)[inside]
    keep = classification != CLASS_NOISE
    return {
        "east_local": east[inside][keep] - (center_east - HALF_WIDTH_METERS),
        "north_local": north[inside][keep] - (center_north - HALF_WIDTH_METERS),
        "height_meters": numpy.asarray(las.z)[inside][keep] * FEET_TO_METERS,
        "return_number": numpy.asarray(las.return_number)[inside][keep],
        "classification": classification[keep],
        "square_point_count": int(inside.sum()),
        "noise_point_count": int((~keep).sum()),
        "tile_point_count": int(las.header.point_count),
    }


def ground_grid(center_east: float, center_north: float) -> numpy.ndarray:
    manifest = json.loads((DATA_DIR / "dem" / "manifest.json").read_text())
    dem = rasterio.open(DATA_DIR / "dem" / manifest["local_file"])
    band = dem.read(1).astype(numpy.float64)
    return numpy.flipud(sample_grid(dem, band, center_east, center_north, GRID_CELLS, CELL_SIZE_METERS))


def first_return_surface(points: dict) -> numpy.ndarray:
    surface = numpy.full((GRID_CELLS, GRID_CELLS), numpy.nan)
    first = points["return_number"] == 1
    columns = numpy.clip(points["east_local"][first].astype(int), 0, GRID_CELLS - 1)
    rows = numpy.clip(points["north_local"][first].astype(int), 0, GRID_CELLS - 1)
    heights = points["height_meters"][first]
    numpy.fmax.at(surface, (rows, columns), heights)
    return surface


def canopy_height_model(surface: numpy.ndarray, ground: numpy.ndarray) -> numpy.ndarray:
    chm = surface - ground
    chm[numpy.isnan(chm)] = 0.0
    return numpy.clip(chm, 0.0, None)


def smoothed(chm: numpy.ndarray) -> numpy.ndarray:
    padded = numpy.pad(chm, 1, mode="edge")
    stacked = numpy.stack([
        padded[row : row + GRID_CELLS, column : column + GRID_CELLS]
        for row in range(3)
        for column in range(3)
    ])
    return stacked.mean(axis=0)


def crown_maxima(smooth_chm: numpy.ndarray) -> list[tuple[int, int]]:
    padded = numpy.pad(smooth_chm, 1, mode="constant", constant_values=-1)
    neighborhoods = numpy.stack([
        padded[row : row + GRID_CELLS, column : column + GRID_CELLS]
        for row in range(3)
        for column in range(3)
        if not (row == 1 and column == 1)
    ])
    is_peak = (smooth_chm >= neighborhoods.max(axis=0)) & (smooth_chm >= MIN_TREE_HEIGHT_METERS)
    return [tuple(cell) for cell in numpy.argwhere(is_peak)]


def crown_separation_meters(height: float) -> float:
    return max(2.0, 0.5 + 0.12 * height)


def suppress_close_maxima(maxima: list[tuple[int, int]], chm: numpy.ndarray) -> list[tuple[int, int]]:
    ordered = sorted(maxima, key=lambda cell: chm[cell], reverse=True)
    accepted: list[tuple[int, int]] = []
    for row, column in ordered:
        separation = crown_separation_meters(float(chm[row, column]))
        if all((row - r) ** 2 + (column - c) ** 2 >= separation**2 for r, c in accepted):
            accepted.append((row, column))
    return accepted


def crown_radius_meters(smooth_chm: numpy.ndarray, row: int, column: int) -> float:
    apex = smooth_chm[row, column]
    edge_height = apex * CROWN_EDGE_HEIGHT_SHARE
    radii = []
    for step_row, step_column in [(0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (1, -1), (-1, 1), (-1, -1)]:
        distance = 0.0
        for step in range(1, int(MAX_CROWN_RADIUS_METERS) + 1):
            r, c = row + step * step_row, column + step * step_column
            if not (0 <= r < GRID_CELLS and 0 <= c < GRID_CELLS) or smooth_chm[r, c] < edge_height:
                break
            distance = step * (1.4142 if step_row and step_column else 1.0)
        radii.append(distance)
    return float(numpy.clip(numpy.mean(radii), MIN_CROWN_RADIUS_METERS, MAX_CROWN_RADIUS_METERS))


def apex_height_meters(chm: numpy.ndarray, row: int, column: int) -> float:
    window = chm[max(0, row - 1) : row + 2, max(0, column - 1) : column + 2]
    return float(window.max())


def road_row_band(road: list[dict]) -> tuple[int, int] | None:
    if not road:
        return None
    norths = [point["north_meters"] for point in road[0]["footprint"]]
    return int(min(norths)), int(max(norths))


def extract_trees(chm: numpy.ndarray, smooth_chm: numpy.ndarray, road: list[dict]) -> tuple[list[dict], int]:
    """A crown maximum on the road surface is overhanging canopy — no trunk grows from
    asphalt — so road-band maxima are suppressed before trees are placed."""
    band = road_row_band(road)
    maxima = [
        (row, column)
        for row, column in crown_maxima(smooth_chm)
        if band is None or not (band[0] <= row < band[1])
    ]
    trees = [
        {
            "east_meters": column + 0.5,
            "north_meters": row + 0.5,
            "height_meters": round(apex_height_meters(chm, row, column), 2),
            "crown_radius_meters": round(crown_radius_meters(smooth_chm, row, column), 2),
        }
        for row, column in suppress_close_maxima(maxima, smooth_chm)
    ]
    return trees, len(maxima)


def extract_structures(points: dict) -> list[dict]:
    building = points["classification"] == CLASS_BUILDING
    if not building.any():
        return []
    east = points["east_local"][building]
    north = points["north_local"][building]
    heights = points["height_meters"][building]
    return [
        {
            "footprint": [
                {"east_meters": float(east.min()), "north_meters": float(north.min())},
                {"east_meters": float(east.max()), "north_meters": float(north.min())},
                {"east_meters": float(east.max()), "north_meters": float(north.max())},
                {"east_meters": float(east.min()), "north_meters": float(north.max())},
            ],
            "height_meters": round(float(numpy.percentile(heights, 90) - heights.min()), 2),
        }
    ]


def naip_grids(center_east: float, center_north: float) -> dict:
    manifest = json.loads((DATA_DIR / "naip" / "manifest.json").read_text())
    naip = rasterio.open(DATA_DIR / "naip" / manifest["local_file"])
    bands = {
        name: numpy.flipud(
            sample_grid(naip, naip.read(band).astype(numpy.float64), center_east, center_north, GRID_CELLS, CELL_SIZE_METERS)
        )
        for name, band in [("red", 1), ("green", 2), ("blue", 3), ("nir", 4)]
    }
    return bands


def extract_water(points: dict, naip: dict, chm: numpy.ndarray) -> list[dict]:
    water_class = points["classification"] == CLASS_WATER
    if water_class.any():
        east = points["east_local"][water_class]
        north = points["north_local"][water_class]
        elevation = float(numpy.median(points["height_meters"][water_class]))
        return [convex_ringed_water(east, north, elevation)]
    brightness = (naip["red"] + naip["green"] + naip["blue"]) / 3
    watery = (naip["nir"] < WATER_NIR_CEILING) & (brightness < WATER_BRIGHTNESS_CEILING)
    blob = largest_blob(watery)
    if blob is None or len(blob) < WATER_MIN_BLOB_CELLS:
        return []
    rows = numpy.array([cell[0] for cell in blob])
    columns = numpy.array([cell[1] for cell in blob])
    blob_chm = chm[rows, columns]
    if blob_chm.mean() >= CANOPY_COVER_HEIGHT_METERS:
        return []  # dark pixels under tall canopy are shade, not a pond
    return [convex_ringed_water(columns + 0.5, rows + 0.5, None)]


def convex_ringed_water(east: numpy.ndarray, north: numpy.ndarray, elevation: float | None) -> dict:
    ring = [
        {"east_meters": float(east.min()), "north_meters": float(north.min())},
        {"east_meters": float(east.max()), "north_meters": float(north.min())},
        {"east_meters": float(east.max()), "north_meters": float(north.max())},
        {"east_meters": float(east.min()), "north_meters": float(north.max())},
    ]
    return {"footprint": ring, "surface_elevation_meters": elevation}


def largest_blob(mask: numpy.ndarray) -> list[tuple[int, int]] | None:
    seen = numpy.zeros_like(mask, dtype=bool)
    best: list[tuple[int, int]] | None = None
    for row, column in numpy.argwhere(mask):
        if seen[row, column]:
            continue
        frontier = [(int(row), int(column))]
        seen[row, column] = True
        blob = []
        while frontier:
            r, c = frontier.pop()
            blob.append((r, c))
            for dr, dc in [(0, 1), (0, -1), (1, 0), (-1, 0)]:
                nr, nc = r + dr, c + dc
                if 0 <= nr < mask.shape[0] and 0 <= nc < mask.shape[1] and mask[nr, nc] and not seen[nr, nc]:
                    seen[nr, nc] = True
                    frontier.append((nr, nc))
        if best is None or len(blob) > len(best):
            best = blob
    return best


def extract_road(naip: dict) -> list[dict]:
    brightness = (naip["red"] + naip["green"] + naip["blue"]) / 3
    grayness = 1 - (
        numpy.abs(naip["red"] - naip["green"])
        + numpy.abs(naip["green"] - naip["blue"])
        + numpy.abs(naip["red"] - naip["blue"])
    ) / (brightness + 1)
    row_brightness = brightness.mean(axis=1)
    row_grayness = grayness.mean(axis=1)
    road_rows = numpy.flatnonzero(
        (row_brightness >= ROAD_BRIGHTNESS_SHARE * row_brightness.max()) & (row_grayness >= ROAD_GRAYNESS_FLOOR)
    )
    if road_rows.size == 0:
        return []
    south, north = int(road_rows.min()), int(road_rows.max()) + 1
    return [
        {
            "footprint": [
                {"east_meters": 0.0, "north_meters": float(south)},
                {"east_meters": float(GRID_CELLS), "north_meters": float(south)},
                {"east_meters": float(GRID_CELLS), "north_meters": float(north)},
                {"east_meters": 0.0, "north_meters": float(north)},
            ]
        }
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("latitude", type=float)
    parser.add_argument("longitude", type=float)
    args = parser.parse_args()

    lidar_manifest = json.loads((DATA_DIR / "lidar" / "manifest.json").read_text())
    tile = lidar_manifest["tiles"][0]
    center_east, center_north = site_utm(args.latitude, args.longitude)
    points = read_square_points(DATA_DIR / "lidar" / tile["local_file"], center_east, center_north)
    ground = ground_grid(center_east, center_north)
    surface = first_return_surface(points)
    chm = canopy_height_model(surface, ground)
    smooth_chm = smoothed(chm)
    naip = naip_grids(center_east, center_north)
    road = extract_road(naip)
    trees, maxima_count = extract_trees(chm, smooth_chm, road)
    structures = extract_structures(points)
    water = extract_water(points, naip, chm)

    histogram = collections.Counter(points["classification"].tolist())
    cover_fraction = float((chm >= CANOPY_COVER_HEIGHT_METERS).mean())
    features = {
        "site": {"latitude_degrees": args.latitude, "longitude_degrees": args.longitude},
        "grid": {"cells": GRID_CELLS, "cell_size_meters": CELL_SIZE_METERS},
        "trees": trees,
        "structures": structures,
        "water": water,
        "road": road,
        "receipts": {
            "tile_point_count": points["tile_point_count"],
            "square_point_count": points["square_point_count"],
            "noise_point_count": points["noise_point_count"],
            "class_histogram": {str(int(key)): int(value) for key, value in sorted(histogram.items())},
            "first_return_count": int((points["return_number"] == 1).sum()),
            "chm_max_meters": round(float(chm.max()), 2),
            "chm_mean_meters": round(float(chm.mean()), 2),
            "canopy_cover_fraction": round(cover_fraction, 3),
            "crown_maxima_count": maxima_count,
            "tree_count": len(trees),
        },
        "provenance": {
            "lidar_product": tile["product_title"],
            "lidar_url": tile["download_url"],
            "dataset": lidar_manifest["dataset"],
            "method": (
                "CHM = per-cell max first-return height minus 1m 3DEP DEM ground; trees = 3x3 "
                "local maxima on 3x3-mean-smoothed CHM >= 3m, greedy radius suppression, crown "
                "radius from 8-ray half-height walk; structures = class-6 points (absence is a "
                "finding); water = class-9 else contiguous low-NIR dark NAIP blob; road = "
                "brightest gray NAIP row band"
            ),
            "horizontal_crs": UTM_ZONE_16N,
            "vertical_datum": "NAVD88 (Geoid12B), lidar feet converted to meters",
        },
    }
    write_json(DATA_DIR / "compiled" / "features.json", features)
    print(json.dumps(features["receipts"], indent=1))
    print(f"structures: {len(structures)} (class-6 absent = REO woodlot finding holds)" if not structures else f"structures: {len(structures)}")
    print(f"water: {len(water)}, road bands: {len(road)}")


if __name__ == "__main__":
    main()
