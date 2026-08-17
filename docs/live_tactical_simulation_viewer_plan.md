# Star Empires — Live Tactical Simulation Viewer Plan

> Status: **V1 IMPLEMENTED / PRE-PR GREEN; guided/PD expansion PLANNED**  
> Base: Stage 17.5 Combat Depth / Ship Fitting Foundation is COMPLETE.  
> Roadmap ownership: this viewer does **not** reopen Stage 17.5 and does not implement Stage-19 tactical AI.

## 0. Current implementation checkpoint

The first usable live viewer is now implemented on `agent/live-tactical-simulation-viewer`.

Implemented V1 path:

```text
fixed 0.05 s authoritative tick
→ production active-radar observation
→ production TrackState fusion
→ WeaponFireControl kinetic intercept solution
→ finite AmmunitionRuntime consumption
→ independent ProjectileBody materialization
→ ProjectileBody motion over successive live ticks
→ authored hull-envelope intersection
→ fitted ShieldFieldRuntime interaction
→ bounded HeavyImpactResolver
→ ShipDamageRuntime local damage
→ production-derived target state
→ read-only TacticalPrototypeVisualSnapshot
→ TacticalPrototypeRenderer
```

The simulation is advanced while the viewer is open. It is not a playback of precomputed frames.

Current controls and launch path are implemented through `--live-tactical-sim` and
`run-live-tactical-sim.bat`.

Pre-PR exact-head checkpoint before this documentation update: `e115ca6126884ff70599acc0095bfe8f3b5158f5`, CI #2813 SUCCESS. The final documented head must be re-verified before PR/merge.

### Explicit V1 boundaries

V1 is intentionally a controlled live kinetic engagement, not Stage-19 tactical AI. Ships currently hold the authored engagement geometry while the sensor/fire-control/ordnance/protection chain advances live.

Guided offensive impact is **not** faked in V1. The Stage-17.5I anti-ship missile fixture is 2,000 kg while the current content-provisional heavy-impact response surface is calibrated only through 1,500 kg projectile mass, and Stage 17.5 does not provide a final warhead/material-response model for silently converting that mismatch into ordinary kinetic damage. Therefore V1 does not clamp missile mass, invent a hidden warhead multiplier, or let rendering decide missile damage.

The next auxiliary expansion may add live guided flight, interceptor/PD/decoy activity and ship maneuver once each consequence is connected to an authoritative production runtime without bypassing calibration domains. This is tooling evolution, not a blocker for Stage 18.

## 1. Purpose

Create a real-time deterministic tactical viewer that advances authoritative combat state tick-by-tick and renders the current state directly. Unlike the existing `--tactical-acceptance` playback, the live viewer must not precompute a small fixed list of frames.

Authoritative V1 flow:

```text
initial physical state
→ fixed simulation tick
→ sensor / track state
→ fire-control intent
→ finite ammunition consumption
→ physical projectile motion
→ shield / protection / local damage
→ next tick
→ read-only visual projection
```

Later guided/PD expansion extends this same session instead of creating a second combat model.

The renderer remains presentation-only. Pausing, stepping, speed changes and camera/window changes cannot change combat outcomes except by controlling how many deterministic simulation ticks are executed.

## 2. Non-goals

This tooling must not:

- introduce Stage-19 fleet/tactical AI;
- add doctrine/class numeric bonuses;
- invent virtual ammunition, energy, shields or reaction mass;
- create a second damage/weapon/collision model for visuals;
- turn render delta-time into simulation authority;
- replace the existing deterministic `--tactical-acceptance` regression viewer.

The first live scenario uses deterministic scripted firing intent only. Production physics/runtime decides whether the operation is possible and what its consequences are.

## 3. L1 — authoritative session and clock

**V1 IMPLEMENTED.**

`LiveTacticalSimulationSession` provides:

- fixed `TICK_SECONDS = 0.05`;
- monotonically increasing tick/time;
- authoritative fitted engineering state;
- current target track;
- finite ammunition state;
- active `ProjectileBody` collection;
- current fitted shield and local damage state;
- recent authoritative impact result for temporary visualization;
- `advanceOneTick()` as the only simulation progression entry point;
- immutable read snapshot and deterministic state fingerprint for UI/tests.

The class has no libGDX dependency.

Future guided expansion may add active `GuidedWeaponBody` collections to the same session.

## 4. L2 — live ordnance and fire control

### V1 IMPLEMENTED

