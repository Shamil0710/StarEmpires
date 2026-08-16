# Star Empires — Simulation Scalability Architecture

> Версия: **0.3**  
> Статус: **cross-stage architectural contract**  
> Синхронизация: **2026-08-16**  
> Область действия: Stage 17.5, Stage 18, Stage 19, Stage 20, Stage 21, Stage 22, Stage 23 и все последующие системы, увеличивающие число одновременно существующих экономических, стратегических, RPG или физических сущностей.

## 1. Назначение

Star Empires должен одновременно сохранять глубокую authoritative simulation и масштабироваться до большого живого мира.

Глубина симуляции **не означает**, что каждая существующая сущность обязана постоянно быть materialized как полноценно активный runtime object и обновляться с tactical/render cadence.

Главный инвариант:

> **Весь мир не должен тикать с частотой tactical simulation или render loop.**

Этот документ не создаёт вторую упрощённую offscreen-симуляцию. Он определяет разные вычислительные представления **одной и той же authoritative реальности**.

## 2. Связь с принятыми контрактами

Scalability architecture дополняет, а не заменяет:

- `docs/development_roadmap.md` — канонический status/dependency roadmap;
- `docs/simulation_time_model.md` — authoritative simulation time;
- `docs/persistence_model.md` — save как value snapshot;
- `docs/economic_invariants.md` — conservation и physical economy;
- `docs/stage17_5_combat_depth_implementation_plan.md` — ship/combat runtime;
- `docs/stage18_resources_industry_infrastructure_plan.md` — resource/industry/facility ontology;
- `docs/stage20_physical_world_generation_plan.md` — физически калиброванный generated world;
- `docs/stage22_content_balance_plan.md` — content breadth и long-run soak.

Нельзя использовать scalability как оправдание для:

- player-only или AI-only правил;
- virtual deliveries;
- hidden resource grants;
- абстрактной второй экономики distant sectors;
- потери persistent identity при despawn/materialization;
- недетерминированного пропуска gameplay-significant событий;
- fake industrial output для distant systems.

## 3. Render rate не равен simulation rate

Графический цикл libGDX не является authoritative временем мира.

Запрещён базовый паттерн:

```text
render frame
→ update every fleet
→ update every ship
→ update every market
→ update every mine/refinery/factory
→ update every AI actor
→ update every NPC
```

Runtime должен иметь явные clock/scheduler boundaries.

Ориентиры являются design ranges, а не frozen gameplay constants:

| Layer | Типичная работа | Ориентир |
| --- | --- | --- |
| Tactical/local physical | combat, projectiles, detailed sensors, maneuver | fixed 30–60 Hz или validated physics tick |
| Active local/system | docking, nearby traffic, local movement/events | ~5–10 Hz или event-driven |
| Strategic/inter-system | fleets, orders, transit, high-level state | ~0.5–2 Hz или event-driven |
| Economy/industry/logistics | markets, extraction, production, cargo flow | scheduled/dirty updates |
| Macro/politics/war | doctrine, mobilization, policy review | редкая deterministic cadence |
| RPG/living-world | NPC intent, missions, reputation consequences | event/cadence by relevance |
| Dormant | отсутствие direct interaction relevance | event-only/on-demand |

## 4. Simulation LOD

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

LOD — не только visual optimization. Он определяет required computation при сохранении authoritative persistent state.

Примеры:

- далёкий freighter не интегрирует позицию каждый tactical tick;
- стабильный market не пересчитывается без dirty input/due settlement;
- refinery/factory не пересчитывает неизменившийся recipe every frame;
- fleet executor выполняет order, а не полный strategic plan every tick;
- NPC без relevance не запускает full behavior tree;
- detailed combat state существует только внутри real interaction domain.

## 5. Persistent identity не равна Ashley Entity

Ashley ECS остаётся runtime composition layer, но не является обязательным контейнером для каждой сущности галактики.

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
- versioned при schema changes;
- покрыта round-trip acceptance.

Запрещается хранить сотни тысяч dormant ships/NPCs/facilities в полном tactical component set только потому, что локальная сущность использует его.

## 6. Materialization contract

Для scalable entity family определить:

