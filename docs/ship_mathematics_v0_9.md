# Star Empires — Ship Mathematics v0.9: Integrated Design Baseline

> Статус: **executable engineering / pre-v1.0 integration baseline**  
> Дата: **2026-08-15**  
> Основание: `docs/ship_mathematics_v0_1.md`–`v0_8.md` и `docs/ship_mathematics_v1_roadmap_integration_contract.md`  
> Код: `src/test/java/com/spacesim/combat/benchmark/ShipMathematicsV09IntegratedHarness.java`  
> Snapshot: `docs/benchmarks/integrated_design_reference_v0_9.json`

---

## 1. Задача v0.9

v0.1–v0.8 последовательно закрыли отдельные инженерные домены:

```text
v0.1  hull scale / thrust / delta-v / power / heat
v0.2  representative ship fits
v0.3  track → weapon interaction
v0.4  deterministic missile / PD combat harness
v0.5  saturation / endurance sweep
v0.6  local + ship thermal architecture
v0.7  debris / protection / compartment exposure
v0.8  signatures / sensors / covariance / EW
```

Но `Ship Mathematics v1.0 Design Baseline` требует не набора хороших подсистем, а **единой модели**.

v0.9 решает четыре интеграционные задачи:

1. фиксирует общий инженерный contract для всех module families;
2. закрывает архитектурный пробел heavy-impact response без ложной универсальной penetration formula;
3. добавляет engine-plume и aspect-dependent signature как часть той же sensor model;
4. впервые связывает ship performance с физическими distance/time scales будущего Stage 19 world generation.

Главный принцип:

> **одни и те же физические величины должны одновременно объяснять fitting, движение, заметность, бой, повреждение, логистику, стоимость и масштаб мира.**

---

# 2. Инженерная опора и ограничения

v0.9 использует первичные NASA/NTRS материалы как архитектурную и физическую опору.

## 2.1. Hypervelocity response

Ключевые источники:

- NASA NTRS `20230003669` / `20230014238` — JSC Hypervelocity Impact Technology: NASA сочетает hypervelocity tests, analysis и hydrocode assessments для обновления/проверки calibrated ballistic limits;
- NTRS `20040071073` — SPHC hydrocode сравнивается с несколькими penetration equations и другими codes;
- NTRS `19920013134` — CTH hydrocode используется для анализа energy deposition / momentum при hypervelocity impact;
- NTRS `19720022272` — projectile shape существенно влияет на penetration: цилиндрические projectiles той же массы могут быть намного эффективнее сфер;
- NTRS `20090024823` — material equation-of-state существенно влияет на simulation result;
- NTRS `20140006492` — изменение projectile density потребовало пересмотра существующих BLE, которые давали слишком оптимистичные predictions.

Вывод:

> heavy-impact response обязан быть material-, geometry-, velocity- и configuration-dependent.

Никакая формула `damage = E / armor` не может быть authoritative foundation.

## 2.2. Engine plume signature

Ключевые источники:

- NTRS `20000024801` — radiant transfer / spectral absorption в rocket exhaust plume;
- NTRS `19680053086` и `19680048227` — измерения spectral radiance rocket exhaust plumes;
- NTRS `20070008237` и `19970022765` — plume emission spectroscopy используется для диагностики engine state/material species.

Вывод:

> работающий двигатель создаёт отдельный spectral/signature channel, который нельзя свести к steady radiator heat.

Конкретная radiative efficiency будущего fusion-era exhaust неизвестна и является setting/calibration input, а не NASA-derived number.

## 2.3. Aspect-dependent signatures

- NTRS `20250002268` — измеренный RCS spacecraft materials меняется по frequency и azimuth;
- NTRS `19790008003` — Space Shuttle RCS измерялся именно по relevant aspect angles;
- NTRS `20120007406` / `20250000768` — optical light curves зависят от object attitude, illumination geometry и phase angle;
- NTRS `19960054364` — diffuse и specular materials имеют различную phase-dependent reflectance.

