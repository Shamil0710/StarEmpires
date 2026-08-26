# Stage 21H — NPCs, missions, reputation and discovery grounded in the living world

**Status:** **COMPLETE**. Accepted through PR #335 from exact green implementation head `d57a71b351a22af52e005499a214e41db0701f85`; implementation merge commit on `main`: `ecc57ad27a653d823da4294d121414aeab7c72e9`. Stage 21I is intentionally out of scope here and remains the next Stage-21 closure slice.

## 1. Objective

Stage 21H makes the RPG layer consume the same living-world state as autonomous factions instead of operating as a disconnected quest generator.

The slice adds persistent NPC identity, actor-bounded received knowledge, funded mission contracts, observed RPG reputation and a compact authored Imperial story chain. It does **not** own treasury, cargo, fleets, construction, diplomacy, warfare, industrial stock, discovery truth or territorial state.

The causal contract is:

```text
Stage-21A / Stage-20 actor-known evidence
→ NPC receives a bounded fact
→ a compatible mission opportunity may be offered
→ issuer authority and real unresolved target are validated
→ reward leaves the existing faction treasury into exact mission escrow
→ ordinary world authorities continue independently
→ event/deadline reconciliation observes the real target
→ accepted player-facing settlement separately proves contractor participation
→ success pays the exact escrow / failure returns it
→ observed contract outcome may update RPG reputation
```

No UI flag, caller-provided actor string or caller-provided completion boolean may certify completion. A satisfied world predicate is necessary but is not by itself sufficient to pay a player-facing contract.

## 2. Reused authority map

| Stage-21H concern | Existing authority reused | Stage-21H responsibility |
|---|---|---|
| faction money | `WorldSimulation` faction treasury transfers and ordinary economic ledger | retain exact escrow balance/provenance only |
| physical delivery | Stage-20 `Stage20FreightPersistentState` transport orders/freighters | reference order ID and minimum delivered kg |
| convoy escort | ordinary `FleetId` placement | require two distinct physical fleets co-present in target system |
| stranded refuel | Stage-17.5 engineering consumable state | observe real `REACTION_MASS` mass on the referenced fleet |
| reconnaissance | Stage-20 owner-local discovery knowledge | require discovered static object state without hidden truth |
| derelict recovery | Stage-20 discovery + Stage-18 finite `SALVAGE_STREAM` source | require both discovery and real finite source depletion |
| construction support | ordinary `ConstructionProjectState` | observe delivered units/terminal project state |
| access | existing Stage-17 market-access/treaty evaluation | observe legal access only |
| defense/interception | Stage-21E `StrategicOperationState` | observe operation lifecycle only |
| NPC opportunity evidence | Stage-21A actor observation / Stage-20 discovery evidence | retain only facts explicitly delivered to the NPC |
| player contractor identity/participation | persistent `PlayerState`, player-owned `FleetId` / `ConstructionProjectId`, owner-local Stage-20 discovery and Stage-21E participant fleets | prove bounded participation without becoming cargo/combat/discovery authority |

Stage 21H never mutates these authorities directly except through already accepted treasury/lifecycle APIs. `Stage21HMissionAuthority` reads whether the ordinary world reached the objective. `Stage21HPlayerMissionAuthority` is a second read-only gate that asks whether existing persistent authorities can prove participation by the player contractor.

## 3. Persistent NPC model

`Stage21HNpcMissionState.NpcState` stores:

- stable NPC ID and localization/name key;
- one of six canonical role archetypes;
- stable faction affiliation;
- current real `StarSystemId` posting;
- availability (`AVAILABLE`, `UNAVAILABLE`, `DISPLACED`, `DEAD`);
- canonical bounded knowledge facts actually received by that NPC.

The six role archetypes are official, military/security, trade/logistics, industry/yard, exploration/intelligence and independent/frontier. Presence changes do not reroll identity or erase knowledge.

## 4. Actor-bounded knowledge and dialogue

Knowledge enters Stage 21H only through explicit bounded channels:

- `receiveActorObservation(...)` accepts an observation only when it is already present in the same affiliated Stage-21A actor snapshot;
- `receiveDiscovery(...)` accepts only an exact static row from the affiliated owner-local Stage-20 discovery state.

Each retained `NpcKnowledgeFact` keeps a stable fact ID, subject, bounded semantic claim, provenance ID, received tick and freshness horizon. `dialogueFacts(...)` returns only current facts retained by that NPC; it does not requery hidden world truth or borrow another actor's discovery state.

## 5. Mission contract model

Each `MissionContract` persists stable mission/template identity, issuer NPC/faction, exact issuer-known source facts, one declarative ordinary-authority objective, lifecycle timestamps, deadline, promised reward, exact escrow balance, bounded outcome code and deduplicated event wakeups.

Active statuses are `OFFERED` and `ACCEPTED`. Terminal statuses are `REJECTED`, `COMPLETED`, `FAILED`, `EXPIRED` and `CANCELLED`. An active contract must retain the entire promised reward in escrow; a terminal contract must retain zero escrow and no pending wakeups.

### 5.1 Causal opportunity gate

A mission is valid only if at least one cited fact was current at creation and its claim family is compatible with the mission template. The persistent aggregate validates this gate as well as live creation, so malformed restored state fails closed.

Accepted claim families remain bounded:

| Template | Required causal claim family |
|---|---|
| emergency supply delivery | `ECONOMIC.RESOURCE_DEFICIT` |
| ordinary market procurement | `ECONOMIC.SUPPLY_DEPENDENCY` or resource deficit |
| convoy escort | `SECURITY.ROUTE_EXPOSURE` |
| stranded rescue/refuel | `SECURITY.ROUTE_EXPOSURE` |
| system/object reconnaissance | Stage-20 `DISCOVERY.*` / static-object discovery |
| derelict investigation/recovery | `DISCOVERY.SPECIAL_LOCATION*` |
| interception/defense | `SECURITY.BORDER_SECURITY` or route exposure |
| construction/repair input delivery | resource deficit or supply dependency |
| Imperial access negotiation | `DIPLOMATIC.MARKET_ACCESS` |

Exact target legitimacy is separately validated by the ordinary owning authority; Stage 21H does not invent a universal cross-ID relationship between an interest report and an execution object.

## 6. Mission set, world completion and contractor proof

The Imperial gold slice exposes the eight minimum Stage-21H contract families plus the access-negotiation story step.

| Contract | Ordinary objective evidence | Player-facing participation proof |
|---|---|---|
| emergency supply delivery | real Stage-20 delivered mass | assigned transport `FleetId` is player-owned |
| ordinary market procurement | real Stage-20 delivered mass | assigned transport `FleetId` is player-owned |
| convoy escort | two distinct FleetIds co-present in target system | contracted escort `FleetId` is player-owned |
| stranded fleet rescue/refuel | real fitted reaction-mass store reaches threshold | target is player-owned or a player-owned fleet is physically co-present |
| system/object reconnaissance | issuer-local Stage-20 discovery reaches required state | equivalent evidence exists in owner-local `actor.player` Stage-20 knowledge |
| derelict investigation/recovery | required discovery plus finite Stage-18 salvage depletion | player discovery evidence plus player-owned physical fleet presence in the site system |
| interception/defense | Stage-21E operation reaches required state | a player-owned `FleetId` is a real operation participant |
| construction/repair input delivery | ordinary project/material delivery state | project is player-owned or a player-owned fleet is physically co-present |
| Imperial access negotiation | existing Stage-17 access law | player's persistent faction affiliation is the requesting participant |

Unsupported or causally ambiguous cases fail closed. In particular, `FLEET_ABSENT` cannot prove who caused the absence and therefore cannot by itself pay a player contract. Self-escort is rejected structurally: convoy and contracted escort FleetIds must be positive and distinct.

This split is deliberate: `Stage21HMissionAuthority` answers **did the world reach the objective?**; `Stage21HPlayerMissionAuthority` answers **can existing persistent authorities prove player participation?**. Neither accepts a UI success flag.

## 7. Issuer authority and reward conservation

