# Stage 20A Closure — Major Infrastructure Extent Bands

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A physical scale / local operational geometry calibration  
**Date:** 2026-08-19

## Purpose

Close `MAJOR_INFRASTRUCTURE_EXTENT_BANDS` without inventing another spatial scale.

The profile derives three descriptive infrastructure extents directly from the already accepted local-route semantic bands:

```text
CORE_STATION_CLUSTER
  ← STATION_TO_STATION
  10 Mm .. 100 Mm

INDUSTRIAL_RESOURCE_NETWORK
  ← STATION_TO_RESOURCE_FIELD
  50 Mm .. 500 Mm

MAJOR_HUB_REACH
  ← JUMP_ARRIVAL_TO_MAJOR_HUB
  100 Mm .. 1 Gm
```

No new physical distance constant is authored by this slice.

## Authority boundary

The current profile is:

```text
stage20a.major-infrastructure-extents.v1
PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

These values describe where Stage-20B generation normally places meaningful major infrastructure. They are **not**:

- a star-system radius;
- a movement boundary;
- a renderer/culling radius;
- a collision wall;
- a teleport/clamp threshold;
- a sensor or weapon hard range.

Every extent row therefore carries:

```text
hardBoundary = false
clampAllowed = false
```

and the record constructor rejects any attempt to set either flag to true.

## Cross-profile invariants

The profile additionally proves:

1. `CORE_STATION_CLUSTER.minExtentM` is farther than the largest accepted station jump-arrival stand-off;
2. the industrial extent is not smaller than the dense station-cluster extent;
3. the major-hub reach is not smaller than the industrial extent;
4. `MAJOR_HUB_REACH.maxExtentM <= INNER_TO_OUTER_SYSTEM.minDistanceM`;
5. all three bands retain exact route-band provenance.

This ensures a generated major-infrastructure distribution cannot silently overlap station arrival exclusion geometry or grow beyond the accepted transition into the inner→outer-system scale.

## Readiness impact

If exact-head CI accepts the slice:

```text
MAJOR_INFRASTRUCTURE_EXTENT_BANDS
BLOCKING_STAGE20B_ENTRY
→ SATISFIED
```

Expected Stage-20A blocker count:

```text
3 → 2
```

Remaining blockers:

```text
MATERIALIZATION_LOD_CLOSURE
PD_SAFE_INTERCEPT_GEOMETRY
```

## Deferred work

Stage 20B may consume these extents as placement/diagnostic distributions, but must preserve unbounded local physical coordinates. Stage 22 may retune the source route bands after playable logistics/economy testing; any such revision must version the source and derived profiles together.
