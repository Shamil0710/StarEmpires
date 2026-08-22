# Stage 20F — Operational Industrial Specialization v1

> Status: **STAGE 20F COMPLETE / ACCEPTED**
> Implementation: `stage20f.operational-industrial-specialization-plan.v1`
> Input authority: accepted installed-yard report with an empty missing-authority set

## Final closure

`Stage20OperationalIndustrialSpecializationPlan` is the final derived acceptance boundary for Stage
20F. It groups exact active evidence by generated station and explicit faction owner:

```text
accepted selected Stage-18 recipes
+ accepted explicit active Stage-18G yards
→ exact owner/station capability index
→ operational industrial specialization
```

This index grants no output, multiplier, stock, facility, ship or route. All physical authority stays
in the preceding reports.

## Derived roles

Only three v1 roles exist:

- `REFINING` — at least one accepted selected Stage-18 refining recipe;
- `COMPONENT_MANUFACTURING` — at least one accepted selected Stage-18 component recipe;
- `SHIPBUILDING` — at least one accepted active installed Stage-18G yard.

The exact recipe catalogs are loaded again at the public report boundary. A process cannot be
misclassified manually, and a station/system/archetype name cannot create a role. Explicit empty-yard
authority therefore closes the seam without adding `SHIPBUILDING`.

## Exact coverage

Final acceptance requires exactly one specialization placement for every selected process and every
active yard. Duplicates, omissions, owner changes and station changes fail closed. The final report
retains:

- exact process and facility identity;
- explicit owner;
- selected output kg/s;
- exact Stage-18 process kind;
- exact active yard projection;
- empty `MissingAuthority` state.

## Accepted Stage-20F chain

```text
generated station/facility/process candidates
→ exact physical input routes
→ globally reserved shared SupplyKey capacity
→ explicit process owners and existing Stage-20E reserve freight slots
→ canonical installed facility operating state and shared station services
→ canonical first-delivery station inventory
→ explicit installed-yard presence or absence
→ operational owner/station specialization
```

No step infers industry from a system type and no step creates hidden emergency supply.

## Runtime bridge handoff — explicit remaining seam

Stage 20F closes deterministic planning/bootstrap authority; it does not claim that those records are
already live runtime entities. The final report therefore always exposes the exact required handoff:

1. `SOURCE_SUPPLY_MATERIALIZATION` — a reserved `SupplyKey` is finite capacity provenance, not a
   running upstream extractor/refinery or an existing source cargo lot. The bridge must bind every
   reserved rate to live producer operation or physical source stock before recurring delivery;
2. `FREIGHT_FLEET_MATERIALIZATION` — retained Stage-20E ownership ordinals must become persistent
   `FleetId` assets without reallocating or replacing the owned ships;
3. `CARGO_ORDER_AND_LOT_MATERIALIZATION` — reservation rates and retained routes must become ordinary
   cargo lots, transport orders and cadence/deadline state;
4. `INDUSTRIAL_ENTITY_MATERIALIZATION` — exact station, facility, storage and yard identities/states
   must be instantiated, not regenerated from role labels.

The first item is the critical seam: theoretical source throughput must never be interpreted as cargo
already present. Consumer initial inventory only bridges the first retained delivery time; it does not
silently create the upstream producer or its recurring output.

## Acceptance evidence

The fixed seed-1 production integration closes the complete chain at a real generated industrial
station, projects an active escort yard from heavy and assembly support facilities, and derives
`REFINING + SHIPBUILDING` for the explicit owner. Repeated execution is deterministic. The empty-yard
case derives `REFINING` only. Rejected yard authority cannot enter final specialization.

Stage 20F is complete. The next roadmap slice is Stage 20G: persistent discovery and
sensor-consistent visibility over the accepted generated physical world. Runtime bridge work must
consume the explicit handoff set above before claiming live industrial logistics.
