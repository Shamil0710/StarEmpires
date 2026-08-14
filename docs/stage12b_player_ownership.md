# Stage 12B — Player Ownership

Status: implementation branch `agent/stage12b-player-ownership`.

## Ownership model

Player ownership is a world-level relationship expressed by `PlayerState.ownedFleetIds`.

It is intentionally **not** encoded by `FactionComponent`. A player-owned ship may remain legally affiliated with a faction, become independent later, or participate in future faction systems without changing the meaning of ownership.

```text
FleetId -> physical fleet identity
FactionComponent -> legal/faction context
PlayerState.ownedFleetIds -> player ownership
```

These concepts are independent.

## Atomic purchase and sale

`PlayerOwnershipService` transfers ownership of an already existing `FleetId`.

Purchase:

1. validate the FleetId exists and is not already owned;
2. validate player funds and seller wallet capacity;
3. construct the post-transfer PlayerState before mutation;
4. transfer money from player wallet to the existing counterparty wallet;
5. replace persistent ownership state;
6. record a normal `MONEY_TRANSFER` ledger entry.

Sale performs the inverse operation. Selling the active fleet selects the lowest remaining owned FleetId, or clears active ship when no owned fleets remain.

No purchase/sale operation creates or destroys an entity. Fleet placement count and local entity count therefore remain unchanged.

## Failure semantics

Insufficient funds, unknown FleetId, duplicate ownership, missing buyer liquidity or wallet overflow return failure without changing ownership or either wallet.

Unexpected post-transfer state/ledger failures execute rollback of both player ownership and the counterparty money transfer.

## Destruction reconciliation

`PlayerRuntime` reconciles `ownedFleetIds` against the authoritative world fleet layer on:

- player-state access;
- frame advancement;
- playable snapshot.

A physically destroyed fleet therefore cannot remain persistently player-owned. If the active fleet is destroyed, the next canonical surviving owned FleetId becomes active; otherwise active ship becomes `null`.

## Acceptance

Stage-12B coverage verifies:

- purchase moves money and ownership atomically;
- sale performs the inverse transfer;
- player + counterparty money is conserved;
- purchase/sale do not change physical fleet/entity counts;
- faction/legal context on the fleet is unchanged by ownership transfer;
- ownership persists through playable save codec;
- failed purchase leaves money and ownership unchanged;
- physical destruction removes stale player ownership and active-ship reference.
