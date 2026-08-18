# Stage 19I-B — multi-combatant scenario foundation

**Status:** implementation foundation for the symmetric 4v4 Stage-19I acceptance slice.

Stage 19I-B begins removing the hard-coded one-attacker / one-target assumption from the live tactical acceptance path. This first sub-slice introduces a deterministic authored battle roster that can describe both the existing duel and the first symmetric 4v4 scenario without owning or duplicating physical combat state.

## 1. New scenario ownership

`LiveTacticalBattleScenario` owns only:

- stable combatant entity identity;
- battle side;
- doctrine/content selection;
- initial local spawn coordinates.

It explicitly does **not** own:

- hull/module statistics;
- ammunition or reaction mass;
- sensors or TrackState;
- movement integration;
- target selection;
- shields, armor or damage;
- combat outcomes.

Those remain production Stage-17.5 / Stage-19 runtime state when a combatant is materialized.

## 2. Determinism rules

The scenario roster:

- requires at least two combatants;
- requires both `ALPHA` and `BETA` sides;
- rejects duplicate or non-positive entity IDs;
- rejects non-finite coordinates;
- canonically orders combatants by stable entity ID;
- exposes immutable per-side rosters.

This prevents authored list order from changing the future fixed-tick simulation order accidentally.

## 3. Authored acceptance scenarios

Two initial factories are defined:

- `legacyDuel()` — preserves the current Stage-19I-A live-view identities and geometry;
- `balanced4v4()` — authors eight stable combatants, four per side, with deterministic mirrored starting geometry.

The 4v4 factory is only scenario input. Its existence is **not** evidence that 4v4 combat is implemented or accepted.

## 4. Tests

`LiveTacticalBattleScenarioTest` proves:

1. authored input order is normalized to canonical entity-ID order;
2. side rosters remain deterministic;
3. duplicate IDs and one-sided scenarios are rejected;
4. the authored 4v4 roster has eight unique combatants and exact 4/4 side symmetry;
5. repeated 4v4 construction is equality-stable;
6. the legacy duel preserves the existing stable viewer identities.

## 5. Next implementation step

The next Stage-19I-B sub-slice must make the production live runtime consume `LiveTacticalBattleScenario` rather than merely defining it.

Target transition:

```text
authored CombatantSpec
→ materialized per-combatant engineering/consumable/damage/transform state
→ actor-owned observed-track collection
→ production tactical/survival policy
→ target assignment
→ physical movement/fire execution
```

The first runtime acceptance remains **symmetric 4v4 headless execution through one shared production session**. It must not be implemented as four unrelated 1v1 sessions.

After 4v4 is deterministic and physically authoritative, Stage 19I proceeds to 8v8, >=32 combatants, dense ordnance/PD/EW saturation and profiling as required by `docs/stage19_scaled_live_tactical_ai_acceptance.md`.
