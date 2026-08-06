# CHARTER — eyes: camera-drive capability (factored-ui) + view harness (plot-twin)

## Where it fits
Jim ruled (2026-08-06): the render must be verifiable by LOOKING, and the LLM's eyes work the
same way — viewpoints as questions, images as answers. Split: factored-ui owns the capability
(drive the camera programmatically, capture the frame, headless — the same camera path a
user's mouse produces); plot-twin owns the harness (named viewpoints → poses → PNGs).
Multi-user walking is OUT of scope (noted direction, not tonight).

## Deliverable
- factored-ui (worktree off il-scene-render-harness @ 8936e5d, branch build/camera-drive):
  public API to set/animate a Scene3dView camera pose (position, target, fov or the existing
  orbit params) and capture the rendered frame headless. Must reuse the existing camera state
  the mouse handlers mutate — one camera, two drivers. Version stays 0.18.0 (no bump, no
  publish; publishToMavenLocal as 0.18.1-eyes-SNAPSHOT for the plot-twin half).
- plot-twin (branch build/eyes): `eyes` module — `viewToy(pose) -> PNG` plus named poses
  (overhead, walk-height-at-<entity>, orbit-N-steps) generating a CONTACT SHEET (grid of
  labeled renders) for the toy plot.

## Verify
- factored-ui: existing desktop tests + SpecVisualCheck floor green on fresh build; new test:
  camera set to a known pose → captured frame differs from default pose and matches a
  committed-hash-free sanity check (non-blank, expected horizon direction).
- plot-twin: fresh full gate all modules; contact sheet PNG generated for ≥6 poses of the toy
  plot. The LEAD reviews the contact sheet by eye — that review is part of the gate.

## Bands
PASS: green both repos + contact sheet legible (entities identifiable at walk height).
WEAK: green but any pose renders blank/garbled (name which). FAIL: capability requires a headed
window (breaks headless floor) or plot-twin drives Compose internals directly.

## Rails
Isolated worktrees both repos. No pushes, no CDN publish. Sequential inside the lane:
factored-ui capability first, local-publish, then plot-twin harness. Report shape as always +
the contact sheet path.
