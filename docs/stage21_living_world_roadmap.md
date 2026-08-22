# Stage 21 — Living World / Autonomous Factions roadmap

> Status: **ACTIVE**. Stage 20 and Stage 20.5 are complete. Stage 21.0 and Stage 21A are complete;
> Stage 21B is the next delivery slice.

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
- contested-system behavior when occupation exists without recognized control;
- rollback/withdrawal behavior when occupation cannot be sustained.

Exit criteria:

- no single fleet arrival flips ownership;
- captured systems continue to contain the same physical assets unless explicitly destroyed/rebuilt;
- control transition survives save/load at every intermediate phase;
- economic access and UI ownership labels derive from the same authoritative control state.

### 21G — Peace, demobilization, repair, rearmament and replacement

Objective: close the causal loop after war instead of ending at a map-color change.

Deliverables:

- peace terms applied through treaties, claims, access, reparations and recognized control authority;
- demobilization/return orders for surviving forces;
- damaged fleets seek ordinary repair and rearm through Stage-18 service capability;
- replacement demand is generated from real losses and competes for treasury, shipyard and material
  capacity;
- no automatic restoration of pre-war fleet strength;
- post-war shortages, debt and exposed routes feed back into actor observations and goals;
- cooldowns before renewed escalation unless a new hostile event materially changes the state.

Exit criteria:

- fleet losses remain visible in physical force counts after peace;
- replacement is impossible without physical/economic capacity;
- peace can leave a faction weaker, indebted, territorially changed or strategically dependent;
- the next strategic cycle reacts to those consequences rather than resetting them.

### 21H — NPCs, missions, discovery and reputation

Objective: expose the living world to the player through persistent people and actionable contracts.

Deliverables:

- persistent named NPC identity for faction command, diplomacy, trade, industry, security and local
  contacts;
- NPC knowledge bounded by faction/local information and personal role;
- mission generation from real needs: shortage haul, escort, survey, recovery, repair supply,
  diplomatic courier, patrol support, bounty/interdiction, evacuation and wartime logistics;
- accepted missions reserve or reference actual resources/objectives rather than creating duplicates;
- success/failure derived from authoritative world events and physical delivery/destruction state;
- persistent reputation/reliability history that influences access and future offers;
- discovery sharing through Stage-20 knowledge provenance rather than global unlock.

Exit criteria:

- deleting mission UI cannot change the underlying world need;
- mission cargo/reward/objective cannot duplicate a resource already owned elsewhere;
- NPCs do not reveal hidden systems, fleets or threats they have not learned;
- save/load preserves NPC, mission, deadline, partial progress and reputation identity.

### 21I — Persistence, command UI, observability, corpus and final gate

Objective: prove the complete causal living-world loop is playable, inspectable and scalable.

Deliverables:

- versioned Stage-21 persistence for actor state, goals, crises, wars, command groups, operations,
  occupation, NPCs, missions, reputation and all wakeup/deadline watermarks;
- migration defaults for Stage-20.5 saves with deterministic first-review scheduling;
- UI surfaces for faction intent, known threats, dependencies, diplomacy, wars, fleet missions,
  claims/occupation, losses, construction, shortages and mission provenance;
- explanation trace showing what evidence caused the current strategic decision without exposing
  hidden information;
- representative-seed corpus covering peaceful trade, deterrence, alliance, coercion, war,
  occupation, peace and recovery;
- save/load branch tests immediately before/after deadlines, negotiations, jumps, battles, occupation
  thresholds and project completion;
- performance counters for reviewed actors, deferred actors, wakeup queue, active operations and
  per-layer milliseconds;
- long deterministic soak with conservation, identity, orphan-reference and hidden-grant audits.

Final exit criteria:

- same seed plus same player commands produce the same persistent living-world outcomes and traces;
- player and AI actions share validators and physical/economic consequences;
- no lost ship, cargo, ammunition, propellant, treasury transfer or territory change reappears after
  save/load;
- no faction decision consumes hidden knowledge;
- large-world strategic work remains bounded by relevance/cadence instead of actor-count × fixed-tick;
- the command UI can explain current wars/goals/operations from authoritative state;
- Stage 21 ends with no known shortcut for teleportation, free replacement, instant control flip,
  hidden economy, duplicate mission resource or AI-only privilege;
- the repository's ordinary Java 17 verification, deterministic long-soak and acceptance corpus pass.

## 6. Stage 21 definition of done

Stage 21 is complete only when at least one deterministic generated-world corpus demonstrates the
full loop:

```text
scarcity/dependency
→ observed interest
→ goal
→ diplomacy/crisis
→ fleet operation
→ physical loss or territorial pressure
→ political outcome
→ demobilization/replacement
→ changed economy/knowledge
→ next strategic review
```

The loop must survive save/load at intermediate boundaries and remain explainable from actor-bounded
evidence. Stage 21 is not complete merely because factions own ships, can move them or randomly enter
wars.
