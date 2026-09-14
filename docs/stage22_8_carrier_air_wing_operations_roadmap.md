# Star Empires — M22.8 Carrier Air Wing / Small Craft Operations roadmap

> **Статус:** PLANNED — не начинать реализацию до полного закрытия и merge M22.7.  
> **Позиция:** `M22.7 Integrated Campaign Handoff → M22.8 Carrier Air Wing / Small Craft Operations → Stage 23 Polish / RC`.  
> **Назначение:** превратить уже предусмотренные Stage-22 carrier/carrier-wing content roles в полноценную физическую, логистическую и тактическую механику без отдельного «движка авианосцев» и без скрытых бесплатных ресурсов.  
> **Scope lock:** одна shared система для player/AI и для обеих core factions; faction identity выражается через lawful content/doctrine/loadout/industry, а не через faction-name multipliers.

Stage-22 content plan уже предусматривает `fleet carrier`, `carrier group`, `carrier aviation stores` и anti-universal comparison `carrier wing vs direct weapons`. M22.8 является operationalization этого ранее намеченного пространства решений, а не разрешением создать параллельную combat/economy authority.

---

# 1. Главный design invariant

Авианосец не является «линкором плюс бесплатные маленькие корабли».

Он должен быть дорогой, логистически тяжёлой, уязвимой на ближней дистанции платформой, которая получает силу через:

- удалённую разведку и actor-bounded sensor picture;
- CAP / interception далеко от основной линии флота;
- мобильные точки пуска guided ordnance;
- многоосевые и синхронизированные атаки;
- reusable small-craft platforms;
- возможность менять loadout между вылетами;
- control of space и reaction-time advantage;
- повторное использование уцелевших аппаратов вместо превращения каждого вылета в одноразовый боеприпас.

Цена этих преимуществ:

- hangar/storage/service mass and volume;
- finite propellant, ordnance, countermeasures and service inputs;
- finite launch/recovery/service throughput;
- необходимость escort/screen;
- уязвимость к EW, dedicated point defence, interceptors и fast raiders;
- дорогие persistent losses;
- sortie turnaround;
- зависимость от physical replacement and resupply chain.

Ключевое правило баланса:

> **Guided munition должна оставаться более эффективной одноразовой доставкой боевой части. Small craft оправдывает массу и стоимость гибкостью, повторным использованием, разведкой, сопровождением, изменением цели и управлением геометрией боя.**

Если reusable craft одновременно дешевле, быстрее, мощнее и не имеет operational/logistics cost по сравнению с missile, это balance defect.

---

# 2. Authority boundaries

M22.8 обязан переиспользовать существующие authorities:

- Stage 15 fleet identity / ownership / orders / movement;
- Stage 17 treasury, policy, territory, diplomacy and legal access;
- Stage 17.5 mass/volume/power/heat, propulsion/reaction mass, sensors/tracks/datalink/EW, weapons/ammunition, damage, repair/refit and tactical materialization;
- Stage 18 resources, components, manufacturing, storage, logistics, shipyard/repair/replacement;
- Stage 19 tactical AI, combat execution, guided ordnance, PD/interceptor/EW and fixed-tick determinism;
- Stage 20 physical geometry, travel distances, materialization/LOD and resource geography;
- Stage 21 readiness, strategic operations, faction decisions, recovery/replacement and actor-bounded knowledge;
- M22.7 campaign coordinator, production launcher, composed persistence and asset/content resolver.

Запрещено создавать:

- `CarrierCombatEngine`, который параллельно рассчитывает другую физику;
- hidden carrier ammunition pools;
- automatic craft respawn after battle;
- faction-only combat multipliers;
- viewer-owned sortie state;
- teleport launch/recovery;
- infinite datalink/control capacity;
- reset fuel/ammunition/damage on tactical materialization or save/load;
- отдельную `aviation economy`, если потребность уже выражается существующими Stage-18 commodities, ammunition definitions, components and repair contracts.

Если существующий authority не способен выразить обязательный параметр, M22.8A должен сначала доказать gap и расширить ближайший общий reusable contract минимально.

---

# 3. Domain model

## 3.1 SmallCraft

Единая mechanical family `SmallCraft` покрывает пилотируемые и автономные аппараты.

Обязательные conceptual fields/capabilities:

```text
stable craft identity / definition identity
control mode: CREWED | AUTONOMOUS
physical mass / volume
propulsion + reaction mass / endurance
signature + sensor package
hardpoints / installed weapons
countermeasure capacity
service class / maintenance burden
hangar compatibility
current damage / readiness / stores
```

Exact Java/config names определяются только после M22.8A repository authority audit; этот документ не разрешает silently reinterpret существующее поле, если у него уже другая семантика.

## 3.2 Persistent craft vs tactical aggregation

Persistent layer должен знать реальные аппараты и реальные потери. Tactical layer может агрегировать несколько craft в `Flight` для производительности.

Рекомендуемая hierarchy:

```text
Air Wing
  → Squadron
      → Flight
          → persistent SmallCraft instances
```

Baseline authoring target:

- `Flight`: 4 аппарата;
- `Squadron`: обычно 3 flights / 12 аппаратов;
- размер wing определяется конкретным carrier hull, hangar capacity, service throughput и doctrine, а не фиксированным глобальным числом.

