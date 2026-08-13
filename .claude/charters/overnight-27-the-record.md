# Charter 27 — the record stops describing a plot that does not exist

## Where it fits
The ledger self-audit found that this repo's constitution describes the wrong parcel, and Jim has
ruled the corrections. Every item below is HIS ruling, already made — you are executing, not
deciding. Where you find a figure the audit did not name, chase it too: the instruction is to
follow the stale premise wherever it went, not to fix a list of four lines.

## 1. The constitution is restated to the real parcel
`CLAUDE.md` opens *"A decade-maintainable digital twin of Jim's 2-acre plot"*; `README.md` says
*"First plot: a 2-acre home parcel (Delta Twp, MI)"*. Both are wrong twice over, and D-020
(2026-08-10) already ruled the half nobody propagated: **the first parcel is Isaac's**, 11157 W
Jolly Rd, Delta Twp — public record. Run 8 measured the rest: **1.839 acres** by county record
(7443.1 m²; the ring measures 7445.4), a **30.9 m × 241 m strip**, not a square and not two acres.

Restate both. Keep the project's ambition intact — this is still a decade-maintainable twin, still
10+ spaces, still the corridor parcels next — but the founding parcel is named correctly and sized
correctly.

## 2. Chase every figure that descends from the stale premise
The audit's sharpest line: *"every acreage and cell-count figure in the ledger descends from that."*
Known descendants, and you must sweep for more:
- **"~810K cells for 2 acres"** in `CLAUDE.md`, and anywhere else it appears. The real extent is
  **919,220 cells** (380 × 2419 at 10cm — the ring's bbox, which is 37.9 m wide because the strip
  is a skewed parallelogram). The lead has already ruled this: **the budget line was approximate
  and descended from the stale premise, so it is the premise that was wrong, not the extent.**
  D-015 measured one sunshed sweep at 20 ms over 810K cells, so ~23 ms here — not a limit.
  Restate the figure as the real one; do NOT present 919K as an overrun against a real budget.
- **D-015's own "900x900 toy plot (810,000 cells)"** — that one is TRUE and stays: it is a
  measurement of the toy plot, which really is 900×900. Do not "correct" a correct historical
  measurement. This is the discrimination the whole charter turns on: restate claims about the
  REAL PARCEL; leave measurements of the TOY PLOT alone.
- **`README.md`'s "build charters staged; no code yet"** and its stage map item 1 — false against
  233 tests, a walkable parcel and 27 charters. Retire wholesale.
- Sweep `CLAUDE.md`, `README.md`, `DECISIONS.md`, `TASKS.md` and `research/RESEARCH.md` for any
  other "2 acres", "square", "90m", "810K" or "900x900" claim about the real parcel and correct
  each. Report the full list you found, including any you decided to leave and why.

## 3. Ledger retirements (Jim approved both)
- **Retire D-014 into D-018, preserving the factored-ui clause.** D-014 ruled verified-gate branches
  land by the lead's call but *"factored-ui merges remain the owner's"*; D-018 later ruled the lead
  merges gate-green work with Jim pulled in only for money, going public, or his devices. Read
  literally, D-018 frees what D-014 reserved. Fold D-014's live sentence into D-018 as a fourth
  exception and mark D-014 retired with the reason and the date. **A retired entry is not deleted**
  — the ledger keeps its history; it is marked retired and says what superseded it.
- **Correct the research record against D-016.** `research/RESEARCH.md` Q-004 still names the
  **Hosek-Wilkie dome** as its conclusion; D-016 rejected it (its fitted radiance tables would have
  to be hand-transcribed, which is unverifiable) and shipped an analytic altitude gradient. While
  you are there, Q-004a still calls canvas-native texture draping affordable, where run 5 measured
  that `Scene3dMesh` exposes **no texCoords** in kotlin-compose 0.19.0 — the blocker lives only in
  the seat file, so the research record still recommends a path the code cannot take. Record it.

## 4. Log the future-vision task Jim named
Add to `TASKS.md` under Later, as a NAMED future direction and explicitly not now:
**pure peer-to-peer mesh sync between engines, no hub.** Jim's reason for logging it is the whole
point and must survive in the text: *so that hub-by-omission never silently becomes permanent.*
Today's architecture has no hub because nothing needed one yet; that is an accident, and an
accident that goes unnamed becomes a decision nobody made. One entry, concise, naming the risk.

## Bands (pre-committed)
- **PASS**: both docs describe Isaac's real parcel with the right acreage and shape; every stale
  descendant figure found, corrected or consciously kept, and the full list reported; D-014 marked
  retired into D-018 with the factored-ui clause preserved as an exception; Q-004's Hosek-Wilkie
  recommendation and Q-004a's draping claim corrected against what shipped; the P2P mesh entry
  logged with its reason; full gate green (counts from `TEST-*.xml`).
- **WEAK**: the prose lands but you found a stale figure you could not resolve without a ruling —
  name it precisely rather than guessing.
- **FAIL**: a correct historical measurement of the TOY plot is "corrected"; a retired entry is
  deleted rather than marked; or the constitution's ambition is trimmed while fixing its facts.

## Rails
- Branch `build/the-record` in worktree `../.git-worktrees/pt-record`
  (`git worktree add ../.git-worktrees/pt-record -b build/the-record main`).
- **Your cwd resets between Bash calls.** Absolute paths everywhere. Every git command through
  `bash tools/wt.sh C:/Users/rasche_j/Documents/workspace/.git-worktrees/pt-record <args>`.
  **Never push.**
- **Commit a checkpoint at every meaningful step** — WIP in the message.
- No `cd X && …`, no `&&` chains, no `git -C`, no multi-line write commands. One command per call.
- **Touch ONLY** `CLAUDE.md`, `README.md`, `DECISIONS.md`, `TASKS.md`, `research/RESEARCH.md`. Two
  other lanes are live in `eyes/`, `render/` and the capture scripts tonight — stay out of them.
  `CLAUDE.md` must stay under 80 lines per the project template.
- This repo runs `rationale` governance: nothing outside the taxonomy, docs extremely concise.
- Run the FULL gate anyway — prose changes can break a doc-lint or a test that reads a doc.
- Report: `git diff --stat`, verbatim gate output, the full list of stale figures found and what
  you did with each, contradictions, questions.
