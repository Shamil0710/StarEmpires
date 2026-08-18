# Stage 19I-C — Shared Guided Ordnance

Status: IMPLEMENTED IN THIS SLICE, PENDING CI/MERGE

## Purpose

Extend the exact-local Stage 19I multi-combatant runtime from the already accepted shared kinetic path into production guided ordnance without adding an abstract missile-combat layer.

## Authoritative composition

The runtime composes existing production ownership:

1. `LiveTacticalBattleWeaponRuntime` advances the one shared combat clock, actor-bounded sensing/AI, ship engineering/flight, fitted kinetic weapons and kinetic protection.
2. `ShipGuidedWeaponEngineeringAdapter` projects current fitted/damaged `WEAPON_AMMUNITION` capability into ordinary `GUIDED` launcher profiles and loaded `GuidedAmmunitionDefinition` content.
3. `AmmunitionRuntime` removes one physical wet-mass round from the central ship consumable state for every accepted launch.
4. `WeaponMountRuntime` owns physical launcher cycle continuity.
5. Authored launcher `supportChannelCount` bounds simultaneous datalink-guided bodies from each physical mount.
6. `GuidedWeaponBody` owns missile mass, geometry, position, velocity, seeker/guidance availability and remaining physical propellant.
7. `GuidanceRuntime` plans and executes bounded lead-pursuit burns from actor-visible `TrackState` and consumes real propellant.
8. `GuidedWeaponBody.advanceBallistic(...)` propagates the resulting physical body independently of presentation.

No doctrine/class numeric combat bonus, missile hit chance, abstract salvo damage, virtual ammunition or second ship-motion engine is introduced.

## Information boundary

Guided launch uses the same Stage 19 tactical authorization as the ship's ordinary combat decisions. The selected target must exist in that source combatant's actor-local visible `TrackState` domain.

For this slice, active guidance uses the explicit `DATALINK` path and the launching actor's current visible track. Production `TrackState` does not yet expose a target-velocity estimate channel, so guidance intentionally uses a zero target-motion estimate instead of reading authoritative enemy transform velocity.

Loss or insufficiency of the source-visible track leaves the missile ballistic; no omniscient correction is manufactured.

## Physical acceptance

The symmetric missile 4v4 acceptance requires:

- all eight combatants eventually materialize guided bodies from actor-visible target authorization;
- every guided launch removes exactly one itemized guided-feed round;
- persistent launcher cooldown is committed on the physical mount;
- guidance burns consume real onboard missile propellant;
- guided bodies move as independent physical bodies;
- active datalink-guided bodies cannot exceed authored launcher support channels;
- identical scenario and tick schedule produce identical whole-battle guided fingerprints.

## Explicitly not completed here

This slice does not claim Stage 19I completion. The following remain mandatory:

- guided-body/ship and interceptor/body collision/protection resolution;
- production layered defense / PD / interceptor launch and channel allocation;
- onboard seeker measurements and seeker-vs-datalink transitions;
- EW/ECCM, decoys, track degradation/loss and guidance consequences;
- damaged/depleted ship tactical behavior and retreat acceptance;
- 8v8 and 32+ combatant scale ladder;
- saturation workload/performance/memory profiling;
- live-viewer/headless parity and final Stage 19 exit evidence.

The next slice should therefore connect the existing `LayeredDefenseScheduler` to active guided threats and materialize real interceptor bodies, while keeping assignment feasibility, ammunition, launcher readiness, channels, thermal constraints and eventual collisions physical.
