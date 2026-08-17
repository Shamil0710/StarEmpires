# Star Empires — Stage 17.5 Combat Depth / Ship Fitting Foundation

> Статус: **COMPLETE — 17.5A–17.5I; Stage 18 NEXT**  
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

Stage 17.5 дополнительно обязан закончиться не только subsystem-level acceptance, но и production-valid **Combat Test Content Pack + Tactical Prototype Visual Set**, на которых несколько физически различных флотов проходят deterministic и interactive боевые столкновения. Детальный exit-gate contract: `docs/stage17_5i_combat_test_content_visual_acceptance.md`.

**Результат:** эта цель достигнута Stage 17.5I; content pack остаётся production-valid/content-provisional, а tactical visuals — replaceable presentation-only layer.

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

**COMPLETE — protection/damage runtime implemented; live engineering/API/persistence composition completed by Stage 17.5H.**

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

Canonical implementation record: `docs/stage17_5f_shields_armor_compartments_subsystem_damage.md`.

Stage 17.5H consumed the authoritative local `DamageState` and shield state at live engineering/API/persistence boundaries. Production ship-instance code no longer relies on a silent pristine-damage reset for those paths.

---

# 10. Stage 17.5G — shipyard / fitting / repair / maintenance seam

**COMPLETE — common physical capability/work-order/economy seam implemented; live ECS/persistence continuity completed by Stage 17.5H.**

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

Implemented Stage-17.5G boundary additionally guarantees:

- build/refit/repair/maintenance completion only after required physical inputs and engineering work are settled;
- ordinary `InventoryComponent` consumption rather than a parallel shipyard warehouse;
- same `EntityId` across refit/repair;
- removed modules remain physical handoff state rather than disappearing;
- removed/retained module damage and scheduled-service age survive refit, so refit cannot become free repair;
- fitting changes performance only through the existing central `DerivedShipCalculator`;
- identical requests use the same ownership-neutral player/AI planning boundary.

## Stage-18 handoff

Stage 18 must replace provisional generic inputs with a production graph based on real resource occurrence, extraction, refining, component manufacture, storage/logistics, facilities and bounded salvage/recycling.

Current `component.*`, shipyard-capability and work values are provisional integration vocabulary. Stage 18 owns final resource/component ontology, finished-goods recipes, facility production graph, market/logistics binding and commodity granularity while preserving the accepted Stage-17.5G causality/interfaces.

## DoD 17.5G

Fitting changes derived performance; purchase/refit/repair uses common economy seams; damaged module repair consumes inputs/work; unsupported hull/module cannot be built; player/AI use same production/fitting boundary.

Canonical implementation record: `docs/stage17_5g_shipyard_refit_repair_maintenance.md`.

---

# 11. Stage 17.5H — shared capability APIs / UI / persistence

**COMPLETE — damage-aware live composition, common engineering grants, read-only capability/UI projection and binary persistence continuity implemented.**

Stage **19** tactical/strategic AI and UI consume capability APIs instead of internal arrays.

Implemented query/read layer includes:

```text
getAccelerationEnvelope()
getRemainingDeltaV()
planJump(...)
getShieldCoverage(...)
getThermalEndurance(...)
getAmmunitionEndurance()
getDamageCapabilityState()
getRepairNeed()
+ fitted sensor / maintenance projections
```

Sensor observation, beam operation and shield recharge are composed through a common damage-aware engineering grant boundary so they cannot receive free power or free thermal capacity. The same boundary is ownership-neutral for player and AI.

`EngineeringComponent` carries the authoritative fitted ship instance across live boundaries: fit, consumables/operating state, compartment/module damage, shield state, maintenance/service age, weapon feed identity and launcher cooldown continuity.

UI consumes read-only `ShipCapabilityService` projections and does not mutate ECS directly.

Persistence contract is explicit:

- core `GameStateCodec` remains backward-compatible schema v4;
- production `ContentBoundSaveCodec` envelope v2 adds deterministic Stage-17.5H extension state;
- derived capability values are recomputed rather than persisted as a second source of truth;
- legacy v1/raw saves receive only neutral missing-H state, never free shield reserve, ammunition identity, repair, cooldown reset, power or sensor knowledge.

Persistent H extension covers required compartment/module integrity, shields, maintenance, weapon loadout/cooldowns and system-local sensor knowledge. Sensor knowledge remains information-domain state, not omniscient physical truth.

Stage-17.5G refit continuity reaches the live component boundary: retained/removed module condition, service age and local hull damage do not reset; surviving local runtime state is reconciled rather than treating refit as respawn/repair/rearm/recharge.

Canonical implementation record: `docs/stage17_5h_capability_ui_persistence.md`.

## DoD 17.5H

