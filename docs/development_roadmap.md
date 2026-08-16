# Star Empires — канонический roadmap разработки

> **Последняя синхронизация: 2026-08-16.**
>
> Этот файл — текущий authoritative status/dependency roadmap. Предыдущая подробная версия до синхронизации Stage 17G сохранена без изменений в `docs/archive/development_roadmap_pre_stage17g_2026-08-16.md`.
>
> Язык новой и содержательно изменяемой документации начиная со Stage 16 — русский; Java/API/content ID/formulas сохраняются в оригинальном виде.

## 1. Главный инвариант проекта

**Star Empires** — 2D top-down space sandbox/RPG/strategy с живой физической экономикой и миром, существующим независимо от игрока.

```text
один корабль
→ торговец / шахтёр / наёмник
→ несколько собственных кораблей
→ компания и автономные флоты
→ собственные станции
→ собственная фракция
→ территория, дипломатия и война
→ региональная / галактическая держава
```

Главный architectural contract:

> **Игрок и AI используют одни и те же физические, информационные, политические и экономические правила везде, где это практически возможно.**

Без отдельного architecture decision запрещены player-only economy/combat rules, passive income вместо real transfers, virtual deliveries, hidden resource grants, scripted asset replacement, мгновенное ordinary travel/construction/refit, class-name performance bonuses и UI, напрямую мутирующий authoritative simulation state.

## 2. Milestones

| Milestone | Цель | Stages | Статус |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | deterministic economic core | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | multi-system factions/logistics/construction/expansion | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | player ship/travel/trade/mining/combat/progression | 12–14 | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | fleets/stations/player faction/combat depth/industry/warfare | 15–19 + 17.5 | **ACTIVE — Stage 17H NEXT** |
| **v0.5 RPG & Living World** | calibrated world generation/discovery/NPC/missions/reputation | 20–21 | PLANNED |
| **v0.6 Content & Balance Alpha** | technology/content breadth + long-horizon balance | 22 | PLANNED |
| **v0.7 Polish / RC** | UX/onboarding/performance/save hardening | 23 | PLANNED |

Manual merge gate remains mandatory because branch protection cannot currently be configured through the available connector:

```text
branch from exact green main
→ clean verify on exact PR head
→ inspect exact diff/head SHA
→ merge that exact SHA only
→ post-merge CI on exact new main SHA
```

## 3. Completed foundation

### Stages 0–6 — v0.1 Economic Sandbox

**COMPLETE.** Java-17 CI/JUnit/JaCoCo/Javadoc, deterministic fixed time, integer milli-credits, conserved ledger semantics, stable identity/persistence, data-driven content, local logistics and headless observability.

### Stages 7–11 — v0.2 Living Galactic Economy

**COMPLETE.** Galaxy/Sector/StarSystem hierarchy, ordinary faction treasury/economy, dynamic economy, physical construction/destruction, inter-system logistics and autonomous physical expansion. Stage 8.5 remains **KEEP_LIBGDX**.

### Stages 12–14 — v0.3 Playable Space Sandbox

**COMPLETE.** Player state/ownership, physical travel/docking/manual trade, shared combat vertical slice, mining, ship progression, UI and deterministic long-horizon economic acceptance.

### Stage 15 — player fleets

**COMPLETE.** Multiple owned `FleetId`, persistent fleet orders, shared inertial movement, physical trade/mining, threat/risk context, global map and ordinary jump FSM.

### Stage 16 — player construction / owned stations

**COMPLETE.** Real construction site/wallet/material delivery/build time, remote continuation, ordinary station materialization, ownership reconciliation and destruction without free replacement.

Canonical records:

- `docs/stage16_player_construction.md`;
- `docs/stage16_construction_timing.md`;
- `docs/stage16_acceptance_matrix.md`;
- `docs/stage16_completion_record.md`.

## 4. Stage 17 — собственная фракция игрока

**ACTIVE. 17A–17G COMPLETE. 17H — NEXT и остаётся финальным Stage-17 transition gate.**

Цель Stage 17: независимый игрок с существующими Stage-15/16 assets становится обычным faction actor без замены `FleetId`/`EntityId`, без бесплатной территории/капитала и без player-only diplomacy/economy.

```text
physical economy / territory / security state
→ measurable interests and dependencies
→ doctrine + diplomatic history
→ common command / strategic decision
→ legal access / tariff / treasury / logistics / production consequences
→ changed physical world state
→ changed future interests and policy
```

