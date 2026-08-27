# Stage 21 — Living World / Autonomous Factions roadmap

> Status: **COMPLETE**. Stage 20 and Stage 20.5 are complete. Stage 21.0 and Stage 21A–21I are complete.
> **Stage 22 — Content / Technology / Balance Alpha is OPEN/NEXT and is not implemented by this stage.**

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

The production core-pair reference is now **Империя + Индустриальный Союз**. Stage 21 proves that
one shared living-world decision chain can express meaningfully different lawful priorities for this
pair without faction-only resource, combat, movement or information authority. Final engineering,
hull, visual and broad narrative differentiation remains Stage 22.

Canonical faction scope: `docs/factions/faction_roster_and_development_horizon.md`.

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
18. Faction doctrine/institutional preference may rank goals and lawful policy choices, but cannot act
    as a hidden production/combat/sensor multiplier or bypass ordinary feasibility/cost authority.

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

Exit criteria — accepted:

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

### 21F — Occupation, claims, stabilization and control transition — COMPLETE

Objective: make territorial expansion gradual, contestable and legally persistent.

Delivered:

- occupation evidence from sustained physical presence and defeated/withdrawn opposition;
- claim creation through the existing Stage-17 claim authority;
- stabilization progress driven by security, infrastructure, supply and political recognition;
- resistance/contest state without conjuring free hostile fleets;
- control transfer only after accepted legal/physical thresholds;
- station/fleet allegiance, market access, tariff and construction consequences through existing law;
- liberation, withdrawal and contested-control paths;
- global-map projection distinguishing claim, occupation, stabilization and recognized control.

Exit criteria — accepted:

- entering a system cannot immediately recolour it;
- occupation without supply/security can stall or collapse;
- save/load preserves exact transition progress and deadlines;
- control changes update future faction interests, access and economic routes causally.

Implementation and acceptance map: `docs/stage21f_territorial_transition.md`.
Accepted through PR #331 from exact green head `c198ddb4e3b45158e350220187327aa7ed98c8f5`;
CI run #5126 (`32883580620`), Java 17 verification job `97918646553`, completed successfully.
Implementation merge commit on `main`: `1294b908ec47c3b4ad9065db17dd5a8a55b4c763`.

### 21G — Peace, demobilization, recovery and replacement — COMPLETE

Objective: close conflict loops without resetting the world.

Delivered:

- ceasefire and peace outcomes tied to persisted war goals, actor-known objective evidence, physical losses/exhaustion/leverage and visible settlement offers;
- recognition, access and territorial terms left under existing Stage-17/21C legal authority, with reparations executed through ordinary conserved treasury transfers;
- demobilization and return orders for surviving command groups through the existing Stage-21D order boundary;
- exact Stage-21E physical loss provenance with destroyed `FleetId` continuity and no resurrection/re-attribution;
- repair, rearm and refuel through physical Stage-18/19 facilities, stocks and service capability;
- replacement demand backed by an exact physical loss and fulfilled only through ordinary shipyard planning/settlement plus fresh `FleetId` commissioning;
- no automatic restoration to pre-war fleet composition or stores;
- Stage-21C post-war cooldown retained as authority, with bounded exact-once treaty-performance and loss/grievance memory feeding later diplomacy;
- deterministic standalone recovery persistence and schema-v10 generated-world checkpoint over the accepted Stage-21F runtime.

Exit criteria — accepted:

- peace does not repair ships, refill stores or recreate destroyed fleets;
- reparations conserve treasury/material transfers;
- destroyed capability returns only after an ordinary production and commissioning chain;
- a war changes later diplomatic/economic decisions even after operations end.

Implementation and acceptance map: `docs/stage21g_peace_recovery_replacement.md`.
Accepted through PR #333 from exact green head `206a197a4fbe0db2d8c72f99b26f1ca7f6abb459`;
CI run #5225 (`32956435219`), Java 17 verification job `98139009665`, completed successfully.
Implementation merge commit on `main`: `98f3ec58be0c57a95868a6c824076181c1bf1b2d`.

### 21H — NPCs, missions, reputation and discovery grounded in the living world — COMPLETE

