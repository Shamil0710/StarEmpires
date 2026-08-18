# Stage 19I-B — shared multi-combatant control tick

**Status:** implementation slice. This is a production control/flight gate, not yet complete 4v4 combat acceptance.

## Purpose

The first eight-combatant roster and per-combatant physical state are not sufficient if each ship is still advanced through unrelated duel logic. This slice introduces one canonical fixed-tick control coordinator over the complete battle state.

`LiveTacticalBattleControlRuntime` is not a second combat engine. It coordinates existing production owners only:

```text
LiveTacticalBattleRuntimeState
→ production fitted sensors / signatures
→ ShipObservationEngineeringService + shared per-ship IntervalBudget
→ ShipSensorRuntime TrackState fusion
→ actor-bounded ObservedContact domain
→ ObservedTacticalIntentPlanner
→ TacticalSurvivalPlanner
→ ShipEngineeringRuntime
→ finite reaction mass / power / heat
→ FlightDynamics
```

Projectile/guided-body, point-defense, EW/decoy and damage resolution are deliberately not reimplemented in this class. Those capabilities remain in the existing live combat stack and must be integrated into the same multi-combatant authority chain in later Stage-19I slices.

## Tick ordering

Every shared fixed tick executes in three phases:

1. **Sense all observers.** Every observer uses start-of-tick physical geometry. Sensor operations for one ship share one production `IntervalBudget`, preventing multiple target observations from spending the same power/storage headroom independently.
2. **Plan all actors.** Tactical and survival decisions consume only the observer's resulting `ObservedContact` list and own authoritative readiness. No actor reads hidden target hull/fit/ammunition/transform state for target selection.
3. **Move all actors.** Engineering resolves actual thrust and consumes physical reaction mass before the shared `FlightDynamics` integrator advances transforms.

Planning all actors before moving any actor prevents stable-entity iteration order from giving later actors newer within-tick geometry.

## Information boundary

The authored battle side is treated as known hostile disposition for this acceptance scenario only. A target becomes a tactical contact only after production sensing has produced measurement history and `ShipSensorRuntime` has fused it into `TrackState`.

Absence from an observer's measured/fused domain means absence from its tactical planner input.

## Acceptance for this slice

For `balanced4v4()`:

- all eight combatants advance through one coordinator and one fixed tick clock;
- each combatant obtains only actor-local hostile tracks;
- selected tactical target must exist in that actor's visible `TrackState` domain;
- AI movement intent changes production transforms through `FlightDynamics`;
- movement consumes the corresponding combatant's finite physical reaction mass;
- read-only queries cannot advance battle state;
- two independently materialized identical 4v4 battles run for the same number of fixed ticks to byte/equality-equivalent control fingerprints.

## Not yet accepted

This slice does not yet prove:

- physical weapon fire by all eight ships;
- missiles/guidance and dense ordnance;
- point defense/interceptors;
- EW/ECCM/decoys;
- multi-ship shield/armor/subsystem damage;
- damaged/ammunition-depleted tactical behavior;
- retreat/disengagement objective completion;
- 8v8, 32+ combatants or saturation performance.

## Next Stage-19I slice

The next integration target is **shared multi-combatant weapon execution**. It must consume each combatant's existing `ActorControlState.fireAuthorized`, actor-local selected `TrackState`, physical weapon loadout, launcher cooldown and ammunition stores, then materialize ordinary production projectile/guided bodies into one shared battle body set.
