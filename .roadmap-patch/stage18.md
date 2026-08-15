# Stage 18 — strategic warfare + coercive diplomacy + advanced combat behavior

**PLANNED после Stage 17.5 COMPLETE.**

Stage 18 завершает вооружённую половину политической модели. Он не создаёт отдельную diplomacy subsystem, а использует Stage-17 treaties, claims, directed trust/grievances, economic dependencies, territory и treasury вместе с Stage-17.5 physical combat capabilities.

## 18A — formal conflict state / crisis escalation

War не выводится автоматически из одного `relation` threshold. Persistent conflict state хранит:

- participants;
- legal state: peace / crisis / war / ceasefire;
- cause / triggering grievance;
- start time;
- explicit war goals;
- treaty obligations and joined allies;
- optional escalation/ceasefire constraints.

Переход к войне должен быть отдельным strategic decision с оценкой security gain, expected cost, logistics readiness, treaty credibility и economic dependence.

## 18B — war goals / political objectives

Военные цели имеют world-state meaning:

- obtain/control/recognition of конкретной territory;
- remove foreign base / construction right;
- force market/transit concession;
- end blockade/embargo;
- impose or remove treaty clause;
- obtain reparations через real treasury transfer;
- defend/restore союзника по guarantee;
- ограниченная punitive goal без обязательного annexation.

Нет победы, которая materialize-ит reward только потому, что заполнилась абстрактная war-score шкала. Progress оценивает реальное possession, blockade, losses, logistics и ability/willingness сторон продолжать войну.

## 18C — mobilization / readiness

Мобилизация проявляется в экономике до выстрелов:

```text
military goal
→ ammunition / fuel / repair / replacement stock demand
→ treasury budget pressure
→ production and logistics response
→ fleet readiness
```

Нельзя получить mobilized fleet через бесплатный spawn. Недостаток ammunition, replacement parts, reaction mass или shipyard capacity ограничивает реальную способность вести войну.

## 18D — blockade / interdiction

Blockade — physical operation fleets/assets на routes, jump chokepoints или возле markets.

Effective blockade зависит от:

- actual fleet presence and combat capability;
- sensor/track capability;
- route geometry;
- ability to intercept;
- defender/escort presence;
- alternative routes;
- resupply/endurance блокирующей стороны.

Кнопка `blockade` сама по себе не удаляет импорт. Traders reroute или прекращают рейс из-за реального legal/risk/access state.

## 18E — fronts / objectives / advanced tactical AI

Strategic objectives строятся из territory, infrastructure, logistics and intelligence. Tactical layer после Stage 17.5 использует общие capabilities:

- escort / screen / intercept;
- retreat / pursuit;
- formation doctrine;
- range / mobility / sensor-aware behavior;
- ammunition/endurance awareness;
- protection of logistics assets and chokepoints.

AI не получает omniscience и не дублирует combat physics.

## 18F — war economy / replacement consequences

Conflict обязан менять living economy:

- traffic rerouting;
- shortage and price shocks;
- ammo/repair/reaction-mass expenditure;
- damaged/destroyed physical assets;
- shipyard replacement backlog;
- treasury drain;
- construction delays;
- temporary loss of markets/routes;
- salvage/capture where ordinary mechanics permit.

Никакого scripted replacement уничтоженных fleets/stations.

## 18G — ceasefire / settlement / peace treaty

War завершается explicit settlement clauses:

- territorial recognition/control changes;
- withdrawal deadlines;
- market/transit/construction rights;
- tariff/access terms;
- treaty termination or guarantees;
- reparations with conserved transfers;
- demilitarized/basing restrictions, если соответствующие mechanics существуют.

Compliance восстанавливает credibility; breach создаёт новый grievance/crisis. Мир не сбрасывает отношения к фиксированному значению.

## 18H — information / intelligence

Strategic warfare использует confidence/freshness/decay. До Stage 19 допустим authoritative compatibility provider; после Stage 19 тот же decision API получает observed/intelligence state с communication latency.

## 18I — deterministic conflict acceptance

```text
trade-dependent factions
→ access/tariff dispute
→ embargo and measurable economic damage
→ ultimatum rejected
→ mobilization creates real stock/logistics demand
→ formal war
→ physical blockade reroutes traffic
→ shortages, losses and replacement backlog
→ ceasefire
→ reparations + territorial/access settlement
→ save/load continuation
→ no money/resources/fleets created by diplomacy or war state itself
```

