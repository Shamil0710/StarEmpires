# Stage 21D — Fleet readiness, command groups and strategic movement

**Status: COMPLETE.** Implementation merged via PR #327 from exact green head `52671c0070184323cca1e0ca56695f3c5b03aa97`; required CI run #4757 (`32665321371`) and Java 17 verification job `97257705063` completed successfully. Merge commit on `main`: `517f32f1d0bd9260fcb090af20888cd5ad8e0e3d`. **Stage 21E is OPEN/NEXT and intentionally not implemented here.**

## Scope

Stage 21D converts ordinary finite `FleetId` entities into inspectable military force projections and lawful strategic commitments. It does not create a second fleet registry, movement engine, logistics store, combat resolver or territory authority.

```text
ordinary FleetId placement + exact fitted EntityState
+ explicit crew/service availability observation
→ read-only readiness projection
→ persistent command group retaining member FleetIds
→ shared PLAYER/AI order validation
→ lawful deterministic neighbor route
→ ordinary WorldSimulation.requestFleetJump / Stage-18 service request
→ physical arrival or service authority result
→ persistent order reconciliation
```

Stage 21E remains responsible for contact acquisition, operation identities, tactical materialization and physical warfare consequence return when opposing forces actually meet.

## Authority boundaries

### Ordinary fleet identity and placement remain authoritative

`FleetForceRegistry` is a read-only reconstruction over `WorldState.fleets()` and the exact local or transit `EntityState` already owned by the world. It allocates no fleet identity and owns no position. A fleet continues to be addressed by the same `FleetId` before, during and after strategic command assignment.

A `CommandGroupState` is only persistent command metadata:

- stable command-group ID;
- owning faction ID;
- member `FleetId` values;
- home system;
- reserve/home-defense flags;
- maximum accepted strategic route risk.

A `FleetId` may belong to at most one persistent command group. The group wrapper never replaces the ordinary fleet entity.

### Stage 17.5 remains fitted-ship and damage authority

`FleetReadinessEvaluator` reads the existing engineering catalog and exact persistent engineering state. It derives bounded basis-point projections for:

- structural condition from compartment/module integrity;
- ammunition from `AMMUNITION` interface load versus fitted capacity;
- propellant from `REACTION_MASS` interface load versus fitted capacity;
- crew from hull baseline + fitted module crew requirements versus explicit available crew;
- sensors from fitted sensor/EW/fire-control module integrity;
- maintenance from fitted module service interval versus persistent service age;
- supply access from explicit observed service/logistics access.

Overall readiness is the minimum component, so one missing physical prerequisite cannot be hidden by unrelated strengths.

There is no independent persistent crew authority in the current ship-local runtime. Stage 21D therefore does **not** fabricate one. `FleetOperationalAvailability` is an explicit bounded observation seam; missing crew or supply-access evidence fails closed to zero until a dedicated upstream authority exists.

### Stage 17 remains legal transit authority

`FleetStrategicRoutePlanner` knows only the existing `GalaxyTopology` neighbor graph. Legal entry is injected through `TransitAccessPolicy`, which must delegate to existing Stage-17 ownership/treaty/war law.

The route planner is deterministic breadth-first search over the topology's canonical sorted neighbor lists. It cannot invent an edge, skip an intermediate system or grant access itself.

### Stage 18 remains refuel/rearm/repair authority

Stage 21D can validate whether a target exposes an observed service capability and can produce a `ServiceOperation` request for:

- `REFUEL`;
- `REARM`;
- `REPAIR`.

There is intentionally no Stage-21D method that adds reaction mass, ammunition or integrity. Actual service must be fulfilled by existing Stage-18 storage/handling/shipyard/economy boundaries. This makes free repair/rearm impossible at the strategic-order layer.

### Existing jump FSM remains movement authority

`FleetOrderExecutionService.dispatchMovementHop` calls only `WorldSimulation.requestFleetJump`. It does not detach/materialize entities directly and does not mutate `FleetJumpState` itself.

Dispatch is recoverable rather than pretending that several independent physical jump requests form a new strategic transaction. A member already executing the exact persisted hop, or already physically present at the next route node, receives no duplicate request; a lagging member may still receive its ordinary jump later. An active jump toward any different edge fails closed. This preserves already accepted physical movement without inventing rollback authority in Stage 21D.

Persistent order progress advances only after `FleetForceRegistry` reconstructs every group member physically in the expected next system. A strategic order therefore cannot teleport a fleet, self-certify arrival or duplicate a completed arrival.

## Readiness and feasibility

