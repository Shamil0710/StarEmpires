# Stage 10C — Galactic Route Planner

Status: COMPLETE — PR #21 pending merge. Stage 10D is the next active implementation focus in the roadmap.

## Purpose

Stage 10C extends the existing local `TradeRoutePlanner` to evaluate profitable supplier-to-consumer routes across multiple StarSystems without creating a second economic scoring stack.

The supported Stage-10C route shape is deliberately bounded:

```text
fleet in system A
    -> supplier in A
    -> deterministic multi-hop jump path
    -> consumer in B/C
```

Repositioning an empty fleet to a supplier in another system is not part of this substage; that belongs to a later multi-job logistics layer. Stage 10D is responsible for bounded cross-system candidate discovery.

## Deterministic galactic path layer

`GalacticPathPlanner` performs pure weighted path finding over immutable `GalaxyTopology`.

Each direct edge uses the same `JumpTransitTiming` policy as Stage 10B, including:

- approach ticks;
- pending ticks;
- detached transit ticks derived from topology distance;
- arrival ticks.

The planner therefore minimizes authoritative jump time rather than merely hop count or geometric distance.

Tie breaking is deterministic:

1. lower total jump ticks;
2. fewer path nodes/hops;
3. lexicographically smaller `StarSystemId` sequence.

`GalacticPath` records:

- ordered systems;
- total authoritative jump ticks;
- total jump seconds using the actual float fixed-step representation;
- summed topology distance.

Disconnected systems return no path, and unknown systems are rejected.

## System-qualified market identity

After Stage 10A, local `EntityId` values are scoped to one `SimulationSession`. A cross-system route therefore never identifies a market by bare `EntityId`.

`SystemMarketRef` combines:

```text
StarSystemId + local market EntityId/snapshot
```

This prevents accidental collisions between identically numbered entities in different systems.

## Market access in pure planning

`FleetTradeProfile` now includes the fleet runtime faction ID while keeping the previous constructor source-compatible for unfactioned callers.

`MarketDirectory.StationMarket` snapshots diplomacy-derived access state. The pure planner rejects inaccessible suppliers and consumers before scoring. The existing `FactionMarketAccessSystem` and `TradeController` remain the authoritative execution-time safety gate.

Access changes participate in `MarketDirectory.revision`, so cached negative route results cannot survive a diplomacy/access mutation unnoticed.

## Shared economic scoring

`TradeRoutePlanner.findBestGalacticRoute(...)` accepts a bounded list of `GalacticTradeOpportunity` candidates. It deliberately does not scan the galaxy.

For every candidate it reuses the same local planning logic for:

- cargo policy and specialization;
- physical capacity;
- supplier stock;
- consumer demand (`targetStock - stock`);
- fleet affordability;
- consumer liquidity;
- reputation-adjusted effective purchase/sale prices;
- expected purchase/sale amount;
- gross profit;
- `TradeRouteCostModel`;
- `GROSS_PROFIT` and `PROFIT_PER_SECOND` scoring modes;
- deterministic tie breaking.

The galactic result, `GalacticTradeRoute`, adds system-qualified endpoints, jump path, strategic distance, local travel estimate, total expected duration and route risk.

## Local travel estimate

Stage 10B does not yet define physical jump-gate anchor coordinates inside each local system. Stage 10C therefore does not invent them.

`GalacticTradeOpportunity` receives an explicit local travel distance/time estimate from the future candidate-discovery/execution layer. Total expected duration is:

```text
explicit local travel time + authoritative GalacticPath jump time
```

This keeps route scoring honest while leaving concrete gate geometry to the appropriate future gameplay/presentation layer.

## Unified cost seam: tariff and risk

`TradeRouteCostModel.Context` remains source-compatible with existing local callers and additionally supports optional galactic metadata:

- supplier system;
- consumer system;
- jump path;
- route-risk basis points.

`WorldTradeRouteCostModel` is the canonical world-policy adapter used by `WorldSimulation.createGalacticTradeRoutePlanner(...)`.

### Risk

Route risk is an expected-cost estimate against purchased cargo value:

```text
ceil(purchase value * route risk bps / 10,000)
```

It is planning exposure only; Stage 10C does not create combat loss events.

### Tariff semantics

The existing Stage-8 `foreignTerritoryTariff` is not a synthetic per-trade transaction tax. It levies foreign market wallet surplus inside controlled territory.

Accordingly, Stage 10C estimates only the positive marginal fiscal exposure created when the fleet purchases from a foreign-owned supplier inside another faction's controlled system. That purchase increases the supplier wallet and therefore its future levy base.

Selling to a consumer reduces that consumer wallet and does not receive an invented destination transaction tariff.

The adapter changes planning cost only. It does not move money and does not alter authoritative fiscal accounting.

## World factory

`WorldSimulation` exposes canonical factories:

- `createGalacticPathPlanner()` — current topology + Stage-10B timing + authoritative fixed step;
- `createGalacticTradeRoutePlanner(scoringMode)` — current content + strategic world tariff/risk cost adapter.

This prevents later stages from accidentally constructing a galactic planner without the current world policy.

## Acceptance evidence

The Stage 10C suite verifies:

- deterministic weighted multi-hop path selection;
- equal-cost deterministic path ordering;
- zero-hop and disconnected topology behavior;
- exact reuse of Stage-10B structural jump barriers;
- pure market-access filtering, including unfactioned access;
- market-directory revision invalidation when access changes;
- system-qualified route endpoints;
- shared `TradeRouteCostModel.Context` receives systems/path/risk;
- external route cost reduces net galactic profit and can reject a route;
- world risk and foreign-territory tariff exposure arithmetic;
- world fiscal exposure can flip supplier choice inside the same `TradeRoutePlanner`;
- world planner factories use the current demo topology and Stage-10B path timing;
- existing local route planner behavior continues to pass the full regression suite.

The final functional branch gate before documentation passed the complete Java 17 CI pipeline: tests, coverage, strict Javadoc and desktop packaging.

## Deliberately deferred to Stage 10D

Stage 10C consumes a bounded candidate list. It does not implement:

- full-galaxy market scans;
- regional/sector market indexes;
- search horizon;
- per-fleet candidate limits;
- stale candidate invalidation across market/topology changes;
- market discovery scheduling.

Those are the explicit responsibilities of Stage 10D — Cross-system Market Discovery.

## Deliberately deferred to Stage 10E

Stage 10C evaluates an inter-system opportunity but does not yet execute a complete autonomous multi-hop trade job. Stage 10E will prove the physical economic loop:

```text
surplus in A
    -> cross-system route selected
    -> fleet physically jumps
    -> cargo delivered to shortage in B
    -> shortage recovers
```
