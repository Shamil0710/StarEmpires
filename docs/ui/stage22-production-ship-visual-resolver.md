# M22.7C production ship visual resolver

## Purpose

M22.7C provides one deterministic, fail-closed presentation authority for authored Stage-22 ship visuals across the production-client system, global, tactical and inspector surfaces. Artwork, alpha bounds, pivots and visual-profile metadata remain presentation-only and never become simulation, collision, fitting, economy, sensor or persistence authority.

Human aesthetic approval remains deferred under #361. That review state is deliberately independent from automated binding correctness.

## Authoritative selection key

`Stage22ProductionShipVisualResolver` resolves a visual from accepted Stage-22 content authorities and records an immutable `BindingKey` containing:

- stable runtime/entity identity;
- stable faction identity;
- systemic faction-profile identity;
- ship visual-profile identity and governed maturity status;
- authored ship family and common role;
- exact Stage-22 hull and fit IDs;
- exact semantic fit fingerprint;
- exact production visual-binding ID;
- faction package fingerprint;
- faction-profile catalog fingerprint;
- runtime presentation state (`IDLE`, `THRUSTING`, or `DAMAGED`).

The broad ship visual profile may remain `CONCEPT` while an exact engine binding is `PRODUCTION`. These are separate authorities: the exact binding must be production-approved, its pinned fit fingerprint must match the current engineering catalogue and its classpath PNG must exist.

## Fail-closed rules

For a governed production faction and a recognized authored role/fit, the resolver does not silently substitute legacy Stage-20.5 artwork. Missing faction authority, absent exact binding, stale fingerprint, missing engineering content, package/profile mismatch or missing production PNG are explicit errors.

Non-core factions stay outside Stage-22 production visual authority until they receive authored packages. Compatibility presentation for those identities is intentional and is not a production-binding fallback.

## Faction identity boundary

The accepted Stage-20 generated-world profile currently uses `faction.alpha` and `faction.beta`. Stage-22 identity governance classifies those IDs as `WORLD_GENERATED`, assigns no canonical package key to them and requires their stable IDs to be preserved. They must not be guessed, renamed or visually masqueraded as the Empire or Industrial Union.

The canonical governed package identities are:

- Empire: `faction.imperial_directorate` / `core.empire`;
- Industrial Union: `faction.industrial_combine` / `core.industrial_union`.

Only these canonical IDs enter Stage-22 production ship authority. Migrating ordinary generated campaigns from compatibility lineages to authored core factions is a campaign/content-composition concern outside M22.7C.

## System-map handoff

`GeneratedWorldUiSnapshot.LocalObjectView.withScale()` keeps the existing runtime physical projection and delegates artwork replacement to `Stage22ProductionShipSpriteAdapter` when the object already carries a canonical core faction identity.

Current compatibility mappings are deliberately narrow:

- cargo transport -> Stage-22 `role.support.freight`;
- provisional medium combat hull -> Stage-22 `role.military.destroyer`.

The adapter preserves the runtime-provided world length, width and scale authority. The provisional Stage-21 military destroyer therefore remains physically 230 x 74 m until engineering content is explicitly migrated. Artwork cannot rewrite the simulation envelope.

## Global-map handoff

`GlobalFleetMapSnapshot.FleetMarker` now carries player-known authoritative stable faction identity plus immutable `InstalledFit` for owned fleets. `GlobalFleetMapModel` obtains those values only from player-owned physical fleet state; it does not scan or reveal unrelated remote NPC entities.

`GlobalFleetMapRenderer` resolves Stage-22 artwork only when both canonical core faction identity and exact installed engineering identity are present. Missing exact core binding fails closed. Legacy/non-core fleet markers retain their prior strategic representation.

## Tactical handoff

`LiveTacticalBattleRuntimeState.ImportedCombatantState` carries an optional stable campaign/world faction ID through the exact strategic-to-tactical import. Authored validation scenarios continue to carry only `ALPHA` / `BETA` battle side; those side labels are never promoted into faction identity.

`ScaledLiveTacticalSimulationProjection` copies stable faction identity and immutable `InstalledFit` into `TacticalPrototypeVisualSnapshot.ShipGlyph`.

The Stage-21 tactical compatibility authority is exact-fit based. `Stage22ProductionShipSpriteAdapter.upgradeStage21TacticalProjection(...)` requires the installed fit to equal one of the five registered Stage-21 strategic variants from the accepted engineering catalogue. All five variants use the physical `hull.test_doctrine_destroyer_v1` envelope (230 x 74 m). Only after that equality proof does the adapter select faction-authored Stage-22 destroyer art. Old schematic role (`KINETIC`, `MISSILE`, `BEAM`, `DEFENSIVE_EW`, `BALANCED`) is not used to decide the faction hull artwork.

`TacticalPrototypeRenderer` consumes that common adapter. Wrecks remain on the explicit derelict path; non-wreck core strategic combatants use the validated Stage-22 production binding.

## Inspector handoff

`ShipInspectionProjection` resolves `fitId` from exact equality between the installed fit and the battle-local engineering catalogue rather than reporting only the doctrine base fit. When campaign faction identity exists, the immutable inspection snapshot carries `VisualIdentity(stableFactionId, InstalledFit)`.

`ShipInspectionPanelRenderer` uses the same Stage-21 tactical adapter as the main tactical renderer. Its enlarged preview therefore selects the same faction destroyer artwork from the same exact identity inputs. Side-only and non-core inspection cards retain compatibility artwork and do not invent faction aliases.

## Renderer contract

`Stage20MinimumPlayableTextureRenderer` remains the shared GPU owner for compatibility clients. Stage-20.5 textures are eagerly available. A texture outside that pack is accepted lazily only when `Stage22ProductionShipSpriteAdapter.isProductionPath(...)` validates it under `assets/ships/.../production/..._base.png`.

For production PNGs the renderer derives the source image size and alpha-crops transparent authoring margins before drawing. Unknown non-production paths are rejected rather than substituted.

Current faction production examples include:

- `assets/ships/empire/production/freight/freight_base.png`;
- `assets/ships/industrial_union/production/freight/freight_base.png`;
- `assets/ships/empire/production/destroyer/destroyer_base.png`;
- `assets/ships/industrial_union/production/destroyer/destroyer_base.png`.

## Regression evidence

Automated coverage verifies that:

- Empire and Industrial Union resolve distinct freight and destroyer assets;
- every authored Stage-22 primary/refit fit resolves through an exact production binding;
- installed Stage-22 engineering fits resolve through the same authority as explicit fit IDs;
- fit fingerprints remain pinned and validated;
- runtime visual state participates in the deterministic binding key;
- broad profile `CONCEPT` status remains distinct from exact binding `PRODUCTION` legality;
- unknown faction/role requests fail closed;
- system-map compatibility preserves runtime physical scale;
- global snapshots expose exact faction/fit identity only for player-owned fleets;
- exact Stage-21 tactical fits select destroyer art independently of schematic role;
- core tactical visual selection rejects a base/non-strategic or missing fit instead of guessing;
- non-core and `faction.alpha` / `faction.beta` identities remain on compatibility art;
- exact imported tactical faction/fit identity reaches `ShipGlyph`;
- side-only tactical fixtures do not invent campaign faction identity;
- exact imported faction/fit identity reaches the ship-inspection snapshot.

## M22.7C completion boundary

M22.7C is complete when the exact branch head passes full CI. No remaining resolver-specific surface requires a second visual authority. The next campaign work should not rename `faction.alpha` / `faction.beta` inside presentation code; it should compose or migrate authoritative campaign faction/content lineage explicitly in the appropriate M22.7 campaign stage.
