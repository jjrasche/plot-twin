# CHARTER — world-state schema + append-only log (roadmap 1)

## Where it fits
First code in the repo. Everything downstream (solvers, ops, renderer) reads or writes these
rows. Architecture is locked in CLAUDE.md + docs/architecture.drawio — build ON it, never
around it.

## Deliverable
Kotlin module (Genesis-shaped: typed rows, append-only log, projections as derived views,
SQLite). Row types: entity (vector footprint + height, named), rule (hardness: hard|soft,
weight), lock/mask, op (the vocabulary: add_room · move · resize · reroute · relax · lock —
slot-filled, no coordinates), position-diff (optimizer-only writer). Projection: current-state.
Units: meters internal, feet/inches at every surface.

## Verify (the gate)
Tests that could fail: append op row → projection reflects it; replay log from zero →
identical projection; a rule append triggers re-solve hook (hook may be a no-op callback for
now); geometry rows rejected unless written by the optimizer role. Gate = full rebuild + test
run, not cached.

## Bands
PASS: all gates green, replay deterministic. WEAK: green but any projection computed
imperatively rather than derived (name it). FAIL: schema requires renderer or solver knowledge
to compile (coupling violation).

## Rails
Branch `build/schema` off main in an isolated worktree. No pushes. Commit small. Report:
diff --stat, gate output verbatim, contradictions with DECISIONS.md, questions.