`Flight` — optimization/materialization entity, а не источник истины о количестве аппаратов.

## 3.3 Минимальный production role set

Первая production-complete версия обязана иметь минимум четыре mechanical roles:

1. **Fleet Interceptor** — air superiority, escort, limited missile interception;
2. **Strike Fighter** — доставка anti-ship guided ordnance и ограниченная самооборона;
3. **Interceptor Drone** — более высокая допустимая манёвренность/attrition tolerance, но большая зависимость от datalink/autonomy policy;
4. **Recon / EW Drone** — удалённый sensor node, track support, jamming/deception.

Дополнительные shuttle/boarding/repair/stealth/suicide roles являются future content и не блокируют M22.8, если не требуются уже существующими authored contracts.

---

# 4. Crewed fighter vs autonomous drone

Различие должно возникать из общих физических и информационных правил.

## Crewed craft advantages

- лучшее действие при degraded/lost datalink;
- более богатый local decision envelope;
- меньшее падение mission effectiveness при сложной неоднозначной обстановке;
- способность продолжать/изменять mission по локальным наблюдениям в пределах doctrine.

## Crewed craft costs

- cockpit/life-support/safety mass and volume;
- ограничение допустимого acceleration profile;
- crew training/replacement cost;
- более высокая экономическая цена потери.

## Autonomous drone advantages

- меньшая crew burden;
- выше допустимые acceleration/manoeuvre envelopes там, где это следует из физики конструкции;
- lower loss externality for personnel;
- подходит для более рискованных interception/decoy/EW roles.

## Autonomous drone costs

- datalink/control bandwidth pressure;
- degraded coordination under EW;
- более узкая autonomous fallback policy;
- необходимость fail-closed target-identification rules.

Lost link не означает instant shutdown. Минимальная state chain:

```text
NETWORKED
→ DEGRADED_LINK
→ AUTONOMOUS_FALLBACK
```

Fallback должен опираться только на locally known/allowed information. Нельзя давать дрону omniscient world-state read после потери связи.

Одноразовый craft, спроектированный как kamikaze/loitering munition, архитектурно должен рассматриваться как `GuidedMunition`, если его gameplay role фактически совпадает с ракетой.

---

# 5. Расходники и обязательная привязка к Stage-18 economy

Сам reusable fighter/drone **не является расходником**. Он persistent manufactured asset до момента физического уничтожения.

M22.8 **не вводит отдельную авиационную экономику по умолчанию**. Operational UI может показывать агрегированные категории `Propellant`, `Aviation Ordnance`, `Countermeasures`, `Service Supplies`, но authoritative quantities обязаны разрешаться в уже существующие физические Stage-18 commodities, ammunition definitions, components and repair inputs.

Текущий Stage-18 audit уже доказывает следующие reusable seams:

- reaction mass interface и commodity binding уже существуют; baseline пример связывает `REACTION_MASS` с `commodity.material.purified_water`;
- `manufacturing.profile.guided_ammunition` и `manufacturing.profile.kinetic_ammunition` уже производят физические ammunition items из Stage-18 materials/components;
- guided ammunition уже имеет roles `STRIKE`, `INTERCEPTOR` и `DECOY` в production/test content lineage;
- repair/maintenance уже расходуют конкретные `INDUSTRIAL_CHEMICALS`, engineering materials и `HEAVY/ELECTRICAL/PRECISION_COMPONENTS`, поэтому новый универсальный commodity `SPARE_PARTS` не требуется без отдельного доказанного gameplay need;
- Stage-18 finished-product policy уже предусматривает `Drone Definition` как производимый finished industrial product, поэтому SmallCraft manufacturing должен расширить этот общий seam, а не создавать отдельную фабрику «авиационных очков».

## 5.1 Propellant / reaction mass

Для каждого small-craft drive M22.8 authoring задаёт обычный consumable binding через существующий reaction-mass interface.

Допустимый пример:

```text
small-craft drive
→ REACTION_MASS interface
→ commodity.material.purified_water
```

если конкретная propulsion technology действительно совместима с водой.

Другой drive может использовать другой уже существующий или technology-specific commodity только при физическом/логистическом основании. Запрещён универсальный `FIGHTER_FUEL`, если он лишь дублирует существующую Stage-18 commodity chain.

## 5.2 Aviation ordnance

`Aviation Ordnance` — **UI/logistics category, а не единый commodity**.

Внутри неё находятся реальные ammunition definitions:

```text
interceptor missile
anti-ship missile
torpedo
gun ammunition where applicable
```

Они производятся через существующие kinetic/guided ammunition manufacturing profiles и хранятся/перемещаются как physical inventory. Авиационная версия боеприпаса может получить отдельную content definition/loadout compatibility, но не получает бесплатный или скрытый production path.

## 5.3 Countermeasures

`Countermeasures` также являются **категорией реальных expendable definitions**, а не абстрактными очками.

Existing `DECOY` guided-ammunition lineage является доказанным reusable seam. M22.8 может авторить нужные варианты:

```text
radar repeater decoy
active jammer decoy
thermal / optical decoy
other setting-consistent expendable countermeasure
```

только если различие создаёт meaningful sensor/EW gameplay.

