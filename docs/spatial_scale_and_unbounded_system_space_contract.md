# Star Empires — Spatial Scale & Unbounded System Space Contract

> Статус: **ACCEPTED CROSS-STAGE INVARIANT**  
> Основной implementation owner: **Stage 20 Physical World Generation / Discovery**  
> Зависимости: accepted `Ship Mathematics v1.0 Design Baseline`, Stage 17.5 ship/combat capability, Stage 18 station/infrastructure geometry, Stage 19 tactical/strategic response behavior, `docs/simulation_scalability_architecture.md`, `docs/inter_system_navigation_contract.md`.

---

# 1. Назначение

Этот документ фиксирует масштаб локального пространства Star Empires, чтобы размеры звёздных систем, расстояния между объектами, размеры и размещение станций, sensor/fire-control/weapon envelopes, движение кораблей и camera/LOD не проектировались независимо друг от друга.

Главный принцип:

> **Масштаб мира выводится из возможностей физических объектов, а не наоборот. Размер системы не является размером игровой арены; это распределение значимых объектов и operational distances внутри концептуально неограниченного локального пространства.**

World generation обязан создавать geometry, в которой одновременно имеют смысл:

- ускорение, торможение и finite delta-v кораблей;
- различия между быстрым patrol craft и тяжёлым loaded freighter;
- дальнее обнаружение без автоматического fire-control;
- kinetic/beam/guided weapon time-of-flight и target maneuver;
- point-defense/intercept stand-off;
- formation spacing;
- docking/traffic around stations;
- расстояния station ↔ station, station ↔ resource field, jump arrival ↔ hub;
- logistics cadence;
- fleet response time;
- tactical encounter duration;
- UI zoom / map readability;
- simulation LOD и numerical precision.

---

# 2. Канонические исходные правила

Accepted Ship Mathematics уже фиксирует:

```text
authoritative length = meters
velocity = m/s
acceleration = m/s2
time = seconds
```

и запрещает отдельные authoritative combat/strategic/sensor distance units.

Local flight использует:

```text
acceleration = thrust / current mass
finite reaction mass
finite delta-v
real braking distance/time
```

Sensors используют physical signal/propagation/noise/track state, а weapons — physical geometry, track quality, time-of-flight, power/heat/ammunition and target response.

Следовательно, Stage 20 не имеет права независимо назначить:

```text
SYSTEM_RADIUS = arbitrary game units
WEAPON_RANGE = arbitrary map radius
SENSOR_RANGE = arbitrary fog radius
STATION_SPACING = arbitrary sprite spacing
```

и затем подгонять корабли под эти числа.

---

# 3. Три разных понятия пространства

Нужно строго различать:

## 3.1. Authoritative physical coordinate space

Локальное пространство системы является концептуально **unbounded**.

Нет физического прямоугольника:

```text
0 <= x <= MAP_WIDTH
0 <= y <= MAP_HEIGHT
```

который ограничивает движение корабля.

Корабль не должен:

- останавливаться на краю экрана;
- отскакивать от невидимой стены;
- телепортироваться внутрь карты;
- уничтожаться из-за выхода за render bounds;
- автоматически покидать систему из-за превышения локального radius.

## 3.2. Generated operational/content envelope

Generator размещает значимые объекты в bounded statistical envelope:

- центральные тела;
- stations;
- resource fields;
- jump arrival/departure zones;
- patrol/security areas;
- anomalies/derelicts;
- infrastructure.

Этот envelope отвечает на вопрос:

> «Где в типичной системе сосредоточена интересная деятельность?»

Он **не является стеной мира**.

За пределами generated envelope пространство может становиться всё более пустым, но продолжает существовать как authoritative coordinate domain.

## 3.3. Render / materialization / simulation window

Renderer и tactical materialization имеют bounded рабочее окно ради производительности.

```text
visible/rendered area
≠ physical world extent
```

Object outside camera/materialization window продолжает существовать в authoritative state и может быть simulated через accepted LOD/strategic representation.

Culling разрешён для rendering/computation, но не для causal state.

---

# 4. Unbounded local space не отменяет jump topology

Локальная coordinate plane системы и inter-system topology — разные уровни.

Даже если корабль улетит на очень большое локальное расстояние, это не означает автоматический переход в соседнюю звёздную систему.

Canonical rule остаётся:

