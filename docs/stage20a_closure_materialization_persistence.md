# Stage 20A Closure — Persistent Physical Materialization State

**Status:** ACCEPTED — exact-head implementation CI green; final merge gate pending  
**Parent:** Stage 20A Closure / Readiness Remediation  
**Workstream:** 1.2 — Persistent physical save/load integration  
**Date:** 2026-08-19

## 1. Purpose

Workstream 1.1 established a reversible in-memory Stage-20 materialization boundary. This slice closes the next persistence layer: a dematerialized entity and its hierarchical physical kinematics must survive deterministic binary save/load without changing the existing core `GameStateCodec v4` format.

The design deliberately separates:

```text
core GameState v4
= existing ECS / economy / clock / RNG / ledger authority

Stage-20 materialization envelope v1
= core GameState v4 bytes
+ explicit hierarchical physical-state sidecar
```

This avoids forcing every existing save/migration path through a new core schema solely because Stage 20 introduces far-coordinate physical authority.

## 2. Why the core GameState schema remains unchanged

`GameState.CURRENT_VERSION` remains:

```text
4
```

and `GameStateCodec` remains the existing deterministic `STEM` codec.

Stage-20 far-coordinate state must not be reconstructed from legacy float `TransformComponent` fields, but changing the core schema would affect all historical save compatibility and acceptance paths.

The Stage-20 persistence layer therefore uses a separate envelope with its own magic/version:

```text
magic = S20M
envelope version = 1
```

The embedded GameState is encoded/decoded by the unchanged production `GameStateCodec`.

## 3. Complete persistent entity set

A normal `SimulationSession.snapshot()` sees current Ashley runtime entities.

A dematerialized Stage-20 entity intentionally has no Ashley representation, so the Stage-20 capture boundary instead uses:

```text
Stage20MaterializationService.snapshotAllPersistentEntities()
```

which deterministically combines:

- live `EntityState` captures;
- retained dematerialized `EntityState` snapshots.

The resulting full entity set replaces only the `entities` field of an otherwise ordinary `GameState` snapshot. Clock, RNG, ledger, event state, ID allocator state and system timers remain exactly the ordinary session values.

Thus dematerialization does not make an entity disappear from persistence merely because it is absent from the current Engine.

## 4. Physical sidecar authority

`Stage20MaterializationPersistentState` stores a deterministic list of:

```text
EntityId
LocalPhysicalKinematics
```

where physical kinematics contain:

```text
long cellX
long cellY
double offsetXM
double offsetYM
double velocityXMps
double velocityYMps
```

The envelope validates that every physical `EntityId` also exists in the embedded full `GameState.entities` set.

It rejects:

- duplicate persistent entity IDs;
- duplicate physical sidecar IDs;
- physical state for an entity absent from the GameState;
- unsupported envelope or core GameState versions.

No physical sidecar row can create a ghost entity outside the normal persistent world state.

## 5. Representation level is intentionally not persisted

The envelope does **not** store:

```text
DORMANT
STRATEGIC
ACTIVE_LOCAL
TACTICAL
```

as gameplay authority.

These are computational relevance states and may be recomputed after load from the current world/player/interaction context.

Therefore restore semantics are:

```text
1. restore complete GameState as ordinary live runtime entities;
2. create Stage20MaterializationService;
3. register exact physical sidecar state by the same EntityId;
4. let the future relevance scheduler immediately reclassify/dematerialize as appropriate.
```

This avoids stale LOD state becoming a save-game rule while preserving every causal value required to reproduce the entity.

## 6. Deterministic binary format

`Stage20MaterializationPersistenceCodec` writes in fixed order:

```text
S20M magic
file format version
envelope version
embedded GameState byte length
embedded GameStateCodec bytes
physical entity count
sorted physical rows:
  EntityId
  cellX
  cellY
  offsetX
  offsetY
  velocityX
  velocityY
```

The codec applies bounded size/count validation, rejects trailing bytes and supports atomic file replacement where the filesystem permits it.

Encoding the same state twice must produce identical bytes.

## 7. Restore invariants

A full Stage-20 save/load round-trip must preserve:

- stable `EntityId`;
- exact supported `EntityState` values;
- inventory / market / engineering / damage / ownership data represented by current EntityState;
- hierarchical physical cells and offsets exactly;
- double physical velocity exactly;
- clock / RNG / ledger / events / allocator state through ordinary GameState v4;
- total persistent entity count, including entities that were dematerialized at capture time.

It must not:

- allocate a replacement persistent ID;
- refill consumables;
- repair damage;
- reset orders by recreating an archetype from scratch;
- infer physical coordinates from legacy floats;
- require the entity to remain dematerialized immediately after load.

## 8. Production seams

Added:

- `Stage20MaterializationPersistentState`;
- `Stage20MaterializationPersistence`;
- `Stage20MaterializationPersistenceCodec`;
- `Stage20MaterializationPersistenceTest`.

Extended:

- `Stage20MaterializationService.snapshotPhysicalStates()` for deterministic live+dormant physical-sidecar capture.

The existing `GameState` and `GameStateCodec` source formats are not modified.

## 9. A.9 calibration update

The former A.9 unresolved item:

```text
no_production_lossless_local_to_persistent_dematerialization_service
```

is retired by Workstreams 1.1 + 1.2.

A.9 still retains the actual remaining blocker:

```text
no_production_persistent_to_local_materialization_scheduler_with_bounded_wake_latency
```

and numeric `ACTIVE_LOCAL` / `TACTICAL` activation bands remain unresolved.

Therefore:

```text
MATERIALIZATION_LOD_CLOSURE
= still BLOCKING_STAGE20B_ENTRY
```

This slice closes persistent causal continuity, not scheduler relevance policy.

## 10. Regression acceptance

Exact-head Java-17 implementation CI completed successfully before this status finalization.

The accepted tests prove:

- a dematerialized entity is still present in the captured full GameState entity set;
- huge hierarchical position and double velocity survive binary encode/decode exactly;
- encoding is deterministic;
- file write/read round-trips exactly;
- restore returns the same persistent EntityId and value-equal EntityState;
- restored physical authority exactly equals pre-save physical authority;
- representation may return live after load without changing causal state;
- orphan physical sidecar IDs are rejected;
- ordinary `GameStateCodec v4` remains independently round-trippable and its current schema version remains 4.

## 11. Immediate next slice

After final exact-head CI and merge acceptance, continue Workstream 1 with **1.3 — production relevance scheduler and physically closed activation bands**.

The scheduler must decide when to promote/demote representation using authoritative relevance and explicit physical interaction envelopes. Its accepted bounded wake-latency semantics will feed A.9:

```text
activation distance
= interaction envelope
+ maximum closing speed × bounded wake latency
```

No viewport, render radius or tactical probe may substitute for those inputs.
