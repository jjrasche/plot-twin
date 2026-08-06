# CHARTER — snapshot rows: making replay cheap without making it a lie

## Where it fits
Current state is a projection over an append-only log. That is the locked architecture and it
is not being renegotiated. But a decade-long log replayed from row zero on every read gets
expensive, and terrain rows are the heavy ones. This lane designs the cache and the snapshot
row — and it doubles as a memo to the Genesis lead on change-based lenses, because it is the
same problem in a different repo.

Jim's framing, in his words: a lens is a folded blob living in read memory; ordering is the
conflict rule.

## The questions to dig
- **Patch-folds vs replace-folds.** Some rows amend the last value; some replace it wholesale.
  Does the fold need to know which, or does one shape cover both? What breaks under each.
- **Snapshot rows.** A snapshot is a row that says "the projection at sequence N was this."
  What makes it trustworthy — is it derivable-and-therefore-droppable, or load-bearing? Can two
  snapshots at the same sequence disagree, and what happens if they do?
- **Cache keyed on last-seq.** The terrain projection cache: key, invalidation, and what a
  reader does when a row lands mid-read.
- **Ordering as the conflict rule.** Work out honestly what ordering does and does not settle.
  Concurrent writers, out-of-order arrival, a capture row that contradicts an older proposal.
- **Compaction.** Can old rows ever be dropped, and what is lost when they are? The log
  remembers every change AND WHY — say what compaction costs against that promise.

## Deliverable
- `research/questions/Q-006-snapshot-and-lenses/ANSWER.md` in the repo's existing answer shape:
  each question, the finding, a RECOMMENDATION with its strongest counterargument.
- A memo to the Genesis lead on the board at
  `~/.claude/brothers/messages/change-based-lenses-for-gen-head.md`, headed `# MSG → gen-head`
  / `# FROM pt`. Carry the picture, not an order: where this sits, what it unblocks, what you
  are unsure of, and an explicit invitation to push back from where Genesis sits. Genesis has
  its own log and its own read model — the point is that both repos are solving one problem, not
  that plot-twin has the answer.
- Code ONLY the terrain projection cache if the recommendation is uncontroversial and the
  earthworks lane's row-type proposal does not conflict with it. Check that first. Snapshot rows
  themselves are a schema change — propose, do not land.

## Verify
- If you touched code: fresh full gate,
  `bash gradlew clean build --no-daemon --no-build-cache --rerun-tasks`, verbatim output, and a
  test that could fail — a cache that returns a stale projection after a new row must go red.
- Each question ends in a recommendation, not a menu.

## Bands
PASS: all five questions answered with recommendations + the Genesis memo delivered on the board.
WEAK: answered but the cache recommendation is untested, or the memo reads as an order rather
than a picture with room to push back.
FAIL: a schema change landed without Jim's ratify, or a design that makes the log droppable
without naming what is lost.

## Rails
- Isolated worktree, branch `design/snapshot-rows`. Chains on the earthworks lane — the lead
  will give you the base SHA and tell you what that lane concluded about row types.
- No pushes.
- **Command shape (permission rails — a prompt sleeps the whole run):** no `cd X && …`, no `&&`
  chains, no `git -C`. The cwd persists — `cd` is its own call, then plain single commands.
  Write-verb commands are single-line and non-compound; commit bodies go in a file with
  `git commit -F <file>`.
- Report: `git diff --stat`, verdict against the bands, contradictions, open questions.
