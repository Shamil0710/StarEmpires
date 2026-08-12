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
- [ ] save/load сохраняет экономическое состояние и ссылки между сущностями через устойчивые ID;
- [ ] товары, рецепты, корабли, станции и фракционные параметры загружаются из data-driven каталога;
- [ ] торговый AI оценивает прибыль с учётом времени/стоимости маршрута;
- [ ] существует headless benchmark минимум на 100 станций, 500 экономических агентов и 100 игровых часов;
- [ ] benchmark собирает ключевые экономические метрики и выявляет разрушение supply chain.

---

## Этап 0 — Repository health и зелёная сборка

**Статус:** COMPLETE — MERGED TO `main` VIA PR #1

### Задачи

- [x] восстановить отсутствующие `AsteroidComponent`, `AsteroidSpawnPoint`, `AsteroidSpawnConfig`, `AsteroidSpawnSystem`;
- [x] устранить все compile errors;
- [x] запустить полный `clean verify` в GitHub Actions;
- [x] добиться прохождения всех тестов;
- [x] добиться прохождения JaCoCo thresholds;
- [x] обновить `actions/setup-java` с v4 на v5;
- [x] убедиться, что README соответствует фактическому HEAD;
- [ ] выполнить desktop smoke-check по существующему checklist — **MANUAL / NON-BLOCKING FOR CORE DoD**;
- [x] открыть PR `fix/economy-stability -> main` — PR #1;
- [x] merge только после зелёного CI — merge commit `483dad87b03eb2a1eb355be6c29e503dc7d872e5`;
- [x] после merge использовать `main` как стабильную базу, feature/fix-ветки — для разработки.

### Результат автоматической проверки

- `./mvnw --batch-mode --no-transfer-progress clean verify` — SUCCESS;
- JUnit, Javadoc с `failOnWarnings=true` и JaCoCo line/branch gates — SUCCESS;
- runnable `star-empires-*-all.jar` и отчёты публикуются как GitHub Actions artifacts.

### Definition of Done

Свежий clone репозитория на JDK 17 выполняет `./mvnw clean verify`, создаёт runnable `-all.jar`, все автоматические проверки зелёные. **Выполнено.**

> Desktop smoke-test требует реального графического/OpenGL-сеанса и остаётся ручным release-checklist. Он не подменяется headless-тестами и не считается автоматически пройденным.

---

## Этап 1 — SimulationClock и детерминированное игровое время

**Статус:** COMPLETE — MERGED TO `main` VIA PR #2

### Задачи

- [x] ввести `SimulationClock` с fixed step — accumulator хранится в целых наносекундах;
- [x] отделить render delta от simulation delta через `SimulationLoop`;
- [x] добавить pause и time scale без изменения размера fixed tick;
- [x] все экономические события и новости перевести на game time;
- [x] ввести единый seeded RNG / RNG service — `SimulationRandom` с именованными потоками;
- [x] определить порядок систем как явный simulation pipeline;
- [x] добавить тесты эквивалентности при разных render frame patterns;
- [x] определить допустимые численные tolerance для экономического состояния — `docs/simulation_time_model.md`.

### Результат проверки

- fixed step демонстрационной игры: `0.1` игровой секунды;
- render/UI time отделён от authoritative simulation time;
- backlog fixed ticks не отбрасывается при защитном `maxStepsPerFrame`;
- `NewsArticle.timestamp` теперь означает миллисекунды игрового времени, а не Unix/wall-clock;
- события и астероиды используют независимые RNG-потоки от одного root seed;
- `EconomicSimulationDeterminismTest` сравнивает экономическое состояние после 300 ticks при `30 × 1s` и `300 × 0.1s` render patterns;
- сравниваются позиции, склады, рыночные цены, торговые состояния, маршруты, астероиды и активные события;
- полный `clean verify` на HEAD Этапа 1 — SUCCESS;
- 156 тестов на интеграционном наборе, Javadoc и JaCoCo gates — SUCCESS;
- merge commit в `main`: `3340d762548e0643c496bdc400b6be51e0df7f64`.

### Definition of Done

Одинаковый initial state + seed + количество simulation ticks приводит к одинаковому экономическому состоянию независимо от FPS/рендеринга. **Выполнено.**

---

## Этап 2 — Деньги и экономические инварианты

**Статус:** VERIFIED — READY FOR PR

### Задачи

- [x] заменить денежные `float` на целочисленную фиксированную денежную единицу (`long`);
- [x] создать `WalletComponent`;
- [x] сделать торговую транзакцию двусторонней: товар и деньги физически переходят между участниками;
- [x] создать `EconomicTransaction` / `EconomicLedger` для диагностики;
- [x] определить явные money sources/sinks отдельно от transfer;
- [x] классифицировать resource source / sink / transform отдельно от физических transfer;
- [x] подключить один общий session ledger к runtime экономическим системам;
- [x] удалить legacy `CreditAccount`, `PlayerProfile` и authoritative `float credits` из торгового API;
- [x] добавить resource-conservation и money-conservation тесты;
- [x] добавить тесты атомарности сделки;
- [x] добавить regression-test сохранения общей денежной массы demo-world;
- [x] подтвердить детерминированность последовательности `EconomicTransaction` при разных render patterns;
- [x] зафиксировать контракт в `docs/economic_invariants.md`.

