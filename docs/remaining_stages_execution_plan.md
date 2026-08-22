# Star Empires — execution plan for all unfinished stages

> Audit date: **2026-08-22**.
> Repository baseline: `main` after generated faction fleets, command UI, camera navigation,
> persistent generated saves and accepted Stage 21A living-actor kernel.

## 1. Verified status

| Stage | Actual status | What is complete | What remains |
|---|---|---|---|
| 21 — Living World | **ACTIVE** | 21.0 generated runtime/UI and 21A actor lifecycle, bounded observations, interests, scheduling and persistence | 21B–21I: goals through diplomacy, war, territory, recovery, NPC/missions/reputation and final soak |
| 22 — Content / Technology / Balance Alpha | **PLANNED** | foundational schemas/physics/industry and provisional test packs exist | production catalog, faction packages, content breadth, progression, balance, art/audio/narrative breadth and long-run alpha gate |
| 23 — Polish / Release Candidate | **PLANNED** | first responsive command UI and developer Windows launcher exist | final UX/accessibility/onboarding, production assets, performance, save recovery, package and RC validation |

Stage 21A is implemented and accepted. There is no evidence that 21B or a later Stage-21 slice is
implemented. Existing Stage-17/19 strategy and reputation primitives remain upstream authority,
not a completed autonomous diplomacy, living-world or mission system.

## 2. Product outcome

The remaining work must transform a generated, inspectable simulation into this complete loop:

```text
finite generated economy and geography
→ factions observe only available evidence
→ interests become durable goals
→ negotiation, alliance, crisis or war
→ physical fleets move, consume supply and suffer losses
→ claims, occupation and control change gradually
→ peace, repair and replacement use the same economy
→ NPCs, missions and reputation expose player participation
→ production content makes factions and choices distinct
→ RC UX/package lets a new player understand and safely continue the campaign
```

## 3. Dependency order

The safest implementation order is not “all AI, then all UI, then all content”. Each slice must end
with an inspectable vertical result.

1. **21A — COMPLETE:** actor observation, evidence, scheduling and decision traces.
2. **21B — NEXT:** durable goals, feasibility, commitment and arbitration.
3. **21C:** diplomacy/crisis/war lifecycle using Stage-17/19 authority.
4. **21D:** readiness, command groups, lawful orders and neighbor-only movement.
5. **21E–21G:** operations, losses, occupation/control, peace and economy-funded recovery.
6. **21H:** NPC/mission/reputation layer grounded in the now-moving world.
7. **21I:** integrated UI, migration, corpus, performance and long-run closure.
8. **22.0 + Imperial gold slice:** freeze content governance and prove one faction package.
9. **Stage-22 catalog breadth:** technology, hulls, stations, four contrast factions and minor actors.
10. **Stage-22 balance/freeze:** progression, world distribution, fleet/logistics/economy soaks.
11. **23A–23E:** scope lock, production UX, accessibility, onboarding and final media.
12. **23F–23J:** performance, saves, packaging, QA and exact RC gate.

Stage-22 concept and writing work may begin in parallel with Stage 21, but it cannot define hidden
mechanics or mass-balance against temporary interfaces. Stage 23 polish may prototype early, but
final acceptance waits for Stage-22 fingerprints.

## 4. Stage 21 execution detail

Canonical mechanics plan: `docs/stage21_living_world_roadmap.md`.

### 4.1 Accepted foundation — 21A COMPLETE

The merged 21A slice provides:

- persistent `FactionLivingActorState` keyed by stable faction ID;
- deterministic review deadlines and deduplicated event wakeups;
- actor-bounded economic, territorial, security and diplomatic observations;
- measurable interest evidence and canonical decision traces;
- bounded top-K scheduling and atomic review batches;
- generated-runtime composition and exact persistence continuation;
- tests for ordering, hidden/stale evidence, deadline continuity and bounded 10,000-actor work.

This foundation owns no treasury, fleets, relations, territory or war mutation. Stage 21B is the
first remaining implementation target.

### 4.2 21B vertical proof

- persistent goal ID, target, evidence, urgency, budget and lifecycle;
- secure-route, stockpile, explore, defend and obtain-access as the initial non-war set;
- add deter/coerce/raid/invade only after feasibility and commitment work;
- budget arbitration across treasury, production and finite fleets;
- hysteresis, cooldown and cancellation costs;
- UI shows “why this goal exists”, “what blocks it” and “when it will be reviewed”.

### 4.3 21C political proof

Minimum vertical scenario:

