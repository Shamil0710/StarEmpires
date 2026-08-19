# Stage 20A Closure — PD Safe-Intercept Geometry

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A weapon / layered-defense spatial calibration  
**Date:** 2026-08-19

## Purpose

Close `PD_SAFE_INTERCEPT_GEOMETRY` without pretending that a successful intercept deletes the incoming kinetic energy.

The production `LayeredDefenseScheduler` already accepts:

```text
safeMinimumInterceptDistanceM
```

as a physical policy input. Before this closure Stage-20A had no accepted provenance for that input.

## Physical evidence

The packaged Stage-20 calibration evidence copies the full 12-row sensitivity sweep from:

```text
docs/benchmarks/protection_debris_reference_v0_7.json
```

The source retains its original status:

```text
authoring-benchmark-only
```

It is not promoted to a universal fragmentation law.

Reference threat:

```text
M_ANTI_SHIP_MISSILE
mass = 12,000 kg
axial velocity = 18,000 m/s
kinetic energy = 1.944e12 J
```

Reference projected target:

```text
REFERENCE_BATTLESHIP_NOSE_ON
110 m × 85 m
```

The benchmark uses tested lateral dispersion sigmas:

```text
50 m/s
200 m/s
500 m/s
```

and stand-offs:

```text
10 km
20 km
50 km
100 km
```

## Provisional Stage-20 risk policy

Physics does not provide a binary `safe` threshold in v0.7. Stage 20 therefore authors a separate, explicit and reviewable policy:

```text
maxProjectedHitFraction = 0.02
```

This is **not a physical law**. It is a provisional generation/defense-policy acceptance threshold and requires Stage-22 playable/content review.

The closure evaluates the narrowest tested debris dispersion:

```text
conservativeLateralSigma = 50 m/s
```

For that row family:

```text
10 km   → hit fraction 0.832291...
20 km   → hit fraction 0.376687...
50 km   → hit fraction 0.074016...  FAIL
100 km  → hit fraction 0.019085...  PASS
```

Therefore the first authored passing stand-off is:

```text
safeMinimumInterceptDistanceM = 100,000 m
```

## Residual risk remains explicit

At the selected 100 km / 50 m/s row:

```text
projected hit fraction ≈ 1.9086%
projected intersecting kinetic energy ≈ 37.103 GJ
```

Consequently the profile requires:

```text
residualRiskZero = false
physicalLaw = false
schedulerInputReady = true
```

The term `safeMinimumInterceptDistanceM` therefore means **minimum distance accepted by the current provisional defense-risk policy**, not guaranteed harmless debris.

## Machine-readable closure

Packaged evidence:

```text
src/main/resources/data/calibration/stage20-pd-safe-intercept-v1.json
```

Profile:

```text
Stage20PdSafeInterceptCalibrationProfile
stage20a.pd-safe-intercept.v1
PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

The selected distance is derived by scanning the copied evidence rows; `100 km` is not duplicated as a hardcoded gameplay constant in the selector.

`Stage20WeaponSpatialCalibrationCalculator` now records the old scheduler-input gap as superseded by this profile while keeping unrelated weapon/PD unresolved notes unchanged.

## Readiness impact

If exact-head CI accepts this slice:

```text
PD_SAFE_INTERCEPT_GEOMETRY
BLOCKING_STAGE20B_ENTRY
→ SATISFIED
```

Expected gate:

```text
blocking requirements = 0
Stage20A GateStatus = READY_FOR_STAGE20B
```

This completes the Stage-20A entry gate and permits a fresh Stage-20B implementation branch.

## Deferred review

Stage 22 must revisit the 2% policy against playable fleet-defense behavior, target classes, richer fragmentation/blast models and accepted loss tolerance. Any change must version the policy/evidence profile; it must not silently mutate `stage20a.pd-safe-intercept.v1`.
