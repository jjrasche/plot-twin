---
id: Q-005
trigger: owner
spawned-by: D-011
feeds: D-011
---

# Moving dirt: the regrade op, the earthwork ledger, and two grounds

**The whole answer in one line: one geometry verb, one ledger projection, one surface parameter.**
A regrade is an op that names a subject, a ground form, and prose extent — never a surface; the
surface is solved. Cut/fill volumes are a *projection* (an earthwork ledger), not violations;
rules read the ledger and emit violations against it. Spoil is conserved by a hard invariant with
one named escape (haul off). Terrain rows gain a surface id, and solvers take `(date, surface)`
the way they already take a date. Purpose selects a rule bundle, not a verb.

## Q1 — Regrade op slots: subject + form + prose; the surface is solved

The op vocabulary carries no coordinates, so "a polygon + a target surface" is not available to
author: a target surface *is* geometry. What an owner can say is a subject, a form, and an extent
in prose. Everything metric is downstream.

- **Authored**: subject (an existing named region/entity, or a name being introduced), ground form
  (pond · terrace · swale · pad · berm · plain grade), extent prose, an optional relation to
  another entity, and an optional explicit spoil destination.
- **Solved**: the boundary polygon (when not inherited), per-cell target elevations, side slopes
  *within* the form's allowed band, and the spoil destination when not named.

