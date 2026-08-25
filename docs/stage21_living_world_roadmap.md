# Stage 21 — Living World / Autonomous Factions roadmap

> Status: **ACTIVE**. Stage 20 and Stage 20.5 are complete. Stage 21.0, Stage 21A, Stage 21B,
> Stage 21C, Stage 21D and **Stage 21E are complete; Stage 21F is OPEN/NEXT**.

## 1. Purpose

Stage 21 turns the accepted physical galaxy into a persistent living world. Generated factions,
fleets, NPCs, missions, discoveries and reputation must react to the same economic, political,
territorial and military state that the player can inspect and change.

The mandatory autonomous-faction causal chain is:

```text
actor-bounded observations
→ measurable interests, dependencies and threats
→ persistent strategic intent
→ diplomacy, crisis or cooperation
→ ordinary fleet / economy / logistics commands
→ physical movement, operation and loss
→ territorial, economic and political consequences
→ changed observations and future intent
```

Stage 21 is not allowed to substitute scripted map colour changes, arbitrary random wars or hidden
NPC resources for this chain.

## 2. Upstream authority consumed by Stage 21

| Existing stage | Authority reused by Stage 21 |
|---|---|
| Stage 15–16 | persistent fleets, stations, ordinary inter-system movement and construction |
| Stage 17 | faction identity, treasury, policy, diplomacy, treaties, embargoes, claims, stabilization and control |
| Stage 17.5 | fitted ship engineering, sensors, information limits, damage and physical capability |
| Stage 18 | finite extraction, storage, production, shipyards, repair, ammunition, propellant and replacement cost |
| Stage 19 | mobilization, coercive diplomacy, tactical operations, blockades, losses and negotiated outcomes |
| Stage 20 | generated topology, physical coordinates, resource scarcity, dependencies and faction starts |
| Stage 20.5 | live generated industrial, freight, exact-arrival, persistence and visual bindings |

Stage 21 composes these authorities. It must not create a parallel diplomacy system, strategic
combat-stat system, territory flag, fleet registry, economy, cargo store or movement implementation.

## 3. Hard invariants

1. Every faction, fleet, station, NPC, mission and strategic operation has persistent stable identity.
2. A faction may decide only from its actor-bounded knowledge, reports and allowed intelligence.
3. No omniscient scan of hidden world state may be used as faction reasoning input.
4. Strategic AI runs through bounded cadence and event wakeups, not every actor on every fixed tick.
5. The player and autonomous factions use the same command and validation boundaries.
6. War declarations, treaties, claims and control changes persist and survive save/load exactly.
7. Fleet travel uses ordinary neighbor routes and the existing jump FSM; no strategic teleport exists.
8. Combat readiness is derived from fitted ships, damage, crew, ammunition, reaction mass and supply.
9. Destroyed ships and consumed cargo remain lost; no free replacement or hidden replenishment exists.
10. New ships require compatible shipyards, physical products, labor, power, time and treasury authority.
11. A blockade, raid or occupation changes the world only through ordinary physical/economic state.
12. Territory does not flip instantly after one arrival; presence, claim, occupation, stabilization,
    recognition and control remain distinct Stage-17 states.
13. Diplomacy is not a periodic random relation roll. Decisions must retain causal evidence.
14. Anti-oscillation cooldowns and commitment horizons prevent war/peace and goal churn.
15. Peaceful trade, deterrence, alliance and limited coercion remain valid outcomes; every seed must
    not be forced into total war.
16. Provisional Stage-17.5/19 combat fits remain explicitly temporary until Stage 22 reviews them.
17. UI and missions project authoritative state and never become simulation authority.

## 4. Runtime model and cadence

Living-world work is split by relevance instead of duplicating simulation fidelity:

- fixed/local ticks continue to own movement, combat and ordinary economic execution;
- event wakeups react to arrivals, attacks, losses, treaty changes, shortages and completed projects;
- short strategic cadence updates active crises, fleet orders and urgent supply problems;
- medium policy cadence reviews interests, readiness, diplomacy and production commitments;
- long institutional cadence reviews doctrine preferences, durable alliances and expansion posture;
- inactive actors retain persistent deadlines and summaries rather than running full tactical AI.

