# Stage 14C — Playable Navigation, HUD and Local Minimap

## Status

**COMPLETE — PR #43, functional main `649224f8`, final CI #988.**

Final verification passed **426/426 tests**, strict Javadoc, JaCoCo line/branch gates and desktop shaded-JAR packaging.

## Goal

Stage 14C converts the Stage-12/13/14A/14B technical mechanics into a locally readable playable screen without introducing UI-only gameplay rules.

The presentation layer remains read-only with respect to simulation state:

```text
WorldState / PlayerState / ECS components
        ↓ read
HUD / local minimap / world view
        ↓ commands only
PlayerRuntime / PlayerMiningService / PlayerMarketService / PlayerShipProgressionService
        ↓
authoritative fixed-tick simulation
```

No renderer or HUD path directly teleports ships, creates cargo/resources, changes wallets, applies damage or performs extraction.

## Bounded player-follow camera

`PlayableCameraState` now owns a presentation-only bounded zoom value. Mouse-wheel input changes world scale while the camera center continues to follow the active physical ship. `HOME` restores the default gameplay zoom.

HUD layout remains Scene2D/screen-space and therefore does not scale with world zoom.

Headless tests cover:

- minimum/maximum zoom bounds;
- deterministic scroll behavior;
- invalid absolute zoom rejection;
- reset to the default gameplay value.

## Local minimap

Stage 14C adds:

- `LocalMinimapSnapshot` — immutable read-only marker data;
- `LocalMinimapModel` — deterministic ECS-to-marker classification;
- `LocalMinimapRenderer` — compact GPU presentation shell.

Current marker categories:

- active player ship;
- stations;
- friendly fleets;
- temporary Stage-13 hostile fleets;
- other fleets;
- asteroids;
- salvage.

Markers are sorted deterministically by persistent local `EntityId`.

### Ownership precedence

Stage 12/14B deliberately separates **player ownership** from `FactionComponent`. A purchased physical FleetId therefore keeps its prior legal/faction identity.

Stage 14C explicitly respects that model:

```text
player owns FleetId
→ local EntityId is recognized as owned
→ minimap classifies it as friendly
→ nearest-hostile player targeting excludes it
→ FactionComponent is not rewritten merely for UI convenience
```

For unowned fleets only, Stage 14C temporarily mirrors the Stage-13 simplification: a combat-capable fleet of another faction is displayed as hostile. Proper diplomacy-aware ROE remains deferred to Stage 18.

## World-view declutter

`PlayableMapEntityFilter` keeps the large local view readable across zoom levels without changing simulation/discovery state.

At distant zoom, navigation-critical stations/fleets and the selected player ship remain visible. Intermediate detail restores salvage, and close zoom restores all supported local objects including asteroids.

The compact minimap still receives the complete authoritative marker set; declutter affects presentation only.

## Unified playable HUD

The playable harness now separates information into ship/combat, interaction/economy and contextual status areas.

### Ship / navigation

HUD reads the active physical state and exposes:

- active FleetId;
- number of owned fleets;
- player credits;
- current system;
- position;
- velocity-derived speed;
- cargo utilization and a compact cargo summary;
- docked/flight/jump-transit state;
- current time scale / pause state;
- camera zoom and route context.

### Combat

For combat-capable hulls the HUD reads real Stage-13 components:

- current/max hull;
- current/max shields;
- weapon range;
- cooldown remaining;
- current target name;
- target distance;
- in-range/out-of-range state;
- fire-request state.

`T` selects the nearest currently valid temporary-hostility target and `F` submits ordinary shared fire intent through `PlayerRuntime`. The UI never applies damage directly.

### Mining

Stage-14A data is now visible from `PlayerMiningView`:

- current mining status/failure reason;
- selected asteroid;
- distance vs extraction range;
- remaining finite reserve;
- free cargo capacity;
- units extracted on the latest fixed tick.

`M` selects a nearby asteroid through `PlayerMiningService`; `R` starts/stops ordinary shared extraction intent. The HUD itself never changes asteroid reserve or cargo.

### Market / economy

While docked, the HUD reads `PlayerMarketView` and shows:

- market-access result;
- cargo utilization;
- selected item;
- station stock;
- player cargo;
- real current buy/sell prices.

`B` / `V` still use the ordinary Stage-12 `PlayerMarketService` / shared `TradeController` path.

### Owned-ship switching

`TAB` attempts to switch direct control to another locally materialized owned FleetId through Stage-14B `PlayerShipProgressionService`. The presentation layer does not recreate or relocate the target ship.

## GPU boundary / coverage policy

The large libGDX application shell and raw OpenGL renderers require a real graphics context. They are excluded from the core JaCoCo line/branch gate for the same reason as the pre-existing world renderer.

The deterministic decision/geometry logic is extracted and remains headless-testable:

- `PlayableCameraState`;
- `LocalMinimapModel`;
- `PlayableMapEntityFilter`;
- existing `WorldMapLayout`.

Desktop packaging remains a CI gate for the GPU-facing shell.

## Controls in the Stage-14 playable harness

```text
WASD          direct flight
mouse wheel   bounded zoom
HOME          reset zoom
E             dock / undock
J             jump along curated route
UP/DOWN       select market item
B / V         buy / sell
M             select nearby asteroid
R             toggle mining
T             select nearest valid hostile combat target
F             toggle fire intent
TAB           switch to another owned local FleetId
SPACE         pause / resume
1 / 2 / 3 / 4 time scale
F5 / F9       save / load
F2            reset curated scenario
```

## Acceptance evidence

PR #43 final CI #988:

- **426 tests**;
- **0 failures**;
- **0 errors**;
- **0 skipped**;
- strict Javadoc passed;
- JaCoCo line/branch thresholds passed;
- shaded desktop JAR packaged and uploaded.

## Deliberate seams

Stage 14C does not attempt the final production UI. Deferred work includes:

- full global galaxy/empire map;
- diplomacy-aware hostility/ROE;
- advanced target-selection UX and mouse interaction;
- final artwork/iconography/notifications/accessibility;
- strategic fleet/order panels;
- mass/thrust/braking diagnostics that depend on Stage 14E flight dynamics.

The next core slice is **Stage 14E — shared game-friendly inertial flight dynamics**, followed by Stage 14D integrated first-hour acceptance/telemetry.
