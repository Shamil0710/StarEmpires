# Star Empires — Development Roadmap

> Статусный документ разработки. Этапы выполняются последовательно: следующий этап начинается только после выполнения Definition of Done предыдущего.
>
> Последнее обновление: 2026-08-12.

## Цель ближайшего milestone: Economic Sandbox v0.1

Надёжный, воспроизводимый экономический sandbox, который можно прогонять без UI сотни игровых часов и использовать как фундамент для галактики, фракций, строительства и боевой системы.

Критерии milestone:

- [x] проект стабильно собирается из чистого clone одной командой;
- [ ] CI зелёный и блокирует сломанные изменения — CI зелёный; обязательная branch protection для `main` пока не настроена доступным connector API;
- [x] симуляция использует фиксированный игровой tick и поддерживает pause/time scale;
- [x] одинаковый seed даёт воспроизводимую экономическую симуляцию;
- [x] товары сохраняются физически: source / transform / sink всегда явны;
- [x] деньги передаются между экономическими агентами, а не создаются/исчезают внутри обычной сделки;
- [x] save/load сохраняет экономическое состояние и ссылки между сущностями через устойчивые ID;
- [x] товары, рецепты, корабли, станции и фракционные параметры загружаются из data-driven каталога;
- [x] торговый AI оценивает прибыль с учётом времени маршрута и имеет extension seam для дополнительных route costs;
- [ ] существует headless benchmark минимум на 100 станций, 500 экономических агентов и 100 игровых часов;
- [ ] benchmark собирает ключевые экономические метрики и выявляет разрушение supply chain.

---

## Этап 0 — Repository health и зелёная сборка

**Статус:** COMPLETE — MERGED TO `main` VIA PR #1

### Основные результаты

- [x] восстановлены отсутствующие asteroid-классы и устранены compile errors;
- [x] `./mvnw clean verify` проходит из чистого clone на JDK 17;
- [x] JUnit, Javadoc `failOnWarnings=true`, JaCoCo и runnable shaded JAR входят в CI;
- [x] `actions/setup-java` обновлён до v5;
- [x] feature/fix-ветки используются как рабочий контур, `main` — стабильная база;
- [ ] desktop OpenGL smoke-check остаётся ручным release-checklist и не блокирует core DoD.

Merge commit: `483dad87b03eb2a1eb355be6c29e503dc7d872e5`.

### Definition of Done

Свежий clone выполняет `./mvnw clean verify`, создаёт runnable `-all.jar`, автоматические проверки зелёные. **Выполнено.**

---

## Этап 1 — SimulationClock и детерминированное игровое время

**Статус:** COMPLETE — MERGED TO `main` VIA PR #2

### Основные результаты

- [x] `SimulationClock` с fixed step `0.1s`;
- [x] render delta отделён от simulation delta через `SimulationLoop`;
- [x] pause/time scale не меняют размер simulation tick;
- [x] события и новости работают на game time;
- [x] deterministic `SimulationRandom` с именованными RNG streams;
- [x] определён явный порядок simulation systems;
- [x] тесты подтверждают эквивалентность разных render-frame patterns при одинаковом числе fixed ticks.

Merge commit: `3340d762548e0643c496bdc400b6be51e0df7f64`.

### Definition of Done

Одинаковый initial state + seed + количество simulation ticks приводит к одинаковому экономическому состоянию независимо от FPS/рендеринга. **Выполнено.**

---

## Этап 2 — Деньги и экономические инварианты

**Статус:** COMPLETE — MERGED TO `main` VIA PR #3

### Основные результаты

- [x] authoritative деньги переведены на `long` milli-credits;
- [x] добавлен `WalletComponent`;
- [x] обычная торговля двусторонняя и атомарная: товар и деньги физически переходят между агентами;
- [x] станции имеют конечную ликвидность;
- [x] введены `EconomicTransaction` и общий `EconomicLedger`;
- [x] resource source / sink / transform отделены от физических transfer;
- [x] добыча, производство, потребление и asteroid spawn отражаются в ledger;
- [x] удалены legacy authoritative `float credits` APIs;
- [x] добавлены money/resource conservation, atomicity и ledger-determinism regression tests;
- [x] контракт задокументирован в `docs/economic_invariants.md`.

