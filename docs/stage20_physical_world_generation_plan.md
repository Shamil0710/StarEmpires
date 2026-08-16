# Star Empires — Stage 20 Physical World Generation / Discovery Plan

> Статус: **PLANNED**  
> Основание: accepted `Ship Mathematics v1.0 Design Baseline`, production Stage 17.5 и Stage 18 Resources / Industry / Infrastructure Foundation  
> Назначение: сделать deterministic seed-driven galaxy generation физически согласованной с кораблями, сенсорами, логистикой, промышленной онтологией, экономикой и временем.

---

# 1. Главный принцип

> **Мир не генерирует произвольные расстояния и произвольные ресурсы, а затем не заставляет корабли и экономику “подстраиваться”. World generation размещает уже определённые Stage-18 resources/facilities и выбирает geometry distributions только после расчёта operational consequences для representative ships.**

Каноническая цепочка:

```text
Stage 18 resource/object/facility ontology
+ world geometry
+ ship acceleration / delta-v / jump capability
+ sensor/signature envelopes
+ docking/mining/combat geometry
→ travel / detection / response times
→ logistics cadence
→ economic buffers / production cadence
→ playable physical economic geography
```

Stage 20 отвечает прежде всего на вопрос **«где это существует?»**, а не повторно определяет **«что существует?»**.

---

# 2. Authoritative scales

Local physical coordinates:

```text
m
m/s
m/s2
s
```

UI может использовать km / million km / AU / h / days.

Inter-system topology использует stable system IDs + explicit jump edges. Edge transit не является hidden teleport: fitted ship платит spool/energy/cooldown, а edge хранит explicit transit semantics.

Канонический cross-stage navigation contract: `docs/inter_system_navigation_contract.md`.

> **Ordinary inter-system movement разрешён только между непосредственными соседями jump graph. Если `B ∉ topology.neighbors(A)`, ordinary jump `A → B` невозможен. Любой дальний маршрут исполняется как последовательность отдельных neighbor hops.**

Запрещено создавать несвязанные:

- combat units;
- strategic map units;
- sensor units;
- economic distance points.

Любая derived strategic cost должна иметь физически объяснимую основу.

---

# 3. Stage 20A — representative-ship scale calibration

До генерации distribution выбрать stable reference fleet:

1. early civilian freighter;
2. loaded bulk freighter;
3. mining ship;
4. patrol/corvette;
5. escort destroyer;
6. cruiser;
7. capital combatant;
8. fleet tanker/logistics support;
9. carrier/aviation group where relevant.

Для каждого считать:

```text
rest-to-rest local travel time
required delta-v
reaction-mass fraction
braking distance/time
sustained vs max-thrust consequences
jump spool/energy/transit/cooldown
stores/endurance
sensor visibility along route
```

Reference v1.0 lower-bound anchors:

- loaded bulk freighter 100,000 km ≈ 19.18 h;
- loaded bulk freighter 1,000,000 km ≈ 61.58 h;
- escort destroyer sustained 100,000 km ≈ 14.32 h;
- escort destroyer sustained 1,000,000 km ≈ 45.29 h.

Это не target travel times. Stage 20 должен решить, какие distributions дают желаемый gameplay cadence, и при необходимости менять world geometry, jump network density или technology content **внутри accepted model**, а не вводить teleport speed multiplier.

## DoD 20A

Machine-readable table минимум для:

```text
station → station
station → resource field
jump arrival → major hub
inner → outer system
system → neighboring system
regional 3–5 hop route
fleet reinforcement route
```

с расчётом civilian и military profiles.

---

# 4. Stage 20B — star-system physical geometry

Generator должен создавать system geometry в SI.

Минимальные generated concepts используют Stage-18 world-object taxonomy:

- stellar/central-body reference;
- local operational regions;
- planets/moons or abstracted celestial anchors where present;
- asteroid/resource bodies and fields;
- stations/infrastructure;
- jump arrival/departure zones;
- patrol/security zones;
- derelict/wreck/anomaly locations;
- empty transit volume.

Star Empires не обязан быть orbital-mechanics simulator. Но если orbit/planet distance используется визуально или mechanically, geometry должна быть internally coherent и иметь physical coordinates.

## Placement constraints

Generation должен проверять:

- collision/clearance;
- docking approach space;
- jump arrival exclusion;
- weapon safety/strategic stand-off where needed;
- sensor line-of-sight/occlusion if implemented;
- mining approach/access;
- practical travel-time bands.

## Anti-pattern

Не создавать 30 stations на круге radius `1000 units`, если `unit` не имеет SI meaning и все маршруты занимают одинаковые секунды независимо от ship mass/thrust.

---

# 5. Stage 20C — local infrastructure spacing calibrated by logistics

Экономические hubs, factories, mines и shipyards должны размещаться так, чтобы distance имела gameplay consequence.

Generator использует target bands не напрямую по km, а через derived metrics:

```text
civilianTravelTimeBand
militaryResponseTimeBand
roundTripDeltaVBand
expectedCargoCycleTime
expectedSensorExposureTime
```

Затем подбирает physical geometry, которая даёт такие bands для representative ships.

