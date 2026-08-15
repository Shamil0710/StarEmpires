# Star Empires — Simulation Scalability Architecture

> Версия: **0.1**  
> Статус: **cross-stage architectural contract / PLANNED**  
> Область действия: Stage 17.5, Stage 18, Stage 19, Stage 21, Stage 22 и все последующие системы, увеличивающие число одновременно существующих экономических, стратегических или физических сущностей.  
> Назначение: не допустить ситуации, в которой глубина симуляции Star Empires упирается в производительность из-за архитектуры, требующей одинаково подробного и частого обновления всего мира.

---

# 1. Главный принцип

Star Empires должен сохранять глубокую authoritative simulation, но глубина симуляции **не означает**, что каждая сущность обязана постоянно существовать как полноценно активный runtime object и обновляться с одинаковой частотой.

Неподвижный инвариант:

> **Весь мир не должен тикать с частотой tactical simulation или render loop.**

Любая новая система обязана отвечать на четыре вопроса:

1. какое authoritative состояние действительно необходимо хранить постоянно;
2. какое состояние можно вычислить по событию или по запросу;
3. при каких условиях сущность должна быть materialized в детальную локальную симуляцию;
4. как сущность deterministic возвращается в более дешёвое strategic/dormant представление.

Оптимизация не должна подменять модель скрытыми бонусами, случайными shortcuts или отдельными правилами для player/AI. Все уровни детализации должны оставаться разными представлениями одной игровой реальности.

---

# 2. Разделение render rate и simulation rate

Графический цикл LibGDX не является authoritative временем мира.

Запрещается архитектура вида:

```text
render frame
→ update every fleet
→ update every ship
→ update every market
→ update every factory
→ update every AI actor
```

Runtime должен иметь явные clock/scheduler boundaries. Частоты ниже являются **целевыми диапазонами для проектирования и benchmark calibration, а не замороженными gameplay constants**:

| Simulation layer | Типичная детализация | Ориентир частоты |
|---|---|---:|
| Tactical/local physical | бой, projectiles, detailed sensors, local maneuver | 30–60 Hz либо фиксированный physics tick |
| Active system/local strategic | локальное движение, docking, nearby traffic, system events | ~5–10 Hz или event-driven |
| Strategic/inter-system | fleets, orders, transit, high-level state | ~0.5–2 Hz или event-driven |
| Economy/logistics | production, market clearing, cargo flow | discrete scheduled ticks / dirty updates |
| Macro/politics/development | doctrine review, expansion, long-horizon planning | редкие deterministic cadence ticks |
| Dormant | состояние без непосредственного взаимодействия | event-only / on-demand |

Конкретная система может использовать другую cadence, если это подтверждено моделью и benchmark.

---

# 3. Simulation LOD как часть authoritative architecture

Каждая масштабируемая сущность должна иметь определённый simulation level-of-detail.

Минимальная модель:

```text
DORMANT
  ↓ materialize / event due
STRATEGIC
  ↓ local relevance
ACTIVE_LOCAL
  ↓ direct tactical relevance
TACTICAL
```

Обратный переход также обязателен:

```text
TACTICAL
→ ACTIVE_LOCAL
→ STRATEGIC
→ DORMANT
```

LOD не должен быть только visual optimization. Он определяет, **какие вычисления реально нужны**, при сохранении authoritative persistent state.

Примеры:

- далёкий торговый корабль не обязан интегрировать позицию каждый tactical tick;
- фабрика без изменения inputs/outputs не обязана пересчитывать один и тот же production result каждый кадр;
- фракционный AI не обязан пересматривать стратегию для каждого subordinate ship каждый tick;
- система, в которой нет due events и локального наблюдателя, может оставаться dormant.

---

# 4. Persistent identity не равна Ashley Entity

Ashley ECS используется для runtime composition, но **не должен автоматически становиться контейнером для каждой существующей сущности галактики**.

Каноническая persistent identity корабля, флота, станции, поселения, рынка или другого world object должна существовать независимо от того, materialized ли объект сейчас в ECS.

