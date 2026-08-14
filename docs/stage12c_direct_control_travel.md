# Stage 12C — Direct Ship Control and Travel

Status: implementation branch `agent/stage12c-direct-control-travel`.

## Production control model

Stage 12 chooses **intent-driven fixed-tick direct control**.

UI/controller code never mutates `TransformComponent` directly. It writes normalized movement intent into transient `PlayerControlledComponent`. `PlayerDirectControlSystem` applies velocity and position changes only from the Ashley simulation update using authoritative simulation delta.

This means pause, time scale and fixed-step partitioning remain authoritative.

## Active ship binding

`PlayerRuntime` resolves the current `PlayerState.activeFleetId` through the Stage-10 world fleet layer. The transient control component is attached only to the active locally materialized fleet entity.

When direct control takes over a ship, existing autonomous behavior is explicitly put in a safe inactive state:

- TradeAI route is cleared and its replanning cooldown is held;
- Mining automation is disabled/paused.

The physical cargo, wallet, faction/legal context and FleetId remain unchanged.

## Movement and camera/selection seam

`setMovementIntent(x, y)` changes only control intent. Vectors above unit magnitude are normalized.

`PlayerShipView` exposes a read-only snapshot of:

- stable FleetId;
- current StarSystemId;
- current local EntityId;
- position;
- velocity;
- docked state.

Presentation code can therefore follow/select the active ship without owning or changing simulation state.

## Docking

Docking does not teleport the ship.

`dockAt(EntityId)` succeeds only when:

- the active fleet is locally materialized;
- no jump is active;
- target is a live market entity in the same StarSystem;
- current physical distance is inside docking range.

A successful dock stops input/velocity and persists a system-qualified `DiscoveredObjectRef` as `PlayerState.dockedAt`. The station is also added to discovery if necessary.

Playable schema v2 introduces this persistent docking reference. Stage-12A playable schema v1 migrates as undocked; raw v0.2 `WorldState` migration still creates no player.

## Jump travel

The player does not receive a separate travel implementation.

`requestJump(destination)` requires an undocked active ship and a direct topology neighbor, then calls the existing Stage-10 `WorldSimulation.requestFleetJump(...)` FSM.

After materialization in the destination system, `PlayerRuntime`:

- keeps the same world-level FleetId;
- activates the destination StarSystem as the full-rate local system;
- rebinds transient direct control to the new local EntityId;
- records the destination as discovered.

## Time controls

Player pause and time-scale commands update the existing `SimulationClock` state of all local sessions. They do not introduce a second clock or render-delta movement path.

## Acceptance

12C acceptance verifies:

- changing input does not move a ship until a fixed simulation tick executes;
- pause prevents movement;
- direct movement follows configured physical ship speed;
- docking requires physical range and locks movement;
- docking survives playable save/load;
- undocking clears the persistent docking state;
- player jump uses the Stage-10 persistent jump pipeline;
- same FleetId arrives in the destination;
- destination becomes the full-rate active system;
- destination discovery and read-only `PlayerShipView` follow the arrived ship.
