# Stage 20A — Formation Spacing Acceptance Bands

**Status:** IMPLEMENTED — exact-head CI required before merge  
**Requirement:** `FORMATION_SPACING_BAND_CLOSURE`  
**Profile:** `stage20a.formation-spacing-bands.v1`  
**Date:** 2026-08-19

## Purpose

Convert already accepted Stage-19 tactical formation geometry into a versioned Stage-20A calibration reference without pretending that acceptance-fixture distances are final combat doctrine.

The source remains:

```text
docs/stage19i_l_tactical_formation.md
Stage20FormationStationSpatialCalibrationProfile
production escort acceleration from Stage20ScaleCalibrationProfile
```

No new spacing distance is introduced by this closure.

## Authority boundary

The Stage-19 document explicitly states that its distances are acceptance-scenario geometry rather than final combat-balance values. Stage 20 therefore records the derived bands as:

```text
authority = PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

This is enough for Stage-20 world-scale calibration: the generator may reason about already tested tactical formation scale without inventing a new number, while later content/balance work may supersede the reference with a new profile version.

The source Stage-20A.6 samples remain `PROVISIONAL_STAGE19_TACTICAL_PROBE`; their historical authority is not rewritten.

## Derived bands

### COMPACT_ACCEPTANCE

Source probes:

```text
stage19.compact_4v4
stage19.compact_16_ship_side
```

Derived directly from those samples:

```text
ship-count evidence: 4 .. 16
center-to-center spacing: 100 .. 120 m
```

### DISPERSED_ACCEPTANCE

Source probe:

```text
stage19.dispersed_4v4
```

The only authored dispersed geometry is preserved exactly:

```text
ship-count evidence: 4
center-to-center spacing: 240 m
```

Stage 20 does not extrapolate a 16-ship dispersed value.

## Physical recovery seam

Each source sample already combines authored Stage-19 geometry with the physically derived production escort acceleration. The profile retains the resulting ideal rest-to-tolerance recovery-time envelope as calibration evidence.

This keeps the causal chain explicit:

```text
authored own-side tactical geometry
+ production escort acceleration
→ physical recovery distance/time evidence
→ provisional Stage-20 formation scale band
```

The ideal recovery time remains a lower-bound calibration quantity, not a promise that live ships always recover in exactly that time.

## Readiness effect

After acceptance the machine-readable Stage-20A gate is expected to change:

```text
FORMATION_SPACING_BAND_CLOSURE:
  BLOCKING_STAGE20B_ENTRY -> SATISFIED

blocking requirement count:
  9 -> 8
```

`PD_SAFE_INTERCEPT_GEOMETRY` intentionally remains open: v0.7 debris physics provides stand-off risk curves but no accepted policy threshold defining when residual debris risk becomes "safe".

Station physical geometry also remains independent and unresolved; tactical ship formation spacing must never be reused as station spacing or docking clearance.
