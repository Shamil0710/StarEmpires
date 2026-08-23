# Stage 21C — Diplomacy, crisis, alliance and war lifecycle

**Status:** **COMPLETE** for the Stage-21C implementation and acceptance contract. Repository merge remains subject to the exact-head CI/review gate; Stage 21D is OPEN/NEXT and is intentionally not implemented here.

## Scope

Stage 21C consumes accepted Stage-21B strategic intent and actor-bounded diplomatic evidence, then records persistent negotiation, crisis, obligation and legal-war state. It deliberately composes the existing authorities instead of creating a second treaty, access, tariff, treasury, territory or warfare system.

```text
accepted Stage-21B goal
+ actor-bounded relation memory
→ bounded proposal / counter-offer
→ accept, reject, expire or persistent crisis
→ pressure → ultimatum → explicit WAR_AUTHORIZED decision
→ legal war identity linked to Stage-19 conflicts
→ ceasefire / peace with re-escalation hysteresis
```

An observed hostile attack is the only direct alternative legal cause for war. A relation score, doctrine score or random tie-break can never create war by itself.

## Stage-21B consumption and actor-bounded evidence

`StrategicDiplomaticProposalPlanner` is a pure planning seam. It receives an exact persisted `FactionStrategicIntentState`, exact goal ID, recipient ID and directed `RelationMemory`; it has no `WorldSimulation` reference and cannot inspect hidden treasury, territory, fleets or markets.

Only an `ACTIVE` persisted goal may issue an autonomous diplomatic request. Non-diplomatic goals such as stockpiling or exploration return no proposal rather than inventing political action. The produced `ProposalRequest.sourceGoalId` preserves the exact Stage-21B goal identity.

`RelationMemory` stores directed remembered events. The complete Stage-21C relation-factor vocabulary is:

- remembered actions;
- treaty performance;
- territorial conflict;
- trade dependence;
- threat;
- diplomatic commitments.

Future-dated evidence is rejected at mutation time and again by the complete generated-checkpoint composition. Derived relation is a clamped projection over persisted actor-known events, not a hidden global relation roll.

## Proposal and counter-offer contract

`ProposalKind` contains all required families:

- access;
- trade;
- recognition;
- construction rights;
- non-aggression;
- defensive cooperation;
- alliance;
- embargo;
- ultimatum;
- ceasefire;
- peace.

Each proposal has stable identity, source/cause, participants, issue, demands, concessions, creation/update ticks, response deadline, status and optional crisis/treaty linkage.

`DiplomaticCounterOfferService` creates a bounded reversed proposal through the same lifecycle service, persists causal lineage as `counter-proposal:<parentProposalId>`, and rejects the replaced original proposal through the normal rejection path. Counter-offer feasibility is validated before the original is rejected, so an impossible treasury or territorial concession cannot destroy a still-valid offer.

Proposal response deadlines are intentionally separate from Stage-17 treaty lifetime. A materialized Stage-17 treaty offer currently uses the existing indefinite treaty lifetime (`expiresTick = -1`) because Stage 21C owns the response deadline, not treaty-duration policy. If a Stage-21C proposal expires before acceptance, its linked Stage-17 proposal is explicitly rejected so no stale independent treaty offer remains.

## Authority boundaries

### Stage 17 remains political/economic law

Stage 21C delegates executable effects through existing `WorldSimulation` boundaries:

- treaty offer/accept/reject/breach through `applyDiplomaticTreatyCommand`;
- embargo impose/revoke through the Stage-17 embargo command;
- ordinary market access through Stage-17 diplomatic access resolution;
- customs tariff exemption through the existing tariff resolver;
- territorial recognition and construction rights through existing territorial commands;
- monetary feasibility from the real faction treasury and reserve floor.

Negotiation cannot promise treasury beyond currently spendable real funds and cannot grant construction rights over territory the grantor does not control.

