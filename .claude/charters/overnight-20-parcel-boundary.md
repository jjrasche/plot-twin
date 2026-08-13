# Charter 20 — the true property line enters the log

## Where it fits
Jim, 2026-08-12: he ratified common-ground's D-009 (the shared parcel-layer seam) and told
this run to consume it. The 90m square dies tonight.

The seam's founding parcel is not contract-covered. `common-ground/data/regions/regions.json`
carries 108 baked townships across Kent/Ottawa/Newaygo/Montcalm/Muskegon; there is no
`data/eaton-delta-twp/parcels.geojson`. The exact sequencing ask plot-twin sent in
`seam-pushback-for-cg-head.md` is still open. So Jim's own fallback applies verbatim: pull the
boundary from Eaton County GIS as the INTERIM, with the seam as the standing path. The lead
has messaged cg-head; that coordination is not your lane.

Nothing in the pipeline has ever held a property line. `compile_parcel.py` cuts a fixed
900×900 cells at 10cm — a 90m square centred on the geocoded address point. Your job is the
boundary itself: pull it, prove it, land it as a log row. Charter 21 clips the grid to it.

## The source (lead-ruled, GET-only, no new dependency)
Eaton County's open ArcGIS parcel layer, no auth, no bot wall:

```
https://services2.arcgis.com/c9l1e4fKpsCnqD7H/arcgis/rest/services/Parcels_AGO/FeatureServer/0/query
  ?where=PARCELID='04003630009000'&outFields=*&returnGeometry=true&outSR=4326&f=json
```

The lead already probed it. It returns ONE feature, a 5-vertex closed ring:
`PARCELID 04003630009000`, `LPARCEL 040-036-300-090-00`, `SITEADDRESS 11157 JOLLY HWY`,
`OWNERNME1 UPDIKE, ISAAC`, `Acreage 1.83922805`, `STATEDAREA 2`. Extent in WGS84:
lon −84.6197636 … −84.6193838, lat 42.6832070 … 42.6853771.

**Write the pull in Python with `urllib`** (`capture/scripts/` is all python; `Bash(python*)`
is allowlisted). Do NOT hand-curl it — a `curl` carrying data flags is flagged write-shaped by
the global hook and prompts, which sleeps the run.

## Deliverable
1. **`capture/scripts/fetch_parcel_boundary.py`** — GET-only, argument is the parcel id,
   writes `capture/data/boundary/boundary.json` (the whole `capture/data/` tree is gitignored)
   plus a receipt. The boundary file carries:
   - the ring in **EPSG:26916 metres** (the CRS every solver already runs in — reproject with
     `pyproj`, same as `compile_parcel.site_utm`), AND the source WGS84 ring unmodified.
   - the **provenance triple plot-twin asked the seam for**, honestly filled:
     `source` (layer URL + `PARCELID`), `pulled_at_utc`, `observed_at` (the county's own
     currency field if the service exposes one — read the layer metadata at `?f=json`; if it
     exposes none, write `observed_at: null` with a `observed_at_absent_reason` string, do not
     silently reuse `pulled_at_utc`), `sha256` of the raw response bytes, and
     `contract: "interim-county-service"` naming that this did NOT come through the D-009 seam.
   - county-stated `acres` and the derived polygon area in m², both.
2. **A typed `parcel_boundary` log row**, written by the CAPTURE writer, ingested through the
   existing compiled-parcel path. Nothing lives outside the log (D-001, D-011): a boundary the
   renderer reads from a side file would be exactly the architecture this repo forbids. Read
   how the `site` row and the D-011 terrain rows do it and follow that shape — polygon vertices
   in metres relative to whatever origin those rows already use, provenance on the row.
3. **Tests** that fail without the implementation: ring closure, OGC validity (no
   self-intersection), area agreement, row round-trips through the log and projects back to an
   identical polygon.

## Findings you are expected to hit (report them, do not "fix" them)
- **The geocoded address point is OUTSIDE the parcel.** `capture/data/geocode.json` says
  42.68317626, −84.61959109; the parcel's south line is at lat 42.6832070 — the point sits
  ~3.4 m south of it, in the Jolly Hwy right-of-way. So the assertion is NOT "site point
  inside the polygon"; it is "site point within 10 m of the boundary". Report the measured
  distance. The boundary defines the extent from now on; the address point is an address.
- **The parcel is a deep narrow strip, not a square** — roughly 31 m east-west by 241 m
  north-south, ~7444 m² (1.84 ac). The 90m square centred on the address point therefore
  overlapped only the southernmost sliver of it. **Measure and report both overlap fractions**
  (parcel area inside the old square ÷ parcel area; and ÷ 8100 m²). That number is the
  headline of why tonight happened.

## Bands (pre-committed)
- **PASS**: boundary pulled with a full receipt (sha256, pulled_at, observed_at-or-reason),
  reprojected to EPSG:26916, OGC-valid, derived area within 1% of the county's 1.83922805 ac
  (7443.6 m²), landed as a `parcel_boundary` log row that round-trips, the two overlap
  fractions and the site-point offset reported as numbers, full gate green (186 tests, 0 fail,
  0 skip — count from `TEST-*.xml`, never from your prose).
- **WEAK**: boundary lands and validates but the log row shape needed a decision you could not
  default-rule from DECISIONS.md — ship the boundary + tests, name the exact fork.
- **FAIL**: the polygon is invented, defaulted, or approximated from the bbox; the renderer or
  compiler is handed the boundary around the log; or the suite breaks.

## Rails
- Branch `build/parcel-boundary` in worktree `../.git-worktrees/pt-boundary`
  (`git worktree add ../.git-worktrees/pt-boundary -b build/parcel-boundary` from the repo root).
- **Your cwd resets between Bash calls.** Absolute paths everywhere. Every git command through
  `bash tools/wt.sh C:/Users/rasche_j/Documents/workspace/.git-worktrees/pt-boundary <args>`.
  **Never push.**
- **Commit a checkpoint at every meaningful step even if it does not build** — say WIP in the
  message. A stalled lane has no hands to save its own work; what is on disk is all the lead
  can recover.
- No `cd X && …`, no `&&` chains, no `git -C`, no multi-line write commands. One command per
  call, single line, matching an allowlist prefix.
- Do not touch `compile_parcel.py`, `extract_features.py`, `fetch_naip.py`, or anything under
  `render/` or `eyes/` — charters 21 and 22 own those. Boundary + its row + its tests only.
- Run the FULL gate (`bash gradlew build` from the worktree root), never a module cut.
- Report: `git diff --stat`, verbatim gate output, the numbers above, contradictions found,
  and any question you could not default-rule.