The allowed bands are real published numbers and they differ by form, which is why the form slot
earns its place: excavated pond side slopes no steeper than 1H:1V, embankment slopes ≥2H:1V each
with the upstream+downstream sum ≥5H:1V (NRCS CPS 378 Pond, July 2022,
https://www.nrcs.usda.gov/sites/default/files/2022-09/Pond_378_NHCP_CPS_2022.pdf); grassed
waterway side slopes flatter than 2H:1V (NRCS CPS 412, Sept 2020,
https://www.nrcs.usda.gov/sites/default/files/2022-09/Grassed_Waterway_412_CPS_9_2020.pdf);
terrace ridge minimum 3 ft wide (NRCS CPS 600, Sept 2020,
https://www.nrcs.usda.gov/sites/default/files/2022-10/Terrace_600_CPS_9_2020.pdf); temporary
excavation faces by soil type — Type A 3/4:1 (53°), Type B 1:1 (45°), Type C 1½:1 (34°), and any
cut over 20 ft deep must be designed by a registered professional engineer (OSHA 29 CFR 1926
Subpart P App. B, Table B-1,
https://www.osha.gov/laws-regs/regulations/standardnumber/1926/1926SubpartPAppB).

**RECOMMENDATION — add exactly one verb, `regrade`, with slots subject · ground-form ·
extent-text · destination · spoil-destination.** Reuse the existing slot machinery; add
ground-form as a slot kind. No coordinates, no target surface, no cut depth in the op.

*Strongest case against*: the op vocabulary is deliberately tiny, and `add_room` with a
pond/terrace room kind already covers every form that creates a new named space — leaving only
"flatten the yard so it drains away from the barn," which could be squeezed into `resize`. The
answer: that leftover is the *majority* of real earthwork (drainage correction, pad prep, slope
easing), and forcing it through `add_room` invents a phantom room for every grading change and
pollutes the entity namespace permanently. A decade-held model cannot afford that.

## Q2 — Cut and fill: a ledger projection, and cost is not a violation

Volumes fall out of proposed-ground minus measured-ground, but the naive subtraction is wrong in
a way the industry has already been burned by. Excavated soil swells; compacted fill shrinks; both
are measured *from the bank (in-place) condition*. Load factor = 1/(1+swell), shrinkage factor =
1 − shrinkage, and bank volume × shrinkage factor = compacted volume (Peurifoy earthmoving
formulation, KSU CE417 ch. 2, eqs. 2-4 to 2-9,
https://faculty.ksu.edu.sa/sites/default/files/2.ce417-note-ch2.pdf). Real plan sets carry the
adjustment as a note: "25% additional volume has been added to embankment to account for
shrinkage" (NDDOT plan sheet, in UGPTI *Earthwork and Mass Diagrams*, 2011,
https://www.ugpti.org/dotsc/engcenter/downloads/2011-03_EarthworkAndMassDiagrams.pdf).

The factor is not a constant and its spread is enormous: shrink 10–18% for clays, 11–35% for
sands, 20–25% for residual soils, 5–22% for rocky/gravelly; swell 30–50% for clays, 3–45% for
sands, 5–40% for rocky/gravelly (Crooks, *Application of Shrinkage and Swelling Factors on State
Highway Construction*, Auburn MS thesis 2013,
https://etd.auburn.edu/bitstream/handle/10415/3532/). The same work shows the consequence: a job
that looks balanced becomes 25,000 yd³ short once a 25% shrink is applied — about $375,000 at
~$15/yd³. **A cut/fill answer without a stated factor and its provenance is not an answer.**

Topsoil is a second, separately conserved material, not part of the bulk number. Iowa DOT
requires design to separate topsoil quantities from excavation quantities "to avoid 'double
payment'" and sets 4 in minimum replacement, 8 in preferred
(https://iowadot.gov/design-manual/chapter-10-roadside-development-and-erosion-control/10a-1-topsoil-replacement);
stripping depth is set by soil cores, stockpiles must avoid slopes and natural drainageways, and
respread is min 2 in compacted on 3:1 slopes, 4 in on flatter (Illinois Urban Manual CS 752,
https://p2infohouse.org/ref/02/01524/urb752cs.htm).

Does "40 cubic yards trucked in" fit the violation shape (rule + location + magnitude)? Honestly:
the *measurement* does not — a net import is a whole-design scalar with no location, and inventing
a centroid would corrupt both the ranked emit and the renderer's location+magnitude overlay. But
the *judgment* does, the moment a rule exists ("balance earthwork on site", hard or soft), and its
location is the fill region that cannot be served.

**RECOMMENDATION — keep `Violation` unchanged; add an earthwork ledger as a projection.** The
ledger is a derived view over the log (bank cut · compacted fill · loose spoil · topsoil stripped
and respread · net import/export · haul volume-distance), carrying the shrink/swell factors *and
whether they came from a site test or an assumption*. Rules read the ledger; violations come only
from rules. Cost is neither: it is a soft-rule score term computed from the ledger's haul term,
which is exactly how the trade prices it — mass haul is volume × distance, and hauling beyond the
free-haul distance is paid as overhaul (UGPTI, above). Where the factor is an assumption, the
ledger reports a band, and a hard no-import rule is evaluated at the pessimistic end.

*Strongest case against*: the architecture says solvers return violations, and adding a second
return shape is exactly the kind of drift that erodes a locked design. The answer: the ledger is
not a solver return — it is a projection, and projections are already plural. The solver leaf
still returns only violations; it reads the ledger the way it reads current state.

## Q3 — Spoil: conserved mass, placed by the optimizer, scored by the same rules

The charter's guess is confirmed almost verbatim by the standards. Grassed-waterway plans **must**
include "Disposal requirements for excess soil material" as a minimum plan element (CPS 412).
Pond design enumerates the legal fates of excavated material — spread uniformly to a height not
exceeding 3 ft with the top graded to a continuous slope away from the pond; placed at its natural
angle of repose at a distance equal to the pond's depth but not less than 12 ft from the edge;
shaped to a designed form that blends visually with the landscape; used for low embankment and
leveling; or hauled offsite — and instructs the designer to "consider runoff flow patterns when
locating the excavated pond and placing the spoil," with a Visual Resource Design section
requiring that the excavated material's shape "relate visually to their surroundings" and be
"smooth, flowing, and fitted to the adjacent landscape rather than angular geometric mounds"
(CPS 378). That is a watershed rule and a viewshed rule over a placed mass — the same two solver
families any entity faces.

The placement problem is therefore concrete: given loose volume V = V_bank × (1+swell), find a
receiving region and surface satisfying (a) setback ≥ excavation depth and ≥12 ft from the edge,
(b) side slopes ≤ angle of repose if uncompacted, (c) D8 flow re-run on the *resulting* proposed
ground still passes the drainage rules, (d) viewshed rules pass, (e) preference for regions that
wanted fill. Spoil geometry is closed-form once repose angle R is known: triangular bank
B = (4V / (L·tan R))^½, conical pile D = (7.64V / tan R)^⅓ (KSU CE417 ch. 2, eqs. 2-10 to 2-13).
Topsoil gets its own placement: stockpile and respread on disturbed areas (CPS 378 and CPS 600),
with excess routed to named uses rather than dropped (Iowa DOT Design Manual 5B,
https://iowadot.gov/design-manual/chapter-5-earthwork/5b-earthwork-excavation-procedures).

What breaks if spoil may vanish, concretely: (1) a design passes every solver and is unbuildable —
the pond is verified, and on dig day 400 yd³ lands somewhere nobody modelled, which is precisely
the failure the twin exists to prevent; (2) the watershed solver lies, because the berm the spoil
actually becomes is a real flow barrier the flow field never saw; (3) the optimizer is biased
toward free-looking designs, since hauling offsite is the expensive fate and a vanished pile costs
nothing.

**RECOMMENDATION — mass conservation is a hard invariant checked at op resolution, not a soft
rule.** Every regrade resolution must close: bank cut × (1+swell) = loose placed + hauled off, and
topsoil stripped = respread + stockpiled + hauled off, each term explicit. `haul-off` is the
always-available named destination — conservation holds, the cost becomes visible, nothing
disappears silently.

*Strongest case against*: a hard invariant makes every regrade unresolvable until a spoil
destination exists, which blocks the toy loop and turns "flatten this corner" into a multi-step
negotiation. The answer: the named `haul-off` destination *is* the escape hatch, and it is the
standards' own fifth option. The invariant forbids not the easy answer, only the unstated one.

## Q4 — Two grounds: one row type, a surface id, and `(date, surface)` solvers

Three questions expose that "which ground does a solver read" has no single default. *Does the
greenhouse flood?* reads proposed. *How much dirt moves?* reads both. *Did the contractor build
it right?* reads measured, afterwards. So the terrain projection must be **parameterized by
surface identity** — exactly as solvers are already parameterized by date, because sun, shade and
deciduous cover are seasonal. Date and surface are the two projection parameters; neither has a
safe default.

Given a required surface parameter, the type-level split buys nothing: two proposals still need
ids to be told apart, so a field appears regardless. Measured is the distinguished surface; every
proposal is a named surface branched from a stated measured baseline. The writer role then follows
from the surface (measured ⇒ capture, proposed ⇒ optimizer) rather than being a free signature —
a tightening of the existing two-writer-roles ruling, not a loosening.

New capture contradicting a built proposal is the good case, not the hard one. The measured
surface simply gains a capture row; the proposal is never rewritten — it stays what was intended,
which is what makes the log worth keeping. The difference between them is the as-built deviation,
and it is the *same subtraction* that produced the cut/fill ledger before the build. One function,
two uses: pre-build it estimates volume, post-build it measures construction deviation. This is
the surveying profession's own distinction — design drawings vs as-built/record drawings — and it
survives here without a second mechanism. A realization row marks a proposal surface as built
against the capture that confirmed it, so it stops competing as a candidate future; nothing is
deleted.

**RECOMMENDATION — one terrain row family carrying a surface id (and, for proposals, the measured
baseline it branched from); solvers take `(date, surface)` with no default; a realization row
retires a proposal once capture confirms it.**

*Strongest case against*: a single type lets a solver read a proposal as if it were measurement —
the exact provenance failure the two-writer-roles ruling exists to prevent — whereas separate
types make that a compile error. The answer: the surface parameter is required and unvalued, so
the mistake is not silent; and the type split does not actually close the hole, because it still
needs a plan id to separate competing proposals, arriving back at a field.

## Q5 — Purpose changes the rules, not the op

The strongest single piece of evidence is inside one standard: "Design all farmable terrace slopes
no steeper than 5:1 in order to allow safe operation of farming equipment. For nonfarmable terrace
slopes, the steepest slopes allowable are 2 horizontal to 1 vertical" (CPS 600). Identical earth
form, identical construction act; the *intended use* moves the number by more than a factor of two
and changes nothing else.

Across standards the pattern holds. Pond, grassed waterway and terrace are the same physical act —
shape ground, place spoil, stabilize with vegetation — and every difference between the three
documents is criteria: design storm (10-yr/24-hr for waterway and terrace; a table keyed on
drainage area and dam height for ponds), side slopes, freeboard (0.5 ft for waterway, 1 ft for an
excavated pond), outlet requirements, and spoil disposal. And residential grading adds its own
number to the same act: lots must fall not fewer than 6 in within the first 10 ft away from
foundation walls, with impervious surfaces within 10 ft sloped at least 2% away (IRC R401.3,
2021 and unchanged 2012–2024, https://codes.iccsafe.org/s/IRC2021P3/chapter-4-foundations/IRC2021P3-Pt03-Ch04-SecR401.3).

**RECOMMENDATION — purpose is a rule-bundle selector, not a second verb.** The ground-form slot's
only job is to pull a named bundle of rule rows in at compile time, where compilation already
happens once at append. One geometry verb, many bundles.

*Strongest case against*: purpose changes which *solvers* run, not only thresholds — a pond needs
impoundment and spillway routing that nothing else needs, a pad needs bearing and setback, a swale
needs channel capacity. That is more than "just rules." Partial concession: the runner already
fans out over rules, so "which solvers run" is already a function of which rules exist, and a
bundle that carries a spillway rule *is* the mechanism. But this only holds while every
purpose-specific check maps onto the four existing solver families, and spillway/impoundment
routing does not — it is hydraulic routing, not D8 accumulation. Expect a fifth leaf family the
first time a pond is real; that is a solver-kit question, not an op-vocabulary question.

## Proposed row/op sketch (types and fields, not code)

- **op verb `regrade`** — slots: `subject` · `ground-form` · `extent-text` · `destination` ·
  `spoil-destination`. New slot kinds: `ground-form`, `spoil-destination`.
- **`ground-form`** values: `pond` · `terrace` · `swale` · `pad` · `berm` · `grade`. Each maps to a
  named rule bundle appended at compile time.
- **`spoil-destination`** values: a named region, or `haul-off`. Absent ⇒ solved by the optimizer.
- **surface id** on the terrain rows: `measured`, or `proposed(<name>)`. Proposed rows additionally
  carry the measured baseline sequence they branched from. Writer role derives from the surface.
- **`earthwork` row** (written at op resolution): op seq · surface · bank-cut · compacted-fill ·
  loose-spoil-placed · haul-off · topsoil-stripped · topsoil-respread · haul volume-distance ·
  shrink factor · swell factor · factor provenance (`site-test` | `assumed`).
- **`surface_realized` row**: proposal surface · the capture sequence that confirmed it.
- **earthwork ledger**: a projection over the log, per op and per plot; input to balance/import
  rules and to the objective's cost term. Not a solver return type.
- **new rules the bundles need**: side-slope limit · earthwork balance / net import · spoil
  setback · topsoil respread depth · foundation fall-away. All express as existing rule rows.

## Code shipped: none

The one genuinely uncontroversial piece — a surface identifier on terrain rows, which both the
one-type and two-type answers require — lands in the world-state schema, which another lane holds
tonight. Flagged for sequencing rather than written.
