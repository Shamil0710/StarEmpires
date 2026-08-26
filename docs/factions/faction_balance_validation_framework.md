# Star Empires — faction balance validation framework

> **Версия:** 1.0  
> **Статус:** CANONICAL VALIDATION CONTRACT  
> **Дата:** 2026-08-26  
> **Scope:** измерение, тестирование и приёмка асимметричного faction content от компонента до
> стратегического восстановления. Сначала применяется к Империи и Индустриальному Союзу, затем —
> к каждой post-core фракции.

Связанные документы:

- `docs/factions/faction_gameplay_visual_balance_bible.md`;
- `docs/factions/faction_implementation_roadmap.md`;
- `docs/stage22_content_balance_plan.md`;
- `docs/characters/character_master_prompt.md`;
- faction systemic identities и faction visual bibles.

Если результат теста противоречит интуитивному описанию фракции, исправляется либо причинная
simulation, либо описание. Скрытый процентный бонус не используется для маскировки расхождения.

---

# 1. Что означает «сбалансировано»

Faction package считается сбалансированным, когда одновременно доказано следующее:

1. он жизнеспособен при компетентной игре и не требует знания сценария заранее;
2. его преимущества возникают из видимых assets, institutions, flows, doctrine и decisions;
3. каждое существенное преимущество имеет оплачиваемую цену и доступную контр-игру;
4. ни одна фракция не является глобальным Pareto-победителем;
5. неблагоприятный matchup создаёт трудную задачу, а не заранее проигранную партию;
6. результат устойчив на нескольких seeds, стартовых позициях и горизонтах;
7. AI использует ту же информацию и те же authority, что и игрок;
8. save/load, replay и migration не меняют причинный результат;
9. игрок может понять причину успеха или поражения из UI и мира;
10. gameplay-образ совпадает с корабельным и персонажным visual language.

Баланс **не** означает:

- одинаковые флоты;
- одинаковые build times;
- одинаковый APM;
- одинаковую кривую силы;
- 50/50 в каждом сценарии;
- зеркальные контрмеры;
- автоматическое выравнивание через faction-wide множитель damage, income или armor.

---

# 2. Единица доказательства

Каждый balance-вывод должен ссылаться на воспроизводимый `BalanceEvidenceRecord` — логическую запись,
не обязательно новый runtime type. Минимальные поля записи:

```text
evidenceId
buildCommitSha
contentFingerprint
saveSchemaVersion
scenarioId
scenarioVersion
factionPackageVersionA/B
aiPolicyVersionA/B
seed
spawnPermutation
startingAssetsFingerprint
startingKnowledgeFingerprint
interventions
resultVector
eventTraceReference
knownLimitations
```

Запись без commit SHA, content fingerprint или seed годится для ручного discovery, но не для gate.

## 2.1. Result vector вместо одного power score

Нельзя сворачивать исход в один «рейтинг силы». Минимальный result vector:

```text
objectiveCompletion
objectiveTime
survivingCapability
irrecoverableAssetLoss
materialLoss
personnelOrCrewBurden
munitionsAndFuelConsumed
repairBacklog
replacementLatency
routeAvailability
informationFreshness
politicalOrOccupationBurden
economicOpportunityCost
recoveryT50
recoveryT80
```

Где `recoveryT50/T80` — время возврата соответственно 50% и 80% заданной baseline capability.
Baseline и capability dimensions объявляются сценарием; их нельзя менять после просмотра результата.

## 2.2. Полная цена силы

Сравнение «равной стоимости» учитывает не только цену hull:

```text
totalBurden = procurement resources
            + manufacturing work
            + facility occupancy
            + training/readiness delay
            + initial stock commitment
            + route and escort burden
            + maintenance burden over horizon
            + expected replacement burden
            + opportunity cost
```

Весовые коэффициенты допустимы только как presentation layer. Сырые dimensions сохраняются всегда.

---

# 3. Слои проверки

## L0 — authority, determinism, persistence

Проверяется до любых balance conclusions:

- один authoritative owner каждого изменяемого состояния;
- стабильные faction IDs и явные migration mappings;
- отсутствие orphaned references после load;
- одинаковый seed + input sequence → одинаковый authoritative result;
- content fingerprint меняется при изменении content;
- replay/save round-trip не добавляет знания и не меняет doctrine;
- UI projections не становятся источником истины;
- generated/demo content не подменяет authored canonical content.

Любой L0 failure блокирует все выводы более высоких слоёв.

## L1 — component и industrial dependency

Измеряются:

- масса, объём, energy/thermal load и maintenance каждого component family;
- число material classes и critical dependencies;
- manufacturing steps и требуемые facility capabilities;
- work-hours и календарный lead time;
- commonality между fits;
- возможность substitution и цена retool;
- запас критических материалов в днях текущего выпуска;
- доля импортной/единственной цепочки.

Acceptance question: преимущество компонента оплачено физикой и industrial chain, а не меткой faction?

## L2 — hull и fit

Измеряются:

- dry/wet mass, propellant fraction, delta-v и acceleration по миссии;
- power и thermal margins в cruise/combat/damaged states;
- crew, habitability, endurance и stores;
- armor/protection distribution и критические зоны;
- sensor, fire-control, EW и communication dependencies;
- weapon arcs, magazine endurance и replenishment;
- repairability, replaceable assemblies и drydock/facility class;
- build time, replacement time и lifecycle burden;
- внешний silhouette/fit соответствует фактическим capabilities.

Каждый hull проверяется минимум в intended role, off-role stress и damaged-return scenario.

## L3 — formation и fleet doctrine

Измеряются:

- coverage задач флотом, а не только duel efficiency;
- scouting-to-engagement latency;
- command/communication degradation;
- concentration time и disengagement capability;
- escort, tanker, repair, salvage и munitions support;
- боезапас и operational tempo между replenishment windows;
- уязвимость к отказу одного role/family;
- способность продолжить задачу после потери flagship/hub/sensor node.

## L4 — logistics и economy

Измеряются:

- throughput материалов, топлива, боеприпасов и replacement assemblies;
- route concentration и число critical edges;
- hub/facility utilization;
- inventory turns и stockout frequency;
- transport work на единицу deployed capability;
- changeover/retool latency;
- repair queue age и production backlog;
- импортная зависимость;
- способность заменить потерю без скрытого spawn.

## L5 — strategy, diplomacy, territory, knowledge

Измеряются:

- качество решения при одинаковой доступной информации;
- стоимость разведывательной ошибки и устаревшего knowledge;
- treaty/market/access dependency;
- mobilization latency;
- occupation, legitimacy, administration и security burden;
- способность конвертировать battlefield result в устойчивый strategic result;
- способность противника увидеть и атаковать dependency;
- соответствие решения persistent doctrine state.

## L6 — attrition и recovery

Измеряются не только потери боя, но кривая восстановления:

- time-to-repair;
- time-to-recrew/retrain;
- time-to-replace;
- накопленный backlog;
- irrecoverable knowledge/facility/habitat loss;
- capability area-under-curve после кризиса;
- момент нового устойчивого operational tempo;
- риск death spiral.

## L7 — player comprehension и visual validation

Проверяется:

- фракция узнаётся в grayscale silhouette;
- class/role читается внутри одной фракции;
- hardpoints/modules подтверждают fit;
- damage state не уничтожает faction identity;
- персонаж соответствует обязательному Character Master Prompt;
- статус читается через faction-appropriate means;
- UI объясняет causal chain, а не только итоговый modifier;
- игрок после события способен корректно назвать главную причину результата.

---

# 4. Метрики и способы расчёта

## 4.1. Industrial commonality

Хранить не один процент, а минимум:

- `uniqueComponentFamilies` на production program;
- `sharedAssemblyFraction` по массе и по work;
- `supplierOrFacilityCount` для critical assemblies;
- `changeoverHours` между двумя главными fits;
- `sparesCoverageDays`;
- `substitutionPenaltyVector`.

Высокая commonality хороша для выпуска и ремонта, но создаёт correlated failure и зависимость от общих
узлов. Оба эффекта должны проявляться в сценариях.

## 4.2. Route concentration

Использовать распределение throughput по маршрутам/узлам, а не субъективную оценку. Допустимы:

- доля крупнейшего edge/hub;
- доля трёх крупнейших edges/hubs;
- HHI как diagnostic;
- максимальный throughput loss после удаления одного узла;
- время reroute и новая транспортная работа.

HHI не является gameplay authority; он лишь обнаруживает концентрацию.

## 4.3. Readiness

Readiness — вектор:

```text
available hulls
mission-capable hulls
trained crews
fuel endurance
munition endurance
repair support
knowledge freshness
command connectivity
```

Один UI percentage разрешён только как проекция с drill-down до исходных dimensions.

## 4.4. Combat exchange

Помимо destroyed hull value записываются:

- mission objective;
- time on objective;
- damage location и lost functions;
- disengaged/recovered/salvaged assets;
- munitions and propellant;
- repair and replacement work;
- support assets exposed;
- knowledge quality на момент решения;
- delayed economic cost.

## 4.5. Resilience curve

После одинаково определённого shock строятся capability curves. Сравниваются:

- глубина падения;
- `T50` и `T80`;
- площадь потерянной capability;
- новый steady state;
- число дополнительных shocks, вызывающих death spiral.

Грамматическая метрика не подменяет визуальный график в review report.

---

# 5. Канонический scenario suite

Каждый scenario имеет версию, неизменяемые starting conditions, разрешённые interventions и explicit
objective. Сценарий должен уметь запускаться с mirrored spawn permutation.

| ID | Сценарий | Главный слой | Что доказывает |
|---|---|---|---|
| B00 | Catalog/authority audit | L0 | IDs, refs, fingerprints, ownership |
| B01 | Save/load/replay round-trip | L0 | persistence и determinism |
| B02 | Viable cold start | L1–L4 | нет скрытой стартовой dependency |
| B03 | Planned expansion | L1–L5 | время и цена развёртывания |
| B04 | Critical-material shortage | L1/L4 | substitution, stock, retool |
| B05 | Single hub/route loss | L4/L6 | concentration и reroute |
| B06 | Distributed low-intensity raids | L3–L6 | patrol coverage и resilience |
| B07 | Equal-burden patrol contest | L2/L3 | role fitness, sensor/endurance |
| B08 | Convoy escort/interdiction | L2–L5 | support fleet и objective play |
| B09 | Prepared-system defense | L3–L6 | reserves, fortification, sustainment |
| B10 | Forced offensive projection | L3–L6 | lift, tempo, overextension |
| B11 | Degraded command and sensors | L2/L3/L5 | doctrine without omniscience |
| B12 | Magazine-limited engagement | L2–L4 | replenishment and fire discipline |
| B13 | Long war / rolling attrition | L1–L6 | production versus preservation |
| B14 | Post-war recovery | L4/L6 | repair, replacement, debt/backlog |
| B15 | Territory occupation | L5/L6 | security, legitimacy, administration |
| B16 | Treaty/market access shock | L4/L5 | institutional dependencies |
| B17 | New enemy adaptation | L1–L5 | doctrine learning and retool latency |
| B18 | Player-causal explanation | L7 | UI clarity |
| B19 | Grayscale ship blind test | L7 | silhouette identity and role read |
| B20 | Character style blind test | L7 | shared style + faction overlay |

## 5.1. Core-pair required subset

До заморозки Stage 22 обязателен полный прогон B00–B14 и B18–B20 для Империи и Индустриального
Союза. B15–B17 обязательны там, где задействованные systems уже production-ready; иначе фиксируется
явный deferred evidence item, а не фиктивный pass.

## 5.2. Post-core required subset

Каждая новая крупная фракция проходит:

1. весь scenario suite solo;
2. все сценарии против core pair;
3. все materially relevant сценарии против каждой ранее выпущенной post-core faction;
4. один seven-faction macro simulation после завершения roster;
5. migration/load тесты со всеми поддерживаемыми save versions.

---

# 6. Experimental protocol

## 6.1. Сначала deterministic probe

Для каждого scenario:

1. фиксируется build/content fingerprint;
2. запускается один seed для проверки assertions и telemetry;
3. выполняется exact replay;
4. выполняется save/load continuation;
5. только затем запускается seed batch.

## 6.2. Mirroring

Минимум две permutation на seed:

- стороны меняют стартовые позиции;
- если карта несимметрична, меняются также route/topology advantages;
- AI identities и doctrine остаются привязаны к faction, не к slot;
- сравнивается среднее пары до агрегации по seeds.

## 6.3. Размер выборки

Начальные engineering gates:

- `3` seeds — smoke/discovery, не balance conclusion;
- `30` mirrored seeds — tuning candidate;
- `100+` mirrored seeds — release-candidate regression для materially stochastic scenario.

Это стартовые нормы, а не закон дизайна. При тяжёлых хвостах, редких death spirals или большой
дисперсии выборка увеличивается. Нельзя уменьшать её только потому, что среднее выглядит удобным.

## 6.4. AI competence

Сравнение недействительно, если AI:

- не умеет применять ключевую faction mechanic;
- получает hidden information;
- использует разные planner horizons без design intent;
- не умеет атаковать заявленный counterplay surface;
- намеренно строит off-doctrine fleet;
- упирается в известный pathing/command bug.

До balance tuning сначала исправляются или изолируются competence defects.

## 6.5. Human tests

Human session фиксирует:

- опыт игрока с жанром и фракцией;
- выбранные решения и доступную информацию;
- время до понимания faction loop;
- ошибочные causal explanations;
- моменты, где UI скрыл цену/контр-игру;
- perceived versus measured power.

Perception важна как UX evidence, но не заменяет simulation evidence.

