# Stage 19I-E — mixed 8v8 exact-local production runtime gate

Status: implementation/acceptance slice for the mandatory Stage 19 scaled live tactical ladder.

## Purpose

This is the first 16-combatant exact-local acceptance. It does not create a larger-battle engine and does not aggregate combat. The same production chain already used by 1v1/4v4 is instantiated once for sixteen physical ships.

The gate is intentionally valuable as a scale probe rather than a larger version of the 4v4 happy path. The first 8v8 runs exposed two production assumptions that were acceptable in isolated duels but wrong for a formation battle: layered defense protected only the interceptor ship itself, and ordnance warning depended only on active radar even when authored defensive EW physically denied that channel. Both findings are fixed through existing physical/information owners rather than through scale-only shortcuts.

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

## Scale finding 1 — self-only layered defense was not formation defense

The initial 8v8 run produced thousands of physical shots/impacts and real STRIKE/DECOY bodies but zero interceptor launches. Investigation showed that `LiveTacticalBattleDefenseRuntime` supplied `LayeredDefenseScheduler` with only one `DefendedZone`: the interceptor carrier's own hull.

That behavior was mechanically consistent for the earlier exact-local duel fixture but wrong for an escort/formation screen. A specialist could observe a missile headed toward an ally yet correctly receive no assignment because policy only asked whether the trajectory intersected its own hull.

The accepted fix keeps protection geometric and physical:

- every friendly combatant contributes its own separate circumscribed physical hull zone;
- `LayeredDefenseScheduler.scheduleObserved(List<DefendedZone>, ...)` finds the earliest observed ballistic intersection with any friendly hull;
- there is no abstract fleet bubble and empty space between ships is not protected merely because it lies inside a formation radius;
- the scheduler still requires the real interceptor to physically reach the observed trajectory before the earliest protected-hull entry;
- policy never reads the guided body's hidden authored `targetId` to decide which ally to protect.

A focused scheduler acceptance proves that an observed trajectory which misses the interceptor's own hull can still be assigned when it intersects a separate allied physical hull.

## Scale finding 2 — radar-only missile warning failed under authored EW

After formation defense was enabled, the 8v8 still produced zero interceptor launches. Diagnostic acceptance showed that both interceptor specialists retained all 16 interceptor rounds but had zero ordnance tracks. The scenario contained two active physical noise jammers in each receiver environment. One defender committed ECCM power/heat but still produced zero active-radar measurements; the other could no longer admit the radar/ECCM operation at the sampled tick.

The fix does not weaken the authored jammer and does not grant a radar exception. `LiveTacticalOrdnanceObservationRuntime` now uses the existing fitted passive thermal channel for missile warning and the existing production fusion geometry:

- active radar continues to use its engineering grant, ECCM and noise-jammer equations and may remain fully suppressed;
- passive thermal sensing sees the guided body's authored thermal/plume-related physical signature through its distinct sensor channel;
- one passive observer produces bearing-only evidence and does not invent range;
- same-side passive bearings may be shared only when both sender and recipient have an operational fitted `COMMUNICATION_DATALINK` capability;
- ordinary `ShipSensorRuntime.fuse()` performs the multi-observer bearing triangulation;
- Cartesian position therefore appears only when physical observer geometry supplies enough information;
- velocity still requires two temporally distinct actor-bounded Cartesian solutions.

The current Stage-17.5I datalink content authors `support_channels=64` but does not yet author transport range, latency or transport-noise parameters. `ShipDatalinkEngineeringAdapter` therefore exposes only damage-aware support-channel capacity. This exact-local acceptance applies zero additional datalink latency/noise because no such content exists; it does not silently invent those values. Partial module integrity reduces available channels and a destroyed datalink provides no sharing.

A focused EW/formation acceptance proves both sides of the information boundary:

- with the 300 MW jammer still suppressing active-radar ordnance returns, two separated allied passive observers can triangulate the same physical body through their fitted datalinks;
- if one required `utility_datalink` is destroyed, each observer remains bearing-only and no range appears merely because the ships are friendly.

## Acceptance evidence

The focused 8v8 test requires before a bounded tick cap:

- exactly 16 materialized physical combatants, 8 per side;
- every combatant to acquire only hostile actor-local contacts and select from that own `TrackState` domain;
- every combatant to physically move and spend its own reaction mass;
- more than one selected target per side, guarding against universal target collapse in this authored geometry;
- physical kinetic fire;
- physical STRIKE guided launches;
- automatic finite physical decoy deployment;
- finite physical interceptor launch;
- at least one ordinary production physical impact;
- exact itemized guided-round accounting for strike/decoy and interceptor specialist ships;
- identical whole-runtime fingerprints for identical 8v8 initial state and fixed tick schedule.

The first fully green implementation head passed 1,103 tests with zero failures/errors, including the focused formation-defense, passive/datalink and mixed-8v8 acceptance tests. A final exact-head `clean verify` is still required after this documentation update before merge.

## Boundary of this slice

This is the 8v8 shared-runtime baseline, not the full Stage-19I exit gate. Follow-up acceptance still needs explicit pre-damaged/depleted 8v8 behavior, including a ship changing tactical behavior because its physical capability is degraded. The >=32-combatant and dense saturation/profiling/live-viewer parity gates remain mandatory afterwards.

Known boundaries intentionally not hidden by this gate include:

- current ship-target fire control still lacks a production target-velocity estimate channel and therefore uses the existing explicit zero-motion estimate seam rather than authoritative target velocity;
- datalink transport range/latency/noise are not yet authored content;
- passive formation sharing currently exercises thermal bearing evidence; richer multiband association/deception remains later Stage-19I work;
- the live tactical viewer is still the earlier 1v1 projection and has not yet been promoted to the scaled runtime.
