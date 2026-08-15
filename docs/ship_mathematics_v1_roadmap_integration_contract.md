# Star Empires — контракт интеграции Ship Mathematics v1.0 в roadmap

> Статус: **обязательное требование к завершению research track**  
> Дата: **2026-08-15**  
> Назначение: зафиксировать требования, которые должны быть перенесены в подробные планы Stage 17.5, Stage 19 и Stage 21 после принятия `Ship Mathematics v1.0 Design Baseline`.

---

## 1. Обязательное действие после завершения исследования

После принятия `Ship Mathematics v1.0 Design Baseline` в `main` необходимо **до активации Stage 17.5** провести отдельный roadmap-integration pass.

В рамках этого pass необходимо:

1. подробно переписать/расширить **Stage 17.5 — Combat Depth / Ship Fitting Foundation** на основе фактически принятой v1.0 модели;
2. подробно переписать/расширить **Stage 21 — Content Width / Balance / Long-run Stability** так, чтобы массовый контент и technology ladder строились строго внутри той же модели;
3. дополнить **Stage 19 — World Generation** требованиями физического масштаба, чтобы generated world был согласован с корабельной кинематикой, логистикой, сенсорами, экономикой и временем;
4. добавить acceptance matrix, связывающую v1.0 benchmark seeds с runtime implementation Stage 17.5, world-generation calibration Stage 19 и content/balance soak Stage 21.

До выполнения этого roadmap-integration pass `Ship Mathematics v1.0` нельзя считать полностью интегрированной в долгосрочный план проекта, даже если сам research baseline принят.

---

## 2. Единая парадигма всех модулей и оборудования

Все вводимые корабельные системы, модули и оборудование должны работать внутри **одной инженерной модели**, а не быть набором отдельных подсистем с несопоставимыми характеристиками.

Базовое правило:

> **Модуль не даёт абстрактный bonus. Он занимает физическое место, имеет массу, потребляет/передаёт реальные ресурсы и создаёт конкретную capability через общие runtime-механизмы.**

Минимальная общая authoring/runtime-парадигма должна поддерживать, где применимо:

```text
stable module/content identity
integration size / compatible hardpoint or slot
physical dimensions / integration envelope
hardware mass kg
occupied volume m3
structural / mounting requirements
continuous electrical power W
peak electrical power W
stored energy requirement J
generated waste heat W
local thermal capacity J
coolant transfer requirement W
crew / automation requirement
ammunition / reaction-mass / consumable interfaces
signature contributions by channel
maintenance / reliability / damage state
material / component construction requirements
repair / replacement requirements
capability-specific physical parameters
```

Не каждый модуль обязан иметь ненулевое значение каждого параметра. Например, passive armor может иметь `power = 0`, но всё равно участвует в общей системе массы, объёма, геометрии, материалов, повреждений и стоимости.

### 2.1. Единые derived budgets корабля

Все модули должны сходиться в общих ship-level budgets:

```text
mass
volume / geometry
center-of-mass / future inertia inputs
power generation and distribution
stored energy
heat generation / transfer / rejection
crew / automation
ammunition / stores
reaction mass
delta-v
thrust / acceleration
signature
sensor / track capability
damage / redundancy / compartment exposure
maintenance / logistics / operating cost
```

Запрещено создавать параллельные скрытые системы вроде:

```text
weaponWeightPoints
sensorPoints
armorPoints
stealthRating
engineTierBonus
```

если они не являются UI-derived representation физически определённых исходных величин.

### 2.2. Общая парадигма по семействам модулей

Одна и та же integration philosophy должна применяться к:

- reactors / power conversion;
- batteries / capacitors / energy storage;
- main drives / maneuver thrusters / FTL integration;
- radiators / coolant loops / thermal stores;
- kinetic weapons;
- beam weapons;
- missile / torpedo / interceptor launch systems;
- magazines / ammunition handling;
- passive / active sensors;
- fire control;
- ECM / ECCM / decoys;
- armor / bumpers / spaced protection / citadel protection;
- command / communications / datalinks;
- crew support / automation;
- cargo / tanks / stores;
- hangars / small-craft support;
- mining / salvage / repair / industrial / science / colony mission systems.

Специализированные capability equations допускаются, но они должны получать inputs из той же общей физической и ресурсной модели.

---

## 3. Единство физики и экономики

`Ship Mathematics v1.0` не должна оставаться только боевой системой.

Масса, объём и компонентный состав должны влиять одновременно на:

```text
ship construction cost
required industrial inputs
shipyard capability
construction time
maintenance load
repair materials
crew demand
fuel / reaction-mass demand
ammunition demand
logistics footprint
strategic endurance
flight performance
combat performance
salvage outcome
replacement economics
```

Следовательно, Stage 21 не имеет права балансировать корабли только через цену, DPS или class multipliers независимо от их physical fit.

High-tier technology должна выражаться через реальные технологические возможности: specific power, material properties, sensor noise, cooling temperature, automation, manufacturing complexity, engine performance, component quality и т. п., а не через blanket `Mk II = +25% all stats`.

---

## 4. Обязательная связь с генерацией мира

Stage 19 world generation должен использовать физические и временные масштабы, согласованные с `Ship Mathematics v1.0`.

Generated galaxy/system geometry нельзя определять исключительно художественными или случайными числами независимо от возможностей кораблей.

Минимум должны учитываться:

```text
local distances m / km / AU
inter-system topology and route lengths
ship acceleration m/s2
cruise / maneuver velocity evolution m/s
braking distance and braking time
reaction-mass consumption kg / delta-v m/s
jump / FTL travel time and operating constraints
sensor detection envelopes
track/fire-control envelopes
communications / datalink latency where relevant
combat engagement distances
station approach / docking distances
mining-field and resource-cluster spacing
logistics travel time
trade-route round-trip time
construction supply time
fleet reinforcement time
strategic response time
production / consumption cycle time
```

