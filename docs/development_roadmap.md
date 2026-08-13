# Star Empires — Development Roadmap

> Главный статусный и плановый документ разработки.
>
> Этот файл является единственным источником истины для последовательности core-этапов, Definition of Done, активного milestone и правил перехода между этапами.
>
> Последнее обновление: **2026-08-14**.
>
> Полная предыдущая редакция roadmap до завершения Stage 10 и начала Stage 11 сохранена без изменений в `docs/archive/development_roadmap_pre_stage11_2026-08-13.md`.

---

## 1. Цель проекта

**Star Empires** — 2D top-down космическая sandbox-RPG/strategy с живой экономикой и миром, который существует независимо от игрока.

Целевая петля развития:

```text
пилот одного корабля
        ↓
торговец / шахтёр / наёмник
        ↓
владелец нескольких кораблей
        ↓
компания и автономные флоты
        ↓
собственные станции
        ↓
собственная фракция
        ↓
территория, дипломатия и войны
        ↓
региональная / галактическая держава
```

Главный системный принцип: **игрок и AI по возможности используют одни и те же физические правила мира**. Не создавать отдельную «экономику игрока», passive income, virtual delivery, instant construction или иные обходы simulation core без отдельного design decision.

---

## 2. Зафиксированный production stack

- Java 17;
- libGDX 1.14.2 / LWJGL3;
- Ashley ECS 1.7.4 для local runtime entities;
- VisUI 1.5.9;
- Maven Wrapper;
- JUnit;
- JaCoCo;
- GitHub Actions;
- data-driven JSON content catalog;
- deterministic fixed-tick simulation;
- versioned binary persistence.

**Stage 8.5 technology decision: `KEEP_LIBGDX`.** Повторная миграция рассматривается только при появлении нового измеримого фундаментального ограничения.

---

## 3. Milestones

| Milestone | Цель | Stages | Статус |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | корректный и масштабируемый economic core | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | живые systems, factions, construction, inter-system logistics, autonomous expansion | 7–11 + 8.5 | **ACTIVE** |
| **v0.3 Playable Space Sandbox** | player ship, travel, trade, mining, combat | 12–14 | PLANNED |
| **v0.4 Fleet & Empire Sandbox** | player fleets, stations, faction, strategic war | 15–18 | PLANNED |
| **v0.5 RPG & Living World** | exploration, NPC, missions, reputation | 19–20 | PLANNED |
| **v0.6 Content & Balance Alpha** | content breadth + long-run stability | 21 | PLANNED |
| **v0.7 Polish / Release Candidate** | UX, onboarding, performance, save hardening | 22 | PLANNED |

---

# MILESTONE v0.1 — ECONOMIC SANDBOX

**Статус: COMPLETE.** Организационный branch-protection пункт для `main` остаётся отдельным repository-administration долгом и не отменяет функциональный DoD milestone.

## Stage 0 — Repository health

**COMPLETE — PR #1.** Clean JDK-17 build, strict Javadoc, JUnit, JaCoCo, runnable shaded JAR и CI.

## Stage 1 — SimulationClock и deterministic time

**COMPLETE — PR #2.** Fixed step `0.1s`, pause/time scale, deterministic named RNG streams, explicit simulation-system order, FPS-independent result.

## Stage 2 — Деньги и economic invariants

**COMPLETE — PR #3.** Authoritative `long` milli-credits, finite station liquidity, atomic bilateral trade, `EconomicLedger`, явные source/sink/transform/transfer semantics.

## Stage 3 — EntityId и persistence

**COMPLETE — PR #4.** Stable IDs, versioned `GameState`, bounded codec, safe replacement, continuation tests, headless save/load.

## Stage 4 — Data-driven content

**COMPLETE — PR #5.** JSON `ContentCatalog`, stable content IDs, data-driven items/recipes/factions/ships/stations, validation, content fingerprint and save binding.

## Stage 5 — Logistics / Trade Route Planner

**COMPLETE — PR #6.** Pure bounded planner, `MarketDirectory`, profit/time scoring, stock/demand/capacity/liquidity/specialization, deterministic tie-breaks and stale-route policy.

## Stage 6 — Headless benchmark / observability

**COMPLETE — PR #7 / #8.** 100 stations / 500 agents / 100 simulated hours, accounting checks, machine-readable reports, supply-chain diagnostics, profiling and regression thresholds.

### v0.1 Definition of Done

Экономический core воспроизводим, физически сохраняет деньги/товары, масштабируется без UI и количественно диагностирует supply-chain failures. **Выполнено.**

---

# MILESTONE v0.2 — LIVING GALACTIC ECONOMY