Такие items используют ordinary ordnance/manufacturing/storage rules и при необходимости физически материализуются через common guided-body authority.

## 5.4 Service Supplies вместо generic Spare Parts

Carrier UI может показывать агрегат `Service Supplies`, но authoritative ремонт и обслуживание small craft должны расходовать конкретные существующие inputs, например:

```text
INDUSTRIAL_CHEMICALS
HEAVY_COMPONENTS
ELECTRICAL_COMPONENTS
PRECISION_COMPONENTS
LIGHT_ALLOY
STRUCTURAL_ALLOY
REFRACTORY_ALLOY
other existing material only when actual repair profile requires it
```

Конкретный набор определяется повреждённой subsystem/content definition через ordinary repair/service contracts.

**Не создавать `commodity.spare_parts` только ради M22.8.** Новый отдельный spare-parts commodity допустим только через explicit architecture/content review, если будет доказано, что его отдельное производство, хранение или логистика создают meaningful gameplay decision, которое нельзя выразить существующими component families.

## 5.5 Energy / recharge

Энергия не становится авиационным consumable item. Recharge использует обычные carrier/reactor/electrical/thermal authorities и simulation time.

Если конкретная technology физически требует transported energy storage/fuel cartridge, это отдельный technology/content decision и не является общим правилом M22.8.

## 5.6 Replacement airframes

Уничтоженный fighter/drone не ремонтируется из `Service Supplies` и не восстанавливается автоматически.

Replacement craft является отдельным manufactured product:

```text
Stage-18 materials/components
+ required fabrication/assembly capabilities
+ SmallCraft manufacturing profile / product binding
+ work / energy / time
→ new persistent SmallCraft instance
```

M22.8 обязан добавить production bindings/recipes для production small-craft definitions поверх существующей Stage-18 manufacturing architecture.

## Hard logistics rules

- no free refuel/rearm on recovery;
- no repair from nothing;
- destroyed craft require newly manufactured/replacement airframe;
- damaged craft consume ordinary repair work/materials/components;
- ordnance/countermeasure consumption reaches real manufacturing/storage/logistics demand;
- aggregated UI categories never become separate hidden inventories;
- campaign save/load preserves exact underlying commodities, ammunition items, craft state and queues.

---

# 6. Propellant and safe-return reserve

Craft mission planner обязан рассчитывать минимум:

```text
launch cost
+ outbound transit
+ planned manoeuvre/combat budget
+ return transit
+ recovery manoeuvre
+ doctrine reserve
```

Default doctrine должна иметь safe-return reserve. Точная доля не hard-coded этим документом и определяется calibration tests.

Игрок/AI может разрешить emergency continuation ниже нормального reserve только как явный risk decision. Результат может быть:

- DIVERT;
- recovery by alternate carrier/base;
- stranded/lost craft, если физического варианта возврата нет.

Нельзя телепортировать аппарат домой после окончания tactical battle.

---

# 7. Ordnance philosophy

## Interceptors

Небольшой finite loadout для:

- hostile small craft;
- selected incoming missiles/torpedoes;
- limited self-defence.

Они не заменяют ship PD. Идея defence-in-depth:

```text
remote sensors
→ CAP/interceptors
→ escort screen
→ ship PD / local defence
```

## Strike craft

Основной anti-ship damage приходит от carried guided ordnance, а не от бесконечного gun DPS по capital hull.

Strike craft ценен тем, что:

- переносит point of launch;
- приближается к цели до release;
- формирует другой attack vector;
- может abort/retarget/return;
- участвует в разведке/escort между strikes.

Heavy anti-ship ordnance остаётся физически дорогим, объёмным и ограниченным.

---

# 8. Hangar operational capability

M22.8A должен найти текущий hangar/carrier authoring и сохранить существующую семантику. При необходимости reusable hangar runtime должен выражать минимум:

```text
stored mass capacity
stored volume capacity
maximum compatible craft mass/geometry
ready slots
launch throughput
recovery throughput
service slots / throughput
stores capacity / access
health / subsystem availability
```

Два carriers с одинаковой nominal wing capacity могут различаться operationally:

- быстрый launch, но слабый turnaround;
- медленный launch, но мощное обслуживание;
- больше internal storage, но меньше ready positions;
- redundancy vs peak throughput.

Это создаёт engineering tradeoff вместо hidden carrier-class bonus.

---

# 9. Sortie lifecycle

Обязательная deterministic state machine:

```text
STOWED
→ ARMING_REFUELING
→ READY
→ LAUNCH_QUEUE
→ LAUNCHING
→ TRANSIT
→ MISSION
→ EGRESS
→ RECOVERY_QUEUE
→ RECOVERING
→ TURNAROUND
→ READY
```

Дополнительные states:

```text
DAMAGED
ABORT
DIVERT
STRANDED
DESTROYED
```

Transitions зависят от simulation time, real throughput, distance, damage, stores, mission order and legal access. Render FPS/wall-clock не имеют authority.

Launch не должен instant-spawn whole air wing вокруг carrier. Recovery также занимает physical/operational time.

---

# 10. Turnaround / maintenance

После recovery craft не становится READY мгновенно.

Turnaround включает:

```text
inspection
→ refuel
→ rearm
→ countermeasure replacement
→ minor maintenance/repair
→ readiness validation
```

