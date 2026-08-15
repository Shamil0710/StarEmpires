# Star Empires — Ship Mathematics v0.8: Sensors, Signatures, Tracks & Electronic Warfare

> Статус: **executable engineering / design seed v0.8**  
> Дата: **2026-08-15**  
> Основание: `docs/ship_mathematics_v0_1.md`–`v0_7.md`  
> Код: `src/test/java/com/spacesim/combat/benchmark/ShipMathematicsV08SensorTrackHarness.java`  
> Snapshot: `docs/benchmarks/sensor_track_reference_v0_8.json`

---

## 1. Задача v0.8

v0.3 уже зафиксировал правильную архитектуру:

```text
sensor measurement
→ track estimate + covariance
→ fire-control solution
```

но его `fireControlAngularSigma = 5e-8 rad` оставался future-tech seed.

v0.8 начинает выводить качество измерений из реальных сигнальных величин и геометрии.

Главное правило:

> **обнаружение, классификация, трек и огневое решение — разные состояния информации.**

Корабль может быть очевидным тепловым контактом на десятках или сотнях миллионов километров и при этом не иметь достаточно точной дальности/скорости для выстрела кинетическим оружием.

Authoritative pipeline:

```text
physical emission / reflection
→ propagation loss
→ aperture / antenna gain
→ detector signal + noise + interference
→ SNR
→ measurement + measurement covariance R
→ track prediction/update P
→ track age / process noise
→ weapon-specific future-state uncertainty
→ fire-control permission / quality
```

Никакого отдельного `sensorRange = 5000 km` или `stealth = 40`.

---

## 2. Инженерная опора и границы

Используются первичные NASA/NTRS источники как физическая и архитектурная опора:

- NASA-RP-1241 / NTRS `19900014464`, *Sensor performance analysis*: electro-optical sensor performance, Planck radiance, signal-to-noise, detector/optical parameters.
- NTRS `19880025010`, *Optical tracking using charge-coupled devices*: precise point-source centroiding; experimentally demonstrated sub-pixel center finding.
- NTRS `20230000548`, *Extended Kalman Filter Performance on the Artemis-1 Mission*: state estimate + covariance, dynamics propagation and measurement updates.
- NTRS `19840029651`, *Space Shuttle Orbiter onboard rendezvous navigation*: radar range/range-rate/line-of-sight measurements processed by a Kalman filter, including rejection of improbable measurements.
- NTRS `19940031050`, *Spacecraft-spacecraft radio-metric tracking*: reliable detection depends on received signal power to noise-density ratio and integration/acquisition conditions.
- NASA-TM-82092 / NTRS `19820015505`: radar equation / scattering cross-section formulation.
- NTRS `20210026612`, *Optical Navigation ... Outlier Rejection*: residual-based measurement rejection improves navigation estimation.

Эти работы **не** задают характеристики военных сенсоров Star Empires.

Следующие величины — explicit setting/balance seeds:

- 1.5-m military passive IR aperture;
- benchmark detector background/noise;
- 10-m / 20-MW active radar;
- target RCS seeds;
- jammer EIRP spectral density;
- ECCM overlap factor;
- 50 nrad systematic optical floor.

Они хранятся отдельно и должны оставаться data-driven.

---

## 3. Информационные состояния

Production track system должен различать минимум:

### 3.1. DETECTED / CONTACT

Сенсор получил статистически значимый signal excess.

Это означает только:

```text
в данном measurement space что-то есть
```

а не:

```text
это battleship класса X на дальности Y с точной скоростью Z
```

### 3.2. CLASSIFIED

Накоплены признаки:

- multi-band thermal color;
- apparent intensity / aspect changes;
- active-emission fingerprint;
- acceleration / maneuver pattern;
- radar cross-section observations;
- known catalog comparison;
- previous track continuity.

Классификация имеет собственную confidence и может быть ошибочной.

### 3.3. TRACKED

Есть state estimate:

```text
x = [positionX, positionY, velocityX, velocityY]
P = covariance
```

Track может существовать после временной потери direct detection, но covariance растёт.

### 3.4. FIRE_CONTROL QUALITY

Огневое решение не является отдельным булевым свойством цели.

Оно вычисляется для:

```text
current track covariance
+ track age
+ weapon time of flight
+ target maneuver model
+ weapon pointing / guidance
```

Один и тот же track может быть достаточен для guided missile mid-course update и недостаточен для unguided kinetic shot.

