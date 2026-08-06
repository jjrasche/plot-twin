---
id: Q-006
trigger: owner
spawned-by: D-001
feeds: D-001
---

# Snapshot rows and lenses: making replay cheap without making it a lie

**The whole answer in one line: every row is a replace at a named scope, so the cache never
invalidates — it only lags — and a snapshot is a durable cache entry, never truth.**
Because no row's meaning depends on the rows before it, folding forward from a cached value is
identical to replaying from zero. That single property buys the cache, buys droppable snapshots,
and is what a relative ("lower this by 30cm") row would destroy. Ordering resolves conflicts but
never *detects* them, and sequence is not time. Truth rows are never compactable; derived rows are
compactable by definition.

## Q1 — Patch-fold vs replace-fold: one shape, if rows stay absolute

The two folds are already in the code and they look different. `BaseTerrainRow` ignores the
accumulator and installs a grid. `TerrainDiffRow` requires the accumulator and rewrites a
rectangle inside it. That reads as two shapes — until you look at what a diff actually carries:
absolute heights for the cells it names. It is a *replace* of those cells and a *patch* of the
grid. Same for entities: `PositionDiffRow` replaces one entity wholesale and patches the entity
map. The word "diff" in both row names describes the scope, not the arithmetic.

So the distinction is not a property of the row — it is a property of the (row, granularity) pair,
and it disappears at the right granularity. **One shape covers both: a row names a scope and gives
that scope's new value; the fold is `state.put(scope, value)`.** Terrain's scope is a rectangle of
cells, an entity's scope is its name, a rule's scope is its name.

What genuinely breaks the single shape is a row whose value is a *function of the old value* — a
relative regrade ("cut 30cm across this polygon"), an increment, an append-to-list. Those are true
patch-folds and they cost three things at once: the row can no longer be read without its whole
prefix, re-application is no longer idempotent, and a snapshot at seq N stops being checkable
against the row at seq N alone. None exist today. The regrade op the earthworks lane proposes does
not create one either — it authors intent, and the optimizer resolves it into absolute target
elevations.

**RECOMMENDATION — every row carries absolute values at a named scope; no row is ever relative to
what it replaces.** Then there is one fold, and patch-vs-replace is a question about scope size
rather than about fold mode.

*Strongest case against*: cut and fill *are* deltas, and the earthwork ledger will constantly
recompute by subtraction what a relative row could have stated once. Answer: subtraction is cheap
and the baseline is named, and the ledger is a projection — derived views may be relative, truth
may not. The rule is about what the log stores, not about what anything computes from it.

## Q2 — Snapshot rows: derived, droppable, and dangerous to key by seq alone

A snapshot row says "projection P at seq N was this". Under absolute-at-scope it is exactly
derivable, so the deciding test is blunt: delete every snapshot row, replay, and you get the same
state. That is not a weakness to apologise for — it is the property that makes a snapshot safe.
A corrupt snapshot can only ever be a bug; it can never be the reason history reads differently.

Can two snapshots at seq N disagree? Yes, three ways, and only one of them is corruption.

1. **Different fold code.** The projection function changes over a decade — a bug fix in the
   terrain patcher, a new row type folded into the same view. Both snapshots were honest when
   written. This is the common case and the dangerous one.
2. **Different parameters.** Terrain at seq N on the measured surface and terrain at seq N on a
   proposed surface legitimately differ. A snapshot keyed by seq alone silently claims one is the
   other.
3. **Corruption.** Rare, and the only one where "which is right" is even a question.

**RECOMMENDATION — a snapshot is keyed by (projection id, projection parameters, seq, fold
version) and carries a content hash; a reader treats a snapshot whose fold version differs from
the running fold as absent and recomputes.** Disagreement is never resolved by "later row wins" —
recompute from the log; if a same-version snapshot then disagrees, that is an error to surface,
not a discrepancy to paper over. Load-bearing? No. Never.

*Strongest case against, and it is a real fork for Jim*: if snapshot rows are legally droppable,
the log stops being uniformly append-only — it becomes append-only for truth rows and prunable for
derived rows, and something must now mark which is which. That mark is new surface area on the
core promise, bought for a performance win. The alternative is a snapshot store *beside* the log
with the same key, which is strictly simpler, loses nothing functionally, and costs only the
one-store aesthetic that D-011 leaned on ("why should anything live outside the log"). **I do not
think this one should be defaulted. It is the same fork D-011 already decided once for terrain,
and the answer that was right for truth may not be right for derived data.** Either way, no
snapshot row lands tonight.

## Q3 — Cache keyed on folded-through seq: it never invalidates, it only lags

This is the cheap consequence of Q1. With absolute-at-scope rows on an append-only log, a cached
projection is **never wrong, only behind**. There is no invalidation event, no dirty bit, no TTL —
a read folds the rows that arrived since the watermark and returns. That is what shipped:

- key: the folded-through seq (`TerrainProjectionCache.foldedThroughSeq`)
- read: `readRowsAfter(watermark)`, fold each, advance the watermark to the last row seen
- a row landing mid-read: the reader's row list *is* the pin. It returns state as of the last row
  it saw, which is a real nameable instant. It never returns a mix of "some rows after N".

**The one condition that makes this wrong is out-of-order visibility** — a row becoming visible
with a seq below the watermark. SQLite's `AUTOINCREMENT` assigns the seq inside the insert, so a
concurrent writer can take seq 41 and commit *after* seq 42 is already readable; a cache that
advanced to 42 will never fold 41 and is then permanently, silently wrong. Today the log has one
writer and it cannot happen. **RECOMMENDATION — appends stay serialized, and if that ever changes,
the cache advances to the highest seq below the lowest in-flight seq, not to the maximum seq
read.** A watermark, not a max. This is the first thing to check the day the log gets a second
writer, and it is invisible until it corrupts.

