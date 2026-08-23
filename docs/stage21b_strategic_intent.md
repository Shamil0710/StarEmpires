# Stage 21B — Strategic intent, goals and commitment

**Status:** implemented in `stage21b-persistent-strategic-goals`; merge remains gated by repository CI and final acceptance review.

## Scope

Stage 21B converts Stage-21A actor-bounded interests into durable, explainable strategic intent. It does not create a second treasury, economy, diplomacy authority, fleet registry, territory authority, movement system, or hidden resource pool.

```text
actor-bounded evidence
+ caller-owned doctrine preference projection
+ read-only feasibility and planning-capacity projections
→ persistent candidates
→ deterministic arbitration and commitment
→ explainable persistent goals
```

The complete 13-family vocabulary is represented by `StrategicGoalType`. A goal is intent metadata only; downstream Stage-21 slices remain responsible for validated political, fleet, economic, and territorial commands.

## Goal contract

`StrategicGoalCandidate` and `StrategicGoalState` carry:

- stable goal, faction and target identity;
- actor-bounded source evidence and provenance;
- urgency, strategic value, feasibility and doctrine preference;
- multidimensional planning request and cost ceiling;
- declarative success and failure conditions;
- typed blockers;
- review cadence, expiry, cooldown and cancellation cost;
- externally reported terminal outcome.

The pre-hysteresis ranking score is `urgency × strategic value × feasibility × doctrine preference`, normalized to basis points. These values are planning inputs rather than stat modifiers.

`StrategicPlanningEnvelope` is a read-only normalized projection over treasury, logistics, construction and readiness capacity. It never mutates the underlying authorities.

## Doctrine compatibility

`FactionStrategicDoctrineProfile` is an immutable caller-owned projection seam. The neutral profile disables automatically generated escalatory families; an upstream policy adapter must explicitly opt into them. The profile affects candidate ranking only and cannot change world state or physical capabilities.

## Lifecycle and anti-churn

Persistent goal lifecycle is `ACTIVE`, `STALLED`, `SUCCEEDED`, `CANCELLED`, or `EXPIRED`.

- blocked goals become `STALLED` with a reason instead of disappearing;
- recovery preserves goal identity;
- cancellation has visible cost and a target cooldown;
- re-entry after cooldown receives a new persistent goal ID;
- existing goals receive hysteresis;
- Stage-21A `commitmentUntilTick` is honored as a minimum anti-churn horizon for nonterminal evidence loss.

Terminal success, failure and expiry still take precedence over commitment retention.

## Material-change wakeups

Stage 21A already owns persistent event wakeups, including `MATERIAL_OBSERVATION_CHANGED`. Stage 21B therefore does not create another event queue.

`FactionStrategicIntentState.lastActorReviewCount` records the latest Stage-21A completed review already consumed by strategic planning. When the actor review count advances, the planner may review goals before ordinary goal cadence, records `ACTOR_REVIEW_ADVANCED`, and persists the new watermark. Repeating planning with the same actor-review count cannot trigger that early review a second time.

`PlanningResult.whyNowCode()` provides a compact stable explanation of the planning trigger.

## Persistence

`FactionStrategicIntentStateCodec` v5 persists goals and the consumed actor-review watermark deterministically. `Stage21BGeneratedWorldRuntimePersistentState` v5 wraps the accepted Stage-21A checkpoint unchanged plus exactly one strategic-intent aggregate per living actor. Faction identities are cross-validated and the outer checkpoint remains bounded and atomically replaceable.

## Acceptance mapping

| Requirement | Evidence |
|---|---|
| Complete roadmap goal vocabulary | `StrategicGoalTaxonomyTest` |
| Explicit doctrine gating | taxonomy/doctrine tests |
| Four-factor deterministic ranking | scoring test |
| Persistent identity and anti-churn | `FactionStrategicGoalPlannerTest` |
| Explainable infeasibility | feasibility, capacity and cost-ceiling blocker tests |
| Stall/recovery without ID churn | planner recovery test |
| Cancellation consequence and cooldown | planner failure/re-entry test |
| Distinct terminal states | success/failure/expiry tests |
| Goal contract survives save/load | contract and codec round-trip tests |
| Generated-world persistence remains compositional | `Stage21BGeneratedWorldRuntimePersistenceAcceptanceTest` |
| Material actor review wakes goals before cadence | `Stage21BMaterialChangeReviewTest` |
| Same actor review is consumed once | material-change anti-repeat test |
| Minimum commitment protects accepted intent | material-change commitment test |
| Actor-review watermark survives save/load | material-change codec test |
| Cross-layer stale bookkeeping fails closed | material-change stale-state test |

## Exit criteria

- Goals are reconstructible from persisted evidence and contract fields, never inferred from UI labels.
- Currently impossible goals are deferred with typed reasons.
- Repeated unchanged reviews preserve goal and target identity.
- Different actor-bounded dependency evidence resolves to different goal families.

## Later-stage boundary

Stage 21B stops at persistent strategic intent. Diplomatic lifecycle, physical fleet orders, operations, territorial transitions, recovery, NPCs, missions and final command UI remain responsibilities of later Stage-21 slices.