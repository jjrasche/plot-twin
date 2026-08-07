# Agentic capture: photos + GPS + satellite → typed world state

**Verdict: the terrain layer is already solved — free QL2 LiDAR + 1m DEM + NAIP cover the
parcel today. The v1 capture pipeline is aerial-first: segment-geospatial + DeepForest on
NAIP for typed footprints, LiDAR canopy-height model for heights, phone photos only as
evidence for what aerial can't see. Whole-parcel photogrammetry/splatting is the expensive,
fragile part — defer it. SAM 3D is weeks-old research-grade; keep it off the critical path.**

## What terrain data exists for the parcel (Delta Twp, Eaton Co, MI)

Downloadable today, $0, ~1 hour total:

- **LiDAR point cloud**: QL2, ~2.2 pts/m², flown Dec 2017 – Apr 2018 (leaf-off), 19.6cm
  vertical accuracy — Eaton County is in the 2016–2017 NRCS 30-county Michigan project
  under MiSAIL's statewide QL2 completion
  ([NOAA InPort 55315](https://www.fisheries.noaa.gov/inport/item/55315);
  [MiSAIL](https://www.michigan.gov/dtmb/services/maps/misail)). Clip an AOI via the
  [NOAA Data Access Viewer](https://coast.noaa.gov/dataviewer/) or pull tiles from
  [USGS LidarExplorer](https://apps.nationalmap.gov/lidar-explorer/).
- **1m bare-earth DEM**: USGS standard product wherever QL2 exists
  ([collection](https://data.usgs.gov/datacatalog/data/USGS:77ae0551-c61e-4979-aedd-d797abdcde0e)).
- **NAIP imagery**: Michigan 2022, 4-band RGB+NIR, 60cm, leaf-on
  ([InPort 70528](https://www.fisheries.noaa.gov/inport/item/70528)); a 2024 flight is
  likely but unconfirmed. Eaton County GIS may hold 6–12in county orthos (unverified —
  one lookup).
- No Eaton refresh is in the FY25 3DEP selections
  ([DCA](https://www.usgs.gov/3d-national-topography-model/fy25-3dep-data-collaboration-announcement-dca-selected-projects)) —
  the 2017–18 QL2 is the best available and it comfortably feeds the 10cm-grid terrain
  decision (heights interpolate; the grid resolution is about entities, not LiDAR density).
- Vintage caveat: 2017–18 point cloud — trees have grown or gone; leaf-off underestimates
  deciduous crowns.

## Toolchain: proven vs. novel

| Stage | Tool | Status |
|---|---|---|
| Footprints + typing from imagery | [segment-geospatial](https://github.com/opengeos/segment-geospatial) (Grounding DINO + SAM → georeferenced GeoJSON, text-prompted) | Proven, open |
| Per-tree detection on NAIP | [DeepForest](https://github.com/weecology/DeepForest) (pretrained NEON crowns, F1 0.73–0.95 — [paper](https://besjournals.onlinelibrary.wiley.com/doi/full/10.1111/2041-210X.13472)) | Proven, open |
| Heights | PDAL/lidR: first-return DSM − DEM = canopy-height model | Proven, trivial |
| Building footprints | Microsoft Building Footprints / OSM (already cover Eaton Co) | Proven — don't re-detect |
| Yard photogrammetry | OpenDroneMap/WebODM (georeferenced ortho + DSM), COLMAP | Proven, but needs drone/photo effort |
| Splatting | Nerfstudio splatfacto, Polycam/Luma | Proven as *visualization*; splats aren't typed entities |
| Splat-from-Google-Earth | [arXiv 2405.11021](https://arxiv.org/abs/2405.11021) | Research-grade, ToS-gray, rural 3D coverage doubtful |
| Single-photo object → 3D | [SAM 3D](https://arxiv.org/abs/2511.16624); aerial evaluation [arXiv 2512.22452](https://arxiv.org/abs/2512.22452) | Research-grade (Nov–Dec 2025); georeferencing unsolved |
| Aerial+ground fusion | Research literature only; pragmatic fusion = everything georeferenced, join by coordinates | Novel as turnkey, trivial as practice |

Reframe from the charter: "photogrammetry → segmentation → entity typing" inverts the cheap
order. The v1 decomposition is **aerial-first (segmentation+typing on existing rasters) →
LiDAR heights → ground photos as evidence attachments**; photogrammetry is v2, and only if
v1 proves 60cm imagery can't resolve the entities that matter.

## Cheapest v1 experiment ($0, ~1 day)

Output: typed entity rows — `id, type, centroid, footprint_polygon, height_m`.

1. Download LAZ clip + 1m DEM + NAIP tile + county parcel boundary (~1 hr).
2. CHM raster: first-return DSM at 0.5–1m minus DEM (PDAL/lidR, 1–2 hrs).
3. segment-geospatial on NAIP with prompts "tree"/"building"/"driveway" → GeoJSON polygons;
   DeepForest for per-tree instances (2–3 hrs). Expect fences and garden beds to FAIL at
   60cm — that's a finding, not a bug.
4. GeoPandas join: per polygon, zonal-stat the CHM (p95) → entity rows (~1 hr).
5. Ground pass (half-day): GPS-tagged phone photos of fences/terraces/beds; v1 places a
   point entity at EXIF GPS (±3–5m), types it with Grounding DINO, footprint hand-traced in
   QGIS over NAIP (~20 min for 2 acres).

The experiment answers the three questions that gate any spend: (a) does NAIP+samgeo yield
usable footprints at parcel scale, (b) does the 2017 CHM still match reality, (c) is phone
GPS good enough to auto-place ground entities. If fences/beds/currency fail → the single
likely next spend is a ~$300 drone flight through OpenDroneMap (2cm ortho + fresh DSM),
which makes the small stuff machine-detectable.

## Contradictions with locked decisions

None — this confirms the "USGS/Eaton County LiDAR first" decision with the concrete dataset,
and supports loop-before-capture: v1 capture is a day of scripting against free data, so
nothing about capture pressures the schema.

---

# Addendum — accuracy budget, pipeline, mesh importer (2026-08-06)

**Verdict: the vertical ground is better than advertised and it is not the problem. The
Eaton collection measured 6.2cm RMSEz on bare earth — 1.6× better than the 10cm QL2 spec —
and 9.2cm under vegetation. What is loose is everything else: horizontal registration is
1.0m RMSE and was never measured, 98% of our 10cm cells are interpolation rather than
measurement, the imagery that would place a footprint is good to ~2m at best, the whole
survey is eight growing seasons old, and the vertical datum it is expressed in is being
retired at the end of this year. Vertical precision is a solved input; position, currency,
and datum are the capture role's actual job.**

Correction to the answer above: the "19.6cm vertical accuracy" recorded for this collection
is the *specification ceiling* (NVA at 95% confidence = RMSEz × 1.96), not a measurement. The
measured numbers are below.

## The receipt

Work unit `MI_31Co_Eaton_2016` of project `MI_31County_2016_A16` (3DEP project ID 64540),
whose extent (−85.0727 to −84.6017 E, 42.4182 to 42.7675 N) contains Delta Twp. Collected
**2017-12-01 → 2018-04-23**, leaf-off, Leica ALS70 + IPAS20 GPS/INS, flown by Sanborn;
4 returns/pulse, nominal pulse spacing 0.67m / density 2.2 pts/m², LAS 1.4 PRF6, 2500ft tiles;
NAD83(2011) State Plane MI South (int. ft) / NAVD88 GEOID12B. USGS Lidar Base Spec 1.2, QL2.

Accuracy was tested once for the 8-county control block (Ottawa · Kent · Ionia · Clinton ·
Barry · **Eaton** · Ingham · Livingston) — there is no Eaton-only breakout.

| tested surface | RMSEz | 95% / 95th pct | mean dz | worst dz | n |
|---|---|---|---|---|---|
| raw point cloud, non-vegetated | **0.064 m** | 0.125 m | +0.010 m | 0.170 m | 134 |
| bare-earth DEM, non-vegetated | **0.062 m** | 0.122 m | +0.004 m | 0.170 m | 134 |
| bare-earth DEM, vegetated | **0.092 m** | 0.195 m | **+0.050 m** | +0.245 m | 100 |
| horizontal | *1.0 m spec, no measurement published* | | | | |

Spec was NVA ≤19.6cm @95% and VVA ≤29.4cm @95th pct; delivered 12.2cm and 19.5cm.
Checkpoints were static-GPS, 20-minute occupations, withheld from calibration.

Sources — the actual project deliverables, not a spec page:
[accuracy spreadsheet](https://rockyweb.usgs.gov/vdelivery/Datasets/Staged/Elevation/metadata/MI_31County_2016_A16/MI_31Co_Eaton_2016/reports/Accuracy_Report_Michigan_LiDAR_2017_8_Counties.xlsx)
(per-checkpoint dz for all 134/100 points) ·
[vendor FGDC metadata](https://rockyweb.usgs.gov/vdelivery/Datasets/Staged/Elevation/metadata/MI_31County_2016_A16/MI_31Co_Eaton_2016/reports/vendor_provided_xml/Michigan_LiDAR_2016_Eaton_Project.xml) ·
[USGS data-validation report](https://rockyweb.usgs.gov/vdelivery/Datasets/Staged/Elevation/metadata/MI_31County_2016_A16/MI_31Co_EatonB_2016/reports/USGS_MI_31Co_EatonB_2016_Summary_Report.pdf).

Two documentation defects found while reading them, worth knowing before anyone re-derives
these numbers: the vendor FGDC metadata says "the RMSEz was computed to be 0.122m @ 95 percent
confidence level" — it is reporting the 95% value in the RMSEz field; the spreadsheet keeps them
separate (0.062 / 0.122). And the USGS validation report's boilerplate claims the data are
"produced to meet 9.8 cm absolute vertical accuracy at the 95-percent confidence level" where
the project spec and vendor metadata both say 19.6cm. **Trust the spreadsheet — it carries the
raw checkpoints.**

## Horizontal is the unmeasured term

The vendor states only that "standard system results for horizontal accuracy meet or exceed the
project specified 1.0 meter RMSE." No checkpoint test, no residuals, no number. So every
LiDAR-derived feature — a swale edge, a driveway boundary, a building corner off the DSM — sits
inside a **1m horizontal uncertainty we are taking on the vendor's word.** Our terrain grid is
10cm. The grid is ten times finer than the georeferencing of the thing it describes.

## NAIP horizontal: a tolerance, never a measurement

There is **no published measured accuracy report for any Michigan NAIP flight.** The number in
circulation is a contract tolerance: since 2016, "all well-defined points tested shall fall
within **4 meters of true ground** at a 95% confidence level" — absolute to ground, not relative
to a reference orthoimage (that regime ended in 2006)
([FSA NAIP Information Sheet, Oct 2017](https://www.fsa.usda.gov/sites/default/files/documents/naip_infosheet_2017.pdf)).
Michigan's own metadata restates that history in the accuracy report's
`evaluationMethodDescription` and then declares `<gmd:result gco:nilReason="missing"/>` — the
field that would hold a measured value is explicitly empty
([InPort 70528 ISO](https://www.fisheries.noaa.gov/inport/item/70528/iso19115)).
The only *measured* Michigan figure found anywhere is
from 2010: **3.942 m at 95% NSSDA ≈ 2.3 m RMSE**
([FSA accuracy briefing, 2011](https://www.fsa.usda.gov/Internet/FSA_File/pm2011_aaroneckert_accuracy.pdf),
which exists because APFO knew the old wording "cannot determine horizontal accuracy at a 95%
confidence level").

Three properties matter more than the headline number:

- **Standard ortho, not true ortho.** Michigan 2022 was rectified against "the 2018 or newer HxIP
  DEM" (InPort 70528 lineage) — a terrain model, so every building, tree, greenhouse and pergola
  **leans radially from nadir**. FSA's own best-practice note concedes it: DSMs should be used
  for man-made objects "where possible," and "the accuracy of the output orthoimage… cannot
  improve upon the errors in these models"
  ([naip_best_practice.pdf](https://www.fsa.usda.gov/Internet/FSA_File/naip_best_practice.pdf)).
  Relief displacement is unbounded by the 4m tolerance and is what actually breaks a traced roof
  footprint.
- **Leaf-on by design** — MI 2022 ground condition 2022-07-29 → 2022-09-09. Full canopy over the
  plot; anything under a tree is invisible to imagery. LiDAR is leaf-off, imagery is leaf-on: the
  two sources see complementary halves of the yard and never the same one.
- **Take the DOQQ GeoTIFF, not the county mosaic.** The compressed county mosaic is MrSID at
  **60:1** for 0.6m imagery (2017 Info Sheet); USGS's JPEG2000 delivery is 10:1 lossy. Neither
  belongs upstream of a segmentation model.

Michigan flies even years at 0.6m (2020 · 2022 · 2024; no 2021/2023/2025 flight). **A 2026 flight
is in acquisition now** — the Eaton quads were tasked 7–9 Jul 2026 (USDA NAIP 2026 acquisition
tracker). That is the freshest ground truth the parcel will get for free, and it lands this year.

## Staleness — the hole the capture role exists to fill

The LiDAR is **Dec 2017 – Apr 2018. Eight growing seasons ago.** Every tree on the parcel has put
on eight years of height and spread; anything built, torn down, or regraded since is simply
absent from the elevation model, and the model will not say so — it will report ground where a
shed now stands.

No refresh is coming. Eaton is absent from the
[FY25](https://www.usgs.gov/3d-national-topography-model/fy25-3dep-data-collaboration-announcement-dca-selected-projects)
and [FY26](https://www.usgs.gov/3d-national-topography-model/fy26-3dep-data-collaboration-announcement-dca-selected-projects)
3DEP collaboration selections; Michigan's FY26 award is SEMCOG's seven southeast counties, none
of which is Eaton. MiSAIL's own status is "QL2 collected 2015–2020." So the 2017-18 cloud is the
best free elevation the parcel will have for the foreseeable future.

This is the argument for the capture role stated plainly: **open data gives us a good but frozen
2018 baseline; the only path to a current parcel is measuring it ourselves.** Aerial gives the
base terrain once; phone capture supplies the delta, forever.

## The datum will move under us before the decade is out

The base terrain is NAVD88 / GEOID12B in NAD83(2011). NGS is retiring both. NATRF2022 +
NAPGD2022 went to beta in June 2025, the FGCS approval vote is expected mid-2026, and adoption is
anticipated by end of 2026
([Federal Register 2024-10-09](https://www.federalregister.gov/documents/2024/10/09/2024-23347/updated-implementation-timeline-for-the-modernized-national-spatial-reference-system-nsrs)).
NGS states NAVD 88 "is both biased (by about one-half meter) and tilted (about 1 meter coast to
coast) relative to the best global geoid models available today," and that "every existing
latitude, longitude, ellipsoid height, and orthometric height in the United States… will change
by as much as four meters"
([NGS New Datums](https://geodesy.noaa.gov/datums/newdatums/index.shtml)).

That shift is an order of magnitude larger than the entire measurement budget above. For a twin
meant to be held for a decade this is not trivia:

**Every base-terrain and measured-entity row must carry its horizontal frame, vertical datum,
geoid model, and epoch as data.** A 2027 RTK survey and the 2018 LiDAR will disagree by
decimetres for reasons that have nothing to do with the ground moving. Without the frame on the
row, that disagreement is unattributable and the log stops being honest. Reprojection then
becomes a normal terrain-diff row with a stated cause, not a silent rewrite of history.

## The pipeline: address → base-terrain rows + candidate footprints

Six steps. Two of them stop for a human, and only two.

| # | step | in | out | who |
|---|---|---|---|---|
| 0 | resolve parcel | address string | parcel polygon + buffered AOI bbox, CRS stated | auto fetch, **operator confirms the parcel** |
| 1 | tile fetch | AOI bbox | LAZ tiles, 1m DEM tiles, NAIP DOQQ, + the report URLs and checksums | automatic |
| 2 | ground filter | LAZ | class-2 points, withheld/overlap dropped | automatic |
| 3 | grid resample | class-2 + breaklines | one base-terrain row: 10cm cells + per-cell support distance | automatic |
| 4 | candidate footprints | NAIP + CHM | candidate polygons with proposed type and confidence — **not rows** | auto detect, **operator confirms each** |
| 5 | heights | CHM + confirmed polygon | height/spread on the confirmed entity row | automatic |

**0 — parcel.** The polygon comes from common-ground's parcel layer, not a collector built here.
One human click, because a wrong parcel poisons every step below it and nothing downstream can
detect the mistake.

**1 — fetch is deterministic, so it is a daemon.** The rockyweb paths are computable from a bbox:
point cloud at `.../LPC/Projects/MI_31County_2016_A16/MI_31Co_Eaton_2016/LAZ/*.laz` (2500ft
tiles, 4–20MB each, ~1000 for the county), 1m DEM at
`.../1m/Projects/MI_31Co_Eaton_2016/TIFF/USGS_one_meter_x{..}y{..}_MI_31Co_Eaton_2016.tif`.
The step records the source URLs, the collection dates, and the accuracy-report URL alongside
the tiles — provenance is fetched, not remembered.

**2 — do not re-classify the ground.** The delivered LAZ already carries class 2 from the
vendor's TerraScan pass, and that specific classification is what the 134 checkpoints tested.
Running our own ground filter would swap a QA'd, accuracy-tested product for an unmeasured one
and silently void the 6.2cm receipt. Filter and move on. Re-classification is justified only for
tiles where we are adding *new* points — and then those points are their own source with their
own signature, not a re-do of the vendor's.

**3 — the resample must ship an honesty field.** Delaunay TIN over class-2 points plus the
vendor's hydro breaklines, sampled at cell centres — the same interpolation family the vendor
used for its own tested DEM, so our surface inherits the measured error instead of inventing a
new one. Not IDW (flattens swale lips), not natural-neighbour without breaklines (bleeds across
them).

Then the part that matters: **every cell also carries its distance to the nearest ground point.**
At 2.2 pts/m² there are 2.2 measurements per 100 cells — **~98% of the terrain grid in the open
is interpolation, and under a leaf-off crown it is worse.** A cell 8cm from a ground return and a
cell 2m from one are not the same claim, and a solver standing on them should not be told they
are. Support distance is what lets a clearance solver report "violation, and the ground here is
inferred" and what tells the capture role where to walk.

**4 — imagery produces candidates, never rows.** segment-geospatial and DeepForest on the DOQQ
give polygons with a type and a score; the operator accepts, retypes, redraws, or rejects each
one. This is the one gate that cannot be automated away, for a reason that comes straight from
the receipts: the imagery is a 4m tolerance, leaf-on, standard-ortho product with radial lean on
everything tall. It cannot place a footprint the log should trust unreviewed. It is also where
the writer signature comes from — an unconfirmed detection has no writer, so it has no row.

**5 — heights are automatic and dated.** CHM = first-return DSM − bare-earth DEM, zonal p95
inside the confirmed crown polygon. Stamped with the 2018 epoch, because that is what it is.

**What this pipeline structurally cannot produce**, and therefore what the phone half exists for:
fences, raised beds, terrace edges, retaining walls, anything under canopy, anything smaller than
about a metre — and everything built, removed, or regraded since April 2018.

**One free upgrade worth chasing before the phone half.** Eaton County runs its own ArcGIS at
`ecgis.eatoncounty.org/ags2/rest/services`, with imagery basemaps for 2005 · 2010 · 2015 · 2020 ·
2023 · **2025** cached down to ~7.5cm/px, plus building footprints, 2ft contours and a 10ft DEM in
its `Basemaps/Terrain` service. The 2023 flight was **March** — leaf-off, which is the opposite
season from NAIP and therefore sees under the deciduous canopy. (The 2025 service's description
text is copy-pasted from the 2023 one, so its flight date needs a phone call to the county before
anyone cites it.) At 7.5cm this is an order of magnitude better than NAIP for tracing a footprint,
and the 2025 vintage is three years fresher. It is a cached tile service, not a downloadable
ortho, and it carries no published accuracy statement — so it is a better *tracing surface*, not a
better *measurement*.

## Ground under a tree: what the returns actually buy

**A last return is not a ground return.** Return order is a statement about detector triggering,
not about what was hit: if the pulse dies in the crown, the last return is canopy. What produces
class 2 is the classifier — TerraScan's progressive TIN densification, Axelsson's 2000 algorithm —
and its parameters are the bias mechanism. Terrasolid's own docs give an iteration distance of
**0.5–1.5m**: the rule is explicitly allowed to promote points up to about a metre above the
current ground surface. Leaf litter, dormant brush, a low branch — class 2 by construction.
[TerraScan Ground](https://terrasolid.com/guides/tscan/crground.html).

**Under canopy, the grid is almost entirely inference.** ASPRS calls an area low-confidence when
ground-point density falls to ¼ of pulse density
([ASPRS Positional Accuracy Standards, 2nd ed.](https://aagsmo.org/wp-content/uploads/2023/03/ASPRS_PosAcc_Edition2_MainBody.pdf));
Kraus & Rieger's wooded-terrain accuracy law is stated valid at ≥25% ground penetration
([Photogrammetric Week '99](https://phowo.ifp.uni-stuttgart.de/publications/phowo99/kraus.pdf)).
At 2.2 pulses/m² that boundary means:

| ground penetration | class-2 pts/m² | mean ground spacing | 10cm cells per measurement |
|---|---|---|---|
| 100% — open lawn | 2.20 | 0.67 m | 45 |
| 40% — open leaf-off crown | 0.88 | 1.07 m | 114 |
| 25% — ASPRS low-confidence line | 0.55 | 1.35 m | 182 |
| 10% — conifer, or leaf-on | 0.22 | 2.13 m | 455 |

**97.8% of cells in the open, and 99.4–99.8% under canopy, contain no measurement.** USGS's own
Lidar Base Specification sets the minimum DEM cell size for QL2 at **1 m**
([LBS tables](https://www.usgs.gov/ngp-standards-and-specifications/lidar-base-specification-tables)) —
our grid is ten times finer than the resolution the source is specified to support. That is not a
mistake; a 10cm grid is the right store for entity-scale features and terrain-diff patches. It
just means the cell is a claim about a surface, and the support-distance field is what keeps that
claim honest.

**Leaf-off is why the numbers are as good as they are.** Simpson et al. measured leaf-off DTM
RMSE **0.22m vs >1m leaf-on** where low understory is dense
([Remote Sens. 9(11):1101](https://doi.org/10.3390/rs9111101)); Wasser et al. measured leaf-off
pulses reaching **1.4–2.6m deeper** at the canopy bottom
([PLOS ONE 8(1):e54776](https://journals.plos.org/plosone/article?id=10.1371%2Fjournal.pone.0054776)).
A December-to-April flight is the single best thing about this dataset.

**The +5cm vegetated bias is one-sided and it is small.** A pulse can be stopped early and read
high; it can never read below the ground — which is why ASPRS reports VVA at the 95th percentile
rather than as an RMSE. Published comparators run worse: +0.07m graminoid and +0.15m willow scrub
(Hopkinson et al.), deciduous-forest RMSE 26cm vs 17–19cm on pavement
([Hodgson & Bresnahan 2004](https://doi.org/10.14358/pers.70.3.331)), and 0.16m clearcut → 0.31m
uncut with the classifier held constant
([Reutebuch et al. 2003, Can. J. Remote Sens. 29(5):527–535](https://research.fs.usda.gov/treesearch/49132)).
Our +5.0cm beats all of
them. **Do not generalize it to the parcel** — 100 checkpoints spread over eight counties says
nothing about one spruce. Under an evergreen or a dense dormant shrub mass, expect the uncut
regime, 20–30cm high.

**Slope is the term nobody measured, and it is the biggest one.** Horizontal error projects into
vertical as `Δz = Δxy · tan(slope)`. At the spec'd 1.0m horizontal RMSE:

| slope | vertical from horizontal | composed with open-ground 0.062m |
|---|---|---|
| 2% | 0.020 m | 0.065 m |
| 5% | 0.050 m | 0.080 m |
| **10%** | **0.100 m** | **0.118 m** |
| 20% | 0.200 m | 0.209 m |

**At 10% the horizontal term alone exceeds the entire measured vertical error.** Kraus & Rieger's
empirical law for wooded DTMs, σ_H[cm] = ±(18 + 120·tanα), predicts a 12cm slope addition at
10% — within 20% of the arithmetic above, from an independent route, with an implied horizontal
uncertainty of ~1.2m that brackets our unverified 1.0m spec.

And then the trap: ASPRS requires vertical checkpoints to be sited **in open terrain, flat or
uniform slope ≤10%**. So the 6.2cm and 9.2cm figures were, by design, measured where this error
cannot appear. They are floors. They are silent about terraces, swales, berms and drainage cuts —
which is precisely the ground an earthworks or D8-flow solver is asked about.

**Tree heights from the CHM are a lower bound, biased low by 1–3m.** Andersen et al. surveyed
treetops by total station and decomposed the error: the bare-earth DEM contributes **−0.004m** —
essentially nothing — while the treetop term is **−0.74m at 6 pts/m², −1.12m at larger footprint**,
and underestimation is universal
([Can. J. Remote Sens. 32(5)](https://www.fs.usda.gov/pnw/pubs/pnw_2006_andersen001.pdf)). We are
at 2.2 pulses/m², a third of their best case. Leaf-off adds another **1.0–1.4m** of penetration
past the deciduous crown top and underestimates fractional cover by **19–24%** for deciduous
(6–8% conifer) — Wasser et al. again.

Two actionable rulings fall out. **Do not interpolate the CHM**: Wasser measured interpolated CHM
at −2.01 to −2.94m below field height versus −0.65 to −1.01m for a plain per-cell maximum first
return. Interpolation triples the error by smoothing away the apex you are trying to keep. And
**a shading, sunshed, or fall-zone rule fed raw CHM heights errs permissive** — it will clear a
pergola that a real tree shades. Flag CHM height as a lower bound, or calibrate against a handful
of field-measured trees on the plot.

## The budget, composed

What a solver standing on a cell is actually told, at the 2018 epoch:

| where it stands | vertical 1σ | known bias | horizontal | measured? |
|---|---|---|---|---|
| open lawn, ~flat | **0.08 m** | — | 1.0 m | vertical yes (n=134); horizontal never |
| open lawn, 10% slope | **0.12 m** | — | 1.0 m | derived from an unmeasured spec |
| under a leaf-off deciduous crown | **0.11 m** | +0.05 m high | 1.0 m | vertical yes (n=100, county-wide) |
| under an evergreen / dense shrub | **~0.25 m** | +0.2–0.3 m high | 1.0 m | **no** — literature only |
| a terrace face or swale lip | **≥0.12 m, unbounded** | — | 1.0 m | **no** — excluded by checkpoint siting |
| anywhere disturbed since Apr 2018 | **unbounded** | unknown sign | — | **no** |

Read the right-hand column. The vertical precision everyone quotes is real, tested, and better
than spec — and it applies to flat open lawn, which is the part of a yard nobody needs a solver
for. **Every regime the twin actually exists to reason about — under trees, on slopes, on
built-since-2018 ground — is unmeasured.** That is the accuracy budget's true shape, and it is the
job description for the capture role.

Two cheap measurements would close most of it, and neither needs anything rented: **count class-2
points per m² inside a crown polygon versus open lawn on our own tile** — that replaces every
literature proxy above with the real number for this parcel; and **take a handful of RTK shots on
a terrace face and under a spruce** — the two regimes the county's checkpoint design deliberately
excluded.

## The phone half: the mesh-importer contract

The capture app exists and is free — Scaniverse captures to mesh on-device, no cloud. The missing
piece is ours: the importer that turns a locally-scaled mesh into capture-signed rows.

**Shape and position arrive on different paths, and that is the contract's central fact.**
Scaniverse's mesh exports (OBJ/FBX/USDZ/GLB) are bare local coordinates with no CRS; its
*georeferenced* product is a separate LAS export where "every point in the point cloud has UTM
coordinates and elevation"
([Scaniverse: now with location](https://dev.scaniverse.com/news/scaniverse-now-with-location)).
So the importer takes a mesh **and** a registration, independently — never one file.

**Format: GLB / glTF 2.0 only.** It is the one format whose spec is normative about both things we
need — right-handed, **+Y up**, and "the units for all linear distances are meters"
([glTF 2.0](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html)). USDZ carries `upAxis` and
`metersPerUnit` and is acceptable *if read*; FBX carries unit scale and up-axis but is
exporter-dependent. **Reject OBJ** — unitless and axis-free means importing a guess into a metric
twin.

**Registration inputs, in the companion file:**
- frame: `crs` · `vertical_datum` · `geoid_model` · `epoch` (per the datum finding above)
- **≥4 correspondence pairs** (mesh-local xyz ↔ surveyed world XYZ). Three is the mathematical
  minimum for a similarity transform — 3 non-collinear points give 9 constraints for 7 unknowns
  ([Horn 1987](https://people.csail.mit.edu/bkph/papers/Absolute_Orientation.pdf)) — but three
  gives no redundancy, so a mis-picked point is undetectable. Four is the engineering minimum.
- capture metadata: device · app + version · capture mode · timestamp · operator

**Do not accept the phone's own fix as registration.** Consumer GNSS is 3–5m horizontal — 30–50×
coarser than the grid it would be writing into. Dual-frequency measures at 3–5m RMS in practice
(decimetres only via raw-carrier post-processing), and **iOS does not expose raw GNSS at all**, so
an iPhone cannot do RTK or PPK through its internal antenna, full stop. ARCore Geospatial's
"typically better than 5 meters, often around 1 meter" is coverage-dependent on Street View and
marketing-shaped; a private rural parcel likely has none. What works: an MFi external RTK receiver
(Emlid Reach RX2, 1–2cm H / 2–4cm V) against MDOT's statewide CORS network (`mdotcors.org`,
NTRIP, reported free after signup — the host refused connections from this session, so confirm
before relying on it) — or surveyed correspondence marks set once and reused forever.

**Registration algorithm:** Umeyama 7-parameter similarity fit on the correspondences
([Umeyama 1991](https://ieeexplore.ieee.org/document/88573/) — it fixes the reflection failure in
Horn/Arun on corrupted data), then optional point-to-plane ICP refine against the class-2 ground
under the footprint. ICP is a refiner, never an initializer — Besl & McKay prove only convergence
to the *nearest local* minimum.

**Fit the scale, then assert it.** ARKit is metric by construction — right-handed, y-up, metres,
from visual-inertial odometry plus depth — so scale is not a free parameter we need. But fitting it
anyway turns it into a check: **|s − 1| over tolerance rejects the import.** A scale of 1.03 is not
a correction to apply, it is a signal that the scan drifted or a correspondence was mis-picked. Same
for the fit residual. Both ride onto the row as provenance.

**Capture doctrine, from the sensor's own limits.** Apple LiDAR range is ~5m and point density
falls from 7,225 pts/m² at 0.25m to 150 pts/m² at 2.5m; accuracy is **±1cm on a >10cm object** but
**±10cm with 15–30cm tails on a 130m feature**
([Luetzenburg et al. 2021, Sci Rep 11:22221](https://pmc.ncbi.nlm.nih.gov/articles/PMC8593014/)) —
the error at scale is SLAM drift, not ranging noise. A field study needed **reference points every
20m** and still got 0.16m vertical RMSE
([Krausková et al. 2025](https://www.mdpi.com/1424-8220/25/19/6141)). So: **objects and small
structures, one control point per ~20m of scan path.** A shop wall exceeds 5m in at least one
dimension and is multi-station by necessity — which is exactly the regime where drift accumulates.

**Output rows** (capture writer signature, per the measured-vs-placed split): footprint polygon
from the mesh projected to the ground plane, height, fit residual, and the mesh itself as an
appearance attachment — **never as solver truth.** Ground under the structure becomes a
terrain-diff row only where the capture actually measured ground. The operator confirms three
things and nothing else: the correspondence picks, the entity type, the footprint.

**Trees: capture measures, it does not import.** Splatting and photogrammetry both fail on foliage
for structural reasons — wind moves branches between frames, thin structures don't match, and
Gaussian primitives are fuzzy by construction: photogrammetry shows **142% mean relative error on
branch count** ([Remote Sens. 17(2):202](https://doi.org/10.3390/rs17020202)), and 3DGS is
documented "extremely sensitive to surrounding vegetation points, often causing buildings to stick
to vegetation."

So a tree capture writes a typed row, not geometry: species · **DBH** · total height · crown spread
as two perpendicular dripline widths · crown base height · condition. That is what arboriculture
itself uses — American Forests' champion formula is three scalars. The instrument split matters and
belongs in the row's provenance: phone LiDAR **is** a credible DBH instrument at breast height
(1.37m) — RMSE **3.13cm** vs terrestrial scanning's 1.59cm, 97.3% tree detection
([Gollob et al. 2021](https://doi.org/10.3390/rs13163129)) — and is **not** a height instrument
(RMSE 38cm, −16cm bias), because the 5m range ceiling means it cannot see a canopy top from the
ground. Height comes from a clinometer, or from the CHM read as a lower bound. Splats are welcome
as appearance, explicitly non-load-bearing for any solver.

Solid structures are the opposite case and import near-perfect: planar, textured, static, and
inside the 5m range if you walk them.

## GPU: everything fits in 8GB

The workstation RTX 2000 Ada's 8GB carries the whole pipeline. Only four things exceed it, all of
them maximum-quality variants that are not on the critical path, and each has an on-device
mitigation:

| step | VRAM | 8GB? |
|---|---|---|
| SAM ViT-H, prompted, fp16 | ~4–6 GB | yes |
| Grounding DINO + SAM (samgeo) | 8 GB recommended | marginal — tile, or ViT-B backbone |
| DeepForest inference | small | yes, with room; CPU fallback viable |
| nerfstudio `splatfacto` | ~6 GB | yes |
| COLMAP feature extract + match | modest | yes |
| SAM auto-mask at default 32×32 grid | over | no — lower `points_per_batch`, ViT-L/ViT-B |
| SAM 3 | ~7.5–10 GB | no — stay on SAM 1 |
| `splatfacto-big` / paper-quality 3DGS | 12–24 GB | no — plain splatfacto, 7k iterations, `-r` downscale |
| COLMAP dense stereo, full res | over | no — cap `max_image_size`, disable geom consistency |

OpenDroneMap/OpenMVS are CPU- and RAM-bound, not VRAM-bound. **No step in this design needs a
rented GPU**, and the two workhorses for a 2-acre plot — splatfacto and DeepForest — fit with
headroom.

## What this addendum contradicts

- **The base answer's "19.6cm vertical accuracy" was a spec, not a measurement.** Measured is
  6.2cm RMSEz bare earth / 9.2cm vegetated. Corrected above.
- **"Heights interpolate; the grid resolution is about entities, not LiDAR density" understated
  the problem.** True for the *choice* of a 10cm grid, but the grid is 10× finer than the 1m
  minimum cell size USGS specifies for QL2 — so the cells need a support-distance field or they
  overstate what is known. That is a schema addition, not an architecture change.
- **Nothing contradicts a locked decision.** The base-terrain row gains three fields — reference
  frame + epoch, per-cell support distance, and a slope-derived vertical σ — all of which are data
  in a row, which is where the architecture already says everything lives.

## Open questions

- Does the county's 2025 imagery exist as claimed, at what resolution, what flight month, and can
  a tile service be legitimately harvested for a private twin? One phone call.
- Is 1.0m horizontal RMSE real? It is the single largest unverified term in the budget, and on a
  terrace face a factor of two in it swings vertical accuracy from 0.08m to 0.21m.
- Do we set permanent surveyed marks on the parcel? Four marks, shot once with RTK, would give
  every future phone capture a registration for free and would outlive the datum change.
