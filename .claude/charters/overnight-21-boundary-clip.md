# Charter 21 — the grid becomes the parcel

## Where it fits
Charter 20 landed the real property line as a log row (branch merged before you start — verify
it is on `main` in your worktree's history, and read its receipt before you write anything).
Your job is the kill: `compile_parcel.py` stops cutting a 90m square and starts cutting the
parcel.

What the square actually was: `CELLS_PER_SIDE = 900` at `CELL_SIZE_METERS = 0.1`, centred on
the geocoded address point. The real parcel is a deep narrow strip about 31 m east-west by
241 m north-south, and the address point sits in the road right-of-way at its SOUTH end. So the
old crop held a sliver of Isaac's land and a great deal of his neighbours'. Charter 20 reports
the exact overlap fractions; quote them, do not re-derive them.

## The extent rule (lead-ruled, so you do not have to guess)
**The grid stays a rectangle; the parcel arrives as a mask.** D-011 rules fixed 10cm cells and
`TerrainGrid` is rectangular — an irregular-extent grid is a different architecture and is not
tonight's work. So:

- Grid extent = the **bounding box of the boundary polygon** in EPSG:26916, snapped outward to
  whole 10cm cells. Roughly 312 × 2412 cells ≈ 753K cells — the same order as the 810K
  CLAUDE.md budgets for two acres, so this is in budget, but measure and report the real count.
- A per-cell **inside-the-boundary mask** rides alongside the heights and albedo, in the same
  base64 style the existing arrays use. Cells outside the property line exist in the grid and
  are marked not-ours.
- Reversal path if the lead or Jim wants a true irregular extent instead: one commit, because
  the mask is additive and nothing downstream loses information.

## The frame problem — read this before you change one line of the compiler
Charter 20 found the hole and the lead has ruled it, because it is the one way this charter
silently corrupts the log. Every `GroundPoint` in the log — the 97 trees, the road, the base
terrain — is metres against an origin that existed **only inside `compile_parcel.py`**
(`site_utm` minus 45 m, the old square's south-west corner). D-021 gave the boundary row an
explicit `GroundFrame` (CRS + origin easting/northing); no other row has one. **So if you recut
the grid, the origin moves and every existing entity row silently relocates** — the trees would
keep their numbers and change their meaning, which is the worst failure this repo can have.

Lead ruling, two parts:
1. **The frame becomes shared state that rows are checked against, not a constant in a script.**
   Rows carrying plot-local coordinates are valid only in one frame per log, and **the projection
   REJECTS a log whose rows disagree on frame** — fail loud, at read time, with a test that
   proves it fires. A frame mismatch must be impossible to read past.
2. **The new origin is the boundary bbox's south-west corner**, and the whole capture is
   re-derived into it in one pass, so the log is internally consistent rather than migrated.
   Do not write a coordinate-shifting migration for rows you are about to regenerate from source.

Reversal if the lead or Jim wants the frame on the site row instead (D-021 names that path):
one field move plus the fixture, no data loss.

## Getting the cached inputs into your worktree
`capture/data/` is gitignored, so your fresh worktree has none of it. The DEM and the lidar tile
are immutable cached downloads — copy them in from the main checkout at
`C:/Users/rasche_j/Documents/workspace/plot-twin/capture/data/` (`dem/` ~479 MB, `lidar/` ~20 MB,
`geocode.json`). Do NOT copy `naip/` — it is the old square's clip and you are re-fetching it.
Do NOT write into the main checkout's data directory; it is shared and another lane reads it.

## Two things the ledger audit found for you (lead-ruled, so you do not re-derive them)
- **What a terrain-diff means on a cell outside the property line.** D-011 rules terrain-diff
  semantics as "rectangular patch, last write wins" and is silent on masked cells, because no
  mask existed when it was written. Lead ruling: **a diff that writes to an outside-the-line cell
  is rejected by the writer, with a typed violation naming the cell** — you cannot regrade your
  neighbour's land, and silently accepting the write would make the mask advisory decoration.
  Add the check where terrain-diff rows are written and a test that proves it fires. Reversal if
  Jim wants outside-the-line edits (a shared drive, a drain easement): one commit, because the
  rejection is a guard and not a schema change.
- **`capture/src/test/kotlin/plottwin/capture/CompiledParcelGateTest.kt:16` pins 42.6006,
  −84.6547** — a point run 5 itself flagged as reverse-locating to Eaton Rapids Twp, ~15.6 km
  south of the parcel. It is in your lane's blast radius. Repoint it at the real parcel, or say
  in your report why it must stay wrong.

## Deliverable
1. **`compile_parcel.py` cuts to the boundary.** Extent from the boundary row, not from
   `CELLS_PER_SIDE`. The fixed constants for the real parcel go away; the 1m fixture cut
   follows the same bbox at 1m resolution (its name encodes the old shape — rename it and
   update every reference, or state why not).
2. **The imagery has to cover the new extent.** The cached NAIP clip is exactly the old 90m
   square (`capture/data/naip/naip_clip.tif`, bounds 694976.7–695066.7 E, 4728335.3–4728425.3 N
   at 0.1m). The parcel runs ~241 m north — most of it has no imagery cached. Re-fetch NAIP over
   the boundary bbox with a small margin via the existing `fetch_naip.py` (network GET, allowed);
   assert the raster covers the full bbox rather than silently clamping at the edge, because
   `bilinear_sample` clips indices and would smear the last pixel northward for 200 m. The 3DEP
   DEM tile already covers 10 km × 10 km, so elevation is fine — verify, don't assume.
3. **Features clip to the property line.** `extract_features.py`'s trees, road corridor and any
   structures/water are filtered to inside-the-boundary (a tree whose trunk is on the neighbour's
   side is not on this plot). Re-derive over the new extent — the woodlot north of the old square
   was never sampled, so expect the tree count to change substantially, and report it against the
   CHM crown maxima over the new extent, not against run 7's 97.
4. **Downstream assumptions get corrected, not silenced.**
   `eyes/src/test/kotlin/plottwin/eyes/RealParcelContactSheetTest.kt:64` asserts
   `terrain.columns == 900 && terrain.rows == 900`. It becomes an assertion derived from the
   boundary receipt (the grid is the boundary's bbox at 10cm), never a new hardcoded pair of
   numbers — a second magic constant is the same bug with a different value. Sweep for other
   square/900 assumptions and fix each the same way.
5. **Numeric gates**: cell count matches bbox ÷ 0.1 within one cell per axis; mask-true cell
   count × 0.01 m² agrees with the polygon area within 1%; every retained tree lies inside the
   polygon; the NAIP raster's bounds contain the bbox; full gate green.

## Bands (pre-committed)
- **PASS**: the compiled parcel's extent IS the boundary bbox with a mask that measures to the
  county's acreage within 1%, NAIP re-fetched and proven to cover it, features re-derived and
  clipped with counts reported, the 900×900 assertion replaced by a derived one, full gate green
  (test counts from `TEST-*.xml`).
- **WEAK**: extent and mask land but imagery or feature re-derivation is incomplete — name
  exactly which cells lack real imagery and how many, with numbers.
- **FAIL**: the mask is faked from the bbox, imagery is stretched or clamped to cover the gap,
  the mask lives outside the log, or an assertion is deleted rather than derived.

## Rails
- Branch `build/boundary-clip` in worktree `../.git-worktrees/pt-clip`
  (`git worktree add ../.git-worktrees/pt-clip -b build/boundary-clip`).
- **Your cwd resets between Bash calls.** Absolute paths everywhere. Every git command through
  `bash tools/wt.sh C:/Users/rasche_j/Documents/workspace/.git-worktrees/pt-clip <args>`.
  **Never push.**
- **Commit a checkpoint at every meaningful step even if it does not build** — WIP in the message.
- No `cd X && …`, no `&&` chains, no `git -C`, no multi-line write commands. One command per
  call, single line, matching an allowlist prefix.
- A re-fetch or a lidar re-derivation is a long run: FOREGROUND with `timeout 600000`, and when
  the call is killed at the cap, re-issue the same command. Make the job resumable with
  completion markers first. Never spawn a background monitor and stop — your background children
  die with you and report to a lead holding none of your context.
- Do not touch the render path or the contact sheet — charter 22 owns those. Do not touch the
  shadow-direction check — charter 23 owns it.
- Run the FULL gate, never a module cut.
- Report: `git diff --stat`, verbatim gate output, every number above, contradictions, questions.
