# TASKS

## Now

- Walk Isaac's parcel in true 3D (97 lidar trees, road, real light):
  `bash gradlew :app:run --args="C:/Users/rasche_j/Documents/workspace/plot-twin/capture/data/compiled/parcel.json"`;
  see a stage diff: `bash gradlew :app:run --args="--stage-diff berm"`
- Seam: Q-013 accepted with four contract asks + eaton-delta-twp sequencing ask (board:
  seam-pushback-for-cg-head.md); ingest work starts only after Jim ratifies cg's D-009
- Shadow-direction check self-suppresses in a many-caster forest — the one red banner left

## Next

- Stage-diff follow-ons from the lane: per-op movement links for multi-op stages;
  `--stage-diff` accepting a persisted log path; optional deepest-cut/highest-fill feet line
- Derived stage-dependency projection (stages coupled via shared entities/surfaces) as a
  consistency check on authored predecessors — Q-008's one cheap, DSM-independent idea

- Dome into the render projection proper: today it joins at scene composition in eyes, which
  the app happens to reuse — a render-module concern living one module too high
- Shadow-direction check should self-suppress (advisory) when the plot has no principal
  shadow caster — bare terrain renders carry meaningless red readings
- Spoil placement as optimizer search: v1 berms go where the op names them; scoring by
  viewshed+watershed per Q-005 is unbuilt. Terrace/swale/berm-as-primary-form still reject.
- Aerial-perspective fog on the ground: needs a per-frame renderer hook in scene3d, since
  baked per-triangle colour is camera-independent and one spec serves seven poses
- Orbit-4 reads the plot's principal shadow 31 degrees off, because the swale trench
  out-darkens the greenhouse along that bearing; the estimator models one occluder
- Top-down 2D projection view with violation overlays
- Real rooms replace toy fixture
- Violation markers are drawn at a fixed world size and swamp the pergola at walk height;
  marker scale probably wants to follow camera distance
- The sunshed solver has never run on the toy plot's own rules — its end-to-end coverage is a
  purpose-built yard, because adding a sun rule meant inventing a threshold nobody measured
- Entity faces light by normal and assume nothing shadows them; true for the toy, false the
  moment two structures stand close

## Later

- Impoundment/spillway hydraulic routing — the fifth solver family Q-005 predicts the first
  time a pond is real
- Optimizer v1: CP-SAT coarse + local-search fine (Q-001 design)
- Capture agent: $0 aerial-first experiment, then photos-as-evidence loop
- AR walkthrough (WebXR/ARCore; GPS + reference-point registration)
- Cinematic beauty-pass skin
- Water-flow visual: particles advected along D8 arrows
- Adaptive grid (quadtree) if uniform 10cm becomes a limit
