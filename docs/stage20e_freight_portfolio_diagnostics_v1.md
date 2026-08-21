# Stage 20E — Freight Portfolio Diagnostics v1

> Status: **READ-ONLY CAUSAL EVIDENCE — NOT ACCEPTANCE AUTHORITY**

## Purpose

After the bootstrap service-cadence v2 candidate separated inventory-buffer coverage from physical supplier service time, the fixed corpus produced accepted faction-start placement in 15 of 16 seeds but whole-seed acceptance remained 0 of 16.

The remaining final gate `Stage20EconomicThroughputAcceptance` requires one producer route to satisfy the full commodity service rate. Faction-start dependency diagnostics, by contrast, already reason about multiple physical suppliers. This diagnostic measures whether the mismatch matters after explicitly accounting for the finite representative freight allocation.

## No fleet multiplication

The current representative profile contains eight `EARLY_CIVILIAN_FREIGHTER` units as provisional Stage-22-reviewable evidence. A multi-supplier diagnostic must not evaluate every supplier as though all eight ships were simultaneously allocated to every route.

`Stage20PhysicalFreightRouteEvaluator.assessWithAllocatedFreighters(...)` therefore exposes the existing physical cycle calculation for a bounded subset of the configured fleet:

```text
1 <= allocated freighters <= configured active freighters
```

It does not create ships and does not change route geometry, endpoint handling, FTL timing, payload or the existing `assess(...)` result.

## Measured bounds

For every placed start and essential commodity, the diagnostic:

1. admits only physical routes inside the v2 supplier-service time envelope;
2. preserves each supplier's generated production capacity;
3. measures marginal delivery from the first through eighth allocated freighter on each route using the production evaluator;
4. selects the deterministic highest physical marginal contributions until the service rate is met or all eight ships are consumed;
5. reports whether the current single-supplier gate already succeeds or whether a multi-supplier portfolio is required.

Two distinct fleet interpretations are intentionally reported rather than silently choosing policy:

- **per-start shared fleet:** the same eight-ship budget is shared by all essential commodities of one faction start;
- **whole-placement shared fleet:** one eight-ship budget is shared by every assigned faction start in that generated seed.

These are diagnostic lower/upper policy boundaries. Neither interpretation is promoted to production authority by this slice.

## Explicit non-goals

This work does not:

- alter the frozen v1 benchmark;
- promote the bootstrap-service v2 candidate;
- change essential demand rates;
- add resources, routes, stations or freighters;
- lower start-quality thresholds;
- change final whole-seed acceptance;
- define ownership of the eight representative freighters;
- resolve simultaneous station/source transfer reservations between independent routes;
- resolve actual inventories, monetary delivered cost or ownership concentration.

Any later acceptance change must use the measured result to define an explicit, non-double-counting allocation authority first.
