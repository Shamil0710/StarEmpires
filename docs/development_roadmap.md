# Star Empires — Development Roadmap

> Главный статусный и плановый документ разработки.
>
> Этот файл является единственным источником истины для последовательности core-этапов, Definition of Done, активного milestone и правил перехода между этапами.
>
> Последнее обновление: **2026-08-14**.
>
> Подробная предыдущая редакция roadmap до Stage 11 сохранена в `docs/archive/development_roadmap_pre_stage11_2026-08-13.md`. Технический completion record Stage 11: `docs/stage11_autonomous_faction_expansion.md`.

---

## 1. Цель проекта

**Star Empires** — 2D top-down космическая sandbox-RPG/strategy с живой экономикой и миром, который продолжает существовать независимо от игрока.

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

Главный системный принцип: **игрок и AI по возможности используют одни и те же физические правила мира**. Не создавать отдельную «экономику игрока», passive income, virtual delivery, instant construction, scripted respawn или иные обходы simulation core без отдельного design decision.

---

## 2. Production stack

- Java 17;
- libGDX 1.14.2 / LWJGL3;
- Ashley ECS 1.7.4;
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
| **v0.2 Living Galactic Economy** | несколько живых systems, factions, construction, inter-system logistics, autonomous expansion | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | player ship, travel, trade, mining, combat | 12–14 | **ACTIVE** |
| **v0.4 Fleet & Empire Sandbox** | player fleets, stations, faction, strategic war | 15–18 | PLANNED |
| **v0.5 RPG & Living World** | exploration, NPC, missions, reputation | 19–20 | PLANNED |
| **v0.6 Content & Balance Alpha** | content breadth + long-run stability | 21 | PLANNED |
| **v0.7 Polish / Release Candidate** | UX, onboarding, performance, save hardening | 22 | PLANNED |

Организационный долг: обязательная branch protection для `main` всё ещё должна быть настроена средствами repository administration. Функциональные CI gates при этом действуют и использовались для каждого завершённого stage.

---

# MILESTONE v0.1 — ECONOMIC SANDBOX

**Статус: COMPLETE.**

## Stage 0 — Repository health

**COMPLETE — PR #1.** Clean JDK-17 build, JUnit, JaCoCo, strict Javadoc, runnable shaded JAR, GitHub Actions.

## Stage 1 — Deterministic time

**COMPLETE — PR #2.** Fixed step `0.1s`, pause/time scale, named deterministic RNG streams, explicit system ordering, FPS-independent simulation result.

## Stage 2 — Money / economic invariants

**COMPLETE — PR #3.** `long` milli-credits, finite liquidity, atomic bilateral trade, `EconomicLedger`, explicit source/sink/transform/transfer semantics.

## Stage 3 — EntityId / persistence

**COMPLETE — PR #4.** Stable IDs, versioned state, bounded codecs, safe file replacement, continuation tests.

## Stage 4 — Data-driven content

**COMPLETE — PR #5.** Versioned JSON catalog, stable content IDs, items/recipes/factions/ships/stations, validation, fingerprint and save binding.

## Stage 5 — Local logistics / route planning

**COMPLETE — PR #6.** Pure bounded `TradeRoutePlanner`, immutable `MarketDirectory`, profit/time scoring, deterministic tie-breaks and stale-route policy.

## Stage 6 — Headless scalability / observability

**COMPLETE — PR #7 / #8.** 100 stations / 500 economic agents / 100 simulated hours, accounting diagnostics, supply-chain failure detection, profiling and machine-readable reports.

### v0.1 DoD

Economic core детерминирован, физически сохраняет деньги/товары, масштабируется без UI и количественно диагностирует деградацию supply chain. **Выполнено.**

---

# MILESTONE v0.2 — LIVING GALACTIC ECONOMY