### 17A–17E — faction identity, treasury/assets, territory, diplomacy/access

**COMPLETE.**

Закрытые contracts:

- dynamic player-created faction identity + persistence;
- founding без grant денег/земли/assets;
- same physical player assets получают faction affiliation без replacement;
- personal/company wallet и public treasury раздельны, transfers conserved;
- presence/claim/stabilization/control/recognition/concession — distinct legal states;
- common treaty/embargo lifecycle для player/AI;
- market access определяется legal resolver;
- customs/treaty consequences идут через ordinary transactions;
- structural dependency измеряется из physical supply/access state.

### 17F — faction policies / strategic economy

**COMPLETE — 17F.1–17F.7.**

- **17F.1 doctrine:** persistent bounded institutional profile, shared decision weights, no performance bonuses.
- **17F.2 fiscal policy:** tax/territorial tariff/reserve/liquidity/construction budget authoring over conserved flows.
- **17F.3 fiscal trade-offs:** treasury/station/construction diagnostics and real wallet trade-offs.
- **17F.4 stock/production policy:** persistent stock floors/production preferences, explicit ordinary materialization only.
- **17F.5 resilience policy:** buffers, supplier diversification, local production, capacity-gap construction, redundant routes and critical-import limits through ordinary physical systems.
- **17F.6 policy feedback / anti-oscillation:** shared persistent review cadence, bounded fiscal/resilience adjustments, reversible overlays, no every-tick recipe/tax/tariff oscillation. Record: `docs/stage17f6_policy_feedback_completion_record.md`.
- **17F.7 player/AI command parity:** `FactionPolicyCommand` + `FactionPolicyCommandExecutor`; player adapter submits the same doctrine/fiscal/stock-production/apply commands as common AI/world callers.

Hard rule:

```text
policy authoring
≠ money/cargo/output creation
```

Physical consequences are realized only by ordinary market/logistics/production/treasury/construction systems.

### 17G — faction management / strategic global-map authority

**COMPLETE — PR #133.** Canonical closeout: `docs/stage17g_faction_management_completion_record.md`.

17G is an application/read-model layer over Stage-17 authoritative state, not a second simulation subsystem.

Implemented:

- immutable `FactionManagementSnapshot` / `FactionManagementModel`;
- explicit independent vs affiliated shape;
- personal wallet + faction economy/treasury;
- doctrine/fiscal/base stock-production/resilience projection;
- player-owned physical fleets/projects/stations;
- territory only for player-known systems through ordinary legal assessment;
- own diplomacy + deterministic effective-access summaries;
- persistent strategic growth plans;
- shared management commands delegating to authoritative treasury/policy/diplomacy/territory systems;
- `FactionGlobalMapSnapshot` / `FactionGlobalMapModel` as presentation projection.

Acceptance proves deterministic/mutation-free projection, ID-preserving affiliation, exact capitalization conservation, common policy/diplomacy paths, no instant sovereignty, no cargo/money creation and save/load preservation.

### 17H — persistence / migration / Stage-17 end-to-end gate

**NEXT.**

Stage 17 becomes COMPLETE only after this exact scenario is green:

```text
independent player with Stage-16 assets
→ found faction
→ same physical assets affiliated
→ transfer real capital
→ apply ordinary policy
→ economy reacts through ordinary systems
→ territory/access only by legal rules
→ save/load
→ diplomacy/access persist
→ no duplication/reset/resources created
```

17H additionally audits current persistence versions/migrations and pre-Stage17 save compatibility. The Stage-17G round-trip does not substitute this final transition/migration gate.

## 5. Stage 17 → 17.5 transition gate

**Stage 17.5 remains BLOCKED until 17H and the final Stage-17 post-merge CI are green.**

Accepted research/design prerequisite already exists:

- `docs/ship_mathematics_v1_0_design_baseline.md` — **ACCEPTED DESIGN BASELINE**;
- `docs/benchmarks/ship_mathematics_v1_0_design_baseline.json`;
- `docs/ship_mathematics_v1_roadmap_integration_contract.md`;
- `docs/ship_hull_module_and_fleet_doctrine.md`;
- `docs/flight_dynamics_and_combat_depth_roadmap.md`;
- PR #91 / merge `3ec2f6cab286dbcd39694c19a055d038c175b59c`.

