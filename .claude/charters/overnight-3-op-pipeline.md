# CHARTER — op pipeline: op rows → optimizer v0 → gated appends (roadmap 3)

## Where it fits
Closes the inner loop: an op row appended to the log reactively triggers placement; solver
verdicts gate the append of exact-position diffs. LLM is OUT of scope here — ops arrive as
rows from a test fixture, not from language.

## Deliverable
- Reactive trigger: op-row append → optimizer invocation (projection + locks + weights in).
- Optimizer v0: dumb candidate generation (grid scan inside the op's region constraint),
  reject hard-rule violators via solver runner, score rest by weighted soft rules,
  deterministic tie-break (nearest existing path, then lowest x,y).
- Gated append: winning placement written as position-diff rows; a fully-violating op appends
  a rejection row (typed, with the violations) — never silence.

## Verify
Tests that could fail: `add_room(greenhouse, region)` on toy state → placement respects
clearance + waterlog rules; locked entity never moves; impossible op → rejection row with
violations; identical log replay → identical placement (determinism).

## Bands
PASS: all green end-to-end on the toy fixture. WEAK: green but optimizer reads anything other
than projection/locks/weights (name it). FAIL: any writer other than optimizer appends
geometry.

## Rails
Branch `build/op-pipeline`, isolated worktree, builds on charters 1+2 branches. No pushes.
Same report shape.