Damage/shield/refit/maintenance state survives live ECS and persistence boundaries; player/AI capability consumers share the same physical API/grant semantics; UI is read-only; no missing physical storage is replaced with virtual energy; exact-head full repository CI is green.

---

# 12. Stage 17.5I — deterministic end-to-end acceptance gate

**COMPLETE — aggregate content, matrix, persistence, full-chain destruction and interactive tactical acceptance are green.**

Stage 17.5I includes two mandatory layers:

1. subsystem/end-to-end deterministic scenarios;
2. production-valid **Combat Test Content Pack + Tactical Prototype Visual Set** per `docs/stage17_5i_combat_test_content_visual_acceptance.md`.

The delivered content pack supplies six representative hull families and five physically distinct doctrine fits:

```text
A — kinetic line
B — missile strike
C — high-mobility / beam
D — defensive / EW
E — balanced control
```

These assets use production schemas/runtime and remain **content-provisional**. They do not receive automatic Stage-22 canonical status.

## 12.1 Deterministic pair / variant matrix

Required pairs are green:

```text
A-A
A-B
A-C
A-D
A-E
B-C
B-D
B-E
C-D
C-E
D-E
```

Covered variants include:

- equal fleet count;
- approximately equal fitted mass;
- compact/dispersed spacing;
- full vs partial ammunition;
- fresh vs pre-damaged state;
- thermal stress;
- degraded sensor-information state;
- protected logistics;
- physical multi-body guided saturation with finite defense resources.

Equal-cost comparison is explicitly `DEFERRED_UNTIL_STAGE18_COMPARABLE_COST_BASIS`. Stage 18 owns the first legitimate comparable industrial/resource/facility cost basis; Stage 17.5I does not invent a fake scalar price.

## 12.2 Shared engineering contention

The Stage-H same-interval risk is closed through one explicit `ShipEngineeringGrantService.IntervalBudget` shared by sensor, beam and shield-recharge operations.

```text
sensor + beam + shield recharge in one interval
→ one continuous reactor margin
→ residual demand may use only physical ENERGY_STORAGE
→ storage discharge power remains bounded
→ local heat admitted deterministically
→ denied operation mutates neither budget nor ship
```

Acceptance: `Stage175ISharedEngineeringIntervalAcceptanceTest`.  
Checkpoint: `09556d783955aa7967847b0a7364141390e020a5` / CI #2772 SUCCESS.

## 12.3 Mid-combat persistence

`Stage175ICombatPersistenceAcceptanceTest` uses production `ContentBoundSaveCodec` envelope v2 and Stage-H mappers to preserve a combined state containing:

- partial physical magazine;
- stored energy;
- local/ship heat;
- thrust/coolant state;
- FTL cooldown field;
- compartment/module damage;
- shield reserve/heat/collapse;
- maintenance;
- weapon feed identity/cooldowns;
- sensor tracks/received/pending measurements.

No test-only save format exists. Core `GameStateCodec` remains schema v4.  
Checkpoint: `6fcc1843680cfc84bf2c9a3dec4aa2df889d73cf` / CI #2774 SUCCESS.

## 12.4 Finite-magazine destruction

`Stage175IPhysicalDestructionScenario` plus `Stage175IFullChainDestructionAcceptanceTest` proves:

```text
real doctrine-A primary magazine
→ AmmunitionRuntime.consumeOne
→ physical 150 kg ProjectileBody
→ fitted charged ShieldFieldRuntime
→ HeavyImpactResolver material response
→ ShipDamageRuntime local compartment/mount damage
→ shield-emitter integrity follows real module damage
→ complete local destruction
→ damage-aware DerivedShipCalculator
→ acceleration = 0 / sensor capability lost
→ wreck/debris presentation
```

The scenario continues until every compartment and every mount physically located in it reaches zero integrity. The real fitted magazine is sufficient without hidden damage bonuses or infinite acceptance ammunition.

Checkpoint: `ab8515ababebd669060570d5a078c45d396b35b5` / CI #2775 SUCCESS.

## 12.5 Post-combat persistence

`Stage175IPostCombatPersistenceAcceptanceTest` captures the fully destroyed physical entity, round-trips it through production persistence, restores ECS and reprojects it. It remains destroyed and remains a wreck.

Save/load therefore cannot become free repair, shield recharge, rearm, respawn or asset replacement.

## 12.6 Tactical prototype visuals / interactive gate

The replaceable presentation path is:

```text
Stage175IPhysicalDestructionScenario authoritative snapshots
→ Stage175ITacticalAcceptancePlayback immutable frames
→ TacticalPrototypeVisualSnapshot
→ TacticalPrototypeRenderer
→ Stage175ITacticalAcceptanceApp
```

The three deterministic frames expose:

1. engagement — kinetic projectile, guided missile, interceptor, EW/deception, beam, shield and thrust;
2. penetration — shield/armor/penetration/local damage;
3. wreck — subsystem loss and deterministic cosmetic debris.

Interactive launch:

```text
java -jar target/star-empires-1.0-SNAPSHOT-all.jar --tactical-acceptance
```

Controls only pause/step/reset/exit. The desktop client has no combat mutation path and cannot fire, repair, replenish, recharge or apply damage.

## 12.7 Exit evidence

Pre-closeout exact implementation checkpoint:

```text
head: 750604aa0a6216a739a584544dc5e1a439ffb378
CI:   #2789
result: SUCCESS
suite: 868 tests, 0 failures / 0 errors / 0 skipped
coverage: PASS
strict Javadoc: PASS
shaded desktop package: PASS
```

Canonical records:

- `docs/stage17_5i_implementation_record.md`;
- `docs/stage17_5i_combat_test_content_visual_acceptance.md`.

Hard invariants remain:

1. no player-only combat physics;
2. no class-name performance bonus;
3. no arbitrary max weapon range replacing physical solution;
4. no free ammunition/reaction mass;
5. no damage bypassing local/system state;
6. no module outside common mass/volume/power/heat/economy contract;
7. no fit accepted with negative mandatory budget;
8. deterministic repeatability;
9. ordinary destruction/salvage consequences;
10. Combat Test Content Pack uses production schemas/runtime rather than test-only stats;
11. prototype tactical visuals are replaceable presentation, never authoritative state;
12. Stage-17.5 test hulls/modules/fits/visuals do not silently become final Stage-22 canon;
13. full CI green.

---

# 13. Explicit non-goals Stage 17.5

Stage 17.5 не обязан:

- создать весь **Stage-22** technology/content catalog;
- финально сбалансировать factions;
- финализировать faction hull rosters, ship names or production visual identity;
- создавать final production-quality ship/projectile/VFX art;
- реализовать full resource/industry ontology Stage 18;
- реализовать advanced tactical fleet AI **Stage 19**;
- создать final generated galaxy **Stage 20**;
- freeze all balance coefficients;
- simulate atom-by-atom materials/CFD/plasma;
- превращать exotic shields/FTL в реальную современную физику.

При этом Stage 17.5 создал минимальный production-valid, content-provisional набор корпусов/оборудования/боеприпасов/fits и временный tactical visual set, достаточные для multi-fleet acceptance. Это test vocabulary, а не финальная Stage-22 энциклопедия контента.

Он реализует правильные interfaces и authoritative accounting.

---

# 14. Recommended implementation order

```text
17.5A schema/material/hull/module COMPLETE
→ 17.5B derived calculator + fitting validator COMPLETE
→ 17.5C propulsion/power/thermal/FTL COMPLETE
→ 17.5D sensors/tracks/EW/datalink COMPLETE
→ 17.5E weapons/guidance/PD/ammunition COMPLETE
→ 17.5F shields/armor/compartments/damage COMPLETE
→ 17.5G shipyard/refit/repair/maintenance seam COMPLETE
→ 17.5H capability APIs/UI/persistence COMPLETE
→ 17.5I full acceptance + combat test content + prototype tactical visuals COMPLETE
→ Stage 17.5 COMPLETE
→ Stage 18 Resources / Industry / Infrastructure NEXT
```

Combat subsystems не создают parallel mass/power/heat state до central calculator.

---

# 15. Stage 17.5 completion definition

Stage 17.5 completion definition is now **satisfied**:

> **любой representative ship — от freighter до battleship — может быть описан одним data-driven fitting model, физически летать, обнаруживать/отслеживать, стрелять, защищаться, получать локальные повреждения, расходовать боеприпасы/реактивную массу, ремонтироваться/переоснащаться через общую economic seam и сохраняться/загружаться; player и AI используют те же capability APIs.**

Дополнительный обязательный exit gate также **satisfied**:

> **несколько production-valid, но content-provisional hull/module/ammunition/fits позволяют собрать минимум четыре materially different специализированных флота и balanced control fleet; их deterministic combat matrix проходит production subsystem/resolver paths, а interactive столкновение полностью читаемо через replaceable Tactical Prototype Visual Set.**

Эти тестовые корабли, модули, фиты и visuals существуют для проверки mechanics coverage. Stage 22 обязан переработать/заменить/явно принять их по общей technology/faction/content парадигме; prototype visuals позднее заменяются production art без изменения authoritative physics.

Следующий этап — **Stage 18 Resources / Industry / Infrastructure**. Он превращает уже принятые construction/repair/material requirements в полноценную физическую производственную экосистему и впервые предоставляет сравнимую industrial/resource/facility cost basis, специально не выдуманную Stage 17.5I. Только после Stage 18 активируется **Stage 19 strategic warfare / advanced combat behavior**.