# TASKS

## Now

- The sky dome shows concentric fan banding where its triangulation reads through the
  gradient, and a dark band sits at the horizon between the dome's lower edge and the ground
- Real terrain ingest for the parcel: the accuracy budget is answered, the pipeline is not
  built (Q-002)

## Next

- Teach the eyes skyline check what sky is, so the sky dome can join the gated spec — today
  it reads the topmost non-background pixel, which a dome makes sky in every column
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

- Optimizer v1: CP-SAT coarse + local-search fine (Q-001 design)
- Capture agent: $0 aerial-first experiment, then photos-as-evidence loop
- AR walkthrough (WebXR/ARCore; GPS + reference-point registration)
- Cinematic beauty-pass skin
- Water-flow visual: particles advected along D8 arrows
- Adaptive grid (quadtree) if uniform 10cm becomes a limit
