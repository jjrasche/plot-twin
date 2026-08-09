# Charter 16 — earthworks command layer: regrade, the ledger, conserved spoil

## Where it fits
Priority three of the 2026-08-08 run, Jim verbatim: "Higher-level intents — dig here,
foundation this footprint, berm the spoil adjacent — that modify the twin and conserve moved
material (cut-fill balance). This is the seed of the before/after state diff. Research 4D BIM
conventions for staging representation and adopt what's standard rather than inventing."

The design is DONE and sourced: read research/questions/Q-005-earthworks-design/ANSWER.md
first — this charter implements its sketch, it does not re-derive it. Jim's instruction
ratifies the direction (one regrade verb, cut/fill as ledger projection, spoil conserved with
haul-off as the named escape, one terrain row family with a surface id, purpose selects a rule
bundle). Two existing rulings are amended as Q-005 requires — writer role derives from surface
(tightens D-013), solvers take `(date, surface)` (extends D-005) — the lead documents both for
Jim's morning review.

## Deliverable
Implement the Q-005 "Proposed row/op sketch" on the locked architecture (log rows, op waist,
pure solvers, projections):

1. **`regrade` op verb** through the existing slot machinery — slots: subject · ground-form
   (pond · terrace · swale · pad · berm · grade) · extent-text · destination ·
   spoil-destination (named region or `haul-off`; absent ⇒ solved). No coordinates in the op.
2. **Surface id on terrain rows**: `measured` | `proposed(<name>)`; proposed rows carry the
   measured baseline sequence they branched from. Writer role derives from surface. A
   `surface_realized` row retires a proposal once capture confirms it.
3. **Terrain projection parameterized by surface**; solvers take `(date, surface)` with no
   default — mechanical signature change across the solver kit.
4. **Resolution**: a regrade op resolves (v1 resolver may be deliberately simple — e.g. the
   extent comes from the subject entity's footprint; form sets target elevations by its
   simplest rule) into terrain-diff rows on a proposed surface + one **`earthwork` row**:
   bank-cut · compacted-fill · loose-spoil-placed · haul-off · topsoil terms · shrink/swell
   factors + provenance (`assumed` for v1, values cited from Q-005's ranges).
5. **Conservation invariant, hard, checked at op resolution**: bank cut × (1+swell) = loose
   placed + hauled off. A resolution that does not close is rejected with a violation naming
   the imbalance magnitude.
6. **Earthwork ledger projection** over the log (per op, per plot); an `earthwork-balance`
   rule reads the ledger and emits violations. Cost stays out of violations.
7. **The three intents as end-to-end tests**: "dig here" (pond/grade on a named region),
   "foundation this footprint" (pad on an entity footprint), "berm the spoil adjacent" (spoil
   destination = berm region next to the dig). Each: op appends → resolution → diffs on a
   proposed surface → ledger balances → before/after queryable (elevation at a cell differs
   between `measured` and the proposal; diff volume equals the ledger's bank cut).
8. **4D BIM staging research** — the one open design question. Research what the industry
   standard actually is for representing construction staging/sequencing (IFC's construction
   scheduling entities, 4D BIM task-to-element linkage, as-planned vs as-built) with primary
   sources, and write the conclusion as research/questions/Q-007-staging-4d-bim/
   (QUESTION.md + ANSWER.md, Q-005's format: recommendation + strongest-case-against, every
   claim cited). Adopt, don't invent: the answer must say precisely which convention maps onto
   ops/surfaces/realization rows and what, if anything, the schema is missing. Code for
   staging only if the answer makes it a pure row-type addition; otherwise flag for Jim.

## Tests that could fail
- Regrade op with a coordinate anywhere in its slots is rejected at the waist.
- Solver called without a surface does not compile / does not run.
- Conservation: a hand-built unbalanced resolution is rejected; the balanced one passes; the
  haul-off escape closes an otherwise-unplaceable dig.
- Before/after: proposal surface differs from measured exactly where the diffs say, nowhere
  else; replay determinism holds for the whole log including the new rows.
- The three intent tests above, calling public API, querying real projections.
- Full `bash gradlew test` green — existing 122+ tests survive the `(date, surface)` change.

## Bands (pre-committed)
- **PASS**: all three intents green end-to-end, conservation invariant demonstrably rejecting
  imbalance, ledger projection tested, Q-007 written with primary sources, full suite green.
- **WEAK**: rows + ledger + conservation land but an intent path is incomplete (e.g. berm
  placement stubbed to a named region only) — name it; or Q-007 lands thin.
- **FAIL**: schema breaks replay, conservation soft-ruled instead of hard, or gates weakened.

## Rails
- Branch `build/earthworks` in worktree `../.git-worktrees/pt-earthworks`
  (`git worktree add ../.git-worktrees/pt-earthworks -b build/earthworks`).
- Modules you own tonight: `worldstate`, `oppipeline`, `solvers`, `geometry`. Do NOT touch
  `eyes/`, `render/`, `app/`, or `capture/` — other lanes hold them. If a signature change
  forces a mechanical fix in a held module, STOP and report; the lead sequences merges.
- **Commit a checkpoint at every meaningful step, even if it does not build — say WIP.**
- No pushes, no merges. Foreground builds only — background tasks die with you.
- Command shape: no `cd X && …`, no `git -C`, no multi-line commands, no compound chains.
  `cd` is its own call. Gradle is `bash gradlew <task>`.
- Research HTTP is GET-only; use WebFetch/WebSearch tools, never curl POST.
- The optimizer does NOT gain spoil-placement search tonight — placement solving is Later;
  v1 spoil goes where the op names it or to haul-off. Say so in the report.
- Report shape: `git diff --stat`, verbatim gate output (counts from TEST-*.xml), the ledger
  numbers from the three intent tests, the two ruling amendments as written, contradictions,
  questions.
