# Star Empires — Post-Stage-15 Inertia and Jump Hardening

> Status: **COMPLETE**
>
> Functional merge: **PR #51**
>
> Merged main: `a32584a928d97a014dd2cbb32fdeaed4fe0c65eb`
>
> Validation: **CI #1151**, run `31826504541`, **454/454 tests passed** plus strict Javadoc, JaCoCo and shaded desktop packaging.

---

## 1. Purpose

This hardening slice closes the movement debt intentionally left after Stage 14E and repairs two Stage-15 completion seams discovered by a clean CI run and manual playtesting.

The resulting invariant is now:

```text
player direct input
→ PlayerDirectControlSystem
→ FlightDynamics

delegated player fleet order
→ FlightCommandComponent
→ AutonomousFlightSystem
→ FlightDynamics

generic TradeAI
→ FlightCommandComponent
→ AutonomousFlightSystem
→ FlightDynamics

generic autonomous Mining
→ FlightCommandComponent
→ AutonomousFlightSystem
→ FlightDynamics
```

Ordinary local ship movement no longer has a generic TradeAI/Mining path that directly writes position or velocity.

Structural transfer boundaries — save/restore, jump detach/attach and destruction/materialization — remain separate from ordinary flight and may assign materialization state explicitly.

---

## 2. Generic NPC inertia debt — CLOSED

Before PR #51, generic `TradeAISystem` and legacy autonomous `MiningSystem` still contained direct local Transform movement even though player and Stage-15 delegated fleets already had the shared inertial path.

PR #51 introduces `InertialNavigation` as a shared intent helper. It never integrates Transform. It only submits a `FlightCommandComponent` and reports whether the ship is:

- approaching;
- braking;
- physically arrived;
- invalid for navigation.

`AutonomousFlightSystem` now runs as the late-tick integration boundary after generic AI/order intent has been written. The actual local movement step remains `FlightDynamics.advance(...)`.

Consequences:

- generic NPCs accelerate instead of snapping to targets;
- generic NPCs brake physically before market/mining interaction;
- cargo mass affects generic NPC acceleration through the real ship Inventory;
- a loaded and empty copy of the same trader hull no longer have identical acceleration;
- zero propulsion cannot teleport a miner to its target;
- player and AI no longer have different normal-flight physics.

`GenericNpcInertiaAcceptanceTest` explicitly proves partial first-tick acceleration and lower acceleration for an otherwise identical loaded trader.

---

## 3. Flight-intent ownership

A physical ship can contain old economic AI components while being owned and delegated by the player. After the generic NPC migration this exposed a real conflict: Stage-15 order execution could write one `FlightCommandComponent`, then legacy economic AI could overwrite it later in the same tick.

`DelegatedFleetComponent` is now a transient runtime ownership marker.

Rules:

```text
active player FleetId
→ PlayerControlledComponent owns local movement intent

inactive player-owned FleetId
→ DelegatedFleetComponent
→ Stage-15 order executor owns local movement intent

ordinary non-owned NPC FleetId
→ generic TradeAI / Mining owns local movement intent
```

The marker is not persistent. `PlayerState`, ownership and durable fleet orders remain authoritative.

When a ship leaves player ownership, normal AI state is restored and any stale delegated marker is released; the former player ship can resume ordinary NPC autonomy.

---

## 4. Stage-15 formation-order repair

The Stage-15 completion documentation claimed real `FOLLOW`, `ESCORT` and `PATROL`, but the checked implementation still resolved these orders to HOLD. PR #51 closes that discrepancy.

### FOLLOW

- resolves the live target `FleetId` each decision;
- uses physical movement only;
- maintains a local separation radius;
- follows inter-system target transit through normal route/jump execution.

### ESCORT

- physically follows the protected FleetId;
- does not alter protected ship thrust or acceleration;
- can reduce route vulnerability only when the escort is player-owned, operational and physically co-located with the protected ship;
- does not rewrite observed system/link danger.

Advanced combat formations, screening, focus fire and weapon-aware escort tactics remain gated by Stage 17.5 / Stage 18 combat depth.

### PATROL

- cycles the persistent patrol-system list;
- physically dwells at each reached system;
- chooses the next route through the same cumulative-risk planner;
- uses the ordinary Stage-10 jump FSM;
- does not clone or teleport a patrol fleet.

---

## 5. `J` jump semantics

Manual testing exposed an important presentation bug around jump arrival.

The `J` key does **not** represent an instant teleport. It requests the existing Stage-10 authoritative finite-state transition:

```text
J pressed
→ MOVING_TO_JUMP
→ JUMP_PENDING
→ IN_TRANSIT
→ ARRIVING
→ direct local control resumes
```

During `IN_TRANSIT`, the same persistent FleetId is detached from the origin local ECS session. At the transition out of transit, the same FleetId is materialized in the destination session.

For the current test topology, Anchor → Corona is approximately 13 fixed ticks, or about **1.3 simulation seconds at x1**.

### 5.1 Why the ship previously appeared off-center

Stage-10 callers historically used local arrival `(0,0)` as a placeholder. The playable local world is currently bounded to:

```text
0 .. 2000  X
0 .. 1400  Y
```

The camera is also bounded so its viewport stays inside the local world. Therefore a ship materialized at `(0,0)` is in a map corner and cannot simultaneously appear at the visual screen center.

### 5.2 Canonical arrival anchor

PR #51 introduces `LocalSystemCoordinates`:

```text
ARRIVAL_X = WORLD_WIDTH  / 2 = 1000
ARRIVAL_Y = WORLD_HEIGHT / 2 = 700
```

The historical exact `(0,0)` jump placeholder is canonicalized to `(1000,700)`. Explicit non-zero materialization coordinates remain exact.

Strategic MOVE uses the same canonical local arrival point.

`PlayerJumpArrivalAcceptanceTest` proves:

- the jump begins through the real FSM;
- detached `IN_TRANSIT` is actually observed;
- the same FleetId arrives in the destination system;
- active-system tracking follows the player ship;
- structural arrival starts with zero local velocity;
- the ship is at `(1000,700)`;
- `WorldMapLayout` maps the ship to the exact center of the player viewport.

---

## 6. Closed and remaining movement seams

Closed by PR #51:

- generic TradeAI direct local position movement;
- generic Mining direct local position movement;
- different normal-flight acceleration rules between player and generic economic NPCs;
- cargo-insensitive generic NPC acceleration;
- Stage-15 FOLLOW/ESCORT/PATROL HOLD fallback;
- corner arrival for default J/strategic jumps.

Still intentionally future work:

- real per-item cargo mass instead of Stage-14 normalized `1 inventory unit = 1 mass unit`;
- equipment, armor and ammunition mass;
- rotational inertia / facing;
- propulsion damage;
- richer combat formation and weapon-aware tactical behavior.

Those remaining items belong to fitting/combat-depth work, not to the closed generic translational-movement debt.