---

## 4. Thermal / IR signature

### 4.1. Heat cannot simply disappear

Связь с v0.6:

```text
subsystems generate heat
→ local coolant
→ ship heat bus
→ radiators / thermal store
→ photons leave ship
```

Снижение instantaneous radiator emission возможно только через физическую цену:

- уменьшить power load;
- накопить heat в finite thermal store;
- изменить radiator temperature / area;
- изменить orientation/view factor;
- повредить или убрать радиаторы, ухудшая endurance.

Следовательно, `stealth mode` не должен быть toggle с бесплатным уменьшением detection range.

### 4.2. Reference hot radiator

Из v0.6:

```text
T = 1100 K
```

Для Blackbody/Planck spectrum benchmark доля мощности:

```text
3–5 µm  ≈ 0.3507951
8–12 µm ≈ 0.0726704
```

В v0.8 thermal detector использует 3–5 µm как один reference channel.

Реальный production sensor должен поддерживать несколько bands и background, зависящий от направления обзора.

---

## 5. Passive IR sensor seed

```text
aperture                 = 1.5 m
reference band           = 3–5 µm
effective photon λ       = 4 µm
optical throughput       = 0.35
quantum efficiency       = 0.70
integration              = 1 s
background-equivalent    = 1.0e6 e-/s
read noise               = 100 e- RMS
detection threshold      = SNR 5
systematic angular floor = 5e-8 rad
```

Background/noise здесь является **benchmark sensor seed**, а не универсальным deep-space background.

Production background должен учитывать:

- stars / dense star fields;
- planet/asteroid limb;
- zodiacal / dust background, если моделируется;
- hot objects in field;
- detector temperature;
- stray light;
- field of view.

---

## 6. Passive signal equation

Reference unresolved source:

```text
P_band_received
= P_radiated
× f_band(T)
× A_aperture
/ (4πR²)
× opticalThroughput
```

Photon energy:

```text
E_photon = h c / λ
```

Signal photoelectrons:

```text
N_signal
= P_band_received × integration / E_photon × QE
```

Benchmark SNR:

```text
SNR = N_signal
      / sqrt(N_signal + N_background + readNoise²)
```

Это intentionally simple photon/background model; detector architecture later may add dark current, pixel sampling and correlated noise explicitly.

---

## 7. Passive thermal detection ranges

Используются уже принятые v0.2 installed continuous waste-heat values, а не новые arbitrary signature points:

| Design | Waste heat used as hot-signature seed | 5σ passive contact range |
|---|---:|---:|
| Torpedo Corvette | 11 MW | **23.05 million km** |
| Recon/EW Frigate | 76.5 MW | **60.79 million km** |
| Escort Destroyer | 60.8 MW | **54.20 million km** |
| Battleship | 1.3873 GW | **258.89 million km** |

Это не означает, что корабль можно атаковать на этой дистанции.

На detection boundary angular uncertainty уже около:

```text
0.6507 µrad
```

а **дальность одиночным bearing-only passive observation напрямую не измеряется**.

### Design consequence

Capital ship трудно сделать «невидимым» при нормальной энергетической работе.

Но:

```text
detected very far away
≠ classified
≠ precise range
≠ velocity solution
≠ fire-control track
```

Это позволяет сохранить одновременно физическую обнаруживаемость горячих кораблей и полезность recon / sensor networks.

---

## 8. Angular measurement quality

Diffraction scale:

```text
θ_diff ≈ 1.22 λ / D
```

Для 1.5 m / 4 µm:

```text
θ_diff ≈ 3.253 µrad
```

Point-source centroiding может быть существенно точнее полной diffraction width при хорошем SNR. NASA/JPL optical trackers демонстрировали sub-pixel center finding; конкретный Star Empires future-tech floor остаётся seed.

Benchmark:

```text
σ_angle = max(5e-8 rad, θ_diff / SNR)
```

При battleship на 10 million km:

```text
SNR ≈ 1612
σ_angle hits floor = 5e-8 rad
```

То есть strong passive observation физически способен воспроизвести прежний v0.3 `fireControlAngularSigma = 0.05 µrad`.

Но всё ещё отсутствует прямой range measurement.

---

## 9. Distributed passive sensing / triangulation

Два bearing observers с пространственной базой дают range geometry.

Для простой symmetric far-target approximation:

```text
σ_range ≈ sqrt(2) × R² × σ_angle / baseline

σ_cross ≈ R × σ_angle / sqrt(2)
```

Reference:

```text
target range = 10 million km
σ_angle      = 5e-8 rad
```

| Observer baseline | Approx range sigma | Approx cross-track sigma |
|---:|---:|---:|
| 100 000 km | **70.7 km** | 354 m |
| 1 000 000 km | **7.07 km** | 354 m |

Увеличение baseline в десять раз уменьшает range uncertainty примерно в десять раз без какого-либо `recon bonus`.

### Fleet consequence

Разнесённые pickets / recon craft / stations полезны потому, что дают **геометрию измерения**.

Один большой sensor aperture на battleship не полностью заменяет spatially separated observers.

---

## 10. Active radar reference

Reference military active sensor seed:

```text
wavelength              = 0.03 m (10 GHz class)
aperture diameter       = 10 m
aperture efficiency     = 0.60
transmit power during dwell = 20 MW
system noise temperature    = 500 K
coherent dwell          = 1 s
waveform bandwidth      = 20 MHz
```

Antenna gain approximation:

```text
G = η × (πD/λ)²
  ≈ 657 974
  ≈ 58.18 dBi
```

Monostatic echo:

```text
P_r = P_t G² λ² σ
      / ((4π)³ R⁴)
```

Matched/coherent benchmark:

```text
SNR ≈ P_r × dwell / N0
N0  = k T_system
```

RCS обязательно остаётся frequency/aspect-dependent physical input.

Текущие seeds только для sensitivity:

```text
Corvette   = 100 m²
Battleship = 10 000 m²
```

Они не являются canonical geometric cross sections.

---

## 11. Active radar benchmark ranges

При `SNR >= 5` и 1 s dwell:

| Target RCS seed | Detection / ranging envelope |
|---:|---:|
| 100 m² | **326 595 km** |
| 10 000 m² | **1 032 783 km** |

Для 100-m² target на 300 000 km:

```text
SNR ≈ 7.023
20-MHz range resolution ≈ 7.495 m
benchmark range sigma ≈ 2.00 m
```

Это показывает другую роль active sensor:

> passive IR обнаруживает горячий объект на намного большей дистанции; active radar покупает гораздо более точный range/range-rate measurement на более короткой дистанции.

---

## 12. Active emission reveals the observer

Radar echo имеет two-way propagation:

```text
~ 1 / R⁴
```

А прямой radar illumination, принимаемый целью внутри main beam:

```text
~ 1 / R²
```

Reference 1-m receiving aperture на расстоянии 1 million km внутри главного луча получает benchmark direct signal:

```text
SNR ≈ 7.15e13 over 1 s
```

В то же время echo от `10 000 m²` target на той же дальности у radar:

```text
SNR ≈ 5.69
```

Разница превышает `1e12` по SNR.

Это **не** означает, что radar виден всем вокруг на такой мощности: v0.8 считает main beam. Side lobes, beam steering pattern и passive intercept geometry должны быть отдельными antenna-pattern data.

Но цель, которую активно освещают, практически неизбежно узнаёт об этом задолго до того, как echo становится лёгким measurement.

### Tactical consequence

Active sensor mode — решение с ценой:

```text
better range / Doppler / classification
vs
emission disclosure
```

---

## 13. ECM: interference, not a percentage debuff

Noise jammer влияет на receiver через реальную принятую spectral power density.

```text
J0_received
= jammerEirpSpectralDensity
× G_receiver
× λ²
/ ((4π)² R²)
```

Sensor sees:

```text
N0_effective = kT + overlap × J0_received
```

и:

```text
SNR = signalEnergy / N0_effective
```

Reference EW seed:

```text
jammer EIRP spectral density = 0.10 W/Hz
```

Это setting value, не характеристика реального существующего jammer.

### 13.1. 300 000 km / 100 m² target

No jammer:

```text
SNR ≈ 7.023
→ detection
```

Full waveform overlap:

```text
SNR ≈ 0.0116
→ radar denied
```

Reference ECCM seed снижает effective overlap до:

```text
0.001
```

За 1 s:

```text
SNR ≈ 4.38
→ still below 5σ benchmark
```

Если sensor платит 2 s coherent dwell:

```text
SNR ≈ 8.76
→ reacquired
```

### Design consequence

ECCM не является:

```text
+40% sensor strength
```

