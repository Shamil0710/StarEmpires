# ref.industrial_union.small_craft.base.v1 — exact-source freeze recheck

**Status:** FROZEN PENDING EXACT-HEAD CI  
**Asset ID:** `industrial_union.small_craft.base`  
**Physical authority:** 29 x 14 x 6.5 m, 200 t  
**Production canvas:** 512x256 RGBA  
**Reference mode:** `GENERATE_5_SELECT_1`

## Why this recheck exists

Batch 001 had five valid generation outputs recorded by generation ID. Before freezing the provisional
candidate-05 choice, the retained source PNGs were re-opened and measured directly.

That verification found that the Run-008 normalization table did not match the actual source-pixel
geometry. Geometry freeze must be based on reproducible source evidence, so the provisional selection
cannot be carried forward merely because it had the highest qualitative score.

## Exact retained-source measurements

Measurement rule:

- use the original separate candidate PNG;
- visible bounds use alpha > 16;
- no crop rescaling is used to change aspect;
- compare visible L/W against authoritative 29/14 = **2.0714**.

| Candidate | Source visible L/W | Relative error | Freeze disposition |
| --- | ---: | ---: | --- |
| 01 | 2.7219 | 31.40% | hard reject at freeze QA — physical envelope |
| 02 | 2.6051 | 25.76% | hard reject at freeze QA — physical envelope |
| 03 | 2.7881 | 34.60% | hard reject — physical envelope + previously recorded fragile lateral booms |
| 04 | **2.3165** | **11.83%** | **valid / selected** |
| 05 | 2.7941 | 34.89% | hard reject at freeze QA — physical envelope |

The reference-freeze contract already states that a candidate visibly violating the authoritative
physical envelope/aspect is a hard rejection. Therefore the provisional candidate-05 selection from
Run 008 is superseded before geometry freeze.

## Corrected selection

**Selected:** `candidate_04`  
**Generation ID:** `92f9d865-c4a3-403e-ac9d-5c919b71e60b`  
**Existing qualitative score:** **92/100**

Candidate 04 preserves the strengths recorded in the original batch review:

- compact standardized modular construction;
- readable Union construction grammar without an Imperial central citadel;
- two common propulsion modules;
- replaceable-looking forward mission/crew block;
- clean cassette/service regions for later fit hardware;
- no baked shield identity;
- no permanent heavy strike weapon;
- no decorative wings/fins.

The only original concern was that it was broader/shorter than the target envelope. Exact source
measurement shows it is nevertheless substantially closer to the authoritative 29x14 m ratio than
every other surviving candidate.

## Canonical normalization

The selected source was processed only by:

1. alpha trim;
2. uniform scale preserving source aspect;
3. placement on a 512x256 transparent canvas.

No repainting, inpainting, stretching or geometry redraw was performed.

Canonical master:

`art_sources/stage23e/references/ref.industrial_union.small_craft.base.v1/selected/reference_master.png`

Measured persisted master with alpha > 16:

- canvas: **512 x 256**;
- visible bounds: **480 x 207**;
- visible center: approximately canvas center;
- minimum horizontal transparent padding: **16 px**;
- physical L/W: **2.0714**;
- visual L/W: **2.3188**;
- relative aspect error: **11.94%**;
- SHA-256: `f2c635b1302a9e17d906d64555602615df73fb5efbdfee5c115c692879fc4780`.

## Readability / silhouette QA

Persisted review artifacts:

- `selected/reference_silhouette.png`;
- `selected/reference_anchor_review.png`;
- `selected/reference_qa_sheet.png`.

Observed downscale review:

- 25% view remains a broad, modular right-facing craft with clearly separated propulsion and forward
  mission mass;
- 12.5% view remains identifiable and does not collapse into a generic rectangle;
- grayscale retains major mass separation and engine/mission-block contrast;
- the silhouette remains free of the rejected candidate-03 wing/boom language.

## Presentation-anchor review

Reviewed presentation zones on the frozen bitmap:

- two rear/common engine regions;
- forward mission block;
- two generic fit/cassette regions;
- central service/core region.

These are **presentation review points only**. They create no gameplay hardpoints, fitted modules or
simulation capability.

## Automated freeze gate

`Stage23EUnionSmallCraftReferenceFreezeTest` verifies the repository master itself:

- exact canonical SHA-256;
- 512x256 RGBA canvas;
- transparent corner/safe-alpha contract;
- centering and visible-bounds sanity;
- physical-aspect acceptance for this frozen master;
- grayscale / 25% / 12.5% readability;
- support near the reviewed presentation zones.

## Freeze decision

The manifest row is changed to:

- status: `FROZEN`;
- selected candidate: `candidate_04`;
- geometry_frozen: `true`.

This decision becomes accepted only when the exact final branch head passes required CI.

The three Union fit references may derive only from this frozen base. They may add lawful fit equipment
but must not generate a replacement hull.
