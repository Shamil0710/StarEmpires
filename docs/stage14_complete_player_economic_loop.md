# Stage 14 — First Complete Player Economic Loop

**Status: COMPLETE**

Functional completion merge: **PR #45**, main `0393eccf790269651bcedbdfd8e4eaf8b60ca06a`.

Final validation for the remaining Stage 14D/14E work: **CI #1010**, workflow run `31811876633`.

- 431/431 tests passed;
- 0 failures, 0 errors, 0 skipped;
- strict Javadoc passed;
- JaCoCo line/branch gates passed;
- shaded desktop JAR packaging passed;
- integrated first-hour acceptance simulated a full 3600 seconds of world time and passed.

Stage 14 closes milestone **v0.3 — Playable Space Sandbox**. The milestone now has one coherent physical player loop rather than isolated technical primitives.

---

## 1. Completed slices

### 14A — Player mining

Completed by PR #39.

The player selects a real finite asteroid, submits transient manual mining intent, and the ordinary `MiningSystem` performs extraction. Resources decrease on the asteroid and increase in the active ship's real `InventoryComponent`. Mining itself does not create credits; value is realized only by an ordinary market sale through `PlayerMarketService` / `TradeController`.

### 14B — Real ship purchase and active-ship progression

Completed by PR #41.

`PlayerShipProgressionService` purchases an already-existing physical `FleetId` through the existing ownership/money-transfer boundary. The offered ship is not cloned, respawned, reset or teleported. Its identity, position and cargo survive ownership transfer. Active-ship switching changes which already-owned FleetId receives direct control without recreating the entity.

Stage 14 intentionally keeps the sale price explicit. The future generated valuation model is tracked in `docs/ship_pricing_roadmap.md` and must eventually derive price from live materials/components, fitting, condition, local market pressure, seller relations and seller-faction margin rather than replacing the physical transfer path.

### 14C — Playable navigation, HUD and local minimap

Completed by PR #43; completion record: `docs/stage14c_playable_navigation.md`.

The desktop playable harness now provides bounded mouse-wheel zoom, active-ship camera follow, screen-space HUD, a local-system minimap from authoritative ECS state, zoom-based declutter, readable combat/mining/economy feedback and ownership-aware local presentation. Presentation remains read-only and may only submit ordinary gameplay commands.

### 14E — Inertial flight-dynamics baseline

Completed by PR #45.

`PlayerDirectControlSystem` no longer assigns desired velocity instantaneously. It delegates to shared `FlightDynamics`, which applies finite fixed-tick acceleration and braking.

The Stage-14 physical profile uses:

```text
dry hull / structure mass
+ real cargo mass
= total translational mass

available thrust / total mass
= current acceleration

available braking thrust / total mass
= current braking acceleration
```

For this first baseline, one physical cargo inventory unit contributes one normalized mass unit. This is deliberately a compatibility seam, not the final content model. Stage 17.5 may replace it with data-driven per-item, equipment, armor and ammunition masses without changing the flight-controller contract.

Consequences are already emergent rather than scripted:

- an empty freighter accelerates and brakes better than the same loaded freighter;
- a light combat ship responds faster than a heavily loaded material carrier;
- releasing movement input requests zero desired velocity and invokes finite counter-thrust instead of setting velocity to zero;
- movement remains game-friendly and may use flight assist, but assist cannot bypass the physical acceleration/braking limits.

`FlightCommandComponent` and `AutonomousFlightSystem` establish an equivalent autonomous/local movement executor over exactly the same `FlightDynamics` boundary. Stage 15 should consume this seam for owned autonomous orders and progressively retire legacy direct-position movement instead of inventing a second AI physics model.

`PlayerFlightService` / `PlayerFlightView` expose read-only speed, dry/cargo/total mass, acceleration, braking acceleration and estimated stopping time/distance for diagnostics/HUD/tuning.

Deterministic Stage-14E acceptance proves:

1. loaded and empty copies of the same freighter have different acceleration/braking;
2. braking is non-instantaneous;
3. a light combat ship responds faster than a loaded heavy carrier;
4. equivalent player and autonomous movement intent produces identical position/velocity evolution under the same physical profile.

Existing Stage-12 travel/direct-control and Stage-14A/14B tests stayed green after the movement replacement.

### 14D — First-hour integrated acceptance and telemetry

Completed by PR #45.

`Stage14TelemetryTracker` and `Stage14TelemetryReport` read ordinary simulation/player state and collect:

- elapsed simulation time;
- initial/final wallet and net wallet change;
- normalized credits/hour;
- ordinary trade contribution;
- mined-cargo sale contribution;
- real ship-purchase spending;
- physical travel time;
- manual mining time;
- combat time;
- idle time;
- average/peak cargo utilization;
- physical owned-FleetId losses;
- observed hull/shield durability loss;
- time to first real ship-progression event.

