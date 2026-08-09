---
id: Q-007
trigger: owner
spawned-by: Q-005
feeds: D-004
---

# Staging is IFC's task model, and the twin already has most of it

**The whole answer in one line: the industry convention is a typed task linked to the elements
it produces, ordered by explicit sequence relationships, with scheduled and actual dates side by
side — the twin's ops, surfaces and realization rows already are that shape, and the one missing
piece is a `stage` row carrying sequence and dates, which is a pure row-type addition.**

## Q1 — What the standard actually is

The open standard for construction scheduling is IFC's process model, unchanged in shape from
IFC4 through IFC4x3:

- **The unit of staging is `IfcTask`** — "an identifiable unit of work to be carried out in a
  construction project," with an optional `Status` label (examples given: `NOTSTARTED`,
  `STARTED`, `COMPLETED`) and an `IsMilestone` flag
  (https://ifc43-docs.standards.buildingsmart.org/IFC/RELEASE/IFC4x3/HTML/lexical/IfcTask.htm).
  Tasks are typed by `IfcTaskTypeEnum`: CONSTRUCTION ("Constructing or building something"),
  DEMOLITION, MOVE ("Moving things from one place to another"), LOGISTIC ("Transportation or
  delivery of something"), REMOVAL, DISPOSAL, RENOVATION, among 23 values
  (https://ifc43-docs.standards.buildingsmart.org/IFC/RELEASE/IFC4x3/HTML/lexical/IfcTaskTypeEnum.htm).
- **4D linkage is task-to-product assignment.** In buildingSMART's own construction-scheduling
  example, "each task is assigned to a resulting product produced by the task using the
  `IfcRelAssignsToProduct` relationship"; the task hierarchy is `IfcRelNests`; ordering is
  `IfcRelSequence` with `RelationshipType` such as `.FINISH_START.` (slab before walls, walls
  serial)
  (https://ifc43-docs.standards.buildingsmart.org/IFC/RELEASE/IFC4x3/HTML/annex_e/construction-scheduling/construction-scheduling-task.html).
- **Dates live on `IfcTaskTime`, planned and actual side by side**: `ScheduleStart` /
  `ScheduleFinish` ("the date on which a task is scheduled to be started") next to
  `ActualStart` / `ActualFinish` ("the date on which a task is actually started"), plus
  `Completion` as a ratio
  (https://ifc43-docs.standards.buildingsmart.org/IFC/RELEASE/IFC4x3/HTML/lexical/IfcTaskTime.htm).
- **As-planned vs as-built is a schedule-level distinction too**: `IfcWorkSchedule` carries
  `IfcWorkScheduleTypeEnum` = PLANNED ("a process showing planned items"), ACTUAL ("a process in
  which actual items undertaken are indicated"), or BASELINE ("a baseline from which changes that
  are made later can be recognized")
  (https://ifc43-docs.standards.buildingsmart.org/IFC/RELEASE/IFC4x3/HTML/lexical/IfcWorkScheduleTypeEnum.htm,
  https://ifc43-docs.standards.buildingsmart.org/IFC/RELEASE/IFC4x3/HTML/lexical/IfcWorkSchedule.htm).
- **Earthworks specifically became first-class in IFC4x3**: `IfcEarthworksCut` is "the resulting
  void from modification of existing terrain … by excavation or by other means of removing
  material," quantified by `Qto_EarthworksCutBaseQuantities` as `UndisturbedVolume` (bank) and
  `LooseVolume`
  (https://ifc43-docs.standards.buildingsmart.org/IFC/RELEASE/IFC4x3/HTML/lexical/IfcEarthworksCut.htm);
  `IfcEarthworksFill` is the raising counterpart
  (https://ifc43-docs.standards.buildingsmart.org/IFC/RELEASE/IFC4x3/HTML/lexical/IfcEarthworksFill.htm).

## Q2 — The mapping onto ops, surfaces, realization

| IFC convention | twin row, today |
|---|---|
| `IfcTask` (typed unit of work) | the op row — verbs play `IfcTaskTypeEnum`'s role (`regrade` ≈ an earthworks CONSTRUCTION task) |
| `IfcRelAssignsToProduct` (task → resulting elements) | resolution rows citing their op by ref — position diffs, terrain diffs and the earthwork row all carry `RowRef(OP, seq)` |
| `IfcWorkSchedule` PLANNED | a `proposed(<name>)` surface — the as-planned ground |
| `IfcWorkSchedule` ACTUAL | the `measured` surface — the as-built ground |
| BASELINE ("from which changes made later can be recognized") | the `branchedFromSeq` a proposal carries — the measured baseline it diffed against |
| `IfcTaskTime.ActualFinish` / task `Status` COMPLETED | the `surface_realized` row — capture's word that the proposal was built, dated by the confirming capture seq |
| `Qto_EarthworksCutBaseQuantities` `UndisturbedVolume` / `LooseVolume` | the earthwork row's bank-cut and loose terms — same bank/loose vocabulary |

Two places the twin is *ahead* of the standard, worth keeping rather than "correcting": IFC
models the cut void but explicitly does not conserve the removed material — "the material
excavated and either used as fill or discarded as waste is not modelled as Cut, but may be
handled as a different concept (Resource) in the future" (IfcEarthworksCut, above) — where the
twin's conservation invariant closes exactly that hole; and IFC's task `Status` is a free label
an application asserts, where the twin's realization is a CAPTURE-signed row, i.e. as-built means
the ground said so, not the scheduler.

## Q3 — What is missing, precisely

Against the convention the twin lacks exactly two things, both properties of one absent row:

1. **Sequence** (`IfcRelSequence`): nothing orders one op after another; every op resolves the
   moment it is appended. "Dig the pond, then berm the spoil, then plant the screen" is not
   representable.
2. **Dates** (`IfcTaskTime`): ops carry no `ScheduleStart`/`ScheduleFinish`; the actual side
   already exists (`surface_realized`), the planned side has nowhere to live.

**RECOMMENDATION — adopt the IFC shape as one new row type, `stage`**: a named grouping that
references its member ops (existing `RowRef` machinery), names predecessor stages
(FINISH_START, the example's default), and optionally carries scheduled start/finish dates.
Surfaces stay the as-planned/as-built axis; the stage row is the sequence-and-dates axis; the
existing realization row is the actual. This is a pure row-type addition — no schema change to
any existing row, no new writer role (stages are plan statements: LLM/owner altitude, like
rules) — so per the charter it lands tonight as `StageRow` with a projection and a gate test.
What does NOT land: any scheduler or date arithmetic; a stage row is a statement of intent the
log remembers, and anything that *computes* schedules is an optimizer question for later.

*Strongest case against*: adopting task rows now is speculative — no consumer exists, and
unconsumed schema is exactly the drift a locked architecture forbids; the honest move is to
write nothing until a walkthrough or a contractor export needs it. The answer: the charter's
question was precisely "what convention do we adopt so the before/after seed doesn't harden into
an invented shape" — and the cheap part to fix later is code, the expensive part is a log full
of rows in the wrong shape. One row type, IFC-shaped, zero behavior, is the minimum that keeps
the log's staging vocabulary standard; everything behavioral stays unbuilt.
