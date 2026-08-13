# Decisions

## D-001 — world state is an event-sourced append-only log (2026-08-05, locked) #state #provenance
Typed rows (entity · rule · lock · weight · op · position-diff); current state is a projection; projections are plural.
| option | decade provenance | Genesis-portable | verdict |
|---|---|---|---|
| append-only log + projections | yes — every change has a why | yes — port = row-writing | CHOSEN |
| mutable state DB | no — history lost | no | rejected — "why is the greenhouse here in 2028" becomes unanswerable |

## D-002 — LLM operates at room/constraint altitude, never geometry (2026-08-05, locked) #llm #altitude
The LLM compiles intent to typed rules and ops, interprets ranked violations, proposes relaxations; it reads projections but never computes or emits coordinates.
| option | geometric accuracy | verdict |
|---|---|---|
| LLM queries solvers, speaks ops | exact (solvers measure) | CHOSEN |
| LLM computes geometry from JSON | collapses 42–80% with complexity (FloorplanQA); coordinates ≈ random (Holodeck ablation) | rejected |

## D-003 — inner loop is classical: solvers measure, optimizer gates and places (2026-08-05, locked) #optimizer #gate
One gate owner: solvers return violations per candidate; the optimizer rejects hard-rule violators, scores the rest by weighted soft rules, breaks ties deterministically. Q-001 refines the internals: constraint compile → coarse CP-SAT → seeded local search, warm-started coarse-to-fine, with a deterministic contradiction pre-check before every solve.
| option | verdict |
|---|---|
| classical solver inner loop, LLM proposes neighborhoods | CHOSEN |
| LLM-written heuristics in the inner loop | rejected — the heuristic trap (Formalize-Don't-Optimize) |
| pure brute force, no LLM pruning | rejected — can't know "the maple matters"; wasteful at scale |

## D-004 — ops are log rows through the op-vocabulary waist (2026-08-05, locked) #ops #provenance
Every LLM op appends through the schema waist (add_room · move · resize · reroute · relax · lock — slot-filled, no coordinates); appends reactively trigger placement. The LLM's only other write path is non-geometric rows (rule · weight · name). No orchestrator node — the event loop is substrate.
| option | provenance | verdict |
|---|---|---|
| ops as appended rows | intent-to-position chain fully logged | CHOSEN |
| ops as direct function calls | causally significant events lost to the ether | rejected — zero-based review |

## D-005 — solvers = runner + pure leaves, ranked emit, date-parameterized (2026-08-05, locked) #solvers
Runner fans out, aggregates, ranks by severity (ranking is the runner's); leaves are pure `f(state, rule) -> [violations]` in four families (geometry · propagation-sheds · accumulation/D8 · land+zoning). Solvers take a date — sun/shade/deciduous are seasonal.
**Amended 2026-08-08 (per Q-005): solvers take `(date, surface)`.** The terrain projection is parameterized by surface identity — `measured` or `proposed(<name>)` — exactly as solvers are parameterized by date, and neither parameter has a default: "does the greenhouse flood?" reads a proposal, "did the contractor build it right?" reads measured, and no caller gets an implicit answer. Enforced at the type level — `SolverWorld` requires a surface, so a solver call that names no ground does not compile.

## D-006 — one Rule class with a hardness field; objective = weighted soft rules (2026-08-05, locked) #rules
hard → gates, soft → scores; the same rule can flip by source (permit = hard, taste = soft). Weights are configurable, versioned rows. Weight is a rule's input; severity is a violation's output. Nothing is random: objective decides, deterministic tie-break settles. Landmarks the owner references become named entity rows (anything referenced twice earns a name).

## D-007 — terrain is 2.5D: 10cm grid + vector entities with heights; LiDAR-first (2026-08-05, locked) #terrain
~919K cells for the first parcel's 1.839 acres (the original "~810K for 2 acres" was approximate scale off a wrong parcel size; D-023 sizes the real extent from the property line, and it is not a budget); entities are exact polygons carrying heights (80ft tree = trunk cylinder + canopy ellipsoid). Q-002 confirmed free QL2 LiDAR + 1m DEM + NAIP cover the parcel (Delta Twp, Eaton Co, MI); phone-survey fallback only if data's missing. Units: feet/inches at every owner-facing surface, meters internal. Toy-loop rooms: greenhouse + pergola.
| option | verdict |
|---|---|
| 2.5D heightmap + entity heights | CHOSEN — covers shadows/viewshed/drainage |
| full 3D voxels | rejected — needed only for interiors/overhangs, none planned |
| 1cm grid | rejected — 81M cells, wasteful; vectors give exactness where it matters |

## D-008 — renderer: factored-ui scene3d, rebuilt as a batched painter (2026-08-05, locked) #renderer
Reads state, draws, owns no truth; violation overlays at location+magnitude. Q-003 measured current scene3d at 6.9fps @ 100K triangles (FAIL); rebuild = chunked float[] buffers through Skiko drawVertices inside the Compose Canvas, O(n) grid-order traversal, effort M.
| option | headless-verifiable | verdict |
|---|---|---|
| batched painter in Compose Canvas | yes | CHOSEN |
| GPU interop under Compose | no — captureToImage sees only the Compose surface | rejected (kept as escape hatch; buffer layer is shared) |
| three.js | yes (differently) | rejected — factored-ui is the one UI; fallback only if the rebuild fails its bands |

## D-009 — standalone repo, Genesis-shaped, common-ground data spine (2026-08-05, locked) #scope
Not built inside Genesis (no dependency); typed rows + log make the eventual port row-writing. common-ground ingests constraint layers (wetland · flood · parcel · zoning); this repo's land solvers read them as files — no duplicate collectors; a generic ETL earns existence only when pulling on a schedule. plot-twin generalizes: one model per plot — Isaac's 1.839 acres on W Jolly Rd first, corridor parcels next.

## D-010 — process rules (2026-08-05, locked) #process
Loop before capture. rationale governance adopted before any code. Tap-to-lock vs intent-only is UX policy, not architecture — every writer terminates as the same typed row. Repo is public. Never cite decision/question IDs at the owner — say the thing itself.

## D-011 — terrain enters the log as rows: base-terrain + terrain-diff (2026-08-06, ruled) #terrain #state
Nothing lives outside the log. Terrain becomes typed rows: a base-terrain row (initial ground) plus terrain-diff rows when ground actually changes (regrade, swale); replay stays honest, small changes stay small. Ingestion follows the Genesis adapter/operator shape — a daemon pulls raw (LiDAR tiles), an operator compiles raw into log rows. Cell resolution settled 2026-08-06 (lead-ratified): fixed 10cm uniform grid, no adaptive cells — the LiDAR source (2.2 pts/m²) means finer adaptivity would store interpolation, not measurement; coarse-to-fine belongs to the optimizer's search (a derived view), never the truth format; fixed cells keep terrain-diff semantics deterministic (rectangular patch, last write wins); size is liftable (toy base row 4.3MB, whole toy log 4.3MB). Compression/quantization is a codec change, not a schema change, deferred until size hurts.
| option | replay honest | log size | verdict |
|---|---|---|---|
| terrain rows in the log (base + diffs) | yes | diffs are small | CHOSEN |
| versioned artifact the log references | pointer only | small log, but truth splits across two stores | rejected — owner's rule: why should anything live outside the log |

## D-012 — one rule row carries prose AND compiled constraint (2026-08-06, ruled) #rules
The prose is the why (owner-facing rationale); the typed constraint is the implementation. Both live in the same rule row; compilation happens once at append, never per solver run. Re-compiling would let the implementation drift from the recorded why.

## D-013 — two geometry writer roles: capture measures, optimizer places (2026-08-06, default taken) #provenance
Measured geometry (LiDAR/survey/photo ingestion) and placed geometry (optimizer output) carry different writer signatures. Rows answer "who says the ground is here" vs "who decided the greenhouse goes here". Default taken by the lead after both charter-1 and charter-3 workers independently hit the single-writer wall; surfaced to the owner.
**Amended 2026-08-08 (per Q-005): for terrain rows the writer role derives from the surface, it is not a free signature.** Terrain rows carry a surface id — `measured`, or `proposed(<name>)` with the measured baseline seq the proposal branched from — and the role follows: measured ground ⇒ CAPTURE, proposed ground ⇒ OPTIMIZER; the log rejects any other pairing. A `surface_realized` row (CAPTURE-only) retires a proposal once capture confirms it was built; the proposal is never rewritten, and measured-now minus proposal is the as-built deviation. A tightening of the two-roles ruling, not a loosening.

## D-014 — the lead merges plot-twin at its own discretion (2026-08-06, ruled; RETIRED 2026-08-13 into D-018) #process
Verified-gate branches land on main by the lead's call; no per-merge ratification. factored-ui merges remain the owner's.
**Retired 2026-08-13, superseded by D-018.** D-018 generalized the merge autonomy and, read literally, freed the one thing this entry reserved. Its live clause — factored-ui merges are the owner's — moves into D-018 as a fourth exception; nothing else here stands on its own. Kept as history, not deleted.

## D-015 — one CPU line sweep is both the sunshed solver and the renderer's light (2026-08-07, ruled) #solvers #renderer
Q-004's horizon sweep is built once, in `:solvers`, and read twice: the solver integrates it over a day into direct-sun hours; the renderer evaluates it at one moment into a lit fraction and, over eight azimuths, into sky openness. Measured on the 900x900 toy plot (810,000 cells): one sweep 20 ms, a whole solstice day at 15-minute samples 1976 ms — cheap enough that the renderer sweeps at solver resolution and averages down, which is where its soft shadow edges come from. Entities are rasterised into the same occluder surface as the ground, so a greenhouse shades a bed for the same reason a hill does.
| option | one truth | verdict |
|---|---|---|
| sweep in :solvers, render reads it | yes | CHOSEN |
| lighting effect in :render + separate solver | no — two shadow answers, only one logged | rejected |

## D-016 — sky v1 is an analytic altitude gradient on a heightfield dome, not Hosek-Wilkie (2026-08-07, ruled) #renderer
Hosek-Wilkie needs its fitted radiance coefficient tables vendored verbatim; hand-transcribing a multi-KB numeric blob is unverifiable, and the dome mesh plus per-triangle colour path is identical either way, so the model is a drop-in later. v1 is horizon-to-zenith interpolation keyed on sun altitude plus a sun-proximity glow. The dome itself is a heightfield entity listed first, which is the only sky scene3d 0.19.0 can draw — it draws heightfield entities in list order before every other mesh.
**Amended 2026-08-09: the dome is in the gated spec.** A sky classifier reads its palette from the spec's own sky mesh, so the skyline check tells sky from terrain; the dome is rebuilt polar (rings uniform in the blend parameter) so triangulation aligns with iso-colour contours, with a below-horizon skirt and the scene background set to the horizon tint. The old exclusion reason is gone; the old square-lattice dome survives only as the banding check's regression prey.

## D-017 — the plot is georeferenced by a site row (2026-08-07, ruled) #state #solvers
Latitude, longitude and time zone enter the log as a typed `site` row with the CAPTURE writer, because the sunshed solver cannot exist without them and nothing lives outside the log (D-001, D-011).

## D-018 — the lead merges its own gate-green work; Jim ratifies by exception only (2026-08-10, ruled) #process
Extends D-014 from plot-twin merges to the general rule: gate-green work lands on main by the
lead's own call, and Jim is pulled in only for money, going public, or pushing to his devices.
Never wait on Jim for a merge; everything else arrives documented in the ledger, reversible,
reviewed at his batch sitting.
**Amended 2026-08-13 (D-014 retired into this entry): a fourth exception — merges in factored-ui
are the owner's, because it is his repo.**

## D-019 — visual deliverables are gated by the lead's own eyes across measured cycles (2026-08-10, ruled) #renderer #process
Every visual deliverable iterates inside the run: the lead renders, looks, scores against
bands committed BEFORE the first cycle, and adjusts until better-vs-worse is measured across
cycles, not guessed from one frame. The edit loop lives in the run, never on Jim's desk. Born
from the run-6 squished satellite-over-topology smear that shipped gate-green while not
resembling ground: pixel checks bound correctness, only an eye bounds resemblance.

## D-020 — the first parcel is Isaac's, 11157 W Jolly Rd, Delta Twp; coordinates are public (2026-08-10, ruled) #state #scope
The shared-case parcel is public record and ratified for tracked fixtures — the run-6 privacy
hold is lifted. The fixture and geocode gate carry the true point.

## D-021 — the property line is a `parcel_boundary` row carrying its own frame and receipt (2026-08-13, defaults taken) #state #provenance #land
Measured ground, so a CAPTURE row (D-013): the ring in plot-local metres stored open (the
closing vertex is the wrap, and a repeated one makes the ring non-simple), the county's stated
acreage, and the pull's receipt. Interim source is Eaton County's open parcel service, named on
the row as `interim-county-service` — when the shared parcel-layer seam delivers, it appends a
new boundary row and this one stays as history.
| option | log holds truth | verdict |
|---|---|---|
| boundary as a typed log row | yes | CHOSEN |
| boundary as a side file the renderer/compiler reads | no — the extent would live outside the log | rejected (D-001, D-011) |

Two defaults taken, both reversible:
- **the frame rides on the boundary row** (`GroundFrame`: CRS + origin easting/northing). Every
  other row's ground coordinates are metres against an origin that exists only inside
  `compile_parcel.py`, so the log could not put its own coordinates back on Earth; the boundary
  is the first row that must survive a regrid, so it says where its origin is. Reversal: move
  the frame to the site row where georeferencing already lives — one field move plus the
  fixture, no data loss.
- **the row carries its own provenance** — the first row in the log to do so. Base-terrain rows
  drop the DEM receipt at the log boundary today (it stays in `parcel.json`). Reversal: a shared
  provenance row referenced by seq, once a second source needs the same shape.

## D-022 — a shadow-direction reading gates only where one caster owns the shade it reads (2026-08-13, ruled) #eyes #renderer
The check estimates a principal shadow bearing from one occluder. On a 97-tree woodlot that
quantity does not exist, so the check now measures whether it exists before claiming it:
each ground sample is attributed to the first body its ray to the sun meets, the shares are
counted inside the check's own screen annulus past its own sky and caster masks, and a
reading gates only when the caster it assumed holds at least 0.75 of that shade. Below the
floor the reading is ADVISORY and states the distribution that suppressed it.
Floor frozen against sampled output from both arms before any suppression existed: the toy
plot's greenhouse held 0.963-1.000 across three moments and seven poses; Isaac's parcel gave
its tallest tree 0.129-0.533 across the same poses on both the fixture and the full-res
compiled grid. 0.75 sits 0.213 above the toy floor and 0.217 below the woodlot ceiling.
| option | trains the lead to trust the banner | verdict |
|---|---|---|
| measure the caster population, suppress where no principal caster exists | yes - red still means wrong | CHOSEN |
| widen the bearing tolerance until the woodlot passes | no - the toy plot stops catching wrong bearings too | rejected |
| drop the check on real parcels | no - a check that cannot fail is not a check | rejected |

**Amended 2026-08-13 (re-measured after charter 28 made the full-res arm unconditional).** The
frozen figures above described a render that no longer exists: charter 22's neutral surround and
dome changed what counts as sky, so the check's own annulus population changed under them, and the
parcel now has six poses rather than seven. Re-measured, the woodlot's assumed-caster share runs
**0.009-0.188** across both arms (fixture and full-res agree to within 0.006 at every pose, which is
its own evidence the 1m fixture is a faithful downsample), against 24-31 casters sharing the ring.
The floor stays at 0.75 and is NOT re-frozen: a threshold moved after seeing a count is a new
experiment, not a fix. The margin simply grew - 0.562 clear of the woodlot ceiling where the
original freeze recorded 0.217 - so the ruling is safer than written, never weaker.
Two things the re-measurement surfaced. The assumed caster is **not** the top caster at five of the
six poses (at overhead tree-001 holds 0.081 while tree-014 holds 0.136), which is the charter-23
default - key suppression on the ASSUMED caster's share, not the top one's - doing exactly the work
it was chosen for. And the rule worth carrying: **a criterion is frozen against a render, so when
the render changes the freeze must be re-measured, not re-frozen.** Nothing would have shown this
if the measurement did not print its populations on every run.

## D-023 — the grid is the property line's bounding box plus a derived mask; the frame is checked, not assumed (2026-08-13, defaults taken) #terrain #state #land
The extent stops being a fixed square and becomes the `parcel_boundary` ring's bounding box,
snapped outward to whole 10cm cells: 380 × 2419 = 919,220 cells for Isaac's parcel. D-011's fixed
10cm cells and `TerrainGrid`'s rectangle are untouched — an irregular extent is a different
architecture. Cells outside the line exist and are marked not-ours.
| option | one property line | verdict |
|---|---|---|
| rectangle of the ring's bbox + per-cell mask | yes | CHOSEN |
| irregular-extent grid | yes, but re-shapes every solver and the renderer | rejected for now — the mask is additive, so this stays one commit away |

Three defaults taken, all reversible:
- **the mask is derived in the projection, never stored.** It is a pure function of the ring and
  the grid, so a stored copy would be a second answer to where the property line runs, and the
  reader could not tell which one the plot is. Reversal: add the packed array to a row and have
  the projection verify it against the derivation — one commit, additive.
- **the base-terrain row may name its frame too, and the projection rejects a log whose rows
  disagree.** Two origins in one log mean its plot-local coordinates mean two things; a grid
  sharing a log with a property line must be that line's own bbox, or reading fails loud. A row
  that names no frame makes no claim and cannot disagree. Reversal: keep the frame only on the
  boundary row and check the extent alone — one field.
- **a terrain diff touching an outside-the-line cell is rejected at the writer** with a typed
  violation naming the cell and the stolen area: you cannot regrade your neighbour's land, and
  accepting the write would make the mask decoration. Reversal if a shared drive or a drainage
  easement needs it: the rejection is a guard, not a schema change — one commit.

The extent also retired a feature: **the road is not on this parcel.** W Jolly Rd's right-of-way
lies south of the south line, the southern rows inside the line carry 4.8–10 m of canopy, and the
old "brightest gray band" detector was reading sunlit treetops (CHM 9–16 m) as pavement over a
242 m strip. The detector now also requires bare ground, and reports absence — like class-6
structures, an absence finding rather than a silence.

## D-024 - the drawn ground IS the property line, and a pose is a bearing the plot's own box sizes (2026-08-13, defaults taken) #renderer #eyes
scene3d's batched painter can only draw a rectangular heightfield, so the render grid's rows
become the ring's own horizontal cuts: every drawn vertex sits on or inside the line, the
neighbours' land is never drawn as ground, and the batched painter keeps its speed. The line
itself is a low unlit kerb laid inside the ring - an annotation like a violation marker, not a
structure claimed to exist. Poses stop carrying distances: one rule, asked twice, solves the
nearest distance at which named corners still hold inside a named share of the frame.
| option | silhouette exact | keeps the batched painter | verdict |
|---|---|---|---|
| render heightfield warped to the ring's row cuts | yes | yes | CHOSEN |
| ground as a generic per-triangle mesh with outside cells dropped | yes | no - 115K triangles back on the 6.9fps path D-008 rejected | rejected |
| outside cells painted the background colour | no - ground drawn and disguised as sky | yes | rejected |

Three defaults taken, all reversible:
- **one interval per row.** The cut assumes each row meets the ring twice; a ring that breaks
  it fails at the mesh rather than painting the gap between two lobes as ground. True of every
  convex parcel. Reversal: clip cells against the ring and drop the fringe into a small generic
  mesh - one new mesh, no truth change.
- **the orbit sweep turns half a step off the axes when a square-on frame would leave the land
  under nine tenths of the frame's width.** A 1:6.4 ribbon seen down its own axis fills at most
  0.292 of the width at any distance, so two of four frames were slivers; measured, not assumed,
  so the square toy plot keeps its square-on sweep. Cost: nobody now sees straight down the
  parcel's length. Reversal: one constant, or a named seventh pose.
- **the painter's far-to-near chunk order reads vertex row 0 as representative**, which on a
  tapered ring is a near-degenerate row, so chunk ordering is approximate by up to the taper's
  own offset. Harmless at this parcel's 6 m of relief; it would matter on a steep one. Reversal:
  order chunks from the ring's own bounding box - a factored-ui change, so the owner's.

## D-025 - the neighbours' land is a render-side backdrop, and the plot may not hover (2026-08-13, ruled by the lead, defaults taken) #renderer #eyes
Omitting outside-the-line ground made the silhouette honest and the frame a prop: 0.65-0.76 of
every orbit frame below the skyline was void, and at two poses the trunk forest showed under the
terrain edge. The ruling: draw a neutral surround. It is flat, untextured, unshadowed and
un-entitied; its inner boundary IS the property line, so it never overlaps the parcel and is never
measured as part of it; and it is not state - no row describes it, `projectWalkableScene` derives
it from the boundary row and the terrain each time. Measured after: void 0.000-0.001 at every
orbit pose, and 0 pixels of open sky beneath the parcel's ground at all six.
| option | horizon | can be mistaken for the parcel | verdict |
|---|---|---|---|
| annulus whose hole is the ring, drawn before the ground | yes | no - it never overlaps, and its palette is gated clear | CHOSEN |
| leave the void (charter 22 cycle 1) | no | no | rejected - reads as a model on a table |
| extend the measured terrain past the line | yes | yes - it would BE the neighbours' land as data | rejected (D-001: not in the log, not drawn as truth) |

Four defaults taken, all reversible:
- **the dome now stands on the plot's ground datum.** A ground plane cannot carry a horizon under a
  sky whose own horizon sits 265 m below the plot: the pale band was 17 degrees low, so the
  surround's rim would have met mid-blue and stepped 44 luma. Dome and surround now share one datum
  and one radius, so the rim lands exactly on the dome's equator and there is no seam to see. The
  toy plot sits near zero elevation, so nothing there moved. Reversal: drop the datum argument.
- **haze is baked as distance beyond the line**, starting at 0.45 and reaching the horizon tint,
  which is also the scene background. Camera-independent colour cannot do per-frame fog, and every
  pose looks at the plot, so distance from the line stands in for distance from the eye. The base
  haze is also what holds the ring steps under the eye's banding threshold. Reversal: one constant;
  lower it for more land presence and pay in banding and in palette margin.
- **the surround starts one render cell inside the line.** The ground samples the ring by row and
  the surround by spoke, so the two chords disagree between samples and left 12 pixels of sky at
  the seam. The overlap is drawn over by the ground: a seam that cannot open rather than one that
  is usually closed. Reversal: share one boundary polyline, at ~68K triangles instead of 22K.
- **the sky-banding reading is measured over the dome's own palette, not the whole backdrop.**
  Banding is a claim about how smoothly the sky was painted; the surround is a second surface whose
  junction with it says nothing about that, and the painter can drop a triangle that straddles the
  camera plane, which read as a 110-luma step. Sky pixels are all still sampled. Reversal: pass the
  backdrop classifier for both, and eliminate the dropped triangles instead.

## D-026 — the shadow-direction check's gate history, recorded because it lived only in the seat file (2026-08-13, ruled) #eyes #process
The check was promoted from advisory to a real gate on 2026-08-06 (run 3) and no entry ever said
so, which is how the rest of this stayed invisible. The same commit that promoted it also excused
it BY NAME in the toy contact-sheet assertion and added a substitute in its place: two thirds of
viewpoints within tolerance, none pointing into the sun's own half-plane. That substitute was a
defensible weaker gate. On 2026-08-08 (run 5) the by-name exclusion was copied into the new
real-parcel assertion **without the substitute**, and the real-parcel path then ran ungated through
runs 5, 6 and 7 — where four of seven poses were wrong, the worst by 173.7 degrees.
Both exclusions are gone (2026-08-13). The toy one is now a pin tolerating exactly one known
failure by name — the terrain-attribution defect at orbit-4 — proven to go red if the pin is moved.
D-022 governs how the check gates from here.
The rule this entry exists to carry: **an exemption is only as good as the substitute beside it,
and copying the exemption without the substitute is how a green gate outlives its justification.**

## D-027 — Eaton County's parcel service is an authorized INTERIM boundary source, with an expiry (2026-08-13, ruled by Jim) #land #provenance
Jim authorized it on 2026-08-12 when the shared parcel-layer seam turned out not to cover its own
founding parcel: common-ground bakes 108 townships, none in Eaton, and the legacy jolly-rd sweep was
pulled with geometry off, so it carries no boundary at all. Recorded here because D-007 and Q-002
still read as the complete source list (USGS 3DEP + NAIP + QL2 lidar) and would otherwise have the
next run re-litigating this.
| option | verdict |
|---|---|
| GET-only county pull, one shot, row tagged `interim-county-service` | CHOSEN — the seam stays the standing path and the row names itself as not having come through it |
| wait for the seam to bake eaton-delta-twp | rejected — blocks the founding parcel on another project's queue |
| read common-ground's non-contract files by path | rejected — the option their own D-009 rejects, and jolly-rd has no geometry to read anyway |
**Expiry, and it is the point of the entry:** this authorization ends when the seam delivers
`eaton-delta-twp` under the contract. The swap is then one source change, and the log shows honestly
which rows predate the seam. It does not generalize — no other county service is authorized by this,
and plot-twin builds no scheduled collector for a layer common-ground owns (D-009 stands).

## D-028 — a missing input fails loudly, the fixture names the cut, and a judged sheet owns its address (2026-08-13, defaults taken) #process #eyes #provenance
Three closures of one bug: a green gate that never ran. `capture/data` is gitignored, so a fresh
clone had three tests skip and a fourth silently drop its full-resolution arm and still report PASS.
Absence now throws through one reader (`CaptureCache`) that names the command that cures it, the
caster measurement is unconditional, and the build itself fails on any skipped test rather than a
human counting skips off a log — run 5 read 1, run 8 read 0, both by hand.
The tracked 1m fixture now carries the sha256, extent and elevation envelope of the untracked 10cm
cut `compile_parcel.py` wrote beside it in the same pass, and the full-resolution gate asserts that
binding before it draws a pixel: the artifact a human scores cannot drift from the artifact every
other test measures. The two provenance blocks disagreeing on elevation (1m 262.3048/268.2345,
10cm 262.2638/268.2613) is benign — both reproduce exactly from the cached DEM, the 10cm grid
samples the same bilinear field 100x denser, and re-running the compiler rewrote the fixture
byte-identical apart from the new binding.
| option | a lost image is detectable | verdict |
|---|---|---|
| judged sheet at `capture/receipts/run-N/cycle-M/`, address refuses a second write | yes, and the image survives | CHOSEN |
| sha256 of every judged sheet in its ledger line only | detectable, not recoverable | CHOSEN as well — every sheet prints its sha256 whether or not it is committed |
| keep one overwritable tracked path | no — run 6's acceptance is already unrecoverable | rejected |

Three defaults taken, all reversible:
- **an unstamped run writes to `build/` and judges nothing.** The stamp is opt-in
  (`-Dplottwin.receipt.run` / `.cycle`), so the routine gate does not accrete images; a run that
  means to score writes where a later run cannot reach. Reversal: default the stamp on.
- **the mislabelled receipt is addressed by commit, not by run.** It was byte-identical to the
  full-resolution sheet while carrying the fixture sheet's name; it is provably the sheet main
  produces at `aaa448b` and NOT provably run 8 cycle 3's, because the dome, surround and scene
  projection all changed after that cycle closed. It now sits at
  `capture/receipts/main-aaa448b/real_parcel_full_res_contact_sheet.png`, sha256
  6d1ed31b3260e2174232d56efcb424cb651452c2a768d0c0fd5143294ea00345. Reversal: re-render at
  `54177b1` and, if the bytes match, move it under `run-8/cycle-3/`.
- **the compiled cut stays untracked.** The cache is 512 MB, 500 of it the DEM tile; the cut alone
  is 8.5 MB and a judged sheet 0.6 MB, so a three-cycle visual run costs ~1.9 MB of tracked PNG.
  Reversal if that weight is unwanted: keep only the sha256 lines, which are printed either way.

## D-029 — the report is the exception channel; the record is the repo (2026-08-13, ruled by Jim) #process
Jim talks only to the orchestrator seat; project heads never queue questions to him directly, and a
head that does has made a routing decision that was not its to make. Everything else — verdicts,
numbers, defaults taken, receipts — lands in the seat file and this ledger, where the orchestrator
reads it. The morning report is exactly three sections: BLOCKERS (only what has no reversible
default — if it has one, take it and it is not a blocker), GOOD SURPRISES (findings that change
what is possible; progress is assumed and is never a surprise), and a one-line DREAM CHECK naming
what this project is ultimately for, so drift is caught in a line rather than a month.
| option | verdict |
|---|---|
| three strict sections, full record in seat + ledger | CHOSEN — the reader's attention goes to what needed them |
| the ranked queue this replaces | rejected — it mixed the one thing needing a ruling with everything that did not, and trained the reader to skim |
Detail deliberately relocated, not suppressed. **The gap this repo must not paper over: an
orchestrator outside this machine cannot read the file board or this repo, so the transport is
named (`snapshot` packs a repo for a reader with no git) rather than assumed.** Carried into the
machine-wide `overnight-build` skill because it binds every project running a lead, not only this one.

## D-030 — leads renew every 3 runs, and the seat file is what makes it cheap (2026-08-13, ruled by Jim) #process
A lead restarts on a fresh instance every 3 runs, or immediately when its context exceeds
comfortable recall of the constitution. Long-context drift is a known failure mode; renewal is
hygiene, not failure. The honest test is not token count: **it is whether the lead can recall this
repo's constitution and ledger without re-reading them** — re-deriving its own project's rules from
files or from subagents means it is already past the line.
The seat file is the memory, which makes a restart also the TEST of the seat file: if a restart
loses something, the file was incomplete, and the fix is the file, never keeping the old instance
alive. Every renewal is logged in the seat file with a one-line handoff note.