---

# 7. Balance hypotheses по фракциям

Ниже — обязательные утверждения, которые implementation должна либо доказать, либо официально
пересмотреть до выпуска.

## Империя

Должна выигрывать value через подготовку, защищённую infrastructure, reserves, repair и preservation.
Должна платить capital intensity, mobilization latency и возрастающую цену удалённой проекции.

Обязательные contrast tests:

- B09 favorable: подготовленная оборона;
- B10/B13 unfavorable без развитой логистики: затяжная дальняя кампания;
- B14 strong при сохранённой repair network;
- B05 severe, если потерян именно редкий strategic node, но не любая случайная route.

## Индустриальный Союз

Должен выигрывать series production, replacement throughput, commonality и large-flow logistics.
Должен платить resource hunger, hub/route concentration и retool inertia.

Обязательные contrast tests:

- B13 favorable при сохранённых flows;
- B04/B05 strongly diagnostic;
- B17 обнаруживает цену смены production program;
- B14 показывает быстрый replacement, но не бесплатный repair.

## Директорат

Должен выигрывать через свежую информацию, precision, automation и well-prepared high-value action.
Должен платить specialist bottlenecks, sensitive components и плохую деградацию после потери сети.

Обязательные contrast tests: B11, B12, B17 и recovery после потери specialized facility.

## Лига Свободных Систем

Должна выигрывать через market response, distributed ownership, contracts и широкий доступ к
гражданским потокам. Должна платить coordination latency, fragmented readiness и price volatility.

Обязательные contrast tests: B08, B16, мобилизация без субсидий и мобилизация с явным контрактом.

## Пограничная Конфедерация

Должна выигрывать repair, salvage, substitution и distributed low-infrastructure survival. Должна
платить heterogeneous maintenance, низкий peak performance и трудную крупносерийную модернизацию.

Обязательные contrast tests: B04–B06, damaged-return, B10 и B17.

## Консорциум

Должен выигрывать capital allocation, concessions, debt и control of assets/flows. Должен платить
leverage risk, legitimacy burden и зависимость от enforceable contracts.

Обязательные contrast tests: B03, B15, B16 и cascading default/reputation shock.

## Кочевой Флот

Должен выигрывать мобильность economic node, rerouting и отказ от части territorial lock-in. Должен
платить уязвимость habitat/industry cores, access dependency и ограниченный тяжёлый replacement.

Обязательные contrast tests: B05, B10, B16, pursuit/interception и loss of mobile core.

---

# 8. Pairwise validation

Для пары фракций создаётся `PairwiseBalanceCard`:

```text
pair
contestedResourceOrObjective
advantageA + physicalCause
costA + visibleCounter
advantageB + physicalCause
costB + visibleCounter
requiredScenarios
prohibitedShortcut
evidenceLinks
openRisks
```

Для семи фракций существует 21 карта. Их canonical interaction contracts перечислены в
`faction_gameplay_visual_balance_bible.md`; здесь фиксируется процедура доказательства.

Карта проходит gate, если:

- обе стороны имеют хотя бы один разумный plan;
- counterplay доступен до необратимого поражения и может быть обнаружен;
- favorable context одной стороны можно изменить действиями другой;
- смена контекста требует времени/ресурсов, а не мгновенного stance toggle;
- результат сохраняет faction identity, не превращая обе стороны в одинаковый оптимальный build.

---

# 9. Acceptance gates

## Gate A — integrity

- все L0 tests зелёные;
- нет silent ID fallback;
- нет dangling content reference;
- fingerprints и save versions корректны;
- authoritative trace воспроизводится.

## Gate B — content viability

- каждый required role имеет хотя бы один законный production path;
- каждый fit физически/энергетически/термически допустим;
- supply, crew, repair и replacement paths существуют;
- intended role подтверждён сценарием;
- off-role failure не выглядит системным багом.

## Gate C — asymmetry

- у фракции есть минимум две доказанные strength conditions;
- есть минимум две доказанные vulnerability conditions;
- каждая strength имеет causal cost и counterplay;
- различие остаётся после equal-burden normalization;
- никакая фракция не доминирует весь scenario suite.

Количество conditions — минимальный smoke gate, не целевой объём дизайна.

## Gate D — recovery

- нет непреднамеренного безвыходного death spiral;
- заявленная resilience видна в recovery curve;
- irrecoverable loss соответствует описанию мира;
- replacement не materializes без ресурсов, work и времени.

## Gate E — visual/UX

Предлагаемый первый production gate для blind test:

- не менее 90% корректного различения core factions по grayscale ship silhouette;
- не менее 80% корректного role-family reading внутри core faction;
- не менее 90% character samples проходят shared-style checklist;
- не менее 80% testers после события называют правильную главную causal dependency.

Проценты являются стартовыми acceptance thresholds и могут быть ужесточены после baseline. Их нельзя
понижать без documented review evidence.

## Gate F — release regression

- обязательные scenario batches пройдены на exact RC SHA;
- нет unresolved balance blocker;
- report содержит raw evidence links, а не только summary;
- migration покрывает поддерживаемые saves;
- visual assets и content manifests имеют версии/fingerprints;
- documentation совпадает с фактическим package.

---

# 10. Порядок исправления дисбаланса

Исправления выполняются строго сверху вниз:

1. authority/persistence/determinism defect;
2. физическая невозможность или content data error;
3. AI competence/knowledge leak;
4. missing logistics/support/repair path;
5. scenario bias или неправильная normalization;
6. content composition и production dependency;
7. doctrine/decision policy;
8. локальный физически объяснимый parameter;
9. только затем — изменение systemic identity contract.

Запрещено начинать с глобального `+X%`/`-X%`, если проблема находится выше в списке.

## 10.1. Допустимые tuning levers

- material composition;
- component mass/volume/power/thermal characteristics;
- manufacturing steps/work/facility class;
- module commonality;
- inventory, route и replenishment requirements;
- crew/training/readiness;
- repairability и replacement assembly granularity;
- doctrine preference weights на существующем decision layer;
- contract/subsidy/reserve/mobilization decisions через общие systems;
- authored fleet composition.

## 10.2. Недопустимые shortcuts

- hidden faction damage/armor/income scalar;
- teleporting supply/replacement;
- AI-only knowledge;
- special-case if/else, повторяющий common authority;
- визуально отсутствующий capability;
- бесплатный retool/repair;
- auto-win treaty/market/event;
- hard counter без раннего signal и response window.

---

# 11. Telemetry и отчёт

## 11.1. Минимальные event groups

- procurement/order/start/complete/cancel;
- material reserve/consume/stockout/substitute;
- facility queue/changeover/failure/repair;
- route planned/blocked/rerouted/delivered;
- fleet readiness/mission/engagement/disengagement;
- component damage/repair/replace/salvage;
- knowledge observed/updated/expired/shared;
- treaty/contract/access/legitimacy change;
- territory control/occupation/security change;
- doctrine decision candidate/selected/rejected reason;
- save/load/migration/fingerprint result.

Telemetry наблюдает authority, но не управляет ей.

## 11.2. Balance report template

Каждый milestone report содержит:

1. exact SHA и content fingerprint;
2. package/scenario versions;
3. проверяемые hypotheses;
4. protocol, seeds и permutations;
5. raw result vectors;
6. distribution/curve visualizations;
7. causal event traces для representative/outlier runs;
8. known defects и excluded runs с причинами;
9. вывод по каждому gate;
10. tuning change log;
11. regression risks;
12. owner и следующий decision date.

## 11.3. PR evidence block

```markdown
### Balance evidence
- Build SHA:
- Content fingerprint:
- Scenarios:
- Seeds / mirrored runs:
- Hypotheses:
- Result vectors:
- Replay/save verification:
- Visual acceptance:
- Open risks:
- Gate decision:
```

---

# 12. CI и manual lanes

## На каждый content PR

- compile/unit tests;
- catalog/schema validation;
- deterministic fixture;
- save/load compatibility;
- fingerprint check;
- targeted scenario smoke;
- generated manifest diff;
- docs/link validation.

## Nightly или scheduled

- 30-seed mirrored batches;
- long-war and recovery suite;
- pairwise regression subset;
- outlier/death-spiral detection;
- visual manifest consistency.

## Release candidate

- 100+ seed materially stochastic batches;
- полный core pair suite;
- все supported migration paths;
- human causal-comprehension session;
- grayscale and character-style blind tests;
- archived evidence report tied to RC SHA.

---

# 13. Definition of done

Faction balance work не завершено, пока:

- package не существует в common systems;
- физическая и экономическая цена не наблюдаема;
- counterplay не воспроизводится;
- AI не умеет обе стороны matchup;
- recovery не измерено;
- visuals не подтверждают gameplay;
- report нельзя связать с exact SHA/fingerprint;
- save/load меняет результат;
- документация говорит о механике, которой нет;
- open risk назван «полировкой», хотя меняет исход.

Финальная формула приёмки:

```text
integrity
+ physical viability
+ strategic diversity
+ visible cost
+ actionable counterplay
+ measured recovery
+ player comprehension
+ reproducible evidence
= balanced faction package
```
