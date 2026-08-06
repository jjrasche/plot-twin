# CHARTER — headed GPU fps measurement (factored-ui, grades the batched painter)

## Where it fits
Repo: factored-ui, worktree factored-ui-build-scene3d (branch build/scene3d-batched-painter @
33d37b4). The batched painter landed WEAK headless (17.6fps @ 100K); the remaining ~52ms/frame
is Skia CPU raster fill, which only a GPU-backed surface removes. This measurement decides
PASS vs keep-WEAK for the rebuild. factored-ui owns the GPU surface; plot-twin only sends specs.

## Deliverable
A headed spike: a real Compose Desktop window (GPU-backed Skiko surface, default renderer)
rendering the same synthetic rolling-hills meshes (10K / 50K / 100K triangles) through the
batched painter, camera orbiting per frame, fps measured over ≥100 frames after warmup.
Committed as a runnable main (spike source, excluded from the test floor) + numbers in the
report. Screenshot receipt of the window content.

## Verify
Fresh build; the fps harness runs headed; existing desktop tests stay green (the spike must
not touch painter code — measurement only; if a painter change is needed, STOP and report).

## Bands
PASS: ≥30fps @ 100K headed. WEAK: 10–30fps headed (report GPU vs CPU split if obtainable).
FAIL: headed ≤ headless (means the fill wasn't the term — the model was wrong; report, don't fix).

## Rails
Worktree only, no pushes, no version bump. A window will open on the workstation — expected.
Report: fps table headed vs headless, verbatim harness output, screenshot path, contradictions.
