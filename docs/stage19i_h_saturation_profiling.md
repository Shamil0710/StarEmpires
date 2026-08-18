# Stage 19I-H — dense ordnance saturation and workload profiling

Status: implementation/acceptance slice after the green 32-ship exact-local baseline.

## Purpose

This gate answers two different Stage-19 questions without changing combat authority:

1. can the same 16v16 production runtime sustain a dense simultaneous mix of kinetic, STRIKE, INTERCEPTOR and DECOY bodies under the already-authored EW environment and finite stores;
2. what workload and wall-clock/memory behavior does that representative local battle actually produce.

No large-battle resolver, virtual body pool, PD probability, free ammunition or profiling-only combat rule is introduced.

## Saturation fixture

The fixture reuses `LiveTacticalBattleScenario.mixed16v16()` and the accepted initial-ordnance authoring seam.

All eight B-fit ships are specialized without changing their fitted modules:

- four ships total (two per side) carry STRIKE on one compatible guided launcher and DECOY on the other;
- four ships total (two per side) carry INTERCEPTOR rounds on both guided launchers;
- each authored launcher feed starts with eight real itemized rounds.

A/D/E ships keep their ordinary physical content and continue to generate the kinetic/EW/control portions of the same battle.

## Dense-body acceptance criterion

Because final weapon/body balance belongs to Stage 22, this gate does not invent a DPS or launcher-count target. The provisional scale criterion is structural and relative:

- kinetic projectile/residual bodies, STRIKE guided bodies, guided interceptors and physical decoys must all be active on the same authoritative tick;
- peak simultaneous non-ship body count must exceed the 32 physical combatant ships;
- finite layered defense must achieve at least one swept physical interceptor/threat contact;
- ship protection must resolve real physical impacts;
- total guided-feed item loss must equal STRIKE launches + DECOY deployments + INTERCEPTOR launches exactly.

This establishes genuine concurrent saturation without treating an arbitrary balance number as physics.

## Profiling boundary

`LiveTacticalWorkloadProfiler` advances the same authoritative `LiveTacticalBattleDeceptionRuntime` by an explicit number of fixed ticks. It separates two evidence classes.

### Deterministic workload

Recorded from authoritative/read-only state after each tick:

- active ships;
- fixed ticks;
- tactical-AI decision count (`ships × ticks`, because production control plans every materialized actor each fixed tick);
- cumulative and peak actor-local ship-track hypotheses;
- cumulative and peak actor-local ordnance-track hypotheses;
- peak active kinetic projectile/residual bodies;
- peak active STRIKE guided bodies;
- peak active interceptor bodies;
- peak active physical decoys;
- peak total non-ship ordnance bodies;
- whether all four body classes were concurrent;
- physical kinetic shots;
- physical STRIKE launches;
- physical DECOY deployments;
- physical INTERCEPTOR launches;
- ship-protection impacts;
- swept physical interceptor/threat contacts.

This deterministic workload projection is replay-tested for identical saturation initial state and fixed tick schedule.

### Environment-dependent performance observations

Measured only by the diagnostic harness and never written into simulation state/fingerprints:

- total wall-clock nanoseconds;
- authoritative ticks per real second;
- mean tick duration;
- p95 tick duration;
- maximum tick duration;
- approximate JVM used heap before/after the interval;
- peak sampled used heap;
- signed final-minus-initial heap delta.

No hard performance threshold is authored in advance. The Stage-19 contract explicitly requires thresholds to be calibrated from representative hardware and profiling evidence rather than invented before measurement.

## Current profiling granularity

The v1 workload report counts track hypotheses rather than every individual sensor-equation invocation, and it records materializations/impacts/interceptions rather than every broad-phase collision candidate. This is sufficient to establish the first required Stage-19 workload evidence and identify which state populations grow under saturation. If the measured profile indicates a bottleneck, lower-level per-subsystem counters can be added without changing simulation authority.

## Acceptance run

The primary saturation/profile test executes 240 fixed ticks (12 seconds of authoritative tactical time at the current 0.05 s fixed step). A second pair of identical fixtures executes 60 ticks each and requires exact equality of deterministic workload and whole-runtime fingerprints, while deliberately not comparing wall-clock or heap values.

The first green CI profile will be copied into this document before the PR is merged, followed by a final exact-head Java 17 `clean verify`.

## Remaining Stage 19I after this gate

Saturation/profiling does not complete Stage 19I. Mandatory remaining exit work includes:

- scaled live viewer over the same 32-ship/saturation authority;
- proof that live/read-only presentation does not mutate combat state;
- headless and live execution parity from the same scenario/runtime;
- final review of the required scenario matrix, including still-explicit ammunition/heat/power decision gaps where the current AI readiness model does not yet consume those states.
