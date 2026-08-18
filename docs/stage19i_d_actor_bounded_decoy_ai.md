# Stage 19I-D — actor-bounded automatic decoy deployment

Status: implementation/acceptance slice for the Stage 19I live tactical gate.

## Purpose

This slice removes the requirement for a test caller to press the decoy launcher manually. It does not add a deception bonus or synthetic sensor hypothesis. The AI may only request deployment; the accepted physical decoy runtime remains authoritative for ammunition, launcher readiness, body mass, propellant, signature, movement and destruction.

## Actor information boundary

An automatic deployment requires all of the following:

1. the combatant's production tactical controller currently has a selected target;
2. that target exists in the combatant's own `visibleContacts` domain with a Cartesian `TRACKED` or `FIRE_CONTROL` solution;
3. the combatant already owns an active physical STRIKE guided body aimed at the same selected target;
4. the combatant has no currently active physical decoy;
5. a damage-aware fitted launcher is actually loaded with authored `DECOY` ammunition and the physical owner accepts the deployment.

The deployment direction is derived from the actor-local estimated target position. Hostile authoritative transforms are not read by deception policy.

## Resource/physics ownership

`LiveTacticalBattleDeceptionRuntime` coordinates policy order only. It delegates the actual launch to `LiveTacticalBattleDecoyRuntime.deployOne(...)`, preserving:

- itemized finite ammunition and mass;
- launcher compatibility and physical envelope;
- mount cooldown;
- damage-aware fitted capability;
- physical `GuidedWeaponBody` materialization;
- finite propellant and powered burn;
- the existing actor-bounded sensor and layered-defense path.

The one-active-decoy-per-source rule is provisional Stage 19I anti-spam policy, not a combat-stat modifier.

## Acceptance

The focused acceptance requires:

- a mixed STRIKE/DECOY ship to launch a real STRIKE before automatic deception can occur;
- exactly one physical decoy round to be consumed for the first automatic deployment;
- the target supporting deployment to exist in the source actor's visible contact domain;
- a decoy-only ship to never self-trigger merely because it can see an enemy;
- identical fixed-tick scenarios to produce equal composite deception fingerprints.

## Remaining Stage 19I-D work

This slice does not close the information-quality gate. Degraded/lost ordnance-track continuity still needs explicit acceptance so stale velocity cannot survive a track-quality break and be resurrected by a single reacquisition. Richer association/deception hypotheses also remain separate work before scaled 8v8/32+/saturation acceptance.
