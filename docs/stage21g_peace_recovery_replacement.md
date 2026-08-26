# Stage 21G — Peace, demobilization, recovery and replacement

**Status:** implementation closeout candidate. The implementation PR may merge only from an exact green head. Canonical `COMPLETE` status is recorded only after the implementation merge is verified on `main`.

## 1. Scope

Stage 21G closes a legal/physical conflict loop without resetting the world. It composes the accepted Stage-21C diplomatic/legal state, Stage-21D command orders, Stage-21E physical loss evidence, Stage-18/19 industrial and supply authorities, ordinary fleet/entity state and Stage-17 treasury/treaty/territorial law.

The accepted recovery chain is intentionally causal:

`war evidence -> ceasefire/peace decision -> accepted Stage-21C proposal -> finite recovery plan -> ordinary payments/orders/facilities/yard work -> fresh commissioning -> post-war memory`

Stage 21G does **not** own a second treasury, treaty, fleet roster, damage model, consumable pool, shipyard inventory, territorial controller or war-score currency.

## 2. Reused authorities

- `DiplomaticLifecycleService` / Stage 21C: proposal acceptance, legal war identity/status, treaty/access/legal terms, minimum re-escalation cooldown and persistent relation memory.
- `StrategicWarPolicyService` / Stage 19: physical losses, sustainment pressure, leverage and objective/settlement evaluation inputs.
- `WorldSimulation` Stage-17 economy/diplomacy/territory commands: real faction treasury transfers, accepted treaty clauses, construction/recognition and existing political law.
- `FleetOrderSubmissionService` / Stage 21D: ordinary `RETURN`/redeployment order validation and persistence for surviving command groups.
- `Stage21EPhysicalConsequenceService` / Stage 21E: exact destroyed ordinary `FleetId` evidence.
- `Stage18StationStorage`, `Stage18ShipConsumableService` and Stage-19 warfare supply: finite refuel/rearm stock mutation.
- `ShipyardEngineeringService` and `Stage18ShipyardRuntime`: repair/build planning, finite material settlement and work-capability constraints.
- ordinary `WorldSimulation` entity/fleet registration: replacement becomes capability only after a real built entity exists and a **fresh** `FleetId` is commissioned.
- existing engineering/entity persistence: damage, consumables, maintenance, weapon-feed identity and other supported ship continuity remain in their prior owner state.

## 3. Peace outcome policy

`Stage21GPeaceOutcomePolicy` is a pure bridge over the existing strategic-war policy. It consumes:

- the actor's persisted Stage-21C legal war goals;
- actor-known objective evidence only;
- Stage-19 physical war evidence: surviving force, reaction mass, repair demand/availability and observed opponent losses/logistics denial;
- the existing bounded policy thresholds;
- a visible settlement offer and the exact mandatory goals it covers.

It returns an existing strategic-war decision such as continue/escalate, seek settlement or accept a visible settlement. It does not mutate diplomacy, invent opponent knowledge, create a second exhaustion currency or turn random input into a peace/war cause.

Acceptance proves that an uncovered mandatory legal goal does not become magically satisfied, while a visible offer covering the mandatory goal may be accepted, and real physical exhaustion can cause a settlement-seeking decision.

## 4. Legal settlement and finite recovery plan

`SettlementRecoveryService.openAcceptedSettlement(...)` accepts only an already-accepted Stage-21C `CEASEFIRE` or `PEACE` proposal whose referenced legal war has the exact corresponding status.

The Stage-21G settlement stores only recovery/provenance metadata:

- accepted proposal ID;
- legal war ID and participant pair;
- lifecycle ticks/status;
- finite payment obligations derived from accepted treasury-payment terms;
- surviving command-group demobilization directives;
- exact physical fleet-loss provenance;
- replacement demands;
- one post-war-memory completion bit.

The recovery plan is explicitly finalized before execution. This prevents an empty or partially authored plan from completing merely because an empty collection happened to satisfy an `allMatch` check.

### Existing legal authority stays authoritative

Non-monetary terms are materialized by Stage 21C through the existing Stage-17 authorities **before** Stage 21G consumes the accepted settlement. Stage 21G does not replay treaty/access/recognition/construction effects.

Treasury payment is deliberately different: Stage 21C accepts it as a bounded promise; Stage 21G executes it only through real faction treasuries.

`Stage21GPeaceAuthorityAcceptanceTest` proves both boundaries:

- accepting/opening peace with a reparation promise leaves the complete physical/economic world snapshot unchanged until the explicit payment executor runs;
- a peace market-access clause is already effective through Stage-17 treaty law before Stage-21G recovery opens, and recovery does not apply it a second time;
- territorial recognition and foreign construction rights are applied by existing Stage-17 territorial authority before recovery, and Stage 21G does not duplicate those mutations.

## 5. Reparations and conservation

Each accepted `TREASURY_PAYMENT` term becomes a deterministic `PaymentObligation` identified by settlement + proposal-term ordinal.

Execution:

1. reads the payer's ordinary faction treasury and reserve floor;
2. stalls without partial transfer when spendable treasury is insufficient;
3. debits through `WorldSimulation.transferFromFactionTreasury(...)` into a clearing wallet;
4. credits the recipient through `transferToFactionTreasury(...)`;
5. rolls the debit back if the credit fails;
6. records completion exactly once so save/load/reconciliation cannot duplicate the transfer.

`SettlementRecoveryServiceTest` proves exact payer `-amount`, recipient `+amount`, repeat execution with no second transfer, reserve-floor stall with no partial transfer, and explicit finalization before settlement completion.

No Stage-21G synthetic money balance or reparations wallet persists across ticks.

## 6. Demobilization and surviving fleets

A surviving command group is registered as a finite demobilization obligation before plan finalization. `submitReturnOrder(...)` delegates to the ordinary Stage-21D `FleetOrderSubmissionService` and therefore reuses:

- current physical fleet/group identity;
- ordinary topology routing;
- access and service validation;
- order-source and command-state persistence;
- existing active-order cancellation/replacement semantics.

The recovery row stores only the exact ordinary `RETURN` order ID after submission. Reconciliation is idempotent: repeating the Stage-21G call returns/reuses the already persisted order instead of creating a second strategic order.

`Stage21GRecoveryAuthorityAcceptanceTest.survivingCommandGroupDemobilizesThroughOrdinaryStage21DReturnOrder` proves the full authority chain and route persistence.

Stage 21G does not teleport fleets home or create a demobilization-only movement system.

## 7. Physical losses and replacement demand

Loss provenance can enter Stage 21G only from an exact `Stage21EPhysicalConsequenceService.ConsequenceReport` and its matching pre-consequence `FleetForceRegistry`.

Validation requires:

- the exact Stage-21E operation ID;
- the exact ordinary lost `FleetId` referenced by the operation/consequence;
- stable/runtime faction identity matching one of the settlement participants;
- one immutable loss row per destroyed `FleetId`;
- every replacement demand to bind to the **same settlement** and the **same stable loss owner** as that exact physical loss.

A replacement request is legal only after that loss exists. It stores a deterministic SHA-256 fingerprint of the requested ordinary installed fit and starts as `DEMANDED`; it does not spawn an entity or fleet.

`Stage21GRecoveryAuthorityAcceptanceTest.stage21ELossBecomesDemandOnlyAndCannotRestoreTheDestroyedFleetIdentity` proves:

- a real Stage-21E physical disappearance creates one loss row;
- replaying the same consequence does not duplicate loss;
- one destroyed `FleetId` owns at most one replacement demand;
- a fleet without a persisted physical loss cannot request replacement;
- the demand carries no commissioned fleet identity or completed build entity;
- the destroyed `FleetId` is never restored by the demand path.

`SettlementRecoveryStateCodecTest.replacementDemandCannotReattributeLossAcrossSettlementOrFaction` additionally proves that a forged recovery aggregate cannot move a physical loss to another peace settlement or attribute its replacement to the other participant.

## 8. Repair, rearm and refuel

`Stage21GPhysicalRecoveryService` composes existing physical authorities.

### Refuel

Refuel uses `Stage18ShipConsumableService` against a real `Stage18StationStorage`. The requested reaction-mass commodity is consumed from finite station stock and only the ordinary ship consumable state is updated.

### Rearm

Rearm uses Stage-19 warfare supply identity plus the actual fitted weapon mount/interface. It consumes real manufactured ammunition product counts, preserves the exact feed content identity and rejects incompatible feed mixing or a non-weapon mount.

### Repair

Repair uses `ShipyardEngineeringService.planRepair(...)`, the projected Stage-18 yard capability and ordinary Stage-18 repair settlement. The repair path consumes finite repair inputs/work and mutates damage only after settlement succeeds.

