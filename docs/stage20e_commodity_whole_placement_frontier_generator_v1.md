# Stage 20E — Per-commodity whole-placement freight frontier generator v1

> Status: **CANDIDATE FRONTIER GENERATION LAYER / NO PHYSICAL AUTHORITY CHANGE**  
> Version: `stage20e.commodity-whole-placement-frontier-generator.v1`

## Why this slice exists

The coordinated whole-placement freight planner v2 preserves the correct physical feasible set, but fixed seed 8 remained `UNRESOLVED_SEARCH_BUDGET` through 2,000, 4,000 and 8,000 route-prefix search nodes.

The preceding `stage20e.commodity-freight-frontier-combiner.v1` slice proved that cross-commodity coupling can be reduced to a small exact shared-fleet join once each required commodity exposes its own physically valid whole-placement option frontier.

This slice supplies that missing upstream layer.

It does **not** change physical world calibration. It changes only the search decomposition used to prove or discover physically feasible bootstrap freight plans.

## Authority boundary

The generator consumes existing authorities:

- accepted Stage-20 faction-start placement;
- explicit-neighbor `GalaxyTopology`;
- Stage-18-derived `SupplyThroughputReport`;
- one existing `CommodityRequirement`;
- the already-authoritative allocated-route evaluator;
- finite per-start inter-system freight budgets.

It preserves:

- route endpoint and explicit-neighbor validation;
- route-time admission;
- integer remote-freighter allocations;
- monotone/non-increasing route throughput marginals;
- local service requiring zero inter-system freighters;
- shared producer capacity across every placed start for the commodity;
- deterministic max-flow allocation;
- fail-closed search-budget semantics.

It creates no ship, resource, producer output, route, stock or hidden service.

## Why one commodity can be solved independently

`SupplyKey` contains both commodity and producer-system identity.

Therefore producer throughput for:

- `commodity.feedstock.water_ice`; and
- `commodity.feedstock.metallic_ore`

is not shared across commodities.

Inside one commodity, producer capacity **is** shared across all faction starts and must remain coordinated.

Across commodities, the remaining coupling is the finite freight fleet at each start. That coupling is handled exactly by `stage20e.commodity-freight-frontier-combiner.v1`.

The decomposition is therefore:

```text
physical water whole-placement frontier
physical ore whole-placement frontier
        ↓
exact shared-fleet combiner
        ↓
one cross-commodity bootstrap freight plan
```

## Frontier definition

For one commodity, a frontier option contains:

- exact remote freighters used by every placed faction start;
- complete reconstructed per-start demand plans;
- concrete physical supplier commitments;
- local/remote classification;
- exact allocated remote freighters per commitment;
- delivered kg/s;
- authoritative route evidence for every remote commitment;
- shared producer-capacity usage.

Only nondominated ship vectors are returned.

Option `A` dominates option `B` iff `A` uses no more remote freighters at every start and fewer at at least one start.

A dominated option can never enable a cross-commodity shared-fleet combination that its dominator cannot also enable.

## Completeness construction

Let each start `s` have physical maximum freight budget `B_s`.

The generator enumerates every cap vector:

```text
0..B_1 × 0..B_2 × ... × 0..B_n
```

ordered deterministically by:

1. minimum total cap ships;
2. lexicographic count vector in canonical stable-faction order.

For the current ordinary two-start, 13-freighter case this is at most:

```text
(13 + 1) * (13 + 1) = 196 cap vectors
```

For each cap vector, the generator runs a one-commodity coordinated physical search.

### Completeness lemma

Assume `U` is a nondominated physically feasible usage vector.

When the solver is run with cap vector exactly equal to `U`, any accepted reconstructed solution `V` must satisfy:

```text
V_s <= U_s for every start s
```

If `V != U`, then `V` would dominate `U`, contradicting the assumption that `U` is nondominated.

Therefore a complete solve of every cap vector must rediscover every nondominated physically feasible usage vector.

This is the reason the generator may stop each individual cap-vector solve after its first accepted plan while still proving the final nondominated frontier complete.

## Per-cap physical search

Each cap-vector solve uses the same physical structure as the coordinated planner:

```text
source
  ↓ authoritative producer capacity
producer nodes
  ↓ local arcs or allocated physical route capacity
start-demand nodes
  ↓ required bootstrap throughput
sink
```

Remote route capacities are built from explicit integer prefix curves.

The search adds only the next freighter on remote route arcs crossing the current residual minimum cut.

Traversal remains deterministic and uses:

1. most-constrained unsatisfied demand first;
2. larger current demand deficit;
3. larger next physical route marginal;
4. stable start/supplier/consumer ties.