Before an offer can exist, Stage 21H validates that the issuer may lawfully reference the target:

- freight order owner must equal issuer faction;
- fleet/refuel target must be issuer-owned where ownership is required;
- construction project must be issuer-owned or legally affiliated;
- discovery state must be issuer-local;
- strategic operation must be owned by the issuer faction;
- treasury objective may reference only the issuer's own economic state;
- access negotiation issuer must be the participant seeking access.

Only after target/issuer validation and a `PENDING` ordinary objective does the reward leave the faction treasury through `WorldSimulation.transferFromFactionTreasury(...)`.

The service owns only the matching escrow wallet. Rejection, cancellation, expiry or objective failure returns the exact remaining escrow through `transferToFactionTreasury(...)`. A player-facing accepted completion pays only after both ordinary objective satisfaction and `Stage21HPlayerMissionAuthority.PARTICIPATED` are proven. If the world satisfies an accepted objective without provable player participation, the contract terminates fail-closed, the escrow returns to the issuer, no completion reputation is created, and the outcome records `opportunity.resolved-without-contractor:*`.

The legacy `reconcileMission(...)` overload that accepted a caller-selected wallet and actor identity is retained only as a fail-closed compatibility seam and cannot settle a reward. Stage 21H has no money source/sink path. If persistent aggregate validation fails after funding, creation rolls escrow back and does not advance the mission allocator.

## 8. World-driven lifecycle and bounded scheduling

Ordinary mission reconciliation produces `PENDING`, `SATISFIED` or `FAILED` from existing world authorities. `PENDING` retains the contract. `FAILED` refunds and terminates. `SATISFIED` resolves an unaccepted opportunity without the player; for an accepted player-facing contract it starts the independent contractor-participation gate described above rather than directly paying.

Ignoring a mission never freezes the source world. Freight may arrive, a project may complete/cancel, a fleet may move/die, an operation may finish, access may change or a finite salvage source may be exhausted without the player.

`MissionWakeup` carries stable event identity and eligible tick. `dueMissionIds(nowTick, maxMissions)` selects only event/deadline-relevant active missions in stable deadline/ID order under an explicit work budget. There is no every-NPC/every-fixed-tick poll loop.

## 9. RPG reputation boundary

`ReputationState` is directed social memory, separate from Stage-17/21C legal faction relations. Only observed evidence contributes: completed accepted contracts, failed accepted contracts, explicitly observed betrayal, explicitly observed player actions and explicitly observed faction outcomes.

Arbitrary caller reputation changes require an NPC-known source fact whose timing is compatible with the event. Replaying the same reputation event ID is idempotent. Successful player-contract reputation uses the canonical `actor.player` subject only after contractor proof succeeds.

## 10. Imperial gold slice and authored chain

`Stage21HImperialGoldSlice` defines six recurring Imperial contacts — one for each canonical role — with stable identity/name keys and an explicit posting system. It defines all eight minimum mission blueprints, the access-negotiation blueprint and a four-step Imperial `supply → yard → access → security` chain.

The chain stores progress only. It cannot create shortages, projects, treaties, convoys, enemies or outcomes. A later step can be linked only after the previous linked mission is terminal-successful. A non-success terminal world outcome closes the chain honestly; final success marks it complete.

## 11. Persistence contract

Standalone `Stage21HNpcMissionStateCodec` is bounded and deterministic. It rejects unsupported/future schema/file versions, malformed enum ordinals, invalid counts, truncation and trailing bytes.

Generated-world persistence uses checkpoint schema `11`, runtime contract `stage21h.generated-world-npc-missions.v11`, the complete accepted Stage-21G checkpoint unchanged and one Stage-21H NPC/mission sidecar.

Cross-layer validation verifies NPC factions/systems, mission objective systems/identities and RPG time against the embedded ordinary world. For every active escrow, the embedded ordinary economic ledger must contain exactly one matching faction-treasury → `mission-escrow:<missionId>` funding transfer for the same amount. A forged sidecar escrow therefore cannot manufacture money on restore.