### Результат проверки

- authoritative денежный баланс хранится только в `WalletComponent` как `long` milli-credits;
- обычная торговля является двусторонним transfer и не использует money source/sink;
- станция имеет конечную ликвидность и не может купить груз без достаточного баланса;
- добыча переносит ресурс `asteroid.remainingResource -> cargo` без скрытого создания товара;
- asteroid spawn записывается как `RESOURCE_SOURCE`, потребление как `RESOURCE_SINK`, производство как `RESOURCE_TRANSFORM`;
- bootstrap-запасы и стартовый капитал считаются baseline initial state, а не runtime source-операциями;
- `MoneyConservationSimulationTest` проверяет неизменность суммы всех кошельков demo-world после 600 fixed ticks;
- `EconomicLedgerDeterminismTest` подтверждает одинаковую последовательность ledger-операций при разных render patterns;
- полный `./mvnw --batch-mode --no-transfer-progress clean verify` на code HEAD `f68816e3a4348bc3a80ae4d0bf8064f512e77c84` — SUCCESS;
- **167 тестов, 0 failures, 0 errors, 0 skipped**;
- Javadoc с `failOnWarnings=true` и JaCoCo line/branch gates — SUCCESS;
- runnable `star-empires-*-all.jar`, JaCoCo, Javadoc и test reports опубликованы GitHub Actions artifacts.

### Definition of Done

Обычная торговля не создаёт и не уничтожает деньги или товар. Любое создание/уничтожение ресурса имеет явный тип экономического события. **Выполнено на code HEAD; ожидается повторный зелёный CI точного status/docs HEAD перед PR.**

---

## Этап 3 — EntityId и сохранения

**Статус:** PLANNED

### Задачи

- [ ] ввести устойчивый `EntityId`;
- [ ] ввести runtime registry `EntityId -> Entity`;
- [ ] убрать persistent-зависимость от прямых Ashley `Entity` ссылок;
- [ ] реализовать versioned `GameState`;
- [ ] реализовать save/load;
- [ ] сохранять game clock и RNG state;
- [ ] добавить round-trip и continuation tests.

### Definition of Done

`simulate(A) -> save -> load -> simulate(B)` эквивалентно `simulate(A+B)` в пределах определённых инвариантов.

---

## Этап 4 — Data-driven контент

**Статус:** PLANNED

### Задачи

- [ ] вынести товары, рецепты, типы кораблей, типы станций и фракционные параметры из Java-кода;
- [ ] использовать устойчивые строковые content IDs и плотные runtime IDs;
- [ ] добавить validation catalog при загрузке;
- [ ] поддержать schema/version для данных;
- [ ] оставить плотные массивы в hot simulation path.

### Definition of Done

Новый товар/рецепт/тип станции можно добавить изменением данных без изменения simulation-кода.

---

## Этап 5 — Логистика и Trade Route Planner

**Статус:** PLANNED

### Задачи

- [ ] отделить route discovery от исполнения FSM корабля;
- [ ] создать `MarketDirectory`;
- [ ] создать `TradeRoutePlanner`;
- [ ] оценивать минимум profit/time вместо gross profit;
- [ ] добавить travel cost и подготовить интерфейсы для fuel/risk/tariffs;
- [ ] исключить глобальный `stations² × goods` поиск на каждого агента;
- [ ] добавить stale-route/replan policy.

### Definition of Done

Торговый AI предпочитает лучший экономический маршрут с учётом времени и масштабируется на benchmark-мир без квадратичного глобального перебора каждым кораблём.

---

## Этап 6 — Headless economic benchmark и observability

**Статус:** PLANNED

### Задачи

- [ ] создать headless simulation runner без OpenGL;
- [ ] сценарий минимум 100 станций / 500 агентов / расширенный каталог товаров;
- [ ] прогон минимум 100 игровых часов;
- [ ] собирать TPS/CPU и allocation baseline;
- [ ] собирать stockouts, unmet demand, price variance, trade volume, production volume;
- [ ] собирать wealth distribution и route profitability;
- [ ] добавить regression thresholds для ключевых инвариантов.

### Definition of Done

Экономический core можно автоматически стресс-тестировать без UI, а деградации поведения и производительности видны количественно.

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

## Текущий следующий шаг

**Переход Этап 2 → Этап 3:** получить зелёный CI на текущем status/docs HEAD `feat/economic-invariants`, открыть PR в `main`, merge только после зелёных push + pull_request проверок точного HEAD. После merge создать отдельную ветку Этапа 3 и начать с устойчивого `EntityId` и runtime registry, не смешивая save/load с data-driven контентом Этапа 4.
