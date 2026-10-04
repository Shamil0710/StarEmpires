# ref.empire.station.mining_outpost.v1 — candidate batch 001

**Status:** SELECTED + CLEAN TECHNICAL MASTER PREPARED — FREEZE PENDING EXACT-HEAD CI  
**Asset ID:** `empire.station.mining_outpost`  
**Reference mode:** `GENERATE_5_SELECT_1`  
**Source generation ID:** `4798a103-b990-4594-a7fe-1bf42d13dbb1`

## Backend packaging

The image backend returned one five-object concept board rather than five independent files.

Unlike the previously rejected small-craft attempts, the object class is correct for this batch: all five
objects are Imperial station concepts. The board itself is not canonical.

For review only, the five objects were deterministically isolated from their board cells, alpha-matted,
reduced to the largest connected station component and normalized to equal review canvases.

## Candidate review

| Candidate | Hard reject | Engineering /25 | Mining role /20 | Imperial identity /20 | Silhouette /15 | Derivative readiness /10 | Distinctive /10 | Total |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 01 | NO | 21 | 12 | 18 | 14 | 7 | 8 | **80** |
| 02 | YES — reads as military vessel/fortified generic station | 21 | 8 | 19 | 13 | 6 | 7 | **74** |
| 03 | NO | 23 | 19 | 19 | 14 | 9 | 8 | **92** |
| 04 | YES — volatile/tank depot dominates | 22 | 9 | 18 | 13 | 8 | 7 | **77** |
| 05 | NO | 20 | 13 | 17 | 13 | 7 | 7 | **77** |

### Candidate 03 — selected

Strengths:

- protected central/service spine and enclosed equipment blocks;
- clearly visible heavy handling/extractor arm roots;
- separate crane/handling geometry suitable for later local animation;
- finite boxed storage/handling masses rather than a giant abstract ore reservoir;
- readable survey/sensor mast;
- multiple plausible service/docking interfaces;
- robust Imperial construction language with gunmetal, warm ivory, burgundy and restrained brass;
- asymmetric retrofit history without becoming chaotic;
- visually distinct from volatile depot and generic ring infrastructure.

Weaknesses accepted for reference use:

- heraldry on the largest housing is stronger than final production art needs;
- exact mining tool head and cargo-transfer mechanics should be refined by later derivative production
  layers without changing the frozen primary silhouette;
- the reference does not and must not communicate reserve quantity.

## Clean-master normalization

Candidate 03 was not redrawn by the image backend.

A deterministic technical normalization was produced directly from the selected board region:

1. isolate candidate cell;
2. foreground segmentation with no geometry synthesis;
3. keep the largest connected station component;
4. remove presentation background;
5. normalize alpha;
6. place the unchanged selected geometry on a transparent 1024x1024 canvas;
7. preserve source orientation and station proportions.

Canonical candidate master:

`art_sources/stage23e/references/ref.empire.station.mining_outpost.v1/selected/reference_master.png`

SHA-256:

`160f5d5779eb762a43feb43109619a14260e587ee068512860a4154eb1863811`

Visible bounds at alpha > 16:

- x: 92..931;
- y: 182..840;
- visible span: 840 x 659 px.

This normalization is acceptable as the geometry reference because it removes only board packaging and
background. It does not add or delete a station module, invent a gameplay capability or redraw the
selected silhouette.

## Presentation-anchor review

Review-only anchor zones:

- protected service/core zone;
- extraction/handling arm root;
- second handling/assembly root;
- docking/service interface;
- survey/sensor cluster;
- engineering/power block;
- ore-handling/storage block.

These are visual authoring anchors only. They do not define station capacity, reserve amount, collision,
facility inventory or simulation hardpoints.

## Freeze gate

`Stage23EEmpireMiningOutpostReferenceFreezeTest` pins:

- exact 1024x1024 RGBA master;
- SHA-256;
- transparent corners;
- safe visible occupancy;
- 25% and 12.5% grayscale/readability bounds;
- review anchor coverage;
- ordinary Stage-18 mining-outpost physical-scale authority remains exact and separate.

Set `geometry_frozen=true` only after exact-head CI succeeds.
