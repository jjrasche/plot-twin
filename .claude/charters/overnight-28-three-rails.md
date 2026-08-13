# Charter 28 — the instrument proves it launched, the judged artifact is the measured one, and a receipt cannot be re-taken

## Where it fits
Jim ruled three rails binding on any project with a visual verification surface, each learned from a
failure that actually happened in common-ground in the last 24 hours. A read-only audit put
plot-twin against all three and returned **FAIL, FAIL, FAIL**, with file-and-line evidence the lead
re-verified by hand. This charter closes them. Nothing here is a judgement call about whether the
rails apply — they do, and the evidence is below.

**Do not start until charter 26 (taste sheet) has merged**; it owns `eyes/` tonight and you will
both edit `RealParcelContactSheetTest.kt`. The lead will release you.

## Rail 1 — proven launched. Absence must FAIL, never assume.
`capture/data/` is gitignored by a nested `capture/data/.gitignore` containing `*`, so a fresh clone
has no cache at all. Three tests then skip, and a fourth silently halves itself:

- `capture/src/test/kotlin/plottwin/capture/CompiledParcelCacheGateTest.kt:27` — the **only** test
  asserting the 10 cm grid is the property line's bbox and that its mask measures the acreage.
- `capture/src/test/kotlin/plottwin/capture/GeocodeStageGateTest.kt:37` — the 30 m site-agreement gate.
- `eyes/src/test/kotlin/plottwin/eyes/RealParcelContactSheetTest.kt:71` — the **only** test that
  renders the artifact a human has ever judged by eye.
- `eyes/src/test/kotlin/plottwin/eyes/CasterPopulationMeasurementTest.kt:23` — the worse one, because
  it is not a skip: `if (Files.exists(compiled)) report(...) else emptyList()`. The full-res arm
  evaporates, `realArm` stays non-empty from the 1 m fixture, and the test reports **PASS**. The
  claim that the frozen caster floor separates toy from woodlot on the real full-res parcel is then
  measured on half the population it names, and the JUnit XML shows green either way.

**Close it:** absence becomes a loud failure naming the regeneration command. Either turn the three
assumptions into assertions and make the caster measurement unconditional, or — if you judge the
8.5 MB cache genuinely untrackable — one `CaptureCachePresentTest` that fails once, loudly, with the
command to fix it, so ONE red test says the instrument never launched instead of three invisible
skips. Take the second only if you can argue it; say which you took and why.

**Also add the thing that would have caught this class:** the gate must be able to state that it
ran. A skip count of zero is currently established by a human reading numbers by hand and doing it
inconsistently across runs (run 5 reported 1 skip, run 8 reported 0). Make it mechanical.

## Rail 2 — the served artifact. This is the one the lead called worse than common-ground's.
Every eyes test that ALWAYS runs reads the tracked 1 m fixture (`RealParcelFixture.kt:6`,
`/real_parcel_1m.json`, 38×242 cells). Exactly one test reads the 10 cm compiled artifact, behind
the Rail-1 assumption. Every S1–S7 score in the run-8 D-019 ledger judged the 10 cm render.

Verified by hash, by the lead:
```
91a414c563c9fe334cbe4f76defc1ae9  capture/receipts/real_parcel_contact_sheet.png   (tracked)
6ce0c4ca89bc4ae46d83fc5b3d15d201  eyes/build/real_parcel_contact_sheet.png         (1 m fixture)
91a414c563c9fe334cbe4f76defc1ae9  eyes/build/real_parcel_full_res_contact_sheet.png (10 cm)
```
The tracked receipt is byte-identical to the **full-res** sheet while carrying the **fixture**
sheet's filename. Both are 1952×892 so the mislabel is invisible. **The lead did this, tonight,
copying the full-res sheet to the receipt path — it is the lead's error, not a lane's, and it is
recorded so the fix is not mistaken for someone else's cleanup.** Net effect: a reader who clones,
runs green, and opens the receipt sees an image their run provably could not have produced.

And the two artifacts can drift silently. `compile_parcel.py` writes both in one pass, so they are
co-generated *when run whole* — but nothing enforces it, no test compares them, and their
provenance blocks ALREADY disagree numerically (fixture `elevation_min 262.3048037719603`, compiled
`262.2637700653011`). Neither carries a content hash, though `fetch_parcel_boundary.py:175` proves
the project knows how.

