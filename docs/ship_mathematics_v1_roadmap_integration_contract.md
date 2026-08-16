# Star Empires — контракт интеграции Ship Mathematics v1.0 в roadmap

> Статус: **обязательный cross-stage integration contract**  
> Дата первоначальной фиксации: **2026-08-15**  
> Синхронизация с новым roadmap: **2026-08-16**  
> Назначение: зафиксировать требования, которые должны проходить через Stage 17.5, Stage 18, Stage 20 и Stage 22 после принятия `Ship Mathematics v1.0 Design Baseline`.

---

## 1. Обязательная integration chain

После принятия `Ship Mathematics v1.0 Design Baseline` долгосрочный roadmap обязан сохранять единую причинную цепочку:

1. **Stage 17.5 — Combat Depth / Ship Fitting Foundation:** production ship/module/physics runtime;
2. **Stage 18 — Resources / Industry / Infrastructure:** реальные material/component/facility chains, которые производят и обслуживают Stage-17.5 оборудование;
3. **Stage 20 — Physical World Generation:** physical scale/resources/infrastructure placement, согласованные с корабельной кинематикой и Stage-18 economy;
4. **Stage 22 — Content Width / Technology / Balance:** массовый content строго внутри тех же physical/economic contracts.

Acceptance matrix должна связывать v1.0 benchmark seeds с:

- runtime implementation Stage 17.5;
- industrial/resource feasibility Stage 18;
- world-generation calibration Stage 20;
- content/balance soak Stage 22.

---

## 2. Единая парадигма всех модулей и оборудования

Все вводимые корабельные системы, модули и оборудование работают внутри **одной инженерной модели**, а не как набор несопоставимых бонусов.

> **Модуль не даёт абстрактный bonus. Он занимает физическое место, имеет массу, потребляет/передаёт реальные ресурсы и создаёт конкретную capability через общие runtime-механизмы.**

Минимальная общая authoring/runtime-парадигма поддерживает, где применимо:

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

Не каждый модуль обязан иметь ненулевое значение каждого параметра.

### 2.1. Единые derived budgets корабля

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

Запрещены параллельные hidden systems вроде `weaponWeightPoints`, `sensorPoints`, `armorPoints`, `stealthRating`, `engineTierBonus`, если это не derived presentation физических inputs.

### 2.2. Общая парадигма по семействам модулей

Одна integration philosophy применяется к:

- reactors / power conversion;
- batteries / capacitors / energy storage;
- main drives / maneuver thrusters / FTL integration;
- radiators / coolant loops / thermal stores;
- kinetic/beam/guided weapons;
- magazines / ammunition handling;
- passive/active sensors;
- fire control;
- ECM / ECCM / decoys;
- armor / bumpers / citadel protection;
- command / communications / datalinks;
- crew support / automation;
- cargo / tanks / stores;
- hangars / small-craft support;
- mining / salvage / repair / industrial / science / colony mission systems.

---

## 3. Единство физики и экономики

`Ship Mathematics v1.0` не является только боевой системой.

Масса, объём и компонентный состав влияют одновременно на:

```text
ship construction inputs
required industrial components
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

Следовательно, Stage 18 обязан дать реальный industrial path к каждому production module/hull, а Stage 22 не имеет права балансировать ships только через цену, DPS или class multipliers независимо от physical fit.

High-tier technology выражается через реальные engineering/material/manufacturing capabilities, а не через blanket `Mk II = +25% all stats`.

---

## 4. Обязательная связь со Stage 18 Resources / Industry / Infrastructure

Stage 18 превращает `material/component construction requirements` из декларативных полей в живую экономику.

Минимальная цепочка:

```text
physical resource occurrence
→ compatible extraction
→ refining / purification
→ engineering material / consumable
→ industrial component
→ module / ammunition / infrastructure
→ ship / station
→ maintenance / repair / replacement
→ bounded salvage / recycling
```

Stage 18 должен:

- использовать агрегированные, но физически осмысленные raw/material families;
- различать bulk metal, light metal, conductor, strategic/refractory, silicate/electronic, carbon/chemical, water/volatile and fuel chains where they create gameplay distinctions;
- давать `HEAVY_COMPONENTS`, `ELECTRICAL_COMPONENTS`, `PRECISION_COMPONENTS` или эквивалентный небольшой component layer;
- связывать modules/ammunition с реальными recipes;
- связывать shipyard capability с physical facilities;
- не создавать hidden player/AI supply;
- сохранять finite reserves, inventories, work and time;
- поддерживать deterministic save/load and salvage/recycling conservation.

### 4.1. Правило агрегации

Не требуется отдельный commodity для каждого real-world alloying element, reagent или semiconductor dopant.

Split aggregate family допустим только если он создаёт meaningful difference по:

- source/occurrence;
- extraction/refining method;
- storage/logistics;
- facility capability;
- strategic scarcity;
- module/technology dependency;
- recycling/substitution.

---

## 5. Обязательная связь с генерацией мира

**Stage 20** world generation использует физические и временные масштабы, согласованные с `Ship Mathematics v1.0`, и закрытую Stage-18 resource/facility ontology.

Generated geometry нельзя определять независимо от возможностей ships и logistics.

Минимум учитываются:

```text
local distances m / km / AU
inter-system topology and route lengths
ship acceleration m/s2
velocity evolution m/s
braking distance/time
reaction-mass consumption kg / delta-v m/s
jump / FTL time and constraints
sensor detection envelopes
track/fire-control envelopes
communications latency where relevant
combat engagement distances
station approach / docking distances
resource-body/mining-field spacing
logistics travel time
trade-route round-trip time
construction supply time
fleet reinforcement time
strategic response time
production / consumption cycle time
```

### 5.1. World scale calibrated from representative ships

До freeze generation distributions выбрать минимум:

- early civilian freighter;
- bulk freighter;
- mining ship;
- patrol/corvette;
- frigate/destroyer;
- cruiser;
- capital fleet;
- tanker/logistics support.

Для scale bands иметь benchmark travel calculations:

```text
origin → local station
station → resource zone
inner-system → outer-system
jump arrival → economic hub
system → neighboring system
regional multi-hop route
```

### 5.2. Экономический масштаб учитывает transport time

Production, consumption, buffers and construction demand калибруются относительно real logistics latency.

Нельзя независимо задать factory cadence, которая физически несовместима с possible supply, если chronic shortage не является explicit design outcome.

### 5.3. Sensor/world scale compatibility

Если passive sensors способны обнаруживать emissions на system scale, visibility model обязана это учитывать.

Сохраняются уровни:

```text
DETECTED
CLASSIFIED
TRACKED
FIRE_CONTROL
```

### 5.4. Combat/navigation — одна coordinate system

Combat distances, formation spacing, missile/interceptor stand-off, weapon time-of-flight and local navigation используют совместимые physical units.

Запрещены unrelated strategic/combat/sensor distance units без canonical conversion.

---

## 6. Требование к Stage 17.5

Stage 17.5 должен закрыть runtime slices:

1. production `HullDefinition` / geometry / slot topology;
2. production `ModuleDefinition` and common module contract;
3. central derived-ship calculator;
4. mass/thrust/reaction mass/delta-v runtime;
5. power/stored energy/thermal topology;
6. fitting validation;
7. sensor/signature/track state;
8. EW/datalink/fire-control;
9. weapon families/ammunition/guidance;
10. protection/compartments/subsystem damage;
11. damage-driven degradation of real capabilities;
12. persistence/migration;
13. player/AI shared capability APIs;
14. fitting/shipyard UI over authoritative model;
15. deterministic regression matrix against v1.0 benchmarks.

---

## 7. Требование к Stage 22 Content & Balance

Stage 22 расширяет content, не создавая вторую систему правил.

Направления:

- broader technology/material families where a split is meaningful;
- faction-specific engineering doctrines;
- civilian/military/industrial module catalog;
- hull families and variants;
- weapons/ammunition/seeker families;
- armor/protection families;
- sensor/EW families;
- reactors/drives/thermal systems;
- logistics/support modules;
- expansion of Stage-18 shipyard/facility capabilities;
- construction/maintenance/repair/replacement balance;
- fleet composition/doctrine balance;
- world-scale logistics soak;
- combat saturation/endurance soak;
- economic replacement-loss soak;
- anti-universal-build tests;
- anti-linear-tier-obsolescence tests.

### Stage 22 invariant

> Новый module/hull/technology/resource split валиден только если его advantages/disadvantages выражаются через accepted physical + Stage-18 economic model and common validation/benchmark rules.

Если нужен новый fundamental stat/resource, это **architecture change request**.

---

## 8. Acceptance requirements

Проверить:

- основные module families описываются общей integration contract;
- нет mandatory class-name bonuses;
- derived ship characteristics вычисляются из authoritative inputs;
- physical parameters имеют canonical units;
- damage меняет real subsystem capabilities;
- civilian/economic ships используют same mass/power/heat/movement model;
- Stage-18 recipes способны физически производить representative v1.0 ships/modules;
- world scales согласованы с representative travel times;
- sensor/weapon ranges согласованы with generated system scales;
- logistics times согласованы with production/consumption/construction cadence;
- Stage 17.5 runtime decomposition complete;
- Stage 18 industrial ontology complete;
- Stage 20 generation calibrated;
- Stage 22 content expansion remains inside common model.

---

## 9. Каноническое правило

```text
Ship Mathematics research
→ v1.0 Design Baseline
→ Stage 17.5 physical runtime
→ Stage 18 resource / industry / infrastructure foundation
→ Stage 19 warfare consuming real logistics/industry
→ Stage 20 physically calibrated world generation
→ Stage 21 living-world/RPG layer
→ Stage 22 broad content / technology / balance
```

Порядок нельзя разворачивать так, чтобы mass content или world generation закрепили scales/resources, противоречащие принятой корабельной физике и physical economy.