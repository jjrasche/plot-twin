# Charter 15 — eyes learn sky, and gate real terrain

## Where it fits
Priority two of the 2026-08-08 run: "the renderer must be checked visually, not assumed."
The verification system EXISTS — `eyes/` module: SkylineCheck, SilhouetteIouCheck,
ShadowDirectionCheck, HistogramCheck, ContactSheet, PlotViewer. **Study it before building**
(read every check + its tests, and seat-pt.md's run-3/4 notes on what each check can and
cannot see). This charter extends it; it does not replace it.

## Deliverable
1. **Sky-aware skyline** (TASKS Next, first item): SkylineCheck currently reads the topmost
   non-background pixel, so a sky dome makes every column "sky" and the dome had to stay out
   of the gated spec (D-016). Teach it to classify sky vs terrain (the analytic gradient makes
   sky pixels predictable — hue/gradient continuity, or render an id-pass, your call; state
   the mechanism and its failure modes). Then the dome joins the gated spec.
2. **DEM-predicted skyline for arbitrary terrain**: the skyline predictor must work from any
   heightfield in the log, not just the toy fixture — charter 14 is landing a real parcel and
   its renders must be gateable by the same checks. Parameterize, don't fork.
3. **Fix the two dome defects the lead found by eye** (TASKS Now): concentric fan banding
   where dome triangulation reads through the gradient, and the dark band at the horizon
   between dome edge and ground. These are render-side fixes in the dome mesh/coloring —
   verify by regenerating the light_sky contact sheet and by a check that would have caught
   the banding (e.g. radial color-monotonicity over the dome region), not by eye alone.
4. **Contact-sheet gate for a real parcel**: a test entry that, given a base-terrain row
   (toy today, charter 14's real row when it lands), renders the seven poses and gates
   skyline + histogram + silhouette where applicable. The lead will chain the real row in.

## Tests that could fail
- Sky classifier: on the existing light_sky sheet, sky pixels ≥99% classified sky over the
  dome region, terrain pixels ≥99% terrain along the known skyline — numbers in the receipt.
- Skyline gate with dome present: passes on toy plot at all seven poses.
- Banding check: fails on the current dome rendering (proving it catches the defect), passes
  after the fix.
- Full `bash gradlew test` green — existing 122+ tests must survive.

## Bands (pre-committed)
- **PASS**: dome in the gated spec, both visual defects fixed with a check that catches each,
  skyline predictor heightfield-generic, all green.
- **WEAK**: sky-aware skyline lands but dome defects remain (or vice versa) — name which.
- **FAIL**: gates loosened to pass (a check weakened rather than the render fixed), or
  existing tests break.

## Rails
- Branch `build/eyes-sky` in worktree `../.git-worktrees/pt-eyes-sky`
  (`git worktree add ../.git-worktrees/pt-eyes-sky -b build/eyes-sky`).
- Touch ONLY `eyes/` and `render/` (dome mesh/color lives in render). If a fix genuinely
  needs another module, STOP and report — the lead will rule.
- **Commit a checkpoint at every meaningful step, even broken — say WIP.**
- No pushes, no merges. Foreground builds only.
- Command shape: no `cd X && …`, no `git -C`, no multi-line commands. `cd` is its own call.
  Gradle is `bash gradlew <task>`.
- Never weaken an assertion to make a gate pass; a check that can no longer fail is FAIL.
- Report shape: `git diff --stat`, verbatim gate output (counts from TEST-*.xml), before/after
  contact-sheet paths, the sky-classifier mechanism + failure modes, contradictions, questions.
