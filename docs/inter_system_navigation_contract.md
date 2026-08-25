# Star Empires — Inter-System Navigation Contract

> Статус: **ACCEPTED CROSS-STAGE INVARIANT**  
> Область: Stage 10 travel, large demo, Stage 20 world generation и все последующие player/AI navigation systems.

## 1. Основное правило

> **Обычное межзвёздное перемещение всегда выполняется только между двумя системами, соединёнными одним explicit jump edge.**

Если система `B` отсутствует в `GalaxyTopology.neighbors(A)`, корабль или флот не может выполнить непосредственный ordinary jump `A → B`.

Дальний маршрут:

```text
A → B → C → D
```

является тремя отдельными физическими/временными переходами:

```text
A → B
B → C
C → D
```

а не одним скрытым переходом `A → D`.

## 2. Authoritative enforcement

Правило должно проверяться ниже UI и AI-command layers.

Текущий production contract уже обеспечивается двумя границами:

```text
PlayerRuntime.requestJump(destination)
→ destination ∈ topology.neighbors(origin)

WorldSimulation.requestFleetJump(...)
→ FleetJumpService.requireDirectConnection(origin, destination)
→ destination ∈ topology.neighbors(origin)
```

Следствие:

- UI не может телепортировать игрока;
- AI не может обойти topology;
- scripted mission logic не должно иметь отдельный ordinary-travel shortcut;
- сохранённый active jump обязан ссылаться на существующий direct edge;
- route planner может планировать далеко, но executor выполняет маршрут hop-by-hop.

## 3. Immediate destination vs route destination

Нужно различать:

```text
route destination
= конечная желаемая система

immediate jump destination
= только один сосед текущей системы
```

UI может позволять выбрать удалённую систему как конечную цель маршрута, но authoritative execution обязан разложить путь на последовательность соседних edges.

После каждого hop мир получает обычное промежуточное состояние. Это означает, что между hops могут измениться:

- доступность следующего edge;
- дипломатический доступ;
- fuel/reaction-mass/energy state;
- повреждения;
- обнаружение и угрозы;
- blockade/security state;
- маршрут и ETA.

Автопилот поэтому не является атомарным teleport command.

## 4. Stage 20 world-generation requirement

Stage 20 generator генерирует **jump graph**, а не произвольную функцию расстояния, допускающую direct travel между любыми системами.

Для каждой системы generator определяет explicit neighbor set через `JumpConnection`/эквивалентный authoritative edge type.

Generated topology должна поддерживать:

- связные регионы;
- несколько альтернативных маршрутов там, где это уместно;
- chokepoints;
- border/gateway systems;
- remote regions;
- различную network centrality;
- meaningful multi-hop logistics;
- конечное время переброски флотов;
- возможность физической блокады маршрутов.

Generator не должен автоматически соединять каждую систему со всеми ближайшими по евклидовой карте системами. Geometry и graph topology связаны design constraints, но jump edge остаётся explicit authoritative объектом.

### 4.1 Один соседний edge — одна локальная FTL-точка

Для каждого incident topology edge система обязана иметь **ровно одну соответствующую локальную FTL entry/exit point**.

Если:

```text
neighbors(B) = {A, C, D}
```

то внутри `B` существуют три различные точки:

```text
B↔A endpoint
B↔C endpoint
B↔D endpoint
```

Нельзя заменять их одной универсальной `jump point`, случайным arrival point или выбором endpoint по индексу списка.

Canonical generated identity должна однозначно связывать локальную точку с парой `local system + neighbor system`. Текущий Stage-20 materialization использует neighbor-specific identity вида:

```text
jump-arrival.<local-system>.<neighbor-system>
```

Количество таких точек в системе обязано точно совпадать с degree этой системы в authoritative topology.

### 4.2 Directional placement

FTL-точка размещается на стороне системы, соответствующей macro-направлению на связанного соседа.

Для outgoing перехода `B → C` точка `B↔C` ориентирована по направлению `B → C` на macro map.

Для arrival `A → B` корабль появляется в `B` у точки `B↔A`, то есть на стороне `B`, обращённой назад к `A`.

Следствие для маршрута:

```text
A → B → C
```

```text
arrival A→B
→ fleet materializes at B↔A endpoint
→ fleet remains physically inside B
→ fleet crosses B to B↔C endpoint
→ only there may B→C spool/transit begin
```

Таким образом промежуточная система не является zero-time routing node.

### 4.3 Generated geometry is persistent authority

Directional FTL geometry создаётся deterministic Stage-20 generation/materialization и затем сохраняется как часть accepted generated world.

