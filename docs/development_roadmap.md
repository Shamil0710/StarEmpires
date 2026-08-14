# Star Empires — дорожная карта разработки

> Канонический документ статуса и переходов между этапами разработки.
>
> Последняя синхронизация: **2026-08-14 после завершения Stage 15, post-Stage-15 hardening PR #51, документационного PR #52 и синхронизации roadmap PR #53**.
>
> Начиная с Stage 16 вся новая и содержательно изменяемая проектная документация ведётся **на русском языке**. Имена классов, enum, content ID, API, формулы и другие технические идентификаторы сохраняются в оригинальном виде, чтобы документация однозначно сопоставлялась с кодом.
>
> Старые completion-records не переводятся массово только ради перевода; они переводятся при следующем содержательном обновлении.
>
> Основные документы: `docs/stage11_autonomous_faction_expansion.md`, `docs/stage12_playable_actor.md`, `docs/stage13_combat_vertical_slice.md`, `docs/stage14_complete_player_economic_loop.md`, `docs/stage15_player_fleets.md`, `docs/post_stage15_inertia_and_jump_hardening.md`, `docs/stage16_player_construction.md`, `docs/stage16_construction_timing.md`, `docs/stage16_acceptance_matrix.md`.
>
> Сквозные планы: `docs/ui_navigation_roadmap.md`, `docs/ai_behavior_roadmap.md`, `docs/cumulative_route_risk_model.md`, `docs/flight_dynamics_and_combat_depth_roadmap.md`, `docs/ship_pricing_roadmap.md`.

---

# 1. Цель проекта и главный инвариант

**Star Empires** — 2D top-down космическая sandbox-RPG/strategy с живой физической экономикой и миром, существующим независимо от игрока.

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

Главный инвариант:

> **Игрок и AI используют одни и те же физические и экономические правила везде, где это практически возможно.**

Запрещены без отдельного обоснованного design decision:

- отдельная «экономика игрока»;
- пассивный доход как замена реальному движению товаров/денег;
- виртуальные доставки;
- мгновенные путешествия/строительство;
- скрытые resource grants;
- scripted replacement уничтоженных активов;
- player-only combat/movement formula.

---

# 2. Технологический стек

- Java 17;
- libGDX 1.14.2 / LWJGL3;
- Ashley ECS 1.7.4;
- VisUI 1.5.9;
- Maven Wrapper;
- JUnit + JaCoCo;
- GitHub Actions;
- data-driven JSON content catalog;
- deterministic fixed-tick simulation;
- versioned bounded binary persistence.

Решение Stage 8.5 остаётся **`KEEP_LIBGDX`**. Пересматривать presentation technology только при появлении нового измеренного фундаментального ограничения.

---

# 3. Основные milestones

| Milestone | Цель | Stages | Статус |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | корректное и масштабируемое ядро экономики | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | многосистемные фракции, логистика, строительство, автономная экспансия | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | корабль игрока, путешествия, торговля, добыча, бой, прогрессия кораблей, читаемая локальная игра | 12–14 | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | флоты игрока, станции, фракция, стратегическая война | 15–18 + 17.5 | **ACTIVE — Stage 16** |
| **v0.5 RPG & Living World** | исследование, NPC, миссии, репутация | 19–20 | PLANNED |
| **v0.6 Content & Balance Alpha** | объём контента и долговременная стабильность | 21 | PLANNED |
| **v0.7 Polish / Release Candidate** | UX, onboarding, performance, save hardening | 22 | PLANNED |

Административный долг репозитория: обязательная branch protection для `main` пока не настраивается доступным connector API. Поэтому полный CI gate остаётся ручным обязательным условием перед каждым core merge.

---

# MILESTONE v0.1 — ECONOMIC SANDBOX

**COMPLETE.**

## Stage 0 — здоровье репозитория

**COMPLETE — PR #1.** Чистая Java-17 сборка, JUnit, JaCoCo, strict Javadoc, runnable shaded desktop JAR и GitHub Actions.

## Stage 1 — детерминированное время

**COMPLETE — PR #2.** Fixed step `0.1s`, pause/time scale, именованные RNG streams, явный порядок систем и независимость simulation результата от FPS.

