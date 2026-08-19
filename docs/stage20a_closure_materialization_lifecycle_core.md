# Stage 20A Closure — Lossless Materialization Lifecycle Core

**Status:** ACCEPTED — exact-head runtime-core CI green; final merge gate pending  
**Parent:** Stage 20A Closure / Readiness Remediation  
**Workstream:** 1 — Code-first physical continuity  
**Date:** 2026-08-19

## 1. Purpose

The Stage-20A readiness gate blocks Stage 20B while materialization/LOD is not physically closed. The first remediation slice establishes a reversible runtime representation boundary without inventing ship, station, sensor or distance data.

This slice is intentionally narrower than the whole blocker:

```text
1.1 reversible runtime materialization core
→ this slice

1.2 persistent physical save/load integration
→ still required

1.3 relevance scheduler + physically closed activation bands
→ still required
```

Therefore acceptance of this slice does **not** change `MATERIALIZATION_LOD_CLOSURE` to satisfied by itself.

## 2. Structural deletion remains separate

`EntityLifecycleService.remove(...)` remains the authoritative structural-deletion path.

Stage-20 dematerialization must not call it because structural deletion:

- requires an economically empty entity;
- invalidates persistent references;
- unregisters the entity as no longer existing;
- represents actual destruction/removal from the world.

`Stage20MaterializationService.dematerialize(...)` instead:

1. captures the complete supported `EntityState`;
2. verifies that separate Stage-20 physical kinematics exist;
3. removes only the Ashley runtime representation through the Engine;
4. allows `EntityRegistry` to drop the temporary live-object mapping through its normal listener;
5. retains persistent ECS state and physical kinematics under the same `EntityId`.

The entity still exists authoritatively even though it has no current Ashley object.

## 3. Physical authority

The new `LocalPhysicalKinematics` value contains:

```text
LocalPhysicalPosition position
velocityXMps double
velocityYMps double
```

`LocalPhysicalPosition` is the accepted Stage-20A.8 hierarchical coordinate representation:

```text
long numerical cell
+ double normalized local offset
```

The materializer refuses to dematerialize an entity that has no registered Stage-20 physical kinematics.

It never reconstructs far-coordinate position/velocity from legacy float `TransformComponent` values.

This prevents an LOD transition from silently downgrading physical authority back to the old bounded-demo coordinate seam.

## 4. Reversible runtime round-trip

The accepted in-memory sequence is:

```text
live Ashley Entity
+ stable EntityId
+ EntityState
+ LocalPhysicalKinematics

→ dematerialize

no Ashley Entity
EntityRegistry live lookup absent
same EntityId retained conceptually
same EntityState retained
same LocalPhysicalKinematics retained

→ materialize

new Ashley Entity instance
same stable EntityId
same supported persistent component values
same LocalPhysicalKinematics
```

Runtime object identity is deliberately not persistent authority. A newly materialized Ashley `Entity` may be a different Java object.

## 5. Strategic physical updates while dematerialized

The service permits `updatePhysicalState(...)` while no Ashley entity exists.

This is required for the accepted scalability model:

```text
DORMANT / STRATEGIC / ACTIVE_LOCAL
```

must be capable of advancing authoritative consequences without forcing full tactical materialization merely to move an object.

This slice does not yet implement the strategic movement scheduler itself. It provides the state boundary that such a scheduler can safely update.

## 6. Persistent entity snapshot seam

`Stage20MaterializationService.snapshotAllPersistentEntities()` returns a deterministic `EntityId`-sorted union of:

- current live Ashley entity snapshots;
- retained dematerialized `EntityState` snapshots.

Thus dematerialization no longer implies that a future save routine must lose the entity merely because it is absent from `Engine.getEntities()`.

However current `GameStateCodec` still has no persistent Stage-20 physical-transform field. Therefore the service does **not** yet claim binary save/load closure.

The next slice must extend the versioned persistence schema so hierarchical physical position and double velocity survive save/load without being reconstructed from legacy float transform values.

## 7. Wake-latency semantics

`materialize(...)` is synchronous:

```text
SYNCHRONOUS_WAKE_LATENCY_SIMULATION_SECONDS = 0
```

This means that once a caller invokes the materialization boundary, the new runtime entity is registered before the method returns and no simulation-time tick is consumed by the transition itself.

This is not yet a complete A.9 activation-band closure because no accepted relevance scheduler currently guarantees **when** the call is made relative to a future interaction envelope.

A later scheduler may keep the zero-simulation-time materializer or wrap it in a bounded queue; the measured/accepted scheduler latency, not an arbitrary map radius, must feed A.9.

## 8. Regression invariants

The new tests require:

- dematerialization refuses to proceed without Stage-20 physical kinematics;
- registry lookup disappears while the runtime representation is absent;
- the full persistent entity count remains unchanged through the service snapshot seam;
- retained `EntityState` is value-identical to the pre-dematerialization state;
- huge hierarchical position and double velocity remain exact;
- physical state can advance while dematerialized;
- materialization returns a new Ashley object with the same stable ID;
- supported persistent ECS state after materialization equals the original state exactly;
- physical kinematics remain unchanged unless explicitly updated;
- the materialization method consumes zero simulation time by contract.

## 9. Machine implementation

Added:

- `LocalPhysicalKinematics`;
- `Stage20MaterializationService`;
- `Stage20MaterializationServiceTest`.

No existing structural-deletion semantics or economic systems are modified.

## 10. Remaining blocker closure

After this slice, `MATERIALIZATION_LOD_CLOSURE` must remain blocking because two required layers are still missing:

### 10.1 Persistent physical save/load

`EntityState` / `GameStateCodec` must gain a versioned optional Stage-20 physical transform containing hierarchical position and double velocity.

Legacy saves must migrate neutrally with the field absent; no physical state may be inferred from legacy float coordinates and falsely marked authoritative.

`SimulationSession.snapshot()` must include retained dematerialized entity snapshots so off-screen existence survives save/load.

### 10.2 Relevance scheduler / activation bands

A production relevance scheduler must decide when to request representation promotion/demotion and expose a bounded wake-latency contract.

Only then may A.9 derive numeric activation bands from:

```text
accepted interaction envelope
+ maximum closing speed × bounded wake latency
```

rather than a viewport or hard-coded LOD radius.

## 11. Immediate next slice

After exact-head CI and merge acceptance of this runtime core, continue Workstream 1 with **persistent physical save/load integration**.