Следовательно:

```text
thermal signature != one scalar
RCS != one scalar
optical reflectance != one scalar
plume radiance != one scalar
```

Они являются functions/tables от frequency/band, orientation, operating state и source-target-sensor geometry.

---

# 3. Единый module integration contract

После v0.9 все module families должны авториться в одной парадигме.

Минимальные общие поля, где применимо:

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

crew / automation requirements
ammunition / consumable interfaces
reaction-mass interfaces
signature contributions
maintenance / damage state
construction inputs
capability-specific physical parameters
```

Это **не означает**, что reactor и armor имеют одинаковые capability fields.

Это означает, что специализированная функция должна подключаться к одной общей physical/resource model.

Пример:

```text
armor:
  mass > 0
  volume > 0
  power = 0
  heat = usually 0
  material/layer geometry != 0
  protection response != 0

sensor:
  mass > 0
  volume > 0
  power > 0
  heat > 0
  aperture > 0
  detector noise != 0
  measurement covariance output != 0

missile battery:
  launcher mass/volume
  electrical demand
  coolant / electronics heat
  magazine mass/volume
  missile wet mass
  launch-cell geometry
  support channels
```

---

# 4. Central Derived Ship State

Stage 17.5 production implementation должен иметь **один central derived-ship calculator**.

Вход:

```text
HullDefinition
+ installed ModuleDefinitions
+ armor/protection layout
+ ammunition
+ reaction mass
+ stores/cargo
+ crew state
+ damage state
```

Выход минимум:

```text
totalMassKg
usedVolumeM3
centerOfMass / future inertia inputs
continuousPowerSupplyW
continuousPowerDemandW
continuousPowerMarginW
peakPowerDemandW
storedEnergyJ
wasteHeatW
heatRejectionW
heatMargins / local coolant constraints
crewDemand
ammunition/stores masses
reactionMassKg
max / sustained thrust
max / sustained acceleration
nominal delta-v
signature state by channel
sensor capability
weapon capability
damage / redundancy capability
maintenance / logistics footprint
```

Запрещено независимо хранить authoritative derived values, которые могут разойтись с fitting inputs.

UI может кешировать derived display values, но source of truth — fit + physical state.

---

# 5. Executable module-budget demonstrator

v0.9 test harness содержит небольшой synthetic integration demonstrator.

Он **не является игровым content** и нужен только для доказательства contract semantics.

Четыре разных module roles — power core, sensor/EW, capacitor и radiator — сходятся в один budget:

```text
mass                  = 450,000 kg
volume                = 910 m3
continuous supply     = 300 MW
continuous demand     = 106 MW
power margin          = 194 MW
peak demand           = 146 MW
stored energy         = 20 GJ
waste heat            = 142.5 MW
heat rejection        = 180 MW
thermal margin        = 37.5 MW
local thermal capacity= 600 MJ
coolant-transfer load = 30 MW
crew                  = 24
```

Ценность acceptance не в этих synthetic цифрах, а в том, что разные module families **не создают отдельные budget systems**.

---

# 6. Heavy-impact problem: что именно было не закрыто после v0.7

v0.7 правильно запретил MMOD BLE extrapolation на наши intact ship weapons.

Но одного запрета недостаточно для v1.0.

Production architecture должна знать:

> какой solver отвечает за тяжёлое попадание?

v0.9 фиксирует ответ:

```text
heavy intact impact
→ calibrated multidimensional response surface
→ interpolation only inside declared calibration domain
→ residual DamagePacket(s)
→ next armor layer / compartment / subsystem
```

Это не обязательно означает, что игра в runtime запускает hydrocode.

Наоборот, hydrocode/experiment/high-fidelity offline authoring являются способом **создать calibrated tables/surfaces**, а deterministic game runtime читает уже подготовленный response model.

---

# 7. Почему response surface, а не universal equation

Heavy-impact key должен содержать минимум:

```text
projectile material / density
projectile shape
projectile length
projectile diameter
impact velocity
incidence angle

