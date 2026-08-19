# Stage 20A — Local Route Semantic Bands v1

**Status:** PROVISIONAL ACCEPTED REFERENCE — exact-head CI required before merge  
**Requirement:** `LOCAL_ROUTE_SEMANTIC_BANDS`  
**Profile:** `stage20a.local-route-semantic-bands.v1`  
**Date:** 2026-08-19

## Purpose

Stage 20A already had raw route sensitivity probes at 10 Mm / 100 Mm / 1 Gm / 10 Gm. Those probes are useful physics measurements but are explicitly **not** semantic world geometry.

This profile separately authors the four meanings required before Stage 20B:

```text
station → station
station → resource field
jump arrival → major hub
inner → outer system
```

The authored bands are operational content-distribution seeds. They are not map edges, hard system radii, viewport sizes, sensor ranges, weapon ranges or LOD thresholds.

## Authority

```text
authority = PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

Stage 22 may tune these distributions after playable economy/travel validation. Any revision changes the versioned profile rather than silently changing generated-world semantics.

## Semantic bands

| Meaning | Minimum | Maximum |
|---|---:|---:|
| Station → station | 10 Mm / 10,000 km | 100 Mm / 100,000 km |
| Station → resource field | 50 Mm / 50,000 km | 500 Mm / 500,000 km |
| Jump arrival → major hub | 100 Mm / 100,000 km | 1 Gm / 1,000,000 km |
| Inner → outer system | 1 Gm / 1,000,000 km | 10 Gm / 10,000,000 km |

These bands are deliberately broader than individual station footprints and exclusion zones while remaining small enough that local propulsion, reaction mass and travel time matter to the economy.

## Dependency on closed station jump geometry

`STATION_JUMP_ARRIVAL_STANDOFF` is already closed by `stage20a.jump-arrival-spatial.v2`.

The route profile records the largest current station center stand-off and requires:

```text
JUMP_ARRIVAL_TO_MAJOR_HUB.minDistanceM
    > maxClosedStationStandOffM
```

This prevents later world generation from treating a semantic jump-to-hub route as permission to place an arrival inside the hub's physical/exclusion geometry.

The two authorities remain distinct:

- A7 owns minimum safe station-centered arrival geometry;
- this profile owns the broader operational arrival-region → hub distribution.

## Physical travel matrix

Every one of the 9 Stage-20 representative roles is evaluated at both endpoints of all 4 bands under 2 physical thrust policies:

```text
4 bands × 9 representatives × 2 endpoints × 2 thrust policies
= 144 deterministic route samples
```

Representative populations:

```text
CIVILIAN_LOGISTICS
- EARLY_CIVILIAN_FREIGHTER
- BULK_FREIGHTER_LOADED
- MINING_SHIP
- FLEET_TANKER_LOADED

MILITARY
- TORPEDO_CORVETTE
- ESCORT_DESTROYER
- CRUISER
- BATTLESHIP
- CARRIER_AVIATION_GROUP
```

## Thrust policies

### ROUTINE_SUSTAINED

Uses the accepted `Stage20RepresentativeEnduranceProfile.sustainedThrustN` for the representative while preserving wet mass, reaction mass and exhaust velocity. A derived propulsion envelope is rebuilt through the shared variable-mass equations.

### MAX_THRUST_RESPONSE

Uses the representative's existing accepted max/reference propulsion envelope unchanged.

There are no civilian-speed or military-speed multipliers.

## Derived outputs

Every route sample records:

```text
totalTravelTimeS
requiredDeltaVMps
reactionMassConsumedKg
reactionMassFractionConsumed
brakingDistanceM
peakSpeedMps
route regime
```

and retains separate provenance for:

```text
authored semantic distance
baseline propulsion authority
applied thrust policy
```

The regression contract requires, at the same route endpoint:

```text
ROUTINE_SUSTAINED thrust <= MAX_THRUST_RESPONSE thrust
ROUTINE_SUSTAINED time >= MAX_THRUST_RESPONSE time
```

and for each band/policy:

```text
MAX endpoint distance >= MIN endpoint distance
MAX endpoint travel time >= MIN endpoint travel time
MAX endpoint required delta-v >= MIN endpoint required delta-v
MAX endpoint reaction-mass consumption >= MIN endpoint consumption
```

Thus distance has direct economic and operational consequences without a second abstract movement model.

## Example routine consequences

Current accepted sustained-thrust references produce approximately:

```text
EARLY_CIVILIAN_FREIGHTER
10 Mm       ~ 6.9 h
100 Mm      ~21.6 h
1 Gm        ~65.8 h
10 Gm      ~255.9 h

MINING_SHIP
10 Mm       ~ 9.0 h
100 Mm      ~28.3 h
1 Gm        ~87.3 h
10 Gm      ~315.6 h

BULK_FREIGHTER_LOADED
10 Mm       ~10.5 h
100 Mm      ~32.9 h
1 Gm       ~101.6 h
10 Gm      ~430.9 h
```

These are derived consequences of the accepted ship/thrust states, not authored target travel times.

## Readiness effect

After acceptance:

```text
LOCAL_ROUTE_SEMANTIC_BANDS:
  BLOCKING_STAGE20B_ENTRY -> SATISFIED

blocking requirement count:
  5 -> 4
```

Remaining blockers:

1. `PD_SAFE_INTERCEPT_GEOMETRY`
2. `TOPOLOGY_QUALITY_CALIBRATION_BANDS`
3. `MAJOR_INFRASTRUCTURE_EXTENT_BANDS`
4. `MATERIALIZATION_LOD_CLOSURE`

This slice does not promote the bands into hard system boundaries. Physical coordinates remain unbounded and Stage 20B may sample within these distributions when authoring generated content.