## Stage 2 — деньги и экономические инварианты

**COMPLETE — PR #3.** Integer milli-credits, конечная ликвидность, atomic bilateral trade, `EconomicLedger`, явные source/sink/transfer/transform semantics.

## Stage 3 — identity и persistence

**COMPLETE — PR #4.** Stable `EntityId`, versioned state, bounded codecs, безопасная замена сущностей и deterministic continuation tests.

## Stage 4 — data-driven content

**COMPLETE — PR #5.** Versioned JSON catalog со stable content IDs, товарами, recipes, factions, ships, stations, validation, fingerprint и save binding.

## Stage 5 — локальная логистика и route planning

**COMPLETE — PR #6.** Bounded `TradeRoutePlanner`, immutable market directory, profit/time scoring, deterministic tie-breaks и stale-route policy.

## Stage 6 — headless scalability / observability

**COMPLETE — PR #7/#8.** Большой headless economic benchmark, accounting diagnostics, выявление проблем supply chain и machine-readable reports.

### v0.1 DoD

Экономическое ядро детерминировано, сохраняет деньги/товары в рамках явных правил, корректно сохраняется, масштабируется headless и выдаёт измеримые diagnostics. **Milestone завершён.**

---

# MILESTONE v0.2 — LIVING GALACTIC ECONOMY

**COMPLETE.**

## Stage 7 — иерархия мира и уровни симуляции

**COMPLETE — PR #9.** `Galaxy -> Sector -> StarSystem`, typed stable IDs, topology, `WorldState`, одна full-rate active system и bounded remote strategic updates.

## Stage 8 — фракции как экономические акторы

**COMPLETE — PR #10.** Treasury, budgets, subsidies, diplomacy, territory, market access, taxes/tariffs, strategic demand и persistence. Политики двигают реальные деньги и ресурсы.

## Stage 8.5 — проверка графического/технологического направления

**COMPLETE — `KEEP_LIBGDX`.** Production-like sprite/VFX seam, separation presentation/simulation, real-GPU validation и Java-17 CI.

## Stage 9 — динамическая экономика

**COMPLETE.**

- lifecycle create/remove + persistence;
- строительство с реальным funding/materials/build time;
- destruction с физической потерей/salvage/economic shock;
- анализ bottleneck и инвестиционная реакция;
- benchmark восстановления экономики после уничтожения producer.

Stage 9 DoD: экономика может физически деградировать, выявить bottleneck, инвестировать и восстановиться без scripted respawn.

## Stage 10 — межсистемная логистика

**COMPLETE — PR #23.**

- persistent world-level `FleetId`;
- authoritative jump FSM с deterministic timing и mid-transit persistence;
- weighted multi-hop routing;
- bounded discovery/revision invalidation;
- реальная цепочка supplier purchase → fleet transit → destination revalidation → physical sale;
- непроданный cargo остаётся на корабле.

## Stage 11 — автономная экспансия фракций

**COMPLETE — PR #24–#27.** Технический record: `docs/stage11_autonomous_faction_expansion.md`.

- deterministic opportunity ranking;
- persistent strategic growth plans;
- реальные faction budget/fleet/material transport;
- ordinary Stage-9 construction;
- deterministic physical competition;
- нет automatic conquest shortcut.

### v0.2 end-to-end

```text
живая многосистемная экономика
→ разрушение / дефицит
→ AI investment и recovery
→ физическая межсистемная логистика
→ persistent expansion plan
→ реальное снабжение стройки
→ новая станция / economic node
→ deterministic territorial growth
```

**v0.2 завершён.**

---

# MILESTONE v0.3 — PLAYABLE SPACE SANDBOX

**COMPLETE.**

Подробное закрытие Stage 14: `docs/stage14_complete_player_economic_loop.md`.

## Stage 12 — Player State, ownership, travel и manual trade

**COMPLETE — PR #29–#32.**

Основной результат:

- `PlayerState` является envelope над player-agnostic `WorldState`;
- владение отделено от faction membership;
- игрок напрямую управляет существующим `FleetId` через fixed-tick intent;
- docking требует физического range;
- путешествия используют Stage-10 jump FSM;
- manual trade использует тот же `TradeController`, что и AI;
- cargo остаётся в реальном ship inventory;
- wallet/ownership/discovery/docking переживают save/load.

## Stage 13 — Combat Vertical Slice

**COMPLETE — PR #35.**

- data-driven первая combat-конфигурация hull/weapon;
- общие player/AI target+fire commands;
- общий range/cooldown/shield/hull resolver;
- deterministic простой CombatAI;
- lethal result идёт через обычный destruction/salvage;
- нет player-only damage/reward path.

Advanced tactical AI сознательно отложен до появления полноценной movement/fitting/armor/shield/weapon глубины.

## Stage 14 — первый полный игровой экономический цикл

**COMPLETE — 14A PR #39, 14B PR #41, 14C PR #43, финальные 14D/14E PR #45.**

Финальный functional merge Stage 14: `0393eccf790269651bcedbdfd8e4eaf8b60ca06a`.

CI #1010 / run `31811876633`: **431/431 tests**, strict Javadoc, JaCoCo, shaded desktop package.

### 14A — добыча игроком

**COMPLETE.** Реальный finite asteroid reserve, общий `MiningSystem`, cargo в ship inventory, продажа только через обычный market controller.

### 14B — покупка корабля / прогрессия

**COMPLETE.** Покупка передаёт существующий `FleetId`; реальные wallet transfer; нет clone/spawn/teleport/reset. Future live valuation описана в `docs/ship_pricing_roadmap.md`.

### 14C — navigation / HUD / minimap

**COMPLETE.** Camera zoom/follow, HUD, ownership-aware minimap, readable economy/mining/combat feedback, read-only presentation boundary.

### 14E — общая инерционная модель

**COMPLETE — PR #45, затем распространена на generic NPC в PR #51.**

```text
dry hull/structure mass
+ cargo mass
= total mass

thrust / total mass = acceleration
braking thrust / total mass = braking acceleration
```

Текущий compatibility rule: **1 cargo inventory unit = 1 normalized mass unit** до появления authoritative per-item mass.

В PR #51 полностью закрыт старый direct-position movement generic `TradeAISystem` / autonomous `MiningSystem`. Direct player, delegated fleet, generic traders и generic miners используют общий `FlightDynamics`.

### 14D — first-hour acceptance / telemetry

**COMPLETE — PR #45.** Полный 3600-second deterministic сценарий физически проходит trade, jump, mining, ship progression, combat, save/load и продолжение инерционного полёта без debug income/resource grants.

### v0.3 DoD

Игрок может пройти связанный физический цикл: полёт → торговля → добыча → прогрессия кораблей → бой → сохранение/продолжение, пока мир живёт независимо. **v0.3 завершён.**

---

# MILESTONE v0.4 — FLEET & EMPIRE SANDBOX

**ACTIVE — Stage 16.**

## Stage 15 — флоты игрока / автономные приказы

**COMPLETE — PR #47, #48, #49; hardening PR #51.**

Документы: `docs/stage15_player_fleets.md`, `docs/post_stage15_inertia_and_jump_hardening.md`.

Цель достигнута: игрок владеет несколькими реальными FleetId, напрямую управляет одним и выдаёт остальным persistent orders без passive income и без отдельной AI economy/movement модели.

### 15A — persistent fleet orders

**COMPLETE.** `HOLD`, `MOVE`, `TRADE`, `MINE`, `ESCORT`, `PATROL`, `FOLLOW`; состояние переживает save/load.

### 15B — shared inertial execution

**COMPLETE.**

```text
persistent order / generic AI intent
→ FlightCommandComponent
→ AutonomousFlightSystem
→ FlightDynamics
→ authoritative Transform
```

Cargo влияет на autonomous mobility через тот же mass calculation.

### 15C — autonomous economic orders

**COMPLETE.** `TRADE` и `MINE` используют реальные markets/inventories/mining resources и Stage-10 physical transit.

### 15D — civilian survival / replanning

