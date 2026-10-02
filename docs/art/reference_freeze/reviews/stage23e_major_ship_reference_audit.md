# Stage 23E — Major Ship Visual Reference Promotion Audit

**Status:** REFERENCE SELECTION AUDIT — GEOMETRY NOT YET FROZEN  
**Scope:** 18 existing production major-ship base masters  
**Decision:** select existing production masters; do not regenerate five-candidate batches unless later 23E QA finds a concrete defect.

## 1. Evidence used

Empire:

- `docs/empire_sprite_refresh.md` records the nine-family production refresh, manual visual review,
  alpha review, common 768x512 RGBA canvas, approximately 660 px visible length, role-specific
  silhouettes and faction-specific construction language;
- `docs/assets/empire_refresh/metrics.json` records per-family canvas, bounds, colors, byte size and
  opaque-pixel counts;
- `Stage22EmpireShipVisualCatalog` binds base/emissive/damage paths while deriving world dimensions
  and hardpoint/service anchors from engineering authority rather than pixels.

Industrial Union:

- `docs/stage22_4_industrial_union_sprite_audit.md` records replacement of the prior schematic
  placeholders with nine role-readable production bases;
- `Stage22IndustrialUnionProductionSpriteTest` enforces unique content, >=100 KiB detail floor,
  768x512 alpha canvas, transparent corners, bounded occupancy, >=4096 visible colors, centered safe
  area and physical-aspect tolerance.

These are sufficient to select the existing base masters for PROMOTE_EXISTING. They are not sufficient
to mark Stage-23E geometry FROZEN, because the new reference contract still requires final
silhouette/downscale/anchor QA.

## 2. Decision matrix

| Faction | Family | Existing base | Git blob SHA | Visible bounds | Bytes | 23E decision |
| --- | --- | --- | --- | ---: | ---: | --- |
| empire | corvette | `src/main/resources/assets/ships/empire/production/corvette/corvette_base.png` | `f9b8fc93cf0a3a6bb2f6ab58bcfb45b25738bce4` | 660x214 | 237821 | SELECTED — promote existing |
| empire | frigate | `src/main/resources/assets/ships/empire/production/frigate/frigate_base.png` | `dd16d7db983b1b89366a7fa75704b2f0c6c7d00e` | 660x192 | 208062 | SELECTED — promote existing |
| empire | destroyer | `src/main/resources/assets/ships/empire/production/destroyer/destroyer_base.png` | `8f3d543adfd5cf35ab8dec0a377fd7dbf859a655` | 660x207 | 213666 | SELECTED — promote existing |
| empire | cruiser | `src/main/resources/assets/ships/empire/production/cruiser/cruiser_base.png` | `e493e3646e67e05d1dd7766b90c9e40ffa5d7545` | 660x202 | 226411 | SELECTED — promote existing |
| empire | battleship | `src/main/resources/assets/ships/empire/production/battleship/battleship_base.png` | `39d928776d2071082cb8cecbebd78b054c944152` | 660x200 | 229874 | SELECTED — promote existing |
| empire | carrier | `src/main/resources/assets/ships/empire/production/carrier/carrier_base.png` | `cf68b83f513b35e99d36f17bf54f29d5b49f378a` | 660x265 | 318905 | SELECTED — promote existing |
| empire | freight | `src/main/resources/assets/ships/empire/production/freight/freight_base.png` | `a1e8a3bb3fdb2fb3c7fa91c38a2881e157dd39c1` | 660x196 | 213830 | SELECTED — promote existing |
| empire | tanker | `src/main/resources/assets/ships/empire/production/tanker/tanker_base.png` | `9641264426956f0314d465aeb1db2ec2df782b5c` | 660x196 | 258246 | SELECTED — promote existing |
| empire | fleet_support | `src/main/resources/assets/ships/empire/production/fleet_support/fleet_support_base.png` | `daabf4b004b1bc2db0a3b6e75c9a5d8e74ef4dc3` | 660x203 | 235674 | SELECTED — promote existing |
| industrial_union | corvette | `src/main/resources/assets/ships/industrial_union/production/corvette/corvette_base.png` | `d0402d967546d27cd711d8f8f625057332eb3e83` | 659x244 | 161781 | SELECTED — promote existing |
| industrial_union | frigate | `src/main/resources/assets/ships/industrial_union/production/frigate/frigate_base.png` | `77f4d570da585710e687227e82de01e7f845437a` | 655x184 | 140666 | SELECTED — promote existing |
| industrial_union | destroyer | `src/main/resources/assets/ships/industrial_union/production/destroyer/destroyer_base.png` | `af9bf03f8dc4a2c6066f77849f83b6d1d00a35e6` | 660x218 | 227044 | SELECTED — promote existing |
| industrial_union | cruiser | `src/main/resources/assets/ships/industrial_union/production/cruiser/cruiser_base.png` | `b837d4b6447cdd49c2d001becc31dd4697570f44` | 649x279 | 263847 | SELECTED — promote existing |
| industrial_union | battleship | `src/main/resources/assets/ships/industrial_union/production/battleship/battleship_base.png` | `9f94178f7d1b0167b17b5e9fca9f2b6160e2718e` | 648x217 | 235322 | SELECTED — promote existing |
| industrial_union | carrier | `src/main/resources/assets/ships/industrial_union/production/carrier/carrier_base.png` | `0e0523adda1e83200b7e86ebf99b126b55fac8b4` | 653x369 | 356864 | SELECTED — promote existing |
| industrial_union | freight | `src/main/resources/assets/ships/industrial_union/production/freight/freight_base.png` | `3361caa7753f00f416c4ccdbdc5edc97c0e8d55b` | 650x196 | 201056 | SELECTED — promote existing |
| industrial_union | tanker | `src/main/resources/assets/ships/industrial_union/production/tanker/tanker_base.png` | `fe3dfec991fecfb347a5157d29023d3cb23df020` | 650x248 | 288732 | SELECTED — promote existing |
| industrial_union | fleet_support | `src/main/resources/assets/ships/industrial_union/production/fleet_support/fleet_support_base.png` | `36d055d8b4505230040f1360534e4dc2bd1e2b6d` | 660x231 | 250210 | SELECTED — promote existing |