Это реальные способы уменьшить effective interference или увеличить processing gain:

- waveform agility;
- coding;
- narrower matched processing;
- longer dwell;
- spatial filtering / multiple apertures;
- cooperative sensor geometry.

И каждый имеет цену в scan rate, power, complexity или geometry.

---

## 14. Decoys and false contacts

Decoy не должен иметь `30% chance to fool missile`.

Он создаёт **альтернативное measurement hypothesis**.

Tracker сравнивает measurement с predicted track через innovation.

Scalar demonstration:

```text
NIS = residual² / (σ_pred² + σ_measurement²)
```

Reference gate:

```text
NIS <= 9
```

Если:

```text
σ_pred = σ_meas = 5e-8 rad
```

то:

| Residual | NIS | Result |
|---:|---:|---|
| 0.05 µrad | 0.5 | accepted hypothesis |
| 0.20 µrad | 8.0 | accepted near gate |
| 0.50 µrad | 50 | rejected |

Если несколько measurements проходят gate, правильная реакция — сохранить ambiguity / несколько hypotheses до появления дополнительной информации.

Не бросать random roll.

---

## 15. Multi-band thermal classification defeats simplistic decoys

Blackbody spectral ratio:

```text
8–12 µm power / 3–5 µm power
```

Reference:

```text
1100 K hot radiator ≈ 0.2072
600 K warm decoy    ≈ 0.9048
```

То есть объект может подогнать **общую яркость одного band**, но другой temperature spectrum выдаёт его в multi-band observation.

Хороший decoy поэтому должен платить за imitation нескольких свойств:

- total radiant power;
- spectral temperature / bands;
- apparent size/geometry where resolved;
- acceleration;
- radar response;
- active-emission fingerprint;
- temporal behavior.

Это создаёт естественную гонку EW/ECCM без class bonuses.

---

## 16. Track age is a real combat quantity

После последнего measurement uncertainty растёт.

v0.3 уже задаёт правильную matrix form:

```text
P(t+dt) = F P Fᵀ + Q
```

v0.8 добавляет простой deterministic maneuver-aware surrogate для acceptance:

```text
σ_pos(age)
= sqrt(
    σ_pos0²
  + (σ_vel × age)²
  + (0.5 × σ_accel × age²)²
)
```

Reference seed:

```text
initial cross-track sigma = 15 m
velocity sigma            = 0.03 m/s
unmodeled accel sigma     = 0.05 m/s²
```

Получаем:

| Track age | Cross-track sigma surrogate |
|---:|---:|
| 0 s | 15 m |
| 30 s | 27.1 m |
| 60 s | 91.3 m |
| 120 s | **360.3 m** |

Следовательно, emitter silence или temporary sensor loss не удаляет track мгновенно, но постепенно ухудшает его полезность.

Для unguided kinetic effective range это напрямую возвращается в v0.3 time-of-flight model.

---

## 17. Fusion of sensor types

Reference 100-m² corvette на 300 000 km:

Passive IR:

```text
SNR ≈ 5364
angular sigma = 5e-8 rad floor
cross-track sigma from one bearing ≈ 15 m
no direct range
```

Active radar:

```text
SNR ≈ 7.023
range sigma ≈ 2 m
poor raw angular resolution relative to optical aperture
```

Combined track therefore естественно берёт:

```text
optical/IR → precise bearing
radar      → range / range-rate
```

а не выбирает один «лучший sensor stat».

Distributed passive observers могут заменить часть active ranging role, но требуют spatial baseline and communication.

---

## 18. External target solution

Теперь можно формально объяснить старый design principle:

```text
Recon Frigate
→ high-SNR / better geometry measurements
→ lower measurement covariance
→ fused fleet track
→ cruiser/battleship receives track
→ weapon solver gets smaller future-state uncertainty
→ useful firing envelope increases
```

Никакого:

```text
Recon Frigate aura: +20% weapon range
```

При этом information transfer должен иметь:

- source identity;
- timestamp;
- covariance;
- latency;
- confidence;
- communication availability;
- potential deception/spoofing state.

---

## 19. Signature channels for v1.0

`SignatureState` не должен быть одним числом.

Минимум:

```text
thermal bands / radiator state
engine / exhaust plume channels
active radar emissions
communications / datalink emissions
reflected optical signature
radar cross-section samples vs aspect/frequency
laser / weapon firing events
transient damage / venting / debris
```