**COMPLETE baseline.** Наблюдаемая атака временно прерывает работу, flee использует физическое движение, persistent order сохраняется, hysteresis предотвращает oscillation.

### 15E — cumulative whole-route risk

**COMPLETE baseline.** Risk оценивается по всем известным system/link segments с confidence/aging и actor-specific cargo/damage/mobility/escort context. Danger score остаётся exposure, а не выдуманной вероятностью.

### 15F — FOLLOW / ESCORT / PATROL

**COMPLETE baseline.** Реальное физическое следование, operational co-located escort mitigation, persistent patrol waypoints и Stage-10 transit без teleport.

### 15G — первая функциональная global map

**COMPLETE baseline.** Discovered topology, owned fleets, orders/transit, known threat intel, ordinary command submission и тот же route planner, что использует execution.

### Post-Stage-15 hardening — PR #51

**COMPLETE.**

- generic TradeAI/Mining direct movement debt закрыт;
- все текущие локальные корабли используют shared inertia;
- `FOLLOW/ESCORT/PATROL` покрыты acceptance;
- `J` использует конечный Stage-10 FSM, а не instant teleport;
- canonical arrival — `(1000,700)` для текущей local map;
- тот же FleetId после arrival становится active entity новой системы;
- камера центрируется на нём;
- Anchor → Corona в текущем demo занимает около 13 fixed ticks / ~1.3 simulation seconds на x1.

PR #51: CI #1151 / run `31826504541`, **454/454 tests**, strict Javadoc, JaCoCo, desktop package.

### Stage 15 DoD

Несколько owned FleetId могут получать persistent autonomous orders, физически торговать/добывать/следовать/эскортировать/патрулировать, реагировать на известную угрозу и использовать whole-route risk через стратегическую карту. **Stage 15 завершён.**

---

# Stage 16 — строительство игрока и владение станциями

**ACTIVE — текущий основной этап.**

Главная спецификация: `docs/stage16_player_construction.md`.

Формула времени: `docs/stage16_construction_timing.md`.

Acceptance matrix: `docs/stage16_acceptance_matrix.md`.

Цель:

> Игрок должен построить настоящую persistent станцию через тот же Stage-9 physical construction core: собственные деньги, физическая стройплощадка, реальный спрос на материалы, реальная доставка, simulation-time строительство, ordinary station entity и отдельное player ownership.

## 16A — ownership/schema separation

**ACTIVE FIRST SLICE.**

Необходимо:

- добавить persistent ownership player construction projects;
- добавить persistent ownership готовых station entities;
- сохранить `WorldState` player-agnostic;
- разделить project beneficiary/ownership, optional faction/legal affiliation и funding source;
- добавить migration PlayerState/world construction contract;
- определить explicit settlement policy остатка site wallet.

Рекомендуемые player fields:

```text
ownedConstructionProjectIds
ownedStationRefs
```

Player ownership не должен автоматически переписывать `FactionComponent`.

## 16B — placement / project authoring

Player-facing `PlayerConstructionService` + read-only view/policies должны:

- перечислять buildable station archetypes;
- показывать funding/materials/materialWork/ETA;
- валидировать discovered/current system;
- валидировать physical location/clearance;
- не разрешать размещение в jump-arrival exclusion zone;
- проверять territory/construction access;
- создавать обычный `ConstructionProject`, а не готовую станцию.

Первый baseline: физическое placement только в active system. Remote construction позже требует реального builder/capability.

## 16C — player funding / site economy

```text
PlayerState wallet
→ atomic transfer
→ construction site WalletComponent
→ EconomicLedger
```

`minimumFunding` является project/site liquidity, а не магической «ценой станции».

Construction site остаётся обычным `MarketComponent + InventoryComponent + WalletComponent` и публикует реальный спрос на недостающие материалы.

## 16D — физические материалы / supply logistics

Manual player delivery обязана проверять:

- owned source FleetId;
- ту же StarSystem;
- отсутствие jump transit;
- physical transfer/docking range;
- достаточно малую скорость;
- реальный ship cargo;
- remaining project requirement.

Обычные NPC traders могут снабжать site через существующий trade path.

