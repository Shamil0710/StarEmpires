# Star Empires — Stage 18 Resources / Industry / Infrastructure Foundation

> Статус: **PLANNED / BLOCKED by Stage 17.5**  
> Синхронизация: **2026-08-16**  
> Назначение: до физической генерации галактики определить минимально полный, физически и экономически правдоподобный язык ресурсов, добычи, переработки, производства, станций, верфей и мировых объектов.

---

# 1. Главный принцип

Stage 18 отвечает на вопрос **«что вообще может физически и экономически существовать в мире Star Empires?»** до того, как world generator начнёт отвечать на вопрос **«где именно это существует?»**.

Каноническая зависимость:

```text
Ship Mathematics / fitting needs
+ resource ontology
+ extraction methods
+ industrial processes
+ facility capabilities
+ station / shipyard capabilities
+ logistics/storage constraints
→ physically meaningful economic ecosystem
→ physically calibrated world generation
```

Stage 18 не является энциклопедией химической промышленности. Он сохраняет столько промежуточных стадий, сколько нужно для meaningful logistics, geography, bottlenecks, warfare and player decisions.

Главное правило упрощения:

> **Отдельный товар, процесс или facility существует в authoritative simulation только если его отделение создаёт новый источник, технологическое требование, логистическое ограничение, стратегический bottleneck, meaningful substitution/recycling choice или заметное gameplay consequence.**

Если две реальные стадии отличаются только промышленной детализацией, но не создают нового игрового решения, они объединяются.

---

# 2. Реальная основа и допустимое упрощение

Baseline опирается на реальные общие закономерности добычи и промышленности, но адаптирует их к развитой космической экономике.

Использованные ориентиры:

- NASA ISRU: вода, кислород, летучие вещества, металлы и местные строительные материалы как ключевые space resources; вода может поддерживать life support и propellant production.
- NASA/JPL asteroid-resource material: carbonaceous bodies как потенциальный источник воды/углеродистых веществ, metallic bodies — металлов.
- ESA Space Resources: regolith как потенциальное сырьё для кислорода, кремния, железа и алюминия; polar/icy deposits — для воды, кислорода и водорода.
- USGS mineral-industry material flows: реальная металлургия обычно разделяет extraction, concentration/beneficiation, refining/smelting и high-purity/fabrication stages.
- USGS examples: copper commonly follows mining → concentration → smelting → electrolytic refining; terrestrial aluminium commonly follows bauxite → alumina → electrolytic aluminium. Star Empires сохраняет логику staged processing, но не предполагает, что extraterrestrial aluminium обязан происходить именно из terrestrial bauxite.

## 2.1 Что намеренно НЕ моделируется по умолчанию

Baseline не создаёт отдельный commodity для:

- каждого химического реагента;
- каждой марки стали;
- каждого отдельного alloying element;
- каждой минералогической разновидности руды;
- каждого industrial gas;
- каждого semiconductor dopant;
- каждого lubricant/solvent;
- промежуточного продукта, который всегда производится и потребляется внутри одного facility без внешней logistics consequence.

Эти детали могут появиться позднее только через explicit architecture/content review.

---

# 3. Уровни материальной экономики

Каноническая цепочка:

```text
RESOURCE OCCURRENCE
→ extracted feedstock
→ refining / separation / purification
→ engineering material / industrial consumable
→ industrial component
→ module / ammunition / machinery
→ ship / station / infrastructure
→ operation / wear / damage
→ salvage / recycling
```

Не каждая цепочка обязана проходить через каждый уровень.

Например water-rich ice может стать usable water после относительно короткой processing chain, тогда как sensor electronics требуют несколько ступеней high-purity manufacturing.

---

# 4. Resource occurrence не равен cargo commodity

World object хранит **resource occurrence** — физическое месторождение/состав, а inventory хранит уже извлечённые товары.

Минимальная occurrence model:

```text
ResourceOccurrence
- stable occurrence ID
- host object / region ID
- composition/resource families
- accessible reserve
- concentration / grade
- extraction difficulty
- extraction environment
- required capability tags
- hazard metadata
- discovered/estimated knowledge state
```

Occurrence может содержать несколько economic streams.

Пример:

```text
carbonaceous asteroid
→ WATER_ICE potential
→ VOLATILE_FEEDSTOCK potential
→ CARBONACEOUS_FEEDSTOCK potential
→ minor METALLIC_ORE potential
```

Генератор Stage 20 определяет конкретные reserves/grades/locations из seed и world conditions, но не изобретает новые resource classes.

