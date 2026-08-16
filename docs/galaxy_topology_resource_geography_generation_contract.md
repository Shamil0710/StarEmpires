# Star Empires — Galaxy Topology & Resource Geography Generation Contract

> Статус: **ACCEPTED CROSS-STAGE INVARIANT**  
> Основной implementation owner: **Stage 20 Physical World Generation / Discovery**  
> Зависимости: Stage 17 faction interests/policy, Stage 17.5 ship capability, Stage 18 resources/industry/logistics, Stage 19 warfare, `docs/inter_system_navigation_contract.md`, `docs/physical_trade_route_scoring_contract.md`.

---

# 1. Назначение

Этот документ фиксирует требования к процедурной генерации галактики, чтобы generated world:

- не превращался в длинную очередь `A → B → C → D`;
- создавал различимые регионы, маршруты, хабы, обходы, frontier pockets и chokepoints;
- распределял ресурсы причинно, пространственно коррелированно и неравномерно;
- создавал comparative advantage и реальные межрегиональные зависимости;
- оставался физически и экономически жизнеспособным без hidden supplies;
- естественно создавал причины для торговли, инфраструктуры, дипломатии, экспансии, охраны маршрутов, блокады и войны;
- использовал одинаковую generated geography для игрока, NPC traders, faction AI и strategic warfare.

Главный принцип:

> **География должна создавать стратегические различия, а экономика — превращать эти различия в физические потоки и интересы. Ни topology, ни resource geography не являются декоративными слоями.**

---

# 2. Каноническая причинная цепочка

```text
macro spatial structure
→ sectors / regions
→ star-system placement
→ explicit neighbor jump graph
→ route redundancy / chokepoints / remoteness
→ physical resource occurrence
→ extraction + processing locations
→ delivered cost / throughput / buffers
→ shortages / surpluses / prices
→ trade flows
→ infrastructure value
→ faction dependencies and interests
→ diplomacy / expansion / security / war
→ changed physical world state
```

Нельзя заменять эту цепочку следующими shortcuts:

```text
MINING_SECTOR tag → +30% production
FRONTIER tag → scripted shortage
STRATEGIC_SYSTEM tag → AI must capture
TRADE_HUB tag → hidden traffic bonus
```

Если система, маршрут или сектор стратегически важны, это должно быть выводимо из реального topology + resources + facilities + logistics + political state.

---

# 3. Разделение генерации на слои

Generation выполняется детерминированными слоями в fixed order:

```text
20-world seed/version
→ macro regions / sector geometry
→ system coordinates
→ topology candidate graph
→ connectivity + diversity shaping
→ topology quality gate
→ physical host/world conditions
→ latent regional resource fields
→ concrete Stage-18 resource occurrences
→ infrastructure / economic bootstrap
→ faction-start placement constraints
→ logistics/economic dependency analysis
→ whole-world quality gate
→ materialized authoritative world
```

Поздний слой может читать результаты раннего, но не должен скрыто переписывать физические правила раннего слоя ради удобного результата.

Например economic bootstrap может отклонить seed или запросить bounded deterministic regeneration, но не имеет права добавить hidden emergency deposit в уже выбранную систему.

---

# 4. Macro geography: sectors are regions, not list partitions

Sector должен представлять пространственно и стратегически различимый region.

Generator обязан поддерживать:

- неодинаковую spatial density систем;
- dense cores;
- sparse border regions;
- remote pockets;
- void-like gaps;
- frontier branches;
- overlapping strategic neighborhoods через explicit межсекторные edges;
- неодинаковую форму и размер сектора в пределах calibrated bounds.

Запрещено определять sector как механическое разбиение отсортированного списка систем на равные группы.

Соседние системы внутри одного physical region могут быть природно коррелированы, но sector membership сам по себе не даёт performance/economic bonus.

---

# 5. Inter-system topology diversity

Canonical navigation invariant остаётся неизменным:

> **Каждый ordinary inter-system movement исполняется ровно по одному explicit neighbor edge за hop.**

Но neighbor graph не должен быть преимущественно линейным.

Generated topology должна содержать смесь структурных motifs:

- local hubs;
- forks;
- cycles/rings;
- meshes малой плотности;
- corridors;
- border gateways;
- remote pockets;
- frontier dead ends в ограниченной доле;
- alternate long/risky paths;
- редкие высокоценные chokepoints.

Ни один motif не должен доминировать настолько, чтобы карта систематически превращалась в один шаблон.

---

# 6. Connectivity construction contract

Generator не должен использовать простую sequential chain как финальную connectivity strategy.

