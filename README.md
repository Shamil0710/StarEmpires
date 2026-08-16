# Star Empires

**Star Empires** — разрабатываемая 2D top-down космическая sandbox/RPG/strategy на Java и libGDX с живой физической экономикой, автономными фракциями и миром, который существует независимо от игрока.

Проект строится как единая симуляция: игрок, AI, флоты, станции и фракции по возможности используют одни и те же физические, экономические, логистические, политические и боевые правила.

Главная линия прогрессии:

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

## Текущее состояние

**Последняя синхронизация README: 2026-08-16 / Stage 17H.**

Канонический статус разработки: [`docs/development_roadmap.md`](docs/development_roadmap.md).

| Milestone | Цель | Статус |
| --- | --- | --- |
| **v0.1 Economic Sandbox** | deterministic economic core | **COMPLETE** |
| **v0.2 Living Galactic Economy** | multi-system factions, logistics, construction, expansion | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | player ship, travel, trade, mining, combat, progression | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | fleets, stations, player faction, combat depth, industry, warfare | **ACTIVE** |
| **v0.5 RPG & Living World** | world generation, discovery, NPC, missions, reputation | PLANNED |
| **v0.6 Content & Balance Alpha** | technology/content breadth + long-horizon balance | PLANNED |
| **v0.7 Polish / RC** | UX, onboarding, performance, save hardening | PLANNED |

На текущем roadmap завершены Stages **0–17**, включая финальный **Stage 17H persistence/migration/end-to-end transition gate**. Следующий runtime slice — **Stage 17.5A: production material/hull/module schema**.

## Что уже реализовано

### Живая экономика

- deterministic simulation time;
- integer milli-credit accounting и conserved transfers;
- физические inventories и рынки;
- потребление, производство и finite-resource mining;
- data-driven товары, рецепты и контент;
- динамические цены, спрос и предложение;
- межсистемная торговля и логистика;
- economic shocks/recovery;
- long-horizon headless acceptance и invariants.

Ключевой принцип:

> Деньги, груз, производственная мощность и обычные экономические последствия не появляются из player-only или AI-only shortcuts.

### Галактика и автономные фракции

- `Galaxy → Sector → StarSystem` hierarchy;
- физические межсистемные перемещения и jump transit;
- autonomous faction treasury/economy;
- physical construction/destruction;
- inter-system supply chains;
- autonomous expansion;
- persistent territorial, diplomatic and access state.

### Игрок и корабль

- persistent `PlayerState` и ownership;
- direct ship control;
- docking/travel/navigation;
- manual trade through shared economy;
- mining;
- ship progression;
- combat vertical slice;
- authoritative save/load.

### Флоты и станции

- persistent multiple `FleetId`;
- fleet orders;
- shared inertial movement;
- physical fleet trade/mining;
- threat/risk context;
- global fleet map;
- ordinary jump FSM;
- real construction sites, wallets, material delivery and build time;
- remote construction continuation;
- persistent player-owned completed stations;
- destruction without free replacement.

### Собственная фракция игрока — Stage 17 COMPLETE

Stage 17 переводит независимого владельца Stage-15/16 assets в обычного faction actor без параллельной player-only simulation.

Реализованы:

- dynamic player-created faction identity;
- id-preserving affiliation existing fleets/stations;
- separate personal wallet and public treasury;
- conserved capitalization;
- presence / claim / stabilization / control / recognition / concession as separate legal states;
- treaties, embargoes and effective market access;
- doctrine;
- fiscal policy;
- stock/production policy;
- resilience policy;
- persistent anti-oscillation policy review;
- player/AI command parity;
- faction-management read model;
- strategic global-map projection;
- shared management command facade.

Stage 17H дополнительно доказал полный переход через реальный binary persistence boundary:

```text
independent player with Stage-16 fleet + station
→ found faction
→ affiliate same physical IDs
→ conserved capitalization
→ common policy
→ ordinary fiscal reaction
→ legal diplomacy / access / territory
→ binary save/load
→ deterministic re-save
→ no duplication/reset/resources created
```

Pre-Stage17 migration также покрыта historical Stage-16 fixture без выдуманного player faction или политических/экономических grants.

## Persistence

Актуальная архитектура описана в [`docs/persistence_model.md`](docs/persistence_model.md).

Current nested versions:

- local `GameState` schema **v3**;
- strategic `WorldState` schema **v9**, world file format **v8**;
- `PlayableWorldState` schema **v5**, playable file format **v1**.

`PlayableWorldState` остаётся v5 осознанно: Stage 17 не добавил новых serialized `PlayerState` fields; institutional faction state versioned внутри `WorldState`.

## Архитектурные инварианты

