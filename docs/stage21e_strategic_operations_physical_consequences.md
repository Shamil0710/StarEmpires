# Stage 21E — Strategic operations and physical warfare consequences

**Status:** implementation/acceptance contract for Stage 21E  
**Authority rule:** Stage 21E coordinates existing physical authorities; it does not create a second fleet, combat, logistics, economy or territory authority.

## 1. Purpose

Stage 21E turns accepted Stage-21D fleet orders into persistent physical operations while preserving the project's core invariant: every military consequence must be traceable to ordinary world state.

Supported operation identities are exactly:

- escort;
- interception;
- raid;
- blockade;
- defense;
- invasion.

The operation layer stores only command metadata: participant `FleetId` values, staging/objective identities, rules of engagement, supply/withdrawal policy, lifecycle, actor-bounded contact evidence and a reference to an exact-local Stage-19 encounter.

It does **not** store combat strength, synthetic losses, production debuffs, duplicate fleet placement or replacement resources.

## 2. Reused authorities

Stage 21E composes these existing owners rather than replacing them:

- **Stage 21A actor observations** — `FactionActorObservationSnapshot` supplies provenance and freshness for target acquisition. The operation service has no hidden-world lookup path for discovering targets.
- **Stage 21D fleet/command state** — `FleetCommandState`, `FleetForceRegistry`, `FleetReadinessState`, `FleetOrderSubmissionService` and ordinary movement remain the fleet identity, readiness, command and travel authorities.
- **Stage 19 physical warfare** — `PhysicalWarfareOperation` and `PhysicalWarfareOperationService` decide whether blockade/interdiction has a real combat-capable physical anchor.
- **Stage 19 exact-local tactical combat** — `Stage21ETacticalMaterializationService` hands exact current persistent `EntityState` payloads through `TacticalMaterializationAuthority`. `Stage19ExactTacticalEncounterResolver` imports detached exact engineering/runtime state and executes the existing control, kinetic, guided, defense, deception and beam stack. The authority fails closed if Stage 19 cannot represent the imported fit; substituting an acceptance doctrine, abstract strength or statistical resolver is forbidden.
- **Stage 20 freight/routing/handling** — `Stage21EGeneratedWorldTrafficRuntime` performs only Stage-21E admission before delegating to the existing `Stage20GeneratedWorldRuntimeBridge.LiveRuntime`. Cargo movement, route progress, jump FSM and handling remain owned by the ordinary Stage-20/18 path.
- **Stage 17.5/18 engineering and stores** — damage, ammunition, reaction mass, crew/service availability and fitted capability remain ordinary physical state and are re-derived into fleet readiness.

## 3. Persistent operation lifecycle

`StrategicOperationState` provides stable operation identities and canonical deterministic ordering.

Lifecycle:

`STAGING -> ACTIVE -> CONTACT_CONFIRMED -> ENGAGED -> WITHDRAWING/COMPLETED/FAILED`

Not every operation needs every intermediate combat state. For example, a blockade can remain `ACTIVE` while its fleet maintains physical presence.

Important invariants:

- one command group cannot own multiple active operations;
- an operation starts only from an already accepted active Stage-21D order;
- participants must be ordinary fleets owned by the command group and physically co-located for initial staging;
- `STAGING` becomes `ACTIVE` only after ordinary fleet placement reports physical arrival at the objective;
- contact evidence cannot exist in `STAGING`;
- `ENGAGED` requires a retained actor-bounded contact and an active Stage-19 encounter reference;
- transition ticks cannot move backwards;
- operation state never moves, damages, destroys or refills a fleet itself.

## 4. Contact acquisition and information latency

`StrategicOperationService.acquireContact(...)` accepts only a current `SECURITY` observation whose target identity is the exact ordinary `FleetId` string.

Allowed evidence channels are limited to actor-bounded sensor/intelligence/owned-asset/discovery reports. Freshness is evaluated at the decision tick and the exact provenance is persisted in `ContactState`.

The tactical gate then performs a second check against physical placement. A formerly valid report cannot materialize a battle after the target has physically moved away from the reported system. This separates **what the actor knows** from **what is physically true at encounter time** without granting omniscience.

## 5. Tactical materialization and commit-back

`Stage21ETacticalMaterializationService` requires:

1. `CONTACT_CONFIRMED` operation state;
2. current contact evidence;
3. target still existing as an ordinary `FleetId`;
4. target physically materialized in the observed system;
5. all operation participants physically materialized in that same system;
6. hostile ownership;
7. an exact Stage-19 materialization authority.

The handoff contains the exact current `EntityState` for every combatant, including persisted engineering fit, damage, stores, ammunition and propellant. Stage 21E accepts only a positive encounter identity returned by the Stage-19 authority.

