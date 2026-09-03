# Star Empires — Stage 22 / M22.5 shared civilian and minor ecosystem

> **Status:** IMPLEMENTATION CANDIDATE — functional closure is present on the feature branch; acceptance remains gated by green exact-head CI, merge and green `main`.  
> **Scope authority:** `docs/factions/faction_implementation_roadmap.md`, M22.5.  
> **Balance authority:** `docs/factions/faction_balance_validation_framework.md`, especially B08/B16.  
> **Content/identity authority:** `data/content/stage22-content-governance-v1.json`.

## 1. Purpose and boundary

M22.5 prevents the Empire/Industrial Union core pair from existing in a sterile two-faction world while preserving the existing simulation ownership model.

This milestone does **not** introduce a third sovereign production package and does not implement the post-core League, Consortium or any other horizon faction. Neutral/minor actors remain the existing stable identities:

- `faction.neutral` — governed authored minor;
- `faction.trade_league` — governed transnational network;
- `faction.miners` — governed authored minor.

All three preserve their stable runtime/save IDs. Their Stage-22 governance entries intentionally have no `canonicalPackageKey`; therefore M22.5 must never resolve them through Empire/Industrial Union doctrine/profile fallback.

## 2. Authority reuse

M22.5 adds immutable content bindings and validation only. Mutable truth remains in existing authorities.

| Concern | Existing authority reused |
|---|---|
| physical production composition | `Stage22AuthoredProductionBridge` over Stage-18 manufacturing/shipyard catalogs |
| ordinary inter-system trade | `InterSystemTradeService` |
| logistics | `Stage18LogisticsRuntime` |
| extraction | `Stage18ExtractionRuntime` + `extraction.asteroid_excavation` |
| salvage | `Stage18SalvageRuntime` + `extraction.salvage_recovery` |
| generated civilian traffic | `Stage21EGeneratedWorldTrafficRuntime` |
| ownership/territory | `FactionTerritoryService` |
| legal market access | `DiplomaticMarketAccessResolver` |
| customs/tariffs | `CustomsTariffResolver` |
| contract/mission objectives | `Stage21HNpcMissionService` |
| convoy/interdiction operation traffic | `Stage21EOperationTrafficPolicy` |

No M22.5 class owns inventory, credits, territory, diplomacy, construction progress, extraction yield or fleet state.

## 3. Civilian asset availability

The contract distinguishes compatibility archetypes from legally licensed Stage-22 core assets while requiring every M22.5 role to have an actual production/support closure.

### Licensed production paths

- freight: `fit.industrial_union.freight.bulk_v1` → `production_manifest.industrial_union.freight_v1`;
- tanker: `fit.empire.tanker.fleet_v1` → `production_manifest.empire.tanker_v1`;
- salvage: `fit.industrial_union.fleet_support.salvage_refit_v1` → `production_manifest.industrial_union.fleet_support_v1`;
- neutral traffic: `fit.empire.freight.bulk_v1` → `production_manifest.empire.freight_v1`.

These are **individual licensed market assets**, not profile inheritance. A minor operator may obtain/use the concrete asset only through ordinary market/access/production conditions; it does not receive the source faction's doctrine, policy state or package identity.

### Mining compatibility-to-production bridge

Mining preserves `ship.basic_miner` for supported runtime/save compatibility, but replacement availability is no longer treated as implicit bootstrap magic.

`Stage22CivilianMiningProductionPath` proves the explicit bridge:

- compatibility runtime archetype: `ship.basic_miner`;
- licensed reviewed physical fit: `fit.industrial_union.fleet_support.repair_v1`;
- exact production manifest: `production_manifest.industrial_union.fleet_support_v1`;
- real Stage-18 shipyard hull/module profiles and manufacturing bindings;
- industrial/mining-capable common module family on the licensed fit;
- operating extraction authority: `Stage18ExtractionRuntime` with `extraction.asteroid_excavation`.

The minor operator remains `faction.miners`; licensing this individual support fit does not grant Industrial Union doctrine/profile identity.

`Stage22CivilianMinorEcosystemValidator` removes `CivilianRole.MINING` from the compatibility row's unresolved list only after this bridge validates successfully. The resulting validation report requires `unresolvedProductionRoles == []` and `productionClosureReady() == true`.

## 4. Neutral/minor service providers

The catalog uses real existing constructible station archetypes rather than invented provider IDs:

- `station.colony` → `faction.neutral`;
- `station.agrodome` → `faction.trade_league`;
- `station.mining_base` → `faction.miners`.