Every cadence must be deterministic from saved state. Loading a checkpoint cannot create an extra
decision, skip a deadline or change tie-breaking order.

## 5. Delivery slices

### 21.0 — Generated-world command foundation — COMPLETE

Delivered entry seam:

- scalable Cyrillic TTF UI using the accepted Imperial graphite/ivory/burgundy/brass language;
- separate system, galaxy, factions, military-forces and logistics surfaces;
- selectable physical objects and structured read-only inspection;
- finite generated-faction patrols using ordinary sequential `FleetId` identities;
- cursor-anchored map zoom, middle-button pan and double-click fleet focus;
- exact local fleet physical sidecar in the atomic generated-runtime checkpoint;
- Windows `run-generated-world.bat` entry point.

This slice makes the accepted world inspectable. It does not itself grant autonomous strategic
decision-making.

### 21A — Living actor kernel and interest evidence — COMPLETE

Objective: give every autonomous faction a bounded persistent decision lifecycle.

Delivered:

- `FactionLivingActorState` bound to existing stable faction identity;
- persisted next-review deadlines, commitment horizons and deduplicated event wakeup reasons;
- actor-bounded economic, territorial, security and diplomatic observation snapshot;
- interest evidence for supply dependency, market access, route exposure, resource deficit,
  border security, territorial opportunity and treaty obligation;
- deterministic priority/conflict resolution without direct stat bonuses;
- canonical decision trace suitable for tests and later UI explanation;
- bounded top-K relevance scheduler with hard review budget;
- atomic lifecycle runtime that publishes observations only for selected actors;
- generated-world composition bridge and Stage-21A persistence envelope over the unchanged Stage-20
  checkpoint authority.

Accepted evidence:

- equivalent bounded facts produce byte-identical `DecisionTrace` bytes regardless of input order;
- save/load immediately before a deadline produces exactly one review;
- wakeups survive persistence, are consumed once, and forged scheduler authorization fails closed;
- stale/hidden-like evidence cannot affect a decision until a fresh allowed observation is present;
- no review layer has direct authority to create money, cargo, ships, territory or relations;
- a 10,000-due-actor acceptance case retains only the configured top-K review budget;
- failed observation publication leaves a selected batch atomically unchanged;
- a real `Stage20PlayableGeneratedWorldFactory` checkpoint round-trips through the Stage-21A
  composition and remains byte-identical before and after actor-only review state changes.

Implementation and acceptance map: `docs/stage21a_living_actor_kernel.md`.

### 21B — Strategic intent, goals and commitment — COMPLETE

Objective: convert measured interests into durable, explainable goals.

Delivered:

- goal families for secure-route, obtain-access, stockpile, defend, escort, explore, claim,
  deter, coerce, raid, blockade, invade and recover;
- explicit target, evidence, urgency, cost ceiling, success condition, failure condition and expiry;
- compatibility with existing Stage-17 faction doctrine preferences without making doctrine a buff;
- force/economic feasibility assessment before commitment;
- competing-goal budget arbitration across treasury, logistics, construction and fleet readiness;
- hysteresis, minimum commitment duration and material-change wakeups;
- cancellation consequences rather than free instantaneous retasking.

Accepted evidence:

- all 13 goal families have stable persistence identities and doctrine-gated candidate generation;
- candidate ranking uses persisted urgency, strategic value, feasibility and doctrine preference;
- infeasible or over-budget goals remain explainable through typed blockers and `STALLED` state;
- accepted goals preserve identity across unchanged reviews and stall/recovery transitions;
- cancellation has visible cost, target cooldown and fresh identity on later re-entry;
- Stage-21A completed-review watermarks provide one-shot material-change re-evaluation before ordinary goal cadence;
- `commitmentUntilTick` protects accepted goals from nonterminal evidence-loss churn while terminal outcomes still win;
- success/failure conditions and cost ceilings remain declarative and survive save/load;
- Stage-21B v5 persistence wraps the accepted Stage-21A checkpoint without rewriting its authority;
- future/corrupt actor-review watermarks fail closed during composition validation.

