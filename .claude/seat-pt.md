# seat: pt — HEAD of plot-twin

Lead agent seat lives in-repo; the machine-wide board (~/.claude/brothers/agents/pt.md) holds
only a pointer here.

- role: lead — holds goal, charters build subagents, verifies by running, lands merges
- topology: orchestrator-worker (subagents build); brothers board only for cross-tab peers (common-ground)
- when: 2026-08-09T04:00Z
- seat: CLAIMED 2026-08-06 by pt-head

## RUN 5 IN FLIGHT (2026-08-08 night)

Jim's three priorities, verbatim charters at .claude/charters/overnight-{14,15,16}-*.md.
Baseline gate run fresh by the lead on main @ 482bff1: BUILD SUCCESSFUL, 122 tests 0 failures.

- lane 14 real terrain: address → 3DEP DEM + NAIP → log rows → walkable real parcel.
  Worktree ../.git-worktrees/pt-terrain, branch build/real-terrain. Google Maps prohibited.
- lane 15 eyes sky: sky-aware skyline (dome joins the gated spec), DEM-generic skyline,
  fix dome fan-banding + horizon dark band. Worktree pt-eyes-sky, branch build/eyes-sky.
- lane 16 earthworks: Q-005 implemented — regrade verb, surface ids, (date,surface) solvers,
  conservation invariant + ledger, three intent tests, Q-007 4D-BIM staging research.
  Worktree pt-earthworks, branch build/earthworks. Amends D-005/D-013 as charter-authorized.

## RUN 3 MORNING REPORT (2026-08-06 night → 2026-08-07)

Four lanes chartered, four delivered. Three merged to main, one verified-but-BLOCKED on a
factored-ui release. Every verdict below is the lead's own fresh re-run, not a worker's claim.

**RELEASE DONE — the render blocker is cleared (Jim authorized 2026-08-07).** kotlin-compose
**0.19.0** is published and live: camera drive merged into `il-scene-render-harness` (6c538a3),
tagged `kotlin-compose-v0.19.0`, publish workflow green, Pages deployed, pom returns 200 over
HTTPS. The tag DID auto-fire CI this time — last release's quirk did not repeat. Verified
before tagging by the lead's own runs: wasmJs compile green, desktopTest 132 tests 0 failures.
plot-twin repinned to 0.19.0 and **mavenLocal removed from all three modules** so a stale local
build can never shadow the published artifact. Eyes then merged; light/sky is running.

**KNOWN RED, not mine:** the general `ci` workflow on `il-scene-render-harness` fails and did so
before this release. Cause: the workflow deliberately runs a FILTERED subset (38 non-UI desktop
tests) while a suite-shrink guard demands at least 100, so the guard fires on a narrowing the
workflow itself performs. The publish workflow is separate and green. Fix is a judgment call —
drop the filter, or make the floor skip when `--tests` is present — so it is left for Jim.

### Landed on main
- **earthworks design** (b261702) — five questions, five recommendations with counterarguments,
  every number sourced. Docs only, schema untouched as railed.
- **snapshot rows + terrain projection cache** — the cache SHIPPED with five tests. Lead gate:
  BUILD SUCCESSFUL, 81 tests, 0 failures (76 before). No schema change. Genesis memo delivered
  to the board.
- **capture accuracy budget** (9822a9c) — real measured numbers from the actual Eaton work-unit
  checkpoint spreadsheet. PASS after ONE REWORK: the first commit appended a correction instead
  of fixing the figure, leaving the wrong number in the summary. Sent back, fixed, re-verified.

- **eyes + CV** (73b770f) — MERGED after the release cleared its pin. Lead gate on merged main:
  BUILD SUCCESSFUL, **95 tests, 0 failures** (90 eyes + 5 cache — nothing broke across the merge;
  eyes was branched before the cache landed, so this was a real cross-module check). Note the
  worker reported 133 tests where the XML said 90; class count matched, test count did not.
  Contact sheet reviewed by the lead's own eye: seven poses, terrain/greenhouse/pergola/path/
  markers all identifiable. All worktrees pruned, all branches deleted.
  - Contact sheet: `../.git-worktrees/pt-eyes/eyes/build/eyes_contact_sheet.png`
  - The worker marked its own shadow-direction check ADVISORY and excluded it from the verdict —
    scene3d has no sun pass and terrain is tinted by elevation, so the "darkest direction" tracks
    downhill, not the sun. The estimator math is gated in both directions with synthetic shadows;
    only the live reading is untrustworthy. It promotes when the light/sky lane lands a sun pass.
    That is exactly the honesty the charter asked for.
  - Legibility, seen by eye: violation markers are drawn at a fixed world size and swamp the
    pergola at walk height. Render-lane call — marker scale should probably follow camera distance.
  - `overhead` is really near-overhead; the pitch clamp keeps the camera shy of vertical.

- **light/sky BUILD** (charter 10) — MERGED. Lead gate: BUILD SUCCESSFUL, **122 tests,
  0 failures**. Rung one of the ladder is up: shadows swing correctly through the day, sky is
  mauve at the ends and blue at midday. The shadow-direction check is promoted from advisory to
  a real gate and the invented sun constant is gone. Contact sheets regenerate at
  `eyes/build/{eyes,light_sky}_contact_sheet.png`.
  - Lead ratified three decisions the lane recorded: sweep-serves-both and gradient-sky were
    charter-authorised; the site row is forced (no latitude, no sun) — all three are on Jim's
    review list, none were Jim-ruled.
  - **Defects the LEAD found by eye that the lane did not name** — sky dome shows concentric fan
    banding from its own triangulation, and a dark band sits at the horizon under the dome. Both
    in TASKS Now. The lane's self-review said "the dome renders behind the ground" and stopped
    there; a worker's eye-review is not a substitute for the lead's.
  - Honest gaps the lane DID name: fog not delivered (needs a per-frame hook 0.19.0 lacks);
    orbit-4 reads 31 degrees off because a swale trench out-darkens the greenhouse on that
    bearing; sweep-cost and paint-cost were measured separately and never composed into a frame.

### Lead's own process note for next run
Every charter gets: commit a checkpoint at every meaningful step, even broken. The eyes lane
stalled after an hour with everything uncommitted — the only real failure mode hit all night,
and it nearly cost the lane.

- run stats: 4 lanes, 4 delivered, 3 merged, 1 rework, 1 stall recovered, 0 rejected outright
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
