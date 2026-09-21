# M22.8G — physical logistics and replacement

Status: **IMPLEMENTED CANDIDATE — stacked on M22.8F / pending exact-head CI**  
Parent gate: `M22.8 — Carrier / Small-Craft Operations`

## Scope

M22.8G closes the first physical industry/logistics loop for persistent individual small craft without
creating a carrier-only economy, virtual replacement pool or free replenishment path.

The production/replacement chain is:

```text
ordinary Stage-18 station stock + installed shipyard capability
→ ShipyardEngineeringService build plan
→ Stage18ShipyardRuntime.settleBuild(...)
→ real hull materials + finished modules + finite yard work consumed
→ fresh monotonic SmallCraftId
→ physically completed but unserviced craft
→ pending ordinary delivery
→ validated physical arrival receipt
→ compatible bay assignment in SERVICING
→ M22.8C turnaround + ordinary Stage-18/19 supplies
→ READY only after finite service completion
```

## Stage-18 manufacture is authoritative

`SmallCraftPhysicalLogisticsService` never invents build materials or work.

`planBuild(...)` delegates fitting/work requirements to the accepted common
`ShipyardEngineeringService`.

`settleBuild(...)` delegates physical settlement to
`Stage18ShipyardRuntime.settleBuild(...)`, which already consumes:

- authored bare-hull Stage-18 commodity masses;
- one finished manufactured module product per fitted mount;
- finite shared yard work.

A rejected settlement creates no craft, advances no small-craft allocator and adds no pending
delivery. Stage-18 remains responsible for atomic storage/work rollback on rejection.

Only after a successful physical settlement does G reserve a fresh `SmallCraftId` and register the
completed physical craft.

## No free combat readiness

A newly built craft receives:

- the exact fitted hull/modules that were physically settled;
- pristine local structure/subsystem integrity;
- ordinary initialized power/thermal/propulsion state;
- **empty physical consumables**;
- no ammunition feed identity;
- no free charged shield reserve;
- initial maintenance state.

Therefore manufacture does not implicitly create reaction mass, ammunition or mission stores.

The new craft is not assigned to any carrier/station bay and cannot become READY merely because
construction completed.

## Replacement identity continuity

Replacement is ordinary manufacture, not resurrection.

A successful replacement:

- receives a fresh monotonic `SmallCraftId`;
- cannot reuse a destroyed/reserved earlier identity;
- preserves the registry allocator watermark;
- creates no relationship that rewrites the identity of the lost craft.

M22.8E remains the destruction authority. G only manufactures a later physical asset.

## Physical delivery

Successful construction creates a `PendingDelivery` record containing the new craft identity,
production station and design identity.

`confirmDelivery(...)` cannot assign the craft by itself. It requires a receipt from the injected
`PhysicalDeliveryAuthority`, representing the ordinary transport/logistics layer.

The receipt must match:

- exact `SmallCraftId`;
- production source station;
- destination host;
- authoritative arrival tick.

Rejected or mismatched delivery leaves the craft pending and outside bay occupancy.

A successful arrival assigns the exact craft to the destination bay in **SERVICING**, never READY.
Bay mass/envelope capacity is therefore rechecked by the existing M22.8B authority at the real
delivery boundary.

## Observable carrier-operation demand

G projects physical demand from the accepted M22.8C `TurnaroundPlan` instead of maintaining a
second supply model.

`TurnaroundDemand` preserves:

- exact consumable transfers, including interface-native amount, physical mass and item count;
- finite inspection/transfer handling work;
- optional ordinary Stage-17.5 repair work plan;
- optional ordinary Stage-17.5 maintenance/spares work plan.

The actual physical supply authorities already exist:

- `Stage18ShipConsumableService` drains bound bulk commodities such as reaction mass from canonical
  station stock;
- `Stage19WarfareSupplyService` drains manufactured physical ammunition products;
- `Stage18ShipyardRuntime.settleRepair(...)` consumes damage-scaled repair materials and yard work;
- `Stage18ShipyardRuntime.settleMaintenance(...)` consumes physical maintenance/spares inputs and
  finite yard work.

G does not duplicate those inventories or manufacture outputs.

## Supply interruption and readiness

Because new/recovered craft remain SERVICING until C receives complete finite settlements, carrier
wing readiness can lawfully degrade when any ordinary dependency is missing:

- propellant stock;
- manufactured ammunition;
- repair materials/components;
- maintenance/spares inputs;
- finite handling or yard work;
- physical bay capacity/condition;
- replacement manufacture;
- transport/delivery.

No doctrine or strategic tick may synthesize the missing resource.

## Acceptance evidence

The M22.8G acceptance suite uses the authored Stage-22 Empire corvette fit and Empire Stage-18
physical shipyard definitions, composed through the common core-pair engineering/industrial
authority.

It proves:

- missing physical build inputs reject atomically with no craft identity allocation;
- a settled build consumes real Stage-18 commodity/module inventory and finite yard work;
- completed craft receives a fresh monotonic identity;
- construction provides no ammunition, reaction mass, ammunition feed identity or charged shield
  reserve;
- completed craft remains outside hangar occupancy with one pending physical delivery;
- an unarrived transport receipt leaves the craft pending and unassigned;
- a matching arrived receipt assigns the exact craft as SERVICING, not READY;
- a mismatched delivery receipt fails closed;
- C turnaround demand is projected exactly without synthetic supply.

## Deferred intentionally

- M22.8H — Stage-21 strategic carrier-group operation composition and actual fleet-order execution;
- M22.8I — player-facing carrier UI;
- M22.8J — dedicated production small-craft/carrier content and visual binding;
- M22.8K/L — representative balance/counterplay and dense-wing performance evidence;
- M22.8M — campaign persistence/migration of G pending-delivery and deployed mission/local-flight
  checkpoints;
- M22.8N — integrated industry → delivery → launch → combat → recovery → replacement soak.
