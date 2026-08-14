# Star Empires — дорожная карта разработки

> Канонический документ статуса и переходов между этапами разработки.
>
> Последняя синхронизация: **2026-08-15 после завершения Stage 16, merge PR #70 и финального CI #1337 / run `31849675260`**.
>
> Начиная с Stage 16 вся новая и содержательно изменяемая проектная документация ведётся **на русском языке**. Имена классов, enum, content ID, API, формулы и другие технические идентификаторы сохраняются в оригинальном виде, чтобы документация однозначно сопоставлялась с кодом.
>
> Подробный completion record Stage 16: `docs/stage16_completion_record.md`.
>
> Основные stage-документы: `docs/stage11_autonomous_faction_expansion.md`, `docs/stage12_playable_actor.md`, `docs/stage13_combat_vertical_slice.md`, `docs/stage14_complete_player_economic_loop.md`, `docs/stage15_player_fleets.md`, `docs/post_stage15_inertia_and_jump_hardening.md`, `docs/stage16_player_construction.md`, `docs/stage16_construction_timing.md`, `docs/stage16_acceptance_matrix.md`, `docs/stage16_completion_record.md`.
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

Без отдельного обоснованного design decision запрещены:

- отдельная «экономика игрока»;
- пассивный доход как замена реальному движению товаров/денег;
- виртуальные доставки;
- мгновенные путешествия/строительство;
- скрытые resource grants;
- scripted replacement уничтоженных активов;
- player-only combat/movement formula;
- UI, напрямую мутирующий authoritative simulation state.

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

Решение Stage 8.5 остаётся **`KEEP_LIBGDX`**. Presentation technology пересматривается только при новом измеренном фундаментальном ограничении.

---

# 3. Основные milestones

| Milestone | Цель | Stages | Статус |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | корректное и масштабируемое ядро экономики | 0–6 | **COMPLETE** |
| **v0.2 Living Galactic Economy** | многосистемные фракции, логистика, строительство, автономная экспансия | 7–11 + 8.5 | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | корабль игрока, путешествия, торговля, добыча, бой, прогрессия кораблей, читаемая локальная игра | 12–14 | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | флоты игрока, станции, фракция, стратегическая война | 15–18 + 17.5 | **ACTIVE — Stage 17** |
| **v0.5 RPG & Living World** | исследование, NPC, миссии, репутация | 19–20 | PLANNED |
| **v0.6 Content & Balance Alpha** | объём контента и долговременная стабильность | 21 | PLANNED |
| **v0.7 Polish / Release Candidate** | UX, onboarding, performance, save hardening | 22 | PLANNED |

Административный долг репозитория: обязательная branch protection для `main` пока не настраивается доступным connector API. Поэтому полный CI gate остаётся ручным обязательным условием перед каждым core merge.

---

# MILESTONE v0.1 — ECONOMIC SANDBOX

**COMPLETE.**

## Stage 0 — здоровье репозитория

**COMPLETE — PR #1.** Java-17 clean build, JUnit, JaCoCo, strict Javadoc, runnable shaded desktop JAR и GitHub Actions.

## Stage 1 — детерминированное время

**COMPLETE — PR #2.** Fixed step `0.1s`, pause/time scale, именованные RNG streams, явный порядок систем и независимость simulation result от FPS.

## Stage 2 — деньги и экономические инварианты

**COMPLETE — PR #3.** Integer milli-credits, конечная ликвидность, atomic bilateral trade, `EconomicLedger`, явные source/sink/transfer/transform semantics.

## Stage 3 — identity и persistence

**COMPLETE — PR #4.** Stable `EntityId`, versioned state, bounded codecs, безопасная замена сущностей и deterministic continuation tests.

## Stage 4 — data-driven content

**COMPLETE — PR #5.** Versioned JSON catalog со stable content IDs, товарами, recipes, factions, ships, stations, validation, fingerprint и save binding.

## Stage 5 — локальная логистика и route planning

