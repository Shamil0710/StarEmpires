# Stage 19I-B — actor-bounded multi-combatant runtime roster

**Status:** implementation sub-slice toward symmetric production 4v4 execution.

This slice materializes the authored `LiveTacticalBattleScenario` into one deterministic local battle runtime without duplicating the combat engine.

## 1. Runtime ownership added

`LiveTacticalBattleRuntimeState` now owns:

- one independent production `TransformComponent` per authored combatant;
- canonical combatant iteration by stable entity identity;
- one separate actor-visible `ObservedContact` collection per combatant.

The runtime does not yet own or duplicate:

- engineering runtime state;
- ammunition or reaction mass;
- damage / shields;
- weapon fire-control state;
- projectile or missile resolution;
- tactical outcome logic.

Those production systems remain to be composed into each materialized combatant in the next sub-slice.

## 2. Information isolation

The contact registry is intentionally observer-scoped. Updating one combatant's observed contacts cannot modify another combatant's tactical information domain.

Contacts are supplied only after the production observation/track pipeline has produced them. The registry never creates, upgrades or infers a track.

For each observer:

- contacts are canonicalized by stable target ID;
- duplicate target IDs are rejected;
- self-target contacts are rejected;
- unknown observer identities are rejected.

This is the minimum structural requirement for 4v4 AI to avoid a single omniscient shared enemy list.

## 3. Physical-state boundary

Each materialized combatant receives an independent mutable `TransformComponent` initialized from its authored spawn coordinates. Mutating one transform cannot change any other combatant.

This establishes the multi-combatant physical-position seam that later production flight integration will consume.

## 4. Acceptance

`LiveTacticalBattleRuntimeStateTest` proves:

1. the 4v4 scenario materializes eight combatants in canonical order;
2. authored spawn coordinates become physical transforms;
3. transforms are independent between combatants;
4. actor-visible contacts do not leak to another same-side combatant;
5. visible contacts are canonical by target identity;
6. duplicate/self contacts are rejected;
7. unknown combatants cannot read or mutate an information domain.

## 5. Next sub-slice

The next Stage-19I-B implementation must compose production per-combatant physical state into this roster:

```text
CombatantSpec
+ TransformComponent
+ Stage-17.5 InstalledFit / engineering runtime
+ finite consumables
+ damage snapshot
+ actor-visible TrackState collection
→ production tactical intent / survival policy
→ physical flight + fire execution
```

The first integration target remains one shared **4v4** headless session. It must allow all eight combatants to sense, decide, move and consume their own physical resources through the same production systems.

Four independent `LiveTacticalSimulationSession` duels are not an acceptable substitute.
