# Stage 20E — Commodity frontier maximum-cap precheck v1

> Status: **ACCEPTANCE CANDIDATE / NO PHYSICAL AUTHORITY CHANGE**  
> Version: `stage20e.commodity-whole-placement-frontier-resolver.v1`

## Purpose

The verified shared-producer relaxation proved that fixed seed 8 water is physically infeasible even when every remote route is allowed to independently consume its start's full `13`-freighter capacity. The accepted exact frontier generator should therefore not spend 2,000 DFS evidence nodes rediscovering an impossibility already established by a stronger necessary condition.

This slice introduces a small production resolver in front of the accepted frontier-generator v2. It does not alter v2 route-prefix search.

## Resolution sequence

For one commodity:

```text
accepted topology / placement / supply / requirement
+ authoritative per-start maximum freight budget
+ authoritative allocated-route evaluator
→ shared-producer maximum-cap relaxation
```

If the relaxation returns `PROVED_INFEASIBLE`:

```text
FrontierStatus.COMPLETE
options = []
searchNodesVisited = 0
```

This is valid because the relaxed network is a strict superset of all exact plans under that maximum cap vector. If the superset cannot satisfy demand, no smaller cap vector or exact ship-sharing plan can satisfy it.

If the relaxation returns `POSSIBLY_FEASIBLE`, the resolver delegates to `Stage20CommodityWholePlacementFrontierGenerator` v2 unchanged. Search-budget exhaustion therefore remains `UNRESOLVED_SEARCH_BUDGET`; the precheck cannot convert unknown search evidence into infeasibility.

## Authority boundary

The resolver changes none of:

- topology, system geometry or jump edges;
- Stage-18 resource occurrence or producer throughput;
- bootstrap commodity demand;
- route time, payload or cadence;
- explicit-neighbor route semantics;
- the derived `13`-freighter/start physical budget;
- integer route-prefix acceptance;
- cross-commodity finite-fleet combination;
- production world acceptance;
- inventories, ownership, prices or freight materialization.

The resolver only exposes a proof that was previously missing from the frontier search pipeline.

## Fixed-corpus measurement

`Stage20CommodityFrontierResolvedCorpusDiagnostics` replays the same representative seeds and the same 2,000-node per-commodity evidence budget as the accepted v1 corpus diagnostic, but calls the resolver instead of the raw v2 generator.

The diagnostic deliberately reuses the existing corpus-report data contract so the following can be compared directly:

- accepted placements;
- accepted / infeasible / unresolved exact combinations;
- per-commodity frontier status;
- search nodes;
- nondominated ship vectors;
- exact selected combinations.

There is no pass-rate target.

### Measured result

Exact merge-ref Java 17 `clean verify` on the dependency-complete candidate passed with `1462` tests, `0` failures, `0` errors and `1` skipped test, plus coverage, Javadoc and desktop-package gates.

At the unchanged `2,000`-node evidence budget per commodity, fixed seeds `1..16` produced:

```text
fixedSeedCount=16
acceptedPlacementSeedCount=15
combinerAcceptedSeedCount=12
combinerInfeasibleSeedCount=3
combinerUnresolvedSeedCount=0
totalFrontierSearchNodesVisited=20595
maxCommodityFrontierSearchNodesVisited=1040
```

The accepted generator-v2 baseline was:

```text
combinerAcceptedSeedCount=12
combinerInfeasibleSeedCount=2
combinerUnresolvedSeedCount=1
totalFrontierSearchNodesVisited=22595
maxCommodityFrontierSearchNodesVisited=2000
```

Thus the resolver preserves all `12` previously concrete accepted combinations, converts exactly the sole unresolved case into complete physical infeasibility, reduces bounded DFS work by exactly `2,000` nodes, and leaves no unresolved commodity frontier in the accepted-placement fixed corpus.

### Seed 8 causal closure

The only status change is fixed seed `8`:

```text
seed=8 placement=ACCEPTED
status=COMBINER_INFEASIBLE
combiner=INFEASIBLE
failure=COMMODITY_INFEASIBLE

commodity.feedstock.metallic_ore:
  frontier=COMPLETE
  nodes=819
  options=1
  vector={faction.alpha=1, faction.beta=5}

commodity.feedstock.water_ice:
  frontier=COMPLETE
  nodes=0
  options=0
```

This classification is justified by the already-verified maximum-cap shared-producer proof: seed 8 water remains infeasible even in the relaxed superset at maximum authoritative fleet caps. It is therefore physical infeasibility, not search-budget exhaustion.

## Acceptance gate

This slice is accepted only after a fresh pull-request merge-ref Java 17 `clean verify` against the current `main` succeeds after the shared-producer bound itself has been merged.

The measured causal requirements are already satisfied:

1. fixed seeds `1..16` were re-measured at the unchanged 2,000-node evidence budget;
2. the sole status change is explained by the maximum-cap proof, not by modified world authority;
3. all 12 previously accepted concrete combinations remain accepted;
4. unresolved frontier count is now zero;
5. no physical authority was changed.

Once the fresh current-`main` merge-ref gate succeeds, the Stage-20E coordinated frontier-search uncertainty gate is closed sufficiently to proceed to bootstrap freight ownership/materialization.
