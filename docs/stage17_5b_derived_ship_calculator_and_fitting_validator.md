# Stage 17.5B — Derived Ship Calculator and Fitting Validator

> **Status: COMPLETE IMPLEMENTATION SLICE**  
> Date: 2026-08-16  
> Base green `main`: `e3e15abd4be7faab5841b683a9a2ac51033a412e`  
> Implementation verification head: `ee911ecf15895692f87611f8e88384cb1de50b38`  
> CI: run `#2375` / Actions `31951421305` / Java 17 verification job `95175344259` — **SUCCESS**.

## 1. Purpose

Stage 17.5B establishes one authoritative engineering boundary between authored Stage-17.5A ship content and later propulsion, sensor, weapon, protection, damage and industrial runtime systems.

Canonical boundary:

```text
DerivedShipState derive(HullDefinition hull,
                        InstalledFit fit,
                        ConsumableState consumables,
                        DamageState damage)
```

The boundary derives common physical budgets from the actual hull, installed modules and carried physical state. It does **not** accept `ShipType`, doctrine class, faction, player ownership or AI ownership as a performance input.

## 2. Production implementation

### `ShipEngineeringState`

Introduces immutable deterministic runtime engineering inputs and outputs:

- `InstalledFit` — stable hull ID plus module-to-mount assignments;
- `ConsumableLoad` — interface-bound physical load with authored capacity amount, SI mass and optional item count;
- `ConsumableState` — cargo, stores, mission payload, mission-space use and interface loads;
- `DamageState` — stable future subsystem-damage input seam;
- deterministic `ValidationIssue` / `ValidationResult`;
- `InstalledCapability` — family-specific capability parameters preserved per mount;
- `MaintenanceDemand` — maintenance metadata preserved per installed module;
- `DerivedShipState` — central common derived physical state.

Collections are copied, deterministically ordered where required and exposed as immutable state.

### `ShipFittingValidator`

A single deterministic validator checks the common physical fitting constraints that the Stage-17.5A schema can currently close:

- hull ID consistency;
- referenced module and mount existence;
- duplicate mount occupation;
- internal slot integration category;
- slot mass and dimensions;
- external hardpoint family and size compatibility;
- hardpoint mass and dimensions;
- authored recoil impulse against hardpoint recoil limit;
- hull propulsion-family compatibility;
- required propulsion parameters;
- total operational mass;
- used integration/mission volume;
- continuous power supply/demand;
- peak power versus continuous supply and stored energy;
- continuous heat generation/rejection and available local thermal buffer;
- crew demand versus life-support capacity;
- consumable interface existence, kind and aggregate capacity;
- damage-state mount references.

Validation never repairs a fit and never invents a capacity, multiplier or class bonus.

### `DerivedShipCalculator`

The calculator resolves the shared engineering state in SI units:

```text
installed dry mass
+ cargo
+ ammunition
+ stores
+ mission payload
+ reaction mass
→ total physical mass
```

It also derives:

```text
used / remaining integration volume
continuous power supply / demand / margin
peak power demand
stored electrical energy
waste heat / heat rejection / heat margin
local thermal capacity
crew required / supported
automation demand
ammunition mass / count
stores / cargo / mission payload mass
reaction mass
available propulsion thrust
mass flow
effective exhaust velocity
acceleration
delta-v
signature contributions
structural protection / compartments
family-specific installed capabilities
maintenance demand
```

Common propulsion equations are already centralized:

```text
a = F / m
mdot = F / ve
ve_effective = ΣF / Σ(F / ve)
deltaV = ve_effective * ln(m0 / m1)
```

Specialized sensor/EW, shield and weapon equations are deliberately **not** guessed in 17.5B. Their authored capability parameters remain attached to the installed mount and are consumed by Stage 17.5D–17.5F.

### `EngineeringFlightProfileAdapter`

Provides a production-engineering bridge into the existing shared `FlightDynamics` integrator.

All carried physical mass affects the profile:

- cargo;
- ammunition;
- stores;
- mission payload;
- reaction mass.

Therefore the same thrust produces different acceleration through the existing common movement equation when the physical loading state changes.

The current Stage-14 assisted speed cap remains an explicit compatibility input. Directional thrust, throttle, thermal throttling and real reaction-mass depletion are Stage 17.5C work and are not hidden inside the adapter.

### `LegacyShipEngineeringAdapter`

Provides an explicit read-only migration seam:

```text
legacy archetype content ID
→ engineering fit ID
```

It resolves the production fit for an existing Ashley entity without respawning, cloning, replacing or mutating the entity. Existing persistent identity therefore remains the same physical asset.

This adapter is compatibility infrastructure, not a new source of ship performance.

## 3. Validation semantics: impossible fit vs finite endurance

Stage 17.5B distinguishes a structurally impossible configuration from a physically derivable configuration whose operating mode has finite endurance.

### Hard errors

Examples:

- missing module or mount;
- incompatible slot/hardpoint;
- mass/volume limit exceeded;
- continuous power deficit;
- peak power deficit with no stored energy;
- continuous thermal deficit with no thermal buffer;
- crew beyond life-support capacity;
- incompatible/over-capacity physical consumable interface;
- missing propulsion parameters;
- non-pristine damage request before the Stage-17.5F subsystem-damage solver exists.

