# Stage 23E — Resource Body Visual Reference Promotion Audit

**Status:** REFERENCE SELECTION AUDIT — GEOMETRY NOT YET FROZEN  
**Scope:** four Stage-20.5 resource-body atlas regions  
**Source atlas:** `src/main/resources/assets/stage20_5/resources/resource_body_atlas_v1.png`  
**Git blob SHA:** `e82bb4f8f37895b201c019619f8951a51aa220f4`

## 1. Authoritative region mapping

The existing `Stage20MinimumPlayableSpriteCatalog` binds the four 320x320 atlas regions exactly:

| Reference | Runtime visual role | Atlas region |
| --- | --- | --- |
| `ref.world.resource.carbonaceous.v1` | `RESOURCE_CARBONACEOUS` | x=0, y=0, 320x320 |
| `ref.world.resource.water_ice.v1` | `RESOURCE_WATER_ICE` | x=320, y=0, 320x320 |
| `ref.world.resource.metallic.v1` | `RESOURCE_METALLIC` | x=0, y=320, 320x320 |
| `ref.world.resource.mineral_silicate.v1` | `RESOURCE_MINERAL` | x=320, y=320, 320x320 |

The resolver maps known occurrence classes into those presentation roles without changing physical
resource authority.

## 2. Existing automated evidence

`Stage20MinimumPlayableSpriteCatalogTest.resourceAtlasKeepsFourDistinctNonEmptyRegions()` already
enforces that every region:

- contains substantial opaque content;
- has a distinct sampled visual fingerprint;
- remains a separate authored atlas region.

The broader sprite test also checks transparent corners and the presence of both opaque and transparent
pixels for Stage-20.5 production assets.

## 3. Visual review

The current atlas was manually reviewed as part of the Stage-23E reference selection pass.

### Carbonaceous

- dark charcoal/gray natural rock;
- irregular non-artificial silhouette;
- no emissive ore/value cue;
- no visible quantity/grade encoding.

### Water / ice

- pale gray / blue-white dirty ice-rock presentation;
- visibly distinct from carbonaceous without text;
- no magical crystal language;
- no reserve/quality glow.

### Metallic

- dark dense rock with muted oxidized orange/metallic material variation;
- classification can be visually inferred at the same level as the known resource class;
- no treasure-like gold/glowing veins;
- no encoded reserve quantity.

### Mineral / silicate

- layered gray-beige stone;
- visually distinct from metallic and carbonaceous;
- no glowing crystal/value treatment;
- no artificial structures.

The four regions therefore meet the **selection** requirement for `PROMOTE_EXISTING`.

## 4. Knowledge-leak rule

These references may communicate the already-known broad occurrence classification. They must never
encode:

- exact reserve amount;
- grade;
- extraction yield;
- hidden strategic value;
- undiscovered secondary composition.

Any later polish that adds brightness, rare-color coding or emissive veins based on those values is a
hard rejection.

## 5. Freeze decision

All four existing regions are **SELECTED for promotion**.

No new five-candidate generation is justified.

They remain `geometry_frozen=false` until the final Stage-23E reference QA records:

- grayscale readability;
- 25% / 12.5% downscale readability;
- silhouette-only distinction;
- confirmation that no presentation change leaks resource authority.

Anchor review is **N/A** for these passive natural bodies.

## 6. Outcome

- selected existing resource references: **4/4**;
- new base-reference generations required now: **0**;
- frozen resource references at this step: **0**;
- next action: final downscale/silhouette QA, then set the four rows to `FROZEN`.