The acceptance test preserves unrelated ordinary continuity while damage is repaired: carried consumables, shared-bus energy, maintenance history and weapon-mount runtime remain unchanged.

Thus peace itself never repairs/refills a ship; recovery happens only after an explicit physical service action with real capability and stock.

## 9. Ordinary replacement build and commissioning

`Stage21GPhysicalRecoveryService.buildReplacement(...)` enforces the replacement chain:

1. resolve an existing `DEMANDED` row backed by a physical loss;
2. verify the requested installed fit against its persisted fingerprint;
3. plan a normal build through `ShipyardEngineeringService`;
4. settle finite Stage-18 yard work/materials;
5. only after successful settlement create an ordinary ECS entity with the expected engineering/faction state;
6. persist the built-entity provenance as `YARD_SETTLED`;
7. register a **fresh** ordinary `FleetId` through normal world fleet registration;
8. persist the exact commissioned identity as `COMMISSIONED`.

If settlement fails, fleet count does not change. If post-build registration fails, the newly created entity is removed rather than leaving detached free capability.

The commissioned fleet cannot reuse the destroyed `FleetId`. A newly built ship starts from the ordinary fresh commissioning state rather than receiving the destroyed ship's ammunition, energy or feed state for free.

`Stage21GPhysicalRecoveryServiceTest.replacementNeedsOrdinaryYardSettlementThenReturnsFreshEmptyFleet` proves failed-then-successful finite settlement, fresh identity, ordinary persisted engineering and empty/drained fresh runtime stores.

## 10. Post-war cooldown, grievance and treaty memory

Stage 21C remains the sole legal owner of peace hysteresis and the minimum re-escalation cooldown. Stage 21G validates that the completed settlement still references a non-active war carrying the Stage-21C cooldown; it never extends, shortens or replaces that field.

`SettlementRecoveryService.recordCompletionMemory(...)` records deterministic bilateral positive `TREATY_PERFORMANCE` evidence only after every finite recovery obligation completes.

`Stage21GPostWarMemoryService` then derives bounded grievance only from persisted physical fleet losses:

- `8` relation-impact points per lost ordinary fleet;
- bounded to `40` points per settlement/owner;
- deterministic event identity `stage21g.settlement.<id>.loss-grievance.<owner>`;
- existing Stage-21C `REMEMBERED_ACTION` factor and `DiplomaticLifecycleService.remember(...)` mutation path;
- repeated reconciliation accepts the exact existing event and never duplicates it;
- an event-ID collision with different semantics fails closed.

This gives future Stage-21B/21C reasoning persisted treaty-performance and loss/grievance evidence while avoiding an unbounded synthetic war-score ledger.

`Stage21GPostWarMemoryServiceTest` proves existing cooldown preservation, bilateral treaty-performance memory, asymmetric loss grievance, exact-once repeat behavior, the grievance cap, and that completed post-war memory changes a later `DiplomaticLifecycleService.selectOutcome(...)` result after operations have ended.

## 11. Persistence contract

### Standalone recovery state

`SettlementRecoveryState` schema `1` persists only Stage-21G recovery/provenance metadata. It canonicalizes deterministic ordering and fails closed on:

- unsupported schema/watermarks;
- duplicate settlement/proposal/payment/demobilization/loss/demand identities;
- future row ticks;
- orphan obligations;
- replacement demand without a physical loss;
- replacement demand whose settlement or faction differs from the exact physical loss provenance;
- multiple demands for one loss;
- commissioned replacement reusing a lost `FleetId`;
- invalid lifecycle combinations.

`SettlementRecoveryStateCodec` provides deterministic bounded standalone encoding/decoding with corrupt/future/truncated/trailing rejection.

### Atomic generated-world checkpoint

`Stage21GGeneratedWorldRuntimePersistentState` schema `10` / runtime `stage21g.generated-world-peace-recovery.v10` embeds the complete accepted Stage-21F checkpoint unchanged plus `SettlementRecoveryState`.

Cross-layer validation requires:

- recovery time not ahead of the embedded authoritative active-system clock;
- each settlement to match an accepted Stage-21C ceasefire/peace proposal and exact legal war participant/status state;
- payment rows to exactly match accepted proposal terms/ordinals;
- demobilizations to reference real Stage-21D command groups with matching stable owner and compatible ordinary `RETURN` orders when present;
- physical losses to reference real Stage-21E operations while the destroyed `FleetId` is absent from ordinary world authority;
- built/commissioned replacements to reference exact ordinary system/entity/fleet state, stable ownership and fit fingerprint;
- a `YARD_SETTLED` entity not yet to be incorrectly registered as a fleet;
- a `COMMISSIONED` demand to resolve to its ordinary current entity/fleet placement.

