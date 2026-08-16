# Star Empires — Simulation Scalability Architecture

> Версия: **0.2**  
> Статус: **cross-stage architectural contract**  
> Синхронизация: **2026-08-16**  
> Источник: пересмотр draft PR #125 на актуальном roadmap после Stage 17G.  
> Область действия: Stage 17.5, Stage 18, Stage 19, Stage 20, Stage 21, Stage 22 и все последующие системы, увеличивающие число одновременно существующих экономических, стратегических, RPG или физических сущностей.

## 1. Назначение

Star Empires должен одновременно сохранять глубокую authoritative simulation и масштабироваться до большого живого мира.

Глубина симуляции **не означает**, что каждая существующая сущность обязана постоянно быть materialized как полноценно активный runtime object и обновляться с tactical/render cadence.

Главный инвариант:

> **Весь мир не должен тикать с частотой tactical simulation или render loop.**

Этот документ не создаёт вторую упрощённую offscreen-симуляцию. Он определяет разные вычислительные представления **одной и той же authoritative реальности**.

## 2. Связь с уже принятыми контрактами

Scalability architecture дополняет, а не заменяет существующие правила:

- `docs/development_roadmap.md` — канонический status/dependency roadmap;
- `docs/simulation_time_model.md` — authoritative simulation time;
- `docs/persistence_model.md` — save является value snapshot, а не сериализованным Ashley object graph;
- `docs/economic_invariants.md` — conservation и physical economy;
- `docs/stage17_5_combat_depth_implementation_plan.md` — будущий production ship/combat runtime;
- `docs/stage19_physical_world_generation_plan.md` — физически калиброванный мир;
- `docs/stage21_content_balance_plan.md` — content breadth и long-run soak.

Нельзя использовать scalability как оправдание для:

- player-only или AI-only правил;
- virtual deliveries;
- hidden resource grants;
- абстрактной второй экономики distant sectors;
- потери persistent identity при despawn/materialization;
- недетерминированного пропуска gameplay-significant событий.

## 3. Render rate не равен simulation rate

Графический цикл libGDX не является authoritative временем мира.

Запрещён базовый паттерн:

```text
render frame
→ update every fleet
→ update every ship
→ update every market
→ update every factory
→ update every AI actor
→ update every NPC
```

Runtime должен иметь явные clock/scheduler boundaries.

Ориентиры ниже являются design ranges, а не замороженными gameplay constants:

| Layer | Типичная работа | Ориентир |
| --- | --- | --- |
| Tactical/local physical | бой, projectiles, detailed sensors, local maneuver | fixed 30–60 Hz или другой validated physics tick |
| Active local/system | docking, nearby traffic, local movement/events | ~5–10 Hz или event-driven |
| Strategic/inter-system | fleets, orders, transit, high-level state | ~0.5–2 Hz или event-driven |
| Economy/logistics | markets, production, cargo flow | scheduled/dirty updates |
| Macro/politics | doctrine, expansion, policy review | редкая deterministic cadence |
| RPG/living-world | NPC intent, missions, reputation consequences | event/cadence by relevance |
| Dormant | отсутствие непосредственной interaction relevance | event-only/on-demand |

Конкретная subsystem может использовать другой cadence после correctness/performance evidence.

## 4. Simulation LOD

Каждая high-cardinality сущность или subsystem должна иметь явный simulation level-of-detail.

Минимальная модель:

```text
DORMANT
  ↓ due event / strategic relevance
STRATEGIC
  ↓ local relevance
ACTIVE_LOCAL
  ↓ direct tactical interaction
TACTICAL
```

Обратный переход обязателен:

```text
TACTICAL
→ ACTIVE_LOCAL
→ STRATEGIC
→ DORMANT
```

LOD — не только visual optimization. Он определяет, какие вычисления реально нужны, при сохранении authoritative persistent state.

Примеры:

