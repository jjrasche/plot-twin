---
id: Q-005
status: answered
trigger: owner
spawned-by: D-011
feeds: D-011
---

# How does the twin move dirt?

Jim, 2026-08-06: dig deeper on earthworks and fill the mental model before any code locks a shape.
Today the twin can only READ ground — measured terrain arrives as capture-signed rows. Designing a
plot means moving dirt.

1. **Regrade as an op.** What are the slots for "reshape this ground"? Which are authored, which
   are solved?
2. **Cut and fill as solver outputs.** Is "40 cubic yards trucked in" a violation, a cost, or both?
   Violations carry location + magnitude + rule — does cost fit that shape?
3. **Spoil is conserved mass.** Dig a pond, the dirt exists. What is the placement problem, and
   what breaks if spoil may vanish?
4. **Two grounds.** Proposed-ground vs measured-ground: one row type with a writer role, or two?
   Which does a solver read by default? What happens when new capture contradicts a built proposal?
5. **Does purpose change the op, or only the rules?** Pond, terrace, swale, building pad are all
   "move dirt."

Ends in a proposal Jim ratifies, not a built feature. Every earthmoving convention or number
carries a primary source on the same line.