**Close it:** `compile_parcel.py` writes a sha256 of the 10 cm parcel bytes plus
columns/rows/cell_size/elevation min+max into the **tracked** 1 m fixture's provenance, and the
full-res gate asserts it — so the fixture and the compiled artifact cannot disagree without a red
test. Rename the tracked receipt to say which artifact it is. Investigate the provenance
disagreement and report whether it is benign resampling or evidence they were generated from
different runs; do not paper over it.

## Rail 3 — a receipt that can be re-taken is not a receipt.
One tracked image path, overwritten four times (`4ff6c1c`, `3d9062f`, `4bc555d`, `54177b1`). Every
other sheet an eye has ever scored goes to `*/build/`, gitignored and wiped by `clean`.

- **Run 6's entire visual acceptance is unrecoverable** — no receipt commit exists between 08-08 and
  08-10 23:14. Worse, while that verdict stood the tracked receipt still held run 5's image, a
  render of the WRONG parcel 15.6 km away in Eaton Rapids. For two days the repo's only receipt
  depicted land that is not the subject of the verdict above it.
- Runs 7 and 8 keep only their FINAL sheet; every intermediate cycle image in both D-019 ledgers is
  gone, including the ones carrying run 8's most specific claims.
- This has already cost us: tonight's retraction had to reopen run 7 and concluded *"the order of
  magnitude is 5-10 real crowns, but that is an estimate and no run-7 number can settle it."* The
  images would have settled it.

**Close it:** a judged sheet is written to a run-and-cycle-stamped path a later run cannot address,
and every sheet an eye scores is committed — not only the last. If tracked-PNG weight is a real
objection, argue it with numbers and take the cheap half instead: the sha256 of each judged sheet
goes into its D-019 ledger line at the moment the score is written, so a lost image is at least
detectable rather than silently substituted. State which you took.

## One stale descendant handed to you from the record lane
`solvers/src/testFixtures/kotlin/plottwin/solvers/ToyPlotFixture.kt:32` carries the comment
`// the home 2 acres: Delta Township, Eaton County, Michigan` on the TOY fixture's site row. It is
wrong twice — the toy plot is a synthetic 90x90 m square, and the real parcel is Isaac's 1.839-acre
strip. The record lane found it and correctly left it, because it is code and outside that lane's
rails. Fix the comment to say what the toy fixture actually is. Do not change the fixture's numbers:
the toy plot is legitimately 900x900 and D-015's measurement of it stands.

## Bands (pre-committed)
- **PASS**: a fresh clone with no `capture/data/` fails LOUDLY and says how to fix itself (prove it —
  move the cache aside, run the gate, paste the output, restore it); the caster measurement no longer
  halves silently; fixture and compiled artifact are bound by a hash a test checks; the tracked
  receipt names the artifact it actually is; judged sheets land at addresses a later run cannot
  overwrite; full gate green with the cache present (counts from `TEST-*.xml`).
- **WEAK**: rails 1 and 2 close but rail 3's storage question needs Jim's call on repo weight — name
  the numbers.
- **FAIL**: absence still passes; the fixture/compiled binding is asserted but never able to fail
  (prove it CAN fail by mutating one and watching red); or the receipt rename happens without the
  hash binding, which would leave a correctly-named artifact that still cannot be verified.

## Rails
- Branch `build/three-rails` in worktree `../.git-worktrees/pt-rails` off main AFTER charter 26 merges.
- **Your cwd resets between Bash calls.** Absolute paths. Git through
  `bash tools/wt.sh C:/Users/rasche_j/Documents/workspace/.git-worktrees/pt-rails <args>`. **Never push.**
- Checkpoint commits even when broken. One command per Bash call, single line, no `&&`, no `git -C`.
- Copy the capture cache in from the main checkout; do not write into it.
- Every claim in your report carries file:line or a hash. This charter exists because a green gate
  lied; do not close it with a green gate nobody re-ran.
- Report: `git diff --stat`, verbatim gate output, test counts from `TEST-*.xml`, the verbatim output
  of the cache-absent run, the mutation proof that the hash binding can fail, which option you took
  on rails 1 and 3 and why, contradictions, questions.