`Stage22CivilianMinorEcosystemValidator` requires each provider to resolve in `ContentCatalog`, retain the declared owner, retain a physical construction definition, and bind ownership/access/tariff/logistics to the existing authorities above.

## 5. Trade / contract / insurance boundary

- trade hook is active through `InterSystemTradeService`;
- contract hook is active through `Stage21HNpcMissionService`;
- insurance is deliberately a **deferred content hook** only.

M22.5 does not create an insurance treasury, debt ledger, premium balance, private finance simulation or League-specific economy. Such systems require a later explicit architecture decision and must not appear as hidden M22.5 state.

## 6. B08 — Convoy escort/interdiction

The content binding resolves both core factions into existing convoy mission content:

- Empire: `mission.empire.convoy_guard` (`CONVOY_ESCORT`);
- Industrial Union: `mission.industrial_union.corridor_escort` (`CONVOY_ESCORT`).

Primary operation/traffic seam: `Stage21EOperationTrafficPolicy`.

`Stage22CivilianMinorScenarioAcceptanceTest` provides behavioral rather than metadata-only evidence. It creates an ordinary operational combat fleet and an active interception operation, verifies that `Stage21EOperationTrafficPolicy` denies the exact civilian traffic edge through `DENIED_BY_PHYSICAL_INTERDICTION`, and then moves the same fleet through ordinary `beginFleetTransfer`. Once the physical interdiction fleet leaves the edge anchor, the same edge becomes available again.

The traffic query is also asserted read-only. No scripted convoy-success modifier or Stage-22 mutable warfare state is introduced.

## 7. B16 — Treaty/market access shock

The content binding resolves both core factions into existing market-access mission content:

- Empire: `mission.empire.formal_market_access` (`MARKET_ACCESS_ALLOWED`);
- Industrial Union: `mission.industrial_union.access_contract` (`MARKET_ACCESS_ALLOWED`).

Primary legal seams are `DiplomaticMarketAccessResolver`, `CustomsTariffResolver` and the existing persisted treaty command boundary.

`Stage22CivilianMinorScenarioAcceptanceTest` starts from an actually denied relation-threshold state with a 750-basis-point standard tariff, offers and accepts a real mutual `MARKET_ACCESS` + `CUSTOMS_TARIFF_EXEMPTION` treaty, and verifies that legal access opens and tariff falls to zero through the existing resolvers. A real treaty breach then restores the original access denial and standard tariff shock.

No remote faction debuff, scripted market modifier or duplicate diplomacy authority is introduced.

## 8. Save migration / stable identity evidence

`Stage22CivilianMinorMigrationAcceptanceTest` exercises a supported generated world through `WorldStateCodec` encode → decode → encode and requires deterministic bytes plus stable runtime/display resolution for:

- `faction.neutral`;
- `faction.trade_league`;
- `faction.miners`.

The test also requires every one of these identities to have no core `canonicalPackageKey` and no major-package fallback.

## 9. Validation and acceptance gate

`Stage22CivilianMinorEcosystemValidator.validateDefault()` requires:

- all five civilian roles to be explicitly represented;
- all five roles to have a legal production/support closure, including the mining compatibility bridge;
- every licensed fit to match a real core ship family and its exact production manifest;
- every service provider to resolve to a real constructible station with matching owner;
- authority class references to resolve;
- B08 and B16 to bind one valid mission from each core faction;
- insurance to remain deferred/non-authoritative;
- minor IDs to remain governed and package-free;
- deterministic ecosystem fingerprinting.

Representative automated evidence:

- `Stage22CivilianMinorEcosystemValidatorTest` — five roles, five production closures, three providers, three preserved minors, zero unresolved production roles and no major-package fallback;
- `Stage22CivilianMinorMigrationAcceptanceTest` — deterministic binary round-trip plus stable minor IDs/runtime slots;
- `Stage22CivilianMinorScenarioAcceptanceTest` — real B08 physical interdiction and real B16 treaty/access/tariff shock.

### M22.5 completion process

Functional closure is present on the feature branch, but milestone status is not advanced by this document alone. The required release sequence remains:

1. green full CI on the exact final implementation branch SHA;
2. inspect exact diff and merge that tested implementation SHA into `main`;
3. verify resulting `main` and available post-merge CI;
4. only then record M22.5 `COMPLETE`, create canonical completion evidence and advance Stage 22 to M22.6.
