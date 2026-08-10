---
id: Q-008
status: answered
trigger: owner
spawned-by: Q-007
---

# Does Design Structure Matrix earn a place in stage ordering, and when?

Jim, 2026-08-09: "Research Design Structure Matrix (Eppinger) for stage-dependency ordering;
write findings as a Q-doc, adopt nothing yet." Context: `StageRow` carries member ops,
predecessor stage names and optional planned dates, deliberately unconsumed. When a scheduler
eventually exists, how should stages be ordered and where should ordering knowledge live?

1. What is a task-based DSM, precisely — matrix semantics, and how sequencing/partitioning
   works (lower-triangular reordering, coupled blocks)?
2. What does DSM add over the predecessor DAG StageRow already has — feedback loops, tearing,
   banding — and is the honest answer "nothing yet at n<20 stages"?
3. How does industry connect DSM to 4D/BIM scheduling — upstream of CPM/Gantt, any mapping to
   IFC's IfcRelSequence?
4. What would adoption look like here if it ever earns it (sketch only, unadopted)?
5. The strongest case against adopting DSM for plot-twin at all.

Every claim carries a primary source on the same line. Nothing is adopted by this answer.