v0.8 numerically closes только:

- hot thermal unresolved-source channel;
- one active radar channel;
- one noise-jammer channel;
- multi-band thermal discrimination seed.

До v1.0 ещё надо отдельно закрыть engine plume и directional/aspect signatures достаточно, чтобы data model не потребовал redesign.

---

## 20. Proposed production data

### SensorDefinition

```text
id
sensorFamily
passiveOrActive
spectralBand / wavelength
apertureM / antennaGeometry
throughput / efficiency
receiverNoiseTemperatureK or detectorNoise
integrationPolicies
waveformBandwidthHz
maxCoherentDwellS
fieldOfView
scanRate
powerW
wasteHeatW
measurementTypes[]
```

### SignatureDefinition / runtime state

```text
thermalRadiatedPowerByBandW
radiatorTemperatureK
radiatorOrientation
enginePlumeState
activeEmissionState
radarCrossSectionByAspect/Frequency
transientSignatureEvents
```

### SensorMeasurement

```text
sensorId
observerId
timestampS
measurementType
z[]
R covariance
snr
spectralFeatures
sourceChannel
```

### TrackState

Сохраняется v0.3:

```text
trackId
estimatedState[]
P covariance
lastMeasurementTime
classificationConfidence
identityConfidence
source history
hypothesis / ambiguity state
```

### ElectronicWarfareState

```text
jammer emissions by band
EIRP / spectral density
beam geometry
waveform overlap
processing / ECCM configuration
false measurement hypotheses
```

---

## 21. v0.8 acceptance invariants

Executable tests фиксируют:

1. passive detection range растёт из radiated power, а не hull-class modifier;
2. hot battleship виден существенно дальше low-heat corvette;
3. strong passive track достигает v0.3 50-nrad angular seed;
4. single passive bearing не подменяется range measurement;
5. spatial baseline физически уменьшает range uncertainty;
6. active radar range obeys echo/RCS physics;
7. active main-beam illumination гораздо легче intercept, чем получить слабое echo;
8. jammer добавляет interference power;
9. ECCM уменьшает effective overlap и может требовать longer dwell;
10. decoy/false measurements проходят или не проходят covariance gate по residual consistency;
11. multi-band spectrum отличает простую temperature-mismatched decoy;
12. stale track uncertainty растёт без новых measurements.

---

## 22. Что v0.8 намеренно не закрывает

До v1.0 остаются:

- engine-plume spectrum and anisotropy;
- reflected-light / albedo signature;
- proper frequency/aspect RCS tables;
- detailed antenna side-lobes;
- full multi-target / multi-hypothesis tracker;
- communication interception and encryption/authentication;
- sophisticated deceptive radar jamming;
- seeker-specific ECM/ECCM;
- sensor damage / misalignment effects;
- background sky model;
- exact full 4×4 EKF implementation in production;
- information latency through fleet datalinks.

v0.8 закрывает **архитектуру и первый executable scale**, а не весь sensor-content catalog.

---

## 23. Следующий research pass

После v0.8 наиболее полезен **v0.9 Integrated Design Baseline**:

1. свести hull/slots/mass/volume/power/heat;
2. propulsion and maneuver;
3. sensors/tracks/EW;
4. weapons/ammunition;
5. protection/debris/compartments;
6. representative combat + civilian fits;
7. валидировать единым design validator;
8. определить оставшиеся blockers до `Ship Mathematics v1.0 Design Baseline`.

При этом v0.7 heavy-impact material response и v0.8 plume/aspect signature остаются обязательными closure items до acceptance v1.0, если v0.9 покажет, что без них production data model остаётся неоднозначной.

---

## 24. Canonical conclusions v0.8

1. **Detection range is not weapon range.**
2. Passive IR makes hot capital ships visible at enormous strategic distances without automatically granting fire-control quality.
3. Single passive bearing does not provide exact range; distributed geometry creates real recon value.
4. Active radar trades emission disclosure for precise ranging.
5. Radar echo falls as `R^-4`, while direct illumination/intercept falls as `R^-2`.
6. ECM is received interference; ECCM is processing/geometry/time trade, not a percentage resistance.
7. Decoys create competing measurement hypotheses, not random miss chances.
8. Multi-band and multi-sensor fusion naturally defeats simplistic decoys.
9. Track covariance and track age are authoritative combat state.
10. External target solutions now have a physical path from remote measurement to improved weapon envelope.
