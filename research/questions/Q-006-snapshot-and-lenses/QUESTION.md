---
id: Q-006
status: answered
trigger: owner
spawned-by: D-001
feeds: D-001
---

# How does replay stay cheap without the log becoming a lie?

Current state is a projection over an append-only log. A decade-long log replayed from row zero on
every read gets expensive, and terrain rows are the heavy ones. Jim's framing: a lens is a folded
blob living in read memory; ordering is the conflict rule.

1. **Patch-folds vs replace-folds.** Some rows amend the last value, some replace it wholesale.
   Does the fold need to know which, or does one shape cover both? What breaks under each?
2. **Snapshot rows.** A row saying "the projection at sequence N was this" — what makes it
   trustworthy? Derivable-and-droppable, or load-bearing? Can two snapshots at the same sequence
   disagree, and what happens if they do?
3. **Cache keyed on last-seq.** The terrain projection cache: key, invalidation, and what a reader
   does when a row lands mid-read.
4. **Ordering as the conflict rule.** What does ordering actually settle? Concurrent writers,
   out-of-order arrival, a capture row contradicting an older proposal.
5. **Compaction.** Can old rows ever be dropped, and what is lost when they are? The log remembers
   every change AND WHY — say what compaction costs against that promise.

Doubles as a memo to the Genesis lead: same problem, different repo, its own log and read model.
