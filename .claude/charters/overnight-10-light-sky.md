# CHARTER — light & sky: the horizon sweep is the sunshed solver

## Where it fits
Rung one of the ladder Jim was told: light+sky → real ground from LiDAR → first-person walk.
The research is DONE — `research/questions/Q-004-light-sky-render/ANSWER.md` carries the
2025-2026 receipts. This charter BUILDS the answered slice. It chains on the eyes lane: the
contact sheet is how this work gets judged, so land on the eyes branch once it merges.

The load-bearing idea: one CPU line-sweep over the heightfield produces soft shadow AND ambient
occlusion AND *is* the sunshed solver. Do not build a renderer effect and a solver separately —
it is one computation, logged like any solver run.

## Deliverable
- plot-twin `:solvers`: a sunshed solver — horizon-angle line sweep over the terrain grid,
  pure `f(world_state, constraint) -> [violations]` per the locked architecture, outputs typed
  with location + magnitude + rule (e.g. "bed X gets 3.1h direct sun, rule wants 6").
- Sun position from date/time: `klausbrunner/solarpositioning` (Grena3) on the JVM, pinned.
- plot-twin `:render`: the sweep's horizon/AO products become per-vertex shading through the
  existing painter path; sky dome (Hosek-Wilkie, or a defensible gradient v1 — say which and
  why in the receipt); aerial-perspective fog with distance.
- Two checks Jim asked for, answered with measurements, not opinions:
  (a) **SkSL runtime effects** — do per-pixel Skia programs run on the CPU/headless backend at
      usable perf on the drawVertices terrain path? Measure before assuming vertex-color-only.
  (b) **Texture draping** — drawVertices takes texCoords; prove a ground texture + a baked
      lightmap (sweep at finer-than-vertex resolution) renders inside Skia. This is the
      canvas-native smoothing answer if it holds.
- Any factored-ui capability half goes in an isolated worktree off `il-scene-render-harness`,
  publishToMavenLocal only. No version bump, no CDN publish.

## Verify
- Fresh full gate, all modules: `bash gradlew clean build --no-daemon --no-build-cache --rerun-tasks`.
- Sunshed solver has tests that could fail: a wall south of a bed must cut its hours; a flat
  open cell at solstice noon must be near-unshaded; sun azimuth at a known date/lat/lon matches
  a published value (cite it).
- Renders: contact-sheet poses at 3 times of day, visibly different, shadows on the correct side.
- SkSL + texture checks each report a NUMBER (fps or ms/frame at the terrain triangle count on
  the headless backend) and a verdict, not a hunch.

## Bands
PASS: gate green + sunshed solver with falsifiable tests + three-times-of-day renders correct +
both checks answered with numbers.
WEAK: green and rendering, but sunshed is a render effect only (not logged as a solver run), or
a check answered without a measurement — name which.
FAIL: sky/lighting requires a headed window, or shading bypasses the state→projection→spec path.

## Rails
- Isolated worktree, branch `build/light-sky`. Base on the eyes branch if the lead has landed
  it; the lead will tell you the SHA.
- No pushes. No CDN publish. No version bumps.
- **Command shape (permission rails — a prompt sleeps the whole run):** no `cd X && …`, no `&&`
  chains, no `git -C`. The cwd persists — `cd` is its own call, then plain single commands.
  Write-verb commands are single-line and non-compound; put commit bodies in a file and use
  `git commit -F <file>`. Never hand-curl a POST.
- Report: `git diff --stat`, VERBATIM gate output, verdict against the bands above, every
  contradiction you hit with the charter or the repo's rulings, and your open questions.
