# Stage 21F — Occupation, claims, stabilization and control transition

**Status: COMPLETE.** Implementation merged via PR #331 from exact green head `c198ddb4e3b45158e350220187327aa7ed98c8f5`; required CI run #5126 (`32883580620`) and Java 17 verification job `97918646553` completed successfully. Merge commit on `main`: `1294b908ec47c3b4ad9065db17dd5a8a55b4c763`. **Stage 21G is OPEN/NEXT and intentionally not implemented here.**

## 1. Scope

Stage 21F composes physical Stage-21E `INVASION` operations with the existing Stage-17 territorial law. It does **not** introduce a second sovereignty, ownership, fleet, economy, diplomacy or construction authority.

The implemented transition is:

`physical invasion -> supplied/security occupation evidence -> Stage-17 claim -> supported Stage-17 stabilization -> Stage-17 control -> optional recognition`

A fleet entering a system is therefore never sufficient to recolour the system.

## 2. Reused authorities

- `StrategicOperationState` / Stage 21E: persistent `INVASION` identity, participants, ROE, supply policy and lifecycle.
- `FleetForceRegistry` / Stage 21D-21E: exact ordinary physical fleet identities and current readiness/supply observations.
- `FactionIdentityResolver`: runtime/stable faction identity bridge for authored and world-defined factions.
- `WorldSimulation.declareTerritorialClaim(...)` and `withdrawTerritorialClaim(...)`: the only claim mutation paths used by 21F.
- `TerritorialControlRuntime` / Stage 17D: claim stabilization, contest, control acquisition, control maintenance and control loss.
- `TerritorialRecognitionState`: already-persisted Stage-17 political recognition authority.
- `FactionTerritoryService`: shared read-only claim/control projection.
- `TerritorialConstructionAuthorization`: construction law after control changes.
- `WorldTradeRouteCostModel` and existing fiscal policy: route and territorial-tariff consequences.
- `DiplomaticMarketAccessResolver`: market-owner access remains diplomacy-owned; territorial control does not silently seize a foreign market or rewrite its access policy.
- `FactionActorObservationSnapshot` / `FactionInterestResolver`: future faction reasoning receives territory through an actor-visible `TERRITORY_LEDGER` adapter instead of direct omniscient world reads.

## 3. Persistent occupation state

`TerritorialTransitionState` stores only occupation-transition metadata:

- stable occupier faction ID;
- target system;
- originating Stage-21E operation ID;
- start and last-evaluated ticks;
- exact accumulated supported-occupation ticks;
- exact unsupported-since deadline watermark;
- whether the current Stage-17 claim was created by this occupation attempt;
- whether Stage-17 control was ever actually established for this occupation;
- physical transition status.

The `claimCreatedByOccupation` provenance bit is deliberately narrow. It exists only so a failed or unsupported invasion may withdraw **its own** still-unestablished claim. A political claim that existed before the invasion is never reclassified as occupation-owned and is therefore never withdrawn by the occupation cleanup path.

The transition state never stores a competing controller or duplicated claim.

`TerritorialTransitionStateCodec` is deterministic and bounded. It fails closed on invalid magic, unsupported/future version, invalid counts, truncation, malformed enum/state, duplicate faction/system transitions and trailing bytes. Round-trip acceptance preserves claim provenance, control history, progress and exact unsupported deadlines.

### Full generated-world checkpoint

Stage 21F follows the same wrapper model as Stage 21E instead of changing `WorldState`:

- `Stage21FGeneratedWorldRuntimePersistentState` schema `9` / runtime `stage21f.generated-world-territorial-transition.v9` embeds the complete accepted Stage-21E checkpoint unchanged plus Stage-21F transition metadata;
- each occupation must reference an existing persisted Stage-21E `INVASION` and its exact objective system;
- the occupation stable faction must exist in embedded Stage-17 strategic state;
- authored and world-defined faction stable/runtime identity are both cross-checked against the Stage-21E operation owner through the canonical resolver;
- occupation-owned claim provenance is accepted only while the corresponding Stage-17 claim actually exists;
- the occupation `lastEvaluatedTick` may not be ahead of the authoritative active-system clock stored inside the embedded generated world;
- `Stage21FGeneratedWorldRuntimePersistenceCodec` provides deterministic bounded encode/decode and atomic file write/read;
- corrupt magic, future file/schema versions, future occupation evaluation state, truncated nested payloads, invalid cross-layer references and trailing bytes fail closed.

