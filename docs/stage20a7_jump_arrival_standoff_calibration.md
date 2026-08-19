# Stage 20A.7 — Jump-Arrival Stand-Off Calibration

**Status:** ACCEPTED  
**Parent:** Stage 20A — Representative-Ship Scale Calibration  
**Date:** 2026-08-19

## 1. Purpose

Stage 20A.7 separates inter-system FTL transition authority from local arrival placement and defines the minimum physical closure required before Stage 20 may generate arrival ↔ infrastructure stand-off distributions.

```text
neighbor topology edge + fitted FTL cadence
≠ local arrival coordinates

local arrival placement
+ explicit infrastructure geometry
+ physical braking response
+ accepted traffic/defense envelope
→ measurable center stand-off
```

No universal jump radius, safety bubble or teleport buffer is introduced.

## 2. Accepted topology semantics remain unchanged

Ordinary inter-system movement remains:

```text
NEIGHBOR_EDGE_ONLY
```

A jump traverses one explicit neighboring-system edge. Stage 20A.7 does not change the Stage-10/15/17.5 jump FSM, translated-mass rules, spool/energy/transit/cooldown cadence or player/AI parity.

Topology chooses **which system** may be entered. Stage-20 local geometry chooses **where inside that system** materialization occurs.

## 3. Current runtime materialization inventory

The existing compatibility seam is machine-recorded:

- explicit non-zero requested arrival coordinates remain exact;
- the Stage-10 legacy `(0,0)` pair is remapped through `LocalSystemCoordinates`;
- the current compatibility anchor is the center of the old bounded viewport;
- current post-materialization ship velocity is zero.

The old constants are:

```text
WORLD_WIDTH  = 2000 legacy coordinate units
WORLD_HEIGHT = 1400 legacy coordinate units
legacy arrival = (1000, 700)
```

This anchor is classified:

```text
LEGACY_BOUNDED_VIEWPORT_COMPATIBILITY
```

It is **not** a Stage-20 physical jump point, system center, hub stand-off or SI world-generation constant.

The accepted Stage-20 spatial contract remains authoritative: local physical space is conceptually unbounded and the render/materialization window is not the world boundary.

## 4. Current physical arrival response

`FleetJumpService` currently materializes an arrived fleet at zero local velocity.

Therefore, for the current runtime transition itself:

```text
post-jump braking distance = 0
```

Stage 20A.7 records that fact for every current Stage-20 representative propulsion profile while preserving each profile's production/provisional authority and acceleration provenance.

This does **not** imply arrival points may be placed arbitrarily close to infrastructure. After materialization, ordinary local travel from arrival → hub still pays physical acceleration, coast/braking, delta-v and reaction-mass consequences.

## 5. Tactical response evidence

Stage 20A.7 consumes Stage-20A.5 only as provisional scale evidence.

The current probe maxima are retained machine-readably for later arrival-geometry authoring:

```text
direct-fire probe max = 10,000 km
beam probe max        = 30,000 km
guided probe max      = 1,000 km
```

The profile also records the largest actually assigned layered-defense intercept distance observed by the Stage-20A.5 production scheduler probes.

These values remain:

```text
PROVISIONAL_CALIBRATION_PROBE
```

They are not automatically converted into station defensive radii or jump stand-off constants. In particular, beam runtime has no artificial hard range wall and Stage-20A.5 safe-intercept distance is still a scheduler input pending fragmentation/blast/debris closure.

## 6. Station-dependent stand-off remains unresolved

Stage 20A.6 established that all eight current Stage-18 station archetypes lack authoritative:

- footprint length/width;
- docking-approach clearance;
- traffic clearance.

Therefore Stage 20A.7 records eight station stand-off entries with:

```text
authority = UNRESOLVED
centerStandOffM = absent
```

The existing 300 × 120 × 70 m escort shipyard berth cannot be promoted to the footprint of the containing station.

Likewise Stage-20A.5 weapon/PD probe ranges cannot be silently promoted to that station's defensive envelope.

## 7. Minimum explicit stand-off input seam

When accepted physical geometry exists, Stage 20A.7 requires explicit inputs:

```text
infrastructureId
provenance
operationalRadiusM
trafficClearanceM
defensiveEnvelopeFromCenterM
arrivalSpeedMps
brakingAccelerationMps2
```

The stand-off calculator derives:

```text
brakingDistance = arrivalSpeed² / (2 * brakingAcceleration)
trafficLimitedCenterDistance = operationalRadius + trafficClearance
brakingLimitedCenterDistance = operationalRadius + brakingDistance
defensiveLimitedCenterDistance = explicit center-based defensive envelope
requiredCenterStandOff = max(
    trafficLimitedCenterDistance,
    brakingLimitedCenterDistance,
    defensiveLimitedCenterDistance
)
```

The formula is deliberately input-driven. It cannot obtain station geometry from storage capacity, facility count, sprite size or viewport dimensions, and it does not automatically consume Stage-20A.5 probe maxima as defense constants.

## 8. Acceptance evidence

Exact-head Java-17 CI on implementation head `0083d3b52f1b40cea97095aebbd80c71457aafbb` completed successfully before this status finalization.

Regression evidence proves:

- identical content/profile versions produce identical output;
- current zero-speed materialization yields zero post-jump braking for all representative ships;
- increased arrival speed increases stopping clearance;
- greater physical braking acceleration decreases stopping distance;
- stronger explicitly accepted defense increases required stand-off when it becomes the dominant constraint;
- all unresolved Stage-18 stations remain unresolved rather than receiving a fallback jump radius;
- the legacy viewport anchor remains explicitly non-authoritative for Stage-20 world placement.

The final docs-only acceptance head remains subject to the normal exact-head merge gate.

## 9. Machine-readable implementation

Added:

- `Stage20JumpArrivalSpatialCalibrationProfile`;
- `Stage20JumpArrivalSpatialCalibrationCalculator`;
- `Stage20JumpArrivalSpatialCalibrationProfileTest`.

Current profile version:

```text
stage20a.jump-arrival-spatial.v1
```

The profile contains:

1. `RuntimeArrivalPolicy`;
2. representative physical arrival-response samples;
3. station stand-off closure entries;
4. provisional Stage-20A.5 tactical response evidence;
5. explicit stand-off input/output schemas;
6. machine-visible unresolved constraints.

## 10. Remaining gaps

Stage 20A.7 intentionally leaves unresolved:

- final physical station footprints/traffic geometry;
- accepted station-specific defensive envelopes;
- generated arrival ↔ major-hub distance distributions;
- generated alternate arrival/infrastructure geometry;
- final relation between arrival placement, patrol response and regional security policy;
- missing representative ship roles already carried by Stage 20A.

A bad world seed must not later be rescued by moving arrivals with a hidden teleport offset.

## 11. Next slice

The next implementation slice is **Stage 20A.8 — far-coordinate numerical precision calibration**.

It must prove that physically meaningful distances, velocities and local interactions remain inside a documented numerical-error budget at large local coordinates and that floating-origin/camera-relative presentation can rebase without changing authoritative physical state.
