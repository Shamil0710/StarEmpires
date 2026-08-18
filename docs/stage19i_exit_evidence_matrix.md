# Stage 19I — final exit evidence matrix

> Status: **COMPLETE — STAGE 19 EXIT REQUIREMENTS SATISFIED**  
> Canonical contract: `docs/stage19_scaled_live_tactical_ai_acceptance.md`.  
> Stage 20 may begin only from a green final Stage-19 closeout head.

## 1. Purpose

This document maps the mandatory Stage-19 scaled-live requirements to production implementations and acceptance evidence.

No requirement is considered satisfied merely because an isolated primitive or unit test exists. The accepted gate requires the production tactical stack, scaled deterministic execution, finite physical resources, live/headless parity, bounded behavior, profiling evidence and absence of hidden large-battle shortcuts.

## 2. Accepted authority chain

```text
scenario physical state
→ actor-bounded tactical AI
→ TrackState / observed ordnance tracks
→ production engineering power / heat / reaction-mass budgets
→ production FlightDynamics
→ production fire control
→ physical kinetic / beam / guided execution
→ PD / interceptor / EW / decoy behavior
→ shield / material / compartment / subsystem damage
→ changed physical capability and tactical decisions
→ next fixed tick
→ read-only live projection
```

Representative production/evidence families:

- `LiveTacticalSimulationSessionTest`;
- `LiveTacticalBattleControlRuntimeTest`;
- `LiveTacticalBattleWeaponRuntimeTest`;
- `LiveTacticalBattleOrdnanceRuntimeTest`;
- `LiveTacticalBattleDefenseRuntimeTest`;
- `LiveTacticalBattleDeceptionRuntimeTest`;
- `LiveTacticalBeamIntegrationAcceptanceTest`;
- `ScaledLiveTacticalSimulationParityTest`;
- `ScaledLiveTacticalExitToolingAcceptanceTest`.

## 3. Mandatory scale ladder

| Requirement | Evidence | Status |
| --- | --- | --- |
| 1v1 production tactical authority | `LiveTacticalSimulationSessionTest`, Stage19I-A integration | **PASS** |
| shared 4v4 | `LiveTacticalBattleControlRuntimeTest`, `LiveTacticalFormationAcceptanceTest` | **PASS** |
| mixed 8v8 | `LiveTactical8v8ExactLocalAcceptanceTest` | **PASS** |
| damaged/depleted 8v8 | `LiveTacticalDamagedDepleted8v8AcceptanceTest` | **PASS** |
| >=32 exact-local ships | `LiveTactical32ShipExactLocalAcceptanceTest` | **PASS** |
| dense finite-resource saturation | `LiveTacticalSaturationProfilingAcceptanceTest` | **PASS** |

The 32-ship live viewer and headless profiler both use `Stage19ScaledLiveTacticalFactory`; there is no second simplified fleet-combat setup or aggregate battle resolver.

## 4. Information and tactical behavior

| Requirement | Evidence | Status |
| --- | --- | --- |
| actor-bounded target selection | `ObservedThreatAssessmentServiceTest`, shared 4v4/8v8/32-ship acceptance | **PASS** |
| no fire without valid hostile track | `ObservedTacticalIntentPlannerTest`, `LiveTacticalBattleWeaponRuntimeTest` | **PASS** |
| degraded/lost ordnance tracks | `LiveTacticalOrdnanceTrackContinuityTest` | **PASS** |
| physical jammer/ECCM consequences | `LiveTacticalOrdnanceElectronicWarfareTest`, `ShipSensorElectronicWarfareTest` | **PASS** |
| passive formation sensing through fitted datalink | `LiveTacticalFormationOrdnanceObservationTest`, `ShipDatalinkEngineeringAdapterTest` | **PASS** |
| physical decoy diversion | `LiveTacticalDecoySensingDefenseTest`, `LiveTacticalBattleDeceptionRuntimeTest` | **PASS** |
| target distribution at scale | `LiveTactical32ShipExactLocalAcceptanceTest` | **PASS** |
| pursuit/disengagement | `TacticalSurvivalPlannerTest`, Stage19C acceptance | **PASS** |
| authored withdrawal objective | `LiveTacticalExitBehaviorMatrixAcceptanceTest` | **PASS** |
| formation keeping/break/recovery | `TacticalFormationPlannerTest`, `LiveTacticalFormationAcceptanceTest` | **PASS** |
| compact/dispersed formation variants | `LiveTacticalFormationAcceptanceTest` | **PASS** |
| tick-level anti-churn / no immediate A→B→A ping-pong | `LiveTacticalExitBehaviorMatrixAcceptanceTest` | **PASS** |
| fitted beam execution through shared actor-local fire control | `LiveTacticalBeamIntegrationAcceptanceTest` | **PASS** |

