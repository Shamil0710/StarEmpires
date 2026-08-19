# Star Empires — Initial Asset / Hull Manifest v1

> **Статус:** CANDIDATE BASELINE v1.0 — требуется явное принятие после ревью  
> **Дата:** 2026-08-19  
> **Назначение:** зафиксировать минимально полный первоначальный набор корабельных hull families, станционных structural families, внешних модульных asset families и космических world-object families, который следует из уже принятой архитектуры Star Empires.  
> **Важно:** этот manifest не является финальным балансом Stage 22 и не превращает doctrine class / station archetype в скрытый gameplay bonus.

---

# 1. Основание и границы документа

Manifest собирает уже существующие решения из:

- `docs/development_roadmap.md`;
- `docs/ship_hull_module_and_fleet_doctrine.md`;
- `docs/ship_mathematics_v1_0_design_baseline.md`;
- `docs/stage17_5a_production_ship_content_schema.md`;
- `src/main/resources/data/content/ship-engineering-v1.json`;
- `docs/stage18_resources_industry_infrastructure_plan.md`;
- `docs/stage20_physical_world_generation_plan.md`;
- `src/main/resources/data/content/catalog-v1.json`.

Он также учитывает уже разработанные в проекте визуальные candidate concepts, но **не повышает их автоматически до production definitions**.

Manifest отвечает на вопрос:

> **Какой минимальный набор базовых визуально/физически различимых asset families должен существовать, чтобы уже спроектированные корабельные, экономические, промышленные и world-generation системы можно было представить без создания отдельного уникального корпуса для каждой специализации?**

Manifest **не**:

- фиксирует окончательные размеры всех кораблей;
- фиксирует окончательный баланс mass / thrust / power / heat / armor / weapons;
- назначает финальные faction-specific Ship Design IDs;
- требует отдельного уникального hull для каждого cargo type, recipe или specialization;
- заменяет `HullDefinition`, `ModuleDefinition`, station/facility content schema или Stage-20 generator;
- вводит новый authoritative stat, resource или hidden class bonus;
- определяет финальный графический pipeline — runtime Star Empires остаётся 2D top-down, а способ изготовления исходника может меняться независимо.

---

# 2. Термины manifest

## 2.1. Asset family

Группа визуально и конструктивно совместимых базовых объектов, которые могут иметь:

- faction-specific designs;
- технологические поколения;
- варианты / refit;
- разные установленные модули;
- damage states;
- разные instance names/history.

Одна `Asset family` **не равна** одному конкретному `Ship Design`.

## 2.2. Manifest ID

Идентификаторы вида `IAHM-SHIP-xx`, `IAHM-STATION-xx`, `IAHM-WORLD-xx` существуют только внутри этого документа для трассировки производства ассетов.

Они **не являются production stable content IDs** и не должны автоматически копироваться в JSON schemas.

## 2.3. Статусы происхождения

- **PRODUCTION REFERENCE** — уже существует production-valid definition/evidence, но он может быть provisional content.
- **LEGACY MAPPING** — существует старый archetype, который должен быть покрыт новой asset family без обязательного сохранения отдельного уникального hull.
- **CONCEPT CANDIDATE** — в проекте уже разработан визуальный/дизайнерский concept, но он ещё не production definition.
- **REQUIRED BASELINE** — family следует из принятой doctrine / Stage-18 / Stage-20 архитектуры и нужна для полного initial coverage.
- **DEFERRED** — валидная family, но не блокирует текущий Stage 20.

## 2.4. Приоритеты производства

- **P0 — current baseline:** нужен для представления уже действующих систем и/или Stage-20 representative world/ship coverage.
- **P1 — Alpha breadth:** нужен к RPG/Living World и Content/Balance Alpha, но не обязан блокировать ранний Stage 20.
- **P2 — expansion:** doctrine/content breadth, которую разумно делать после базового покрытия.

---

# 3. Главные правила asset architecture

