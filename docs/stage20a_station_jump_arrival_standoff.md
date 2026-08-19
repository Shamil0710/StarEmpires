# Stage 20A — Station Jump-Arrival Stand-off v2

**Status:** PROVISIONAL DERIVED PHYSICAL GEOMETRY — exact-head CI required before merge  
**Requirement:** `STATION_JUMP_ARRIVAL_STANDOFF`  
**Profile:** `stage20a.jump-arrival-spatial.v2`  
**Date:** 2026-08-19

## Closure rule

The existing A7 physical rule remains authoritative:

```text
brakingDistance = arrivalSpeed² / (2 * brakingAcceleration)
trafficLimited = operationalRadius + additionalTrafficClearance
brakingLimited = operationalRadius + brakingDistance
defensiveLimited = defensiveExclusionReference
requiredCenterStandOff = max(trafficLimited, brakingLimited, defensiveLimited)
```

No universal jump radius is authored.

## Inputs

For every one of the eight Stage-18 station archetypes:

- `operationalRadius` comes from `stage20a.station-physical-geometry.v1` through the existing A6 placement-envelope derivation;
- `defensiveExclusionReference` comes from `stage20a.station-defensive-sensor-geometry.v1`;
- current materialization speed comes from the production `FleetJumpService` / `LocalSystemCoordinates` arrival policy;
- braking capability uses the minimum positive representative acceleration in the current `Stage20ScaleCalibrationProfile` as a conservative physical input.

Each station sample preserves those sources in machine-readable provenance.

## No traffic double-counting

The accepted station `operationalRadius` already contains:

```text
footprintHalfDiagonal + max(dockingApproachClearance, trafficClearance)
```

Therefore A7 v2 supplies:

```text
additionalTrafficClearance = 0
```

when deriving station jump stand-off. Passing the authored traffic clearance again would count the same clearance twice.

## Current zero-speed materialization

The current runtime arrival speed is `0 m/s`, so:

```text
brakingDistance = 0
```

for every current representative. This is not hard-coded into the stand-off formula: the existing physical braking term remains active and will automatically become non-zero if the arrival policy changes.

## Defensive exclusion semantics

The defensive input is the independent Stage-20A exclusion reference accepted in `stage20a.station-defensive-sensor-geometry.v1`. It is **not** treated as:

- a production station weapon range;
- proof of an end-to-end station fire-control chain;
- a replacement for the Stage-20A.5 raw tactical probe maxima.

Stage 22 still owns actual station sensor/weapon/fire-control content.

## Authority

Closed station stand-offs use:

```text
PROVISIONAL_STAGE20_DESIGN_REFERENCE
```

All eight samples contain a positive center stand-off and no unresolved reasons. The old bounded-viewport arrival anchor remains `LEGACY_BOUNDED_VIEWPORT_COMPATIBILITY` and is not promoted to physical world geometry.

## Readiness effect

After acceptance:

```text
STATION_JUMP_ARRIVAL_STANDOFF:
  BLOCKING_STAGE20B_ENTRY -> SATISFIED

blocking requirement count:
  6 -> 5
```

Remaining Stage-20A blockers:

1. `PD_SAFE_INTERCEPT_GEOMETRY`
2. `LOCAL_ROUTE_SEMANTIC_BANDS`
3. `TOPOLOGY_QUALITY_CALIBRATION_BANDS`
4. `MAJOR_INFRASTRUCTURE_EXTENT_BANDS`
5. `MATERIALIZATION_LOD_CLOSURE`

The generated arrival-to-hub distance distribution itself remains later Stage-20 world authoring; this slice closes only the minimum physical station exclusion/arrival geometry needed before Stage 20B.
