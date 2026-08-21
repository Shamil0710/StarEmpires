# Stage 20E — Bootstrap Supplier Service Cadence v2 Candidate

> Status: **MEASUREMENT CANDIDATE — NOT CURRENT AUTHORITY**
>
> Purpose: correct one semantic defect discovered by fixed-corpus Stage-20E diagnostics without rewriting the frozen v1 rejection baseline or weakening physical/economic gates to obtain a desired pass rate.

## 1. Measured cause

The fixed representative corpus showed that the current v1 ordinary-start bootstrap profile rejects essentially every candidate on supplier route time even when global physical supply exists and freight-cycle throughput is not the limiting layer.

Read-only causal diagnostics established:

- global resolved water supply is sufficient in all 16 representative seeds;
- global resolved metallic-ore supply is sufficient in 15 of 16 seeds;
- physical routes exist;
- the dominant candidate/commodity failure is `ROUTE_TIME_BOTTLENECK`;
- the measured freight-throughput bottleneck count is zero in that diagnostic layer.

This does **not** authorize extra resources, extra freighters, extra topology edges, lower sustained demand, seed replacement or a target pass fraction.

## 2. Semantic defect in v1

`stage20e.bootstrap-requirements.v1` derives correct reference process demand rates from Stage-18 physical facility/process constraints:

- `commodity.feedstock.water_ice` = 50 kg/s;
- `commodity.feedstock.metallic_ore` = 25 kg/s.

However, v1 also computes:

```text
reference station storage capacity / sustained shared storage-class demand
```

and uses the resulting duration as `maxSupplierRouteTimeS`.

That duration is physically useful, but it is **buffer coverage / depletion exposure**, not automatically a route-feasibility limit.

The canonical Stage-20 contracts instead require the causal separation:

```text
physical route time
→ ship throughput
→ inventory buffer need
→ shortage / price / resilience consequence
```

Stage 20J also lists `round-trip travel time` and `buffer depletion time` as separate calibration measurements.

## 3. Candidate correction

The v2 candidate preserves the exact v1 demand/process authority and derives supplier service time from already accepted physical cadence:

```text
one source local access leg
+ origin jump-access leg
+ regional five-hop ordinary FTL arrival
+ destination jump-access leg
+ one physical load operation
+ one physical unload operation
→ maximum ordinary-start supplier delivery-time envelope
```

The components are not new magic constants:

- local access comes from `Stage20LocalRouteSemanticCalibrationProfile`;
- five-hop regional timing comes from `Stage20IntersystemCadenceCalibrationProfile` for `EARLY_CIVILIAN_FREIGHTER`;
- payload comes from the representative propulsion/freight definition;
- handling comes from payload divided by the Stage-18 trade/logistics hub transfer rate.

The formula intentionally matches the endpoint decomposition already used by `Stage20GeneratedWorldProductionProbe.physicalRoutes(...)` and the production `Stage20PhysicalFreightRouteEvaluator`.

## 4. What remains unchanged

Candidate v2 does **not** change:

- root seeds `1..16`;
- macro galaxy geometry request;
- topology-quality profile;
- jump-edge generation or repair;
- resource occurrence generation;
- extraction rates or finite reserves;
- initial infrastructure mix;
- faction identities;
- faction-start acceptance thresholds;
- FTL physics;
- representative freight class;
- active freighter count (`8`);
- sustained bootstrap demand (`50 / 25 kg/s`);
- frozen v1 benchmark.

The previous storage-derived value is retained explicitly as `referenceBufferCoverageSecondsByCommodity` evidence instead of silently discarded.

## 5. Evidence / promotion rule

The same fixed corpus `1..16` is replayed with only the corrected input authority. No minimum pass fraction is authored in advance.

Before any promotion to current authority:

1. exact-head repository `clean verify` must pass;
2. measured candidate/placement/whole-seed results must be inspected;
3. remaining rejection causes must be treated causally rather than tuned away;
4. frozen v1 evidence must remain reproducible;
5. promotion, if justified, must be explicit and versioned.

If v2 still rejects all or most seeds, that result is evidence for the next causal Stage-20E slice, not permission to add hidden rescue state.

## 6. Remaining authority boundaries

This correction does not resolve later Stage-20E gaps such as:

- whole-route monetary delivered cost authority;
- actual initial inventory/buffer stock and depletion exposure;
- initial source/facility ownership and ownership concentration;
- shared-fleet portfolio allocation semantics for simultaneous multi-supplier delivery.

Those remain separate roadmap work and must not be fabricated inside this candidate correction.
