# Star Empires — канонический roadmap разработки

> **Последняя синхронизация: 2026-08-16.**
>
> Этот файл — текущий authoritative status/dependency roadmap. Предыдущая подробная версия до синхронизации Stage 17G сохранена без изменений в `docs/archive/development_roadmap_pre_stage17g_2026-08-16.md`.
>
> Язык новой и содержательно изменяемой документации начиная со Stage 16 — русский; Java/API/content ID/formulas сохраняются в оригинальном виде.

## 1. Главный инвариант проекта

**Star Empires** — 2D top-down space sandbox/RPG/strategy с живой физической экономикой и миром, существующим независимо от игрока.

Целевая прогрессия:

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

Без отдельного architecture decision запрещены:

- отдельная player-only economy;
- player-only combat/movement formulas;
- passive income вместо реальных transfers/production/logistics;
- virtual deliveries и hidden resource grants;
- scripted replacement уничтоженных активов;
- мгновенное ordinary travel/construction/refit;
- class-name performance bonuses, не выводимые из physical fit;
- отдельные magical authoritative `armorPoints`, `sensorPoints`, `stealthRating` вне принятой physical model;
- UI, напрямую мутирующий authoritative simulation state.

## 2. Milestones

| Milestone | Цель | Stages | Статус |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | deterministic economic core | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | multi-system factions/logistics/construction/expansion | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | player ship/travel/trade/mining/combat/progression | 12–14 | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | fleets/stations/player faction/combat depth/warfare | 15–18 + 17.5 | **ACTIVE — Stage 17G closeout** |
| **v0.5 RPG & Living World** | calibrated world generation/discovery/NPC/missions/reputation | 19–20 | PLANNED |
| **v0.6 Content & Balance Alpha** | technology/content breadth + long-horizon balance | 21 | PLANNED |
| **v0.7 Polish / RC** | UX/onboarding/performance/save hardening | 22 | PLANNED |

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

**COMPLETE.** Java-17 CI/JUnit/JaCoCo/Javadoc, deterministic fixed time, integer milli-credits, conserved ledger semantics, stable identity/persistence, data-driven content, local logistics and headless observability are established.

### Stages 7–11 — v0.2 Living Galactic Economy

**COMPLETE.** Galaxy/Sector/StarSystem hierarchy, ordinary faction treasury/economy, dynamic economy, physical construction/destruction, inter-system logistics and autonomous physical expansion are established. Stage 8.5 decision remains **KEEP_LIBGDX**.

### Stages 12–14 — v0.3 Playable Space Sandbox

**COMPLETE.** Player state/ownership, physical travel/docking/manual trade, shared combat vertical slice, mining, ship progression, UI and deterministic long-horizon economic acceptance are established.

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

**ACTIVE. 17A–17F.7 COMPLETE. 17G implementation/acceptance выполняется в PR #133. 17H остаётся финальным Stage-17 transition gate.**

Цель Stage 17: независимый игрок с существующими Stage-15/16 assets становится обычным faction actor без замены `FleetId`/`EntityId`, без бесплатной территории/капитала и без player-only diplomacy/economy.

Общая причинная модель:

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
- explicit founding без grant денег/земли/assets;
- same physical player assets получают faction affiliation без replacement;
- personal/company wallet и public treasury остаются раздельными, transfers conserved;
- claims/presence/stabilization/control/recognition/concessions являются distinct legal states;
- common treaty/embargo lifecycle для player/AI;
- market access определяется legal resolver, а не UI shortcut;
- customs/treaty consequences проявляются через ordinary transactions;
- structural dependency измеряется из physical supply/access state.

### 17F — faction policies / strategic economy

**COMPLETE — 17F.1–17F.7.**

- **17F.1 doctrine — COMPLETE:** persistent bounded institutional profile, shared decision weights, no performance bonuses.
- **17F.2 fiscal policy — COMPLETE:** tax/territorial tariff/reserve/liquidity/construction budget authoring over conserved flows.
- **17F.3 fiscal trade-offs — COMPLETE:** treasury/station/construction diagnostics and real wallet trade-offs.
- **17F.4 stock/production policy — COMPLETE:** persistent stock floors/production preferences, explicit ordinary materialization only.
- **17F.5 resilience policy — COMPLETE:** buffers, supplier diversification, local production, capacity-gap construction, redundant routes and critical-import limits through ordinary physical systems.
- **17F.6 policy feedback / anti-oscillation — COMPLETE:** shared persistent review cadence, bounded fiscal/resilience adjustments, reversible overlays, no every-tick recipe/tax/tariff oscillation. Canonical record: `docs/stage17f6_policy_feedback_completion_record.md`.
- **17F.7 player/AI command parity — COMPLETE:** `FactionPolicyCommand` + `FactionPolicyCommandExecutor`; player adapter submits the same doctrine/fiscal/stock-production/apply commands as common AI/world callers.

17F hard rule:

```text
policy authoring
≠ money/cargo/output creation
```

Policy first changes persistent strategy/authorization; physical consequences are realized only by ordinary market/logistics/production/treasury/construction systems.

### 17G — faction management / strategic global-map authority

**IN PROGRESS — implementation and acceptance are on PR #133; status becomes COMPLETE only after exact-head merge gate is green.**

17G is an application/read-model layer over Stage-17 authoritative state, not a new simulation subsystem.

#### 17G.1 — immutable management projection

Implemented contract:

