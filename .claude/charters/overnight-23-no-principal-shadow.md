# Charter 23 — a many-caster forest has no principal shadow

## Where it fits
The one red banner left on the eyes gate, and Jim named it directly tonight. The
shadow-direction check estimates the plot's principal shadow bearing by modelling ONE
occluder. On the toy plot that is true — a greenhouse and a pergola stand alone and cast a
shadow with a direction. On Isaac's real parcel, 97 lidar trees cast 97 overlapping shadows,
and the estimator picks whichever feature happens to out-darken the rest along some bearing.
Run 7 measured the failure twice: orbit-4 read the principal shadow 31° off because the swale
trench out-darkened the greenhouse, and the check now reads red across most woodlot poses.

A check that is red for a scene it cannot describe is worse than no check — it trains the lead
to ignore a banner. The fix is not a better estimator: it is teaching the check to recognise
scenes where the quantity it measures does not exist, and to say ADVISORY instead of RED.

## Deliverable
1. **A caster-population measurement** on the real scene: for a given sun bearing, how the
   shadow-casting silhouette is distributed across occluders. One caster holding most of it =
   a principal shadow exists. Ninety-seven casters each holding a sliver = there is no such
   thing as "the" shadow direction, and the estimator's answer is noise dressed as a bearing.
2. **A frozen criterion** that separates the two, and self-suppression: when no principal
   caster exists the check reports ADVISORY with the measured distribution stated, and its
   bearing error stops counting as a failure. When one does, the check behaves exactly as it
   does today — the toy plot must keep catching a genuinely wrong bearing.
3. **Tests** that fail without it: the real-parcel scene suppresses; the toy plot does not;
   and a mutation test — deliberately break the toy plot's bearing and confirm the check still
   goes red. A check that can no longer fail is not a check.

## Freeze the criterion against SAMPLED OUTPUT, not against a definition
This is the load-bearing discipline and the one way this charter can quietly cheat. Do NOT
pick a threshold from what "principal" ought to mean and then go count. Measure the
distribution on BOTH arms first — the real 97-tree woodlot across the seven poses, and the toy
plot across its own poses — write down the two sets of numbers, choose the threshold that
separates the measured populations with margin, freeze it, and only then run the check. **A
threshold changed after seeing whether the check passed is a new experiment, not a fix** —
if you move it, say so and restate both distributions.

If the two populations overlap and no threshold separates them, that is a real finding: report
it with the numbers and ship WEAK rather than tuning until it looks clean.

## Bands (pre-committed)
- **PASS**: the criterion is frozen against sampled output from both arms (numbers in the
  report, both distributions), the real parcel's shadow-direction check reads ADVISORY with
  its measured distribution, the toy plot still reads RED on a mutated bearing, all seven
  woodlot poses lose the meaningless red banner, full gate green (186 tests, 0 fail, 0 skip —
  counted from `TEST-*.xml`).
- **WEAK**: the two distributions do not separate cleanly, or suppression works but the
  mutation test shows the check weakened on the toy plot — name it with numbers.
- **FAIL**: the check is disabled, thresholded to always-advisory, or the red is removed by
  loosening the bearing tolerance instead of by recognising the scene.

## Rails
- Branch `build/no-principal-shadow` in worktree `../.git-worktrees/pt-shadow`
  (`git worktree add ../.git-worktrees/pt-shadow -b build/no-principal-shadow`).
- **Your cwd resets between Bash calls.** Absolute paths everywhere. Every git command through
  `bash tools/wt.sh C:/Users/rasche_j/Documents/workspace/.git-worktrees/pt-shadow <args>`.
  **Never push.**
- **Commit a checkpoint at every meaningful step even if it does not build** — WIP in the
  message.
- No `cd X && …`, no `&&` chains, no `git -C`, no multi-line write commands. One command per
  call, single line, matching an allowlist prefix.
- Touch ONLY the shadow-direction check and its tests. Charter 22 owns the render path and the
  contact sheet; charter 21 owns the compiler. If a render change looks necessary, stop and say
  so in the report rather than reaching into another lane's files.
- Run the FULL gate, never a module cut. Any long batch run goes in the FOREGROUND with
  `timeout 600000`; if the call is killed at the cap, re-issue the same command — do not spawn
  a background monitor and stop, because your background children die with you.
- Report: `git diff --stat`, verbatim gate output, both measured distributions, the frozen
  threshold and when it was frozen, the mutation-test result, contradictions, questions.
