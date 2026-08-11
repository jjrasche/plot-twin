# Charter 19 — the eyes get real: true 3D from open data

## Where it fits
Jim, 2026-08-10, verbatim: "Tonight, real build, no toy, no flat overlay. The eyes must
become real: last run produced a squished satellite-over-topology smear that does not
resemble ground. Build true 3D: terrain mesh from the 3DEP lidar, plus actual modeled
objects — trees, structures, the pond — as real geometry, not draped imagery. Research 3D
modeling of natural terrain and vegetation from open data as needed; adjust through visual
cycles until the render resembles Isaac's real parcel at 11157 W Jolly Rd."

Why the smear: the 1m 3DEP DEM is BARE-EARTH — every tree is flattened into painted ground.
The vertical world lives in the QL2 lidar POINT CLOUD (first returns = canopy/roof surfaces,
class 6 = buildings, class 9 = water), which Q-002 already confirmed covers the parcel.
D-007 already rules the target shape: entities are exact vectors with heights — a tree is a
trunk cylinder + canopy ellipsoid. This charter turns point-cloud verticality into those
entity rows and renders them as geometry. D-019 (new) rules the process: visual cycles,
scored by the lead's eye, better-vs-worse measured.

## Deliverable
1. **Point-cloud stage** (`capture/scripts/fetch_lidar.py` + `extract_features.py`, python;
   `pip install laspy lazrs` is allowed): fetch the QL2 LAZ tile(s) covering the parcel from
   TNM (dataset "Lidar Point Cloud (LPC)"), gitignored cache. Derive, for the 90m square:
   - **Canopy height model**: first-return surface minus the DEM ground, 1m grid.
   - **Trees**: crown local-maxima on the CHM → per-tree (position, height, crown radius);
     cluster to a sane count (a wooded acre is dozens of crowns, not thousands).
   - **Structures**: class-6 points → footprint rectangles/polygons + eave height. If class 6
     is empty here, say so — absence of buildings on an REO woodlot is a finding, not a bug.
   - **Water**: class-9 points (and/or NAIP darkness) → pond polygon + surface elevation.
   Write one `features.json` beside the compiled parcel (gitignored), with provenance.
2. **Ingest**: features become CAPTURE-written entity rows in the log (tree = trunk cylinder
   + canopy ellipsoid per D-007; structure = footprint + height; pond = water polygon).
   Extend the compiled-parcel ingestion path; nothing lives outside the log.
3. **Render**: entity meshes through the existing painter — cylinder/ellipsoid tessellation
   for trees (two greens: trunk brown, canopy from the NAIP pixel under it), prism for
   structures, flat water surface for the pond. Ground keeps NAIP albedo. Trees SHADE the
   ground already via the occluder rasterisation (D-015) — verify canopy entities enter it.
4. **Numeric gates** (eyes): tree count within ±30% of CHM crown maxima; rendered canopy
   cover fraction at overhead within ±0.15 of the CHM cover fraction; orbit skyline
   roughness strictly greater than the bare-terrain baseline; water region renders where the
   pond polygon says; all 175 existing tests green.
5. **First contact sheet** for the lead's cycle protocol (below): seven poses + a
   walk-height pose INSIDE the woods and one ON the road corridor.

## The lead's visual cycle protocol (D-019 — committed BEFORE cycle 1)
After merge, the LEAD iterates: render → score → adjust → re-render. Scorecard, each
dimension 0 (absent/wrong) · 1 (present but off) · 2 (reads true), scored per cycle in the
seat file:
- S1 trees stand vertically and read as trees at walk height (not cones, not lollipop grid)
- S2 overhead canopy pattern matches NAIP (woods where woods are, field stays open)
- S3 the road corridor is clear of trees and reads as a road
- S4 pond sits where imagery says, reads as water
- S5 orbit skyline is canopy-rough, not table-flat
- S6 heights are believable (mature canopy 15–25 m, not 3 m shrubs or 60 m towers)
- S7 the whole frame, cold: "is this that parcel?" — the lead's honest eye
Stop when the total plateaus over two consecutive cycles AND S7 = 2. Pass band for the RUN:
S7 = 2 with no dimension at 0, numeric gates green, full suite green.

## Bands (worker's, pre-committed)
- **PASS**: features extracted from real lidar with receipts (point counts, class histogram,
  tree count, CHM stats), entity rows land through the log, entities render as geometry,
  numeric gates green, first contact sheet delivered, 175 existing tests green.
- **WEAK**: trees land but structures/pond unresolved (or vice versa) — name it; or gates
  advisory pending lead cycles.
- **FAIL**: verticality faked (terrain exaggeration, draped imagery), entities bypass the
  log, or suite breaks.

## Rails
- Branch `build/true-3d` in worktree `../.git-worktrees/pt-veg`
  (from repo root: `git worktree add ../.git-worktrees/pt-veg -b build/true-3d`).
- **Your cwd resets between Bash calls**: every git command through
  `bash tools/wt.sh <abs-worktree> <git-args>` (it refuses the irreversible tail); every
  build `bash <abs-worktree>/gradlew -p <abs-worktree> <task>`. Never bare git/gradle.
- Modules: `capture`, `worldstate` (entity row kinds if a new one is genuinely needed —
  prefer existing entity machinery), `render`, `eyes`, `app`. Point cloud + rasters stay in
  gitignored `capture/data/`; committed artifacts are code, small fixtures, receipt PNGs.
- Sources: USGS 3DEP lidar + NAIP ONLY. No Google anything; the county imagery service is
  NOT ratified. HTTP via python scripts, GET only.
- **Commit a checkpoint at every meaningful step, even broken — say WIP.**
- Foreground everything; no pushes, no merges; never weaken a check.
- Report: git diff --stat via wt.sh, verbatim gate output (counts from TEST-*.xml), the
  extraction receipts, contact-sheet path, contradictions, questions for the lead.