1. persistent minimal state;
2. derived reconstructable state;
3. local runtime state;
4. activation/materialization conditions;
5. dematerialization conditions;
6. interrupted-process continuation semantics;
7. save/load representation;
8. state-hash coverage.

При materialization нельзя бесплатно:

- пополнять ammunition/reaction mass/cargo;
- пополнять industrial inputs/output;
- восстанавливать depleted resource reserve;
- ремонтировать damage;
- сбрасывать orders/cooldowns;
- менять ownership/affiliation;
- терять scheduled commitments.

## 7. Event-driven transit и long processes

Долгие процессы моделируются через state + due event там, где постоянная интеграция не добавляет значимого результата.

Пример transit:

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

Тот же принцип применим к:

- construction completion;
- extraction/production batches;
- repair/refit completion;
- contracts;
- policy review;
- FTL phases;
- fleet orders;
- mission/NPC deadlines;
- industrial maintenance events.

## 8. Scheduler contract

Simulation scheduler должен поддерживать разные cadence domains и stable ordering.

Минимальные требования:

- simulation time отделено от wall-clock/render time;
- equal timestamp events имеют deterministic ordering;
- tie-break использует explicit priority + stable ID;
- subsystem может сообщать next due time вместо polling;
- expensive work может распределяться по cadence, если semantics допускают;
- gameplay-significant scheduler state переживает save/load;
- same seed + initial state + command/event stream дают одинаковый authoritative result независимо от render FPS.

## 9. Dirty-state economy / industry

Экономическая и промышленная глубина не требует полного пересчёта всех markets/facilities/orders каждый economy tick.

State становится dirty, например, когда:

- inventory изменился;
- cargo прибыл/убыл;
- resource reserve/grade доступность изменилась;
- recipe/capability изменился;
- facility получила damage/repair;
- input availability/price изменились существенно;
- order создан/закрыт;
- legal access/route/territory state изменился;
- наступил scheduled extraction/production/settlement event.

Если inputs не изменились и due event отсутствует, повторное вычисление того же результата избегается.

Это оптимизация вычисления, **не отмена физических consequences**.

## 10. AI scheduling и decision hierarchy

AI work не должен масштабироваться как `all actors × full planning × every tick`.

```text
Faction / polity intent
→ strategic planning
→ fleet / industrial / organization orders
→ local controller
→ ship/facility/NPC execution
```

Каждый layer должен иметь:

- explicit decision cadence;
- event-driven wakeups;
- bounded work budget;
- deterministic tie-breaking;
- hysteresis/commitment rules;
- reuse/cache при неизменных inputs.

## 11. Route/path cache policy

Mass logistics и fleet movement используют shared route infrastructure.

Требования:

- route queries опираются на versioned navigation/world graph;
- equivalent requests могут использовать cache;
- topology/cost/access changes invalidируют affected entries;
- ships/fleets не пересчитывают неизменившийся route each tick;
- cache не является authoritative source.

Stage-20 generation обязана иметь explicit topology version/invalidation semantics.

## 12. High time acceleration

`×10`, `×50`, `×100` нельзя реализовывать только многократным запуском полного tactical update.

Предпочтительные механизмы:

- event stepping;
- larger safe deterministic steps;
- scheduled completion events;
- aggregate/incremental updates;
- selective materialization;
- validated analytic resolver where semantically equivalent.

Ни одно gameplay-significant due event не исчезает из-за acceleration.

## 13. Combat и interaction domains

Stage 17.5 реализует подробную Ship Mathematics там, где подробность нужна.

Detailed tactical state materialized только при наличии реального combat/sensor interaction domain.

Strategic combat resolver, если позднее нужен, обязан:

- быть explicit approximation layer;
- быть calibrated против detailed model;
- иметь bounded domain validity;
- сохранять ammunition, damage, losses, reaction mass and economic consequences;
- быть deterministic;
- не сводить design к одному hidden `combatPower` multiplier.

## 14. Industrial scalability boundary

Stage 18 вводит large-cardinality resource occurrences/facilities/recipes, поэтому отдельно фиксируется:

- dormant deposit не требует every-frame depletion calculation;
- extraction/production work может быть scheduled by batch/next-due semantics;
- reserve state authoritative и finite;
- facility capability remains persistent without full local ECS materialization;
- aggregated distant throughput должен использовать те же recipe/input/output/work constraints;
- materialization не создаёт missing inputs/output;
- world generation не materialize-all deposits/facilities после load.

Если strategic production resolver отличается от local detailed presentation, он обязан сохранять exact material accounting within declared deterministic semantics.

## 15. Data layout, allocations и GC

Performance work начинается с profiling.

Для high-cardinality data отслеживаются:

- object count;
- boxing;
- short-lived allocations;
- hot-loop iterator/stream allocations;
- cache locality;
- duplicated derived state;
- retained dormant runtime objects.

После profiler evidence допустимы primitive collections, packed arrays, structure-of-arrays, pools и specialised immutable snapshots.

## 16. Concurrency policy

> **Сначала хороший single-thread deterministic baseline, затем parallelism только по profiler evidence.**

Parallel simulation требует:

- independent partitions/work units;
- deterministic input snapshots;
- explicit synchronization boundary;
- deterministic merge/reduction order;
- no gameplay-visible race dependence;
- replay/save/load acceptance.

## 17. Profiling workflow

```text
reproducible headless benchmark
→ measure
→ JFR / JDK Mission Control profile
→ identify hotspot/allocation source
→ change one factor
→ rerun benchmark
→ record regression/improvement
```

Минимальные metrics:

- CPU/wall time per domain;
- p50/p95/p99 step/cadence time;
- events per simulated hour/day;
- allocations per simulated time;
- GC count/pause/bytes;
- live heap after stabilization;
- materialized entity counts;
- route query/cache hit rate;
- AI decisions per simulated hour;
- economy/industry dirty/recomputed ratio;
- achieved simulated-time / real-time acceleration.

## 18. Versioned target envelope

Начальный design envelope, не current guarantee:

```text
Galaxy systems:                    10 000+
Stations / major facilities:       10 000–50 000
Resource occurrences:              100 000+
Economically active actors:        100 000+
Strategic ships / fleets:          100 000+
Persistent NPC records:            100 000+
Locally materialized entities:     ~100–1 000
Full tactical entities:            ~100–500
```

Числа уточняются benchmark evidence по мере появления runtime/content.

## 19. Canonical headless benchmark scenarios

### S1 — dormant universe scale

```text
10 000 systems
50 000 stations/facilities
100 000+ resource occurrences
100 000 economic actors
100 000 strategic ships/fleets
100 000 persistent NPC records
majority dormant/event-driven
```

### S2 — logistics/economy/industry churn

Высокая доля changing inventories, deposits, routes, markets, extraction, production chains, facilities and contracts.

Цель: измерить cost of real activity и dirty-state efficiency.

### S3 — dense local combat

```text
100–500 tactical entities
weapons/projectiles/sensors/EW/thermal/damage active
```

### S4 — accelerated long-run

Минимум один игровой год при high acceleration с economy, industry, logistics, AI, factions and scheduler.

### S5 — materialization/save-load churn

Повторяющиеся:

```text
strategic → local → tactical → strategic
save → load
route interruption/resume
production interruption/resume
combat damage/consumable continuation
```

## 20. Deterministic scalability acceptance

### Same-seed replay

```text
same seed
+ same content fingerprint
+ same initial state
+ same command/event stream
= same authoritative state hash
```

### Save/load equivalence

Long run с save/load boundary даёт тот же authoritative result, что uninterrupted run, кроме explicitly documented numeric tolerance where necessary.

### Materialization round-trip

Не теряет identity, inventory, fitting, damage, orders, resource reserve, facility/production state, transit commitments, policy/diplomacy references или scheduled events.

### Render-rate independence

Different render FPS не меняют authoritative world result.

### Time-acceleration correctness

High-speed execution сохраняет gameplay-significant due events and invariants.

## 21. Performance regression governance

После фиксации stable scenarios/environment вводятся versioned thresholds.

Regression должна быть исправлена или явно объяснена/принята как conscious tradeoff. Нельзя скрывать regression ослаблением теста.

## 22. Cross-stage gates

### Stage 17H — current transition gate