```text
observed dependency on a foreign gateway
→ access proposal
→ bounded counter-offer
→ acceptance or persisted crisis
→ deadline/breach/escalation
→ lawful war state or negotiated resolution
```

Required negative proofs:

- hidden resource cannot affect offer;
- relation score alone cannot declare war;
- a treaty cannot transfer unowned treasury/territory;
- save/load at proposal, counter-offer, ultimatum and ceasefire preserves one transition.

### 4.4 21D–21E military proof

Build from real `FleetId` entities:

- readiness projection from fit, damage, ammunition, propellant, maintenance, crew and access;
- persistent command-group wrapper without replacing member identities;
- order validation and reserved commitment;
- route and staging through known neighbor topology;
- contact acquisition and Stage-19 materialization only when forces physically meet;
- exact return of damage, stores, survivors and destroyed identities;
- no strategic combat-power shortcut as sole resolution authority.

Initial operation ladder:

1. patrol/guard;
2. escort;
3. intercept/withdraw;
4. raid;
5. blockade;
6. invasion.

Each rung requires persistence and conservation before the next.

### 4.5 21F–21G territorial and recovery proof

- keep claim, occupation, stabilization, recognition and control distinct;
- use physical presence, security, infrastructure and supply as evidence;
- apply access/tariff/construction changes through existing law;
- peace terms use real concessions and treasury transfers;
- repair/rearm/refuel consume physical stocks/capability/time;
- replacement enters an ordinary shipyard queue and preserves the loss record.

The decisive acceptance scenario is not “one side wins a battle”, but “war changes control and
industrial capability, peace is negotiated, and recovery continues without reset or free respawn”.

### 4.6 21H RPG/content proof

Begin with six persistent role types and eight mission contracts rather than a large text dump:

- faction official;
- military commander;
- trader/logistician;
- industrial/yard contact;
- explorer/intelligence contact;
- independent/frontier actor.

First mission contracts:

- emergency haul;
- normal procurement;
- escort;
- stranded-fleet rescue;
- reconnaissance;
- derelict investigation;
- interception/defense;
- repair/construction supply.

Each must be created from a real shortage/threat/object/project and complete by observing ordinary
state. This forms the Imperial gold-slice seed for the broader content plan.

### 4.7 21I closure evidence

- all living-world state visible through read-only projections;
- supported Stage-20.5/21.0 save migration;
- peaceful, alliance, negotiated-crisis, limited-war, occupation and recovery corpus;
- mid-chain save/load for every state machine;
- long-run decision/identity/economy conservation soak;
- actor workload scales with due work/events, not universe size each tick;
- human-readable timeline explains causality without leaking private information.

## 5. Stage 22 execution detail

Canonical engineering/balance plan: `docs/stage22_content_balance_plan.md`.
Canonical cross-media/content plan: `docs/content_production_plan_stage21_23.md`.

### 5.1 Stage 22 entry slice — content governance

Before authoring breadth:

- produce complete current-ID inventory and reverse references;
- decide `PROMOTE / REAUTHOR / REPLACE / RETIRE` for every provisional test definition;
- review the proposed five-major/three-minor faction roster;
- freeze content naming, units, manifests and asset folder conventions;
- create automated validators for IDs, authority links, localization, art metadata and provenance;
- define alpha floors and cut priority.

### 5.2 Imperial gold slice

Build the first production-complete faction using the accepted Imperial visual code and character
style. It must include mechanics, art, characters, missions and campaign evidence, not only sprites.

Minimum output:

- political/economic/engineering doctrine;
- six military and three civilian/support hulls;
- three signature station variants;
- complete required module/fit/industry links;
- six recurring NPCs plus generated role pool;
- ten mission templates and two faction chains;
- heraldry, icons, VFX/audio subset and localized copy;
- actual-size silhouette and long-run faction acceptance.

### 5.3 Technology and hull breadth

Implement Stage-22 work packages in dependency order:

1. materials/components and manufacturing bottlenecks;
2. reactors, storage and distribution;
3. propulsion/FTL and thermal systems;
4. sensors/fire control/EW;
5. weapons/ammunition/protection;
6. hull/compartment/slot families;
7. shipyards/facilities and construction/maintenance economics;
8. doctrine fits, fleets and market availability.

Every content family closes its own physical, economic, UI, persistence and benchmark loop before
the next breadth wave.

### 5.4 Faction/content expansion