A hard error prevents authoritative derivation and is returned through deterministic diagnostics.

### Finite-endurance warnings

A negative static thermal margin is not automatically an impossible fit. The production demonstrator currently derives:

```text
waste heat          = 1.923 GW
heat rejection      = 1.500 GW
steady deficit      = 0.423 GW
local thermal store = 171 GJ
```

The fit is therefore accepted with `THERMAL_ENDURANCE_LIMITED`; Stage 17.5C will model actual thermal evolution, transfer topology and throttling.

Likewise peak electrical demand above continuous supply is not rejected when fitted stored energy can physically bridge the peak. It is surfaced as finite stored-energy endurance rather than a hidden free-power allowance.

## 4. Explicit schema-v1 gaps — no guessed capacities

The Stage-17.5A schema already authors several real demands whose matching ship-level capacity/topology is not yet represented symmetrically:

- module `requiredMountStrengthN` versus a corresponding mount force/strength capacity axis;
- module `automationRequirement` versus whole-ship automation capacity;
- module `coolantTransferDemandW` versus explicit coolant-bus/path transfer capacity.

Stage 17.5B does **not** close these gaps with arbitrary constants. Instead it emits deterministic diagnostics:

- `MOUNT_STRENGTH_CAPACITY_UNMODELED`;
- `AUTOMATION_CAPACITY_UNMODELED`;
- `COOLANT_TRANSFER_CAPACITY_UNMODELED`.

The corresponding production capacity/topology must be added by the appropriate later engineering slice, especially Stage 17.5C for propulsion/power/thermal runtime. Until then these diagnostics remain visible rather than silently granting capability.

Hardpoint arc is already authored physical mount geometry in Stage 17.5A. Stage 17.5B preserves that geometry; weapon traverse/fire-control use of the arc belongs to Stage 17.5E and is not replaced by a generic accuracy/range rule.

## 5. Damage boundary

`DamageState` is part of the central API now so later subsystem damage does not require replacing the calculator boundary.

Stage 17.5B intentionally accepts pristine capability only. A non-pristine module integrity request is rejected with `DAMAGE_MODEL_NOT_ACTIVE` rather than applying a generic percentage debuff.

Stage 17.5F will route physical damage through protection, compartments and actual installed systems and then derive degraded capability from the damaged physical state.

## 6. Acceptance evidence

The 17.5B acceptance tests cover:

| Acceptance | Result |
| --- | --- |
| Stage-17.5A production escort-destroyer demonstrator derives deterministic common budgets | PASS |
| Frozen Torpedo Corvette reference acceleration / delta-v | PASS |
| Frozen Escort Destroyer reference acceleration / delta-v | PASS |
| Frozen Battleship reference acceleration / delta-v | PASS |
| Frozen loaded Bulk Freighter reference acceleration / delta-v | PASS |
| Frozen loaded Fleet Tanker reference acceleration / delta-v | PASS |
| Invalid load/fit validation repeats the same deterministic result | PASS |
| Cargo/ammunition/stores/payload/reaction mass changes movement through common `FlightDynamics` | PASS |
| Legacy engineering mapping preserves the existing persistent `EntityId` | PASS |
| Runtime engineering input collections are immutable and reject nonsensical negative values | PASS |

CI evidence on the implementation line showed:

```text
Tests run: 709
Failures: 0
Errors: 0
Skipped: 0
```

The six new `DerivedShipCalculatorTest` tests all passed. The final implementation head `ee911ecf15895692f87611f8e88384cb1de50b38` then passed the complete Java-17 `clean verify` gate, including strict Javadoc verification and packaging.

## 7. What Stage 17.5B deliberately does not claim

This slice does **not** yet switch the whole live game runtime from legacy `ShipType` profile construction to production engineering state.

It does **not** yet implement:

- persistent reaction-mass depletion;
- throttle/directional/main-vs-maneuver thrust envelopes;
- drive damage degradation;
- jet-power closure;
- live power distribution / brownout / load shedding;
- coolant-bus topology and thermal evolution;
- radiator damage/throttling;
- production FTL translated-mass/energy/spool/cooldown lifecycle;
- sensor/track/EW equations;
- weapon/ammunition firing runtime;
- shield/protection/subsystem damage runtime;
- production engineering persistence/UI.

Those remain in Stage 17.5C–17.5H. 17.5B supplies the common authoritative calculation/validation seam they must consume.

## 8. Stage 17.5B completion decision

The Stage-17.5B definition of done is satisfied at the central calculator/validator boundary:

- representative Corvette / Destroyer / Battleship / Bulk Freighter / Tanker mathematics match the accepted frozen baseline;
- invalid fits reject deterministically;
- physical carried mass affects the common movement equation;
- no class-name or player/AI performance path is introduced;
- legacy identity migration has an ID-preserving adapter seam;
- unclosed schema axes are explicit diagnostics rather than hidden bonuses.

**Next implementation slice: Stage 17.5C — propulsion / reaction mass / power / thermal / FTL.**
