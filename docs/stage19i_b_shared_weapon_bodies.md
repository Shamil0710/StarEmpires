# Stage 19I-B — shared multi-combatant weapon-body gate

**Status:** implementation slice. This extends the shared 4v4 authority chain through physical kinetic weapon launch, but it is not yet complete 4v4 combat acceptance.

## Authority chain

The shared battle now composes the existing production systems as follows:

```text
shared 4v4 sensing / TrackState
→ actor-bounded tactical + survival policy
→ physical engineering / movement
→ fire authorization
→ fitted ShipWeaponEngineeringAdapter mounts
→ actor-visible selected TrackState
→ WeaponFireControl
→ AmmunitionRuntime over central ConsumableState
→ WeaponMountRuntime launcher-cycle continuity
→ ProjectileBody
→ ballistic fixed-tick body motion
```

No abstract fleet weapon DPS, hit percentage or virtual ammunition counter is introduced.

## Firing requirements

A kinetic shot is materialized only when all of the following are true:

1. Stage-19 tactical policy selected a target from the actor's visible contact domain;
2. survival policy still authorizes firing;
3. the selected target remains present as an actor-visible `TrackState`;
4. the production fitted weapon adapter exposes an operational kinetic mount;
5. the mount's persisted `WeaponMountRuntime` cycle is ready;
6. `AmmunitionRuntime.planOne(...)` confirms a real round exists in the central physical consumable state;
7. `WeaponFireControl.planKinetic(...)` returns a physically valid intercept solution.

Only after those checks succeed are the next ammunition state, launcher-cycle state and `ProjectileBody` committed.

## Information boundary

The weapon runtime never reads the authoritative target transform or velocity to improve fire control. The selected position comes from the actor-visible track.

The current production `TrackState` has no target-velocity estimate channel. Until that model is expanded, this slice deliberately uses the same explicit zero target-motion estimate as the established 1v1 live session. This is a known accuracy limitation, not hidden omniscience and not a hit-probability shortcut.

## Physical continuity

- Every fired round is removed from the shooter's ordinary `ConsumableState` through `AmmunitionRuntime`.
- Launcher cycle time is stored in the combatant's existing `ShipInstanceRuntimeState.weaponMountRuntime`.
- Projectile source identity is the stable firing combatant entity ID.
- Projectile mass, geometry, material, inertial velocity and ballistic movement use the existing `ProjectileBody` representation.
- Projectile bodies are kept in one shared deterministic battle body set.
- No render state can create, move or remove an authoritative projectile.

## Acceptance requirements for this slice

For the deterministic `balanced4v4()` scenario:

- every one of the eight production AI combatants must eventually obtain a valid observed firing solution and materialize at least one kinetic body;
- each shot must reduce that combatant's itemized physical ammunition by exactly one round for the current fixture;
- launcher cycle state must prevent free next-tick refire;
- projectile bodies must move independently over subsequent fixed ticks;
- bodies must retain stable source identity;
- two independently materialized identical 4v4 sessions run for the same fixed ticks to equality-identical AI/control/store/cooldown/projectile fingerprints.

## Not yet accepted

This slice intentionally does **not** implement a second collision or damage system. The following remain required before 4v4 combat can be accepted:

- shared swept projectile/ship intersections;
- production shields, armor and compartment/subsystem damage for every combatant;
- guided missiles and guidance state;
- point-defense/interceptors;
- EW/ECCM/decoys;
- damaged/ammunition-depleted behavioral response;
- retreat/disengagement completion;
- 8v8, 32+ and dense-saturation performance gates.

## Next Stage-19I slice

The next slice should route the shared projectile-body set through **multi-combatant physical collision and the existing `KineticProtectionRuntime`**, updating the struck combatant's existing `ShipInstanceRuntimeState` damage/shield continuity instead of creating battle-local hit points.
