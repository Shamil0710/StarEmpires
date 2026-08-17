# Stage 18F — Stations, physical storage and logistics

Status: implementation slice on `agent/stage18f-station-storage-logistics`.

## Purpose

Stage 18F turns the Stage-18B–18E industrial layers into infrastructure that can belong to actual persistent stations/outposts instead of isolated test stores.

The governing rules are:

1. a station role is a readable composition, not a production bonus;
2. Stage-18 physical inventory is measured in kilograms by storage class;
3. commodities and finished modules/ammunition compete for the same compatible physical capacity;
4. cargo movement consumes finite handling mass and respects storage/handling compatibility;
5. the legacy integer `InventoryComponent` keeps its historical units until a separate explicit migration policy exists.

## Composable station infrastructure

`Stage18StationInfrastructureCatalog` provides eight baseline roles that are sufficient for the currently implemented Stage-18 industrial chain:

```text
mining outpost
volatile/water depot
refinery complex
industrial station
high-tech manufacturing hub
trade/logistics hub
naval ordnance depot
frontier multipurpose station
```

Each template contains only:

```text
explicit Stage-18E facility definition IDs
physical storage capacity by Stage-18 storage class
cargo-handling storage classes
cargo-handling kg/s
maximum handled single-unit mass
allowed physical location tags
```

There is no `recipeId`, production multiplier or hidden capability field.

A useful negative example is `station.infrastructure.trade_logistics_hub`: it deliberately installs **zero** industrial facilities. Its specialization comes from large storage and fast handling. It cannot manufacture merely because its archetype says “hub”.

`Stage18StationIndustrialNode.instantiate(...)` materializes a template into:

```text
stable station ID
location tag
explicit installed-facility references
canonical physical station storage
station cargo-handling capability
```

Dynamic facility power, heat, labor, condition and maintenance remain inputs to the Stage-18E facility runtime rather than being invented from the station role.

## Canonical physical station storage

`Stage18StationStorage` is the first Stage-18 canonical station inventory boundary.

It stores:

```text
capacityByStorageClassKg
commodityMassByIdKg
finishedProductCountById
```

Finished product count remains useful for modules/ammunition, but capacity is always physical mass:

```text
used storage-class mass
= commodity mass
+ Σ(product unit mass × product count)
```

Therefore 600 kg of hazardous industrial chemicals and a 1,000 kg interceptor require 1,600 kg of `storage.hazardous_controlled`; they do not count as two abstract cargo units.

The state exposes deterministic immutable snapshots and a validated restore path. This is a persistence seam for later world/save integration, not a second legacy inventory encoding.

## Legacy inventory coexistence

The existing `InventoryComponent` remains intentionally unchanged:

```text
int[] stock
int capacity
abstract integer item units
```

Stage 18F does not reinterpret those integers as kilograms and does not silently convert `item.ore`, `item.steel`, `item.energy` or `item.weapons` inventories.

This avoids corrupting old economy/save semantics. A future migration can convert only where an explicit physical conversion rule exists.

## Physical logistics boundary

`Stage18LogisticsRuntime` transfers cargo atomically between two `Stage18StationStorage` nodes.

A common handling capability has:

```text
supported storage classes
mass rate kg/s
maximum single-unit mass
```

For two station archetypes the shared endpoint capability is:

```text
supported classes = intersection(source, destination)
rate = min(source rate, destination rate)
unit envelope = min(source envelope, destination envelope)
```

Commodity transfer requires:

```text
known Stage-18 commodity
compatible handling/storage class
sufficient source mass
sufficient destination compatible capacity
sufficient remaining interval handling mass
```

Finished-product transfer additionally requires each unit to fit the handling mass envelope.

The runtime does not teleport cargo between star systems. It is the loading/unloading settlement boundary that future route/hauler scheduling can call at physical endpoints.

## One station inventory across Stage 18B–18D

`Stage18StationProductionBridge` removes the temporary-store ownership problem without rewriting the tested production runtimes.

For extraction/refining it creates a commodity staging store whose capacity is the station's physical capacity **minus mass already occupied by finished products** in the same storage classes.

For manufacturing it stages the station's complete commodity and product inventory under the station's complete capacities.

Then:

```text
canonical station snapshot
→ temporary settlement view
→ existing Stage-18B / 18C / 18D runtime
→ commit only on accepted/committed result
→ new canonical station snapshot
```

Rejected manufacturing/refining does not mutate canonical station inventory.

Acceptance covers a continuous physical chain:

```text
installed asteroid extraction facility
→ METALLIC_ORE stored at station

installed bulk refinery
→ ore consumed
→ STRUCTURAL_ALLOY stored at station

installed heavy fabrication plant
→ materials consumed
→ HEAVY_COMPONENTS stored at station

installed ordnance plant
→ materials/components consumed
→ countable interceptor added
→ interceptor physical mass occupies hazardous storage
```

## Scope boundaries

Stage 18F deliberately does not own:

- route choice, inter-system travel or autonomous hauler AI;
- market pricing migration from legacy items to every Stage-18 commodity;
- shipyard berth/envelope integration — Stage 18G;
- repair/refit industrial consumption — Stage 18G;
- wreck-derived salvage/recycling yield — Stage 18H;
- final Stage-18 persistence wiring into the complete world save codec — Stage 18I acceptance/integration;
- hidden station production bonuses.

The next implementation owner is **Stage 18G — shipyard, repair and refit industrial integration**.