Objective: make the RPG layer a participant in the autonomous world rather than a disconnected
quest generator.

Delivered:

- persistent NPC identities, roles, affiliations, location, knowledge and availability;
- actor-bounded NPC knowledge and dialogue facts;
- reputation changes caused by observed player actions, contracts, betrayals and faction outcomes;
- mission opportunities derived from real shortages, threats, exploration, diplomacy and operations;
- contract identity, issuer, objective authority, deadline, reward escrow/cost and failure effects;
- discovery/intelligence missions that respect Stage-17.5/20 information boundaries;
- event-driven mission wakeups and bounded relevance instead of polling every possible NPC;
- mission completion that delegates to ordinary cargo, combat, construction, diplomacy or discovery
  state and cannot self-certify from UI input;
- independent player-contractor proof from persistent `PlayerState`, owned `FleetId`/construction/operation
  participation and owner-local Stage-20 discovery before player-facing escrow payout;
- deterministic standalone sidecar persistence and schema-v11 generated-world checkpoint composed over
  the complete Stage-21G runtime with exact escrow-ledger provenance validation.

Exit criteria — accepted:

- a mission cannot promise cargo, money, access or ships the issuer does not lawfully control;
- destroying or moving the underlying target updates/fails the mission deterministically;
- NPCs never reveal facts absent from their knowledge state;
- ignoring a mission does not freeze the world; factions may solve, lose or change the opportunity;
- a satisfied world predicate cannot pay a player contract unless existing persistent authorities also
  prove bounded player participation.

Implementation and acceptance map: `docs/stage21h_npc_missions_reputation_discovery.md`.
Accepted through PR #335 from exact green head `d57a71b351a22af52e005499a214e41db0701f85`;
CI run #5307 (`32978603732`), Java 17 verification job `98209354294`, completed successfully.
Implementation merge commit on `main`: `ecc57ad27a653d823da4294d121414aeab7c72e9`.

### 21I — Command UI, persistence, corpus and long-run final gate — COMPLETE

Objective: make the full living-world chain inspectable, resumable and robust.

Delivered:

- read-only integrated faction projection for interests, relations, treaties, crises, wars, goals and decision evidence;
- military projection for command groups, orders, readiness, routes, supply/operation context and destinations;
- global territorial/access/war/intelligence projection and actor-bounded timeline/event information;
- persistent NPC, mission, reputation and discovery inspection on the same final read model;
- `Stage21GeneratedMilitaryEngineeringCatalog` as the single Stage-21 boundary for provisional generated military engineering content, including bootstrap, FTL and legacy generated UI reads;
- schema-v12 final generated-world checkpoint through `Stage21IGeneratedWorldRuntimePersistentState`, codec and migration;
- supported migration that preserves accepted earlier generated-runtime authority rather than regenerating the world or inventing later decisions;
- deterministic final restore/re-encode and fail-closed validation for incompatible/corrupt/future state;
- representative generated-seed boundedness evidence;
- representative positive cooperation corpus with real Stage-21C → Stage-17 `TRADE` and `ALLIANCE` treaty authority and byte-stable final checkpoints;
- core-pair Империя/Индустриальный Союз acceptance proving explainable lawful divergence and convergence without faction-name resource/combat/sensor/movement cheats;
- increasing faction/system/fleet/NPC workload-envelope acceptance retaining bounded work authority;
- non-vacuous long-run soak crossing ordinary FTL, production Stage-19 loss/store consumption, occupation/control, peace/demobilization, continued physical freight and grounded mission/reputation consequences.

Exit criteria — accepted:

- every visible strategic value is projected from authoritative state and the projector is read-only;
- UI projection cannot mutate treasury, diplomacy, territory, fleet, warfare or mission state;
- representative tests support peaceful trade, alliance, coercion/war, territorial transition, recovery and renewed trade without seed-specific simulation exceptions;
- core-pair traces prove both institutional/doctrine divergence and lawful convergence from actor-bounded evidence;
- no core-pair outcome depends on a faction-name production/combat/sensor/movement modifier;
- the schema-v12 checkpoint preserves deterministic restore/re-encode and stable identity invariants;
- supported earlier Stage-21 checkpoint authorities are composed/migrated rather than rewritten;
- bounded-performance acceptance preserves scheduled/event-driven work instead of world-wide full-rate AI;
- the final soak is non-vacuous: a real ordinary military `FleetId` moves, production Stage 19 creates physical loss/store consumption, territorial law advances, peace/demobilization occurs, physical economy continues and Stage-21H consequences resolve;
- all Stage-21 hard invariants have non-vacuous acceptance evidence across the final Stage-21 suite.

Canonical closeout: `docs/stage21i_living_world_final_gate_completion_record.md`.

## 6. Mandatory acceptance ladder — COMPLETE

1. Pure deterministic tests for interests, goals, deadlines and anti-oscillation — accepted.
2. Actor-knowledge tests proving hidden state cannot leak into decisions — accepted.
3. Diplomacy lifecycle tests from proposal through expiry, breach, crisis, war and peace — accepted.
4. Fleet-order tests for feasibility, routing, service, cancellation and persistence — accepted.
5. Operation tests with real Stage-19 losses and Stage-18 supply consumption — accepted.
6. Territorial tests covering claim, contest, occupation, stabilization and control — accepted.
7. Replacement tests proving shipyard/material/time/treasury conservation — accepted.
8. NPC/mission tests proving real issuer authority, knowledge and world-state completion — accepted.
9. Mid-chain save/load tests at state-machine boundaries — accepted through the owning Stage-21C–H persistence suites and final schema-v12 composition.
10. Representative generated-seed corpus and bounded long-run performance soak — accepted in Stage 21I.
11. Core-pair doctrine/institution acceptance proving Империя/Индустриальный Союз divergence and convergence through the same observation/goal/command authorities without hidden stat cheats — accepted in Stage 21I.

Acceptance is non-vacuous: the final suite includes real crisis/diplomatic state, military movement,
physical Stage-19 loss and store consumption, territorial transition, continued freight and mission/reputation resolution.

## 7. Stage-22 boundary

Stage 21 uses the explicit provisional Stage-17.5/19 combat test pack behind the final
`Stage21GeneratedMilitaryEngineeringCatalog` boundary to validate living-world causality. Stage 22 owns
final hull families, faction engineering doctrine, module/content balance, fleet composition and manufacturable content review.

Stage 21 therefore decides **why, when and where** a faction acts and proves that the action is
physical. Stage 22 finalizes **what production-quality ships and technologies** the core factions field.

The Stage-22 production-complete major-faction scope is intentionally limited to **Империя and
Индустриальный Союз**. Директорат, Лига Свободных Систем, Пограничная Конфедерация, Консорциум and
Кочевой Флот are canonical post-core horizon factions and do not block Stage-21 completion.

## 8. Definition of Stage-21 completion — SATISFIED

Stage 21 is complete because the accepted implementation and final corpus demonstrate, without scripted outcome grants:

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

The final representative corpus additionally proves that the core pair can interpret the same lawful
world evidence differently where institutions/doctrine justify it, and identically where the physical
optimum is shared. Faction identity is therefore a preference/institutional layer over common
authorities, not a script forcing opposite outcomes.

No single seed is required to exercise every political outcome naturally. Targeted deterministic
fixtures prove individual branches; the representative corpus proves that the combined system supports
multiple plausible histories without hidden exceptions.

## 9. Verified implementation status

| Slice | Status | Accepted production seam |
|---|---|---|
| 21.0 | **COMPLETE** | generated runtime/UI/fleets/navigation/save/launcher |
| 21A | **COMPLETE** | persistent actor cadence, bounded observations and interest evidence |
| 21B | **COMPLETE** | persistent strategic goals, feasibility, commitment and explainability |
| 21C | **COMPLETE** | proposal/counter-offer/crisis/treaty/war legal lifecycle |
| 21D | **COMPLETE** | physical readiness, command groups, lawful orders and neighbor-only movement |
| 21E | **COMPLETE** | persistent operations, exact Stage-19 consequences, physical losses/store consumption and traffic interdiction |
| 21F | **COMPLETE** | occupation/stabilization/control transitions |
| 21G | **COMPLETE** | peace/demobilization/finite recovery/loss-backed replacement/post-war memory |
| 21H | **COMPLETE** | persistent NPCs, actor-bounded knowledge, funded missions, reputation and discovery |
| 21I | **COMPLETE** | integrated read-only UI, schema-v12 migration, cooperation/conflict corpus, core-pair proof, workload envelope and final physical soak |

