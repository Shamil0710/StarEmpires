# Star Empires — Development Roadmap

> Главный статусный и плановый документ разработки.
>
> Этот файл является единственным источником истины для последовательности этапов, Definition of Done, активного milestone и правил перехода между этапами.
>
> Последнее обновление: 2026-08-13.

## 1. Цель проекта

**Star Empires** — космическая sandbox-RPG/strategy с живой экономикой и миром, который продолжает существовать независимо от игрока.

Целевая петля развития игрока:

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

Ключевой принцип проекта: **игрок и AI по возможности используют одни и те же физические правила мира**. Игрок не получает отдельной «игровой экономики», а действует внутри той же системы ресурсов, рынков, логистики, строительства, боя и фракционной политики, что и остальные акторы.

---

## 2. Зафиксированный технологический стек

Текущий production stack проекта:

- Java 17;
- libGDX / LWJGL3;
- Ashley ECS для локальных runtime entities;
- Maven Wrapper;
- JUnit;
- JaCoCo;
- GitHub Actions;
- data-driven JSON content catalog;
- deterministic fixed-tick simulation;
- versioned binary persistence.

**Stage 8.5 завершён с решением `KEEP_LIBGDX`.** Production presentation stack зафиксирован как Java 17 + libGDX 1.14.2 + LWJGL3, Ashley 1.7.4 и VisUI 1.5.9. Решение основано на real-GPU Representative/Tactical/Close-up validation, production-like heavy-corvette asset pipeline и зелёном Java-17 CI. Повторная миграция рассматривается только при появлении нового измеримого фундаментального ограничения.

---

## 3. Общая карта milestones

| Milestone | Цель | Основные этапы | Статус |
| --- | --- | --- | --- |
| **v0.1 Economic Sandbox** | доказать корректность и масштабируемость экономического core | Stage 0–6 | COMPLETE |
| **v0.2 Living Galactic Economy** | подтвердить presentation stack, получить несколько живых систем, реальные фракции, строительство, межсистемную логистику и автономное расширение | Stage 7–11, включая Stage 8.5 | ACTIVE |
| **v0.3 Playable Space Sandbox** | превратить симуляцию в игру: игрок, путешествия, торговля, добыча и базовый бой | Stage 12–14 | PLANNED |
| **v0.4 Fleet & Empire Sandbox** | дать игроку собственные флоты, станции, фракцию и стратегическую войну | Stage 15–18 | PLANNED |
| **v0.5 RPG & Living World** | исследования, NPC, миссии, события и персональная прогрессия | Stage 19–20 | PLANNED |
| **v0.6 Content & Balance Alpha** | расширить контент и доказать длительную устойчивость полной игры | Stage 21 | PLANNED |
| **v0.7 Polish / Release Candidate** | UX, onboarding, performance, save compatibility, release quality | Stage 22 | PLANNED |

Параллельно основному roadmap развивается **Visual / UX Track**, описанный ниже. Он не должен нарушать последовательность core-этапов и не должен подменять функциональные Definition of Done.

---

# MILESTONE v0.1 — ECONOMIC SANDBOX

**Статус:** COMPLETE, кроме организационного branch-protection пункта.

Цель milestone: надёжный, воспроизводимый экономический sandbox, который можно прогонять без UI сотни игровых часов и использовать как фундамент для галактики, фракций, строительства и боя.

Критерии:

- [x] проект стабильно собирается из чистого clone одной командой;
- [ ] обязательная branch protection для `main` — CI зелёный, но branch protection не настроена доступным connector API;
- [x] fixed simulation tick и pause/time scale;
- [x] deterministic seed;
- [x] физическое сохранение товаров;
- [x] двусторонняя передача денег без скрытой эмиссии в обычной торговле;
- [x] save/load с устойчивыми ID;
- [x] data-driven товары, рецепты, корабли, станции и фракции;
- [x] route planning по экономической отдаче на единицу времени;
- [x] benchmark 100 stations / 500 economic agents / 100 simulated hours;
- [x] quantitative observability и supply-chain failure detection.

---

## Stage 0 — Repository health и зелёная сборка

**Статус:** COMPLETE — MERGED TO `main` VIA PR #1

### Основные результаты

- [x] восстановлены отсутствующие asteroid-классы и устранены compile errors;
- [x] `./mvnw clean verify` проходит из чистого clone на JDK 17;
- [x] JUnit, Javadoc `failOnWarnings=true`, JaCoCo и runnable shaded JAR входят в CI;
- [x] `actions/setup-java` обновлён до v5;
- [x] feature/fix-ветки используются как рабочий контур, `main` — стабильная база;
- [ ] desktop OpenGL smoke-check остаётся ручным release-checklist и не блокирует core DoD.

### Definition of Done

Свежий clone выполняет `./mvnw clean verify`, создаёт runnable `-all.jar`, автоматические проверки зелёные. **Выполнено.**

---

## Stage 1 — SimulationClock и детерминированное игровое время

**Статус:** COMPLETE — MERGED TO `main` VIA PR #2

### Основные результаты

- [x] `SimulationClock` с fixed step `0.1s`;
- [x] render delta отделён от simulation delta;
- [x] pause/time scale не меняют размер simulation tick;
- [x] события и новости работают на game time;
- [x] deterministic `SimulationRandom` с именованными RNG streams;
- [x] определён явный порядок simulation systems;
- [x] одинаковое число fixed ticks даёт одинаковый результат независимо от FPS.

### Definition of Done

Одинаковый initial state + seed + количество simulation ticks приводит к одинаковому экономическому состоянию независимо от render-frame pattern. **Выполнено.**

---

## Stage 2 — Деньги и экономические инварианты

**Статус:** COMPLETE — MERGED TO `main` VIA PR #3

### Основные результаты

- [x] authoritative деньги переведены на `long` milli-credits;
- [x] добавлен `WalletComponent`;
- [x] торговля двусторонняя и атомарная;
- [x] станции имеют конечную ликвидность;
- [x] введены `EconomicTransaction` и `EconomicLedger`;
- [x] resource source / sink / transform отделены от transfer;
- [x] добыча, производство, потребление и spawn отражаются в ledger;
- [x] legacy authoritative `float credits` APIs удалены;
- [x] regression tests покрывают conservation, atomicity и ledger determinism.

### Definition of Done

Обычная торговля не создаёт и не уничтожает деньги или товар. Любое создание или уничтожение ресурса имеет явный economic transaction type. **Выполнено.**

---

## Stage 3 — EntityId и сохранения

**Статус:** COMPLETE — MERGED TO `main` VIA PR #4

### Основные результаты

- [x] устойчивый `EntityId`;
- [x] общий runtime `EntityRegistry`;
- [x] persistent references переведены на ID;
- [x] versioned `GameState`;
- [x] bounded deterministic `GameStateCodec`;
- [x] safe file replacement;
- [x] сохранение clock, RNG streams, events, asteroid spawner, price recorder и ledger;
- [x] `SimulationSession` snapshot/restore/save/load без OpenGL;
- [x] continuation tests.

