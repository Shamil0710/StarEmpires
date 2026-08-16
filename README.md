# Star Empires

**Star Empires** — разрабатываемая 2D top-down космическая sandbox/RPG/strategy на Java и libGDX с живой физической экономикой, автономными фракциями и миром, который продолжает существовать независимо от игрока.

Проект строится не как набор отдельных «игровых режимов», а как единая симуляция. Игрок, AI, флоты, станции и фракции по возможности используют одни и те же физические, экономические, логистические, политические и боевые правила.

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

**Последняя синхронизация README: 2026-08-16.**

Канонический статус разработки хранится в [`docs/development_roadmap.md`](docs/development_roadmap.md).

| Milestone | Цель | Статус |
| --- | --- | --- |
| **v0.1 Economic Sandbox** | deterministic economic core | **COMPLETE** |
| **v0.2 Living Galactic Economy** | multi-system factions, logistics, construction, expansion | **COMPLETE** |
| **v0.3 Playable Space Sandbox** | player ship, travel, trade, mining, combat, progression | **COMPLETE** |
| **v0.4 Fleet & Empire Sandbox** | fleets, stations, player faction, combat depth, warfare | **ACTIVE** |
| **v0.5 RPG & Living World** | world generation, discovery, NPC, missions, reputation | PLANNED |
| **v0.6 Content & Balance Alpha** | technology/content breadth + long-horizon balance | PLANNED |
| **v0.7 Polish / RC** | UX, onboarding, performance, save hardening | PLANNED |

На текущем `main` завершены Stages **0–16** и **17A–17G**. Следующий обязательный этап — **Stage 17H: persistence / migration / Stage-17 end-to-end gate**.

Stage 17.5 (Combat Depth / Ship Fitting Foundation) уже имеет исследовательскую и design-базу, но runtime-реализация остаётся заблокированной до успешного завершения 17H.

## Что уже реализовано

### Живая экономика

- deterministic simulation time;
- integer milli-credit accounting и conserved transfers;
- физические inventories и рынки;
- потребление, производство и finite-resource mining;
- data-driven товары, рецепты и контент;
- динамические цены, спрос и предложение;
- межсистемная торговля и логистика;
- экономические shocks и восстановление;
- long-horizon headless acceptance и invariants.

Ключевой принцип:

> Деньги, груз, производственная мощность и обычные экономические последствия не должны появляться из player-only или AI-only shortcuts.

### Галактика и автономные фракции

- `Galaxy → Sector → StarSystem` hierarchy;
- физические межсистемные перемещения и jump transit;
- автономные faction treasuries и economy;
- строительство и уничтожение физических объектов;
- межсистемные supply chains;
- автономное расширение фракций;
- persistent territorial, diplomatic и access state.

### Игрок и корабль

- persistent `PlayerState` и ownership;
- прямое управление кораблём;
- docking, travel и navigation;
- ручная торговля через общие экономические правила;
- mining;
- ship progression;
- combat vertical slice;
- сохранение/загрузка authoritative state.

### Флоты и станции

- несколько собственных persistent `FleetId`;
- fleet orders;
- shared inertial movement;
- физическая торговля и добыча флотами;
- threat/risk context;
- global fleet map;
- ordinary jump state machine;
- строительство собственных станций;
- реальные construction sites, wallets, material delivery и build time;
- remote continuation construction;
- ownership reconciliation и destruction без бесплатной замены объекта.

### Собственная фракция игрока

Stage 17A–17G реализует переход игрока из независимого владельца кораблей/станций в обычного faction actor без создания параллельной player-only симуляции:

- dynamic player-created faction identity;
- affiliation существующих физических assets без замены их ID;
- разделение personal/company wallet и public treasury;
- conserved capitalization;
- presence / claim / stabilization / control / recognition / concession как разные legal states;
- treaties и embargo;
- legal market access и customs consequences;
- doctrine;
- fiscal policy;
- stock/production policy;
- resilience policy;
- policy feedback и anti-oscillation;
- player/AI command parity;
- faction-management read model;
- strategic global-map projection;
- shared management command facade.

Следующий Stage 17H проверит всю эту цепочку через migrations, pre-Stage17 save compatibility и полный end-to-end round trip.

## Архитектурные инварианты

Проект придерживается нескольких жёстких правил:

1. **Игрок и AI используют общие authoritative systems**, где это практически возможно.
2. **Simulation state отделён от presentation/UI.** UI читает projections/read models и не должен напрямую мутировать мир.
3. **Обычные перемещения, строительство, refit и производство требуют времени и физических ресурсов.**
4. **Нет passive income вместо реальных transfer flows.**
5. **Нет virtual deliveries или скрытых resource grants.**
6. **Persistent identity должна переживать materialization, ownership changes и save/load.**
7. **Determinism и conservation проверяются автоматическими acceptance tests.**
8. **Производительность не должна достигаться ценой второй упрощённой экономики для далёкого мира.** Масштабирование проектируется через simulation LOD, event-driven transitions, aggregation и deterministic materialization.

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