layer material
layer thickness
layer spacing / stand-off
backing / next layer configuration
```

В зависимости от family могут потребоваться дополнительные axes.

Output должен содержать не `damagePoints`, а физически интерпретируемые residuals:

```text
perforation / no perforation
residual mass fraction
residual velocity vector
deposited energy
spall/ejecta mass
fragment distribution / cone
crater / hole geometry
next-layer DamagePacket(s)
```

### Calibration rule

Runtime имеет право интерполировать только внутри explicit domain.

За пределами domain:

```text
NO SILENT EXTRAPOLATION
```

Authoring validator должен либо выбрать другую validated model, либо заблокировать production content.

До v1.0 основные weapon families не должны оставаться в состоянии `UNCALIBRATED_HEAVY_IMPACT`.

---

# 8. Kinetic penetrator geometry seeds

До v0.9 kinetic weapons имели mass + muzzle velocity, но не имели shape.

Это недостаточно: NASA impact work показывает сильную shape sensitivity.

v0.9 добавляет **authoring geometry seeds**, а не claims реальной оптимальности.

Reference penetrator density sensitivity seed:

```text
rho = 19,000 kg/m3
```

## M coilgun dart

```text
mass     = 25 kg
diameter = 0.05 m
length   = 0.6701 m
L/D      = 13.40
velocity = 15 km/s
energy   = 2.8125 GJ
```

## L coilgun dart

```text
mass     = 150 kg
diameter = 0.10 m
length   = 1.0052 m
L/D      = 10.05
velocity = 20 km/s
energy   = 30 GJ
```

## XL capital dart

```text
mass     = 1,000 kg
diameter = 0.20 m
length   = 1.6753 m
L/D      = 8.38
velocity = 30 km/s
energy   = 450 GJ
```

Эти размеры являются первым geometry seed только для построения calibration axes.

До material response pass нельзя объявлять конкретную penetration depth.

---

# 9. Missile impact и penetrator impact — разные события

M anti-ship missile из v0.3/v0.4:

```text
wet/reference terminal body mass ~12,000 kg
velocity benchmark             ~18 km/s
kinetic energy                 ~1.944 TJ
```

Но это **complex body**, а не автоматически 12-тонный long-rod penetrator.

Production content должен явно определить terminal architecture:

```text
kinetic body
fragmentation warhead
shaped / directed warhead
separate dense penetrator
proximity fragmentation
sensor/guidance kill only
```

Поэтому missile body и coilgun dart не используют один и тот же heavy-impact geometry model только потому, что оба несут kinetic energy.

---

# 10. Engine plume как физическая signature

Из v0.1:

```text
minimum jet kinetic power = 0.5 × thrust × exhaustVelocity
```

При military seed:

```text
ve = 100,000 m/s
```

получаем:

| Ship | Max thrust | Minimum jet kinetic power |
|---|---:|---:|
| Torpedo Corvette | 2.2 MN | **110 GW** |
| Escort Destroyer | 13.2 MN | **660 GW** |
| Battleship | 137.5 MN | **6.875 TW** |

Это огромный энергетический поток, который нельзя игнорировать при signature design.

Однако неизвестно, какая часть mature fusion-era exhaust попадает в конкретный optical/IR band.

Поэтому v0.9 не вводит ложный universal plume efficiency.

---

# 11. Plume radiative-fraction sensitivity

Для 3–5 µm authoring benchmark берётся sweep:

```text
low     = 1e-6 of minimum jet power
central = 1e-5
high    = 1e-4
```

Это **не NASA values**.

Они нужны, чтобы проверить чувствительность future world/sensor scale к ещё не определённой fusion plume radiance.

Для line-of-sight aspect используется provisional relative sensitivity:

```text
forward   = 0.25
broadside = 1.0
aft       = 4.0
```

Это также не финальная angular phase function.

Production v1.0 должен заменить её data-driven plume table/function.

---

# 12. Plume detection benchmark

Используется тот же 1.5-m passive detector/noise contract, что и v0.8, но входом является непосредственно source power в 3–5 µm band.

При central `1e-5` и broadside aspect:

| Ship | 3–5 µm band power | 5σ detection range |
|---|---:|---:|
| Corvette | 1.1 MW | **12.31 million km** |
| Destroyer | 6.6 MW | **30.15 million km** |
| Battleship | 68.75 MW | **97.31 million km** |

Для destroyer при одном central radiative fraction:

```text
forward   ~15.07 million km
broadside ~30.15 million km
aft       ~60.30 million km
```

При broadside, но sweep radiative fraction:

```text
1e-6 →  9.53 million km
1e-5 → 30.15 million km
1e-4 → 95.34 million km
```

### Design result

Даже несколько порядков uncertainty в fusion-plume radiative efficiency **не позволяют считать engine-on signature несущественной по умолчанию**.

Но конкретный balance вывод пока делать рано.

Для v1.0 нужны:

- exhaust/plume spectral authoring model;
- throttle dependence;
- reaction-mass species/state;
- plume aspect function;
- engine geometry / multiple nozzles;
- occultation by hull where physically applicable.

---

# 13. Общая signature architecture после v0.9

Authoritative `SignatureState` должен агрегировать независимые channels.

## 13.1. Thermal radiator/body emission

Зависит от:

```text
surface temperature
emissivity
visible projected radiator area
aspect
bandpass
thermal operating state
```

## 13.2. Engine plume

Зависит от:

```text
thrust
mass flow
exhaust velocity
plume spectral radiance
throttle
reaction-mass / plume state
viewing aspect
```

## 13.3. Active RF/laser emissions

Зависит от:

```text
transmit power
frequency/wavelength
antenna/aperture gain
beam direction
side lobes
waveform/dwell
```

## 13.4. Radar cross section

Должен стать:

```text
RCS(frequency, aspect, configuration, damageState)
```

v0.8 `100 m2 / 10,000 m2` остаются sensitivity seeds, а не постоянными RCS классов.

## 13.5. Reflected optical light

Должно зависеть от:

```text
stellar illumination
phase angle
projected area
surface BRDF / reflectance
attitude / tumble
specular glints
```

Это означает, что stealth design — не один stat.

Он является компромиссом между heat rejection, geometry, materials, operating state, thrust, active emissions и orientation.

---

# 14. Damage и signature должны быть связаны

v0.6–v0.9 вместе требуют, чтобы damage менял реальные capabilities.

Примеры:

```text
radiator damage
→ heat rejection falls
→ temperature / thermal storage changes
→ thermal signature changes
→ weapon/sensor duty may throttle

