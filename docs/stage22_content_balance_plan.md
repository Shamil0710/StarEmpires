# Star Empires — Stage 22 Content Width / Technology / Balance / Long-run Stability

> Статус: **PLANNED**  
> Основание: production Stage 17.5 + Stage 18 industrial foundation + Stages 19–21 world/war/RPG layers  
> Назначение: расширить мир, корабли, модули, технологии и faction differentiation, не создавая вторую систему правил и не ломая Stage-18 resource/industry ontology.

---

# 1. Главный принцип Stage 22

> **Stage 22 расширяет пространство решений внутри принятых physical/economic contracts, а не добавляет скрытые parallel stats ради удобства контента.**

Новый hull/module/technology/resource считается допустимым только если его преимущества и недостатки выражаются через accepted budgets/interfaces.

Если автору нужен новый фундаментальный stat/resource, это `Architecture Change Request`.

Stage 18 создаёт **minimum complete economic language**; Stage 22 создаёт широкий playable vocabulary внутри него.

---

# 2. Входные условия

До основного Stage 22 должны быть стабильны:

- Stage 17.5 production fitting/combat foundation;
- Stage 18 resources/industry/infrastructure foundation;
- Stage 19 strategic warfare/coercive diplomacy/advanced combat behavior;
- Stage 20 physically calibrated world generation;
- Stage 21 NPC/missions/reputation/living-world mechanics;
- v1.0 schema and capability APIs;
- real construction/refit/repair/maintenance seams;
- baseline industrial resource/component/facility graph.

Stage 22 может получать early content prototypes раньше, но массовая балансировка не должна строиться поверх временных mechanics.

---

# 3. Technology ladder philosophy

Technology не является линейной шкалой `tier → all stats up`.

Каждая технология должна менять конкретные engineering parameters.

Примеры осей:

```text
specific power W/kg
reactor efficiency / waste heat
stored energy J/kg
specific thrust N/kg
exhaust velocity m/s
cooling temperature / emissivity
sensor aperture efficiency / noise
pointing jitter
ECM spectral power / processing
material density / response surface
shield field capacity / cost / recharge / heat
launcher cycle / support channels
automation / crew demand
manufacturing tolerance
maintenance interval / complexity
```

## Hard rule

Запрещён blanket authoring pattern:

```text
Mk II:
  damage *= 1.25
  armor *= 1.25
  speed *= 1.25
  sensor *= 1.25
```

если это не derived presentation конкретных engineering improvements.

---

# 4. Stage 22A — material/component technology catalog

Расширить Stage-18 aggregate material catalog до practical content set **только там, где split создаёт gameplay distinction**.

Возможные категории:

- structural alloy families;
- high-temperature structures;
- armor strike faces;
- backing/spall layers;
- composites;
- radiator materials;
- conductor/superconductor families if setting needs;
- high-density penetrator materials;
- optics/sensor substrates;
- electronics/automation components;
- exotic shield/FTL components.

Каждый material/component связывается с:

- density/mass;
- Stage-18 manufacturing inputs;
- facility capability;
- repair/salvage semantics;
- response surfaces/signature/thermal properties where relevant.

## Split rule

Например Stage-18 `STRATEGIC_METAL_ORE` не делится автоматически на tungsten/molybdenum/niobium/tantalum/PGM markets. Split допускается, если разные sources/processes/storage/use-cases создают реально разные logistics/technology decisions.

## Heavy-impact content

Stage 22 расширяет `HeavyImpactResponseSurface` dataset.

Нельзя использовать один universal curve для всех materials.

Требуется coverage diagnostics: какие weapon/protection combinations находятся внутри calibrated authoring domain, а какие нет.

---

# 5. Stage 22B — reactors, energy storage и electrical distribution families

Создать materially different families:

- compact high-specific-power combat reactor;
- efficient civilian/endurance reactor;
- high-temperature military reactor;
- low-signature/low-output mode where setting supports;
- capacitor/battery families;
- redundant distribution modules.

Tradeoffs:

```text
mass
volume
continuous output
peak support
waste heat
thermal temperature
signature
maintenance
materials
component requirements
fuel/consumable requirements where applicable
cost
reliability/damage tolerance
```

Не должно существовать reactor, который одновременно легче, мощнее, холоднее, дешевле и проще без technology/economic reason.

