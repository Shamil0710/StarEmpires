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

Будущий Stage 20 generator обязан генерировать **jump graph**, а не произвольную функцию расстояния, допускающую direct travel между любыми системами.

Для каждой системы generator определяет explicit neighbor set через `JumpConnection`/будущий эквивалентный authoritative edge type.

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

## 5. Исключительные способы перемещения

Если позднее появятся wormhole, artificial gate, ancient relay или другая special transit technology, она не должна обходить этот contract скрытым teleport API.

Есть два допустимых варианта:

1. special object создаёт/открывает explicit edge в authoritative topology;
2. вводится отдельный, явно спроектированный transition type с собственными persistent state/cost/risk/access semantics.

В первом случае две системы становятся соседними на время существования/доступности edge.

Во втором случае это уже не ordinary jump и требуется отдельное architecture decision.

## 6. Player / AI parity

Player и AI должны использовать один graph.

Запрещено:

- player-only shortcut между несоседними системами;
- AI strategic teleport для ускорения симуляции;
- abstract fleet relocation, пропускающая hops;
- mission relocation без физического transition semantics;
- «fast travel», создающий другое authoritative положение корабля без прохождения маршрута.

Simulation LOD может агрегировать вычисления времени между событиями, но не менять последовательность посещённых edges и систем.

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
```

## 9. Gameplay consequence

Этот contract нужен не только для технической чистоты. Он создаёт физическую стратегическую географию:

```text
jump topology
→ реальные маршруты торговли
→ транзитные системы
→ узкие места
→ границы
→ логистические расстояния
→ время подкреплений
→ ценность баз и складов
→ возможность блокады
→ стратегическое значение территории
```

Поэтому neighbor-only travel является фундаментальным правилом мира Star Empires, а не временным ограничением текущего demo UI.