**COMPLETE — PR #6.** Bounded `TradeRoutePlanner`, immutable market directory, profit/time scoring, deterministic tie-breaks и stale-route policy.

## Stage 6 — headless scalability / observability

**COMPLETE — PR #7/#8.** Большой headless economic benchmark, accounting diagnostics, supply-chain bottleneck observability и machine-readable reports.

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
- supplier purchase → fleet transit → destination revalidation → physical sale;
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

- `PlayerState` является envelope над player-agnostic `WorldState`;
- владение отделено от faction membership;
- игрок напрямую управляет существующим `FleetId` через fixed-tick intent;
- docking требует physical range;
- путешествия используют Stage-10 jump FSM;
- manual trade использует тот же `TradeController`, что и AI;
- cargo остаётся в real ship inventory;
- wallet/ownership/discovery/docking переживают save/load.

## Stage 13 — Combat Vertical Slice

**COMPLETE — PR #35.**

- data-driven первая combat-конфигурация hull/weapon;
- общие player/AI target+fire commands;
- общий range/cooldown/shield/hull resolver;
- deterministic простой CombatAI;
- lethal result идёт через ordinary destruction/salvage;
- нет player-only damage/reward path.

Advanced tactical AI сознательно отложен до появления полноценной movement/fitting/armor/shield/weapon глубины.

## Stage 14 — первый полный игровой экономический цикл

**COMPLETE — 14A PR #39, 14B PR #41, 14C PR #43, финальные 14D/14E PR #45.**

Финальный functional merge Stage 14: `0393eccf790269651bcedbdfd8e4eaf8b60ca06a`.

CI #1010 / run `31811876633`: **431/431 tests**, strict Javadoc, JaCoCo, shaded desktop package.

### 14A — добыча игроком

**COMPLETE.** Реальный finite asteroid reserve, общий `MiningSystem`, cargo в ship inventory, продажа только через ordinary market controller.

### 14B — покупка корабля / прогрессия

**COMPLETE.** Покупка передаёт существующий `FleetId`; реальные wallet transfers; нет clone/spawn/teleport/reset. Future live valuation описана в `docs/ship_pricing_roadmap.md`.

### 14C — navigation / HUD / minimap

**COMPLETE.** Camera zoom/follow, HUD, ownership-aware minimap, readable economy/mining/combat feedback, read-only presentation boundary.

### 14D — first-hour acceptance / telemetry

**COMPLETE — PR #45.** Полный 3600-second deterministic сценарий физически проходит trade, jump, mining, ship progression, combat, save/load и продолжение инерционного полёта без debug income/resource grants.

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

В PR #51 закрыт старый direct-position movement generic `TradeAISystem` / autonomous `MiningSystem`. Direct player, delegated fleet, generic traders и generic miners используют общий `FlightDynamics`.

### v0.3 DoD

Игрок может пройти связанный физический цикл: полёт → торговля → добыча → прогрессия кораблей → бой → сохранение/продолжение, пока мир живёт независимо. **v0.3 завершён.**

---

# MILESTONE v0.4 — FLEET & EMPIRE SANDBOX

**ACTIVE — Stage 17.**

## Stage 15 — флоты игрока / автономные приказы

**COMPLETE — PR #47, #48, #49; hardening PR #51.**

Документы: `docs/stage15_player_fleets.md`, `docs/post_stage15_inertia_and_jump_hardening.md`.

Цель достигнута: игрок владеет несколькими реальными `FleetId`, напрямую управляет одним и выдаёт остальным persistent orders без passive income и без отдельной AI economy/movement модели.

### Реализовано

- `HOLD`, `MOVE`, `TRADE`, `MINE`, `ESCORT`, `PATROL`, `FOLLOW`;
- persistent fleet orders + save/load;
- shared `FlightCommandComponent → AutonomousFlightSystem → FlightDynamics`;
- physical autonomous trade/mining;
- civilian flee/survival baseline;
- cumulative whole-route risk;
- physical follow/escort/patrol;
- discovered topology + fleets/orders/threat intel на global map;
- finite Stage-10 jump semantics;
- generic TradeAI/Mining direct movement debt закрыт.