---

# 6. Stage 22C — propulsion / maneuver / reaction-mass / FTL families

## Sublight drives

Различия через:

- thrust;
- exhaust velocity;
- drive mass/volume;
- jet power;
- waste heat;
- plume spectral/aspect signature;
- throttle envelope;
- maintenance;
- reaction-mass compatibility;
- Stage-18 material/component requirements.

Создать минимум doctrines:

- civilian efficiency;
- military high thrust;
- raider high delta-v;
- tug/heavy industrial thrust;
- low-observable compromise where feasible.

## FTL

Families отличаются:

- max translated mass;
- energy per kg;
- charge power;
- spool time;
- cooldown;
- mass/volume;
- maintenance;
- topology/access compatibility if setting requires;
- manufacturing/strategic-material bottlenecks.

Capital jump capability должна иметь реальную fitting/economic цену.

---

# 7. Stage 22D — thermal systems

Catalog:

- radiator families;
- coolant pumps;
- thermal buses;
- local heat sinks;
- phase-change stores;
- armored/retractable radiators where setting permits;
- high-temperature vs survivable/low-signature choices.

Balance должен учитывать:

- mass;
- area/geometry;
- rejection W;
- operating temperature;
- vulnerability;
- signature;
- stored thermal capacity from real material/mass model;
- repair/maintenance;
- industrial inputs.

---

# 8. Stage 22E — sensors, communications, fire control и EW catalog

Families:

- passive IR/optical;
- active radar;
- precision fire-control optical/radar;
- recon apertures;
- distributed sensor nodes;
- datalink/command modules;
- noise/deceptive jammers;
- ECCM processors;
- decoy dispensers;
- recon drones.

Balance через:

```text
aperture
band/frequency
noise
integration time
pointing
power
heat
field of regard
scan throughput
ECM interference
processing/dwell
mass/volume
signature when active
precision-component demand
```

No `sensor range tier` independent of target/signature/geometry.

---

# 9. Stage 22F — weapon and ammunition families

## Kinetic

Разнообразие:

- high-velocity low-mass darts;
- heavier lower-velocity penetrators;
- rapid-fire medium systems;
- capital kinetic weapons;
- specialized debris/area rounds only with explicit physics.

Каждая family задаёт projectile material/geometry/mass/velocity/ammunition handling.

## Beam

- PD laser;
- medium precision laser;
- heavy laser;
- alternate wavelengths/apertures where useful.

Tradeoffs: aperture, pointing, power, heat, dwell, vulnerability.

## Guided

- S interceptors;
- extended area defense;
- M anti-ship missiles;
- L heavy torpedoes;
- specialized seekers/warheads/decoys.

Tradeoffs: wet mass, delta-v, thrust, seeker, terminal reserve, magazine volume/mass, support channels.

## Ammunition economy

Ammo имеет real manufacturing/resource cost и logistics footprint через Stage-18 ordnance/material/component chains.

Fleet sustained warfare должен физически потреблять ammunition production.

---

# 10. Stage 22G — shields and protection families

## Shields

Technology variations выражаются через:

- field capacity;
- field cost per coupled incident energy;
- max interaction power;
- coverage geometry;
- threat coupling;
- recharge power/efficiency;
- heat;
- emitter mass/volume;
- damage tolerance;
- restart;
- manufacturing/material requirements.

Examples:

- broad low-density civilian field;
- high-power combat field;
- directional reinforced emitter;
- efficient slow-recharge field;
- capital distributed field network.

Не вводить independent `shield armor bonus`.

## Passive protection

Families:

- light debris protection;
- spaced armor;
- ceramic/metal citadel stacks;
- compartment reinforcement;
- magazine protection;
- localized armor;
- damage-control redundancy.

Tradeoffs идут через mass/volume/geometry/material response, а не flat HP.

---

# 11. Stage 22H — hull families and variants

Создать content breadth внутри hierarchy:

```text
Hull Size
→ Architecture
→ Doctrine
→ Specialization
→ Design
→ Variant/Refit
```

Минимально покрыть:

### Military

- patrol;
- torpedo corvette;
- recon/EW frigate;
- escort destroyer;
- general cruiser;
- battlecruiser/raider;
- battleship;
- fleet carrier;
- troop/assault/support variants.

