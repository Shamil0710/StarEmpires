# Star Empires — Stage 17.5 Combat Depth / Ship Fitting Foundation

> Статус: **PLANNED — research gate satisfied; activation still requires this roadmap-integration PR to reach `main`**  
> Основание: accepted `Ship Mathematics v1.0 Design Baseline` (`docs/ship_mathematics_v1_0_design_baseline.md`)  
> Machine-readable baseline: `docs/benchmarks/ship_mathematics_v1_0_design_baseline.json`  
> Назначение: перенести принятую v1.0 инженерную модель из test-side research в authoritative runtime без повторного проектирования fundamental ship/combat architecture.

---

# 1. Главная цель Stage 17.5

Stage 17.5 должен заменить Stage-13 combat vertical slice полноценным, но всё ещё детерминированным foundation, на котором Stage 18 сможет строить weapon-aware tactical AI.

Главный инвариант:

> **Stage 17.5 реализует v1.0; он не изобретает вторую корабельную модель.**

Production runtime обязан использовать ту же парадигму для player, AI, civilian, military и industrial ships.

---

# 2. Обязательные входы

Перед переводом Stage 17.5 в `ACTIVE` должны существовать в `main`:

- accepted `Ship Mathematics v1.0 Design Baseline`;
- machine-readable v1.0 benchmark;
- green v1.0 deterministic acceptance;
- этот подробный Stage-17.5 implementation plan;
- подробные Stage-19 и Stage-21 планы, согласованные с той же моделью.

Stage 17 может продолжаться/завершаться параллельно, но Stage 17.5 не должен менять player-faction semantics.

---

# 3. Frozen architecture, которую нельзя тихо менять внутри Stage 17.5

Stage 17.5 принимает как frozen:

```text
Hull Size → Hull Architecture → Doctrine Class → Specialization → Ship Design → Variant/Refit → Ship Instance
```

и единые budgets:

```text
mass
volume / geometry
power
stored energy
heat / coolant / rejection
crew / automation
ammunition / stores
reaction mass
thrust / acceleration / delta-v
signature
sensor / track capability
shield field capability
weapon capability
protection / compartments
maintenance / logistics / operating cost
```

Если implementation обнаруживает реальное противоречие, требующее нового фундаментального stat/resource/budget, работа останавливается на explicit architecture change request. Нельзя обходить проблему временным hidden multiplier.

---

# 4. Stage 17.5A — versioned content schema: HullDefinition / ModuleDefinition / MaterialDefinition

## Цель

Создать production data model, способную описать v1.0 без compile-time enum explosion.

## Обязательные definitions

### `HullDefinition`

Минимум:

```text
stable content ID
hull architecture
bounding dimensions / collision geometry seam
bare hull / structural mass
internal volume / integration zones
CORE / WEAPON / UTILITY / INTERNAL / MISSION topology
hardpoints: size, position, arc, recoil/mount constraints
compartment topology
crew/life-support baseline
base signature geometry
structural material stack references
max operational mass / structural limits
reference thrust-mount compatibility
```

### `ModuleDefinition`

Общий integration contract v1.0:

```text
stable ID
family/category
compatible slot/hardpoint
physical dimensions
mass
volume
structural requirements
power supply/demand
peak power
stored energy
waste heat
local thermal capacity
coolant interface
heat rejection
crew/automation
consumable/ammunition/reaction-mass interfaces
signature contributions
construction/material inputs
maintenance/repair metadata
capability-specific payload
```

### `MaterialDefinition`

Минимум:

```text
stable ID
density
role/tags
thermal/optical/radar authoring properties where needed
heavy-impact response-surface references
construction/repair component requirements
```

Static strength values не заменяют HVI response surface.

### `ProtectionStackDefinition`

```text
layer material
thickness
spacing
orientation/coverage geometry
mount/structural mass
response-surface lookup key
```

## Validation

Catalog validation должен отклонять:

- unknown material/module/hull IDs;
- incompatible hardpoints;
- negative/NaN SI values;
- unbounded lists;
- duplicate IDs;
- response surfaces без calibration domain;
- hidden class bonuses;
- missing migration version.

## DoD 17.5A

Machine-readable v1.0 demonstrator fit загружается через production content schema и даёт тот же semantic fingerprint при deterministic reload.

---

# 5. Stage 17.5B — central derived-ship calculator + fitting validator

## Цель

