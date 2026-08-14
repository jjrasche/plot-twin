# Charter 22 — the render reads as the actual parcel

## Where it fits
Jim looked at the app window on 2026-08-11 and asked whether what he saw was the dimensions of
the parcel. It was not — it was an arbitrary 90m square around an address point. Charter 20
landed the real property line; charter 21 made the grid its bounding box with an
inside-the-boundary mask. Charter 20 and the shadow lane are on main; charter 21 is NOT merged
(it lands red, and the lead does not land red), so you branch off it — see the rails. Verify all
three are in your worktree's history and read their receipts.

Your job is the part Jim will actually judge: **the render has to read as that parcel.** The
pass band for the whole run is his own criterion — the frame reads as the actual property shape.
Nothing you can measure substitutes for that, so build the render honestly and hand the lead a
contact sheet good enough to score by eye. D-019 governs: the lead runs the visual cycles
(render → measure → adjust → re-render), not you.

## The lead has looked at the sheet, and three checks are red. Read this first.
Charter 21 landed the extent and the mask and shipped an honest WEAK: `skyline-coverage` fails at
0.11–0.14 on `overhead`, `orbit-1-of-4` and `orbit-3-of-4` (bound 0.50), and
`orbit_skyline_is_canopy_rough_not_bare_terrain_flat` reads wooded 3.455 against a bare baseline
3.667 over 82 drawn columns. It recommended the fix belongs in the checks, because a 1:6.4 plot
cannot fill a square frame's width from an axis-framed view.

**The lead looked at the contact sheet and rejects that recommendation.** What the sheet shows:
`overhead` reads as the real parcel — a deep narrow wooded ribbon, and that is the run's win.
`walk-height-in-woods` is the strongest frame this project has made. But `orbit-1` and `orbit-3`
show the plot as a **small tuft floating in a large empty sky**, and `orbit-2`/`orbit-4` show it as
a thin sliver near the horizon. Those frames read as a model on a table, not a place. Every orbit
pose was framed for a 90m square with a 127 m diagonal; the parcel is now 242 m on its long axis.

So: **the poses are broken and the coverage check is correctly reporting it.** A check that reads
red because the camera wastes 87% of the frame on sky is doing its job. The shadow-direction lane
landed on main tonight after finding that its own red had been excused by check name in an
assertion while the report printed it red — do not repeat that pattern one charter later.

**Binding: you may not change `SKYLINE_COVERAGE_BOUND`, the coverage formula, or the roughness
comparison in order to make these three pass.** Re-frame until they pass honestly. The roughness
failure is very likely a consequence of the same cause — 82 drawn columns of an end-on sliver is a
degenerate sample, and it should recover on its own once orbit-1 fills the frame. If after honest
re-framing coverage genuinely cannot reach 0.50 on some pose, bring the lead a reasoned proposal
with the measured numbers and the re-framed sheet — the lead will rule, and may well agree that a
coverage denominator of "columns where either the render drew or the prediction predicted" is the
truer measure. That ruling is the lead's, after the framing is honest, not before.

One contradiction charter 21 found that you should surface rather than silently pick a side:
`SKYLINE_COVERAGE_BOUND` is used two ways in one file — as a FAIL bound in
`the_real_parcel_renders...`, and as a skip-this-viewpoint FILTER in
`the_dem_predicted_skyline_agrees...`. Both readings cannot be right. Report which you think is,
with the numbers; do not resolve it by editing the constant.

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
- Branch `build/render-property-line` **off `build/boundary-clip`, NOT off main** — charter 21's
  extent and mask are your input and are deliberately unmerged, because they land red and the lead
  does not land red. `git worktree add ../.git-worktrees/pt-line -b build/render-property-line build/boundary-clip`.
  The two branches merge together once the sheet is green.
- `capture/data/` is gitignored, so your worktree has none of it. Copy `dem/`, `lidar/`, `naip/`,
  `compiled/`, `geocode.json` and `boundary/` in from
  `C:/Users/rasche_j/Documents/workspace/.git-worktrees/pt-clip/capture/data/` — that tree already
  holds the re-fetched NAIP and the re-derived features for the real extent, which the main
  checkout does NOT. Do not write into another worktree's data directory.
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
