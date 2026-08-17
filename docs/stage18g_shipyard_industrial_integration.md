# Stage 18G — Shipyard, repair and refit industrial integration

Status: implementation slice on `agent/stage18g-shipyard-industrial-integration`.

## Purpose

Stage 18G connects the existing Stage-17.5G engineering planner to the physical Stage-18 economy without replacing the already tested build/refit/repair/maintenance rules.

The central invariant is:

> A shipyard can complete engineering work only after a real installed yard exists at a real Stage-18F station, its required Stage-18E support facilities are operational, finite engineering work is available, and the canonical station inventory contains the required physical materials/components/modules.

No docking action grants free repair, no station class grants a hidden shipyard, and credits cannot substitute for missing physical inputs.

## Why Stage-17.5G requirement units are not kilograms

Stage-17.5G intentionally authored provisional requirement identities such as:

```text
component.heavy
component.electrical
component.precision
```

Those values were created before the Stage-18 mass ontology and are planner-facing engineering requirement units. Stage 18G therefore does **not** apply an undocumented conversion such as `1 unit = 1 kg`.

Instead:

```text
Stage-17.5G requirement tokens
→ remain compatibility inputs to ShipyardEngineeringService

Stage-18G physical profiles
→ own kilograms and finished-product identities
```

Only after a Stage-18G settlement has physically consumed real inventory does the bridge generate the compatibility `WorkSettlement` expected by Stage-17.5G completion.

This keeps the old planning API stable without corrupting the new physical economy.

## Physical yard definition

`Stage18ShipyardCatalog` defines an installed yard envelope rather than a station tier.

The baseline `yard.orbital_escort_v1` defines:

```text
required Stage-18E support facilities
physical berth dimensions
maximum supported service mass
Stage-17.5G compatibility fabrication/tooling tokens
integration precision
finite integration power
finite engineering work rate
labor / automation capacity
Stage-18 storage-class handling interfaces
maximum handled finished-module mass
allowed physical installation locations
```

The baseline yard requires these ordinary Stage-18E facilities to be installed and active at its owning station:

```text
facility.fabrication.heavy
facility.fabrication.assembly
```

The yard does not invent their capacity. `Stage18ShipyardRuntime.projectYard(...)` resolves the actual Stage-18F installed-facility references against current Stage-18E capability snapshots. A missing, disabled or mismatched support line prevents the yard from exposing a `ShipyardCapability`.

The yard itself also has finite condition, allocated integration power, work rate, labor and automation. Damage therefore reduces the physical service envelope rather than applying a hidden station efficiency modifier.

## Bare-hull construction mass closure

The Stage-17.5 engineering demonstrator hull has authored bare mass:

```text
hull.escort_destroyer_v1
bareHullMassKg = 12,000,000 kg
```

Stage 18G authors its real build input mass as:

```text
structural alloy             7,800,000 kg
light alloy                  1,000,000 kg
refractory alloy               500,000 kg
ceramic/glass                  900,000 kg
carbon material                500,000 kg
electronic-grade material      100,000 kg
heavy components               800,000 kg
electrical components          250,000 kg
precision components           150,000 kg
-----------------------------------------
total                       12,000,000 kg
```

The loader rejects the catalog if this mass does not equal the authoritative bare-hull mass. It also rejects raw extracted feedstock as a direct shipyard input.

This deliberately means:

```text
ORE → SHIP
```

is impossible. Ore must pass through the ordinary Stage-18 refining/component chain first.

## Finished modules are physical products

A new ship does not receive modules because their old Stage-17.5 construction requirement tokens were satisfied abstractly.

For every fitted mount, Stage 18G consumes one existing Stage-18D finished module product from canonical station storage.

Example:

```text
manufacturing inputs
→ Stage 18D produces module.reactor_5gw_v1
→ module occupies storage.oversized by its real 2,200,000 kg mass
→ logistics may deliver it to the shipyard station
→ Stage 18G consumes that exact finished product during ship build/refit
```