Verified PR head: `8dcacbf88220990c5b7a61cd4b56ece06121478b`.
Merge commit: `41aaf96c101edf5a216ba23fd30f592df0eb8d51`.

### Definition of Done

Обычная торговля не создаёт и не уничтожает деньги или товар. Любое создание/уничтожение ресурса имеет явный экономический тип. **Выполнено.**

---

## Этап 3 — EntityId и сохранения

**Статус:** COMPLETE — MERGED TO `main` VIA PR #4

### Основные результаты

- [x] введён устойчивый `EntityId`;
- [x] введён общий runtime `EntityRegistry`;
- [x] persistent-ссылки TradeAI/Mining переведены с прямых Ashley `Entity` на `EntityId`;
- [x] реализован versioned `GameState`;
- [x] реализован детерминированный ограниченный бинарный `GameStateCodec`;
- [x] сохранение использует безопасную замену файла;
- [x] сохраняются clock, RNG streams, события, asteroid spawner, price recorder и economic ledger;
- [x] `SimulationSession` поддерживает snapshot/restore/save/load без OpenGL;
- [x] `SpaceSimGame` использует тот же authoritative simulation pipeline;
- [x] round-trip и file continuation tests подтверждают `simulate(A) -> save/load -> simulate(B)`.

Verified PR head: `9899aae8016e2d1154de46a1ddaf840893c8e09e`.
Merge commit: `10559976dacced9d07392df9125f504a851d9b76`.

### Definition of Done

`simulate(A) -> save -> load -> simulate(B)` эквивалентно `simulate(A+B)` в пределах определённых инвариантов. **Выполнено.**

---

## Этап 4 — Data-driven контент

**Статус:** COMPLETE — MERGED TO `main` VIA PR #5

### Основные результаты

- [x] создан versioned validated JSON `ContentCatalog`;
- [x] товары, рецепты, фракции, ship archetypes и station archetypes описываются данными;
- [x] stable string content IDs отделены от dense runtime IDs;
- [x] hot simulation path сохраняет плотные массивы;
- [x] `MAX_ITEMS` стал runtime capacity `64`, а не числом enum-товаров;
- [x] `MarketSystem` и `TradeAISystem` получают item metadata из `ContentCatalog`;
- [x] `ShipType` оставлен как runtime role/cargo policy, конкретные модели вынесены в archetypes;
- [x] generic `ArchetypeEntityFactory` создаёт stations/traders/miners/combat ships из данных;
- [x] `DemoWorldFactory` хранит scenario placement, а не экономические tuning constants;
- [x] stable archetype ID сохраняется через `ArchetypeComponent` и `EntityState` schema v2;
- [x] Stage-3 save v1 мигрирует 5 item slots в текущую capacity без потери legacy значений;
- [x] semantic SHA-256 fingerprint каталога реализован;
- [x] content-bound `STEC` save envelope отклоняет несовместимый catalog до restore;
- [x] raw `STEM` Stage-3 saves остаются поддержаны;
- [x] data-only item test доказывает работу товара без Java `ItemType` constant;
- [x] validation fail-fast проверяет ссылки station -> faction/recipe/item и role-specific ship parameters;
- [x] authoring/versioning contract описан в `docs/content_catalog.md`;
- [x] implementation evidence описан в `docs/stage4_verification.md`.

Verified final PR head: `b9ddcd3171ced36ee12945408805e84763a9362d`.
Push CI exact head: SUCCESS.
Pull-request CI exact head: SUCCESS.
Merge commit: `aed0e711146dfd2b326974bec6d1823f43f062ed`.
Post-merge `main` CI: SUCCESS.

### Definition of Done