Damaged craft может перейти в extended maintenance/repair.

Carrier readiness UI и AI должны различать:

- total aboard;
- ready;
- airborne;
- turnaround;
- damaged/repairing;
- lost/destroyed.

Количество physical airframes и количество combat-ready craft — разные показатели.

---

# 11. Hangar damage and carrier loss

Damage не должен иметь только binary `works / destroyed` semantics, если существующий subsystem model позволяет graded capability.

Hangar/launch/recovery/service damage может снижать:

- launch throughput;
- recovery throughput;
- ready slots;
- service throughput;
- accessible stores.

Конкретная curve авторится/calibrates в M22.8 tests, но instant deletion всего wing от одного compartment hit запрещён без физически обоснованной magazine/catastrophic damage chain.

Если carrier теряет recovery capability при airborne wing:

```text
DIVERT_REQUIRED
→ compatible friendly carrier
→ compatible station/base
→ emergency fallback
→ physical loss when no reachable option remains
```

Никакого despawn/teleport recovery.

---

# 12. Mission set

M22.8 production floor:

## CAP — Combat Air Patrol

- patrol designated defensive volume;
- engage hostile small craft;
- opportunistic/lawful missile interception;
- preserve enough fuel for recovery.

## INTERCEPT

- launch against detected threat;
- prioritize time-to-impact / threat value;
- break off when interception no longer feasible or return reserve requires it.

## ESCORT

- protect strike/recon/EW group;
- do not abandon protected package for low-value targets without doctrine reason.

## ANTI_SHIP_STRIKE

- ingress;
- release from valid geometry/range;
- optional multi-axis split;
- egress/recovery.

## RECON

- extend sensor coverage;
- build/refresh tracks;
- remain actor-bounded;
- avoid becoming free perfect targeting.

## EW_SUPPORT

- jamming/deception/track support through Stage-17.5/19 EW authority;
- finite power/heat/position/risk consequences;
- no abstract flat `-25% enemy damage` shortcut.

---

# 13. Carrier battle doctrine

Carrier AI обязан рассматривать себя как standoff asset.

## Positioning

Utility position должен учитывать:

```text
enemy effective direct/guided threat envelope
+ closing distance during reaction time
+ safety margin
< friendly small-craft operational radius
```

Carrier старается держать противника внутри reach авиакрыла, но вне выгодной enemy direct-fire geometry.

## Screen

Preferred formation concept:

```text
recon / pickets
→ CAP
→ escort / air-defence screen
→ carrier
→ planned retreat / recovery vector
```

Carrier alone должен быть materially weaker than a carrier battle group.

## QRA

Часть craft может находиться в `READY` reserve для Quick Reaction Alert. Doctrine выбирает компромисс между:

- airborne CAP;
- ready reserve;
- strike package;
- service/maintenance reserve.

Никакой один фиксированный процент не является universal truth; baseline percentages задаются calibration content и могут меняться по threat model.

---

# 14. Strike package and synchronized attack

Полноценная carrier strike operation:

```text
recon / target confirmation
→ EW / deception preparation
→ fighter sweep / escort
→ strike ingress
→ guided ordnance release
→ synchronization with ship-launched salvo where doctrine permits
→ multi-axis saturation
→ egress
→ recovery
```

Сила carrier strike заключается в geometry/timing, а не в hidden damage bonus.

AI должен уметь оценивать:

- track quality;
- target value;
- enemy CAP;
- PD/interceptor capacity;
- estimated penetration;
- expected craft loss;
- ordnance expenditure;
- carrier exposure during launch/recovery.

Не каждая обнаруженная цель заслуживает strike.

---

# 15. Small-craft / missile / direct-weapon niches

M22.8 balance matrix обязана сохранить разные роли.

| System | Primary strength | Primary cost / weakness |
| --- | --- | --- |
| Reusable small craft | flexibility, remote presence, recon, retargeting, repeated sorties | carrier/hangar/service/logistics/loss risk |
| Autonomous drones | high manoeuvre tolerance, no crew loss, risky roles | datalink/autonomy/EW limits |
| Guided missiles/torpedoes | efficient one-way warhead delivery, high terminal performance | single-use, finite magazines |
| Kinetic direct fire | sustained physical fire, relatively simple ammunition economy | geometry/track/range limitations |
| Beam / laser | fast response, precision/PD utility | power/heat/aperture/pointing burden |
| Ship PD/interceptors | terminal defence | limited local envelope and finite channels/ammunition |

Required anti-dominance question:

> При одинаковой industrial burden существует ли осмысленная ситуация, где commander выберет guided salvo/direct-fire fleet вместо carrier-heavy solution — и наоборот?

Если ответ «нет», M22.8 balance gate не пройден.

---

# 16. Target selection and subsystem attack

Strike AI не должен ранжировать только global HP.

Допустимые objectives через существующую subsystem damage authority:

- propulsion / mobility kill;
- sensors/fire control;
- missile launchers / magazines;
- PD/air-defence capability;
- hangar/launch/recovery infrastructure;
- command/communications where physically authored.

Mission kill должен быть legitimate outcome, если target потерял operational capability без полного уничтожения hull.

---

# 17. Carrier vs major threats

## Carrier vs line battleship