The shipyard also checks its single-unit handling envelope before accepting the module.

## Build settlement

The build chain is:

```text
valid Stage-17.5G BUILD plan
+ active installed Stage-18G yard
+ active required Stage-18E support facilities
+ physical bare-hull Stage-18 inputs
+ one finished Stage-18D module per fitted mount
+ finite yard engineering work
→ Stage-18G physical settlement
→ compatibility WorkSettlement
→ ShipyardEngineeringService.completeBuild(...)
```

All inventory/work sufficiency is preflighted before mutation. A missing kilogram or missing module rejects the operation without consuming any material, product or work budget.

## Refit settlement and removed-module continuity

Refit consumes only newly introduced module products.

For each changed mount:

```text
new module
→ one matching Stage-18D product consumed

removed module
→ reported as a released physical product identity
→ integrity/service age preserved by ShipyardRefitContinuity
```

A damaged removed module is **not** silently inserted into the station's generic pristine product count. The caller receives the released identity plus Stage-17.5G continuity state and can later route it to storage, repair, resale, salvage or recycling with its condition intact.

The persistent ship `EntityId` remains unchanged.

## Repair settlement

Repair uses explicit physical Stage-18 profiles rather than free restoration.

Structural damage:

```text
compartment full-loss material profile
× (1 - compartment integrity)
→ required repair kilograms
```

Module damage:

```text
module full-loss service profile
× (1 - module integrity)
→ required repair kilograms
```

Only after those inputs and the Stage-17.5G engineering work are physically settled can `completeRepair(...)` restore the damage state.

No docking, loading or save/load path performs this automatically.

## Scheduled maintenance

Every current Stage-17.5G module has a Stage-18G maintenance-spares profile.

When `ShipyardEngineeringService.planMaintenance(...)` marks a mount due, Stage 18G consumes the authored spares/consumables for that module. Only a settled service resets its maintenance age.

This makes scheduled service part of ordinary industrial demand rather than a free timer reset.

## Atomicity

Stage 18G follows the same settlement rule as extraction/refining/manufacturing:

```text
preflight plan feasibility
preflight active yard
preflight finite work
preflight storage-class compatibility
preflight finished-unit handling mass
preflight all commodity masses
preflight all product counts
→ mutate inventory
→ consume work
→ produce compatibility settlement
```

Any rejected request leaves canonical Stage-18F storage and yard work budget unchanged.

## Ownership boundaries

Stage 18G owns:

- physical shipyard design/envelope definitions;
- installed-yard projection at a Stage-18F station;
- required Stage-18E support-facility presence;
- real hull build material/component mass;
- finished-module consumption during build/refit;
- damage-scaled repair material/component consumption;
- scheduled maintenance spares;
- finite yard engineering work settlement;
- compatibility handoff into Stage-17.5G completion.

Stage 18G deliberately does **not** own:

- mining/refining/component/module manufacturing — Stage 18B–18D;
- generic station storage/logistics — Stage 18F;
- salvage/recycling yields or disposition of removed/destroyed assets — Stage 18H;
- construction of new station facilities/yards from delivered goods — Stage 18H;
- final world-save integration and complete industrial-universe acceptance — Stage 18I;
- final production content/balance promotion — Stage 22.

## Acceptance covered by this slice

The Stage-18G tests prove:

1. a yard cannot exist as a hidden station-class bonus;
2. required Stage-18E support facilities must be installed and active;
3. bare-hull input mass closes exactly to authoritative hull mass;
4. BUILD consumes real hull commodities plus finished module products before completion;
5. missing physical input rejects atomically;
6. REFIT consumes newly installed module products while preserving ship identity;
7. removed damaged modules retain integrity/service age instead of becoming pristine stock;
8. REPAIR consumes damage-scaled physical material/component mass;
9. MAINTENANCE consumes physical spares before service-age reset;
10. Stage-17.5G requirement tokens never receive a silent unit-to-kilogram conversion.

The next implementation owner is **Stage 18H — recycling, salvage and physical construction economy**.
