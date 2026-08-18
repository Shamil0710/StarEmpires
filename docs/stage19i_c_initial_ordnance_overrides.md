# Stage 19I-C — Validated Initial Ordnance Variants

Status: IMPLEMENTED IN THIS SLICE, PENDING CI/MERGE

## Purpose

Stage 19I requires scaled scenarios with partial/depleted ammunition and a physical interceptor screen, while the five Stage-17.5I A–E doctrine fixtures are already a closed acceptance set and must not be silently expanded or numerically modified.

`LiveTacticalInitialOrdnanceService` therefore authors only the initial contents of already fitted physical weapon feeds.

## Rules

The service:

- never changes hull identity;
- never changes installed module/mount identity;
- never adds doctrine/class numeric bonuses;
- requires the mount to contain a production launcher profile;
- requires ammunition family to match launcher family;
- validates ammunition mass/length/diameter against the fitted launcher envelope;
- validates item count against the fitted ammunition-interface capacity;
- replaces itemized round count, interface-native amount and physical carried mass together;
- replaces the feed's authored ammunition content identity atomically;
- preserves damage, shields, maintenance, launcher cooldown state, power/heat state and all unrelated consumables.

All requests are validated before mutation, so invalid multi-feed authoring cannot partially change a combatant.

## Interceptor use

The existing B missile fit contains two ordinary `module.test_weapon_missile_v1` guided launchers. The existing 750 kg interceptor ammunition fits those launcher envelopes. Stage 19I can therefore materialize an interceptor-screen combatant by starting a normal B-fit combatant with selected guided feeds explicitly loaded with `ammo.test_interceptor_750kg_v1`.

This does not create a sixth doctrine. It is a scenario initial condition over real fitted hardware and physical stores.

## Additional exit-gate value

The same boundary supports mandatory Stage 19I variants such as:

- zero ammunition on a selected mount;
- partial ammunition loads;
- asymmetric initial stores;
- alternate compatible authored ammunition on an already fitted launcher.

It is explicitly not an in-combat reload API and does not replace Stage-18 station servicing/logistics.