---

# 5. Baseline natural feedstock families

Количество raw families намеренно ограничено.

| Family | Что абстрагирует | Основные применения |
| --- | --- | --- |
| **WATER_ICE** | лёд, hydrated material, recoverable H2O | water, life support, process feed, selected propellant/reaction-mass chains |
| **VOLATILE_FEEDSTOCK** | H/C/N/O-bearing ices/gases, ammonia/methane/CO2/N2-like streams | industrial gases, chemicals, propellant/fuel families, agriculture |
| **CARBONACEOUS_FEEDSTOCK** | carbon-rich regolith/organics/graphitic material | carbon materials, polymers/chemicals, reducing agents |
| **METALLIC_ORE** | Fe/Ni/Co-rich bulk metallic feed | structural alloys, machinery, armor, general heavy industry |
| **LIGHT_METAL_MINERALS** | Al/Mg/Ti-rich silicate/oxide feed | light alloys, structures, thermal/ship components |
| **CONDUCTOR_ORE** | Cu-rich and analogous conductive-metal feed | cabling, motors, coils, electrical systems |
| **STRATEGIC_METAL_ORE** | Cr/W/Mo/Nb/Ta/PGM-like high-performance metals merged into one scarce family | refractory/high-temperature alloys, armor/weapon/drive/reactor/high-end components |
| **SILICATE_MINERALS** | Si/O-rich rock/regolith and ceramic mineral feed | glass, ceramics, insulation, substrates, electronic-grade feed |
| **FISSILE_MINERALS** | U/Th-like actinide-bearing feed where reactor technology uses it | reactor-fuel production |

## 5.1 Почему именно такие объединения

### Common Fe/Ni/Co → METALLIC_ORE

Для gameplay важнее bulk structural-metal economy, чем отдельная торговля железом, никелем и кобальтом на каждой стадии. Отдельные alloying requirements могут существовать как recipe modifiers/content data позднее без трёх параллельных macro supply chains.

### Al/Mg/Ti → LIGHT_METAL_MINERALS

Реальная химия различна, особенно для titanium, но для macro-economy они образуют понятный класс энергоёмкой light/high-performance metallurgy. Stage 22 может разделить отдельные advanced material families, если fitting balance действительно этого потребует.

### W/Mo/Nb/Ta/Cr/PGM-like → STRATEGIC_METAL_ORE

Эти элементы редки, технологически важны и создают хороший bottleneck, но отдельная экономика каждого элемента избыточна для baseline. Strategic family выражает scarcity и advanced processing requirement.

### Silicon feed не равен electronics

`SILICATE_MINERALS` остаётся bulk raw material. High-purity electronics появляются только после отдельного precision purification/fabrication chain.

---

# 6. Типовые природные источники

Resource distribution в Stage 20 использует физически правдоподобные associations, а не uniform loot table.

| Host / world object | Типичные baseline resources | Комментарий |
| --- | --- | --- |
| **Metallic asteroid / differentiated core fragment** | METALLIC_ORE, STRATEGIC_METAL_ORE, minor CONDUCTOR_ORE | высокая metal fraction, ценные trace resources |
| **Stony / silicate asteroid** | SILICATE_MINERALS, LIGHT_METAL_MINERALS, METALLIC_ORE | распространённый mixed feed |
| **Carbonaceous asteroid** | WATER_ICE, VOLATILE_FEEDSTOCK, CARBONACEOUS_FEEDSTOCK, minor metals | важен для frontier water/chemistry economy |
| **Icy body / comet / icy ring material** | WATER_ICE, VOLATILE_FEEDSTOCK | высокий logistics value вдали от developed hubs |
| **Rocky moon / planet / large differentiated body** | SILICATE_MINERALS, LIGHT_METAL_MINERALS, METALLIC_ORE, CONDUCTOR_ORE, STRATEGIC_METAL_ORE, FISSILE_MINERALS depending geology | требует более тяжёлой surface/deep extraction infrastructure |
| **Gas/ice giant atmosphere** | VOLATILE_FEEDSTOCK; later specialized isotope/noble-gas streams if justified | high-capability atmospheric harvesting, не бесплатный bulk source |
| **Wreck / debris field / abandoned infrastructure** | SALVAGE OUTPUT, not geological reserve | materials/components are recovered from destroyed manufactured assets |

Не каждый system обязан содержать все families.

Scarcity и spatial specialization являются feature, если существует физически возможная inter-system logistics network.

