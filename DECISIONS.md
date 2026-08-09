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
~810K cells for 2 acres; entities are exact polygons carrying heights (80ft tree = trunk cylinder + canopy ellipsoid). Q-002 confirmed free QL2 LiDAR + 1m DEM + NAIP cover the parcel (Delta Twp, Eaton Co, MI); phone-survey fallback only if data's missing. Units: feet/inches at every owner-facing surface, meters internal. Toy-loop rooms: greenhouse + pergola.
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
Not built inside Genesis (no dependency); typed rows + log make the eventual port row-writing. common-ground ingests constraint layers (wetland · flood · parcel · zoning); this repo's land solvers read them as files — no duplicate collectors; a generic ETL earns existence only when pulling on a schedule. plot-twin generalizes: one model per plot — the home 2 acres first, corridor parcels next.

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

## D-014 — the lead merges plot-twin at its own discretion (2026-08-06, ruled) #process
Verified-gate branches land on main by the lead's call; no per-merge ratification. factored-ui merges remain the owner's.

## D-015 — one CPU line sweep is both the sunshed solver and the renderer's light (2026-08-07, ruled) #solvers #renderer
Q-004's horizon sweep is built once, in `:solvers`, and read twice: the solver integrates it over a day into direct-sun hours; the renderer evaluates it at one moment into a lit fraction and, over eight azimuths, into sky openness. Measured on the 900x900 toy plot (810,000 cells): one sweep 20 ms, a whole solstice day at 15-minute samples 1976 ms — cheap enough that the renderer sweeps at solver resolution and averages down, which is where its soft shadow edges come from. Entities are rasterised into the same occluder surface as the ground, so a greenhouse shades a bed for the same reason a hill does.
| option | one truth | verdict |
|---|---|---|
| sweep in :solvers, render reads it | yes | CHOSEN |
| lighting effect in :render + separate solver | no — two shadow answers, only one logged | rejected |

## D-016 — sky v1 is an analytic altitude gradient on a heightfield dome, not Hosek-Wilkie (2026-08-07, ruled) #renderer
Hosek-Wilkie needs its fitted radiance coefficient tables vendored verbatim; hand-transcribing a multi-KB numeric blob is unverifiable, and the dome mesh plus per-triangle colour path is identical either way, so the model is a drop-in later. v1 is horizon-to-zenith interpolation keyed on sun altitude plus a sun-proximity glow. The dome itself is a heightfield entity listed first, which is the only sky scene3d 0.19.0 can draw — it draws heightfield entities in list order before every other mesh. It is not in the gated spec: the eyes skyline check reads the topmost non-background pixel, and a sky dome makes every column's topmost pixel sky.

## D-017 — the plot is georeferenced by a site row (2026-08-07, ruled) #state #solvers
Latitude, longitude and time zone enter the log as a typed `site` row with the CAPTURE writer, because the sunshed solver cannot exist without them and nothing lives outside the log (D-001, D-011).