The first Stage-17.5 implementation slice is fixed: **17.5A schema/material/hull/module**. Stage 18 cannot activate before Stage 17.5 COMPLETE.

## 6. Stage 17.5 — Combat Depth / Ship Fitting Foundation

**PLANNED / BLOCKED by Stage 17.** Research gate complete; runtime promotion not started.

Implementation sequence:

- **17.5A:** production `MaterialDefinition` / `HullDefinition` / `ModuleDefinition` / protection/slots/hardpoints/compartments;
- **17.5B:** one central derived-ship calculator + fitting validator;
- **17.5C:** propulsion/reaction mass/power/thermal/FTL;
- **17.5D:** sensors/signatures/track state/datalink/EW;
- **17.5E:** kinetic/beam/guided/PD/ammunition;
- **17.5F:** shields/armor/compartments/subsystem damage;
- **17.5G:** shipyard/refit/repair/maintenance economy;
- **17.5H:** capability APIs/UI/persistence;
- **17.5I:** deterministic aggregate acceptance.

Hard invariants: no player-only combat physics, no class-name bonuses, no free ammunition/reaction mass, no final global-HP-only survivability model, every module uses common mass/volume/power/heat/economy budgets, authoritative fitted/consumable/damage state is persistent and deterministic.

Detailed plan: `docs/stage17_5_combat_depth_implementation_plan.md`.

## 7. Stage 18 — Resources / Industry / Infrastructure Foundation

**PLANNED after 17.5 COMPLETE.**

Stage 18 закрывает онтологию материального мира **до** warfare и procedural world generation.

Главный вопрос:

> Какие физические ресурсы существуют, где в принципе могут встречаться, как добываются, во что перерабатываются, какие facilities нужны, как из этого строятся modules/ships/stations и какие реальные logistics bottlenecks возникают?

Каноническая цепочка:

```text
resource occurrence
→ compatible extraction
→ finite feedstock
→ refining / purification
→ engineering material / consumable
→ industrial component
→ module / ammunition / infrastructure
→ ship / station
→ wear / repair / loss
→ bounded salvage / recycling
```

Baseline намеренно **реалистичен, но агрегирован**. Отдельный commodity/process создаётся только если он добавляет meaningful source, extraction/refining requirement, storage/logistics constraint, strategic bottleneck или substitution/recycling choice.

### Baseline raw families

- `WATER_ICE`;
- `VOLATILE_FEEDSTOCK`;
- `CARBONACEOUS_FEEDSTOCK`;
- `METALLIC_ORE`;
- `LIGHT_METAL_MINERALS`;
- `CONDUCTOR_ORE`;
- `STRATEGIC_METAL_ORE`;
- `SILICATE_MINERALS`;
- `FISSILE_MINERALS` where current technology requires it.

### Baseline industrial outputs

- purified water / industrial gases / technology-specific reaction mass or fuel;
- structural / light / conductor / refractory material families;
- ceramics/glass, carbon materials, industrial chemicals, electronic-grade materials;
- `HEAVY_COMPONENTS`, `ELECTRICAL_COMPONENTS`, `PRECISION_COMPONENTS`;
- real module/ammunition/station/ship recipes;
- bounded salvage/recycling.

### Stage 18 sub-stages

- **18A:** schema / resource ontology;
- **18B:** extraction and source compatibility;
- **18C:** refining / material production;
- **18D:** industrial components + module/ammunition recipes;
- **18E:** facility capability architecture;
- **18F:** stations / storage / logistics;
- **18G:** shipyard / repair / refit industrial integration;
- **18H:** recycling / salvage / construction economy;
- **18I:** deterministic minimal-industrial-universe acceptance.

Hard rules:

- no `ORE → SHIP` direct economy;
- no production from credits without physical inputs;
- no station-class magic bonuses;
- no infinite deposits by default;
- no player/AI hidden supply;
- no SKU explosion without gameplay value;
- shipyards require real materials/components/modules/work/capabilities.

Detailed plan: `docs/stage18_resources_industry_infrastructure_plan.md`.

## 8. Stage 19 — strategic warfare / coercive diplomacy / advanced combat behavior

**PLANNED after Stage 18 COMPLETE.**

