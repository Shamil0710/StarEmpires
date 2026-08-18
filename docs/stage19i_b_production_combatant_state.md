# Stage 19I-B — production combatant state materialization

**Status:** implementation slice. This is not the Stage-19 exit gate and does not yet claim a live 4v4 battle.

## Purpose

The Stage-19I multi-combatant runtime must not represent fleet ships as IDs and transforms while continuing to borrow one duel's engineering state. Every materialized combatant now owns the production physical state required for later shared sensing, AI, movement and fire execution.

## Authoritative composition

For every `LiveTacticalBattleScenario.CombatantSpec`, `LiveTacticalBattleRuntimeState` resolves:

1. the acceptance doctrine fixture selected by `doctrineId`;
2. the ordinary production-valid installed fit referenced by that doctrine;
3. the production hull definition;
4. the production protection/damage layout;
5. a pristine `ShipDamageRuntime.Snapshot` for that hull/layout;
6. a damage-aware `ShipEngineeringRuntime.RuntimeState` initialized from the doctrine's physical consumables;
7. fitted shield emitters projected through `ShipShieldEngineeringAdapter` and initialized as charged physical shield state;
8. the doctrine's physical weapon-feed identity in `ShipInstanceRuntimeState`;
9. an independent `EngineeringComponent` and `TransformComponent` for the physical combatant.

Doctrine remains content selection only. It grants no combat scalar, hidden accuracy, free thrust, free ammunition, shield bonus or damage modifier.

## Ownership boundaries

`LiveTacticalBattleRuntimeState` owns battle-local mutable composition only. It does not replace the existing production owners:

- propulsion, power, thermal state and reaction mass remain in `ShipEngineeringRuntime.RuntimeState`;
- ammunition quantity remains in `ConsumableState`;
- damage remains in `ShipDamageRuntime.Snapshot`;
- shields remain in `ShipInstanceRuntimeState.shieldStatesByMount`;
- weapon-feed identity and launcher continuity remain in `ShipInstanceRuntimeState`;
- movement remains in `TransformComponent` / `FlightDynamics`;
- observed targets remain separate actor-bounded `ObservedContact` domains.

Derived mass, acceleration, delta-v, sensor capability and weapon capability are not persisted as fleet-runtime bonuses. They must be recomputed from the physical state by the ordinary production calculators/adapters.

## Acceptance requirements for this slice

The balanced 4v4 roster must materialize eight combatants such that:

- scenario and runtime iteration remain stable-entity ordered;
- every combatant has an independent transform;
- every combatant has an independent `EngineeringComponent`, operating runtime and instance state;
- the installed fit and hull match the selected doctrine content;
- initial physical consumables exactly equal the doctrine-authored stores;
- reaction mass is finite and physically present;
- ammunition is finite and physically present for the balanced-control fixture;
- local damage starts pristine through the production hull/layout pair;
- fitted shield state is materialized and charged through the production shield adapter/runtime;
- weapon-feed identity comes from the doctrine's physical loadout;
- actor-visible contact domains remain isolated between combatants.

## What this does not prove

This slice does **not** yet prove that all eight ships sense, choose targets, maneuver and fire in one shared tick loop. It also does not prove 8v8, 32+ combatants, saturation, EW/decoys, retreat or performance.

## Next Stage-19I-B slice

The next slice must add one fixed-tick multi-combatant execution loop over this runtime state:

```text
per-combatant production observation
→ actor-local TrackState / ObservedContact
→ production tactical + survival policy
→ engineering command / finite reaction mass
→ shared FlightDynamics integration
→ later fire-control and physical-body resolution
```

The first acceptance target is a single shared 4v4 headless session. Four independent 1v1 simulations remain forbidden.
