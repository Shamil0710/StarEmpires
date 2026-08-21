# Stage 20E — Coordinated whole-placement freight search v2

> Status: **CANDIDATE SEARCH IMPLEMENTATION / NO PHYSICAL AUTHORITY CHANGE**  
> Planner version: `stage20e.coordinated-whole-placement-freight-planner.v2`

## Why this slice exists

The fixed representative corpus under planner v1 established a narrow search problem rather than a new physical-world calibration problem:

- 15/16 fixed seeds reached accepted v2-candidate faction placement;
- 12/15 accepted placements were solved by the globally coordinated freight planner;
- seeds 4 and 6 were proved infeasible by real aggregate producer scarcity;
- seed 8 alone remained `UNRESOLVED_SEARCH_BUDGET` at 2,000 nodes;
- targeted convergence evidence then kept seed 8 unresolved at 2,000, 4,000 and 8,000 nodes.

The causal next step is therefore to improve deterministic bounded search before changing any resource, demand, topology, fleet-capacity or acceptance threshold.

## Unchanged authorities

Planner v2 keeps the same:

- accepted faction-start placement;
- explicit neighbor topology and physical route validation;
- route-time admission boundary;
- route payload/throughput curves;
- finite per-start inter-system freighter budget;
- shared authoritative producer capacities;
- local-vs-remote freight accounting;
- max-flow feasibility semantics;
- `ACCEPTED / INFEASIBLE / UNRESOLVED_SEARCH_BUDGET` distinction;
- fail-closed treatment of search-budget exhaustion.

It does **not** add ships, resources, stock, routes, producer output or hidden delivery capacity.

## Optimization 1 — most-constrained demand first

The v1 search already branches only on incrementable remote route arcs crossing the current residual minimum cut. Planner v2 preserves that exact candidate set.

Only traversal order changes.

For the current cut, candidates are ordered by:

1. fewest currently augmentable cut-crossing routes for that demand;
2. larger current demand deficit;
3. larger next physical route marginal;
4. existing deterministic start / commodity / supplier / consumer ties.

This is a traversal heuristic only. No branch is accepted, rejected or removed because of the heuristic.

A regression covers the canonical conflict shape where start A can use suppliers C or D while start B can use only C. The constrained demand must be visited first, allowing the valid `A <- D`, `B <- C` plan to be found within a three-node evidence budget without changing any physical capacity.

## Optimization 2 — optimistic remaining-fleet upper bound

Planner v2 may prune a search state only when even an intentionally optimistic descendant-capacity bound cannot satisfy total demand.

For each placed start:

```text
remaining ships = per-start fleet budget - ships already allocated
```

The planner collects **all** remaining marginal route increments after the current route prefixes, sorts them descending and adds the largest `remaining ships` marginals to the current maximum flow.

This deliberately overestimates what descendants can really deliver because it ignores, among other things:

- shared producer competition between different routes;
- the fact that later marginals require earlier route prefixes;
- contention between different demands for the same producer;
- flow-network bottlenecks that may prevent added arc capacity from becoming delivered flow.

Therefore:

```text
optimistic bound >= maximum physically achievable descendant throughput
```

Only when:

```text
current max flow + optimistic remaining increase + epsilon < total required demand
```

is the state impossible under the finite fleet bound and safe to prune.

The bound can fail to prune an impossible branch; it cannot validly prune a feasible one. A regression keeps an exact-fit two-freighter route feasible at equality.

## Versioning

The planner version is bumped from v1 to v2 because deterministic traversal order, visited-node counts and the first accepted supplier portfolio may change. This does **not** imply a change to the physical feasible set.

Historical v1 corpus and seed-8 convergence evidence remain historical evidence and are not rewritten.

## Measurement gate

After exact-head CI is green, the same diagnostics must be read again under planner v2:

1. fixed seed 8 convergence marker;
2. whole fixed-corpus coordinated marker;
3. existing planner regression suite.

No target accepted-seed count is asserted in advance.

Interpretation remains causal:

- if seed 8 resolves within the existing 2,000-node corpus budget, the search defect is closed without retuning physics;
- if it remains unresolved, investigate stronger completeness-preserving search structure before increasing an acceptance budget;
- if it becomes proved infeasible, inspect that physical/fleet reason directly.

## Explicit non-goals

This slice does not close the remaining Stage-20E authorities for:

- bootstrap freight ownership/materialization;
- supplier/facility ownership concentration;
- physical whole-route monetary delivered cost;
- initial inventory/buffer depletion exposure;
- shared endpoint/source transfer reservations;
- surface/deep extraction logistics policy.

Those remain subsequent Stage-20E work after coordinated finite-capacity search is stable.