- carrier advantage на большой дистанции при хорошей разведке и room to operate;
- battleship advantage резко растёт после прорыва в direct-fire geometry;
- carrier не должен выигрывать честную close-range artillery duel за счёт абстрактной class bonus.

## Carrier vs missile fleet

- больше CAP/interceptor allocation;
- раннее detection/interception;
- small craft прореживают salvos, но terminal PD остаётся обязательным.

## Carrier vs fast raiders

- один из natural counters;
- carrier зависит от screen, pickets and mobility;
- рейдер может выиграть через closure, sensor deception, escort bypass, engine/hangar mission kill.

## Carrier vs carrier

- recon and track quality;
- air superiority;
- EW;
- launch/recovery timing;
- stores/endurance;
- attrition and recovery options.

Бой не должен сводиться к сравнению одного `carrier DPS` stat.

---

# 18. Datalink / autonomy capacity

Если текущие Stage-17.5/19 contracts уже имеют достаточный datalink/channel authority, M22.8 reuse его. Если нет — разрешено минимальное reusable extension после audit.

Нужно выразить bounded coordination capacity, например через existing channels/bandwidth/track-update mechanics.

Over-capacity не должен просто уничтожать craft. Возможные causal consequences:

- slower track updates;
- lower coordination quality;
- transition to local autonomy;
- worse synchronization;
- reduced remote retargeting.

EW ухудшает information/coordination chain, а не выдаёт произвольный damage debuff.

---

# 19. Strategic logistics and industry

Carrier warfare обязана доходить до Stage-18 economy.

Physical chain:

```text
resource/component production
→ SmallCraft manufacturing through ordinary Stage-18 product bindings
→ guided/kinetic aviation ordnance manufacturing
→ physical propellant commodity + real countermeasure ammunition + repair/service components
→ storage and freight
→ carrier/base resupply
→ sortie consumption and losses
→ repair/replacement demand
→ changed readiness / procurement / faction decision
```

Loadout carrier должен быть реальным strategic choice:

- interceptor-heavy defensive package;
- strike-heavy package;
- long-deployment stores-heavy package;
- high-tempo short operation.

### Economic reuse invariant

M22.8 implementation должна прежде всего **bind existing Stage-18 economy**, а не расширять commodity ontology без необходимости:

```text
Propellant UI
→ real compatible Stage-18 commodity via REACTION_MASS binding

Aviation Ordnance UI
→ physical ammunition definitions produced through existing manufacturing profiles

Countermeasures UI
→ physical DECOY / other expendable ammunition definitions

Service Supplies UI
→ aggregation of actual repair/maintenance materials and component families

Replacement Craft
→ manufactured SmallCraft finished product
```

Запрещено создавать отдельные `aviation points`, `fighter ammo points`, generic `SPARE_PARTS` или скрытые carrier-only stocks, если они не соответствуют самостоятельному physical commodity с доказанным gameplay value.

---

# 20. Persistence and materialization

M22.8 state входит в composed campaign save M22.7.

Обязательно сохранять:

- stable small-craft identities where individually persistent;
- air-wing/squadron/flight composition;
- carrier assignment;
- mission/sortie state;
- airborne vs aboard status;
- fuel/reaction mass;
- ammunition/countermeasures;
- damage/readiness;
- launch/recovery/service queues;
- hangar runtime damage/capacity state;
- diversion state;
- relevant datalink/autonomy state;
- replacement/repair orders and physical stores.

Save/load mid-sortie должен продолжать ту же causal mission без respawn/reset/duplicate consumption.

Tactical aggregation/dematerialization обязана commit-back реальные потери, stores, damage and positions through existing combat materialization authority.

---

# 21. UI / player command layer

Добавить production surface **Carrier Operations** или эквивалент в существующую information architecture.

Минимальный read-only/command projection:

```text
AIR WING: total / ready / airborne / turnaround / damaged / lost
MISSION ALLOCATION: CAP / QRA / STRIKE / RECON-EW / RESERVE
AVIATION STORES: propellant / interceptor ordnance / strike ordnance / countermeasures / service supplies
HANGAR STATUS: storage / ready slots / launch / recovery / service throughput
ACTIVE SORTIES: mission / target or patrol area / fuel reserve / ETA / recovery destination
```

`AVIATION STORES` — presentation aggregation only. Inspector/details должны позволять при необходимости раскрыть реальные underlying commodity/ammunition/component quantities; UI aggregate не имеет собственного inventory authority.

Player commands работают через same validators/authorities as AI:

- Maintain CAP;
- Intercept;
- Escort;
- Strike Target;
- Recon Area/Sector;
- EW Support;
- Recall Wing;
- change doctrine/loadout where legal and physically serviceable.

Player не обязан micro-manage every craft. Primary control level — squadron/flight mission orders and doctrine.

Tactical overlay должен уметь показывать bounded operational information:

- CAP/patrol area;
- estimated combat/recovery radius;
- safe-return warning;
- airborne groups;
- recovery queues;
- relevant tracks known to player actor.

UI не раскрывает hidden enemy truth.

---

# 22. AI doctrine presets

Минимальный doctrine set:

- **DEFENSIVE:** strong CAP/QRA, conservative strike threshold;
- **BALANCED:** mixed CAP/strike/reserve;
- **OFFENSIVE:** larger strike package, lower reserve within safety constraints;
- **PRESERVE_WING:** early disengage, high loss aversion;
- **ATTRITION_ACCEPTING:** appropriate for expendable/cheap drone-heavy doctrine, but still bounded by real economy.

Doctrine меняет preferences/thresholds, а не fundamental physics.

Carrier utility inputs минимум:

```text
enemy time-to-threat-range
track quality
wing readiness
air superiority estimate
strike penetration estimate
expected craft losses
aviation stores endurance
hangar health
escort strength
carrier mobility
recovery/divert risk
mission value
```

---

# 23. Tactical aggregation and performance

M22.8 не должен материализовать сотни fully independent high-cost AI entities без profiling evidence.

Baseline strategy:

```text
persistent individual craft
→ grouped tactical Flight actor
→ per-member loss/damage accounting
→ deterministic commit-back
```

Если profiling показывает, что independent craft acceptable для small engagements, это не отменяет requirement иметь bounded scaling path для carrier-vs-carrier / dense missile battles.

No separate simplified physics by fleet size. Optimization допускает aggregation/scheduling/LOD только при сохранении accepted causal outputs and deterministic commit-back.

---

# 24. Initial content floor

M22.8 production floor для core pair:

- минимум по одному carrier-capable production fit/ecosystem для Империи и Индустриального Союза или lawful shared/licensed alternative, если это соответствует already accepted faction package design;
- four shared mechanical small-craft roles from section 3.3;
- faction-specific lawful loadouts/doctrine/industry differences through shared rules;
- production-valid sprites for required small-craft and carried ordnance used by ordinary client;
- readable top-down scale/silhouette under actual tactical camera;
- no requirement производить уникальную модель каждого role для каждой faction, если shared manufacturer/market contract объясняет common platform.

M22.8A audit определяет, какие existing carrier/small-craft concepts можно promote/reuse, а какие являются только visual/provisional references.

---

# 25. Implementation sequence

## M22.8A — authority/content/economy audit + ADR

- inventory current carrier hulls, hangar modules/capabilities, aviation-store fields, guided ordnance, drone/recon content and visual assets;
- audit Stage-18 resource ontology, `REACTION_MASS` bindings, ammunition manufacturing profiles, repair/service inputs and finished-product seams relevant to carrier aviation;
- document exact semantic meaning of existing fields;
- classify every planned carrier consumable as `REUSE_EXISTING_COMMODITY / REUSE_AMMUNITION_DEFINITION / UI_AGGREGATE_ONLY / NEW_CONTENT_BINDING / ARCHITECTURE_CHANGE_REQUIRED`;
- explicitly prove that generic `SPARE_PARTS`, `AVIATION_ORDNANCE` or `FIGHTER_FUEL` commodities are unnecessary unless new evidence shows otherwise;
- identify reusable authorities and real gaps;
- ADR: small craft extends common combat/economy/persistence model;
- define schema/migration needs;
- freeze initial role/content floor.

**Exit:** no unresolved competing-authority/economy question and no planned hidden carrier-only resource pool.

## M22.8B — SmallCraft definitions, manufacturing and compatibility

- shared definition/instance model;
- CREWED/AUTONOMOUS control mode;
- physical budgets and hardpoints;
- hangar compatibility validation;
- Stage-18 SmallCraft manufacturing profile/product bindings using existing materials/components/facilities where possible;
- content fingerprint integration.

**Tests:** valid/invalid compatibility, deterministic loading, manufacturing input conservation, future/corrupt content fail-closed.

## M22.8C — Hangar runtime capability

- storage/readiness/launch/recovery/service capacities;
- subsystem damage integration;
- deterministic queue ordering;
- no instant whole-wing launch/recovery.

**Tests:** throughput, damage degradation, queue determinism, persistence.

## M22.8D — Aviation inventory + Stage-18 economic bindings

- bind small-craft reaction mass to compatible existing Stage-18 commodities through ordinary `REACTION_MASS` interfaces;
- bind interceptor/strike/gun ammunition to physical ammunition definitions and existing kinetic/guided manufacturing profiles;
- author countermeasure definitions by reusing the existing `DECOY`/guided-ordnance seam where applicable;
- express service/maintenance through actual Stage-18 chemicals/materials/components instead of a new generic spare-parts commodity;
- provide UI aggregation for Propellant / Ordnance / Countermeasures / Service Supplies without creating a second inventory;
- real storage, transfer, loading and unloading;
- no free replenishment.

**Tests:** conservation, insufficient stores, correct underlying commodity depletion, refill/rearm/service through ordinary transfer, aggregate-vs-underlying inventory consistency, save/load.

## M22.8E — Sortie lifecycle

- READY/launch/transit/mission/egress/recovery/turnaround;
- abort/divert/stranded/lost;
- safe-return calculation;
- alternate recovery destination.

**Tests:** happy path, abort, carrier loss, no reachable diversion, mid-sortie save/load.

## M22.8F — Flight tactical materialization

- persistent craft ↔ Flight aggregation;
- shared movement/sensors/weapons/damage;
- deterministic per-member loss accounting;
- commit-back without duplication/reset.

**Tests:** 4→3→2 member losses, damage carryover, ordnance/fuel carryover, repeated materialization.

