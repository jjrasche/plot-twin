"""QL2 lidar + DEM + NAIP -> features.json: trees, structures, water, road inside the property line.

Usage: python capture/scripts/extract_features.py 42.68317626142 -84.619591093007
Offline for rasters (capture/data/dem, capture/data/naip), reads the cached LAZ from
capture/data/lidar/ and the property line from capture/data/boundary/. Canopy height model =
first-return surface minus DEM ground, 1m grid over the boundary's bounding box.
Coordinates in features.json are plot-local meters in the boundary's frame (row 0 = southernmost).
A feature whose ground position falls outside the property line belongs to a neighbour and is
dropped: this plot's log holds this plot's things.
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
from compile_parcel import sample_frame
from parcel_frame import UTM_ZONE_16N, ParcelFrame, frame_of, inside_ring, read_boundary

CELL_SIZE_METERS = 1.0
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


def read_frame_points(laz_path: pathlib.Path, frame: ParcelFrame) -> dict:
    las = laspy.read(laz_path)
    crs = las.header.parse_crs()
    horizontal = crs.sub_crs_list[0] if crs.is_compound else crs
    to_utm = Transformer.from_crs(horizontal, UTM_ZONE_16N, always_xy=True)
    east, north = to_utm.transform(numpy.asarray(las.x), numpy.asarray(las.y))
    east_local = east - frame.origin_east
    north_local = north - frame.origin_north
    inside = (
        (east_local >= 0.0)
        & (east_local <= frame.columns * frame.cell_size)
        & (north_local >= 0.0)
        & (north_local <= frame.rows * frame.cell_size)
    )
    classification = numpy.asarray(las.classification)[inside]
    keep = classification != CLASS_NOISE
    return {
        "east_local": east_local[inside][keep],
        "north_local": north_local[inside][keep],
        "height_meters": numpy.asarray(las.z)[inside][keep] * FEET_TO_METERS,
        "return_number": numpy.asarray(las.return_number)[inside][keep],
        "classification": classification[keep],
        "extent_point_count": int(inside.sum()),
        "noise_point_count": int((~keep).sum()),
        "tile_point_count": int(las.header.point_count),
    }


def ground_grid(frame: ParcelFrame) -> numpy.ndarray:
    manifest = json.loads((DATA_DIR / "dem" / "manifest.json").read_text())
    dem = rasterio.open(DATA_DIR / "dem" / manifest["local_file"])
    band = dem.read(1).astype(numpy.float64)
    return numpy.flipud(sample_frame(dem, band, frame))


def inside_boundary_mask(frame: ParcelFrame, ring_local: list[list[float]]) -> numpy.ndarray:
    mask = numpy.zeros((frame.rows, frame.columns), dtype=bool)
    for row in range(frame.rows):
        north = (row + 0.5) * frame.cell_size
        for column in range(frame.columns):
            mask[row, column] = inside_ring((column + 0.5) * frame.cell_size, north, ring_local)
    return mask


def first_return_surface(points: dict, frame: ParcelFrame) -> numpy.ndarray:
    surface = numpy.full((frame.rows, frame.columns), numpy.nan)
    first = points["return_number"] == 1
    columns = numpy.clip((points["east_local"][first] / frame.cell_size).astype(int), 0, frame.columns - 1)
    rows = numpy.clip((points["north_local"][first] / frame.cell_size).astype(int), 0, frame.rows - 1)
    heights = points["height_meters"][first]
    numpy.fmax.at(surface, (rows, columns), heights)
    return surface


def canopy_height_model(surface: numpy.ndarray, ground: numpy.ndarray) -> numpy.ndarray:
    chm = surface - ground
    chm[numpy.isnan(chm)] = 0.0
    return numpy.clip(chm, 0.0, None)


def smoothed(chm: numpy.ndarray) -> numpy.ndarray:
    rows, columns = chm.shape
    padded = numpy.pad(chm, 1, mode="edge")
    stacked = numpy.stack([
        padded[row : row + rows, column : column + columns]
        for row in range(3)
        for column in range(3)
    ])
    return stacked.mean(axis=0)


def crown_maxima(smooth_chm: numpy.ndarray) -> list[tuple[int, int]]:
    rows, columns = smooth_chm.shape
    padded = numpy.pad(smooth_chm, 1, mode="constant", constant_values=-1)
    neighborhoods = numpy.stack([
        padded[row : row + rows, column : column + columns]
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
    rows, columns = smooth_chm.shape
    apex = smooth_chm[row, column]
    edge_height = apex * CROWN_EDGE_HEIGHT_SHARE
    radii = []
    for step_row, step_column in [(0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (1, -1), (-1, 1), (-1, -1)]:
        distance = 0.0
        for step in range(1, int(MAX_CROWN_RADIUS_METERS) + 1):
            r, c = row + step * step_row, column + step * step_column
            if not (0 <= r < rows and 0 <= c < columns) or smooth_chm[r, c] < edge_height:
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


def extract_trees(
    chm: numpy.ndarray,
    smooth_chm: numpy.ndarray,
    road: list[dict],
    ring_local: list[list[float]],
) -> tuple[list[dict], int, int]:
    """A crown maximum on the road surface is overhanging canopy - no trunk grows from asphalt -
    so road-band maxima are suppressed, and a trunk on the neighbour's side of the line is theirs."""
    band = road_row_band(road)
    maxima = [
        (row, column)
        for row, column in crown_maxima(smooth_chm)
        if band is None or not (band[0] <= row < band[1])
    ]
    inside_maxima = [
        (row, column) for row, column in maxima if inside_ring(column + 0.5, row + 0.5, ring_local)
    ]
    trees = [
        {
            "east_meters": column + 0.5,
            "north_meters": row + 0.5,
            "height_meters": round(apex_height_meters(chm, row, column), 2),
            "crown_radius_meters": round(crown_radius_meters(smooth_chm, row, column), 2),
        }
        for row, column in suppress_close_maxima(inside_maxima, smooth_chm)
    ]
    return trees, len(maxima), len(inside_maxima)


