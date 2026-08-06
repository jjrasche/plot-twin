# CHARTER — scene3d batched painter rebuild (factored-ui)

## Where it fits
Repo: C:\Users\rasche_j\Documents\workspace\factored-ui (NOT plot-twin). Implements
docs/research/scene3d-gpu-rebuild.md (plot-twin repo) — read it first, it is the design.
Baseline measured FAIL: 6.9fps @ 100K triangles; profiled killers are the per-frame z-sort
(31ms), boxed vertex assembly (32ms), per-triangle Path draws. The fix is NOT GPU interop
(breaks the SpecVisualCheck floor — captureToImage sees only the Compose surface).

## Deliverable
Batched painter inside the Compose Canvas: chunked prebuilt float[] buffers through Skiko
array-level drawVertices (signature verified against skiko-awt 0.8.18 in the memo), O(n)
grid-order traversal replacing the sort. Character/bone rendering must not regress. First-
person capsule is OUT of scope (additive later).

## Verify
- Existing scene3d desktop render tests + SpecVisualCheck floor stay green (fresh full
  build, no cached green).
- Re-run the memo's fps spike harness on the new painter, same synthetic meshes.

## Bands
PASS: ≥30fps @ 100K triangles, all existing tests green. WEAK: 10–30fps (report the profile
split). FAIL: <10fps, any render-test regression, or the design forces pixels outside the
Compose surface.

## Rails
Isolated worktree off current HEAD (26a096c); branch `build/scene3d-batched-painter`. Do NOT
touch the existing unpushed branch commits (111e5b9..26a096c awaiting Jim's go). No pushes,
no version bump, no publishes. The memo's two named unknowns (wasm/Android drawVertices,
headed GPU-fill) are OUT of scope — desktop is the target; note them in the report. Report:
diff --stat, verbatim gate output, fps numbers vs bands, contradictions, questions.
