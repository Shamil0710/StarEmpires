# Stage 20E — Commodity frontier maximum-cap precheck v1

> Status: **CANDIDATE PRODUCTION INTEGRATION / NO PHYSICAL AUTHORITY CHANGE**  
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

There is no pass-rate target. The expected causal change is measured rather than asserted in advance.

## Acceptance gate

Before this slice can be accepted:

1. exact-head Java 17 `clean verify` must pass;
2. fixed seeds `1..16` must be re-measured at the unchanged 2,000-node evidence budget;
3. any status change must be explained by the maximum-cap proof, not by modified world authority;
4. no previously accepted concrete combination may disappear;
5. any remaining unresolved frontier keeps fail-closed semantics.

If the sole previous unresolved seed is converted into complete physical infeasibility while the 12 previously accepted combinations remain concrete and unchanged in authority, the Stage-20E coordinated frontier-search uncertainty gate is closed sufficiently to proceed to bootstrap freight ownership/materialization.