Допустима техническая spanning structure для гарантии connected graph только как промежуточный шаг, после которого topology обязана пройти diversity shaping и quality gate.

Предпочтительная причинная схема:

```text
spatial candidate neighbors
→ bounded local candidate edges
→ connected backbone
→ intra-region redundancy edges
→ selected inter-region gateways
→ bounded remote/frontier branches
→ quality analysis
→ deterministic repair or seed rejection
```

Candidate edge должен иметь spatial/physical justification или explicit special-transition semantics.

---

# 7. Intra-sector versus inter-sector connectivity

В общем случае:

- внутри развитого sector/region route redundancy выше;
- между sectors количество gateway edges ниже;
- frontier sectors могут иметь меньшую redundancy;
- core sectors могут иметь больше alternative paths;
- inter-sector chokepoints допустимы и желательны, но не должны превращать всю galaxy в railroad.

Это создаёт:

```text
interior mobility
+ meaningful borders
+ defendable gateways
+ alternate strategic routes
+ regional identity
```

Не требуется, чтобы каждый sector имел одинаковое количество выходов.

---

# 8. Topology quality metrics

Stage 20 generator обязан вычислять machine-readable topology diagnostics минимум по следующим классам метрик:

## 8.1 Connectivity

- число connected components;
- unreachable systems;
- unreachable sectors;
- diameter / representative route-length bands.

Production ordinary galaxy должна быть connected, кроме явно authored isolated/special regions с отдельным transition contract.

## 8.2 Degree distribution

- доля systems degree 1;
- доля degree 2;
- доля local hubs;
- mean/median degree;
- sector-level exit degree.

Цель — обнаруживать excessive dead ends и excessive chain-like structure.

## 8.3 Linear-corridor metric

Отслеживать максимальные и percentile lengths последовательностей, где intermediate systems имеют только два релевантных route выхода и не создают meaningful branch/cycle choice.

Long accidental corridors должны приводить к deterministic repair или rejection.

Intentional frontier/corridor content допускается, если находится внутри authored/calibrated budget.

## 8.4 Cycle / redundancy metrics

- доля систем, имеющих хотя бы один meaningful alternate route;
- route redundancy между major regional nodes;
- edge-disjoint/alternate-path availability where required;
- cycle participation.

## 8.5 Criticality metrics

- articulation systems;
- bridge edges;
- betweenness/traffic concentration proxy;
- доля galaxy traffic, потенциально зависящая от одного gateway.

Chokepoints должны существовать как стратегическая возможность, а не как случайная тотальная уязвимость всей карты.

## 8.6 Sector topology diversity

Для каждого sector:

- internal connectedness;
- internal route redundancy;
- exit count;
- average path to exits;
- remoteness from major hubs;
- structural motif fingerprint.

---

# 9. Numeric thresholds are calibrated, not guessed

Этот contract фиксирует **метрики и causal requirements**, но не придумывает вечные magic numbers заранее.

Конкретные acceptance bands обязаны быть versioned generation profile после Stage 17.5/18 calibration, например:

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

Порог считается production-valid только после проверки representative ships, trade cadence, reinforcement time и Stage-18 supply chains.

---

# 10. Resource geography uses latent regional conditions

Stage 20 не распределяет raw resources независимыми uniform dice rolls по каждой системе.

Сначала generation создаёт пространственно коррелированные latent physical fields/conditions, из которых затем выводятся конкретные Stage-18 occurrences.

Примеры возможных latent dimensions, только если они поддерживаются Stage-18 ontology и setting rules:

- metallicity / metal-rich environment;
- asteroid-body density;
- volatile availability;
- water/ice potential;
- carbonaceous material potential;
- light-metal mineral potential;
- conductor-resource potential;
- strategic/heavy-metal potential;
- silicate/rocky-body potential;
- fissile potential where technology requires it;
- energy/environmental potential;
- organic/habitable potential only if Stage-18 ontology defines it;
- rare/anomalous occurrence potential through explicit content rules.

Эти поля являются generation inputs для physical occurrence probability/grade/reserve, а не runtime production multipliers.

---

# 11. Spatial autocorrelation + local exceptions

Resource geography должна сочетать:

```text
regional correlation
+ host-body compatibility
+ local variance
+ rare exceptions
```

Поэтому соседние systems/sectors могут образовывать recognizable belts/clusters, но не обязаны быть одинаковыми.

Например metal-rich regional field повышает вероятность подходящих metallic hosts/deposits, но не гарантирует одинаковую руду во всех системах.

Локальное богатое месторождение в бедном регионе допустимо и полезно для exploration, если оно возникает через explicit probabilistic occurrence rule.

