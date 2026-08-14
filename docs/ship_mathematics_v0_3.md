# Star Empires — Ship Mathematics v0.3: Sensors, Guidance and Weapon Interaction

> Статус: **engineering / balance seed v0.3**  
> Дата: **2026-08-15**  
> Связан с: `docs/ship_hull_module_and_fleet_doctrine.md`, `docs/ship_mathematics_v0_1.md`, `docs/ship_mathematics_v0_2.md`, `docs/benchmarks/ship_reference_designs_v0_2.json`  
> Назначение: заменить абстрактные `accuracy/range/PD chance` физически параметризованной моделью трека, времени полёта, манёвра, наведения, лазерного dwell и локальной защиты.

---

## 1. Главный принцип v0.3

В authoritative combat model не должно существовать независимых магических параметров вида:

```text
weaponAccuracy = 0.75
weaponRange = 1200
missileHitChance = 0.60
pointDefenseChance = 0.40
```

Вместо этого исход хода оружия выводится из измеримых или инженерно осмысленных величин:

```text
sensor measurement
→ track estimate + covariance
→ weapon pointing / guidance
→ time of flight
→ target maneuver envelope
→ interception / beam dwell / impact
→ local protection response
→ subsystem / compartment damage
```

UI может показывать игроку агрегированные показатели вроде «эффективная дальность» или «качество огневого решения», но они являются **derived values для конкретной цели и текущей ситуации**.

---

## 2. Граница между внешней физикой и design assumptions

### 2.1. Что опирается на реальные инженерные принципы

Для v0.3 используются следующие реальные идеи:

1. Навигационные/трековые фильтры оценивают не только состояние объекта, но и неопределённость этого состояния через covariance matrix. NASA описывает именно такой подход для Extended Kalman Filters Orion/Artemis и в Navigation Filter Best Practices.
2. Proportional Navigation является реальным семейством законов наведения и исторически исследовалась NACA/NASA для target-seeking missiles.
3. Размер diffraction-limited optical spot зависит от wavelength, aperture и distance; aperture уменьшает beam divergence. NASA optical/laser-communication work отдельно подчёркивает необходимость точного pointing и влияние aperture/divergence.
4. Hypervelocity protection не сводится к «HP брони». NASA MMOD work использует material/configuration-specific ballistic-limit equations и экспериментальную проверку. Разные empirical penetration equations могут существенно расходиться вне валидированного диапазона.

### 2.2. Что является технологическим seed Star Empires

Следующие числа **не выдаются за характеристики современной техники**:

- 0.05 µrad benchmark fire-control angular uncertainty;
- future-warship pointing stability;
- missile fusion/micro-fusion propulsion;
- seeker ranges;
- terminal maneuver capability;
- laser power levels;
- material fluence thresholds, пока не создан отдельный material catalog.

Они являются physically parameterized balance seeds и обязаны оставаться явно настраиваемыми.

### 2.3. Источники для инженерной опоры

- NASA NTRS 20230000548 — *Extended Kalman Filter Performance on the Artemis-1 Mission*.
- NASA NTRS 20250002787 — *Navigation Filter Best Practices*.
- NACA/NASA NTRS 20090033671 — *Paths of Target Seeking Missiles in Two Dimensions*.
- NASA NTRS 20110003031 — *Simplified Architecture for Precise Aiming of a Deep-Space Communication Laser Transceiver*.
- NASA Technical Publication 20030068423 — *Meteoroid/Debris Shielding*.
- NASA-TM-103565 / NTRS 19920007464 — *Single wall penetration equations*.

Эти работы определяют физическую логику и ограничения; они не являются прямым «балансным справочником» для вымышленного оружия Star Empires.

---

## 3. Track State: оружие стреляет не по Entity, а по оценке состояния

Каждая сторона боя имеет собственные track records.

Reference 2D combat state:

```text
x = [positionX_m,
     positionY_m,
     velocityX_mps,
     velocityY_mps]
```

и covariance:

```text
P = 4 × 4 symmetric covariance matrix
```

Для каждого трека также хранятся:

```text
trackId
estimatedState
covariance
measurementTimestampS
lastUpdateTimestampS
sourceMask
classificationConfidence
identityConfidence
trackContinuityId
```

### 3.1. Prediction

Между измерениями трек распространяется вперёд:

```text
x(t + dt) = F × x(t)
P(t + dt) = F × P × Fᵀ + Q
```

`Q` — process noise: насколько модель допускает неизвестное ускорение и манёвр цели.