### Definition of Done

`simulate(A) -> save/load -> simulate(B)` эквивалентно `simulate(A+B)` в пределах определённых инвариантов. **Выполнено.**

---

## Stage 4 — Data-driven контент

**Статус:** COMPLETE — MERGED TO `main` VIA PR #5

### Основные результаты

- [x] versioned validated JSON `ContentCatalog`;
- [x] товары, рецепты, фракции, ship archetypes и station archetypes описываются данными;
- [x] stable string content IDs отделены от dense runtime IDs;
- [x] runtime item capacity не привязана к enum count;
- [x] market/trade получают metadata из каталога;
- [x] generic `ArchetypeEntityFactory`;
- [x] content fingerprint;
- [x] content-bound save envelope;
- [x] миграция legacy saves;
- [x] fail-fast validation ссылок и role-specific parameters.

### Definition of Done

Новый товар, рецепт, ship/station archetype можно добавить через данные без изменения simulation-кода. **Выполнено.**

---

## Stage 5 — Логистика и Trade Route Planner

**Статус:** COMPLETE — MERGED TO `main` VIA PR #6

### Основные результаты

- [x] route discovery отделён от ship FSM;
- [x] `MarketDirectory`;
- [x] immutable/value-object trade planning state;
- [x] pure `TradeRoutePlanner`;
- [x] scoring по net profit / travel time;
- [x] учитываются stock, спрос, capacity, specialization, liquidity;
- [x] `TradeRouteCostModel` оставляет seam для fuel/risk/tariffs;
- [x] bounded candidate shortlist;
- [x] stale-route/replan policy;
- [x] deterministic tie-breaks;
- [x] performance regression.

### Definition of Done

Trade AI выбирает экономически лучший локальный маршрут без глобального квадратичного перебора каждым агентом. **Выполнено.**

---

## Stage 6 — Headless benchmark и observability

**Статус:** COMPLETE — Stage 6A MERGED VIA PR #7; Stage 6B MERGED VIA PR #8

### Основные результаты

- [x] headless benchmark runner;
- [x] deterministic scenarios;
- [x] world 100 stations / 500 agents;
- [x] full `scale100h`: 3 600 000 fixed ticks;
- [x] performance/heap metrics;
- [x] stockouts/unmet demand;
- [x] price/trade/production/mining/resource metrics;
- [x] wealth distribution / Gini;
- [x] route profitability;
- [x] money/resource accounting checks;
- [x] machine-readable JSON reports;
- [x] profiling and confirmed hot-path optimizations;
- [x] regression thresholds;
- [x] broken supply-chain detection.

### Definition of Done

Экономический core автоматически стресс-тестируется без UI и количественно показывает корректность, производительность и деградацию supply chain. **Выполнено.**

---

# MILESTONE v0.2 — LIVING GALACTIC ECONOMY

**Статус:** ACTIVE

Цель: перейти от стабильной локальной экономики к **самоизменяющейся галактической экономике**, одновременно подтвердив до дальнейшего масштабирования, что presentation stack способен поддержать целевой визуальный уровень игры.

---

## Stage 7 — Иерархия мира и уровни симуляции

**Статус:** COMPLETE — MERGED TO `main` VIA PR #9

### Основные результаты

- [x] hierarchy `Galaxy -> Sector -> StarSystem` с typed stable IDs;
- [x] strategic planets и asteroid fields;
- [x] canonical jump connections;
- [x] deterministic topology indexes;
- [x] `WorldState`, `WorldStateCodec`, `WorldPersistence`;
- [x] legacy single-system save migration;
- [x] active system на полном fixed rate;
- [x] remote systems на coarse strategic updates;
- [x] bounded deterministic scheduler;
- [x] 2 sectors / 3 systems production demo;
- [x] единый economic core для desktop/headless/world layers.

### Definition of Done

Несколько систем экономически живут одновременно, удалённые системы не требуют full local object-level tick. **Выполнено.**

---

## Stage 8 — Фракции как экономические акторы

**Статус:** COMPLETE — MERGED TO `main` VIA PR #10

### Основные результаты

- [x] faction treasury и budget policy;
- [x] subsidies;
- [x] diplomacy/relations;
- [x] territory ownership;
- [x] market access restrictions;
- [x] stock/production policy;
- [x] military/expansion strategic demand;
- [x] taxes/tariffs;
- [x] world persistence для strategic state;
- [x] end-to-end money-conservation verification.

### Definition of Done

Фракционные решения физически изменяют спрос, производство, логистику и финансовые потоки обычной экономики. **Выполнено.**

---

## Stage 8.5 — Graphics / Technology Validation

**Статус:** COMPLETE — TECHNOLOGY DECISION `KEEP_LIBGDX`

### Итог Stage 8.5

- libGDX обновлён до `1.14.2`, VisUI до `1.5.9`, Ashley сохранён `1.7.4`;
- presentation pipeline отделён от authoritative simulation/headless core;
- production-like heavy-corvette asset contract подтверждён на real GPU;
- source-facing/hardpoint orientation normalization работает для sprite, engines и weapons;
- `OFF / IDLE / THRUST`, emissive и severe-damage layers прошли dedicated visual acceptance;
- финальный Representative после authored integration: 2560x1369, 50 ships, 500 asteroids, 2000 particles, ~2376 FPS, avg ~0.43 ms, p95 ~0.60 ms, max ~1.68 ms, 35 draw calls;
- reference GPU: NVIDIA GeForce RTX 4070, RAM 31.92 GiB, Windows 11 Pro build 26100;
- Java 17 compatibility подтверждена CI; local real-GPU run выполнялся выбранным JDK 24.0.2;
- final decision record: `docs/stage8_5_technology_decision.md`.

Dedicated production bloom не блокирует gate: capability доказана emissive/additive/FBO path, а configurable `BloomMode` запланирован в последующих visual stages.

### Цель

До дальнейшего расширения simulation/gameplay core доказать на работающем vertical slice, что текущий Java + libGDX/LWJGL3 presentation stack способен обеспечить целевой визуальный уровень Star Empires и требуемую производительность для большой 2D top-down космической sandbox.

Stage не является косметическим polish-pass. Это **technology decision gate**: после него либо libGDX подтверждается как production rendering framework, либо создаётся отдельное архитектурное решение о миграции с измеримыми доказательствами ограничения текущего стека.

### Главный принцип

Simulation core не должен зависеть от конкретного renderer, sprite asset или VFX implementation.

```text
Simulation / WorldState / Ashley ECS
                ↓
       Presentation snapshot/view
                ↓
          RenderPipeline
                ↓
 ┌──────────────┼──────────────┐
 ↓              ↓              ↓
Sprites       VFX/PostFX    Scene2D UI
```

Renderer читает состояние мира, но не становится authoritative owner gameplay state.

### Stage 8.5A — Dependency и compatibility validation

Текущий baseline репозитория на момент планирования:

- Java 17;
- libGDX `1.12.1`;
- Ashley `1.7.4`;
- VisUI `1.5.2`;
- LWJGL3 backend.

Целевой spike:

