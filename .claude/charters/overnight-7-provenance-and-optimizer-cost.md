# CHARTER — op provenance rows + optimizer cost + shared geometry (hardening the inner loop)

## Where it fits
plot-twin main (44dd4e0). Three worker-flagged gaps: placements don't cite their op; D8 flow
recomputes 810K cells per candidate; point-in-polygon exists twice. Owner ruled: provenance
follows Genesis — READ FIRST how agent-platform-genesis's log does references (reference
columns between rows) at C:\Users\rasche_j\Documents\workspace\agent-platform-genesis — study
its store/log schema before designing; mirror its reference shape, don't invent one.

## Deliverable
- Provenance: position-diff rows carry the seq of the op that caused them; consumed ops get a
  typed op-status (resolved/rejected with reference to the resolution row). Pending-ops
  projection shrinks accordingly. Intent → position chain fully queryable from the log.
- Optimizer cost: memoization seam in solvers so the terrain-only D8 flow field computes once
  per terrain version, not per candidate (pure caching — leaves stay pure functions; cache
  keyed on terrain identity, lives in the runner or a solver-owned derivation cache).
  Measure: op-pipeline gate test wall time before/after (was 5.6s placement / 11.4s replay).
- Shared geometry: extract point-in-polygon + point-to-segment into one internal geometry
  module used by solvers and oppipeline (solver math, NOT factored-ui — factored-ui only draws).
- Writer roles: add the capture writer role for measured geometry (base for the terrain lane).

## Verify
All existing gates stay green (fresh full rebuild, XML counts all modules). New tests: op-seq
on position-diff after placement; op-status row after resolve and after reject; replay
determinism unchanged; memoized and unmemoized flow fields identical on the toy fixture.

## Bands
PASS: green + measured optimizer speedup reported. WEAK: green but cache leaks impurity into
a leaf (name it). FAIL: provenance shape contradicts Genesis's reference pattern without a
stated reason, or any non-optimizer/capture writer appends geometry.

## Rails
Branch `build/provenance` off main in a fresh worktree. No pushes. Genesis is READ-ONLY —
never modify it. Report: diff --stat, gate output, timing numbers, the Genesis reference shape
you found (short), contradictions, questions.