Exit criteria:

- goals are reconstructible from saved evidence and never inferred from a UI label;
- impossible goals are rejected or deferred with a reason;
- repeated unchanged reviews do not churn goal or target identity;
- different physical dependencies can produce different goals for otherwise similar factions.

Implementation and acceptance map: `docs/stage21b_strategic_intent.md`.

### 21C — Diplomacy, crisis, alliance and war lifecycle — COMPLETE

Objective: let factions negotiate or escalate through the existing political authority.

Delivered:

- directed relation memory derived from remembered actions, treaty performance, territorial conflict,
  trade dependence, threat and diplomatic commitments;
- proposal identities for access, trade, recognition, construction rights, non-aggression, defensive
  cooperation, alliance, embargo, ultimatum, ceasefire and peace;
- bounded counter-offers with explicit causal lineage and feasibility validation before replacement;
- persistent crisis state with participants, issue, demands, concessions, deadlines and escalation;
- explicit legal war identity, participants, war goals, start evidence and Stage-19 conflict links;
- alliance/treaty obligation evaluation with allowed refusal and real treaty/reputation consequence;
- bounded negotiation offers checked against real spendable treasury and territorial authority;
- accepted trade delegated to Stage-17 treaty/access/tariff law;
- response deadlines separated from accepted treaty lifetime, with stale offers closed on expiry;
- war/peace anti-oscillation and minimum re-escalation cooldowns;
- complete Stage-21C generated-world checkpoint composition over unchanged Stage-21B + Stage-19 state;
- fail-closed validation for unknown cross-layer identities, missing treaty/conflict links and future
  actor evidence;
- mid-lifecycle continuation after save/load at proposal, counter-offer, ultimatum and ceasefire;
- production-materializable generated-world roots `DEFAULT_WORLD_SEED` and `DEFAULT_WORLD_SEED + 1`,
  each replaying predeclared trade, deterrence, negotiated-resolution and causal-war histories;
- frozen Stage-20 representative seeds `1..16` retained only for the bounded tie-break proof, where
  seed input is verified incapable of creating war.

Exit criteria — accepted:

- war cannot begin without a persisted causal crisis/decision or an explicit actor-observed hostile attack;
- random input may resolve bounded peaceful tie-breaking but cannot be the reason for war;
- treaties and wars round-trip through persistence and accepted treaty clauses affect ordinary access/tariff law;
- both production-materializable generated-world roots replay all four predeclared persisted political
  histories and produce trade, deterrence, negotiated resolution and causal war as expected;
- the frozen Stage-20 representative seeds `1..16` remain a separate tie-break-only proof and are not
  reclassified as production-materializable worlds;
- Stage-21B goal consumption is pure/actor-bounded and physical warfare execution remains owned by
  Stage 19 and later Stage-21 military slices.

Implementation and acceptance map: `docs/stage21c_diplomacy_crisis_lifecycle.md`.

### 21D — Fleet readiness, command groups and strategic movement — COMPLETE

Objective: turn owned physical ships into finite forces that can receive lawful strategic orders.

Delivered:

- read-only force registry reconstructed from ordinary `FleetId` placements and fitted entities;
- readiness from damage, ammunition, propellant, crew, sensors, maintenance and supply access;
- persistent command-group identity without replacing member FleetIds;
- orders for patrol, guard, escort, stage, reinforce, intercept, shadow, raid, blockade, invade,
  withdraw, refuel, rearm, repair and return;
- neighbor-only route planning using known topology and legal access;
- mobilization/staging deadlines based on physical location and handling/service capability;
- risk, reserve and home-defense constraints;
- player and AI submission through the same validated fleet-order boundary;
- recoverable exact-hop dispatch: members already on the persisted edge or physically at the next
  node do not receive duplicate jumps, while lagging members may continue through ordinary jump authority;
- generated-world Stage-21D persistence wrapping Stage-21C without replacing Stage-20 transit state.

Exit criteria — accepted:

