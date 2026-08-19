# Stage 20A Closure — Track / Fire-Control Policy

**Status:** ACCEPTED — implementation head passed exact-head Java-17 CI; final status-only merge gate pending  
**Parent:** Stage 20A Closure / Readiness Remediation  
**Workstream:** 3 — Sensor / weapon / formation closure  
**Date:** 2026-08-19

## 1. Purpose

Stage 20A.4 deliberately left its fused TRACKED/FIRE_CONTROL policy provisional until weapon geometry existed. Stage 20A.5 then established production kinetic, beam and guided physical execution. This closure resolves the pending policy without inventing one universal sensor sigma/age threshold for every weapon family.

The accepted rule is:

```text
shared solved-Cartesian weapon floor = TRACKED

weapon usefulness / admissibility above that floor
= weapon + target + covariance + motion + geometry + effect dependent
```

The sensor-side `FIRE_CONTROL` information state may remain a useful high-quality evidence label, but it is not a universal permission bit that all weapons must require.

## 2. Production evidence

### Kinetic

`WeaponFireControl.planKinetic(...)` already accepts:

```text
TRACKED
or
FIRE_CONTROL
```

and propagates:

- position covariance;
- target velocity uncertainty;
- bounded maneuver acceleration;
- time of flight;
- pointing jitter;
- resulting one-sigma aim uncertainty and maneuver envelope.

Therefore kinetic usefulness is already continuous rather than governed by one global FIRE_CONTROL sigma wall.

### Guided

`GuidanceRuntime.planLeadPursuit(...)` already accepts a position-known `TRACKED` state and evaluates:

- target kinematics;
- missile acceleration / burn duration;
- remaining delta-v;
- terminal reserve;
- physical lead/pursuit geometry.

Guided execution likewise does not need a universal sensor-side FIRE_CONTROL threshold.

### Beam

Before this closure, `BeamWeaponRuntime.plan(...)` was the outlier: it required the global enum state `FIRE_CONTROL` even though the runtime already propagated track covariance into the effective beam spot.

The runtime now accepts:

```text
TRACKED
or
FIRE_CONTROL
```

when Cartesian position is known.

Track uncertainty remains physically consequential:

```text
worse covariance
→ larger one-sigma track radius
→ larger effective beam spot
→ lower mean irradiance
```

Thus a poor TRACKED solution is not magically equivalent to a precise track; it is merely evaluated continuously rather than rejected by one unrelated global threshold.

## 3. No new hard range or hit chance

This change does not introduce:

- a hard beam range;
- a global weapon hit probability;
- a universal position-sigma limit;
- a universal track-age limit;
- a player-only targeting exception.

A beam may form a mathematical solution at long range while becoming ineffective because diffraction, pointing jitter, covariance and target/material response dilute delivered effect.

## 4. Machine-readable closure

Added:

- `Stage20FireControlPolicyClosureProfile`;
- `Stage20FireControlPolicyClosureProfileTest`;
- `BeamWeaponTrackQualityClosureTest`.

Profile version:

```text
stage20a.fire-control-policy-closure.v1
```

It records:

```text
minimumSharedWeaponTrackState = TRACKED
universalSensorFireControlThresholdRequired = false
kineticConsumesContinuousTrackUncertainty = true
beamConsumesContinuousTrackUncertainty = true
guidedConsumesContinuousTrackState = true
```

with explicit runtime provenance.

## 5. Historical A.4 gap handling

The A.4 calibration artifact remains historically truthful: at the time A.4 was accepted, final fused fire-control policy genuinely was pending weapon geometry.

This closure therefore **supersedes** that historical pending item rather than rewriting the A.4 record as if the answer had already existed.

The readiness calculator should consume `Stage20FireControlPolicyClosureProfile` as later accepted closure evidence and mark:

```text
FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE
= SATISFIED
```

in the narrow follow-up readiness refresh.

## 6. Regression invariants

Tests require:

- a position-known `TRACKED` beam target is admitted;
- a merely `CLASSIFIED` target is still rejected;
- worse TRACKED covariance increases effective beam spot;
- worse covariance lowers irradiance;
- physical dwell-duty remains enforced for TRACKED beam solutions;
- the closure profile rejects one universal sensor-side FIRE_CONTROL permission threshold;
- kinetic, beam and guided families are all represented in the closure provenance.

## 7. Acceptance evidence

The implementation head `bec77b23b4302cb65ef9b92156678a0dd6ef46f5` passed the complete Java-17 `clean verify` gate after the historical beam regression was deliberately updated from the superseded `FIRE_CONTROL-only` rule to the accepted Stage-20 `TRACKED Cartesian floor` rule.

The first failed CI attempt exposed an invalid new test fixture missing the required beam ID. The second failed attempt exposed exactly one old regression asserting the provisional rule being replaced. Neither failure was bypassed: the fixture was corrected and the existing regression was rewritten to retain rejection below TRACKED plus the physical dwell-duty invariant.

## 8. Immediate next action

After the final status-only exact-head CI and merge gate:

1. refresh the Stage-20A readiness gate to consume this later closure profile and remove exactly `FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE` from the blocker set;
2. continue Workstream 3 only where accepted physics exists;
3. do not use this closure to hide the still-open representative sensor/target coverage blocker.
