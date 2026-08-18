# Stage 19I-G — 32-ship exact-local production baseline

Status: implementation/acceptance slice for the mandatory `>=32 combatants` Stage-19I scale gate.

## Purpose

This gate doubles the already accepted mixed 8v8 battle to 16v16 while retaining one authoritative exact-local production runtime. It is deliberately separated from the later saturation/profiling gate so a 32-ship functional failure cannot be hidden inside a dense body-count benchmark.

## Authored roster

`LiveTacticalBattleScenario.mixed16v16()` contains exactly 32 physical combatants:

- 16 ALPHA;
- 16 BETA;
- per side: A kinetic line ×4, B missile strike ×4, D defensive EW ×2, E balanced control ×6.

Doctrine counts are identical across sides while their vertical ordering differs. Doctrine identity selects existing authored physical fits/stores only and grants no hidden combat modifier.

## Moderate ordnance specialization

This baseline does not yet maximize body density.

Through the already accepted `LiveTacticalInitialOrdnanceService`:

- one B-fit on each side carries STRIKE + DECOY;
- one B-fit on each side carries INTERCEPTOR rounds;
- the other B-fits remain their ordinary missile-strike configuration.

This exercises the complete production body/defense chain while reserving deliberate dense guided/kinetic/interceptor/decoy pressure for the next saturation gate.

## One runtime, not sixteen duels

All 32 ships share the same chain:

`LiveTacticalBattleRuntimeState`
→ `LiveTacticalBattleControlRuntime`
→ `LiveTacticalBattleWeaponRuntime`
→ `LiveTacticalBattleOrdnanceRuntime`
→ `LiveTacticalBattleDecoyRuntime`
→ `LiveTacticalOrdnanceObservationRuntime`
→ `LiveTacticalBattleDefenseRuntime`
→ `LiveTacticalBattleDeceptionRuntime`.

No fleet DPS abstraction, private duel session, virtual ammunition or scale-specific movement/combat resolver is introduced.

## Acceptance

The focused baseline requires:

- exactly 32 materialized physical ships, 16 per side;
- after early production sensing, every combatant has at least one actor-local hostile contact;
- every selected target comes from that actor's own `TrackState` domain;
- no actor-visible friendly/self hostile target leak;
- every combatant spends its own finite physical reaction mass through normal movement;
- each side selects more than two distinct target identities in the authored geometry, guarding against a single universal target hypothesis;
- within a bounded continuation: kinetic fire, STRIKE launches, automatic physical decoys, finite physical interceptors and ordinary production impacts all occur;
- specialist guided ammunition accounting remains exact;
- identical 32-ship initial state and fixed tick schedule produce equal whole-runtime fingerprints.

## Boundary

This gate proves the baseline `>=32` combatant scale only. Stage 19I remains open afterwards.

Mandatory follow-up:

1. dense saturation with guided, kinetic, interceptor and decoy bodies under EW;
2. profiling/diagnostics for fixed-tick wall time and active body/sensing/collision load;
3. scaled live/headless/read-only projection parity.

Known broader limitations remain explicit, including absent authored datalink range/latency/noise, the existing target-velocity fire-control seam and the still-1v1 live tactical viewer.