- a strategic order causes only ordinary movement/service operations;
- in-transit fleets retain exact identity, fit, damage, cargo and arrival authority across save/load;
- a fleet lacking fuel/ammunition/access cannot silently execute an infeasible order;
- double assignment, teleport, duplicate arrival and free repair/rearm are rejected.

Implementation and acceptance map: `docs/stage21d_fleet_readiness_command_movement.md`.

### 21E — Strategic operations and physical warfare consequences — COMPLETE

Objective: execute coercion and warfare through Stage-19/ordinary-world state.

Delivered:

- persistent operation identities for escort, interception, raid, blockade, defense and invasion;
- participant, staging, objective, rules-of-engagement, supply and withdrawal state;
- owning-faction actor-bounded contact acquisition before interception or battle materialization;
- exact Stage-19 tactical materialization for forces that physically meet, with synchronous commit-back
  to the same ordinary entity/`FleetId` identities;
- deterministic return of ship damage, ammunition, propellant, survivors and catastrophic losses;
- a non-vacuous generated-world casualty path where only production Stage 19 destroys a valid surviving
  ordinary target and the surviving operation fleet returns with less physical ammunition and/or reaction mass;
- blockade/interdiction consequences through actual Stage-20/18 traffic, handling and route availability;
- industrial consequences only from destroyed/denied physical assets; no generic production modifier;
- reinforcement only after ordinary physical arrival and withdrawal decisions that respect real readiness,
  propellant and normal Stage-21D movement authority;
- schema-v8 deterministic persistence with fail-closed active-participant ownership, future/corrupt
  payload validation and rejection of transient in-memory tactical encounters at the full checkpoint boundary.

Exit criteria — accepted:

- no operation applies a generic remote production/combat percentage debuff;
- every reported loss maps to a removed/damaged ordinary entity and conserved material outcome;
- an unsupplied superior force can fail, withdraw or lose readiness without a resource grant;
- operation acceptance proves a real Stage-19 loss and physical operation-store consumption;
- tactical and strategic continuation remains deterministic through the supported operation checkpoint boundary.

Implementation and acceptance map: `docs/stage21e_strategic_operations_physical_consequences.md`.

### 21F — Occupation, claims, stabilization and control transition — OPEN/NEXT

Objective: make territorial expansion gradual, contestable and legally persistent.

Deliverables:

- occupation evidence from sustained physical presence and defeated/withdrawn opposition;
- claim creation through the existing Stage-17 claim authority;
- stabilization progress driven by security, infrastructure, supply and political recognition;
- resistance/contest state without conjuring free hostile fleets;
- control transfer only after accepted legal/physical thresholds;
- station/fleet allegiance, market access, tariff and construction consequences through existing law;
- liberation, withdrawal and contested-control paths;
- global-map projection distinguishing claim, occupation, stabilization and recognized control.

Exit criteria:

- entering a system cannot immediately recolour it;
- occupation without supply/security can stall or collapse;
- save/load preserves exact transition progress and deadlines;
- control changes update future faction interests, access and economic routes causally.

### 21G — Peace, demobilization, recovery and replacement

Objective: close conflict loops without resetting the world.

Deliverables:

- ceasefire and peace outcomes tied to war goals, losses, exhaustion, leverage and offers;
- recognition, access, reparations and territorial terms using existing legal/treasury authority;
- demobilization and return/redeployment orders for surviving fleets;
- persistent loss and veteran/damage continuity where supported by ordinary entity state;
- repair, rearm and refuel through physical facilities and stocks;
- replacement demand submitted to existing industrial/shipyard planning;
- no automatic restoration to pre-war fleet composition;
- post-war cooldown, grievance and treaty memory feeding future interests.

Exit criteria:

- peace does not repair ships, refill stores or recreate destroyed fleets;
- reparations conserve treasury/material transfers;
- destroyed capability returns only after an ordinary production and commissioning chain;
- a war changes later diplomatic/economic decisions even after operations end.

### 21H — NPCs, missions, reputation and discovery grounded in the living world

Objective: make the RPG layer a participant in the autonomous world rather than a disconnected
quest generator.

Deliverables:

- persistent NPC identities, roles, affiliations, location, knowledge and availability;
- actor-bounded NPC knowledge and dialogue facts;
- reputation changes caused by observed player actions, contracts, betrayals and faction outcomes;
- mission opportunities derived from real shortages, threats, exploration, diplomacy and operations;
- contract identity, issuer, objective authority, deadline, reward escrow/cost and failure effects;
- discovery/intelligence missions that respect Stage-17.5/20 information boundaries;
- event-driven mission wakeups and bounded relevance instead of polling every possible NPC;
- mission completion that delegates to ordinary cargo, combat, construction, diplomacy or discovery
  state and cannot self-certify from UI input.

Exit criteria:

- a mission cannot promise cargo, money, access or ships the issuer does not lawfully control;
- destroying or moving the underlying target updates/fails the mission deterministically;
- NPCs never reveal facts absent from their knowledge state;
- ignoring a mission does not freeze the world; factions may solve, lose or change the opportunity.

### 21I — Command UI, persistence, corpus and long-run final gate

Objective: make the full living-world chain inspectable, resumable and robust.

Deliverables:

- faction UI for interests, relations, treaties, crises, wars, goals and decision evidence;
- military UI for command group, order, readiness, route, supply, operation and destination;
- global overlays for access, claims, occupation, control, wars, fronts and known intelligence;
- timeline/event log with actor-bounded public/private presentation;
- selectable NPC, mission and discovery inspection;
- backward migration for supported Stage-20.5/21.0 generated-world saves;
- deterministic new-world, mid-crisis, mid-transit, mid-operation and post-war save/load tests;
- representative seed corpus covering peaceful coexistence, alliance, coercion, limited war,
  territorial transition, recovery and renewed trade;
- bounded-performance evidence for increasing faction/system/fleet/NPC counts;
- long-run soak with physical production, losses, diplomacy and territory continuing without
  resource creation, ID duplication, deadline loss or decision oscillation.

Exit criteria:

- every visible strategic value traces to authoritative state;
- UI actions cannot mutate simulation except through explicit validated commands;
- corpus tests prove outcome diversity without seed-specific exceptions;
- final checkpoint restore continues byte/determinism and identity invariants;
- all Stage-21 hard invariants have non-vacuous acceptance evidence.

## 6. Mandatory acceptance ladder

1. Pure deterministic tests for interests, goals, deadlines and anti-oscillation.
2. Actor-knowledge tests proving hidden state cannot leak into decisions.
3. Diplomacy lifecycle tests from proposal through expiry, breach, crisis, war and peace.
4. Fleet-order tests for feasibility, routing, service, cancellation and persistence.
5. Operation tests with real Stage-19 losses and Stage-18 supply consumption.
6. Territorial tests covering claim, contest, occupation, stabilization and control.
7. Replacement tests proving shipyard/material/time/treasury conservation.
8. NPC/mission tests proving real issuer authority, knowledge and world-state completion.
9. Mid-chain save/load tests at every state-machine boundary.
10. Representative generated-seed corpus and bounded long-run performance soak.

Acceptance must be non-vacuous: a green test that never creates a crisis, moves a military FleetId,
consumes supply, records damage, changes a territorial state or resolves a mission is not evidence for
the corresponding slice.

## 7. Stage-22 boundary

Stage 21 may use the explicit provisional Stage-17.5/19 combat test pack to validate living-world
causality. Stage 22 owns final hull families, faction engineering doctrine, module/content balance,
fleet composition and manufacturable content review.

Stage 21 therefore decides **why, when and where** a faction acts and proves that the action is
physical. Stage 22 finalizes **what production-quality ships and technologies** each faction fields.

## 8. Definition of Stage-21 completion

Stage 21 is complete only when at least one accepted generated campaign can, without scripted
outcome grants:

```text
run a physical economy
→ produce divergent faction interests
→ negotiate, cooperate or enter a causal crisis
→ commit and physically move ordinary fleets
→ resolve supply, operation and losses
→ preserve or change territory through Stage-17 law
→ negotiate an outcome and recover through Stage-18 industry
→ generate grounded NPC/mission/reputation consequences
→ save/load at intermediate boundaries
→ continue deterministically through a bounded long-run soak
```

