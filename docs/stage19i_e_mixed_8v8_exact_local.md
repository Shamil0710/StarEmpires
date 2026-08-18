# Stage 19I-E — mixed 8v8 exact-local production runtime gate

Status: implementation/acceptance slice for the mandatory Stage 19 scaled live tactical ladder.

## Purpose

This is the first 16-combatant exact-local acceptance. It does not create a larger-battle engine and does not aggregate combat. The same production chain already used by 1v1/4v4 is instantiated once for sixteen physical ships.

## Authored roster

`LiveTacticalBattleScenario.mixed8v8()` contains eight combatants per side and uses only the existing Stage-17.5I acceptance doctrines:

- kinetic line (A);
- missile strike (B);
- defensive EW (D);
- balanced control (E).

Both sides have equal doctrine counts but different vertical ordering. Doctrine identity only chooses existing physical fit/store content and grants no numeric combat bonus.

## Ordnance roles without new doctrine IDs

The accepted `LiveTacticalInitialOrdnanceService` authors scenario-local physical feed contents before battle runtime construction:

- one B-fit on each side keeps one STRIKE launcher and carries one DECOY launcher;
- a second B-fit on each side carries INTERCEPTOR rounds on both compatible guided launchers.

This does not mutate fitted modules or launcher envelopes. Every round remains itemized physical ammunition with ordinary launcher cooldown/support channels.

## Required single-runtime chain

The 8v8 acceptance runs one:

`LiveTacticalBattleRuntimeState`
→ `LiveTacticalBattleControlRuntime`
→ `LiveTacticalBattleWeaponRuntime`
→ `LiveTacticalBattleOrdnanceRuntime`
→ `LiveTacticalBattleDeceptionRuntime`
→ actor-bounded ordnance sensing / layered defense / physical collision.

No combatant gets a private duel session and no large-battle resolver exists.

## Acceptance evidence

The focused test requires before a bounded tick cap:

- exactly 16 materialized physical combatants, 8 per side;
- every combatant to acquire only hostile actor-local contacts and select from that own TrackState domain;
- every combatant to physically move and spend its own reaction mass;
- more than one selected target per side, guarding against universal target collapse in this authored geometry;
- physical kinetic fire;
- physical STRIKE guided launches;
- automatic finite physical decoy deployment;
- finite physical interceptor launch;
- at least one ordinary production physical impact;
- exact itemized guided-round accounting for strike/decoy and interceptor specialist ships;
- identical whole-runtime fingerprints for identical 8v8 initial state and fixed tick schedule.

## Boundary of this slice

This is the 8v8 shared-runtime baseline, not the full Stage-19 8v8 matrix. Follow-up acceptance still needs explicit pre-damaged/depleted/thermally stressed 8v8 behavior, including a ship changing tactical behavior because its physical capability is degraded. The >=32-combatant and dense saturation/profiling/live-viewer gates remain mandatory afterwards.