Принцип:

```text
Persistent World State
        ↓ materialization
Local Runtime / Ashley Entity
        ↓ dematerialization
Persistent World State
```

Materialization/dematerialization обязана быть:

- deterministic;
- lossless относительно authoritative state;
- versioned там, где затрагивает persistence schema;
- проверяемой round-trip acceptance tests.

Запрещается держать сотни тысяч dormant ships в полном tactical component set только потому, что локальный корабль использует такой набор компонентов.

---

# 5. Event-driven transit и scheduled state transitions

Длительные процессы должны по возможности моделироваться через состояние + расписанное событие, а не через бессмысленный per-tick polling.

Пример межсистемного/дальнего transit:

```text
TransitState
- departureTime
- origin
- destination
- trajectory / route reference
- arrivalTime
- current consumable commitments
- interruption conditions
```

После создания корректного transit plan scheduler может зарегистрировать due event:

```text
ARRIVAL(shipId, arrivalTime)
```

До наступления релевантного события объект не обязан выполнять полный movement update.

Если корабль становится локально релевантным до прибытия, его положение/скорость/remaining state восстанавливаются deterministic из authoritative transit state.

Тот же принцип применяется к:

- construction completion;
- repair completion;
- production batches;
- contract deadlines;
- policy review cadence;
- cooldowns;
- FTL phases;
- scheduled fleet orders;
- долгим logistics operations.

---

# 6. Tick hierarchy и scheduler contract

Simulation scheduler должен поддерживать разные cadence domains и stable ordering.

Минимальные требования:

- simulation time отделено от wall-clock/render time;
- due events имеют deterministic ordering при одинаковом timestamp;
- ordering использует stable IDs / explicit priority, а не iteration order hash collections;
- subsystem может запросить next due time вместо постоянного polling;
- expensive work распределяется по cadence, если model semantics не требуют одновременного execution;
- scheduler state, влияющий на gameplay, должен переживать save/load.

При одинаковом seed, initial state и command/event stream результат должен совпадать независимо от render FPS.

---

# 7. Dirty-state economy и incremental recomputation

Экономическая глубина не должна означать полный пересчёт всех markets/factories/orders на каждом economy tick.

Система должна по возможности использовать invalidation/dirty semantics.

Market/production state становится dirty, например, когда:

- изменился physical inventory;
- пришёл или ушёл cargo;
- изменился production recipe/capability;
- появился/закрылся order;
- изменилась цена/доступность существенного input;
- facility получила damage/repair;
- изменился доступ к маршруту, территории или правовому режиму;
- наступило scheduled production/settlement event.

Если входное состояние не изменилось и нет due event, повторное вычисление того же результата должно избегаться.

Это правило не разрешает пропуск реальных economic consequences. Оно требует вычислять их **тогда, когда они могут измениться**.

---

# 8. AI scheduling и иерархия решений

AI должен быть построен так, чтобы число decisions не росло как `all actors × every tick`.

Предпочтительная иерархия:

```text
Faction / polity intent
→ strategic planning
→ fleet / organization orders
→ local controller
→ ship execution
```

Обычный ship executor большую часть времени выполняет уже принятое решение и не обязан заново решать всю strategic problem.

Каждый AI layer должен иметь:

- explicit decision cadence;
- event-driven wakeups для существенных изменений;
- bounded work budget;
- stable deterministic tie-breaking;
- возможность reuse/caching результатов, если inputs не изменились.

Stage 18/19 не должны вводить систему, где каждый стратегический корабль каждый simulation tick выполняет полный perception → planning → route search → economic evaluation pipeline.

---

# 9. Route/path computation и cache policy

Массовая логистика и fleet movement требуют shared route infrastructure.

Требования:

- маршруты вычисляются по versioned world/navigation graph;
- одинаковые/эквивалентные route queries могут использовать cache;
- cache имеет explicit invalidation при изменении graph topology/cost model;
- fleets/ships не пересчитывают неизменившийся маршрут каждый tick;
- дорогие path searches подлежат profiling и benchmark до ввода дополнительной complexity.

