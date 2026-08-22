# Stage 21 — Living World / Autonomous Factions roadmap

> Status: **ACTIVE**. Stage 20 and Stage 20.5 are complete. The generated-world command UI,
> finite faction patrol bootstrap and Windows launcher form the Stage-21 entry foundation.

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

### 21A — Living actor kernel and interest evidence

Objective: give every autonomous faction a bounded persistent decision lifecycle.

Deliverables:

- `FactionLivingActorState`-equivalent state bound to existing stable faction identity;
- persisted next-review deadlines, commitment horizons and event wakeup reasons;
- actor-bounded economic, territorial, security and diplomatic observation snapshot;
- interest evidence for supply dependency, market access, route exposure, resource deficit,
  border security, territorial opportunity and treaty obligation;
- deterministic priority/conflict resolution without direct stat bonuses;
- decision trace suitable for tests and UI explanation;
- relevance scheduler proving bounded work as faction/system counts grow.

Exit criteria:

- same seed/checkpoint/events produce byte-identical actor decisions;
- save/load immediately before a deadline produces one, not zero or two, reviews;
- hidden information cannot change a decision until observed through an allowed channel;
- no review directly creates money, cargo, ships, territory or relations.

### 21B — Strategic intent, goals and commitment

Objective: convert measured interests into durable, explainable goals.

Deliverables:

- goal families for secure-route, obtain-access, stockpile, defend, escort, explore, claim,
  deter, coerce, raid, blockade, invade and recover;
- explicit target, evidence, urgency, cost ceiling, success condition, failure condition and expiry;
- compatibility with existing Stage-17 faction doctrine preferences without making doctrine a buff;
- force/economic feasibility assessment before commitment;
- competing-goal budget arbitration across treasury, logistics, construction and fleet readiness;
- hysteresis, minimum commitment duration and material-change wakeups;
- cancellation consequences rather than free instantaneous retasking.

Exit criteria:

- goals are reconstructible from saved evidence and never inferred from a UI label;
- impossible goals are rejected or deferred with a reason;
- repeated unchanged reviews do not churn goal or target identity;
- different physical dependencies can produce different goals for otherwise similar factions.

### 21C — Diplomacy, crisis, alliance and war lifecycle

Objective: let factions negotiate or escalate through the existing political authority.

Deliverables:

- relation changes derived from remembered actions, treaty performance, territorial conflict,
  trade dependence, threat and diplomatic commitments;
- proposals for access, trade, recognition, construction rights, non-aggression, defensive
  cooperation, alliance, embargo, ultimatum, ceasefire and peace;
- persistent crisis state with participants, issue, demands, concessions, deadlines and escalation;
- explicit war identity, participants, legal state, war goals and start evidence;
- alliance/treaty obligation evaluation with an allowed refusal and reputational consequence;
- bounded negotiation offers using real treasury, access and territorial concessions;
- war/peace anti-oscillation and minimum re-escalation cooldowns.

Exit criteria:

- war cannot begin without a persisted causal crisis/decision or an observed hostile attack;
- random input may resolve bounded tie-breaking but cannot be the reason for war;
- treaties and wars round-trip through persistence and affect ordinary access/tariff law;
- representative seeds exhibit trade, deterrence, negotiated resolution and war rather than one
  universal outcome.

### 21D — Fleet readiness, command groups and strategic movement

Objective: turn owned physical ships into finite forces that can receive lawful strategic orders.

Deliverables:

- read-only force registry reconstructed from ordinary `FleetId` placements and fitted entities;
- readiness from damage, ammunition, propellant, crew, sensors, maintenance and supply access;
- persistent command-group identity without replacing member FleetIds;
- orders for patrol, guard, escort, stage, reinforce, intercept, shadow, raid, blockade, invade,
  withdraw, refuel, rearm, repair and return;
- neighbor-only route planning using known topology and legal access;
- mobilization/staging deadlines based on physical location and handling/service capability;
- risk, reserve and home-defense constraints;
- player and AI submission through the same validated fleet-order boundary.

Exit criteria:

- a strategic order causes only ordinary movement/service operations;
- in-transit fleets retain exact identity, fit, damage, cargo and arrival authority across save/load;
- a fleet lacking fuel/ammunition/access cannot silently execute an infeasible order;
- double assignment, teleport, duplicate arrival and free repair/rearm are rejected.

### 21E — Strategic operations and physical warfare consequences

Objective: execute coercion and warfare through Stage-19/ordinary-world state.

Deliverables:

- operation identities for escort, interception, raid, blockade, defense and invasion;
- participant, staging, objective, rules-of-engagement, supply and withdrawal state;
- actor-bounded contact acquisition before interception or battle materialization;
- appropriate Stage-19 tactical materialization for forces that physically meet;
- deterministic return of ship damage, ammunition, propellant, losses and survivors;
- blockade/interdiction consequences through actual traffic, handling and route availability;
- industrial/territorial effects only from destroyed, denied or occupied physical assets;
- reinforcement and retreat that respect route time and information latency.

Exit criteria:

- no operation applies a generic remote production/combat percentage debuff;
- every reported loss maps to a removed/damaged ordinary entity and conserved material outcome;
- an unsupplied superior force can fail, withdraw or lose readiness;
- tactical and strategic continuation remains deterministic through mid-operation save/load.

### 21F — Occupation, claims, stabilization and control transition

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
