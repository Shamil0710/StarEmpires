# Stage 20A.5 — Weapon / PD Spatial Calibration

**Status:** IMPLEMENTED — CI acceptance pending  
**Parent:** Stage 20A — Representative-Ship Scale Calibration  
**Authority boundary:** production Stage-17.5 weapon/defense runtimes + Stage-17.5I provisional combat-test content

## 1. Purpose

Stage 20A.5 connects production weapon execution to the spatial-scale calibration pipeline. It does not add weapon ranges, hit chances, PD bonuses or world-generation distances.

The machine-readable output is `Stage20WeaponSpatialCalibrationProfile`; `Stage20WeaponSpatialCalibrationCalculator` supplies deterministic probe geometry to existing production runtimes and records their physical results.

Canonical dependency chain:

```text
fitted engineering / authored ammunition
→ ShipWeaponEngineeringAdapter
→ WeaponFireControl / BeamWeaponRuntime / GuidanceRuntime
→ LayeredDefenseScheduler
→ Stage20WeaponSpatialCalibrationProfile
→ later Stage-20 world geometry / quality gates
```

No Stage-20 weapon equation duplicates the production combat model.

## 2. Kinetic calibration

Representative fixture: Stage-17.5I `A_KINETIC_LINE`, primary fitted kinetic mount, physical `ammo.test_kinetic_dart_150kg_v1`.

Production seams:

- `ShipWeaponEngineeringAdapter.deriveKineticMounts(...)` owns fitted round muzzle velocity, launcher state and pointing jitter;
- `WeaponFireControl.planKinetic(...)` owns lead solution, time of flight, propagated one-sigma aim uncertainty and target-maneuver envelope;
- projectile kinetic energy comes from the resulting production `KineticRound`.

Probe ranges are 300 km, 3,000 km and 10,000 km. The 3,000 km point deliberately covers the accepted heavy-direct-fire scale reference. These are sensitivity coordinates only, not weapon range limits.

Each range is measured twice:

- stationary deterministic target-motion control;
- controlled lateral-motion sensitivity probe at 1,000 m/s with 25 m/s velocity sigma and 0.5 m/s² bounded maneuver acceleration.

The second probe exists to expose how the production fire-control uncertainty/maneuver envelope grows with time of flight. Its motion numbers are not doctrine or balance constants.

## 3. Beam calibration

Representative fixture: Stage-17.5I `C_HIGH_MOBILITY_BEAM`, primary fitted beam emitter.

Production seams:

- `ShipWeaponEngineeringAdapter.deriveBeamMounts(...)` owns fitted optical/output/electrical/thermal definition;
- `BeamWeaponRuntime.plan(...)` owns diffraction, pointing, track-radius composition, effective spot size, dwell energy and mean irradiance.

Probe ranges are 300 km, 3,000 km, 10,000 km and 30,000 km with a one-second dwell or the fitted continuous-dwell limit if shorter.

The production beam runtime intentionally has no arbitrary hard range wall. Therefore Stage 20A.5 records continuous spot/irradiance degradation and **does not emit a maximum beam range**. A final target-dependent effectiveness boundary requires the appropriate target material/thermal response closure; Stage 20 does not invent one.

## 4. Guided-weapon calibration

Representative fixture: authored `ammo.test_anti_ship_missile_2t_v1`.

Production seams:

- `GuidedWeaponBody.launch(...)` creates the full physical body with real propellant and powered-burn lifetime;
- `GuidanceRuntime.planLeadPursuit(...)` owns lead/intercept navigation and terminal-reserve protection;
- `GuidanceRuntime.execute(...)` consumes real propellant and changes physical velocity through the production rocket model.

Probe ranges are 100 km, 300 km and 1,000 km. Each is measured against stationary and 1,000 m/s lateral target-motion inputs. The profile records predicted intercept horizon, initially deliverable delta-v, terminal reserve, first bounded burn and real propellant consumption.

These outputs are navigation/terminal-geometry evidence. They are not a universal missile maximum range and do not replace later full-flight/collision validation.

## 5. PD / interceptor calibration

The production Stage-19 defense path is layered guided interception. Stage 20A.5 therefore probes `LayeredDefenseScheduler.scheduleObserved(...)` with actor-bounded observed threat kinematics rather than reading hidden threat truth.

Representative interceptor: `ammo.test_interceptor_750kg_v1`.

Controlled geometry:

```text
protected-zone radius       1,500 m
station offset              12,000 m
inbound closing speed        2,500 m/s
threat start ranges         50 / 100 / 300 km
safe-distance probes         0 / 5 km
```

For accepted assignments the profile records ballistic impact time, planned intercept time and intercept distance from the protected center. The regression gate requires every assignment to occur no later than predicted impact and outside `max(protected radius, configured safe minimum)`.

The 5 km safe-distance value is inherited from the existing Stage-17.5I acceptance fixture as a **probe input**. It is not promoted to canonical PD doctrine. The scheduler currently accepts safe minimum distance as policy state; a fragmentation/blast/debris model does not yet derive that distance physically. This remains machine-visible unresolved closure.

## 6. Fire-control quality conclusion

Stage 20A.4 correctly kept `TrackQualityPolicy.defaultPolicy()` provisional. Stage 20A.5 shows why a single global final accuracy constant would be wrong:

- kinetic uncertainty amplification depends on time of flight, pointing jitter, track covariance, velocity uncertainty and maneuver horizon;
- beam exposure geometry depends continuously on target range and track-position covariance;
- guided weapons accept TRACKED/FIRE_CONTROL position solutions but propulsion/terminal reserve determines whether navigation can act;
- layered defense depends on observed target kinematics, threat time-to-impact and interceptor reachability.

Therefore Stage 20A.5 does **not** replace the provisional sensor policy with one universal sigma/age threshold. Later integration must derive weapon/target-specific admissibility from these physical envelopes. The provisional policy remains visible rather than silently becoming a balance constant.

## 7. Explicit unresolved closures

The profile records these gaps:

1. beam runtime has no hard range wall; target-material response is needed for a target-dependent effectiveness boundary;
2. safe-intercept distance is scheduler input until fragmentation/blast/debris physics can derive it;
3. the current range-owning layered-defense scheduler represents guided interceptors; there is no separate kinetic-PD range scheduler to calibrate as an independent domain;
4. final TRACKED/FIRE_CONTROL age/covariance admissibility is weapon-, target- and motion-dependent and must not become a single global threshold.

None of these gaps is hidden with an arbitrary Stage-20 constant.

## 8. Determinism / acceptance

`Stage20WeaponSpatialCalibrationProfileTest` requires:

- identical content + profile version to produce identical output;
- kinetic time of flight to grow with probe range;
- maneuver envelope to grow with the longer production prediction horizon;
- beam spot radius to grow continuously while irradiance falls, without a hard range wall;
- guided commands to consume physical propellant while preserving terminal reserve policy;
- layered-defense assignments to respect configured safe-intercept geometry and impact timing;
- every sample to retain its production runtime provenance.

## 9. Next Stage-20A slice

After 20A.5 acceptance, Stage 20A should continue with formation spacing and station physical footprint/spacing, then jump-arrival stand-off, far-coordinate precision and materialization/LOD calibration before Stage 20B begins star-system physical geometry generation.
