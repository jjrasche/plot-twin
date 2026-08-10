# Charter 18 — Design Structure Matrix research for stage ordering

## Where it fits
Jim, 2026-08-09: "Research Design Structure Matrix (Eppinger) for stage-dependency ordering;
write findings as a Q-doc, adopt nothing yet." Context: `StageRow` (run 5, Q-007) carries
member ops, predecessor stage names and optional planned dates, deliberately unconsumed. The
question a scheduler will eventually face is how stages should be ordered and where the
ordering knowledge should live. DSM is the candidate formalism; tonight is understanding, not
adoption.

## Deliverable
`research/questions/Q-008-stage-ordering-dsm/` — QUESTION.md + ANSWER.md in the house format
(Q-005/Q-007 are the models: one-line answer up top, recommendation + strongest-case-against
per question, EVERY claim carrying a primary-source citation; Eppinger & Browning's DSM book,
MIT DSM community material, and peer-reviewed applications outrank blog posts).

Questions the ANSWER must actually answer:
1. What a task-based DSM is, precisely — the matrix semantics (rows/columns as tasks, marks
   as information/precedence dependence), and how sequencing/partitioning works (reordering
   to lower-triangular, identifying coupled blocks/circuits).
2. What DSM adds over the plain predecessor DAG StageRow already has — specifically feedback
   loops / coupled stage clusters (dig ↔ dewater ↔ shore), tearing heuristics, and banding
   for parallelism. If the honest answer for earthworks-scale plots is "nothing yet at n<20
   stages", say so with the argument.
3. How the industry connects DSM to 4D/BIM scheduling if at all — is DSM used upstream of
   CPM/Gantt task networks, and does anything map it onto IFC's IfcRelSequence?
4. What adoption WOULD look like here if it ever earns it — which existing rows carry the
   matrix for free (ops reference entities; stages reference ops; dependencies may be
   derivable from shared entities/surfaces rather than authored), and what, if anything, is
   missing. This is a sketch, not a proposal — mark it clearly as unadopted.
5. The strongest case AGAINST adopting DSM for plot-twin at all, argued honestly.

## Tests that could fail
Docs-only lane, so the gates are editorial and checkable:
- Every non-obvious claim has a citation with a URL; at least two primary sources actually
  read (quote a sentence from each so the lead can spot-check).
- The n<20 question (2) is answered with a position, not hedged.
- Nothing in the ANSWER instructs code changes; the sketch in (4) is marked unadopted.

## Bands (pre-committed)
- **PASS**: Q-008 lands complete on the five questions, cited, with a clear one-line answer
  and a clear non-adoption boundary.
- **WEAK**: fewer than five questions carry real answers, or citations thin out — name which.
- **FAIL**: recommendations smuggle in adoption, or claims float uncited.

## Rails
- Branch `build/dsm-research` in worktree `../.git-worktrees/pt-dsm`
  (`git worktree add ../.git-worktrees/pt-dsm -b build/dsm-research`).
- Touch ONLY `research/questions/Q-008-stage-ordering-dsm/`. No code, no DECISIONS.md edits,
  no TASKS.md edits — the lead lands those.
- **Your cwd resets between Bash calls.** Use `git -C <abs-worktree>` for every git command.
  Research HTTP via WebFetch/WebSearch tools only.
- Commit a checkpoint after each question is drafted, even rough — say WIP.
- No pushes, no merges.
- Report shape: the one-line answer, the five verdicts in a sentence each, source list with
  the two quoted spot-checks, contradictions, questions for the lead.
