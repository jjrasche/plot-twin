# seat: pt — HEAD of plot-twin

Lead agent seat lives in-repo; the machine-wide board (~/.claude/brothers/agents/pt.md) holds
only a pointer here.

- role: lead — holds goal, charters build subagents, verifies by running, lands merges
- topology: orchestrator-worker (subagents build); brothers board only for cross-tab peers (common-ground)
- when: 2026-08-06T22:35Z
- seat: CLAIMED 2026-08-06 by pt-head (third overnight run in flight)
- RUN 3 IN FLIGHT (started 2026-08-06 ~22:40Z), base main @ e3ec7cb:
  - parallel now: eyes+CV (`build/eyes`, charter 9) · earthworks design (`design/earthworks`,
    charter 11) · capture pipeline research (`research-capture-pipeline`, charter 12)
  - chained: light/sky BUILD (charter 10) on eyes — the contact sheet is how it gets judged;
    snapshot rows (charter 13) on earthworks — both touch the schema
  - critical path is eyes: it is the instrument every later render lane is graded by
  - worktrees at ../.git-worktrees/pt-{eyes,earthworks,capture}
  - note: branch `research` already exists, so the capture lane's branch has no slash
  - LANDED: earthworks design PASS, merged at b261702 (docs only, schema untouched). Snapshot
    lane chained on it and told to land no schema change — the surface id waits on Jim.
  - LANDED: snapshot/lens design PASS, merged — terrain projection cache shipped with five
    tests (lead re-ran fresh gate: 81 tests, 0 failures), no schema change. Genesis memo on the
    board at ~/.claude/brothers/messages/change-based-lenses-for-gen-head.md
  - LANDED: capture accuracy budget PASS after one rework (first commit appended a correction
    instead of fixing the figure — sent back, fixed, verified). Real measured numbers now.
- NEEDS JIM (run 3, growing):
  - capture asks three fields onto the base terrain row: reference frame + epoch, per-cell
    support distance (98% of 10cm cells hold no measurement), slope-derived vertical sigma
  - set permanent surveyed marks on the parcel? four RTK shots would register every future
    phone capture for free and would outlive the 2026 datum change
  - may we pull from Eaton County's own imagery service? ~7.5cm/px, March leaf-off, three
    years fresher than the federal imagery — the licensing question is real, not rhetorical
  - the national vertical reference retires end-2026, shifting coordinates by up to several
    meters — larger than the entire measurement error budget
  - snapshot forks, deliberately not defaulted: (a) do snapshots live IN the log or in a store
    beside it? if in the log, the log stops being uniformly append-only and row types need a
    truth-vs-derived mark; (b) may an oversized truth row's payload move to cold storage with a
    hash left in the log? that is the shape the terrain ruling already rejected once
  - the log has no observation time — a March flight ingested in July outranks a June survey.
    Schema change, flagged not landed
  - conflict DETECTION (an expected-baseline sequence at append) — both design lanes reached
    this independently from opposite directions, which is a strong signal
  - earthworks proposal to ratify: one `regrade` verb (subject/form/prose extent — no
    coordinates); cut/fill as an earthwork ledger projection rather than a violation type;
    spoil conserved as a hard invariant with `haul-off` as the named escape; ONE terrain row
    family carrying a surface id instead of two types; purpose selects a rule bundle, not a verb
  - two rulings that proposal would amend: terrain writer role is hardcoded to capture, but
    proposed surfaces make the optimizer a terrain writer; solvers gain a required surface
    parameter beside date
  - a fifth solver family looks unavoidable the first time a pond is real — impoundment and
    spillway routing are hydraulic, not D8 accumulation
  - do violations need a region scope? the earthwork balance rule has no natural point location