`Stage21EGeneratedWorldStage19Authority` re-validates each handoff against the current ordinary fleet entity and Stage-20 exact local kinematics before importing detached tactical copies. After the bounded Stage-19 exchange:

- surviving engineering/runtime state and exact local kinematics are committed to the **same** ordinary entity and `FleetId`;
- catastrophic loss calls the ordinary `WorldSimulation.destroyEntity(...)` path and releases the Stage-20 physical sidecar;
- no replacement `FleetId`, ammunition, propellant or repaired damage is generated;
- the transient tactical encounter is resolved synchronously by `Stage21EGeneratedWorldTacticalExecutionService`, so a checkpoint cannot retain an encounter whose runtime exists only in memory;
- `Stage21ECommandLossReconciliationService` removes physically destroyed fleet identities from live Stage-21D command membership while allowing terminal Stage-21E history to retain the lost participant identity.

The current `Stage19ExactTacticalEncounterResolver` returns control after the first catastrophic physical destruction or after its provisional 120-second exact-local simulation horizon. That horizon is an execution quantum for the existing tactical runtime, not an abstract strategic damage roll; all ammunition, propellant, damage, energy and kinematics generated inside the bounded exchange are returned as physical state.

## 6. Physical consequences

`Stage21EPhysicalConsequenceService` is deliberately read-only. It compares before/after `FleetForceRegistry` reconstructions for operation participants and the confirmed hostile contact target.

A loss is reported only when the exact ordinary `FleetId` is absent afterwards. Surviving-fleet damage/ammunition/propellant/crew deltas are derived from the before/after physical readiness projections and the report records whether the exact entity payload changed.

Therefore:

- no operation can report a destroyed ship/fleet that still exists in ordinary state;
- no percentage casualty table is authoritative;
- no lost ammunition or propellant is recreated by reconciliation;
- no free replacement is generated by operation completion.

## 7. Blockade, interdiction and economic consequences

`Stage21EOperationTrafficPolicy` exposes only physical availability:

- blockade denies endpoint handling/adjacent traffic only while `PhysicalWarfareOperationService` confirms a hostile operation fleet is physically present and combat-capable in the objective system;
- interception denies an exact topology edge only while a hostile operation fleet physically anchors a valid Stage-19 interdiction on that edge;
- friendly traffic is not denied by its own operation policy;
- there is no `-X% production`, `-X% trade` or generic route-risk debuff.

`Stage21EGeneratedWorldTrafficRuntime` is the generated-world Stage-21E production admission boundary. It receives the current persistent `StrategicOperationState` on every call and, before any mutation:

- checks source/destination handling before delegating to ordinary outpost-to-hub transfer, freight loading or unloading;
- checks the exact next topology edge before delegating to `LiveRuntime.requestNextRouteHop(...)` and the ordinary jump FSM;
- retains the denying operation ID and physical `FleetId` in failure diagnostics;
- stores no copied blockade, route, cargo or station state.

If admission is denied, the underlying Stage-20/18 mutation is not invoked. If admission is open, the exact existing mutation path performs its normal phase, storage, topology, placement and conservation validation. Industrial/economic consequences therefore arise because real freight physically cannot traverse or handle a denied route, or because an ordinary physical asset was actually destroyed. Stage 21E does not directly mutate production output.

Territorial ownership mutation remains with the dedicated territorial authority. Stage 21E invasion/defense operations provide the physical fleets/objective state required by the next territorial-consolidation stage; they do not award territory from an operation score.

## 8. Ordinary strategic mobility and fitted FTL

Stage 21E does not add a strategic teleport, route cursor or second jump FSM. Generated military fleets move through `WorldSimulation.requestFleetJump(...)`, the existing `FleetJumpService` phase machine and `FleetWorldService` detach/attach boundaries.

The provisional Stage-21 military mobility content is explicitly layered over the completed Stage-17.5I/19 combat fixtures:

- `Stage175ICombatTestContentPack.loadDoctrines()` remains the stable Stage-17.5I/19 doctrine catalog consumed by older authorities such as Stage-18 manufacturing;
- `loadStage21StrategicDoctrines()` composes five explicit strategic variants without changing the five original combat fits;
- every strategic variant replaces exactly the existing `utility_datalink` assignment with `module.test_stage21_strategic_ftl_v1`; it gains no hidden slot, duplicate storage or class bonus;
- the new FTL module and the strategic variants remain provisional pre-Stage-22 content and are not silently promoted into Stage-18 manufacturing canon.

`ProductionFittedJumpResolver` is only a catalog-composition seam for the existing jump FSM. It deterministically selects one engineering catalog that contains the exact fitted hull and all installed modules, then delegates to `ShipEngineeringRuntime`. Unknown or ambiguous fitted content fails closed.