**Статус: ACTIVE.**

## Stage 7 — World hierarchy и simulation levels

**COMPLETE — PR #9.** `Galaxy -> Sector -> StarSystem`, typed stable IDs, canonical jump topology, `WorldState`, active full-rate system, remote strategic updates, bounded scheduler, production demo 2 sectors / 3 systems.

## Stage 8 — Factions как economic actors

**COMPLETE — PR #10.** Treasury, budget policy, subsidies, diplomacy, territory, access, taxes/tariffs, strategic demand and world persistence. Faction decisions move real money/resources.

## Stage 8.5 — Graphics / Technology Validation

**COMPLETE — `KEEP_LIBGDX`.**

Зафиксировано:

- libGDX 1.14.2 / Ashley 1.7.4 / VisUI 1.5.9;
- presentation layer отделён от authoritative simulation;
- production-like heavy-corvette asset pipeline;
- engine OFF/IDLE/THRUST, emissive/additive and damage layers;
- real-GPU representative validation;
- reference RTX 4070 baseline;
- Java-17 CI compatibility;
- decision record `docs/stage8_5_technology_decision.md`.

Stage 8.5 больше не блокирует core development.

## Stage 9 — Dynamic Economy: lifecycle, construction, resilience

**COMPLETE — Stage 9E acceptance passed.**

### 9A — Entity lifecycle

**COMPLETE — PR #13 candidate / `docs/stage9a_entity_lifecycle.md`.** Runtime create/remove, deterministic IDs, index/route invalidation, mutable entity counts and persistence.

### 9B — Persistent Construction Project

**COMPLETE — PR #14 candidate / `docs/stage9b_construction_project.md`.** World-level project ID/state, physical funding, market demand, material delivery, build time, completion via archetype factory, cancellation/refund semantics.

### 9C — Destruction / economic shock

**COMPLETE — PR #15.** Physical asset destruction, explicit cargo/resource fate, ledger sinks/transfers, market/production removal and news hooks.

### 9D — Bottleneck analysis / AI investment

**COMPLETE — PR #17.** Deterministic shortage/logistics analysis, persistent pressure hysteresis, affordability and utility checks, AI construction through the same Stage-9 API.

### 9E — Economic resilience benchmark

**COMPLETE — PR #18 candidate / `docs/stage9e_economic_resilience.md`.** Critical foundry destruction causes shortage; AI detects it, funds/supplies replacement construction and supply chain measurably recovers without scripted respawn.

### Stage 9 Definition of Done

Экономика способна физически деградировать, диагностировать собственный bottleneck, инвестировать и восстановить производственную мощность. **Выполнено.**

---

## Stage 10 — Inter-system Logistics

**Статус: COMPLETE.** Stage 10D/E merged via **PR #23**, main commit `9aeddb8`.

### 10A — World-level Fleet Identity

**COMPLETE — PR #19.** Persistent `FleetId` отделён от system-local `EntityId`; fleet может быть `IN_SYSTEM` или detached/in-transit; mid-transit persistence безопасна.

### 10B — Jump Transit

**COMPLETE — PR #20 / `docs/stage10b_jump_transit.md`.**

Authoritative FSM:

```text
IN_SYSTEM
  ↓
MOVING_TO_JUMP
  ↓
JUMP_PENDING
  ↓
IN_TRANSIT
  ↓
ARRIVING
  ↓
IN_SYSTEM
```

Deterministic timing, topology-edge validation, remote continuation, save/load continuation and active-system independence доказаны тестами.

### 10C — Galactic Route Planner

**COMPLETE — implementation from PR #21 integrated to `main`; `docs/stage10c_galactic_route_planner.md`.**

- deterministic weighted multi-hop `GalacticPathPlanner`;
- system-qualified market identity;
- market-access filtering;
- existing Stage-5 `TradeRoutePlanner` reused rather than duplicated;
- cargo, stock, demand, liquidity, tariffs, route-risk seam and total duration included in scoring;
- world-level canonical planner factories.

### 10D — Cross-system Market Discovery

**COMPLETE — PR #23 / `docs/stage10d_cross_system_market_discovery.md`.**

- [x] bounded reachable-topology discovery;
- [x] no full-galaxy market-pair scan per fleet;
- [x] per-system `MarketDirectory` + sector index;
- [x] deterministic candidate ordering;
- [x] configurable jump/system/consumer/candidate horizon;
- [x] aggregate market revision and stale-result invalidation;
- [x] same-sector preference before wider regional expansion.

### 10E — Physical Inter-system Economic Acceptance