Основной код находится в `src/main/java/com/spacesim` и разделён на domain/runtime/UI boundaries. В проекте уже существуют отдельные области для economy, combat, content, simulation, persistence, player/faction services, UI/read models и benchmark/acceptance infrastructure.

## Требования и быстрый старт

Нужен **JDK 17**. Отдельно устанавливать Maven не требуется — репозиторий содержит Maven Wrapper.

Для desktop-приложения необходим графический сеанс с поддержкой OpenGL.

### Windows

Самый простой запуск:

```powershell
.\run.cmd
```

Скрипт проверяет Java, выполняет сборку и тесты через Maven Wrapper и запускает shaded `-all.jar`.

Только сборка без запуска окна:

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

После сборки самодостаточное desktop-приложение находится в:

```text
target/star-empires-1.0-SNAPSHOT-all.jar
```

Запуск:

```bash
java -jar target/star-empires-1.0-SNAPSHOT-all.jar
```

На macOS LWJGL graphics loop необходимо запускать в первом потоке:

```bash
java -XstartOnFirstThread -jar target/star-empires-1.0-SNAPSHOT-all.jar
```

## Verification и CI

Основной локальный gate:

```text
clean verify
```

Он включает компиляцию, JUnit, JaCoCo, Javadoc и packaging desktop JAR.

Текущие JaCoCo thresholds:

- не менее **70% line coverage**;
- не менее **60% branch coverage**.

Тонкие OpenGL/LWJGL boundaries исключены из числового coverage gate и проверяются отдельным graphics/desktop smoke path; чистая логика geometry, projection, hit-test и simulation остаётся headless-testable.

После успешной проверки доступны:

- `target/site/jacoco/index.html` — coverage report;
- `target/site/apidocs/index.html` — Javadoc;
- `target/surefire-reports/` — результаты тестов;
- `target/star-empires-1.0-SNAPSHOT-all.jar` — desktop application.

GitHub Actions выполняет Java-17 verification и публикует build/test/report artifacts.

## Development / merge gate

Поскольку branch protection пока не является достаточным автоматическим барьером, проект использует обязательный ручной merge gate:

```text
branch from exact green main
→ clean verify on exact PR head
→ inspect exact diff/head SHA
→ merge that exact SHA only
→ post-merge CI on exact new main SHA
```

Изменения roadmap-уровня должны синхронизировать документацию и сохранять существующие architectural invariants.

## Ключевая документация

- [`docs/development_roadmap.md`](docs/development_roadmap.md) — authoritative status/dependency roadmap;
- [`docs/economic_invariants.md`](docs/economic_invariants.md) — экономические invariants;
- [`docs/simulation_time_model.md`](docs/simulation_time_model.md) — модель времени;
- [`docs/persistence_model.md`](docs/persistence_model.md) — persistence contract;
- [`docs/ai_behavior_roadmap.md`](docs/ai_behavior_roadmap.md) — AI behavior roadmap;
- [`docs/stage15_player_fleets.md`](docs/stage15_player_fleets.md) — player fleets;
- [`docs/stage16_player_construction.md`](docs/stage16_player_construction.md) — player construction/stations;
- [`docs/stage17_player_faction.md`](docs/stage17_player_faction.md) — player faction foundation;
- [`docs/stage17g_faction_management_completion_record.md`](docs/stage17g_faction_management_completion_record.md) — Stage 17G closeout;
- [`docs/ship_mathematics_v1_0_design_baseline.md`](docs/ship_mathematics_v1_0_design_baseline.md) — accepted ship mathematics design baseline;
- [`docs/ship_hull_module_and_fleet_doctrine.md`](docs/ship_hull_module_and_fleet_doctrine.md) — hull/module/fleet doctrine;
- [`docs/stage17_5_combat_depth_implementation_plan.md`](docs/stage17_5_combat_depth_implementation_plan.md) — будущая Stage 17.5 implementation sequence;
- [`docs/stage19_physical_world_generation_plan.md`](docs/stage19_physical_world_generation_plan.md) — physical world generation;
- [`docs/stage21_content_balance_plan.md`](docs/stage21_content_balance_plan.md) — content/balance alpha.

Исторические roadmap snapshots находятся в `docs/archive/` и не должны использоваться как текущий статус проекта.

## Ближайшая последовательность разработки

```text
Stage 17H migration/end-to-end acceptance
→ Stage 17 COMPLETE
→ Stage 17.5A schema/material/hull/module
→ Stage 17.5 Combat Depth / Ship Fitting Foundation
→ Stage 18 strategic warfare
→ Stage 19–22 living world, content/balance and polish
```

До завершения **Stage 17H** нельзя перескакивать непосредственно к Stage 17.5 или Stage 18.

## Лицензия

См. [`LICENSE`](LICENSE).
