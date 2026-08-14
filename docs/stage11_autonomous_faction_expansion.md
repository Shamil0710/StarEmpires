# Stage 11 — Autonomous Faction Expansion

Status: **COMPLETE** — Stage 11A PR #24, Stage 11B PR #25, Stage 11C PR #26, Stage 11D PR #27; final Stage-11 main merge `f5b58c7`. Final PR #27 Java-17 CI run #838 passed tests, coverage, strict Javadoc and desktop packaging.

## Goal

Stage 11 converts the Stage-8 `EXPANSION` strategic demand modifier into real spatial growth that uses the same physical economy, construction and inter-system logistics as every other actor.

## 11A — Opportunity model

`FactionExpansionOpportunityAnalyzer` ranks a bounded reachable frontier from authoritative world state:

- controlled source territory;
- Stage-10 weighted jump paths;
- materialized finite asteroid resources;
- live market unmet demand and network footprint;
- data-driven anchor construction funding;
- faction treasury affordability;
- diplomacy-derived hostile pressure;
- explicit penalty for already foreign-controlled systems.

The analyzer is read-only, deterministic and explainable. It does not create stations, move fleets or change territory.

## 11B — Persistent growth plan

Physical expansion intent is stored inside the existing persistent `EXPANSION` strategic goal as `StrategicGrowthState.Plan`.

A plan contains stable identity, source/target systems, strategic reason, anchor archetype, optional linked Stage-9 `ConstructionProjectId`, support fleet requirement/assignment, initial construction stock targets, approved budget and lifecycle timestamps.

Lifecycle:

```text
PLANNED
  -> APPROVED
  -> EXECUTING
  -> ESTABLISHED
     | CANCELLED
     | FAILED
```

World persistence uses bounded file-format v2 strategic-growth trailer data while retaining top-level WorldState schema v7. File-format v1 saves migrate with no invented growth plans. Round-trip and continuation are covered by tests.

## 11C — Physical execution

`FactionExpansionRuntime` owns the current authoritative `WorldSimulation` and applies persistent strategic transitions through ordinary `snapshot -> restore`. Rebuilds happen only when strategic state changes, not every frame.

For an unclaimed viable target the runtime:

1. creates a persistent Stage-11 plan from the best Stage-11A opportunity;
2. deterministically assigns an idle faction cargo fleet;
3. creates an ordinary Stage-9 construction project in the target system;
4. physically funds the project from faction treasury;
5. buys actual construction materials from source-system markets through `TradeController`;
6. pays suppliers from the support fleet wallet;
7. moves the same persistent `FleetId` through the Stage-10 jump FSM;
8. unloads cargo through `ConstructionProjectService.deliver(...)` semantics;
9. repeats trips when cargo capacity is smaller than project requirements;
10. waits for real material fulfillment and build time;
11. claims an unowned target only after the anchor project reaches `COMPLETED`.

The executor does not use virtual delivery or an instant-spawn station. Save/load during an active jump is part of acceptance coverage and the persistent FleetId survives local EntityId replacement between systems.

An assigned fleet is authoritative through `assignedSupportFleetIds`. Its ordinary local `TradeAI` route is temporarily suppressed while it is strategically assigned; this is derived runtime behavior rather than a second persistent ownership model.

## 11D — Competition

`StrategicGrowthCompetitionResolver` provides deterministic pre-combat competition over the same physical construction results.

For multiple plans targeting the same unclaimed system:

1. only plans whose linked anchor project is physically `COMPLETED` are eligible to claim;
2. the lower authoritative `completedTick` wins;
3. exact completion-tick ties are resolved by stable `PlanId`;
4. `FactionExpansionCompetitionCoordinator` advances completed winners before competitors;
5. losing plans then observe foreign territory and fail through the ordinary Stage-11C rule.

If a system is already controlled by an unrelated faction, Stage 11 never auto-conquers it. A station may exist as an economic outpost, but strategic conquest requires future warfare/territory rules from Stage 18.

## Failure semantics

Expansion can fail or remain blocked from real world constraints:

- insufficient faction treasury before approval;
- missing/destroyed assigned support fleet;
- disconnected jump route;
- failed/cancelled anchor construction;
- foreign control appearing before claim;
- unavailable market supply or fleet liquidity can stall execution rather than fabricate resources.

No failure path creates money, goods or stations.

## Acceptance evidence

The Stage-11 acceptance suite covers:

- bounded and deterministic opportunity ranking;
- resource-sensitive ranking and budget gate;
- persistent plan lifecycle and duplicate-target protection;
- file-format round-trip, migration and continuation;
- real supplier payment by a support fleet;
- multi-trip physical material delivery;
- save/load during jump transit;
- completed Stage-9 anchor station before territory claim;
- persistent FleetId with changed system-local EntityId;
- rejection of automatic expansion into foreign-controlled systems;
- deterministic earlier-completion and equal-tick competition resolution;
- coordinator execution through the ordinary physical growth runtime.

## Deferred

Stage 11 deliberately does not add:

- military conquest of occupied systems;
- blockade/jump-edge availability overlay;
- combat strength comparison;
- player-owned expansion UI;
- general player fleet-order framework;
- procedural galaxy generation.

These remain Stage 18, Stage 12/15/16 and Stage 19 responsibilities according to the roadmap.

## Definition of Done

A faction can independently choose an economically/strategically justified unclaimed neighboring system, persist its intent, allocate real budget and a real fleet, purchase and transport physical construction resources between StarSystems, complete an ordinary Stage-9 station project, and establish a new territorial/economic node. Multiple factions competing for the same target resolve deterministically from physical completion timing without scripted spawn or automatic military conquest. **Выполнено.**