PR #51: CI #1151 / run `31826504541`, **454/454 tests**, strict Javadoc, JaCoCo, desktop package.

### Stage 15 DoD

Несколько owned `FleetId` могут получать persistent autonomous orders, физически торговать/добывать/следовать/эскортировать/патрулировать, реагировать на известную угрозу и использовать whole-route risk через strategic map. **Stage 15 завершён.**

---

# Stage 16 — строительство игрока и владение станциями

**COMPLETE — PR #56–#70.**

Подробности:

- спецификация: `docs/stage16_player_construction.md`;
- формула времени: `docs/stage16_construction_timing.md`;
- acceptance matrix: `docs/stage16_acceptance_matrix.md`;
- итоговая запись: `docs/stage16_completion_record.md`.

Финальный functional merge: **PR #70**, merge commit `74bb854a79226280f1770032c1725b9ff32fd40e`.

Финальный CI #1337 / run `31849675260`:

- **484/484 tests**;
- 0 failures;
- 0 errors;
- strict Javadoc green;
- JaCoCo gates green;
- shaded desktop JAR build green;
- `BUILD SUCCESS`.

Artifact upload в этом run достиг GitHub storage quota, но согласно `docs/ci_artifact_publication_policy.md` publication является non-blocking; обязательный build/test gate полностью прошёл.

## 16A — ownership/schema separation

**COMPLETE.** Persistent player project/station ownership; playable/world migrations; `WorldState` player-agnostic; ownership отделён от optional legal/faction affiliation и funding/settlement semantics.

## 16B — placement / project authoring

**COMPLETE.** `PlayerConstructionService`, authoritative query/create, bounds/clearance/jump-arrival exclusion, territory/access policy. UI preview не создаёт station/project самостоятельно.

## 16C — player funding / site economy

**COMPLETE.** Player wallet → physical site wallet через atomic transfer + `EconomicLedger`. Extra funding меняет liquidity, но не ускоряет persisted build duration.

## 16D — physical materials / supply logistics

**COMPLETE.**

- manual delivery требует owned FleetId, physical range, low speed и real cargo;
- ordinary external TradeAI может снабжать site через общий market path;
- persistent `SUPPLY_PROJECT` использует discovered suppliers, Stage-15 cumulative route risk, shared movement/jump и ordinary purchase;
- никакого hard reservation или virtual delivery.

## 16E — build execution / time / remote continuation

**COMPLETE baseline.**

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

Lifecycle:

```text
PLANNED
→ FUNDED
→ AWAITING_MATERIALS
→ BUILDING
→ COMPLETED
```

Terminal states: `CANCELLED` / `FAILED`.

`buildDurationTicks` сохраняется в уже созданном project; remote coarse simulation продолжает BUILDING и корректно переживает save/load.

## 16F — completion / owned station / finance

**COMPLETE.**

```text
site removed
→ construction materials consumed by explicit sink
→ ordinary station entity created
→ resulting EntityId recorded
→ PlayerState ownership reconciled into OwnedStationRef
```

Station wallet остаётся real operating capital. Player deposit/withdraw идут только через atomic transfers + ledger. Passive profit transfer отсутствует.

## 16G — project/station UI и strategic map

**COMPLETE baseline.** Authoritative management model, local construction placement UI, funding/cancellation/supply commands, progress/material/ETA display, global map markers для owned projects/stations. Presentation остаётся read-only относительно simulation state.

## 16H — cancellation / failure / hardening

**COMPLETE в безопасной Stage-16 границе.**

- empty/funded project может быть отменён с реальным refund остатка wallet;
- при уже доставленных required materials voluntary cancellation отклоняется до появления корректного recoverable-material policy;
- во время `BUILDING` voluntary cancellation отклоняется до salvage-by-progress модели;
- destroyed site → `FAILED` без automatic refund/respawn;
- destroyed completed station удаляет ownership без replacement grant;
- remote completion/destruction и persistence покрыты acceptance.