Желательный Stage-16 fleet extension: persistent `SUPPLY_PROJECT`, который использует Stage-15 market/risk/movement APIs и физически доставляет недостающие материалы.

## 16E — build execution / time / capability seam

**TIME FOUNDATION уже реализован в PR #51.**

```text
materialWork = Σ(requiredAmount × constructionHandlingWeight)

buildTime =
    baseSetupSeconds
  + materialWork / baselineAssemblyRate
```

Текущие work values:

- `MATERIAL = 1.00`;
- `GAS_LIQUID = 0.55`;
- `FINISHED_GOODS = 1.60`;
- `baselineAssemblyRate = 12 work/s`.

Это work units, не килограммы.

Сохраняется lifecycle:

```text
PLANNED
→ FUNDED
→ AWAITING_MATERIALS
→ BUILDING
→ COMPLETED
```

с terminal `CANCELLED` / `FAILED`.

Будущий `effectiveAssemblyRate` должен зависеть от реального builder/site capability. Уже созданный project хранит resolved `buildDurationTicks`.

## 16F — completion / owned station / finance

После completion:

```text
site removed
→ required materials consumed as explicit construction sink
→ ordinary station entity created
→ resulting EntityId recorded
→ PlayerState получает ownership этой physical station
```

Station ownership и faction/legal identity остаются разными понятиями.

Минимальная station finance boundary:

```text
player wallet ↔ station WalletComponent
```

только через atomic transfer + ledger.

**Никакого автоматического passive profit transfer.** Станции нужен реальный operating capital.

## 16G — project/station UI и strategic map

Показывать из authoritative state:

- archetype/location;
- funding/site wallet;
- delivered/required/missing materials;
- materialWork;
- build progress/ETA;
- status/failure reason;
- owned completed stations;
- supply routes/orders.

Local placement preview не меняет мир до accepted command.

## 16H — cancellation / failure / hardening

Нужно закрыть текущий material-fate debt:

- cancel до материалов → refund и removal;
- cancel после partial delivery → материалы не исчезают, а становятся physically recoverable;
- voluntary cancel после `BUILDING` можно запретить до появления корректного salvage-by-progress;
- destroyed site → `FAILED` без refund/respawn shortcut;
- multiple player/NPC projects конкурируют за одни markets/materials deterministic образом.

### Stage 16 Definition of Done

Автоматизированный deterministic сценарий должен доказать:

```text
player wallet + owned fleet
→ valid site/archetype selection
→ real project/site creation
→ real player funding
→ real market/cargo sourcing
→ physical owned-fleet delivery
→ full material fulfillment
→ BUILDING only after fulfillment
→ leave system / remote simulation continues
→ save/load mid-project
→ completion after authoritative ticks
→ ordinary physical station entity
→ player ownership of exact resulting EntityId
→ ordinary station market/production/wallet behavior
→ player deposit/withdraw through real transfers
→ save/load completed station
→ ordinary destruction removes asset without free replacement
```

Обязательные отрицательные проверки:

- нельзя строить в invalid/undiscovered location;
- нельзя строить без territory access;
- нельзя передать material через всю систему;
- нельзя построить без физических материалов;
- нельзя ускорить строительство простой доплатой;
- нельзя получить duplicate project/station повторной командой;
- нельзя потерять/удвоить деньги при rollback;
- нельзя изменить persisted duration после load;
- UI не может мутировать construction state напрямую.

---

# Будущие технологические тиры кораблей и построек

**PLANNED cross-cutting requirement.**

Технологические тиры вводятся, когда Stage 16 construction и Stage 17.5 fitting/shipbuilding дают им реальные механические последствия.

Ключевой принцип:

> **Tech tier — это уровень технологической/производственной сложности, а не линейное качество объекта.**

Запрещена модель:

```text
T2 = ×2 цена / HP / damage / build time
T3 = ×3
```

Предпочтительная причинная цепочка:

```text
tech tier
→ prerequisite technology/unlock
→ более специализированные components/materials
→ required facility/shipyard capability
→ tooling/integration complexity
→ ограниченное число производителей
→ более сложная logistics/supply chain
→ scarcity/market pressure
→ эмерджентная цена и доступность
```

