# Stage 20E — Commodity frontier shared-producer bound v1

> Status: **VERIFIED NECESSARY-CONDITION BOUND / NO PHYSICAL AUTHORITY CHANGE**  
> Version: `stage20e.commodity-frontier-shared-producer-bound.v1`

## Why this slice exists

The accepted frontier-generator v2 reduced the fixed-corpus result from `0 accepted / 2 infeasible / 13 unresolved` to `12 accepted / 2 infeasible / 1 unresolved` at the unchanged 2,000-node evidence budget per commodity. The sole remaining unresolved case was fixed seed 8, `commodity.feedstock.water_ice`.

Seed 8 has about `104.9053 kg/s` of authoritative water producer capacity against `100 kg/s` of whole-placement bootstrap demand. The existing route-prefix optimistic bound ranks remaining route marginals by faction start but does not retain shared producer capacity or producer-to-demand reachability in the bound. With such small whole-placement headroom, that relaxation can overestimate impossible cap vectors enough to spend the whole evidence budget before proving them impossible.

This slice introduces a stronger, completeness-safe necessary-condition bound without changing any physical or economic authority.

## Relaxed network

For one commodity and one per-start fleet-cap vector, construct a max-flow network:

```text
source
→ producer nodes       capacity = authoritative producer kg/s
→ demand/start nodes   capacity = optimistic route capacity under the assessed start cap
→ sink                 capacity = authoritative bootstrap demand per start
```

Local producer-to-start service uses the producer capacity and consumes zero inter-system ships, matching the existing frontier authority.

For a remote producer-to-start arc, the bound evaluates the same authoritative allocated-freighter route curve and admits only routes that:

- exist under the physical route evaluator;
- follow explicit topology neighbors;
- preserve path and route time as ship count changes;
- fit the bootstrap route-time limit;
- have monotone cumulative throughput;
- have non-increasing marginal throughput.

The arc capacity is the maximum cumulative physical throughput reachable when that one route is allowed to consume the whole assessed cap for its start.

## Deliberate relaxation

The real frontier search requires all remote routes serving one start to share one finite fleet cap.

The bound deliberately ignores that competition:

```text
real plan:
  sum ships over all remote routes to start S <= cap[S]

relaxed bound:
  every individual remote route to S may independently use up to cap[S]
```

Therefore the relaxed network is a superset of the physically feasible set. It may say `POSSIBLY_FEASIBLE` for a cap vector that the exact search later proves impossible, but it cannot exclude a physically feasible plan.

Shared producer capacity is **not** relaxed: all producer-to-demand arcs still compete through the authoritative source-to-producer capacity edge.

## Status semantics

### `PROVED_INFEASIBLE`

If relaxed max flow is below total whole-placement demand, the real problem is also infeasible under that cap vector because the real search has strictly no more routing capacity than the relaxed network.

This result is safe for exact-search pruning and for a necessary-condition whole-frontier precheck at the maximum physical cap vector.

### `POSSIBLY_FEASIBLE`

If relaxed max flow reaches total demand, no acceptance claim follows. Exact integer route-prefix/fleet-sharing search remains mandatory.

The bound must never be used as:

- a whole-placement acceptance authority;
- a producer reservation plan;
- a fleet materialization plan;
- a replacement for integer route-prefix search when the bound remains possible;
- a justification to change topology/resources/demand/fleet budgets.

## Exact-head seed-8 evidence

PR #288 measured the bound on fixed seed 8 water using the actual production-probe topology, supply report, physical allocated-route evaluator and the unchanged `13`-freighter/start capacity authority.

Exact-head Java 17 `clean verify` run `32485913806` completed successfully with `1457` tests, `0` failures, `0` errors, `1` skipped, Javadoc and coverage gates passing.

Measured evidence:

```text
rootSeed=8
commodity=commodity.feedstock.water_ice
maximumShipsPerStart=13
minimumShipsByFaction={faction.alpha=10, faction.beta=6}
capVectorCount=32
provedInfeasibleCapVectorCount=32
possiblyFeasibleCapVectorCount=0
possiblyFeasibleCapVectors=[]
```

The assessed grid is exactly:

```text
alpha = 10..13
beta  = 6..13
```

so it includes the maximum physical cap vector `(13,13)`.

Because every remote route in the relaxation may independently consume the full cap of its start, while authoritative producer capacity and producer-to-demand reachability remain enforced, failure even at `(13,13)` is stronger than the real transport problem's necessary condition. Therefore seed 8 water is **physically infeasible under the accepted Stage-20E authorities**, not merely unresolved because of DFS search budget.

This finding does not imply that global water tonnage is insufficient. Aggregate supply exceeds the 100 kg/s two-start requirement. The proven failure comes from the combination of authoritative producer capacity, admitted route reachability/time and finite per-start freight capability.

## Regression boundary

Synthetic tests require:

- shared producer capacity can prove a cap vector impossible even when each demand is individually reachable;
- relaxed per-start ship sharing never becomes a concrete acceptance claim;
- assignment/map iteration order does not change the result;
- non-neighbor route paths fail closed rather than contributing optimistic capacity.

## Explicit non-authorities

This bound changes none of:

- fixed seed corpus;
- Stage-20 topology or geometry;
- Stage-18 resource occurrence or producer output;
- bootstrap water/ore demand;
- route time/cadence/payload;
- derived `13` remote freighters per ordinary start;
- producer ownership;
- inventories/buffers;
- monetary delivered cost;
- production world acceptance;
- freight ownership/materialization.

## Next causal slice

Integrate the verified bound into `Stage20CommodityWholePlacementFrontierGenerator` as an early necessary-condition precheck using the **maximum authoritative per-start cap vector**.

Required semantics:

1. if the maximum-cap relaxation is `PROVED_INFEASIBLE`, return a `COMPLETE` empty commodity frontier without spending route-prefix DFS nodes;
2. if it is `POSSIBLY_FEASIBLE`, preserve the existing exact frontier search unchanged;
3. rerun the same fixed seeds `1..16` at the unchanged 2,000-node per-commodity evidence budget;
4. do not introduce a pass-rate target and do not change physical/economic authority.

If that integration converts the sole unresolved seed 8 frontier into a complete physical infeasibility proof while preserving all other results, the coordinated frontier-search uncertainty gate is closed sufficiently for Stage 20E to proceed to bootstrap freight ownership/materialization and the remaining economic authorities.