Stage 19 consumes Stage-17 treaties/claims/trust/grievances/dependencies/treasury, Stage-17.5 physical capabilities and Stage-18 real industrial/logistics network. It does not create a second diplomacy model or abstract war rewards.

```text
crisis / war goal
→ mobilization demand and treasury pressure
→ ammunition / reaction mass / repair / replacement demand
→ physical logistics/readiness
→ tactical operations using Stage-17.5 capability APIs
→ real losses/blockade/industrial/territory effects
→ negotiated political/economic outcome
```

Strategic targets become ordinary physical assets: mines, water/propellant sources, depots, precision fabs, ammunition plants, shipyards and routes.

A blockade or destroyed facility changes war capacity through actual inventories/throughput/replacement time, not `-20% production` scripting.

## 9. Stage 20 — physically calibrated world generation / discovery

**PLANNED after Stage 19.**

Stage 20 отвечает на вопрос **«где существует уже определённый мир?»**.

World generation must honor:

- physical scale/distance;
- travel time and propulsion/FTL capability;
- Stage-18 world-object/resource occurrence rules;
- extraction compatibility and finite reserves;
- infrastructure/shipyard requirements;
- logistics/economic geography;
- sensor-consistent discovery;
- Stage-19 strategic response times.

Generator размещает Stage-18 resources/facilities, но не изобретает новые resource types или hidden emergency deposits для спасения плохого seed.

Detailed plan: `docs/stage20_physical_world_generation_plan.md`.

## 10. Stage 21 — RPG / living-world layer

**PLANNED after Stage 20.**

NPCs, missions, discovery and reputation consume authoritative physical/economic/political state instead of disconnected scripted state.

NPC/missions can react to:

- real shortages and trade flows;
- industrial employment/capability;
- exploration/resource discoveries;
- military losses/blockades;
- territory/access/diplomacy;
- actual ship/station/faction state.

Living-world simulation must follow scalability architecture: persistent identity, relevance/cadence/event wakeups, deterministic deadlines and no all-NPC full AI tick.

## 11. Stage 22 — content breadth / technology / balance alpha

**PLANNED after Stage 21.**

Stage 22 expands the accepted language instead of inventing parallel systems.

It adds:

- broader material/technology families where a split is meaningful;
- reactor/drive/thermal/sensor/EW/weapon/shield/protection content;
- hull families and variants;
- faction engineering doctrines;
- expanded shipyard/facility capabilities;
- fleet composition balance;
- world-scale logistics and macroeconomic soak;
- anti-universal-build and anti-linear-tier-obsolescence validation;
- technology/content fingerprint governance.

New equipment follows the physical manufacturable/logistical paradigm introduced by Stage 17.5 and Stage 18, not isolated stat modifiers.

Detailed plan: `docs/stage22_content_balance_plan.md`.

## 12. Stage 23 — polish / release candidate

**PLANNED.** UX/onboarding/accessibility/performance/content validation/save hardening after simulation/content architecture is stable.

Stage 23 does not introduce a new foundational economy/physics layer. It closes release hardening, performance budgets, migration diagnostics and representative long-session validation.

## 13. Cross-stage dependency chain

```text
Stage 17 faction actor
→ Stage 17.5 physical ship/module language
→ Stage 18 physical resource/industry language
→ Stage 19 warfare consuming real industrial/logistics capacity
→ Stage 20 physical placement/economic geography
→ Stage 21 NPC/missions/living-world consequences
→ Stage 22 content breadth/balance/long-run soak
→ Stage 23 polish/RC
```

The ordering is deliberate:

```text
WHAT SHIPS NEED
→ HOW THE ECONOMY PRODUCES IT
→ HOW WAR CONSUMES/DESTROYS IT
→ WHERE IT EXISTS
→ WHO LIVES/ACTS INSIDE IT
→ HOW BROAD/BALANCED THE CONTENT IS
```

## 14. Current immediate sequence

```text
Stage 17H migration/end-to-end acceptance
→ Stage 17 COMPLETE + final transition gate
→ activate 17.5A schema/material/hull/module
→ Stage 17.5 implementation
→ Stage 18 resources/industry/infrastructure
→ Stage 19 warfare
→ Stage 20 world generation
→ Stage 21 living world
→ Stage 22 content/balance
→ Stage 23 RC
```

**Immediate implementation priority remains Stage 17H.** Roadmap renumbering does not permit skipping Stage-17 or Stage-17.5 gates.