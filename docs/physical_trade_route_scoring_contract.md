# Star Empires — Physical Whole-Route Trade Scoring Contract

> Статус: **ACCEPTED CROSS-STAGE INVARIANT**  
> Область: межсистемная торговля, civilian logistics, faction procurement, route planning, Stage 17.5 ship physics, Stage 18 industry/logistics, route risk, diplomacy, future world generation и AI.
>
> Этот документ дополняет, а не заменяет:
> - `docs/inter_system_navigation_contract.md`;
> - `docs/trade_route_planning.md`;
> - `docs/cumulative_route_risk_model.md`;
> - `docs/stage17_5_combat_depth_implementation_plan.md`;
> - `docs/stage18_resources_industry_infrastructure_plan.md`;
> - `docs/stage20_physical_world_generation_plan.md`.

---

## 1. Главный принцип

> **Экономическая выгодность межсистемного рейса оценивается по полному физическому маршруту edge-by-edge, а не только по ценам в начальной и конечной системах и не по прямому геометрическому расстоянию между ними.**

Если маршрут имеет вид:

```text
A → B → C → D
```

то economic/logistics planner обязан считать последствия всех трёх реальных переходов:

```text
A → B
B → C
C → D
```

а также необходимых локальных участков внутри систем.

Система `D` не является допустимым торговым назначением для флота из `A`, если authoritative topology/path planner не может построить реальный маршрут из соседних jump edges.

---

## 2. Topology first, economics second

Для межсистемной торговли каноническая последовательность такая:

```text
market opportunity
→ authoritative GalacticPath over explicit neighbor edges
→ physical/temporal route consequences
→ political/access consequences
→ risk consequences
→ economic score
→ execution hop-by-hop through ordinary WorldSimulation/FleetJumpService
```

Запрещено:

- оценивать `A → D` как прямой рейс, если topology требует `A → B → C → D`;
- использовать Euclidean system-to-system distance вместо реального jump path;
- позволять economic planner выбрать маршрут, который physical executor не может пройти;
- давать AI abstract strategic relocation в обход промежуточных систем;
- считать конечный рынок выгодным, игнорируя невозможный или запрещённый промежуточный edge.

---

## 3. Каноническая экономическая величина

Для обычного коммерческого рейса долгосрочная целевая модель концептуально равна:

```text
expectedNetProfit =
      expectedSaleRevenue
    - purchaseCost
    - propulsionAndReactionMassCost
    - jumpEnergyAndConsumablesCost
    - maintenanceAndWearCost
    - endpointTaxesAndTariffs
    - transitTaxesFeesAndTolls
    - insuranceEscortAndSecurityCost
    - expectedCargoAndAssetLossCost
    - otherExplicitPhysicalRouteCosts
```

Production comparison для обычной торговли должен по умолчанию учитывать время:

```text
tradeUtility ≈ expectedNetProfit / fullExpectedRouteTime
```

Фракционная doctrine может вводить дополнительные objective weights или hard constraints, но не должна удалять реальные физические расходы из модели.

`gross profit` допустим только как явно названная regression/debug policy, а не как финальная production экономика.

---

## 4. FullExpectedRouteTime

Время маршрута должно быть производным от реально исполняемого пути.

Минимально:

```text
fullExpectedRouteTime =
      localTravelToSupplier
    + loading/docking overhead where modeled
    + localTravelToDeparturePoint where modeled
    + Σ everyJumpEdgeTime
    + Σ intermediate local transfer/approach time where modeled
    + localTravelToConsumer
    + unloading/docking overhead where modeled
```

После Stage 17.5 время движения должно использовать capability фактического корабля/флота, включая влияние массы и текущей загрузки там, где это предусмотрено accepted Ship Mathematics.

Нельзя сохранять постоянный `movementSpeed` как окончательную экономическую характеристику, если production movement model уже умеет вычислить реальные acceleration/delta-v/FTL consequences.

---

## 5. Edge-by-edge physical costs

Каждый traversed edge должен иметь возможность внести собственные последствия в route score.

После появления соответствующей физики это включает минимум:

```text
edge transit time
+ FTL spool/energy/cooldown consequences
+ reaction mass / fuel where applicable
+ heat / wear / maintenance consequence
+ edge-specific fee or toll
+ access restriction
+ edge-specific danger/exposure
```

Стоимость длинного маршрута поэтому не обязана быть линейной только по `jumpCount`.

Два маршрута по четыре hops могут иметь существенно разную стоимость.

---

## 6. Intermediate-system consequences

Промежуточная система является настоящим world state, а не декоративной точкой маршрута.

При оценке и/или replanning она может влиять через:

- controlling faction;
- diplomatic access;
- embargo/war status;
- blockade;
- customs/transit tariff;
- security level;
- piracy/combat activity;
- intelligence freshness;
- refuel/rearm/repair possibility;
- safe harbor;
- congestion/queue where modeled;
- alternative outgoing edges.

Endpoint-only tariff/risk calculation является временным неполным приближением и не должен закрепляться как финальная архитектура.

---

## 7. Whole-route risk

Каноническая threat model задаётся `docs/cumulative_route_risk_model.md`.

Trade scoring обязан использовать риск **всего оставшегося пути**, а не только destination danger.

Целевая причинная цепочка:

```text
system/link threat
× exposure time
× intelligence confidence
× actor vulnerability
× cargo/asset value at risk
→ expected route loss cost
```

Когда доступны достаточно качественные вероятностные данные:

```text
routeSurvivalProbability = Π (1 - segmentLossProbability)
routeLossProbability     = 1 - routeSurvivalProbability
```

Простое:

```text
riskPerJump × jumpCount
```

разрешено только как временная bounded approximation до появления segment-aware threat data.

---

## 8. Cargo mass and ship capability

После Stage 17.5 торговая логистика обязана использовать тот же ship capability model, что игрок и боевые/AI флоты.

Нельзя вводить отдельный economic-only speed/fuel model.

Пример причинной цепочки:

```text
more/heavier cargo
→ greater translated/loaded mass
→ changed acceleration / delta-v / energy demand
→ changed travel time and consumable usage
→ changed exposure and operating cost
→ changed trade utility
```

Следовательно, один и тот же ценовой spread может быть выгодным для лёгкого курьера и невыгодным для тяжёлого загруженного bulk freighter.

---

## 9. Physical consumables are not abstract penalties

После появления production consumables:

- reaction mass;
- fuel;
- FTL consumables;
- ammunition where relevant to escort/mission economics;
- repair materials;
- maintenance resources;

их route cost должен происходить из реального потребления/износа и реальной цены восполнения.

Запрещено одновременно:

1. физически расходовать ресурс;
2. ещё раз применять скрытый фиксированный `fuel cost` bonus/penalty, не связанный с этим расходом.

Любая денежная оценка consumable cost является valuation реального физического расхода, а не заменой расхода.

---

## 10. Stage 18 logistics coupling

Stage 18 industry/logistics должна использовать этот же контракт.

Физическая supply chain:

```text
resource source
→ extraction
→ processing
→ component production
→ storage
→ transport over real routes
→ consumer / shipyard / construction site
```

Экономическая доступность input-а зависит не только от цены на source market, но и от реального маршрута доставки.

Должна выполняться цепочка:

```text
route distance/topology
→ travel time
→ operating cost
→ freighter throughput
→ buffer requirement
→ delivered cost
→ shortage pressure
→ market price / industrial viability
```

Нельзя компенсировать плохую/длинную топологию hidden restock или virtual delivery.

---

## 11. Replanning is mandatory

Маршрут — advisory plan, а не гарантированная будущая реальность.

После каждого hop или при существенном событии remaining route может быть пересчитан.

Replanning triggers включают минимум:

- edge/access changed;
- blockade/war/embargo changed;
- market price or stock materially changed;
- consumer liquidity/capacity changed;
- ship damaged;
- propulsion/FTL capability changed;
- fuel/reaction mass/energy reserve changed;
- escort composition changed;
- threat/intelligence changed;
- better alternative route appeared.

Пересчитывается **оставшийся физический путь от текущей системы**, а не исходная абстрактная пара `origin → destination`.

Hysteresis/switching cost допустим для предотвращения oscillation, но не может разрешать физически невозможный следующий hop.

---

## 12. Player / AI parity

Игрок, NPC traders, faction logistics и automated player fleets должны использовать совместимые route facts.

Допустимо различать:

- доступную информацию;
- doctrine;
- risk tolerance;
- strategic priority;
- willingness to accept losses;

Недопустимо различать базовую физику и topology.

AI не получает:

- бесплатное топливо;
- бесплатный reaction mass;
- teleport;
- меньшую реальную длину маршрута;
- игнорирование транзитных систем;
- hidden trade-profit bonus для компенсации плохой логистики.

---

## 13. Current implementation baseline — 2026-08-16

На момент принятия этого contract уже реализовано:

```text
GalacticPathPlanner
→ строит path только через topology.neighbors(...)
→ суммирует authoritative direct-edge timing

GalacticMarketDiscovery
→ ищет reachable systems через neighbor graph
→ передаёт реальный GalacticPath в opportunity

GalacticTradeOpportunity
→ totalExpectedSeconds включает jumpPath.totalJumpSeconds()

TradeRoutePlanner
→ production galactic scoring может использовать PROFIT_PER_SECOND
→ получает full GalacticPath + route risk metadata в TradeRouteCostModel.Context

InterSystemTradeJob
→ исполняет path hop-by-hop через WorldSimulation.requestFleetJump(...)
```

