# Stage 14A — Player Mining

> Status: **COMPLETE**
>
> Functional PR: **#39 — Stage 14A manual player mining**
>
> Functional merge on `main`: `f652b2aa0c27dcfd1f7680af79f7afd5f766b845`
>
> Final functional CI: **#942**, 418/418 tests passed.

---

## 1. Goal

Stage 14A exposes manual player mining without creating a second resource/economy implementation.

The accepted loop is:

```text
existing player-owned mining FleetId
→ direct physical flight under PlayerRuntime
→ select live finite asteroid
→ request manual extraction
→ ordinary MiningSystem validates range/equipment/capacity
→ finite asteroid reserve decreases
→ same physical units appear in the ship InventoryComponent
→ save/load preserves cargo and asteroid reserve
→ manual mining command does not persist
→ player physically docks at an ordinary market
→ PlayerMarketService / TradeController sells real cargo
→ only the sale increases player credits
```

The player adapter may choose intent and read state; it does not mutate resources, cargo, Transform or money directly.

---

## 2. Shared extraction boundary

`MiningSystem` now owns one physical extraction implementation used by both autonomous miners and player-controlled mining.

The shared extraction path validates:

- a live asteroid resolved through persistent `EntityId`;
- compatible resource type;
- non-depleted finite reserve;
- valid physical positions;
- physical extraction range;
- real free cargo capacity;
- valid extraction rate and configuration.

Extraction preserves the physical accounting relation:

```text
asteroid remaining resource + mined cargo
```

apart from subsequent ordinary transfers/sales. Manual mining does not record a resource source and does not award credits.

The existing autonomous miner still performs its historical search/travel/mine/return/sell cycle. When a ship is player-controlled, `MiningSystem` switches to command evaluation only: it never moves the ship and never auto-sells the player's cargo.

---

## 3. Player command surface

### `MiningCommandComponent`

A transient ECS command carries:

- selected asteroid `EntityId`;
- continuous extraction request;
- authoritative diagnostic status;
- whole units extracted during the latest fixed tick.

Player-facing statuses include:

- idle / ready / mining;
- no target;
- invalid target;
- incompatible resource;
- out of range;
- cargo full;
- depleted resource;
- incompatible ship/equipment role;
- invalid configuration;
- docked.

The command is intentionally not part of persistence. Save/load keeps physical consequences but does not resume an old held mining button or stale target automatically.

### `PlayerMiningService`

The service:

- resolves the current active physical player fleet/ship;
- accepts live asteroid selection;
- writes only transient manual-mining intent;
- never changes Transform, inventory, asteroid reserve or wallet directly;
- rejects unavailable player states such as docking/jump/no mining-capable active hull.

### `PlayerMiningView`

The read-only view exposes HUD/test data:

- current status/reason;
- selected target;
- configured resource;
- ship cargo and free capacity;
- target remaining finite reserve;
- physical distance and extraction range;
- units extracted in the latest tick.

This is sufficient for the later Stage 14C HUD to present meaningful mining feedback without learning simulation rules itself.

---

## 4. Player-control boundary

Stage 12 already suppresses the autonomous mining cycle on the active player ship. Stage 14A preserves that boundary.

For a player-controlled miner:

```text
Player input / UI
→ PlayerMiningService
→ MiningCommandComponent
→ MiningSystem shared extraction boundary
→ finite asteroid + real InventoryComponent
```

Movement remains:

```text
Player input
→ PlayerControlledComponent
→ PlayerDirectControlSystem
→ Transform
```

Therefore manual mining cannot pull the player's ship toward an asteroid, stop it as a side effect, return it to a station, or sell its cargo.

---

## 5. Persistence

No persistent schema change was required.

Already-persistent physical state carries the durable result:

- asteroid reserve remains in `AsteroidComponent.remainingResource`;
- mined material remains in the ship `InventoryComponent`;
- mining counters/remainder remain in `MiningComponent` where applicable.

`MiningCommandComponent` is transient. After restore the player must explicitly choose a target/request again.

This avoids stale runtime command references while preserving deterministic physical world state.

---

## 6. Tests and acceptance

### Focused manual-mining tests

`ManualMiningSystemTest` proves:

1. manual mining moves finite resource to cargo without resource creation;
2. manual mining does not take ownership of player velocity/movement;
3. out-of-range mining does not move the ship or create cargo;
4. full cargo is an authoritative rejection;
5. depletion leaves the resource aboard the ship and stops the request instead of auto-selling;
6. an incompatible controlled hull receives a readable rejection.

### Stage 14A end-to-end acceptance

`Stage14PlayerMiningAcceptanceTest` uses a real `DemoGalaxyFactory` world and real player-owned mining `FleetId`:

- the player physically flies into extraction range through `PlayerRuntime`;
- selects a live spawned finite asteroid;
- extracts real ore through `MiningSystem`;
- verifies the asteroid reserve decreases exactly by the cargo gained;
- verifies mining alone does not increase `PlayerState` wallet;
- saves and restores the playable world;
- verifies cargo and asteroid reserve survive restore;
- verifies transient manual command/target does not resume after restore;
- physically reaches/docks at a live market;
- sells through ordinary `PlayerMarketService` / shared trade core;
- verifies only that sale increases player credits and destination stock.

---

## 7. Validation evidence

PR #39 final CI #942:

- **418 tests run**;
- **0 failures**;
- **0 errors**;
- **0 skipped**;
- JaCoCo coverage gate passed;
- strict Javadoc passed;
- desktop shaded JAR packaged successfully.

CI validated the synthetic PR merge before the final GitHub merge; the functional `main` merge is `f652b2aa0c27dcfd1f7680af79f7afd5f766b845`.

---

## 8. Deliberate seams

Stage 14A does **not** yet provide the final graphical mining interaction.

Deferred to later Stage 14 work:

- HUD/minimap target presentation and contextual interaction affordances — Stage 14C;
- final keyboard/mouse binding and visual mining VFX — presentation work alongside Stage 14C/VFX track;
- cargo-mass effect on handling — planned flight-dynamics slice 14E;
- richer mining equipment/fitting/resource differentiation — later combat/content/fitting stages.

The important functional invariant is already established: manual mining is physical, finite, shared with autonomous extraction rules and economically valuable only after a real sale.

---

## 9. Transition

Stage 14A is complete.

The next core functional slice is **Stage 14B — Ship purchase / active-ship progression**:

1. buy/acquire an already-existing physical `FleetId` through real wallet/ownership transfer;
2. switch the active owned `FleetId` without recreating the entity;
3. preserve cargo, placement, ownership and persistence semantics;
4. prove progression to a more capable real ship without a debug/free upgrade grant.
