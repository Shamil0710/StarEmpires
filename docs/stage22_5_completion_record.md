# Stage 22 M22.5 — shared civilian/minor ecosystem completion record

> Status: **COMPLETE**  
> Exact implementation head: `2f3e159e173b7413f4e5b71c8e39bdde21c16f5c`  
> Implementation PR: #353 — `Stage 22.5: shared civilian and minor ecosystem`  
> Merged implementation on `main`: `854a40464eaf72e7ee86047f0de1f3ab7c7c5ed6`  
> Post-merge CI: run `33756484564`, job `100652040418` — **SUCCESS**

## 1. Scope and authority boundary

M22.5 prevents the Empire/Industrial Union core pair from existing in a sterile two-faction world while preserving the existing simulation ownership model. It adds shared civilian/minor content bindings, validation and acceptance evidence without introducing a third sovereign faction package or a parallel simulation authority.

Mutable truth remains in accepted authorities:

- Stage-22 authored production composition through `Stage22AuthoredProductionBridge` over Stage-18 manufacturing and shipyard catalogs;
- ordinary trade through `InterSystemTradeService`;
- logistics through `Stage18LogisticsRuntime`;
- extraction through `Stage18ExtractionRuntime`;
- salvage through `Stage18SalvageRuntime`;
- generated civilian traffic through `Stage21EGeneratedWorldTrafficRuntime`;
- ownership/territory through `FactionTerritoryService`;
- legal market access through `DiplomaticMarketAccessResolver`;
- customs/tariffs through `CustomsTariffResolver`;
- NPC contract/mission objectives through `Stage21HNpcMissionService`;
- convoy/interdiction traffic effects through `Stage21EOperationTrafficPolicy`.

No M22.5 class owns credits, inventory, territory, diplomacy, fleet state, construction progress, extraction yield or a faction-specific warfare/economy shortcut.

## 2. Shared civilian role floor and legal production closure

The accepted ecosystem covers all five required shared civilian roles:

- freight;
- tanker;
- mining;
- salvage;
- neutral traffic.

Four roles use individual licensed core assets without package/profile inheritance:

- freight: `fit.industrial_union.freight.bulk_v1` → `production_manifest.industrial_union.freight_v1`;
- tanker: `fit.empire.tanker.fleet_v1` → `production_manifest.empire.tanker_v1`;
- salvage: `fit.industrial_union.fleet_support.salvage_refit_v1` → `production_manifest.industrial_union.fleet_support_v1`;
- neutral traffic: `fit.empire.freight.bulk_v1` → `production_manifest.empire.freight_v1`.

A minor operator may use these concrete assets only through ordinary production/market/access conditions. Concrete asset availability does not confer Empire or Industrial Union doctrine, policy state or package identity.

## 3. Dedicated civilian mining bridge

Mining preserves compatibility with `ship.basic_miner` but closes replacement/operation through an explicit non-sovereign production path rather than bootstrap magic or an unchanged repair fit.

Accepted chain:

- compatibility archetype: `ship.basic_miner`;
- reviewed physical envelope source: `fit.industrial_union.fleet_support.repair_v1`;
- dedicated civilian fit: `fit.civilian.miners.asteroid_excavator_v1`;
- dedicated mission module: `module.civilian.miners.asteroid_excavation_section_v1`;
- exact civilian production manifest: `production_manifest.civilian.miners.asteroid_excavator_v1`;
- runtime extraction: `extraction.asteroid_excavation`;
- capability: `capability.extraction.asteroid_excavation`.

`Stage22CivilianMiningEngineeringCatalogLoader` composes the reviewed physical hull/reactor/drive/sensor/thermal envelope, replaces exactly one repair/salvage workshop with the mining mission section, and supplies ordinary Stage-18 manufacturing/shipyard closure through the accepted production bridge.

`Stage22CivilianMiningProductionPath` performs a real committed Stage-18 extraction and also proves the accepted repair fit is rejected for asteroid mining. This is a negative control against implicit capability inheritance.

The operator remains `faction.miners`; reuse of physical components does not promote the actor into `core.industrial_union`.

## 4. Minor identities, localization and service providers

The existing governed identities remain stable:

- `faction.neutral` — `MINOR_AUTHORED`, canonical display `Независимые`;
- `faction.trade_league` — `TRANSNATIONAL_NETWORK`, canonical display `Торговая Лига`;
- `faction.miners` — `MINOR_AUTHORED`, canonical display `Горняки`.

All remain package-free and disallow major-package fallback.

Neutral/minor service provision reuses existing constructible station archetypes:

- `station.colony` → `faction.neutral`;
- `station.agrodome` → `faction.trade_league`;
- `station.mining_base` → `faction.miners`.

Validation requires every provider to exist in the content catalog, retain its declared owner and physical construction definition, and bind ownership/access/tariff/logistics to existing common authorities.

## 5. Trade, contract and insurance boundary

M22.5 exposes:

- active trade integration through `InterSystemTradeService`;
- active contract/mission integration through `Stage21HNpcMissionService`;
- insurance as an explicitly **deferred, non-authoritative content hook**.

No insurance treasury, premium ledger, debt system, private-finance simulation or League/Consortium package is fabricated to satisfy this milestone.

## 6. B08 convoy/interdiction acceptance

The core-pair content binding uses existing convoy mission content:

- Empire: `mission.empire.convoy_guard`;
- Industrial Union: `mission.industrial_union.corridor_escort`.