- explicit independent vs affiliated shape;
- personal wallet + faction economy/treasury;
- doctrine/fiscal/base stock-production/resilience overlay;
- player-owned physical fleets/projects/stations;
- territory views only for player-known systems;
- own persistent diplomacy + deterministic counterparty legal/access summaries;
- persistent strategic growth/expansion plans;
- deterministic ordering;
- repeated capture cannot advance time or mutate world/player state.

Primary API:

- `FactionManagementSnapshot`;
- `FactionManagementModel`.

#### 17G.2 — player faction management command facade

Implemented contract:

- `PlayerFactionManagementService` never mutates state directly;
- treasury capitalization/withdrawal delegates to conserved treasury runtime service;
- asset affiliation delegates to id-preserving affiliation service;
- doctrine/fiscal/stock-production/apply delegates to common `FactionPolicyCommandExecutor` path;
- treaty and embargo delegate to common player/AI diplomacy commands;
- territorial claim/withdraw/relinquish/recognition/construction-right operations delegate to ordinary territorial-law boundaries;
- facade rejects independent authority and actor impersonation.

Tariff scope is deliberately split by the existing domain model:

- own-station tax + foreign-territory levy are authored through `FactionFiscalPolicyState` / common policy command;
- ordinary customs tariff/treaty exemption remain authoritative diplomacy/transaction state and are projected read-only unless an existing common diplomacy command changes the legal instrument;
- UI does not receive a private tariff setter.

#### 17G.3 — strategic global-map composition

Implemented:

- `FactionGlobalMapSnapshot`;
- `FactionGlobalMapModel`.

The existing `GlobalFleetMapRenderer` stays presentation-only and continues to consume prepared visible state rather than `WorldSimulation`. Faction management is composed beside the non-omniscient global-map snapshot; no mutation callbacks are embedded in read models.

#### 17G.4 — acceptance / closeout

Required and implemented acceptance coverage on the branch:

1. independent player gets no hidden faction authority but keeps owned Stage-16 assets;
2. repeated read-model capture is deterministic and mutation-free;
3. affiliated projection equals authoritative economy/policy/diplomacy/territory/growth state;
4. known-system territory is assessed through ordinary territorial law;
5. asset affiliation preserves persistent fleet placement/IDs;
6. capitalization conserves personal + treasury money exactly;
7. fiscal/tariff policy uses the common policy command;
8. treaty/embargo uses the common diplomacy boundary and facade blocks impersonation;
9. claim does not grant instant sovereignty;
10. management actions create/delete neither cargo nor total money;
11. save/load preserves economy, policy, diplomacy, territory and owned-fleet projection.

Acceptance tests:

- `Stage17G1FactionManagementReadModelAcceptanceTest`;
- `Stage17G2FactionManagementCommandsAcceptanceTest`.

### 17H — persistence / migration / Stage-17 end-to-end gate

**NEXT after 17G COMPLETE.**

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

17H must additionally audit current persistence versions/migrations and pre-Stage17 save compatibility. A read-model round-trip in 17G does not substitute the full transition/migration gate.

## 5. Stage 17 → 17.5 transition gate

**Stage 17.5 is BLOCKED until Stage 17H and the final Stage-17 post-merge CI are green.**

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

Hard invariants:

- no player-only combat physics;
- no class-name bonuses;
- no free ammunition/reaction mass;
- no global HP-only survivability model as final architecture;
- every module participates in common mass/volume/power/heat/economy budgets;
- fitting/consumables/damage/thermal/shield/FTL state is persistent and deterministic.

Detailed plan: `docs/stage17_5_combat_depth_implementation_plan.md`.

## 7. Stage 18 — strategic warfare / coercive diplomacy / advanced combat behavior

**PLANNED after 17.5 COMPLETE.**

Stage 18 uses Stage-17 treaties/claims/trust/grievances/dependencies/treasury plus Stage-17.5 physical capabilities. It must not reintroduce abstract war rewards or a second diplomacy model.

Core sequence:

```text
crisis / war goal
→ mobilization demand and treasury pressure
→ physical logistics/readiness
→ tactical operations using Stage-17.5 capability APIs
→ real losses/blockade/territory effects
→ negotiated political/economic outcome
```

## 8. Stage 19 — physically calibrated world generation

**PLANNED after empire/combat foundations.**

World generation must honor physical scale, distance, travel time, propulsion/FTL capability, resource distribution, infrastructure and economic geography. Generated topology cannot be balanced only by arbitrary graph distance when the runtime has physical speed/range/energy constraints.

Detailed plan: `docs/stage19_physical_world_generation_plan.md`.

## 9. Stage 20 — RPG / living-world layer

**PLANNED.** NPCs, missions, discovery and reputation must consume authoritative world/economic/political state instead of spawning disconnected scripted content.

## 10. Stage 21 — content breadth / technology / balance alpha

**PLANNED.**

Stage 21 builds on Ship Mathematics and the same physical item/module paradigm introduced in 17.5. New equipment must be real manufacturable/logistical content, not isolated stat modifiers. World-generation and balance benchmarks must respect physical scale/distance/speed and long-horizon economic consequences.

Detailed plan: `docs/stage21_content_balance_plan.md`.

## 11. Stage 22 — polish / release candidate

**PLANNED.** UX/onboarding/accessibility/performance/content validation/save hardening after simulation/content architecture is stable.

## 12. Current immediate sequence

```text
17G exact-head aggregate CI + roadmap closeout
→ merge PR #133 + exact post-merge main CI
→ Stage 17H migration/end-to-end acceptance
→ Stage 17 COMPLETE + transition gate
→ activate 17.5A
```

Do **not** skip directly to Stage 17.5 or Stage 18 while 17H is outstanding.