## 3.1. Hull не равен specialization

Сохраняется уже принятая иерархия:

```text
Hull Size
→ Hull Architecture
→ Doctrine Class
→ Specialization
→ Ship Design
→ Variant / Refit
→ Ship Instance
```

Следовательно:

```text
Frigate hull family
→ escort fit
→ reconnaissance fit
→ EW fit
→ patrol fit
```

может использовать общую конструктивную основу, если physical fitting действительно совместим.

Запрещено автоматически создавать четыре независимых корпуса только потому, что существуют четыре specialization label.

## 3.2. Видимые интерактивные системы по возможности не запекаются в base hull

Base hull должен визуально поддерживать заменяемые внешние элементы там, где gameplay model допускает замену/повреждение/модернизацию:

- weapon mounts;
- missile/VLS blocks;
- PD/interceptor mounts;
- sensor/EW arrays;
- datalink/communication arrays;
- radiators;
- external cargo/tank modules;
- hangar/launch interfaces;
- mining/salvage/repair equipment;
- main-drive/RCS assemblies where architecture allows replacement.

Это продолжает Stage-17.5 principle: роль возникает из физического fit, а не из имени класса.

## 3.3. Station archetype не равен уникальному station hull

Stage 18 уже фиксирует composable station capabilities. Поэтому:

```text
station structural family
+ installed facility modules
+ storage / docking / power / radiator / sensor / defense infrastructure
→ конкретный station archetype/design
```

`refinery complex` и `industrial station` не обязаны быть двумя полностью независимыми архитектурами, если различие можно честно выразить установленными facilities.

## 3.4. World object не равен loot container

ResourceOccurrence принадлежит physical host object/region. Внешний вид может давать правдоподобные геологические намёки, но не должен автоматически раскрывать undiscovered exact reserves/grade/composition.

## 3.5. Wreck должен происходить из реального constructed asset

Базовая политика:

```text
ship/station
→ damage/destruction
→ persistent wreck/debris state
→ bounded salvage/recycling
```

Поэтому предпочтительны reusable damage/wreck transformations существующих hull/station assets, а не отдельный набор произвольных «сундуков-обломков» без происхождения.

---

# 4. Initial ship hull manifest

## 4.1. Военные doctrine hull families

| Manifest ID | Hull family | Doctrine / роль | Основание | Priority | Initial decision |
| --- | --- | --- | --- | --- | --- |
| `IAHM-SHIP-01` | **Corvette Hull** | screen, interception, torpedo pressure | canonical doctrine; Stage-20 `patrol/corvette` reference; heavy-corvette concept | **P0** | Основной малый боевой reference hull текущего baseline. |
| `IAHM-SHIP-02` | **Frigate Hull** | escort, recon, EW, long patrol | canonical doctrine; legacy `ship.guard_frigate`; «Аргус» concept | **P0** | Один базовый frigate hull должен поддерживать минимум escort и recon/EW fit без обязательного нового корпуса. |
| `IAHM-SHIP-03` | **Destroyer Hull** | specialized fleet combatant, escort/PD/strike | canonical doctrine; production `hull.escort_destroyer_v1`; «Бастион» concept | **P0** | Единственная family с текущим полноценным production engineering hull reference. |
| `IAHM-SHIP-04` | **Cruiser Hull** | independent long-range force projection | canonical doctrine; Stage-20 representative cruiser | **P0** | Нужен как первый genuinely self-contained heavy operational hull. |
| `IAHM-SHIP-05` | **Battleship / Capital Combatant Hull** | heavy line / breakthrough / capital reference | canonical doctrine; Stage-20 capital-combatant profile | **P0** | Initial capital family. Не фиксирует конкретное вооружение или faction design. |
| `IAHM-SHIP-06` | **Patrol Craft Hull** | policing, customs, low-cost presence | canonical doctrine | **P1** | Сохраняется отдельно от corvette, но Stage 20 может использовать corvette как initial `patrol/corvette` calibration profile. |
| `IAHM-SHIP-07` | **Battlecruiser Hull** | fast heavy raider / hunter | canonical doctrine | **P2** | Валидный doctrine class, но не нужен для минимального current world-generation coverage. |
| `IAHM-SHIP-08` | **Carrier Hull** | hangar / drones / small-craft operations | Carrier family doctrine; `HANGAR_SMALL_CRAFT`; Stage-20 carrier group where relevant; «Пастырь» / light-carrier concepts | **P1** | Делать как отдельную family из-за радикально иной internal/external volume geometry. |

