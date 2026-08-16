# Star Empires — Stage 17.5 Combat Depth / Ship Fitting Foundation

> Статус: **PLANNED — research gate satisfied; activation still requires Stage 17H completion**  
> Основание: accepted `Ship Mathematics v1.0 Design Baseline` (`docs/ship_mathematics_v1_0_design_baseline.md`)  
> Machine-readable baseline: `docs/benchmarks/ship_mathematics_v1_0_design_baseline.json`  
> Назначение: перенести принятую v1.0 инженерную модель из research в authoritative runtime без повторного проектирования fundamental ship/combat architecture.

---

# 1. Главная цель Stage 17.5

Stage 17.5 заменяет Stage-13 combat vertical slice полноценным deterministic foundation, который затем используют:

- **Stage 18** — resource/industry/facility production chains;
- **Stage 19** — weapon-aware tactical AI and strategic warfare;
- **Stage 20** — physically calibrated world generation;
- **Stage 22** — broad technology/content/balance.

Главный инвариант:

> **Stage 17.5 реализует v1.0; он не изобретает вторую корабельную модель.**

Production runtime использует ту же парадигму для player, AI, civilian, military и industrial ships.

---

# 2. Обязательные входы

Перед переводом Stage 17.5 в `ACTIVE` должны существовать в `main`:

- accepted `Ship Mathematics v1.0 Design Baseline`;
- machine-readable v1.0 benchmark;
- green v1.0 deterministic acceptance;
- подробный Stage-17.5 implementation plan;
- synchronized Stage-18 resource/industry plan;
- synchronized Stage-20 world-generation plan;
- synchronized Stage-22 content/balance plan;
- completed Stage 17H migration/end-to-end gate.

Stage 17.5 не меняет player-faction semantics.

---

# 3. Frozen architecture

Stage 17.5 принимает как frozen:

```text
Hull Size
→ Hull Architecture
→ Doctrine Class
→ Specialization
→ Ship Design
→ Variant/Refit
→ Ship Instance
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

Если implementation обнаруживает реальное противоречие, требующее нового fundamental stat/resource/budget, нужен explicit architecture change request. Hidden multiplier запрещён.

---

# 4. Stage 17.5A — versioned content schema

## `HullDefinition`

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

## `ModuleDefinition`

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
Stage-18 construction/material/component inputs
maintenance/repair metadata
capability-specific payload
```

## `MaterialDefinition`

```text
stable ID
density
role/tags
thermal/optical/radar authoring properties where needed
heavy-impact response-surface references
construction/repair material-family mapping
```

## `ProtectionStackDefinition`

```text
layer material
thickness
spacing
orientation/coverage geometry
mount/structural mass
response-surface lookup key
```

## Validation

Reject:

- unknown IDs;
- incompatible hardpoints;
- negative/NaN SI values;
- duplicate IDs;
- unbounded lists;
- response surfaces without calibration domain;
- hidden class bonuses;
- missing migration version.

## DoD 17.5A

Machine-readable v1.0 demonstrator fit loads through production schema and gives stable semantic fingerprint after deterministic reload.

---

# 5. Stage 17.5B — central derived-ship calculator + fitting validator

Single authoritative boundary:

```text
DerivedShipState derive(HullDefinition hull,
                        InstalledFit fit,
                        ConsumableState consumables,
                        DamageState damage)
```

Derived state includes:

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

Fitting validation simultaneously checks:

- slots/hardpoints/arcs;
- mass;
- volume;
- power/peak power;
- energy storage;
- heat transfer/rejection;
- crew/automation;
- ammunition/consumables;
- support modules;
- mission-space constraints.

Existing Stage-4/13/14 archetypes need migration/adapter path without respawning existing `FleetId`.

## DoD 17.5B

Representative Corvette/Destroyer/Battleship/Bulk Freighter/Tanker pass calculator; reference acceleration/delta-v match benchmarks within documented tolerance; invalid fits reject deterministically; cargo/ammunition/equipment mass affects movement through common `FlightDynamics`.

---

# 6. Stage 17.5C — propulsion / reaction mass / power / thermal / FTL

Production runtime preserves:

```text
a = F/m
mdot = F/ve
jetPower >= 0.5 F ve
deltaV = ve ln(m0/m1)
```

Reaction mass is real persistent consumable.

Max and sustained thrust are capabilities of fit/damage/thermal state, not class constants.

Thermal topology supports:

```text
module local thermal state
coolant transfer path
ship heat bus
thermal stores
radiators
```

Power distinguishes continuous supply/demand, peak demand, stored energy, brownout/shedding and damaged distribution.

FTL uses fitted capability:

```text
translated mass compatibility
required jump energy
charge power
spool time
edge transit time
cooldown
heat/damage restrictions
```

Current short test edge remains fixture until **Stage 20** scale calibration.

## DoD 17.5C

Cargo/reaction-mass changes alter acceleration; damaged drive lowers thrust without generic debuff; cooling damage can throttle modules; jump planning respects translated-mass envelope; jump lifecycle survives save/load where persistent.

---

# 7. Stage 17.5D — signatures / sensors / tracks / datalink / EW

Production objects:

```text
SignatureState
SensorDefinition / SensorRuntimeState
SensorMeasurement
TrackState
TrackCovariance
ElectronicWarfareState
DatalinkState
```

Channels:

- thermal;
- engine plume;
- radar;
- reflected optical.

Information states:

```text
DETECTED
CLASSIFIED
TRACKED
FIRE_CONTROL
```

Recon improves fleet solution through measurement geometry, freshness/latency and covariance fusion, not aura bonus.

ECM/ECCM/decoys use interference/processing/hypothesis mechanics rather than one random `decoyChance`.

## DoD 17.5D

Bearing-only contact has no exact range; distributed observers reduce covariance; active radar ranges but emits; jammer/ECCM tradeoffs explicit; stale tracks degrade; player/AI share track model.

---

# 8. Stage 17.5E — weapons / ammunition / guidance / layered defense

## Kinetic

Projectile state includes material, shape, dimensions, mass, position/velocity, momentum and energy.

## Beam

Runtime uses wavelength, aperture, jitter, beam power, range-derived spot, dwell, local material response and thermal duty.

## Guided

Missile/interceptor uses physical propulsion, seeker and guidance state.

## Magazines / launchers

```text
physical inventory
launch cells
cycle times
support channels
handling limits
magazine damage
```

## Layered defense

Threat priority deterministic, at minimum predicted impact time + stable ID.

Scheduler accounts for geometry, safe intercept range, launcher readiness, support channels, ammunition, emitter position, thermal duty, fallback and residual debris.

## DoD 17.5E

No authoritative `weaponAccuracy`/`missileHitChance`/`PDChance`; ammunition depletes physically; formation spacing changes defense; guidance kill does not delete kinetic body; repeated waves expose magazine/thermal endurance.

---

# 9. Stage 17.5F — shields / armor / compartments / subsystem damage

Shield state uses:

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

Heavy-impact solver selects bounded `HeavyImpactResponseSurface` by projectile and protection-stack parameters. Silent extrapolation outside calibrated domain is forbidden.

Compartments route damage through layers → compartments → systems.

Damage-driven degradation examples:

```text
sensor aperture damaged → measurement sigma worsens
coolant trunk damaged → thermal limit worsens
magazine damaged → inventory/handling changes
shield emitter damaged → coverage/power falls
drive damaged → thrust falls
reactor/distribution damaged → power margin falls
```

## DoD 17.5F

Hit location/orientation matters; global hull HP is not sole survivability; shield saturation/recharge accounting preserved; damage never creates capability; destruction/salvage use ordinary lifecycle/economy path.

---

# 10. Stage 17.5G — shipyard / fitting / repair / maintenance seam

Stage 17.5G defines **engineering and runtime requirements**; Stage 18 later supplies the full economic resource/facility graph.

Flow:

```text
material/component requirements
→ capable shipyard/facility
→ build/refit/repair work
→ physical fitted asset
```

Shipyard checks at least:

- berth/integration envelope;
- fabrication capability;
- material/component handling;
- precision capability;
- tooling;
- work rate;
- labor/automation;
- power/industrial input seam.

`yardTier=3 can build tier3` cannot be the only check.

