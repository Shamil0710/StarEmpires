# Stage 20A.4 — Sensor / Signature Spatial Calibration

**Status:** implementation candidate; acceptance requires exact-head CI and merge into `main`  
**Parent:** Stage 20A — Representative-Ship Scale Calibration  
**Date:** 2026-08-19

## Purpose

Stage 20A.4 converts the already-existing physical sensor/signature model into versioned spatial calibration evidence without introducing a scalar `sensorRange`, stealth score, EW score, screen-space radius or hidden range multiplier.

The measured chain is:

```text
production fitted sensor
+ physical target signature
+ physical separation
+ damage-aware aperture/noise
+ physical jammer geometry where applicable
+ ECCM state where applicable
→ ShipSensorRuntime.observe(...)
→ SNR
→ DETECTED / CLASSIFIED / TRACKED / FIRE_CONTROL measurement evidence
→ measured spatial envelope
```

## Production-authoritative baseline

The first production matrix uses the current `fit.escort_destroyer_schema_v1` as both observer and target because it is the production engineering fit that already exposes a complete physically authored sensor/signature path.

`ShipSensorEngineeringAdapter` supplies the current fitted sensor definitions and channelized `SignatureState`; `Stage20SensorCalibrationCalculator` only changes physical separation and asks the production `ShipSensorRuntime` whether the requested evidence state still survives.

The current production escort exposes:

- passive thermal sensing;
- active radar sensing;
- physical thermal/plume/RCS signature components through the normal engineering derivation.

No range is copied from UI or tactical-viewer scale.

## Measured information-state envelopes

`Stage20SensorCalibrationCalculator` deterministically brackets and bisects the physical separation boundary for:

- `DETECTED`;
- `CLASSIFIED`;
- `TRACKED`;
- `FIRE_CONTROL`.

The hierarchy is validated so stronger information states cannot have a larger spatial envelope than weaker states.

A single passive bearing can legitimately produce DETECTED/CLASSIFIED evidence while TRACKED/FIRE_CONTROL remain absent. Stage 20 does not invent range for a bearing-only measurement merely to fill the table. Distributed passive triangulation is retained as an explicit follow-up geometry problem.

## Damage sensitivity

The same production escort sensor mount is re-derived through ordinary `DamageState` at a 50% surviving `utility_sensor` integrity probe.

This is a sensitivity condition, not a new damage bonus/debuff. The ordinary engineering adapter physically changes aperture, receiver noise and related sensor parameters; the calibration then re-measures the resulting evidence envelope.

Acceptance tests require physical sensor damage not to improve any supported spatial evidence boundary.

## EW / ECCM sensitivity

Active-radar sensitivity uses the already-authored Stage-17.5I `D_DEFENSIVE_EW` fit rather than a hand-entered jammer score.

The jammer is derived through:

```text
Stage175IFleetDoctrineCatalog.D_DEFENSIVE_EW
→ Stage175ICombatTestContentPack.loadDoctrines()
→ DerivedShipCalculator
→ ShipElectronicWarfareEngineeringAdapter
→ physical NoiseJammer
```

The calibration probe places that jammer 1,000,000 m cross-range from the observer. This is an explicit sensitivity-probe geometry, not a future station spacing, sensor range or world-generation constant.

The profile measures active-radar envelopes with:

- jammer absent;
- authored jammer present, ECCM disabled;
- authored jammer present, ECCM enabled.

The jammer remains marked `PROVISIONAL_ACCEPTED_COMBAT_TEST`; it is not silently promoted to final Stage-22 faction content. ECCM recovery retains the fitted electrical-demand and waste-heat values in the profile so processing gain is not treated as free capability.

## Track-age / uncertainty boundary

The current `ShipSensorRuntime.TrackQualityPolicy.defaultPolicy()` is explicitly preserved as:

`PROVISIONAL_PRE_STAGE20_DEFAULT`

because its own production documentation says it is a deterministic default used before Stage-20 scale calibration.

Stage 20A.4 therefore records its current position-sigma, age and process-noise values for provenance and regression purposes but does **not** declare them final world/balance constants.

The final fused TRACKED/FIRE_CONTROL quality policy must be revisited after Stage 20A.5 weapon time-of-flight/effectiveness geometry is available, so fire-control covariance requirements can be justified against physical weapon employment rather than selected in isolation.

## Explicit unresolved gaps

The machine-readable profile keeps these gaps open:

- `final_fused_track_quality_policy_pending_weapon_geometry`;
- `distributed_passive_triangulation_geometry_not_yet_profiled`;
- `representative_sensor_and_target_class_coverage_incomplete`.

Missing coverage is preferable to fabricated sensor capability.

## Acceptance invariants

Stage 20A.4 tests require:

- identical production content + calibration version produces identical output;
- current production escort yields passive-thermal and active-radar samples;
- active radar yields nested DETECTED → CLASSIFIED → TRACKED → FIRE_CONTROL distance envelopes;
- a single passive bearing never gains fake ranged TRACKED/FIRE_CONTROL evidence;
- 50% sensor-mount integrity reduces supported evidence envelopes rather than applying a generic hidden debuff;
- stronger physical radar signature cannot reduce detection distance;
- authored noise jamming shrinks the active-radar envelope;
- fitted ECCM recovers part of jammed capability and retains real power/heat cost;
- provisional EW and provisional pre-Stage20 track-policy provenance remain machine-visible.

## Next slice

Stage 20A.5 should calibrate production weapon employment geometry:

1. kinetic time-of-flight / effectiveness bands;
2. beam physical geometry / dwell / effectiveness bands;
3. guided weapon flight / terminal geometry;
4. PD / interceptor engagement and safe-intercept geometry;
5. target-motion sensitivity where the production weapon runtime already owns it;
6. use those results to revisit the provisional fused-track/fire-control quality policy rather than inventing an isolated accuracy threshold.

After weapon/PD geometry, Stage 20A continues into formation spacing, station physical footprint/spacing, jump-arrival stand-off and numerical precision/materialization bands before Stage 20B star-system geometry generation begins.