## 3. Why no regeneration is justified now

The five-candidate rule exists to choose a visual family when one does not yet have an acceptable
production identity. These 18 families already have:

- accepted class-specific silhouettes;
- faction-specific visual grammar;
- production-scale detail rather than placeholder geometry;
- normalized runtime canvas/orientation;
- repository-bound production paths;
- automated or documented alpha/detail/aspect checks.

Generating five replacements for each would spend 90 candidate generations while increasing art drift
risk and discarding previously reviewed work.

A family switches back to `GENERATE_5_SELECT_1` only if the remaining 23E QA finds a specific
release-facing defect that cannot be corrected without changing frozen-level geometry.

## 4. Remaining freeze gate per ship

Before changing a row from `SELECTED` to `FROZEN`:

1. verify the exact repository source path/blob still matches the reviewed master;
2. create/verify grayscale readability;
3. verify 25% and 12.5% downscale silhouette readability;
4. verify silhouette-only class distinction against adjacent classes;
5. verify runtime engineering-derived anchor overlay against the base sprite;
6. confirm no painted feature is being treated as new simulation authority;
7. record the review result and provenance;
8. set `geometry_frozen=true`.

For Empire, existing damage/emissive layers are **not** automatically frozen merely because the base
reference is selected. Stage 23E audits those layers separately.

For Industrial Union, this promotion applies to **base art only**; missing damage/emissive/wreck/marker
layers remain Stage-23E production work.

## 5. Outcome

All **18/18** existing major-ship base masters are selected for promotion.

- regenerated major-ship base families required now: **0**;
- selected existing masters: **18**;
- frozen references: **0** at this audit step;
- next action: complete the per-family 23E silhouette/downscale/anchor freeze gate.
