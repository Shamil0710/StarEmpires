# ref.empire.station.high_tech_hub.v1 — recovered clean-master acceptance

**Status:** CLEAN MASTER ACCEPTED — REPOSITORY PERSISTENCE REQUIRED  
**Selected candidate:** `candidate_05`, 94/100  
**Generation ID:** `101b50b6-ca78-46ed-9308-5b3b9e03bf3c`

## Accepted master identity

- recovered filename: `hightech_candidate_05_master.png`;
- byte size: **458,750**;
- SHA-256: `2f59e3d89d97ec14ce0bcc3de93204aad367fe6686cc7a047dc61524bbe7395c`;
- canvas: **1024x1024 RGBA**;
- alpha>16 bounds: x **92..931**, y **298..725**;
- visible span: **840 x 428** px;
- corner alpha: **0 / 0 / 0 / 0**.

## Gameplay-scale readability

25% review:

- canvas: 256x256;
- visible span: 210 x 108 px;
- visible-cell fraction: 0.204742431640625;
- grayscale/luma bins: 16;
- luma range: 254.

12.5% review:

- canvas: 128x128;
- visible span: 106 x 54 px;
- visible-cell fraction: 0.208984375;
- grayscale/luma bins: 16;
- luma range: 251.

The selected role remains readable at both review scales.

## Role and mechanism disposition

The master preserves the selected precision/high-technology identity: compact protected instrumented core, four clean thermal/precision panels, ordered service modules, sensor/communications dome and controlled lateral material interfaces.

**Mechanical disposition:** `mechanical_precision_rig` = **REQUIRED**. The accepted central circular precision/instrument assembly provides the restrained local mechanism; later animation must use only subtle bounded travel and must not turn it into a holographic or energy spectacle.

## Review anchors

- `precision_rig`: approximately `(512, 515)`;
- `sensor_comms_dome`: approximately `(600, 325)`;
- `thermal_upper_left`: approximately `(300, 390)`;
- `thermal_upper_right`: approximately `(650, 390)`;
- `thermal_lower_left`: approximately `(300, 660)`;
- `thermal_lower_right`: approximately `(650, 660)`;
- `material_interface_left`: approximately `(135, 520)`;
- `material_interface_right`: approximately `(875, 520)`;

All listed anchors have substantial authored alpha coverage within a 55 px review radius.

## Authority boundary

The reference is presentation authority only. It does not define station throughput, recipes, storage,
physical collision/footprint, ownership, economy or installed gameplay capability.

## Freeze gate

Before `FROZEN`:

1. persist the exact accepted PNG at `art_sources/stage23e/references/ref.empire.station.high_tech_hub.v1/selected/reference_master.png`;
2. verify SHA-256 exactly matches the value above;
3. record committed Git blob SHA;
4. verify the committed image through the pre-authored silhouette/anchor/QA views;
5. run exact-head CI;
6. only then set `geometry_frozen=true`.

No new candidate generation or reselection is required unless the recovered master fails byte identity
or committed-byte QA.
