# Stage 21A — Living actor kernel and interest evidence

**Status:** implemented in `stage21a/living-actor-kernel-v1`; merge is gated by repository CI.

## Scope

Stage 21A introduces a persistent, actor-bounded autonomous-faction review lifecycle without creating a second economy, diplomacy, territory, fleet, combat, or movement authority.

The kernel is deliberately pure:

```text
persisted lifecycle state
+ actor-bounded observation snapshot
+ bounded scheduler authorization
→ deterministic interest evidence / decision trace
+ updated lifecycle metadata
```

It cannot directly mutate treasury, cargo, ships, territory, claims, relations, treaties, or fleet orders. Those remain upstream Stage 17–20 authorities and command boundaries.

## Runtime pieces

### `FactionLivingActorState`

Persists one row per autonomous faction using the existing stable faction content ID. The row contains:

- next ordinary review deadline;
- commitment horizon reserved for later Stage-21 goal anti-churn logic;
- last completed review tick and completed-review count;
- deduplicated persistent event wakeups with stable source identity, receipt tick, eligibility tick, and causal reason.

Wakeups cover arrival, observed attack, reported loss, treaty change, shortage report, project completion, and material observation change.

### `FactionActorObservationSnapshot`

Contains four separate actor-known domains:

- economic;
- territorial;
- security;
- diplomatic.

Every observation carries an allowed channel, stable provenance ID, receipt tick, explicit freshness horizon, target identity, interest family, and bounded severity. The snapshot has no `WorldSimulation` or generated-truth reference.

Allowed channels are local sensor reports, Stage-20 discovery knowledge, delivered intelligence reports, actor-visible economic ledgers, territory ledgers, diplomatic registries, and owned-asset reports.

### `FactionInterestEvidence` / `FactionInterestResolver`

The resolver covers all Stage-21A evidence families:

- supply dependency;
- market access;
- route exposure;
- resource deficit;
- border security;
- territorial opportunity;
- treaty obligation.

Priority is evidence-derived only. For each interest/target pair, the strongest current bounded observation defines priority; corroborating rows remain in provenance but do not become hidden stat bonuses. Equal priorities resolve deterministically by interest kind and stable target ID.

The resulting decision trace has canonical UTF-8 bytes so replay tests can compare byte-identical actor decisions.

### `FactionLivingActorScheduler`

The scheduler:

- reacts to persisted event wakeups or ordinary review deadlines;
- orders due actors only from persisted due time, trigger type, and stable faction ID;
- authorizes no more than the caller-provided `maxReviews` budget;
- uses a top-K heap, retaining at most `maxReviews` due candidates while scanning actor lifecycle metadata;
- reports eligible, selected, and deferred counts for bounded-work acceptance.

### `FactionLivingActorKernel`

The review kernel accepts only lifecycle state, one actor-bounded snapshot, scheduler authorization, and review cadence. It returns a decision trace and new lifecycle state. It has no command executor and no mutable world authority.

### `FactionLivingActorRuntime`

Owns only the Stage-21A lifecycle rows. A bounded review batch asks the caller for observation snapshots only for scheduler-selected factions. Lifecycle updates are committed atomically after every selected pure review succeeds; one invalid snapshot cannot partially advance the batch.

### `FactionLivingActorStateCodec`

Versioned deterministic UTF-8 checkpoint codec persists all living-actor lifecycle rows in stable faction order, including deadlines, commitment horizons, review watermarks, and pending wakeups.

### `FactionLivingActorBootstrap`

Creates exactly one state per explicitly authorized autonomous faction and deterministically staggers first review deadlines across the cadence window, avoiding synchronized review spikes.

### `Stage21AGeneratedWorldRuntimeBridge`

Composes the living-actor runtime over the accepted Stage-20 generated-world runtime. The Stage-20 world remains the authority for physical simulation, fleets, freight, economy and generated-world persistence. Strategic review is intentionally explicit rather than hidden inside `advanceFrame`, because a review requires an actor-bounded observation publication step.

### `Stage21AGeneratedWorldRuntimePersistentState` / `Stage21AGeneratedWorldRuntimePersistenceCodec`

Stage 21A uses a new composition envelope around the existing Stage-20 checkpoint instead of modifying the accepted Stage-20 schema. The envelope persists:

- the complete Stage-20 generated-world runtime checkpoint unchanged;
- canonical living-actor lifecycle rows.

Actor IDs are cross-validated against the saved Stage-20 faction authority. The outer binary codec is bounded and supports atomic file replacement.

## Acceptance mapping

| Stage-21A exit criterion | Implementation / test |
|---|---|
| Same seed/checkpoint/events produce byte-identical actor decisions | canonical snapshot ordering + `DecisionTrace.canonicalBytes()` + input-order invariance test |
| Save/load immediately before a deadline produces one, not zero or two, reviews | state codec exact-deadline test plus real generated-world Stage-21A envelope restore/wakeup single-consumption acceptance |
| Hidden information cannot change a decision until observed through an allowed channel | kernel has no world/truth input; stale evidence is ignored; fresh allowed report changes the decision only after it enters the snapshot |
| No review directly creates money, cargo, ships, territory or relations | pure kernel type boundary exposes lifecycle metadata and trace only; generated-world bridge does not synthesize actor knowledge or issue strategic commands |
| Work remains bounded as actor counts grow | top-K scheduler hard `maxReviews` cap, including a 10,000-due-actor acceptance case |
| Stage-20 world authority survives Stage-21A persistence unchanged | `Stage21AGeneratedWorldRuntimePersistenceAcceptanceTest` wraps a real `Stage20PlayableGeneratedWorldFactory` runtime, round-trips the Stage-21A envelope, restores it, consumes one wakeup, restores again, and asserts the embedded Stage-20 checkpoint remains byte-identical |
| A failed observation publication cannot partially advance a review batch | atomic `FactionLivingActorRuntime.reviewDue` test verifies all selected lifecycle rows remain unchanged on failure |

## Non-goals retained for later Stage 21 slices

Stage 21A does **not** create strategic goals, war state, diplomatic proposals, fleet orders, operations, occupation/control transitions, production commitments, or replacement resources. Those remain 21B+ responsibilities.