### 4.1. World scale must be calibrated from gameplay-capable ships

До заморозки generation distributions необходимо выбрать representative ships как минимум:

- early civilian freighter;
- bulk freighter;
- mining ship;
- patrol/corvette;
- frigate/destroyer;
- cruiser;
- capital fleet;
- tanker / logistics support.

Для каждого generated scale band должны существовать benchmark travel calculations:

```text
origin → local station
station → mining zone
inner-system → outer-system
jump arrival → economic hub
system → neighboring system
regional multi-hop route
```

World generation считается некалиброванной, если типичный маршрут создаёт бессмысленные времена относительно design goals или если физически разные корабли почти не отличаются по operational travel outcome.

### 4.2. Экономический масштаб должен учитывать транспортное время

Production, consumption, inventory, market buffer и construction demand должны калиброваться относительно реального logistics latency.

Например, нельзя независимо задать:

```text
factory consumes all reserves every 30 s
```

если типичный поставщик физически приходит раз в несколько часов/дней simulation time, если только именно хронический дефицит не является осознанным design outcome.

И наоборот, огромные market buffers не должны полностью обнулять значение расстояния и транспортной инфраструктуры.

### 4.3. Sensor scale и world scale должны быть совместимы

Если passive sensor способен обнаруживать capital emission на масштабе системы, generation/visibility model должна это учитывать.

Это не означает автоматическое знание точной позиции или identity: должны сохраняться различия

```text
DETECTED
CLASSIFIED
TRACKED
FIRE_CONTROL
```

Но world generation не должна помещать объекты на произвольные дистанции, игнорируя фактически рассчитанные signature/sensor envelopes.

### 4.4. Combat scale и navigation scale — одна система координат

Боевые расстояния, formation spacing, missile/interceptor stand-off, weapon time-of-flight и local navigation должны использовать совместимые physical units и geometry.

Запрещено иметь отдельно:

```text
strategic distance units
combat distance units
sensor distance units
```

которые не имеют однозначного преобразования в canonical physical scale.

UI может использовать удобные km / thousand km / AU, но authoritative simulation должна иметь единый physical meaning.

---

## 5. Требование к Stage 17.5 после v1.0

После завершения research track Stage 17.5 должен быть подробно разложен на runtime slices исходя из фактически принятой v1.0 architecture.

Обязательные направления будущего уточнения:

1. production `HullDefinition` / geometry / slot topology;
2. production `ModuleDefinition` и единая module contract;
3. central derived-ship calculator;
4. mass / thrust / reaction mass / delta-v runtime integration;
5. power / stored energy / thermal topology;
6. fitting validation;
7. sensor / signature / track state;
8. EW / datalink / fire-control integration;
9. weapon families / ammunition / guidance;
10. protection / compartments / subsystem damage;
11. damage-driven degradation через изменение физических capabilities;
12. persistence / migration всех новых state variables;
13. player/AI shared capability APIs;
14. fitting/shipyard UI поверх authoritative model;
15. deterministic regression matrix против v1.0 benchmark seeds.

Точный порядок и DoD должны быть сформированы **после** v1.0, чтобы roadmap отражал результат исследования, а не преждевременные предположения.

---

## 6. Требование к Stage 21 после v1.0

Stage 21 должен расширять контент, не создавая вторую систему правил.

После v1.0 его необходимо подробно дополнить как минимум следующими направлениями:

- полный technology ladder компонентов;
- faction-specific engineering doctrines;
- civilian / military / industrial module catalog;
- hull families and variants;
- weapons/ammunition/seeker families;
- armor/material families;
- sensor/EW families;
- reactors/drives/thermal systems;
- logistics/support modules;
- shipyard/facility capability tiers;
- construction material/component chains;
- maintenance / repair / replacement economics;
- fleet composition and doctrine balance;
- world-scale logistics soak;
- combat saturation/endurance soak;
- economic replacement-loss soak;
- anti-universal-build tests;
- anti-linear-tier-obsolescence tests.

### Stage 21 invariant

> Новый модуль, новый корпус или новый technology tier считается валидным только если его преимущества и недостатки выражаются через принятую v1.0 модель и проходят общие validation/benchmark rules.

Если для нового контента требуется новый фундаментальный stat/budget, которого нет в v1.0, это должно считаться **architecture change request**, а не тихим расширением JSON.

---

## 7. Acceptance requirements перед переходом от исследования к implementation

Перед формальным закрытием `Ship Mathematics v1.0 Design Baseline` необходимо проверить:

- все основные module families могут быть описаны одной общей integration contract;
- нет обязательных combat/system capabilities, требующих class-name bonuses;
- все derived ship characteristics вычисляются из authoritative inputs;
- физические параметры имеют canonical units;
- damage меняет реальные subsystem capabilities;
- civilian/economic ships используют ту же mass/power/heat/movement model;
- benchmark world scales согласованы с representative ship travel times;
- sensor/weapon ranges согласованы с generated local-system scales;
- logistics times согласованы с production/consumption/construction cadence;
- Stage 17.5 roadmap готов к подробному runtime decomposition;
- Stage 19 roadmap готов к physically calibrated generation pass;
- Stage 21 roadmap готов к content/balance expansion внутри единой модели.

---

## 8. Каноническое правило

```text
Ship Mathematics research
→ v1.0 Design Baseline
→ detailed roadmap integration pass (17.5 + 19 + 21)
→ Stage 17.5 runtime promotion
→ Stage 19 physically calibrated world generation
→ Stage 21 broad content / technology / balance
```

Порядок нельзя разворачивать так, чтобы массовый content или world generation закрепили масштабы, противоречащие принятой корабельной физике.