Readiness is a decision projection, not a generic combat-power stat. Stage 21D uses explicit prerequisites needed to decide whether an order can be issued:

- any movement away from the current system requires non-zero physical propellant readiness;
- any order requires non-zero observed crew availability;
- combat-oriented orders require bounded structural, ammunition, crew and sensor readiness;
- service orders require observed service/logistics access and matching existing service capability.

Unknown engineering hull/module references fail closed to unavailable readiness rather than receiving a fallback score.

## Order taxonomy

The persistent `OrderType` vocabulary contains all required Stage-21D orders:

1. `PATROL`
2. `GUARD`
3. `ESCORT`
4. `STAGE`
5. `REINFORCE`
6. `INTERCEPT`
7. `SHADOW`
8. `RAID`
9. `BLOCKADE`
10. `INVADE`
11. `WITHDRAW`
12. `REFUEL`
13. `REARM`
14. `REPAIR`
15. `RETURN`

`OrderSource` records `PLAYER` or `AI`, but both sources enter the same `FleetOrderSubmissionService.submit` validation path. Source identity changes audit provenance only; it does not relax feasibility or legal checks.

Each persistent order records stable ID, command group, type/source, final target, exact neighbor-route, route cursor, submission tick, staging deadline and lifecycle status.

## Mobilization, reserve, home-defense and risk

Submission requires all command-group members to be ordinary local fleets staged in one physical system. An already in-transit member cannot be retasked through a new order as though it were locally available.

The staging deadline is derived from the lawful route hop count plus observed nominal hop timing and service/handling duration. It is persistent metadata rather than an instant arrival guarantee.

Reserve and home-defense groups cannot accept offensive `RAID`, `BLOCKADE` or `INVADE` commitments away from their designated home system.

Every command group also carries `maxStrategicRiskBps`. A read-only risk authority supplies a bounded 0..10000 observation for the lawful route; values outside the domain fail closed, and a route above doctrine ceiling is rejected. Risk does not resolve combat and cannot substitute for Stage-19/21E physical contact.

Only one active order may occupy a command group's active-order slot. This rejects double strategic assignment before physical execution begins.

## Persistence

`FleetCommandStateCodec` deterministically persists command groups, orders, allocator watermarks and route progress. Input group/order ordering is canonicalized before encoding. The codec rejects:

- corrupt magic;
- unsupported future file version;
- truncated payloads;
- trailing bytes;
- collection counts outside hard bounds;
- invalid command/order invariants.

`Stage21DGeneratedWorldRuntimePersistentState` schema `7` / runtime contract `stage21d.generated-world-force-command.v7` embeds the complete accepted Stage-21C runtime unchanged and adds only `FleetCommandState`.

Composition validates every member `FleetId`, physical faction ownership, home system and every neighbor edge in an order route against the embedded physical world. Unknown or mismatched cross-layer references fail closed.

The Stage-20.5 checkpoint remains the authority for exact local/transit fleet state. A mid-transit generated fleet therefore retains the existing ordinary `FleetJumpState`, detached entity payload and exact arrival sidecar while Stage 21D independently preserves command metadata around it.

## Acceptance mapping

