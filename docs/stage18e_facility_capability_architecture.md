# Stage 18E — Facility capability architecture

Status: implementation slice on `agent/stage18e-facility-capabilities`.

## Purpose

Stage 18E removes the remaining abstract-factory seam from the Stage-18B/18C/18D industrial chain. Extraction, refining and manufacturing recipes already require physical capability tags and finite power/work/maintenance budgets; Stage 18E defines where those budgets come from in the world.

The central rule is:

> A station archetype never grants production by name. Production exists only because an installed, operational facility exposes the required capabilities and finite physical rates.

A future `industrial station` can therefore be readable as an archetype while its actual abilities are the composition of its installed refinery, fabrication, power, storage and other facilities.

## Facility definition

The Stage-18E catalog defines installed industrial lines by explicit physical characteristics:

```text
FacilityDefinition
- stable ID / display name
- broad family: EXTRACTION / PROCESSING / FABRICATION
- exposed Stage-18 capability tags
- rated process power
- engineering work rate
- maintenance work rate
- heat rejection required per process watt
- staffed labor units required at full rate
- zero-staff automation floor
- storage-class interfaces
- maximum handled single-unit mass
- allowed physical installation locations
```

The baseline contains 14 definitions and covers every Stage-18 industrial capability required by the current extraction/refining/manufacturing chain:

- asteroid, surface, deep and atmospheric extraction;
- volatile processing;
- bulk refining/beneficiation;
- advanced materials processing;
- chemical processing;
- recycling/salvage processing;
- heavy fabrication;
- electrical works;
- precision/electronics fabrication;
- ordnance fabrication;
- module/equipment assembly.

Agriculture/life support, shipyards, station-construction yards, standalone power plants and logistics depots remain later owners because their downstream mechanics are not yet implemented in 18B–18D. They are not represented as fake no-op facilities merely to fill a list.

## Installed facility state

A catalog definition is only a pristine design envelope. The world supplies an `InstalledFacilityState` with:

```text
facility instance ID
facility definition ID
condition fraction
allocated process power
available heat rejection
available staffed labor
available maintenance work rate
physical location tag
enabled/disabled state
```

This is intentionally a narrow projection boundary. Persistent station composition and storage ownership remain Stage 18F.

## Effective capacity projection

For an enabled facility in a compatible location:

```text
conditionPower = ratedPower × condition
heatLimitedPower = availableHeatRejection / heatPerProcessW

effectivePower = min(
    conditionPower,
    allocatedProcessPower,
    heatLimitedPower
)

powerFraction = effectivePower / conditionPower

staffedFraction = min(1, availableLabor / requiredLabor)
laborFraction = automationFloor
              + (1 - automationFloor) × staffedFraction

throughputLimiter = min(powerFraction, laborFraction)

effectiveEngineeringWorkRate
    = ratedWorkRate × condition × throughputLimiter

effectiveMaintenanceWorkRate
    = min(ratedMaintenanceRate × condition,
          availableMaintenanceWorkRate)
```

The result is deterministic and deliberately exposes bottlenecks rather than hiding them behind a generic station efficiency modifier.

Consequences:

- damage reduces the physical rated envelope;
- insufficient supplied power caps process power;
- insufficient radiator/heat-sink capacity caps usable power even when electricity is abundant;
- insufficient labor reduces engineering throughput but respects the authored automation floor;
- maintenance is a separate finite service budget and cannot be reused as engineering work;
- an incompatible location or disabled/destroyed facility grants no capability tags at all.

## Direct adapters to Stage 18B–18D

`Stage18FacilityRuntime` converts one effective snapshot into the existing finite capability records:

```text
FacilityCapabilitySnapshot
→ Stage18ExtractionRuntime.ExtractionCapability
→ Stage18RefiningRuntime.RefiningCapability
→ Stage18ManufacturingRuntime.ManufacturingCapability
```

This avoids parallel recipe execution logic. The already-tested Stage-18B/18C/18D settlement boundaries remain authoritative for mass, energy, work, maintenance and storage mutation; Stage 18E only supplies the physical installed capacity that those boundaries consume.

Acceptance tests execute real operations through this seam:

```text
installed asteroid excavator
→ Stage 18B asteroid extraction

installed bulk refinery
→ Stage 18C structural-alloy refining

installed heavy fabrication plant
→ Stage 18D heavy-component manufacturing
```

## Storage and handling interfaces

Facilities declare which Stage-18 storage classes they can physically exchange with, plus a maximum handled single-unit mass. These are capability facts, not persistent inventory.

Stage 18F will own the actual station/depot storage instances, transfer topology and logistics. At that point it can require both:

1. compatible facility interface;
2. physically connected compatible storage capacity.

Stage 18E therefore does not duplicate or pre-empt the Stage-18F storage model.

## Location constraints

Baseline location tags are deliberately physical rather than political or archetype-based, for example:

- `location.free_body`;
- `location.surface`;
- `location.deep_subsurface`;
- `location.atmospheric`;
- `location.volatile_site`;
- `location.orbital_station`;
- `location.surface_station`;
- `location.salvage_site`.

A bulk refinery may operate on an orbital or surface station, but not magically on a free-flying asteroid. Extraction facilities similarly require the physical environment they were designed for.

## Boundaries

Stage 18E does **not** yet own:

- persistent station facility composition/save state — Stage 18F integration;
- persistent storage/depot inventory and cargo-transfer topology — Stage 18F;
- transport/hauling/logistics execution — Stage 18F;
- shipyard berth envelopes, construction/refit/repair integration — Stage 18G;
- salvage yield from actual wreck composition/damage — Stage 18H;
- civilian agriculture/life-support chains not yet backed by Stage-18 recipes/content;
- final production content balancing — Stage 22.

The next implementation owner after this slice is **Stage 18F — stations, storage and logistics**.
