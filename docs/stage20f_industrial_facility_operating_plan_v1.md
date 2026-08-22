# Stage 20F — Industrial Facility Operating Plan v1

> Status: **PROVISIONAL STAGE-20F FACILITY OPERATING AUTHORITY**
> Implementation: `stage20f.industrial-facility-operating-plan.v1`
> Input authority: accepted industrial input freight ownership plus exact generated candidates

## Purpose

Input reservation and owned freight prove that selected rates can be supplied. They do not prove
that the consuming facility is installed, enabled, powered, cooled, staffed or maintained.
`Stage20IndustrialFacilityOperatingPlan` closes that seam with explicit world-authored Stage-18
state:

```text
selected process and explicit faction owner
→ exact generated station/facility slot
→ canonical installed-facility instance ID
→ Stage18FacilityRuntime projection
→ shared facility recipe demand
→ shared finite station services and cargo transfer
→ all-or-nothing operating authority
```

The plan grants no free power, labor, heat rejection or maintenance. These are finite caller
authority and remain separate from process selection.

## Exact physical identity

Every selected process maps to one `FacilitySlotKey`: the exact generated system, station placement
and facility definition. The authority must cover the selected facility and station sets exactly.
Extra, missing or duplicate assignments fail closed.

Installed facility IDs use the same deterministic identity later used by
`Stage18StationIndustrialNode.instantiate(...)`:

```text
<station placement ID>.facility.<canonical archetype ordinal>
```

The current generated layout also requires `location.orbital_station`. A station label, owner
location or list order cannot substitute for either physical identity.

## Recipe and facility sharing

Each selected output rate is converted back into exact Stage-18 recipe demand. Refining derives
gross input through the recipe output-mass fraction; component manufacturing uses output mass
directly. The report retains continuous power, engineering work, maintenance and capability tags.

Processes sharing one facility also share one projected snapshot. Summed demand must fit effective
power, work and maintenance simultaneously, and every required capability tag must be active.
Process ownership must equal the explicit installed-facility owner.

## Station sharing

`StationServiceAllocation` is one finite pool per selected station. All selected installed states at
that station share:

- process power;
- heat rejection;
- staffed labor;
- maintenance work rate.

Selected input and output mass rates also share the generated station archetype's one cargo-transfer
ceiling. This prevents two facilities from independently claiming the complete station envelope.

## Authority state

Acceptance removes only `INSTALLED_FACILITY_OPERATING_STATE`. Initial inventory and installed yards
remain explicit. Any facility or station conflict rejects the complete operating authority; no
subset is promoted.

Public report records revalidate exact owner/request coverage, current Stage-18 recipe demand,
facility aggregates, station allocation aggregates, cargo-transfer demand and missing-authority
transitions. A caller cannot manually assemble contradictory accepted evidence.

## Production coverage

The fixed seed-1 integration selects a real remote-input process, binds its freight to existing
Stage-20E reserve ownership, reconstructs the exact generated facility ordinal, projects a canonical
full-condition Stage-18 state and proves deterministic accepted operation. Negative coverage proves
that station-service starvation and noncanonical facility IDs leave this authority unresolved.

## Next roadmap slice

`docs/stage20f_industrial_initial_station_inventory_plan_v1.md` consumes accepted operating state and
requires canonical initial storage sufficient to bridge every selected process until its first
already-owned delivery arrives.