## Tech tier станций

Будущий `StationArchetype.techTier` или stable equivalent должен влиять на:

- technology ownership/unlock;
- minimum construction facility capability;
- specialized component requirements;
- assembly/commissioning complexity;
- module/function availability;
- repair/upgrade infrastructure;
- возможные licenses/faction restrictions.

Целевая формула времени:

```text
materialWork = Σ(quantity × authoritativeMassOrHandlingWork)

effectiveAssemblyRate = f(real site/builder capability)

buildTime =
    (baseSetupTime + materialWork / effectiveAssemblyRate)
    × techTierFactor
    × complexityFactor
```

Higher tier не обязан всегда строиться дольше: advanced yard может иметь высокий effective assembly rate.

## Tech tier кораблей

Будущий `ShipArchetype.techTier` должен влиять на:

- required shipyard class/capability;
- component/fitting prerequisites;
- tooling/production complexity;
- repair/refit requirements;
- blueprint/unlock rules;
- valuation **косвенно** через реальные components, production cost, scarcity, condition, seller policy и market state.

Tech tier не заменяет hull class/role. Маленький high-tier courier может быть меньше low-tier bulk freighter.

## Capability + specialization

Одной tier-цифры может быть недостаточно. В будущем допустимы capability tags, например:

```text
CAP_HEAVY_HULL
CAP_PRECISION_ELECTRONICS
CAP_MILITARY_REACTOR
CAP_CAPITAL_ASSEMBLY
```

Условие production может требовать и достаточный tier, и нужную specialization.

## Persistence rule tiers

При введении tiers:

1. stable bounded content fields + validation;
2. явная migration/default для существующего content;
3. shared player/AI capability checks;
4. реальные tier-appropriate components/facilities;
5. ship price через `docs/ship_pricing_roadmap.md`, не blanket multiplier;
6. deterministic acceptance для insufficient/sufficient technology/facility;
7. already-started projects сохраняют свой resolved contract.

---

# Stage 17 — собственная фракция игрока

**PLANNED.**

Использовать Stage-8 treasury, territory, relations, access, taxes/subsidies и policies.

Stage 16 owned stations/fleets должны перейти под player faction без замены физических EntityId/FleetId.

Technology ownership/unlocks должен стать общим player/faction state здесь, если минимальный technology requirement не понадобится раньше.

---

# Stage 17.5 — Combat Depth / Ship Fitting Foundation

**PLANNED prerequisite перед advanced tactical AI.**

Необходимая база:

- несколько materially different hull classes;
- meaningful ship tech-tier integration при достаточном content;
- armor больше generic hull HP;
- richer shield behavior;
- несколько weapon families/range envelopes;
- fitting/equipment foundation;
- equipment/armor/cargo/ammunition mass integration;
- stable combat-capability APIs;
- deterministic enriched combat tests.

Здесь же должна появиться authoritative база для будущего shipbuilding/player shipyards.

---

# Stage 18 — strategic warfare + advanced combat behavior

**PLANNED после Stage 17.5 gate.**

- formal war/peace/hostility;
- fronts/blockades/territory objectives;
- advanced weapon/range/mobility-aware tactical AI;
- escort/screen/intercept/retreat/pursuit;
- replacement logistics;
- shared threat intelligence confidence/freshness/decay;
- conflict-driven traffic rerouting и economic consequences;
- strategic global-map overlays.

### v0.4 DoD

Игрок развивается от одного корабля до автономных fleets/stations/faction и участвует в конфликтах, которые меняют реальные assets, trade routes, supply chains и territory.

---

# MILESTONE v0.5 — RPG & LIVING WORLD

**PLANNED.**

## Stage 19 — исследование / discovery / world generation

Persistent discovered systems/routes/stations/resources; deterministic seed-driven galaxy generation; anomalies, derelicts, special locations. Доступность информации остаётся explicit.

## Stage 20 — NPC / missions / reputation / progression

Persistent NPC там, где identity важна. Missions должны возникать из реального world state: haul, mine, escort, bounty, investigate, defend, shortage, expansion, war, discovery.

