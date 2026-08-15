# Star Empires — Ship Mathematics v1.0 Design Baseline

> Статус: **candidate design freeze; становится accepted baseline только после green CI и merge в `main`**  
> Дата: **2026-08-15**  
> Основание: `docs/ship_hull_module_and_fleet_doctrine.md`, `docs/ship_mathematics_v0_1.md`–`v0_9.md`, `docs/ship_mathematics_v1_roadmap_integration_contract.md`  
> Executable evidence: `src/test/java/com/spacesim/combat/benchmark/ShipMathematicsV10DesignBaselineHarness.java`  
> Acceptance: `src/test/java/com/spacesim/combat/benchmark/ShipMathematicsV10DesignBaselineAcceptanceTest.java`  
> Machine-readable baseline: `docs/benchmarks/ship_mathematics_v1_0_design_baseline.json`

---

# 1. Назначение v1.0

`Ship Mathematics v1.0 Design Baseline` завершает исследовательский track перед Stage 17.5.

Это **не production implementation** нового combat runtime и не финальный баланс всех будущих кораблей. Это freeze фундаментальной модели, достаточный для того, чтобы Stage 17.5 мог переносить её в runtime без повторного изобретения архитектуры.

Главное утверждение v1.0:

> **Корабль Star Empires — единый физический, энергетический, информационный, повреждаемый и экономический объект. Все системы корабля работают через общие budgets и interfaces; боевые роли, логистика и масштаб мира являются следствиями одних и тех же исходных величин.**

После принятия v1.0 допускается менять:

- balance coefficients;
- конкретные material/response tables;
- catalog breadth;
- faction-specific technology;
- world-generation distributions;
- maintenance/repair/economic coefficients;

если эти изменения укладываются в уже принятую модель.

Новый фундаментальный stat/resource/budget после v1.0 является **architecture change request**, а не обычным content change.

---

# 2. Что именно замораживается

## 2.1. Канонические физические величины

Authoritative simulation использует SI:

```text
length                  m
area                    m2
volume                  m3
mass                    kg
time                    s
velocity                m/s
acceleration            m/s2
force / thrust          N
momentum                N*s
energy                  J
power                   W
temperature             K
mass flow               kg/s
pressure                Pa
angular quantities      rad / rad/s
radiative intensity     physically defined channel-specific units
```

UI может отображать km, AU, t, kt, MW, GW, TJ и другие удобные единицы только на presentation boundary.

Не существует отдельной authoritative `combat distance unit`, `strategic distance unit` или `sensor distance unit`.

## 2.2. Иерархия кораблей

Сохраняется доктринальная иерархия:

```text
Hull Size
→ Hull Architecture
→ Doctrine Class
→ Specialization
→ Ship Design
→ Variant / Refit
→ Ship Instance
```

`Doctrine Class` помогает человеку читать назначение проекта, но не создаёт скрытые modifiers.

Например, `ESCORT_DESTROYER` эффективен как escort потому, что его проект физически содержит:

- нужные launcher cells;
- interceptor magazines;
- terminal-support channels;
- PD emitters;
- sensors/fire-control;
- acceleration;
- thermal/power margins;
- выгодную formation geometry;

а не потому, что enum `DESTROYER` даёт `+25% point defense`.

---

# 3. Единый Module Integration Contract

Все module families используют общую интеграционную парадигму.

Каждый модуль, где применимо, описывается через:

```text
stableId
integrationCategory
compatibleSlotOrHardpoint
physicalDimensionsM
massKg
occupiedVolumeM3
structuralMountingRequirements
continuousPowerSupplyW
continuousPowerDemandW
peakPowerDemandW
storedEnergyCapacityJ
wasteHeatW
localThermalCapacityJ
coolantTransferDemandW
heatRejectionW
crewRequirement
automationRequirement
ammunitionInterfaces
consumableInterfaces
reactionMassInterfaces
signatureContributions
maintenanceState
damageState
constructionInputs
capabilitySpecificParameters
```