### Beam boundary

Stage 19 integrates fitted directed-energy emitters into the same actor-local target selection and engineering authority as other weapons:

```text
selected TrackState
→ fire authorization
→ BeamWeaponRuntime geometry / dwell / spot
→ shared engineering interval admission
→ real incremental electrical energy + local heat
→ deterministic physical target-plane exposure
```

Stage-17.5/19 content does **not** author optical absorption, ablation or laser-specific armor response. Stage 19 therefore does not invent a hidden beam-DPS coefficient or bypass calibrated protection. That content-physics extension belongs to later explicit content/material authoring, principally Stage 22.

## 5. Physical resource and damage consequences

| Requirement | Evidence | Status |
| --- | --- | --- |
| finite ammunition | weapon/guided/defense runtimes and itemized feed accounting | **PASS** |
| partial-ammunition start | `LiveTacticalExitBehaviorMatrixAcceptanceTest` | **PASS** |
| ammunition depletion changes AI | `LiveTacticalResourceConstraintAcceptanceTest` | **PASS** |
| reaction-mass depletion changes AI | `LiveTacticalDamagedDepleted8v8AcceptanceTest` | **PASS** |
| power denial changes AI through engineering→sensor→track | `LiveTacticalResourceConstraintAcceptanceTest` | **PASS** |
| thermal saturation changes AI through the same path | `LiveTacticalResourceConstraintAcceptanceTest` | **PASS** |
| pre-damage changes AI | `LiveTacticalDamagedDepleted8v8AcceptanceTest` | **PASS** |
| shield/material/compartment/subsystem chain | `LiveTacticalBattleGuidedImpactAcceptanceTest`, `Stage175IFullChainDestructionAcceptanceTest` | **PASS** |
| interceptor rounds/cooldowns/support channels are finite | `LiveTacticalBattleDefenseRuntimeTest`, saturation acceptance | **PASS** |
| decoys are finite physical bodies | `LiveTacticalBattleDecoyRuntimeTest`, deception acceptance | **PASS** |
| authored 2,000 kg strike-missile material-response envelope | `HeavyImpactResidualCalibrationTest` | **PASS** |

### 2 t strike calibration closure

The historical Stage-17.5 doctrine response surface ended at 1,500 kg while the already-authored strike missile has 2,000 kg wet mass. Stage 19 closes that gap explicitly rather than extrapolating:

- only `response.stage17_5i_doctrine_v1` is promoted;
- maximum admitted projectile mass becomes exactly **2,000 kg**;
- velocity bounds and all other content stay unchanged;
- confidence is explicitly `stage19_strike_2t_provisional_test_only`;
- 2,000 kg resolves inside the domain;
- 2,001 kg remains rejected by `OutsideCalibrationDomainException`.

The promotion is intentionally provisional and remains subject to Stage-22 content/material review.

## 6. Guided ship-impact / interceptor ordering evidence

The accepted runtime currently resolves guided ship impact inside the ordnance phase before the outer interceptor-contact phase. Stage 19 does not hide this ordering. `Stage19GuidedImpactOrderingAudit` is a deterministic read-only audit that:

1. snapshots already-active STRIKE/interceptor bodies and ship positions at tick start;
2. reproduces the same actor-bounded guidance equations;
3. reproduces swept moving-ship AABB contact fractions;
4. reproduces swept moving-body contact with the same circumscribed radius as production interception;
5. records any interceptor contact physically earlier than a ship impact that would have been suppressed by phase ordering.

`Stage19GuidedImpactOrderingAuditTest` proves the detector is non-vacuous with an artificial earlier body-body crossing.