---

# 7. Extraction methods

Baseline различает методы там, где меняются required assets, operating environment и economics.

## 7.1 Asteroid / free-body excavation

Для metallic/stony/carbonaceous small bodies.

Требует:

- anchoring / station-keeping capability;
- excavation/cutting capability;
- material capture;
- power;
- cargo handling;
- wear/maintenance.

Не требует terrestrial gravity-well mine abstraction.

## 7.2 Surface / regolith mining

Для moons, planets, larger bodies.

Требует:

- surface-compatible machinery;
- excavation;
- hauling;
- local power;
- environment tolerance.

## 7.3 Deep / hard-rock mining

Для deposits, которые нельзя экономично получить surface excavation.

Tradeoff:

```text
higher infrastructure + work + maintenance
→ access to richer/deeper reserves
```

## 7.4 Thermal volatile extraction

Для water/volatile-rich regolith, carbonaceous feed и ices.

Процесс conceptually:

```text
excavate / expose feed
→ add thermal/process energy
→ release vapor/volatile fraction
→ capture / condense / separate
```

Stage 18 не симулирует молекулярную kinetics; authoritative inputs — feedstock, energy capacity, process capability, time and yield.

## 7.5 Atmospheric harvesting

Для gas/ice giant or other atmosphere-capable bodies.

Требует специализированных vehicles/facilities, separation capability и significant energy/operational cost.

Нельзя использовать как бесконечный бесплатный source только потому, что atmosphere large.

## 7.6 Salvage / recovery

Wrecks и destroyed installations возвращают часть реально вложенных материалов/components через bounded recovery yield.

Нельзя:

```text
destroy ship worth X
→ spawn arbitrary salvage worth > physically recoverable inputs
```

---

# 8. Beneficiation / concentration policy

Реальная промышленность часто отделяет crushing/sorting/concentration до refining.

В baseline эта стадия существует как **process capability**, но не всегда как отдельный trade commodity.

Default:

```text
raw feedstock
→ beneficiation + refining inside one industrial chain
→ engineering material
```

Отдельный transportable `CONCENTRATE` вводится только для конкретной resource family, если выполняется хотя бы одно условие:

- удаление waste radically меняет haul mass;
- mine-side processing создаёт meaningful investment decision;
- concentrate имеет отдельный storage/hazard constraint;
- separation позволяет торговать ore и refined hub независимо.

Так мы сохраняем реальную причинность без удвоения количества товаров.

---

# 9. Baseline engineering materials and consumables

После refining экономика должна иметь ограниченный набор materially meaningful outputs.

| Product family | Получается преимущественно из | Использование |
| --- | --- | --- |
| **PURIFIED_WATER** | WATER_ICE | life support, industrial process, agriculture, selected reaction-mass/propellant recipes |
| **INDUSTRIAL_GASES** | WATER_ICE + VOLATILE_FEEDSTOCK | oxygen/nitrogen/inert/process gases merged where separate logistics is not useful |
| **INDUSTRIAL_CHEMICALS** | VOLATILE_FEEDSTOCK + CARBONACEOUS_FEEDSTOCK | polymers, sealants, lubricants, explosives/energetics, process chemistry |
| **STRUCTURAL_ALLOY** | METALLIC_ORE | hull structure, station structure, armor backing, machinery |
| **LIGHT_ALLOY** | LIGHT_METAL_MINERALS | mass-sensitive structure, radiators, tanks, spacecraft components |
| **CONDUCTOR_METAL** | CONDUCTOR_ORE | wiring, coils, motors, power distribution |
| **REFRACTORY_ALLOY** | STRATEGIC_METAL_ORE + common metals | high-temperature, high-stress, armor/weapon/drive/reactor components |
| **CERAMIC_GLASS** | SILICATE_MINERALS + selected additives | optics feed, armor ceramics, insulation, windows, thermal/electrical structures |
| **CARBON_MATERIAL** | CARBONACEOUS_FEEDSTOCK | graphite/carbon structures, composites, thermal/chemical uses |
| **ELECTRONIC_GRADE_MATERIAL** | SILICATE_MINERALS + CONDUCTOR/STRATEGIC inputs | semiconductor/optical high-purity substrate abstraction |
| **REACTOR_FUEL** | FISSILE_MINERALS or later technology-specific resource chain | energy generation where applicable |

## 9.1 Reaction mass policy

Stage 18 **не фиксирует один магический universal fuel**.

Stage 17.5/22 drive definitions задают compatible consumable family.

