# Stage 19I-C — Layered Defense / Physical Interceptor Launch

Status: IMPLEMENTED IN THIS SLICE, PENDING CI/MERGE

## Purpose

Connect the existing Stage-17.5E `LayeredDefenseScheduler` to the shared Stage-19I battle state so a defensive assignment becomes a real physical interceptor launch rather than an acceptance-only counter.

## Physical ownership

A defensive station is materialized from one ordinary combatant mount loaded with guided ammunition whose authored role is `INTERCEPTOR`.

The integration reads and commits:

- the current damage-aware fitted guided mount;
- persistent `WeaponMountRuntime` readiness;
- authored launcher support-channel capacity;
- itemized physical ammunition count and carried mass from central `ConsumableState`;
- current combatant position/velocity;
- the existing `GuidedWeapon` propulsion/seeker definition.

An accepted assignment consumes one round through `AmmunitionRuntime`, starts the ordinary launcher cycle and creates one `GuidedWeaponBody`. No defense probability, virtual interceptor pool or deletion-on-assignment is introduced.

## Scheduler integration

For this first live integration each interceptor-equipped combatant protects its own physical hull zone. The protected zone uses the circumscribed radius of the current hull length/width bounding dimensions; the same radius is the minimum safe intercept distance.

The scheduler receives only hostile active STRIKE guided bodies. One ready physical mount may begin at most one new launch per fixed tick even when it owns multiple simultaneous support channels. Existing active interceptors count against the authored channel capacity.

The scheduler's scalar inherited launch-speed input is currently set conservatively to zero because the production combatant has a vector inertial velocity while the scheduler accepts only a scalar omnidirectional launch-speed contribution. The materialized interceptor itself still inherits the combatant's real velocity vector.

## Exact-local information bridge — temporary

This slice deliberately does **not** claim the final Stage-19I information model for ordnance defense.

`LayeredDefenseScheduler` and current `GuidanceRuntime` predate live missile tracking and accept direct physical body state / `TrackState`. Until the EW/ordnance-sensing slice exists, this gate creates an explicitly labelled exact-local FIRE_CONTROL track for an active hostile guided body and uses its exact current velocity as the interceptor target-motion estimate.

This is temporary acceptance plumbing, not permission for final tactical AI omniscience. Stage 19 cannot close until ordnance detection, track degradation/loss, EW/ECCM and decoy consequences replace this bridge.

## Interceptor lifecycle in this slice

Existing interceptors perform one bounded `GuidanceRuntime` burn and one ballistic propagation step per shared fixed tick, consuming real propellant and authored powered-burn lifetime.

If the referenced strike body disappears before physical body-body interception is implemented, the now-orphaned interceptor is not deleted. It is converted into an ordinary unguided physical `ProjectileBody` and transferred to the existing shared projectile pool.

Physical interceptor-versus-threat collision is intentionally the **next** gate. This slice proves assignment → finite launch → physical guided body only.

## Acceptance

The interceptor duel requires:

- a normal B missile-fit attacker to create physical STRIKE threats;
- a normal B missile-fit defender, initially loaded through `LiveTacticalInitialOrdnanceService` with authored 750 kg INTERCEPTOR rounds;
- ordinary ship-target guided fire to consume zero defender interceptor rounds;
- `LayeredDefenseScheduler` to authorize a defensive launch;
- each defensive launch to remove exactly one itemized interceptor round;
- persistent launcher cooldown to be committed;
- the interceptor to consume real propellant and physically move;
- identical fixed-tick runs to produce identical defense fingerprints.

## Still pending

- swept interceptor/threat body-body collision and physical residual consequence;
- kinetic point-defense routing;
- actor-bounded ordnance sensing;
- EW/ECCM and decoys;
- degraded/depleted/retreat behavior;
- 4v4 defensive screen general acceptance, then 8v8 and 32+;
- saturation profiling and live/headless parity.