### Civilian/industrial

- courier/light trader;
- bulk freighter;
- gas/liquid tanker;
- miner;
- salvage ship;
- repair/support ship;
- fleet tanker;
- colony/industrial transport where setting needs.

## Anti-obsolescence invariant

Larger hull не должен автоматически отменять меньший.

Проверять:

- acceleration;
- signature;
- crew/OPEX;
- docking/shipyard access;
- PD/screen utility;
- scouting geometry;
- sortie/response cost;
- production time;
- logistics demand.

---

# 12. Stage 22I — faction engineering doctrines

Faction differentiation должна возникать из content choices, а не faction magic bonuses.

Например faction может предпочитать:

- high-thrust engines;
- heavy passive armor;
- strong shields;
- missile saturation;
- precision kinetics;
- carrier doctrine;
- recon/EW networks;
- automation/high capital cost;
- manpower-heavy low-tech maintenance;
- logistics endurance.

Faction doctrine определяет design preferences, procurement, fleet composition и industrial investments.

Допустимый bounded commander/policy modifier не должен переписывать фундаментальную физику.

---

# 13. Stage 22J — expansion of Stage-18 shipyard/facility/production ladder

Stage 18 уже определяет foundational facility capability model. Stage 22 расширяет его content breadth.

Facility axes:

- berth/integration size;
- heavy fabrication;
- precision manufacturing;
- exotic material handling;
- optics/electronics;
- reactor/drive capability;
- shield/FTL capability;
- ammunition/warhead capability;
- work rate;
- automation/labor;
- repair capability.

Technology ladder должен создавать real component bottlenecks.

High-tier ship нельзя строить только потому, что treasury имеет credits.

Новый station/shipyard archetype обязан разрешаться в реальные installed capabilities, а не hidden class modifier.

---

# 14. Stage 22K — construction / maintenance / repair / replacement economics balance

Stage 18 уже замыкает physical material/component/facility chain. Stage 22 калибрует её для expanded content.

Для каждого ship/module family вычислять/авторить через common economy seam:

```text
material inputs
component inputs
facility capability
work time
crew/labor
purchase price derived from economy
maintenance parts/time
repair materials/time
ammunition consumption
reaction mass
replacement time
salvage value
```

War sustainability становится экономическим outcome.

Fleet может выиграть бой и проиграть campaign, если ammunition/repair/replacement network не выдерживает attrition.

---

# 15. Stage 22L — fleet composition and doctrine balance

Проверять не 1v1 ships только, а fleet systems.

Required archetypal fleets:

- patrol/security;
- convoy escort;
- missile strike group;
- carrier group;
- line battle group;
- raider force;
- recon/EW task force;
- logistics train;
- civilian convoy.

Metrics:

- combat effectiveness;
- ammunition endurance;
- repair endurance;
- sensor reach;
- formation geometry;
- reaction-mass/endurance;
- OPEX;
- replacement cost/time;
- vulnerability to different doctrines.

---

# 16. Stage 22M — combat saturation / endurance soak

Expand combat matrices.

Axes минимум:

```text
attacker count
weapon family
salvo size
wave count
escort count
formation spacing
sensor quality
ECM/ECCM state
shield state
thermal state
magazine state
damage state
```

Outputs:

- leakers;
- interceptor expenditure;
- beam dwell/heat;
- shield reserve/recharge;
- subsystem damage;
- magazine remaining;
- repair burden;
- survival;
- cost exchange ratio.

Нельзя балансировать только по DPS.

---

# 17. Stage 22N — world-scale logistics soak

На Stage-20 generated regions проверять:

- real trade round trips;
- mining haul time;
- refinery/input specialization;
- electrical/precision component supply;
- tanker replenishment;
- ammunition resupply;
- repair parts;
- carrier aviation stores;
- fleet reinforcement;
- capital ship reaction mass;
- shipyard component supply.

Distance должен создавать measurable economic geography.

---

# 18. Stage 22O — macro economy / long-run simulation soak

Headless runs должны выявлять:

- inflation/deflation;
- dead economies;
- permanent shortages;
- excessive buffers;
- entity/ledger growth;
- construction backlog;
- runaway ship production;
- faction snowball;
- impossible replacement losses;
- logistics collapse;
- resource monopolies;
- idle shipyards;
- unbounded ammunition accumulation;
- pathological concentration of Stage-18 strategic resources;
- precision-component deadlocks.