Это означает, что **старый трек сам по себе ухудшается**, даже если цель всё ещё существует.

### 3.2. Measurement update

Сенсор выдаёт не «обнаружил = true», а measurement с uncertainty:

```text
z
R
measurementTime
sensorId
```

После update covariance уменьшается в тех компонентах, которые реально измерял сенсор.

### 3.3. Почему это важно для флота

Recon/EW frigate может не наносить много damage, но давать флоту:

- более частые measurements;
- меньшую angular/range uncertainty;
- меньшее track age;
- лучшее velocity estimate;
- устойчивость к ECM;
- track continuity после временной потери контакта.

Именно поэтому внешний target-quality track способен увеличить **реальную** effective range тяжёлого оружия без `+20% range`.

---

## 4. Track quality не является одним процентом

Для UI допускается агрегированный `TrackQuality`, но authoritative данные должны хранить uncertainty.

Минимальный useful combat representation:

```text
positionCovarianceM2
velocityCovarianceM2PerS2
lastMeasurementAgeS
angularMeasurementSigmaRad
rangeMeasurementSigmaM
```

### 4.1. Benchmark precision-fire track v0.3

Для первых deterministic acceptance calculations принимается:

```text
fireControlAngularSigma = 0.05 µrad = 5e-8 rad
velocitySigma = 0.03 m/s
reactionDelayAfterDetectingShot = 3 s
```

Это **future-tech balance seed**, а не заявление о современной sensor capability.

В дальнейшем фактическая covariance должна выводиться из SensorDefinition + geometry + signal strength + ECM + fusion of observations.

---

## 5. Target projected size

Попадание зависит от фактической projected target area, а не от enum класса.

Для benchmark используется боковая/усреднённая проекция:

```text
projectedArea ≈ beam × height × 0.65
referenceEquivalentRadius = sqrt(projectedArea / π)
```

| Hull | Benchmark projected area | Equivalent radius |
|---|---:|---:|
| Corvette | 135.2 m² | 6.56 m |
| Frigate | 312.0 m² | 9.97 m |
| Destroyer | 618.8 m² | 14.03 m |
| Cruiser | 1 608.8 m² | 22.63 m |
| Battlecruiser | 2 925 m² | 30.51 m |
| Battleship | 6 077.5 m² | 43.98 m |

Production implementation должна получать projected silhouette из orientation/hull geometry или из заранее подготовленных directional cross-section samples.

---

## 6. Unguided kinetic fire

### 6.1. У кинетики нет физической стены `maxRange`

Projectile в вакууме продолжает полёт. Ограничением становится вероятность встретиться с целью.

```text
timeOfFlight = distance / muzzleVelocity
```

За это время растёт uncertainty будущего положения цели.

### 6.2. Benchmark aim-plane uncertainty

Для v0.3 используется аналитический surrogate:

```text
sigmaAngularM = rangeM × sqrt(trackAngularSigmaRad²
                           + weaponPointingSigmaRad²)

sigmaVelocityM = velocitySigmaMps × timeOfFlightS

maneuverTimeS = max(0, timeOfFlightS - reactionDelayS)

sigmaManeuverM = 0.5 × maneuverSigmaAccelerationMps2 × maneuverTimeS²

sigmaAimPlaneM = sqrt(
    sigmaAngularM²
  + sigmaVelocityM²
  + sigmaManeuverM²)
```

Первый benchmark принимает:

```text
maneuverSigmaAcceleration = 0.15 × availableLateralAcceleration
```

Это не означает, что target randomly rolls dice. Это аналитический surrogate для uncertainty до перехода к fixed-step maneuver scenarios.

### 6.3. Benchmark probability integral

Если aim-plane error приближён isotropic 2D Gaussian, а projected target — кругом radius `r`:

```text
P(hit) = 1 - exp(-r² / (2 × sigmaAimPlane²))
```

Эта формула используется **только для fast benchmark curve**. Production solver сможет интегрировать реальную covariance ellipse и silhouette.

### 6.4. Weapon pointing seeds

| Weapon | Projectile | Muzzle velocity | Pointing sigma seed |
|---|---:|---:|---:|
| M coilgun | 25 kg | 15 km/s | 0.25 µrad |
| L coilgun | 150 kg | 20 km/s | 0.10 µrad |
| XL capital kinetic | 1 000 kg | 30 km/s | 0.04 µrad |

Target lateral-acceleration benchmark:

| Hull | Lateral acceleration used by benchmark |
|---|---:|
| Corvette | 0.35 m/s² |
| Frigate | 0.25 m/s² |
| Cruiser | 0.12 m/s² |
| Battlecruiser | 0.10 m/s² |
| Battleship | 0.06 m/s² |

### 6.5. Получившаяся 50% single-shot effective range

При precision-quality track выше:

| Weapon → target | Range where benchmark P(hit) ≈ 50% |
|---|---:|
| M coilgun → Corvette | ~263 km |
| M coilgun → Frigate | ~363 km |
| L coilgun → Cruiser | ~983 km |
| L coilgun → Battlecruiser | ~1 234 km |
| XL kinetic → Battleship | ~2 819 km |

Это не hard range. Если target не маневрирует, surprise shot происходит до реакции, track лучше, либо salvo покрывает несколько возможных trajectories, effective range увеличивается.

И наоборот, ECM, stale track и aggressive maneuver сокращают её.

**Вывод:** capital gun естественно является anti-capital weapon, а не универсальной пушкой против всех размеров.

---

## 7. Guided weapons v0.3

Guided weapon покупает способность корректировать prediction error ценой:

- собственной массы;
- reaction mass;
- двигателя;
- seeker;
- computing;
- связи;
- стоимости;
- уязвимости для interception;
- ограниченного delta-v.

### 7.1. Reference missile propulsion seeds

| Weapon | Wet mass | Dry mass | Exhaust velocity | Δv | Thrust | Initial accel | Final accel | Burn time | Jet power |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| S fleet interceptor | 1.2 t | 0.7 t | 30 km/s | 16.17 km/s | 180 kN | 150 m/s² | 257 m/s² | 83.3 s | 2.7 GW |
| Extended area interceptor | 4 t | 2 t | 35 km/s | 24.26 km/s | 400 kN | 100 m/s² | 200 m/s² | 175 s | 7.0 GW |
| M anti-ship missile | 12 t | 5 t | 40 km/s | 35.02 km/s | 600 kN | 50 m/s² | 120 m/s² | 466.7 s | 12 GW |
| L heavy torpedo | 120 t | 40 t | 50 km/s | 54.93 km/s | 3.0 MN | 25 m/s² | 75 m/s² | 1 333 s | 75 GW |

Эти двигатели являются setting technology и явно выше современной missile propulsion.

### 7.2. Guidance law

Первый deterministic solver использует proportional-navigation family как baseline:

```text
aCommand = N × closingVelocity × lineOfSightRate
```

с:

```text
N = 4.0        // balance seed
|aCommand| <= availableLateralAcceleration
```

Важно: solver применяет реальную mass depletion, thrust и remaining delta-v. PN не даёт missile бесплатной lateral velocity.

### 7.3. Guidance phases

```text
launch
→ inertial / predicted-intercept burn
→ network midcourse updates
→ local seeker acquisition
→ proportional-navigation terminal phase
→ intercept / miss
```

Потеря datalink не уничтожает missile автоматически, но увеличивает uncertainty до terminal acquisition.

### 7.4. Kinematic sanity checks

M anti-ship missile при 50 m/s² initial acceleration и Δv ~35 km/s:

- ideal straight-line 2 000 km intercept from low initial relative speed требует порядка 283 s и ~14.1 km/s velocity gain;
- 5 000 km — порядка 447 s и ~22.4 km/s в constant-acceleration approximation;
- обе геометрии помещаются в nominal Δv budget до учёта terminal maneuver.

S interceptor против incoming missile с closing speed ~15 km/s имеет достаточно acceleration/Δv, чтобы выполнять head-on engagements на тысячах километров, но doctrine должна сохранять несколько km/s terminal reserve вместо полного расходования fuel на прямой разгон.

---

## 8. Defensive missile architecture

v0.2 уже дала Escort Destroyer:

```text
4 × S point-defense laser
2 × M fleet interceptor battery
1 × L area-defense interceptor battery
```

Battleship:

```text
6 × S point-defense laser
2 × M fleet interceptor battery
1 × L area-defense interceptor battery
```

v0.3 уточняет, почему dedicated destroyer остаётся полезен: он добавляет почти capital-grade **defensive magazine depth и launch geometry** за значительно меньшую стоимость, а не повышает линкору скрытый defense modifier.

### 8.1. Battery seed

M fleet-interceptor battery:

```text
24 × S interceptors
2 launch cells
4 s launch-cycle per cell
2 simultaneous local terminal-support channels
```

L area-defense battery:

```text
48 × extended interceptors
4 launch cells
6 s launch-cycle per cell
6 simultaneous terminal-support channels
```