Baseline economy должна поддерживать минимум:

```text
raw water / volatile source
→ purification/separation
→ technology-specific reaction mass / propellant commodity
```

Если несколько drive technologies используют логистически эквивалентный processed fluid, они могут ссылаться на один commodity. Если storage/source/strategic behavior различается — commodities разделяются.

## 9.2 Reactor fuel policy

`REACTOR_FUEL` — baseline placeholder family, а не утверждение, что вся setting использует uranium fission.

Конкретная technology может:

- использовать fissile chain;
- использовать volatile/isotope chain;
- не требовать transported fuel commodity, если это физически и design-wise обосновано.

Но fuel не появляется бесплатно при materialization/refit.

---

# 10. Industrial component layer

Чтобы shipyard не превращал руду напрямую в готовый cruiser, вводится небольшой component layer.

Baseline component families:

## 10.1 HEAVY_COMPONENTS

Абстрагирует:

- pressure vessels;
- structural sections;
- pumps;
- actuators;
- bearings;
- heavy machinery;
- generic mechanical assemblies.

Типовые inputs:

```text
STRUCTURAL_ALLOY
+ LIGHT_ALLOY where required
+ REFRACTORY_ALLOY for high-duty variants
+ work + facility capability
```

## 10.2 ELECTRICAL_COMPONENTS

Абстрагирует:

- cables/busbars;
- motors;
- coils;
- switching/power electronics;
- electrical distribution assemblies.

Типовые inputs:

```text
CONDUCTOR_METAL
+ CERAMIC_GLASS
+ STRUCTURAL/LIGHT material
+ INDUSTRIAL_CHEMICALS
```

## 10.3 PRECISION_COMPONENTS

Абстрагирует:

- processors/control electronics;
- sensor electronics;
- precision optics/mechatronics;
- high-tolerance guidance/control assemblies.

Типовые inputs:

```text
ELECTRONIC_GRADE_MATERIAL
+ CONDUCTOR_METAL
+ CERAMIC_GLASS
+ small STRATEGIC/REFRACTORY input
+ precision fabrication capability
```

Этого достаточно, чтобы создать различие между mining/refining economy, heavy industry и high-tech industry без десятков generic subcomponents.

---

# 11. Finished industrial products

Finished goods не должны обязательно существовать как три generic tiers.

После component layer production recipes создают реальные content definitions:

```text
Ship Module Definition
Ammunition Definition
Drone Definition
Station Module / Facility Module Definition
Construction Component where required
```

Примеры:

### Drive module

```text
STRUCTURAL/LIGHT material
+ REFRACTORY_ALLOY
+ HEAVY_COMPONENTS
+ ELECTRICAL_COMPONENTS
+ PRECISION_COMPONENTS
→ drive module
```

### Sensor module

```text
LIGHT_ALLOY
+ CERAMIC_GLASS
+ ELECTRONIC_GRADE_MATERIAL
+ ELECTRICAL_COMPONENTS
+ PRECISION_COMPONENTS
→ sensor module
```

### Armor section

```text
STRUCTURAL_ALLOY
+ CERAMIC_GLASS and/or REFRACTORY_ALLOY
+ HEAVY_COMPONENTS where geometry requires
→ protection section
```

### Guided ammunition

```text
STRUCTURAL/LIGHT material
+ INDUSTRIAL_CHEMICALS or technology-specific propellant
+ ELECTRICAL_COMPONENTS
+ PRECISION_COMPONENTS
→ missile/torpedo round
```

Ammo остаётся физическим manufactured inventory item.

---

# 12. Civilian / habitation goods baseline

Industrial model должен поддерживать living economy, но Stage 18 не превращается в full population simulator.

Минимум:

## FOOD

Conceptual chain:

```text
PURIFIED_WATER
+ nutrient/volatile inputs
+ energy
+ agricultural/habitat capacity
+ time
→ FOOD
```

Nutrients могут быть folded into `VOLATILE_FEEDSTOCK`/industrial supply на baseline, пока отдельная fertilizer economy не создаёт gameplay value.

## CONSUMER_GOODS

Conceptual chain:

```text
INDUSTRIAL_CHEMICALS
+ small metal/material inputs
+ manufacturing capacity
→ CONSUMER_GOODS
```

Это обеспечивает civilian demand sink без симуляции тысяч SKU.

Medical/specialized civilian goods могут расширяться Stage 22.

---

# 13. Canonical example chains

## 13.1 Frontier water / propellant economy