- handoff: prior head's batch, now this run's scope:
  1. EYES (confirmed by Jim): charter staged at .claude/charters/overnight-9-eyes.md — camera-drive capability in factored-ui (worktree off il-scene-render-harness @ 8936e5d, one camera two drivers, headless capture) + plot-twin view harness w/ contact sheet the lead reviews by eye. Rewrite the charter if you see better.
  2. EARTHWORKS DESIGN DIG (confirmed — "dig deeper and fill my mental model"): charter it yourself. Scope from tonight's ruling talk: regrade op slots; cut/fill volume as solver outputs; spoil is conserved mass the optimizer must PLACE (pond dig → berm/fill placement scored by viewshed+watershed rules like any entity); proposed-ground vs measured-ground row types; whether purpose changes the op or just the rules. Ends in a proposal Jim ratifies; code only the uncontroversial row types.
  3. SNAPSHOT/COMPACTION ROWS (proposed, not struck — confirm-by-silence): terrain projection cache + snapshot-row design; doubles as a memo to gen-head on change-based lenses (patch-folds vs replace-folds, snapshot rows, cache keyed on last-seq). Jim's framing: lens = folded blob in read memory; ordering is the conflict rule.
  4. OPEN-DATA CAPTURE RESEARCH (Jim added 2026-08-06 late): design the "address → walkable parcel" pipeline as a skill/charter — pull QL2 LiDAR tile + NAIP for a parcel, operator compiles base-terrain rows + candidate entity footprints. Deliverable = accuracy budget with receipts (verify QL2 vertical spec ~10cm RMSE against the actual Eaton Co tile report; NAIP horizontal; canopy from returns; 2017-18 staleness named as the capture-role gap) + the pipeline design. Q-002 answer folder is the base — extend, don't redo.
  5. LIGHT/SKY: research DONE (previous head ran it 2026-08-06 eve) — banked as research/questions/Q-004-light-sky-render/ANSWER.md with 2025-2026 receipts. BUILD the answered slice: line-sweep horizon lighting (one CPU sweep = soft shadow + AO, and the sweep IS the sunshed solver — log it like any solver), Grena3 sun-from-date via klausbrunner/solarpositioning (JVM lib), Hosek-Wilkie dome (or gradient v1), aerial-perspective fog — all per-vertex through the existing painter. Charter it; capability half lives in factored-ui, sunshed half in plot-twin solvers.
  - GPU RULING (Jim, 2026-08-06 late): NO remote GPU for now — Jim is waiting on Genesis to be the coordinator of remote GPU jobs. Capture/splat compute runs on the WORKSTATION GPU: NVIDIA RTX 2000 Ada Laptop, 8GB VRAM (nvidia-smi verified; CUDA-capable, so gsplat/nerfstudio and Brush both viable). 8GB fits per-structure yard captures (splat training of single buildings/scenes typically &lt;8GB; MILo-class mesh-in-loop may not — TEST at small scale first). If a job genuinely exceeds 8GB, REPORT to Jim — do not rent. (vastai CLI + key + illuminant box-spec exist on this machine as future context; the 48GB floor in that box-spec was illuminant's video-model requirement, NOT a plot-twin need.)
  - lane 1 addendum (EYES gains CV, Jim charged 2026-08-06 late): build computer-vision feature extraction so verification relies less on pure VLM eyeballing — deterministic numbers first, VLM only for what numbers can't say. Features: edge/silhouette maps vs heightfield-predicted skyline; entity silhouette IoU vs footprint projected through the camera; shadow-direction estimation vs solver sun azimuth (lighting correctness as a number); histogram sanity. Precedent to reuse: illuminant ported its silhouette-IoU gate to plain JVM BufferedImage (no OpenCV) — same pattern, see illuminant DECISIONS 2026-06-25 item 4.
  - lane 5 addendum (Jim Q&A 2026-08-06 late): two checks join the light/sky charter — (a) SkSL runtime effects (per-pixel programs INSIDE Skia, run on CPU backend too, so headless floor survives) — verify perf on the drawVertices terrain path before assuming vertex-color-only; (b) texture draping — drawVertices takes texCoords, so NAIP orthophoto as ground texture + baked lightmap (horizon-sweep at finer-than-vertex res) = per-pixel looks fully inside Skia. Textures-not-splats is the canvas-native smoothing answer.
  - lane 4 addendum (phone capture, Jim confirmed interest): the missing piece is OURS — a mesh importer: phone-app mesh export (Scaniverse: free, on-device capture→mesh today) → scaled/georegistered → capture-signed entity rows. Trees are splatting's worst case (foliage ≠ clean surface) — capture MEASURES height/spread for the typed trunk+canopy tree; solid structures (shed/pergola/boulder) import near-perfect.
  - lane 4 addendum (illuminant connection): if capture needs GPU compute (splat→mesh training does), do NOT grow compute plumbing — reuse illuminant's remote-GPU-job control-plane shape (initiate→provision pinned container per infra/gpu-job/box-spec.yaml→watch→ingest artifact; ruling in illuminant DECISIONS 2026-06-25). Q-004 verdict: splats are CAPTURE only (splat→mesh matured: MILo TOG 2025, consumer apps export meshes on-device); splats never enter world state, never the renderer.
- ladder told to Jim (~a week of runs to "walk your parcel, looks good"): light+sky → real ground from LiDAR → first-person walk; lanes 4+5 are the research feet under the first two rungs.
- release quirk to know: kotlin-compose-v0.18.0 tag push did NOT auto-fire CI (cause unknown; July tags fired fine) — manual `gh workflow run "kotlin-compose publish" --ref <tag>` worked; Pages CDN build then hung "building", fixed by `gh api -X POST repos/jjrasche/factoredui/pages/builds`. Watch for repeat next release.
- prior state (first two runs, all merged):
- SECOND overnight run COMPLETE — charters 5-8 all lead-verified by fresh re-runs and MERGED (merge authority ruled to lead); main @ 94afcfd holds the full loop:
  - charter 5 headed fps: **PASS** — 131fps @ 100K triangles on GPU-backed window (headless was 17.6) — batched-painter rebuild graded PASS, no escape hatch needed; kotlin-compose 0.17.2-batched-SNAPSHOT published to mavenLocal; branch build/scene3d-batched-painter @ c0ad6b2 stays UNMERGED in factored-ui (owner's merge)
  - charter 7 provenance+cost: **PASS, merged** — Genesis-shaped refs (role-tagged RowRef on the later row), op-status rows, position-diffs cite their op; D8 flow-field cache (placement test 6.5s→0.08s); shared geometry module; CAPTURE writer role (D-013)
  - charter 8 terrain rows: **PASS, merged** — BaseTerrainRow + TerrainDiffRow, grid is a projection, solver side-channel deleted; toy log w/ terrain = 4.3MB; cell resolution ratified into D-011 (fixed 10cm, adaptivity stays in optimizer search)
  - charter 6 walkable: **PASS, merged** — render module (pure projection→scene3d spec, violation markers), desktop app w/ orbit camera (:app:run), receipt PNG verified twice (pre- and post-terrain-merge, identical scene); render decimation factor 4 → 101K triangles at the painter's proven band
- run stats: 76/76 tests green on final main gate; 6 lanes over 2 nights, 0 rejected, 2 steered (walkable post-merge adaptation, terrain chained on provenance agent)
- NEEDS JIM: (1) merge/keep decision on factored-ui branch build/scene3d-batched-painter (Jim's repo, Jim's merge); (2) optimizer-PROPOSED regrades — measured ground is CAPTURE-only today; design-time earthworks need a ruling (distinct row type?); (3) smaller: standing render decimation factor, per-family violation-marker scaling, RejectionRow's embedded op now redundant w/ refs, int16 terrain quantization parked in favor of exact replay
- worktrees still up: plot-twin-build-{schema,solvers,op-pipeline,provenance,walkable}, factored-ui-build-scene3d — prune after Jim reviews
- status: stopping place — walk the toy plot: `bash gradlew :app:run` on main
