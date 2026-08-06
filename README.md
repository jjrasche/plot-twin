# plot-twin

**One living 3D model per plot of land, held for a decade.** The owner speaks intent; an LLM
compiles it to typed rules and ops; pure solvers verify; a classical optimizer places; an
append-only log remembers every change and why. The owner walks the result — on screen now,
on the ground through a phone later. Every structure is placed before it's bought; every
constraint (drainage, sun, access, permit) is enforced before a shovel moves.

**Verdict so far:** architecture locked and adversarially reviewed; research answered
(optimizer design · free LiDAR/NAIP covers the first parcel · renderer rebuild specced,
effort M); build charters staged; no code yet.

## Stage map

1. **now** — world-state schema, solver kit, op pipeline, renderer rebuild (charters staged)
2. **next** — 2D truth-view with violation overlays; real terrain + rooms
3. **later** — CP-SAT optimizer, capture agent, AR walkthrough

First plot: a 2-acre home parcel (Delta Twp, MI). Next: the common-ground corridor parcels —
this repo designs one plot; the sibling common-ground project selects which land.

## Navigation

- [CLAUDE.md](CLAUDE.md) — constitution: architecture spine, rules
- [DECISIONS.md](DECISIONS.md) — every choice with its rejected options
- [research/RESEARCH.md](research/RESEARCH.md) — live conclusions, one row each
- [TASKS.md](TASKS.md) — the one work list
- [docs/architecture.drawio](docs/architecture.drawio) — the inner/outer loop, reviewed
