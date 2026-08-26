# Star Empires — Stage 22 Content Width / Technology / Balance / Long-run Stability

> Статус: **PLANNED**  
> Основание: production Stage 17.5 + Stage 18 industrial foundation + Stages 19–21 world/war/RPG layers  
> Назначение: расширить мир, корабли, модули, технологии и faction differentiation, не создавая вторую систему правил и не ломая Stage-18 resource/industry ontology.  
> **Production-complete sovereign faction scope Stage 22: Империя + Индустриальный Союз.**

Canonical faction scope and horizon:

- `docs/factions/faction_roster_and_development_horizon.md`;
- `docs/factions/faction_gameplay_visual_balance_bible.md`;
- `docs/factions/empire_systemic_identity.md`;
- `docs/factions/empire_visual_bible.md`;
- `docs/factions/industrial_union_systemic_identity.md`;
- `docs/factions/industrial_union_visual_bible.md`;
- `docs/factions/post_core_faction_horizon.md`;
- `docs/factions/faction_balance_validation_framework.md`;
- `docs/factions/faction_implementation_roadmap.md`;
- `docs/characters/character_master_prompt.md`.

Директорат, Лига Свободных Систем, Пограничная Конфедерация, Консорциум и Кочевой Флот являются
каноническим **post-core horizon** и не входят в Stage-22/23 completion blocker set.

---

# 1. Главный принцип Stage 22

> **Stage 22 расширяет пространство решений внутри принятых physical/economic contracts, а не добавляет скрытые parallel stats ради удобства контента.**

Новый hull/module/technology/resource считается допустимым только если его преимущества и недостатки выражаются через accepted budgets/interfaces.

Если автору нужен новый фундаментальный stat/resource, это `Architecture Change Request`.

Stage 18 создаёт **minimum complete economic language**; Stage 22 создаёт широкий playable vocabulary внутри него.

Фракционная асимметрия следует той же логике:

```text
shared world + shared authorities
→ institutions / policy / industrial structure / engineering choices
→ different procurement and fleet solutions
→ different physical costs and vulnerabilities
```

Не допускается `factionName → hidden production/combat/sensor multiplier` как основа идентичности.

---

# 2. Входные условия

До основного Stage 22 должны быть стабильны:

- Stage 17.5 production fitting/combat foundation;
- Stage 18 resources/industry/infrastructure foundation;
- Stage 19 strategic warfare/coercive diplomacy/advanced combat behavior;
- Stage 20 physically calibrated world generation;
- Stage 21 NPC/missions/reputation/living-world mechanics;
- Stage-21I core-pair decision/corpus acceptance;
- v1.0 schema and capability APIs;
- real construction/refit/repair/maintenance seams;
- baseline industrial resource/component/facility graph.

Stage 22 может получать early content prototypes раньше, но массовая балансировка не должна строиться поверх временных mechanics.

Stage 22.0 обязан отдельно проверить, какие существующие generated-world stable faction IDs являются
runtime compatibility identities, и не переименовывать/переосмысливать их без migration.

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

Минимально покрыть роли:

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

Stage-22 product floor does not require every listed role to receive a unique faction hull in both
core factions. The faction package floor remains six military + three civilian/support base hulls per
core faction, supplemented by shared/licensed hulls where the market and manufacturers justify it.

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

# 12. Stage 22I — faction engineering and industrial doctrines

Stage-22 production acceptance обязана различить **Империю** и **Индустриальный Союз** через content choices and industrial behavior, а не faction magic bonuses.

## Империя — required systemic direction

- heavy, serviceable axial engineering;
- redundancy and protected central citadel;
- long service life and refit continuity;
- strategic reserves and state procurement;
- mature support/repair network;
- willingness to carry higher mass/cost/maintenance footprint for survivability and continuity.

## Индустриальный Союз — required systemic direction

- standardized platform/component families;
- repeated production series;
- high bulk throughput;
- strong replacement/logistics culture;
- production specialization and common spare families;
- strategic resource hunger and vulnerability to concentrated bottlenecks/retooling.

For both factions, doctrine defines design preferences, procurement, fleet composition and industrial
investment. A bounded commander/policy preference may influence ranking; it may not rewrite fundamental
physics or materialize output.

If Stage-18 current manufacturing authority cannot express repeated-series specialization for the
Industrial Union, Stage 22 may introduce the **minimum reusable common extension** after explicit code
and authority audit. An `IndustrialUnionProductionSystem` is prohibited.

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
- repair capability;
- production-series/changeover capability **only if the Stage-22.0/22.2 authority audit proves a common missing seam**.

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