Autonomous terminal seekers позволяют иметь больше missiles in flight, чем continuous ship guidance channels; channels нужны для midcourse/terminal support under degraded seeker conditions.

### 8.2. Magazine implication

Reference Escort Destroyer carries roughly:

```text
2 × 24 S interceptors = 48
1 × 48 extended interceptors = 48
--------------------------------
96 dedicated defensive guided weapons
```

до дополнительных reload/reserve вариантов.

Reference Battleship имеет сопоставимый nominal defensive guided-weapon count, но Escort Destroyer удваивает defense depth группы, добавляет другой baseline/angle к sensor network и принимает на себя часть launcher saturation.

Следствие:

> battleship не обязан быть беспомощен без escort, но escort должен резко увеличивать число последовательных salvo, которые capital group способна пережить до magazine exhaustion.

---

## 9. Laser model

Laser не получает отдельную accuracy roll. Требуются:

```text
wavelengthM
beamOutputPowerW
apertureM
pointingJitterRad
opticalEfficiency
trackCovariance
targetAbsorptivity
localDwellTimeS
```

### 9.1. Diffraction + jitter benchmark

Для circular aperture first-order spot approximation:

```text
diffractionRadiusM ≈ 1.22 × wavelengthM × rangeM / apertureM
jitterRadiusM = pointingJitterRad × rangeM
spotRadiusM = sqrt(diffractionRadiusM² + jitterRadiusM²)
```

v0.3 seed:

```text
wavelength = 1.064 µm
pointingJitter = 0.05 µrad
```

### 9.2. Irradiance

```text
irradianceWPerM2 = beamPowerW / (π × spotRadiusM²)
absorbedPowerDensity = irradiance × absorptivity
absorbedFluence = absorbedPowerDensity × dwellTime
```

Target damage comes from local material response, а не из arbitrary laser damage number.

### 9.3. Laser hardware seeds

| Laser | Beam power | Aperture | Intended role |
|---|---:|---:|---|
| S PD laser | 5 MW | 0.5 m | missile / craft defense |
| M laser battery | 30 MW | 1.5 m | PD + exposed systems / light craft |
| L heavy laser | 100 MW | 4.0 m | sustained precision attack |

### 9.4. Missile-vulnerability benchmark

Пока material catalog не создан, benchmark использует **не канонический material seed**:

```text
missile vulnerable-surface disable fluence = 8 MJ/m² absorbed
benchmark absorptivity = 0.50
```

S PD laser:

| Range | Spot radius | Incident irradiance | Ideal continuous dwell to 8 MJ/m² absorbed |
|---|---:|---:|---:|
| 100 km | 0.26 m | 23.6 MW/m² | 0.68 s |
| 200 km | 0.52 m | 5.90 MW/m² | 2.71 s |
| 300 km | 0.78 m | 2.62 MW/m² | 6.10 s |
| 500 km | 1.30 m | 0.94 MW/m² | 16.95 s |
| 750 km | 1.95 m | 0.42 MW/m² | 38.13 s |
| 1 000 km | 2.60 m | 0.236 MW/m² | 67.78 s |

Это хорошо создаёт layered defense: laser физически может воздействовать дальше, но при быстром incoming threat practical dwell window резко сокращается.

### 9.5. Heavy-laser armor benchmark

Только как balance seed до material catalog:

```text
military outer-layer local damage fluence = 250 MJ/m² absorbed
benchmark absorptivity = 0.25
```

L heavy laser 100 MW / 4 m:

| Range | Spot radius | Incident irradiance | Ideal dwell to seed threshold |
|---|---:|---:|---:|
| 1 000 km | 0.33 m | 295 MW/m² | 3.39 s |
| 2 000 km | 0.66 m | 73.8 MW/m² | 13.55 s |
| 3 000 km | 0.99 m | 32.8 MW/m² | 30.48 s |
| 5 000 km | 1.64 m | 11.8 MW/m² | 84.68 s |

Production combat must additionally model target rotation, deliberate roll, occlusion, beam walk, local ablation plume effects if retained, and inability to hold one exact surface patch indefinitely.

---

## 10. Armor / protection v0.3

### 10.1. Не вводить универсальную penetration formula вне её валидности

NASA hypervelocity work прямо показывает, что penetration / ballistic-limit equations являются configuration- и material-dependent и что разные empirical equations могут заметно расходиться; shielding performance проверяется experiment/hydrocode.

Поэтому v0.3 **не экстраполирует** ISS/MMOD equations на 150 kg projectile at 20 km/s или 1 000 kg projectile at 30 km/s.