Не каждый модуль обязан использовать все поля.

Примеры:

- armor имеет `powerDemand = 0`, но массу, геометрию, материал, volume/spacing и damage state;
- passive sensor может иметь малый power и heat, но aperture, band, detector noise и pointing geometry;
- reactor имеет supply power и waste heat;
- radiator имеет mass/volume/deployment geometry и heat rejection;
- missile magazine имеет mass/volume/ammunition interface, protection и handling limits;
- cargo tank добавляет mass/volume/cargo capacity и может менять center of mass;
- shield emitter имеет field parameters, power, heat и coverage geometry;
- FTL module имеет translated-mass envelope, charge energy/power, heat и cooldown.

## 3.1. Обязательные module families

v1.0 contract должен быть способен описать минимум:

1. `REACTOR_POWER`;
2. `ENERGY_STORAGE`;
3. `MAIN_DRIVE`;
4. `MANEUVER_THRUSTERS`;
5. `FTL_JUMP`;
6. `THERMAL_CONTROL`;
7. `SENSOR_EW_FIRE_CONTROL`;
8. `COMMUNICATION_DATALINK`;
9. `SHIELD_FIELD`;
10. `ARMOR_PROTECTION`;
11. `WEAPON_AMMUNITION`;
12. `CREW_LIFE_SUPPORT_AUTOMATION`;
13. `CARGO_TANK_STORES`;
14. `HANGAR_SMALL_CRAFT`;
15. `MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE`.

Специализированная capability equation разрешена, но она не создаёт параллельную экономику массы/энергии/тепла/экипажа.

---

# 4. Central Derived Ship State

Production Stage 17.5 должен иметь один authoritative calculator/derived-state boundary.

Минимальный итог:

```text
totalMassKg
usedInternalVolumeM3
remainingIntegrationVolumeM3
centerOfMass / future inertia inputs
continuousPowerSupplyW
continuousPowerDemandW
continuousPowerMarginW
peakPowerDemandW
storedEnergyAvailableJ
wasteHeatW
heatTransferTopology
heatRejectionW
continuousHeatMarginW
crewRequired
crewSupported
ammunitionMassKg
storesMassKg
cargoMassKg
reactionMassKg
availableThrustN
accelerationMps2
massFlowKgPerS
deltaVMps
signatureState
sensorCapability
track/fireControl capability
shield capability
weapon capability
protection/compartment state
maintenance/logistics state
```

Fitting validation одновременно проверяет:

```text
slot / hardpoint compatibility
geometry / integration envelope
mass
volume
structural mounting
continuous power
peak power / stored energy
heat transfer / heat rejection
crew / automation
ammunition / stores
reaction mass
mission interfaces
```

Build, который проходит только по hardpoint enum, но нарушает power/heat/mass, является невалидным.

---

# 5. Движение и propulsion

Для обычного local flight сохраняется ньютоновская модель:

```text
acceleration = availableThrust / totalMass
massFlow = thrust / exhaustVelocity
jetPower >= 0.5 * thrust * exhaustVelocity
deltaV = exhaustVelocity * ln(initialMass / finalMass)
```

Cargo, armor, ammunition, equipment и reaction mass входят в реальную массу.

Следовательно:

- loaded freighter хуже разгоняется, чем empty freighter;
- expended missile magazines немного уменьшают массу;
- повреждение drive уменьшает available thrust;
- потеря reaction mass ограничивает operational reach;
- тяжелее защищённый вариант платит acceleration/delta-v;
- tanker физически меняет собственную динамику после offload.

`max thrust` и `sustained thrust` могут различаться из-за thermal/endurance constraints, но используют один и тот же mass state.

---

# 6. Power, stored energy и thermal topology

v1.0 фиксирует двухуровневую thermal architecture:

```text
module-local thermal state / coolant interface
→ ship heat transport bus
→ thermal stores / radiators
→ space
```

Недостаточно иметь только общий `shipHeat`.

Пример из v0.6:

```text
PD emitter local loop damaged
→ coolantTransferW decreases
→ local temperature rises
→ emitter throttles / loses duty cycle
→ defense capability falls
```

без `-20% PD debuff`.

Ship-level heat rejection также остаётся реальным ограничением, особенно для sustained high-power operations.

Thermal stores в J должны выводиться из конкретной mass/material/temperature/phase-change model либо быть explicit exotic technology input; бесплатный `20 TJ heat buffer` без массы запрещён.

---

# 7. Sensors, signatures, tracks и EW

Информационная цепочка v1.0:

```text
physical emission / reflection
→ propagation
→ aperture / antenna
→ detector signal + noise + interference
→ measurement + covariance R
→ TrackState estimate + covariance P
→ prediction / age / process noise
→ weapon-specific future-state uncertainty
→ fire-control solution
```

Обязательные состояния информации:

```text
DETECTED
CLASSIFIED
TRACKED
FIRE_CONTROL
```

Дальний thermal contact не равен точному range solution.

## 7.1. SignatureState не является scalar stealth

Минимальные channels:

### Thermal radiator

Зависит от:

- surface temperature;
- emissivity;
- radiating area;
- orientation / visible projected area;
- band.

### Engine plume

Базовая энергетическая связь:

```text
minimumJetPowerW = 0.5 * thrustN * exhaustVelocityMps
```

Далее setting-specific model определяет, какая часть энергии оказывается в наблюдаемом spectral band и как radiance зависит от aspect.

NASA/NTRS experiments подтверждают, что реальные rocket exhaust plumes имеют измеримую spectral radiance и что plume spectroscopy несёт информацию о составе/состоянии двигателя. Это подтверждает **архитектуру отдельного plume channel**, но не даёт radiative fraction для вымышленного fusion drive.

Поэтому `1e-6 / 1e-5 / 1e-4` 3–5 µm fractions из v0.9 остаются authoring sensitivity, не физической истиной.

### Radar

RCS задаётся как минимум функцией:

```text
RCS(frequency, aspect, configuration)
```

NASA spacecraft/material measurements показывают именно frequency/azimuth dependence. v0.8 scalar `100 m2 / 10,000 m2` сохраняется только как midpoint sensitivity seed.

### Reflected optical

Зависит от:

```text
illumination geometry
phase angle
orientation
surface BRDF / reflectance
specular vs diffuse response
```

## 7.2. EW

ECM не является `-30% accuracy`.

Noise jammer / deceptive emitter / decoy изменяют реальные measurements:

- signal/noise/interference;
- waveform overlap;
- residual;
- innovation covariance;
- data association;
- track continuity.

ECCM изменяет processing, dwell, waveform/filtering и network geometry с соответствующей ценой времени/power/compute.

---

# 8. Weapons и fire control

Ни у одного weapon family нет универсального `range` как физической стены.

## Kinetic

```text
target track covariance
+ pointing uncertainty
+ time of flight
+ target maneuver envelope
+ projectile geometry
→ hit distribution
→ impact response
```

Основные v0.3 P50 seeds сохраняются как benchmark, не hard max range.

Projectile обязательно несёт:

```text
material
density
shape
mass
length
diameter
velocity vector
momentum
kinetic energy
```

## Beam

Laser interaction:

```text
wavelength
aperture
jitter
range
spot geometry
beam power
dwell
absorptivity
local material response
```

Laser также платит electrical power, local heat, coolant и emitter duty.

## Guided weapons

Missiles/interceptors имеют:

```text
wet/dry/propellant mass
thrust
exhaust velocity
mass flow
burn time
delta-v
seeker
track/guidance input
navigation law
terminal reserve
warhead / impact state
```