No single seed is required to exercise every political outcome naturally. Targeted deterministic
fixtures prove individual branches; the representative corpus proves that the combined system
supports multiple plausible histories without hidden exceptions.

## 9. Verified implementation status and first code sequence

| Slice | Status | First production seam |
|---|---|---|
| 21.0 | **COMPLETE** | generated runtime/UI/fleets/navigation/save/launcher |
| 21A | **COMPLETE** | persistent actor cadence, bounded observations and interest evidence |
| 21B | **COMPLETE** | persistent strategic goals, feasibility, commitment and explainability |
| 21C | **COMPLETE** | proposal/counter-offer/crisis/treaty/war legal lifecycle |
| 21D | **COMPLETE** | physical readiness, command groups, lawful orders and neighbor-only movement |
| 21E | **COMPLETE** | persistent operations, exact Stage-19 consequences, physical losses/store consumption and traffic interdiction |
| 21F | **OPEN — next** | occupation/stabilization/control transitions |
| 21G | **OPEN** | peace/demobilization/repair/replacement |
| 21H | **OPEN** | persistent NPCs, missions, reputation and discovery |
| 21I | **OPEN** | integrated UI/migration/corpus/performance final gate |

Stage 21A accepted the deliberately narrow actor foundation: stable faction-bound lifecycle state,
immutable actor-bounded observations, measurable interests, deterministic traces, ordered wakeups,
bounded top-K scheduling and exact checkpoint continuation.

Stage 21B accepts stable evidence-bound strategic goals, explicit scoring inputs and cost ceilings,
multidimensional feasibility/budget arbitration, persistent lifecycle and cancellation consequences,
minimum commitment, one-shot material-change re-evaluation, read-only explainability and exact v5
checkpoint continuation. It deliberately does not create diplomatic legal state, fleet orders,
physical operations or territory changes.

Stage 21C accepts the political/legal layer: accepted Stage-21B goals and actor-known diplomatic
memory become bounded proposals/counter-offers, crises, guarantees and causal legal wars. Executable
treaty/access/tariff/territory effects stay in Stage 17; actor-perspective conflict records stay in
Stage 19; Stage-21C persistence composes those authorities and proves exact mid-lifecycle
continuation. Random/tie-break input cannot create war.

Stage 21D accepts the finite force-command layer over real ordinary fleet state:

1. `FleetForceRegistry` reconstructs read-only force state from existing placements and fitted payloads;
2. readiness derives from damage, ammunition, propellant, crew, sensors, maintenance and supply access;
3. persistent command groups retain member `FleetId` identities rather than replacing them;
4. all fifteen strategic order families share the same PLAYER/AI validation boundary;
5. routing is deterministic, neighbor-only and constrained by existing legal access;
6. reserve, home-defense, risk, service and duplicate-assignment constraints fail closed;
7. movement delegates only to the existing jump FSM, including idempotent same-hop retry and recoverable staggered group progress;
8. Stage-21D persistence composes Stage-21C while the Stage-20.5 checkpoint remains exact local/transit authority.

Stage 21E accepts the physical strategic-operation layer over those real force identities:

1. six persistent operation families retain ordinary participant `FleetId` identities and explicit objective/ROE/supply/withdrawal metadata;
2. contact acquisition is bounded to the owning faction's current Stage-21A evidence through the Stage-17 identity resolver;
3. tactical materialization re-validates physical co-location and imports exact current entity/engineering state into production Stage 19;
4. surviving damage, ammunition, propellant and kinematics commit back to the same ordinary identities while catastrophic loss uses ordinary destruction authority;
5. dedicated generated-world acceptance proves a real Stage-19-created `FleetId` loss plus physical ammunition/reaction-mass consumption without replacement or replenishment grants;
6. blockade/interdiction can deny only real Stage-20/18 handling or an exact route edge while physically anchored;
7. reinforcement requires ordinary arrival and withdrawal remains an ordinary validated movement/service decision;
8. Stage-21E persistence composes Stage-21D and rejects invalid active participants, future/corrupt state and transient active tactical runtime at checkpoint boundaries.

