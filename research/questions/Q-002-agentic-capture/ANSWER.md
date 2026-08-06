# Agentic capture: photos + GPS + satellite → typed world state

**Verdict: the terrain layer is already solved — free QL2 LiDAR + 1m DEM + NAIP cover the
parcel today. The v1 capture pipeline is aerial-first: segment-geospatial + DeepForest on
NAIP for typed footprints, LiDAR canopy-height model for heights, phone photos only as
evidence for what aerial can't see. Whole-parcel photogrammetry/splatting is the expensive,
fragile part — defer it. SAM 3D is weeks-old research-grade; keep it off the critical path.**

## What terrain data exists for the parcel (Delta Twp, Eaton Co, MI)

Downloadable today, $0, ~1 hour total:

- **LiDAR point cloud**: QL2, ~2.2 pts/m², flown Dec 2017 – Apr 2018 (leaf-off), 19.6cm
  vertical accuracy — Eaton County is in the 2016–2017 NRCS 30-county Michigan project
  under MiSAIL's statewide QL2 completion
  ([NOAA InPort 55315](https://www.fisheries.noaa.gov/inport/item/55315);
  [MiSAIL](https://www.michigan.gov/dtmb/services/maps/misail)). Clip an AOI via the
  [NOAA Data Access Viewer](https://coast.noaa.gov/dataviewer/) or pull tiles from
  [USGS LidarExplorer](https://apps.nationalmap.gov/lidar-explorer/).
- **1m bare-earth DEM**: USGS standard product wherever QL2 exists
  ([collection](https://data.usgs.gov/datacatalog/data/USGS:77ae0551-c61e-4979-aedd-d797abdcde0e)).
- **NAIP imagery**: Michigan 2022, 4-band RGB+NIR, 60cm, leaf-on
  ([InPort 70528](https://www.fisheries.noaa.gov/inport/item/70528)); a 2024 flight is
  likely but unconfirmed. Eaton County GIS may hold 6–12in county orthos (unverified —
  one lookup).
- No Eaton refresh is in the FY25 3DEP selections
  ([DCA](https://www.usgs.gov/3d-national-topography-model/fy25-3dep-data-collaboration-announcement-dca-selected-projects)) —
  the 2017–18 QL2 is the best available and it comfortably feeds the 10cm-grid terrain
  decision (heights interpolate; the grid resolution is about entities, not LiDAR density).
- Vintage caveat: 2017–18 point cloud — trees have grown or gone; leaf-off underestimates
  deciduous crowns.

## Toolchain: proven vs. novel

| Stage | Tool | Status |
|---|---|---|
| Footprints + typing from imagery | [segment-geospatial](https://github.com/opengeos/segment-geospatial) (Grounding DINO + SAM → georeferenced GeoJSON, text-prompted) | Proven, open |
| Per-tree detection on NAIP | [DeepForest](https://github.com/weecology/DeepForest) (pretrained NEON crowns, F1 0.73–0.95 — [paper](https://besjournals.onlinelibrary.wiley.com/doi/full/10.1111/2041-210X.13472)) | Proven, open |
| Heights | PDAL/lidR: first-return DSM − DEM = canopy-height model | Proven, trivial |
| Building footprints | Microsoft Building Footprints / OSM (already cover Eaton Co) | Proven — don't re-detect |
| Yard photogrammetry | OpenDroneMap/WebODM (georeferenced ortho + DSM), COLMAP | Proven, but needs drone/photo effort |
| Splatting | Nerfstudio splatfacto, Polycam/Luma | Proven as *visualization*; splats aren't typed entities |
| Splat-from-Google-Earth | [arXiv 2405.11021](https://arxiv.org/abs/2405.11021) | Research-grade, ToS-gray, rural 3D coverage doubtful |
| Single-photo object → 3D | [SAM 3D](https://arxiv.org/abs/2511.16624); aerial evaluation [arXiv 2512.22452](https://arxiv.org/abs/2512.22452) | Research-grade (Nov–Dec 2025); georeferencing unsolved |
| Aerial+ground fusion | Research literature only; pragmatic fusion = everything georeferenced, join by coordinates | Novel as turnkey, trivial as practice |

Reframe from the charter: "photogrammetry → segmentation → entity typing" inverts the cheap
order. The v1 decomposition is **aerial-first (segmentation+typing on existing rasters) →
LiDAR heights → ground photos as evidence attachments**; photogrammetry is v2, and only if
v1 proves 60cm imagery can't resolve the entities that matter.

## Cheapest v1 experiment ($0, ~1 day)

Output: typed entity rows — `id, type, centroid, footprint_polygon, height_m`.

1. Download LAZ clip + 1m DEM + NAIP tile + county parcel boundary (~1 hr).
2. CHM raster: first-return DSM at 0.5–1m minus DEM (PDAL/lidR, 1–2 hrs).
3. segment-geospatial on NAIP with prompts "tree"/"building"/"driveway" → GeoJSON polygons;
   DeepForest for per-tree instances (2–3 hrs). Expect fences and garden beds to FAIL at
   60cm — that's a finding, not a bug.
4. GeoPandas join: per polygon, zonal-stat the CHM (p95) → entity rows (~1 hr).
5. Ground pass (half-day): GPS-tagged phone photos of fences/terraces/beds; v1 places a
   point entity at EXIF GPS (±3–5m), types it with Grounding DINO, footprint hand-traced in
   QGIS over NAIP (~20 min for 2 acres).

The experiment answers the three questions that gate any spend: (a) does NAIP+samgeo yield
usable footprints at parcel scale, (b) does the 2017 CHM still match reality, (c) is phone
GPS good enough to auto-place ground entities. If fences/beds/currency fail → the single
likely next spend is a ~$300 drone flight through OpenDroneMap (2cm ortho + fresh DSM),
which makes the small stuff machine-detectable.

## Contradictions with locked decisions

None — this confirms the "USGS/Eaton County LiDAR first" decision with the concrete dataset,
and supports loop-before-capture: v1 capture is a day of scripting against free data, so
nothing about capture pressures the schema.
