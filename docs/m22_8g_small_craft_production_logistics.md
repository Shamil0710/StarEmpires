# M22.8G — physical small-craft production, station delivery and supply

Status: **IMPLEMENTED CANDIDATE — depends on accepted M22.8F and exact-head CI**  
Parent gate: `M22.8 — Carrier / Small-Craft Operations`

## Scope

M22.8G closes the physical replacement and supply boundary for individual persistent small craft.

It reuses:

- Stage-17.5 authored engineering fits and shipyard work planning;
- Stage-18 shipyard material/module/work settlement;
- Stage-18 station storage and commodity servicing;
- Stage-18 manufactured-product inventory;
- Stage-19 warfare-supply ammunition loading;
- M22.8A stable `SmallCraftId` allocation and physical engineering state;
- M22.8B finite bay mass/envelope capacity.

There is no fighter pool, abstract replacement currency, docking refill, hidden warehouse or direct
spawn into a carrier.

## Build causal chain

One new craft is created only through:

```text
authored production fit
→ Stage-17.5 shipyard plan
→ exact next-ID / station-bay preflight (read-only)
→ Stage-18 hull commodities + finished fitted modules + finite yard work settlement
→ fresh monotonic SmallCraftId allocation
→ Stage-17.5 build completion proof
→ pristine but UNSUPPLIED engineering state
→ physical PARKED assignment in the same station that owns the shipyard/storage
```

Identity is allocated only after Stage-18 settlement succeeds. Failed planning, stock, work or bay
capacity therefore cannot consume a small-craft identity.

The completed craft starts with:

- zero reaction mass;
- zero ammunition;
- no ammunition feed identity;
- no charged shield reserve;
- no cargo/mission payload.

Ordinary new-equipment runtime initialization is allowed for installed hardware state, but it does
not synthesize consumable stores.

## No teleport delivery

The production destination must be:

- a `HostKind.STATION` bay;
- whose `BayId.hostStableId` exactly equals the owning
  `Stage18StationIndustrialNode.stationId`.

A build request targeting a carrier bay or another station fails before Stage-18 settlement.

This is intentionally the only immediate delivery allowed by G. Moving the newly built craft from
the production station to a carrier requires ordinary launch/mission/recovery and later M22.8H
strategic operation composition.

## Capacity preflight before settlement

Before any material/work settlement, G creates a non-mutating preview using exactly the current next
unused `SmallCraftId`.

The preview must:

- resolve the authored fit through `SmallCraftFitAuthority`;
- satisfy ordinary Stage-17.5 fitting budgets;
- fit the current station bay envelope, usable volume and supported mass.

This prevents consuming a hull's materials/work and then discovering that the current location model
cannot lawfully hold the completed physical craft.

## Physical refuel

`SmallCraftStationSupplyService.loadCommodityAtStation(...)` requires the craft to be physically
`PARKED` or `SERVICING` in the exact station bay.

It:

1. preflights added mass against current hull operational mass and degraded/current bay capacity;
2. delegates stock/interface validation and removal to `Stage18ShipConsumableService`;
3. commits the returned central `ConsumableState` to the same `SmallCraftId`.

For the current Empire endurance drive this means purified-water reaction mass uses the existing
`ship_consumable.reaction_mass.empire_endurance_water_v1` binding. If stock is absent, neither
station storage nor craft state changes.

## Physical rearm

`SmallCraftStationSupplyService.loadAmmunitionAtStation(...)` uses manufactured Stage-18 product
identity directly as the Stage-17.5/19 ammunition content identity.

Before stock removal it checks:

- the requested weapon mount exists on the fitted craft;
- an accepted launcher profile exists and exposes the physical ammunition interface;
- the Stage-18 product is ammunition;
- the exact ammunition content exists in the common tactical catalog;
- physical mass/length/diameter fit the launcher envelope;
- authored kinetic projectile mass agrees with the loaded body where the module specifies it;
- already-loaded rounds are not silently relabelled as a different ammunition identity;
- hull and bay mass remain lawful after loading.

Only then does `Stage19WarfareSupplyService` remove finished rounds from station storage.

After a successful load the same persistent craft receives:

- the updated physical ammunition `ConsumableLoad`;
- a matching `WeaponLoadoutState.FeedBinding` for that mount/interface/product.

This explicitly closes the M22.8E requirement that Stage-19 combat knows which physical ammunition
body occupies a feed.

## Supply interruption and readiness degradation

Rejected refuel/rearm requests do not mutate craft or storage. A craft can therefore remain parked or
servicing with:

- zero/low propellant;
- zero/low ammunition;
- unresolved repair/maintenance backlog.

M22.8F reads those exact physical states when deciding recovery and wing readiness. G does not
fabricate a readiness number or compensate for missing supply.

## Replacement continuity

E permanently removes destroyed craft without rewinding the allocator.

G allocates replacement identity only after another real shipyard settlement. The next produced craft
therefore receives a fresh monotonic `SmallCraftId`; no destroyed identity can reappear because of
a strategic tick, supply call or production completion.

## Acceptance evidence

Automated tests use the real Stage-22 Empire corvette fit and physical Empire shipyard catalog to prove:

- ordinary Stage-18 hull commodities, finished fitted modules and finite yard work are required;
- successful build creates a fresh identity only after settlement;
- the new craft is PARKED at the exact production station and starts unsupplied;
- insufficient Stage-18 stock changes neither storage, work/identity authority nor craft registry;
- an incompatible bay blocks the build before settlement;
- direct build delivery to a carrier or another station is rejected;
- station-local purified-water reaction mass is removed from canonical commodity stock and loaded into
  the same craft;
- manufactured Empire kinetic rounds are removed from canonical product stock, loaded into the same
  physical feed and bound to the matching ammunition content identity;
- supply interruption leaves both craft and storage unchanged;
- degraded bay mass capacity blocks loading before stock mutation.

## Deferred intentionally

- M22.8H — Stage-21 carrier-group strategic operation composition and station→carrier transfer;
- M22.8I — player-facing carrier/wing/logistics UI;
- M22.8J — final production small-craft hull/fit/art content (G uses an already production-authorized
  Empire corvette as the provisional causal proof, without promoting it to final small-craft content);
- M22.8K/L — balance/counterplay and dense-wing performance evidence;
- M22.8M — complete save/load/migration hardening for replacement/supply checkpoints;
- M22.8N — integrated industry→combat→loss→replacement soak.
