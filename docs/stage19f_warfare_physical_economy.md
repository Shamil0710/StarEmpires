# Stage 19F — Warfare ↔ Stage-18 physical economy

Status: implementation slice for Stage 19F.

## Purpose

Stage 19F closes the causal boundary between physical combat losses and the Stage-18 industrial economy. It does not add a warfare currency, readiness multiplier, virtual reinforcement pool, free docking refill or second combat engine.

The intended chain is:

```text
physical combat / maneuver
→ ammunition and reaction-mass expenditure / damage / destruction
→ actual material shortage
→ Stage-18 storage, logistics, industry and shipyard settlement
→ physical resupply / repair / replacement
→ changed physical combat readiness
```

## Ownership boundaries

Stage 17.5 remains authoritative for fitted ship engineering, physical ammunition consumption, damage, weapons, missiles, point defense and destruction.

Stage 18 remains authoritative for:

- canonical kg-native commodity storage;
- countable manufactured modules and ammunition;
- logistics transfers;
- commodity-to-interface servicing through `Stage18ShipConsumableService`;
- damage-scaled repair bills and settlement through `Stage18ShipyardRuntime`;
- physical ship construction / replacement through the existing shipyard runtime;
- salvage and recycling after physical destruction.

Stage 19F only connects those existing owners where a warfare-facing servicing seam is missing.

## Ammunition servicing seam

`Stage19WarfareSupplyService` connects a countable Stage-18 finished ammunition product to the production Stage-17.5 `ConsumableState` used by `AmmunitionRuntime`.

A successful load therefore requires all of the following:

1. the content ID exists in `Stage18ManufacturingProductRegistry`;
2. the product is physically classified as `AMMUNITION`;
3. a production `Launcher` and an authoritative ammunition `InterfaceDefinition` agree on the interface ID;
4. the requested rounds fit inside the authored interface capacity;
5. canonical `Stage18StationStorage` contains the requested finished-product count.

Only after all checks pass are finished rounds removed from station storage. The exact unit mass from the Stage-18 manufacturing registry is added to the central ship consumable state, together with the corresponding item count and launcher-native interface amount.

The resulting feed is not a Stage-19 abstraction: `AmmunitionRuntime.consumeOne(...)` removes the same item count, interface amount and physical mass when the weapon fires.

Rejected servicing mutates neither storage nor ship state. Docking alone never refills ammunition.

## Reaction mass

No new Stage-19 reaction-mass ledger is introduced. `Stage18ShipConsumableService` remains the servicing owner. The existing binding `ship_consumable.reaction_mass.escort_water_v1` removes canonical `commodity.material.purified_water` from Stage-18 station storage before adding physical mass to the fitted `propellant_feed` interface. Insufficient storage or interface capacity rejects the operation.

## Repair

No Stage-19 repair shortcut is introduced. Combat damage must remain on Stage-17.5 ship damage state and is restored only through `Stage18ShipyardRuntime` / `ShipyardEngineeringService` repair planning and settlement. Repair material requirements and work are derived from the actual damage request; settlement removes the exact material bill from canonical station storage before applying repairs.

Credits do not repair integrity and a strategic AI order cannot bypass the material/work settlement.

## Destroyed ships and replacements

A destroyed ship is not restored by changing a readiness counter. Destruction remains on the existing physical destruction/wreck path. A replacement is a new physical construction order settled by the Stage-18 shipyard runtime, consuming the required hull materials, finished modules, yard capability and work before a persistent replacement ship exists.

Salvage/recycling may return only physically recoverable mass through the existing Stage-18 salvage chain; it is not a refund of abstract fleet strength.

## Acceptance invariants

Stage 19F is accepted only while these invariants hold:

- firing reduces the production central ammunition state;
- loading ammunition consumes the same countable Stage-18 finished products that industry/logistics produce and move;
- ammunition unit mass is preserved from finished storage into the ship feed and out again when fired;
- insufficient ammunition stock cannot produce rounds;
- authored feed capacity cannot be exceeded;
- reaction mass continues to consume canonical Stage-18 commodity mass;
- physical repair continues to consume actual repair materials/work;
- destroyed ships require the existing physical replacement-construction path;
- no Stage-19 scalar directly changes storage, production, damage, ammunition or ship count.

## Deferred work

Stage 19G may use these physical shortages and recovery times as inputs to strategic war objectives, escalation and coercive diplomacy, but must not convert them into authoritative hidden bonuses. Stage 19I will later exercise production tactical AI against the live physical combat runtime at fleet scale.
