# Stage 21H — NPCs, missions, reputation and discovery grounded in the living world

**Status:** implementation/acceptance candidate. Canonical `COMPLETE` status is recorded only after the implementation PR merges from an exact green head and the resulting `main` is verified. Stage 21I is intentionally out of scope here.

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
→ success pays the exact escrow / failure returns it
→ observed contract outcome may update RPG reputation
```

No UI flag may certify completion and no mission creates its own world outcome.

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

Stage 21H never mutates any of these authorities directly except through already accepted transfer/lifecycle APIs.

## 3. Persistent NPC model

`Stage21HNpcMissionState.NpcState` stores:

- stable NPC ID and localization/name key;
- one of six canonical role archetypes;
- stable faction affiliation;
- current real `StarSystemId` posting;
- availability (`AVAILABLE`, `UNAVAILABLE`, `DISPLACED`, `DEAD`);
- canonical bounded knowledge facts actually received by that NPC.

The six role archetypes are:

1. official;
2. military/security;
3. trade/logistics;
4. industry/yard;
5. exploration/intelligence;
6. independent/frontier.

Presence changes do not reroll identity or erase knowledge.

## 4. Actor-bounded knowledge and dialogue

Knowledge enters Stage 21H through two explicit channels:

- `receiveActorObservation(...)` accepts only a current observation already present in the same affiliated Stage-21A actor snapshot;
- `receiveDiscovery(...)` accepts only an exact static row from the affiliated owner-local Stage-20 discovery state.

Each retained `NpcKnowledgeFact` keeps a stable fact ID, subject, bounded semantic claim, provenance ID, received tick and freshness horizon.

`dialogueFacts(...)` returns only current facts retained by that NPC. It does not read the world, enrich from hidden state or borrow another faction's discovery state.

## 5. Mission contract model

Each `MissionContract` persists:

- stable deterministic mission ID;
- authored template and version;
- issuer NPC and faction;
- exact issuer-known source fact IDs;
- one declarative ordinary-authority objective;
- creation/deadline/status timestamps;
- promised reward and current escrow amount;
- bounded outcome code;
- deduplicated pending event wakeups.

Active statuses are `OFFERED` and `ACCEPTED`. Terminal statuses are `REJECTED`, `COMPLETED`, `FAILED`, `EXPIRED` and `CANCELLED`.

An active contract must retain the entire promised reward in escrow. A terminal contract must retain zero escrow and no pending wakeups.

### 5.1 Causal opportunity gate

A mission is valid only if at least one cited fact was current at creation **and** its claim family is compatible with the mission template. This gate is validated by the persistent aggregate, so malformed restored state fails closed as well as live creation.

Accepted claim families are intentionally bounded:

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

The gate does not invent a universal cross-ID relationship between an interest report and its execution object. Exact target legitimacy is separately validated by the owning ordinary authority.

## 6. Minimum mission set and completion authority

The Imperial gold slice exposes the eight minimum Stage-21H contract families required by the living-world roadmap.

| Contract | Objective predicate | Non-vacuous completion evidence |
|---|---|---|
| emergency supply delivery | `FREIGHT_ORDER_DELIVERED_KG_AT_LEAST` | real Stage-20 delivered mass |
| ordinary market procurement | `FREIGHT_ORDER_DELIVERED_KG_AT_LEAST` | real Stage-20 delivered mass |
| convoy escort | `ESCORT_FLEETS_PRESENT_IN_SYSTEM` | two **distinct** FleetIds co-present in target system |
| stranded fleet rescue/refuel | `FLEET_REACTION_MASS_KG_AT_LEAST` | real fitted reaction-mass store reaches threshold |
| system/object reconnaissance | `DISCOVERY_AT_LEAST` | issuer-local Stage-20 static discovery reaches required state |
| derelict investigation/recovery | `DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST` | required discovery plus finite Stage-18 salvage depletion |
| interception/defense | `OPERATION_STATUS` | owned Stage-21E operation reaches required state |
| construction/repair input delivery | `CONSTRUCTION_DELIVERED_UNITS_AT_LEAST` or physical freight delivery | ordinary project/material delivery state |

The extra Imperial authored access step uses `MARKET_ACCESS_ALLOWED` and observes existing Stage-17 law.

Self-escort is fail-closed: convoy and contracted escort FleetIds must be positive and distinct in persistent objective shape and again at runtime evaluation.

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

The service owns only the matching escrow wallet. Rejection, cancellation, expiry or objective failure returns the exact remaining escrow through `transferToFactionTreasury(...)`. Accepted completion transfers the exact escrow to the supplied authoritative recipient wallet. Stage 21H has no money source/sink path.

If persistent aggregate validation fails after funding, creation rolls the escrow back and does not advance the mission allocator.

## 8. World-driven lifecycle and bounded scheduling

Mission reconciliation reads ordinary state and produces one of three observations:

- `PENDING` — retain the active contract;
- `SATISFIED` — an accepted contract may complete; an unaccepted opportunity resolves without the player and refunds the issuer;
- `FAILED` — the physical/legal target disappeared or became terminal incompatibly, so the mission fails/refunds.

This means ignoring a mission never freezes the source world. Freight may arrive, a project may complete/cancel, a fleet may move/die, an operation may finish, access may change or a finite salvage source may be exhausted without the player.

`MissionWakeup` carries a stable event identity and eligible tick. `dueMissionIds(nowTick, maxMissions)` selects only event/deadline-relevant active missions in stable deadline/ID order and enforces an explicit work budget. There is no every-NPC/every-fixed-tick poll loop.

## 9. RPG reputation boundary

`ReputationState` is directed social memory, separate from Stage-17/21C legal faction relations.

Only observed evidence contributes:

- completed accepted contracts;
- failed accepted contracts;
- explicitly observed betrayal;
- explicitly observed player actions;
- explicitly observed faction outcomes.

Arbitrary caller reputation changes require an NPC-known source fact whose timing is compatible with the event. Replaying the same reputation event ID is idempotent.

## 10. Imperial gold slice and authored chain

`Stage21HImperialGoldSlice` defines six recurring Imperial contacts — exactly one for each canonical role — with stable identity/name keys and an explicit posting system.

It also defines:

- all eight minimum mission blueprints;
- one access-negotiation blueprint used by the authored chain;
- a four-step Imperial `supply → yard → access → security` chain.

The chain stores progress only. It cannot create shortages, projects, treaties, convoys, enemies or outcomes. A later step can be linked only after the previous linked mission is terminal-successful. A non-success terminal world outcome closes the chain honestly; final success marks it complete.

## 11. Persistence contract

Standalone `Stage21HNpcMissionStateCodec` is bounded and deterministic. It rejects unsupported/future schema/file versions, malformed enum ordinals, invalid counts, truncation and trailing bytes.

Generated-world persistence uses:

- checkpoint schema `11`;
- runtime contract `stage21h.generated-world-npc-missions.v11`;
- the complete accepted Stage-21G checkpoint unchanged;
- one Stage-21H NPC/mission sidecar.

Cross-layer validation verifies NPC factions/systems, mission objective systems/identities and RPG time against the embedded ordinary world.

For every active escrow, the embedded ordinary economic ledger must contain the exact faction-treasury → `mission-escrow:<missionId>` funding transfer for the same amount. A forged sidecar escrow therefore cannot manufacture money on restore.

## 12. Acceptance evidence

Focused tests include:

- `Stage21HNpcMissionStateTest` — persistent mission/NPC/story invariants and template/objective compatibility;
- `Stage21HNpcMissionStateCodecTest` — deterministic standalone round-trip and corrupt/future/truncated payload rejection;
- `Stage21HNpcKnowledgeTest` — actor-bounded receipt and dialogue/non-omniscience behavior;
- `Stage21HMissionAuthorityTest` — physical freight ownership, target removal and discovery + finite salvage;
- `Stage21HMissionAuthorityCoverageTest` — distinct-fleet escort, real reaction-mass refuel, Stage-21E operation completion and ordinary construction failure;
- `Stage21HMissionOpportunityCausalityTest` — unrelated current knowledge cannot ground a different mission family, escrow rolls back, self-escort fails at persistent shape;
- `Stage21HNpcMissionServiceTest` — treasury/escrow conservation, payout, reject/refund, world-resolved unaccepted opportunities, reserve-floor enforcement, wakeup budget and observed reputation;
- `Stage21HImperialGoldSliceTest` — exactly six roles, eight minimum contract families and four authored steps;
- `Stage21HStoryChainTest` — successful bounded progression and honest world-closed chain;
- `Stage21HGeneratedWorldRuntimePersistenceAcceptanceTest` — active funded mission round-trip over complete Stage 21G, exact lower-ledger escrow provenance and corrupt/future reference rejection.

Final acceptance still requires the exact implementation HEAD to pass the repository's complete Java 17 verification workflow (tests, coverage, Javadoc and desktop package) and the implementation PR to pass merge/review/base gates.

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
| target move/destruction changes mission | ordinary FleetId/order/project/operation lookups; missing/terminal targets fail closed |
| ignoring mission does not freeze world | OFFERED world-resolution path refunds and terminates without player completion |
| persistence/determinism | standalone codec + schema-11 generated checkpoint + escrow ledger proof |

## 14. Deliberate Stage 21I boundary

Stage 21H intentionally does **not** implement the Stage-21I command/inspection UI, global overlays, timeline/event log, supported-save backward migrations, representative full living-world corpus, scaling benchmark or final long-run Stage-21 soak.

Stage 21I may project the accepted Stage-21H state after Stage 21H is formally closed, but it must not become authority for mission completion, NPC knowledge, reputation, money or world outcomes.
