# Stage 9A — Entity Lifecycle Infrastructure

**Status:** COMPLETE candidate  
**Stage:** 9A  
**Purpose:** establish one authoritative runtime create/remove boundary before construction and destruction mechanics depend on mutable entity counts.

## Architecture

Local lifecycle is owned by `EntityLifecycleService`, which is created by every `SimulationSession` from the same:

- Ashley `Engine`;
- deterministic `EntityIdAllocator`;
- tracked `EntityRegistry`.

Creation sequence:

```text
detached economically-empty Entity
        ↓
validate no pre-existing EntityId
        ↓
allocate deterministic EntityId
        ↓
attach EntityIdComponent
        ↓
Engine.addEntity
        ↓
EntityRegistry listener registers runtime mapping
```

Structural removal sequence:

```text
persistent EntityId
        ↓
resolve through EntityRegistry
        ↓
verify economically empty
        ↓
clear TradeAI / Mining persistent references
        ↓
invalidate transient MarketDirectory / route-search caches
        ↓
Engine.removeEntity
        ↓
EntityRegistry listener unregisters runtime mapping
```

The whole removal path is synchronous, so a save snapshot cannot observe an entity as absent while another persistent component still points at its removed ID.

## Conservation boundary

Stage 9A intentionally does **not** implement economic destruction.

`createEntity` and structural `removeEntity` reject entities that contain:

- non-zero wallet balance;
- any inventory stock;
- remaining asteroid resource.

This prevents lifecycle infrastructure from silently acting as a money/resource source or sink.

The distinction is intentional:

- Stage 9A: structural create/remove of economically empty runtime objects;
- Stage 9C: destruction of non-empty stations/ships with explicit `DESTROY / SALVAGE / TRANSFER` fate and ledger accounting.

Bootstrap and the existing asteroid spawner remain separate explicit source paths: bootstrap defines the initial world state, while asteroid spawning already records `RESOURCE_SOURCE` in the economic ledger.

## Persistent-reference invalidation

### Trade AI

When a removed ID is used as `buyStationId`, `sellStationId` or `targetStationId`:

- route fields are reset;
- state returns to `IDLE`;
- route-search cooldown becomes zero so replanning is allowed immediately.

`TradeAISystem.invalidateAfterEntityRemoval(...)` also removes a removed fleet's failed-search cache entry. If a market was removed, `MarketDirectory.invalidate()` immediately drops station snapshots, supplier/consumer indexes and opportunity shortlists while advancing market revision.

### Mining

When the removed ID is the current asteroid target:

- `targetAsteroidId` is cleared;
- fractional extraction progress is cleared;
- active `TRAVEL_TO_ASTEROID` / `MINING` state returns to `SEARCHING`.

When the removed ID is the saved home base:

- `homeBaseId` is cleared;
- active `UNLOADING` moves back to `RETURNING_TO_BASE` so a replacement market can be selected.

## Session and world APIs

`SimulationSession` now exposes:

```text
createEntity(Entity)
removeEntity(EntityId)
```

`WorldSimulation` delegates the same operations to any known `StarSystemId`, so mutable entity counts work identically for:

- the active full-rate system;
- remote coarse-simulation systems.

Entity IDs remain local to each `SimulationSession`, consistent with the current Stage-7 world model. Stage 10 owns the future cross-system fleet identity/transit model.

## Verification

Automated coverage includes:

1. deterministic runtime ID allocation;
2. immediate registry registration;
3. create → snapshot → restore with exact state;
4. allocator continuation after restore;
5. rejection of already-live/pre-identified entities;
6. rejection of non-empty create/remove operations;
7. immediate TradeAI route-reference cleanup;
8. immediate Mining home-base cleanup before snapshot;
9. removed entity absent from session snapshot and restore;
10. active-system create/save/load/remove;
11. remote-system create/save/load/remove;
12. structural lifecycle does not append economic ledger entries;
13. two independent sessions with the same seed and lifecycle commands produce identical IDs and final snapshots.

A merge is allowed only after the latest ordinary branch commit passes GitHub Actions Java 17 tests, coverage/Javadoc and desktop packaging. This final documentation commit intentionally provides that exact-head CI trigger after the one-shot roadmap automation.

## Definition of Done result

Stage 9A establishes the required lifecycle seam without changing the persistent schema or adding a second simulation model.

The next active substage is **Stage 9B — Persistent Construction Project**. Construction must use this lifecycle boundary and must not bypass its conservation rules with direct unaccounted runtime spawn.
