# Star Empires — Stage 22 / M22.5 shared civilian and minor ecosystem

> **Status:** IMPLEMENTATION IN PROGRESS — authority/content binding landed on feature branch; mining production closure remains a blocker.  
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

The current contract distinguishes compatibility archetypes from legally licensed Stage-22 core assets.

### Production-closed licensed paths

- freight: `fit.industrial_union.freight.bulk_v1` → `production_manifest.industrial_union.freight_v1`;
- tanker: `fit.empire.tanker.fleet_v1` → `production_manifest.empire.tanker_v1`;
- salvage: `fit.industrial_union.fleet_support.salvage_refit_v1` → `production_manifest.industrial_union.fleet_support_v1`;
- neutral traffic: `fit.empire.freight.bulk_v1` → `production_manifest.empire.freight_v1`.

These are **individual licensed market assets**, not profile inheritance. A minor operator may obtain/use the concrete asset only through ordinary market/access/production conditions; it does not receive the source faction's doctrine, policy state or package identity.

### Explicit unresolved path

- mining traffic currently resolves the compatibility archetype `ship.basic_miner` and Stage-18 extraction method `extraction.asteroid_excavation`;
- that legacy archetype does not itself prove a Stage-18/Stage-22 ship production manifest;
- therefore `CivilianRole.MINING` remains the only explicit `unresolvedProductionRole` and blocks M22.5 completion.

The blocker must be closed by an authored shared/licensed mining fit with a real manufacturing/shipyard path. It must not be hidden by treating bootstrap availability as construction authority.

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

The M22.5 binding resolves both core factions into existing convoy mission content:

- Empire: `mission.empire.convoy_guard` (`CONVOY_ESCORT`);
- Industrial Union: `mission.industrial_union.corridor_escort` (`CONVOY_ESCORT`).

Primary operation/traffic seam: `Stage21EOperationTrafficPolicy`.

The acceptance meaning remains the canonical B08 contract: support fleet and objective play must protect or threaten real logistics/civilian traffic. No scripted convoy-success modifier is introduced by M22.5.

## 7. B16 — Treaty/market access shock

The M22.5 binding resolves both core factions into existing market-access mission content:

- Empire: `mission.empire.formal_market_access` (`MARKET_ACCESS_ALLOWED`);
- Industrial Union: `mission.industrial_union.access_contract` (`MARKET_ACCESS_ALLOWED`).

Primary legal seam: `DiplomaticMarketAccessResolver`, whose existing precedence remains authoritative (embargo, treaty right, relation fallback). M22.5 introduces no remote faction debuff for access loss.

## 8. Save migration / stable identity evidence

`Stage22CivilianMinorMigrationAcceptanceTest` exercises a supported generated world through `WorldStateCodec` encode → decode → encode and requires deterministic bytes plus stable runtime/display resolution for:

- `faction.neutral`;
- `faction.trade_league`;
- `faction.miners`.

The test also requires every one of these identities to have no core `canonicalPackageKey` and no major-package fallback.

## 9. Current validation gate

`Stage22CivilianMinorEcosystemValidator.validateDefault()` currently requires:

- all five civilian roles to be explicitly represented;
- every licensed fit to match a real core ship family and its exact production manifest;
- every service provider to resolve to a real constructible station with matching owner;
- authority class references to resolve;
- B08 and B16 to bind one valid mission from each core faction;
- insurance to remain deferred/non-authoritative;
- minor IDs to remain governed and package-free;
- unresolved production roles to remain visible.

### M22.5 completion remains blocked until

1. `CivilianRole.MINING` receives a legal production/support path;
2. targeted and full CI are green on the final exact branch SHA;
3. B08/B16 acceptance evidence remains green after the mining path is added;
4. roadmap status is updated only after the implementation is merged into `main` and main CI is green.
