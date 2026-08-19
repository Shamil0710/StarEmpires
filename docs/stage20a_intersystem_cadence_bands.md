# Stage 20A Closure — Inter-system Cadence Bands

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A FTL / route cadence calibration  
**Date:** 2026-08-19

## Purpose

Close `INTERSYSTEM_CADENCE_CALIBRATION_BANDS` using only the accepted Stage-20 FTL translated-mass, energy, spool, reference edge-transit and cooldown evidence.

No new FTL seconds are authored in this slice.

## Authority boundary

Current ordinary FTL remains:

```text
NEIGHBOR_EDGE_ONLY
```

The current accepted reference provides, per compatible representative:

```text
mass-sensitive spool time
reference edge transit = 30 s
cooldown = 90 s
```

This slice derives hop-count cadence probes. It does **not** define the future generated edge-transit distribution; `FTL_EDGE_TRANSIT_DISTRIBUTION` remains owned by later Stage 20.

Heavy representatives outside the reference-drive translated-mass domain remain explicitly excluded rather than receiving multiple-drive, convoy or capital exceptions.

## Timing equation

For `N` neighboring edges:

```text
arrivalTime
= N × (spool + edgeTransit)
+ (N − 1) × cooldown
```

The final cooldown is not charged before arrival at the destination.

Separate ready-again cadence is:

```text
readyAgainTime = arrivalTime + cooldown
```

This distinction prevents route travel time from silently including a post-arrival wait that only matters before the next jump.

## Required named bands

The machine-readable profile contains:

```text
NEIGHBOR_EDGE               = 1 hop / all compatible representatives
REGIONAL_3_HOP              = 3 hops / all compatible representatives
REGIONAL_5_HOP              = 5 hops / all compatible representatives
FLEET_REINFORCEMENT_3_HOP   = 3 hops / compatible military response set
```

Current compatible set:

```text
TORPEDO_CORVETTE
ESCORT_DESTROYER
EARLY_CIVILIAN_FREIGHTER
MINING_SHIP
CRUISER
```

Current explicitly excluded set:

```text
BATTLESHIP
BULK_FREIGHTER_LOADED
FLEET_TANKER_LOADED
CARRIER_AVIATION_GROUP
```

The reinforcement subset is:

```text
TORPEDO_CORVETTE
ESCORT_DESTROYER
CRUISER
```

## Representative consequences

Examples with the current accepted reference law:

```text
TORPEDO_CORVETTE
1-hop arrival      43.375 s
3-hop arrival     310.125 s
5-hop arrival     576.875 s

ESCORT_DESTROYER
1-hop arrival     163.250 s
3-hop arrival     669.750 s
5-hop arrival    1176.250 s

EARLY_CIVILIAN_FREIGHTER
1-hop arrival     205.000 s
3-hop arrival     795.000 s
5-hop arrival    1385.000 s

MINING_SHIP
1-hop arrival     380.000 s
3-hop arrival    1320.000 s
5-hop arrival    2260.000 s

CRUISER
1-hop arrival     469.24375 s
3-hop arrival    1587.73125 s
5-hop arrival    2706.21875 s
```

These are reference-drive calibration consequences, not promises about final galaxy travel time.

## Readiness impact

If accepted, exactly one requirement changes:

```text
INTERSYSTEM_CADENCE_CALIBRATION_BANDS
BLOCKING_STAGE20B_ENTRY
→ SATISFIED
```

Expected blocker count:

```text
12 → 11
```

`FTL_EDGE_TRANSIT_DISTRIBUTION` remains `OWNED_BY_LATER_STAGE20`.

## Regression requirements

Tests must prove:

- all compatible representatives have deterministic 1/3/5-hop samples;
- four named bands exist;
- final cooldown is excluded from arrival but included in ready-again cadence;
- more massive compatible representatives retain longer spool/cadence where the same reference drive applies;
- all current overmass representatives remain explicitly excluded;
- reinforcement includes only the current compatible military response set;
- changing accepted one-edge spool/transit/cooldown inputs changes multi-hop consequences rather than being masked by fixed route seconds;
- readiness removes exactly one blocker.