```text
local movement inside system A
→ remains inside system A

inter-system transition A → B
→ only through explicit designed transition semantics
→ ordinary case: one topology neighbor edge
```

Нельзя использовать отсутствие map edge как скрытый способ обойти `GalaxyTopology.neighbors(...)`.

---

# 5. Масштаб определяется capability matrix

Stage 20 scale calibration получает production capability outputs Stage 17.5 и Stage 18.

Минимальная reference matrix:

## Ships

- early civilian freighter;
- loaded bulk freighter;
- mining ship;
- fast patrol/corvette;
- escort destroyer;
- cruiser;
- capital combatant;
- tanker/logistics support;
- representative small craft where relevant.

## Targets / signatures

- cold/coasting small ship;
- maneuvering small ship;
- hot combatant under thrust;
- loaded freighter;
- large station;
- missile/interceptor;
- passive/static infrastructure where relevant.

## Sensors

Representative passive/active suites across thermal, plume, radar and optical channels.

## Weapons

- kinetic direct fire;
- beam;
- guided missile/torpedo;
- interceptor;
- point-defense beam/projectile where implemented.

## Infrastructure

- small outpost;
- trade/industrial station;
- military base;
- major shipyard/hub.

---

# 6. Required scale hierarchy

Не существует одного universal distance ratio для всех technologies/targets, но generated world обязан сохранять различимые operational layers.

Conceptual hierarchy:

```text
docking / close maneuver geometry
< formation / PD / terminal-defense geometry
< ordinary weapon-effective engagement geometry
< local logistics / station / resource spacing
< broad system operational extent
```

Sensor detection может находиться значительно дальше weapon/fire-control envelope для ярких целей.

Для малозаметной цели или degraded sensor state порядок может быть другим; поэтому acceptance использует representative matrix, а не hard universal circle.

Главное требование:

> **Tactical, logistics и system scales не должны случайно схлопываться в один и тот же диапазон расстояний.**

---

# 7. Reference v1.0 anchors — calibration evidence, not final map constants

Accepted design baseline содержит reference examples:

```text
heavy direct-fire reference envelope ~ 3,000 km
loaded bulk freighter 100,000 km rest-to-rest ~ 19.18 h
loaded bulk freighter 1,000,000 km rest-to-rest ~ 61.58 h
escort destroyer sustained 100,000 km ~ 14.32 h
escort destroyer sustained 1,000,000 km ~ 45.29 h
destroyer central plume detection reference ~ 30 million km
```

Эти anchors доказывают необходимость multi-scale world.

Они **не являются**:

- обязательным final weapon range;
- обязательным system radius;
- обязательным station spacing;
- обязательным sensor hard range.

Stage 20 обязан пересчитать production bands после Stage 17.5/18/19 и versioned content calibration.

---

# 8. Ship movement calibration

Для каждой candidate geometry distribution считать минимум:

```text
rest-to-rest travel time
acceleration phase
coast phase if applicable
braking time / braking distance
required delta-v
reaction-mass use
thermal/endurance consequence
arrival velocity state
```

World scale должен создавать реальное различие между hull/load states.

Например:

```text
fast patrol craft
vs loaded bulk freighter
```

на одном и том же маршруте обязаны получать разные physical transit consequences.

Если все ships проходят station-to-station leg почти за одинаковое время из-за слишком маленьких distances, geometry слишком сжата.

Если обычная локальная экономика требует absurdly long physical cycles даже с intended time controls/autopilot, geometry может быть слишком растянута.

Исправление выполняется через calibration world geometry/content capabilities, а не hidden speed multiplier.

---

# 9. Sensor scale calibration

Sensor distance не является hard fog circle.

Для representative target/channel combinations Stage 20 использует derived statistical bands минимум для:

```text
first plausible detection
stable detection
classification
track-quality threshold
fire-control-quality threshold
track persistence / reacquisition
```

Range зависит от:

- signature channel;
- target state/orientation;
- aperture;
- noise/interference;
- active/passive mode;
- observation geometry;
- dwell/integration;
- EW;
- multi-sensor fusion.

Generated system должен иметь достаточно пространства, чтобы состояние:

```text
DETECTED but not FIRE_CONTROL
```

было реальным и полезным, а не существовало несколько simulation ticks.

---

# 10. Weapon scale calibration

Weapons не получают universal hard maximum range ради карты.

