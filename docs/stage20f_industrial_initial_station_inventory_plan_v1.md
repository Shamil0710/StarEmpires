# Stage 20F — Industrial Initial Station Inventory Plan v1

> Status: **PROVISIONAL STAGE-20F INITIAL-INVENTORY AUTHORITY**
> Implementation: `stage20f.industrial-initial-station-inventory-plan.v1`
> Input authority: accepted industrial facility operating report

## Purpose

An active facility and owned input fleet still do not imply that material is present at bootstrap.
Without physical stock, the selected facility would claim output before its first delivery can
arrive. `Stage20IndustrialInitialInventoryPlan` requires that bridge explicitly:

```text
accepted source/process reservation
× retained physical delivery time
= required first-delivery pipeline-fill mass
→ exact consuming-station Stage18StationStorage snapshot
→ all-or-nothing initial-inventory authority
```

This creates no hidden restock and does not mutate runtime storage. It validates a caller-authored
canonical snapshot for later materialization.

## Exact canonical storage

The authority supplies one `StationStorageSnapshot` for every selected operating station and no
others. The snapshot station ID must equal the generated placement ID. Its capacity-by-storage-class
map must exactly equal the retained generated station archetype.

`Stage18StationStorage.restore(...)` then validates every commodity, product, storage class and
physical capacity against the current Stage-18 ontology and manufactured-product registry. An
arbitrary map cannot bypass canonical storage semantics.

## Physical bootstrap buffer

For every accepted reservation, required mass is:

```text
reservedInputKgPerSecond × retainedRoute.travelTimeS
```

Local and remote reservations use the same rule because both retain positive physical delivery
cadence. Multiple reservations for the same commodity and station are summed before comparison.
Available mass is the exact canonical snapshot mass; capacity is never interpreted as inventory.

The report retains required mass, available mass and shortage for every selected station/commodity.
If any shortage is positive, the complete report is `INSUFFICIENT_INITIAL_INVENTORY` and no station
is partially promoted.

## Authority state

Acceptance removes only `INITIAL_STATION_INVENTORY`. At this point the preceding selected-input,
freight-ownership and facility-operating authorities remain closed, while `INSTALLED_SHIPYARDS`
remains machine-readable and unresolved.

Public report construction independently derives the expected station/commodity buffer matrix from
the retained reservations and verifies exact authority assignment, station archetype provenance,
required mass and available snapshot mass. Forged derived rows fail closed.

## Production coverage

The fixed seed-1 integration stores the exact physical first-delivery mass for a real remote-input
selection and proves deterministic acceptance. Empty but otherwise canonical storage produces an
explicit shortage and leaves `INITIAL_STATION_INVENTORY` unresolved. Tampered buffer evidence is
rejected by the public report boundary.

## Next roadmap slice

The remaining Stage-20F missing authority is explicit installed shipyards. Yard projection must bind
real Stage-18G state to generated stations and active Stage-18 support facilities. Only then may final
operational specialization classify shipbuilding capability; no station or system label may grant it.
