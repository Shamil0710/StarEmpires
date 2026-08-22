# Stage 20.5E — Minimum Playable Sprite Pack v1

Status: implemented and production-bound.

## Accepted pack

The v1 pack contains ten transparent PNG resources and thirteen deterministic visual roles:

- light utility/player craft;
- bulk freight transport;
- mining/industrial craft;
- production escort destroyer;
- medium combat cruiser;
- trade dock/core;
- extraction/refining/industrial station;
- major construction shipyard;
- four resource-body atlas regions (carbonaceous, water/ice, metallic, mineral);
- finite-salvage derelict.

The art follows `Империя — единый визуальный код-стайл v0.1`: axial functional silhouettes,
protected central citadels, heavy repairable engineering, restrained gunmetal/ivory/burgundy/brass
palette, visible service structure, minimal ornament and no fantasy wings. Every source is strict
top-down, forward-right where directional, and has genuine alpha. Base sprites contain no starfield,
UI, text, exhaust, projectile, beam, smoke, debris cloud or transient combat effect.

## Runtime binding

`Stage20MinimumPlayableSpriteCatalog` is the versioned deterministic identity/role binding. The
sprite filename never becomes simulation identity. Exact production mappings currently cover:

- `hull.escort_destroyer_v1` → escort destroyer sprite;
- explicit Stage-20.5B provisional `hull.test_bulk_freighter_v1` → cargo sprite while retaining
  the mandatory Stage-22 review marker in freight authority;
- every Stage-18 station archetype → trade or industrial presentation, with ordinary installed-yard
  state selecting the shipyard sprite;
- every Stage-18 resource occurrence type → a stable resource atlas region;
- Stage-20H `DERELICT` → the derelict sprite.

Unknown future hull content uses `stage20_5.sprite-role-fallback.v1`. A fallback is explicitly
presentation scale only and cannot claim physical geometry. Exact hull and station bindings instead
consume their ordinary engineering/Stage-20A physical dimensions. The production escort's authored
`weapon_spinal` anchor is validated against the authoritative `+Y physical → +X sprite` axis law.

`WorldMapRenderer` now renders the pack over the same live Ashley entities used by the playable
world. The three existing Stage-17.5/19 tactical viewer apps use
`TacticalPrototypeRenderer.withMinimumPlayableSprites()` over their unchanged immutable combat
snapshots; projectiles, shields, impacts, damage cues and side/readability cues remain separate VFX.
The default shape renderer constructor remains available as a compatibility/fallback path.

## Quality and invariants

- all ten PNGs have genuine transparent and opaque pixels and transparent canvas corners;
- pivots are centered and directional sprites share forward-right orientation;
- resource atlas quadrants are non-empty and visually distinct;
- utility/cargo/mining/light-combat/medium-combat silhouettes remain distinguishable at normal
  marker scale;
- sprite swaps read ECS/content state only and expose no mutation channel;
- no visual metadata participates in collision, sensors, weapons, fitting, cargo or economy;
- visual binding is recomputed from stable content/role identity after save/load, so no new saved
  simulation identity or duplicate object is introduced.

`Stage20MinimumPlayableSpriteCatalogTest` validates role completeness, PNG alpha, atlas regions,
exact physical scaling, production hardpoint alignment, stable fallback behavior and existing
playable/tactical presentation routes.

## Generation provenance

The accepted bitmap sources were generated with the built-in image generation workflow from the
project's Imperial visual brief, then background-extracted where necessary, visually inspected as a
contact sheet, trimmed, centered, downscaled and losslessly stored under
`src/main/resources/assets/stage20_5/`. Failed checkerboard-background and wrong-subject variants
were not included.