### Production reference для destroyer

На момент фиксации manifest существует:

```text
hull.escort_destroyer_v1
length = 220 m
width = 72 m
height = 38 m
```

Это **PRODUCTION REFERENCE**, но не финальный content balance/design. Он остаётся Stage-17.5 engineering demonstrator до отдельного Stage-22 promotion/re-author decision.

## 4.2. Civilian / Auxiliary hull families

| Manifest ID | Hull family | Primary coverage | Основание | Priority | Initial decision |
| --- | --- | --- | --- | --- | --- |
| `IAHM-SHIP-09` | **General Civilian Freighter / Container Hull** | finished goods, mixed packaged cargo, early civilian freighter | legacy `food_container`, `weapons_container`; Stage-20 early civilian freighter | **P0** | Cargo commodity не создаёт отдельный hull. Containers/modules меняются поверх общей family. |
| `IAHM-SHIP-10` | **Bulk / Material Freighter Hull** | ore, structural materials, high-mass bulk cargo | legacy `ore_hauler`, `steel_hauler`; Stage-20 loaded bulk freighter | **P0** | Отдельная family оправдана иной mass/volume/cargo-handling geometry. |
| `IAHM-SHIP-11` | **Tanker / Reaction-Mass Hull** | liquids/gases, reaction mass, fleet refuel | legacy `energy_tanker`; Stage-20 fleet tanker/logistics support; Stage-17.5 physical reaction mass | **P0** | Tank geometry и transfer interfaces должны читаться отдельно от dry cargo hull. |
| `IAHM-SHIP-12` | **Mining Hull** | asteroid/free-body extraction and resource handling | legacy `basic_miner`; Stage-18 extraction; Stage-20 mining ship | **P0** | Базовый miner должен иметь визуально читаемые anchoring/extraction/capture interfaces. |
| `IAHM-SHIP-13` | **Fleet Logistics / Replenishment Hull** | ammunition, stores, spare parts, multi-cargo support, fleet endurance | Stage-20 representative logistics support; Stage-18 warfare supply | **P0** | Не обязан совпадать с tanker: mixed fleet stores и replenishment создают иную mission geometry. |
| `IAHM-SHIP-14` | **Repair / Salvage / Industrial Support Hull** | repair, salvage, recovery, mobile industrial support | canonical specializations; Stage-18 bounded salvage/recycling | **P1** | Может иметь общий auxiliary backbone с mission-specific external work modules. |
| `IAHM-SHIP-15` | **Small Craft / Drone Hull Family** | carrier-launched drones, boats, auxiliary small craft | `HANGAR_SMALL_CRAFT`; carrier concepts | **P1** | Это subordinate family, а не полноценная doctrine-class replacement. Нужна для carrier gameplay/readability. |

---

# 5. Mapping уже существующего ship content

## 5.1. Legacy production archetypes → manifest families

| Existing archetype | Manifest mapping | Решение |
| --- | --- | --- |
| `ship.ore_hauler` | `IAHM-SHIP-10` | Не сохранять отдельный уникальный ore hull только из-за commodity. |
| `ship.steel_hauler` | `IAHM-SHIP-10` | Общий bulk/material hull с другим cargo state/fit. |
| `ship.energy_tanker` | `IAHM-SHIP-11` | Tanker family. |
| `ship.food_container` | `IAHM-SHIP-09` | General container/freighter family. |
| `ship.weapons_container` | `IAHM-SHIP-09` | General container/freighter family; security/handling modules могут отличаться. |
| `ship.basic_miner` | `IAHM-SHIP-12` | Mining family. |
| `ship.guard_frigate` | `IAHM-SHIP-02` | Frigate family; combat role определяется installed fit. |

