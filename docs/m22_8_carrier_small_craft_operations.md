# M22.8 — Carrier / Small-Craft Operations

Status: **REQUIRED / PLANNED — Stage 22 remains open until this gate is accepted**  
Added: 2026-09-16  
Depends on: accepted M22.7 integrated campaign handoff plus Stage 17.5 combat/fitting, Stage 18 physical industry/logistics, Stage 19 tactical warfare and Stage 21 strategic operations.

## 1. Purpose

M22.8 closes the carrier and embarked-small-craft gameplay gap before Star Empires enters Stage 23 Release Candidate hardening.

A carrier is not allowed to be a large hull with a virtual `N fighters` statistic. Fighters, interceptors, strike craft and reusable drones that matter to combat or operations must exist as persistent physical assets with stable identity, real mass/capability, finite consumables, damage/loss state and lawful production/replacement.

Canonical lifecycle:

```text
manufacture craft
→ physical delivery / assignment
→ carrier or station hangar inventory
→ service / fuel / arm / repair
→ launch queue
→ independent physical mission
→ ammunition / propellant / heat / damage consequences
→ recovery queue or permanent loss
→ turnaround / repair / replacement
→ persistent strategic readiness
```

No step may create free craft, ammunition, reaction mass, repair material or replacement capacity.

## 2. Authority and architecture rules

M22.8 extends existing authorities; it does not create a parallel carrier combat engine.

- Stage 17.5 remains authoritative for fitted physical capability, propulsion, power, thermal state, signatures, sensors/tracks, datalink/EW, weapons/ammunition, layered defence, damage and maintenance/refit semantics.
- Stage 18 remains authoritative for resources, manufactured components, ammunition, repair/replacement inputs, facilities, shipyard capability and physical logistics.
- Stage 19 remains authoritative for local tactical movement, target/track constraints, weapons, missiles, PD/interceptors, EW/decoys, damage and actor-bounded tactical AI.
- Stage 21 remains authoritative for faction readiness, command groups, strategic operations, loss provenance, recovery and replacement funding.
- M22.7 `GeneratedCampaignCoordinator` remains the generated-campaign composition root; M22.8 integrates through it rather than adding a second campaign timeline.

Hard prohibitions:

- no `carrierClass -> combat bonus` or faction-name carrier multiplier;
- no abstract wing HP pool replacing individual craft when craft are materialized;
- no virtual launch, teleport recovery or instantaneous rearm/refuel;
- no hidden off-screen replenishment;
- no player-only launch/recovery or targeting rules;
- no viewer/UI authority over simulation truth;
- no save/load reset of craft damage, consumables, queue position, assignment or mission state;
- no separate simplified combat physics solely because many small craft are present.

## 3. Required delivery slices

### M22.8A — Small-craft identity and persistence foundation

- stable persistent craft identity and owner/faction assignment;
- craft type/fit resolves through production content and ordinary fitting budgets;
- persistent condition, ammunition, reaction mass, damage and maintenance state;
- lawful materialization/dematerialization without identity or resource reset;
- supported save/load and deterministic allocator continuity.

### M22.8B — Hangars, bays and physical capacity

- authored hangar/bay capacity expressed by physical constraints rather than an arbitrary ship-class count;
- craft size/mass/envelope compatibility;
- finite parked/servicing/ready/launch/recovery occupancy;
- damaged or incompatible bay state can lawfully reduce throughput/capacity;
- station-based small-craft operations use the same capacity model where applicable.

### M22.8C — Launch, recovery and turnaround

- deterministic launch/recovery queues;
- launch/recovery cycle time and safe sequencing;
- finite refuel/reaction-mass service;
- finite ammunition loading;
- finite repair/service work and required inputs;
- aborted/failed recovery has physical consequences rather than silent teleport.

### M22.8D — Mission and command model

Minimum reusable mission vocabulary:

- CAP / defensive patrol;
- QRA / emergency intercept;
- interception;
- escort;
- anti-ship strike;
- reconnaissance / sensor extension;
- EW/support where a fit lawfully provides it;
- return / recover / divert.

PLAYER and AI issue mission intent through the same validation path. Orders must respect knowledge, range, endurance, available ready craft, bay state and command/datalink constraints.

### M22.8E — Tactical integration

- small craft participate in the accepted Stage-19 movement/sensor/track/fire-control/damage authorities;
- PD, guided weapons, decoys, EW and datalink interactions remain physical and track-bounded;
- carrier hulls do not receive hidden combat immunity;
- craft loss is permanent until ordinary replacement occurs;
- damaged surviving craft return with the damage they actually received.

### M22.8F — Carrier-group AI and doctrine

- carrier standoff behaviour derived from threat/range/endurance evidence;
- escorts/screens respond through ordinary command-group and tactical authorities;
- AI can maintain CAP, launch intercepts, commit strikes and recover depleted/damaged craft;
- multi-axis and sequential strikes are possible through ordinary mission scheduling, not scripted damage events;
- withdrawal/survival logic accounts for carrier, escort and wing state without omniscient knowledge.

### M22.8G — Physical logistics and replacement

- craft and relevant ammunition/consumables are manufactured through Stage-18 capability and inputs;
- replacement craft receive fresh stable identities and must be physically delivered/assigned;
- wing readiness can degrade because of supply interruption, losses, damaged bays or maintenance backlog;
- no destroyed craft reappears after tactical commit-back, strategic tick or save/load;
- carrier operations create observable demand for ammunition, propellant, spares/components and repair capacity where the existing ontology supports them.

