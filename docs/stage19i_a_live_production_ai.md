# Stage 19I-A — live production tactical AI integration

**Status:** implementation slice of the mandatory Stage-19I scaled live tactical-AI exit gate.

Stage 19I-A connects the existing production Stage-19 actor-bounded tactical decision layer to the existing production Stage-17.5 physical engineering/combat runtime inside `LiveTacticalSimulationSession`. It deliberately remains a focused 1v1 foundation. It does **not** close Stage 19 and it must not become a second combat engine that diverges from the later 4v4 / 8v8 / 32+ scale path.

## 1. Authority chain

The implemented live chain is:

```text
production target signature
→ production sensor observation / TrackState
→ Stage-19 actor-bounded tactical intent
→ Stage-19 survival decision
→ Stage-17.5 operating command
→ actual engineering thrust / reaction-mass consumption
→ shared FlightDynamics inertial integration
→ production fire-control authorization
→ finite ammunition consumption
→ independent projectile body
→ production shield / material / subsystem damage
→ next fixed tick
```

The live viewer remains outside combat authority. Presentation reads snapshots only.

## 2. Stage 19I-A physical invariants

- AI receives target information only through the production observation / `TrackState` path.
- Initial state does not contain an omniscient preselected target.
- Tactical intent alone cannot move a ship; movement occurs only after Stage-17.5 engineering resolves actual thrust.
- Actual thrust consumes the same finite reaction-mass state already owned by Stage 17.5.
- Zero available thrust cannot manufacture acceleration.
- The shared `FlightDynamics` integrator owns inertial transform integration for this live seam.
- Stage-19 survival policy may suppress fire or change movement intent but grants no physical bonus.
- Fire still requires the production fire-control path and finite launcher ammunition.
- Doctrine changes policy/content selection only; it does not multiply hidden combat statistics.

## 3. Acceptance added in this slice

`LiveTacticalSimulationSessionTest` now proves that:

1. the session remains fixed-tick and read-only snapshots do not advance time;
2. equal tick counts produce equal authoritative fingerprints;
3. the AI starts without an omniscient target or pre-authorized fire;
4. production sensing establishes actor-visible target information;
5. Stage-19 tactical AI selects the observed hostile contact;
6. AI intent changes the authoritative physical transform and inertial velocity;
7. the maneuver consumes finite Stage-17.5 reaction mass;
8. projectiles remain independently moving physical bodies;
9. shots consume physical ammunition before materialization;
10. impacts change the production shield/material/damage state.

`Stage19PhysicalFlightDynamicsTest` separately verifies the Stage-17.5 physical-flight bridge: zero-thrust inertial drift, externally resolved thrust and invalid physical-input rejection.

## 4. Explicit non-goals

This slice does not yet provide:

- symmetric AI control for both sides;
- generic multi-ship entity/session ownership;
- 4v4, 8v8 or 32+ exact local battles;
- formation coordination at fleet scale;
- dense missile / interceptor / decoy saturation;
- fleet-scale EW and degraded datalink cases;
- performance/memory acceptance measurements;
- Stage-19 completion.

## 5. Transition to Stage 19I-B

The next slice should remove the 1v1 attacker/target structural assumption without replacing the production authority chain.

Recommended Stage 19I-B target:

```text
LiveTacticalCombatant
+ stable combatant identity
+ per-combatant transform
+ per-combatant engineering/consumables/damage
+ actor-owned TrackState collection
+ production tactical/survival decision
+ target assignment
+ finite weapon state
```

Then generalize `LiveTacticalSimulationSession` to deterministic collections of combatants and prove a symmetric **4v4** headless scenario first. Only after 4v4 uses the same runtime should the acceptance ladder proceed to 8v8 and >=32 combatants.

The required final Stage-19I gate remains `docs/stage19_scaled_live_tactical_ai_acceptance.md`.