The final 32-ship / 240-tick saturation technical verification observed:

- **12** previously-active guided ship-impact candidates;
- **0** physically earlier interceptor contacts suppressed by current ordering.

`LiveTacticalSaturationProfilingAcceptanceTest` requires both a non-zero candidate sample and zero ordering ambiguities. A future content/geometry change that violates this invariant turns the test red rather than silently changing physical outcomes.

## 7. Live/headless/tooling

| Requirement | Evidence | Status |
| --- | --- | --- |
| same scenario/runtime for live and headless | `Stage19ScaledLiveTacticalFactory`, parity tests | **PASS** |
| read-only projection | `ScaledLiveTacticalSimulationParityTest` | **PASS** |
| pause/resume | `ScaledLiveTacticalExitToolingAcceptanceTest` | **PASS** |
| exact single-step | same | **PASS** |
| deterministic reset | same | **PASS** |
| X1/X2/X4/X8 fixed-tick batching only | same | **PASS** |
| debug inspection of tracks/intent/target/formation/weapons/resources/damage/AI | scaled debug snapshot + desktop viewer | **PASS** |
| runnable scaled viewer | `--scaled-live-tactical-sim` | **PASS** |
| render/projection reads do not mutate authority | parity/tooling tests | **PASS** |

## 8. Performance / scalability evidence

Final technical closeout profile, 32 ships / 240 authoritative ticks:

- active ships: **32**;
- tactical AI decisions: **7,680**;
- cumulative ship-track hypotheses: **122,880**;
- cumulative ordnance-track hypotheses: **106,944**;
- peak ship-track hypotheses: **512**;
- peak ordnance-track hypotheses: **576**;
- peak kinetic bodies: **164**;
- peak STRIKE guided bodies: **31**;
- peak interceptor bodies: **15**;
- peak decoys: **4**;
- peak simultaneous non-ship bodies: **208**;
- all four body classes concurrent: **yes**;
- kinetic shots: **2,940**;
- guided launches: **32**;
- interceptor launches: **16**;
- decoy deployments: **4**;
- protection impacts: **2,969**;
- physical interceptions: **4**.

Hosted-runner diagnostics for the technical closeout run:

- ~**85.5 authoritative ticks/s**;
- mean ~**11.65 ms/tick**;
- p95 ~**26.16 ms/tick**;
- max ~**28.44 ms/tick**;
- peak sampled used heap ~**370 MB**.

These measurements are diagnostics, not authoritative constants or invented target-hardware thresholds. Wall time and heap never enter simulation state/fingerprints. Representative-hardware tuning remains a later optimization/content-calibration concern, but the measured Stage-19 scale is acceptable for proceeding to Stage-20 spatial calibration.

## 9. Explicit no-shortcut audit

The accepted Stage-19 runtime contains no:

- aggregate fleet-DPS replacement for visible local combat;
- player-only combat rules;
- doctrine/class numeric combat multipliers;
- virtual ammunition, reaction mass, interceptors or decoys;
- viewer-owned movement, targeting, collision or damage;
- omniscient hostile-transform lookup for tactical target selection;
- faction-based physical projectile intangibility;
- hidden production penalty in place of physical interdiction/logistics damage;
- uncalibrated material-response extrapolation for the authored 2 t strike round;
- invented laser armor-DPS coefficient.

## 10. Final verification

The final technical closeout head passed Java 17:

```text
./mvnw --batch-mode --no-transfer-progress clean verify
```

Technical closeout result before the final status-only synchronization commit:

- **1,141 tests**;
- **0 failures**;
- **0 errors**;
- **0 skipped**;
- Javadocs: PASS;
- JaCoCo thresholds: PASS;
- desktop/package build: PASS.

The final merge gate repeats the same full command on the exact status-synchronized PR head. Any red result reopens Stage 19.

## 11. Exit decision

All mandatory rows of `docs/stage19_scaled_live_tactical_ai_acceptance.md` have direct production evidence.

**Stage 19 is accepted as COMPLETE once this status-synchronized closeout head passes the final exact-head Java-17 verification and is merged. Stage 20 — Physical World Generation / Discovery — is NEXT.**
