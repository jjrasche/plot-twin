# CHARTER — walkable toy plot: projection → scene3d spec → free camera (roadmap "loop first" proof)

## Where it fits
plot-twin main (44dd4e0) has worldstate + solvers + oppipeline and the 810K-cell toy plot
fixture. factored-ui's batched painter renders heightfield terrain. This charter closes the
walk: the owner moves inside the 3D toy plot. Renderer reads state, draws, owns no truth.

## Deliverable
- A `render` module in plot-twin: pure function projection (+ terrain grid) → scene3d spec
  (factored-ui's spec JSON: terrain mesh with grid_cells_x/z, entity meshes for greenhouse /
  pergola / path, violation overlays at location+magnitude as colored markers).
- A desktop app main that opens the scene with a free/first-person camera (factored-ui's
  existing camera controls; do not build camera physics — whatever scene3d already supports).
- Dependency: publish kotlin-compose from the factored-ui-build-scene3d worktree to mavenLocal
  with a `-batched-SNAPSHOT` style version (never to the CDN/gh-pages); plot-twin depends on
  that local version, pinned exactly.

## Verify
- Spec-generation tests: toy fixture → spec with expected mesh counts, terrain dimensions,
  violation markers for the known pergola-waterlog + path-pinch. Full fresh gate, all modules.
- Headless render receipt: the spec rendered through factored-ui headless (same harness as the
  fps spikes) → PNG showing terrain + entities; verify by eye, commit path in report.

## Bands
PASS: gates green, receipt PNG shows the toy plot with entities and violation markers, app main
launches and camera moves. WEAK: green but any renderer knowledge leaks into worldstate/solvers
(name it). FAIL: render module writes to the log or computes placement.

## Rails
Branch `build/walkable` off main in a fresh worktree. Depends on charter-5 lane's worktree for
the local publish — sequence after it. No pushes. Report: diff --stat, gate output, receipt
paths, contradictions, questions.
