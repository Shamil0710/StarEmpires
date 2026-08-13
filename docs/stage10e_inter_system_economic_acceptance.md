# Stage 10E — Inter-system Economic Acceptance

Status: COMPLETE candidate in PR #23, pending final green CI and integration.

## Purpose

Stage 10E connects galactic planning to physical money, cargo and fleet transit.

```text
world FleetId in supplier system
        ↓
bounded Stage-10D discovery
        ↓
Stage-10C TradeRoutePlanner scoring
        ↓
TradeController purchase
        ↓
Stage-10B jump transit
        ↓
world FleetId materializes with a new local EntityId
        ↓
live destination revalidation
        ↓
TradeController sale
```

No world-level cargo teleport or synthetic money settlement is introduced.

## Execution

`InterSystemTradeService` captures an existing world fleet as a `FleetTradeProfile`, runs bounded discovery and passes the resulting shortlist to the canonical world galactic `TradeRoutePlanner`.

`InterSystemTradeJob` then executes the selected route through existing authoritative APIs. The source purchase uses `TradeController.buyFromStation(...)`. Every path edge uses `WorldSimulation.requestFleetJump(...)`. The destination sale uses `TradeController.sellToStation(...)`.

The fleet therefore carries the actual purchased inventory and wallet through Stage-10A/B world handoff and receives a fresh system-local `EntityId` after arrival while retaining the same world `FleetId`.

## Live destination revalidation

A planned route is not a reservation. Destination stock, demand, price, capacity and liquidity may change while the fleet is in transit.

On arrival the job revalidates:

- faction market access;
- item tradability;
- live demand;
- free inventory capacity;
- effective buy price;
- consumer wallet debit capacity;
- fleet wallet credit capacity.

The largest still-valid positive amount is sold atomically through `TradeController`. Unsold cargo remains physically in the fleet for later replanning. If no positive transaction remains valid, the job fails without deleting cargo or bypassing market rules.

## Acceptance evidence

`Stage10InterSystemLogisticsAcceptanceTest` verifies a deterministic demo-galaxy scenario:

- Anchor contains a physical surplus;
- Corona contains a profitable shortage;
- a real world fleet buys cargo in Anchor;
- the fleet enters the Stage-10B jump FSM;
- the same world `FleetId` arrives in Corona with a new local `EntityId`;
- a positive live-valid cargo amount is sold through the authoritative controller;
- Corona stock is physically higher after delivery.

The same suite verifies aggregate market-revision invalidation and configurable one-hop versus two-hop discovery horizons.

## Connectivity scope

`GalaxyTopology` currently represents immutable canonical jump connectivity. Stage 10E proves discovery and execution only across reachable topology edges. Runtime edge availability is intentionally a separate future world policy rather than a benchmark-only mutation of immutable topology.

## Definition of Done

Stage 10 is complete when world fleet identity, deterministic jump transit, shared galactic scoring, bounded discovery and physical inter-system cargo delivery all work together while the full regression, strict Javadoc, coverage and desktop packaging pipeline remains green.
