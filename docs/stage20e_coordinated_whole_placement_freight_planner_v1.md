# Stage 20E — Coordinated whole-placement freight planner v1

Status: **planner foundation; not yet promoted into production whole-seed acceptance**.

## Why this layer exists

Fixed-corpus evidence after the corrected bootstrap service cadence and the derived 13-freighter per-start capacity showed:

- 15/16 seeds have accepted v2-candidate faction-start placement;
- all 15 accepted placements are feasible when each start is planned independently with the derived 13-freighter service-capacity bound;
- only 4/15 of the independently selected whole placements reserve shared producer capacity without conflict;
- 11/15 conflict because independently optimal start portfolios may choose the same finite producer capacity.

Therefore the next causal problem is not resource abundance, demand rate, route availability or per-start fleet size. It is **global supplier coordination**.

## Contract

`Stage20CoordinatedWholePlacementFreightPlanner` accepts:

- authoritative explicit-neighbor `GalaxyTopology`;
- an already accepted `PlacementResult`;
- authoritative non-reserved `SupplyThroughputReport`;
- calibrated essential `CommodityRequirement`s;
- an explicit finite remote-freighter budget **per placed start**;
- a caller-authorized search-node budget;
- the physical allocated-route evaluator used by Stage 20E freight physics.

It returns one of:

- `ACCEPTED` — a globally feasible coordinated supplier/freight plan was found;
- `INFEASIBLE` — the fixed physical state was proved infeasible under the supplied fleet bounds;
- `UNRESOLVED_SEARCH_BUDGET` — bounded search ended before a proof; this must not be converted into seed rejection.

## Two coupled accounting layers

### Integer freight allocation

For each remote producer-to-start route, one, two, ... freighters are evaluated through the existing physical allocated-route API.

The planner requires:

- unchanged route path as ship count increases;
- unchanged travel time as ship count increases;
- route time inside the requirement service boundary;
- monotone cumulative throughput;
- non-increasing marginal throughput.

A route can therefore receive freighter `k+1` only after the complete prefix `1..k` has been allocated.

Each placed start has its own finite ship budget. Ships are never reused between routes or commodities inside that start.

### Shared producer throughput

At every discrete freight state the planner solves a deterministic maximum-flow problem:

- source → `SupplyKey` capacity = authoritative physical producer kg/s;
- producer → local demand = local service arc with zero inter-system ships;
- producer → remote demand = physical route capacity at the current integer ship count;
- demand → sink = calibrated required kg/s.

This means local and remote consumers compete for the same producer capacity. Local production is not automatically reserved for the local start. If global feasibility requires it, the flow may export that producer's output and satisfy the local start from a different physical supplier.

## Bounded deterministic search

The planner begins with zero remote route allocations. If the current maximum flow is below whole-placement demand, it computes the residual source-side minimum cut.

Only a next-route-freighter capacity increment crossing that cut can increase maximum flow. The planner therefore branches only on those increments, in deterministic order.

This preserves the relevant search space without enumerating the full Cartesian product of every route count. Failed discrete states are memoized.

The search-node limit is supplied by the caller. The planner intentionally has no hidden default that could turn a computational limit into a physical rule.

## Pre-proofs

Before coordinated search:

1. if total authoritative producer capacity for a required commodity is below total whole-placement demand, the result is `GLOBAL_PRODUCER_CAPACITY_INSUFFICIENT`;
2. every start is checked independently with `Stage20FreightPortfolioAllocator`; if even one start cannot satisfy itself with the full supplied per-start budget, the result is `SINGLE_START_INFEASIBLE`.

These are safe necessary conditions. Passing them does not imply that shared producer capacity is globally feasible.

## Accepted evidence

An accepted plan exposes:

- per-faction/per-start finite remote freighters used;
- per-commodity actual delivered kg/s;
- exact local/remote producer commitments;
- exact physical route for every remote commitment;
- authoritative producer capacity and actual reserved kg/s.

Only actual required flow is reserved. Surplus capacity exposed by the final allocated freighter is not consumed automatically.

## Non-authorities

This v1 planner does **not**:

- create or grant ships;
- assign ship or facility ownership;
- create initial inventory or safety stock;
- create resources or extraction sites;
- add/repair topology edges;
- change calibrated demand rates or route-time boundaries;
- calculate monetary whole-route delivered cost;
- establish buffer depletion/resilience acceptance;
- establish ownership concentration acceptance.

Those remain separate Stage-20E closure requirements.

## Promotion sequence

Before production whole-seed acceptance changes:

1. exact-head tests must validate planner invariants;
2. run the planner read-only on the same fixed 1..16 v2-candidate corpus using the independently derived 13-freighter per-start authority;
3. measure `ACCEPTED`, proven `INFEASIBLE` and `UNRESOLVED_SEARCH_BUDGET` separately;
4. only then decide whether the planner semantics are ready for a versioned current acceptance path.

The historical v1 corpus snapshot remains immutable. Stage 22 review remains required for provisional calibration/content assumptions.