# Charter 17 — the stage-diff becomes communication

## Where it fits
Jim, 2026-08-09 (ratifying run 5 and chartering this): "Plot Twin's purpose is letting people
see the dream and find their own work in the delta between now and next. Build the smallest
version: current measured surface vs one proposed stage, rendered as a visual diff a
non-technical person reads at a glance — what changes, where material moves, roughly how much
work. Use the eyes to gate it."

Everything upstream exists on main @ HEAD: surfaces (`measured` vs `proposed(<name>)`,
D-013 as amended), regrade resolution emitting proposed-surface terrain diffs plus an
earthwork row, the earthwork ledger projection, `terrainOn(surface)`, the walkable painter,
and the eyes gate machinery (contact sheets, sky classifier, checks). This charter renders
what the log already knows. The reader is a NON-TECHNICAL person — a neighbor, a spouse, a
contractor — not the owner-operator. Every design choice bends toward glanceability.

## Deliverable
A stage-diff view: given a world state holding a measured surface and one proposed surface,
produce a single image (and the projection behind it) that answers three questions at a
glance:

1. **What changes** — top-down view of the plot; unchanged ground stays quiet (muted albedo/
   grey); cells the proposal cuts render in one ramp, cells it fills in another (magnitude →
   intensity). The ramp choice must survive colour-blindness: use a red/blue pair, not
   red/green.
2. **Where material moves** — a visible link from cut region to fill region (arrow or band
   whose weight scales with volume); hauled-off material gets an explicit edge-of-plot arrow
   labeled with its volume. Numbers come FROM THE LEDGER, never recomputed in the view.
3. **Roughly how much work** — a legend strip in plain language and owner units (cubic yards,
   feet): dug, placed, hauled, and one comparative anchor (e.g. dump-truck loads at ~10 yd³
   bank per truck — cite the figure you use in a code comment or the legend itself).

Shape constraints (the locked architecture):
- The view is a PROJECTION: pure function of (state, measured surface, proposed surface) →
  spec/image. It owns no truth, computes no volumes (reads the ledger), and lives in
  `:render` beside the walkable projection. Painting goes through the existing painter /
  BufferedImage path so it renders headless.
- The three intent fixtures from charter 16 (dig / foundation / berm) are the test scenes.
  The berm intent is the star: cut region, adjacent fill region, zero haul — the diff must
  show the material's short journey.
- An `:app` entry (flag or second window is fine, simplest wins) so Jim can open the diff for
  a stage; do not fork the render path.

Eyes gating (the charter's own bands live here):
- A diff-region check: the set of pixels the view paints as cut/fill must agree with the
  terrain-diff cells the proposal actually wrote (project the diff extents through the same
  camera; IoU ≥ 0.9 per region).
- A conservation-legibility check: legend numbers parsed back out of the render (or asserted
  at the projection layer) equal the ledger's numbers exactly.
- A quiet-ground check: outside diff regions + link graphics, the view must not differ from
  the plain top-down base render by more than an epsilon share of pixels — the diff may not
  invent noise.
- Contact sheet: the three intents × the diff view, written to `eyes/build/`, reviewed by the
  lead's eye at verify time.

## Tests that could fail
- Each of the three checks above, per intent fixture, calling public API and rendering
  headless.
- The projection is deterministic: same log → identical image bytes on rerun.
- Full `bash gradlew -p <worktree> test` green — the 168-test suite survives.

## Bands (pre-committed)
- **PASS**: all three intents render a gated diff; all three checks green; legend equals
  ledger; full suite green; contact sheet legible to the lead's eye AS a non-technical
  glance (the lead will look at it cold and name what changes/moves/how much before reading
  your report).
- **WEAK**: diff + legend land but the material-movement link is missing or unlabeled, or
  only some intents gate — name the gap.
- **FAIL**: view computes its own volumes, gates weakened, or suite breaks.

## Rails
- Branch `build/stage-diff` in worktree `../.git-worktrees/pt-stage-diff` (from repo root:
  `git worktree add ../.git-worktrees/pt-stage-diff -b build/stage-diff`).
- Modules you own: `render`, `eyes`, `app`. Read `worldstate`/`oppipeline`/`solvers` freely;
  if a projection helper genuinely belongs in `worldstate`, STOP and report first.
- **Your cwd resets between Bash calls (proven last run).** Use `git -C <abs-worktree>` for
  every git command and `bash <abs-worktree>/gradlew -p <abs-worktree> <task>` for every
  build. Single-line, non-compound commands only. Never run bare git/gradle — it would hit
  the shared main checkout.
- **Commit a checkpoint at every meaningful step, even if it does not build — say WIP.**
- No pushes, no merges, foreground builds only, never weaken a check to pass it.
- Report shape: `git -C <worktree> diff --stat main...`, verbatim gate output with counts
  from TEST-*.xml, the three per-intent check numbers, contact-sheet path, contradictions,
  questions for the lead.
