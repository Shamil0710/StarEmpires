# M22.7C production ship visual resolver

## Purpose

M22.7C introduces one deterministic, fail-closed presentation authority for authored Stage-22 ship visuals. The resolver is intentionally read-only: artwork, alpha bounds, pivots and visual-profile metadata never become simulation, collision, fitting, economy, sensor or persistence authority.

The current integration covers the first production-client handoff: generated-world freight ships owned by the two governed core factions. It does **not** claim that the provisional Stage-20.5 freight hull/fit has already been replaced in simulation, and it does **not** mark the deferred human aesthetic review in #361 as passed.

## Authoritative selection key

`Stage22ProductionShipVisualResolver` resolves a visual from accepted Stage-22 content authorities and records a complete immutable `BindingKey` containing:

- stable runtime/entity identity;
- stable faction identity;
- systemic faction-profile identity;
- ship visual-profile identity and its governed maturity status;
- authored ship family and common role;
- exact Stage-22 hull and fit IDs;
- exact semantic fit fingerprint;
- exact production visual-binding ID;
- faction package fingerprint;
- faction-profile catalog fingerprint;
- runtime presentation state (`IDLE`, `THRUSTING`, or `DAMAGED`).

The broad faction ship visual profile currently remains `CONCEPT`, consistent with deferred human review #361. That status is retained in the key. It is deliberately separate from exact engine-binding legality: the selected exact-fit `VisualBindingDefinition` must be `PRODUCTION`, its pinned fit fingerprint must match the current engineering catalog, and its classpath asset must exist.

## Fail-closed rules

For a governed production faction and a recognized authored role/fit, the resolver never falls back to Stage-20.5 artwork. The following conditions are explicit errors:

- missing faction production authority;
- missing authored role or exact fit;
- missing exact visual binding;
- non-production exact binding;
- stale fit fingerprint;
- missing engineering fit or hull;
- package/profile mismatch;
- missing production PNG.

Non-core factions remain outside this Stage-22 authority and can continue through their existing presentation path until they receive authored production packages.

## Current generated-world freight handoff

`GeneratedWorldUiSnapshot.LocalObjectView.withScale()` is the current production-client seam. The ordinary generated-world model still obtains the physical freight projection from the Stage-20.5 runtime. For a cargo-role object owned by either core faction, `Stage22ProductionShipSpriteAdapter` then replaces only the artwork binding:

- `faction.imperial_directorate` -> `assets/ships/empire/production/freight/freight_base.png`;
- `faction.industrial_combine` -> `assets/ships/industrial_union/production/freight/freight_base.png`.

The adapter preserves the runtime-provided world length, world width and scale authority. This is intentional: the displayed ship cannot silently change the physical simulation envelope before the provisional freight hull/fit itself is migrated. The Stage-22 visual binding and fingerprint are appended to the presentation provenance so the compatibility handoff remains diagnosable.

## Renderer contract

`Stage20MinimumPlayableTextureRenderer` remains the GPU owner used by the generated-world client. Stage-20.5 textures are still eagerly loaded. A texture outside that pack is accepted lazily only when `Stage22ProductionShipSpriteAdapter.isProductionPath(...)` validates it as a Stage-22 ship production base PNG under `assets/ships/.../production/..._base.png`.

For those production PNGs the renderer derives the full source image size at runtime and alpha-crops transparent authoring margins before drawing. Unknown non-production paths are still rejected rather than substituted.

## Regression evidence

Automated coverage verifies that:

- Empire and Industrial Union freight roles resolve to different faction production assets;
- every authored primary/refit fit for both current factions resolves through an exact production binding;
- exact fit fingerprints remain pinned and validated;
- runtime visual state participates in the deterministic key;
- broad profile `CONCEPT` status remains distinct from exact binding `PRODUCTION` legality;
- unknown faction/role requests fail closed;
- the renderer adapter preserves exact Stage-22 scale for direct production use;
- the generated-world compatibility handoff changes only cargo artwork while preserving the current physical scale authority;
- ordinary `GeneratedWorldUiModel.capture()` projects faction-correct production freight artwork for both core factions.

## Remaining M22.7C work

This document does not close #364. The next required integrations are the exact-fit production binding for ordinary military ships and the remaining global/tactical/inspector surfaces. Those paths must use this resolver rather than introduce new role-only or filename-heuristic authorities. Tactical integration must first expose stable faction plus legal fit/content identity in its immutable presentation snapshot; side/role alone is insufficient for a production binding.