Runtime обязан:

- читать persisted endpoint identity, position и calibrated connection/approach data;
- не перегенерировать azimuth, radial placement или neighbor pairing при load;
- не подменять generated endpoint случайной runtime coordinate;
- использовать одну и ту же геометрию для player, AI, freight и military fleets;
- сохранять exact local physical state во время approach так, чтобы save/load не менял маршрут или положение.

Legacy ECS `Transform` является live presentation/simulation projection exact hierarchical/double physical state, а не отдельным источником FTL geometry.

### 4.4 Physical departure readiness

Ordinary jump request не означает мгновенный detach из текущей системы.

Если fleet находится не на outgoing endpoint, existing `FleetJumpService` использует `MOVING_TO_JUMP` как физическую локальную approach phase. Fitted FTL energy commit, spool/transit detach и последующий межсистемный переход не могут начаться, пока arrival/departure authority не подтвердит точное достижение persisted outgoing endpoint и stationary departure readiness.

Запрещено:

- мгновенно переносить fleet на outgoing endpoint при jump request;
- начинать spool вдали от endpoint;
- списывать FTL energy и detach fleet до authoritative departure-readiness check;
- перескакивать из incoming endpoint напрямую в следующий edge при multi-hop route.

## 5. Исключительные способы перемещения

Если позднее появятся wormhole, artificial gate, ancient relay или другая special transit technology, она не должна обходить этот contract скрытым teleport API.

Есть два допустимых варианта:

1. special object создаёт/открывает explicit edge в authoritative topology;
2. вводится отдельный, явно спроектированный transition type с собственными persistent state/cost/risk/access semantics.

В первом случае две системы становятся соседними на время существования/доступности edge.

Во втором случае это уже не ordinary jump и требуется отдельное architecture decision.

## 6. Player / AI parity

Player и AI должны использовать один graph и одну generated FTL geometry.

Запрещено:

- player-only shortcut между несоседними системами;
- AI strategic teleport для ускорения симуляции;
- abstract fleet relocation, пропускающая hops;
- mission relocation без физического transition semantics;
- «fast travel», создающий другое authoritative положение корабля без прохождения маршрута;
- AI/freight-only универсальная jump point, отличная от player-visible generated endpoints.

Simulation LOD может агрегировать вычисления времени между событиями, но не менять последовательность посещённых edges, систем или обязательных physical transition endpoints.

## 7. UI contract для live demo

Для непосредственного jump UI показывает только neighbors текущей системы.

Baseline manual controls:

```text
K
→ выбрать следующий direct neighbor

J
→ запросить authoritative jump в выбранного direct neighbor
```

HUD показывает:

- текущий system ID + name;
- current strategic controller;
- список direct neighbors;
- выбранный immediate destination;
- controller соседних систем, если эта информация известна текущему test harness.

Выбор в UI не создаёт movement. `J` только посылает обычный command в `PlayerRuntime`.

## 8. Acceptance invariants

Минимальные автоматические проверки:

```text
non-neighbor player request
→ rejected
→ no FleetJumpState created

non-neighbor world request
→ rejected by authoritative FleetJumpService
→ no FleetJumpState created

direct-neighbor request
→ accepted when ordinary state requirements allow it

navigation UI model
→ every selectable immediate destination ∈ topology.neighbors(current)

Stage-20 generated graph
→ every ordinary executed inter-system hop corresponds to one explicit edge

every generated system
→ exactly one distinct local FTL endpoint per direct neighbor
→ endpoint is deterministically placed on the neighbor-facing side

arrival A→B
→ exact B↔A endpoint

continuation B→C
→ physical MOVING_TO_JUMP crossing from B↔A to B↔C
→ departure cannot commit away from B↔C

save/load during MOVING_TO_JUMP
→ exact hierarchical position + velocity round-trip
→ continuation reaches the same persisted outgoing endpoint
→ no process-local hidden approach state
```

## 9. Gameplay consequence

Этот contract нужен не только для технической чистоты. Он создаёт физическую стратегическую географию:

```text
jump topology
→ реальные маршруты торговли
→ физические entry/exit approaches внутри систем
→ транзитные системы
→ узкие места
→ границы
→ логистические расстояния
→ время подкреплений
→ ценность баз и складов
→ возможность блокады и перехвата у конкретных transition points
→ стратегическое значение территории
```

Поэтому neighbor-only travel и directional physical FTL endpoints являются фундаментальными правилами мира Star Empires, а не временным ограничением текущего demo UI.
