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

## D-014 — the lead merges plot-twin at its own discretion (2026-08-06, ruled) #process
Verified-gate branches land on main by the lead's call; no per-merge ratification. factored-ui merges remain the owner's.
