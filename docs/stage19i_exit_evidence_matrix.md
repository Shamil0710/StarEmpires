# Stage 19I — final exit evidence matrix

> Status: **DRAFT FINAL CLOSEOUT**  
> This record is promoted to COMPLETE only after the final Stage-19 closeout PR passes exact-head Java 17 `clean verify` and is merged.

## 1. Purpose

This document maps the mandatory requirements from `docs/stage19_scaled_live_tactical_ai_acceptance.md` to production implementations and acceptance evidence.

No requirement is considered satisfied merely because a smaller unit test exists. The final gate requires the production tactical stack, scaled deterministic execution, finite physical resources, live/headless parity, and absence of hidden large-battle shortcuts.

## 2. Authority chain

Accepted production chain:

```text
scenario physical state
→ actor-bounded tactical AI
→ TrackState / observed ordnance tracks
→ production engineering budgets
→ FlightDynamics
→ fire control
→ kinetic / beam / guided weapon execution
→ PD / interceptor / EW / decoy behavior
→ shield / material / compartment / subsystem damage
→ changed physical capability and tactical decisions
→ next fixed tick
→ read-only live projection
```

Evidence families:

- `LiveTacticalSimulationSessionTest`
- `LiveTacticalBattleControlRuntimeTest`
- `LiveTacticalBattleWeaponRuntimeTest`
- `LiveTacticalBattleOrdnanceRuntimeTest`
- `LiveTacticalBattleDefenseRuntimeTest`
- `LiveTacticalBattleDeceptionRuntimeTest`
- `ScaledLiveTacticalSimulationParityTest`
- `ScaledLiveTacticalExitToolingAcceptanceTest`

## 3. Mandatory scale ladder

| Requirement | Evidence | Status |
| --- | --- | --- |
| 1v1 production tactical authority | `LiveTacticalSimulationSessionTest`, Stage19I-A integration | PASS |
| shared 4v4 | `LiveTacticalBattleControlRuntimeTest`, `LiveTacticalFormationAcceptanceTest` | PASS |
| mixed 8v8 | `LiveTactical8v8ExactLocalAcceptanceTest` | PASS |
| damaged/depleted 8v8 | `LiveTacticalDamagedDepleted8v8AcceptanceTest` | PASS |
| >=32 exact-local ships | `LiveTactical32ShipExactLocalAcceptanceTest` | PASS |
| dense finite-resource saturation | `LiveTacticalSaturationProfilingAcceptanceTest` | PASS |

The 32-ship saturation case uses the same `Stage19ScaledLiveTacticalFactory` for live and headless execution.

## 4. Information and tactical behavior

| Requirement | Evidence | Status |
| --- | --- | --- |
| actor-bounded target selection | `ObservedThreatAssessmentServiceTest`, `LiveTacticalBattleControlRuntimeTest`, 8v8/32-ship acceptance | PASS |
| no fire without valid hostile track | `ObservedTacticalIntentPlannerTest`, `LiveTacticalBattleWeaponRuntimeTest` | PASS |
| degraded/lost ordnance tracks | `LiveTacticalOrdnanceTrackContinuityTest` | PASS |
| physical jammer/ECCM consequences | `LiveTacticalOrdnanceElectronicWarfareTest`, `ShipSensorElectronicWarfareTest` | PASS |
| passive formation sensing through fitted datalink | `LiveTacticalFormationOrdnanceObservationTest`, `ShipDatalinkEngineeringAdapterTest` | PASS |
| physical decoy diversion | `LiveTacticalDecoySensingDefenseTest`, `LiveTacticalBattleDeceptionRuntimeTest` | PASS |
| target distribution at scale | `LiveTactical32ShipExactLocalAcceptanceTest` requires multiple distinct selected targets per side | PASS |
| pursuit/disengagement | `TacticalSurvivalPlannerTest`, Stage19C acceptance | PASS |
| authored withdrawal objective | `LiveTacticalExitBehaviorMatrixAcceptanceTest` | PASS |
| formation keeping/break/recovery | `TacticalFormationPlannerTest`, `LiveTacticalFormationAcceptanceTest` | PASS |
| compact/dispersed formation variants | `LiveTacticalFormationAcceptanceTest` | PASS |
| tick-level anti-churn / no immediate A-B-A ping-pong | `LiveTacticalExitBehaviorMatrixAcceptanceTest` | PASS |

## 5. Physical resource and damage consequences