Thus Stage-17 claims/control, Stage-21E operations and Stage-21F occupation progress survive one atomic checkpoint without duplicating their authorities.

## 4. Occupation evidence and failure paths

`TerritorialTransitionService` accepts only `INVASION` operations and requires its supplied evaluation tick to equal the current authoritative world tick.

Occupation progress advances only while surviving operation participants:

- still belong to the expected faction;
- are physically in the objective system;
- meet the operation's minimum mission-readiness threshold;
- meet the operation's minimum supply-access threshold.

A foreign fleet counts as territorial opposition only when its faction is the current Stage-17 controller or owns a Stage-17 claim to the objective. A merely co-located neutral/third-party fleet is not converted into synthetic resistance.

A real territorial rival ordinary fleet produces `CONTESTED` occupation and stalls the occupation clock. No resistance fleet, cargo, money or combat strength is generated by Stage 21F.

Unsupported occupation starts a persistent deadline and deterministically decays accumulated progress. After `OCCUPATION_COLLAPSE_GRACE_TICKS`, the physical occupation collapses and progress resets.

If an invasion-created claim loses supply/security, becomes territorially contested, fails or enters withdrawal before control is established, 21F withdraws that **occupation-owned unestablished claim** through the ordinary Stage-17 API and clears its provenance. Stationary infrastructure therefore cannot continue that military claim after the physical occupation no longer supports it. An unrelated pre-existing claim remains untouched.

An operation already moved to `WITHDRAWING` remains in the ordinary Stage-21E withdrawal lifecycle; 21F records only the territorial transition and any occupation-owned legal-claim cleanup.

## 5. Claim, recognition and control transition

After `REQUIRED_OCCUPATION_TICKS` of supported occupation:

1. 21F calls `WorldSimulation.declareTerritorialClaim(...)` if no claim exists and records that narrow provenance;
2. the invasion operation may complete;
3. Stage-17 territorial evidence continues independently;
4. only Stage 17 may accumulate stabilization and establish control.

Infrastructure is therefore not duplicated in 21F. Stage-17's existing evidence model still requires qualifying physical anchors/forces before stabilization can establish control.

Political recognition participates causally without becoming a substitute authority. `TerritorialRecognitionStabilizationPolicy` reads only persisted Stage-17 `CLAIM` recognition rows and lowers the required uninterrupted qualifying physical stabilization duration by 60 ticks per recognition, capped at 300 ticks of credit. The ordinary unrecognized requirement remains 600 ticks. Recognition cannot create a claim, cannot create infrastructure and cannot establish control when qualifying physical evidence is absent.

When control is finally established, Stage 17 remains the sole controller authority and normalizes the established claim to its ordinary fully-established state. `RECOGNIZED_CONTROL` remains a distinct presentation phase when directed control recognition exists.

## 6. Liberation and established-control history

`controlEverEstablished` exists because a foreign controller during early occupation is not automatically a liberation: it may simply be the pre-existing legal controller while the invader's claim is still stabilizing.

Once Stage 17 has actually established control for the occupier, later observation of a different Stage-17 controller is recorded as `LIBERATED`. Stage 21F does not manufacture the liberating force or perform the control mutation itself.

## 7. Consequences through existing law

After Stage-17 control changes:

- foreign stations retain their original `FactionComponent` allegiance;
- existing ordinary fleets retain the same `FleetId`, placement identity and faction allegiance;
- wallets are not seized or capitalized by territorial transition;
- market-access policy remains owned by the station owner's diplomatic policy;
- ordinary foreign construction is denied without a concession, while domestic construction is allowed;
- the live trade-route cost model sees the new territorial controller and therefore its existing territorial tariff;
- fiscal collection uses the existing money-transfer ledger path;
- the new control entry becomes a durable actor-bounded `BORDER_SECURITY` interest through `TerritorialInterestObservationAdapter` using `TERRITORY_LEDGER` provenance.

`TerritorialInterestObservationAdapter` accepts only the current authoritative world tick, so stale or future ledger reads cannot become autonomous-faction evidence.

This closes the required `control -> future interests / access / route economics` causality without bypassing previous authorities.

## 8. Determinism and continuation

Stage-21F transition arithmetic is based on authoritative elapsed ticks rather than invocation count. Acceptance therefore compares incremental reconciliation with a lumped reconciliation over the same authoritative timeline and requires identical transition state, operation state and Stage-17 claim result.