**On the second projection parameter, explicitly.** Today the key looks like a scalar. If terrain
rows gain a surface identifier, the cache key becomes `(surface, seq)` and the cache stops being a
field and becomes a map of watermarks — one entry per surface, sharing nothing, and a proposal
surface's watermark cannot advance past measured rows appended after it branched. That is a change
of key *type*, and the code is shaped so it is exactly that: a single mutable watermark beside a
single folded value, both replaceable by a map, with the fold function (`foldTerrain`) already
parameter-free and shared with full replay. I did not pre-build the map — a one-field key object
today is speculative generality — but nothing in the cache assumes the key is a scalar except its
own two fields.

*Strongest case against*: caching state in an object that a reader holds across appends invites
exactly the staleness the architecture avoids by recomputing. Answer: the gate test proves it — a
cache that skips the tail read fails all five cases, including the one that only checks the
watermark advanced past a non-terrain row.

## Q4 — Ordering settles which write wins, and nothing else

Ordering is the conflict *resolution* rule: two writes to the same scope, later seq wins. Held
honestly, that is a small claim, and three things fall outside it.

**Concurrent writers.** Last-write-wins never fails, so it never reports. Two writers touching the
same scope both succeed and the earlier intent is gone with no trace that anything was contested —
the log records what happened but not that a disagreement happened. Detection needs each write to
state the seq it believed it was amending, and to be rejected if the scope has moved since:
optimistic concurrency, at append, not at fold. The earthworks lane independently proposed this
exact shape for proposals ("carrying the measured baseline sequence they branched from"), which is
a strong signal it is the right mechanism rather than a bolt-on.

**Out-of-order arrival.** The seq is assigned at *ingest*, not at observation. A LiDAR flight from
March ingested in July gets a July seq and outranks a June survey. **Sequence is bookkeeping time;
it is not world time.** Solvers already take a date because sun and shade are seasonal — that date
is world time, and the two must never be conflated. Terrain rows currently carry no observation
time, so today the log cannot answer "what did the ground look like in March" correctly if the
captures were ingested out of order. Flagged, not landed — it is a schema change.

**A capture contradicting an older proposal.** Ordering says the capture wins in its own scope,
and that is the whole answer *provided the scopes differ*. Without a surface identifier, measured
ground and a proposed regrade occupy the same scope, and a new capture silently destroys a design
nobody agreed to abandon. That is the strongest argument for the surface id reached from a
completely different direction than the earthworks lane took to it.

**RECOMMENDATION — keep last-write-wins as the fold rule; add conflict *detection* at append via
an expected-baseline seq per scope.** Ordering is a resolution rule; a decade-long log needs
detection, and the fold is the wrong place for it because by then the loser is already gone.

## Q5 — Compaction: four different things wear the name, and one of them is a lie

1. **Snapshot, then delete the prefix.** The classic. It costs exactly the promise — the deleted
   rows are the ops, the rejections, the rule prose. State survives; *why* does not, permanently
   and unrecoverably. A twin that cannot answer "why is the greenhouse here" is the mutable state
   DB that D-001 already rejected, reached by a slower road.
2. **Snapshot, keep the prefix, read from the snapshot.** Not compaction at all — caching with a
   durable home. Costs nothing. This is what Q2 is about.
3. **Re-encode without changing content.** Already ruled a codec change, not a schema change
   (D-011). Free, and the right first answer if size hurts.
4. **Drop derived rows.** Legal precisely because they are recomputable, and it needs the
   truth/derived mark that Q2's fork turns on.

The one case that genuinely tempts (1): a base terrain row is ~4.3MB, and a decade of re-captures
is dozens of them, mostly bulk with little *why*. Even there, dropping an old base row destroys
"how much has the ground moved since 2026" and destroys the ability to re-derive an as-built
deviation — the same subtraction the earthwork ledger runs. The bulk rows are where the answers to
the decade questions live.

**RECOMMENDATION — truth rows are never dropped; derived rows may be dropped freely; if storage
hurts, the answer is a codec, then a storage tier, never a delete.** The storage-tier escape:
a truth row's payload may move to cold or external storage with its content hash left in the log.
The row still exists and replay is still possible, only slower. That escape does bump against
D-011's "why should anything live outside the log" — a hash-and-pointer is precisely the
"versioned artifact the log references" shape that ruling rejected — so it is a fork for Jim, not
a default I am taking.

## Proposed rows/fields (not landed — schema waits on Jim)

- **`snapshot` row** — projection id · projection parameters · seq · fold version · payload ·
  content hash. Writer role DERIVED. Droppable by construction; ignored when the fold version
  does not match the running fold. Live alternative: the same key in a store beside the log.
- **observation time** on capture-written rows, distinct from seq. Without it, ingest order can
  outrank observation order and no solver can tell.
- **expected-baseline seq** on writes that amend a scope, so a contested write is rejected rather
  than silently winning.
- **a truth/derived mark** on row types — the precondition for any legal deletion, and the thing
  that decides whether snapshots belong in the log at all.

## Code shipped

The terrain projection cache — no new row type, no schema change.

- `TerrainFold.kt` — `foldTerrain(terrain, row)`, the single terrain fold, now shared by full
  replay and the cache. It was previously private inside the projection; two folds that must agree
  is the bug this pre-empts.
- `TerrainProjectionCache.kt` — watermark + folded value, folds only the tail.
- `WorldLog.readRowsAfter(seq)` — the tail read; `readAll()` is now that call from seq 0.
- `TerrainProjectionCacheGateTest.kt` — five cases, all five red when the cache is made to skip
  its tail read.