`Stage22CivilianMinorScenarioAcceptanceTest` creates an ordinary operational combat fleet and active interception operation. `Stage21EOperationTrafficPolicy` denies the exact civilian traffic edge with `DENIED_BY_PHYSICAL_INTERDICTION` while that real fleet physically anchors the edge. Moving the same `FleetId` away through ordinary fleet transfer reopens the edge.

The traffic query is asserted read-only. There is no scripted convoy-success modifier or Stage-22 mutable warfare state.

## 7. B16 treaty/market-route shock acceptance

The core pair uses existing market-access mission content:

- Empire: `mission.empire.formal_market_access`;
- Industrial Union: `mission.industrial_union.access_contract`.

Acceptance begins from an actually denied relation-threshold state with a 750-basis-point standard tariff. A real mutual `MARKET_ACCESS` + `CUSTOMS_TARIFF_EXEMPTION` treaty is offered and accepted through the persisted treaty command boundary; `DiplomaticMarketAccessResolver` opens access and `CustomsTariffResolver` reduces tariff to zero. A real treaty breach restores the denial and standard tariff shock.

No remote faction debuff, scripted market modifier or duplicate diplomacy state is introduced.

## 8. Persistence, determinism and fail-closed validation

`Stage22CivilianMinorMigrationAcceptanceTest` exercises a supported generated world through `WorldStateCodec` encode → decode → encode and requires byte-stable output plus exact stable identity resolution for `faction.neutral`, `faction.trade_league` and `faction.miners`.

`Stage22CivilianMinorEcosystemValidator` additionally requires:

- exactly the full five-role civilian floor;
- five legal production/support closures and zero unresolved production roles;
- exact fit/manifest bindings for licensed assets;
- a valid dedicated civilian mining fit/module/manifest and real extraction capability;
- three valid constructible service providers;
- three governed package-free minor/network identities;
- authority references that actually resolve;
- valid B08/B16 core-pair mission bindings;
- deferred insurance to remain non-authoritative;
- deterministic ecosystem fingerprinting;
- invalid minor/hook definitions to fail closed.

## 9. Principal automated evidence and merge verification

Targeted M22.5 coverage includes:

- `Stage22CivilianMinorEcosystemValidatorTest`;
- `Stage22CivilianMinorMigrationAcceptanceTest`;
- `Stage22CivilianMinorScenarioAcceptanceTest`;
- the mining runtime/engineering path exercised through validator acceptance;
- full repository Stage-17.5/18/21/22 regression verification through CI.

Closure evidence:

- exact implementation head: `2f3e159e173b7413f4e5b71c8e39bdde21c16f5c`;
- exact-head PR CI run `33754120684` (#5777), job `100644294043`: **SUCCESS**;
- full verify on the accepted implementation tree: **1934 tests, 0 failures, 0 errors, 1 skipped**;
- strict Javadoc passes after documenting all seven public compact record constructors and their 50 canonical parameters without changing executable behavior;
- JaCoCo reports all configured coverage checks met;
- desktop shaded-JAR/package gate passes;
- Stage-19J long-soak workflow is conditionally skipped for this PR and is not a required M22.5 gate;
- pre-merge audit found no submitted review, no unresolved review thread, no requested changes, no base drift and a mergeable exact head;
- guarded merge of PR #353 produced `main` commit `854a40464eaf72e7ee86047f0de1f3ab7c7c5ed6`;
- tested implementation head and merge commit share exact tree SHA `7ab16b37d836a7621a7f6f260714c219456a641d`;
- post-merge `main` CI run `33756484564` (#5778), job `100652040418`: **SUCCESS**, with the same full repository verification gates green and successful artifact publication.

All M22.5 roadmap exit gates are therefore satisfied on merged and post-merge-verified evidence.

## 10. Exit criteria matrix

| M22.5 roadmap criterion | Accepted evidence |
|---|---|
| neutral/minor ports and logistics services | three constructible station providers bound to ordinary ownership/access/tariff/logistics authorities |
| freighter/tanker/mining/salvage traffic | five-role civilian catalog with legal core/dedicated production paths and existing traffic/runtime authorities |
| trade/contract hooks | `InterSystemTradeService` + `Stage21HNpcMissionService`; insurance explicitly deferred |
| minor identity classes/localization | governed `MINOR_AUTHORED` / `TRANSNATIONAL_NETWORK` identities with canonical display names and no package fallback |
| ownership/access rules | `FactionTerritoryService`, `DiplomaticMarketAccessResolver`, `CustomsTariffResolver` bindings |
| convoy/interdiction content | behavioral B08 physical-interdiction acceptance using an ordinary operation fleet |
| market and route shocks | behavioral B16 treaty/access/tariff open → breach → restore acceptance |
| old demo/minor migration | byte-stable `WorldStateCodec` round trip with exact stable minor IDs |
| B08/B16 runnable on core pair | one valid Empire and Industrial Union mission binding for each scenario plus runtime acceptance |
| civilian legal production/support path | four licensed exact-fit paths + dedicated civilian mining manufacturing/shipyard/extraction closure |

## 11. Intentionally deferred beyond M22.5

M22.5 does **not** implement:

- an insurance/debt/private-finance authority;
- a production-complete League of Free Systems or Consortium package;
- any third core sovereign faction package;
- M22.6 Empire-vs-Industrial-Union paired multi-seed tuning/freeze;
- final core-pair dominance tuning or balance sign-off;
- Stage-23 polish/RC work.

These are explicit later-stage boundaries, not missing civilian/minor authorities.

## 12. Next

**M22.6 — core pair balance/freeze is OPEN/NEXT. Its implementation has not begun in the M22.5 closeout.**