Ввести единственную authoritative boundary для вычисления характеристик fitted ship.

Пример API-направления:

```text
DerivedShipState derive(HullDefinition hull,
                        InstalledFit fit,
                        ConsumableState consumables,
                        DamageState damage)
```

## Derived state

Минимум:

```text
totalMassKg
usedVolumeM3
remainingVolumeM3
powerSupplyW
powerDemandW
powerMarginW
peakPowerDemandW
storedEnergyJ
wasteHeatW
heatRejectionW
heatMarginW
crewRequired / supported
ammunition mass/count
stores mass
reaction mass
thrust envelope
mass flow
acceleration
delta-v
sensor/signature capability
shield capability
weapon capabilities
protection state
maintenance/logistics demand
```

## Fitting validation

Одновременно проверять:

- physical slot/hardpoint;
- mount size/arc;
- mass;
- volume;
- power;
- peak power / energy storage;
- heat transfer/rejection;
- crew/automation;
- ammunition/consumables;
- required supporting modules;
- mission-space constraints.

Не допускается sequential fitting, при котором последняя проверка не видит предыдущие budgets.

## Runtime migration

Существующие Stage-4/13/14 ship archetypes должны получить migration/adapter path. Нельзя respawn существующие `FleetId` ради нового fitting state.

## DoD 17.5B

- representative Corvette/Destroyer/Battleship/Bulk Freighter/Tanker проходят production calculator;
- v1.0 reference acceleration/delta-v совпадает с benchmark в пределах documented tolerance;
- перегруженный/перегретый/over-power fit deterministic rejected;
- cargo/ammunition/equipment mass влияет на movement через общий `FlightDynamics`.

---

# 6. Stage 17.5C — propulsion, reaction mass, power, thermal topology и FTL

## Local propulsion

Production runtime переносит:

```text
a = F/m
mdot = F/ve
jetPower >= 0.5 F ve
deltaV = ve ln(m0/m1)
```

Reaction mass становится real persistent consumable.

## Max vs sustained thrust

Оба режима являются capabilities конкретного fit/damage/thermal state, а не class constants.

## Thermal topology

Обязательны:

```text
module local thermal state
coolant transfer path
ship heat bus
thermal stores
radiators
```

Damage локального coolant path должен влиять на конкретные modules.

## Power

Разделить:

- continuous supply/demand;
- peak demand;
- stored energy/capacitors;
- brownout/shedding policy;
- damaged generation/distribution.

## FTL

Stage-10 FSM сохраняется как lifecycle boundary, но jump capability теперь запрашивается из fitted ship:

```text
translated mass compatibility
required jump energy
charge power
spool time
edge transit time
cooldown
heat/damage restrictions
```

Current 1.3-s test edge остаётся fixture до Stage-19 scale calibration.

## DoD 17.5C

- same fitted ship changes acceleration after cargo/reaction-mass changes;
- damaged drive lowers thrust without generic movement debuff;
- damaged coolant can throttle module;
- jump plan rejects ship over translated-mass envelope;
- jump state survives save/load mid-charge/mid-transit/cooldown where persistent.

---

# 7. Stage 17.5D — SignatureState, SensorMeasurement, TrackState, datalink и EW

## Production objects

Минимум:

```text
SignatureState
SensorDefinition / SensorRuntimeState
SensorMeasurement
TrackState
TrackCovariance
ElectronicWarfareState
DatalinkState
```

## Signature channels

### Thermal

Temperature/emissivity/projected area/band.

### Engine plume

Derived from thrust/jet power + data-driven spectral/aspect authoring tables.

### Radar

Frequency × aspect RCS.

### Reflected optical

Illumination/phase/orientation/BRDF.

## Information states

```text
DETECTED
CLASSIFIED
TRACKED
FIRE_CONTROL
```

UI может агрегировать качество, но authoritative TrackState хранит uncertainty.

## Distributed targeting

Recon ship должен улучшать fleet solution через measurement geometry, datalink freshness/latency и covariance fusion, а не через aura bonus.

## ECM/ECCM/decoys

- noise/interference;
- waveform overlap;
- dwell/processing tradeoff;
- residual/NIS gating;
- multiple hypotheses where required;
- no random `decoyChance` as sole mechanic.

## DoD 17.5D