| Requirement | Evidence | Status |
| --- | --- | --- |
| finite ammunition | weapon/guided/defense runtimes and itemized feed accounting | PASS |
| partial-ammunition start | `LiveTacticalExitBehaviorMatrixAcceptanceTest` | PASS |
| ammunition depletion changes AI | `LiveTacticalResourceConstraintAcceptanceTest` | PASS |
| reaction-mass depletion changes AI | `LiveTacticalDamagedDepleted8v8AcceptanceTest` | PASS |
| power denial changes AI through engineering→sensor→track | `LiveTacticalResourceConstraintAcceptanceTest` | PASS |
| thermal saturation changes AI through the same path | `LiveTacticalResourceConstraintAcceptanceTest` | PASS |
| pre-damage changes AI | `LiveTacticalDamagedDepleted8v8AcceptanceTest` | PASS |
| shield/material/compartment/subsystem chain | `LiveTacticalBattleGuidedImpactAcceptanceTest`, `Stage175IFullChainDestructionAcceptanceTest` | PASS |
| interceptor rounds/cooldowns/support channels are finite | `LiveTacticalBattleDefenseRuntimeTest`, saturation acceptance | PASS |
| decoys are finite physical bodies | `LiveTacticalBattleDecoyRuntimeTest`, deception acceptance | PASS |

Final closeout additionally requires explicit coverage of the full 2,000 kg authored strike-missile material-response envelope; no silent response-surface extrapolation is permitted.

## 6. Live/headless/tooling

| Requirement | Evidence | Status |
| --- | --- | --- |
| same scenario/runtime for live and headless | `Stage19ScaledLiveTacticalFactory`, parity tests | PASS |
| read-only projection | `ScaledLiveTacticalSimulationParityTest` | PASS |
| pause/resume | `ScaledLiveTacticalExitToolingAcceptanceTest` | PASS |
| exact single-step | same | PASS |
| deterministic reset | same | PASS |
| X1/X2/X4/X8 fixed-tick batching only | same | PASS |
| debug inspection of tracks/intent/target/formation/weapons/resources/damage/AI | scaled debug snapshot + desktop viewer | PASS |
| runnable scaled viewer | `--scaled-live-tactical-sim` | PASS |
| render/projection reads do not mutate authority | parity/tooling tests | PASS |

## 7. Performance evidence

Representative 32-ship / 240-tick saturation evidence from the Stage19I-M verification line:

- 32 active ships;
- peak 208 simultaneous non-ship bodies;
- peak 164 kinetic bodies;
- peak 31 STRIKE guided bodies;
- peak 15 interceptor bodies;
- peak 4 decoys;
- all four body classes concurrent;
- 7,680 tactical AI decisions;
- 122,880 cumulative ship-track hypotheses;
- 106,944 cumulative ordnance-track hypotheses;
- 2,940 kinetic shots;
- 32 guided launches;
- 16 interceptor launches;
- 4 decoy deployments;
- 2,969 protection impacts;
- 4 physical interceptions.

Hosted-runner wall-clock measurements are diagnostic rather than balance constants. The recorded Stage19I-M run produced roughly 90.5 authoritative ticks/s, ~11.0 ms mean tick, ~25.4 ms p95 tick and bounded sampled heap behavior. Hardware thresholds remain a later representative-hardware calibration concern; no wall-clock value enters authoritative fingerprints.

## 8. Explicit no-shortcut audit

The accepted Stage-19 runtime contains no:

- aggregate fleet-DPS replacement for visible local combat;
- player-only combat rules;
- doctrine/class numeric combat multipliers;
- virtual ammunition, reaction mass, interceptors or decoys;
- viewer-owned movement, targeting, collision or damage;
- omniscient hostile-transform lookup for tactical target selection;
- faction-based physical projectile intangibility;
- hidden production penalty in place of physical interdiction/logistics damage.

## 9. Final closeout blockers

Before this record can be marked **COMPLETE**, the final closeout PR must prove all of the following on one exact green head:

1. fitted beam weapons are executed by the shared Stage-19 tactical AI / engineering / fire-control runtime rather than remaining only an isolated Stage-17.5 primitive;
2. the authored 2,000 kg strike missile is inside an explicit heavy-impact calibration domain whenever material protection is reached;
3. the existing ship-impact-before-interceptor tick ordering has explicit scaled evidence showing zero earlier interceptor contacts lost to that ordering, or is replaced by a unified earliest-event resolver;
4. this evidence matrix and the canonical development roadmap are synchronized;
5. Java 17 `./mvnw --batch-mode --no-transfer-progress clean verify` is green on the exact final PR head.

Only then may Stage 19 be marked COMPLETE and Stage 20 become NEXT.
