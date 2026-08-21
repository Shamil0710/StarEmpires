# Stage 20E — Commodity frontier shared-producer bound v1

> Status: **CANDIDATE SEARCH-PRUNING EVIDENCE / NO PHYSICAL AUTHORITY CHANGE**  
> Version: `stage20e.commodity-frontier-shared-producer-bound.v1`

## Why this slice exists

The accepted frontier-generator v2 reduced the fixed-corpus result from `0 accepted / 2 infeasible / 13 unresolved` to `12 accepted / 2 infeasible / 1 unresolved` at the unchanged 2,000-node evidence budget per commodity. The sole remaining unresolved case is fixed seed 8, `commodity.feedstock.water_ice`.

Seed 8 has about `104.9053 kg/s` of authoritative water producer capacity against `100 kg/s` of whole-placement bootstrap demand. The existing route-prefix optimistic bound ranks remaining route marginals by faction start but does not retain shared producer capacity or producer-to-demand reachability in the bound. With such small global headroom, that relaxation can overestimate many cap vectors enough to spend the whole evidence budget before proving them impossible.

This slice measures a stronger, still completeness-safe upper bound before changing the production search.

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

This result is safe for exact-search pruning.

### `POSSIBLY_FEASIBLE`

If relaxed max flow reaches total demand, no acceptance claim follows. Exact integer route-prefix/fleet-sharing search remains mandatory.

The bound must never be used as:

- a whole-placement acceptance authority;
- a producer reservation plan;
- a fleet materialization plan;
- a replacement for integer route-prefix search;
- a justification to change topology/resources/demand/fleet budgets.

## Candidate evidence gate

PR #288 initially keeps this bound outside `Stage20CommodityWholePlacementFrontierGenerator` and measures it on fixed seed 8 water across every cap vector from the accepted single-start minimum vector through the unchanged 13-freighter/start maximum.

The measurement records:

- actual single-start minimum vector;
- number of assessed cap vectors;
- count proved impossible by the shared-producer relaxation;
- count remaining possibly feasible;
- exact list of remaining cap vectors.

There is no pass-rate target. If the bound does not materially reduce the seed-8 search region, it should not be integrated merely because it is theoretically stronger.

## Regression boundary

Synthetic tests require:

- shared producer capacity can prove a cap vector impossible even when each demand is individually reachable;
- relaxed per-start ship sharing never becomes a concrete acceptance claim;
- assignment/map iteration order does not change the result;
- non-neighbor route paths fail closed rather than contributing optimistic capacity.

## Explicit non-authorities

This candidate changes none of:

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

## Next causal decision

If exact-head CI confirms that the bound removes a meaningful portion of seed-8 water cap vectors, the next slice should integrate the same proof into the frontier generator before route-prefix DFS and rerun the unchanged 2,000-node fixed corpus.

Only after every accepted-placement commodity frontier is complete or otherwise decisionally resolved should Stage 20E proceed to bootstrap freight ownership/materialization.