Это осознанный conservation-preserving boundary, а не скрытая потеря материалов.

### Stage 16 end-to-end DoD

Финальный deterministic acceptance доказал:

```text
player wallet + owned fleets
→ valid site selection
→ ordinary project/site creation
→ real player funding
→ external market supply
→ owned SUPPLY_PROJECT
→ physical player cargo delivery
→ full physical fulfillment
→ BUILDING
→ real jump away
→ remote continuation
→ save/load mid-build
→ ordinary station completion
→ exact player ownership
→ real return + completed jump FSM
→ physical docking
→ station deposit/withdraw
→ save/load completed station
→ ordinary destruction
→ ownership removal without free replacement
```

**Stage 16 завершён.**

---

# Stage 17 — собственная фракция игрока

**ACTIVE — текущий основной этап.**

Главная задача Stage 17 — превратить независимого экономического игрока с owned fleets/stations в полноценного faction actor, **не заменяя уже существующие физические `FleetId` / `EntityId` и не создавая отдельную player-only политико-экономическую модель**.

Stage 17 должен переиспользовать Stage-8 faction core:

- treasury;
- budgets;
- subsidies;
- relations/diplomacy;
- territory/control;
- market access;
- taxes/tariffs;
- strategic policies;
- persistence.

## 17A — player faction identity / creation contract

Сначала определить persistent и migration-safe boundary:

```text
independent PlayerState
→ explicit create/join-own-faction action
→ stable faction identity
→ faction state in ordinary world/faction model
```

Нельзя молча создавать hidden player faction только потому, что у игрока есть station.

Нужно определить:

- stable faction content/runtime identity strategy;
- название/metadata без нарушения data-driven content boundary;
- когда игрок имеет право создать faction;
- начальный treasury contract;
- отношение personal wallet ↔ faction treasury;
- persistence/migration;
- ownership vs faction affiliation после создания.

## 17B — переход существующих assets под player faction

Stage-16 owned fleets/stations должны менять legal/faction affiliation **без respawn**:

```text
existing FleetId / EntityId
+ player ownership
→ explicit affiliation transition
→ same physical object
```

Нужно исключить duplicate fleet/station, reset cargo/wallet/condition и hidden grants.

## 17C — faction treasury и player finance boundary

Personal wallet и faction treasury должны оставаться разными economic accounts.

Минимальная модель:

```text
player wallet ↔ faction treasury
```

только через explicit transfer/ledger semantics.

Не вводить passive «весь доход моей фракции = личные деньги игрока».

## 17D — territory / control / construction access

Переиспользовать Stage-8 territory/control semantics и Stage-16 construction access.

Игрок должен видеть и изменять территориальный статус только через ordinary faction mechanics. Own station сама по себе не должна магически означать полный sovereignty над системой без явно определённого правила.

## 17E — diplomacy / market access

Player faction получает ordinary relations/access state:

- neutral/friendly/hostile;
- market access;
- tariffs/taxes;
- future treaties/war hooks.

Player и AI должны пользоваться общей policy boundary.

## 17F — faction policies / strategic economy

Переиспользовать Stage-8 budgets/subsidies/strategic demand. Player UI управляет policies, но реальные деньги и ресурсы продолжают двигаться через ordinary economy.

## 17G — faction management UI / global map

Нужен read-only authoritative management model + command layer для:

- treasury;
- owned/affiliated fleets/stations;
- relations;
- territory;
- access/tariffs;
- policies;
- strategic construction/expansion context.

## 17H — persistence / migration / end-to-end acceptance

Финальный Stage-17 сценарий должен доказать минимум:

```text
independent player with existing Stage-16 assets
→ create player faction
→ same physical assets become affiliated
→ transfer real capital into faction treasury
→ apply ordinary faction policy
→ ordinary economy reacts
→ territory/access state changes only through legal rules
→ save/load
→ diplomacy/access persistence
→ no asset duplication/reset
→ no money/resource creation
```

