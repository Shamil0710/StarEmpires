# Stage 20A Closure — Readiness Refresh after Fire-Control Policy Acceptance

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A Closure / Readiness Remediation  
**Date:** 2026-08-19

## Purpose

Refresh the complete Stage-20A readiness gate after acceptance of `Stage20FireControlPolicyClosureProfile` without rewriting the historical Stage-20A.4 sensor artifact and without changing any unrelated readiness requirement.

## Accepted dependency

The later fire-control closure establishes:

```text
minimum shared solved-Cartesian weapon state = TRACKED
universal sensor-side FIRE_CONTROL permission bit = not required
final usefulness = weapon + target + covariance + motion + geometry + effect dependent
```

Therefore the historical A.4 gap:

```text
final_fused_track_quality_policy_pending_weapon_geometry
```

remains historically true in the A.4 profile but is superseded for current readiness by:

```text
stage20a.fire-control-policy-closure.v1
```

## Readiness delta

Exactly one requirement changes state:

```text
FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE
BLOCKING_STAGE20B_ENTRY
→ SATISFIED
```

The Stage-20A gate remains:

```text
BLOCKED_FOR_STAGE20B
```

and the expected blocking requirement count changes:

```text
16 → 15
```

No other blocker is removed.

In particular, this refresh does **not** close:

- representative propulsion/endurance coverage;
- civilian ordinary-FTL coverage;
- representative sensor/target-class coverage;
- representative weapon/target coverage;
- PD safe-intercept fragmentation/debris geometry;
- formation spacing bands;
- station footprint/defensive/arrival geometry;
- local-route semantic bands;
- topology-quality calibration bands;
- major-infrastructure extent bands;
- numeric materialization/LOD activation bands.

## Regression requirement

`Stage20ACalibrationReadinessProfileTest` must prove simultaneously that:

1. blocker count is exactly 15;
2. `FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE` is `SATISFIED`;
3. its evidence records the historical A.4 pending state and the accepted later closure version;
4. all four currently missing representative roles remain explicit;
5. all other previously blocking requirements remain blocking.

## Immediate next work

After exact-head CI and merge, continue Stage-20A closure from the remaining physical dependencies. Representative ship/endurance/civilian-FTL coverage remains the primary unresolved dependency; old `ship_reference_designs_v0_2.json` is `authoring-benchmark-only`, contains cruiser/carrier seeds but no mining ship or early civilian freighter, and must not be silently promoted to production authority.