Core-pair balance должен отдельно проверить:

- Imperial repair/refit continuity versus its higher capital/logistics burden;
- Industrial Union replacement throughput versus its resource/route/retooling dependence;
- отсутствие бесплатного восстановления capability у обеих сторон.

---

# 15. Stage 22L — fleet composition and doctrine balance

Проверять не 1v1 ships только, а fleet systems.

Required archetypal fleet roles across the combined catalog:

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

Pairwise acceptance must show two viable fleet ecosystems, not one globally optimal doctrine wearing two palettes.

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
- shipyard component supply;
- Imperial reserve/repair network pressure;
- Industrial Union bulk-flow/series-production pressure.

Distance должен создавать measurable economic geography.

At least one pairwise fixture must demonstrate that route disruption hurts the two core factions through
different **real dependency graphs**, not through different remote debuffs.

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
- precision-component deadlocks;
- Imperial reserve hoarding that starves ordinary economy without policy reason;
- Industrial Union runaway production or impossible retooling lock-in.

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
- logistics fit vs combat fit;
- Imperial durability/serviceability vs mass/cost;
- Industrial Union commonality/throughput vs flexibility/bottleneck exposure.

---

# 20. Stage 22Q — anti-linear-tier-obsolescence matrix

Technology tiers должны создавать niches.

Проверить:

- old/common equipment дешевле/repairable/available;
- advanced equipment требует rarer components/facilities;
- high-performance components могут быть hotter/complex/expensive;
- smaller hulls сохраняют useful roles;
- faction industrial base влияет на viable technology;
- Imperial modernization can keep mature hulls useful without making old equipment universally best;
- Industrial Union standard families can remain economical without forbidding specialized alternatives.

Полная линейная замена допустима для отдельных mature components, но не должна автоматически превращать всю игру в `highest tier = only rational choice`.

---

# 21. Stage 22R — core-faction differentiation acceptance

Production-complete acceptance выполняется для **двух core factions**: Империи и Индустриального Союза.

Для каждой core faction сгенерировать reference fleet, industrial support and recovery/replacement chain.

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
- procurement/maintenance/replacement behavior;
- lawful strategic decisions from Stage-21 evidence.

Но обе factions используют одну физику и одну Stage-18 material/economic grammar.

Mandatory pairwise proofs:

1. silhouette recognition without color/heraldry;
2. different viable engineering/fleet solution under the same physical rules;
3. different industrial/logistics pressure under at least one shared world condition;
4. at least one shared world condition where both choose the same rational response;
5. no hidden faction production/combat/sensor modifier;
6. no dominant faction across economy + warfare + recovery metrics;
7. player access to both ecosystems through real markets/relations/industry.

The five post-core factions are referenced only as architecture-compatibility horizons and are not
required fixtures for Stage 22R.

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

Core-pair content must support meaningful player access to both Imperial and Industrial Union markets
without making faction choice a menu-only unlock.

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
- long-run economy metrics;
- core-pair reference fleets and industrial-support fingerprints.

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
8. нужен ли он обеим core factions или как минимум остаётся reusable для player/future factions?
9. является ли request на самом деле post-core requirement, который не должен расширять Stage 22?

Если это действительно fundamental new axis — создать explicit architecture proposal + benchmarks + regression, а не добавлять hidden field.

Потребности пяти horizon factions не являются основанием prematurely добавить private-economy/debt/mobile-industry/etc. в Stage 22, если core pair и player gameplay этого не требуют.

---

# 25. Stage 22 completion gate

Stage 22 COMPLETE, когда:

- content catalog достаточно широк для alpha;
- **Империя и Индустриальный Союз production-complete как две sovereign core faction packages**;
- major technology families имеют meaningful tradeoffs;
- Stage-18 resource/industry graph выдерживает expanded content без hidden supply;
- core factions различимы через engineering/economy/institutional doctrine;
- civilian + military roles имеют viable designs;
- no universal dominant fit;
- no automatic large-hull obsolescence of small hulls;
- high-tier production имеет реальные resource/component/facility bottlenecks;
- ammunition/reaction mass/repair logistics работают в long-run;
- Imperial repair/reserve model and Industrial Union series/throughput model have measurable benefits and costs;
- Stage-20 world-scale economy стабильна на representative seeds;
- strategic wars создают replacement/economic consequences;
- save/load/soak остаются bounded;
- post-core factions remain architecturally unblocked but are **not required to be implemented**;
- full CI + long-run benchmark gates green.

---