---

# 12. Physical occurrence, not sector bonus

Concrete resource occurrence обязано следовать Stage-18 host/extraction semantics:

```text
regional condition
+ physical host body
+ resource compatibility
+ concentration/grade
+ finite accessible reserve
+ extraction difficulty
→ actual deposit
```

Sector label не создаёт материал из воздуха.

`MINING_REGION` может быть derived presentation label после generation, но не causal source `+X% ore`.

---

# 13. Comparative advantage instead of uniform self-sufficiency

Generation должна намеренно создавать regional comparative advantage.

Хороший region не означает `всего много`.

Примеры допустимой структуры:

```text
Region A
high: metallic / strategic metals
low: volatiles

Region B
high: water / volatiles
low: heavy metals

Region C
high: industrial infrastructure / energy access
low: local bulk feedstock
```

Результат должен создавать physical reasons для inter-region logistics.

---

# 14. Essential viability versus strategic dependency

Generator обязан различать:

## Essential viability

Минимальные critical chains, необходимые для обычного существования стартовой экономики, должны быть физически достижимы в calibrated time/throughput bounds.

Это **не** означает local self-sufficiency каждой системы или каждого sector.

Essential input может находиться:

- локально;
- в соседнем region;
- через короткий устойчивый multi-hop route;
- через несколько competing suppliers.

## Strategic dependency

Advanced growth, high-volume industry, military production, shipbuilding, rare technology и resilience могут намеренно зависеть от distant/limited sources.

Именно strategic dependency должна создавать стимулы к:

- long-range trade;
- stockpiles;
- escorts;
- diplomacy;
- alternative suppliers;
- infrastructure construction;
- territorial expansion;
- blockade breaking;
- coercion/war.

---

# 15. No accidental dead economies

Generated world или faction start не принимается, если без explicit scenario intent:

- essential chain physically unreachable;
- available suppliers не могут обеспечить theoretical minimum throughput;
- единственный critical source недоступен по ordinary law с самого старта без альтернативы;
- required route требует невозможной Stage-17.5 ship capability;
- sector survives only because of hidden restock;
- generator добавляет emergency resources после simulation start;
- стартовая faction economy необратимо collapses независимо от разумных действий actor.

Shortage допустим. Dependency допустима. Crisis допустим.

**Unrecoverable accidental seed failure — нет.**

---

# 16. Scarcity must be meaningful but recoverable

Resource scarcity должна менять delivered cost, throughput, buffer pressure и strategic behavior.

Желаемая causal chain:

```text
resource shortage / expensive route
→ price / stock pressure
→ profitable imports or substitution
→ traffic changes
→ infrastructure/security demand
→ faction recognizes dependency
→ policy / diplomacy / expansion response
→ changed physical supply situation
```

Запрещено генерировать shortage, эффект которого компенсируется hidden universal supply.

---

# 17. Industrial centers may be separated from resource sources

Generator не обязан размещать processing и industry рядом с сырьём.

Наоборот, часть meaningful economic geography должна возникать из разделения:

```text
resource source
→ freight route
→ refinery / processing hub
→ component industry
→ shipyard / military / population consumer
```

Расположение industrial hub может быть обусловлено:

- route centrality;
- energy/access conditions;
- existing facilities;
- security;
- storage/logistics capacity;
- imported feedstock feasibility.

Но никакой `industrial center` не получает output без реальных facilities/inputs.

---

# 18. Economic dependency diagnostics

После bootstrap generator обязан строить diagnostics, а не скрытые gameplay modifiers.

Минимум для каждого sector/faction-start region:

- essential local supply coverage;
- import dependency by resource/component family;
- export potential;
- supplier concentration;
- route concentration;
- delivered-cost bands;
- throughput headroom;
- buffer depletion exposure;
- critical gateway dependency;
- alternative supplier/path count.

Пример derived fact:

> `68% projected volatile imports traverse gateway system X`.

Это diagnostic/strategic fact, который AI и UI позднее могут использовать в пределах своей информации. Это не scripted objective.

---

# 19. Faction-start placement happens after geography

Faction placement не должно определять resource map задним числом.

Порядок:

```text
topology + resources + facilities
→ economic viability/dependency analysis
→ faction start candidate evaluation
→ bounded deterministic placement
```

Start placement обеспечивает разумную возможность существования, но не зеркальную симметрию.

Разные factions могут начинать с разными:

- resource access;
- route centrality;
- supplier diversity;
- frontier exposure;
- expansion opportunities;
- strategic vulnerabilities.

Эти различия должны находиться в accepted balance envelope и возникать из physical geography.

---