- далёкий торговый корабль не интегрирует позицию каждый tactical tick;
- стабильный market не пересчитывается без dirty input или due settlement;
- fleet executor выполняет существующий order, а не полный strategic planning каждый tick;
- NPC без локальной или сюжетно-системной relevance не запускает full behavior tree каждый кадр;
- detailed combat state существует только внутри реального interaction domain.

## 5. Persistent identity не равна Ashley Entity

Ashley ECS остаётся runtime composition layer, но не является обязательным контейнером для каждой сущности галактики.

Каноническая persistent identity должна существовать независимо от materialization:

```text
Persistent World State
        ↓ materialize
Local Runtime / Ashley Entity
        ↓ dematerialize
Persistent World State
```

Materialization/dematerialization обязана быть:

- deterministic;
- lossless относительно authoritative state;
- stable-ID preserving;
- versioned при изменении persistence schema;
- покрыта round-trip acceptance.

Запрещается хранить сотни тысяч dormant ships/NPCs/facilities в полном tactical component set только потому, что локальная сущность использует его.

## 6. Materialization contract

Для каждого scalable entity family необходимо явно определить:

1. persistent minimal state;
2. derived state, который можно восстановить;
3. local runtime state;
4. activation/materialization conditions;
5. dematerialization conditions;
6. interrupted-process continuation semantics;
7. save/load representation;
8. state-hash coverage.

При materialization нельзя бесплатно:

- пополнять ammunition/reaction mass/cargo;
- ремонтировать damage;
- сбрасывать orders/cooldowns;
- менять ownership/affiliation;
- терять scheduled commitments.

## 7. Event-driven transit и scheduled transitions

Долгие процессы моделируются через state + due event там, где постоянная интеграция не добавляет значимого результата.

Пример transit state:

```text
TransitState
- departureTime
- origin
- destination
- route/trajectory reference
- arrivalTime
- consumable commitments
- interruption conditions
```

Scheduler может зарегистрировать:

```text
ARRIVAL(entityId, arrivalTime)
```

До due event полный movement update не требуется, если объект не становится локально релевантным.

Тот же принцип применяется к:

- construction completion;
- repair/refit completion;
- production batches;
- contract deadlines;
- policy review cadence;
- FTL phases;
- scheduled fleet orders;
- long logistics operations;
- mission/NPC deadlines и world events.

## 8. Scheduler contract

Simulation scheduler должен поддерживать разные cadence domains и stable ordering.

Минимальные требования:

- simulation time отделено от wall-clock/render time;
- due events с одинаковым timestamp имеют deterministic ordering;
- tie-break использует explicit priority + stable ID, а не hash iteration order;
- subsystem может сообщать next due time вместо постоянного polling;
- expensive work может распределяться по cadence, если semantics это допускают;
- gameplay-significant scheduler state переживает save/load;
- одинаковый seed + initial state + command/event stream дают одинаковый authoritative result независимо от render FPS.

## 9. Dirty-state economy и incremental recomputation

Экономическая глубина не требует полного пересчёта всех markets/factories/orders каждый economy tick.

State становится dirty при существенном изменении inputs, например:

- physical inventory изменился;
- cargo прибыл/убыл;
- recipe/capability изменился;
- order создан/закрыт;
- input availability/price существенно изменились;
- facility получила damage/repair;
- legal access/route/territory state изменился;
- наступил scheduled settlement/production event.

Если inputs не изменились и due event отсутствует, повторное вычисление того же результата должно избегаться.

Это оптимизация вычисления, **не отмена физических economic consequences**.

## 10. AI scheduling и decision hierarchy

AI work не должен масштабироваться как `all actors × full planning × every tick`.

Предпочтительная hierarchy:

```text
Faction / polity intent
→ strategic planning
→ fleet / organization orders
→ local controller
→ ship/NPC execution
```

Каждый AI layer должен иметь:

- explicit decision cadence;
- event-driven wakeups;
- bounded work budget;
- deterministic tie-breaking;
- hysteresis/commitment rules;
- reuse/cache результатов при неизменных inputs.

Обычный executor большую часть времени выполняет уже принятое решение.

## 11. Route/path cache policy