`Stage21GGeneratedWorldRuntimePersistenceCodec` is deterministic and bounded, embeds the complete Stage-21F payload and recovery payload, supports atomic file replacement, and rejects invalid magic, future file/schema versions, corrupt/truncated nested payloads and trailing bytes.

`Stage21GGeneratedWorldRuntimePersistenceAcceptanceTest` uses a **non-empty accepted peace settlement with a real reparation obligation** to prove byte-identical re-encode, exact embedded Stage-21F retention, legal cross-layer references, authoritative-time rejection and corrupt/future/truncated/trailing fail-closed behavior.

## 12. Roadmap acceptance map

| Stage-21G requirement | Accepted implementation/evidence |
|---|---|
| peace outcomes tied to goals, losses, exhaustion, leverage and visible offers | `Stage21GPeaceOutcomePolicy`, `Stage21GPeaceOutcomePolicyTest` |
| recognition/access/legal terms reuse existing law | Stage-21C acceptance path + `Stage21GPeaceAuthorityAcceptanceTest` proves Stage-17 access, territorial recognition and construction effects exist before recovery and are not replayed |
| reparations conserve treasury transfers | `SettlementRecoveryService.executePayments`, `SettlementRecoveryServiceTest` |
| surviving fleets demobilize through ordinary orders | `SettlementRecoveryService.submitReturnOrder`, `Stage21GRecoveryAuthorityAcceptanceTest` |
| physical loss continuity / no fleet resurrection | `recordPhysicalLosses`, exact replacement provenance and acceptance tests |
| repair/rearm/refuel require physical facility/stock | `Stage21GPhysicalRecoveryService`, physical recovery tests |
| replacement uses existing industrial/shipyard planning | ordinary Stage-18 build settlement + ordinary entity/fleet commissioning test |
| no automatic pre-war fleet restoration | no spawn in demand path; failed build changes no fleet count; commissioned FleetId must be fresh |
| post-war cooldown/grievance/treaty memory | existing Stage-21C cooldown + `Stage21GPostWarMemoryService` and tests |
| deterministic save/load / fail closed | standalone recovery codec tests + schema-v10 generated-world persistence acceptance |

## 13. Exit criteria evidence

### Peace does not repair ships, refill stores or recreate destroyed fleets

Accepted by whole-world snapshot invariance across legal peace/recovery opening, explicit physical service APIs, loss-demand-only acceptance and fresh commissioning requirements.

### Reparations conserve treasury/material transfers

Accepted by exact debit/credit assertions, reserve-floor stall with no partial transfer, rollback semantics and exact-once obligation completion. Physical refuel/rearm/repair/build consume existing stock/work authority rather than hidden recovery grants.

### Destroyed capability returns only after ordinary production and commissioning

Accepted by the Stage-21E physical-loss gate, demand-only state, failed-yard no-fleet-change evidence, ordinary Stage-18 settlement and fresh ordinary FleetId commissioning.

### War changes later decisions after operations end

Accepted by persisted treaty-performance plus bounded loss/grievance relation memory after completed recovery while Stage-21C peace cooldown remains intact. `Stage21GPostWarMemoryServiceTest.completedWarMemoryChangesLaterDiplomaticOutcomeAfterOperationsEnd` feeds the persisted post-war relation back into the existing Stage-21C outcome selector and proves the later diplomatic result changes without a new Stage-21G scoring authority.

## 14. Explicitly deferred to later stages

Stage 21G intentionally does **not** implement:

- Stage-21H NPC identities, dialogue knowledge, missions, reputation or discovery contracts;
- new mission rewards or quest-owned cargo/money/ships;
- a new autonomous shipyard scheduler beyond the existing Stage-18 planning/settlement authority;
- strategic fleet doctrine/content expansion beyond recovery/demobilization seams;
- a second diplomatic scoring model, war-score currency or omniscient loss ledger;
- a second territory, economy, fleet, damage, consumable, treaty or cooldown authority;
- Stage-21I command-UI/final long-run closure work.

Stage 21H may consume the accepted post-war world state only after Stage 21G is formally closed and merged.
