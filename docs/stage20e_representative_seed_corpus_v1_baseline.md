# Stage 20E — Representative Seed Corpus v1 Measured Baseline

> Status: **MEASURED REJECTION BASELINE — NOT AN ACCEPTED TARGET**  
> Source corpus: `stage20e.representative-seed-corpus.v1`  
> Source profile: `stage20e.representative-production-probe-profile.v1`  
> Exact benchmark: `docs/benchmarks/stage20e-representative-seed-corpus-v1.json`

## Repository-exact measurement

The fixed v1 corpus was evaluated by repository CI after the seed set and representative profile were already fixed. The measured result is:

```text
requested seeds:              16
accepted seeds:                0
rejected seeds:               16
unresolved-authority seeds:    0

ECONOMIC_THROUGHPUT_REJECTED: 32
FACTION_START_PLACEMENT_REJECTED: 16
```

Every one of the sixteen Stage-20D topology candidates was **ACCEPTED**. Topology is therefore not the measured blocker in this corpus.

Every seed then failed both essential throughput checks evaluated for the observed start system:

- `commodity.feedstock.metallic_ore`, required `25 kg/s`;
- `commodity.feedstock.water_ice`, required `50 kg/s`.

Failures alternate between:

- `NO_FEASIBLE_ROUTE` within the calibrated supplier-route time boundary;
- `INSUFFICIENT_THROUGHPUT` where a physically reachable supplier exists but the best currently accepted delivered rate is below the required service level.

Every seed also ends with `FACTION_START_PLACEMENT_REJECTED / INSUFFICIENT_ACCEPTED_CANDIDATES`.

## Interpretation boundary

`0/16` is not a new desired acceptance percentage and is not permission to lower the Stage-20E requirement profile until seeds pass.

It is evidence that the current integration of:

```text
resource occurrence + extraction capacity
+ physical freight fleet
+ route-time horizon
+ supplier selection/aggregation
+ faction-start candidate evaluation
```

cannot satisfy the already derived Stage-18 service-level demand in the representative generated worlds.

The correct next action is causal diagnosis, not rescue.

## Explicitly forbidden responses

Do not respond to this baseline by:

- replacing failed seeds with hand-picked successful roots;
- lowering `50 kg/s` or `25 kg/s` only because the corpus fails;
- giving individual failed seeds more ships, deposits, stations or stock;
- opening extra jump edges after economic failure;
- extending route horizons without physical/cadence justification;
- adding hidden fallback supply;
- treating a test pass as evidence that a rejected generated world is economically acceptable.

## Causal questions for the next slice

The next Stage-20E remediation must answer, in this order:

1. Does `Stage20EconomicThroughputAcceptance` incorrectly require one supplier to satisfy the entire service level where the canonical model permits a portfolio of physical suppliers?
2. Does the theoretical supply report aggregate compatible extraction sources correctly at the system level and across physically independent suppliers?
3. Is the current route-time horizon being compared to the intended supplier-delivery metric, or to a longer physical round-trip/loading cycle that the calibration did not intend?
4. Is the representative eight-freighter allocation physically incapable of supporting the service level even with adequate sources, and if so what upstream evidence should determine the fleet allocation rather than choosing a rescue count?
5. Why do the normalized economic failures in the fixed corpus all reference `StarSystemId[value=1]`; does the production probe evaluate the final bounded faction placements, or an earlier fixed candidate/start set?
6. Which faction-start hard gates reject candidates independently of the final economic acceptance report?

Any remediation must preserve the frozen v1 JSON. Improved behavior requires an explicitly versioned new probe/profile/corpus evidence snapshot rather than rewriting this baseline.