Mass logistics и fleet movement используют shared route infrastructure.

Требования:

- route queries опираются на versioned navigation/world graph;
- эквивалентные запросы могут использовать cache;
- topology/cost/access changes явно invalidируют affected entries;
- fleets/ships не пересчитывают неизменившийся маршрут каждый tick;
- cache не является authoritative source — authoritative остаются graph, route policy и persistent order/transit state.

## 12. High time acceleration

Режимы `×10`, `×50`, `×100` и другие high-speed modes нельзя реализовывать только многократным запуском полного tactical update.

Предпочтительные механизмы:

- event stepping;
- larger safe deterministic steps;
- scheduled completion events;
- aggregate/incremental updates;
- selective materialization;
- validated analytic resolver там, где он эквивалентен required model semantics.

Если система действительно требует high-frequency integration, должен существовать explicit boundary, ограничивающий acceleration или переводящий interaction в validated resolver.

Ни одно gameplay-significant due event не может исчезнуть из-за ускорения времени.

## 13. Combat и interaction domains

Stage 17.5 реализует подробную Ship Mathematics v1.0 там, где подробность нужна.

Из этого не следует, что каждый далёкий корабль постоянно выполняет:

- detailed sensor measurements;
- covariance updates;
- fire-control calculations;
- projectile integration;
- thermal substeps;
- subsystem damage routing.

Detailed tactical state materialized только при наличии реального combat/sensor interaction domain.

Если позднее вводится strategic combat resolver, он обязан:

- быть явно названным approximation layer;
- быть calibrated против detailed authoritative model;
- иметь bounded domain validity;
- сохранять ammunition, damage, losses, fuel/reaction mass и economic consequences;
- быть deterministic;
- не сводить ship design к одному скрытому `combatPower` multiplier.

## 14. Data layout, allocations и GC

Performance work начинается с profiling, а не с premature low-level rewrite.

Для high-cardinality strategic data отслеживаются:

- object count;
- boxing;
- short-lived allocations;
- iterator/stream allocations в hot loops;
- cache locality;
- duplicated derived state;
- retention dormant runtime objects.

После profiler evidence допустимы:

- primitive collections;
- packed arrays;
- structure-of-arrays;
- object pools при измеримой пользе;
- specialised immutable snapshots.

Новая dependency ради производительности добавляется только после benchmark evidence.

## 15. Concurrency policy

Default policy:

> **Сначала хороший single-thread deterministic baseline, затем parallelism только по profiler evidence.**

Concurrency не должна маскировать архитектуру, которая просто делает слишком много работы.

Parallel simulation требует:

- independent partitions/work units;
- deterministic input snapshots;
- explicit synchronization boundary;
- deterministic merge/reduction order;
- отсутствия gameplay-visible race dependence;
- replay/save/load acceptance.

## 16. Profiling workflow

Базовый цикл:

```text
reproducible headless benchmark
→ measure
→ JFR / JDK Mission Control profile
→ identify hotspot/allocation source
→ change one factor
→ rerun benchmark
→ record regression/improvement
```

JFR/JMC — development tooling, не runtime dependency.

Минимальные metrics:

- CPU/wall time per simulation domain;
- p50/p95/p99 step/cadence time;
- events processed per simulated hour/day;
- allocations per simulated time unit;
- GC count/pause/allocated bytes;
- live heap after stabilization;
- materialized entity counts;
- route queries/cache hit rate;
- AI decisions per simulated hour;
- economy dirty/recomputed ratio;
- achieved simulated-time / real-time acceleration.

## 17. Versioned performance target envelope

Начальный target envelope используется для проектирования и benchmark calibration, но **не является заявлением о текущей гарантированной производительности**:

```text
Galaxy systems:                    10 000+
Stations / major facilities:       10 000–50 000
Economically active actors:        100 000+
Strategic ships / fleets:          100 000+
Persistent NPC records:            100 000+
Locally materialized entities:     ~100–1 000
Full tactical entities:            ~100–500
```