The first scenario demonstrates:

1. production active-radar observation and track fusion;
2. production `WeaponFireControl` kinetic solution;
3. physical `AmmunitionRuntime` consumption;
4. materialized kinetic projectile with ballistic per-tick motion;
5. finite launcher cadence / deterministic scripted firing intent.

A shot exists only after physical ammunition is consumed and a production fire solution/body exists.

### Guided / PD expansion — PLANNED

Add without changing the V1 authority boundary:

- physical guided body with per-tick guidance burn/ballistic motion;
- defensive interceptor/PD scheduling and finite stores;
- explicit decoy/deception hypotheses where production runtime supports them;
- calibrated guided-body/warhead consequence path rather than treating an out-of-domain 2,000 kg missile as an ordinary accepted heavy-impact sample.

## 5. L3 — live impact and damage

**V1 IMPLEMENTED for calibrated kinetic bodies.**

When a physical kinetic body intersects the target's authored hull envelope:

```text
ProjectileBody
→ optional fitted shield
→ KineticProtectionRuntime
→ bounded HeavyImpactResolver
→ ShipDamageRuntime
→ current compartment/module integrity
→ DerivedShipCalculator read state
```

The impact path reuses Stage-17.5 production runtimes. A projectile cannot disappear merely because its sprite reaches a drawn target.

A later refinement should derive the hull-local hit point directly from the geometric segment intersection rather than using the current deterministic center-hit acceptance point. That refinement will improve spatial damage variety without changing the protection/damage authority boundary.

## 6. L4 — live visual projection

**V1 IMPLEMENTED.**

`LiveTacticalSimulationProjection` is a pure read-only adapter from the current live-session snapshot into `TacticalPrototypeVisualSnapshot`.

V1 displays:

- both ships;
- current physical kinetic bodies;
- current target shield state;
- recent authoritative impact result;
- current compartment damage/wreck state;
- target track/information state through the desktop HUD.

The existing `TacticalPrototypeRenderer` remains a consumer only.

Future guided/PD bodies will be projected only when they exist in authoritative live-session state.

## 7. L5 — desktop viewer controls

**V1 IMPLEMENTED.**

Mode: `--live-tactical-sim`

```text
SPACE       pause / resume
N / RIGHT   execute exactly one simulation tick while paused
R           reset scenario
1           0.25x
2           0.5x
3           1x
4           2x
5           4x
6           8x
F1          toggle debug HUD
ESC         exit
```

Rendering targets 60 FPS, but simulation advances only in whole fixed ticks. Accumulated wall-clock time selects how many fixed ticks to execute; it never changes `TICK_SECONDS`.

The HUD reports live tick/time, track state, physical projectile count, shots, impacts, ammunition, shield reserve, mean target integrity, derived acceleration, shared-bus energy and ship heat.

## 8. L6 — deterministic headless acceptance

**V1 IMPLEMENTED / GREEN.**

Current tests prove:

- two fresh sessions advanced for the same number of ticks produce equal fingerprints and core state;
- no simulation progress occurs without `advanceOneTick()`;
- physical projectile positions change over successive ticks;
- finite ammunition decreases on actual launch;
- at least one physical impact changes authoritative shield and/or local damage state;
- visual projection does not mutate session state.

The reset control constructs the same deterministic fresh session; a future expansion can add a dedicated reset-equivalence test alongside maneuver/guided state.

## 9. L7 — launch and repository gate

### Implemented before final merge

- `run-live-tactical-sim.bat` for one-click Windows launch;
- `DesktopLauncher` routing for `--live-tactical-sim`;
- coverage exclusion only for `LiveTacticalSimulationApp`, never for session/projection logic;
- branch CI checkpoints through #2813 green.

### Remaining gate

- final documented exact-head CI;
- compare against current `main`;
- PR exact-head CI;
- exact-SHA merge;
- post-merge CI on `main`.

## 10. Exit definition for first usable version

V1 is functionally complete when the final repository gate passes: the user can launch a window and observe a combat scenario being advanced **during viewing** through fixed authoritative simulation ticks. A contact forms through production sensors, kinetic ordnance is launched from finite stores, bodies visibly move over multiple ticks, impacts resolve through production protection/damage runtime, and the current state can be paused/single-stepped/reset without the renderer owning combat state.

Guided/PD/maneuver expansion remains desirable auxiliary tooling, but is not allowed to fabricate mechanics merely to make V1 visually busier and does not block Stage 18.
