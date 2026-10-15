# ref.industrial_union.station.industrial_station.v1 — candidate batch 001

**Status:** SELECTED FOR CLEAN MASTER — NOT YET FROZEN  
**Asset ID:** `industrial_union.station.industrial_station`  
**Reference mode:** `GENERATE_5_SELECT_1`  
**Prompt authority:** `docs/stage23e_visual_reference_freeze.md §14.14` + Industrial Union visual bible  
**Source generation ID:** `47c6c305-f2f3-4ae3-981b-bdc12212ad15`

## Backend packaging note

The connected image backend returned a presentation board containing both pilot factions rather than
five independent Union files. Only the **lower Industrial Union row** is relevant to this review.

For candidate review, the five Union designs were deterministically:

1. cropped from their panel cells;
2. stripped of presentation background by technical alpha matting;
3. reduced to the largest connected station component;
4. normalized to individual transparent 1024x1024 review canvases;
5. left otherwise geometrically unchanged.

This normalization is for **review only**. The source presentation board is not a canonical reference,
and none of the normalized files may become `reference_master.png` without a separate clean-master
acceptance pass.

## Candidate review

| Candidate | Hard reject after review normalization | Engineering /25 | Role /20 | Faction /20 | Silhouette /15 | Derivative /10 | Distinctive /10 | Total |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 01 | NO | 23 | 19 | 19 | 14 | 9 | 8 | **92** |
| 02 | NO | 20 | 15 | 17 | 13 | 8 | 8 | **81** |
| 03 | NO | 22 | 18 | 19 | 13 | 8 | 8 | **88** |
| 04 | NO | 21 | 16 | 18 | 13 | 8 | 8 | **84** |
| 05 | NO | 22 | 17 | 19 | 13 | 8 | 7 | **86** |

Candidate 02 is not eligible for selection despite total 81 because role readability is below the
required 16/20 floor.

### Candidate 01 — selected

Strengths:

- strongest visible serial-production identity;
- two large repeated production / assembly masses read without relying only on orange paint;
- four mechanically plausible diagonal gantry / handling roots;
- clear modular central spine;
- repeated service and utility blocks support the Union "machine of flow" language;
- good grayscale silhouette separation from the Imperial pilot;
- moving components have identifiable bases suitable for later local animation.

Weaknesses / clean-master requirements:

- the final master must make one representative **stowed gantry** and one distinct
  **stowed assembly rig** unambiguous;
- some current solar/service panels are visually close to generic station utility equipment and
  should remain subordinate to the production bays;
- technical alpha cleanup from the presentation board is not sufficient for a frozen master.

### Candidate 02

Strong faction modularity, but circular/ring architecture weakens the specific industrial-production
role and risks reading as generic infrastructure.

### Candidate 03

Good repeated production cells and strong throughput language. Less explicit gantry/assembly geometry
than candidate 01, so it remains the strongest fallback.

### Candidate 04

Valid modular engineering but reads closer to a mixed utility/logistics node than a dedicated
production station.

### Candidate 05

Strong standardized module repetition, but the long central body and dense side blocks drift toward
logistics/warehouse presentation.

## Selected candidate

`candidate_01`

Score: **92/100**

Selection reason:

Candidate 01 gives the clearest combination of serial production, standardized Union construction,
material-handling geometry and animation-ready mechanism roots while retaining a distinct silhouette
from the Imperial naval depot.

## Geometry-freeze decision

**DO NOT FREEZE YET.**

A clean final master is still required:

- one station only;
- transparent 1024x1024 canvas;
- no labels/UI/presentation frame;
- same selected candidate-01 primary geometry;
- explicit stowed gantry;
- explicit distinct assembly rig;
- no extra gameplay capability;
- clean alpha;
- no transient work VFX.

After that master exists, produce:

- grayscale review;
- 25% review;
- 12.5% review;
- silhouette-only review;
- anchor/mechanical-root review.

## Failed clean-master attempt

A subsequent geometry-preserving clean-master request returned another unrelated five-object board
(source generation ID `dee4b31f-9980-42b1-a5b4-3ae0d2974a20`) instead of an edit of candidate 01.

That output is rejected and has no authority over the selected geometry.

## Next action

Preserve candidate 01 as the selected design and obtain a true geometry-preserving clean master.
If the connected generator cannot perform the edit reliably, keep this reference at `SELECTED`
and continue other reference audits rather than silently substituting a new design.
