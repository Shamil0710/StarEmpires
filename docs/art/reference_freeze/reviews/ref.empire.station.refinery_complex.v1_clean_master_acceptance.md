# ref.empire.station.refinery_complex.v1 — recovered clean-master acceptance

**Status:** CLEAN MASTER ACCEPTED — REPOSITORY PERSISTENCE REQUIRED  
**Selected candidate:** `candidate_02`, 94/100  
**Generation ID:** `8bfb7e78-9299-411f-83d0-c797a893cce7`

## Accepted master identity

- recovered filename: `refinery_candidate_02_master.png`;
- byte size: **740,524**;
- SHA-256: `2b5fbf5d23262c6d2bac619b106495ca16d33c37de1637bfbb2b0faa80ee9fe8`;
- canvas: **1024x1024 RGBA**;
- alpha>16 bounds: x **148..875**, y **92..931**;
- visible span: **728 x 840** px;
- corner alpha: **0 / 0 / 0 / 0**.

## Gameplay-scale readability

25% review:

- canvas: 256x256;
- visible span: 182 x 210 px;
- visible-cell fraction: 0.3543243408203125;
- grayscale/luma bins: 16;
- luma range: 255.

12.5% review:

- canvas: 128x128;
- visible span: 92 x 106 px;
- visible-cell fraction: 0.3624267578125;
- grayscale/luma bins: 16;
- luma range: 254.

The selected role remains readable at both review scales.

## Role and mechanism disposition

The master preserves the selected refinery identity: repeated enclosed process towers, protected process trunks, side intermediate-pressure vessels, central service/control mass and separated input/output-side process structures.

**Mechanical disposition:** `mechanical_radiator_shutter` = **N/A for this accepted Empire design**. The production specification makes this sheet conditional on visible shutter hardware. No unambiguous radiator shutter exists in the selected geometry, so Stage 23E must not invent one merely to preserve an asset-count target. Refinery process activity will later use emissive groups/runtime VFX instead.

## Review anchors

- `service_core`: approximately `(512, 555)`;
- `process_tower_left`: approximately `(340, 250)`;
- `process_tower_right`: approximately `(690, 250)`;
- `process_tower_center`: approximately `(512, 360)`;
- `input_process_vessels`: approximately `(205, 520)`;
- `output_process_vessels`: approximately `(820, 520)`;
- `lower_service_module`: approximately `(512, 710)`;

All listed anchors have substantial authored alpha coverage within a 55 px review radius.

## Authority boundary

The reference is presentation authority only. It does not define station throughput, recipes, storage,
physical collision/footprint, ownership, economy or installed gameplay capability.

## Freeze gate

Before `FROZEN`:

1. persist the exact accepted PNG at `art_sources/stage23e/references/ref.empire.station.refinery_complex.v1/selected/reference_master.png`;
2. verify SHA-256 exactly matches the value above;
3. record committed Git blob SHA;
4. verify the committed image through the pre-authored silhouette/anchor/QA views;
5. run exact-head CI;
6. only then set `geometry_frozen=true`.

No new candidate generation or reselection is required unless the recovered master fails byte identity
or committed-byte QA.