### M22.8H — Strategic operations integration

- Stage-21 readiness/projection includes embarked-wing readiness without replacing individual authoritative state;
- strategic escort/interception/raid/defence operations can lawfully employ carrier groups;
- tactical materialization and commit-back preserve craft identities, losses, consumables and carrier/bay state;
- post-battle recovery/replacement flows through ordinary treasury/industry/logistics.

### M22.8I — Player-facing carrier UI

Read-only projections plus validated commands must expose at minimum:

- carrier hangar occupancy and capacity;
- craft identity/type/fit/condition;
- ready / servicing / repair / launch / mission / recovery states;
- ammunition/propellant/service readiness;
- current mission and target/area where knowledge permits;
- launch/recovery queue and blockers;
- meaningful diagnostics for invalid launch/recovery/order attempts.

UI may not directly mutate craft, carrier, ammunition or queue state.

### M22.8J — Content and visual binding

- production carrier/support hull definitions for the shipped core factions where their doctrine requires them;
- production small-craft definitions/fits sufficient to prove interception, defence and strike roles;
- sprite/marker identity remains presentation-only and resolves from stable content IDs;
- relative size/readability follows accepted world/sprite scale rules rather than forcing craft to carrier-like screen size;
- missing visual assets fail visibly according to the accepted production asset resolver policy.

### M22.8K — Balance and counterplay

Representative paired evidence must cover carrier groups against:

- conventional gun/beam/kinetic surface combatants;
- missile-heavy forces;
- strong point-defence/interceptor screens;
- EW/deception pressure;
- degraded logistics and replacement conditions.

The acceptance target is not a predetermined winner. It must demonstrate bounded viable roles, physical counterplay, no universally dominant carrier configuration, and no artificial carrier weakness/strength added through class-name modifiers.

### M22.8L — Scale and performance

- deterministic small, medium and dense-wing scenarios;
- bounded scheduling/materialization cost documented for representative carrier groups;
- no world-wide render-rate update for dormant craft;
- local exact combat continues to use the accepted tactical authority;
- performance fixes may use scheduling/LOD/materialization architecture but may not change physical laws for off-screen craft.

### M22.8M — Save/load, migration and failure hardening

Save evidence must include checkpoints with craft:

- parked and ready;
- servicing/rearming/refuelling;
- damaged under repair;
- queued for launch/recovery;
- deployed on mission;
- permanently lost with replacement pending.

Malformed/unsupported carrier state fails closed before mutating a live campaign. Supported migration must not synthesize free craft or supplies.

### M22.8N — Final carrier acceptance / soak

One deterministic integrated acceptance corpus must prove the complete causal chain:

```text
industry/resources
→ craft manufacture and delivery
→ hangar/service readiness
→ launch and mission
→ real track-bounded combat
→ consumption/damage/loss
→ recovery and turnaround
→ strategic readiness change
→ funded physical repair/replacement
→ save/load continuation
```

The corpus must exercise PLAYER and AI through shared authorities, at least one carrier-group loss/degradation path, at least one successful recovery path, and a dense-wing long-run case without hidden grants or duplicate identities.

## 4. Stage-22 / Stage-23 dependency correction

M22.0–M22.7 remain accepted historical work. Their evidence is not invalidated.

However, the 2026-09-16 record that declared **Stage 22 COMPLETE after M22.7** was premature because the required carrier/small-craft gameplay slice had been omitted from the authoritative sequence. Therefore:

- Stage 22 status becomes **IN PROGRESS — M22.0–M22.7 accepted; M22.8 REQUIRED / PLANNED**;
- `docs/stage22_completion_record.md` is historical M22.7 closure evidence but no longer a valid final Stage-22 completion gate;
- Stage 23 is **BLOCKED / NOT STARTED** until M22.8 final acceptance is merged and a new final Stage-22 completion record is created from exact accepted evidence;
- the existing `stage23a-release-governance` branch, if retained, is future work and must not be treated as an active Stage-23 implementation baseline.

## 5. M22.8 exit criteria

M22.8 may be marked COMPLETE only when all of the following are true:

1. small craft are persistent physical assets with stable identity and finite state;
2. hangar capacity, launch, recovery and turnaround are finite and deterministic;
3. PLAYER and AI use the same validated mission/operation paths;
4. Stage-19 sensors/tracks/EW/weapons/PD/damage remain the tactical authority;
5. ammunition, propellant, repair and replacement have no hidden/free replenishment path;
6. destroyed craft remain destroyed until ordinary industry/logistics create and deliver replacement;
7. carrier-group AI has defensive, intercept, strike, recovery and withdrawal evidence;
8. strategic materialization/commit-back and save/load preserve identities, losses, consumables and readiness;
9. production UI exposes carrier/wing state without becoming authoritative;
10. representative balance evidence shows physical counterplay rather than class modifiers;
11. dense-wing deterministic/performance evidence is accepted;
12. full repository CI is green on the exact implementation head;
13. the exact accepted head is merged, post-merge verification is green/available, and only then is Stage 22 re-closed.

Until these criteria are satisfied, any document or branch that describes Stage 23 as unblocked is superseded by this contract and the corrected authoritative roadmap.