Legacy mapping сохраняет gameplay coverage, но **не требует один-к-одному переносить старые names/visual hulls**.

## 5.2. Current project candidate concepts → manifest families

Эти названия фиксируются только как **CONCEPT CANDIDATE mapping**:

| Candidate concept | Manifest family | Specialization |
| --- | --- | --- |
| Heavy Corvette | `IAHM-SHIP-01` | heavy/screen combat corvette |
| «Аргус» | `IAHM-SHIP-02` | long-range reconnaissance / EW frigate |
| «Бастион» | `IAHM-SHIP-03` | escort / missile / PD destroyer |
| «Пастырь» | `IAHM-SHIP-08` | light drone carrier |
| Light Carrier | `IAHM-SHIP-08` | light carrier variant |
| Cargo Ship concepts | `IAHM-SHIP-09` / `IAHM-SHIP-10` | container or bulk depending physical cargo architecture |

Ни один concept из этой таблицы не становится автоматически universal hull для всех factions.

---

# 6. Standard Hull Asset Package

Для каждого утверждённого Ship Design, производимого на базе manifest family, asset pipeline должен уметь предоставить единый минимальный пакет.

## 6.1. Обязательный base package

1. **Base hull silhouette / body** — без baked-in сменных mission modules, если их предполагается заменять.
2. **Physical scale metadata** — реальные bounding dimensions в метрах, согласованные с production `HullDefinition`.
3. **Orientation / pivot contract** — единый forward axis, center/pivot и top-down orientation.
4. **Hardpoint / external slot map** — координаты и допустимые visual envelopes внешних модулей.
5. **Engine / RCS attachment map** — точки main-drive plume и maneuver-thruster VFX.
6. **Collision / selection footprint** — presentation geometry не должна подменять authoritative physics, но должна быть согласована с ней.
7. **Base material/color layer** — без emissive/VFX baking.
8. **Emissive mask/layer** — только реальные светящиеся элементы.
9. **Damage layer/state support** — scorch, armor loss, exposed structure, subsystem-local damage where readable.
10. **Wreck derivation support** — разрушенный state/fragment masks или правила procedural decomposition.
11. **Zoom/readability representation** — asset должен сохранять читаемость в реальном gameplay scale.

## 6.2. Запрещено

- менять число видимых weapon/sensor/hangar modules без соответствующего installed fit;
- рисовать active exhaust в base hull;
- запекать missile launch, shield flare, beam, mining laser или damage fire в неизменяемую основу;
- использовать декоративные элементы как ложные interactive modules;
- создавать visual hardpoint, которого физически нет в design definition;
- скрывать физически крупный установленный внешний module без explicit design reason.

---

# 7. External ship module visual manifest

Stage-17.5 v1.0 содержит 15 module families. Не каждая требует отдельного внешнего объекта, но initial asset library должна визуально поддерживать следующие категории.

## 7.1. Engineering / mobility

- **Main Drive assemblies** — P0.
- **Maneuver / RCS thrusters** — P0.
- **Thermal-control / radiator structures** — P0.
- **FTL/jump external hardware**, только если конкретная architecture делает его внешне читаемым — P1.

`REACTOR_POWER` и `ENERGY_STORAGE` по умолчанию считаются внутренними и не требуют ложной внешней «реакторной башни».

## 7.2. Information / command

- **Sensor / EW / fire-control arrays** — P0.
- **Communication / datalink arrays** — P0.

Разные capability могут использовать общий mounting language, но визуально значимые apertures/antennae должны соответствовать установленному equipment.