Persistent commanders могут давать bounded personality/doctrine modifiers без omniscience.

---

# MILESTONE v0.6 — CONTENT & BALANCE ALPHA

**PLANNED.**

## Stage 21 — ширина контента / баланс / long-run stability

Расширить resources, components, ships, stations и faction differentiation после стабилизации механик.

Это также основной этап расширения technology ladder после стабилизации tier mechanics.

Long-run soak/benchmark matrix должна выявлять:

- inflation/deflation;
- permanent shortages/dead economies;
- uncontrolled entity/ledger growth;
- route-planner scaling problems;
- faction snowball;
- civilians never travelling;
- civilians ignoring obvious known wars;
- engage/retreat or route-choice oscillation;
- escorts abandoning convoys;
- danger that never decays;
- universal risk avoidance или suicidal profit chasing;
- tech tiers превращаются в обязательные линейные upgrades;
- high-tier production обходит реальные component/facility bottlenecks;
- construction queues/material logistics создают runaway backlog или бесплатное производство.

---

# MILESTONE v0.7 — POLISH / RELEASE CANDIDATE

**PLANNED.**

## Stage 22 — UX / onboarding / performance / release hardening

- унификация и polish HUD/management UI;
- production global/local map filters/search/notifications;
- input discoverability/accessibility/scaling;
- onboarding first trade/mining/combat/fleet/station;
- autosave/backup/corrupt-save UX и supported migration window;
- profiling large combat, remote worlds, route planning, asset lists, construction и save/load;
- final graphics settings/release baselines;
- clean build/regression/soak/save-load-soak gates.

---

# 4. Параллельный Visual / UX track

Visual work развивается параллельно, но не заменяет functional DoD.

- **V1 Ship sprite pipeline:** grounded top-down language, size grammar, hardpoints, pivots/collision conventions.
- **V2 Engine/movement:** idle/thrust/maneuver привязываются к реальной movement/thrust state.
- **V3 Station language:** construction, industrial, mining, trade, military, colony, faction differentiation; будущие tech tiers визуально выражаются через правдоподобную инфраструктуру/material sophistication, а не случайное количество декора.
- **V4 Combat VFX:** weapons, shields/hits/destruction/salvage.
- **V5 Playable navigation/readability:** Stage-14 baseline — **COMPLETE**.
- **V6 Strategic map / empire UI:** topology/navigation → fleets/orders → construction/stations → territory/trade/risk/war в Stages 15–18.

Gameplay не зависит от одного конкретного sprite asset. Presentation metadata остаётся data-driven поверх authoritative archetypes.

---

# 5. Сквозные инженерные правила

## Persistence

Каждый persistent domain object имеет stable identity, schema ownership, bounded codec, migration policy и continuation tests.

## Determinism

Planner/AI используют deterministic iteration/tie-breaks. RNG именован и применяется только там, где randomness — явное design requirement.

## Economic conservation

Каждое изменение денег/ресурсов имеет transfer/source/sink/transform semantics и ledger/invariant coverage. Скрытого дохода/ресурсов нет.

## Physicality

Construction, trade, mining, progression, expansion и warfare используют реальные entities, finite resources/cargo, wallets, travel и build time. Remote simulation может снижать fidelity, но не создавать несовместимые последствия.

## Shared player/AI core

Player-facing commands и AI intent адаптируются к общим simulation controllers. Player-only реализация требует explicit justification.

## Movement physicality

Direct player, delegated fleet, generic trader и generic miner используют shared `FlightDynamics`. Normal local movement не имеет права напрямую snap `Transform.position/velocity`, кроме explicit structural materialization events (spawn/load/jump arrival) с документированной семантикой.

## Jump / structural materialization

Inter-system travel использует Stage-10 finite jump FSM. Persistent FleetId может временно отсоединяться от local ECS и материализоваться в destination. Arrival anchor и camera follow должны быть authoritative/documented.

## AI information / route risk

Risk decisions используют доступные observations/intelligence. Whole-route risk оценивает все traversed systems/links, а не только destination.

## Construction physicality

