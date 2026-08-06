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

## Vision

One living model of the land, held for a decade. Jim speaks intent; the LLM compiles it to
typed rules and ops; solvers verify; an optimizer places; the log remembers every change and
why. Jim walks the result — on screen now, through his phone on the actual ground later — and
what he sees is always derived from state that machines can check. The twin is where the
10-year build gets designed, argued with, and kept honest: every structure placed before it's
bought, every constraint (drainage, sun, access, permit) enforced before a shovel moves.
Success = the plot's next decade of decisions each ran through the loop first.

## Roadmap

1. World-state schema + append-only log (entity · rule · lock · weight · op rows)
2. Toy parcel + solver kit v1 (runner, clearance, D8 flow) — the loop, proven
3. Op pipeline: intent → op rows → optimizer v0 → gated appends
4. Top-down 2D projection view (cheap first truth-view, violations overlaid)
5. scene3d GPU rebuild (pt-research design) → first-person walkthrough
6. Real terrain + real rooms (DEM/survey ingest, the actual parcel)
7. Optimizer v1: LLM-proposed neighborhoods, solver-scored search
8. Capture agent: photos/GPS/satellite → entity rows
9. AR on-site walkthrough; cinematic skin last

## Rules

- Decisions live in DECISIONS.md, one line each, dated.
- Docs are extremely concise. Future features are one line in docs/future.md.
- Standards: `~/.claude/references/coding-standards.md`.

## Pending: adopt rationale governance (Jim-approved direction, 2026-08-05)

Read `~/.claude/skills/rationale/SKILL.md` and run its ADOPT mode on this repo: inventory
every doc, present the full migration table to Jim BEFORE moving anything, migrate on a
branch, lint clean, `.rationale` marker goes on LAST. Notes for the migration: DECISIONS.md
upgrades from one-line entries to the skill's entry standard (option tables, rejected
options keep kill reasons — the locked architecture bullets above are decisions to
retrofit); docs/future.md content likely becomes TASKS.md Later; the "docs are extremely
concise" rule survives — the taxonomy enforces it. Sister repo: workspace/common-ground
(first adopter, reference migration in its git history). Data relationship: common-ground
ingests/selects land data anywhere-wide; this repo's land/regulatory solver family PULLS
constraint layers from it — do not build duplicate collectors here.
