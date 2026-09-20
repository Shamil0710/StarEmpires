# Stage 20A Closure — Major Infrastructure Extent Bands

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A physical scale / local operational geometry calibration  
**Date:** 2026-08-19

## Purpose

Close `MAJOR_INFRASTRUCTURE_EXTENT_BANDS` without inventing another spatial scale.

The historical v1 profile derived all three extents directly from the accepted Stage-20 route bands. Stage-22 cadence review now keeps the compact core/resource route extents but deliberately preserves the broad 1 Gm major-hub/system reach as a separate physical coverage authority:

```text
CORE_STATION_CLUSTER
  ← STATION_TO_STATION v2
  4 Mm .. 30 Mm

INDUSTRIAL_RESOURCE_NETWORK
  ← STATION_TO_RESOURCE_FIELD v2
  10 Mm .. 150 Mm

MAJOR_HUB_REACH
  ← JUMP_ARRIVAL_TO_MAJOR_HUB v2 minimum
  → retained INNER_TO_OUTER_SYSTEM transition
  40 Mm .. 1 Gm
```

The retained 1 Gm maximum is not a new constant: it is the unchanged `INNER_TO_OUTER_SYSTEM.minDistanceM` transition. This explicitly decouples broad active-local/system coverage from the much shorter routine traffic routes.

## Authority boundary

The current profile is:

```text
stage20a.major-infrastructure-extents.v2
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
4. `MAJOR_HUB_REACH.maxExtentM == INNER_TO_OUTER_SYSTEM.minDistanceM`;
5. compact route provenance and the retained inner/outer transition provenance remain explicit.

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
