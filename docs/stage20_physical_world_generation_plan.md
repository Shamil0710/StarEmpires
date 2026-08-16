# Star Empires — Stage 20 Physical World Generation / Discovery Plan

> Статус: **PLANNED**  
> Основание: accepted `Ship Mathematics v1.0 Design Baseline`, production Stage 17.5 и Stage 18 Resources / Industry / Infrastructure Foundation  
> Назначение: сделать deterministic seed-driven galaxy generation физически согласованной с кораблями, сенсорами, логистикой, промышленной онтологией, экономикой и временем.

Canonical generation-diversity contract: `docs/galaxy_topology_resource_geography_generation_contract.md` — **ACCEPTED CROSS-STAGE INVARIANT**.

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

Дополнительная mandatory generation chain:

```text
macro regions
→ system placement
→ explicit neighbor topology
→ topology diversity gate
→ regional physical conditions
→ Stage-18 resource occurrences
→ facilities / economic bootstrap
→ faction-start candidates
→ whole-route delivered-cost / dependency analysis
→ world-quality gate
→ materialized authoritative world
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

Те же calibration runs задают versioned acceptance bands для topology/resource quality metrics. Порогам запрещено быть произвольными вечными constants: они должны быть проверены representative ships, trade cadence, reinforcement times и Stage-18 supply chains.

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

Generation profile также фиксирует calibrated bands минимум для:

```text
maxLinearCorridorLength
maxDegreeOneFraction
minRegionalCycleCoverage
minCoreRouteRedundancy
maxSingleGatewayDependency
sectorExitBand
hubDegreeBand
regionalHopDistanceBand
```

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

## Topology diversity generation

`Sector` должен быть spatial/strategic region, а не механическим отрезком списка systems.

Generator обязан поддерживать смесь structural motifs:

```text
local hubs
forks
cycles / rings
low-density meshes
corridors
border gateways
remote pockets
bounded frontier dead ends
alternate long/risky paths
rare strategic chokepoints
```

Production connectivity algorithm не может считать sequential chain достаточным финальным результатом.

Допустим connected backbone как технический intermediate step, но после него mandatory:

```text
spatial candidate edges
→ connected backbone
→ intra-region redundancy
→ selected inter-region gateways
→ bounded frontier branches
→ topology quality analysis
→ deterministic repair or seed rejection
```

В общем случае developed/core sector имеет больше internal route redundancy, чем inter-sector gateway density. Это создаёт meaningful borders без превращения galaxy в railroad.

## Topology diagnostics

Machine-readable quality report минимум содержит:

- connected components / unreachable systems;
- degree distribution;
- fraction of degree-1 / degree-2 systems;
- hub distribution;
- longest and percentile linear-corridor lengths;
- cycle participation;
- alternate / edge-disjoint route coverage where required;
- articulation systems;
- bridge edges;
- gateway / betweenness concentration proxy;
- sector exit count;
- internal sector redundancy;
- regional remoteness / hop-distance bands;
- structural motif fingerprint per sector.

Long accidental chains, excessive dead ends или excessive single-gateway dependency приводят к deterministic bounded repair или seed rejection. Intentional frontier corridors/chokepoints допускаются только внутри calibrated generation budget.

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

representative generated galaxy
→ connected where intended
→ not predominantly chain-like
→ measurable cycles/forks/hubs/alternate paths
→ bounded chokepoint concentration
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

## Regional resource geography

Generator не делает independent uniform resource roll для каждой системы.

До concrete deposits он создаёт spatially correlated latent physical conditions, поддерживаемые Stage-18 ontology, например:

```text
metallicity / metal-rich potential
asteroid-body density
water / ice potential
volatile potential
carbonaceous potential
light-metal potential
conductor-resource potential
strategic/heavy-metal potential
silicate/rocky potential
fissile potential where applicable
energy/environment potential
other Stage-18-backed physical conditions
```

Concrete occurrence затем выводится как:

```text
regional condition
+ physical host body
+ Stage-18 compatibility
+ local deterministic variance
+ concentration / grade
+ finite reserve
+ extraction difficulty
→ actual deposit
```

Regional correlation создаёт recognizable belts/clusters; local variance и rare exceptions сохраняют exploration value. Ни regional field, ни presentation label не являются runtime production multiplier.

## Comparative advantage

Generation должна создавать regional comparative advantage вместо uniform self-sufficiency.

Один region может быть богат metals, другой volatiles, третий — выгодным industrial/logistics center только потому, что реальные facilities, energy/access и routes позволяют импортировать feedstock.

`SYSTEM_TYPE_INDUSTRIAL = +30% production` и аналогичные bonuses запрещены.

## Essential viability versus strategic dependency

Essential Stage-18 chains стартовой экономики обязаны быть физически reachable в calibrated time/throughput envelope, но не обязаны находиться внутри каждой system/sector.

Допустимо и желательно, чтобы growth/advanced industry/shipbuilding/military production зависели от внешних sources.

Target causal chain:

```text
shortage / expensive source
→ inventory + price pressure
→ profitable imports / substitution
→ physical traffic
→ infrastructure / escort / stockpile demand
→ measurable faction dependency
→ diplomacy / diversification / expansion / coercion / war
→ changed physical supply state
```

Shortage допустим. Dependency допустима. Crisis допустим. Accidental unrecoverable dead economy — нет.

## Faction-start placement order

Faction starts оцениваются только после topology + resources + facilities:

```text
topology + resources + facilities
→ viability/dependency diagnostics
→ faction-start candidate evaluation
→ bounded deterministic placement
```

Starts могут быть асимметричны по centrality, resource access, frontier exposure и supplier diversity, но normal procedural seed не должен создавать accidental unrecoverable start или случайную civilization-critical monopoly одной faction без explicit scenario design.

## Required dependency diagnostics

Минимум для sector/faction-start region:

- essential local supply coverage;
- import dependency by family;
- export potential;
- supplier concentration;
- route concentration;
- delivered-cost bands;
- throughput headroom;
- buffer depletion exposure;
- critical gateway dependency;
- alternative supplier/path count;
- accessible reserve/ownership concentration for critical resources.

Это authoritative-derived diagnostics, а не scripted objectives.

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

Whole-route dependency/cost использует `docs/physical_trade_route_scoring_contract.md`: геометрическая близость не заменяет actual neighbor-edge path.

## Bootstrap acceptance

Generated world не принимается, если типичная seed economy:

- не имеет физического пути к essential Stage-18 resource/component chain;
- требует impossible delta-v;
- имеет consumption быстрее theoretical maximum delivery без намеренного shortage design;
- требует facility capability, которой физически невозможно снабжаться;
- автоматически получает hidden supplies;
- добавляет universal fallback deposit только ради спасения экономики;
- имеет accidental start, который необратимо collapses независимо от разумных faction actions;
- создаёт unintentional civilization-critical monopoly без meaningful alternative source/route/political response.

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

Industrial center может быть intentionally separated от resource source, если actual freight routes, storage, energy/access и facilities делают такую специализацию жизнеспособной.

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

Calibration также должна показать, что regional comparative advantage действительно создаёт measurable trade potential, а не только красивые resource labels.

---

# 13. Stage 20K — deterministic seed/persistence contract

Generator обязан быть:

- deterministic по seed/version;
- bounded;
- stable-order;
- migration-aware;
- reproducible headless.

Generated stable IDs не должны зависеть от collection iteration order.

Same:

```text
worldSeed
+ generatorVersion
+ generationProfile
+ contentFingerprint
```

должны создавать equivalent physical world и equivalent generation-quality report.

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

## Topology diversity tests

Для representative seed batch проверять минимум:

- one connected ordinary production galaxy where intended;
- degree distribution;
- bounded degree-1 fraction;
- bounded long degree-2 / no-choice corridors;
- hub/fork/cycle presence;
- regional cycle / alternate-route coverage;
- articulation-system and bridge-edge concentration;
- critical gateway dependency;
- sector exit diversity;
- core versus frontier redundancy;
- topology fingerprint diversity between sectors.

Seed не принимается только потому, что connected: connected chain остаётся плохим production result.

## Resource/economy tests

- Stage-18 occurrence associations statistically plausible within authored setting rules;
- regional resource autocorrelation measurable without uniform sector bonuses;
- local variance preserves exploration-worthy exceptions;
- essential supply chains physically feasible;
- remote resources создают measurable logistics premium;
- refining/precision/shipyard specialization creates real trade;
- comparative advantage creates inter-region flows;
- typical start regions viable but not universally self-sufficient;
- supplier and route concentration measurable;
- accidental civilization-critical monopoly rejected unless explicit scenario design;
- no hidden restock;
- buffers не обнуляют distance;
- world does not require every system to be economically self-sufficient.

## Strategic emergence tests

- gateway closure changes delivered cost/route choice/dependency diagnostics;
- rich source with poor access differs economically from equally rich redundant-access source;
- critical supplier/gateway importance can be derived from authoritative state;
- faction strategic response consumes measured interests/dependencies rather than worldgen capture tags.

## Faction-start tests

- asymmetry allowed;
- no accidental unrecoverable start;
- essential inputs physically reachable in accepted time/throughput bands;
- alternatives exist for civilization-critical dependency unless explicit scenario override;
- no free faction asset/resource correction after start.

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

## World-quality gate outcome

Каждый production candidate получает reproducible machine-readable result:

```text
ACCEPT
DETERMINISTIC_REPAIR
REJECT_SEED
EXPLICIT_SCENARIO_OVERRIDE
```

Repair bounded и versioned. Repair не имеет права создавать hidden resources, нарушать Stage-18 host compatibility, physics, conservation, player/AI parity или выдавать free faction assets.

---

# 15. Stage 20 hard invariants

1. every authoritative local distance maps to meters;
2. every ship ETA uses actual movement/jump capability;
3. cargo/ship mass affects logistics time through shared physics;
4. every ordinary inter-system movement is exactly one explicit neighbor edge; no direct jump to non-neighbor systems and no skipped intermediate hops;
5. production topology cannot use sequential chain as a sufficient final generation algorithm;
6. generated topology must pass measurable anti-linearity/redundancy/criticality gates;
7. sectors are spatial/strategic regions, not list partitions;
8. no instant inter-system teleport outside an explicitly designed transition contract;
9. sensor visibility uses physical channels;
10. discovery does not grant omniscience;
11. production cadence is checked against logistics latency;
12. resource types/facilities come from Stage-18 ontology, not generator-only shortcuts;
13. resource geography combines regional physical correlation + host compatibility + local deterministic variance;
14. sector/archetype labels cannot grant hidden resource/production bonuses;
15. ordinary world does not require every system/sector to be self-sufficient;
16. essential start viability must be physically reachable without hidden supplies;
17. strategic scarcity/dependency must survive bootstrap strongly enough to affect logistics/economics;
18. industrial specialization exists through real facilities/inputs/imports;
19. whole-route economic value/dependency uses actual neighbor-edge paths;
20. generated dead economy requires explicit intended scenario, not accidental seed failure;
21. accidental civilization-critical monopoly is rejected unless explicit scenario design passes acceptance;
22. faction interests derive from authoritative world state, not injected worldgen objectives;
23. player and AI inhabit the same generated geometry, resources and jump graph;
24. deterministic same seed/version/profile/content fingerprint produces equivalent world/report;
25. generator never creates runtime hidden supplies or emergency deposits to bypass physical economy.

---

# 16. Stage 20 completion definition

Stage 20 COMPLETE означает:

> **галактика и системы генерируются детерминированно из закрытой Stage-18 resource/industry ontology так, что расстояния, travel time, delta-v, neighbor-only jump topology, sensor visibility, resource occurrence, industrial specialization, logistics throughput и economic cadence согласованы с accepted Ship Mathematics и production capabilities; topology имеет измеримое разнообразие вместо преобладающей линейной очереди; sectors образуют различимые regions с hubs, forks, cycles, gateways, alternate paths, frontier pockets и bounded chokepoints; resources образуют физически осмысленные региональные кластеры с comparative advantage и local variance; typical starts жизнеспособны, но сохраняют реальные внешние зависимости; whole-route delivered cost и gateway concentration естественно создают торговые и стратегические интересы; плохие seeds отклоняются или детерминированно исправляются до materialization; discovery остаётся explicit, дальние маршруты исполняются как последовательности direct edges, а generated world не требует hidden teleport/restock/resource shortcuts для нормальной жизни.**
