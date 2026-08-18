# Stage 19I-F — damaged / depleted 8v8 behavior gate

Status: implementation/acceptance slice after the green mixed 8v8 shared-runtime baseline.

## Purpose

The scaled tactical gate must prove that AI behavior changes because the ship's real physical capability changes. Merely showing lower derived statistics is insufficient.

This slice therefore starts the already accepted mixed 8v8 exact-local scenario with two deliberately degraded E-fit ships while otherwise equivalent E-fit peers on the same sides remain fresh:

- one ship enters with a fitted `utility_datalink` module at 10% integrity;
- one ship enters with all physical reaction-mass interface loads at zero;
- fresh E-fit peers retain their authored physical state.

No doctrine ID, target priority, weapon damage multiplier or abstract morale value is changed.

## Validated initial-state seam

`LiveTacticalInitialReadinessService` authors only physical state that already belongs to a materialized ship instance:

- `setModuleIntegrity(...)` validates that the mount is actually installed and writes the ordinary `ShipDamageRuntime.Snapshot` / `DamageState` input;
- `retainReactionMassFraction(...)` scales the current interface-bound reaction-mass amount and SI mass while preserving ammunition, cargo, stores, power/thermal state and all fitted modules.

The service does not calculate performance. Sensors, datalinks, mass, delta-v, acceleration and tactical readiness are re-derived through the ordinary production stack.

It is intentionally an initial-condition seam, not an alternative live damage API. In-battle hits continue to use the accepted shield/material/local-damage pipeline.

## Hard-zero maneuver readiness

The production `LiveTacticalBattleControlRuntime` survival policy previously used literal zero thresholds for reaction mass, delta-v and acceleration. Because `TacticalSurvivalPlanner` correctly tests readiness as "below threshold", a physically empty reserve could never cross a zero threshold.

This slice changes only the production hard-zero detector to the existing numerical `EPSILON` (`1e-9`) for:

- reaction mass;
- delta-v;
- acceleration.

This does **not** invent an operational reserve percentage or an arbitrary number of kilograms/meters-per-second. Any physically meaningful non-zero value behaves as before. It only makes canonical zero/unmaneuverable state survival-critical.

## Acceptance behavior

On the same first shared 8v8 control tick:

### Pre-damaged E-fit

The actor still acquires and selects an ordinary actor-local hostile target, proving that the survival result is not caused by missing enemy information.

Its 10% fitted subsystem integrity is below the existing 15% production minimum-module threshold. The survival layer therefore overrides the normal combat intent:

- action: `DISENGAGE` at the authored spawn/safe point where no non-zero retreat vector exists;
- reason: `SUBSYSTEM_DAMAGE`;
- fire authorization: false;
- no fabricated movement command.

### Fully reaction-mass-depleted E-fit

The physical reaction-mass state is zero, so derived delta-v is also non-operational. The EPSILON hard-zero policy enters the survival path, and `OwnReadiness.canManeuver()` prevents a fake retreat maneuver:

- action: `DISENGAGE`;
- reason: `CANNOT_MANEUVER`;
- fire authorization: false;
- zero physical velocity/displacement from the initial state.

### Fresh E-fit peers

Otherwise equivalent fresh E-fit combatants remain:

- `CONTINUE` / `READY`;
- actor-local target selected;
- ordinary non-zero intercept movement;
- real reaction-mass expenditure through the common engineering/flight runtime.

The same damaged/depleted 8v8 initial state and fixed tick schedule must also produce identical whole-runtime fingerprints.

## What this gate proves

The scaled live AI now responds to at least these real own-ship state changes without reading hidden enemy state:

- local subsystem integrity;
- complete propulsion-consumable depletion;
- resulting inability to create a physical maneuver.

This complements the earlier proof that combat impacts actually feed shield/armor/local-damage state back into derived capability.

## Boundary

This slice does not add ammunition, heat or power reserve fields to `TacticalSurvivalPlanner.OwnReadiness`. Those require separate design justification rather than being smuggled into the 8v8 gate.

Stage 19I remains open after this slice. Mandatory next scale work remains:

1. at least 32 simultaneous combatant ships on the same production exact-local runtime;
2. a dense saturation case containing guided, kinetic, interceptor and decoy bodies under EW;
3. profiling/diagnostics for fixed-tick cost and active body/sensing/collision counts;
4. scaled live/headless/read-only projection parity.