## M22.8G — CAP and interception vertical slice

First production end-to-end acceptance:

```text
carrier
→ ready interceptors
→ launch
→ CAP
→ hostile missile/small-craft threat
→ interception
→ finite expenditure/loss
→ recovery
→ refuel/rearm/repair through real Stage-18 stocks
→ second sortie
```

Это обязательный foundation before strike complexity.

## M22.8H — Strike / escort / multi-axis guided attack

- strike payload;
- escort mission;
- ingress/release/egress;
- target/subsystem selection;
- multi-axis geometry;
- synchronization with ship-launched ordnance through existing tactical authority.

**Tests:** no free warhead, finite ammo, abort/retarget, PD saturation without guaranteed penetration.

## M22.8I — Recon / EW / datalink / autonomy

- remote track contribution;
- datalink capacity/quality using existing authority or minimal reusable extension;
- degraded-link/autonomous fallback;
- EW-caused coordination degradation;
- actor-bounded information.

**Tests:** no omniscience, lost link, EW degradation, safe fail-closed target rules.

## M22.8J — Carrier tactical and fleet AI

- standoff positioning;
- CAP/QRA allocation;
- strike utility;
- escort/screen coordination;
- retreat/recovery logic;
- doctrine presets;
- carrier group vs lone carrier behavior.

**Tests:** battleship closure, missile threat, fast raider, carrier-vs-carrier, damaged hangar.

## M22.8K — Campaign logistics / replacement / faction behavior

- manufacturing and resupply paths;
- replacement airframes through ordinary SmallCraft manufacturing;
- repair/service consumption of real Stage-18 inputs;
- readiness impact;
- Stage-21 faction decision consequences from losses/stores shortages.

**Tests:** attrition without free replacement, convoy loss impacts sorties, component/ordnance shortage has causal effect, recovery after resupply.

## M22.8L — Production UI / command integration

- Carrier Operations projection;
- validated mission commands;
- camera/overlay integration;
- known-information-only enemy display;
- build/content fingerprint diagnostics;
- aggregate aviation-store presentation resolves to inspectable authoritative inventory.

**Tests:** read-only projection purity, invalid command rejection, player/AI shared validation path, no UI-owned inventory.

## M22.8M — persistence / migration / deterministic continuation

- composed campaign schema integration;
- mid-sortie save/load;
- queue/service state;
- damaged/airborne/diverted craft;
- underlying commodity/ammunition/service-input state;
- supported migration and future/corrupt fail-closed behavior.

**Tests:** roundtrip and continuation hash across 1×/8× where presentation is non-authoritative.

## M22.8N — balance / performance / final acceptance

- carrier-vs-direct-weapon anti-dominance;
- carrier-vs-carrier;
- carrier + escorts vs lone carrier;
- missile fleet;
- fast raiders;
- heavy EW;
- damaged hangar;
- carrier destroyed with airborne wing;
- long-run stores/replacement economy;
- dense tactical profiling;
- no M22.6 regression outside explicitly re-opened carrier-related matrices.

**Exit:** accepted balance report delta + Stage-23 handoff update.

---

# 26. Mandatory acceptance matrix

M22.8 cannot be closed by unit tests only.

## A. Persistence / conservation

- no free craft, ammunition, propellant, countermeasures or service inputs after recovery/reload;
- destroyed craft stay destroyed until real replacement is manufactured/transferred;
- same stable craft/wing identities survive supported save/load;
- mid-sortie save/load does not duplicate mission, launch or consumption;
- UI aggregate totals reconcile with authoritative Stage-18 commodities/ammunition/components.

## B. Carrier vs battleship

- carrier has meaningful standoff advantage with good information and room to operate;
- if battleship/line force closes into effective direct-fire geometry, carrier survival falls sharply unless screen/disengagement succeeds.

## C. Carrier vs missile fleet

- CAP reduces incoming pressure but does not make terminal PD irrelevant;
- interceptor stores and sortie tempo are finite.

## D. Carrier vs fast raiders

- lone carrier is vulnerable;
- escort/pickets materially improve survival;
- raiders can mission-kill engines/hangars/sensors through ordinary subsystem rules.

## E. Carrier vs carrier

- recon, CAP, EW, strike timing, recovery and stores affect outcome;
- result is not a scalar `carrierPower` comparison.

## F. Heavy EW

- drone coordination degrades causally;
- crewed craft retain some local effectiveness where doctrine/sensors permit;
- neither side gains hidden information.

## G. Damaged hangar

- launch/recovery/service throughput degrades according to authored capability damage;
- airborne craft can divert;
- one hit does not arbitrarily erase whole wing.

## H. Long-run campaign

- repeated sorties create measurable resupply/repair/replacement demand through ordinary Stage-18 goods;
- convoy/industry disruption reduces readiness through real stocks;
- recovered logistics can restore operations without hidden grants;
- shortage of precision/electrical/heavy components or relevant materials can lawfully reduce maintenance/replacement throughput instead of being masked by generic spare-parts points.

## I. Performance

- representative carrier battle has bounded tick/memory/entity growth;
- aggregation/LOD does not change accepted deterministic outcomes beyond explicitly measured tolerance/contract;
- no render-FPS authority.

---

