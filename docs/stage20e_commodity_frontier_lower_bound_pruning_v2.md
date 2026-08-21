# Stage 20E — Commodity frontier lower-bound pruning v2

Status: **CANDIDATE SEARCH OPTIMIZATION / NO PHYSICAL AUTHORITY CHANGE**

Implementation version: `stage20e.commodity-whole-placement-frontier-generator.v2`

## Why this slice exists

The accepted v1 per-commodity frontier decomposition preserves the correct physical feasible set, but the first fixed-corpus measurement at an evidence-only budget of 2,000 search nodes per commodity exposed a search-order bottleneck: high-demand commodity frontiers spend most or all of the bounded evidence budget proving low per-start fleet caps that are already impossible when each start is considered independently.

That bounded-search exhaustion is **not** evidence to change resources, topology, bootstrap demand, route cadence, payload, producer output or the derived 13-freighter-per-start service-capacity requirement.

## Accepted v1 fixed-corpus baseline

The accepted `stage20e.commodity-frontier-corpus-diagnostics.v1` evidence on fixed seeds `1..16`, using 2,000 search nodes per commodity, records:

- fixed seeds: `16`;
- accepted faction-start placements: `15`;
- exact combiner accepted: `0`;
- complete infeasible: `2`;
- unresolved frontier: `13`;
- total frontier search nodes visited: `50,952`;
- maximum one-commodity frontier search nodes visited: `2,000`;
- fixed seed `8`: unresolved.

This is the comparison baseline for v2. The 2,000-node value is an evidence budget, not a production acceptance threshold or a world-quality target.

## Safe lower bound

Before whole-placement frontier search, v1 already runs the existing single-start physical freight allocator for every placed start and the commodity. In v2, the accepted single-start result is also used as a lower bound on the cap-vector coordinate for that start.

If start `i` requires at least `m_i` remote freighters when it can use all authoritative producer capacity without competing with other starts, then a whole-placement solution with shared producer competition cannot satisfy that same start with fewer than `m_i` remote freighters.

Therefore cap vectors with coordinate `< m_i` are physically impossible and may be omitted without removing a feasible whole-placement option.

The authoritative maximum remains unchanged. For each start the explored coordinate range becomes:

`singleStartMinimum_i .. authoritativeFleetBudget_i`

rather than:

`0 .. authoritativeFleetBudget_i`.

## Preserved semantics

The optimization does **not** pre-allocate those minimum ships and does not synthesize a solution. Every cap-vector solve still starts from zero route-prefix allocations and reconstructs a concrete physical solution through the existing max-flow/search machinery.

Unchanged authorities and invariants:

- explicit-neighbor topology and route validation;
- route-time admission;
- integer route-prefix freighter allocation;
- authoritative producer capacities;
- producer competition between faction starts for the commodity;
- local-service semantics;
- finite per-start fleet maxima;
- physical supplier commitments and producer-usage reconstruction;
- nondominated frontier semantics;
- `COMPLETE` versus `UNRESOLVED_SEARCH_BUDGET` fail-closed distinction;
- exact cross-commodity frontier combiner semantics.

## Regression proof

The v2 regression includes a two-start commodity where each start independently requires exactly two remote freighters and the authoritative maximum is also two. With a five-node search budget, the only admissible cap vector `(2,2)` closes as a complete physical frontier.

The pre-v2 enumeration would spend bounded evidence on lower cap vectors such as `(0,0)`, `(0,1)`, `(1,0)` and could exhaust the same small budget before reaching the only physically admissible vector.

## Required measurement gate

After exact-head CI is green, re-run the unchanged fixed evidence protocol:

1. fixed seed `8`;
2. fixed representative corpus seeds `1..16`;
3. 2,000 search nodes **per commodity** as the comparison budget;
4. unchanged v2-candidate production probe;
5. unchanged physical route/supply/bootstrap/fleet authorities;
6. exact frontier combiner after the commodity frontiers are generated.

Record at minimum:

- frontier status per commodity;
- search nodes visited;
- known nondominated ship vectors;
- exact combiner status;
- selected combined fleet vector when concrete acceptance is found;
- comparison against the v1 fixed-corpus evidence.

The 2,000-node value remains an evidence/comparison budget, **not** a world-quality or acceptance threshold.

## Decision rule after measurement

- If seed 8 and the corpus are sufficiently resolved to establish stable frontier behavior, proceed to the next Stage-20E causal slice such as freight ownership/materialization and remaining economic authorities.
- If material unresolved cases remain, continue search decomposition/optimization using completeness-preserving bounds.
- Do not tune physical world generation merely to satisfy a bounded search budget.