**COMPLETE — PR #23 / `docs/stage10e_inter_system_economic_acceptance.md`.**

Accepted loop:

```text
supplier surplus in system A
→ bounded galactic discovery
→ Stage-10C scoring
→ physical TradeController purchase
→ Stage-10B jump FSM
→ same FleetId / new local EntityId in system B
→ live destination revalidation
→ physical TradeController sale
→ destination stock increases
```

Destination state is revalidated after transit. If demand/capacity/liquidity changed, the largest still-valid positive amount is sold; unsold cargo remains physically aboard for replanning.

Dynamic runtime open/closed jump-edge overlay is **not fabricated** in Stage 10. Canonical topology is currently immutable; blockade/war availability belongs to Stage 18 and must be shared by path planning and jump execution.

### Stage 10 Definition of Done

Resources physically cross StarSystems aboard persistent fleets, using the same trade accounting and deterministic transit used elsewhere. Cross-system opportunities are bounded and profitably scored with access/tariff/time constraints. Full Java-17 tests, coverage, strict Javadoc and desktop packaging are green. **Выполнено.**

---

## Stage 11 — Autonomous Faction Expansion

**Статус: ACTIVE.**

Цель: превратить Stage-8 `EXPANSION` demand modifier в реальное пространственное развитие factions через существующие physical economy, Stage-9 construction и Stage-10 logistics.

### 11A — Expansion Opportunity Model

**COMPLETE — PR #24, main commit `4cbc83e`; `docs/stage11a_expansion_opportunity_model.md`.**

Фракция детерминированно оценивает bounded reachable systems по реально существующим сигналам:

- current territory / nearest controlled source;
- authoritative Stage-10 jump path/time;
- materialized finite `AsteroidComponent.remainingResource`;
- live market unmet demand;
- market-network footprint;
- data-driven anchor construction funding;
- treasury affordability;
- diplomacy-derived hostile pressure;
- explicit penalty for already foreign-controlled target.

`ExpansionOpportunity` объясняет score через отдельные physical metrics. `ExpansionOpportunityPolicy` хранит веса отдельно от decision code. RNG не используется. Read-only analyzer не создаёт проекты, не двигает fleet и не меняет territory.

Acceptance подтверждает bounded frontier, deterministic repeatability, resource-sensitive ranking и budget gate. Full CI green.

### 11B — Persistent Expansion Plan

**Статус: ACTIVE.**

Persistent plan должен определить:

- stable world-level plan ID;
- owner faction;
- source и target system;
- strategic reason;
- выбранный anchor station archetype;
- optional linked `ConstructionProjectId` после начала execution;
- required support fleet requirement / assigned fleet IDs;
- initial stock targets;
- reserved/approved budget semantics;
- status + timestamps;
- deterministic ordering;
- schema ownership, bounded codec, migration and continuation tests.

**11B не должен сам строить станцию или менять territory.** Он фиксирует намерение и состояние, которое Stage 11C затем исполняет через существующие APIs.

### 11C — Physical Expansion Execution

**PLANNED после 11B.**

- [ ] выбрать лучший viable opportunity и создать persistent plan;
- [ ] создать Stage-9 construction project через тот же API;
- [ ] физически профинансировать project;
- [ ] доставить материалы из существующих systems через Stage-10 logistics;
- [ ] station появляется только после real material/time fulfillment;
- [ ] market новой station входит в общую экономику;
- [ ] при необходимости назначить miners/traders/support fleets;
- [ ] expansion может остановиться/провалиться из-за бюджета, access или logistics;
- [ ] territory меняется только по explicit authoritative rule.

### 11D — Competition

**PLANNED.**

- [ ] несколько factions могут выбрать одну target system;
- [ ] persistent plans существуют одновременно;
- [ ] deterministic pre-combat resolution через timing/resources/access;
- [ ] hostile military competition остаётся seam для Stage 18;
- [ ] никакого автоматического «захвата» системы только от score.

### Stage 11 Definition of Done

Хотя бы одна faction самостоятельно выбирает экономически/стратегически оправданную соседнюю систему, сохраняет persistent plan, физически финансирует construction, доставляет реальные материалы через межсистемную логистику и создаёт устойчивый новый economic node без scripted spawn. Territory изменяется только по заранее определённому правилу.

---

# MILESTONE v0.3 — PLAYABLE SPACE SANDBOX

**Статус: PLANNED.** Core Stage 12 не начинается до Stage-11 Definition of Done.

## Stage 12 — Player State, Ownership, Travel

### 12A — PlayerState

Persistent wallet, reputation, faction/independent status, owned ship IDs, active ship ID, discovered state and optional home/start location.

