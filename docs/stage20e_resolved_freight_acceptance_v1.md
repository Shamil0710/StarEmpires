# Stage 20E — Resolved coordinated freight acceptance v1

> Status: **VERIFIED PRODUCTION ECONOMIC ACCEPTANCE PRIMITIVE / NO WORLD REPAIR**  
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

Exact Java 17 merge-ref verification run `32492850834` completed successfully with:

```text
Tests run: 1469
Failures: 0
Errors: 0
Skipped: 1
coverage: PASS
Javadocs: PASS
package: PASS
```

The production-path fixed corpus measured:

```text
fixedSeedCount=16
acceptedPlacementSeedCount=15
freightAcceptedSeedCount=12
freightInfeasibleSeedCount=3
freightUnresolvedSeedCount=0
totalSearchNodesVisited=20595
perStartFreighterCapacity=13
```

The three complete physical infeasibilities are seeds `4`, `6` and `8`, each reported as exact combiner `INFEASIBLE` with `COMMODITY_INFEASIBLE`. Seed `10` never enters freight acceptance because its faction-start placement is rejected. All other accepted placements produce a concrete finite-fleet combination.

This exactly reproduces the already accepted resolver/frontier corpus (`12 accepted / 3 physically infeasible / 0 unresolved`, `20,595` total search nodes). The production primitive therefore preserves the accepted physical feasible set rather than introducing a new acceptance threshold.

The physical route evaluator used by this measurement is created through `stage20e.physical-freight-route-evaluator-factory.v1` at the independently derived `13`-freighter capacity. Payload, FTL plans, local access and transfer rates are unchanged.

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

The production primitive is now measured and physically equivalent to the accepted frontier closure. The next Stage-20E slice is to update the whole-seed acceptance boundary so that:

```text
placement accepted
→ resolved coordinated freight required
→ exact ACCEPTED permits the economic freight gate
→ exact INFEASIBLE rejects the seed
→ UNRESOLVED_FRONTIER blocks acceptance as unresolved authority
```

Placement-rejected seeds do not need a synthetic freight result because no ordinary faction-start set exists to service.