- finish the most contrasting second faction and run pairwise acceptance;
- add remaining three major factions one at a time;
- clarify and content the three minor/transnational actors;
- add common civilian ecosystem, locations, NPCs, missions and event copy;
- reject hue-shift-only faction art and stat-bonus-only doctrine.

### 5.5 Alpha balance and freeze

Required combined evidence:

- anti-universal-fit and anti-obsolescence matrices;
- fleet saturation/endurance and cost exchange;
- ammunition/repair/reaction-mass logistics;
- market/progression accessibility;
- full generated-world logistics and macroeconomic soak;
- wars with real replacement consequences;
- faction outcome diversity and bounded snowball;
- deterministic content fingerprints and save compatibility;
- explicit final list of assets deferred to 23E.

## 6. Stage 23 execution detail

Canonical plan: `docs/stage23_release_candidate_roadmap.md`.

Work proceeds through ten slices:

- **23A:** scope lock, severity/change governance and RC manifest;
- **23B:** production UI/navigation/search/inspectors/actions;
- **23C:** resolutions, accessibility, rebinding and RU/EN localization;
- **23D:** onboarding over ordinary simulation state;
- **23E:** final art/VFX/audio replacement;
- **23F:** profiler-driven performance, memory and long-session stability;
- **23G:** atomic saves, migrations, recovery and diagnostics;
- **23H:** distributable package and clean-machine launcher;
- **23I:** campaign journeys, playtests and regression closure;
- **23J:** exact-package Release Candidate gate.

The source-tree `run-generated-world.bat` remains useful for development, but the RC Windows package
must launch without Maven, git, network access or a separately installed JDK.

## 7. Cross-stage acceptance matrix

| Concern | Stage 21 | Stage 22 | Stage 23 |
|---|---|---|---|
| living factions | causal behavior complete | faction content/doctrine breadth | explainability/UX hardening |
| military | lawful orders/operations/losses | production hulls/fits/fleet balance | readability/performance/final media |
| territory | claim→occupation→control | faction doctrine/content reactions | overlay/onboarding polish |
| NPC/missions | authority and persistence | roster/template/story breadth | editorial/accessibility closure |
| economy | causal war/recovery use | expanded stable catalog and progression | diagnostics/performance/save safety |
| visuals | functional projection | alpha faction/content assets | remaining prototype replacement |
| saves | new state and migrations | content fingerprint/aliases | recovery, compatibility and user UX |
| launcher | source developer BAT complete | alpha runnable artifact | clean-machine distributable package |

## 8. Risk register and mitigation

### AI complexity explosion

Risk: every faction/NPC scans the world and plans each tick.
Mitigation: persisted deadlines, event wakeups, actor knowledge snapshots, bounded candidate sets and
workload benchmarks established in 21A and extended at every later actor layer.

### Scripted war replacing causality

Risk: random relation rolls or narrative triggers recolour the map.
Mitigation: persisted interest evidence, crisis/war identity, physical operations, territorial
thresholds and no outcome without ordinary authority transition.

### Content breadth before stable schemas

Risk: dozens of assets/JSON entries require expensive rework.
Mitigation: Wave 0 governance, Imperial gold slice, contrast pair, then horizontal expansion.

### Faction identity as recolor/bonus

Risk: visually or mechanically shallow factions.
Mitigation: five complete visual bibles, grayscale silhouette review, engineering/industry trade-offs
and same-laws acceptance.

### Mission system as hidden grant engine

Risk: rewards and targets exist only in text.
Mitigation: issuer authority, escrow/transfer, saved target references and objective predicates over
ordinary state.

### Save incompatibility during rapid content growth

Risk: renamed IDs or fingerprints destroy campaigns.
Mitigation: stable-ID freeze, reverse-reference inventory, aliases/migrations, backup-before-migrate
and explicit unsupported-pack errors.

### RC becomes another feature stage

Risk: polish never converges.
Mitigation: 23A scope lock, evidence-based severity, explicit deferred list and exact-package gate.

## 9. Definition of roadmap completion

The remaining roadmap is complete only when:

- autonomous factions generate diverse causal histories under bounded actor knowledge;
- diplomacy, war, territory, peace and replacement use ordinary persistent authority;
- NPCs/missions/reputation allow the player to participate without freezing or scripting the world;
- production content supplies distinct, balanced factions and meaningful peaceful/military choices;
- every visual/narrative/audio element is bound to real state and has provenance;
- saves survive supported updates and failures without silent loss;
- a clean-machine RC package launches, remains readable at supported resolutions, passes long-run
  performance/continuation tests and exposes no remaining blocker.
