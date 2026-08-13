# plot-twin

A decade-maintainable digital twin of a plot of land, first parcel Isaac's 1.839 acres at
11157 W Jolly Rd, Delta Twp, MI — a 31 × 241 m strip by county record, not a square. 10+ spaces
("rooms" — garden terraces, compost network, greenhouse, pergola, shop) co-edited by Jim and AI.
Proves that AI can build and verify 3D spaces through tight symbolic edit cycles.

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
- **Terrain**: 10cm grid over the property line's bounding box — 919,220 cells (380 × 2419) for
  the first parcel; the figure is approximate scale, not a budget. 2.5D heightmap + per-entity
  heights. Entities are exact vectors (polygons), not cells.
- **Renderer v1**: factored-ui `scene3d` (Kotlin/Skiko — already has meshes, camera, headless
  render tests). three.js is the fallback only if terrain perf tanks. State is renderer-agnostic.
- **Standalone now, Genesis-shaped**: typed rows + append-only log + derived views so the port to
  agent-platform-genesis is writing rows into its log. Not built inside Genesis (no dependency).

## Order of build

Loop first, capture later: toy state + 2 solvers (clearance, D8 flow) + walkable render proves the
cycle before photos→state ingestion exists.

## Vision

One living model per plot of land, held for a decade. The owner speaks intent; the LLM
compiles it to typed rules and ops; solvers verify; an optimizer places; the log remembers
every change and why. The owner walks the result — on screen now, through a phone on the
actual ground later — and what they see is always derived from state that machines can check.
The twin is where a long build gets designed, argued with, and kept honest: every structure
placed before it's bought, every constraint (drainage, sun, access, permit) enforced before a
shovel moves. First plot: Isaac's 1.839 acres on W Jolly Rd. Next: the common-ground corridor
parcels — the design tool that turns "which land" (common-ground's job) into "what grows here."
Success = a plot's next decade of decisions each ran through the loop first.

## Rules

- Never cite decision/question IDs at Jim — he doesn't author them; say the thing itself.
- No quick fixes: features may be sliced thin, but every slice lands on the locked architecture (log rows, pure solvers, LLM altitude) — never around it.
- Docs are extremely concise.
- Standards: `~/.claude/references/coding-standards.md`.

## Governance

This repo runs `rationale` governance (`.rationale` marker; see that skill). Stores:
README (orientation) · DECISIONS.md (choices + kill reasons) · research/RESEARCH.md (live
conclusions) · TASKS.md (the one work list). Nothing outside the taxonomy. Data spine:
common-ground ingests/selects land data; this repo's land solvers PULL its constraint
layers — never build duplicate collectors here.
