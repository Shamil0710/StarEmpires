# Stage 9C — Destruction and Economic Shock

**Status:** COMPLETE candidate — awaiting exact-head CI

## Authoritative boundary

Stage 9C separates economic destruction from Stage-9A structural removal.

```text
validate destruction policy
        ↓
resolve inventory fate
        ↓
resolve wallet fate
        ↓
record resource/money ledger entries
        ↓
fail active construction project if its site was destroyed
        ↓
Stage-9A structural removal + reference/cache invalidation
        ↓
persistent destruction news
```

A non-empty entity is never passed directly to structural removal.

## Resource fate

`DestructionPolicy` requires one explicit `ResourceDestructionFate`:

- `DESTROY` — inventory units disappear only through `RESOURCE_SINK` entries;
- `SALVAGE` — inventory is moved into a new persistent physical `IdentityComponent.Kind.SALVAGE` entity;
- `TRANSFER_TO_ENTITY` — inventory is moved into an explicitly supplied recipient in the same StarSystem.

`EconomicTransaction.Type.RESOURCE_TRANSFER` records salvage/recipient movement without inventing a resource source or sink. Existing ledger serialization remains name-based, so older transaction types retain their binary representation.

## Money fate

`MoneyDestructionFate` is explicit:

- `SINK` — destroyed wallet balance is debited and recorded as `MONEY_SINK`;
- `TRANSFER_TO_FACTION_TREASURY` — destroyed wallet balance moves atomically to the entity owner's persistent faction treasury and is recorded as `MONEY_TRANSFER`.

All recipient/capacity/ownership constraints are validated before mutation.

## Physical salvage

Salvage is a normal persistent local ECS entity:

- identity kind `SALVAGE`;
- copied world position;
- inventory capacity equal to transferred stock;
- no magic wallet, market or production.

It is created economically empty through Stage 9A before physical resource transfer and survives ordinary save/load.

## Construction integration

Destroying a non-terminal construction site resolves its real wallet/material inventory under the same destruction policy and then transitions the owning project to `FAILED`.

The failed project preserves pre-destruction delivered-material history while clearing its site reference and wallet value. Completed construction projects are historical records: their `completedStationEntityId` may continue to refer to a station that was later destroyed without invalidating WorldState/save restoration.

## Economic shock

Destroying a market/producer:

- removes its production component/capacity immediately;
- removes its market immediately;
- delegates TradeAI/Mining persistent-reference and MarketDirectory cache invalidation to Stage 9A;
- leaves explicit ledger evidence for value fate;
- publishes deterministic game-time destruction news through the existing persistent `GlobalEventManager` pending-news queue.

The Stage-9C acceptance test destroys a real demo `station.foundry` and verifies the producer/market count drops immediately and remains absent after world save/restore. Stage 9D consumes the resulting market/supply signals for bottleneck classification; Stage 9E measures the complete recovery loop.

## Verification

Automated coverage includes:

- `DESTROY` resource/money sinks;
- `TRANSFER_TO_ENTITY` resource conservation and transfer ledger;
- persistent physical `SALVAGE`;
- owner-treasury money transfer;
- market/production removal;
- persistent destruction news;
- active construction site -> `FAILED` with delivered-history preservation;
- completed construction history after later station destruction;
- remote-system destruction/save-load;
- deterministic same-seed destruction snapshots;
- real foundry economic-shock capacity removal.

Merge requires exact-head Java 17 tests, coverage, strict Javadoc and packaging to pass.
