# Stage 14B — Player Ship Purchase and Active-Ship Progression

**Status: COMPLETE**

Functional implementation merged through **PR #41**.

- Functional `main`: `0b9df2d3614d0907a45b44362e52507aefa295f7`
- Final CI: **#960**
- Tests: **421 / 421 passed**, 0 failures, 0 errors, 0 skipped
- JaCoCo coverage gate: passed
- Strict Javadoc: passed
- Desktop shaded JAR packaging: passed

---

## 1. Goal

Stage 14B turns the Stage-12 ownership primitives into the first real player ship-progression seam:

```text
existing owned starter ship
→ physically dock at a seller station
→ inspect an explicit sale offer for an existing FleetId
→ pay with the real player wallet
→ seller receives real money
→ same existing physical FleetId becomes player-owned
→ undock / stop current ship
→ switch active control to the newly owned FleetId
→ continue with unchanged cargo / position / identity
→ save / load preserves ownership and active ship
```

The implementation deliberately does **not** create a replacement ship, clone an entity, teleport an asset, reset cargo, rewrite faction identity or award a progression asset for free.

---

## 2. Explicit physical sale offer

`PlayerShipSaleOffer` identifies exactly one current transaction candidate:

- `StarSystemId` containing the sale;
- persistent local `EntityId` of the seller station;
- persistent physical `FleetId` being offered;
- explicit positive price in authoritative milli-credits.

The offer is a value object only. It does not reserve, create or move the ship.

An explicit price is intentional. Stage 14B establishes the authoritative purchase/ownership path without introducing a hidden player-only valuation formula. A later shipyard/content/economic layer may generate offers and prices while continuing to use exactly the same transfer boundary.

---

## 3. Purchase diagnostics

`PlayerShipPurchaseView` exposes a read-only result for UI and tests. Current statuses include:

- `AVAILABLE`;
- `NOT_DOCKED_AT_SELLER`;
- `INVALID_SELLER`;
- `FLEET_NOT_AVAILABLE`;
- `ALREADY_OWNED`;
- `SELLER_MISMATCH`;
- `INSUFFICIENT_FUNDS`.

The view contains player-readable text plus the live ship name, archetype content ID, price and current player wallet where resolvable.

This keeps presentation code read-only: UI can inspect authoritative eligibility and submit a purchase request, but it does not mutate money, ownership or physical state itself.

---

## 4. Authoritative purchase boundary

`PlayerShipProgressionService` revalidates an offer immediately before purchase.

A purchase requires:

1. the player is physically docked at the exact seller station;
2. the seller is a live station with ordinary `MarketComponent`, `WalletComponent`, faction and identity;
3. the offered `FleetId` is still live;
4. the fleet is physically materialized `IN_SYSTEM` in the offer system;
5. the fleet is not currently in jump transit;
6. the local entity is a real ship with identity/archetype/faction data;
7. seller faction matches the offered ship's current faction;
8. the player does not already own that FleetId;
9. the player wallet can pay the explicit price.

When these conditions hold, the service delegates the actual atomic money/ownership mutation to the existing Stage-12 `PlayerOwnershipService`.

Therefore the transfer still uses:

- the real `PlayerState` wallet;
- the real seller-station `WalletComponent`;
- the ordinary local `EconomicLedger`;
- the same already-existing `FleetId`.

Money is conserved between buyer and seller. No ship-value money source/sink or debug credit path was added.

---

## 5. Physical identity is preserved

The acceptance test proves that successful purchase does not change:

- world fleet count;
- local entity count;
- candidate `FleetId`;
- candidate local entity identity;
- physical position;
- existing cargo;
- faction component.

Ownership is a relationship to the persistent asset, not a request to instantiate another copy of that asset.

---

## 6. Active owned-FleetId switching

`PlayerShipProgressionService.switchActiveFleet(...)` provides the Stage-14 direct-control progression seam.

The target FleetId must:

- already be owned by the player;
- still exist;
- be physically materialized in a StarSystem;
- not be in jump transit.

The player must also be undocked.

The current local active ship must already be physically stopped before control can be released. The progression service intentionally does **not** zero `Transform.velocity` directly. If the ship is moving, callers use the existing `PlayerRuntime.stopMovement()` intent and advance an ordinary fixed tick before switching.

Before releasing the old direct-control binding, transient combat and manual-mining intents are cleared. No persistent cargo/economic/world state is discarded.

The new active FleetId is applied through the existing `PlayerRuntime`/`PlayerState` reconciliation path. Runtime synchronization removes `PlayerControlledComponent` from the previous active ship and attaches it to the new one.

A ship in jump transit cannot be selected for direct control until it physically materializes again.

---

## 7. Acceptance coverage

`Stage14ShipProgressionAcceptanceTest` covers three vertical scenarios.

### 7.1 Physical purchase, switch and continuation

```text
existing starter FleetId
→ place it physically at seller for the test setup
→ dock through PlayerRuntime.dockAt
→ inspect valid sale offer
→ purchase
→ player wallet decreases
→ seller station wallet increases
→ total buyer + seller money conserved
→ existing candidate FleetId becomes owned
→ fleet/entity counts unchanged
→ candidate cargo and position unchanged
→ undock
→ switch active FleetId
→ PlayerControlled binding moves to candidate
→ save / encode / decode / restore
→ both owned FleetIds survive
→ selected active FleetId survives
→ player money, candidate cargo and position survive
```

### 7.2 Invalid / unaffordable offer

The acceptance verifies that not being docked at the seller and insufficient player funds both reject the purchase atomically. Seller money, player money and ownership remain unchanged.

### 7.3 Safe active switching

The acceptance verifies that:

- a moving active ship cannot be released immediately;
- stopping through ordinary direct-control intent + fixed tick permits switching;
- an unowned FleetId cannot become active.

---

## 8. Deliberate seams

Stage 14B does **not** yet implement a full shipyard inventory/pricing simulation or final station purchase UI.

Those are intentionally separate concerns:

- future shipyard/content/economic systems may generate `PlayerShipSaleOffer` values;
- Stage 14C can surface purchase status and owned-ship selection in the presentation layer;
- later fleet stages can give inactive owned ships autonomous orders instead of making direct control the only useful ownership mode.

The important Stage-14B invariant is already complete: **real earned money can acquire a real existing asset, and direct player control can move between real owned FleetIds without recreating or teleporting them.**
