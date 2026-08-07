# TASKS

## Now

- Run overnight charters (.claude/charters/): 1 world-state schema · 2 solver kit · 3 op
  pipeline · 4 scene3d batched-painter rebuild (factored-ui)

## Next

- Teach the eyes skyline check what sky is, so the sky dome can join the gated spec — today
  it reads the topmost non-background pixel, which a dome makes sky in every column
- Aerial-perspective fog on the ground: needs a per-frame renderer hook in scene3d, since
  baked per-triangle colour is camera-independent and one spec serves seven poses
- Orbit-4 reads the plot's principal shadow 31 degrees off, because the swale trench
  out-darkens the greenhouse along that bearing; the estimator models one occluder
- Top-down 2D projection view with violation overlays
- Real terrain ingest: QL2 LiDAR / USGS DEM for the parcel (Q-002 pipeline)
- Real rooms replace toy fixture

## Later

- Optimizer v1: CP-SAT coarse + local-search fine (Q-001 design)
- Capture agent: $0 aerial-first experiment, then photos-as-evidence loop
- AR walkthrough (WebXR/ARCore; GPS + reference-point registration)
- Cinematic beauty-pass skin
- Water-flow visual: particles advected along D8 arrows
- Adaptive grid (quadtree) if uniform 10cm becomes a limit