# 20. Anti-monopoly bootstrap rule

Обычный production seed не должен случайно выдавать одной стартовой faction фактическую неоспоримую монополию на civilization-critical resource, если setting/scenario специально этого не требует.

Quality gate должен измерять:

- share of accessible reserves;
- supplier ownership concentration;
- alternative source count;
- route accessibility;
- expansion distance to alternatives.

Rare/strategic monopolies допустимы как deliberate world feature, если:

- они не делают остальные starts случайно нежизнеспособными;
- существуют политические/торговые/военные способы реакции;
- feature явно проходит scenario/balance acceptance.

---

# 21. Faction behavior must emerge from state

World generation создаёт conditions, но не пишет faction goals напрямую.

Правильная цепочка:

```text
physical shortage / opportunity
+ route/access/security state
+ faction doctrine/history/information
→ measured interest/dependency
→ ordinary Stage-17/19 decision path
```

Неправильно:

```text
worldgen marked system X important
→ AI receives CAPTURE_X order
```

Faction может хотеть ту же систему X, но потому что она контролирует supply, gateway, shipyard, safe route, rare deposit или иной реальный интерес.

---

# 22. Topology and resource geography must interact

Одинаковое resource richness не означает одинаковую economic value.

Например:

```text
rich deposit + 1 fragile gateway + long haul
≠
rich deposit + 3 redundant routes + nearby refinery
```

Generation/acceptance обязаны оценивать не только `resource units`, но и delivered physical accessibility.

Стратегическая ценность возникает из сочетания:

```text
resource value
× accessibility
× throughput
× route redundancy
× security
× industrial proximity
× political access
```

Это conceptual decomposition, не фиксированная gameplay formula.

---

# 23. Whole-route economics is mandatory

Canonical contract: `docs/physical_trade_route_scoring_contract.md`.

Generated resource/economic quality оценивается через реальные neighbor-edge routes.

Нельзя объявить sectors экономически связанными только потому, что они геометрически близки.

Для supply dependency учитываются:

- actual path;
- every hop;
- travel time;
- ship capability;
- cargo/consumables;
- route policy/access;
- risk/security when available;
- intermediate systems;
- alternative paths.

---

# 24. Strategic chokepoints: intended scarcity of connectivity

Chokepoint считается полезным, если он создаёт choices:

- defend;
- bypass longer route;
- negotiate access;
- escort traffic;
- build infrastructure;
- contest/blockade;
- develop alternate supplier.

Chokepoint считается плохим accidental topology, если потеря одного ordinary edge необоснованно обрывает большую часть galaxy без meaningful alternative gameplay и это не является explicit scenario design.

---

# 25. World-generation quality gate

Каждый production candidate world проходит headless deterministic quality analysis до принятия.

Минимальные diagnostic groups:

```text
TOPOLOGY
- connectivity
- degree distribution
- linearity
- cycles/redundancy
- articulation/bridges
- gateway concentration
- sector structural diversity

RESOURCE GEOGRAPHY
- regional autocorrelation
- resource concentration
- host compatibility
- reserve distribution
- rare-resource concentration

ECONOMIC VIABILITY
- essential-chain reachability
- theoretical logistics throughput
- delivered-cost bands
- supplier diversity
- buffer viability

STRATEGIC DIVERSITY
- chokepoint distribution
- hub distribution
- remote/frontier opportunities
- critical route concentration
- expansion-access diversity

FACTION STARTS
- minimum viability
- dependency diversity
- monopoly risk
- reachable alternatives
- asymmetric-but-bounded opportunity
```

Quality report должен быть machine-readable и reproducible по seed + generator version.

---

# 26. Gate outcomes

Quality gate может вернуть:

```text
ACCEPT
DETERMINISTIC_REPAIR
REJECT_SEED
EXPLICIT_SCENARIO_OVERRIDE
```

`DETERMINISTIC_REPAIR` допускается только для bounded topology/generation corrections, определённых versioned algorithm.

Repair не может:

- создавать hidden resources;
- teleport facilities;
- нарушать Stage-18 occurrence compatibility;
- создавать player-only route;
- менять physics;
- выдавать faction free assets.

`EXPLICIT_SCENARIO_OVERRIDE` требует authored reason и не является обычным procedural default.

---

# 27. Determinism and persistence

Same:

```text
worldSeed
+ generatorVersion
+ generationProfile
+ contentFingerprint
```

должны создавать equivalent generated physical world и equivalent quality report.

После materialization save хранит authoritative generated state. Новая generator version не переписывает существующую campaign silently.

---

# 28. Stage ownership

## Stage 17.5