Actual reparations transfer is intentionally deferred to Stage 21G. Stage 21C records and bounds a treasury-payment promise but does not create escrow, duplicate credits or mutate a parallel wallet.

The existing Stage-17 treaty schema has no independent non-aggression clause. Stage 21C therefore persists non-aggression as an explicit negotiated legal commitment without prematurely extending the older authority. Defensive cooperation and alliance reuse the existing Stage-17 `GUARANTEE` clause where a concrete treaty effect exists.

### Stage 19 remains warfare authority

Stage 21C owns the legal/political war identity and causal evidence. A lawful declaration creates exactly two actor-perspective Stage-19 conflict records carrying explicit objectives. Physical fleet readiness, movement, interception and operations remain Stage 21D/21E responsibilities.

## Crisis and war lifecycle

Persistent crisis escalation is:

```text
NEGOTIATION
→ PRESSURE
→ ULTIMATUM
→ WAR_AUTHORIZED
```

Acceptance may instead resolve the linked crisis. Every escalation step stores explicit decision/evidence identity and a deadline.

A legal war stores:

- stable war identity;
- canonically ordered participants;
- explicit objectives for both participants;
- causal `WarStartEvidence`;
- exact Stage-19 conflict IDs;
- legal status;
- start/status-change ticks;
- re-escalation cooldown.

Admissible start evidence is only:

1. a persisted crisis already in `WAR_AUTHORIZED`, with matching participants and decision evidence; or
2. an explicitly actor-observed hostile attack with a valid observation tick.

The direct hostile-attack path accepts explicit actor-observed evidence identity and observation tick from the caller rather than inventing a second Stage-21A intelligence schema. Stage 21A remains the owner of bounded observation/report channels.

War cannot predate its evidence. An existing active war blocks duplicate declaration. Ceasefire and peace impose a minimum re-escalation cooldown of `600` ticks, and peace is terminal for that legal war identity.

## Alliance and treaty obligations

A real active Stage-17 guarantee may be evaluated as an obligation. Honoring it persists a positive commitment-memory event and leaves the treaty active. Refusal is allowed, but the existing Stage-17 treaty is breached through the treaty command boundary and the beneficiary records a negative reputational memory event.

No free fleet, combat participation or hidden enforcement is created by the obligation decision; physical honoring remains a later military-order concern.

## Outcome selection and randomness boundary

`DiplomaticLifecycleService.selectOutcome` uses actor-bounded relation, trade dependence, threat, commitments, persisted crisis escalation and presence of a credible settlement offer.

Random/deterministic tie-break input is consulted only when two peaceful alternatives have exactly equal scores. It can choose between trade and deterrence, but cannot produce `WAR`. War requires the explicit crisis/hostile-attack predicates above.

The fixed Stage-20 representative root seeds `1..16` are now exercised through actual generated-world runtimes. Each seed is paired with a predeclared persisted political-history fixture (trade, deterrence, negotiated resolution or causal war); the root seed is used only as the bounded tie-break input and is never itself the war cause. This keeps the diversity proof non-vacuous without introducing seed-specific production exceptions.

## Persistence

`DiplomaticLifecycleStateCodec` deterministically persists relation memory, proposals, crises, wars, goals and obligation decisions. It rejects corrupt magic, truncation, trailing bytes, unsupported stale/future file versions and unsupported future lifecycle schemas.

`Stage21CGeneratedWorldRuntimePersistentState` schema `6` / runtime contract `stage21c.generated-world-diplomacy-lifecycle.v6` embeds the accepted Stage-21B checkpoint unchanged and composes it with Stage-21C diplomacy plus Stage-19 warfare state.

Restore validation fails closed when cross-layer references are invalid, including:

- unknown faction identities;
- future-dated actor relation evidence or obligation decisions relative to the checkpoint tick;
- proposal links to missing Stage-17 treaties;
- inconsistent proposal/crisis linkage;
- obligation decisions referencing missing treaties;
- crisis-based war evidence that does not match the persisted authorized crisis;
- legal wars referencing missing or mismatched Stage-19 conflicts.