def points_inside_ring(points: dict, selected: numpy.ndarray, ring_local: list[list[float]]) -> numpy.ndarray:
    east = points["east_local"][selected]
    north = points["north_local"][selected]
    return numpy.array(
        [inside_ring(float(east[index]), float(north[index]), ring_local) for index in range(east.size)],
        dtype=bool,
    )


def extract_structures(points: dict, ring_local: list[list[float]]) -> list[dict]:
    building = points["classification"] == CLASS_BUILDING
    if not building.any():
        return []
    on_plot = points_inside_ring(points, building, ring_local)
    if not on_plot.any():
        return []
    east = points["east_local"][building][on_plot]
    north = points["north_local"][building][on_plot]
    heights = points["height_meters"][building][on_plot]
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


def naip_grids(frame: ParcelFrame) -> dict:
    manifest = json.loads((DATA_DIR / "naip" / "manifest.json").read_text())
    naip = rasterio.open(DATA_DIR / "naip" / manifest["local_file"])
    return {
        name: numpy.flipud(sample_frame(naip, naip.read(band).astype(numpy.float64), frame))
        for name, band in [("red", 1), ("green", 2), ("blue", 3), ("nir", 4)]
    }


def extract_water(points: dict, naip: dict, chm: numpy.ndarray, inside: numpy.ndarray, ring_local: list) -> list[dict]:
    water_class = points["classification"] == CLASS_WATER
    if water_class.any():
        on_plot = points_inside_ring(points, water_class, ring_local)
        if on_plot.any():
            east = points["east_local"][water_class][on_plot]
            north = points["north_local"][water_class][on_plot]
            elevation = float(numpy.median(points["height_meters"][water_class][on_plot]))
            return [convex_ringed_water(east, north, elevation)]
    brightness = (naip["red"] + naip["green"] + naip["blue"]) / 3
    watery = (naip["nir"] < WATER_NIR_CEILING) & (brightness < WATER_BRIGHTNESS_CEILING) & inside
    blob = largest_blob(watery)
    if blob is None or len(blob) < WATER_MIN_BLOB_CELLS:
        return []
    rows = numpy.array([cell[0] for cell in blob])
    columns = numpy.array([cell[1] for cell in blob])
    if chm[rows, columns].mean() >= CANOPY_COVER_HEIGHT_METERS:
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