Новый товар/рецепт/тип станции или конкретный ship/station archetype можно добавить изменением данных без изменения simulation-кода. **Выполнено.**

---

## Этап 5 — Логистика и Trade Route Planner

**Статус:** COMPLETE — MERGED TO `main` VIA PR #6

### Основные результаты

- [x] route discovery отделён от исполнения FSM корабля;
- [x] создан `MarketDirectory` с immutable defensive snapshots рынков;
- [x] созданы value objects `FleetTradeProfile`, `TradeOpportunity`, `TradeRoute`, `TradeSaleRoute` без Ashley `Entity` references;
- [x] создан pure `TradeRoutePlanner`;
- [x] production default оценивает новые грузы по net profit per travel second вместо gross profit;
- [x] уже купленный cargo оценивается по net sale revenue per travel second;
- [x] учитываются purchase amount, cargo capacity, specialization, cargo policy, спрос, stock, капитал и рыночная ликвидность;
- [x] score учитывает `fleet -> supplier -> consumer` distance/time;
- [x] создан `TradeRouteCostModel` как extension seam для fuel/risk/tariffs без введения вымышленных balancing costs на этом этапе;
- [x] supplier-consumer pairing выполняется один раз на общий market snapshot вместо глобального `stations² × goods` перебора каждым агентом;
- [x] shortlist ограничен максимум 8 consumers на supplier/item и сохраняет ценовые и margin/distance кандидаты;
- [x] stale-route/replan policy определена и реализована: execution повторно валидирует stations/prices/capacity/liquidity, invalid route сбрасывается с cooldown без partial transfer;
- [x] deterministic tie-breaks не используют RNG;
- [x] Stage 2 money/resource invariants остаются зелёными;
- [x] Stage 3 save/load continuation остаётся зелёным, persistent route schema не менялась;
- [x] performance-oriented regression с 40 suppliers + 40 consumers доказывает bounded candidate count;
- [x] архитектурный и экономический контракт описан в `docs/trade_route_planning.md`.

Verified final PR head: `ac80376130f7680b0b59e0fab59c63719a75795d`.
Push CI exact head: SUCCESS.
Pull-request CI exact head: SUCCESS.
Exact-head suite: **235 tests, 0 failures, 0 errors, 0 skipped**; Javadoc SUCCESS; JaCoCo thresholds SUCCESS.
Merge commit: `fd7c0ece140487aa13a661225c8c9034e9333537`.
Post-merge `main` CI: SUCCESS.

### Definition of Done

Торговый AI предпочитает лучший маршрут по экономической отдаче на единицу времени, route discovery находится вне ship FSM, а каждый агент больше не выполняет глобальный квадратичный перебор всех пар станций и товаров. **Выполнено.**

---

## Этап 6 — Headless economic benchmark и observability

**Статус:** ACTIVE — следующий рабочий этап

### Цель

Получить воспроизводимый количественный контур, который показывает не только корректность инвариантов, но и устойчивость экономики и стоимость симуляции на масштабе до перехода к multi-system миру.

### Задачи

- [ ] создать headless simulation/benchmark runner без OpenGL;
- [ ] определить versioned benchmark scenario и его seed;
- [ ] сценарий минимум 100 станций / 500 экономических агентов / расширенный каталог товаров;
- [ ] прогон минимум 100 игровых часов;
- [ ] собирать wall-clock duration, simulated-time/real-time ratio и ticks/second;
- [ ] собрать allocation/heap baseline доступными JVM-инструментами без привязки core к profiler library;
- [ ] собирать stockouts и длительность дефицитов;
- [ ] собирать unmet demand;
- [ ] собирать price variance/volatility;
- [ ] собирать trade volume и transaction count;
- [ ] собирать production/mining/consumption volume;
- [ ] собирать wealth distribution и wallet concentration;
- [ ] собирать route profitability и planner opportunity counts;
- [ ] явно проверять money/resource conservation в benchmark run;
- [ ] добавить machine-readable benchmark report;
- [ ] определить regression thresholds только после получения первого baseline, не подгоняя их заранее;
- [ ] добавить smaller deterministic benchmark smoke-test в CI, а тяжёлый 100-hour profile оставить отдельным reproducible command, если runtime окажется неприемлемым для каждого PR.