### 12B — Ownership

Ownership не равен faction membership автоматически. Transfer должен быть atomic/persistent; destroyed owned entity корректно обновляет player state.

### 12C — Direct ship control

Выбрать production movement model; camera, selection, docking/undocking, jump travel, pause/time controls; input не обходит fixed simulation.

### 12D — Player market interaction

Manual buy/sell, cargo/wallet UI, market stock/price visibility and access/reputation gates через тот же authoritative trade core, что AI.

**DoD:** игрок на owned ship перелетает между минимум двумя systems, покупает, физически перевозит и продаёт cargo.

## Stage 13 — Combat Vertical Slice

Минимум: target acquisition, positioning, range, data-driven weapon, cooldown/fire, shields/hull, deterministic damage/destruction, combat AI, salvage/resource sink seam and economic aftermath.

Player и AI используют один authoritative damage/destruction pipeline. Ship loss должен менять faction assets and replacement demand.

## Stage 14 — First Complete Player Economic Loop

```text
explore
→ find opportunity
→ trade / mine / fight
→ earn credits
→ upgrade or buy ship
→ take larger opportunities
```

Player mining использует finite resources; ship purchase uses real ownership/wallet transfer; basic role differentiation covers cargo/movement/combat/mining. Добавить playtest telemetry: credits/hour, profit/hour, downtime, cargo utilization, losses.

**DoD:** первый внутренний playable hour без debug grants.

---

# MILESTONE v0.4 — FLEET & EMPIRE SANDBOX

## Stage 15 — Player Fleets / Autonomous Orders

Persistent fleet grouping and orders: HOLD, MOVE, TRADE, MINE, ESCORT, PATROL, FOLLOW. Player-owned autonomous traders/miners reuse existing planners, not passive income formulas. Fleet management UI exposes location/order/cargo/profit.

## Stage 16 — Player Construction / Station Ownership

Player uses the same Stage-9 `ConstructionProject`. UI/API chooses archetype/system/location/funding; materials may arrive via market, manual delivery or player traders. Owned station exposes wallet, market/production policy and P/L.

## Stage 17 — Player Faction

Data-driven founding requirements, then reuse Stage-8 treasury/territory/relations/access/taxes/subsidies/strategic policy. Player faction must live in the same persistence and strategic systems as AI factions.

## Stage 18 — Strategic Warfare / Territory / Politics

- formal hostility/war/peace state;
- defend/attack/escort/blockade/capture objectives;
- physical military logistics and replacement demand;
- runtime jump-route availability/risk overlay shared by planner + executor;
- blockade throughput/shortage/price/response benchmark;
- explicit territory transition rule; never flip control from one arbitrary object loss.

**DoD:** war changes physical assets, routes, supply chains and territory; factions respond economically and strategically.

---

# MILESTONE v0.5 — RPG & LIVING WORLD

## Stage 19 — Exploration / Discovery / World Generation

Persistent discovered systems/routes/stations/resources; deterministic seed-driven galaxy generation; faction starts and economy bootstrap; data-driven anomaly/derelict/special-location seams.

## Stage 20 — NPC / Missions / Reputation / Progression

Persistent NPC only where individual identity matters. Mission framework is built on real world state: haul, deliver, mine, escort, bounty, investigate, defend. Dynamic contracts should arise from shortage/loss/expansion/war/discovery. Reputation connects to access, contracts and faction relations.

---

# MILESTONE v0.6 — CONTENT & BALANCE ALPHA

## Stage 21 — Content Breadth / Balance / Long-run Stability

Expand resources/intermediates/components/civilian/military goods only after their mechanics are stable. Build coherent ship and station ecosystems and faction differentiation through real starting conditions/policies/doctrine rather than hidden resource bonuses.

Full-world benchmark must include inter-system trade, construction, faction expansion, combat losses, player assets and long duration. Detect runaway inflation/deflation, dead economies, permanent shortages, uncontrolled entity/ledger growth, planner scaling problems and faction snowball.

---

# MILESTONE v0.7 — POLISH / RELEASE CANDIDATE

## Stage 22 — UX / Onboarding / Performance / Release Hardening

- unified HUD/map/fleet/station/faction/market UX;
- onboarding through first trade/mining/combat/autonomous ship/station;
- explicit save migration/support window, backup/autosave/corrupt-save behavior;
- performance profiles for large combat, many remote systems, route planning, large asset lists and save size/time;
- persistent `BloomMode = OFF / LIGHT / FULL` with benchmarked release thresholds;
- clean CI, full regression, benchmark/soak/save-load gates and no known critical conservation/determinism bugs.