| Requirement | Evidence |
|---|---|
| Force registry is reconstructed from ordinary FleetIds | `FleetForceRegistryTest.reconstructionUsesExactlyTheOrdinaryWorldFleetIdentitiesAndPlacements` |
| Missing external crew/service observations fail closed | `FleetForceRegistryTest`, `FleetReadinessEvaluatorTest.missingAvailabilityFailsClosedWithoutInventingCrewOrSupplyAccess` |
| Readiness derives from damage, ammunition, propellant, crew, sensors, maintenance and supply | `FleetReadinessEvaluatorTest.readinessIsDerivedFromDamageAmmunitionPropellantCrewSensorsMaintenanceAndSupply` |
| Unknown engineering state does not get fallback readiness | `FleetReadinessEvaluatorTest.missingEngineeringUnknownHullAndUnknownModuleFailClosed` |
| Duplicate FleetId command assignment is rejected | `FleetCommandStateCodecTest.duplicateFleetAssignmentAcrossCommandGroupsFailsClosed` |
| Command/order codec is deterministic and bounded | `FleetCommandStateCodecTest` |
| All 15 order families use one validation boundary | `FleetOrderSubmissionServiceTest.everyOrderFamilyUsesTheSameValidatedSubmissionBoundary` |
| PLAYER and AI receive equivalent routing/feasibility checks | `FleetOrderSubmissionServiceTest.playerAndAiReceiveEquivalentLawfulRoutingAndFeasibilityChecks` |
| Fuel/ammunition/access/service infeasibility fails closed | `FleetOrderSubmissionServiceTest.infeasibleFuelAmmunitionAccessAndServiceRequestsFailClosed` |
| Reserve/home-defense/risk constraints are enforced | `FleetOrderSubmissionServiceTest.reserveHomeDefenseAndRiskConstraintsRejectUnlawfulCommitments` |
| Double active-order assignment is rejected | `FleetOrderSubmissionServiceTest.secondActiveOrderForTheSameCommandGroupIsRejected` |
| Route selection is deterministic and neighbor-only | `FleetStrategicRoutePlannerTest` |
| Strategic movement creates only ordinary jump-FSM work | `FleetOrderExecutionServiceIntegrationTest.strategicMovementDispatchesTheExistingJumpFsmAndReconcilesOnlyAfterPhysicalArrival` |
| Same-hop retry is idempotent and partially progressed groups remain recoverable | `FleetOrderExecutionServiceIntegrationTest.strategicMovementDispatchesTheExistingJumpFsmAndReconcilesOnlyAfterPhysicalArrival`, `partiallyProgressedGroupPlansOnlyLaggingMemberAndCompletesAfterBothArrive` |
| Duplicate arrival and teleport are rejected | `FleetOrderExecutionServiceIntegrationTest` |
| Repair/rearm/refuel remain service requests rather than free mutation | `FleetOrderExecutionServiceIntegrationTest.serviceOrdersAreRequestsOnlyAndCannotGrantFreeRepairOrRearm` |
| Complete Stage-21D wrapper rejects unknown fleet/owner/system/route references and future versions | `Stage21DGeneratedWorldRuntimePersistenceAcceptanceTest` |
| Real generated fleet survives mid-transit Stage21D save/load and completes through ordinary arrival authority | `Stage21DGeneratedWorldRuntimePersistenceAcceptanceTest.midTransitGeneratedFleetAndActiveCommandRoundTripThenCompleteThroughOrdinaryArrivalAuthority` |
| Existing exact transit payload authority preserves stable FleetId/cargo/local payload | `WorldSimulationJumpAcceptanceTest.jumpSurvivesMidTransitSaveLoadAndLargeRenderFrame` |
| Fitted jump engineering cannot fall back to free legacy movement | `FleetJumpEngineeringIntegrationTest.fittedPhysicalRejectionCannotFallBackToLegacyJump` |
| Exact Stage-20 generated arrival sidecar survives restored mid-transit continuation | `Stage20LiveArrivalAuthorityIntegrationTest.restoredMidTransitFleetNeedsNoProcessLocalDepartureMarker` |

## Exit criteria mapping

- **Strategic order causes only ordinary movement/service operations:** movement delegates to `WorldSimulation.requestFleetJump`; service types return Stage-18 requests only.
- **In-transit fleets retain identity, fit, damage, cargo and arrival authority across save/load:** Stage 21D embeds unchanged Stage-20/21C authority; generated mid-transit acceptance restores the same `FleetId`, jump state, entity payload and exact arrival authority before ordinary arrival.
- **Fleet lacking fuel/ammunition/access cannot silently execute:** shared submission validation fails closed before an order is accepted.
- **Double assignment, teleport, duplicate arrival and free repair/rearm are rejected:** command-state uniqueness, one-active-order rule, neighbor routing, exact-hop idempotency, recoverable staggered dispatch, physical-arrival reconciliation and service-request-only execution enforce these boundaries.

## Closeout evidence

- implementation PR: `#327`;
- exact green implementation head: `52671c0070184323cca1e0ca56695f3c5b03aa97`;
- required CI: run `32665321371` / run #4757 — **SUCCESS**;
- Java 17 verification job `97257705063` — **SUCCESS**;
- merged implementation commit on `main`: `517f32f1d0bd9260fcb090af20888cd5ad8e0e3d`;
- review submissions, review threads and PR comments were empty at the merge gate;
- Stage 21E remains the next slice and has not been started by this closeout.

## Later-stage boundary

Stage 21D intentionally does not implement:

- Stage 21E contact acquisition, strategic-operation identity, tactical materialization, blockade/invasion consequences or combat result return;
- Stage 21F occupation/stabilization/control transition;
- Stage 21G peace demobilization, physical replenishment and replacement planning;
- Stage 21H NPC/mission/reputation living-world integration;
- Stage 21I final integrated command UI, migration corpus, performance and long-run soak.

Those stages must consume the finite fleet identities, readiness evidence, lawful routes and persistent orders defined here without turning strategic metadata into physical authority.
