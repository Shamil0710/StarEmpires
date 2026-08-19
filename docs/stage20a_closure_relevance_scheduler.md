# Stage 20A Closure — Relevance Scheduler / Wake-Latency Closure

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A Closure / Readiness Remediation  
**Workstream:** 1.3 — Production relevance scheduler  
**Date:** 2026-08-19

## 1. Purpose

Workstreams 1.1 and 1.2 closed reversible runtime materialization and persistent physical save/load. This slice closes the remaining code-first scheduling layer: authoritative relevance must determine when a Stage-20 entity is dormant, strategic, active-local or tactical, and a dormant entity must be synchronously promoted before relevant work executes.

The slice deliberately does **not** fabricate universal numeric LOD radii. Current A.4/A.5 sensor/weapon interaction geometry remains target/state dependent and station docking/traffic geometry remains incomplete. Therefore generic `ACTIVE_LOCAL` / `TACTICAL` distance bands stay unresolved even though scheduler wake latency is now bounded.

## 2. Runtime representation mapping

The accepted scalability hierarchy remains:

```text
DORMANT
STRATEGIC
ACTIVE_LOCAL
TACTICAL
```

The production runtime mapping is:

```text
DORMANT
→ no Ashley runtime Entity
→ persistent EntityState + Stage-20 physical state retained

STRATEGIC
→ live persistent ECS Entity
→ existing reduced-rate SimulationSession may continue economy/strategic consequences

ACTIVE_LOCAL
→ live persistent ECS Entity
→ local operational relevance

TACTICAL
→ live persistent ECS Entity
→ detailed physical/tactical relevance
```

Only `DORMANT` implies runtime dematerialization.

This is important because blindly removing STRATEGIC entities from Ashley would freeze the existing ECS-based economy and create a second off-screen simulation model. Keeping STRATEGIC live in the accepted reduced-rate session preserves one authoritative economy.

## 3. Relevance priority

`Stage20RepresentationScheduler` consumes the existing A.9 `RelevanceInput` and therefore inherits the deterministic priority:

```text
direct tactical interaction
→ TACTICAL

else local operational relevance
→ ACTIVE_LOCAL

else strategic relevance or due authoritative event
→ STRATEGIC

else
→ DORMANT
```

Render/camera visibility is not an input.

A due event can therefore wake a completely off-screen dormant entity to STRATEGIC before the event is processed.

## 4. Synchronous wake contract

The underlying `Stage20MaterializationService.materialize(...)` is synchronous and consumes:

```text
0 simulation seconds
```

once called.

The scheduler exposes the same bounded wake-latency semantics:

```text
maximum materialization wake latency = 0 simulation seconds
```

A transition from dormant to any relevant live level completes before `synchronize(...)` returns.

No simulation tick may pass with the entity still absent after the scheduler has accepted relevance requiring live representation.

## 5. Transition behavior

### Live STRATEGIC → DORMANT

When no tactical/local/strategic/due-event relevance remains:

```text
capture persistent state
retain physical state
remove Ashley runtime representation
required level = DORMANT
```

### DORMANT → STRATEGIC

A due or strategic event causes:

```text
restore Ashley representation synchronously
same EntityId
same EntityState
same physical kinematics
required level = STRATEGIC
```

### STRATEGIC ↔ ACTIVE_LOCAL ↔ TACTICAL

No Ashley object replacement occurs.

The same live persistent Entity remains registered while downstream systems change computational detail/cadence according to relevance.

Thus a tactical camera transition or nearby local relevance cannot accidentally recreate inventory, reset damage or allocate a new persistent identity.

## 6. Initial-state inference

Representation level itself is not persistent authority and is not stored in the Stage-20 save envelope.

For an entity not yet processed by the scheduler:

```text
if Stage20MaterializationService says dematerialized
→ infer DORMANT

else
→ infer STRATEGIC
```

The latter is conservative because an ordinary restored `SimulationSession` is already a valid persistent/reduced-rate ECS runtime.

The first relevance synchronization then promotes to ACTIVE_LOCAL/TACTICAL if needed or demotes to DORMANT if truly irrelevant.

## 7. Context-specific activation bands

The scheduler can derive a physical promotion threshold for a **specific accepted interaction class** using A.9:

```text
activation distance
= explicit interaction envelope
+ maximum closing speed × maximum wake latency
```

With the current synchronous wake contract:

```text
maximum wake latency = 0

activation distance
= explicit interaction envelope
```

This does not mean there is one universal LOD radius.

The caller must still provide:

- a physically accepted interaction envelope;
- maximum relevant closing speed;
- exact provenance for that interaction class.

For example, future station docking, missile-defense or weapon-specific materialization may each receive different physical thresholds.

## 8. A.9 calibration change

The previous unresolved scheduler item:

```text
no_production_persistent_to_local_materialization_scheduler_with_bounded_wake_latency
```

is retired.

A.9 now records the actual remaining gap:

```text
numeric_active_local_tactical_activation_bands_wait_on_context_specific_physical_interaction_envelope_closure
```

The generic machine-readable band rows remain:

```text
ACTIVE_LOCAL = UNRESOLVED
TACTICAL     = UNRESOLVED
```

because the physical interaction envelopes needed to populate them are still blocked by representative sensor/weapon/station closure.

## 9. Readiness impact

The Stage-20A readiness gate still reports:

```text
MATERIALIZATION_LOD_CLOSURE
= BLOCKING_STAGE20B_ENTRY
```

but its evidence changes materially:

```text
lossless_materialization_lifecycle_closed = true
numeric_activation_bands_closed           = false
```

This prevents the remaining physical-band debt from hiding the fact that the runtime/persistence/scheduler lifecycle is now closed.

## 10. Regression invariants

Tests require:

- an untracked live Stage-20 entity begins conservatively as STRATEGIC;
- no relevance demotes it to DORMANT and removes only the runtime representation;
- persistent EntityState and physical kinematics remain exact while dormant;
- a due event synchronously promotes DORMANT → STRATEGIC with zero simulation-time latency;
- the restored Entity keeps the same persistent ID and value-equal state;
- STRATEGIC → ACTIVE_LOCAL → TACTICAL changes relevance without replacing the live Entity object;
- an explicit context-specific activation band uses the accepted interaction envelope and scheduler latency, not a viewport constant;
- the scheduler refuses to manage an entity lacking Stage-20 physical authority.

## 11. Machine implementation

Added:

- `Stage20RepresentationScheduler`;
- `Stage20RepresentationSchedulerTest`.

Updated:

- `Stage20MaterializationLodCalibrationCalculator` to retire the scheduler-latency gap while retaining unresolved physical bands;
- `Stage20ACalibrationReadinessProfileTest` to require lifecycle closure evidence while keeping the same blocker set.

## 12. Remaining Workstream-1 dependency

The code-first materialization lifecycle is now complete:

```text
runtime round-trip          → closed by 1.1
persistent physical save    → closed by 1.2
relevance scheduler/wake    → closed by 1.3
```

The remaining numeric LOD-band portion cannot be closed independently: it depends on physical interaction envelopes owned by later closure workstreams.

Therefore Workstream 1 should not invent placeholder radii merely to turn the umbrella blocker green.

## 13. Immediate next action

After exact-head CI and merge acceptance, continue the dependency-ordered closure plan with **Workstream 2 — representative ship / endurance / civilian FTL coverage**.

Before adding any new representative values, audit the accepted Ship Mathematics reference resources for reusable physical evidence. If a required role has no accepted physical basis, keep it explicitly blocked rather than fabricate mass, thrust, delta-v or stores.
