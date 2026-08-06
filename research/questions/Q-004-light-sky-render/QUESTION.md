---
id: Q-004
status: answered
trigger: owner
spawned-by: D-008
feeds: D-008
---

# What are the current (2025-2026) light/sky and capture-render techniques for our Skia/CPU-vertex pipeline?

Jim, 2026-08-06: "light and sky is a good one… what can you learn that isn't in your training
data about bleeding edge techniques that makes what we're building more capable and robust."

Asked of a web-research worker (receipts required, 2025-2026 sources prioritized):
1. Terrain lighting with no fragment shaders: directional sun, horizon-map self-shadowing state
   of practice, soft shadows bakeable into vertex colors.
2. Cheap sky/atmosphere: current analytic sky models; sun-position accuracy that actually
   matters for shadow realism.
3. Gaussian splatting: phone-video capture tooling, terrain scale, splat→mesh — splat as CAPTURE
   feeding typed state vs splat-as-renderer against the Skia constraint.
4. Wildcard: anything 2025-2026 a Jan-2026 cutoff would miss.
