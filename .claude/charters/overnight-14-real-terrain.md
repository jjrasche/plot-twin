# Charter 14 — real terrain: address in, walkable parcel out

## Where it fits
Priority one of the 2026-08-08 run, ruled by Jim verbatim: "Use USGS 3DEP lidar for elevation
and NAIP aerial imagery for texture; both are open. Build the pipeline: address in, DEM plus
imagery out, rendered as navigable 3D terrain." **Google Maps extraction is prohibited —
licensing.** 3DEP + NAIP are the ONLY sources. Q-002 (research/questions/Q-002-agentic-capture/)
already answered the accuracy budget — extend it, don't redo it. Terrain schema exists:
BaseTerrainRow + TerrainDiffRow (D-011), site row (D-017), CAPTURE writer role (D-013).
The renderer, eyes checks, and walkable app all exist and read projections.

## Deliverable
A new `:capture` gradle module (Kotlin; python helper scripts under `capture/scripts/` are fine
for HTTP + geodata munging) implementing the pipeline as pure stages:

1. **address → parcel location**: geocode via the free US Census geocoder (GET). The test
   parcel is Jim's — the site row's lat/lon already in the log fixture is ground truth; the
   address path must reproduce it within tolerance.
2. **location → 3DEP elevation**: query USGS TNM Access API for the best available 3DEP
   product covering the parcel (1m DEM at minimum; QL2 lidar tile if reachable without heavy
   deps). Download to a **gitignored** `capture/data/` dir. Never commit raster/point binaries.
3. **location → NAIP imagery**: fetch the NAIP orthophoto covering the parcel (USGS/USDA open
   endpoints). Same gitignored cache.
4. **compile → log rows**: resample elevation to the 10cm grid (D-011: fixed uniform, bilinear
   from source resolution — store interpolation honestly, Q-002's support-distance concern is
   noted in the row metadata if the field exists, otherwise in the receipt), emit a
   base-terrain row + site row with CAPTURE writer through the normal append path. Nothing
   lives outside the log.
5. **texture**: bake NAIP color onto the terrain — v1 is per-triangle/per-vertex color through
   the existing painter (that path is proven). If drawVertices texCoords draping works headless
   in kotlin-compose 0.19.0, measure it and say so, but do NOT block PASS on it.
6. **render**: the existing `:app` walkable viewer runs on the real parcel projection. Add an
   entry point or flag; do not fork the render path.

## Tests that could fail
- Geocode stage: known address → coordinates matching the site row within 30 m.
- DEM stage: downloaded elevation over the parcel has a plausible range for Delta Twp, Eaton
  Co MI (roughly 250–280 m NAVD88 — verify against the actual tile and state the number).
- Compile stage: base-terrain row round-trips through the log; projection grid dimensions
  match the parcel extent at 10cm; replay is deterministic.
- Render stage: headless capture of the real parcel produces a contact sheet via the existing
  eyes PlotViewer path; SkylineCheck's DEM-predicted skyline agrees with the rendered skyline
  (coordinate with charter 15's sky-aware fix — if it hasn't landed, run the check advisory
  and say so).
- Offline replay: with the gitignored cache populated, the whole pipeline runs without
  network (stages take files, not URLs — network is a thin fetch layer).

## Bands (pre-committed)
- **PASS**: address → real-parcel walkable render, all stages tested, elevation range receipt
  quoted from real data, NAIP color visible in the contact sheet, full `bash gradlew test`
  green including existing 122+ tests.
- **WEAK**: real DEM renders but NAIP texture missing or checks advisory-only — name the gap.
- **FAIL**: no real data reaches the log, or existing tests break.

## Rails
- Branch `build/real-terrain` in worktree `../.git-worktrees/pt-terrain` (create from repo
  root: `git worktree add ../.git-worktrees/pt-terrain -b build/real-terrain`).
- **Commit a checkpoint at every meaningful step, even if it does not build — say WIP.**
- No pushes. No merges — the lead lands.
- Command shape: no `cd X && …`, no `git -C`, no multi-line commands, no compound chains.
  `cd` is its own call; then plain single commands. Gradle is `bash gradlew <task>`.
- **All HTTP through python scripts** (`python capture/scripts/fetch_dem.py …`) — never curl.
  GET-only public endpoints; no keys, no auth.
- Long downloads/builds run in the FOREGROUND — your background tasks die with you.
- Raw tiles are gitignored; committed artifacts are code, small fixtures, receipt PNGs only.
- Report shape: `git diff --stat`, verbatim gate output (test counts from TEST-*.xml, not
  prose), elevation-range receipt, contradictions found, questions for the lead.