Telemetry never awards money, changes cargo, moves a ship, mines resources or applies damage. Wallet contribution methods only classify deltas after ordinary authoritative commands already changed the world.

---

## 2. Integrated first-hour proof

`Stage14FirstHourAcceptanceTest` executes one deterministic continuous scenario using production simulation APIs:

```text
existing starter FleetId
→ inertial physical flight to source market
→ physical docking
→ ordinary TradeController-backed purchase
→ undock + physical braking
→ Stage-10 jump transit
→ inertial flight + docking at destination
→ ordinary sale
→ buy an already-existing mining FleetId
→ switch active control without respawn/teleport
→ inertial flight to a finite compatible asteroid
→ extract real finite resource through MiningSystem
→ fly to a solvent market
→ ordinary mined-cargo sale
→ buy an already-existing combat FleetId
→ switch active control
→ fight through shared Stage-13 combat pipeline
→ normal destruction/salvage consequence
→ continue the living world to 3600 simulation seconds
→ save / encode / decode / restore
→ continue inertial direct flight after load
```

The test does not freeze the world around the player. During development an earlier run demonstrated that live AI competitors could change destination warehouse capacity while the player physically travelled. The acceptance was corrected by using a deliberately small one-unit trade transaction rather than reserving capacity or creating a player-only market exception. This preserves the design invariant that the player operates inside the same living economy as everyone else.

The combat encounter fixture creates a normal physical data-driven combat entity with reduced current durability so the acceptance remains bounded. It grants no money/resources and damage/destruction still runs through the shared combat/destruction path.

At the end of the hour the acceptance requires, among other checks:

- positive realized trade contribution;
- positive realized mining contribution;
- real spending on at least two ship-progression purchases;
- non-zero travel, mining, combat and idle time;
- non-zero cargo utilization;
- no unexpected owned-asset loss in the curated scenario;
- real observed combat damage;
- first ownership progression before the hour ends;
- at least three physically owned FleetIds;
- finite credits/hour;
- save/load preservation of wallet, ownership and active FleetId;
- successful continued inertial motion after restore.

CI #1010 ran this complete 3600-second simulation successfully.

---

## 3. Stage 14 DoD result

The target loop is now mechanically proven:

```text
read local opportunity
→ physically travel
→ trade / mine / fight
→ gain or preserve real economic value
→ purchase an existing real ship
→ switch to that ship
→ continue play in the same persistent world
```

The player does not receive:

- debug income;
- virtual cargo delivery;
- instant travel;
- player-only mining yield;
- player-only combat damage;
- free replacement ships;
- a separate market-price formula;
- a presentation/UI mutation path around authoritative gameplay rules.

**Stage 14 is complete. Milestone v0.3 is complete.**

---

## 4. Deliberate seams carried into later stages

These are not Stage-14 blockers and must not be misreported as implemented:

### Stage 15 movement migration

Legacy autonomous `TradeAISystem` / `MiningSystem` local movement still contains compatibility-era direct movement. Stage 15 player fleet/autonomous-order work should route owned autonomous `MOVE`, `TRADE`, `MINE`, `ESCORT`, `PATROL` and `FOLLOW` execution through the shared `FlightCommandComponent` / `AutonomousFlightSystem` / `FlightDynamics` seam where local high-fidelity movement is required.

### Stage 15 civilian risk behavior

Cumulative danger scoring remains planned for Stage 15. AI must evaluate danger across the entire traversed route — systems and links — using actor-specific cargo/ship value, damage, mobility, escort and knowledge freshness. See `docs/cumulative_route_risk_model.md`.

### Stage 17.5 combat depth / fitting

Per-item/equipment/armor/ammunition mass, richer propulsion/fitting, armor, richer shields and distinct weapon families belong to Stage 17.5. Advanced tactical AI remains gated until those capability variables exist.

### Strategic/global map

Stage 14 has a functional local minimap and readable playable HUD. The global fleet/empire map grows with Stage 15–18 rather than being faked ahead of the corresponding systems.

### Ship valuation

The Stage-14 purchase path accepts an explicit positive sale price. Generated live-economy valuation remains required later and is specified in `docs/ship_pricing_roadmap.md`.

---

## 5. Next stage

The next active core stage is **Stage 15 — Player Fleets / Autonomous Orders**.

Its first implementation tranche should deliberately build on Stage 14 rather than bypass it:

1. persistent player fleet/order model;
2. `HOLD` / `MOVE` command execution through shared inertial movement;
3. `TRADE` / `MINE` autonomous economic orders reusing existing planners/controllers;
4. `ESCORT` / `PATROL` / `FOLLOW` group movement and cohesion;
5. civilian flee/suspend/resume/replan behavior;
6. cumulative whole-route risk scoring and actor-specific risk tolerance;
7. first functional global-map layer for owned fleet selection/orders/routes;
8. persistence/determinism/physical-economy acceptance throughout.