- [ ] проверить обновление libGDX `1.12.1 -> 1.14.2`;
- [ ] проверить обновление VisUI `1.5.2 -> 1.5.9`;
- [ ] Ashley `1.7.4` оставить без изменения, если compatibility audit не выявит причины для замены;
- [ ] `./mvnw clean verify` остаётся зелёным;
- [ ] существующие headless simulation tests не получают OpenGL dependency;
- [ ] desktop launcher проходит smoke-check;
- [ ] save/load и content fingerprint semantics не меняются из-за presentation upgrade;
- [ ] зафиксировать migration notes для breaking/deprecated APIs.

Обновление библиотек не считается самоцелью: версия принимается только после regression и desktop validation.

### Stage 8.5B — Presentation architecture

Ввести минимальную структуру presentation layer, достаточную для дальнейшего роста без создания нового engine внутри игры.

Предпочтительные responsibilities:

- `RenderPipeline` — порядок render passes;
- `WorldRenderer` — orchestration world presentation;
- `SpriteRenderer` — ships/stations/asteroids;
- `ParticleRenderer` — engines/explosions/debris;
- `LightingRenderer` или emissive pass — glow/lights;
- `PostProcessRenderer` — framebuffer/post effects;
- `UiRenderer` / Scene2D boundary — HUD и panels.

#### Требования

- [ ] simulation systems не вызывают rendering APIs;
- [ ] renderer не мутирует authoritative economic/gameplay state;
- [ ] Ashley `Entity` не обязан быть presentation object;
- [ ] presentation использует stable archetype/entity identity там, где нужна привязка asset/effect;
- [ ] visual interpolation допускается между fixed simulation ticks;
- [ ] render order/layers определены явно;
- [ ] camera zoom не меняет simulation semantics;
- [ ] rendering может быть полностью отключён для headless benchmark.

### Stage 8.5C — Ship sprite и asset pipeline

Проверить production-подход на реальном корабельном sprite, а не на геометрическом placeholder.

Минимальный asset contract:

```text
ship_base.png
ship_emissive.png      optional
ship_damage.png        optional
ship_normal.png        optional / experimental
engine animation
presentation metadata
```

#### Зафиксировать

- [ ] world-units-to-pixels / sprite scale convention;
- [ ] pivot/origin convention;
- [ ] small / medium / large size grammar;
- [ ] texture-atlas policy;
- [ ] engine hardpoints;
- [ ] weapon hardpoint seam;
- [ ] faction tint/markings seam;
- [ ] collision footprint не выводится неявно из прозрачных краёв PNG;
- [ ] asset ID является presentation metadata поверх archetype, а не gameplay identity;
- [ ] missing asset имеет безопасный fallback/debug representation.

### Stage 8.5D — Engine animation и emissive rendering

На одном production-like ship реализовать минимум:

- [ ] idle engine state;
- [ ] thrust animation;
- [ ] интенсивность engine glow зависит от реального movement/thrust state;
- [ ] emissive engine/windows pass;
- [ ] additive glow;
- [ ] optional maneuver/reverse thruster seam;
- [ ] animation timing не использует simulation RNG;
- [ ] visual-only animation не влияет на determinism gameplay state.

### Stage 8.5E — Visual Technology Spike scene

Создать отдельную reproducible desktop scene/режим, демонстрирующий одновременно:

- [ ] реальный ship sprite;
- [ ] несколько визуально различимых размеров/ролей кораблей или масштабирование одного test set;
- [ ] десятки ships;
- [ ] сотни asteroids;
- [ ] parallax star field;
- [ ] nebula/background layer;
- [ ] animated engine exhaust;
- [ ] emissive/additive engine glow;
- [ ] projectile weapon;
- [ ] beam/laser effect;
- [ ] explosion particles;
- [ ] shield-hit effect;
- [ ] debris/salvage visual seam;
- [ ] bloom или эквивалентный framebuffer post-effect;
- [ ] screen-space distortion/heat/shockwave prototype, если реализация остаётся достаточно дешёвой;
- [ ] damage overlay prototype;
- [ ] production-like sci-fi HUD fragment;
- [ ] плавный zoom от tactical ship view к обзорному системному масштабу.

Эффекты этого spike не обязаны становиться финальным art direction. Их задача — доказать rendering capabilities и сформировать reusable pipeline.

### Stage 8.5F — Rendering observability и performance baseline

Visual spike должен измеряться так же дисциплинированно, как economic core.

Собирать/показывать минимум:

- FPS;
- CPU frame time;
- GPU frame time, если доступен надёжный measurement path;
- render calls / draw calls;
- sprites/objects rendered;
- active particles;
- texture bindings или batch flushes, если метрика доступна;
- framebuffer/post-process pass count;
- JVM heap delta;
- viewport/resolution;
- reference hardware/OS/GPU.

#### Minimum representative scene

Для repeatable baseline использовать не меньше:

- 50 ships;
- 500 asteroids/background objects;
- 2 000 active particles в пиковом эффекте;
- emissive/glow pass;
- минимум один post-process pass;
- HUD.

Целевой ориентир — **60 FPS при 1920x1080 на зафиксированной reference developer machine**, но итоговый gate принимается по frame-time evidence и визуальному результату, а не по одной магической цифре. Stress profile с более высоким object/particle count измеряется отдельно и не обязан быть release gate Stage 8.5.

### Stage 8.5G — Technology decision record

По итогам spike создать документ вроде `docs/stage8_5_graphics_validation.md` или ADR, который фиксирует:

1. проверенные версии libraries;
2. архитектуру renderer;
3. screenshots/описание реализованных capabilities;
4. measured baseline;
5. найденные ограничения;
6. стоимость дальнейшего развития;
7. решение `KEEP_LIBGDX` или `MIGRATION_RECOMMENDED`;
8. если migration recommended — конкретный кандидат и причины, почему ограничение невозможно/нерационально решить внутри libGDX/LWJGL3.

### Engine decision rule

**Оставить libGDX**, если spike подтверждает нужный визуальный результат, batching/render performance и возможность построения необходимых shader/FBO/VFX layers без нарушения simulation architecture.

Рассматривать миграцию только если обнаружено фундаментальное ограничение framework, а не отсутствие готового эффекта, shader или asset pipeline.

Если gate не пройден, кандидат сравнивается отдельным prototype/evidence pass. Приоритетные направления для сравнения:

- FXGL — если главной проблемой окажется high-level 2D/UI tooling;
- jMonkeyEngine — если подтверждён переход к преимущественно 3D presentation;
- прямой LWJGL — только если доказана необходимость низкоуровневого renderer, которую невозможно разумно закрыть libGDX.

### Acceptance scenario

```text
current simulation snapshot
        ↓
presentation layer
        ↓
production ship sprites + engines
        ↓
large 2D scene
        ↓
particles + emissive + post-processing + HUD
        ↓
measured frame/render metrics
        ↓
written technology decision
```

### Definition of Done

Stage 8.5 завершён, когда:

- dependency upgrade/compatibility decision проверен реальным build + desktop smoke;
- headless simulation остаётся полностью отделена от OpenGL;
- существует reusable presentation/render pipeline вместо дальнейшего роста монолитного debug renderer;
- production-like ship sprite и engine animation работают через установленный asset contract;
- visual technology scene демонстрирует sprites, масштаб, particles, emissive/glow, weapons, shield/explosion feedback, post-processing и HUD;
- performance baseline сохранён вместе с reference hardware и scene parameters;
- целевая визуальная концепция признана достижимой на выбранном stack либо документировано доказано обратное;
- technology decision record явно фиксирует дальнейший stack;
- только после этого **Stage 9 становится ACTIVE**.

### Рекомендуемое PR-разбиение

1. dependency upgrade + compatibility regression;
2. presentation/render-pipeline boundary;
3. sprite/atlas/hardpoint asset pipeline;
4. engine animation + particles + emissive pass;
5. framebuffer/post-processing + HUD spike;
6. visual performance baseline + technology decision document.

---

## Stage 9 — Dynamic Economy: строительство, lifecycle и воспроизводство экономики

**Статус:** COMPLETE — Stage 9E acceptance passed

Текущий implementation focus: **Stage 10A — Fleet identity на world level**.

### Цель

Сделать экономику способной менять собственную производственную структуру: строить новые станции, удалять уничтоженные объекты, обнаруживать производственные bottlenecks и восстанавливать supply chain без scripted respawn.

### Архитектурный принцип

Строительство не является `spawnStation()` после таймера.

Construction project должен быть **физическим экономическим потребителем**, который:

1. имеет owner и persistent ID;
2. требует data-driven набор материалов;
3. имеет funding;
4. создаёт реальный market demand;
5. получает материалы через существующую логистику;
6. завершает объект только после фактического material fulfillment;
7. не создаёт товары или деньги из ничего.

### Stage 9A — Entity lifecycle infrastructure

**Статус:** COMPLETE — PR #13 candidate; `docs/stage9a_entity_lifecycle.md`

#### Задачи

- [x] определить authoritative lifecycle persistent local entities: create/register/structural disable-for-removal/unregister; экономическое destroy непустых assets остаётся Stage 9C;
- [x] обеспечить deterministic allocation `EntityId` для созданных runtime объектов;
- [x] добавить безопасный removal path для stations и ships;
- [x] удаление станции инвалидирует route planner state и cached opportunities;
- [x] dangling persistent references не переживают structural removal;
- [x] `EntityRegistry`, `MarketDirectory`, spatial indexes и simulation systems согласованно видят removal;
- [x] save/load корректно сохраняет мир после runtime create/structural removal;
- [x] active и remote systems поддерживают изменяемый entity count;
- [x] destruction/removal не нарушает money/resource accounting.

#### Acceptance tests

- создать станцию -> сохранить -> загрузить -> продолжить;
- удалить станцию с активными trade routes -> simulation продолжает работу без stale access;
- удалить и восстановить несколько объектов при одинаковом seed -> deterministic state;
- structurally removed объект отсутствует после world save/load; non-empty destruction проверяется Stage 9C.

### Stage 9B — Persistent Construction Project

**Статус:** COMPLETE — PR #14 candidate; `docs/stage9b_construction_project.md`

`ConstructionProject` должен хранить как минимум:

- stable project ID;
- owner faction;
- target `StationArchetypeId`;
- target `StarSystemId`;
- location;
- required materials;
- delivered materials;
- project wallet / funding state;
- construction progress;
- state machine;
- timestamps/ticks создания и завершения.

Рекомендуемые состояния:

```text
PLANNED
  ↓
FUNDED
  ↓
AWAITING_MATERIALS
  ↓
BUILDING
  ↓
COMPLETED
```

Допустимы terminal states `CANCELLED` / `FAILED`, если они реально понадобятся.

#### Задачи

- [x] construction requirements описываются data-driven;
- [x] проект создаётся без hardcoded simulation recipe;
- [x] faction treasury физически финансирует project wallet;
- [x] project demand виден обычному market/logistics core;
- [x] поставленный материал физически перемещается из cargo/inventory в project storage;
- [x] partial delivery сохраняется;
- [x] project state сохраняется через world persistence;
- [x] completion создаёт station через существующий archetype factory;
- [x] station появляется только после material + time requirements;
- [x] cancel/refund policy явно определена и не нарушает conservation.

Для первой версии не расширять каталог ради реализма: использовать существующие ресурсы, например steel + energy, если этого достаточно для доказательства механики.

### Stage 9C — Destruction и economic shock

**Статус:** COMPLETE — PR #15; `docs/stage9c_destruction_and_economic_shock.md`

#### Задачи

- [x] production-safe API уничтожения station/ship;
- [x] destruction удаляет производственную мощность и market availability;
- [x] судьба cargo/stock при уничтожении задана явно: destroyed/salvage/transfer;
- [x] resource sink от уничтожения отражается в ledger;
- [x] destruction создаёт измеримый stockout/unmet demand;
- [x] route planner корректно реагирует на исчезнувший market;
- [x] events/news могут сообщить об экономически значимом уничтожении.

### Stage 9D — Bottleneck analysis и AI investment

**Статус:** COMPLETE — PR #17; `docs/stage9d_economic_response.md`

AI должен реагировать не на магические пороги вида `if steel < X build foundry`, а на **дефицит производственной мощности**.

Минимальные входы `EconomicBottleneckAnalyzer`:

- persistent unmet demand;
- stockout duration;
- price pressure;
- blocked production cycles;
- input shortages;
- utilization существующих producers;
- transport bottlenecks;
- региональное отсутствие production archetype.

#### Задачи

- [x] deterministic bottleneck report;
- [x] distinction между production shortage и logistics shortage;
- [x] faction investment candidate selection;
- [x] affordability/budget check;
- [x] expected economic utility / strategic utility;
- [x] anti-thrashing cooldown/hysteresis;
- [x] AI создаёт construction project через тот же API, что позднее будет использовать игрок;
- [x] исключить бесконечное строительство из-за временного demand spike.

### Stage 9E — Economic resilience benchmark

**Статус:** COMPLETE — PR #18 candidate; `docs/stage9e_economic_resilience.md`

Acceptance scenario:

```text
stable economy
    ↓
destroy critical foundry
    ↓
steel shortage
    ↓
weapons shortage / price pressure
    ↓
AI detects bottleneck
    ↓
new construction project
    ↓
materials delivered
    ↓
replacement capacity online
    ↓
supply chain recovers
```

Метрики:

- time to detect;
- time to investment decision;
- time to material fulfillment;
- time to new capacity online;
- time to recovery;
- peak unmet demand;
- peak price pressure;
- total investment;
- money conservation;
- resource accounting.

### Definition of Done

После уничтожения критической производственной станции supply chain действительно деградирует, AI обнаруживает bottleneck, физически финансирует и снабжает replacement construction, новая мощность появляется, а экономические показатели демонстрируют восстановление без scripted respawn.

### Рекомендуемое PR-разбиение

