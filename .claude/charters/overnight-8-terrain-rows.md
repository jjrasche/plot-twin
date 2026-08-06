# CHARTER — terrain into the log: base-terrain + terrain-diff rows (D-011)

## Where it fits
Owner ruled: nothing lives outside the log. Terrain currently rides beside the projection as
an in-memory grid. This charter makes terrain log rows and the grid a projection of them.
Chains AFTER the provenance charter (both touch worldstate row vocabulary; sequential, same lane).

## Deliverable
- Row types: base-terrain (initial ground, written by the capture writer role) + terrain-diff
  (cell-region height changes — a regrade or swale is a small diff, never a new blob).
- Projection: terrain grid derived from base + diffs; solvers consume the projected grid
  (drop the side-channel terrain field from the solver world input).
- Resolution decision: BEFORE implementing, read the terrain decision and the two research
  memos (Q-001 optimizer coarse-to-fine, Q-002 LiDAR) for the adaptive-grid discussion; decide
  fixed 10cm vs adaptive cells and record the choice + reasoning as a DECISIONS.md amendment
  candidate in the report (do not edit DECISIONS.md — the lead ratifies).
- Ingestion seam only (Genesis adapter/operator shape): define the operator interface that
  compiles raw elevation data into base-terrain rows; a stub operator feeds the toy fixture
  through it. No LiDAR download, no daemon — the seam, proven by the fixture.

## Verify
All gates green on fresh full rebuild (worldstate/solvers/oppipeline/render if present).
New tests: base+diff replay → identical grid; a diff over the swale region changes only its
cells; solver results identical to the pre-change side-channel grid on the toy fixture;
storage size of the toy base row reported (log must stay liftable — report the bytes).

## Bands
PASS: green, terrain fully log-derived, toy base row size reported. WEAK: green but grid still
partially side-channel (name where). FAIL: solvers read terrain from anywhere but the projection.

## Rails
Same worktree/branch as the provenance charter (build/provenance), committed after its work is
green — one lane, two charters. No pushes. Report shape as always.
