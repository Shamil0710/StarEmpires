# Stage 20F — Industrial Input Freight Ownership Plan v1

> Status: **PROVISIONAL STAGE-20F OWNED-FREIGHT AUTHORITY**
> Implementation: `stage20f.industrial-input-freight-ownership-plan.v1`
> Inputs: one accepted industrial input reservation report, the exact Stage-20E bootstrap freight
> ownership report, and explicit caller-authored process owners

## Purpose

An accepted input reservation retains exact source, process, kg/s and route evidence, but that route
was still a non-owning capacity projection. `Stage20IndustrialInputFreightOwnershipPlan` closes the
next accounting seam without creating a second fleet model:

```text
accepted remote InputReservation
→ explicit process → faction owner
→ unchanged retained physical route
→ exact minimum integer freighter count
→ owner's existing Stage-20E reserve slots
→ all-or-nothing owned industrial freight allocation
```

The plan creates no `FleetId`, runtime entity, cargo lot, station inventory or movement order. Its
assigned slot identity is deterministic pre-materialization provenance for the later runtime bridge.

## Explicit ownership boundary

Every selected process, including a process whose selected inputs happen to be local, must appear
exactly once in a versioned `ProcessOwnershipAuthority`. The owner must be a stable faction already
present in the exact `Stage20BootstrapFreightOwnershipPlan.OwnershipReport` for the same root seed.

Ownership is never inferred from:

- the process system;
- the nearest faction start;
- station or system labels;
- route endpoints;
- candidate ordering.

This is the missing seam between Stage-20F process selection and Stage-20E bootstrap ownership.

## Exact physical freight demand

For each remote reservation the current production entry point reconstructs the same fitted
Stage-20D/E route evaluator from the exact current representative profile. It evaluates ship counts
from one through the owner's complete finite pool and requires every result to preserve the retained
ordered neighbor path and delivery time. The first count whose sustainable throughput is at least the
reserved input kg/s becomes the route's integer freight demand.

The report retains:

- the complete process/input/`SupplyKey` identity;
- reserved input kg/s and original route;
- the explicit owning faction;
- minimum required integer freighters when bounded;
- physical route capacity at that minimum;
- physical capacity using the complete owned pool;
- payload/evaluator/profile provenance.

An evaluator that loses or changes the retained route fails closed as an authority mismatch.

## One shared existing ownership pool

Industrial demand does not receive free per-route fleets. All route demands assigned to one faction
are summed and compared with that faction's one Stage-20E reserve:

```text
owned pool
- already committed Stage-20E essential-service slots
= previously uncommitted reserve slots
```

Only slots whose Stage-20E `OwnershipSlot.commitment()` is empty may be assigned. Accepted industrial
allocations retain the exact faction-local `ownershipOrdinal`, and one slot may appear at most once
across all industrial routes. Stage-20E essential commitments are never repurposed or double-counted.

If any route exceeds the complete pool, or the summed bounded route demands exceed the remaining
reserve, the result is `INSUFFICIENT_OWNED_FREIGHT`. Rejection retains diagnostics but commits no
partial `OwnedInputFreightAllocation` rows.

An entirely local selected process closes this authority with zero freight allocations: explicit
absence of inter-system demand does not conjure a ship.

## Authority state

An accepted report removes only `OWNED_INDUSTRIAL_INPUT_FREIGHT`. It preserves the already closed
`RESERVED_INDUSTRIAL_INPUTS` authority and leaves these seams machine-readable:

1. `INSTALLED_FACILITY_OPERATING_STATE`;
2. `INITIAL_STATION_INVENTORY`;
3. `INSTALLED_SHIPYARDS`.

No operating facility, initial stock, yard, specialization role or production bonus is created.

## Regression coverage

Focused tests prove that:

- remote reservations receive the exact minimum integer ship count;
- assigned ordinals are distinct pre-existing reserve slots;
- two routes sharing one owner cannot each claim the whole reserve;
- Stage-20E committed freight cannot be reused;
- rejection commits no partial slots;
- owner coverage and physical route provenance fail closed;
- local-only input selection assigns no free ship.

The fixed accepted seed-1 integration reconstructs a real remote-only industrial input, assigns its
process to an explicit faction with reserve capacity, re-evaluates the physical route under the
current production profile, and proves deterministic binding to existing Stage-20E reserve slots.

## Next roadmap slice

`docs/stage20f_industrial_facility_operating_plan_v1.md` now binds selected facility slots to exact
`Stage18FacilityRuntime.InstalledFacilityState`, shared station services and cargo-transfer limits.
`docs/stage20f_industrial_initial_station_inventory_plan_v1.md` then validates canonical physical
storage and the complete first-delivery pipeline buffer. Installed shipyards remain explicit before
shipbuilding specialization can become operational.