Save/load continuation is tested at an active unsupported deadline. The baseline and restored worlds are advanced to the same collapse tick and must produce identical occupation progress, deadline consumption, operation state and territorial strategy state. No extra review, deadline shift or free claim is created by restore.

The full Stage-21 integrated command/UI/corpus runtime remains Stage 21I. Stage 21F supplies the deterministic reconciliation service, legal mutations, persistent state and acceptance boundaries that that integrated runtime must invoke; it does not create a second frame loop or duplicate Stage-21A/Stage-20 runtime authority.

## 9. Global-map read model

`TerritorialTransitionService.project(...)` is presentation/read state only and distinguishes:

- `UNCLAIMED`;
- `PRESENCE`;
- `CLAIM`;
- `OCCUPATION`;
- `STABILIZATION`;
- `CONTESTED`;
- `CONTROL`;
- `RECOGNIZED_CONTROL`;
- `LIBERATED`.

The projection never becomes authority.

## 10. Acceptance evidence

Primary Stage-21F tests:

- `TerritorialTransitionStateCodecTest`
  - deterministic exact round trip;
  - exact claim-provenance/progress/deadline/control-history persistence;
  - corrupt/future/truncated/trailing fail-closed cases;
  - duplicate/invariant rejection.
- `Stage21FTerritorialTransitionAcceptanceTest`
  - no immediate recolour on entry/occupation;
  - supplied occupation threshold;
  - Stage-17 infrastructure stabilization before control;
  - post-claim supply loss withdraws the occupation-owned unestablished claim and blocks infrastructure-only control;
  - unsupported decay/collapse;
  - territorial claimant/controller fleet contest;
  - unrelated third-party fleet does not become synthetic resistance;
  - withdrawal path;
  - persisted claim recognition shortens only qualifying physical stabilization and cannot replace infrastructure;
  - world + operation + transition save/load continuity.
- `Stage21FClaimProvenanceAcceptanceTest`
  - a Stage-17 claim that predates invasion remains non-occupation provenance;
  - subsequent supply loss cannot withdraw that pre-existing political claim.
- `Stage21FDeterministicContinuationAcceptanceTest`
  - incremental versus lumped reconciliation over the same timeline yields identical results;
  - save/load at an unsupported deadline resumes to the exact same deterministic collapse.
- `Stage21FGeneratedWorldRuntimePersistenceAcceptanceTest`
  - deterministic full schema-v9 Stage-21F checkpoint round trip over the exact embedded Stage-21E checkpoint;
  - retained fleet/group/operation/occupation identities;
  - unknown operation, wrong objective, wrong faction owner, non-`INVASION` operation and unknown strategic faction rejection;
  - future occupation-evaluation tick rejection against embedded authoritative world time;
  - corrupt/future/truncated/trailing top-level checkpoint rejection.
- `Stage21FLiberationAcceptanceTest`
  - a later real Stage-17 foreign controller marks a previously established occupation `LIBERATED`;
  - no free force is created.
- `Stage21FControlConsequencesAcceptanceTest`
  - route-cost tariff changes causally after control;
  - construction law changes causally after control;
  - foreign station allegiance, wallet and market-access policy are not silently rewritten;
  - fiscal tariff uses the existing treasury/ledger path.
- `Stage21FFleetAllegianceAcceptanceTest`
  - an existing ordinary foreign fleet keeps the same `FleetId`, local placement identity and faction after an unrelated territorial control transfer.
- `Stage21FTerritorialInterestAcceptanceTest`
  - established control becomes an actor-bounded future territorial interest;
  - the adapter invents no interest without actor claim/control evidence;
  - stale/future observation ticks fail closed.

Existing Stage-17 acceptance remains part of the repository regression contract, especially `Stage17DTerritorialControlAcceptanceTest` and `Stage17DControlConsequencesAcceptanceTest`.

Repository `clean verify` remains the final acceptance gate and includes tests, coverage checks, Javadoc and packaging.

## 11. Deliberate future-stage boundaries

Stage 21F does not implement:

- Stage-21G peace/demobilization/repair/rearm/replacement behavior;
- Stage-21H NPC/mission/reputation behavior;
- Stage-21I integrated command UI, save migration, representative corpus, performance and long-run soak;
- new strategic war-planning policy;
- new treaty negotiation behavior;
- synthetic resistance armies;
- automatic station/fleet allegiance transfer;
- a second territorial-controller flag;
- a new customs/market-access system;
- a new construction permission system.

Those boundaries prevent scope creep and preserve the authority contracts established by earlier stages.
