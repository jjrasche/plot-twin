# Charter 26 — one sheet Jim rules three questions from, in five minutes

## Where it fits
Run 8 made the render honest: the drawn ground IS the property line, the county's line stands as a
kerb, a neutral surround carries the horizon. What is left is not correctness — it is taste, and
taste is Jim's, not the lead's. He has asked for a taste session tonight and named exactly three
questions. Your job is to make those three answerable **by eye, in five minutes, from one sheet.**

This is the first deliverable in this repo whose audience is Jim rather than a gate. Build it that
way: the sheet is the product, and a beautiful render that does not let him compare loses to a
plain one that does.

## The three questions, and what a good option set looks like
1. **The surround treatment — 2 to 3 options.** Today it reads as pale mist; nobody would call it
   farmland. It is one constant (`SURROUND_BASE_HAZE = 0.45`); lowering it buys land presence and
   pays in ring banding and palette margin. Give him the honest ends of that trade plus a middle:
   something that reads as neighbouring FARMLAND, something that reads as today's MIST, and a
   middle if one exists. Each option must still satisfy what the surround is for — visibly
   not-mine, never mistakable for the property, no competition with the parcel.
2. **The sun disk, tight vs soft.** Two options, same pose, same time of day.
3. **The owner's pose, 15–20° off the parcel's long axis.** Charter 22 measured that a truly axial
   view fills only 0.28–0.37 of the frame and cannot pass the coverage bound at any distance, so
   the owner's walk has to come off-axis. Show it. This is the pose that answers "what does my land
   look like when I stand at one end and look down it" — the one an owner actually wants.

## What makes this sheet good rather than pretty
- **One sheet.** Not three sheets, not a folder. He rules all three in one sitting.
- **Every panel labelled with what it IS and what it COSTS** — "surround: farmland (ring banding
  visible at overhead)" beats "option A". He is ruling on a trade; show him the trade.
- **Hold everything else constant within a question.** Same pose, same sun, same parcel across the
  surround options, or he is comparing two things at once and the comparison is worthless.
- **Group by question**, so the three decisions read as three rows and not as nine unrelated
  pictures.
- Say plainly, in the report, which option YOU would pick and why. He is entitled to your eye; he
  is not obliged to take it.

## Also in this lane, because it is the same file (Jim ruled it)
`SKYLINE_COVERAGE_BOUND` is used two ways in one file: a FAIL bound in `the_real_parcel_renders…`
and a skip-this-viewpoint FILTER in `the_dem_predicted_skyline_agrees…`. **Jim ruled: it is a FAIL
bound. Kill the filter use.** Both lanes that met it reached the same conclusion independently, and
the reasoning is that a filter keyed on the same constant silently DISCARDS viewpoints when the
bound is tightened rather than failing them. Nothing is currently filtered (all poses sit at
0.697–0.880), so the change should be behaviour-preserving today — say so with the numbers, or say
what moved and why. If removing the filter leaves that test asserting nothing meaningful, give it
the assertion it should have had, and name what it now proves.

## Bands (pre-committed)
- **PASS**: one labelled sheet, three questions grouped, each option's cost named, everything else
  held constant within a question; the coverage filter-use gone with the numbers showing what
  moved; your own recommendation stated per question; full gate green (counts from `TEST-*.xml`).
- **WEAK**: the sheet lands but an option set is thin (e.g. no honest farmland option exists
  without breaking the surround's job) — say so with the measurement rather than shipping a
  cosmetic third option to make three.
- **FAIL**: options differ in more than the one variable under test; the sheet needs a second
  artifact to interpret; the coverage bound itself is changed rather than its filter use; or the
  gate breaks.

## Rails
- Branch `build/taste-sheet` in worktree `../.git-worktrees/pt-taste`
  (`git worktree add ../.git-worktrees/pt-taste -b build/taste-sheet main`).
- `capture/data/` is gitignored: copy `dem/`, `lidar/`, `naip/`, `compiled/`, `boundary/` and
  `geocode.json` from `C:/Users/rasche_j/Documents/workspace/plot-twin/capture/data/` — the main
  checkout now holds the CORRECT regenerated cache for the real extent. Do not write into it.
- **Your cwd resets between Bash calls.** Absolute paths everywhere. Every git command through
  `bash tools/wt.sh C:/Users/rasche_j/Documents/workspace/.git-worktrees/pt-taste <args>`.
  **Never push.**
- **Commit a checkpoint at every meaningful step even if it does not build** — WIP in the message.
- No `cd X && …`, no `&&` chains, no `git -C`, no multi-line write commands. One command per call,
  single line, matching an allowlist prefix.
- Render batches run in the FOREGROUND with `timeout 600000`; re-issue the same command if killed
  at the cap. Never a background monitor — your background children die with you.
- Do not touch CLAUDE.md, README.md, DECISIONS.md, TASKS.md or research/ — another lane owns the
  record tonight. Do not touch the shadow-direction check or the caster population.
- Run the FULL gate, never a module cut.
- Report: `git diff --stat`, verbatim gate output, test counts from `TEST-*.xml`, the ABSOLUTE PATH
  of the sheet, the per-panel labels, your recommendation per question, the coverage-filter
  numbers, contradictions, questions.