После появления соответствующего runtime/content эти числа заменяются или уточняются versioned benchmark evidence.

Нельзя автоматически снижать intended simulation depth только потому, что текущая реализация неэффективна; сначала требуется profiler evidence.

## 18. Canonical headless benchmark scenarios

### S1 — dormant universe scale

```text
10 000 systems
50 000 stations/facilities
100 000 economic actors
100 000 strategic ships/fleets
100 000 persistent NPC records
majority dormant/event-driven
```

Цель: размер persistent universe не должен создавать proportional tactical update cost.

### S2 — logistics/economy churn

Высокая доля изменяющихся inventories, routes, markets, production chains и contracts.

Цель: измерить реальную стоимость activity и эффективность dirty-state processing.

### S3 — dense local combat

```text
100–500 tactical entities
weapons/projectiles/sensors/EW/thermal/damage active
```

Цель: определить local tactical ceiling и degradation curve.

### S4 — accelerated long-run

Минимум один игровой год при high time acceleration с economy, logistics, AI, factions и scheduler.

Цель: throughput, memory stability, bounded queues, determinism и systemic invariants.

### S5 — materialization/save-load churn

Повторяющиеся:

```text
strategic → local → tactical → strategic
save → load
route interruption/resume
combat damage/consumable continuation
```

Цель: доказать отсутствие identity/state leaks и hidden reset effects.

До появления полного content размеры могут scale down, но scenario shape сохраняется.

## 19. Deterministic scalability acceptance

### Same-seed replay

```text
same seed
+ same content fingerprint
+ same initial state
+ same command/event stream
= same authoritative state hash
```

### Save/load equivalence

Long run с save/load boundary даёт тот же authoritative result, что и uninterrupted run, кроме явно документированной numeric tolerance там, где она действительно необходима.

### Materialization round-trip

```text
strategic/dormant state
→ materialize
→ bounded local progression
→ dematerialize
```

не теряет identity, inventory, fitting, damage, orders, transit commitments, policy/diplomacy references или scheduled events.

### Render-rate independence

Разные render FPS не меняют authoritative world result.

### Time-acceleration correctness

High-speed execution сохраняет все gameplay-significant due events и invariants.

## 20. Performance regression governance

Пока baseline формируется, benchmarks могут быть report-only.

После фиксации stable scenarios/environment вводятся versioned thresholds.

Регрессия типа:

```text
strategic benchmark p95: 18 ms → 47 ms
heap after soak:          1.2 GB → 2.6 GB
allocation rate:          +150%
```

должна быть либо исправлена, либо явно объяснена и принята как conscious tradeoff с обновлением baseline.

Нельзя скрывать regression простым ослаблением теста.

## 21. Cross-stage gates по актуальному roadmap

### Stage 17H — current transition gate

Этот документ **не меняет** текущий приоритет: Stage 17H остаётся следующим обязательным этапом.

17H должен завершить migration/end-to-end contract Stage 17 и подтвердить save compatibility. Scalability runtime не должен внедряться ценой обхода этого gate.

### Stage 17.5 — Combat Depth / Ship Fitting Foundation

До завершения 17.5 должны существовать seams между persistent ship state и local tactical runtime.

17.5 не считается архитектурно закрытым, если:

- Ship Mathematics требует постоянного tactical tick для каждого ship мира;
- materialization/dematerialization не имеет deterministic round-trip;
- render FPS влияет на authoritative combat;
- dormant ships требуют brute-force tactical replay при time acceleration;
- detailed sensors/fire-control невозможно отключать вне relevant interaction domain.

### Stage 18 — strategic warfare / coercive diplomacy / advanced combat behavior

Stage 18 использует Stage-17 political state и Stage-17.5 physical capabilities.

Scalability requirements:

- tactical AI full quality работает только в active combat domains;
- strategic mobilization/war planning имеет bounded cadence/event wakeups;
- fleet hierarchy передаёт orders вниз вместо полного faction replanning каждым ship;
- blockades/fronts/threat intelligence обновляются через explicit state changes;
- strategic resolver, если нужен, calibrated против detailed model и сохраняет физические losses/consumables.