До закрытия этого gate Stage 17 не объявляется COMPLETE.

---

# Stage 17.5 — Combat Depth / Ship Fitting Foundation

**PLANNED prerequisite перед advanced tactical AI.**

Необходимая runtime база:

- несколько materially different hull classes;
- authoritative SI/per-item/component mass integration;
- armor больше generic hull HP;
- richer shield behavior;
- несколько weapon families/range envelopes;
- fitting/equipment foundation;
- equipment/armor/cargo/ammunition mass integration;
- stable combat-capability APIs;
- deterministic enriched combat tests;
- shipyard/shipbuilding capability seam.

## Параллельный engineering/design baseline

В репозитории уже ведётся **design/authoring track**, который не считается реализованным combat runtime:

- `docs/ship_hull_module_and_fleet_doctrine.md`;
- `docs/ship_mathematics_v0_1.md`;
- `docs/ship_mathematics_v0_2.md`;
- `docs/ship_mathematics_v0_3.md`;
- `docs/benchmarks/ship_reference_designs_v0_2.json`;
- weapon-interaction benchmark v0.3.

Authoritative design direction:

```text
Hull Size
→ Hull Architecture
→ Doctrine Class
→ Specialization
→ Ship Design
→ Variant / Refit
→ Ship Instance
```

SI используется как canonical engineering unit system. Роль корабля должна по возможности возникать из массы, объёма, тяги, энергии, тепла, hardpoints, сенсоров, экипажа и установленных модулей, а не из магических class bonuses.

Weapon mathematics v0.3 проектирует будущую цепочку:

```text
sensor measurement
→ track estimate + covariance
→ weapon pointing / guidance
→ time of flight
→ target maneuver envelope
→ interception / beam dwell / impact
→ local protection response
→ subsystem / compartment damage
```

Эти документы являются engineering/balance seeds и **не означают**, что новый combat resolver уже внедрён в runtime.

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

Игрок развивается от одного корабля до autonomous fleets/stations/faction и участвует в конфликтах, которые меняют реальные assets, trade routes, supply chains и territory.

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

Расширить resources, components, ships, stations и faction differentiation после стабилизации mechanics.

Это основной этап расширения technology ladder после стабилизации tier/capability mechanics.

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
- onboarding first trade/mining/combat/fleet/station/faction;
- autosave/backup/corrupt-save UX и supported migration window;
- profiling large combat, remote worlds, route planning, asset lists, construction и save/load;
- final graphics settings/release baselines;
- clean build/regression/soak/save-load-soak gates.

---

# 4. Параллельный Visual / UX track

Visual work развивается параллельно, но не заменяет functional DoD.

- **V1 Ship sprite pipeline:** grounded top-down language, size grammar, hardpoints, pivots/collision conventions.
- **V2 Engine/movement:** idle/thrust/maneuver привязываются к реальной movement/thrust state.
- **V3 Station language:** construction, industrial, mining, trade, military, colony, faction differentiation; future tech tiers выражаются через правдоподобную infrastructure/material sophistication.
- **V4 Combat VFX:** weapons, shields/hits/destruction/salvage.
- **V5 Playable navigation/readability:** Stage-14 baseline — **COMPLETE**.
- **V6 Strategic map / empire UI:** fleets/orders + construction/stations baseline завершены в Stages 15–16; territory/diplomacy/war продолжаются в Stages 17–18.

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

Construction, trade, mining, progression, expansion и warfare используют real entities, finite resources/cargo, wallets, travel и build time. Remote simulation может снижать fidelity, но не создавать несовместимые последствия.

## Shared player/AI core

Player-facing commands и AI intent адаптируются к общим simulation controllers. Player-only реализация требует explicit justification.

## Movement physicality

Direct player, delegated fleet, generic trader и generic miner используют shared `FlightDynamics`. Normal local movement не имеет права напрямую snap `Transform.position/velocity`, кроме explicit structural materialization events (spawn/load/jump arrival) с документированной семантикой.