# 26. Итоговый Stage 22 invariant

```text
technology/content/institutional choice
→ changes physical/component/production capability
→ changes Stage-18 material/component/facility requirements
→ changes fitted ship budgets or lawful industrial workflow
→ changes movement/signature/combat/endurance/throughput
→ changes construction/maintenance/logistics cost
→ changes fleet doctrine and Stage-20 economic geography
```

Если technology, module или faction feature минует эту цепочку и просто добавляет abstract bonus, он нарушает accepted design baseline.

---

# 27. Stage-22 production sequence

Буквенные work packages 22A–22T задают полный scope, но не означают, что двадцать независимых
каталогов следует писать параллельно. Канонический порядок поставки:

## 22.0 — content inventory, faction identity and governance gate

- machine-readable inventory всех существующих content IDs и обратных ссылок;
- решение `PROMOTE / REAUTHOR / REPLACE / RETIRE` для каждого Stage-17.5/19 provisional ID;
- audit current generated-world stable faction IDs and display names;
- explicit disposition/migration mapping for the Imperial and industrial runtime lineages;
- mark the five canonical horizon factions as **reserved post-core concepts**, not Stage-22 packages;
- classify legacy `neutral` / `trade_league` / `miners` actors as minor/transnational/test/runtime organizations where appropriate rather than silently treating them as sovereign peers;
- schemas/manifests для art, NPC, mission, localization, VFX/audio bindings;
- automated validation и semantic fingerprint policy;
- утверждённые core-pair alpha floors и cut priority.

Без 22.0 запрещено массово генерировать ассеты или переименовывать stable content/faction IDs.

## 22.1 — Imperial gold slice

Закрывает одну faction package end-to-end:

- 22A–K engineering/industry definitions, необходимые её reference roster;
- минимум шесть военных и три civilian/support base hulls;
- три signature station variants;
- doctrine fits, reference fleet, market/progression access и replacement chain;
- шесть recurring NPCs, десять mission templates и две короткие faction chains;
- production sprites/characters/icons/VFX/audio subset;
- peaceful, crisis, battle, loss, recovery и save/load acceptance.

Визуальная основа — принятый код «Империи»: тяжёлая ремонтопригодная осевая инженерия,
центральная цитадель, сдержанная иерархия, graphite/ivory/burgundy/brass и отсутствие fantasy decor.

Systemic authority: `docs/factions/empire_systemic_identity.md`.  
Visual authority: `docs/factions/empire_visual_bible.md`.  
Character authority: `docs/characters/character_master_prompt.md`.

## 22.2 — Industrial Union contrast slice

Вторая core faction **зафиксирована**: Индустриальный Союз.

Package end-to-end:

- exact systemic/political/industrial profile and stable-ID mapping;
- production-series/commonality authority audit and only the minimum reusable extension if current Stage-18 seams are insufficient;
- минимум шесть military и три civilian/support base hulls;
- три signature station variants;
- standardized doctrine fits, reference fleet and logistics train;
- physical material/component/route dependencies and replacement chain;
- visual bible, sprites/characters/icons/VFX/audio subset;
- шесть recurring NPCs, десять mission templates и две короткие faction chains;
- peaceful, crisis, battle, loss, bottleneck disruption, replacement and save/load acceptance.

Pairwise acceptance against the Empire must prove:

- различимый силуэт без цвета и герба;
- другую viable engineering/fleet solution в одной физике;
- иные lawful procurement/industrial choices without scripted personality bonuses;
- реальную торговую/ресурсную зависимость, conflict incentives and counterplay;
- measurable standardization benefit and retooling/bottleneck cost;
- отсутствие одного доминирующего fit/faction across combined metrics.

Systemic authority: `docs/factions/industrial_union_systemic_identity.md`.  
Visual authority: `docs/factions/industrial_union_visual_bible.md`.  
Character authority: `docs/characters/character_master_prompt.md`.

## 22.3 — shared civilian/minor ecosystem and cross-market integration

После core pair не создаются ещё три sovereign packages.

Scope:

- lawful shared/licensed civilian hulls and components where manufacturers/markets justify them;
- political role audit for legacy `neutral`, `trade_league`, `miners` runtime actors;
- contracts, market access, extraction, arbitration, convoy and independent-settlement content;
- shared repair/refit/service infrastructure;
- cross-faction module/industry availability;
- peaceful/economic careers with content density comparable to military play;
- special locations, regional manufacturers and service-history variants.

Legacy organizations may receive reduced identity/contact packages sufficient for runtime clarity; they
are not promoted into additional production-complete sovereign factions by this Stage.