### Stage 19 — physically calibrated world generation

Stage 19 должен генерировать мир, который масштабируется как mostly dormant/event-driven persistent universe.

Требуется:

- versioned navigation topology;
- physical travel-time calibration;
- route-cache invalidation semantics;
- bounded local materialization;
- generated density, совместимая с target performance envelope;
- отсутствие требования materialize весь generated universe после load.

### Stage 20 — RPG / living-world layer

NPCs, missions, discovery и reputation не должны превращаться в `all NPCs × full AI × every tick`.

Требуется:

- persistent NPC identity отдельно от local presentation entity;
- relevance/cadence/event wakeups;
- deterministic mission/world-event deadlines;
- local materialization only where interaction requires it;
- no omniscient instant reactions to distant events;
- save/load continuation NPC/mission state.

### Stage 21 — Content & Balance Alpha

Stage 21 является основной calibration/soak stage для universe scale.

До завершения Stage 21 должны существовать:

- canonical headless performance suite;
- long-run deterministic soak;
- versioned performance baseline/budget;
- allocation/GC profiling;
- time-acceleration benchmark;
- state-hash verification;
- economy/AI/logistics/content-scale scenarios;
- documented safe local/tactical density;
- documented strategic universe envelope.

Content breadth не считается закрытой, если nominal world size достигается только ценой runaway CPU/heap/queue growth.

### Stage 22 — Polish / Release Candidate

Stage 22 не проектирует новую фундаментальную scalability model. Он закрывает release hardening:

- final profiler pass на representative content;
- stable regression thresholds;
- startup/load/materialization performance;
- save hardening и migration diagnostics;
- memory leak/long-session checks;
- UI/render boundaries без нарушения simulation determinism;
- documented supported performance envelope для RC.

## 22. Запрещённые shortcut-подходы

Без explicit architecture decision запрещены:

- `update()` для каждой world entity каждый render frame;
- отдельная fake economy для distant sectors;
- despawn с потерей persistent identity/state;
- strategic `combatPower` как единственная замена fitted capabilities;
- random tick skipping, зависящий от FPS/CPU load;
- unlimited parallel tasks без deterministic merge contract;
- premature JNI/native rewrite без profiler evidence;
- микросервисная архитектура только ради local single-player performance;
- вторая ECS только ради количества entities;
- hidden AI/offscreen performance multipliers, меняющие игровые законы;
- full strategic AI planning для каждого subordinate actor каждый tick;
- materialize-all-on-load architecture.

## 23. Definition of Done для v0.2 contract

Контракт считается реально внедрённым по мере развития roadmap, когда:

- [ ] Stage 17.5 имеет persistent ↔ tactical materialization seam;
- [ ] scheduler поддерживает stable due-event ordering;
- [ ] strategic transit не требует постоянного tactical movement update;
- [ ] economy использует dirty/incremental recomputation там, где это корректно;
- [ ] AI layers имеют decision cadences и event wakeups;
- [ ] route computation имеет cache/invalidation policy;
- [ ] high time acceleration имеет event/aggregate path;
- [ ] Stage 19 generated universe может оставаться mostly dormant;
- [ ] Stage 20 NPC layer не требует full per-frame NPC simulation;
- [ ] canonical benchmark seeds/scenarios хранятся в репозитории;
- [ ] Stage 21 включает long-run deterministic scalability soak;
- [ ] profiling procedure воспроизводима;
- [ ] performance budget versioned;
- [ ] regression governance включена в release process;
- [ ] save/load + materialization round-trip покрыты acceptance;
- [ ] ни один major subsystem не предполагает `render frame = world simulation tick`.

## 24. Итоговый архитектурный инвариант

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

Главная цель — позволить одновременно увеличивать **глубину** и **размер** живого мира, не превращая всю галактику в набор постоянно активных tactical entities и не отказываясь от единой физической, экономической и политической парадигмы Star Empires.
