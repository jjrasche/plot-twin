# LLM + solver hybrid optimization

**Verdict: three-tier inner loop — deterministic compile → coarse-grid CP-SAT topology solve →
seeded local-search polygon refinement — under the locked LLM outer loop. The LLM emits
relations, specs, and relaxations; never coordinates, never search strategy.**

## The seam, from evidence

- LLM-emitted **coordinates are no better than random**: Holodeck's ablation scores
  constraint-based placement 0.706 MRR vs 0.364 for LLM-direct coordinates (random = 0.369)
  ([Holodeck, arXiv 2312.09067](https://arxiv.org/abs/2312.09067)).
- LLMs are reliable at pairwise distance/visibility questions but collapse on free-space
  computation (1–3%), max-rectangle fitting (~30%), rotated-polygon collision, clearance
  buffering ([FloorplanQA, arXiv 2507.07644](https://arxiv.org/html/2507.07644v2)).
- Letting the LLM touch solver internals is the "heuristic trap": unverified bounds, broken
  completeness, over-constraining — for ~1.1× median speedup
  ([Formalize-Don't-Optimize, arXiv 2605.12421](https://arxiv.org/abs/2605.12421)).
- Every successful system converges on the same shape: LLM emits structure, classical
  machinery does geometry ([Co-Layout, arXiv 2511.12474](https://arxiv.org/abs/2511.12474);
  [HouseTune](https://arxiv.org/html/2411.12279v2);
  [Text-to-Layout](https://arxiv.org/html/2509.00543v1)).

This confirms the locked LLM-altitude decision and sharpens it: the seam sits at *relations
in, violations out* — nothing numeric-geometric crosses it in either direction except typed
violation records.

## Recommended architecture

1. **Compile (deterministic).** Op rows (`add_room` / `move` / `resize` / `reroute` /
   `relax` / `lock` with relational payloads) → CP-SAT model. Hard rules = hard constraints;
   soft rules = weighted penalty literals; `lock` = fixed assignment. Add a
   contradiction pre-check before solving — conflicting LLM constraints were Co-Layout's
   top failure mode, and they had no refinement loop for it (our relax loop is exactly the
   mechanism they name as missing).
2. **Coarse topology solve — OR-Tools CP-SAT**, seeded, single-worker (determinism).
   Entities as bounding regions on a ~2–5m grid (1–10K cells). CP-SAT's demonstrated
   envelope is ≤~50–60 entities on discrete encodings
   ([CDCL+CP-SAT facility layout, arXiv 2512.18034](https://arxiv.org/pdf/2512.18034)) —
   Jim's ~10–30 rooms/structures fit comfortably. Bonus: CP-SAT infeasibility cores *name
   the conflicting constraints* — ideal violation text for the LLM. CP-SAT beats ILP here:
   native reified/logical constraints, hard infeasibility proofs, free, and it is the
   representation LLM formalization is most accurate against (Formalize-Don't-Optimize's
   headline result).
3. **Fine refinement — seeded local search on exact polygons at 10cm**, warm-started from
   the coarse solution with a soft consistency penalty (Co-Layout's ablation-validated
   coarse-to-fine trick — nobody in this literature solves exact models at ~810K-cell
   resolution). Moves scored by the pure-function solvers (overlap, setbacks, cut/fill,
   slope — nonlinear terrain costs can't enter a linear model anyway). Acceptance is
   lexicographic: hard-violation count → weighted soft score → entity-ID tie-break.
   Matches the locked no-randomness rule; SA/local search is what the facility-layout and
   polygon-nesting literature uses for exact shapes
   ([SA continuous FLP](https://wseas.com/journals/bae/2024/d745107-052(2024).pdf);
   [SA polygon packing](https://arxiv.org/pdf/0809.5005)).

## Op vocabulary

**Proven-reliable emissions (keep):**
- Relational constraints from a small closed vocabulary — Holodeck's ten types
  (near/far, edge/middle, adjacent, face-to, aligned) are the template.
- Entity specs with attribute targets (Co-Layout): `add_room` carries type, area range,
  adjacency wishes, orientation preference — never coordinates.
- `relax` / reweight of a *named* rule, driven by violation text.
- Strategy hints as selection from an enumerated pool, not free-form
  ([HeurAgenix, arXiv 2506.15196](https://arxiv.org/abs/2506.15196) — its LLM only picks
  which heuristic applies next; a fine-tuned 7B does it nearly as well as GPT-4o).

**Banned emissions (proven failures):**
- Absolute coordinates. `move`/`resize` payloads are symbolic/relative ("toward east
  boundary", "to minimum area"); the optimizer resolves numbers.
- Any geometric computation (free space, clearance regions, centroids).
- Solver parameters, bounds, or search strategy.

## Contradictions with locked decisions

None. This grounds the existing FloorplanQA-based altitude decision and the
Formalize-Don't-Optimize inner-loop decision, and adds two refinements worth adopting:
(a) coarse-to-fine as the optimizer's internal structure, (b) a deterministic
constraint-contradiction pre-check before every solve.