Нет отдельного `missileHitChance`.

## Point defense

Layered defense возникает из:

```text
sensor track
launch geometry
cells
cycle time
support channels
magazines
interceptor dynamics
safe intercept distance
laser emitter geometry
laser thermal duty
residual debris
```

Formation spacing поэтому является физической частью defense performance.

---

# 9. Armor, debris и heavy-impact response

## 9.1. Разные regimes

v1.0 сохраняет строгую границу:

### MMOD / debris regime

Empirical BLE разрешены только внутри documented validation envelope конкретной конструкции/material system.

### Intact heavy weapon regime

Нельзя переносить MMOD Whipple BLE на:

- 25 kg M dart;
- 150 kg L dart;
- 1,000 kg XL dart;
- 12,000 kg missile body.

NASA research показывает, что hypervelocity response зависит от projectile shape/material, impact angle, target configuration и material model; BLE сами являются semi-empirical products of test datasets. Это подтверждает необходимость bounded calibration, а не универсальной формулы.

## 9.2. Baseline material catalog

v1.0 вводит минимальные authoring material IDs:

```text
material.structural_aluminum_v1
material.high_strength_steel_v1
material.ceramic_strike_face_v1
material.carbon_composite_v1
material.high_density_penetrator_v1
```

Rounded densities в benchmark нужны для mass/geometry exercises и **не являются сертифицированным engineering material database**.

Static yield/strength numbers не используются как универсальная HVI penetration truth.

## 9.3. HeavyImpactResponseSurface

Authoritative architecture:

```text
projectile material
projectile density
projectile shape
projectile length / diameter
impact velocity vector
incidence angle
layer materials
layer thicknesses
layer spacing
→ bounded calibrated response surface
→ perforation / ricochet / stop
→ residual mass / velocity
→ deposited energy
→ ejecta / spall
→ structural opening/crater
→ next-layer DamagePackets
```

Production solver обязан знать domain calibration.

Если query выходит за domain:

```text
EXTRAPOLATION_FORBIDDEN
```

или требуется explicit fallback policy, отмеченная как low-confidence authoring approximation. Silent extrapolation запрещена.

v1.0 machine-readable baseline содержит synthetic response surface только для проверки API/validation semantics. Его numeric output **не является утверждением о реальной защите**.

---

# 10. Compartments и subsystem damage

Корабль не является global HP sponge.

Damage сохраняет пространственную информацию:

```text
impact point
impact vector
residual projectile/debris state
fragment cone/distribution
thermal deposition
pressure/plasma where modeled
```

Затем damage проходит через physical layers/compartments/subsystems.

Пример:

```text
debris enters PORT_COOLANT
→ coolant trunk integrity falls
→ available coolant transfer falls
→ shield/laser/reactor local thermal margins change
→ physical capabilities degrade
```

Damage-to-capability должен проходить через реальные system inputs.

---

# 11. Energetic shields — frozen fictional engineering contract

v0.1 оставлял shields как возможную exotic technology. Для Stage 17.5 этого недостаточно, поэтому v1.0 закрывает архитектуру.

Важно:

> **Shields остаются вымышленной технологией сеттинга. v1.0 не утверждает, что такая технология физически возможна. Но её влияние на корабль моделируется строго через общие engineering budgets.**

ShieldDefinition должен содержать минимум:

```text
emitter geometry / coverage arcs or surfaces
emitter mass / volume
idle power
field energy capacity J
field energy cost per coupled/deflected incident J
max interaction power W
damage-type / incidence coupling
recharge input power W
recharge efficiency
waste heat
local thermal capacity
coolant interface
collapse threshold
restart energy / restart delay
emitter damage state
signature contribution where applicable
```

## 11.1. Почему это не generic shield HP

Поле действительно имеет finite energy reserve, но combat outcome также ограничен:

- geometry/coverage;
- instantaneous interaction power;
- threat coupling;
- recharge power;
- thermal rejection;
- damaged emitters;
- restart delay.

Два shields с одинаковым `fieldEnergyCapacityJ` могут работать совершенно по-разному.

## 11.2. v1 authoring demonstrator

Только для executable closure:

```text
field capacity = 120 GJ
field cost = 0.25 J field per 1 J deflected incident
max interaction power = 5 TW
thermalized incident fraction = 5%
recharge input = 600 MW
recharge efficiency = 85%
restart delay = 20 s
```

Против `450 GJ` XL kinetic interaction за `0.1 s`:

```text
first impact:
450 GJ deflected
112.5 GJ field reserve spent
7.5 GJ reserve remains
22.5 GJ heat generated

second identical impact:
only 30 GJ can be deflected by remaining reserve
420 GJ leaks past field
field collapses
```

Это balance seed, не физическая характеристика реального поля.

Главное — модель создаёт естественную saturation/recharge/thermal динамику и может быть повреждена физически.

---

# 12. FTL / jump — frozen fictional integration contract

FTL также остаётся exotic setting technology.

Для local Newtonian space нет попытки объяснять FTL обычной propulsion physics.

Однако jump drive обязан платить:

```text
module mass
module volume
charge power
stored / translation energy
translated mass envelope
waste heat
spool time
cooldown
maintenance / damage
```

Inter-system edge имеет explicit world data:

```text
origin / destination
availability / topology conditions
edge transit time
arrival conditions / dispersion if applicable
```

Принцип:

```text
requiredTranslationEnergyJ = translatedMassKg * edge/drive energyPerKgJ
spoolTime = requiredEnergy / usefulChargePower
```

Конкретная fictional law позже может быть богаче, но translated mass, energy, power и time остаются обязательными inputs.

v1.0 reference seed:

```text
max translated mass = 100,000 t
translation energy = 25 kJ/kg
charge power = 5 GW
charge efficiency = 80%
cooldown = 90 s
```

Для loaded escort destroyer `21,927 t`:

```text
required translation energy = 548.175 GJ
spool time = 137.04375 s
```

Battleship `545,765 t` физически не может использовать этот drive variant по translated-mass envelope и требует capital jump hardware.

Так large hull logistics возникают из fit, а не из `capital ships cannot jump because class rule`.

### Текущий Stage-10 compatibility fixture

Существующие ~1.3 s в test topology не объявляются v1.0 world-scale truth. Stage 19 обязан заново откалибровать inter-system distributions через frozen interface:

```text
spool + transit + arrival + cooldown + local approach
```

---

# 13. Representative military + civilian designs

v1.0 сохраняет reference seeds v0.2 и подтверждает, что одни формулы работают для military и civilian hulls.

| Design | Departure mass | Max acceleration | Nominal Δv |
|---|---:|---:|---:|
| Torpedo Corvette | 2.140 Mt | 1.028 m/s² | 32.90 km/s |
| Escort Destroyer | 21.927 Mt | 0.602 m/s² | 38.45 km/s |
| Battleship | 545.765 Mt | 0.252 m/s² | 45.64 km/s |
| Loaded Bulk Freighter | 143.000 Mt | 0.0839 m/s² | 15.37 km/s |
| Loaded Fleet Tanker | 170.000 Mt | 0.147 m/s² | 19.42 km/s |

`Mt` здесь используется как million kilograms / 1,000 tonnes для удобства таблицы; authoritative значения остаются kg.

Эти designs не становятся immutable final content. Они остаются reference anchors для regression и Stage-21 balance.

---

# 14. Связь с world generation

`Ship Mathematics v1.0` теперь является input Stage 19.

Generated world обязан быть физически совместим с:

```text
ship acceleration
braking
finite delta-v
jump spool/transit/cooldown
sensor detection
track quality
weapon time of flight
formation spacing
intercept stand-off
logistics round-trip time
production/consumption cadence
fleet response time
```