Предоставляет real ship capability, от которой зависят route feasibility/time/consumables.

## Stage 18

Определяет resource/facility ontology, extraction/refining/industry/storage/logistics chains. Stage 20 не изобретает параллельные commodity types.

## Stage 19

Предоставляет warfare/blockade/security consequences, позволяющие проверить стратегическую ценность gateways, depots, sources и routes.

## Stage 20

Реализует topology/resource geography generator, bootstrap, diagnostics, quality gate и deterministic persistence boundary.

## Stage 21

Использует generated geography для exploration, missions, NPC opportunities и living-world events; не подменяет её scripted disconnected map.

## Stage 22

Балансирует generation profiles/content frequencies после macro soak tests, не исправляя плохую географию universal bonuses.

---

# 29. Required acceptance scenarios

## A. Anti-chain galaxy

Generated world connected, но не состоит преимущественно из длинных degree-2 corridors. Quality metrics фиксируют cycles, forks, hubs, alternate paths и bounded dead ends.

## B. Regional identity

Несколько sectors имеют различимые topology fingerprints и resource profiles без sector-type performance bonuses.

## C. Comparative advantage

Два regions физически жизнеспособны, но один экспортно силён по metals, другой — по volatiles/other Stage-18 family. Trade advantage следует из deposits + routes + facilities.

## D. Essential viability without self-sufficiency

Start region не производит всё сам, но critical missing input имеет physical reachable supply в accepted throughput/time envelope.

## E. Chokepoint economics

Закрытие/подорожание gateway меняет delivered cost, route choice и dependency diagnostics; система становится важной без scripted strategic tag.

## F. Alternative-route value

Два одинаково богатых source regions имеют различную ценность из-за redundancy, travel time и infrastructure.

## G. Faction asymmetric starts

Factions начинают в неодинаковых регионах, но quality gate не допускает случайно безнадёжный start или unintentional civilization-critical monopoly.

## H. Resource cluster + exception

Spatially correlated resource belt существует, но local deterministic variance создаёт exploration-worthy exceptions без uniform random noise.

## I. No hidden rescue

Seed, проваливший essential viability, rejected/repaired до materialization; runtime не получает emergency stock/deposit.

## J. Emergent strategic interest

Из generated dependency можно вывести critical supplier/gateway; faction strategic system получает эти факты через ordinary analysis/policy path, а не worldgen objective injection.

---

# 30. Hard invariants

1. ordinary inter-system movement всегда следует explicit neighbor graph;
2. final topology не может использовать sequential chain как достаточный production topology algorithm;
3. generated topology обязана проходить measurable anti-linearity/redundancy/criticality gate;
4. sectors являются spatial/strategic regions, а не просто list partitions;
5. resource occurrence выводится из Stage-18 ontology + physical host/environment conditions;
6. resource geography использует regional correlation + local variance, а не independent uniform distribution everywhere;
7. sector/archetype labels не дают hidden production/resource bonuses;
8. ordinary world не обязан быть self-sufficient по каждой system/sector;
9. essential start viability должна быть физически достижима без hidden supplies;
10. strategic scarcity/dependency должна сохраняться и влиять на logistics/economics;
11. industrial specialization существует через real facilities/inputs/imports;
12. economic value оценивается через actual whole neighbor-edge route;
13. faction interest выводится из authoritative state, а не injected worldgen objectives;
14. faction starts могут быть асимметричны, но accidental unrecoverable starts rejected;
15. accidental civilization-critical monopoly rejected unless explicit scenario design;
16. quality gate deterministic, machine-readable и versioned;
17. repair не имеет права нарушать conservation/physics/ontology/player-AI parity;
18. same seed/version/profile/content fingerprint воспроизводит equivalent world;
19. generated geography общая для player, NPC, faction AI, economy и warfare;
20. no runtime emergency deposit/restock exists to rescue bad generation.

---

# 31. Completion definition

Этот contract считается реализованным, когда Stage 20 способен детерминированно создавать и принимать только такие worlds, где:

> **galaxy topology имеет измеримое структурное разнообразие вместо линейной очереди; sectors образуют разные regions с hubs, cycles, gateways, alternate paths, frontier pockets и bounded chokepoints; Stage-18 resources распределяются через физически осмысленные региональные поля и host occurrences; стартовые экономики жизнеспособны, но не универсально самодостаточны; comparative advantage, shortages, delivered cost и route concentration создают реальные торговые и стратегические зависимости; faction AI может реагировать на эти зависимости через обычные Stage-17/19 rules; а плохой seed отклоняется или детерминированно исправляется до materialization без hidden resources, teleport или free bonuses.**