## Jump / structural materialization

Inter-system travel использует Stage-10 finite jump FSM. `IN_SYSTEM` materialization в фазе `ARRIVING` ещё не означает, что jump полностью завершён; действия вроде docking должны соблюдать active jump state до terminal transition.

## AI information / route risk

Risk decisions используют доступные observations/intelligence. Whole-route risk оценивает все traversed systems/links, а не только destination.

## Construction physicality

Construction feasibility/time определяются authoritative project/archetype/material/facility data. Missing materials/capability нельзя заменить hidden currency shortcut. Уже начатый project хранит resolved construction contract.

## Ownership vs faction identity

Владение кораблём/станцией игроком — отдельный persistent слой. Stage 17 может менять explicit affiliation, но не должен заменять физический asset.

## Technology tiers

Future ship/station tiers — data-driven technology/production constraints, а не blanket stat/price multipliers. Player и AI используют одинаковые tier/capability checks.

## Presentation read-only boundary

HUD/minimap/global-map/construction/faction UI читают authoritative state и отправляют ordinary commands, но не мутируют economy/combat/mining/ownership/physics/construction/faction state напрямую.

## Documentation language

Начиная с Stage 16:

- новая проектная документация — на русском;
- обновляемый roadmap — на русском;
- stage specifications/acceptance matrices/completion records — на русском;
- code identifiers и content IDs не переводятся;
- исторические документы переводятся при содержательном обновлении, а не массовой механической операцией.

## Measure before optimization

Крупные системы получают diagnostics/benchmarks. Оптимизация делается по измерениям или явной структурной проблеме scaling, а не по предположениям.

---

# 6. Правила перехода между stages

1. `main` остаётся стабильным.
2. Core work начинается от текущего green `main`.
3. Broken blocking CI запрещает merge и stage transition.
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
15. Construction time определяется real project/material/facility inputs; already-started projects сохраняют resolved duration.
16. Player station ownership отделено от faction identity; Stage 17 делает affiliation transition явно.
17. Future tech tiers — stable content/system data, не blanket multipliers.
18. Новая/обновляемая документация с Stage 16 ведётся на русском.
19. Artifact publication failure из-за внешней quota не отменяет green core gate, если `clean verify`/tests/Javadoc/JaCoCo/package успешно завершены согласно `docs/ci_artifact_publication_policy.md`.
20. Этот roadmap меняется только после фактически подтверждённых implementation/merge evidence либо при явном изменении будущего плана пользователем.

---

# 7. Текущий следующий шаг

**ACTIVE: Stage 17 — собственная фракция игрока.**

Фактическая база перед Stage 17:

- Stage 15 COMPLETE — multiple owned fleets + persistent orders;
- Stage 16 COMPLETE — physical construction + owned ordinary stations;
- Stage-8 faction treasury/territory/relations/access/policies уже существуют для ordinary factions;
- ownership assets и faction affiliation уже концептуально разделены;
- `WorldState` остаётся player-agnostic;
- final Stage-16 gate: **484/484 green**, PR #70 merged.

Immediate Stage-17 order:

1. **17A:** зафиксировать player-faction identity/creation/persistence contract и acceptance matrix;
2. **17B:** explicit affiliation transition существующих owned fleets/stations без respawn/ID replacement;
3. **17C:** personal wallet ↔ faction treasury atomic finance boundary;
4. **17D:** territory/control/construction-access integration;
5. **17E:** diplomacy/market-access/tariff integration;
6. **17F:** player-facing faction policies поверх Stage-8 core;
7. **17G:** authoritative faction management/global-map UI;
8. **17H:** migration, conservation, save/load и full Stage-17 end-to-end acceptance.

Не начинать advanced tactical AI до Stage 17.5. Не превращать параллельные ship-mathematics documents в production constants без отдельной runtime implementation/validation. Не создавать hidden player faction, duplicate assets или passive treasury income shortcuts.