## 7.3. Defense / protection

- **Shield emitter housings / field projectors**, где design делает их внешними — P0.
- **Replaceable armor/protection sections / damage overlays** — P0.

## 7.4. Weapons

Initial visual weapon families:

- **Kinetic direct-fire mount** — P0;
- **Beam/energy mount** — P0;
- **Guided missile / VLS / launcher block** — P0;
- **PD / interceptor mount** — P0;
- **Magazine/service interface indication**, только где она внешне значима — P1.

Конкретные barrel count / launcher cell count должны соответствовать content definition, а не декоративному рисунку.

## 7.5. Mission / logistics

- **Dry cargo/container module** — P0;
- **Tank/liquid/gas module** — P0;
- **Hangar / launch / recovery interface** — P1;
- **Mining / excavation / anchoring equipment** — P0;
- **Salvage / repair / recovery equipment** — P1;
- **External stores / mission pod framework** — P1.

`CREW_LIFE_SUPPORT_AUTOMATION` по умолчанию внутренний family; его наличие не требует декоративных «жилых башен».

---

# 8. Initial station structural manifest

Stage 18 определяет 13 station archetypes. Initial asset production **не должно требовать 13 полностью уникальных station hulls**.

## 8.1. Structural families

| Manifest ID | Structural family | Stage-18 archetype coverage | Priority | Design rule |
| --- | --- | --- | --- | --- |
| `IAHM-STATION-01` | **Light Outpost / Extraction Platform** | mining outpost; research/survey station where small | **P0** | Compact service core + docking + utility spine; extraction/survey equipment устанавливается модульно. |
| `IAHM-STATION-02` | **Depot / Logistics Hub** | volatile/water depot; fuel/reaction-mass depot; trade/logistics hub | **P0** | Storage/tank/cargo geometry является основным визуальным отличием. |
| `IAHM-STATION-03` | **Industrial / Manufacturing Station** | refinery complex; industrial station; high-tech manufacturing hub | **P0** | Один structural backbone должен принимать разные processing/fabrication modules. |
| `IAHM-STATION-04` | **Habitat / Agricultural Station** | habitat/agricultural station | **P0** | Отличается crew/habitation/life-support volume, а не декоративным куполом без функции. |
| `IAHM-STATION-05` | **Repair / Shipyard / Integration Yard** | repair yard; shipyard | **P0** | Berths, service frames, construction/integration envelope — главная geometry. |
| `IAHM-STATION-06` | **Naval / Security Base** | naval base | **P0** | Защищённая command/storage/repair platform с физическими sensor/weapon/traffic envelopes. |
| `IAHM-STATION-07` | **Frontier Multipurpose Hub** | frontier multipurpose station | **P0** | Модульный mixed-use hub для слабой инфраструктуры и дальнейшего наращивания. |

Все 13 Stage-18 archetypes покрываются этими 7 structural families через installed facilities.

## 8.2. Legacy station mapping

| Legacy archetype | Manifest mapping | Решение |
| --- | --- | --- |
| `station.mining_base` | `IAHM-STATION-01` | Mining/extraction fit. |
| `station.power_plant` | facility module on `02/03/07` | Power generation не требует отдельной universal station geometry. |
| `station.agrodome` | `IAHM-STATION-04` | Habitat/agriculture family. |
| `station.foundry` | `IAHM-STATION-03` | Industrial family + refinery/fabrication facilities. |
| `station.arsenal` | `IAHM-STATION-03` or `06` | Ordnance/manufacturing fit; military ownership alone не создаёт уникальный hull. |
| `station.colony` | `IAHM-STATION-07` initially | Frontier/multipurpose hub; later colony content may grow beyond one station. |

---

# 9. Station facility module manifest

Ниже сохраняются все Stage-18 baseline facility families. Они являются gameplay capabilities и должны иметь визуальный язык/footprint там, где physical scale делает их внешне различимыми.