engine damage
→ available thrust falls
→ travel/combat acceleration falls
→ plume power changes

sensor aperture damage
→ collecting area / pointing quality changes
→ measurement covariance grows

armor penetration
→ coolant trunk hit
→ local cooling falls
→ PD emitter throttles
```

Не нужен отдельный generic `damaged = -30% all stats`.

---

# 15. World scale: корабельная математика становится генераторным constraint

`docs/ship_mathematics_v1_roadmap_integration_contract.md` уже запретил проектировать Stage 19 независимо от кораблей.

v0.9 добавляет executable pre-calibration.

Для lower-bound rest-to-rest local transit используются:

```text
acceleration a
available total delta-v Δv
travel distance D
```

Если полного accel-half / brake-half профиля хватает по delta-v:

```text
peakVelocity = sqrt(a × D)
usedDeltaV   = 2 × peakVelocity
time         = 2 × sqrt(D / a)
```

Если delta-v недостаточно:

```text
peakVelocity = availableDeltaV / 2
accelerate
→ coast
→ brake
```

Это физическая нижняя граница, **не готовое AI travel planner**.

Operational route обязан оставлять reserves и учитывать docking, approach, jump, waiting, maintenance и stores.

---

# 16. Reference world-scale distances

Первый pre-calibration sweep:

```text
10,000 km
100,000 km
1,000,000 km
10,000,000 km
```

## 16.1. Loaded bulk freighter

Из v0.2:

```text
loaded acceleration = 0.083916 m/s2
nominal delta-v      = 15.373 km/s
```

Lower-bound rest-to-rest:

| Distance | Profile | Time |
|---:|---|---:|
| 10,000 km | accel/brake | **6.06 h** |
| 100,000 km | accel/brake | **19.18 h** |
| 1,000,000 km | accel/coast/brake | **61.58 h / 2.57 d** |
| 10,000,000 km | accel/coast/brake | **386.83 h / 16.1 d** |

Уже на `1 million km` loaded freighter упирается не только в acceleration, но и в finite delta-v.

## 16.2. Escort destroyer at sustained thrust

Из v0.2:

```text
sustained acceleration = 0.150499 m/s2
nominal delta-v         = 38.455 km/s
```

| Distance | Profile | Time |
|---:|---|---:|
| 10,000 km | accel/brake | **4.53 h** |
| 100,000 km | accel/brake | **14.32 h** |
| 1,000,000 km | accel/brake | **45.29 h / 1.89 d** |
| 10,000,000 km | accel/coast/brake | **179.96 h / 7.50 d** |

### Важный вывод

Разница ship roles возникает не только в бою.

Один и тот же generated system scale по-разному воспринимается:

- loaded freighter;
- empty freighter;
- military escort;
- tanker;
- carrier group.

Поэтому Stage 19 должен тестировать distributions на **нескольких representative ships**, а не на одном abstract travel speed.

---

# 17. Что эти цифры НЕ означают

v0.9 пока не говорит:

> станции должны быть в среднем в 100,000 km друг от друга.

И не говорит:

> игрок должен ждать 19 real-time hours.

Simulation time scale, local compression, jump architecture, placement distributions и gameplay cadence будут отдельными design decisions.

Но теперь они обязаны быть математически согласованы.

Например, если `100,000 km` является обычным local logistics leg, world/economy designer обязан знать, что reference loaded freighter физически тратит около `19 h simulation time` при aggressive rest-to-rest profile.

Это затем связано с:

```text
factory consumption rate
market buffer depth
trade profit per trip
crew endurance
maintenance interval
fleet response time
construction supply time
mission deadline scale
```

---

# 18. World-scale acceptance philosophy

После v1.0 Stage 19 должен прогонять минимум:

```text
early-game civilian ship
loaded bulk freighter
miner
patrol/corvette
escort destroyer
cruiser
capital group
tanker/logistics group
```

по маршрутным classes:

```text
station ↔ station
station ↔ resource field
inner ↔ outer system
jump arrival ↔ economic hub
system ↔ neighboring system
regional multi-hop route
```

Для каждого измеряются:

```text
travel time
peak velocity
used delta-v
reaction mass
required reserves
sensor exposure during transit
reinforcement latency
round-trip logistics time
```

Generation distribution считается плохой, если:

- почти все корабли имеют одинаковый operational outcome;
- обычная логистика физически невозможна без постоянного refuel;
- distance не имеет экономического значения;
- наоборот, каждая доставка превращается в бессмысленно долгий bottleneck;
- sensor envelope делает всю generated geometry информационно тривиальной без осознанной причины.

---

# 19. Что v0.9 закрыло архитектурно

## CLOSED: common module contract

Все module families имеют один integration philosophy и общие budgets.

## CLOSED: heavy-impact runtime architecture

Heavy impact больше не является открытым вопросом уровня «какую penetration formula использовать».

Ответ:

```text
calibrated response surfaces
+ explicit calibration domain
+ deterministic interpolation
+ residual physical packets
```

Открыта **калибровка данных**, а не runtime architecture.

## CLOSED: plume/aspect signature architecture

Signature теперь явно многоканальная и aspect-dependent.

Открыты конкретные fusion-plume tables и authored RCS/BRDF data.

## CLOSED: world-scale coupling architecture

Generated distance должен проходить через real acceleration / delta-v / time calculations.

Открыты сами Stage-19 generation distributions.

---

# 20. Что обязательно остаётся до v1.0

v0.9 — **не v1.0**.

Остались обязательные closure items.

## 20.1. Production material catalog

Нужно определить хотя бы reference families:

```text
structural alloy
high-density penetrator material
ceramic / composite protection
sacrificial bumper
splinter liner
radiator material
pressure / tank structure
```

Для каждого нужны физически осмысленные properties и calibration provenance.

## 20.2. Heavy-impact response tables/surfaces

Для production kinetic weapon families должны существовать валидные response domains.

Без них v1.0 не принимается.

## 20.3. Fusion plume calibration

Нужно выбрать physically/setting-consistent plume spectral model или range of authored drive technologies.

`1e-6 / 1e-5 / 1e-4` являются sensitivity sweep, не финальными values.

## 20.4. Aspect signature tables

Нужны authored/data-derived:

```text
RCS(frequency, aspect)
thermal projected area(aspect)
optical BRDF / phase behavior
plume radiance(aspect, throttle)
```

## 20.5. Integrated representative fits

Нужно прогнать всю систему на representative ships и проверить одновременно:

```text
mass
volume
power
heat
crew
ammo
reaction mass
movement
signature
sensors
weapons
protection
logistics
cost inputs
```

## 20.6. v1.0 acceptance matrix

Нужен один consolidated machine-readable baseline и regression harness.

---

# 21. Принцип technology ladder после v0.9

Technology improvement должен менять реальные engineering properties.

Допустимые примеры:

```text
higher specific power reactor
lower detector noise
better radiator material / higher temperature limit
higher exhaust velocity at cost of thrust/power/complexity
better penetrator material / manufacturing tolerance
lighter structural material at increased cost
better automation reducing crew but increasing electronics/power dependence
better ECCM processing at power/compute/thermal cost
```

Плохой пример:

```text
Mk II engine = +25% speed
Mk III sensor = +30% range
advanced armor = +40% HP
```

без физических причин и resource tradeoffs.

---

# 22. Acceptance v0.9

Executable acceptance требует:

1. разные module families сходятся в один mass/volume/power/heat/crew contract;
2. kinetic weapon получает explicit penetrator geometry;
3. heavy impact требует calibrated response surface;
4. прямое BLE extrapolation / `E/armor` не возвращается;
5. plume source выводится из physical thrust/exhaust power + explicit spectral sensitivity;
6. plume aspect реально меняет passive detection result;
7. radiative-efficiency uncertainty остаётся явной sensitivity axis;
8. world-scale travel вычисляется из acceleration + finite delta-v;
9. на достаточно длинном route solver естественно переходит от accel/brake к accel/coast/brake;
10. все benchmark values остаются deterministic и SI.

---

# 23. Переход к Ship Mathematics v1.0

Следующий исследовательский шаг уже не должен называться ещё одной независимой подсистемой.

Он должен быть **closure pass к v1.0 Design Baseline**:

```text
v0.9 integrated architecture
→ material / heavy-impact calibration closure
→ plume / aspect data closure
→ integrated representative fit validation
→ consolidated benchmark + invariant matrix
→ Ship Mathematics v1.0 Design Baseline
```

После принятия v1.0 выполняется уже зафиксированный обязательный roadmap integration pass:

```text
v1.0
→ detailed Stage 17.5
→ physically calibrated Stage 19
→ detailed Stage 21
```

И только после этого Stage 17.5 может стать `ACTIVE`.