Repair requires parts/materials/work, not credits only. Refit modifies same physical asset; no respawn/clone.

## Stage-18 handoff

Stage 18 must replace provisional generic inputs with a production graph based on real resource occurrence, extraction, refining, component manufacture, storage/logistics, facilities and bounded salvage/recycling.

## DoD 17.5G

Fitting changes derived performance; purchase/refit/repair uses common economy seams; damaged module repair consumes inputs/work; unsupported hull/module cannot be built; player/AI use same production/fitting boundary.

---

# 11. Stage 17.5H — shared capability APIs / UI / persistence

Stage **19** tactical/strategic AI and UI consume capability APIs instead of internal arrays.

Minimum query layer:

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

UI exposes mass, acceleration, delta-v, power/heat margin, crew, ammo, sensors, protection, signature and operating/maintenance demand without mutating ECS directly.

Persistence saves installed modules, consumables, damage, required thermal/shield/FTL state and persistent tracks where design requires. Derived state recomputes deterministically.

---

# 12. Stage 17.5I — deterministic end-to-end acceptance gate

Required scenarios:

### Fit / mass

```text
same hull empty
vs loaded cargo
vs heavy armor
vs extra ammunition
→ physically different acceleration/delta-v
```

### Power/thermal

Healthy fit survives expected duty; cooling damage throttles physically.

### Sensor/network

Recon geometry improves track/fire-control without bonus aura.

### Combat saturation

Repeat attacker/escort/spacing/wave matrices on production resolver.

### Shield

Finite reserve + power + recharge + heat; repeated hits saturate.

### Heavy impact

Bounded response lookup + compartment damage + no silent extrapolation.

### Civilian

Freighter/tanker use same mass/propulsion/power schema.

### Economy

Build/refit/repair consumes real resources and money through common seams; Stage 18 later expands these into complete industrial chains.

### Persistence

Save/load at fitted state, mid-combat damage, partial magazine, heated module, shield recharge and FTL lifecycle.

Hard invariants:

1. no player-only combat physics;
2. no class-name performance bonus;
3. no arbitrary max weapon range replacing physical solution;
4. no free ammunition/reaction mass;
5. no damage bypassing local/system state;
6. no module outside common mass/volume/power/heat/economy contract;
7. no fit accepted with negative mandatory budget;
8. deterministic repeatability;
9. ordinary destruction/salvage consequences;
10. full CI green.

---

# 13. Explicit non-goals Stage 17.5

Stage 17.5 не обязан:

- создать весь **Stage-22** technology/content catalog;
- финально сбалансировать factions;
- реализовать full resource/industry ontology Stage 18;
- реализовать advanced tactical fleet AI **Stage 19**;
- создать final generated galaxy **Stage 20**;
- freeze all balance coefficients;
- simulate atom-by-atom materials/CFD/plasma;
- превращать exotic shields/FTL в реальную современную физику.

Он обязан реализовать правильные interfaces и authoritative accounting.

---

# 14. Recommended implementation order

```text
17.5A schema/material/hull/module
→ 17.5B derived calculator + fitting validator
→ 17.5C propulsion/power/thermal/FTL
→ 17.5D sensors/tracks/EW/datalink
→ 17.5E weapons/guidance/PD/ammunition
→ 17.5F shields/armor/compartments/damage
→ 17.5G shipyard/refit/repair/maintenance seam
→ 17.5H capability APIs/UI/persistence
→ 17.5I full acceptance
```

Combat subsystems не должны создавать parallel mass/power/heat state до central calculator.

---

# 15. Stage 17.5 completion definition

Stage 17.5 закрыт, когда:

> **любой representative ship — от freighter до battleship — может быть описан одним data-driven fitting model, физически летать, обнаруживать/отслеживать, стрелять, защищаться, получать локальные повреждения, расходовать боеприпасы/реактивную массу, ремонтироваться/переоснащаться через общую economic seam и сохраняться/загружаться; player и AI используют те же capability APIs.**

После этого активируется **Stage 18 Resources / Industry / Infrastructure**, который превращает construction/repair/material requirements в полноценную физическую производственную экосистему. Только после Stage 18 активируется **Stage 19 strategic warfare / advanced combat behavior**.