## 9.1. Production / extraction facility families

1. **Extraction Facility** — P0;
2. **Volatile Processor** — P0;
3. **Bulk Refinery / Smelter** — P0;
4. **Advanced Materials Plant** — P0;
5. **Chemical Plant** — P0;
6. **Heavy Fabrication Plant** — P0;
7. **Electrical Works** — P0;
8. **Precision / Electronics Fab** — P0;
9. **Ordnance Plant** — P0;
10. **Agricultural / Life-Support Complex** — P0;
11. **Recycling / Salvage Processor** — P0;
12. **Assembly Plant** — P0;
13. **Shipyard / Integration Yard** — P0;
14. **Station Construction / Integration Facility** — P0;
15. **Power Plant** — P0;
16. **Storage / Depot / Logistics Facility** — P0.

## 9.2. Cross-cutting station infrastructure assets

Дополнительно Stage-20 station geometry требует reusable infrastructure families:

- **Docking / berth structures** — P0;
- **Shipyard integration/service frames** — P0;
- **Radiator / thermal infrastructure** — P0;
- **Sensor / communication arrays** — P0;
- **Weapon / defensive mounts** — P0;
- **Traffic/approach markings and clearance presentation** — P1 presentation asset, не physical bonus.

Facility module не обязан иметь огромную уникальную внешнюю секцию, если процесс физически находится внутри station structure. Но его required footprint, storage, power, cooling и service access не должны противоречить station geometry.

---

# 10. Initial world-object manifest

Stage 20 отвечает на вопрос «где это существует?», но asset production должен заранее уметь представить уже определённую Stage-18 world-object taxonomy.

| Manifest ID | World asset family | Stage-18/20 meaning | Priority | Representation rule |
| --- | --- | --- | --- | --- |
| `IAHM-WORLD-01` | **Star / Central Body** | stellar/central-body reference | **P0** | Prefer parametric size/color/emission over unique asset per star. |
| `IAHM-WORLD-02` | **Rocky Planet** | rocky/differentiated large body | **P0** | Large celestial anchor; resource data remains authoritative separately. |
| `IAHM-WORLD-03` | **Rocky Moon / Large Airless Body** | surface/deep extraction host | **P0** | Distinct scale/terrain language from small asteroids. |
| `IAHM-WORLD-04` | **Gas / Ice Giant** | atmospheric volatile-harvesting host | **P0** | Atmospheric source does not imply free/infinite accessible cargo. |
| `IAHM-WORLD-05` | **Metallic Asteroid** | metallic/core-fragment host | **P0** | May visually suggest metal-rich geology without revealing exact hidden reserve/grade. |
| `IAHM-WORLD-06` | **Stony / Silicate Asteroid** | silicate/light-metal/mixed host | **P0** | Common resource-body family. |
| `IAHM-WORLD-07` | **Carbonaceous Asteroid** | water/volatile/carbonaceous host | **P0** | Distinct dark/primitive material language; no loot-color coding. |
| `IAHM-WORLD-08` | **Icy Body / Comet** | water/volatile host | **P0** | Supports free-body extraction and remote-resource geography. |
| `IAHM-WORLD-09` | **Asteroid / Resource Field** | region containing multiple resource bodies/occurrences | **P0** | Composition from member objects/occurrences, not one giant decorative sprite. |
| `IAHM-WORLD-10` | **Ring / Icy Ring Material Field** | ring-material resource region where generated | **P1** | Field/region representation; not necessarily individual simulated particle per rock. |
| `IAHM-WORLD-11` | **Wreck / Debris Field** | salvage output from destroyed manufactured assets | **P0** | Prefer derived fragments/damage states from real ship/station assets. |
| `IAHM-WORLD-12` | **Abandoned Infrastructure / Derelict** | abandoned manufactured location | **P1** | Reuse station/ship structural language plus age/damage state. |
| `IAHM-WORLD-13` | **Anomaly Location** | Stage-20 discovery location | **P1** | Mechanics/content intentionally not over-authored before Stage 21; generic discovery presentation is sufficient initially. |
| `IAHM-WORLD-14` | **Jump Arrival / Departure Zone** | explicit inter-system transition geometry | **P0** | Spatial/presentation asset; ordinary jump remains explicit graph transition, not a magic map-edge portal. |
| `IAHM-WORLD-15` | **Patrol / Security Zone Presentation** | generated operational/security region | **P1** | UI/map overlay, **not** a physical object/hull. |
| `IAHM-WORLD-16` | **Empty Transit Volume** | valid unbounded local space outside meaningful-content clusters | **P0 semantic** | Intentionally has no asset. Empty space must remain valid world state. |