```text
icy body / carbonaceous asteroid
→ WATER_ICE
→ thermal extraction + purification
→ PURIFIED_WATER
→ life support / agriculture

PURIFIED_WATER + separation/electrolysis capability
→ INDUSTRIAL_GASES
→ life support / processing / selected propulsion chains

PURIFIED_WATER or VOLATILE_FEEDSTOCK
→ technology-specific propellant/reaction-mass processing
→ ship consumable
```

Gameplay consequence: remote water-rich body can support depot/mining infrastructure even if it lacks metals.

## 13.2 Bulk structural industry

```text
metallic/stony deposit
→ METALLIC_ORE
→ beneficiation/refining
→ STRUCTURAL_ALLOY
→ HEAVY_COMPONENTS
→ hull/station structure + machinery
→ shipyard/station integration
```

Gameplay consequence: bulk metal is common but heavy and logistics-intensive; local refining can matter more than raw reserve value.

## 13.3 Light spacecraft materials

```text
light-metal-rich regolith
→ LIGHT_METAL_MINERALS
→ energy-intensive refining
→ LIGHT_ALLOY
→ tanks/radiators/light structures
→ ship/module fabrication
```

Gameplay consequence: access to ore alone does not guarantee output; refining energy/capability can be bottleneck.

## 13.4 Electrical industry

```text
CONDUCTOR_ORE
→ refining
→ CONDUCTOR_METAL

CONDUCTOR_METAL
+ CERAMIC_GLASS
+ INDUSTRIAL_CHEMICALS
→ ELECTRICAL_COMPONENTS
→ motors / power systems / modules / infrastructure
```

Gameplay consequence: shipbuilding center may have abundant steel but still stall on electrical imports.

## 13.5 Precision electronics / sensors

```text
SILICATE_MINERALS
→ high-purity refining
→ ELECTRONIC_GRADE_MATERIAL

ELECTRONIC_GRADE_MATERIAL
+ CONDUCTOR_METAL
+ CERAMIC_GLASS
+ small STRATEGIC input
→ PRECISION_COMPONENTS
→ sensors / computers / seekers / EW / fire control
```

Gameplay consequence: high-tech production depends on small-volume, high-value supply chains distinct from bulk metal logistics.

## 13.6 High-temperature / military materials

```text
STRATEGIC_METAL_ORE
→ advanced separation/refining
→ REFRACTORY_ALLOY
→ high-duty components
→ drives / reactors / heavy weapons / advanced armor
```

Gameplay consequence: rare strategic deposits affect advanced production without requiring a separate market for tungsten, molybdenum, niobium, tantalum and every PGM.

## 13.7 Carbon / chemical industry

```text
carbonaceous body
→ CARBONACEOUS_FEEDSTOCK
+ VOLATILE_FEEDSTOCK
→ chemical processing
→ CARBON_MATERIAL + INDUSTRIAL_CHEMICALS
→ composites / polymers / sealants / energetic materials / consumer production
```

## 13.8 Nuclear-fuel example where technology uses fissiles

```text
fissile-bearing deposit
→ FISSILE_MINERALS
→ concentration / purification / fuel fabrication
→ REACTOR_FUEL
→ reactor inventory
```

Fuel consumption and replacement use ordinary inventory/production paths.

## 13.9 Salvage / recycling

```text
damaged/destroyed ship or station
→ recoverable physical wreck state
→ salvage operation
→ bounded recovered materials/components
→ sorting/recycling
→ engineering materials and/or reusable components
```

Recovered mass/value is derived from actual constructed state, damage and recovery efficiency, not a random reward table detached from inputs.

---

# 14. Facility capability model

Station archetype не является magic production bonus.

Authoritative facility описывается capabilities:

```text
FacilityDefinition
- supported processes / recipes
- throughput/work rate
- required power
- heat rejection requirement
- labor/automation requirement
- storage interfaces
- berth/handling limits
- environment/location constraints
- maintenance inputs
- damage state
- technology/certification tags where needed
```

## Baseline facility families