def extract_road(naip: dict, inside: numpy.ndarray, ring_local: list[list[float]]) -> list[dict]:
    """The road is the brightest gray east-west band; only the part on this plot is logged, so
    the corridor ring is the band clipped to the property line."""
    brightness = (naip["red"] + naip["green"] + naip["blue"]) / 3
    grayness = 1 - (
        numpy.abs(naip["red"] - naip["green"])
        + numpy.abs(naip["green"] - naip["blue"])
        + numpy.abs(naip["red"] - naip["blue"])
    ) / (brightness + 1)
    on_plot_rows = inside.any(axis=1)
    row_brightness = numpy.where(inside, brightness, numpy.nan)
    row_grayness = numpy.where(inside, grayness, numpy.nan)
    with numpy.errstate(invalid="ignore"):
        mean_brightness = numpy.nanmean(row_brightness, axis=1)
        mean_grayness = numpy.nanmean(row_grayness, axis=1)
    road_rows = numpy.flatnonzero(
        on_plot_rows
        & (mean_brightness >= ROAD_BRIGHTNESS_SHARE * numpy.nanmax(mean_brightness))
        & (mean_grayness >= ROAD_GRAYNESS_FLOOR)
    )
    if road_rows.size == 0:
        return []
    south, north = float(road_rows.min()), float(road_rows.max() + 1)
    band = [[0.0, south], [float(inside.shape[1]), south], [float(inside.shape[1]), north], [0.0, north]]
    corridor = clip_ring_to_convex_ring(band, ring_local)
    if len(corridor) < 3:
        return []
    return [{"footprint": [{"east_meters": east, "north_meters": north} for east, north in corridor]}]


def clip_ring_to_convex_ring(ring: list[list[float]], clip_ring: list[list[float]]) -> list[list[float]]:
    """Sutherland-Hodgman against each clip edge: exact for a convex clip ring, which is why
    the caller refuses a non-convex property line rather than clipping it wrong."""
    vertices = clip_ring[:-1] if clip_ring[0] == clip_ring[-1] else clip_ring
    winding = 1.0 if signed_area_of(vertices) > 0 else -1.0
    clipped = ring
    for vertex in range(len(vertices)):
        edge_start = vertices[vertex]
        edge_end = vertices[(vertex + 1) % len(vertices)]
        clipped = clip_to_half_plane(clipped, edge_start, edge_end, winding)
        if not clipped:
            return []
    return clipped


def signed_area_of(vertices: list[list[float]]) -> float:
    return sum(
        vertices[vertex][0] * vertices[(vertex + 1) % len(vertices)][1]
        - vertices[(vertex + 1) % len(vertices)][0] * vertices[vertex][1]
        for vertex in range(len(vertices))
    ) / 2.0


def side_of(point: list[float], edge_start: list[float], edge_end: list[float], winding: float) -> float:
    return winding * (
        (edge_end[0] - edge_start[0]) * (point[1] - edge_start[1])
        - (edge_end[1] - edge_start[1]) * (point[0] - edge_start[0])
    )


def clip_to_half_plane(
    ring: list[list[float]], edge_start: list[float], edge_end: list[float], winding: float
) -> list[list[float]]:
    clipped: list[list[float]] = []
    for vertex in range(len(ring)):
        current = ring[vertex]
        previous = ring[vertex - 1]
        current_side = side_of(current, edge_start, edge_end, winding)
        previous_side = side_of(previous, edge_start, edge_end, winding)
        if current_side >= 0.0:
            if previous_side < 0.0:
                clipped.append(crossing_point(previous, current, previous_side, current_side))
            clipped.append(current)
        elif previous_side >= 0.0:
            clipped.append(crossing_point(previous, current, previous_side, current_side))
    return clipped