Для generation/balance использовать effectiveness envelopes, например:

```text
time of flight
track uncertainty
projectile dispersion
beam spot / dwell
missile burn / delta-v / terminal reserve
target maneuver envelope
impact/effect probability distribution
thermal/ammunition endurance
```

Machine-readable calibration может хранить percentiles/quality bands, но это не физическая стена.

Для representative weapon/target pairs world generation обязан проверить, что:

- engagement имеет meaningful approach/detection phase;
- long-range fire имеет real information/time-of-flight cost;
- close-range weapons не становятся автоматически dominant из-за слишком маленькой карты;
- long-range weapons не покрывают всю significant system geometry без intended design reason.

---

# 11. Point defense / formation / fleet geometry

Stage 17.5 layered-defense model требует physical spacing.

Scale calibration учитывает:

```text
formation spacing
interceptor launch-to-intercept distance
safe intercept distance
missile terminal distance
PD emitter geometry
support/datalink geometry
residual debris risk
```

System/tactical scale не может сжимать fleets так, чтобы formation design не имел смысла.

---

# 12. Station physical size

Station — физический infrastructure asset, а не только крупный UI sprite.

Authoritative station footprint/geometry должна выводиться из реальных Stage-18/shipyard capabilities, где применимо:

- berth/docking capacity;
- storage volume;
- industrial modules;
- shipyard envelope;
- reactor/power infrastructure;
- radiators/thermal infrastructure;
- habitation/crew;
- weapon/sensor mounts;
- structural clearance;
- approach corridors;
- traffic handling.

Presentation sprite/icon может быть exaggerated для читаемости на дальнем zoom, но:

```text
visual icon size
≠ collision size
≠ weapon origin geometry
≠ docking geometry
```

---

# 13. Station and infrastructure spacing

Placement station ↔ station и station ↔ jump/resource field должен проверять:

- civilian transit time;
- military response time;
- braking/approach time;
- docking traffic geometry;
- defensive fire-control/weapon envelope;
- sensor coverage;
- jump-arrival stand-off;
- mining/freight cadence;
- safe maneuvering space.

Default generation не должна ставить два независимых крупных hubs настолько близко, что они постоянно находятся в unavoidable point-blank combat geometry.

Но military stations могут intentionally перекрывать routes/approaches, если это physical outcome authored/generated strategic placement.

---

# 14. Jump-arrival geometry

Jump arrival position является частью system-scale design.

Generator обязан проверять:

```text
arrival → major hub distance
arrival → hostile defensive envelope
arrival → safe braking/approach path
arrival → patrol response time
arrival → alternate infrastructure
```

Обычный jump не должен автоматически материализовать fleet внутри guaranteed lethal point-blank envelope major station, кроме explicit transition/scenario rule.

С другой стороны, arrival points не должны быть настолько далеко, что jump завершён, но meaningful system interaction всегда требует disproportionate empty-flight time.

---

# 15. System size as distribution, not boundary

У system нет authoritative `radius` в смысле wall.

Допустимо иметь generated descriptive metrics:

```text
coreActivityRadiusPercentile
majorInfrastructureExtent
resourceFieldExtent
jumpArrivalExtent
surveyedContentExtent
expectedTrafficExtent
```

Но эти metrics описывают распределение контента.

Они не используются как:

```text
if distanceFromStar > systemRadius:
    clampShip()
```

или

```text
if outsideSystemRadius:
    deleteEntity()
```

---

# 16. Empty space remains meaningful

Большие пустые дистанции допустимы, если они создают consequence:

- transit time;
- fuel/reaction mass usage;
- sensor exposure;
- isolation;
- delayed response;
- convoy/escort value;
- exploration uncertainty.

Но generator не должен создавать пустоту только ради масштаба, если она не влияет на решения.

World-generation calibration ищет gameplay-relevant physical emptiness, а не максимальный astronomical realism любой ценой.

---

# 17. UI / camera / maps

UI использует несколько presentation scales над одним authoritative world state.

Например:

```text
tactical/local camera
system map
strategic galaxy map
```

Они не являются разными physics spaces.

Camera boundary не является world boundary.

Допустимы:

- zoom-dependent icons;
- aggregation;
- labels;
- off-screen indicators;
- system-map projection;
- tactical focus camera;
- re-centering.