Cache не является authoritative source: authoritative остаются world graph, route policy и persistent order/transit state.

---

# 10. Time acceleration

Игровые режимы `×10`, `×50`, `×100` и другие high-speed modes **не должны реализовываться только как многократный запуск полного tactical update**.

При ускорении времени runtime должен использовать:

- event stepping;
- larger safe fixed steps там, где математика это допускает;
- scheduled completion events;
- aggregate/incremental updates;
- materialization только для действительно конфликтующих/наблюдаемых локальных ситуаций.

При этом ускорение времени не должно менять исход только из-за пропущенного gameplay-significant события.

Для систем, где exact high-frequency integration действительно необходима, должен существовать explicit boundary, после которой time acceleration ограничивается, либо применяется validated analytic/aggregate resolver.

---

# 11. Combat и strategic abstraction

Stage 17.5 обязан реализовать точную принятую Ship Mathematics v1.0 в детальном боевом runtime.

При этом из этого **не следует**, что каждый далёкий корабль галактики постоянно выполняет:

- detailed sensor measurements;
- covariance updates на tactical cadence;
- fire-control solutions;
- projectile integration;
- thermal substep simulation;
- full subsystem damage routing.

Detailed combat state materialized только тогда, когда существует реальное interaction domain, требующее этой детализации.

Если позднее будет введён strategic combat resolver, он обязан:

- быть отдельным явно названным approximation layer;
- быть calibrated против authoritative detailed model;
- иметь bounded domain validity;
- сохранять ключевые ресурсы и consequences;
- быть deterministic;
- не превращаться в скрытый `combatPower` multiplier, отменяющий ship design/fitting model.

---

# 12. Data layout, allocations и GC

До профилирования запрещается преждевременно переписывать runtime на сложные low-level structures только ради предполагаемой производительности.

Но для high-cardinality strategic data необходимо контролировать:

- object count;
- boxing;
- short-lived allocations;
- iterator/stream allocations в hot loops;
- cache locality;
- duplicated derived state;
- retention dormant runtime objects.

Если profiling показывает проблему, допустимы:

- primitive collections;
- packed arrays;
- structure-of-arrays для массовых однотипных strategic records;
- object pools там, где они измеримо полезны;
- specialised immutable snapshots.

Новая библиотека (`fastutil` и аналоги) добавляется только после benchmark, показывающего измеримый bottleneck и ожидаемую пользу.

---

# 13. Concurrency policy

Базовая authoritative simulation должна оставаться deterministic и максимально простой для воспроизведения ошибок.

Поэтому политика по умолчанию:

> **Сначала хороший single-thread deterministic baseline, затем параллелизм только по данным профилирования.**

Запрещается вводить concurrency только для того, чтобы временно скрыть архитектуру, которая делает слишком много работы.

Parallel simulation допускается, если определены:

- independent partitions/work units;
- deterministic input snapshots;
- explicit synchronization boundary;
- deterministic merge/reduction order;
- no gameplay-visible race dependence;
- replay/save/load acceptance.

Кандидаты на будущий parallelism: независимые sectors/systems, batch analytics, path queries, background derivation — только после доказательства необходимости.

---

# 14. Profiling workflow

Performance decisions должны приниматься по измерениям.

Базовый инженерный workflow:

```text
reproducible headless benchmark
→ measure
→ JFR/JDK Mission Control profile
→ identify hotspot/allocation source
→ change one architectural/implementation factor
→ rerun benchmark
→ record regression/improvement
```

JFR/JDK Mission Control являются development/profiling tooling, а не runtime dependency игры.

Минимально отслеживать:

- wall/CPU time per simulation domain;
- p50/p95/p99 simulation step time для relevant cadence;
- events processed per simulated hour/day;
- allocations per simulated time unit;
- GC count/pause/allocated bytes;
- live heap after stabilization;
- entity/materialization counts;
- route queries/cache hit rate;
- AI decisions per simulated hour;
- economy dirty/recomputed ratio;
- achieved simulated-time / real-time acceleration.

