"""The one ground frame every plot-local coordinate in the log is measured against.

The frame's origin is the parcel boundary's bounding-box south-west corner, snapped outward to
a whole 10cm cell in EPSG:26916. It is deliberately independent of the grid resolution, so the
1m fixture cut and the 10cm cut speak the same frame and the log stays internally consistent.

Nothing here reads the network: the boundary arrives via fetch_parcel_boundary.py.
"""

import json
import math

from pyproj import Transformer

from capture_paths import DATA_DIR

UTM_ZONE_16N = "EPSG:26916"
WGS84 = "EPSG:4326"
CELL_SIZE_METERS = 0.1
CELL_SNAP_EPSILON = 1e-9


def site_utm(latitude: float, longitude: float) -> tuple[float, float]:
    to_utm = Transformer.from_crs(WGS84, UTM_ZONE_16N, always_xy=True)
    return to_utm.transform(longitude, latitude)


def bbox_of(ring: list[list[float]]) -> tuple[float, float, float, float]:
    easts = [east for east, _ in ring]
    norths = [north for _, north in ring]
    return min(easts), min(norths), max(easts), max(norths)


def snapped_origin_of(ring_utm: list[list[float]]) -> tuple[float, float]:
    """Outward snap: the origin sits on the 10cm lattice at or west/south of the bbox corner."""
    west, south, _, _ = bbox_of(ring_utm)
    return (
        math.floor(round(west / CELL_SIZE_METERS, 6)) * CELL_SIZE_METERS,
        math.floor(round(south / CELL_SIZE_METERS, 6)) * CELL_SIZE_METERS,
    )


def cells_spanning(span_meters: float, cell_size: float) -> int:
    return math.ceil(span_meters / cell_size - CELL_SNAP_EPSILON)


class ParcelFrame:
    """Origin plus the cell extent that covers the ring at one resolution."""

    def __init__(self, ring_local: list[list[float]], origin_east: float, origin_north: float, cell_size: float):
        _, _, east_reach, north_reach = bbox_of(ring_local)
        self.origin_east = origin_east
        self.origin_north = origin_north
        self.cell_size = cell_size
        self.columns = cells_spanning(east_reach, cell_size)
        self.rows = cells_spanning(north_reach, cell_size)

    @property
    def cell_count(self) -> int:
        return self.columns * self.rows

    def bbox_utm(self, margin_meters: float = 0.0) -> tuple[float, float, float, float]:
        return (
            self.origin_east - margin_meters,
            self.origin_north - margin_meters,
            self.origin_east + self.columns * self.cell_size + margin_meters,
            self.origin_north + self.rows * self.cell_size + margin_meters,
        )


def boundary_path():
    return DATA_DIR / "boundary" / "boundary.json"


def read_boundary() -> dict:
    path = boundary_path()
    if not path.exists():
        raise SystemExit(f"no boundary at {path}; run capture/scripts/fetch_parcel_boundary.py first")
    return json.loads(path.read_text())


def frame_of(boundary: dict, cell_size: float) -> ParcelFrame:
    origin = boundary["plot_local_origin"]
    return ParcelFrame(boundary["ring_local_closed"], origin["easting_meters"], origin["northing_meters"], cell_size)


def inside_ring(east: float, north: float, ring_local: list[list[float]]) -> bool:
    vertices = ring_local[:-1] if ring_local[0] == ring_local[-1] else ring_local
    is_inside = False
    previous = len(vertices) - 1
    for vertex in range(len(vertices)):
        east_here, north_here = vertices[vertex]
        east_there, north_there = vertices[previous]
        if (north_here > north) != (north_there > north):
            crossing_east = east_here + (north - north_here) / (north_there - north_here) * (east_there - east_here)
            if east < crossing_east:
                is_inside = not is_inside
        previous = vertex
    return is_inside
