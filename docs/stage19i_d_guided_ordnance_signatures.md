# Stage 19I-D — Guided Ordnance Physical Signatures

Status: IMPLEMENTED IN THIS SLICE, PENDING CI/MERGE

## Purpose

Remove the first prerequisite for actor-bounded missile tracking. Guided ammunition must expose physical observability through content-authored source strengths rather than a defense-AI coefficient, content-ID heuristic or arbitrary detection range.

## Content authority

Each `GuidedAmmunitionDefinition` may now carry a `GuidedSignatureDefinition` with:

- thermal radiant power;
- powered engine-plume radiant power;
- radar cross section;
- reflected optical source power;
- active radio/seeker emission power;
- jammer emission power.

The definition converts directly to the existing production `SignatureState` consumed by ordinary sensor equations.

These values are **source strengths**, not ranges, hit chances, threat scores or detection bonuses. Detection remains a consequence of the existing sensor mode equations, observer hardware/damage, range geometry, background/noise and EW state.

## Compatibility

The ammunition JSON remains schema version 1. A legacy guided definition that omits `signature` receives `GuidedSignatureDefinition.zero()`.

This is deliberately conservative: old content without authored physical observability does not receive a hidden positive signature merely because Stage 19I began tracking missiles.

Current production/Stage-19I resources author their signatures explicitly.

## Provisional Stage-19I values

The current test-content source strengths are acceptance/calibration values, not final Stage-22 balance:

- 2 t anti-ship missile: RCS 0.65 m², thermal 1.5 MW, powered plume 250 MW;
- 750 kg interceptor: RCS 0.25 m², thermal 0.6 MW, powered plume 120 MW;
- production 1 t interceptor: RCS 0.30 m², thermal 0.8 MW, powered plume 150 MW.

Optical/radio source terms are also explicit in content. Current guided ammunition authors zero jammer emission; EW/decoy bodies will add their own authored states later.

## Fingerprint

All six physical signature terms participate in the ammunition semantic fingerprint. Changing missile observability is therefore a content-semantic change and cannot silently alter deterministic acceptance runs.

## Dynamic plume boundary

`enginePlumeRadiantPowerW` is authored here, but this slice does not yet decide when the plume is active in sensor projection. Stage 19I already tracks finite powered-burn lifetime and actual guidance burns; the next ordnance-observation runtime must compose the static body signature with actual current propulsion/emission state rather than assume an always-on engine.

## Next gate

Use these authored source strengths with the existing fitted sensor/engineering runtime to produce defender-local `SensorMeasurement` / `TrackState` for guided bodies. Only after that replacement should `LiveTacticalBattleDefenseRuntime` stop reading exact guided-body coordinates/velocity for assignment. EW/ECCM, decoy hypotheses and seeker/datalink degradation follow on top of that actor-bounded track domain.
