# Charter 22 — the render reads as the actual parcel

## Where it fits
Jim looked at the app window on 2026-08-11 and asked whether what he saw was the dimensions of
the parcel. It was not — it was an arbitrary 90m square around an address point. Charter 20
landed the real property line; charter 21 made the grid its bounding box with an
inside-the-boundary mask. Both are merged before you start; verify that in your worktree's
history and read their receipts.

Your job is the part Jim will actually judge: **the render has to read as that parcel.** The
pass band for the whole run is his own criterion — the frame reads as the actual property shape.
Nothing you can measure substitutes for that, so build the render honestly and hand the lead a
contact sheet good enough to score by eye. D-019 governs: the lead runs the visual cycles
(render → measure → adjust → re-render), not you.

## Deliverable
1. **Outside the line is not this plot.** Cells the mask marks outside are not drawn as ground —
   ruled by the lead: they are omitted, so the rendered ground silhouette IS the parcel shape.
   The deep narrow strip must be unmistakable from overhead. If omission leaves the frame
   floating in void in a way that reads worse than a neutral surround, say so with a measurement
   and propose the alternative; do not quietly re-add the neighbours' land as ground.
2. **The property line is visible as a line** in the render — the boundary polygon drawn as
   geometry, readable at walk height and from orbit. It comes from the `parcel_boundary` log
   row; the renderer reads state, never a side file.
3. **The camera work has to fit a 31 × 241 m strip.** Every existing pose was framed for a 90m
   square: an overhead pose that fits the square either crops 200 m of the parcel or shrinks it
   to a thread. Re-frame the pose set so the overhead pose fits the whole parcel and the walk and
   orbit poses stand somewhere meaningful ON it — and say in your report what each pose now sees.
4. **Contact sheet** regenerated for the lead's cycle protocol, with the poses labelled. The
   lead scores it; you deliver it and state what you believe is still wrong with it.
5. **Numeric gates** that could fail: the rendered ground silhouette's area fraction matches the
   mask's true-fraction; the drawn boundary's extent matches the boundary row's extent; the
   overhead pose's frame contains the whole polygon; trees render only inside the line; the full
   gate green.

## What NOT to do
Do not compensate for a wrong shape with a prettier picture. If the silhouette is wrong the
render is wrong, and no amount of light or colour fixes it — this run's whole point is that the
frame's geometry is honest about whose land it is. Every previous run's visual work (albedo as
reflectance, litter floor, scattered skylight, sunlit surround) stands; do not re-litigate it,
and do not regress it while re-framing.

## Bands (pre-committed)
- **PASS**: outside-the-line ground is not drawn, the property line renders as geometry from the
  log row, poses re-framed so the overhead pose holds the whole parcel, contact sheet delivered
  with per-pose notes, all numeric gates green, full gate green (test counts from `TEST-*.xml`).
- **WEAK**: the silhouette is right but a pose or the boundary line is unresolved — name it, and
  state what the lead's eye should expect to find wrong.
- **FAIL**: the silhouette still reads as a square or a crop; the boundary is drawn from a side
  file instead of the log row; earlier visual work regressed; or the suite breaks.

## Rails
- Branch `build/render-property-line` in worktree `../.git-worktrees/pt-line`
  (`git worktree add ../.git-worktrees/pt-line -b build/render-property-line`).
- **Your cwd resets between Bash calls.** Absolute paths everywhere. Every git command through
  `bash tools/wt.sh C:/Users/rasche_j/Documents/workspace/.git-worktrees/pt-line <args>`.
  **Never push.**
- **Commit a checkpoint at every meaningful step even if it does not build** — WIP in the message.
- No `cd X && …`, no `&&` chains, no `git -C`, no multi-line write commands. One command per
  call, single line, matching an allowlist prefix.
- Render batches run in the FOREGROUND with `timeout 600000`; re-issue the same command if the
  call is killed at the cap. Never a background monitor.
- Do not touch `compile_parcel.py`, `extract_features.py` or the boundary pull — those lanes are
  closed. Do not touch the shadow-direction check.
- Run the FULL gate, never a module cut.
- Report: `git diff --stat`, verbatim gate output, every number above, the contact sheet path,
  per-pose notes, what you think is still wrong, contradictions, questions.