- passive bearing-only contact не получает точную range;
- distributed observers уменьшают range covariance;
- active radar даёт ranging, но создаёт detectable emission;
- jammer can deny and ECCM can recover at explicit cost;
- stale track covariance grows;
- player/AI use same track records.

---

# 8. Stage 17.5E — weapon families, ammunition, guidance и layered defense

## Kinetic

Production projectile state:

```text
material
shape
length/diameter
mass
position/velocity
momentum
energy
```

Fire solution использует TrackState, weapon pointing, time of flight и target maneuver envelope.

## Beam

Runtime учитывает:

- wavelength;
- aperture;
- jitter;
- beam power;
- range-derived spot;
- dwell;
- material/local response;
- local thermal duty.

## Guided

Missile/interceptor получает physical propulsion/seeker/guidance state.

## Magazines / launchers

Хранить:

```text
physical inventory
launch cells
cycle times
support channels
handling limits
magazine damage
```

## Layered defense scheduler

Threat priority deterministic, минимум по predicted impact time + stable ID.

Scheduler обязан учитывать:

- layer geometry;
- safe intercept range;
- launcher readiness;
- support channels;
- ammunition;
- laser emitter position;
- thermal duty;
- retry/inner-layer fallback;
- residual debris.

## v0.4/v0.5 parity

Research scenarios становятся golden/reference regression для production resolver, но допускается documented numeric change после введения новых fragment/track/thermal layers.

## DoD 17.5E

- нет `weaponAccuracy`, `missileHitChance`, `PDChance` как authoritative independent stats;
- finite ammunition physically depletes;
- formation spacing меняет defense outcome;
- guidance kill не удаляет kinetic body;
- repeated waves показывают magazine/thermal endurance.

---

# 9. Stage 17.5F — shields, armor, compartments и subsystem damage

## Shields

Production shield использует v1.0 contract:

```text
emitter geometry / coverage
field reserve J
interaction power W
threat coupling
recharge power/efficiency
heat
collapse/restart
emitter damage
```

Не хранить только generic `shieldHP` без физического backing state.

## Armor / heavy impact

Solver выбирает bounded `HeavyImpactResponseSurface` по:

```text
projectile material/geometry/velocity/angle
protection stack materials/thickness/spacing
```

Outside calibration domain:

```text
EXTRAPOLATION_FORBIDDEN
```

или explicit low-confidence fallback с diagnostics; silent extrapolation запрещена.

## Debris

Interception/armor hits могут создавать debris/fragment DamagePackets.

## Compartments

Минимальная spatial topology должна поддерживать routing damage через layers → compartments → systems.

## Damage-driven degradation

Примеры:

```text
sensor aperture damaged → measurement sigma worsens
coolant trunk damaged → local thermal limit worsens
magazine damaged → inventory/handling capability changes
shield emitter damaged → coverage/interaction power falls
drive damaged → thrust falls
reactor/distribution damaged → power margin falls
```

## DoD 17.5F

- same hit can produce different consequences by impact location/orientation;
- no global hull HP as sole survivability mechanic;
- shield saturation/recharge demonstrator reproduces v1.0 accounting;
- damage never creates capability through negative values/overflow;
- destruction/salvage still use ordinary lifecycle/economy path.

---

# 10. Stage 17.5G — shipyard/fitting/equipment lifecycle + construction/repair/maintenance economy

## Goal

Связать инженерную модель с живой экономикой.

Module/hull content должен иметь реальные construction inputs.

Production/repair flow:

```text
materials/components
→ capable shipyard/facility
→ build/refit/repair work
→ physical fitted asset
```

## Shipyard capability

Не `yardTier=3 can build tier3` как единственная проверка.

Минимум учитывать:

- integration volume/berth size;
- fabrication capabilities;
- material/component handling;
- precision/technology capability;
- available tooling;
- work rate;
- labor/automation;
- power/industrial inputs where modeled.

Tier может быть UI-derived summary.

## Maintenance / repair

Повреждённый subsystem должен требовать соответствующие parts/materials/work, а не только credits.

## Refit

Refit изменяет тот же physical asset; не respawn/clone.

## DoD 17.5G

- fitting changes mass/performance immediately through common calculator;
- purchase/refit/repair moves real money/resources;
- damaged module repair consumes parts/work;
- shipyard cannot build unsupported hull/module without capability;
- player и AI используют общую production/fitting boundary.

---

# 11. Stage 17.5H — shared capability APIs, player/AI commands, UI и persistence

