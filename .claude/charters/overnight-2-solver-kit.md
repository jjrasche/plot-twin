# CHARTER — solver kit v1: runner + clearance + D8 (roadmap 2)

## Where it fits
Depends on charter 1's row types (compile-time only — build against its branch). The solvers
are the twin's eyes; violations are the LLM's food and the renderer's overlays.

## Deliverable
- Runner: fan-out over registered leaf solvers, aggregate, rank by severity. Orchestrator, no
  domain logic.
- Leaf 1 — clearance sweep: min distance along a path polyline vs a rule's bound; violation
  carries location (point of pinch), magnitude (shortfall), rule id.
- Leaf 2 — D8 flow accumulation over the 10cm terrain grid; waterlog violation = accumulation
  above a rule's threshold inside an entity footprint.
- Toy fixture: hand-built 2-acre state — terrain with one swale, greenhouse + pergola + one
  path (the ratified toy rooms).

## Verify
Tests that could fail: known pinch-point fixture → exact shortfall; flat terrain → zero
accumulation everywhere; single-basin fixture → all flow exits one cell; violations sort
stable and deterministic. Full rebuild gate.

## Bands
PASS: all green, both leaves pure (no I/O, no state mutation). WEAK: green with impurity
(name it). FAIL: solver needs renderer or LLM types to compile.

## Rails
Branch `build/solvers` in isolated worktree. No pushes. Same report shape as charter 1.