FTL planning uses the authoritative current `EngineeringComponent`, including `instanceState.damage().moduleDamage()`. Therefore a destroyed FTL mount cannot execute through a pristine fallback. Accepted jumps commit ordinary shared-bus energy draw, local jump heat and FTL cooldown through `ShipEngineeringRuntime.commitJump(...)` before the existing physical detach boundary. The same persistent `FleetId` and installed fit survive the topology hop.

The exact Stage-19 importer recognizes only two identities for each A-E doctrine: the original exact combat fit or its one registered Stage-21 strategic variant. Arbitrary same-hull refits are still rejected. This lets strategic fleets enter the existing tactical stack without replacing their actual fitted state or weakening Stage-19 identity validation.

## 9. Supply, withdrawal and reinforcement

Supply/withdrawal policy contains decision thresholds only; it grants no resources.

`StrategicOperationService.reviewSupplyAndReadiness(...)` re-reads current ordinary readiness. Depending on physical facts it can:

- continue;
- require submission of an ordinary `WITHDRAW` order;
- fail because no participant survives;
- fail because configured withdrawal is physically impossible without propellant.

The operation service itself never teleports a retreating fleet. The caller must close/cancel the source order as appropriate and submit `WITHDRAW` through the normal Stage-21D order/movement path.

`Stage21EReinforcementService` similarly attaches reinforcement only after the ordinary `FleetId` has already travelled to and physically arrived in the objective system, belongs to the operation faction and meets the operation readiness threshold.

## 10. Persistence contract

Persistent layers:

- `StrategicOperationStateCodec` — deterministic bounded operation payload;
- `Stage21EGeneratedWorldRuntimePersistentState` — Stage-21D checkpoint plus Stage-21E operation metadata;
- `Stage21EGeneratedWorldRuntimePersistenceCodec` — atomic bounded top-level checkpoint with exact schema/runtime identity.

Current schema/runtime identity:

- schema: `8`;
- runtime: `stage21e.generated-world-physical-operations.v8`.

Decode fails closed on invalid magic, future file/schema versions, corrupt bounds, incompatible active group/order/system references and trailing bytes.

Historical participant identities and terminal group/order references may remain in operation metadata after a real physical loss. Existing surviving fleet owners are still cross-checked against the operation faction. This allows a post-battle checkpoint to remember which ordinary identity was lost without resurrecting it or weakening the Stage-21D live-command validation contract.

## 11. Acceptance evidence

Automated coverage includes:

- deterministic operation-state roundtrip including retained contact and tactical encounter metadata;
- corrupt magic, future payload version and trailing bytes fail closed;
- current actor-bounded security evidence can create a contact;
- stale evidence cannot reveal/materialize a target;
- only the six Stage-21E roadmap operation families are admitted from Stage-21D orders;
- exact generated-world Stage-19 execution moves hostile fleets through ordinary topology hops, commits real damage/stores/losses to ordinary fleet authority, creates no replacement fleets and yields byte-identical repeated outcomes;
- Stage-19 exact import accepts the original doctrine fit and its one registered strategic variant while rejecting arbitrary same-hull mutation;
- the Stage-17.5I/19 doctrine loader remains unchanged by Stage-21 content composition, while every registered strategic fit replaces exactly one datalink mount with one physical FTL module;
- strategic FTL planning/commit is covered for translated mass, energy, local heat and cooldown, and destroyed FTL hardware fails the damage-aware production resolver;
- a generated military fleet completes an ordinary topology-neighbor jump through the existing world jump FSM, retains the same `FleetId` and fit, and retains physical energy/heat/cooldown evidence of FTL use;
- post-battle command cleanup removes destroyed `FleetId` references from live Stage-21D command state while terminal operation history remains valid;
- full post-battle Stage-21E checkpoint roundtrip embeds the exact already-committed ordinary physical world and contains no hidden active tactical runtime;
- a physically anchored hostile blockade prevents the actual Stage-20 source-loading mutation and leaves freight state unchanged;
- a physically anchored hostile interception prevents the actual Stage-20 next-hop request before the ordinary jump FSM starts;
- friendly operation presence does not deny its own ordinary freight handling;
- future top-level file/schema versions fail closed.

Repository `clean verify` remains the final acceptance gate and includes unit/integration tests, coverage checks, Javadoc and packaging.

## 12. Stage boundary

Intentionally **not** owned by Stage 21E:

- new fleet spawning or replenishment systems;
- statistical/remote combat resolution;
- doctrine bonuses detached from physical fits;
- direct economic percentage penalties;
- direct territorial ownership mutation or consolidation;
- strategic AI planning/coalition doctrine beyond the current operation execution contract.

Territorial occupation, claims, stabilization and control transition remain Stage 21F. Final re-authoring/promotion of provisional A-E combat/strategic content and its manufacturing recipes remains Stage 22 unless a later canonical roadmap revision explicitly moves that responsibility.

Those capabilities must compose this physical operation layer in their respective roadmap stages rather than bypass it.