def crossing_point(previous: list[float], current: list[float], previous_side: float, current_side: float) -> list[float]:
    fraction = previous_side / (previous_side - current_side)
    return [
        previous[0] + fraction * (current[0] - previous[0]),
        previous[1] + fraction * (current[1] - previous[1]),
    ]


def refuse_unless_convex(ring_local: list[list[float]]) -> None:
    vertices = ring_local[:-1] if ring_local[0] == ring_local[-1] else ring_local
    winding = 1.0 if signed_area_of(vertices) > 0 else -1.0
    for vertex in range(len(vertices)):
        turn = side_of(
            vertices[(vertex + 2) % len(vertices)],
            vertices[vertex],
            vertices[(vertex + 1) % len(vertices)],
            winding,
        )
        if turn < 0.0:
            raise SystemExit(
                "the property line is not convex at vertex %d; corridor clipping would be wrong. "
                "Clipping a corridor to a concave parcel is real work, not a tolerance." % vertex
            )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("latitude", type=float)
    parser.add_argument("longitude", type=float)
    args = parser.parse_args()

    boundary = read_boundary()
    ring_local = boundary["ring_local_closed"]
    refuse_unless_convex(ring_local)
    frame = frame_of(boundary, CELL_SIZE_METERS)

    lidar_manifest = json.loads((DATA_DIR / "lidar" / "manifest.json").read_text())
    tile = lidar_manifest["tiles"][0]
    points = read_frame_points(DATA_DIR / "lidar" / tile["local_file"], frame)
    ground = ground_grid(frame)
    surface = first_return_surface(points, frame)
    chm = canopy_height_model(surface, ground)
    smooth_chm = smoothed(chm)
    inside = inside_boundary_mask(frame, ring_local)
    naip = naip_grids(frame)
    road = extract_road(naip, inside, ring_local)
    trees, maxima_count, inside_maxima_count = extract_trees(chm, smooth_chm, road, ring_local)
    structures = extract_structures(points, ring_local)
    water = extract_water(points, naip, chm, inside, ring_local)

    histogram = collections.Counter(points["classification"].tolist())
    features = {
        "site": {"latitude_degrees": args.latitude, "longitude_degrees": args.longitude},
        "grid": {
            "columns": frame.columns,
            "rows": frame.rows,
            "cell_size_meters": frame.cell_size,
            "origin_easting_meters": frame.origin_east,
            "origin_northing_meters": frame.origin_north,
        },
        "trees": trees,
        "structures": structures,
        "water": water,
        "road": road,
        "receipts": {
            "tile_point_count": points["tile_point_count"],
            "extent_point_count": points["extent_point_count"],
            "noise_point_count": points["noise_point_count"],
            "class_histogram": {str(int(key)): int(value) for key, value in sorted(histogram.items())},
            "first_return_count": int((points["return_number"] == 1).sum()),
            "chm_max_meters": round(float(chm.max()), 2),
            "chm_mean_meters": round(float(chm.mean()), 2),
            "canopy_cover_fraction": round(float((chm[inside] >= CANOPY_COVER_HEIGHT_METERS).mean()), 3),
            "inside_boundary_cell_count": int(inside.sum()),
            "extent_cell_count": int(inside.size),
            "crown_maxima_count": maxima_count,
            "inside_boundary_crown_maxima_count": inside_maxima_count,
            "tree_count": len(trees),
        },
        "provenance": {
            "lidar_product": tile["product_title"],
            "lidar_url": tile["download_url"],
            "dataset": lidar_manifest["dataset"],
            "method": (
                "CHM = per-cell max first-return height minus 1m 3DEP DEM ground; trees = 3x3 "
                "local maxima on 3x3-mean-smoothed CHM >= 3m inside the property line, greedy "
                "radius suppression, crown radius from 8-ray half-height walk; structures = "
                "class-6 points on the plot (absence is a finding); water = class-9 else "
                "contiguous low-NIR dark NAIP blob inside the line; road = brightest gray NAIP "
                "row band clipped to the property line"
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
