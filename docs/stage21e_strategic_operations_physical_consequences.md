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
- **Stage 19 exact-local tactical combat** — `Stage21ETacticalMaterializationService` hands exact current persistent `EntityState` payloads through `TacticalMaterializationAuthority`. The authority must fail closed if Stage 19 cannot represent the imported fit; substituting an acceptance doctrine, abstract strength or statistical resolver is forbidden.
- **Stage 20 freight/routing/handling** — Stage 21E exposes binary physical route/handling availability for Stage-20 adapters. Cargo movement and handling remain owned by the ordinary freight/logistics path.
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

## 5. Tactical materialization

`Stage21ETacticalMaterializationService` requires:

1. `CONTACT_CONFIRMED` operation state;
2. current contact evidence;
3. target still existing as an ordinary `FleetId`;
4. target physically materialized in the observed system;
5. all operation participants physically materialized in that same system;
6. hostile ownership;
7. an exact Stage-19 materialization authority.

The handoff contains the exact current `EntityState` for every combatant, including persisted engineering fit, damage, stores, ammunition and propellant. Stage 21E accepts only a positive encounter identity returned by the Stage-19 authority.

## 6. Physical consequences

`Stage21EPhysicalConsequenceService` is deliberately read-only. It compares before/after `FleetForceRegistry` reconstructions.

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
- friendly traffic is not denied by its own generic operation policy;
- there is no `-X% production`, `-X% trade` or generic route-risk debuff.

Industrial/economic consequences must therefore arise downstream because actual freight cannot traverse/handle a denied physical route or because an ordinary physical asset was actually destroyed. Stage 21E does not directly mutate production output.

Territorial ownership mutation remains with the dedicated territorial authority. Stage 21E invasion/defense operations provide the physical fleets/objective state required by the next territorial-consolidation stage; they do not award territory from an operation score.

## 8. Supply, withdrawal and reinforcement

Supply/withdrawal policy contains decision thresholds only; it grants no resources.

`StrategicOperationService.reviewSupplyAndReadiness(...)` re-reads current ordinary readiness. Depending on physical facts it can:

- continue;
- require submission of an ordinary `WITHDRAW` order;
- fail because no participant survives;
- fail because configured withdrawal is physically impossible without propellant.

The operation service itself never teleports a retreating fleet. The caller must close/cancel the source order as appropriate and submit `WITHDRAW` through the normal Stage-21D order/movement path.

`Stage21EReinforcementService` similarly attaches reinforcement only after the ordinary `FleetId` has already travelled to and physically arrived in the objective system, belongs to the operation faction and meets the operation readiness threshold.

## 9. Persistence contract

Persistent layers:

- `StrategicOperationStateCodec` — deterministic bounded operation payload;
- `Stage21EGeneratedWorldRuntimePersistentState` — Stage-21D checkpoint plus Stage-21E operation metadata;
- `Stage21EGeneratedWorldRuntimePersistenceCodec` — atomic bounded top-level checkpoint with exact schema/runtime identity.

Current schema/runtime identity:

- schema: `8`;
- runtime: `stage21e.generated-world-physical-operations.v8`.

Decode fails closed on invalid magic, future file/schema versions, corrupt bounds, incompatible group/order/system references and trailing bytes.

Historical participant identities may remain in operation metadata after a real physical loss. Existing surviving fleet owners are still cross-checked against the operation faction. This allows a post-battle checkpoint to remember which ordinary identity was lost without resurrecting it.

## 10. Acceptance evidence

Automated coverage includes:

- deterministic operation-state roundtrip including retained contact and active tactical encounter;
- corrupt magic, future payload version and trailing bytes fail closed;
- current actor-bounded security evidence can create a contact;
- stale evidence cannot reveal/materialize a target;
- only the six Stage-21E roadmap operation families are admitted from Stage-21D orders;
- full generated-world Stage-21E checkpoint roundtrip retains the complete Stage-21D world/command layer plus an active physical operation;
- future top-level file/schema versions fail closed.

Repository `clean verify` remains the final acceptance gate and includes unit/integration tests, coverage checks, Javadoc and packaging.

## 11. Stage boundary

Intentionally **not** owned by Stage 21E:

- new fleet spawning or replenishment systems;
- statistical/remote combat resolution;
- doctrine bonuses detached from physical fits;
- direct economic percentage penalties;
- direct territorial ownership mutation or consolidation;
- strategic AI planning/coalition doctrine beyond the current operation execution contract.

Those capabilities must compose this physical operation layer in their respective roadmap stages rather than bypass it.
