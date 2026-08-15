# Star Empires — Stage 19 Physical World Generation / Discovery Plan

> Статус: **PLANNED**  
> Основание: accepted `Ship Mathematics v1.0 Design Baseline` и `docs/ship_mathematics_v1_roadmap_integration_contract.md`  
> Назначение: сделать deterministic seed-driven galaxy generation физически согласованной с кораблями, сенсорами, логистикой, экономикой и временем.

---

# 1. Главный принцип

> **Мир не генерирует произвольные расстояния, а затем не заставляет корабли “подстраиваться”. World generation выбирает geometry distributions только после расчёта operational consequences для representative ships.**

Каноническая цепочка:

```text
world geometry
+ ship acceleration / delta-v / jump capability
+ sensor/signature envelopes
+ docking/mining/combat geometry
→ travel / detection / response times
→ logistics cadence
→ economic buffers / production cadence
→ playable world scale
```

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

Запрещено создавать несвязанные:

- combat units;
- strategic map units;
- sensor units;
- economic distance points.

Любая derived strategic cost должна иметь физически объяснимую основу.

---

# 3. Stage 19A — representative-ship scale calibration

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

Это не target travel times. Stage 19 должен решить, какие distributions дают желаемый gameplay cadence, и при необходимости менять world geometry, jump network density или technology content **внутри v1.0 model**, а не вводить teleport speed multiplier.

## DoD 19A

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

с расчетом civilian и military profiles.

---

# 4. Stage 19B — star-system physical geometry

Generator должен создавать system geometry в SI.

Минимальные generated concepts:

- stellar/central-body reference;
- local operational regions;
- planets/moons or abstracted celestial anchors where present;
- stations;
- jump arrival/departure zones;
- resource belts/fields/clouds;
- patrol/security zones;
- derelict/anomaly locations;
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

# 5. Stage 19C — local infrastructure spacing calibrated by logistics

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

Например, можно авторить:

```text
SHORT_LOCAL_LOGISTICS
MEDIUM_SYSTEM_ROUTE
LONG_SYSTEM_ROUTE
REMOTE_RESOURCE_ROUTE
```

но каждый label обязан разрешаться в SI distance/time distributions через calibration, а не быть самостоятельной distance system.

---

# 6. Stage 19D — inter-system jump topology

Jump graph генерируется одновременно как strategic topology и physical/temporal logistics layer.

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

## DoD 19D

Для generated region route planner должен выдавать физическое ETA и energy/operational consequence, а не только hop count.

---

# 7. Stage 19E — resource generation + economic bootstrap

Resources не распределяются независимо от transport physics.

Generator должен создавать:

- source regions;
- quality/abundance distributions;
- extraction difficulty;
- initial extraction sites;
- industrial hubs;
- consumer/shipyard demand;
- logistics links.

Затем bootstrap проверяет, что экономика может физически снабжаться существующими кораблями.

## Необходимая coupling

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

- не имеет физического пути к essential resource;
- требует impossible delta-v;
- имеет consumption быстрее theoretical maximum delivery без намеренного shortage design;
- автоматически получает hidden supplies.

---

# 8. Stage 19F — discovery / sensor-consistent visibility

Persistent discovery должен различать:

```text
UNKNOWN
DETECTED / rough contact
CLASSIFIED
TRACKED
KNOWN_STATIC_LOCATION
```

Для static celestial/infrastructure objects можно иметь долговременное knowledge state после survey.

Для mobile fleets применяется sensor/track model Stage 17.5.

## Sensor/world coupling

Если capital plume/thermal emission detectable на десятках миллионов km, generator не может делать вид, что nearby ship абсолютно invisible только из-за tile/fog boundary.

Но дальний detection не раскрывает автоматически:

- точный range;
- identity;
- velocity;
- loadout;
- fire-control position.

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

# 9. Stage 19G — anomalies, derelicts и special locations

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

Derelict/salvage site должен иметь physical remains/resource state, если с ним взаимодействуют economy/salvage systems.

---

# 10. Stage 19H — communications / intelligence latency seam

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

# 11. Stage 19I — generated economy cadence calibration

Production/consumption/build/maintenance cadence проверяется против generated logistics.

Для representative supply chains считать:

```text
mine output per time
freighter payload
round-trip travel time
loading/docking overhead
number of freighters needed
factory consumption rate
buffer depletion time
construction supply ETA
```

Цель — чтобы logistics throughput возникал из ships × distance × cargo × time.

Не использовать hidden `market restock per minute` как замену перевозкам.

---

# 12. Stage 19J — deterministic seed/persistence contract

Generator обязан быть:

- deterministic по seed/version;
- bounded;
- stable-order;
- migration-aware;
- reproducible headless.

Generated stable IDs не должны зависеть от collection iteration order.

Save хранит уже materialized authoritative world; изменение generator версии не переписывает существующую кампанию без explicit migration/new-world policy.

---

# 13. Stage 19K — physical world acceptance matrix

## Scale tests

Для N representative seeds проверить distributions:

- station spacing;
- mining route spacing;
- hub-to-jump distance;
- local travel time;
- multi-hop travel time;
- reinforcement time;
- delta-v demand.

## Sensor tests

- visible objects не скрываются artificial map radius;
- detection ≠ fire-control;
- recon placement меняет knowledge quality.

## Economy tests

- essential supply chains physically feasible;
- remote resources создают measurable logistics premium;
- no hidden restock;
- buffers не обнуляют distance.

## Tactical/strategic scale tests

- combat envelopes малы относительно typical strategic route where intended;
- formation/PD distances сохраняют физический смысл;
- jump arrival zones не создают guaranteed instant point-blank combat без explicit design.

## Performance tests

- generation bounded;
- remote simulation does not full-rate integrate every object;
- generated topology remains route-planner scalable.

---

# 14. Stage 19 hard invariants

1. every authoritative local distance maps to meters;
2. every ship ETA uses actual movement/jump capability;
3. cargo/ship mass affects logistics time through shared physics;
4. no instant inter-system teleport outside jump FSM;
5. sensor visibility uses physical channels;
6. discovery does not grant omniscience;
7. production cadence is checked against logistics latency;
8. generated dead economy requires explicit intended scenario, not accidental seed failure;
9. player and AI inhabit the same generated geometry;
10. deterministic same seed/version produces equivalent world.

---

# 15. Stage 19 completion definition

Stage 19 COMPLETE означает:

> **галактика и системы генерируются детерминированно так, что расстояния, travel time, delta-v, jump topology, sensor visibility, resource placement, logistics throughput и economic cadence согласованы с accepted Ship Mathematics v1.0 и production Stage-17.5 capabilities; discovery остаётся explicit, а generated world не требует скрытых teleport/restock shortcuts для нормальной жизни.**