## 14.1. Local travel benchmark

v0.9 rest-to-rest solver остаётся pre-calibration reference.

Loaded bulk freighter:

```text
100,000 km  ≈ 19.18 h
1,000,000 km ≈ 61.58 h
```

Escort destroyer sustained:

```text
100,000 km  ≈ 14.32 h
1,000,000 km ≈ 45.29 h
```

Это lower-bound without docking, reserve margins, traffic, combat or jump overhead.

Эти цифры не означают, что Stage 19 обязан выбирать такие расстояния. Они означают, что **выбор расстояния обязан показать рассчитанное время и operational consequence**.

## 14.2. Scale hierarchy benchmark

Reference relationship:

```text
heavy direct-fire envelope ~ 3,000 km
local logistics calibration leg = 100,000 km
destroyer central plume detection ~ 30 million km
```

Следовательно, объект может быть обнаружен намного раньше, чем становится fire-control target.

World generation и fog-of-war должны уважать эту иерархию.

## 14.3. Economy cadence

Если delivery loop физически занимает часы simulation time, production/consumption buffers должны быть спроектированы относительно этого времени.

Нельзя независимо задавать:

```text
factory consumes all material every 30 seconds
```

если supply ships физически приходят раз в часы, кроме случая, когда хронический shortage является намеренным outcome.

Также нельзя давать бесконечные buffers, полностью уничтожающие ценность логистики.

---

# 15. Связь с экономикой и construction content

Ship/component design одновременно задаёт:

```text
materials
components
manufacturing capability
shipyard volume / hardpoint capability
construction work/time
purchase value
maintenance load
repair materials
crew demand
reaction-mass demand
ammunition demand
replacement cost
salvage output
```

Stage 21 technology ladder не может использовать blanket:

```text
Mk II = +25% everything
```

High-tier преимущества должны выражаться через параметры вроде:

```text
specific power
specific thrust
exhaust velocity
material response
sensor noise
aperture quality
pointing stability
thermal temperature / emissivity
recharge efficiency
automation
manufacturing tolerance
maintenance complexity
```

Улучшение почти всегда должно иметь manufacturing/logistics/mass/heat/cost tradeoff, если только конкретная технология действительно доминирует старую и её экономическая цена объясняет вытеснение.

---

# 16. Persistence и runtime API expectations для Stage 17.5

Stage 17.5 должен ввести versioned persistence для state, который влияет на продолжение симуляции:

```text
installed modules / fitting identity
ammunition quantities
reaction mass
stored energy
local thermal state
shield reserve / collapse / restart state
module damage state
compartment damage
sensor/track state where strategically persistent
maintenance state
FTL charge/cooldown state
```

Derived values не обязаны сохраняться, если они детерминированно пересчитываются из authoritative inputs.

Player и AI должны использовать общие queries:

```text
canAccelerate / acceleration envelope
remainingDeltaV
canJump / jump plan
sensor observation capability
track quality
canFire weapon / fire-control quality
shield coverage/state
protection state
thermal endurance
ammunition endurance
repair / maintenance need
```

UI читает эти queries и отправляет commands; не мутирует physical state напрямую.

---

# 17. Determinism

Combat-depth runtime сохраняет:

- fixed simulation step;
- stable entity/weapon/module iteration;
- explicit deterministic tie-breaks;
- stored seed только там, где stochastic model является design requirement;
- отсутствие wall-clock dependence;
- bounded response-surface lookup;
- deterministic track association policy или named RNG stream для explicit ambiguity model.

Research harnesses v0.4–v1.0 остаются regression evidence.

---

# 18. Final architecture closure matrix

К v1.0 архитектурно закрыты:

1. hull / slot / hardpoint geometry;
2. mass / volume / propulsion / delta-v;
3. power / peak power / energy storage;
4. local + ship thermal architecture;
5. sensors / signatures / tracks / covariance;
6. ECM / ECCM / decoys;
7. kinetic / beam / guided / point-defense families;
8. ammunition / magazines / layered defense;
9. armor / debris / heavy-impact response contract;
10. compartments / subsystem damage;
11. energetic shield contract;
12. FTL/jump integration contract;
13. crew / automation / mission modules;
14. civilian/logistics reference designs;
15. world-scale coupling;
16. construction / maintenance / economy seam;
17. shared player/AI capability contract.

После v1.0 могут оставаться **content/calibration** вопросы, но не должны оставаться вопросы вида:

- «какую новую фундаментальную шкалу добавить для stealth?»;
- «как вообще учитывать массу оборудования?»;
- «shield — это HP или subsystem?»;
- «combat distance и world distance — разные условные единицы?»;
- «armor — просто hull multiplier или spatial protection?»;
- «sensor range — hard radius или measurement SNR?»;
- «destroyer escort bonus нужен отдельным modifier?».

Ответы на эти вопросы теперь определены моделью.

---

# 19. Что разрешено менять после freeze

Без reopening architecture разрешается:

- менять numeric shield capacities/coupling/recharge;
- добавлять shield emitter families;
- менять fictional jump energy/cooldown/transit distributions;
- добавлять material response surfaces;
- калибровать plume fractions/aspect tables;
- добавлять RCS/BRDF/material data;
- менять weapon/missile/laser balance;
- менять hull dimensions/masses в разумных пределах;
- расширять ship/module catalog;
- создавать faction doctrines;
- настраивать world-generation distance distributions;
- настраивать production/consumption/maintenance cadence.

Но всё это должно проходить через frozen inputs/budgets.

---

# 20. Что требует Architecture Change Request

После принятия v1.0 отдельного решения требует:

- новый фундаментальный ship budget вне common contract;
- parallel `armorPoints`, `sensorPoints`, `stealthRating` как authoritative state;
- class-name bonuses, не выводимые из equipment/geometry;
- отдельная player-only или AI-only combat/flight formula;
- новый distance/time scale без SI mapping;
- damage path, обходящий module/compartment state;
- technology tier, требующий отдельной скрытой resource system;
- world generation, физически не связанный с ship travel/sensor/logistics model.

---

# 21. Acceptance gate

`Ship Mathematics v1.0 Design Baseline` принимается только если одновременно выполнено:

```text
1. machine-readable v1.0 baseline существует;
2. executable v1.0 acceptance существует;
3. все v0.4–v0.9 regression tests продолжают проходить;
4. representative civilian + military ships используют общие equations;
5. integrated fit проходит mass/volume/power/heat/crew validation;
6. heavy impact запрещает silent extrapolation;
7. shield имеет energy/power/heat/geometry contract;
8. FTL имеет mass/energy/power/time contract;
9. world-scale benchmark использует SI distance + physical travel time;
10. closure matrix не содержит открытого architectural domain;
11. full repository Java-17 clean verify green;
12. baseline merged into main.
```

После этого gate из `development_roadmap.md` считается выполненным по research части.

Но перед фактической активацией Stage 17.5 остаётся обязательный уже зафиксированный шаг:

```text
accepted Ship Mathematics v1.0
→ detailed roadmap integration pass
   - Stage 17.5
   - Stage 19
   - Stage 21
→ only then Stage 17.5 may become ACTIVE
```

---

# 22. Итоговый design invariant

```text
materials + geometry + modules + consumables
→ mass / volume / power / heat / crew / signatures
→ thrust / delta-v / sensors / shields / weapons / protection
→ damage / endurance / logistics / maintenance
→ fleet doctrine / economic cost / world-scale consequence
```

Никакая крупная gameplay capability корабля не должна возникать вне этой цепочки без explicit architecture decision.

Именно это, а не конкретный набор чисел, является `Ship Mathematics v1.0 Design Baseline`.