1. entity lifecycle infrastructure;
2. persistent construction state/schema;
3. physical project demand and delivery;
4. station completion/spawn;
5. destruction/removal integration;
6. bottleneck analyzer;
7. faction investment planner;
8. resilience benchmark + documentation.

---

## Stage 10 — Inter-system logistics и физическое перемещение между системами

**Статус:** ACTIVE

### Почему этот этап идёт до полноценного combat

Без межсистемной логистики галактика остаётся набором параллельных локальных экономик. Колонизация, снабжение войн, региональная специализация, blockade и настоящая экспансия требуют physical transit.

### Stage 10A — Fleet identity на world level

- [ ] определить persistent identity fleet/ship при переходе между local `SimulationSession`;
- [ ] разделить «entity находится в system» и «entity находится in transit»;
- [ ] transit state входит в `WorldState`;
- [ ] переход не дублирует корабль одновременно в двух systems;
- [ ] сохранение в середине transit безопасно.

### Stage 10B — Jump transit

Минимальная FSM:

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

- [ ] deterministic jump duration;
- [ ] jump connections используются как navigation edges;
- [ ] path невозможен без topology connection;
- [ ] transit продолжается в remote simulation;
- [ ] save/load continuation сохраняет arrival state;
- [ ] active system может меняться независимо от transit других флотов.

### Stage 10C — Galactic route planner

Расширить planning от локального:

```text
fleet -> supplier -> consumer
```

до:

```text
fleet
 -> supplier in system A
 -> jump path
 -> consumer in system B/C
```

Учитывать:

- in-system travel time;
- jump travel time;
- tariffs;
- market access;
- route risk seam;
- cargo capacity;
- expected purchase/sale amount;
- total expected route duration.

Использовать существующий `TradeRouteCostModel` как extension seam, не создавать параллельный scoring stack без необходимости.

### Stage 10D — Cross-system market discovery

- [ ] bounded market discovery across reachable topology;
- [ ] route search не масштабируется как полный перебор всех markets галактики каждым fleet;
- [ ] regional/sector indexes;
- [ ] deterministic candidate ordering;
- [ ] configurable search horizon;
- [ ] stale route invalidation при изменении topology/access/market state.

### Stage 10E — Inter-system economic benchmark

Сценарий:

- system A имеет избыток steel;
- system B испытывает steel shortage;
- jump connection доступен;
- local supply недостаточен;
- trade fleet физически переносит ресурс;
- после закрытия connection shortage возвращается;
- после восстановления connection trade возобновляется.

### Definition of Done

Ресурс физически перемещается между StarSystems через fleet transit, а route planner способен выбирать прибыльный межсистемный маршрут с учётом времени пути, доступа и тарифов.

---

## Stage 11 — Autonomous Faction Expansion

**Статус:** PLANNED

### Цель

Превратить Stage 8 `EXPANSION` из demand modifier в реальное пространственное развитие фракций.

### Stage 11A — Expansion opportunity model

Фракция анализирует:

- незанятые / слабо контролируемые systems;
- доступность ресурсов;
- distance/jump connectivity;
- существующий trade network;
- strategic threat;
- expected construction cost;
- expected demand/profit;
- diplomacy/access constraints.

### Stage 11B — Expansion plan

Persistent plan содержит:

- target system;
- strategic reason;
- anchor construction project;
- required support fleet;
- initial stock targets;
- budget;
- status.

### Stage 11C — Physical expansion execution

- [ ] faction создаёт construction project;
- [ ] материалы доставляются из существующих systems;
- [ ] новая station появляется физически;
- [ ] market начинает участвовать в экономике;
- [ ] при необходимости создаются/назначаются miners/traders;
- [ ] territory ownership меняется только по определённому правилу;
- [ ] expansion может провалиться из-за нехватки бюджета/логистики.

### Stage 11D — Competition

- [ ] несколько фракций могут оценить одну system;
- [ ] deterministic conflict resolution без combat через timing/resources/access;
- [ ] будущая военная конкуренция добавляется в Stage 18.

### Definition of Done

Хотя бы одна фракция способна самостоятельно выбрать экономически/стратегически интересную соседнюю систему, профинансировать строительство, доставить ресурсы и создать новый устойчивый экономический узел.

---

# MILESTONE v0.3 — PLAYABLE SPACE SANDBOX

**Статус:** PLANNED

Цель: впервые получить игру, в которую можно играть как в космическую sandbox, а не только наблюдать simulation core.

---

## Stage 12 — Player entity, ownership и базовое путешествие

**Статус:** PLANNED

### Stage 12A — Player state

Добавить persistent `PlayerState`:

- wallet;
- reputation;
- faction affiliation или independent status;
- owned ship IDs;
- active ship ID;
- owned station IDs — может оставаться пустым до Stage 16;
- discovered systems/objects;
- optional home/start location.

### Stage 12B — Ownership model

- [ ] entity ownership не равен faction membership автоматически;
- [ ] корабль может принадлежать игроку и иметь faction/legal context;
- [ ] ownership сохраняется;
- [ ] уничтожение owned entity корректно обновляет player state;
- [ ] покупка/продажа ownership выполняется атомарно.

### Stage 12C — Direct ship control

- [ ] выбрать один production control model: direct thrust или point-and-click movement;
- [ ] camera follow;
- [ ] selection;
- [ ] docking/undocking;
- [ ] jump travel;
- [ ] pause/time controls;
- [ ] UI безопасно взаимодействует с fixed simulation.

### Stage 12D — Player market interaction

- [ ] ручная покупка;
- [ ] ручная продажа;
- [ ] cargo UI;
- [ ] wallet UI;
- [ ] market price/stock visibility;
- [ ] market access/reputation constraints;
- [ ] те же authoritative trade/economic rules, что и AI.

### Definition of Done

Игрок начинает в одном owned ship, может перелететь между минимум двумя системами, пристыковаться, вручную купить товар, перевезти его и продать, при этом сделка проходит через тот же economic core, что и AI trade.

---

## Stage 13 — Combat Vertical Slice

**Статус:** PLANNED

### Scope discipline

Не строить сразу сложную оружейную мета-систему. Первый бой должен доказать:

- target acquisition;
- pursuit/positioning;
- range;
- damage;
- shields;
- hull;
- destruction;
- economic aftermath.

### Stage 13A — Combat state

Расширить текущий `CombatComponent` только необходимыми authoritative данными:

- hull/max hull;
- shields/max shields;
- armor — только если нужен первой модели;
- weapon slots или weapon profile;
- target;
- cooldown/fire state;
- combat affiliation/hostility.

### Stage 13B — Weapons

Data-driven weapon archetype минимум:

- damage;
- rate/cooldown;
- range;
- projectile/instant-hit model;
- damage application rule.

Не вводить damage types, десятки weapons, crew systems и сложную модульность до доказательства loop.

### Stage 13C — Combat AI

Минимальные состояния:

```text
PATROL / IDLE
  ↓
ACQUIRE_TARGET
  ↓
APPROACH
  ↓
ENGAGE
  ↓
DISENGAGE / DESTROYED
```