Mid-lifecycle acceptance restores and continues exactly one transition from proposal, counter-offer, ultimatum and ceasefire boundaries.

## Acceptance mapping

| Requirement | Evidence |
|---|---|
| All six remembered relation factors are persistent actor-known evidence | `DiplomaticLifecycleServiceIntegrationTest.allRequiredRelationFactorsContributeOnlyThroughRememberedActorEvidence` |
| All eleven proposal families have stable persistent identities | `DiplomaticLifecycleServiceIntegrationTest.everyRequiredProposalFamilyHasPersistentIdentity` |
| Accepted Stage-21B goals feed diplomacy without hidden world reads | `StrategicDiplomaticProposalPlannerTest` |
| Bounded counter-offer with causal lineage | `DiplomaticCounterOfferServiceTest` |
| Invalid counter-offer cannot destroy original offer | `DiplomaticCounterOfferServiceTest.infeasibleCounterDoesNotDestroyOriginalProposal` |
| Real treasury/territory bounds | `DiplomaticLifecycleServiceIntegrationTest.negotiationCannotPromiseTreasuryOrTerritoryTheGrantorDoesNotOwn` |
| Treaty access and tariff use Stage-17 law | `DiplomaticLifecycleServiceIntegrationTest.acceptedTradeProposalUsesStage17TreatyAccessAndTariffLaw` |
| Response deadline is not accepted treaty lifetime | proposal/treaty deadline integration tests |
| Guarantee may be honored or refused with real treaty/reputation consequence | guarantee obligation integration tests |
| War needs persisted crisis decision before declaration | `warRequiresPersistedCauseCreatesStage19ConflictsAndCannotOscillateAfterPeace` |
| Observed hostile attack is an explicit alternate cause without fabricated crisis | `observedHostileAttackCanCreateLegalWarWithoutFabricatingACrisis` |
| Random input cannot select war | `Stage21CRepresentativeOutcomeAcceptanceTest` plus planner tests |
| Fixed generated-world corpus covers trade, deterrence, negotiated resolution and causal war | `Stage21CRepresentativeOutcomeAcceptanceTest.fixedGeneratedSeedCorpusExercisesDifferentPersistedPoliticalHistories` |
| Deterministic codec round-trip and corrupt/stale/future rejection | `DiplomaticLifecycleStateCodecTest` |
| Complete generated checkpoint rejects future actor evidence | `Stage21CGeneratedWorldRuntimePersistenceAcceptanceTest.compositionRejectsFutureActorMemoryEvidence` |
| Generated-world Stage-17/21C/19 composition round-trips | `Stage21CGeneratedWorldRuntimePersistenceAcceptanceTest` |
| Proposal/counter-offer/ultimatum/ceasefire continue after restore | `Stage21CDiplomaticMidLifecyclePersistenceAcceptanceTest` |

## Exit criteria mapping

- War cannot begin without a persisted causal crisis/decision or observed hostile attack: enforced in service and persistence cross-validation.
- Random input is bounded to peaceful tie-breaking and cannot be the reason for war.
- Treaties and wars round-trip while accepted treaty clauses affect ordinary access/tariff law.
- The fixed generated-world corpus exercises trade, deterrence, negotiated resolution and causal war rather than one universal result, while explicit persisted political history—not seed randomness—remains the causal input.

## Later-stage boundary

Stage 21C stops at political/legal causality. It intentionally does not implement:

- Stage 21D fleet readiness, command groups, routing or validated movement orders;
- Stage 21E physical operations, interception, blockade/invasion execution or tactical consequence return;
- Stage 21F occupation/stabilization/control transition;
- Stage 21G reparations transfer, demobilization, repair, rearm, refuel or replacement;
- Stage 21H NPC/mission/reputation content loops;
- Stage 21I final integrated command UI, migration corpus, performance and long-run soak.

Those slices must consume Stage-21C political state without bypassing the Stage-17/19 authorities described above.
