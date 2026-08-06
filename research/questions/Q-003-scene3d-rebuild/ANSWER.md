# scene3d GPU rebuild — design + effort

**Baseline spike: FAIL (6.9 fps at 100K triangles) — the rebuild assumption is confirmed by
measurement. Recommended rebuild: a batched-painter renderer inside the Compose Canvas —
precomputed chunked vertex buffers, O(n) grid-order traversal instead of per-frame depth
sort, one Skia-level `drawVertices` array call per chunk. Effort: M (1–3 wk). A true
z-buffer GPU-interop renderer is L, per-platform, and breaks the SpecVisualCheck headless
floor — keep it as the escape hatch, not the plan.**

## Baseline spike (measured 2026-08-04, headless `ImageComposeScene` 800×600, desktop JVM)

Synthetic rolling-hills heightfield through `Scene3dMesh.prepare()` into the current
`Scene3dView`; 2 warmup + 5 timed frames, camera orbiting every frame:

| triangles | ms/frame | fps | band |
|---|---|---|---|
| 10,082 | 27.5 | 36.4 | PASS |
| 49,928 | 79.9 | 12.5 | WEAK |
| 100,352 | 144.3 | 6.9 | **FAIL** |

Receipt render: `factored-ui/packages/kotlin-compose/build/spike_terrain_100k.png` (correct
terrain, not a blank frame). Caveat: headless Compose tests rasterize on Skia CPU; a headed
GPU-backed surface speeds the *fill* but none of the CPU costs below, which are the
dominant term today.

## Where the time actually goes (second spike, same harness)

Replacing the 100K per-triangle `Path` draws with **one** `Canvas.drawVertices` batch
(compiles and renders correctly on desktop Compose — verified, receipt
`build/spike_terrain_drawvertices.png`) gave 103.5 ms/frame, instrumented:

| stage | ms/frame | fix |
|---|---|---|
| project 50,625 vertices (CPU) | 2.1 | none needed — projection is cheap |
| depth-sort 100K triangles | 31.4 | eliminate: grid-order traversal (below) |
| build boxed `List<Offset>`/`List<Color>` | 32.0 | eliminate: precomputed `float[]`/`int[]` |
| Skia raster fill + overhead | ~38 | GPU-backed surface in headed use |

So the painter's *algorithm* is not the bottleneck — the painter's *implementation* is:
per-frame sorting, per-triangle `Path` allocation, and boxed vertex assembly.

## Recommended design: batched painter (effort M)

Stays a Compose-Canvas primitive; every pixel lands in the Compose/Skia surface, so
`SpecVisualCheck` (headless render → PNG + shadow tree via `captureToImage`) survives
untouched, and the renderer contract — reads state, draws, owns no truth — is unchanged.

1. **Chunked terrain buffers.** Split the heightfield into grid chunks (~4–8K triangles);
   per chunk, prebuild `float[]` positions-template, `int[]` colors, `short[]` indices
   once per terrain edit (not per frame). Skiko exposes exactly this API on the native
   canvas: `org.jetbrains.skia.Canvas.drawVertices(VertexMode, float[], int[], float[],
   short[], BlendMode, Paint)` — verified against skiko-awt 0.8.18 (the version factored-ui
   builds against). `short[]` indices cap a chunk at 32K indices — chunking satisfies it.
2. **O(n) painter order, no sort.** A 2.5D heightfield on a regular grid draws correctly
   back-to-front by traversing rows/columns from the far corner toward the camera
   (choose traversal corner by camera-yaw quadrant); chunks draw far-to-near the same way.
   This deletes the 31ms sort and is exact for terrain self-occlusion.
3. **Entities stay entities.** Per-entity depth sort (a handful of meshes, thousands of
   triangles) is cheap; entities draw after the terrain chunks they stand in front of,
   ordered by chunk depth. Known limit: no per-pixel z, so *interpenetrating* meshes can
   mis-layer — acceptable in a 2.5D world where entities sit on terrain; the escape hatch
   below removes the limit if it ever bites.
4. **First-person capsule.** New camera mode (position+look, terrain-height follow with
   eye offset, near-plane clip on terrain) alongside the existing orbit camera — additive,
   no new rendering machinery.
5. **Frame budget (estimated from measured parts):** 2ms project + ~1ms traversal + batch
   submit; fill on GPU in headed use. 30fps+ desktop is plausible but the estimate rides on
   the fill, which headless CPU numbers can't prove — measure headed in week 1.

**Week-1 verification gates (the honest risks, all unverified today):**
- `drawVertices` on **wasm** (Compose-on-Skia/WebGL2) and **Android** (framework canvas has
  historic hardware-acceleration caveats for it). If either fails, that platform falls back
  to the Skiko-level call or the escape hatch.
- A headed desktop fps measurement to confirm the GPU-backed fill assumption.

## Rejected / deferred paths

- **Custom CPU z-buffer rasterizer** — excluded by charter; nothing measured here argues
  for it (projection is cheap, but per-pixel CPU shading of 810K-cell terrain is not).
- **True GPU interop (z-buffer 3D under Compose)** — desktop offscreen GL → texture →
  Compose image; wasm WebGL canvas layered under the Compose canvas; Android SurfaceView.
  Three per-platform backends, and pixels rendered outside the Compose surface are
  invisible to `captureToImage`, so the SpecVisualCheck floor dies unless each platform
  gains an offscreen-render + readback bridge. Effort **L (>3 wk — flagging to Jim per
  charter)**. Keep as the escape hatch if batched-painter misses 30fps headed or
  interpenetration artifacts matter; the batched design's chunk/buffer layer is the same
  data layout a GL vertex-buffer path would consume, so the work is not thrown away.
- **Third-party KMP engines (kool, wgpu4k)** — unvetted here (research agent for this died
  mid-flight); watch items, not inputs to this decision.

## Bands

- Baseline spike: **FAIL** (6.9 fps @ 100K tris; bands graded PASS ≥30 / WEAK 10–30 / FAIL <10).
- Rebuild effort: **M (1–3 wk)** for batched painter incl. first-person camera and the
  week-1 platform gates; escape-hatch GPU interop: **L**.

## Contradictions with locked decisions

None. "three.js only as perf fallback" stays dormant — the baseline FAIL is answered by the
rebuild, per Jim's factored-ui-is-the-path call. The renderer keeps reading projections and
owning no truth; violation overlays are unaffected (2D draws on top).