- **Extraction Facility** — asteroid/surface/deep resource extraction.
- **Volatile Processor** — water/volatile thermal extraction, purification, gas/propellant processing.
- **Bulk Refinery / Smelter** — common metallic and light-metal refining.
- **Advanced Materials Plant** — refractory alloys, ceramics, carbon/composite materials.
- **Chemical Plant** — industrial chemicals, polymers, energetic/process materials.
- **Heavy Fabrication Plant** — HEAVY_COMPONENTS and large structures.
- **Electrical Works** — ELECTRICAL_COMPONENTS.
- **Precision / Electronics Fab** — electronic-grade processing and PRECISION_COMPONENTS.
- **Ordnance Plant** — ammunition/warheads/munition assembly where recipes require.
- **Agricultural / Life-Support Complex** — FOOD and relevant civilian consumables.
- **Recycling / Salvage Processor** — reclaim materials/components with bounded yield.
- **Assembly Plant** — selected finished modules/equipment.
- **Shipyard / Integration Yard** — ship construction, refit, heavy repair.
- **Station Construction / Integration Facility** — station modules/large infrastructure.
- **Power Plant** — local utility capacity, not normally a cargo commodity.
- **Storage / Depot / Logistics Facility** — inventory and cargo-class handling.

Individual station can combine several facilities.

---

# 15. Station archetypes

Archetype — это readable role, а не источник скрытых capabilities.

Baseline roles:

```text
mining outpost
volatile/water depot
refinery complex
industrial station
high-tech manufacturing hub
trade/logistics hub
fuel/reaction-mass depot
habitat/agricultural station
repair yard
shipyard
naval base
research/survey station
frontier multipurpose station
```

Например `industrial station` может быть composed as:

```text
Bulk Refinery
+ Heavy Fabrication
+ Electrical Works
+ storage
+ docking
+ power
```

Но station получает production только от реально установленных capabilities.

---

# 16. Shipyard model

Shipyard — отдельный strategic asset, а не `credits → ship` menu.

Минимальные axes:

```text
berth / integration envelope
max hull dimensions / mass class
heavy fabrication capability
precision/electronics integration
reactor/drive handling
weapon/ordnance integration
shield/FTL integration where applicable
work rate
labor/automation
repair capability
refit capability
staging/storage capacity
```

Construction concept:

```text
ship design
→ required engineering materials
→ required industrial components
→ required finished modules
→ required yard capabilities
→ physical delivery to yard
→ integration work over time
→ launched persistent ship
```

High-end shipyard не создаётся только от большого treasury balance. Он сам требует physical construction chain and supporting industry.

---

# 17. Storage and logistics classes

Чтобы resource chains создавали logistics gameplay, cargo compatibility должна быть выразимой без отдельной physics model для каждого SKU.

Baseline storage classes:

- **BULK_SOLID** — ores/minerals/common bulk materials;
- **GENERAL_CARGO** — components/consumer goods/modules in standard handling units;
- **LIQUID / WATER** — water-like fluids;
- **CRYOGENIC / VOLATILE** — temperature-sensitive propellant/volatile cargo where required;
- **PRESSURIZED_GAS** — gas storage where separate handling matters;
- **HAZARDOUS / ENERGETIC** — reactor fuel, explosives, ammunition or chemicals where applicable;
- **HIGH_VALUE_CONTROLLED** — precision/strategic cargo where security/legal systems care;
- **OVERSIZED** — hull sections/large machinery that need specialized haulers/berths.

Content may merge storage classes if a technology makes them operationally equivalent.

Storage class affects:

- compatible cargo hold/tank;
- loading/unloading capability;
- loss/spoil/boiloff only if explicitly modeled;
- safety/hazard consequences;
- transport cost and ship specialization.

---

# 18. Energy, heat and workforce

## 18.1 Energy

Electric power/process heat являются **local facility capacity**, а не автоматически tradable inventory.

Production recipe требует power/time, но не создаёт fictitious cargo `ENERGY` unless future gameplay explicitly needs transportable stored-energy goods.

Fuel для reactor/drive remains physical inventory where technology requires it.

## 18.2 Heat

High-energy refining/manufacturing can require heat-rejection capability, but Stage 18 does not run full thermal simulation for every dormant factory.

Local detailed facility can expose physical thermal state; strategic/offscreen production uses validated capability/throughput budgets consistent with common rules.

## 18.3 Workforce

Stage 18 defines `work/labor/automation requirement`, but full NPC population simulation belongs later.

Until Stage 21 living-world layer provides richer population state, facility workforce can be represented by persistent capacity supplied by habitation/automation systems. It must not grant player-only free throughput.

---

# 19. Waste, by-products and mass accounting

Не каждый waste stream становится cargo item.

Default recipe accounting:

```text
input mass
→ useful outputs
+ discarded/process waste
```

Rules:

- production cannot create net material mass without an explicit mass source;
- mass loss may represent discarded slag/tailings/gases when they have no gameplay value;
- valuable by-product becomes separate output only if it creates meaningful economy;
- recycling yield is <100% unless a specific process explicitly justifies otherwise;
- energy consumption is not treated as material mass.

Waste/tailings may become physical world state only where hazards, reclamation or resource recovery make them relevant.

---

# 20. Construction, maintenance and repair coupling

Stage 18 extends the existing physical construction invariant.

Station/facility construction:

```text
engineering materials
+ components
+ construction site
+ physical delivery
+ work/time
→ installed facility
```

Ship/module maintenance:

```text
wear/damage state
+ compatible materials/components/spares
+ repair capability
+ work/time
→ restored state
```

No automatic free repair after docking, materialization or save/load.

Stage 17.5G remains responsible for shipyard/refit/repair runtime seams; Stage 18 supplies the industrial graph those systems consume.

---

# 21. Colonization / industrial development ladder

Stage 18 defines **possible industrial development**, not scripted settlement upgrades.

Typical emergent chain:

```text
survey / discovery
→ extraction outpost
→ local water/power/logistics
→ mine-side processing
→ refinery
→ storage/trade hub
→ heavy fabrication
→ electrical/precision manufacturing
→ repair yard
→ shipyard / major industrial complex
```

Но фактический world state зависит от resources, demand, capital, logistics, policy and construction.

Нельзя:

```text
colony level 3
→ free shipyard unlock
```

---

# 22. Economic archetypes for world generation

Stage 20 может использовать archetypes как bootstrap constraints, но не как hidden resource grants.

Examples:

- **Resource frontier** — extraction-heavy, imports manufactured goods.
- **Water/volatile hub** — water/propellant/life-support exporter.
- **Bulk refining system** — imports ore or mines locally, exports structural/light materials.
- **Heavy industrial center** — components, modules, construction goods.
- **High-tech center** — precision/electronics bottleneck, high-value imports/exports.
- **Trade/logistics hub** — storage/transshipment/market access, limited own extraction.
- **Shipbuilding center** — deep supply chain + large integration yards.
- **Habitation/consumer center** — large civilian demand and food/consumer production.
- **Military/naval hub** — ammunition, repair, depots, secure logistics.

Generator must instantiate ordinary facilities, inventories, routes and actors that make the archetype true.

---

# 23. Stage 18 implementation sequence

## Stage 18A — schema / resource ontology

Implement production-grade definitions for:

- resource occurrence;
- extracted feedstock family;
- engineering material;
- industrial consumable;
- component family;
- storage class;
- process/capability tag.

DoD:

- no class-name bonuses;
- data-driven IDs;
- deterministic serialization/fingerprint;
- existing early-game resources have explicit migration/mapping plan.

## Stage 18B — extraction and source compatibility

Implement:

- occurrence → extraction method compatibility;
- finite reserves;
- grade/yield;
- extraction work/power/maintenance inputs;
- asteroid/surface/volatile/salvage baseline paths.

## Stage 18C — refining / material production

Implement baseline recipes for:

- water/volatiles;
- structural metals;
- light metals;
- conductors;
- strategic/refractory material;
- ceramics/glass;
- carbon/chemicals;
- electronic-grade material;
- reactor fuel where current technology requires.

## Stage 18D — industrial components and module recipes

Implement:

- HEAVY_COMPONENTS;
- ELECTRICAL_COMPONENTS;
- PRECISION_COMPONENTS;
- mapping from Stage 17.5 modules/ammunition to real manufacturing inputs.

## Stage 18E — facility capability architecture

Production belongs to installed capability, not station class.

Implement throughput/power/work/storage/location requirements and damage/maintenance seams.

## Stage 18F — stations / storage / logistics

Implement composable station/facility archetypes and cargo/storage compatibility.

## Stage 18G — shipyard / repair / refit industrial integration

Connect Stage 17.5G to physical component/material delivery and yard capabilities.

## Stage 18H — recycling / salvage / construction economy

Close material loop with bounded salvage/recycling and physical facility construction.

## Stage 18I — deterministic industrial acceptance

Run a hand-authored minimal industrial universe before procedural generation.

Required scenario:

```text
resource occurrence
→ extraction
→ refining
→ engineering materials
→ industrial components
→ ship/module/ammunition production
→ operation/consumption
→ damage
→ repair
→ destruction
→ salvage/recycling
```

Must prove conservation, finite reserves, no hidden supply and save/load equivalence.

---

# 24. Minimal viable industrial universe acceptance

