# Stage 20E — Commodity freight frontier combiner v1

> Status: **CANDIDATE EXACT COMBINATION LAYER / NO PHYSICAL AUTHORITY CHANGE**  
> Version: `stage20e.commodity-freight-frontier-combiner.v1`

## Why this slice exists

The coordinated whole-placement planner v2 preserves the correct physical feasible set, but fixed seed 8 still exhausts 2,000, 4,000 and 8,000 route-prefix search nodes. The failure is therefore not evidence for changing resources, bootstrap demand, topology, route cadence, or the derived 13-freighter/start service-capacity requirement.

The current essential bootstrap commodities are independent in their producer-capacity authority:

- `commodity.feedstock.water_ice` consumes water producer capacity;
- `commodity.feedstock.metallic_ore` consumes metallic-ore producer capacity.

A `SupplyKey` includes the commodity identifier, so producer capacity reserved for one commodity is not shared with another commodity. The real cross-commodity coupling that remains at a placed start is the finite inter-system freight fleet.

This slice isolates that coupling.

## Input contract

The combiner does **not** generate routes and does **not** decide whether a supplier portfolio is physically valid.

For each required commodity it receives a frontier of already physically valid whole-placement options. Every option states only the remote freighter count used at every placed faction start. The upstream frontier generator remains responsible for proving:

- explicit-neighbor route validity;
- route-time admission;
- payload/throughput curves;
- producer capacity sharing between faction starts for that commodity;
- local-vs-remote freight semantics;
- integer route-prefix allocation;
- deterministic physical supplier commitments.

Each frontier is explicitly classified as either:

- `COMPLETE`; or
- `UNRESOLVED_SEARCH_BUDGET`.

A complete empty frontier proves that the commodity has no physically feasible whole-placement option under its upstream authority. An unresolved empty frontier proves nothing except that no option was found before the upstream search budget ended.

## Exact shared-fleet join

Let each placed start have finite remote-freighter budget `B_s` (currently derived as 13 for each ordinary start by `stage20e.bootstrap-freight-capacity-requirement.v1`).

For one selected option from every commodity frontier, the combination is feasible iff:

```text
for every placed start s:
    sum(selectedCommodityOption.remoteFreighters[s]) <= B_s
```

The combiner solves this exactly with dynamic programming over ship-count vectors.

For two starts with budget 13, the reachable state space is bounded by:

```text
(13 + 1) * (13 + 1) = 196 ship-count vectors
```

rather than by the Cartesian product of all route-prefix decisions inside water and ore simultaneously.

The implementation is generic over the number of commodities and starts; the bounded vector state is determined only by the supplied per-start freight budgets.

## Safe dominance reduction

Inside one commodity frontier, option `A` dominates option `B` only if `A` uses no more remote freighters than `B` at every start and fewer at at least one start.

Because the combiner's only cross-commodity constraint is an upper bound on per-start ships, replacing `B` by `A` can never turn a fitting combination into a non-fitting one.

Equal ship vectors are deterministically represented by the lexicographically smallest stable option ID.

This reduction changes neither physical supply nor the feasible shared-fleet set.

## Status semantics

### `ACCEPTED`

At least one concrete combination of already discovered physical commodity options fits every start's finite fleet.

This status is valid even if an upstream frontier is incomplete: a concrete feasible plan does not require proof that no additional options exist.

### `INFEASIBLE`

The combiner may report infeasibility only from complete evidence:

- a complete commodity frontier is empty; or
- every commodity frontier is complete and no shared-fleet combination fits.

### `UNRESOLVED_FRONTIER`

No currently known combination fits and at least one frontier is incomplete.

This distinction is mandatory. Search incompleteness must never be converted into seed infeasibility.

## Deterministic accepted selection

When several known combinations fit, the combiner chooses deterministically by:

1. minimum total remote freighters across all starts;
2. lexicographically ordered per-start ship vector;
3. stable commodity/option IDs.

This ordering is only a deterministic representative choice among already feasible options. It is not a monetary-cost authority and does not claim global economic optimality.

## Regression boundary

The v1 regressions cover:

- complementary water/ore options that must be joined under one finite fleet per start;
- complete frontiers with no fitting combination -> physical `INFEASIBLE`;
- incomplete frontier with no known fitting combination -> `UNRESOLVED_FRONTIER`;
- incomplete frontier with a concrete fitting option -> `ACCEPTED`;
- complete empty commodity frontier -> `COMMODITY_INFEASIBLE`;
- dominance and equal-vector deterministic tie handling;
- input/frontier/map ordering invariance;
- exact faction-set validation;
- invalid negative ship counts and duplicate commodity frontiers.

## Explicit non-authorities

This slice does not change:

- the representative fixed seed corpus;
- Stage-20B macro geometry;
- jump topology or route physics;
- FTL/local-route cadence;
- Stage-18 resources or producer output;
- bootstrap water/ore demand rates;
- the 13-freighter/start capacity requirement;
- faction-start acceptance thresholds;
- production whole-seed acceptance;
- bootstrap freight ownership/materialization;
- initial inventories or buffer depletion;
- monetary delivered cost;
- supplier/facility ownership concentration.

No ship, resource, route, stock, producer capacity or hidden service is created by this combiner.

## Next causal slice

After exact-head CI for this combiner, the next Stage-20E slice is a bounded **per-commodity whole-placement frontier generator** built on the existing physical route and producer-capacity authorities.

Its required properties are:

1. generate only physically valid whole-placement plans for one commodity;
2. preserve shared producer capacity between faction starts;
3. preserve the finite integer remote-freighter count at each start;
4. expose nondominated per-start ship-count options plus the physical commitments needed for reconstruction;
5. distinguish complete frontier proof from search-budget exhaustion;
6. feed those frontiers into this exact combiner;
7. re-measure fixed seed 8 and the fixed 1..16 corpus without a pass-rate target.

Only after that decomposition is measured should Stage 20E decide whether coordinated search is closed sufficiently to proceed to freight ownership/materialization and the remaining economic authorities.