---

# 15. Versioned performance budget

Performance budget является таким же архитектурным контрактом, как save compatibility или deterministic acceptance. Он должен быть versioned и изменяться осознанно.

Начальный **target envelope**, который используется для проектирования и будущих benchmark seeds, но **не является заявлением о текущей гарантированной производительности**:

```text
Galaxy systems:                    10 000+
Stations / major facilities:       10 000–50 000
Economically active actors:        100 000+
Strategic ships / fleets:          100 000+
Locally materialized entities:     ~100–1 000
Full tactical entities:            ~100–500
```

Target envelope должен уточняться реальными benchmark после появления соответствующего content/runtime.

Нельзя снижать intended simulation depth только потому, что текущая реализация неэффективна, пока profiling не показал, что модель сама по себе непрактична.

---

# 16. Canonical headless benchmark scenarios

Stage 21 должен довести до CI/регулярного performance suite минимум следующие сценарии.

## S1 — Galaxy dormant scale

```text
10 000 systems
50 000 stations/facilities
100 000 economic actors
100 000 strategic ships/fleets
majority dormant/event-driven
```

Цель: доказать, что размер persistent universe сам по себе не создаёт proportional tactical update cost.

## S2 — Logistics/economy churn

Высокая доля одновременно изменяющихся inventories, routes, markets, production chains и contracts.

Цель: измерить dirty-state efficiency и стоимость реальной активности, а не пустого мира.

## S3 — Dense local combat

```text
100–500 full tactical entities
weapons/projectiles/sensors/EW/thermal/damage active
```

Цель: найти реальный tactical ceiling и деградацию frame/simulation budget.

## S4 — Accelerated long-run

Минимум один игровой год при high time acceleration с economy, logistics, AI, factions и event scheduler.

Цель: одновременно проверять throughput, memory stability, deterministic state, runaway queues и systemic invariants.

До появления полного content эти размеры могут scale down, но scenario shape должен сохраняться.

---

# 17. Deterministic scalability acceptance

Для масштабирования обязательны не только speed benchmarks, но и correctness gates.

Минимальный acceptance contract:

### Same-seed replay

```text
same seed
+ same initial content fingerprint
+ same command/event stream
= same final authoritative state hash
```

### Save/load equivalence

Долгий прогон с save/load boundary должен давать тот же authoritative result, что и непрерывный прогон, в пределах явно документированной numeric tolerance там, где она действительно нужна.

### Materialization round-trip

```text
strategic/dormant state
→ materialize
→ deterministic local no-op / bounded progression
→ dematerialize
```

не должен терять persistent identity, inventory, fitting, damage, orders, transit commitments или scheduled events.

### Render-rate independence

Разные render FPS не должны менять authoritative world result.

### Time-acceleration correctness

High-speed execution должен сохранять все gameplay-significant due events и documented invariants.

---

# 18. Performance regression governance

Пока baseline ещё формируется, benchmark может быть report-only.

После фиксации reference hardware/environment и stable scenarios вводятся versioned thresholds.

Изменение, например:

```text
strategic benchmark p95: 18 ms → 47 ms
heap after soak:          1.2 GB → 2.6 GB
allocation rate:          +150%
```

не должно проходить незамеченным.

Допустимая regression должна быть:

- объяснена;
- связана с measurable gameplay benefit или необходимой correctness change;
- отражена в обновлённом budget/baseline;
- не скрыта ослаблением теста без архитектурного решения.

---

# 19. Cross-stage gates

## Stage 17.5 — Combat Depth / Ship Fitting

До завершения Stage 17.5 должны быть определены seams между persistent ship state и local tactical runtime.

Stage 17.5 не считается архитектурно закрытым, если:

- Ship Mathematics требует существования каждого ship как постоянно тикающей tactical entity;
- materialization/dematerialization не имеет deterministic round-trip;
- render FPS влияет на authoritative combat/state;
- high time acceleration требует brute-force воспроизведения всех tactical ticks для dormant ships;
- detailed sensor/fire-control state невозможно выключить для нерелевантных interaction domains.

## Stage 18 — Tactical AI

Tactical AI может быть дорогим внутри active combat domain, но не должен автоматически работать на полном качестве для всех ships мира.

## Stage 19 — Fleets, strategic movement and large-scale coordination

Stage 19 обязан ввести/использовать:

- hierarchical orders;
- scheduled strategic decisions;
- route reuse/cache;
- event-driven transit;
- bounded materialization of local encounters.

## Stage 21 — Content breadth, balance and long-run stability

Stage 21 является основной **scalability closure stage**.

До его завершения должны существовать:

- canonical headless performance suite;
- long-run soak;
- versioned performance baseline/budget;
- allocation/GC profiling;
- time-acceleration benchmark;
- deterministic state-hash verification;
- economy/AI/logistics scale scenarios;
- documented current maximum safe local/tactical density;
- documented current strategic universe envelope.

Content breadth не считается закрытой, если nominal world size достигается только ценой постоянного excessive CPU/heap growth.

## Stage 22 — Large world generation / expansion

Любое увеличение заявленного размера мира должно опираться на Stage-21 scalability evidence.

World generation должна создавать persistent universe, который способен оставаться mostly dormant/event-driven без необходимости materialize весь мир после загрузки.

---

# 20. Запрещённые shortcut-подходы

Без отдельного architecture decision запрещены:

- `update()` для каждой существующей world entity каждый render frame;
- отдельная упрощённая «фейковая экономика» для distant sectors, не связанная с physical inventories;
- despawn distant ship с потерей persistent identity/state;
- strategic `combatPower` как единственная замена fitted ship capabilities;
- random skip ticks, меняющий результат в зависимости от FPS/CPU load;
- unlimited parallel tasks без deterministic merge contract;
- premature JNI/native rewrite без profiler evidence;
- микросервисы/отдельный backend только ради локальной single-player simulation performance;
- добавление второй ECS только ради количества entities;
- hidden performance multipliers, которые меняют игровые законы для AI/offscreen actors.

---

# 21. Definition of Done для Simulation Scalability Architecture v0.1

Контракт считается реально внедрённым, когда:

- [ ] Stage 17.5 runtime имеет persistent ↔ tactical materialization seam;
- [ ] scheduler поддерживает stable due-event ordering;
- [ ] strategic transit не требует постоянного tactical movement update;
- [ ] economy использует dirty/incremental recomputation там, где это семантически корректно;
- [ ] AI имеет явные decision cadences и event wakeups;
- [ ] route computation имеет reuse/cache/invalidation policy;
- [ ] high time acceleration имеет event/aggregate path;
- [ ] canonical headless benchmark seeds хранятся в репозитории;
- [ ] long-run deterministic soak включён в Stage 21 acceptance;
- [ ] JFR/profile procedure документирована и воспроизводима;
- [ ] performance metrics собираются автоматически или полуавтоматически;
- [ ] performance budget зафиксирован после benchmark calibration;
- [ ] regression policy включена в CI/acceptance governance;
- [ ] save/load + materialization round-trip покрыты acceptance tests;
- [ ] ни один крупный subsystem не предполагает, что render frame = world simulation tick.

---

# 22. Итоговый архитектурный инвариант

Целевая цепочка Star Empires:

```text
deterministic persistent world
→ scheduled/event-driven strategic simulation
→ simulation LOD
→ bounded local materialization
→ exact tactical model where required
→ deterministic dematerialization
→ headless benchmarks + invariants
→ profiling
→ measured optimization
```

Главная цель этого документа — позволить увеличивать **глубину и размер живого мира одновременно**, не превращая всю галактику в набор постоянно активных tactical entities и не отказываясь от единой физической/экономической парадигмы игры.
