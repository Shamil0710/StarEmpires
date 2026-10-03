# ref.empire.station.frontier_multipurpose.v1 — recovered clean-master acceptance

**Status:** CLEAN MASTER ACCEPTED — REPOSITORY PERSISTENCE REQUIRED  
**Selected candidate:** `candidate_03`, 95/100  
**Generation ID:** `39b5a286-e7bc-4ff8-b174-5969598dddf0`

## Accepted master identity

- recovered filename: `frontier_candidate_03_master.png`;
- byte size: **752,620**;
- SHA-256: `8a57860f9e6bb8aa273d30966d64a1d6428e493048cb2debef01cd45dc1fafd2`;
- canvas: **1024x1024 RGBA**;
- alpha>16 bounds: x **92..931**, y **104..919**;
- visible span: **840 x 816** px;
- corner alpha: **0 / 0 / 0 / 0**.

## Gameplay-scale readability

25% review: visible span **210 x 204**, visible-cell fraction **0.308685302734375**,
16 luma bins, luma range **255**.

12.5% review: visible span **106 x 102**, visible-cell fraction **0.3201904296875**,
16 luma bins, luma range **255**.

The mixed-generation asymmetric station remains highly legible at both scales.

## Role and mechanism disposition

The master preserves the selected frontier identity:

- protected service/crew core;
- mixed module generations and enclosure types;
- compact cargo/storage and utility capability blocks;
- clear sensor/comms structure;
- several docking/service interfaces;
- visible repair/service-arm geometry;
- ordered retrofit history rather than random scrap construction.

Mechanical disposition:

- `mechanical_service_arm` = **REQUIRED**, rooted around `(470,355)`;
- `mechanical_module_door` = **REQUIRED**, representative door around `(565,760)`.

Later animation must preserve attachment roots and may animate only an installed/active capability.

## Review anchors

- `service_core`: approximately `(560, 525)`;
- `service_arm_root`: approximately `(470, 355)`;
- `module_door`: approximately `(565, 760)`;
- `sensor_comms`: approximately `(585, 220)`;
- `cargo_module_left`: approximately `(260, 535)`;
- `utility_module_right`: approximately `(780, 525)`;
- `lower_addon_module`: approximately `(430, 760)`;

Every listed anchor has substantial authored alpha coverage within a 55 px review radius.

## Authority boundary

This reference does not define installed gameplay modules, throughput, storage, repair capacity,
collision/physical envelope, ownership or economic authority.

## Freeze gate

Before `FROZEN`:

1. persist exact accepted PNG at `art_sources/stage23e/references/ref.empire.station.frontier_multipurpose.v1/selected/reference_master.png`;
2. verify SHA-256 exactly matches `8a57860f9e6bb8aa273d30966d64a1d6428e493048cb2debef01cd45dc1fafd2`;
3. record Git blob SHA;
4. verify committed grayscale/downscale/silhouette/anchor review;
5. run exact-head CI;
6. only then set `geometry_frozen=true`.

No new candidate generation/reselection is required unless byte identity or final committed-byte QA fails.