Можно авторить labels:

```text
SHORT_LOCAL_LOGISTICS
MEDIUM_SYSTEM_ROUTE
LONG_SYSTEM_ROUTE
REMOTE_RESOURCE_ROUTE
```

но каждый label обязан разрешаться в SI distance/time distributions через calibration, а не быть самостоятельной distance system.

---

# 6. Stage 20D — inter-system jump topology

Jump graph генерируется одновременно как strategic topology и physical/temporal logistics layer.

## Neighbor-only movement invariant

Generator создаёт explicit graph, а не fully connected distance map.

```text
route destination D
≠ immediate jump destination D

A → B → C → D
= three authoritative hops
= A→B, B→C, C→D
```

Immediate jump destination всегда обязан находиться в `topology.neighbors(currentSystem)`.

Route planner может выбрать удалённую конечную систему и построить multi-hop route, но executor выполняет его edge-by-edge. После каждого hop следующий переход заново существует как ordinary world action; blockade, access state, damage, fuel/energy, diplomacy или topology changes могут изменить возможность продолжения маршрута.

Special gate/wormhole/relay не получает скрытый teleport shortcut. Если он работает как ordinary jump connection, он материализуется как explicit edge. Иной transition type требует отдельного architecture decision.

Для edge хранить минимум:

```text
stable edge ID
origin/destination
access/discovery state
edge transit time or parameters
arrival geometry
hazard/security metadata where physically observed
```

Ship jump plan добавляет:

```text
translated mass compatibility
required energy
spool time
cooldown
heat/damage constraints
```

## Topology goals

Нужно получить одновременно:

- multiple trade paths;
- chokepoints без тотального railroading;
- meaningful border systems;
- remote regions;
- alternative risky/long routes;
- strategic value of tankers/logistics;
- fleet response times, не мгновенные на всю галактику.

## DoD 20D

Для generated region route planner должен выдавать физическое ETA и energy/operational consequence, а не только hop count.

Дополнительно acceptance обязан доказывать:

```text
every ordinary inter-system hop
→ corresponds to one explicit topology edge

non-neighbor direct request
→ rejected

multi-hop route
→ ordered sequence of neighbor edges
→ no skipped intermediate systems
```

---

# 7. Stage 20E — Stage-18 resource occurrence generation + economic bootstrap

Stage 20 **не создаёт новую resource taxonomy**.

Он получает из Stage 18:

```text
resource families
+ world-object/host compatibility
+ extraction methods
+ facility capabilities
+ industrial recipes
+ storage/logistics requirements
```

Generator выбирает:

- конкретные source regions/host bodies;
- resource composition;
- grade/concentration;
- finite accessible reserves;
- extraction difficulty/environment;
- initial extraction sites;
- industrial hubs;
- consumer/shipyard demand;
- logistics links.

## Resource/world coupling

Resource occurrence должна зависеть от host/world conditions.

Примеры baseline associations:

```text
metallic asteroid
→ metallic + strategic metal bias

carbonaceous asteroid
→ water + volatile + carbonaceous bias

icy body
→ water/volatile bias

rocky differentiated body
→ silicate/light-metal/common-metal + possible strategic/fissile deposits
```

Это probabilistic/seed-driven distributions, а не гарантированный loot list.

## Необходимая economic coupling

```text
resource distance
→ haul time
→ ship throughput
→ inventory buffer need
→ price pressure
→ industrial viability
```

Ресурс может быть намеренно remote и дорогим, но это должно быть economic outcome, а не случайный dead economy.

## Bootstrap acceptance

Generated world не принимается, если типичная seed economy:

- не имеет физического пути к essential Stage-18 resource/component chain;
- требует impossible delta-v;
- имеет consumption быстрее theoretical maximum delivery без намеренного shortage design;
- требует facility capability, которой физически невозможно снабжаться;
- автоматически получает hidden supplies;
- добавляет universal fallback deposit только ради спасения экономики.

---

# 8. Stage 20F — industrial specialization bootstrap

Stage-18 economic archetypes используются как **constraints**, а не как готовые bonuses.

Например generated `shipbuilding center` должен реально содержать или физически импортировать:

```text
structural/light materials
+ electrical components
+ precision components
+ required modules
+ yard capabilities
+ power/work/storage
```

Generated `resource frontier` может быть богат сырьём и беден processing capability.

Generated `high-tech center` может зависеть от remote bulk inputs, но иметь precision/electronics advantage только через реальные facilities.

World generator не пишет `SYSTEM_TYPE_INDUSTRIAL = +30% production`.

---

# 9. Stage 20G — discovery / sensor-consistent visibility

Persistent discovery должен различать:

```text
UNKNOWN
DETECTED / rough contact
CLASSIFIED
TRACKED
KNOWN_STATIC_LOCATION
```

Для static celestial/infrastructure/resource objects можно иметь долговременное knowledge state после survey.

Для mobile fleets применяется sensor/track model Stage 17.5.

## Sensor/world coupling

Если capital plume/thermal emission detectable на больших физических дистанциях, generator не может делать вид, что nearby ship абсолютно invisible только из-за tile/fog boundary.

