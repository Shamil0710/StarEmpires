# M22.7C production ship visual resolver

## Purpose

M22.7C introduces one deterministic, fail-closed presentation authority for authored Stage-22 ship visuals. The resolver is intentionally read-only: artwork, alpha bounds, pivots and visual-profile metadata never become simulation, collision, fitting, economy, sensor or persistence authority.

The current integration establishes the production resolver and a generated-world UI compatibility seam for ships that already carry one of the two canonical governed core faction identities. It does **not** rename or reinterpret world-generated compatibility factions, does **not** claim that provisional Stage-20.5/21 freight or military engineering has already been replaced in simulation, and does **not** mark the deferred human aesthetic review in #361 as passed.

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

Non-core factions remain outside this Stage-22 authority and continue through their existing presentation path until they receive authored production packages.

## Faction identity boundary

The accepted Stage-20 generated-world profile currently uses `faction.alpha` and `faction.beta`. Stage-22 identity governance explicitly classifies those IDs as `WORLD_GENERATED`, assigns no canonical package key to them and requires their stable IDs to be preserved. They therefore **must not** be guessed, renamed or visually masqueraded as the Empire or Industrial Union.

The current canonical core package identities are:

- Empire: `faction.imperial_directorate` / `core.empire`;
- Industrial Union: `faction.industrial_combine` / `core.industrial_union`.

Only those canonical IDs enter the Stage-22 production ship resolver. Existing `faction.alpha` / `faction.beta` generated campaigns remain on their compatibility artwork until campaign composition gains an explicit authoritative core-faction lineage; that is a campaign/content migration concern, not a presentation alias.

## Current system-map compatibility handoff

`GeneratedWorldUiSnapshot.LocalObjectView.withScale()` is the current production-client seam. The UI model first obtains physical dimensions from the existing runtime authority; `Stage22ProductionShipSpriteAdapter` may then replace only the artwork for a canonical core-faction ship whose compatibility role is explicitly mapped.

Current mappings are deliberately narrow:

- cargo transport -> Stage-22 `role.support.freight`;
- provisional medium combat hull -> Stage-22 `role.military.destroyer`.

For core identities this produces the faction-specific production base PNGs, including:

- `assets/ships/empire/production/freight/freight_base.png`;
- `assets/ships/industrial_union/production/freight/freight_base.png`;
- `assets/ships/empire/production/destroyer/destroyer_base.png`;
- `assets/ships/industrial_union/production/destroyer/destroyer_base.png`.

The adapter preserves the runtime-provided world length, world width and scale authority. In particular, the Stage-21 provisional military destroyer remains physically 230 x 74 m until its engineering content is explicitly migrated. Artwork cannot silently rewrite that envelope. The Stage-22 visual binding and fingerprint are appended to presentation provenance so the compatibility handoff remains diagnosable.

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
- canonical core cargo compatibility changes only artwork while preserving current physical scale authority;
- canonical core medium-combat compatibility resolves faction destroyer artwork while preserving the provisional 230 x 74 m physical hull;
- `faction.alpha` / `faction.beta` generated freight retains its exact stable identity and legacy compatibility artwork rather than being silently rebound to a core package.

## Remaining M22.7C work

This document does not close #364. Global and tactical surfaces still need authoritative stable faction plus legal fit/content identity before they can use this resolver without guessing. The legacy tactical snapshot currently exposes scenario side (`ALPHA` / `BETA`) and doctrine role/fit but no campaign stable faction identity; those side labels must not be treated as aliases for Empire/Industrial Union. Inspector/global integration must likewise carry authoritative identity into their immutable snapshots instead of creating a second role-only or filename-heuristic visual authority.