### Definition of Done

Экономический core автоматически стресс-тестируется без UI; benchmark минимум 100 stations / 500 agents / 100 simulated hours воспроизводим; отчёт показывает производительность, дефициты, цены, торговлю, производство, wealth и route-planner metrics; экономические инварианты остаются доказанными.

---

## Этап 7 — Иерархия мира и уровни симуляции

**Статус:** PLANNED

### Задачи

- [ ] `Galaxy -> Sector -> StarSystem`;
- [ ] планеты, станции, астероидные поля, флоты и jump connections;
- [ ] разделить local и strategic simulation frequency;
- [ ] определить update budget для удалённых систем;
- [ ] внедрить spatial/system-level indexes.

### Definition of Done

Мир содержит несколько систем, удалённые системы продолжают экономически жить без симуляции каждого объекта на полном local tick.

---

## Этап 8 — Фракции как экономические акторы

**Статус:** PLANNED

### Задачи

- [ ] treasury и бюджет;
- [ ] отношения и территория;
- [ ] production/stock policies;
- [ ] military и expansion demand;
- [ ] ограничения доступа к рынкам;
- [ ] налоги/тарифы/субсидии;
- [ ] стратегические цели, создающие экономический спрос.

### Definition of Done

Фракционные решения физически изменяют спрос, производство, логистику и финансовые потоки мира.

---

## Этап 9 — Строительство и воспроизводство экономики

**Статус:** PLANNED

### Задачи

- [ ] строительство требует реальных ресурсов и перевозок;
- [ ] станции появляются и исчезают как часть симуляции;
- [ ] AI реагирует на supply-chain bottlenecks;
- [ ] уничтожение объекта создаёт реальный дефицит;
- [ ] экономика умеет инвестировать в новые производственные мощности.

### Definition of Done

Supply chain может самостоятельно перестраиваться после дефицита или уничтожения производственного звена.

---

## Этап 10 — Combat vertical slice

**Статус:** PLANNED

Боевой слой развивается после стабилизации экономики, чтобы уничтожение, защита и захват объектов имели реальные экономические последствия.

### Задачи

- [ ] weapons / damage / armor / shields;
- [ ] targeting и combat AI;
- [ ] fleet combat;
- [ ] salvage;
- [ ] потери кораблей и станций интегрированы с экономикой;
- [ ] военный спрос интегрирован с производственными цепочками.

### Definition of Done

Боевой результат изменяет физические активы и supply chain, а экономика и фракционный AI реагируют на последствия.

---

## Правила выполнения roadmap

1. Не расширять количество контента ради количества до стабилизации соответствующего core-этапа.
2. Любая новая экономическая механика сопровождается тестом инварианта.
3. Сломанный CI блокирует переход к следующему этапу.
4. README описывает только фактически доступное поведение текущего стабильного состояния.
5. Архитектурные решения фиксируются до массового наполнения контентом.
6. Оптимизация проводится по benchmark/профилям, кроме очевидно не масштабируемых алгоритмов, уже выявленных архитектурой.
7. После завершения каждого этапа этот файл обновляется: статус, выполненные пункты, важные решения и следующий активный этап.
8. Новый stage branch создаётся только от зелёного актуального `main` после merge предыдущего этапа.

## Текущий следующий шаг

**Этап 6 — Headless economic benchmark и observability.** После зелёного CI этого status-коммита создать отдельную ветку от актуального `main`. Первый вертикальный срез: benchmark scenario + headless runner + machine-readable metrics для малого deterministic smoke-run. Затем масштабировать этот же runner до 100 stations / 500 agents / 100 simulated hours и только по фактическим данным вводить performance/regression thresholds.