Но дальний detection не раскрывает автоматически:

- точный range;
- identity;
- velocity;
- loadout;
- fire-control position.

## Resource knowledge

Discovery resource body не означает точное знание reserve.

Можно различать:

```text
host known
→ resource indication
→ classified resource family
→ estimated grade/reserve
→ surveyed deposit
```

## Discovery sources

- passive sensors;
- active scans;
- probes/recon craft;
- purchased/shared map data;
- faction intelligence;
- physical visit/survey;
- persistent infrastructure broadcasting.

Все источники должны иметь provenance/freshness там, где информация может устареть.

---

# 10. Stage 20H — anomalies, derelicts и special locations

Special content размещается внутри той же geometry.

Anomaly не телепортирует игрока в отдельное disconnected coordinate system без explicit transition semantics.

Generator должен учитывать:

- detection signature;
- scan requirements;
- approach time;
- local hazard;
- salvage/mining value;
- traffic/security proximity;
- discovery rarity.

Derelict/salvage site должен иметь physical remains/resource state, совместимый со Stage-18 salvage/recycling semantics.

---

# 11. Stage 20I — communications / intelligence latency seam

Если production design вводит non-instant datalink/communications latency на strategic distances, world generation обязан использовать physical distance.

Минимальная architecture готова к:

```text
observation time
transmission time
receipt time
freshness/age
```

Не обязательно вводить realistic light-speed micromanagement на каждом UI action. Но military intel freshness не должна получать отдельные unrelated map units.

---

# 12. Stage 20J — generated economy cadence calibration

Production/consumption/build/maintenance cadence проверяется против generated logistics и Stage-18 recipes.

Для representative supply chains считать:

```text
mine output per time
refinery throughput
component-factory consumption/output
freighter payload
round-trip travel time
loading/docking overhead
number of freighters needed
factory consumption rate
buffer depletion time
construction supply ETA
shipyard replenishment time
```

Цель — чтобы logistics throughput возникал из ships × distance × cargo × facilities × time.

Не использовать hidden `market restock per minute` как замену перевозкам.

---

# 13. Stage 20K — deterministic seed/persistence contract

Generator обязан быть:

- deterministic по seed/version;
- bounded;
- stable-order;
- migration-aware;
- reproducible headless.

Generated stable IDs не должны зависеть от collection iteration order.

Save хранит уже materialized authoritative world; изменение generator версии не переписывает существующую кампанию без explicit migration/new-world policy.

Resource occurrence IDs/reserves, facilities and discovered knowledge входят в persistence semantics.

---

# 14. Stage 20L — physical world acceptance matrix

## Scale tests

Для N representative seeds проверить distributions:

- station spacing;
- mining route spacing;
- hub-to-jump distance;
- local travel time;
- multi-hop travel time;
- reinforcement time;
- delta-v demand.

## Resource/economy tests

- Stage-18 occurrence associations statistically plausible within authored setting rules;
- essential supply chains physically feasible;
- remote resources создают measurable logistics premium;
- refining/precision/shipyard specialization creates real trade;
- no hidden restock;
- buffers не обнуляют distance;
- world does not require every system to be economically self-sufficient.

## Sensor tests

- visible objects не скрываются artificial map radius;
- detection ≠ fire-control;
- recon placement меняет knowledge quality;
- resource survey quality changes deposit knowledge rather than reserve itself.

## Tactical/strategic scale tests

- combat envelopes малы относительно typical strategic route where intended;
- formation/PD distances сохраняют физический смысл;
- jump arrival zones не создают guaranteed instant point-blank combat без explicit design.

## Performance tests

- generation bounded;
- remote simulation does not full-rate integrate every object;
- generated topology remains route-planner scalable;
- generated facilities/resources fit scalability target envelope.

---

# 15. Stage 20 hard invariants

1. every authoritative local distance maps to meters;
2. every ship ETA uses actual movement/jump capability;
3. cargo/ship mass affects logistics time through shared physics;
4. every ordinary inter-system movement is exactly one explicit neighbor edge; no direct jump to non-neighbor systems and no skipped intermediate hops;
5. no instant inter-system teleport outside an explicitly designed transition contract;
6. sensor visibility uses physical channels;
7. discovery does not grant omniscience;
8. production cadence is checked against logistics latency;
9. resource types/facilities come from Stage-18 ontology, not generator-only shortcuts;
10. generated dead economy requires explicit intended scenario, not accidental seed failure;
11. player and AI inhabit the same generated geometry and jump graph;
12. deterministic same seed/version produces equivalent world;
13. generator never creates hidden supplies or emergency deposits to bypass physical economy.

---

# 16. Stage 20 completion definition

Stage 20 COMPLETE означает:

> **галактика и системы генерируются детерминированно из закрытой Stage-18 resource/industry ontology так, что расстояния, travel time, delta-v, neighbor-only jump topology, sensor visibility, resource occurrence, industrial specialization, logistics throughput и economic cadence согласованы с accepted Ship Mathematics и production capabilities; discovery остаётся explicit, дальние маршруты исполняются как последовательности direct edges, а generated world не требует скрытых teleport/restock/resource shortcuts для нормальной жизни.**