**Статус: COMPLETE.** Финальный Stage-11 merge: `f5b58c7` (PR #27).

## Stage 7 — World hierarchy / simulation levels

**COMPLETE — PR #9.** `Galaxy -> Sector -> StarSystem`, stable typed IDs, jump topology, `WorldState`, active full-rate system, remote strategic updates and bounded scheduler.

## Stage 8 — Factions as economic actors

**COMPLETE — PR #10.** Treasury, budgets, subsidies, diplomacy, territory, access, taxes/tariffs, strategic demand and persistence. Faction policy moves real money/resources.

## Stage 8.5 — Graphics / Technology Validation

**COMPLETE — `KEEP_LIBGDX`.** Production-like sprite/VFX pipeline, presentation/simulation separation, real-GPU validation and Java-17 CI established. Decision record: `docs/stage8_5_technology_decision.md`.

## Stage 9 — Dynamic Economy

**COMPLETE — Stage 9E acceptance passed.**

- **9A Lifecycle:** deterministic runtime create/remove and persistence.
- **9B Construction:** persistent Stage-9 projects with real funding, material demand/delivery and build time.
- **9C Destruction:** physical asset removal, explicit cargo/resource fate, ledger accounting and economic shock.
- **9D Bottleneck response:** deterministic shortage analysis, pressure hysteresis and AI investment.
- **9E Resilience:** destroyed critical producer -> shortage -> AI investment -> physical replacement -> recovery.

### Stage 9 DoD

Economy can physically degrade, diagnose its bottleneck, invest in replacement capacity and recover without scripted respawn. **Выполнено.**

## Stage 10 — Inter-system Logistics

**COMPLETE — PR #23, main `9aeddb8`.**

### 10A — Fleet identity

**COMPLETE — PR #19.** Persistent world-level `FleetId`; system-local `EntityId` may change across transit; fleets cannot exist in two systems simultaneously.

### 10B — Jump transit

**COMPLETE — PR #20.** Authoritative FSM:

```text
IN_SYSTEM
→ MOVING_TO_JUMP
→ JUMP_PENDING
→ IN_TRANSIT
→ ARRIVING
→ IN_SYSTEM
```

Deterministic timing, topology validation, remote continuation and mid-transit save/load are covered.

### 10C — Galactic route planner

**COMPLETE — PR #21 implementation integrated to main.** Weighted multi-hop paths, system-qualified markets, access, tariffs, cargo, duration, risk seam and shared Stage-5 scoring.

### 10D — Cross-system market discovery

**COMPLETE — PR #23.** Bounded topology/sector search, deterministic candidate order, configurable horizons and aggregate revision invalidation; no full-galaxy market-pair scan per fleet.

### 10E — Physical inter-system acceptance

**COMPLETE — PR #23.** Real supplier purchase -> persistent fleet transit -> live destination revalidation -> physical sale. Unsold cargo remains aboard if destination conditions changed.

### Stage 10 DoD

Resources physically cross StarSystems aboard persistent fleets and cross-system routes are discovered/scored with time, access and tariff constraints. **Выполнено.**

## Stage 11 — Autonomous Faction Expansion

**COMPLETE.** Technical record: `docs/stage11_autonomous_faction_expansion.md`.

### 11A — Expansion Opportunity Model

**COMPLETE — PR #24, main `4cbc83e`.** Bounded deterministic ranking from real territory, jump time, materialized finite resources, market demand/network, construction cost, treasury affordability and diplomacy pressure. No RNG and no world mutation.

### 11B — Persistent Expansion Plan

**COMPLETE — PR #25, main `cea8562`.**

- persistent `StrategicGrowthState.Plan` attached to existing `EXPANSION` goal;
- stable composite PlanId;
- source/target, reason, anchor archetype/project, support fleets, stock targets, budget, lifecycle/timestamps;
- bounded world file-format v2 trailer;
- file-format v1 migration with no fabricated plans;
- round-trip and continuation coverage.

Lifecycle:

```text
PLANNED
→ APPROVED
→ EXECUTING
→ ESTABLISHED
  ↘ CANCELLED / FAILED
```

### 11C — Physical Expansion Execution

**COMPLETE — PR #26, main `ff1a4bc`.**

Accepted physical loop:

```text
best unclaimed opportunity
→ persistent plan
→ assign real faction cargo FleetId
→ create ordinary Stage-9 construction project
→ treasury funds project
→ support fleet buys real steel/energy from markets
→ supplier receives real credits
→ fleet physically jumps between systems
→ cargo delivered into project
→ repeat trips if required
→ materials fulfilled
→ real build time
→ station COMPLETED
→ territory claim
```

Acceptance includes save/load during jump transit and confirms the same persistent FleetId survives system-local EntityId replacement.

Foreign-controlled targets are never auto-conquered by Stage 11.

### 11D — Competition

**COMPLETE — PR #27, main `f5b58c7`.**

- multiple persistent growth plans may target the same unclaimed system;
- only physically completed anchor projects are eligible to claim;
- lower `ConstructionProjectState.completedTick` wins;
- equal completion ticks use stable `PlanId` tie-break;
- `FactionExpansionCompetitionCoordinator` advances the completed winner before rivals;
- losers then observe foreign control and fail through normal Stage-11C rules;
- existing foreign territory cannot be automatically conquered; military competition remains Stage 18.

Final PR #27 Java-17 CI run #838 passed tests, coverage, strict Javadoc and desktop packaging.

### Stage 11 DoD

A faction independently selects an economically/strategically justified unclaimed neighboring system, persists its intent, allocates real budget and a real fleet, purchases and transports physical resources, completes an ordinary construction project and creates a stable territorial/economic node. Competition resolves deterministically from physical completion timing without scripted spawn or combat bypass. **Выполнено.**

### v0.2 End-to-end result

```text
living multi-system economy
→ physical destruction and shortage
→ AI investment/recovery
→ inter-system resource movement
→ faction evaluates frontier
→ persistent expansion plan
→ fleet supplies real construction
→ new station/economic node
→ deterministic territorial growth
```

**v0.2 Living Galactic Economy завершён.**

---

# MILESTONE v0.3 — PLAYABLE SPACE SANDBOX

**Статус: ACTIVE.**

## Stage 12 — Player State, Ownership, Travel

**Статус: ACTIVE — следующий core stage.**

### 12A — PlayerState

Добавить persistent player state минимум:

- wallet;
- reputation;
- faction affiliation или independent state;
- owned `FleetId` / ship IDs;
- active ship;
- discovered systems/objects;
- optional home/start system.

Player state должен иметь stable schema owner, codec/migration и continuation tests.

### 12B — Ownership

- entity ownership не равен faction membership автоматически;
- player-owned ship может иметь faction/legal context;
- ownership transfer atomic/persistent;
- destruction корректно обновляет player state;
- purchase/sale ownership не создаёт деньги или entity duplicates.

### 12C — Direct ship control / travel

Выбрать один production control model и доказать минимальную loop:

- direct movement/control;
- camera follow/selection;
- docking/undocking;
- jump travel через существующий Stage-10 pipeline;
- pause/time controls;
- UI/input не обходят fixed simulation.

### 12D — Player market interaction

- manual buy/sell;
- cargo and wallet UI;
- stock/price visibility;
- access/reputation constraints;
- тот же authoritative `TradeController`, что и AI.

### Stage 12 DoD

Игрок владеет одним ship/fleet, перелетает минимум между двумя системами, стыкуется, вручную покупает товар, физически перевозит его и продаёт через тот же economic core, что AI.

## Stage 13 — Combat Vertical Slice

**PLANNED.** Минимум: target acquisition, positioning, range, data-driven weapon, cooldown/fire state, shields/hull, deterministic damage/destruction, combat AI, salvage/resource-sink seam and economic aftermath.

Player и AI используют один authoritative damage/destruction pipeline. Ship loss должен менять faction assets and replacement demand.

## Stage 14 — First Complete Player Economic Loop

**PLANNED.**

```text
explore
→ find opportunity
→ trade / mine / fight
→ earn credits
→ upgrade or buy ship
→ take larger opportunities
```

Player mining uses finite resources; ship purchase uses real wallet/ownership transfer. Add playtest telemetry: credits/hour, profit/hour, travel downtime, cargo utilization and losses.

### v0.3 DoD

Игрок получает первый внутренний playable hour без debug grants: travel + economy + mining/combat + progression to a better ship.

---

# MILESTONE v0.4 — FLEET & EMPIRE SANDBOX

**Статус: PLANNED.**

## Stage 15 — Player Fleets / Autonomous Orders

Persistent grouping and orders: HOLD, MOVE, TRADE, MINE, ESCORT, PATROL, FOLLOW. Player-owned autonomous economic fleets reuse existing planners rather than passive-income formulas.

## Stage 16 — Player Construction / Station Ownership

Player uses the same Stage-9 `ConstructionProject`: chooses archetype/system/location/funding, may deliver materials manually or through owned traders, and receives a real operating station.

## Stage 17 — Player Faction

Data-driven founding requirements followed by reuse of Stage-8 treasury, territory, relations, access, taxes, subsidies and strategic policies.

## Stage 18 — Strategic Warfare / Territory / Politics

- formal hostility/war/peace;
- defend/attack/escort/blockade/capture objectives;
- military construction/replacement logistics;
- runtime jump-route availability/risk overlay shared by planner + executor;
- blockade -> throughput loss -> shortage/price/economic response benchmark;
- explicit territory transition rule;
- Stage-11 economic outposts/competition integrate with military conquest here.

### v0.4 DoD

Player can grow from one ship into autonomous fleets/stations/faction and wage wars whose results change physical assets, trade routes, supply chains and territory.

---

# MILESTONE v0.5 — RPG & LIVING WORLD

**Статус: PLANNED.**

## Stage 19 — Exploration / Discovery / World Generation

Persistent discovered systems/routes/stations/resources; deterministic seed-driven galaxy generation; faction starts/economy bootstrap; data-driven anomalies, derelicts and special locations.

## Stage 20 — NPC / Missions / Reputation / Progression

Persistent NPC only where identity matters. Missions arise from real world state: haul, deliver, mine, escort, bounty, investigate, defend. Shortage/loss/expansion/war/discovery should create dynamic contracts. Reputation connects to access, contracts and faction relations.

---

# MILESTONE v0.6 — CONTENT & BALANCE ALPHA

**Статус: PLANNED.**

## Stage 21 — Content Breadth / Balance / Long-run Stability

Expand resources/intermediates/components/civilian/military goods only after corresponding mechanics are stable. Build coherent ship/station ecosystems and faction differentiation through real starting conditions, policies and doctrine rather than hidden resource bonuses.

Full-world benchmarks must include inter-system trade, construction, faction expansion, combat losses, player-owned assets and long duration. Detect inflation/deflation, dead economies, permanent shortages, uncontrolled entity/ledger growth, route-planner scaling and faction snowball.

---

# MILESTONE v0.7 — POLISH / RELEASE CANDIDATE

**Статус: PLANNED.**

## Stage 22 — UX / Onboarding / Performance / Release Hardening

- unified HUD and management UI;
- map layers, filters/search and notifications;
- onboarding for first trade/mining/combat/fleet/station;
- autosave/backup/corrupt-save UX and supported migration window;
- performance profiling of large combat, many remote systems, route planning, large asset lists and save/load;
- `BloomMode = OFF / LIGHT / FULL` production settings and baselines;
- clean build, regression, soak and save/load soak release gates.

---

# 4. Parallel Visual / UX Track

Visual work may proceed parallel to core stages but never substitutes their functional DoD.

- **V1 Ship sprite pipeline:** grounded top-down visual language, size grammar, hardpoints, pivots/collision conventions.
- **V2 Engine/movement animation:** idle/thrust/maneuver states driven by real movement state.
- **V3 Station language:** construction, industrial, mining, trade, military, colony and faction differentiation.
- **V4 Combat VFX:** weapons, shield/hit/destruction/salvage and benchmarked `BloomMode` tiers alongside Stage 13.
- **V5 Strategic map / empire UI:** territory, fleets, trade flows, shortages and wars alongside Stage 15–18.

Authoritative gameplay never depends on a particular PNG/sprite asset. Presentation metadata remains data-driven over simulation archetypes.

---

# 5. Cross-cutting engineering rules

## Persistence

Every persistent domain object defines stable identity, schema/file-format ownership, bounded codec, migration policy and continuation tests.

## Determinism

Every planner/AI uses deterministic iteration/tie-breaks; RNG is named and used only when randomness is an explicit design requirement.

## Economic conservation

Every money/resource mutation uses transfer/source/sink/transform semantics and has ledger/invariant coverage. No hidden income or resource creation.

## Physicality

Construction, trade, expansion and future warfare must use real entities, cargo, wallets, travel and build time. Remote simulation may reduce fidelity but may not invent an incompatible economy.

## Observability / performance

Major systems require measurable metrics and benchmarks. Optimize from evidence or when algorithmic scaling is structurally unacceptable; avoid speculative micro-optimization.

---

# 6. Stage transition rules

1. `main` remains stable.
2. New core work starts from current green `main`.
3. Broken CI blocks merge and stage transition.
4. Each stage has an explicit minimal vertical slice and DoD.
5. Persistent changes require migration/continuation coverage.
6. Economic changes require conservation/invariant coverage.
7. Deterministic decision code requires tie-break coverage.
8. Player and AI reuse common simulation APIs unless a separate path is explicitly justified.
9. Do not expand content breadth before mechanics stabilize.
10. Update this roadmap only after factual completion/merge evidence exists.

---

# 7. Current next step

**ACTIVE: Stage 12 — Player State, Ownership, Travel.**

Immediate implementation order:

1. short Stage-12 design/audit pass: authoritative player identity and ownership boundaries;
2. Stage 12A persistent `PlayerState` + schema/codec/migration/continuation;
3. Stage 12B ownership model over existing stable world/fleet IDs;
4. Stage 12C one directly controllable owned ship with docking and Stage-10 jump travel;
5. Stage 12D manual market buy/sell through existing `TradeController`;
6. acceptance: owned ship moves between two systems and completes a manual physical trade loop;
7. only after Stage 12 DoD begin Stage 13 combat vertical slice.

Stage 11 is closed. Do not add more expansion complexity before a new measured/design requirement appears; military territorial competition belongs to Stage 18.