### 10.2. Authoritative armor geometry

Хранить:

```text
ArmorLayer {
  materialId
  thicknessM
  densityKgPerM3
  arealDensityKgPerM2
  standOffDistanceM
  incidenceAngleRad
  coverageRegion
}
```

Derived line-of-sight areal density:

```text
LOS_arealDensity = Σ(layerArealDensity / cos(incidenceAngle))
```

с geometric clamp для grazing cases.

### 10.3. Два разных damage regimes

#### Debris / fragments / intercepted missile remnants

Использовать material-specific ballistic-limit models **только внутри documented validity envelope** конкретного model/data table.

Это главная область, где Whipple/spaced shielding физически полезно.

#### Dedicated heavy penetrator / intact anti-ship missile

Не превращать armor в HP subtraction.

Pipeline:

```text
impact point
→ local armor stack
→ incidence geometry
→ projectile mass / area / velocity / material
→ calibrated penetration-response table/model
→ residual debris/penetrator state
→ compartment cone / local subsystem damage
```

До появления validated penetration table direct heavy hit может использовать conservative `overmatch / uncertain / stopped` classification, но обязан сохранять физические input values для последующей замены solver без изменения content schema.

### 10.4. Kinetic energy scale

Существующие v0.1 weapons:

```text
M: 25 kg @ 15 km/s  -> 2.81 GJ
L: 150 kg @ 20 km/s -> 30 GJ
XL: 1000 kg @ 30 km/s -> 450 GJ
```

Следовательно, capital survivability остаётся layered-defense problem, а не способностью face armor поглощать сотни прямых XL hits.

---

## 11. Damage localization

После penetration/dwell событие должно создавать physical damage packet:

```text
DamagePacket {
  worldImpactPoint
  localNormal
  incomingDirection
  kineticEnergyJ
  momentumNs
  thermalEnergyJ
  fragmentConeRad
  residualMassKg
  residualVelocityMps
  penetrationDepthOrRegion
}
```

Затем compartment model определяет:

- какие модули лежат на пути;
- повреждены ли power/data/coolant lines;
- возникли ли secondary fragments;
- есть ли magazine/reactor/crew-space consequence;
- локализован ли ущерб переборками.

Это даёт реальную ценность разнесению систем и redundancy.

---

## 12. ECM / ECCM

ECM не должно давать `enemy accuracy -20%` напрямую.

Оно действует на measurement / track pipeline:

```text
measurement noise R ↑
false-track probability ↑
track continuity ↓
seeker acquisition range ↓
classification confidence ↓
```

ECCM, multi-sensor fusion и external observers уменьшают эти эффекты.

Таким образом Recon/EW Frigate способен одновременно:

- ухудшить enemy firing solution;
- улучшить friendly covariance;
- помочь missiles получить midcourse updates;
- сохранить track после манёвра/частичного sensor denial.

---

## 13. Deterministic simulation architecture

Production combat interaction должен оставаться deterministic при одинаковом seed/state.

Предлагаемый pipeline fixed tick:

```text
1. propagate physical bodies
2. propagate track estimates/covariance
3. create sensor measurements
4. deterministic sensor-fusion update
5. weapon fire-control decisions
6. missile guidance + mass depletion
7. beam dwell accumulation
8. projectile propagation
9. interception / collision events
10. local armor response
11. compartment/module damage
12. thermal/power consequences
```

Randomness, где она действительно нужна для unresolved phenomena, проходит только через authoritative seeded RNG и сохраняется в GameState continuation.

---

## 14. Data model additions

### TrackEstimate

```text
trackId
observerFactionId
estimatedPositionXM
estimatedPositionYM
estimatedVelocityXMps
estimatedVelocityYMps
covariancePacked[]
lastMeasurementTimeS
classificationConfidence
identityConfidence
sourceMask
```

### KineticWeaponDefinition

```text
projectileMassKg
muzzleVelocityMps
weaponPointingSigmaRad
muzzleVelocitySigmaMps
cycleTimeS
storedEnergyJ
rechargePowerW
firingArc
```

### GuidedWeaponDefinition

```text
wetMassKg
dryMassKg
exhaustVelocityMps
thrustN
maxLateralAccelerationMps2
terminalReserveDeltaVMps
seekerAngularSigmaRad
seekerAcquisitionRangeM
datalinkSupported
navigationConstant
lethalRadiusM
```

### LaserWeaponDefinition

