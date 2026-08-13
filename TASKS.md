# TASKS

## Now

- The grid IS the property line's bounding box: 380 × 2419 cells @ 10cm = 919,220 cells, origin
  695000.700 E 4728383.300 N (EPSG:26916), and the mask measures 7444 m² against the county's
  7443.1. The render now draws only inside it — ground silhouette 0.8068 of the bbox against the
  mask's 0.8095 true-share — with the county's line standing as a kerb from the boundary row.
  Every pose is re-framed and `skyline-coverage` is green at all six (0.74–1.00).
- The void under the parcel is closed: a neutral render-side surround, whose hole is the property
  line itself, took below-skyline void from 0.65–0.76 to 0.000 at every orbit pose. Open question
  for Jim, not another cycle: it reads as pale mist rather than farmland, which is one haze
  constant and a question about how his own land should look.
- The road is NOT on Isaac's land. W Jolly Rd's right-of-way lies south of the south line (the
  address point sits 2.961 m south of the frame origin) and the southern rows inside the line
  carry 4.8–10 m of canopy. The old extraction only found a road because the 90m square reached
  45 m south of the address point; the brightest-gray band over a 242 m strip is sunlit treetops
  (CHM 9–16 m), so the detector now also requires bare ground and honestly finds nothing.
- Replace the interim county boundary with a seam-delivered one once common-ground's parcel
  layer covers eaton-delta-twp; the row says `interim-county-service` so the swap is a new row.
- Walk Isaac's parcel in true 3D (99 lidar trees inside the property line, real light):
  `bash gradlew :app:run --args="C:/Users/rasche_j/Documents/workspace/plot-twin/capture/data/compiled/parcel.json"`;
  see a stage diff: `bash gradlew :app:run --args="--stage-diff berm"`
- Seam: Q-013 accepted with four contract asks + eaton-delta-twp sequencing ask (board:
  seam-pushback-for-cg-head.md); ingest work starts only after Jim ratifies cg's D-009 — this
  ALSO unblocks the boundary-polygon fix above, so it's now double-motivated
- Shadow-direction check self-suppresses in a many-caster forest — the one red banner left
- The toy plot's orbit-4 shadow-direction finding is red (30.1 deg against a 20 deg bound) and is
  now PINNED by check and viewpoint rather than dropped by check name: exactly one failure is
  tolerated, so an unrelated shadow regression still goes red and the pin itself fails when the
  cause is fixed. The cause is terrain attribution - the swale trench out-darkens the greenhouse
  and the estimator attributes shade to entities only. Ruled: no minimum-sample floor, because the
  reading is a true failure. Whoever lands terrain attribution deletes the pin.

## Next

- Stage-diff follow-ons from the lane: per-op movement links for multi-op stages;
  `--stage-diff` accepting a persisted log path; optional deepest-cut/highest-fill feet line
- Derived stage-dependency projection (stages coupled via shared entities/surfaces) as a
  consistency check on authored predecessors — Q-008's one cheap, DSM-independent idea

- Dome into the render projection proper: today it joins at scene composition in eyes, which
  the app happens to reuse — a render-module concern living one module too high
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
- Named future direction, explicitly not now: **pure peer-to-peer mesh sync between engines, no
  hub.** Logged so that hub-by-omission never silently becomes permanent — today's architecture
  has no hub only because nothing has needed one, which is an accident, and an unnamed accident
  becomes a decision nobody made.
