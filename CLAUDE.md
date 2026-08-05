# plot-twin

A decade-maintainable digital twin of Jim's 2-acre plot: 10+ spaces ("rooms" — garden terraces,
compost network, greenhouse, pergola, shop) co-edited by Jim and AI. Proves that AI can build and
verify 3D spaces through tight symbolic edit cycles.

## Architecture (locked)

- **World state is the artifact.** Event-sourced: append-only log of typed diffs; current state is
  a projection. Renderers and LLMs both read state; neither owns truth.
- **The leash**: deterministic upstream (state + solvers), generative downstream (looks).
- **Solvers are pure functions** `f(world_state, constraint) -> [violations]`, violations typed
  with location + magnitude + rule. ~4 families: geometry queries (distance/clearance), propagation
  kernels (viewshed/sunshed/audioshed), grid accumulation (D8 water flow), land/regulatory checks.
- **LLM altitude**: outer loop only. Compiles intent → typed constraints, proposes room-level ops
  and constraint relaxations, interprets violation text. Never computes geometry, never inner-loop
  positioning — solvers measure, optimizers place.
- **Terrain**: 10cm grid (~810K cells for 2 acres), 2.5D heightmap + per-entity heights.
  Entities are exact vectors (polygons), not cells.
- **Renderer v1**: factored-ui `scene3d` (Kotlin/Skiko — already has meshes, camera, headless
  render tests). three.js is the fallback only if terrain perf tanks. State is renderer-agnostic.
- **Standalone now, Genesis-shaped**: typed rows + append-only log + derived views so the port to
  agent-platform-genesis is writing rows into its log. Not built inside Genesis (no dependency).

## Order of build

Loop first, capture later: toy state + 2 solvers (clearance, D8 flow) + walkable render proves the
cycle before photos→state ingestion exists.

## Rules

- Decisions live in DECISIONS.md, one line each, dated.
- Docs are extremely concise. Future features are one line in docs/future.md.
- Standards: `~/.claude/references/coding-standards.md`.