## 22.4 — core-pair combined alpha balance

- 22L–R fleet/combat/logistics/economy/core-faction matrices;
- 22S progression/market access;
- 22T benchmark/fingerprint governance;
- NPC/mission/location distribution and repetition audit;
- final decision for every provisional ID;
- representative-seed pairwise economic and war/recovery soak;
- explicit finite list of remaining prototype visuals for Stage 23;
- post-core compatibility checklist confirming no Stage-22 hardcode makes the five horizon factions impossible.

There is no Stage-22 work package for production-complete Directorate/League/Frontier Confederation/
Consortium/Nomad Fleet. Their implementation begins only after the main core stage is complete.

---

# 28. Content breadth authority

Подробный сквозной production plan для factions, ships, stations, characters, missions, locations,
UI art, VFX, audio, localization, manifests, quotas и cut rules:

`docs/content_production_plan_stage21_23.md`.

Faction roster/horizon authority:

`docs/factions/faction_roster_and_development_horizon.md`.

Faction gameplay/visual/counterplay contract:

`docs/factions/faction_gameplay_visual_balance_bible.md`.

Detailed execution and evidence gates:

- `docs/factions/faction_implementation_roadmap.md`;
- `docs/factions/faction_balance_validation_framework.md`.

При конфликте:

- physical/economic параметр и manufacturability определяются Stage-17.5/18/22 contracts;
- faction roster/core-vs-horizon scope определяется faction roster contract;
- faction/asset/narrative production workflow определяется content production plan;
- world behavior, knowledge и mission completion определяются Stage 21;
- release packaging/accessibility/recovery определяются Stage 23.

---

# 29. Stage-22 PR/workstream decomposition

Recommended reviewable sequence:

1. inventory/provisional disposition schema;
2. stable faction-ID/display-name migration/disposition audit;
3. content/asset/localization manifest validation;
4. Imperial systemic doctrine + roster lock;
5. 22A material/component production catalog;
6. 22B–D power/propulsion/thermal Imperial gold-slice families;
7. 22E–G sensor/EW/weapon/protection Imperial gold-slice families;
8. 22H Imperial hulls/fits and physical anchors;
9. 22J–K yard/facility/cost/replacement chain;
10. Imperial visuals/characters/missions/runtime binding;
11. Imperial reference fleet/campaign acceptance;
12. Industrial Union systemic package + production-series/commonality authority audit;
13. Industrial Union engineering/hulls/stations/content package;
14. Industrial Union visuals/characters/missions/runtime binding;
15. core-pair fleet/industry/logistics/recovery acceptance;
16. shared civilian/minor organization ecosystem;
17. special locations/events/full mission breadth;
18. 22L–M fleet/saturation matrices;
19. 22N–O world logistics/macro soak;
20. 22P–S anti-dominance/core-faction/progression closure;
21. 22T fingerprint/performance baselines;
22. Stage-22 alpha completion record and Stage-23 handoff manifest.

Post-core faction packages are intentionally absent from this sequence.

---

# 30. Quantified alpha acceptance floor

Stage 22 не закрывается одним большим каталогом. Минимальный product-level floor:

- **two reviewed production-complete sovereign core-faction packages: Империя + Индустриальный Союз**;
- **zero required production-complete post-core faction packages**;
- two production visual bibles with silhouette/material/character/UI rules;
- six signature military + three faction civilian/support base hulls per core faction (**12 military + 6 faction civilian/support total**);
- at least eight neutral/licensed/shared civilian hulls across ordinary markets;
- ten functional station exterior roles across the combined game and three signature variants per core faction (**6 signature faction station variants total**);
- at least twelve recurring named core-faction NPCs (**6×2**), plus enough shared/minor/independent contacts to cover required Stage-22 civilian gameplay without forcing another sovereign package;
- at least twenty mechanically distinct mission templates across the two core faction packages (**10×2**) plus shared mission breadth sufficient to reach the final game-wide mission library target defined by the content production plan;
- four authored core-faction story chains (**2×2**);
- twenty special-location archetypes unless later cut by an explicit product-scope review;
- RU source copy and complete EN localization path;
- production-valid art/metadata for every alpha-facing definition;
- no unresolved provisional content decision;
- combined combat, economy, logistics, progression, save and long-run acceptance;
- explicit post-core architecture compatibility review with no requirement to author those factions' production content.

Количество является floor покрытия, а не KPI наполнения. Дубликат существующего role, filler text
или paint-only variant не засчитывается как новая механическая единица.
