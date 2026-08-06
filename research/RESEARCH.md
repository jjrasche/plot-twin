# RESEARCH — live conclusions

| id | conclusion | status | scope | grounding |
|---|---|---|---|---|
| Q-001 | Three-tier inner loop: op-row constraint compile → coarse CP-SAT topology → seeded local search on exact polygons; LLM emits relational ops from a closed set, never coordinates (LLM-coordinates ≈ random in ablations) | ANSWERED(2026-08) | site-layout optimization, one parcel | [ANSWER](questions/Q-001-llm-solver-hybrid/ANSWER.md) |
| Q-002 | Terrain already solved free: QL2 LiDAR (~2.2 pts/m²) + USGS 1m DEM + 60cm 4-band NAIP cover the Delta Twp parcel; aerial-first typing pipeline; $0 one-day v1 experiment specced | ANSWERED(2026-08) | the home parcel, Delta Twp, Eaton Co MI | [ANSWER](questions/Q-002-agentic-capture/ANSWER.md) |
| Q-003 | Current scene3d FAILs at terrain scale (6.9fps @ 100K triangles; killers: per-frame sort + boxed assembly, not projection); rebuild = batched drawVertices inside Compose Canvas, effort M; GPU interop rejected (blinds captureToImage) | ANSWERED(2026-08) | factored-ui desktop; wasm/Android unverified | [ANSWER](questions/Q-003-scene3d-rebuild/ANSWER.md) |
