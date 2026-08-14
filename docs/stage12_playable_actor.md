# Stage 12 — Playable Actor, Ownership, Travel and Manual Trade

Status: **COMPLETE — PR #29–#32. Final functional main: `89da8dc`.**

## Goal

Stage 12 turns the Stage-7–11 living galactic simulation into a directly playable economic sandbox without creating a second player-only world, economy, travel model or ownership model.

The central architectural boundary is:

```text
PlayableWorldState
├── WorldState        <- independent galaxy simulation
└── PlayerState       <- human actor state
```

The world can continue to exist without a player. Player state references physical world assets through stable IDs.

## 12A — Persistent player state

PR #29, main `3a7efe1`.

`PlayerState` persistently owns:

- personal wallet in integer milli-credits;
- optional faction/legal affiliation;
- faction reputation;
- owned world-level `FleetId` values;
- active FleetId;
- discovered systems and system-qualified objects;
- optional home system;
- later, persistent docking state added by 12C.

`PlayableWorldStateCodec` stores the unchanged `WorldStateCodec` payload together with bounded player data.

A raw pre-Stage-12 `WorldState` save migrates with `playerState = null`; migration never invents a ship, wallet balance, discovery or affiliation. Continuation tests prove deterministic world + player save/load behavior.

## 12B — Ownership

PR #30, main `998f373`.

Player ownership is membership in `PlayerState.ownedFleetIds`, not faction membership:

```text
FleetId                  -> physical fleet identity
FactionComponent         -> legal/faction context
PlayerState.ownedFleetIds -> human ownership
```

`PlayerOwnershipService` buys/sells already-existing physical fleets through real wallet transfers and the existing ledger. Ownership transfer never duplicates or respawns an entity. Physical destruction removes stale ownership and active-fleet references.

## 12C — Direct control, docking and travel

PR #31, main `342659b`.

Production control is intent-driven and fixed-tick:

- UI/controller writes normalized transient input to `PlayerControlledComponent`;
- `PlayerDirectControlSystem` alone mutates physical `TransformComponent` during Ashley simulation ticks;
- pause/time scale continue to use ordinary `SimulationClock`;
- direct control suppresses conflicting autonomous TradeAI/mining behavior on the active ship;
- `PlayerShipView` exposes read-only data for camera/HUD/selection.

Docking requires real physical range and does not teleport the ship. Dock target persists as a system-qualified discovery reference. Playable schema v2 migrates Stage-12A v1 saves as undocked.

Jump travel calls the existing Stage-10 `WorldSimulation.requestFleetJump(...)` FSM. The same world-level FleetId survives transit while the local EntityId may change. After arrival, the destination becomes the full-rate active system and direct control is rebound to the materialized fleet entity.

## 12D — Manual market interaction

PR #32, main `89da8dc`.

`PlayerMarketService` reuses the same `TradeController` used by AI. It creates a synchronous, non-persistent participant proxy that:

- shares the active ship's real `InventoryComponent`;
- mirrors PlayerState wallet;
- mirrors player faction affiliation;
- mirrors player reputation;
- never enters an Ashley Engine or persistence.

The ordinary TradeController therefore remains authoritative for:

- station market access;
- reputation-adjusted prices;
- stock;
- cargo capacity;
- participant/station liquidity;
- atomic bilateral item/money transfer;
- ledger recording.

After a successful trade, cargo already exists physically on the real ship and the resulting wallet/reputation are copied back to PlayerState.

`PlayerMarketView` / `PlayerMarketItemView` expose read-only data for future presentation without letting UI mutate economic components.

## End-to-end Stage-12 acceptance

`Stage12PlayableTradeLoopAcceptanceTest` proves the complete loop with one real persistent FleetId:

```text
own existing physical ship
→ direct fixed-tick flight to station
→ physical docking
→ inspect live market
→ manual buy through TradeController
→ real cargo aboard ship
→ undock
→ Stage-10 jump transit
→ same FleetId materialized in second system
→ direct fixed-tick flight to destination station
→ physical docking
→ manual sell through TradeController
→ destination stock changes
→ player/station money conserved
→ save/load preserves player + physical cargo + reputation + destination
```

The test verifies source stock decreases, destination stock increases, cargo physically follows the fleet across systems, player/station money remains conserved for each trade and reputation changes survive persistence.

Final PR #32 CI run #866 passed **407/407 tests**, JaCoCo coverage gate, strict Javadoc and desktop packaging.

## What Stage 12 deliberately does not add

Stage 12 does not include:

- combat;
- player mining command/UI;
- general persistent multi-fleet order framework;
- player construction/station ownership;
- player faction founding;
- strategic warfare;
- procedural galaxy generation or missions.

Those remain Stage 13–20 responsibilities.

## Definition of Done

The human player owns a real fleet, controls it through the fixed simulation, docks physically, trades through the same economic controller as AI, carries actual cargo between at least two StarSystems through the existing jump pipeline and can save/load that state without introducing a separate player economy or virtual travel path. **Completed.**