Stage 21F is now the first remaining implementation slice. It must consume accepted Stage-21E
physical operation outcomes and existing Stage-17 territorial law to model claim, occupation,
stabilization, recognition and gradual control transition. It must not infer control from an abstract
battle score or immediately recolour a system merely because a fleet arrived.

## 10. Suggested state ownership

Names below describe responsibilities; exact Java names may change during implementation.

| State/Service | Owns | Must not own |
|---|---|---|
| living actor state | cadence, commitment horizon, wakeups, decision trace refs | treasury, fleets, relations |
| observation snapshot | bounded known facts and evidence timestamps | omniscient live world references |
| interest/goal state | priority, target, budget intent, success/failure/expiry | direct asset mutation |
| crisis/war state | participants, issue, demands, deadlines, legal lifecycle | tactical damage or territory flag |
| command group/order | membership references, intent, route and lifecycle | replacement FleetIds or teleport |
| operation state | objective, participants, supply/withdrawal and outcome refs | abstract untraceable combat loss |
| mission state | issuer, target refs, predicates, deadline, escrow and outcome | UI-certified completion |
| NPC state | identity, affiliation, role, knowledge, location, availability | global truth or free rewards |

Existing treasury, diplomacy, territory, fleet placement, jump FSM, Stage-18 industry and Stage-19
combat remain their respective authorities.

## 11. Minimum Stage-21 authored content

Stage 21 needs enough authored content to prove mechanics, but not the final Stage-22 breadth.

### Gold-slice actors

- six persistent NPC role archetypes: official, military, trade/logistics, industry/yard,
  exploration/intelligence and independent/frontier;
- at least one recurring Imperial contact for each role;
- deterministic names, affiliation, authority, location, availability and knowledge boundaries;
- character art may begin with accepted production candidates, but identity/state cannot depend on
  whether art is loaded.

### First eight mission contracts

1. emergency physical supply delivery;
2. ordinary market procurement;
3. convoy escort;
4. stranded-fleet rescue/refuel;
5. system/object reconnaissance;
6. derelict investigation and finite recovery;
7. interception/defense tied to a real threat;
8. construction or repair input delivery.

Every contract must prove issuer authority, saved target identity, objective observation, deadline,
real reward source and deterministic failure/update when the underlying world changes.

### First narrative chain

A compact 3–5-step Imperial chain should combine a real supply dependency, institutional conflict,
access negotiation and possible escort/crisis outcome. Characters provide interpretation and
choice; the live world decides whether cargo, route, treaty, fleet and target still exist.

Full faction/NPC/mission/location breadth remains Stage 22 and follows
`docs/content_production_plan_stage21_23.md`.

## 12. Pull-request decomposition

Each item should remain separately reviewable and leave `main` green:

1. **COMPLETE:** 21A actor state/cadence/persistence;
2. **COMPLETE:** 21A observation/evidence/interest derivation;
3. **COMPLETE:** 21A scheduling/diagnostics/generated-runtime composition;
4. **COMPLETE:** 21B goal state/feasibility/arbitration;
5. **COMPLETE:** 21C proposals/treaties/counter-offers and crisis state;
6. **COMPLETE:** 21C war/peace legal lifecycle and persistence acceptance;
7. **COMPLETE:** 21D readiness/command groups;
8. **COMPLETE:** 21D orders/routing/service validation/persistence;
9. **COMPLETE:** 21E operation lifecycle and contact/materialization seam;
10. **COMPLETE:** 21E physical consequence return/persistence;
11. **NEXT:** 21F occupation/stabilization/control;
12. 21G peace/demobilization/replacement;
13. 21H NPC identity/knowledge/availability;
14. 21H mission/escrow/objective/reputation;
15. 21H authored gold-slice content;
16. 21I command UI/overlays/timeline;
17. 21I migration/corpus/performance/soak and completion record.

A PR may combine adjacent items only when the resulting authority boundary and acceptance evidence
remain independently reviewable.