### Stage 13D — Destruction and salvage

- [ ] ship destruction проходит Stage-9 lifecycle;
- [ ] inventory/cargo fate задана явно;
- [ ] salvage создаётся физически, если выбран этот дизайн;
- [ ] потеря ship создаёт replacement demand;
- [ ] weapons/steel/other production reacts;
- [ ] news/event entry для значимых потерь.

### Stage 13E — Player combat

- [ ] player ship выбирает цель;
- [ ] attack command;
- [ ] combat feedback;
- [ ] смерть/потеря корабля имеет определённый game-over/recovery rule;
- [ ] manual controls не обходят simulation rules.

### Acceptance scenario

Минимальная битва вроде `2 frigates vs 3 raiders` должна:

1. завершаться воспроизводимым результатом при одинаковом seed/input;
2. физически уничтожать ships;
3. создавать salvage/resource sinks по правилам;
4. менять faction asset count;
5. создавать replacement military demand.

### Definition of Done

Боевой результат физически меняет активы и supply chain, а player и AI используют один authoritative damage/destruction pipeline.

---

## Stage 14 — First Complete Player Economic Loop

**Статус:** PLANNED

### Цель

Получить первый «игровой час», который уже можно оценивать как fun/not fun.

### Основная loop

```text
explore
  ↓
find opportunity
  ↓
trade / mine / fight
  ↓
earn credits
  ↓
upgrade or buy ship
  ↓
take larger opportunities
```

### Stage 14A — Player mining

- [ ] player может использовать mining ship;
- [ ] asteroid resource конечен;
- [ ] mined resource попадает в cargo;
- [ ] продажа проходит обычный market;
- [ ] mining UI показывает target/progress/cargo.

### Stage 14B — Ship purchase / sale

- [ ] data-driven ship prices;
- [ ] ownership transfer;
- [ ] wallet transfer;
- [ ] spawn/delivery rule;
- [ ] старый ship можно продать или сохранить.

### Stage 14C — Minimal ship upgrade/equipment seam

Архитектура должна позволять различать как минимум:

- cargo capacity;
- movement performance;
- combat capability;
- mining capability.

Полный equipment system не является обязательным DoD Stage 14.

### Stage 14D — Playtest telemetry

Собирать хотя бы:

- credits/hour;
- trade profit/hour;
- mining profit/hour;
- combat reward/loss;
- travel downtime;
- average cargo utilization;
- player deaths/losses;
- market opportunity frequency.

### Definition of Done

Игрок может начать с базового корабля, заработать на торговле/добыче/бою и приобрести следующий корабль без debug grants. Это первый внутренний playable vertical slice.

---

# MILESTONE v0.4 — FLEET & EMPIRE SANDBOX

**Статус:** PLANNED

---

## Stage 15 — Player fleets и автономные orders

**Статус:** PLANNED

### Цель

Перевести игрока из роли одного пилота в владельца компании.

### Stage 15A — Fleet ownership

- [ ] player-owned ship может работать без direct control;
- [ ] fleet grouping;
- [ ] fleet leader/member model;
- [ ] persistent orders;
- [ ] transfer ship between fleets;
- [ ] destroyed member safely removed.

### Stage 15B — Orders

Минимальные orders:

- HOLD;
- MOVE;
- TRADE;
- MINE;
- ESCORT;
- PATROL;
- FOLLOW.

### Stage 15C — Delegated economy

Player autonomous trader/miner использует существующий AI planner с owner-specific policy, а не упрощённую «passive income» формулу.

### Stage 15D — Fleet management UI

- owned assets list;
- location/status;
- current order;
- cargo;
- profit/loss summary;
- change order;
- jump route overview.

### Definition of Done

Игрок может владеть несколькими кораблями, лично управлять одним и одновременно заставить другие физически торговать/добывать в persistent world.

---

## Stage 16 — Player construction и station ownership

**Статус:** PLANNED

### Главный принцип

Игрок использует **тот же Stage-9 Construction Project**, что и faction AI.

### Stage 16A — Create project UI/API

Игрок выбирает:

- station archetype;
- system;
- permitted location;
- project funding;
- optional construction priority.

### Stage 16B — Material logistics

Игрок может:

- позволить рынку закупать материалы;
- доставить материалы вручную;
- назначить свои traders;
- отменить проект по тем же refund rules, что и AI.

### Stage 16C — Owned station economy

- station wallet;
- market settings;
- production recipe/policy;
- stock targets;
- access rules в пределах разрешённого scope;
- revenue/cost reporting.

### Stage 16D — Station management UI

- inventory;
- market;
- production;
- wallet;
- construction state;
- profit/loss;
- incoming/outgoing logistics.

### Definition of Done

Игрок зарабатывает ресурсы/деньги, создаёт construction project, физически снабжает его и получает работающую owned station, которая участвует в общей экономике.

---

## Stage 17 — Player Faction

**Статус:** PLANNED

### Цель

Позволить естественный переход «компания -> политический актор».

### Stage 17A — Found faction

Требования должны быть explicit и data-driven/configurable:

- capital threshold;
- owned assets;
- optional station/headquarters;
- fee/resource requirement;
- legal/diplomatic state.

### Stage 17B — Reuse Stage-8 strategic systems

Player faction получает:

- treasury;
- territory;
- relations;
- market access policy;
- taxes/tariffs;
- subsidies;
- stock/production policy;
- military/expansion goals.

Игрок управляет теми же моделями через UI, а не получает отдельные player-only systems.

### Stage 17C — Faction management UI

- treasury;
- budget;
- policies;
- diplomacy;
- territory;
- construction;
- fleets;
- strategic demand.

### Definition of Done

Игрок создаёт собственную фракцию, которая существует в тех же strategic state/persistence/economic systems, что и AI factions.

---

## Stage 18 — Strategic warfare, territory и galactic politics

**Статус:** PLANNED

### Stage 18A — Hostility / war state

- [ ] formal relation state;
- [ ] war declaration/peace transition;
- [ ] market access changes;
- [ ] combat hostility;
- [ ] territory implications.

### Stage 18B — Strategic objectives

Минимальные objectives:

- defend system;
- attack station;
- escort supply;
- blockade route;
- capture/contest system.

### Stage 18C — Military logistics

Флот не должен быть бесплатной абстракцией.

Минимальная экономическая связь:

- construction/replacement ships требует production;
- weapons/ship components создают demand;
- repair/resupply costs добавляются, если входят в подтверждённый дизайн;
- потеря transport routes влияет на frontline economy.

### Stage 18D — Blockade

Ключевой системный тест:

- закрытие/опасность jump route;
- падение throughput;
- shortage;
- price growth;
- local production response;
- strategic response.

### Stage 18E — Territory transition

Заранее определить authoritative rule контроля системы:

- station presence;
- military dominance;
- control points;
- negotiated handover;
- или комбинация.

Не менять territory просто от уничтожения одного случайного объекта.

### Definition of Done

Война меняет physical assets, trade routes, territory и supply chains; AI/player faction способны реагировать стратегически и экономически.

