# CHARTER — address → walkable parcel: the open-data capture pipeline

## Where it fits
Rung two of the ladder: real ground under the walk. Jim wants the pipeline designed as a
repeatable capability — give it an address, get base-terrain rows and candidate entity
footprints from free public data. `research/questions/Q-002-agentic-capture/` is the base:
EXTEND it, do not redo it.

The deliverable that matters most is the **accuracy budget with receipts** — how wrong is the
ground we hand a solver, and how do we know?

## The work
- **Verify the vertical accuracy claim.** USGS QL2 LiDAR is specified at roughly 10cm RMSEz.
  Do not take that on faith: find the ACTUAL tile report for the Eaton County collection
  covering the parcel and read what it measured. Cite it. If the real number differs from the
  spec, that difference is the finding.
- **NAIP horizontal accuracy** for imagery-derived footprints — same treatment, real source.
- **Canopy from returns**: how first/last returns separate ground from vegetation, and what
  that costs in ground accuracy under a tree.
- **Staleness is a gap, not a footnote.** 2017-18 collection means anything built since is
  invisible. Name that explicitly as the hole the capture role fills.
- **The pipeline design**: tile fetch → ground classification → 10cm grid resample →
  base-terrain rows, plus imagery → candidate entity footprints an operator confirms. Say
  which steps are automatic, which need a human confirm, and where the operator sits.
- **The phone-capture half.** The missing piece is ours: a mesh importer. Phone app exports a
  mesh (Scaniverse captures to mesh on-device today) → scale and georegister → capture-signed
  entity rows. Design the import contract: what format, what registration inputs, what the
  operator confirms. Trees are splatting's worst case — foliage is not a clean surface — so
  for a tree, capture MEASURES height and spread for the typed trunk+canopy entity rather than
  importing geometry. Solid structures import near-perfect.
- **Compute rule, ruled by Jim and not up for debate:** no rented GPU. Anything needing GPU runs
  on the workstation — RTX 2000 Ada Laptop, 8GB VRAM. If a step genuinely exceeds 8GB, say so in
  the report; do not design around a rental.

## Deliverable
- Extend `research/questions/Q-002-agentic-capture/ANSWER.md` (or add a sibling file in that
  folder if the existing answer would be distorted) with: the accuracy budget table, the
  pipeline design, and the mesh-importer contract.
- No code this lane unless a tiny reference script genuinely clarifies the design.

## Verify
- Every accuracy number cites its actual source document, not a spec page repeating a spec.
- The budget composes: state the end-to-end error a solver would see standing on that ground.
- Each pipeline step names its input, output, and who confirms it.

## Bands
PASS: accuracy budget with real receipts + pipeline design + importer contract.
WEAK: design complete but one or more accuracy numbers rest on the published spec because the
actual tile report could not be found — say so plainly and name what you searched.
FAIL: numbers asserted without sources, or a design that assumes rented GPU.

## Rails
- Isolated worktree, branch `research/capture-pipeline`. Research lane — no production code.
- No pushes.
- **Command shape (permission rails — a prompt sleeps the whole run):** no `cd X && …`, no `&&`
  chains, no `git -C`. The cwd persists — `cd` is its own call, then plain single commands.
  Write-verb commands are single-line and non-compound; commit bodies go in a file with
  `git commit -F <file>`. Never hand-curl a POST; GET-shaped `curl -s -G` is fine.
- Report: `git diff --stat`, verdict against the bands, contradictions, open questions.
- Docs are extremely concise; nothing outside the documentation taxonomy.
