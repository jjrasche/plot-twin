---
id: Q-004
trigger: owner
spawned-by: D-008
feeds: D-008
---

# Sun, sky, splats, and what's new — for the Skia/CPU-vertex renderer

**Smallest slice to "sun + sky that looks good": one CPU sweep + one color pass, no new
architecture.** Sun vector from date/lat (Grena3 via klausbrunner/solarpositioning); one
line-sweep over the heightfield in the sun's azimuth yielding per-cell soft-shadow fraction AND
ambient occlusion in the same pass — that sweep IS the sunshed solver; vertex color =
albedo × (N·L × shadowFrac × sunColor + AO × zenithColor) through the existing batched painter
unchanged; sky = Hosek-Wilkie on a ~200-vertex dome (or a gradient rect for v1) via the same
drawVertices path. Cacheable per (date, hour).

Constraint: per-vertex colors on chunked float buffers via Skia drawVertices; no fragment
shaders; 10cm 2.5D heightfield; sun from date+lat; shadows = sunshed solver made visible.

## Q1 — terrain lighting without fragment shaders (ranked)
1. **Line-sweep horizon lighting** — sweep grid rows along sun azimuth with a monotone occluder
   stack; O(n), one pass gives hard shadow + soft term + AO; pure CPU over exactly our data;
   render feed and sunshed solver are the same function. Reusser,
   https://rreusser.github.io/notebooks/line-sweep-terrain-lighting/ (~2021, builds on
   Timonen & Westerholm 2010). Proven-in-tooling.
2. **Fourier-compressed horizon maps** — Fritsch et al., HPG **June 2025**: per-cell
   horizon-vs-azimuth as truncated Fourier series; beats classic 8-direction maps at equal
   memory. Their runtime is shader-side, but the REPRESENTATION is stealable as our precomputed
   sunshed store (evaluate series at sun azimuth per vertex on CPU).
   https://graphics.tu-bs.de/publications/fritsch2025fast. Peer-reviewed.
   State of practice confirmed: horizon maps weren't replaced, they got compressed.
3. **Per-vertex AO + bent normals baked into vertex colors** — still the standard no-shader
   answer (polycount wiki; Unity ships mesh AO baking in 2026.1:
   https://docs.unity.com/en-us/asset-transformer-sdk/2026.1/manual/functions/bakeao). Same
   horizon integral as #1/#2 — one precompute feeds shadow and ambient.
4. **Soft shadows from horizon angles** — fraction of the sun's 0.53° disc above the horizon
   angle (smoothstep over ±0.27°); binary shadowing would alias at 10cm cells.
   https://arxiv.org/pdf/2005.06671 (2020); Snyder & Nowrouzezahrai EGSR 2008.

## Q2 — sky + sun position
1. **Sun position: solved; pick by convenience.** SPA ±0.0003°, Grena3 ~0.01° and ~10× faster —
   both dwarf what a 10cm grid can express (0.1° elevation error ≈ 5mm shadow shift on a 3m
   structure). JVM library: https://github.com/klausbrunner/solarpositioning (active 2025,
   implements both + sunrise/sunset). Include atmospheric refraction near horizon (SPA does) —
   visible in long evening shadows. NREL SPA: https://docs.nlr.gov/docs/fy08osti/34302.pdf.
2. **Sky dome: Hosek-Wilkie (2012) per-vertex on a coarse dome is still the right cheap
   answer.** Successor (Wilkie et al. 2021 "Prague" fitted model — post-sunset radiance, aerial
   perspective; shipping in LightWave 2025) costs hundreds of MB of fitted data — wrong trade
   for a yard twin vs Hosek-Wilkie's few-KB tables. Impl refs:
   https://github.com/diharaw/sky-models.
3. Post-2024 sky research went learned/capture-based (https://arxiv.org/html/2412.11883v1,
   Dec 2024) — doesn't fit the CPU constraint; noted, not recommended.
4. **Aerial perspective**: classic exponential per-vertex fog toward horizon color — the
   no-shader stand-in for attenuation; biggest single "outdoors" cue after shadows.

## Q3 — Gaussian splatting: capture yes, renderer no
1. **Splat-as-renderer doesn't fit Skia canvas** — 2025 work moves toward hardware pipelines
   (https://arxiv.org/html/2505.18764v1, May 2025); CPU trainers exist (OpenSplat) but
   depth-sorted alpha-blended 10⁵-10⁶ primitives through a canvas API won't be interactive.
   Verdict: splat → mesh/heightfield → typed state is the only architecture-consistent use.
2. **Phone-video → splat is practical consumer tooling now**: Scaniverse (free, on-device,
   exports mesh OBJ/GLB/LAS), Polycam, KIRI Engine (in-app 3DGS→mesh since Nov 2024), Brush
   (open-source, no CUDA), gsplat/nerfstudio (dominant open trainer, NVIDIA). Failure modes for
   yards: blank lawns, exposure swings.
3. **Splat → mesh matured**: SuGaR (2024) → 2DGS/GOF (2024) → **MILo, ACM TOG/SIGGRAPH Asia
   2025 (https://dl.acm.org/doi/10.1145/3763339)** — mesh extracted differentiably DURING splat
   training; current quality leader, code available. Meshes then sample to the 10cm heightfield
   + entity polygons; splats never enter world state.
4. **Terrain/outdoor scale is active 2025-2026** (LODGE May 2025, LiV-GS Nov 2024, SF-Recon Nov
   2025, NVIDIA GTC DC 2025 city-scale session) — a 2-acre yard sits below these scales;
   realistic workflow = per-structure captures stitched onto the LiDAR heightfield.

## Q4 — newer than a Jan-2026 cutoff
1. **Neural terrain representations became a benchmarked subfield June 2026**
   (https://arxiv.org/abs/2606.00404): terrain-specific models because image-codec assumptions
   fail on heightfields — derivative fidelity matters (exactly what shadow/flow solvers
   consume). Future compressed store/LOD at corridor scale; validates exact heightfield as
   solver ground truth.
2. **Mesh-in-the-loop consolidated fast**: MILo (TOG 2025) + "From Blobs to Spokes" (Apr 2026,
   https://arxiv.org/pdf/2604.07337) + 2D-SuGaR (May 2026) — a Jan-2026 model would still say
   "splat→mesh quality iffy"; corrected claim: it's now good enough to be the default
   capture→typed-state bridge.
3. **PLANING (Jan 2026, https://arxiv.org/pdf/2601.22046)**: streaming triangle+Gaussian hybrid
   — the field converging on plot-twin's stance (explicit geometry as truth, splats as looks).

## What most contradicts a Jan-2026 model
1. Horizon maps got Fourier-compressed, not replaced (HPG June 2025).
2. Splat→mesh is no longer the weak link — consumer apps export meshes on-device.
3. Terrain-specific neural compression exists and argues derivative-preserving representations —
   an argument FOR our exact heightfield.