1. **Игрок и AI используют общие authoritative systems**, где это практически возможно.
2. **Simulation state отделён от presentation/UI.**
3. **Обычные movement/construction/refit/production требуют времени и физических ресурсов.**
4. **Нет passive income вместо реальных transfers.**
5. **Нет virtual deliveries или hidden resource grants.**
6. **Persistent identity переживает materialization, affiliation, ownership changes и save/load.**
7. **Determinism и conservation покрываются acceptance tests.**
8. **Производительность не достигается второй fake economy для distant world.** Используются simulation LOD, scheduled/event-driven transitions и deterministic materialization.

Scalability contract: [`docs/simulation_scalability_architecture.md`](docs/simulation_scalability_architecture.md).

## Технологии

- **Java 17**;
- **libGDX 1.14.2**;
- **Ashley ECS 1.7.4**;
- **VisUI 1.5.9**;
- **LWJGL3 desktop backend**;
- **Maven Wrapper**;
- **JUnit 5**;
- **JaCoCo**;
- GitHub Actions CI.

Основной код находится в `src/main/java/com/spacesim` и разделён на domain/runtime/UI boundaries.

## Требования и запуск

Нужен **JDK 17**. Maven Wrapper уже находится в репозитории.

### Windows

```powershell
.\run.cmd
```

Только сборка:

```powershell
.\run.cmd --build-only
```

Полная verification:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
```

### Linux / macOS

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

После сборки desktop JAR:

```text
target/star-empires-1.0-SNAPSHOT-all.jar
```

Запуск:

```bash
java -jar target/star-empires-1.0-SNAPSHOT-all.jar
```

macOS:

```bash
java -XstartOnFirstThread -jar target/star-empires-1.0-SNAPSHOT-all.jar
```

## Verification и CI

Основной gate — `clean verify`, который включает compile, JUnit, JaCoCo, Javadoc и desktop packaging.

Текущие JaCoCo thresholds:

- не менее **70% line coverage**;
- не менее **60% branch coverage**.

После проверки доступны:

- `target/site/jacoco/index.html`;
- `target/site/apidocs/index.html`;
- `target/surefire-reports/`;
- `target/star-empires-1.0-SNAPSHOT-all.jar`.

## Development / merge gate

Пока `main` не защищён автоматическим branch protection, обязателен ручной gate:

```text
branch from exact green main
→ clean verify on exact PR head
→ inspect exact diff/head SHA
→ merge that exact SHA only
→ post-merge CI on exact new main SHA
```

## Ключевая документация

- [`docs/development_roadmap.md`](docs/development_roadmap.md) — authoritative roadmap;
- [`docs/economic_invariants.md`](docs/economic_invariants.md) — economic invariants;
- [`docs/simulation_time_model.md`](docs/simulation_time_model.md) — simulation time;
- [`docs/persistence_model.md`](docs/persistence_model.md) — persistence architecture;
- [`docs/simulation_scalability_architecture.md`](docs/simulation_scalability_architecture.md) — large-world scalability;
- [`docs/ai_behavior_roadmap.md`](docs/ai_behavior_roadmap.md) — AI behavior;
- [`docs/stage15_player_fleets.md`](docs/stage15_player_fleets.md) — player fleets;
- [`docs/stage16_player_construction.md`](docs/stage16_player_construction.md) — player construction/stations;
- [`docs/stage17_player_faction.md`](docs/stage17_player_faction.md) — Stage-17 faction foundation;
- [`docs/stage17g_faction_management_completion_record.md`](docs/stage17g_faction_management_completion_record.md) — Stage 17G;
- [`docs/stage17h_persistence_transition_completion_record.md`](docs/stage17h_persistence_transition_completion_record.md) — Stage 17H final gate;
- [`docs/ship_mathematics_v1_0_design_baseline.md`](docs/ship_mathematics_v1_0_design_baseline.md) — accepted Ship Mathematics v1.0;
- [`docs/stage17_5_combat_depth_implementation_plan.md`](docs/stage17_5_combat_depth_implementation_plan.md) — next runtime implementation;
- [`docs/stage18_resources_industry_infrastructure_plan.md`](docs/stage18_resources_industry_infrastructure_plan.md) — resource/industry ontology;
- [`docs/stage20_physical_world_generation_plan.md`](docs/stage20_physical_world_generation_plan.md) — world generation;
- [`docs/stage22_content_balance_plan.md`](docs/stage22_content_balance_plan.md) — content/balance alpha.

## Ближайшая последовательность

```text
Stage 17 COMPLETE
→ Stage 17.5A material/hull/module schema
→ Stage 17.5B–I combat/fitting foundation
→ Stage 18 resources/industry/infrastructure
→ Stage 19 warfare
→ Stage 20 world generation
→ Stage 21 living world
→ Stage 22 content/balance
→ Stage 23 RC
```

## Лицензия

См. [`LICENSE`](LICENSE).