Текущий приоритет не меняется: Stage 17H завершает migration/end-to-end contract Stage 17 и save compatibility.

### Stage 17.5 — Combat Depth / Ship Fitting Foundation

Требует persistent ↔ tactical seams, deterministic materialization, render-rate independence and bounded detailed interaction domains.

### Stage 18 — Resources / Industry / Infrastructure

Scalability requirements:

- deposits/facilities have persistent state separate from local ECS;
- extraction/production support due-event/batch semantics;
- finite reserves and inventories survive save/load;
- dirty/incremental recomputation avoids all-facility polling;
- facility capability is data state, not runtime class bonus;
- industrial acceptance runs headless and deterministic.

### Stage 19 — strategic warfare

- tactical AI full quality only in active combat domains;
- strategic mobilization/war planning bounded cadence/event wakeups;
- fleet hierarchy passes orders down;
- blockades/fronts/industrial-loss consequences update through explicit state changes;
- resolver if needed calibrated and physically conservative.

### Stage 20 — physical world generation

Generated universe remains mostly dormant/event-driven.

Requires:

- versioned navigation topology;
- physical travel-time calibration;
- route-cache invalidation;
- bounded local materialization;
- resource/facility density within performance envelope;
- no materialize-all-on-load.

### Stage 21 — RPG / living world

- persistent NPC identity separate from local presentation;
- relevance/cadence/event wakeups;
- deterministic mission/world-event deadlines;
- local materialization only where needed;
- no omniscient instant reactions;
- save/load continuation.

### Stage 22 — Content & Balance Alpha

Primary calibration/soak stage.

Before completion require:

- canonical headless performance suite;
- long-run deterministic soak;
- versioned performance baseline/budget;
- allocation/GC profiling;
- time-acceleration benchmark;
- state-hash verification;
- economy/industry/AI/logistics/content-scale scenarios;
- documented safe local/tactical density;
- documented strategic universe envelope.

### Stage 23 — Polish / Release Candidate

No new foundational scalability model. Close:

- final profiler pass;
- stable regression thresholds;
- startup/load/materialization performance;
- save/migration diagnostics;
- leak/long-session checks;
- UI/render boundaries;
- documented supported RC envelope.

## 23. Запрещённые shortcuts

Без explicit architecture decision запрещены:

- `update()` for every world entity every render frame;
- fake distant economy/industry;
- despawn with identity/state loss;
- strategic `combatPower` as sole ship model;
- random tick skipping dependent on FPS/CPU;
- unlimited nondeterministic parallel tasks;
- premature JNI/native rewrite;
- microservices only for local single-player performance;
- second ECS only for quantity;
- hidden AI/offscreen multipliers changing laws;
- full strategic planning per subordinate each tick;
- materialize-all-on-load;
- generator-created emergency resources outside Stage-18 ontology;
- distant factories receiving virtual inputs.

## 24. Definition of Done для v0.3 contract

Контракт внедрён по мере roadmap, когда:

- [ ] Stage 17.5 has persistent ↔ tactical seam;
- [ ] Stage 18 deposits/facilities support persistent scheduled simulation;
- [ ] scheduler has stable due-event ordering;
- [ ] strategic transit avoids constant tactical movement update;
- [ ] economy/industry use dirty/incremental recomputation where correct;
- [ ] AI layers have decision cadences/event wakeups;
- [ ] route computation has cache/invalidation policy;
- [ ] high acceleration has event/aggregate path;
- [ ] Stage 20 generated universe can remain mostly dormant;
- [ ] Stage 21 NPC layer avoids full per-frame NPC simulation;
- [ ] canonical benchmark seeds/scenarios are versioned;
- [ ] Stage 22 includes long-run deterministic scalability soak;
- [ ] profiling procedure reproducible;
- [ ] performance budget versioned;
- [ ] regression governance part of release process;
- [ ] save/load + materialization round-trip acceptance exists;
- [ ] no major subsystem assumes `render frame = world simulation tick`.

## 25. Итоговый архитектурный инвариант

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

Главная цель — увеличивать одновременно **глубину** и **размер** живого мира, не превращая галактику в набор постоянно активных tactical entities и не отказываясь от единой физической, экономической и политической парадигмы Star Empires.