Construction feasibility/time определяются authoritative project/archetype/material/facility data. Missing materials/capability нельзя заменить hidden currency shortcut.

Уже начатый project хранит resolved construction contract.

## Ownership vs faction identity

Владение кораблём/станцией игроком — отдельный persistent слой и не должно неявно переписывать faction/legal identity. Это особенно важно до Stage 17.

## Technology tiers

Future ship/station tiers — data-driven technology/production constraints, а не blanket stat/price multipliers. Player и AI используют одинаковые tier/capability checks.

## Presentation read-only boundary

HUD/minimap/global-map/construction UI читают authoritative state и отправляют ordinary commands, но не мутируют economy/combat/mining/ownership/physics/construction напрямую.

## Documentation language

Начиная с Stage 16:

- новая проектная документация — на русском;
- обновляемый roadmap — на русском;
- новые stage specifications/acceptance matrices/completion records — на русском;
- code identifiers и content IDs не переводятся;
- исторические документы переводятся при содержательном обновлении, а не массовой механической операцией.

## Measure before optimization

Крупные системы получают diagnostics/benchmarks. Оптимизация делается по измерениям или явной структурной проблеме scaling, а не по предположениям.

---

# 6. Правила перехода между stages

1. `main` остаётся стабильным.
2. Core work начинается от текущего green `main`.
3. Broken CI блокирует merge и stage transition.
4. Каждый stage имеет explicit vertical slice и DoD.
5. Persistent changes требуют migration/continuation coverage.
6. Economic changes требуют conservation/invariant coverage.
7. Deterministic decision code требует tie-break coverage.
8. Player и AI используют общие APIs, если разделение не обосновано.
9. Не расширять content breadth до стабилизации mechanics.
10. UI/map остаются views + command adapters.
11. Advanced tactical combat AI не начинается до combat-depth gate.
12. Strategic danger routing оценивает весь путь.
13. Generic/local movement debt закрыт PR #51; direct normal-movement `Transform` mutation не возвращается.
14. Generated ship pricing в будущем использует live economy/material/component/fitting/condition/relationship inputs и real-asset ownership transfer.
15. Construction time определяется реальными project/material/facility inputs; already-started projects сохраняют resolved duration.
16. Player station ownership отделено от faction identity; Stage 16 не создаёт скрытую player faction.
17. Future tech tiers — stable content/system data, не blanket multipliers.
18. Новая/обновляемая документация с Stage 16 ведётся на русском.
19. Этот roadmap меняется только после фактически подтверждённых implementation/merge evidence либо при явном изменении будущего плана пользователем.

---

# 7. Текущий следующий шаг

**ACTIVE: Stage 16 — строительство игрока и владение станциями.**

Фактическая база перед началом implementation:

- Stage 15 COMPLETE;
- generic NPC inertia debt CLOSED;
- finite `J` jump + canonical arrival/camera centering покрыты acceptance;
- current build-time formula уже в production `ConstructionDurationPolicy`;
- `ConstructionProjectService` уже создаёт physical site, funding/material state, BUILDING lifecycle, completion station и persistence;
- главный architectural gap — Stage-9 construction пока жёстко связывает owner с faction treasury, тогда как игрок до Stage 17 является отдельным economic actor.

Immediate implementation order:

1. **16A:** player project/station ownership schema + separation ownership/legal faction/funding source;
2. **16B:** authoritative project query/create + placement/access policy;
3. **16C:** atomic player-wallet funding + construction-site economy;
4. **16D:** physical manual material delivery, затем owned-fleet supply integration;
5. **16E:** сохранить уже работающую duration policy, добавить progress/remote continuation/capability seam;
6. **16F:** completion → exact station ownership + station wallet deposit/withdraw;
7. **16G:** local construction placement UI + strategic project/station management;
8. **16H:** cancellation/material fate, failure, persistence, deterministic end-to-end acceptance;
9. после Stage-16 DoD перейти к **Stage 17 — собственная фракция игрока**.

Не начинать сейчас advanced tactical AI. Не создавать instant stations/virtual delivery. Не создавать player faction только ради обхода Stage-9 ownership. Не вводить tech tiers как произвольные линейные коэффициенты.