Stage 18 cannot close until a deterministic headless scenario proves at least these chains simultaneously:

1. **water chain** supports life/process/ship consumable production;
2. **bulk metal chain** builds real structure/heavy components;
3. **electrical chain** creates electrical bottleneck distinct from bulk metal;
4. **precision chain** creates high-tech bottleneck distinct from heavy industry;
5. **strategic material chain** constrains at least one advanced module family;
6. **shipyard chain** constructs or refits a real persistent ship from delivered goods;
7. **ammunition/consumable chain** replenishes only through physical production/logistics;
8. **repair chain** consumes physical inputs and work;
9. **salvage/recycling chain** never recovers more than physically available material;
10. **save/load** preserves inventories, reserves, recipes-in-progress, facilities and construction state.

---

# 25. Coupling to Stage 19 warfare

Strategic warfare comes **after** this industrial foundation so military operations can target real economic assets.

Examples:

```text
block water/propellant route
→ reduced fleet endurance

lose precision fab
→ sensor/missile replacement bottleneck

lose strategic-metal source
→ advanced drive/weapon production slows

lose capital shipyard
→ replacement time increases physically
```

War consequences must emerge from ordinary logistics/production state rather than scripted `-20% production` modifiers.

---

# 26. Coupling to Stage 20 physical world generation

Stage 20 receives a closed ontology:

```text
known world-object types
+ known resource occurrence rules
+ known extraction compatibility
+ known facility capabilities
+ known industrial recipes
+ known ship/logistics performance
→ generate physical economic geography
```

Stage 20 chooses:

- where resources occur;
- grades/reserves;
- distances;
- infrastructure placement;
- initial industrial specialization;
- physical routes and bottlenecks.

Stage 20 does **not** invent new resource types to rescue a bad seed.

---

# 27. Coupling to Stage 22 Content & Balance Alpha

Stage 18 creates the **minimum complete economic language**.

Stage 22 expands vocabulary:

- more alloys/material families where justified;
- multiple reactor/drive/sensor/weapon families;
- faction engineering doctrines;
- high-tech/exotic resources if setting requires;
- specialized civilian goods;
- richer ammunition/propellant families;
- broader station/shipyard capability range;
- balance and long-run soak.

Rule:

> Stage 22 may split an aggregate Stage-18 family only when the split creates a demonstrated gameplay/engineering/logistics distinction.

---

# 28. Запрещённые shortcuts

Без explicit architecture decision запрещены:

- `ORE → SHIP` direct recipes;
- production from credits without physical inputs;
- station type granting hidden production multiplier;
- infinite deposits by default;
- every resource spawning in every system for convenience;
- player-only mine/refinery efficiency;
- AI-only virtual component supply;
- ammunition/refuel on materialization;
- free repair on docking/load;
- abstract `industrial capacity` that replaces real facilities/inventories;
- one universal `RARE_RESOURCE` used for every advanced technology without physical/content reason;
- dozens of trace-element commodities that produce no meaningful player decision;
- production recipes whose missing inputs are silently ignored;
- shipyard building any hull solely because credits are available.

---

# 29. Stage 18 Definition of Done

Stage 18 COMPLETE when:

- resource/facility ontology is data-driven and versioned;
- baseline natural feedstock families have defined occurrence rules;
- extraction method compatibility is explicit;
- refining/material/component chains are connected without dead goods;
- station capabilities are composable and not class bonuses;
- shipyards use physical materials/components/modules/work;
- storage/logistics classes constrain relevant cargo;
- ammunition/reaction mass/reactor fuel are physical where technology requires;
- civilian water/food/consumer baseline exists without SKU explosion;
- salvage/recycling is bounded by real constructed state;
- industrial construction uses ordinary Stage-16-style physical delivery/time semantics;
- minimal industrial universe acceptance is deterministic and save/load-safe;
- no essential production path depends on hidden resources;
- Stage 20 world generator can consume the ontology without inventing missing economic rules;
- full CI and deterministic industrial acceptance are green.

---

# 30. Итоговый Stage 18 invariant

```text
physical resource occurrence
→ compatible extraction
→ finite physical feedstock
→ explicit processing capability
→ engineering material / consumable
→ industrial component
→ real module / ammunition / infrastructure
→ ship / station operation
→ wear / loss / repair
→ bounded salvage / recycling
```

Если товар или facility существует только как abstract bonus и не занимает место в этой причинной цепочке, он не является частью authoritative industrial model Star Empires.