---

# 11. Asteroid and natural-body variation policy

Stage-18 taxonomy различает host types, а не конкретные художественные меши/спрайты.

Поэтому для production variety рекомендуется:

```text
4 baseline small-body host families
× minimum 4 silhouette/shape variants each
= 16 initial asteroid/comet shape variants
```

Это **production recommendation**, а не authoritative gameplay constant.

Допустимы procedural/parametric variations:

- scale;
- rotation;
- albedo/material variation inside host-family bounds;
- crater/fracture distribution;
- local surface roughness;
- minor shape deformation.

Недопустимо:

- кодировать exact reserve amount цветом;
- делать каждый `STRATEGIC_METAL_ORE` asteroid ярко-золотым до discovery;
- привязывать gameplay resource family к одному-единственному silhouette;
- генерировать resource body только как visual decoration без real occurrence/state.

---

# 12. Damage, wreck and salvage asset policy

## 12.1. Ships

Каждая P0/P1 ship family должна поддерживать минимум:

```text
intact
→ damaged
→ critically damaged
→ wreck / recoverable debris
```

Это не обязательно четыре независимых hand-authored изображения. Допустимы layered/procedural combinations:

- armor panel loss;
- scorch/burn masks;
- exposed internals;
- disabled external modules;
- detached module fragments;
- localized engine/reactor/weapon damage cues;
- final wreck decomposition.

## 12.2. Stations

Station wreck должен сохранять читаемую связь с исходной structural family и installed facilities.

Salvage visuals не имеют права создавать gameplay material value самостоятельно: authoritative recovered yield идёт из Stage-18 salvage/recycling state.

---

# 13. Faction-specific asset policy

Manifest определяет **functional families**, а не одну универсальную внешность галактики.

Пример:

```text
IAHM-SHIP-03 Destroyer Hull family
→ Imperial Destroyer Design A
→ Trade-League Destroyer Design A
→ frontier/refit design
```

могут иметь совершенно разные:

- silhouettes;
- armor layouts;
- radiator treatment;
- service-access language;
- visual materials;
- module housings;

при условии, что physical definition и installed fit остаются честными.

Уже созданный visual code «Империи» должен использоваться для первой faction-specific asset line, но **не превращает Imperial silhouette в universal hull model для остальных factions**.

Stage 22 остаётся местом, где provisional reference definitions получают final content re-author/promotion и faction breadth.

---

# 14. Production waves

## Wave A — Stage 20 / current physical-world baseline

Сначала необходимо покрыть:

### Ships

- `IAHM-SHIP-01` Corvette;
- `IAHM-SHIP-02` Frigate;
- `IAHM-SHIP-03` Destroyer;
- `IAHM-SHIP-04` Cruiser;
- `IAHM-SHIP-05` Capital/Battleship reference;
- `IAHM-SHIP-09` General civilian freighter;
- `IAHM-SHIP-10` Bulk freighter;
- `IAHM-SHIP-11` Tanker;
- `IAHM-SHIP-12` Miner;
- `IAHM-SHIP-13` Fleet logistics/support.

Это покрывает current economic roles, existing concepts и representative-ship needs Stage 20 без обязательного production-ready Battlecruiser/Carrier.

