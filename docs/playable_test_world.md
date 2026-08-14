# Playable Test World

Status: manual Stage-12 test harness before Stage 13 combat work.

## Purpose

This build turns the already verified Stage-12 player core into something that can be launched and tested manually as a small game slice.

It intentionally does **not** add combat or a second player economy. The client only exposes existing production APIs:

- one existing physical `FleetId` becomes player-owned;
- WASD writes direct-control intent consumed by the fixed-tick simulation;
- docking uses physical distance through `PlayerRuntime.dockAt(...)`;
- inter-system travel uses the Stage-10 jump FSM;
- manual buying/selling uses `PlayerMarketService` and the same `TradeController` as AI;
- cargo remains in the real ship `InventoryComponent`;
- F5/F9 use the bounded atomic `PlayableWorldStateCodec` save envelope.

## Curated world

The test starts in **Anchor** with one existing cargo-capable ship roughly 70 world units from a compatible market.

The bootstrap automatically selects a real item that the ship can carry and that the same market archetype trades in both Anchor and Corona. Corona starts with a deliberate shortage of that item as an initial world condition. The ordinary `MarketSystem` then calculates prices through a real fixed tick, and the factory refuses to start if the resulting route is not profitable.

The HUD shows the exact recommended item, source market and destination market for the current build.

This initial shortage is only scenario setup. Once play begins, no special price formula, virtual transfer, free cargo or scripted income is used.

## Controls

| Key | Action |
| --- | --- |
| `W A S D` | direct fixed-tick ship movement |
| `E` | dock at nearest market when inside physical docking range / undock |
| `J` | jump between the two endpoints of the curated Anchor–Corona route |
| `Up / Down` or `[ / ]` | select tradable market item while docked |
| `B` | buy 1 selected item through `TradeController` |
| `V` | sell 1 selected item through `TradeController` |
| `Space` | pause/resume all simulation sessions |
| `1 / 2 / 3 / 4` | time scale x1 / x2 / x4 / x8 |
| `F5` | atomic save to `saves/playable-test-world.sav` |
| `F9` | load that save, including mid-transit state |
| `F2` | reset a fresh deterministic test world; does not delete the save |

The camera follows the currently materialized player ship. During jump transit the player ship is intentionally absent from local ECS and the map falls back to a galaxy-sized view until arrival materializes the same persistent `FleetId` in the destination system.

## Recommended manual acceptance pass

1. Launch the default desktop JAR.
2. Confirm the HUD shows one owned fleet, 25,000 starting credits and the Anchor recommended route.
3. Fly with WASD toward the HUD's nearest/source market.
4. Press `E` outside docking range and confirm docking is rejected with a real distance.
5. Move inside range and press `E`; confirm `[DOCKED]` and the market panel appear.
6. Select the HUD-recommended item and press `B` several times.
7. Verify ship cargo increases, station stock decreases and credits decrease.
8. Press `E` to undock, then `J` to request the normal Stage-10 jump.
9. While in transit press `F5`, then `F9`; confirm transit continues rather than teleporting or duplicating the ship.
10. Arrive in Corona; confirm the same world-level FleetId remains active and the camera follows the newly materialized local entity.
11. Fly to the recommended destination market and dock.
12. Confirm the carried item is still physically in cargo and Corona's player sell price is higher than Anchor's player buy price for the curated route.
13. Press `V` to sell the carried units; confirm cargo falls and credits rise.
14. Save again, reset with `F2`, then load with `F9`; confirm wallet, cargo, docking/travel state and active system return from the save.
15. Test pause and x1/x2/x4/x8 time scales while moving and while the economy continues around the player.

## Expected limitations

This is a test harness, not the final game UI.

- No combat yet; Stage 13 remains the next core stage.
- No player mining command UI yet; the underlying finite-resource mining economy continues to run autonomously and Stage 14 will expose the first full player progression loop.
- The test-route `J` shortcut only selects the curated Anchor/Corona endpoint. It still delegates the actual travel to the ordinary Stage-10 jump FSM.
- HUD and keyboard interaction are intentionally minimal and will be replaced by production UX later.
- Existing remote economy/faction simulation continues while the player flies and trades.

## Legacy spectator mode

The previous economy/map desktop view remains available:

```text
java -jar star-empires-1.0-SNAPSHOT-all.jar --spectator
```

Graphics validation modes remain unchanged:

```text
--graphics-spike
--asset-pack-validation
```

## Release gate

A test build is releasable only after the normal Java-17 CI passes:

- all JUnit tests;
- JaCoCo thresholds;
- strict Javadoc;
- shaded desktop JAR packaging.

The downloadable artifact must be the `-all.jar`, not the thin dependency-free JAR.
