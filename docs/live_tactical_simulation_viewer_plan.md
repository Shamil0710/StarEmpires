# Star Empires — Live Tactical Simulation Viewer Plan

> Status: **ACTIVE auxiliary post-17.5 validation tooling**  
> Base: Stage 17.5 Combat Depth / Ship Fitting Foundation is COMPLETE.  
> Roadmap ownership: this viewer does **not** reopen Stage 17.5 and does not implement Stage-19 tactical AI.

## 1. Purpose

Create a real-time deterministic tactical viewer that advances authoritative combat state tick-by-tick and renders the current state directly. Unlike the existing `--tactical-acceptance` playback, the live viewer must not precompute a small fixed list of frames.

Authoritative flow:

```text
initial physical state
→ fixed simulation tick
→ sensor / track state
→ fire-control intent
→ finite ammunition consumption
→ physical projectile / guided-body motion
→ shield / protection / local damage
→ capability degradation
→ next tick
→ read-only visual projection
```

The renderer remains presentation-only. Pausing, stepping, speed changes and camera/window changes cannot change combat outcomes except by controlling how many deterministic simulation ticks are executed.

## 2. Non-goals

This tooling must not:

- introduce Stage-19 fleet/tactical AI;
- add doctrine/class numeric bonuses;
- invent virtual ammunition, energy, shields or reaction mass;
- create a second damage/weapon/collision model for visuals;
- turn render delta-time into simulation authority;
- replace the existing deterministic `--tactical-acceptance` regression viewer.

The first live scenario may use a deterministic scripted controller that expresses **intent only**. Production physics/runtime decides whether an action is possible and what its consequences are.

## 3. L1 — authoritative session and clock

Add a pure headless `LiveTacticalSimulationSession` with:

- fixed `tickSeconds`;
- monotonically increasing tick/time;
- authoritative ship physical state;
- current target track;
- finite ammunition state;
- active `ProjectileBody` / `GuidedWeaponBody` collections;
- current shield and local damage state;
- deterministic impact/event history needed only for current-frame visualization;
- `advanceOneTick()` as the only simulation progression entry point;
- immutable read snapshot for UI/tests.

No libGDX dependency is allowed in this class.

## 4. L2 — live ordnance and fire control

At minimum the first scenario must demonstrate:

1. production active-radar observation and track fusion;
2. production `WeaponFireControl` solution;
3. physical `AmmunitionRuntime` consumption;
4. materialized kinetic projectile with ballistic per-tick motion;
5. physical guided missile with per-tick guidance burn/ballistic motion;
6. finite launcher cadence / deterministic scripted firing intent.

A shot exists only after physical ammunition is consumed and a production fire solution/body exists.

## 5. L3 — live impact and damage

When a physical body intersects the target's authored hull envelope:

```text
body
→ optional fitted shield
→ KineticProtectionRuntime
→ bounded HeavyImpactResolver
→ ShipDamageRuntime
→ current compartment/module integrity
→ DerivedShipCalculator capability change
```

The impact path must reuse Stage-17.5 production runtimes. A projectile may not disappear merely because its sprite reaches a drawn target.

## 6. L4 — live visual projection

Add a pure projection from the current live-session snapshot into `TacticalPrototypeVisualSnapshot`.

It should show, when authoritative state exists:

- ships and thrust state;
- current kinetic bodies;
- current guided bodies;
- current shield state;
- recent impact result;
- current compartment damage/wreck state;
- current track/information state in HUD text.

The existing `TacticalPrototypeRenderer` remains a consumer only.

## 7. L5 — desktop viewer controls

Add `--live-tactical-sim` with controls:

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

Rendering can run at 60 FPS, but simulation advances only in whole fixed ticks. Accumulated wall-clock time selects how many fixed ticks to execute; it never changes `tickSeconds`.

## 8. L6 — deterministic headless acceptance

Tests must prove:

- two fresh sessions advanced for the same number of ticks produce equal fingerprints/state;
- no simulation progress occurs without `advanceOneTick()`;
- projectile positions change over successive ticks;
- finite ammunition decreases only on actual launch;
- at least one physical impact changes shield and/or local damage state;
- repeated impacts can reduce production-derived capability;
- reset creates the exact initial deterministic state;
- visual projection does not mutate session state.

## 9. L7 — launch and repository gate

Add:

- `run-live-tactical-sim.bat` for one-click Windows launch;
- `DesktopLauncher` routing for `--live-tactical-sim`;
- coverage exclusion only for the OpenGL application shell, never for session/projection logic;
- branch exact-head CI;
- PR exact-head CI;
- exact-SHA merge;
- post-merge CI on `main`.

## 10. Exit definition for first usable version

The first version is accepted when the user can launch a window and observe a combat scenario that is being advanced **during viewing** through fixed authoritative simulation ticks: contacts form, ordnance is launched from finite stores, bodies visibly move over multiple ticks, impacts resolve through production protection/damage runtime, and current state can be paused/single-stepped/reset without the renderer owning any combat state.
