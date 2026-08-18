# Stage 19I-D — Actor-Bounded Guided-Ordnance Tracks

## Status

Implementation acceptance slice for the mandatory Stage-19 scaled live tactical AI exit gate.

## Purpose

Remove the temporary exact-local missile-state bridge from layered-defense policy and interceptor datalink guidance.

The authoritative physical guided body remains in the simulation for propagation and collision, but defense decisions must be driven by the same information-model boundary used elsewhere:

```text
physical guided body
→ authored physical signature
→ fitted/damaged production radar
→ engineering power/heat grant
→ SensorMeasurement
→ observer-local TrackState
→ velocity inferred from successive observed positions
→ LayeredDefenseScheduler
→ finite interceptor launch
→ datalink guidance from observed kinematics
→ physical swept body-body collision
```

## Accepted ownership boundary

- `LiveTacticalBattleOrdnanceRuntime` owns physical strike bodies and battle time.
- `LiveTacticalOrdnanceObservationRuntime` owns no physical bodies and advances no combat clock.
- radar observability uses authored guided-ammunition `SignatureState`, production sensor equations and ordinary engineering grants.
- one radar operation is granted once per scan and evaluated against all current hostile guided bodies; transmitter power is not multiplied per target.
- ordnance scans are phase-shifted from the existing ship-target scan cadence.
- each combatant receives a separate measurement history and separate ordnance tracks.
- target velocity is unknown after only one Cartesian solution and is inferred only after a temporally distinct second observed solution.
- `LayeredDefenseScheduler.scheduleObserved(...)` accepts only observed identity/position/velocity; it receives no target mass, guidance health, source fit or hidden capability.
- interceptor datalink guidance consumes the observer-local `TrackState` and observed velocity estimate.
- exact guided-body geometry remains available only to physical collision resolution.

## Acceptance invariants

- a physically present missile is not actionable merely because it exists in authoritative state;
- one observed position cannot manufacture exact velocity;
- defense requires TRACKED/FIRE_CONTROL quality, solved position and observed velocity;
- destroyed fitted radar produces no ordnance track and therefore no interceptor launch;
- interceptor ammunition, launcher cooldowns, support channels, propulsion and collision remain physical and finite;
- no interception probability or hidden defensive bonus is introduced;
- deterministic replay remains byte/equality stable through the existing defense fingerprint.

## Deliberate remaining Stage-19I-D work

This slice uses ACTIVE_RADAR and an empty target EW environment. Stage 19 still requires:

- physical EW jammer state routed into ordnance observation;
- ECCM effects through fitted sensor processing;
- explicit deception/decoy hypotheses and association behavior;
- degraded/lost ordnance-track behavior under EW and sensor damage;
- eventual passive thermal/plume/optical/radio ordnance observation where production policy needs it;
- scaled saturation evidence.

## Validation

The exact PR head must pass the repository Java 17 `clean verify` workflow before merge.