Contractor proof itself is derived from existing persistent `PlayerState`, Stage-20 discovery, FleetId/construction/operation state at settlement time and therefore does not duplicate those authorities inside the Stage-21H sidecar.

## 12. Acceptance evidence

Focused tests include:

- `Stage21HNpcMissionStateTest` — persistent mission/NPC/story invariants and template/objective compatibility;
- `Stage21HNpcMissionStateCodecTest` — deterministic standalone round-trip and corrupt/future/truncated payload rejection;
- `Stage21HNpcKnowledgeTest` — actor-bounded receipt and dialogue/non-omniscience behavior;
- `Stage21HMissionAuthorityTest` — physical freight ownership, target removal and discovery + finite salvage;
- `Stage21HMissionAuthorityCoverageTest` — distinct-fleet escort, real reaction-mass refuel, Stage-21E operation completion and ordinary construction failure;
- `Stage21HPlayerMissionAuthorityTest` — player-bound freight/escort/discovery/construction/operation participation and fail-closed ambiguous absence causation;
- `Stage21HMissionOpportunityCausalityTest` — unrelated current knowledge cannot ground a different mission family, escrow rolls back, self-escort fails at persistent shape;
- `Stage21HNpcMissionServiceTest` — treasury/escrow conservation, contractor-bound payout, accepted objective resolved by the world without contractor payout, legacy caller self-certification rejection, reject/refund, unaccepted world resolution, reserve-floor enforcement, wakeup budget and observed reputation;
- `Stage21HImperialGoldSliceTest` — exactly six roles, eight minimum contract families and four authored steps;
- `Stage21HStoryChainTest` — successful bounded progression and honest world-closed chain;
- `Stage21HGeneratedWorldRuntimePersistenceAcceptanceTest` — active funded mission round-trip over complete Stage 21G, exact lower-ledger escrow provenance and corrupt/future reference rejection.

Acceptance evidence is green on implementation head `d57a71b351a22af52e005499a214e41db0701f85`: CI run #5307 (`32978603732`), Java 17 verification job `98209354294`, including repository tests, coverage checks, Javadoc and desktop packaging. PR #335 passed the inspected merge/base/review gates and was merged to `main` as `ecc57ad27a653d823da4294d121414aeab7c72e9`.

## 13. Stage 21H exit map

| Roadmap requirement | Implementation evidence |
|---|---|
| persistent NPC identity/role/affiliation/location/knowledge/availability | `Stage21HNpcMissionState.NpcState` + codec |
| actor-bounded knowledge/dialogue | Stage-21A/20 receive boundaries + `dialogueFacts` |
| observed RPG reputation | bounded event-backed `ReputationState` |
| missions arise from living-world evidence | persistent causal opportunity claim gate + real pending objective validation |
| issuer/objective/deadline/reward/failure | `MissionContract`, issuer authority adapter, conserved escrow lifecycle |
| discovery respects information boundaries | owner-local Stage-20 knowledge only |
| bounded event-driven scheduling | deduplicated wakeups + explicit `maxMissions` budget |
| completion delegates to world authorities | freight/fleet/engineering/discovery/salvage/construction/access/operation evaluators |
| completion cannot self-certify from UI input | fail-closed legacy settlement seam + independent `Stage21HPlayerMissionAuthority` contractor proof before payout |
| target move/destruction changes mission | ordinary FleetId/order/project/operation lookups; missing/terminal targets fail closed |
| ignoring mission does not freeze world | OFFERED world-resolution path refunds and terminates without player completion |
| accepted mission solved by other actors does not pay player | world satisfaction without contractor proof refunds issuer and records non-completion failure |
| persistence/determinism | standalone codec + schema-11 generated checkpoint + escrow ledger proof |

## 14. Deliberate Stage 21I boundary

Stage 21H intentionally does **not** implement the Stage-21I command/inspection UI, global overlays, timeline/event log, supported-save backward migrations, representative full living-world corpus, scaling benchmark or final long-run Stage-21 soak.

Stage 21I may project accepted Stage-21H state after Stage 21H is formally closed, but it must not become authority for mission completion, NPC knowledge, reputation, money or world outcomes.
