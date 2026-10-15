# ref.empire.station.naval_ordnance_depot.v1 — candidate batch 001

**Status:** SELECTED FOR REFINEMENT — NOT YET FROZEN  
**Asset ID:** `empire.station.naval_ordnance_depot`  
**Reference mode:** `GENERATE_5_SELECT_1`  
**Prompt authority:** `docs/stage23e_visual_reference_freeze.md §14.9` + Imperial visual bible  
**Source generation ID:** `d6bd5925-7292-4c7f-ac3a-e25918131148`

## Batch normalization

The image generator returned five physically separated candidates on one transparent source canvas.
The source canvas itself is **not** a canonical reference.

For review, the five non-overlapping alpha components were deterministically extracted without
redrawing their geometry and normalized onto separate 1024x1024 transparent canvases. Lightweight
192x192 PNG review previews are committed under:

`art_sources/stage23e/references/ref.empire.station.naval_ordnance_depot.v1/candidates/`

The repository previews are review derivatives, not final production masters.

## Candidate review

| Candidate | Hard reject | Engineering /25 | Role /20 | Faction /20 | Silhouette /15 | Derivative /10 | Distinctive /10 | Total |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 01 | YES | — | — | — | — | — | — | — |
| 02 | NO | 21 | 16 | 18 | 13 | 7 | 8 | **83** |
| 03 | YES | — | — | — | — | — | — | — |
| 04 | YES | — | — | — | — | — | — | — |
| 05 | YES | — | — | — | — | — | — | — |

### Candidate 01

Hard rejection: detached generator debris remains outside the station silhouette.

Useful direction:

- strong protected central core;
- clearly separated side storage/magazine-like blocks;
- Imperial mass hierarchy reads well.

Do not promote this bitmap without a clean regeneration/normalization pass.

### Candidate 02

No hard-rejection condition in the normalized candidate.

Strengths:

- strongest clean single-object result in batch;
- protected circular central service/citadel mass;
- good radial separation between traffic/service structures;
- side blocks can plausibly support reinforced docking/loading interfaces;
- strong Imperial silhouette without depending only on palette;
- enough empty separation around major blocks for later local animation layers.

Weaknesses:

- the required **stowed loading arm** is not unambiguously readable;
- the required **large armored magazine door/shutter and track/recess** are not yet explicit enough;
- some secondary towers are visually ambiguous between sensor/security and service structures.

Score: **83/100**.

Result: **SELECTED FOR REFINEMENT**.

### Candidate 03

Hard rejection: detached generator debris below the main silhouette.

Secondary concern: long lateral industrial truss/berth language is less specifically "protected
naval ordnance depot" than candidate 02.

### Candidate 04

Hard rejection: detached generator fragments remain above the main silhouette.

Secondary concern: large diagonal modules read as generic radial infrastructure and do not make safe
ordnance handling as clear as candidate 02.

### Candidate 05

Hard rejection: detached generator fragments remain above the station.

Secondary concern: broad horizontal logistics silhouette drifts toward trade/logistics hub identity.

## Selected candidate

`candidate_02`

Selection reason:

Candidate 02 is the only clean candidate in this batch that passes the documented minimum total and
the engineering / role / faction minimums. It also provides the most useful protected radial layout
for separating magazines, berths and security/service structures.

## Geometry-freeze decision

**DO NOT FREEZE YET.**

Before `ref.empire.station.naval_ordnance_depot.v1` may become `FROZEN`, the selected design needs
one constrained refinement pass that preserves its major silhouette while making these mandatory
production mechanisms explicit:

1. one visibly stowed loading/service arm with a clear root and deployment path;
2. one large armored magazine door/shutter with an obvious protected recess/track;
3. clear distinction between magazine/storage modules and docking/traffic interfaces;
4. removal of any remaining generated micro-artifacts;
5. final 1024x1024 transparent master;
6. grayscale, 25%, 12.5%, silhouette and anchor review.

No redesign of the main radial/citadel geometry is requested.

## Repository artifacts

- `candidate_01_preview.png`
- `candidate_02_preview.png`
- `candidate_03_preview.png`
- `candidate_04_preview.png`
- `candidate_05_preview.png`

These are intentionally lightweight review previews. The final selected/refined master is not yet
present and must not be substituted by upscaling a preview.
