# Stage 7 — World hierarchy and simulation tiers: verification

Статус документа: **READY FOR PR**. Stage 7 считается `COMPLETE` только после merge feature-ветки в `main` и зелёного post-merge CI.

## Scope

Stage 7 вводит persistent world-layer поверх уже существующей deterministic `SimulationSession`, не создавая вторую экономическую реализацию.

Реализовано:

- typed stable IDs и hierarchy `Galaxy -> Sector -> StarSystem`;
- deterministic `JumpConnection` и system-neighbor indexes;
- persistent `PlanetNode` и `AsteroidFieldNode` со stable IDs и lookup `landmark -> parent StarSystem`;
- stations, fleets и individual asteroids продолжают жить как локальные ECS entities внутри `SimulationSession` конкретной системы;
- `WorldState` содержит canonical topology и ровно один authoritative `GameState` для каждой StarSystem;
- `WorldStateCodec` сохраняет topology + system snapshots, используя существующий `GameStateCodec` как единственный economic serialization layer;
- `WorldPersistence` сохраняет content fingerprint, active system и scheduler cadence/budget;
- legacy `STEC` и raw `STEM` single-session saves читаются без изменения `GameState` и оборачиваются в default single-system world;
- `WorldSimulation` исполняет active StarSystem на полном fixed rate и удалённые системы coarse strategic updates;
- remote scheduler имеет жёсткий per-frame update budget, largest-lag-first selection и stable `StarSystemId` tie-break;
- scheduler не имеет hidden mutable cursor: continuation определяется persistent clocks + system IDs;
- production `DemoGalaxyFactory` создаёт 3 экономически живые системы и 2 jump connections;
- desktop `SpaceSimGame` больше не собирает отдельный Ashley economic pipeline и использует тот же `WorldSimulation`/`SimulationSession`, что headless tests и benchmark.

## Simulation frequency contract

Local fixed step остаётся `0.1 s` и не изменён.

Default strategic cadence:

- `WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS = 10`;
- один remote `Engine.update()` представляет 10 эквивалентных local ticks, то есть `1.0 s` game time;
- default remote budget: `8` coarse updates на один world frame.

Это намеренная reduced-fidelity граница: remote system не симулирует каждый объект на каждом `0.1 s` local tick, но использует тот же economic Engine и те же systems с агрегированным delta.

`WorldSimulationTest.remoteEconomyЖивётПриМеньшемЧислеObjectLevelUpdates` подтверждает количественно:

- 200 local fixed ticks active system;
- две remote systems также достигают tick `200`;
- всего 40 remote coarse updates, по 20 на систему;
- следовательно каждая remote system получает в 10 раз меньше object-level Engine updates, чем active system на том же game-time interval;
- remote ledgers не пусты;
- inventories остаются неотрицательными.

## Determinism and budget

`WorldSimulation` выбирает remote system с максимальным lag. При равном lag используется canonical ascending `StarSystemId`.

При budget `1` и трёх remote systems тест подтверждает порядок `2 -> 3 -> 4`. Невыполненная работа остаётся выражена как clock lag и догоняется последующими frames; она не теряется.

Save/load continuation test выполняет world simulation до сохранения, проходит `WorldStateCodec`, восстанавливает независимый runtime и затем сравнивает полный `WorldState` после каждого последующего цикла. Расхождений нет.

## Persistence boundary

Stage 7 намеренно **не меняет** `GameState` schema и `GameStateCodec`.

Это архитектурное решение сохраняет Stage 3–6 single-session compatibility и держит уровни ответственности раздельно:

- `GameState` — authoritative состояние одной local `SimulationSession`;
- `WorldState` — topology + отображение `StarSystemId -> GameState`;
- `WorldPersistence` — content-bound file envelope + active-system/scheduler metadata.

WorldState требует ровно один local snapshot для каждой StarSystem topology. Duplicate, unknown и missing system states fail fast.

## Strategic landmarks and indexes

`StarSystemNode` содержит immutable canonical lists:

- `PlanetNode`;
- `AsteroidFieldNode`.

`GalaxyTopology` проверяет глобальную уникальность `PlanetId` и `AsteroidFieldId` и предоставляет O(1)-style hash lookup:

- `findPlanet(PlanetId)`;
- `systemOf(PlanetId)`;
- `findAsteroidField(AsteroidFieldId)`;
- `systemOf(AsteroidFieldId)`;
- существующие `findSystem`, `sectorOf`, `neighbors`.

Локальные spatial indexes экономических объектов остаются внутри local simulation (`Ashley Family`, `SpatialHashGrid`), поэтому strategic topology не дублирует mutable ECS state.

## Production demo

`DemoGalaxyFactory` создаёт:

- 2 sectors;
- 3 StarSystems: Anchor, Corona, Frontier;
- 4 strategic planets;
- 3 strategic asteroid fields;
- 2 jump connections (`Anchor <-> Corona <-> Frontier`);
- отдельную ordinary `SimulationSession` для каждой системы с deterministic derived root seed.

Active Anchor использует исходный desktop root seed без преобразования, поэтому local demo economy сохраняет прежнюю deterministic seed identity.

## Exact-head CI evidence

Implementation head перед этим documentation-only commit:

`e5ffac43407e2a53e5199427c43ce7ffb53ec341`

GitHub Actions CI run: `31630480184`, push event, conclusion `success`.

`./mvnw --batch-mode --no-transfer-progress clean verify`:

- tests: **274**;
- failures: **0**;
- errors: **0**;
- skipped: **0**;
- Javadoc: success with fail-on-warning policy;
- JaCoCo: `All coverage checks have been met`;
- shaded desktop artifact uploaded successfully.

Important focused suites:

- `GalaxyTopologyTest`: 5/5;
- `StrategicLandmarkTopologyTest`: 3/3;
- `WorldStateCodecTest`: 6/6;
- `WorldPersistenceTest`: 5/5;
- `WorldSimulationTest`: 5/5;
- `SimulationClockStrategicTest`: 2/2;
- `DemoGalaxyFactoryTest`: 2/2;
- existing Stage 0–6 regression suites remain green, including economic benchmark tests.

После этого documentation-only commit требуется новый exact-head push CI; затем тот же final SHA должен пройти отдельный `pull_request` CI перед merge.

## Definition of Done mapping

Roadmap DoD: «Мир содержит несколько систем, удалённые системы продолжают экономически жить без симуляции каждого объекта на полном local tick».

Подтверждение:

1. production demo содержит 3 systems;
2. active system использует unchanged full fixed-rate `SimulationSession.advanceFrame`;
3. remote systems используют тот же economic core через `advanceStrategicSteps(10)`;
4. remote object-level update frequency снижена в 10 раз при default cadence;
5. remote trade/mining/production/event systems продолжают получать game-time updates;
6. remote ledgers изменяются, inventories остаются валидными;
7. update work bounded per frame;
8. full world state детерминированно сохраняется и продолжается после load.

Следовательно Stage 7 функционально готов к PR. Manual OpenGL desktop smoke остаётся отдельной неавтоматической release-проверкой и не подменяется headless CI.
