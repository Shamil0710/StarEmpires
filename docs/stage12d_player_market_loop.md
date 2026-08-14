# Stage 12D — Manual Player Market Loop

Status: implementation branch `agent/stage12d-manual-market-loop`.

## Goal

Expose manual player trading without creating a second economic implementation.

The player must use the same authoritative station markets, stock, liquidity, faction access, reputation price modifiers and `TradeController` operations as autonomous fleets.

## Actor boundary

Player-specific financial identity and reputation remain in `PlayerState`:

- personal wallet in milli-credits;
- optional faction/legal affiliation;
- reputation by stable faction content ID.

Physical cargo remains exclusively on the active ship's real `InventoryComponent`.

The docked station continues to own its normal:

- `MarketComponent`;
- `InventoryComponent`;
- `WalletComponent`;
- `FactionMarketAccessComponent` when materialized by strategic policy.

## Shared TradeController adapter

`PlayerMarketService` does not reimplement trading formulas or validation.

For one synchronous manual command it creates a non-persistent Ashley `Entity` proxy that:

- shares the active ship's **actual** `InventoryComponent`;
- mirrors `PlayerState.walletMilliCredits` in a temporary `WalletComponent`;
- mirrors player faction affiliation through `FactionComponent`;
- mirrors persistent player reputation through `ReputationComponent`;
- is never added to an Engine and never enters persistence.

The service then calls the ordinary `TradeController.buyFromStation(...)` or `sellToStation(...)` with the docked live station.

On success:

- cargo has already moved physically between station and active ship;
- station money has already moved through its real wallet;
- the proxy's resulting wallet/reputation are copied back into `PlayerState`;
- the existing session `EconomicLedger` contains the normal trade event.

This gives the player the same access, liquidity, capacity and reputation-price gates as AI trade while preserving the player/world persistence boundary introduced in 12A.

## Manual market view

`PlayerMarketView` and `PlayerMarketItemView` expose read-only data for future UI:

- docked station reference;
- player wallet;
- cargo used/capacity;
- market access result;
- item content/runtime IDs and display name;
- station physical stock and target stock;
- player physical cargo amount;
- player-specific buy/sell prices after reputation modifiers;
- tradable state.

Presentation therefore does not need to inspect or mutate raw economic components directly.

## Preconditions

Manual trade requires:

- initialized active player FleetId;
- active fleet locally materialized;
- persistent docking at a live market in that same system;
- valid item content ID and positive amount;
- normal TradeController access/wallet/inventory/liquidity checks;
- for purchases, the ship role must allow purchasing that item.

A rejected command leaves player wallet, station wallet, station stock and ship cargo unchanged.

## Stage-12 end-to-end acceptance

`Stage12PlayableTradeLoopAcceptanceTest` proves the complete Stage-12 Definition of Done with one real player-owned FleetId:

1. initialize player ownership of an existing physical fleet;
2. directly fly the ship through fixed-tick movement into docking range;
3. dock at a live station;
4. inspect the manual market view;
5. buy real physical cargo through the shared TradeController;
6. verify source stock decreases and money is conserved between player and source station;
7. undock;
8. request a normal Stage-10 jump;
9. preserve the same world-level FleetId through transit and destination materialization;
10. directly fly to the corresponding destination station;
11. dock and inspect carried cargo;
12. sell the same physical cargo through the shared TradeController;
13. verify destination stock increases and money is conserved between player and destination station;
14. save/load the playable state and confirm player wallet, reputation, cargo and active destination system survive.

No step uses virtual delivery, a player-only price formula, instant travel, direct UI Transform mutation, fabricated cargo or a separate player economy.