An optimistic remaining-fleet upper bound may prune only when even an intentionally over-generous descendant capacity cannot reach total demand.

The bound may fail to prune an impossible branch. It must not prune a feasible one.

## Shared search budget

`searchNodeBudget` is shared across all cap-vector solves.

The generator reports:

### `COMPLETE`

Every required cap vector was physically resolved, or a zero-ship option was found.

A zero-ship whole-placement option dominates every possible positive-ship option, so no further cap-vector search is required to prove the nondominated frontier complete.

A complete empty frontier is physical proof that no whole-placement option exists for that commodity under the supplied authorities and fleet limits.

### `UNRESOLVED_SEARCH_BUDGET`

The shared search budget ended before all required cap vectors were resolved.

Already discovered physical options are retained.

This status must never be interpreted as physical infeasibility.

## Fast exact proofs before route-prefix search

The generator preserves two safe prechecks already used by the coordinated planner:

1. **aggregate producer scarcity** — if total authoritative producer capacity for the commodity is below total whole-placement demand, the frontier is immediately complete and empty;
2. **single-start infeasibility** — if any placed start cannot satisfy the commodity independently within its own physical fleet budget, the whole-placement frontier is immediately complete and empty.

Neither precheck invents new rejection authority. Both are necessary conditions for any whole-placement solution.

## Determinism

The generator canonicalizes:

- stable faction IDs;
- assignment order;
- budget-map order;
- producer order;
- route order;
- cap-vector order.

Equal usage vectors retain the first deterministic physical reconstruction reached under that canonical traversal.

Option IDs are derived only from the canonical faction/ship vector.

Input list or map iteration order therefore cannot change the exposed frontier.

## Combiner projection

`FrontierReport.toCombinerFrontier()` projects rich physical options to:

```text
commodityId
frontierVersion
COMPLETE | UNRESOLVED_SEARCH_BUDGET
optionId
remoteFreightersByFaction
```

The exact combiner does not need to repeat route or producer-capacity search.

The rich physical commitments remain attached to the generator report for later reconstruction/materialization.

## Regression boundary

The v1 regressions cover:

- a real shared-producer conflict with two complementary nondominated vectors:
  - `(alpha=1, beta=2)`;
  - `(alpha=2, beta=1)`;
- projection into the exact cross-commodity combiner;
- shared-fleet selection of the compatible asymmetric commodity option;
- explicit `UNRESOLVED_SEARCH_BUDGET` under a one-node bound;
- aggregate producer scarcity -> complete empty frontier with zero search nodes;
- all-local service -> complete zero-ship frontier;
- stable assignment/map ordering invariance;
- exact placed-faction budget-set validation;
- rejection of a route containing a non-neighbor shortcut.

## Explicit non-authorities

This slice does **not** change:

- representative seed corpus;
- Stage-20B macro geometry;
- generated system positions;
- jump topology;
- FTL/local route cadence;
- Stage-18 resources;
- producer output;
- water/ore bootstrap demand;
- the derived 13-freighter/start service-capacity requirement;
- faction-start placement thresholds;
- production whole-seed acceptance;
- freight ship ownership or materialization;
- initial inventory/buffer depletion;
- monetary delivered cost;
- supplier/facility ownership concentration;
- extraction logistics policy.

## Measurement gate after CI

After exact-head CI is green, the next causal action is **measurement**, not tuning.

Run the generator for each essential bootstrap commodity and feed the resulting frontiers into the exact combiner for:

1. fixed seed 8;
2. fixed representative seeds `1..16`.

Record for each seed/commodity:

- frontier status;
- frontier option count;
- search nodes visited;
- nondominated ship vectors;
- exact combiner status;
- selected shared-fleet vector when accepted.

No accepted-seed target is asserted in advance.

Interpretation remains causal:

- if seed 8 becomes accepted within the existing evidence budget, the prior cross-commodity search explosion is closed without changing physics;
- if a commodity frontier becomes complete empty, inspect the proved physical cause;
- if a frontier remains unresolved, optimize the one-commodity frontier search before increasing acceptance budgets or retuning resources;
- if the combiner proves no shared-fleet combination exists from complete non-empty frontiers, inspect the finite fleet authority directly.

## Next Stage-20E work after decomposition is measured

Only after the frontier-generator + exact-combiner path is measured should Stage 20E proceed to the remaining economic authorities:

- bootstrap freight ownership/materialization;
- supplier/facility ownership concentration;
- physical whole-route monetary delivered cost;
- initial inventory/buffer depletion exposure;
- shared endpoint/source transfer reservations;
- surface/deep extraction logistics policy.

Those remain separate causal slices and are not silently folded into frontier search.