```text
beamOutputPowerW
wavelengthM
apertureM
pointingJitterRad
maxContinuousDwellS
slewRateRadPerS
opticalEfficiency
wasteHeatW
```

### ArmorMaterialDefinition

```text
densityKgPerM3
specificHeatJPerKgK
meltingTemperatureK
phaseChangeEnergyJPerKg
laserAbsorptivityByWavelength
validatedImpactModelId
validatedImpactEnvelope
```

---

## 15. v0.3 acceptance scenarios

### A. Same gun, different target

M coilgun precision-track 50% range must be substantially shorter against Corvette than L/XL weapons against capital targets because maneuver uncertainty grows during time-of-flight.

Expected benchmark:

```text
M -> Corvette ~263 km
L -> Cruiser ~983 km
XL -> Battleship ~2819 km
```

### B. Stale track

Holding range constant while increasing track age / covariance must reduce P(hit) without changing weapon definition.

### C. Recon support

Adding recon-quality measurements before firing must reduce covariance and increase effective range of allied direct-fire weapons.

### D. Guided weapon correction

An anti-ship missile with remaining Δv should correct prediction error that would cause an unguided projectile to miss, until target maneuver consumes its terminal reserve.

### E. Missile defense layering

Long-range interceptor → area-defense interceptor → laser PD must be processed as separate physical engagements. A leaker from one layer continues into the next; there is no single global `PD chance`.

### F. Magazine exhaustion

Repeated salvo must eventually deplete interceptor magazines even if no defender is destroyed. Escort Destroyer therefore increases fleet endurance independent of DPS.

### G. Laser dwell

Moving the same missile from 200 km to 500 km must increase PD dwell time according to diffraction/pointing geometry, not an arbitrary range penalty.

### H. Armor localization

Two identical hits at different locations may have very different consequences if one crosses empty/spaced volume and another intersects magazine, reactor, sensor trunk or propulsion subsystem.

### I. ECM

ECM changes measurement covariance / seeker acquisition, while weapons themselves retain identical mechanical parameters.

---

## 16. Balance conclusions v0.3

1. **Direct-fire kinetic weapons become target-size dependent naturally.**
2. **Missiles are long-range correction-capable weapons, but pay mass, Δv, cost and interception risk.**
3. **Lasers are line-of-sight precision weapons whose practical range is governed by aperture, wavelength, pointing and dwell.**
4. **Escort Destroyer gains a strong doctrine niche through magazine depth, launcher throughput and defensive geometry rather than direct DPS.**
5. **Recon/EW Frigate gains a strong niche through covariance control and target-quality tracks.**
6. **Battleship remains lethal but cannot substitute for distributed sensors and defensive magazines indefinitely.**
7. **Armor protects primarily by localizing/mitigating damage and defeating fragments/light threats; heavy intact hypervelocity hits remain dangerous.**
8. **There is no universal weapon range. Effective range is an interaction between weapon, track, target and geometry.**

---

## 17. Что v0.3 намеренно ещё не объявляет final

Нужна отдельная calibration phase для:

- actual sensor SNR / detection-range equations;
- radar vs lidar vs passive IR/optical measurement models;
- exact seeker acquisition ranges;
- final missile lateral-thrust / divert-control model;
- final material laser thresholds;
- direct heavy-penetrator hydrocode/calibrated surrogate;
- detailed compartment geometry;
- exact ECM waveform/electromagnetic model;
- final missile kill radius / fragmentation model;
- cost/industrial balance of each munition.

Эти параметры нельзя честно закрыть одной красивой цифрой без следующего уровня моделирования.

---

## 18. Следующий шаг

`Ship Mathematics v0.4` должен превратить этот interaction model в **sensor + salvo benchmark harness**:

```text
SensorDefinition
→ measurements with R
→ track covariance evolution
→ fire-control solution
→ 2D missile fixed-step PN trajectories
→ interceptor assignment
→ launcher throughput
→ laser dwell scheduling
→ leakers
→ impact location
→ damage packets
```

Минимальные scenario matrices:

1. 24 torpedo corvettes → battleship alone;
2. те же corvettes → battleship + escort destroyer;
3. cruiser alone vs cruiser + recon/EW frigate;
4. carrier strike group vs area-defense group;
5. stale track vs precision track kinetic fire;
6. repeated missile waves until magazine exhaustion;
7. ECM-on vs ECM-off track degradation.

После этого можно впервые строить настоящие `survival probability / missiles expended / time-to-kill / magazine endurance / cost exchange` curves и уже по ним менять slots и module stats.