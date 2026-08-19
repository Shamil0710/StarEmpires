# Stage 20A Closure — Materialization / LOD

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A physical scale / runtime relevance calibration  
**Date:** 2026-08-19

## Purpose

Close `MATERIALIZATION_LOD_CLOSURE` without rewriting the historical `stage20a.materialization-lod.v1` evidence and without turning render/materialization distance into a physical system boundary.

Historical v1 remains authoritative history:

```text
ACTIVE_LOCAL = UNRESOLVED
TACTICAL     = UNRESOLVED
```

The new superseding profile is:

```text
stage20a.materialization-lod-closure.v1
```

It closes the numeric promotion bands only after later Stage-20A work supplied accepted physical interaction/distribution geometry.

## Production lifecycle authority

Production runtime already provides a reversible materialization boundary:

```text
Stage20MaterializationService
Stage20RepresentationScheduler
```

The accepted runtime semantics are:

- authoritative `EntityId` survives dematerialization;
- persistent ECS state is captured and restored;
- authoritative hierarchical/double physical kinematics survive dematerialization;
- DORMANT removes only the local Ashley runtime representation;
- STRATEGIC remains live persistent/reduced-rate simulation;
- ACTIVE_LOCAL and TACTICAL remain live representations;
- due-event / strategic / local / tactical relevance promotes synchronously;
- simulation-time wake latency is currently `0 s`.

Therefore no guessed distance padding is required for wake latency.

## ACTIVE_LOCAL activation

`ACTIVE_LOCAL` uses the already accepted descriptive major-infrastructure reach:

```text
Stage20MajorInfrastructureExtentCalibrationProfile.maximumMajorInfrastructureExtentM()
= 1 Gm
```

This is a proactive reduced-local-simulation distribution window, not a movement/render/world boundary.

## TACTICAL activation

`TACTICAL` uses the maximum already accepted direct station interaction/exclusion geometry:

```text
max(
  station operationalRadiusM,
  station defensiveExclusionReferenceM
)
```

Sources:

```text
Stage20StationPhysicalGeometryProfile
Stage20StationDefensiveSensorGeometryProfile
```

The tactical band deliberately does **not** use passive or active sensor-detection distance. A target may be detected far outside the tactical activation radius; detection alone does not force full combat materialization.

## Relevance-first invariant

Distance is only a proactive activation aid.

The accepted ordering remains:

```text
direct tactical relevance → TACTICAL
local operational relevance → ACTIVE_LOCAL
strategic relevance / due event → STRATEGIC
otherwise → DORMANT
```

A distance threshold cannot suppress a stronger authoritative relevance signal.

Likewise render culling cannot demote physical authority.

The closure profile therefore requires:

```text
authoritativeStateRetained = true
distanceCanSuppressDirectRelevance = false
renderBoundary = false
worldBoundary = false
```

## Readiness impact

If exact-head CI accepts the slice:

```text
MATERIALIZATION_LOD_CLOSURE
BLOCKING_STAGE20B_ENTRY
→ SATISFIED
```

Expected Stage-20A blocker count:

```text
2 → 1
```

The sole remaining blocker will be:

```text
PD_SAFE_INTERCEPT_GEOMETRY
```

## Deferred review

Stage 22 may tune proactive LOD policy after representative playable-load profiling. Such tuning must version the closure profile and cannot change the core invariants: physical space remains unbounded, persistent state remains authoritative, and render distance is never a physical boundary.
