# Stage 20F — Industrial Input Reservation Plan v1

> Status: **PROVISIONAL STAGE-20F SHARED-INPUT AUTHORITY**
> Implementation: `stage20f.industrial-input-reservation-plan.v1`
> Input authority: one accepted `Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult`
> plus one explicit caller-authored process/output-rate selection

## Purpose

The route-evidence plan exposes physically reachable process inputs, but every candidate still sees
the same non-reserved upstream capacity. `Stage20IndustrialInputReservationPlan` closes that exact
accounting boundary for a caller-selected set of physical processes:

```text
accepted generated root seed
→ exact system / station / facility / process identity
→ explicit requested output kg/s
→ exact recipe input/output mass ratios
→ per-input required kg/s
→ admitted physical source routes
→ global finite SupplyKey maximum flow
→ accepted all-or-nothing input reservations
```

The plan does not select a process from a station name, system archetype or specialization label. The
selection authority must identify every process with the full generated identity and state a positive
finite output rate.

## Exact selection boundary

`ProcessSelectionKey` contains:

- processing system;
- generated station placement ID;
- exact Stage-18 facility definition ID;
- exact Stage-18 process/recipe ID;
- output commodity ID.

Each key may appear once in a versioned `SelectionAuthority` for the exact accepted root seed. Unknown
keys are rejected, and the requested rate cannot exceed that candidate's retained individual physical
input-limited upper bound. The caller therefore supplies policy; this plan only validates and reserves
physical capacity.

## Shared-capacity reservation

For every selected process input, the required input rate is derived exactly as:

```text
requested output kg/s × recipe input kg per output kg
```

One deterministic maximum-flow network is then solved per input commodity:

```text
source
→ SupplyKey, capped by final retained Stage-20E capacity
→ selected process input, capped by that input's ADMITTED route capacity
→ sink, capped by exact required input rate
```

`SupplyKey` is the existing `(commodity, physical source system)` identity. Its source edge is shared
by every selected input that can use it, so the same finite rate cannot be counted twice. Local supply
uses the same finite source edge as remote supply. `NO_FEASIBLE_ROUTE` and `ROUTE_TIME_EXCEEDED` rows
create no flow arc.

The graph and result ordering are canonical by commodity, supply key and complete process identity.
Reordering selection requests cannot change the result.

## All-or-nothing authority

The result is either:

- `ACCEPTED` — every selected process input is fully reservable; or
- `SHARED_SUPPLY_KEY_CONFLICT` — at least one selected input cannot coexist under the finite shared
  source ceilings.

A conflict report retains per-input and per-commodity maximum-reservable diagnostics, but commits no
`InputReservation` rows. It therefore keeps `RESERVED_INDUSTRIAL_INPUTS` unresolved. An accepted report
retains every exact non-zero source/process/input reservation and removes only that one missing
authority.

## What remains unresolved

An accepted reservation still retains four machine-readable missing authorities:

1. `OWNED_INDUSTRIAL_INPUT_FREIGHT`;
2. `INSTALLED_FACILITY_OPERATING_STATE`;
3. `INITIAL_STATION_INVENTORY`;
4. `INSTALLED_SHIPYARDS`.

Each remote input reservation carries the exact physical route and reserved kg/s needed by the next
freight bridge. It does **not** assign a ship, fleet, owner or shared route/fleet budget. The earlier
route-throughput rows were assessed independently, so the same representative freight authority may
still underlie several accepted remote reservations. That is the next explicit accounting seam.

The retained Stage-20E supply closure is also capacity provenance rather than live stock or a running
upstream order. Reserving a derived-material `SupplyKey` does not power, staff or start its producing
facility and does not place material into inventory. Those claims remain blocked by the operating-state
and initial-inventory authorities.

No runtime station, facility, inventory, freight asset, yard or specialization bonus is created.

## Regression coverage

Focused synthetic tests prove that:

- one explicit process/output request reserves its exact derived input rate;
- two individually feasible process requests cannot reserve 12 kg/s from one 10 kg/s `SupplyKey`;
- a rejected global selection commits no partial reservations;
- selection ordering cannot change the deterministic flow result;
- unknown process identities, wrong root seeds and rates above the individual physical bound fail
  closed.

The accepted fixed seed-1 production integration proves deterministic reconstruction from the real
generation path, exact supply-key ceilings, complete per-input reservation, route endpoint provenance
and the unchanged freight/operating-state/inventory/yard seams.

## Next roadmap slice

`docs/stage20f_industrial_input_freight_ownership_plan_v1.md` now consumes accepted remote
reservations, derives exact integer ship demand on their unchanged physical routes, and binds that
demand to distinct reserve slots in the existing Stage-20E ownership pool under explicit process
owners.

The next authority is installed facility operating state. Initial Stage-18 inventory remains a
separate bootstrap authority, and installed yards remain a separate prerequisite before shipbuilding
specialization can become operational.