**DoD:** external player can reach own fleet/station/faction without developer instructions or debug tools.

---

# 4. Parallel Visual / UX Track

Visual work runs parallel but never substitutes simulation DoD.

## V1 — Ship sprite pipeline

Top-down grounded near-future language, size grammar, role-readable silhouettes, engine/weapon/damage hardpoints, scale/pivot/collision conventions and data-driven asset identity.

## V2 — Engine / movement animation

Idle/thrust/maneuver/reverse-lateral seams driven by real movement state; animation RNG never mutates gameplay determinism.

## V3 — Station visual language

Construction site, industrial, mining, trade, military, colony and faction differentiation. Prefer rendering actual persistent construction state.

## V4 — Combat VFX / post-processing

Projectile/beam/hit/shield/destruction/debris. Implement and benchmark `BloomMode OFF/LIGHT/FULL`; gameplay readability cannot depend on FULL.

## V5 — Strategic map / empire UI

Systems, routes, territory, fleet orders, trade flows, shortages and wars.

---

# 5. Cross-cutting Engineering Rules

## Persistence

Every persistent domain object defines stable ID, schema ownership, codec, migration policy, bounds/validation and continuation tests.

## Determinism

Every planner/AI has deterministic iteration order and explicit tie-break. RNG is named and used only when stochastic behavior is genuinely part of design.

## Economic conservation

Every money/resource mutation has transfer/source/sink/transform semantics, ledger representation and invariant tests. No hidden passive creation.

## Observability

Major systems expose measurable metrics: rendering, construction, investment, inter-system throughput, expansion, fleet losses, combat, recovery, territory and player economy.

## Performance

Optimize from benchmark/profile evidence, except when an algorithm is structurally unbounded by design.

---

# 6. Roadmap Execution Rules

1. `main` remains stable.
2. New core branch starts from current green `main`.
3. Broken CI blocks merge and next core substage.
4. Next core substage becomes ACTIVE only after previous DoD.
5. Large stages split into reviewable PRs.
6. Do not add content merely for quantity before the mechanic is stable.
7. New economic mechanics require invariant tests.
8. Persistent state requires migration + save/load continuation tests.
9. Deterministic planners require explicit tie-break tests.
10. README describes only stable `main` behavior.
11. Architectural decisions precede mass content work.
12. Player and AI reuse common simulation APIs unless a separate path is explicitly justified.
13. No passive income, virtual delivery or instant construction without design decision.
14. Remote simulation may reduce fidelity but cannot become a second incompatible economy.
15. After each completed stage/substage update this roadmap with verification evidence, merge reference and next ACTIVE item.
16. Every milestone requires at least one end-to-end acceptance scenario.
17. Visual work may run in parallel but does not satisfy core simulation DoD unless explicitly specified.
18. Prove a minimal working loop before expanding complexity/content.

---

# 7. Milestone Acceptance Scenarios

## v0.2 Living Galactic Economy

```text
critical producer destroyed
→ shortage
→ faction detects bottleneck
→ physical replacement construction
→ cross-system logistics restores supply
→ faction evaluates expansion opportunity
→ persistent expansion plan
→ materials travel to target system
→ new station/economic node online
→ stable autonomous expansion
```

## v0.3 Playable Space Sandbox

```text
player starts with one ship
→ travels
→ trades/mines
→ fights
→ earns credits
→ buys a better ship
```

## v0.4 Fleet & Empire Sandbox

```text
multiple owned ships
→ delegated economy
→ owned station
→ player faction
→ strategic conflict
→ territory + supply chains change
```

## v0.5 RPG & Living World

```text
real shortage/war/discovery
→ dynamic contract
→ player completes through simulation
→ reputation/world state changes
```

---

# 8. Current Next Step

**ACTIVE: Stage 11B — Persistent Expansion Plan.**

Immediate implementation sequence:

1. define stable `ExpansionPlanId`, reason/status model and canonical immutable plan state;
2. define owner/source/target/anchor/budget/support/initial-stock invariants;
3. add world-level allocator and plan store;
4. bump `WorldState` schema and add bounded binary codec;
5. migrate all Stage-7..10 saves to empty expansion-plan state;
6. expose deterministic plan creation from a selected Stage-11A opportunity without spending money or spawning assets;
7. add save/load round-trip + continuation + invalid-input tests;
8. update this roadmap and mark 11B COMPLETE only after green full Java-17 CI;
9. then activate Stage 11C physical execution through Stage-9 construction and Stage-10 logistics.

Stage 12 / playable player state remains blocked until full Stage 11 DoD.
