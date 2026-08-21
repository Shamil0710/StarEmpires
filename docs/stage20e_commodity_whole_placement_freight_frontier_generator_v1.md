# Stage 20E — Commodity whole-placement freight frontier generator v1

> Status: **CANDIDATE EXACT SWEEP LAYER / PHYSICAL SOLVER ADAPTER NOT YET WIRED**  
> Version: `stage20e.commodity-whole-placement-freight-frontier-generator.v1`

## Why this slice exists

The coordinated whole-placement freight planner v2 is physically correct but fixed seed 8 still exhausts 2,000 / 4,000 / 8,000 coupled water+ore route-prefix nodes. PR #283 separated the cross-commodity coupling into an exact finite-fleet combiner. The missing layer is now a deterministic way to construct the nondominated physical whole-placement frontier for one commodity.

This generator provides that sweep without creating a second route-physics authority.

It deliberately depends on a `BudgetVectorPlanner` adapter. That adapter must be backed by the authoritative physical coordinated planner and must enforce an exact positive remote-freighter upper bound independently at each placed start.

The current public coordinated planner still exposes one uniform per-start budget, so production integration is explicitly deferred to the next narrow seam. This PR does not fake variable budgets through route caps or post-filtering.

## Completeness theorem

For one commodity, let a physically feasible whole-placement plan use ship vector:

```text
v = (v_1, v_2, ... v_n)
```

where each coordinate is the number of remote freighters used at one placed faction start.

The generator evaluates every positive upper-bound vector `b` with:

```text
1 <= b_s <= maximumRemoteFreightersPerStart
```

using an exact physical solver.

Only one deterministic accepted physical plan is required for each vector.

Why this is sufficient to recover every nondominated feasible ship vector:

1. take any nondominated feasible vector `v`;
2. the sweep evaluates the budget vector `b = v`;
3. any accepted physical result under those bounds must use some `u <= v` coordinate-wise;
4. if `u < v` in any coordinate, `u` dominates `v`;
5. that contradicts the assumption that `v` was nondominated;
6. therefore the accepted result at `b = v` must use exactly `v`.

So exhaustive upper-bound-vector evaluation plus Pareto pruning recovers the complete nondominated **ship-usage frontier**, provided every vector solve itself resolves.

This argument does not require enumerating every supplier portfolio under a vector.

## Positive budget vectors and zero physical usage

The sweep starts every budget coordinate at 1 rather than inventing a new zero-budget planner semantic.

A local-only physical solution using zero remote freighters is still discoverable under the `(1, 1, ...)` budget vector. The retained `CommodityOption` records the **actual** physical usage, so a zero-usage frontier point remains `(0, 0, ...)`.

## Physical adapter contract

`BudgetVectorPlanner` receives:

- one `CommodityRequirement`;
- a canonical positive `remoteFreighterBudgetByFaction` map;
- a bounded `searchNodeBudget` for that vector.

It returns one `PhysicalEvaluation` classified as:

- `ACCEPTED`;
- `INFEASIBLE`;
- `UNRESOLVED_SEARCH_BUDGET`.

Accepted evidence must contain the detailed physical `StartPlan` and `ProducerUsage` records produced from the authoritative planner semantics.

The generator verifies that accepted evidence:

- covers each canonical faction start once;
- exposes the exact evaluated per-start budget;
- contains exactly one demand per start;
- contains only the requested commodity;
- exposes actual ship usage no greater than the evaluated upper bound;
- contains no producer usage for another commodity.

The next integration seam will additionally bind this adapter directly to the accepted placement systems through the authoritative coordinated planner rather than a synthetic evaluator.

## Sweep state

For `N` placed starts and maximum budget `B`, the v1 sweep evaluates:

```text
B^N
```

positive budget vectors.

For the current representative case:

```text
N = 2
B = 13
13^2 = 169 physical vector solves per commodity
```

This is intentionally bounded and explicit. It replaces one coupled combinatorial water+ore search with independent bounded one-commodity solves plus the PR #283 exact cross-commodity DP.

No pass-rate target is involved.

## Frontier completeness semantics

The generated combinable frontier is `COMPLETE` only when **every** budget-vector physical solve resolved as either accepted or proved infeasible.

If any vector returns `UNRESOLVED_SEARCH_BUDGET`:

- the frontier is `UNRESOLVED_SEARCH_BUDGET`;
- all concrete accepted nondominated options discovered elsewhere in the sweep are retained;
- downstream PR #283 may accept if those known concrete options already form a fitting water+ore combination;
- absence of a known combination cannot become physical infeasibility while a relevant frontier remains incomplete.

This preserves the Stage-20E fail-closed boundary.

## Detailed reconstruction evidence

For each retained nondominated ship vector, the generator keeps a `FrontierPlan` containing:

- the combinable `CommodityOption`;
- the exact evaluated budget vector;
- search nodes visited for the solve that produced it;
- detailed `StartPlan` records;
- detailed `ProducerUsage` records.

Therefore choosing an option in the cross-commodity combiner does not lose the physical supplier commitments needed for later reconstruction and acceptance evidence.

## Determinism

- placement factions are canonicalized and sorted;
- budget vectors are enumerated lexicographically;
- actual usage vectors are canonicalized;
- the first physical plan for an identical actual usage vector is retained;
- retained vectors are Pareto-pruned only by coordinate-wise ship use;
- final options are sorted by stable option ID.

Input assignment order cannot change the frontier.

## Regression boundary

The v1 tests cover:

1. recovery of three mutually nondominated ship vectors from a full budget sweep;
2. retention of concrete options while one vector is unresolved;
3. Pareto removal of dominated physical usage vectors;
4. local-only `(0,0)` actual usage discovered under positive `(1,1)` budgets;
5. rejection when physical start plans do not expose the exact evaluated budgets;
6. rejection when producer evidence leaks another commodity;
7. rejection of non-accepted placement or invalid sweep budgets.

## Explicit non-authorities

This slice does not change or provide:

- jump topology;
- local/FTL route physics;
- producer capacity;
- Stage-18 resource generation;
- bootstrap water/ore demand;
- the derived 13-freighter/start capacity requirement;
- freight hull materialization or ownership;
- faction-start acceptance;
- production whole-seed acceptance;
- initial stock/buffers;
- monetary delivered cost;
- supplier/facility ownership concentration.

It also does **not** claim that the current uniform-budget public planner already satisfies the `BudgetVectorPlanner` contract.

## Next causal slice

The next narrow Stage-20E step is to expose exact per-start budget vectors through the existing `Stage20CoordinatedWholePlacementFreightPlanner` without changing its current uniform public behavior or physical feasible set.

Required integration properties:

1. existing `plan(..., int remoteFreighterBudgetPerStart, ...)` remains behaviorally unchanged;
2. internal route curves use the budget of their actual destination start;
3. single-start prechecks use that start's exact budget;
4. DFS fleet exhaustion and optimistic upper-bound pruning use per-start budgets;
5. accepted `StartPlan.remoteFreighterBudget` exposes the exact vector coordinate;
6. a new adapter maps that physical result into this generator's `PhysicalEvaluation`;
7. regression proves uniform-vector parity with the existing public planner;
8. only then re-run seed 8 and the fixed 1..16 corpus through water/ore frontier generation plus the PR #283 exact combiner.

The measured result, not a desired pass count, determines the following Stage-20E work.