# 27. Rebalance contract after M22.6 freeze

M22.8 intentionally adds a major combat/logistics capability after M22.6 core-pair freeze. Therefore it must not claim M22.6 evidence still covers the new dimension automatically.

Re-open only the affected evidence:

- carrier wing vs direct weapons;
- missile saturation / PD / interceptor expenditure;
- fleet composition and escort value;
- logistics endurance / replacement burden;
- faction carrier doctrine/loadout where authored;
- relevant B-scenarios whose outcome changes materially because of small craft.

Unrelated frozen content remains unchanged unless M22.8 exposes a real shared-authority defect.

Every numeric/content change outside carrier/small-craft scope requires a documented causal reason and targeted regression.

---

# 28. Stage-23 handoff

Stage 23 starts only after M22.8 is COMPLETE and merged.

M22.8 handoff manifest must contain:

- final small-craft/hangar/store schemas and fingerprints;
- carrier doctrine/content roster;
- Stage-18 economy binding map for propellant, ordnance, countermeasures, service inputs and SmallCraft manufacturing;
- persistence/migration version;
- production UI/command surfaces;
- accepted balance delta report;
- tactical/performance baselines;
- list of remaining presentation-only polish for Stage 23E/F;
- no unresolved mandatory simulation/economy seam.

Stage 23 may improve:

- final sprites/VFX/audio/animation;
- information architecture/usability;
- tutorial/onboarding;
- performance after profiling;
- save recovery UX;

но не должен впервые implement core sortie causality, free-resource fixes, missing economy bindings or missing persistence.

---

# 29. Explicit non-goals / future extensions

Не являются M22.8 blockers, если не нужны accepted core content:

- individual pilot XP/personality/ace system;
- detailed ejection/rescue simulation;
- boarding craft gameplay;
- stealth-specialized small craft;
- dozens of craft classes;
- per-component maintenance UI for every fighter;
- manual deck choreography;
- cinematic landing animation;
- suicide craft as a separate reusable-airframe family;
- post-core-faction unique carrier mechanics.

Будущие extensions обязаны оставаться поверх shared M22.8 authority.

---

# 30. M22.8 completion gate

M22.8 COMPLETE только когда одновременно:

- [ ] M22.7 accepted, merged, and resulting `main` verified;
- [ ] authority/content/economy audit and ADR accepted;
- [ ] SmallCraft uses shared Stage-17.5/18/19 physical/combat/economy authorities;
- [ ] planned carrier stores are mapped to real Stage-18 commodities/ammunition/components or explicitly justified new content bindings;
- [ ] no generic `FIGHTER_FUEL`, `AVIATION_ORDNANCE`, `SPARE_PARTS` or hidden carrier-only resource pool exists without explicit architecture review and demonstrated gameplay need;
- [ ] SmallCraft replacement airframes have ordinary Stage-18 manufacturing recipes/product bindings;
- [ ] real finite craft identities/losses exist without automatic replacement;
- [ ] hangar launch/recovery/service throughput is finite, damage-aware and persistent;
- [ ] propellant, ordnance, countermeasures and service inputs are physical/conserved;
- [ ] deterministic sortie lifecycle including abort/divert/loss exists;
- [ ] CAP/interception vertical slice passes end-to-end;
- [ ] strike/escort/recon/EW missions use actor-bounded information;
- [ ] drones have degraded-link/autonomous fallback without omniscience;
- [ ] carrier tactical AI maintains standoff, screen, CAP/QRA, strike and retreat behavior;
- [ ] ordinary fleet/industry/logistics consequences reach Stage-21 strategic decisions;
- [ ] Carrier Operations UI and player commands use shared validators and do not own a parallel inventory;
- [ ] mid-sortie composed save/load and persistence migration pass;
- [ ] carrier-vs-battleship/missile/raider/carrier/EW/damaged-hangar acceptance matrix passes;
- [ ] long-run resupply/repair/replacement economy shows no hidden grants;
- [ ] carrier-vs-direct-weapon anti-dominance balance gate passes;
- [ ] dense tactical/performance baselines are recorded;
- [ ] affected M22.6 balance evidence is re-run and accepted;
- [ ] exact PR head required CI is green;
- [ ] PR merged and resulting `main` verified;
- [ ] Stage 23 becomes OPEN/NEXT only after this evidence.

---

# 31. Canonical execution sequence

```text
M22.6 Core Pair Balance / Freeze COMPLETE
→ M22.7 Integrated Campaign Handoff ACTIVE / PARTIAL
→ M22.8 Carrier Air Wing / Small Craft Operations PLANNED
→ Stage 23 Polish / Release Candidate PLANNED
→ post-core faction horizon
```

Главный acceptance narrative M22.8:

> **Авианосная группа обнаруживает угрозу через обычные sensors/tracks, поднимает конечное число реально существующих craft, расходует физическое топливо и боеприпасы из существующей Stage-18 экономики, выполняет CAP/escort/strike/recon/EW через общую combat authority, несёт постоянные потери, возвращает выжившие аппараты через конечную recovery/service capacity, обслуживается реальными material/component inputs, пополняется и заменяет airframes через обычную промышленность и после save/load продолжает ту же операцию без бесплатных ресурсов, reset или параллельной симуляции.**