Запрещено менять authoritative position/range только ради того, чтобы объект поместился на экран.

---

# 18. Numerical precision / floating-origin requirement

Unbounded conceptual space не означает бесконечную numeric precision.

Implementation обязан иметь explicit precision strategy, способную удерживать accepted positional/velocity error budget на максимальных operational distances.

Допустимые техники:

- sufficiently precise authoritative coordinates;
- local reference frames;
- floating origin / camera-relative rendering;
- hierarchical coordinates;
- deterministic origin rebasing.

Конкретный representation выбирается implementation/calibration, но запрещено:

- хранить огромные authoritative coordinates в representation, где tactical precision заметно разрушается;
- исправлять drift недетерминированными teleports;
- менять real distance из-за render-origin shift.

Render coordinates должны выводиться относительно local/camera origin, если это необходимо для precision.

---

# 19. Simulation LOD requirement

Unbounded system space совместимо с bounded performance через simulation LOD.

```text
near / tactically relevant
→ exact local materialization

far but strategically relevant
→ scheduled / aggregate simulation preserving state

irrelevant empty volume
→ no per-frame work
```

LOD может менять **стоимость вычисления**, но не:

- position semantics;
- elapsed physical time;
- route history;
- consumable use;
- damage state;
- ownership;
- visibility causality;
- travel destination.

Object не исчезает causal-wise только потому, что игрок отлетел далеко.

---

# 20. Gameplay pacing and time controls

Физический мир может иметь маршруты длительностью часы simulation time.

Player UX не обязан заставлять игрока вручную держать thrust в течение этого времени.

Допустимые presentation/control tools:

- autopilot;
- time acceleration;
- strategic orders;
- route planning;
- camera/map transitions.

Они не должны менять physical elapsed time или давать player-only faster travel.

NPC/AI world продолжает жить по тем же временным последствиям.

---

# 21. Machine-readable spatial calibration profile

Stage 20 production profile должен содержать или детерминированно выводить минимум следующие classes metrics:

```text
referenceShipSet
referenceTargetSignatureSet
referenceSensorSet
referenceWeaponSet
referenceStationSet

localTravelTimeBands
brakingDistanceBands
deltaVUseBands
reactionMassUseBands

sensorDetectionBands
classificationBands
trackQualityBands
fireControlQualityBands

weaponEffectivenessBands
weaponTimeOfFlightBands
missileReach/terminalReserveBands
pdInterceptBands
formationSpacingBands

stationPhysicalFootprintBands
stationApproachClearanceBands
stationSpacingBands
jumpArrivalStandOffBands
resourceFieldSpacingBands
majorInfrastructureExtentBands

precisionErrorBudget
materializationDistanceBands
renderProjectionThresholds
```

Render thresholds являются presentation outputs и не определяют physics.

---

# 22. Required scale-quality diagnostics

Для representative generated systems world-quality report должен показывать минимум:

- civilian station-to-station transit distribution;
- military response-time distribution;
- jump-arrival-to-hub travel distribution;
- mine/resource-to-refinery haul distribution;
- system-crossing travel distribution;
- sensor detection-to-fire-control warning-time distribution;
- weapon time-of-flight distributions;
- fraction of infrastructure pairs in mutual immediate lethal geometry;
- fraction of arrival points inside major defensive effective envelopes;
- ratio distributions between tactical engagement and logistics spacing;
- numerical precision margin at far operational coordinates;
- materialization/LOD correctness across large separation.

---

# 23. Acceptance scenarios

## A. Fast patrol vs loaded freighter

Same physical route.

Expected:

- different acceleration;
- different braking;
- different travel time;
- different reaction-mass consequence;
- no hidden normalization to equal arrival time.

## B. Detection before fire control

Representative hot combatant is observed at long distance.

Expected:

```text
DETECTED
→ later TRACKED
→ later FIRE_CONTROL if geometry/data supports it
```

not immediate precision targeting at first sensor contact.

## C. Long-range weapon

Increasing range increases information/time-of-flight/energy/guidance difficulty according to weapon family; no invisible map wall ends projectile solely because it crossed screen bounds.

## D. Station defense geometry

Major station can defend meaningful surrounding volume, but ordinary system generation does not place every major station permanently in unavoidable mutual point-blank combat.

## E. Jump arrival

Ordinary arrival creates meaningful approach/response time and is not guaranteed lethal spawn inside major hostile station envelope unless explicitly designed.

