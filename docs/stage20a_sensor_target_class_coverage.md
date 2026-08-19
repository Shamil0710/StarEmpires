# Stage 20A — Representative Sensor / Target-Class Coverage Closure

**Status:** IMPLEMENTED — exact-head CI required before merge  
**Requirement:** `SENSOR_TARGET_CLASS_COVERAGE`  
**Profile:** `stage20a.sensor-target-coverage.v1`  
**Date:** 2026-08-19

## Purpose

Close the Stage-20A readiness blocker that existed because Stage20A.4 measured only the production escort sensor suite against the production escort signature.

This closure does **not** rewrite the historical Stage20A.4 report. The old `representative_sensor_and_target_class_coverage_incomplete` entry remains evidence of what A4 had not yet covered. The current readiness gate is superseded by the explicit `Stage20SensorTargetClassCoverageProfile` when deciding whether representative sensor/target coverage is ready for Stage 20B.

## Observer authority

The observer remains production-derived:

```text
representative: ESCORT_DESTROYER
fit: fit.escort_destroyer_schema_v1
authority: PRODUCTION_ENGINEERING
modes consumed by closure:
- PASSIVE_THERMAL
- ACTIVE_RADAR
```

Threshold distances are computed through the existing production observation model with nominal sensor runtime and no EW fallback constants.

## Target matrix

| Target class | Thermal source | Radar source | Authority |
|---|---|---|---|
| `CARRIER_INTERCEPTOR` | `ship_reference_designs_v0_2.json` installed continuous waste heat = 4 MW | not authored | provisional accepted reference |
| `TORPEDO_CORVETTE` | `sensor_track_reference_v0_8.json` = 11 MW | v0.8 RCS seed = 100 m² | provisional accepted reference |
| `RECON_EW_FRIGATE` | `sensor_track_reference_v0_8.json` = 76.5 MW | not authored | provisional accepted reference |
| `ESCORT_DESTROYER` | production `ShipSensorEngineeringAdapter.staticSignature` | production `ShipSensorEngineeringAdapter.staticSignature` | production engineering |
| `CRUISER` | `ship_reference_designs_v0_2.json` installed continuous waste heat = 402.3 MW | not authored | provisional accepted reference |
| `BATTLESHIP` | `sensor_track_reference_v0_8.json` = 1,387.3 MW | v0.8 RCS seed = 10,000 m² | provisional accepted reference |
| `FLEET_CARRIER` | `ship_reference_designs_v0_2.json` installed continuous waste heat = 1,020.3 MW | not authored | provisional accepted reference |

### Authority boundary for v0.2 waste heat

The v0.2 `installedContinuousWasteHeatW` values are accepted here only as **provisional Stage-20 thermal-envelope references**. They are not promoted to production content and remain `stage22ReviewRequired=true`.

No spectral distribution, aspect response, plume state, optical return, radio emission, radar cross-section, stealth coefficient or combat effectiveness is inferred from ship class, mass, sprite size or waste heat.

Where a physical channel is not authored, it remains zero/unsupported in the representative signature.

## Physical information-state rules retained

The closure intentionally preserves the existing observation semantics:

- passive thermal evidence may provide `DETECTED` / `CLASSIFIED` envelopes;
- a single passive bearing does not invent a ranged `TRACKED` solution;
- passive evidence does not invent `FIRE_CONTROL` quality;
- active radar is evaluated only where an RCS channel actually exists;
- no universal scalar `sensorRange` or scalar `stealth` score is introduced.

The already accepted `FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE` remains a separate Stage-20A requirement and is not replaced by this matrix.

## Readiness effect

After this closure the machine-readable Stage-20A gate is expected to change:

```text
SENSOR_TARGET_CLASS_COVERAGE:
  BLOCKING_STAGE20B_ENTRY -> SATISFIED

blocking requirement count:
  11 -> 10
```

Stage 20A remains `BLOCKED_FOR_STAGE20B` because independent physical/calibration blockers still remain. The next remediation item in dependency order is `WEAPON_REPRESENTATIVE_TARGET_COVERAGE`; station footprint, docking and jump-arrival geometry must still not be invented before their own authority work is complete.

## Regression expectations

The test suite requires:

- deterministic profile derivation;
- all seven representative target classes present;
- production authority retained for the escort target;
- six benchmark-derived targets explicitly provisional and Stage-22-reviewable;
- passive thermal detection for every target class;
- no fabricated passive ranged track or fire-control envelope;
- active-radar results only for targets with authored/non-zero RCS;
- physically ordered thermal envelopes for the accepted reference values;
- readiness blocker count reduced from 11 to 10 with this requirement marked `SATISFIED`.
