# Stage 20A Closure — Local Route Semantic Bands

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A physical scale / local operational geometry calibration  
**Date:** 2026-08-19

## 1. Purpose

Close `LOCAL_ROUTE_SEMANTIC_BANDS` by assigning explicit provisional operational-distance distributions to the four local-route meanings required before Stage 20B, then deriving civilian/military travel consequences through the accepted variable-mass ship physics.

The required semantics are:

```text
station → station
station → resource field
jump arrival → major hub
inner → outer system
```

Before this slice, Stage-20A had useful raw probes at 10 Mm / 100 Mm / 1 Gm / 10 Gm but those probes were explicitly **not** world sizes or semantic route definitions.

This slice does not relabel those probes. It authors a separate semantic distribution resource with its own provenance and provisional authority.

## 2. Authority boundary

Distance authoring status:

```text
PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

The bands are world-generation calibration inputs, not:

- hard system radii;
- map edges;
- ship sensor/weapon ranges;
- render/materialization windows;
- mandatory exact distances between every matching object pair;
- station jump-arrival safety stand-off.

Exact station footprint, traffic-clearance and jump-arrival stand-off remain separate blockers. In particular, `JUMP_ARRIVAL_TO_MAJOR_HUB` is an operational region-to-hub distance distribution, not permission to place arrival coordinates inside an unresolved station safety envelope.

## 3. Accepted semantic distance bands

### STATION_TO_STATION

```text
10 Mm → 100 Mm
10,000 km → 100,000 km
```

This covers dense infrastructure clusters through separated same-system industrial/commercial nodes while retaining the previously accepted 100,000 km local-logistics lower-bound anchor as the upper end of the dense station network rather than as a universal leg.

### STATION_TO_RESOURCE_FIELD

```text
50 Mm → 500 Mm
50,000 km → 500,000 km
```

Extraction therefore creates a meaningful transport leg beyond ordinary station-cluster maneuvering without automatically forcing every mining trip into outer-system scale.

### JUMP_ARRIVAL_TO_MAJOR_HUB

```text
100 Mm → 1 Gm
100,000 km → 1,000,000 km
```

Ordinary arrivals do not emerge directly on top of a major hub. The range is intentionally broad enough for traffic/security geography to matter, while exact local stand-off remains owned by the separate station/arrival geometry closure.

### INNER_TO_OUTER_SYSTEM

```text
1 Gm → 10 Gm
1,000,000 km → 10,000,000 km
```

This is the current operational-content envelope for inner/outer system separation, not an astronomical system boundary. Physical coordinates remain unbounded beyond the generated content envelope.

## 4. Travel policies

Every role is evaluated under two physical policies.

### ROUTINE_SUSTAINED

Uses the accepted `Stage20RepresentativeEnduranceProfile.sustainedThrustN` for that representative while preserving its current:

```text
wet mass
reaction mass
exhaust velocity
delta-v
```

The route envelope is re-derived through the same variable-mass equations. No constant-acceleration approximation is introduced.

### MAX_THRUST_RESPONSE

Uses the representative's existing accepted max/reference propulsion envelope unchanged.

This separates routine logistics/patrol movement from urgent response without inventing `civilianSpeed` or `militarySpeed` multipliers.

## 5. Representative populations

Civilian/logistics:

```text
EARLY_CIVILIAN_FREIGHTER
BULK_FREIGHTER_LOADED
MINING_SHIP
FLEET_TANKER_LOADED
```

Military:

```text
TORPEDO_CORVETTE
ESCORT_DESTROYER
CRUISER
BATTLESHIP
CARRIER_AVIATION_GROUP
```

Every role is measured at both ends of every semantic band under both thrust policies:

```text
4 bands × 9 representatives × 2 endpoints × 2 policies
= 144 deterministic physical samples
```

## 6. Physical outputs

Each sample records:

```text
totalTravelTimeS
requiredDeltaVMps
reactionMassConsumedKg
reactionMassFractionConsumed
brakingDistanceM
peakSpeedMps
route regime
```

plus separate provenance for:

```text
authored distance band
baseline propulsion state
applied thrust policy
```

Thus changing ship capability changes route consequences while changing world-distance authoring changes the same consequences through the shared solver.

## 7. Example routine consequences

With the current accepted sustained-thrust policies, representative values include approximately:

```text
EARLY_CIVILIAN_FREIGHTER
10 Mm       ~ 6.89 h
100 Mm      ~21.56 h
1 Gm        ~65.82 h
10 Gm      ~255.89 h

MINING_SHIP
10 Mm       ~ 9.04 h
100 Mm      ~28.34 h
1 Gm        ~87.25 h
10 Gm      ~315.59 h

BULK_FREIGHTER_LOADED
10 Mm       ~10.47 h
100 Mm      ~32.87 h
1 Gm       ~101.57 h
10 Gm      ~430.89 h
```

These are consequences of the current load/thrust policies, not authored target travel times.

## 8. Readiness impact

If accepted:

```text
LOCAL_ROUTE_SEMANTIC_BANDS
BLOCKING_STAGE20B_ENTRY
→ SATISFIED
```

Expected blocker count:

```text
11 → 10
```

No station geometry, topology, sensor, weapon, PD or materialization blocker is removed by this slice.

## 9. Regression requirements

Tests must prove:

- exactly four required semantic bands exist;
- every band has positive ordered min/max distance and explicit provenance;
- all nine representatives appear in both civilian/military grouping as appropriate;
- exactly 144 endpoint/policy samples are produced;
- routine sustained thrust never exceeds the current representative max thrust;
- at the same role/distance, routine sustained travel is never faster than max-thrust response;
- larger endpoint distance increases travel time and physical consumption consequences;
- the production escort keeps production baseline propulsion provenance while sustained-thrust provenance remains separate;
- local-route authoring never changes unresolved station jump-arrival stand-off authority;
- readiness removes exactly one blocker.

## 10. Deferred tuning

Stage 22 may tune the distributions and operational thrust policy after playable scale testing. Stage 20B may sample within these intervals when placing generated content, but must not clamp physical coordinates to their maxima or treat the intervals as system boundaries.