## Stable query layer

Stage 18 AI и UI должны потреблять capability APIs, а не читать внутренние arrays напрямую.

Минимум:

```text
getAccelerationEnvelope()
getRemainingDeltaV()
planJump(edge)
observe(target/channel)
getTrack(target)
getFireSolution(weapon,targetTrack)
getShieldCoverage(direction)
getThermalEndurance(action)
getAmmunitionEndurance()
getDamageCapabilityState()
getRepairNeed()
```

## UI

Fitting screen должен показывать derived consequences:

- mass;
- acceleration;
- delta-v;
- power margin;
- heat margin;
- crew;
- ammunition;
- sensor capability;
- shield/protection;
- signature;
- projected operating/maintenance demand.

UI не мутирует ECS state напрямую.

## Persistence

Versioned save должен сохранять authoritative state:

- installed modules;
- consumables;
- damage;
- thermal state where required;
- shield state;
- FTL charge/cooldown;
- persistent tracks only where design requires.

Derived state можно recompute deterministically.

## DoD 17.5H

Full save/load continuation across fitting, combat damage, ammunition, thermal/shield state and FTL.

---

# 12. Stage 17.5I — deterministic end-to-end acceptance gate

Stage 17.5 считается COMPLETE только после combined matrix.

## Required scenarios

### Fit / mass

```text
same hull empty
vs loaded cargo
vs heavy armor
vs extra ammunition
→ physically different acceleration/delta-v
```

### Power/thermal

Healthy fit survives expected duty; damaged cooling throttles physically.

### Sensor/network

Recon geometry materially improves track/fire-control without bonus aura.

### Combat saturation

Repeat v0.5-style attacker/escort/spacing/wave matrix on production resolver.

### Shield

Finite reserve + power + recharge + thermal state; repeated high-energy hits saturate.

### Heavy impact

Bounded response lookup + compartment damage + no extrapolation.

### Civilian

Freighter/tanker use same mass/propulsion/power schema and retain economic usefulness.

### Economy

Build/refit/repair consumes real resources and transfers real money.

### Persistence

Save/load at:

- fitted healthy state;
- mid-combat damage;
- partial magazine;
- heated module;
- collapsed/recharging shield;
- FTL spool/transit/cooldown.

## Hard invariants

1. no player-only combat physics;
2. no class-name performance bonus;
3. no arbitrary max weapon range replacing physical solution;
4. no free ammunition/reaction mass;
5. no damage bypassing local/system state;
6. no module outside common mass/volume/power/heat/economy contract;
7. no fit accepted when a mandatory budget is negative;
8. deterministic repeatability preserved;
9. ordinary destruction/salvage/economic consequences preserved;
10. full CI green.

---

# 13. Explicit non-goals Stage 17.5

Stage 17.5 **не обязан**:

- создать весь Stage-21 catalog;
- финально сбалансировать все factions;
- реализовать advanced tactical fleet AI Stage 18;
- создать final galaxy Stage 19;
- заморозить все numeric balance coefficients;
- симулировать atom-by-atom material physics;
- делать CFD/plasma simulation;
- превращать exotic shields/FTL в реальную современную физику.

Он обязан реализовать **правильные interfaces и authoritative accounting**.

---

# 14. Recommended implementation order

```text
17.5A schema/material/hull/module
→ 17.5B derived calculator + fitting validator
→ 17.5C propulsion/power/thermal/FTL
→ 17.5D sensors/tracks/EW/datalink
→ 17.5E weapons/guidance/PD/ammunition
→ 17.5F shields/armor/compartments/damage
→ 17.5G shipyard/refit/repair/maintenance economy
→ 17.5H capability APIs/UI/persistence
→ 17.5I full acceptance
```

Если practical implementation требует небольшого пересечения slices, dependency direction всё равно сохраняется: combat systems не должны создавать собственные parallel mass/power/heat state до появления central calculator.

---

# 15. Stage 17.5 completion definition

Stage 17.5 закрыт, когда:

> **любой representative ship — от freighter до battleship — может быть описан одним data-driven fitting model, физически летать, обнаруживать/отслеживать, стрелять, защищаться, получать локальные повреждения, расходовать боеприпасы/реактивную массу, ремонтироваться/переоснащаться через реальную экономику и сохраняться/загружаться; player и AI используют те же capability APIs.**

Только после этого Stage 18 может начинать advanced tactical AI.