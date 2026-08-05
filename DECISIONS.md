# Decisions

- 2026-08-05 — World state is event-sourced (append-only typed diffs, projected views). Decade provenance + Genesis portability.
- 2026-08-05 — Loop before capture: prove edit-verify-render cycle on toy state before photos→state.
- 2026-08-05 — Standalone repo, not built inside Genesis; Genesis-shaped data so the port is row-writing.
- 2026-08-05 — LLM operates outer loop only: intent→constraints, room-level ops, relaxations, violation interpretation. No geometry arithmetic (FloorplanQA: accuracy collapses with complexity).
- 2026-08-05 — Inner loop is classical: solvers verify, optimizer places (Formalize-Don't-Optimize, arXiv 2605.12421).
- 2026-08-05 — Terrain 10cm grid, 2.5D heightmap; entities exact vectors with heights. Full voxels rejected.
- 2026-08-05 — Renderer v1 = factored-ui scene3d (exists: Scene3dView/Mesh/WorldState + headless render tests). three.js only as perf fallback.
- 2026-08-05 — Repo public on GitHub.
- 2026-08-05 — One Rule class, `hardness` field: hard (gates) or soft (weighted score). Same rule can flip by source — permit stipulation = hard, taste = soft. Severity is a violation's output; weight is a rule's input.
- 2026-08-05 — Optimizer objective = soft rules with configurable, versioned weights (rows in the log). No randomness: objective decides, deterministic tie-break settles.
- 2026-08-05 — Solvers take a date; sun/shade/deciduous are seasonal functions.
- 2026-08-05 — Landmarks Jim references ("the big tree") become named entity rows; anything referenced twice earns a name.
- 2026-08-05 — Architecture diagram: docs/architecture.drawio (inner/outer loop, op-vocabulary waist, relaxation edge).
