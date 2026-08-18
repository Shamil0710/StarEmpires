# Stage 19I-C — Guided Physical Impacts

Status: IMPLEMENTED IN THIS SLICE, PENDING CI/MERGE

## Purpose

Complete the first production guided-ordnance consequence path: an active `GuidedWeaponBody` must physically cross a moving hull footprint before any protection consequence occurs, and that consequence must use the exact same shield/material/local-damage owners already accepted for kinetic bodies.

## Shared authority

`LiveTacticalBattleWeaponRuntime` now exposes two package-local composition seams only:

- `resolveExternalPhysicalImpact(...)` — routes an already detected physical intersection through the existing `KineticProtectionRuntime`, shield state, armor/material response and `ShipDamageRuntime`, then persists the result into the same `ShipInstanceRuntimeState`;
- `acceptExternalProjectile(...)` — transfers a surviving external residual body into the existing production projectile pool.

These seams do not perform targeting, hit probability or guided collision detection. They exist specifically so guided/interceptor bodies do not need a second protection model or a second residual-body simulation.

## Guided collision

`LiveTacticalBattleOrdnanceRuntime` now:

1. snapshots ship positions before the authoritative wrapped battle tick;
2. advances the common ship/kinetic runtime;
3. materializes actor-authorized guided launches;
4. executes at most one production guidance burn per body;
5. performs swept relative segment/AABB collision against every non-source combatant, including friendlies;
6. converts the guided body at the first physical intersection into its current physical mass, geometry and velocity;
7. routes that body through the shared protection seam;
8. removes the guided body and releases its launcher support channel;
9. if material response leaves a residual projectile, advances it through the unused tick fraction and transfers it into the one existing projectile pool.

A guided body therefore ceases to be guided after physical impact. Residual penetrator/debris mass is ordinary physical projectile state.

## Determinism state

The guided fingerprint now also records:

- physical launch tick;
- remaining propellant;
- remaining authored powered-burn lifetime;
- per-target guided physical impact counts.

## Acceptance

This slice requires:

- a missile-doctrine ship to launch from an actor-visible production track;
- the guided body to physically intersect a moving defending ship;
- the impact to be counted by both the guided coordinator and the shared protection owner;
- persistent fitted shield reserve to change from that physical protection interaction;
- the external impact seam to use ordinary production shield/material resolution;
- any surviving residual body to be admissible into the existing projectile pool;
- identical guided-impact duels to remain byte/equality deterministic at the fingerprint level.

## Calibration boundary discovered

The provisional doctrine heavy-impact response surface currently authorizes projectile masses only up to 1500 kg, while the existing anti-ship missile launches at 2000 kg wet mass. The accepted close-range guided-impact scenario remains inside the protection model because the fitted shield fully absorbs the early low-energy missile interaction before armor calibration is queried.

No silent material-response extrapolation is introduced. An unshielded or shield-penetrating impact above the authored mass domain remains an explicit calibration gap to resolve before final Stage 19I saturation acceptance (or by replacing/promoting the provisional Stage-17.5I response data during Stage 22 content calibration).

## Still pending

Stage 19I remains incomplete. Next mandatory work is layered defense/interceptor materialization, followed by seeker/EW/decoy degradation, damaged/depleted tactical behavior and retreat, 8v8, 32+ ships, saturation profiling and live/headless parity.