Это сохраняется как foundation.

Текущие известные временные упрощения:

```text
route risk
≈ riskPerJump × jumpCount

world route costs
≈ endpoint customs/tariffs + existing strategic policies

ship operating cost
→ ещё не использует полный Stage-17.5 reaction-mass/energy/maintenance model

intermediate transit politics/risk
→ ещё не полностью оцениваются segment-by-segment
```

Эти ограничения являются **implementation debt**, а не принятым финальным дизайном.

---

## 14. Stage ownership

### Stage 17.5

Добавить production ship capability/consumable inputs, необходимые route economics:

- loaded mass;
- propulsion consequences;
- reaction mass/fuel;
- power/energy;
- FTL spool/transit/cooldown costs;
- maintenance/wear seams;
- damage-dependent capability.

Route planner не обязан сам симулировать двигатель; он получает deterministic derived route consequences из общей ship model.

### Stage 18

Связать физическую промышленную/logistics ontology с delivered cost и throughput:

- real transported resources;
- storage/buffers;
- refuel/replenishment inputs;
- maintenance/repair inputs;
- freight capacity;
- delivered-cost consequences;
- no virtual delivery/restock.

### Subsequent danger/diplomacy/AI work

Заменить uniform hop-risk и endpoint-only political costs на whole-route segment-aware model:

- system threat;
- link threat;
- war/front/blockade;
- transit access;
- intermediate tariffs/tolls;
- actor-specific risk;
- intel freshness;
- escort/security economics.

### Stage 20 world generation

Generated topology должна проходить economic acceptance с этим route model: supply chains обязаны быть физически достижимы и иметь разумный delivered cost/throughput без hidden shortcuts.

### Balance stage

Балансировать параметры только после того, как score использует реальные route consequences. Нельзя «исправлять» плохую логистику универсальным profit multiplier.

---

## 15. Acceptance scenarios

Mature implementation должна детерминированно проходить минимум следующие сценарии.

### A. Same spread, different topology

```text
Route 1: 2 hops
Route 2: 7 hops
same endpoint prices
```

При прочих равных длинный путь получает большее время и реальные дополнительные operating/risk costs.

### B. Equal hop count, different edges

Два маршрута по 4 hops имеют разные transit times/fees/risk; planner способен выбрать более выгодный полный путь, а не считать их одинаковыми.

### C. Dangerous chokepoint

Кратчайший путь пересекает опасный/заблокированный chokepoint; безопасный обход может выиграть по actor-specific expected utility.

### D. Loaded vs empty ship

Один hull с разной загрузкой получает разные physical route consequences там, где mass влияет на accepted ship model.

### E. Transit politics

Endpoint markets одинаковы, но один маршрут проходит через запрещённую/дорогую транзитную территорию; это влияет на feasibility/utility.

### F. Route changes during execution

После первого hop следующий edge закрывается. Fleet не телепортируется к destination: remaining route replans from the actual current system or job fails/holds according to doctrine.

### G. Real consumables

Маршрут, для которого у корабля недостаточно physical consumables или нет feasible replenishment plan, не принимается только потому, что endpoint spread положителен.

### H. Economic geography

Изменение одного central edge/blockade/security state должно измеримо менять traffic, delivered cost, shortages и привлекательность альтернативных систем без глобального scripted production penalty.

---

## 16. Hard invariants

1. every inter-system trade route references an authoritative neighbor-edge path;
2. route feasibility is checked before economic attractiveness;
3. full route time includes every executed jump edge;
4. final production economics must account for every material physical route cost once that subsystem exists;
5. intermediate systems/edges may affect access, cost and risk;
6. route risk is whole-path, actor-specific and information-bounded;
7. physical consumable use is shared with player/AI simulation and is never replaced by hidden economic penalties;
8. player and AI use the same topology and physical ship capability rules;
9. remote-simulation LOD may aggregate computation, but not skip physical route semantics;
10. route plans revalidate/replan from actual current state;
11. no hidden restock, virtual delivery or strategic teleport may rescue an otherwise infeasible supply chain;
12. temporary approximations must remain explicitly documented as approximations and be replaced when their authoritative subsystem becomes available.

---

## 17. Completion definition

Этот contract считается полностью materialized, когда:

> **NPC/player/faction logistics выбирают и исполняют межсистемные рейсы по одному authoritative neighbor graph; economic score использует полный physical path, его фактическое время, consumables, operating/maintenance costs, endpoint и transit policy, cumulative segment risk и delivered-cost consequences, а изменение topology/war/blockade/ship capability естественно перестраивает торговые потоки без teleport, hidden supply или unrelated economy bonuses.**
