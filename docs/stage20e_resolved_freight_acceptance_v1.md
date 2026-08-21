# Stage 20E — Resolved coordinated freight acceptance v1

> Status: **CANDIDATE PRODUCTION ECONOMIC ACCEPTANCE / NO WORLD REPAIR**  
> Version: `stage20e.resolved-freight-acceptance.v1`

## Why this slice exists

The historical `Stage20EconomicThroughputAcceptance` is a single-supplier quantitative gate. Its own contract states that it does not model simultaneous multi-commodity finite-fleet allocation. That gate remains useful as historical/baseline evidence, but it cannot be the final Stage-20E whole-start freight authority after the accepted portfolio/frontier work.

The accepted Stage-20E chain now provides all required pieces:

```text
accepted faction-start placement
+ authoritative physical supply
+ finite per-start freight capacity
+ integer allocated physical route evaluator
→ per-commodity whole-placement frontier resolver
→ exact cross-commodity finite-fleet combiner
```

`Stage20ResolvedFreightAcceptance` packages that chain into one production acceptance primitive.

## Status semantics

The final status is the exact combiner status:

### `ACCEPTED`

A concrete set of already physical per-commodity options fits every start's finite freight capacity.

The rich upstream `FrontierReport`s are retained, so later physical-plan reconstruction can recover producers, routes, throughput commitments and producer reservations.

### `INFEASIBLE`

Only complete frontier evidence may produce physical infeasibility. This includes:

- a complete empty commodity frontier;
- complete commodity frontiers whose options cannot fit the shared finite fleet.

### `UNRESOLVED_FRONTIER`

At least one frontier is incomplete and no known concrete combination fits. Search-budget exhaustion remains uncertainty rather than world rejection.

## Inputs are explicit authorities

The primitive does not derive or guess:

- topology;
- start placement;
- producer supply;
- essential commodity demand;
- per-start freight capacity;
- route physics;
- search evidence budget.

All are caller inputs. The primitive verifies that finite freight budgets cover exactly the placed faction set and that each selected start exists in the supplied topology.

## Fixed-corpus production-path measurement

`Stage20ResolvedFreightAcceptanceCorpusDiagnostics` replays the unchanged representative v2-candidate generated world, obtains the independently derived freight-capacity authority, creates a physical evaluator through `Stage20PhysicalFreightRouteEvaluatorFactory`, and calls the production acceptance primitive directly.

The evidence-only exact search budget remains `2,000` nodes per commodity. No accepted-seed rate target is applied.

The diagnostic prints:

- fixed/accepted-placement seed counts;
- accepted / infeasible / unresolved coordinated freight counts;
- exact failure reason per seed;
- bounded search nodes per seed and in aggregate.

The expected purpose of the measurement is causal parity with the already measured frontier-resolver corpus, not a new target. Any difference must be investigated rather than tuned away.

## Explicit non-authorities

This slice does not:

- alter Stage-18 resources or producer throughput;
- add topology edges;
- change bootstrap demand;
- change FTL/local route timing or payload;
- create ships, ownership, inventory or money;
- grant deliveries;
- choose prices or buffer stock;
- mutate the historical single-supplier baseline;
- change whole-seed composition yet.

## Next causal slice

Once fixed-corpus evidence confirms this primitive reproduces the accepted `12 accepted / 3 physically infeasible / 0 unresolved` frontier closure without changing authorities, update the whole-seed acceptance boundary so that:

```text
placement accepted
→ resolved coordinated freight required
→ exact ACCEPTED permits the economic freight gate
→ exact INFEASIBLE rejects the seed
→ UNRESOLVED_FRONTIER blocks acceptance as unresolved authority
```

Placement-rejected seeds do not need a synthetic freight result because no ordinary faction-start set exists to service.