## F. Leave the visible map

Ship moves beyond camera/system-map presentation extent.

Expected:

- position keeps advancing;
- no clamp/bounce/delete;
- camera/map may re-center or project icon;
- authoritative state remains valid.

## G. LOD transition

Ship becomes far enough to dematerialize tactically and later returns.

Expected:

- equivalent elapsed time/position/consumables/state within accepted deterministic tolerance;
- no respawn/reset.

## H. Very distant coordinates

Combat-capable objects operate far from local origin.

Expected:

- tactical relative precision remains inside calibrated error budget;
- floating-origin/reference-frame changes do not change physical distance.

## I. Empty outer space

Ship travels far beyond normal generated content envelope.

Expected:

- space remains valid but increasingly empty;
- no emergency content wall/spawn ring solely to force return;
- inter-system move still requires explicit transition.

## J. UI zoom

Same two objects are viewed at tactical and system-map zoom.

Expected:

- UI icon scale may change;
- authoritative distance, detection, fire-control and weapon solution do not.

---

# 24. Forbidden shortcuts

Запрещены без explicit architecture decision:

```text
hard rectangular system boundary
invisible wall at map edge
screen-space weapon range
screen-space sensor range
destroy entity when offscreen
teleport ship back inside system radius
enter neighboring system by flying across local map edge
station physical size = UI sprite size
all ships normalized to same local travel time
all sensors use one hard radius
all weapons use one hard max range
LOD despawn that loses authoritative state
floating-origin rebase that changes physics
```

---

# 25. Cross-stage ownership

## Stage 17.5

Provides production ship capability:

- acceleration/delta-v/reaction mass;
- signatures;
- sensor/track/fire-control behavior;
- weapon/guidance/PD envelopes;
- formation-relevant capability;
- damage-dependent capability.

## Stage 18

Provides station/facility physical/integration requirements and logistics cadence inputs.

## Stage 19

Provides representative tactical behavior, engagement doctrine and fleet response patterns needed to validate spatial scale.

## Stage 20

Owns spatial generation/calibration:

- object distance distributions;
- station/resource/jump geometry;
- operational extent;
- scale-quality gate;
- unbounded coordinate-space implementation contract;
- LOD/precision acceptance.

## Stage 22

Balances content without introducing a second distance system.

---

# 26. Hard invariants

1. all authoritative local distances use the same physical SI coordinate semantics;
2. star-system local space has no gameplay wall/edge;
3. generated content envelope is not a movement boundary;
4. render/camera/materialization bounds are not physics bounds;
5. moving far locally never bypasses explicit inter-system transition topology;
6. world scale is calibrated from representative ship/sensor/weapon/station capability;
7. acceleration/braking/delta-v remain meaningful at generated distances;
8. sensor detection, track and fire-control remain distinct states across meaningful geometry;
9. weapon effectiveness uses physical family-specific solution, not screen radius;
10. tactical, logistics and broad system scales must remain measurably distinct where intended;
11. station physical geometry is independent from presentation icon size;
12. jump arrival geometry respects approach/defensive envelopes;
13. no off-screen destruction/clamp/teleport shortcut;
14. simulation LOD preserves authoritative state and elapsed physical consequences;
15. numerical precision strategy must satisfy calibrated far-coordinate error budget;
16. camera/floating-origin changes cannot alter physical positions/distances;
17. player and AI use the same geometry and time consequences;
18. time acceleration/autopilot cannot create player-only faster physical travel;
19. scale thresholds are versioned calibration outputs, not guessed eternal constants;
20. bad scale profiles are rejected or recalibrated before production world acceptance.

---

# 27. Completion definition

Этот contract считается реализованным, когда:

> **generated star systems use one physical SI coordinate model whose meaningful object spacing is calibrated against real ship acceleration/braking/delta-v, sensor/track/fire-control behavior, weapon/PD/formation envelopes, station geometry, jump arrivals and logistics cadence; tactical, logistics and system-scale distances remain harmonized but distinct; ships may travel arbitrarily far in local space without hitting a gameplay map edge; rendering and simulation LOD remain bounded implementation windows rather than world boundaries; far objects retain authoritative state; numerical precision remains inside calibrated tolerance; and inter-system movement still occurs only through explicit transition topology shared by player and AI.**
