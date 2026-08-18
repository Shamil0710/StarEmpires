# Stage 19I-K — scaled live exit tooling

Status: implementation/acceptance slice after resource-constrained AI acceptance.

## Purpose

The Stage-19 scaled-live contract requires the viewer to be an observer/controller of fixed simulation time rather than combat authority. It also requires pause/resume, exact single stepping, deterministic reset, simulation-speed controls and inspectable tactical diagnostics.

This slice adds those controls to the accepted 32-ship saturation session without introducing a second clock or second battle runtime.

## Session controls

`ScaledLiveTacticalSimulationSession` still wraps exactly one `LiveTacticalBattleDeceptionRuntime` created by `Stage19ScaledLiveTacticalFactory.createSaturation32()`.

Presentation scheduling state now consists only of:

- paused/running;
- `SimulationSpeed` = X1/X2/X4/X8 fixed ticks per scheduled batch.

Authority rules:

- `stepOneTick()` delegates exactly one unchanged production `advanceOneTick()` even while paused;
- `advanceScheduledBatch()` executes zero ticks while paused;
- otherwise it executes only the configured integer count of ordinary fixed ticks;
- `reset()` creates a fresh runtime through the same shared factory rather than copying or rewinding mutable combat state;
- visual and debug reads never advance the runtime.

A speed setting therefore changes only how many fixed ticks a presentation loop requests per wall-clock scheduling interval. It never changes `TICK_SECONDS`, sensor cadence, AI, flight, ammunition, guidance, collision, protection or damage rules.

## Read-only diagnostics

`ScaledTacticalDebugSnapshot` and `ScaledTacticalDebugProjection` expose current production/actor-local evidence for inspection:

- actor-local track identities and information quality;
- target selection;
- fire request and survival-filtered fire authorization;
- maneuver intent axes;
- survival action and reason;
- physical ammunition item count;
- physical reaction mass;
- shared stored bus energy;
- ship and summed local stored heat;
- mean compartment integrity;
- minimum fitted-module integrity;
- physical kinetic/STRIKE/interceptor/decoy body populations.

Repeated debug reads are acceptance-tested against the authoritative fingerprint.

Formation state is deliberately **not fabricated**. Current shared Stage-19I control does not yet author a formation objective in its tactical context. The runnable HUD therefore reports `formation objective: NOT AUTHORED`; formation behavior remains an explicit exit-audit blocker after this tooling slice.

## Runnable scaled viewer

`ScaledLiveTacticalSimulationApp` renders the current 32-ship saturation snapshot with the existing `TacticalPrototypeRenderer` and reads the immutable diagnostic projection for its HUD.

Desktop launcher mode:

```text
--scaled-live-tactical-sim
```

Viewer controls:

- SPACE — pause/resume;
- N or RIGHT — one exact production tick while paused;
- R — deterministic shared-factory reset;
- 1/2/4/8 — X1/X2/X4/X8 fixed-tick batching;
- UP/DOWN — select combatant diagnostic row;
- F1 — toggle diagnostic HUD;
- ESC — exit.

The application contains no combat resolution code.

## Acceptance

`ScaledLiveTacticalExitToolingAcceptanceTest` requires:

1. pause blocks scheduled advancement without state mutation;
2. paused single-step advances exactly one authoritative tick and remains paused;
3. X8 batching for 10 batches produces the exact same authoritative fingerprint as 80 individual fixed steps;
4. reset reproduces the canonical fresh factory fingerprint and tick zero;
5. 40 repeated debug reads preserve the authoritative fingerprint and expose all 32 combatants;
6. debug physical body counts match the same pools projected by the visual snapshot.

## Remaining Stage 19I audit blockers

After this slice, continue the line-by-line exit audit. Known items still requiring direct treatment include at least:

- actual formation keeping / break / recovery and compact-vs-dispersed formation scenario evidence;
- an authored retreat/disengagement objective rather than only damage/resource-triggered survival;
- explicit anti-oscillation/deadlock/order-churn soak evidence at fleet scale;
- explicit review of partial-ammunition and any other required scenario-matrix rows not already directly evidenced.

Stage 19 must remain open until those mandatory rows are accepted or the contract itself explicitly marks a conditional row not applicable.