### Stations

Все семь `IAHM-STATION-01..07` structural families должны иметь хотя бы schematic/reference representation, потому что Stage 20 калибрует station footprint, spacing, docking, defensive and traffic geometry.

### World

P0 natural/world families:

- star/central body;
- rocky planet/moon;
- gas/ice giant;
- four resource-small-body families;
- asteroid/resource fields;
- wreck/debris;
- jump zones;
- intentionally empty transit space.

## Wave B — Stage 21 / RPG & Living World breadth

Добавить/довести:

- `IAHM-SHIP-06` Patrol Craft;
- `IAHM-SHIP-08` Carrier;
- `IAHM-SHIP-14` Repair/Salvage;
- `IAHM-SHIP-15` Small Craft / Drones;
- research/survey visual specialization;
- derelict/abandoned infrastructure;
- discovery/anomaly presentation;
- richer civilian/frontier station variants.

## Wave C — Stage 22 / Content & Balance Alpha

Добавить/пересмотреть:

- `IAHM-SHIP-07` Battlecruiser;
- final doctrine breadth;
- faction-specific ship/station designs;
- final module visual sets;
- final promoted/re-authored engineering hull definitions;
- technology-generation variants;
- balance-driven size/slot/hardpoint revisions;
- additional natural-body diversity only where it improves readable geography/content.

---

# 15. Summary inventory v1

## Ship hull families

```text
15 total functional families
10 × P0
4 × P1
1 × P2
```

P0 deliberately covers the current Stage-20 representative fleet plus already existing combat/economic content.

## Station structural families

```text
7 base structural families
covering all 13 Stage-18 station archetypes
```

Archetype diversity primarily comes from facility composition rather than one bespoke structural hull per station name.

## Station facility families

```text
16 Stage-18 production/extraction facility families
+ reusable docking / thermal / sensor / defense infrastructure
```

## World-object manifest

```text
16 semantic entries
including physical bodies, fields, manufactured ruins,
transition/security presentation and intentionally empty space
```

---

# 16. Acceptance criteria for Manifest v1

Manifest может быть переведён из `CANDIDATE BASELINE` в `ACCEPTED BASELINE`, если подтверждено одновременно:

- [ ] все canonical military doctrine classes представлены или явно deferred;
- [ ] current legacy ship archetypes имеют mapping без обязательного SKU→unique-hull explosion;
- [ ] Stage-20 representative ship profiles имеют asset-family coverage;
- [ ] `hull.escort_destroyer_v1` корректно обозначен как provisional production reference, а не final content;
- [ ] все 13 Stage-18 station archetypes покрываются composable structural families;
- [ ] все 16 Stage-18 baseline facility families сохранены как capability/asset requirements;
- [ ] Stage-18 natural host-object taxonomy покрыта world-object families;
- [ ] Stage-20 jump/resource/station/discovery geometry имеет presentation coverage;
- [ ] empty local space не превращено в map wall;
- [ ] ship/station wreck policy связан с real destruction and bounded salvage;
- [ ] asset family не создаёт hidden gameplay stat или class bonus;
- [ ] faction-specific visual design отделён от functional hull family;
- [ ] visible interactive modules могут соответствовать installed fit вместо baked decorative approximation;
- [ ] physical scale metadata остаётся привязано к SI production definitions;
- [ ] Stage 22 сохраняет право re-author/rebalance provisional combat content.

---

# 17. Каноническая краткая формула

```text
functional hull / station / world-object family
+ authoritative physical definition
+ installed modules / facilities
+ faction visual language
+ damage / operational state
→ конкретный игровой asset/design/instance
```

Главный production principle:

> **Мы создаём минимальное число базовых корпусов, достаточное для реального физического разнообразия, а не отдельный корпус для каждого названия роли. Вариативность должна возникать из architecture, installed modules, facilities, faction design language, refits and persistent state.**