---

# MILESTONE v0.5 — RPG & LIVING WORLD

**Статус:** PLANNED

---

## Stage 19 — Exploration, discovery и world generation

**Статус:** PLANNED

### Stage 19A — Discovery state

Игрок не обязан знать всю GalaxyTopology с начала.

- [ ] discovered systems;
- [ ] discovered jump connections;
- [ ] discovered stations;
- [ ] discovered resource fields;
- [ ] map intel age, если потребуется.

### Stage 19B — Procedural / semi-procedural generation

Определить seed-driven generation contract:

- sectors;
- systems;
- stars/planets как strategic landmarks;
- asteroid/resource distribution;
- jump topology;
- faction starting regions;
- initial economy bootstrap.

Generation должен быть deterministic по seed и content catalog.

### Stage 19C — Exploration content seam

Поддержать data-driven:

- anomalies;
- derelicts;
- resource discoveries;
- special locations;
- one-off events.

### Definition of Done

Новый seed создаёт воспроизводимую multi-system galaxy, которую игрок может постепенно открывать, а discovery влияет на доступную информацию и решения.

---

## Stage 20 — NPC, missions, reputation и RPG progression

**Статус:** PLANNED

### Stage 20A — NPC identity

Persistent NPC model вводится только под реальную игровую потребность:

- identity;
- faction;
- role;
- relation/reputation;
- current assignment;
- optional skills/personality.

Не симулировать индивидуального NPC там, где достаточно агрегированной модели.

### Stage 20B — Mission framework

Mission строится поверх реальных world state/events.

Минимальные templates:

- haul;
- deliver;
- mine;
- escort;
- bounty;
- investigate/discover;
- defend.

### Stage 20C — Dynamic contracts

Контракты возникают из состояния мира:

- shortage -> delivery mission;
- fleet loss -> combat/escort demand;
- expansion -> construction supply;
- war -> patrol/bounty/escort;
- discovery -> exploration.

### Stage 20D — Reputation

Объединить player reputation с:

- market access;
- contract availability;
- faction diplomacy;
- prices, если дизайн подтверждён;
- hostility.

### Stage 20E — Player progression

Выбрать ограниченную production model:

- skills/perks;
- licenses;
- ship specialization;
- reputation gates;
- или комбинацию.

Не добавлять RPG progression, которая не меняет реальные игровые решения.

### Definition of Done

Игрок получает динамические задания, возникающие из живого мира, а reputation/progression открывают новые способы взаимодействия без создания параллельной scripted campaign economy.

---

# MILESTONE v0.6 — CONTENT & BALANCE ALPHA

**Статус:** PLANNED

---

## Stage 21 — Content breadth, balance и long-run stability

**Статус:** PLANNED

### Цель

Только после стабилизации core loops расширять количество контента.

### Stage 21A — Economy content

Постепенно расширить:

- raw resources;
- intermediates;
- advanced components;
- civilian goods;
- military goods;
- ship/station construction materials.

Каждый новый tier должен иметь экономическую роль, а не существовать ради количества.

### Stage 21B — Ship ecosystem

Размеры и роли:

- small;
- medium;
- large;
- freighter;
- tanker;
- miner;
- combat;
- support/specialized.

Визуальные размеры должны соответствовать игровым roles и collision/navigation constraints.

### Stage 21C — Station ecosystem

- extraction/support;
- refining;
- manufacturing;
- trade hub;
- shipyard;
- military;
- colony;
- special strategic infrastructure.

### Stage 21D — Faction differentiation

Различия через:

- starting economy;
- doctrine;
- preferred production;
- taxation;
- diplomacy;
- ship/station archetypes;
- expansion behavior.

Не использовать простые скрытые resource bonuses, если различие можно выразить через реальные правила.

### Stage 21E — Full-world benchmark

Новые profiles должны включать:

- multi-system world;
- inter-system trade;
- construction;
- faction expansion;
- combat losses;
- player-owned assets;
- long simulated duration.

Проверять:

- runaway inflation/deflation;
- dead economies;
- permanent shortages;
- uncontrolled entity growth;
- memory/ledger growth;
- route-planner scaling;
- faction snowball;
- recovery from shocks.

### Definition of Done

Контентная версия поддерживает длительную симуляцию без системного коллапса, а несколько стратегий игрока остаются экономически жизнеспособными.

---

# MILESTONE v0.7 — POLISH / RELEASE CANDIDATE

**Статус:** PLANNED

---

## Stage 22 — UX, onboarding, performance и release hardening

**Статус:** PLANNED

### Stage 22A — UI/UX pass

- unified HUD;
- map layers;
- fleet management;
- station management;
- faction management;
- market comparison;
- notifications;
- filters/search;
- keyboard shortcuts;
- accessibility basics;
- graphics-quality settings с persistent `BloomMode = OFF / LIGHT / FULL`;
- sensible hardware/profile defaults, при этом пользователь может переопределить bloom вручную.

### Stage 22B — Onboarding

- new game flow;
- controls tutorial;
- first trade;
- first mining;
- first combat;
- first autonomous ship;
- first station.

### Stage 22C — Save compatibility policy

До release определить:

- supported migration window;
- corrupt-save behavior;
- backup/autosave;
- crash-safe writes;
- version diagnostics;
- content fingerprint mismatch UX.

### Stage 22D — Performance

Профилировать реальные gameplay scenarios:

- large fleet combat;
- many remote systems;
- galaxy route planning;
- UI with thousands of assets;
- save/load size/time;
- long-running construction/war economy;
- отдельные graphics baselines для `BloomMode.OFF`, `BloomMode.LIGHT` и `BloomMode.FULL`;
- release thresholds должны гарантировать приемлемый gameplay FPS как минимум для `OFF/LIGHT`, а `FULL` остаётся quality tier, а не gameplay requirement.

### Stage 22E — Release quality gates

- [ ] clean build;
- [ ] CI;
- [ ] full regression;
- [ ] benchmark thresholds;
- [ ] desktop smoke;
- [ ] soak test;
- [ ] save/load soak;
- [ ] no known critical economy conservation bugs;
- [ ] no known deterministic continuation bugs.

### Definition of Done

Candidate можно отдать внешнему игроку без developer instructions и получить полный игровой цикл от старта до собственного флота/станции/фракции без debug tools.

---

# 4. Parallel Visual / UX Track

Этот трек идёт параллельно, но не заменяет core stages. Stage 8.5 создаёт базовую rendering/asset infrastructure, на которую опираются все последующие visual tasks.

## V1 — Ship sprite pipeline

Базовый production contract начинается в Stage 8.5 и далее развивается параллельно Stage 9–10.

- единый top-down grounded near-future visual language;
- small / medium / large size grammar;
- читаемые silhouette roles;
- engine hardpoints;
- damage/weapon hardpoints seam;
- sprite scale contract;
- pivot/origin convention;
- collision footprint convention.

## V2 — Engine / movement animation

Первый production spike начинается в Stage 8.5, дальнейшее развитие — параллельно Stage 10–12.