Diagnostics должны показывать причинную цепочку, а не только final score.

---

# 19. Stage 22P — anti-universal-build matrix

Для каждой hull size проверять разнообразие viable fits.

Если один fit одновременно лучший по:

- DPS;
- defense;
- sensors;
- mobility;
- endurance;
- cost;

это balance defect, если нет осознанной technology discontinuity.

Required tests:

- armor vs acceleration;
- shield vs power/heat;
- missile magazine vs protection/volume;
- sensor aperture vs mass/cost;
- high delta-v vs payload;
- automation vs cost/power/vulnerability;
- carrier wing vs direct weapons;
- logistics fit vs combat fit.

---

# 20. Stage 22Q — anti-linear-tier-obsolescence matrix

Technology tiers должны создавать niches.

Проверить:

- old/common equipment дешевле/repairable/available;
- advanced equipment требует rarer components/facilities;
- high-performance components могут быть hotter/complex/expensive;
- smaller hulls сохраняют useful roles;
- faction industrial base влияет на viable technology.

Полная линейная замена допустима для отдельных mature components, но не должна автоматически превращать всю игру в `highest tier = only rational choice`.

---

# 21. Stage 22R — faction differentiation acceptance

Для каждой major faction сгенерировать reference fleets и industrial support.

Faction identity должна быть видна по:

- silhouettes/hull architecture;
- propulsion behavior;
- preferred engagement geometry;
- sensors/EW;
- shields/armor;
- weapon mix;
- logistics endurance;
- production chains;
- fleet composition;

но все factions используют одну физику и одну Stage-18 material/economic grammar.

---

# 22. Stage 22S — player progression and market availability

Игрок должен получать доступ к content через реальный мир:

- faction relations;
- market access;
- industrial location;
- shipyard capability;
- component availability;
- salvage/capture where legal;
- research/progression systems Stage 21+.

Не выдавать high-tier fit через menu unlock без physical/economic source, если это не explicit RPG abstraction.

---

# 23. Stage 22T — benchmark/fingerprint governance

Machine-readable content benchmark должен фиксировать:

- representative resource/material/component families;
- representative hulls;
- representative fits;
- technology families;
- cost/material inputs;
- combat matrices;
- world logistics matrices;
- long-run economy metrics.

Изменение content fingerprint требует явного review expected consequences.

Не golden-test every numeric output навсегда: lock only intentional invariants/reference anchors.

---

# 24. Architecture change policy during Stage 22

Новый content request, требующий поля вне accepted contracts, проходит вопросы:

1. можно ли выразить capability существующими physical parameters?
2. можно ли выразить resource через Stage-18 aggregate family без потери meaningful gameplay?
3. это новый derived UI metric или authoritative resource?
4. влияет ли он на player и AI одинаково?
5. нужен ли persistence?
6. влияет ли на world/economy scale?
7. требует ли migration?

Если это действительно fundamental new axis — создать explicit architecture proposal + benchmarks + regression, а не добавлять hidden field.

---

# 25. Stage 22 completion gate

Stage 22 COMPLETE, когда:

- content catalog достаточно широк для alpha;
- major technology families имеют meaningful tradeoffs;
- Stage-18 resource/industry graph выдерживает expanded content без hidden supply;
- factions различимы через engineering/economy doctrine;
- civilian + military roles имеют viable designs;
- no universal dominant fit;
- no automatic large-hull obsolescence of small hulls;
- high-tier production имеет реальные resource/component/facility bottlenecks;
- ammunition/reaction mass/repair logistics работают в long-run;
- Stage-20 world-scale economy стабильна на representative seeds;
- strategic wars создают replacement/economic consequences;
- save/load/soak остаются bounded;
- full CI + long-run benchmark gates green.

---

# 26. Итоговый Stage 22 invariant

```text
technology/content choice
→ changes physical/component capability
→ changes Stage-18 material/component/facility requirements
→ changes fitted ship budgets
→ changes movement/signature/combat/endurance
→ changes construction/maintenance/logistics cost
→ changes fleet doctrine and Stage-20 economic geography
```

Если technology или module минует эту цепочку и просто добавляет abstract bonus, он нарушает accepted design baseline.