Stage 21I accepts the final integration/hardening layer without inventing a new authority:

1. `Stage21ILivingWorldUiProjector` / `Stage21IFinalLivingWorldUiProjector` expose accepted A–H state read-only;
2. `Stage21GeneratedMilitaryEngineeringCatalog` centralizes provisional Stage-21 military content consumption without promoting it to Stage 22 canon;
3. schema-v12 persistence composes supported generated-world state and deterministic migration rather than regenerating campaigns;
4. the representative cooperation corpus proves real lawful `TRADE` and `ALLIANCE` outcomes through Stage-21C/17 authority;
5. the bounded seed corpus proves stable generated-world behavior without seed-specific physical cheats;
6. the core-pair test proves both explainable doctrine divergence and physical-optimum convergence;
7. the workload test retains bounded work as actor/system/fleet/NPC populations grow;
8. the final soak crosses real fitted FTL, Stage-19 loss/store consumption, territorial transition, peace/demobilization, physical freight and Stage-21H consequences before deterministic schema-v12 restore.

Full Stage-21I evidence map: `docs/stage21i_living_world_final_gate_completion_record.md`.

## 10. Suggested state ownership — retained after completion

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
| Stage-21I final projection | immutable inspection/read model | any simulation mutation |
| Stage-21I persistence envelope | composition/migration/version validation | replacement upstream economy/politics/combat authority |

Existing treasury, diplomacy, territory, fleet placement, jump FSM, Stage-18 industry and Stage-19
combat remain their respective authorities.

## 11. Minimum Stage-21 authored content — accepted scope

Stage 21 contains enough authored content to prove mechanics without pulling final Stage-22 breadth forward.

### Gold-slice actors

- six persistent NPC role archetypes: official, military, trade/logistics, industry/yard,
  exploration/intelligence and independent/frontier;
- recurring Imperial contacts for the accepted role slice;
- deterministic identity/affiliation/authority/location/availability/knowledge boundaries;
- identity/state independent of whether character art is loaded.

The completed Stage-21H authored gold slice remains intentionally Imperial. Stage 21I does not reopen
21H merely to duplicate the full NPC package for the contrast faction.

### Core-pair Stage-21I fixtures

For Индустриальный Союз, Stage 21I implements only enough deterministic systemic identity to prove the
shared decision machinery:

- stable faction/profile reference suitable for persistence/migration audit;
- bounded doctrine/institution preferences traceable in decision diagnostics;
- strategic shortage/route/industrial evidence fixtures using ordinary state;
- divergent and convergent outcomes against Imperial reference behavior;
- no final hull roster, visual bible runtime package or NPC quota before Stage 22.2.

### Mission/content boundary

Stage-21H owns the accepted first mission/content slice. Stage 21I only projects and exercises it in the
final soak; it does not create a second final-content package.

Full core-pair faction/NPC/mission/location breadth remains Stage 22 and follows
`docs/content_production_plan_stage21_23.md`.

## 12. Pull-request decomposition — COMPLETE

Each item was kept behind the same authority boundaries and accepted without requiring future-stage content breadth:

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
11. **COMPLETE:** 21F occupation/stabilization/control;
12. **COMPLETE:** 21G peace/demobilization/replacement;
13. **COMPLETE:** 21H NPC identity/knowledge/availability;
14. **COMPLETE:** 21H mission/escrow/objective/reputation;
15. **COMPLETE:** 21H authored gold-slice content;
16. **COMPLETE:** 21I command UI/overlays/timeline/final read projection;
17. **COMPLETE:** 21I migration/core-pair corpus/performance/soak and completion record.

Stage 21 is closed. **Do not start Stage 22 from this closeout PR.**