- idle;
- thrust;
- maneuver;
- optional reverse/lateral thrusters;
- 4-frame minimum animation where appropriate;
- animation должна следовать реальному movement state, а не декоративному random playback.

## V3 — Station visual language

Параллельно Stage 9/11/16.

- construction site;
- industrial;
- mining;
- trade;
- military;
- colony;
- faction differentiation seam.

Construction stages желательно визуализировать через ту же persistent project state.

## V4 — Combat VFX и production post-processing

Базовые capabilities проверены Stage 8.5; production content развивается параллельно Stage 13.

- muzzle/beam/projectile;
- shields;
- hit feedback;
- destruction;
- salvage/debris;
- реализовать и benchmark-нуть `BloomMode = OFF / LIGHT / FULL`;
- `OFF` — fallback/performance/accessibility без bloom pass;
- `LIGHT` — целевой restrained gameplay default с bounded low-cost bloom вокруг emissive/VFX;
- `FULL` — high-quality multi-pass режим для мощного hardware/cinematic presentation;
- gameplay readability и simulation correctness не должны зависеть от `FULL`;
- отдельно измерять frame-time/draw/pass cost каждого BloomMode на representative combat scene.

## V5 — Strategic map / empire UI

Параллельно Stage 15–18.

- systems;
- jump routes;
- territory;
- fleet orders;
- trade flows;
- shortages;
- wars.

### Visual Track правило

Нельзя привязывать authoritative gameplay к конкретному sprite asset. Asset IDs могут быть data-driven presentation metadata поверх simulation archetype.

---

# 5. Cross-cutting engineering tracks

Некоторые задачи должны сопровождать несколько stages, а не становиться отдельными «большими переделками».

## Persistence

Каждый новый persistent domain object обязан определить:

- stable ID;
- schema ownership;
- codec;
- migration policy;
- continuation test;
- bounds/validation.

## Determinism

Любой AI/planner должен иметь:

- deterministic iteration order;
- explicit tie-break;
- named RNG stream только если случайность действительно нужна;
- save/load continuation coverage.

## Economic conservation

Любая механика, меняющая деньги/товары:

- использует transfer/source/sink/transform semantics;
- имеет ledger representation;
- имеет invariant/regression test;
- не создаёт hidden passive income/resource creation.

## Observability

Для major systems добавлять измеримые метрики:

- rendering frame time/draw calls начиная со Stage 8.5;
- construction;
- faction investment;
- inter-system throughput;
- fleet losses;
- combat;
- recovery;
- territory;
- player economy.

## Performance

Оптимизация делается:

1. по benchmark/profile;
2. либо заранее, если алгоритм очевидно не масштабируется по структуре.

Не делать speculative micro-optimization без evidence.

---

# 6. Правила выполнения roadmap

1. **`main` остаётся стабильной базой.**
2. Новый stage/feature branch создаётся только от зелёного актуального `main`.
3. Сломанный CI блокирует merge и переход к следующему core stage.
4. Следующий core stage становится ACTIVE только после Definition of Done предыдущего.
5. Крупный Stage дробится на independently reviewable PRs.
6. Не расширять количество контента ради количества до стабилизации соответствующего механизма.
7. Любая новая экономическая механика сопровождается invariant tests.
8. Любой persistent state сопровождается save/load continuation tests.
9. Любой deterministic planner/AI сопровождается deterministic tie-break tests.
10. README описывает только реально доступное поведение стабильного `main`.
11. Архитектурные решения фиксируются до массового наполнения контентом.
12. Player и AI переиспользуют общие simulation APIs, если нет доказанной причины для отдельного пути.
13. Не создавать passive income, virtual delivery, instant construction и другие обходы физической экономики без отдельного design decision.
14. Remote simulation может иметь reduced fidelity, но не должна создавать вторую несовместимую экономическую модель.
15. После завершения Stage обновить этот файл: статус, exact verification evidence, merge commit и следующий активный этап.
16. Для каждого milestone должна существовать хотя бы одна end-to-end acceptance scenario.
17. Visual/UX work может идти параллельно, но не считается выполнением simulation Definition of Done, кроме explicit Stage 8.5 technology gate.
18. Новая система должна сначала доказать минимальную working loop; только потом расширяется глубиной и количеством контента.
19. Stage 9 не становится ACTIVE, пока Stage 8.5 не зафиксировал production technology decision.

---

# 7. Definition of Ready для нового Stage

Перед началом крупного Stage должны быть понятны:

- проблема, которую он решает;
- dependency на предыдущие stages;
- authoritative state owner;
- persistence impact;
- economic invariant impact;
- deterministic behavior;
- minimal vertical slice;
- acceptance test;
- что сознательно **не входит** в scope;
- ожидаемое PR-разбиение.

Если один из этих пунктов неизвестен, сначала выполняется короткий design/audit pass, а не начинается массовая реализация.

---

# 8. Milestone acceptance scenarios

## v0.2 Living Galactic Economy

Technology gate перед экономической частью milestone:

```text
current simulation
→ visual technology spike
→ measured rendering baseline
→ stack decision recorded
→ Stage 9 unblocked
```

Затем основной systemic scenario:

```text
critical producer destroyed
→ shortage
→ faction detects bottleneck
→ construction project
→ materials arrive from another system
→ new capacity online
→ economy recovers
→ faction later expands to neighboring system
```

## v0.3 Playable Space Sandbox

```text
player starts with one ship
→ travels to another system
→ trades/mines
→ fights
→ earns credits
→ buys a better ship
```

## v0.4 Fleet & Empire Sandbox

```text
player owns multiple ships
→ delegates trade/mining
→ builds station
→ creates faction
→ fights over a system
→ territory and economy change
```

## v0.5 RPG & Living World

```text
world shortage/war/discovery
→ dynamic contract generated
→ player accepts
→ completes task through real simulation
→ reputation/world state changes
```

---

# 9. Текущий следующий шаг

**ACTIVE: Stage 9C — Destruction и economic shock.**

Stage 8.5 завершён решением `KEEP_LIBGDX`; presentation technology gate больше не блокирует core development.

Immediate implementation sequence:

1. explicit destruction API поверх Stage-9A structural removal;
2. определить cargo/stock fate `DESTROY / SALVAGE / TRANSFER`;
3. определить money/wallet fate без silent currency loss;
4. ledger accounting для уничтоженных ресурсов и transfers;
5. physical salvage entity при выбранной salvage policy;
6. immediate production/market removal и route invalidation;
7. events/news hook для значимых потерь;
8. economic-shock tests, затем Stage 9D bottleneck analysis.

Параллельный visual track продолжает развивать approved asset pipeline. `BloomMode = OFF / LIGHT / FULL` реализуется и benchmark-ится вместе с Stage 13 / V4 Combat VFX, а финальные graphics-quality settings и thresholds входят в Stage 22.

После Stage 9 следующий core этап — **Stage 10: Inter-system logistics**, затем **Stage 11: Autonomous Faction Expansion**. Полноценный combat остаётся в playable milestone после появления межсистемной экономики и player state.
