# Star Empires — execution plan for all unfinished stages

> Audit/synchronization date: **2026-08-26**.  
> Repository baseline for this planning snapshot: `main` at `3bafa3fd793e2cb892a0a278ec343041580d9621`, after accepted Stage-21H implementation/closeout.  
> Authoritative live status remains `docs/development_roadmap.md`; this file is a cross-stage execution/risk snapshot.

## 1. Verified status

| Stage | Actual status | What is complete | What remains |
|---|---|---|---|
| 21 — Living World | **ACTIVE** | 21.0 + 21A–21H complete: generated runtime/UI, bounded actors/goals, diplomacy/crisis/war, physical fleets/operations, territorial transition, peace/recovery/replacement, NPC/missions/reputation/discovery | **21I only:** integrated UI, migration, representative/core-pair corpus, performance and final long-run soak |
| 22 — Content / Technology / Balance Alpha | **PLANNED** | foundational schemas/physics/industry and provisional test packs exist | governance/migration, Imperial gold slice, Industrial Union contrast slice, shared civilian/minor ecosystem, pairwise content/balance/freeze |
| 23 — Polish / Release Candidate | **PLANNED** | responsive command UI and developer Windows launcher exist | final UX/accessibility/onboarding, production assets, performance, save recovery, package and RC validation for the core release scope |

Canonical faction decision:

- **Империя** — production core / Stage-22.1 gold slice;
- **Индустриальный Союз** — production core / Stage-22.2 contrast slice;
- **Директорат, Лига Свободных Систем, Пограничная Конфедерация, Консорциум, Кочевой Флот** — canonical post-core horizon, not Stage-21/22/23 blockers.

Faction authority documents:

- `docs/factions/faction_roster_and_development_horizon.md`;
- `docs/factions/empire_systemic_identity.md`;
- `docs/factions/industrial_union_systemic_identity.md`;
- `docs/factions/post_core_faction_horizon.md`.

## 2. Product outcome

The remaining core work must transform a generated, inspectable simulation into this complete loop:

```text
finite generated economy and geography
→ factions observe only available evidence
→ interests become durable goals
→ negotiation, alliance, crisis or war
→ physical fleets move, consume supply and suffer losses
→ claims, occupation and control change gradually
→ peace, repair and replacement use the same economy
→ NPCs, missions and reputation expose player participation
→ Empire / Industrial Union production content prove asymmetric same-laws factions
→ RC UX/package lets a new player understand and safely continue the campaign
```

The five horizon factions are preserved for later expansion and must not cause scope explosion before this loop reaches RC.

## 3. Dependency order

1. **21A–21H — COMPLETE.**
2. **21I — NEXT:** integrated UI/migration/representative corpus/performance/soak, including a bounded
   Империя/Индустриальный Союз systemic decision proof without final Stage-22 hull/content breadth.
3. **22.0:** content inventory, provisional disposition, current faction-ID/display-name reverse-reference and migration audit.
4. **22.1:** Imperial gold slice end-to-end.
5. **22.2:** Industrial Union contrast slice end-to-end, including common production-series/commonality audit.
6. **22.3:** shared civilian/minor ecosystem and cross-market integration.
7. **22.4:** pairwise fleet/logistics/economy/progression balance, long-run alpha soak and content freeze.
8. **23A–23E:** scope lock, production UX, accessibility, onboarding and final media.
9. **23F–23J:** performance, saves, packaging, QA and exact RC gate.
10. **Post-core:** activate horizon faction packages one at a time after fresh architecture review.

Stage-22 concept/art preparation may begin before Stage 21 closes, but cannot define hidden mechanics or
mass-balance against temporary interfaces. Stage 23 polish may prototype early, but final acceptance waits for Stage-22 fingerprints.

## 4. Stage 21 closure detail

Canonical plan: `docs/stage21_living_world_roadmap.md`.

### 4.1 Accepted foundation — 21A–21H COMPLETE

The accepted chain already proves:

- stable faction actor lifecycle, bounded observations and deterministic scheduling;
- durable strategic goals, feasibility, commitment and anti-churn;
- proposals, treaties, crises, obligations, causal war and peace hysteresis;
- finite readiness, command groups and neighbor-only ordinary movement;
- persistent physical operations, Stage-19 battle consequences and real supply/loss return;
- gradual claim/occupation/stabilization/control through Stage-17 authority;
- peace, demobilization, conserved reparations, finite repair/rearm/refuel and loss-backed replacement;
- persistent NPC identities, actor-bounded knowledge, funded missions, player participation and reputation;
- schema-composed persistence through Stage-21H without parallel economy/war/territory/quest authority.

### 4.2 Stage 21I — final living-world gate

Required implementation/evidence:

- faction UI for interests, relations, treaties, crises, wars, goals and decision evidence;
- military UI for command group, order, readiness, route, supply, operation and destination;
- global overlays for access, claims, occupation, control, wars/fronts and known intelligence;
- actor-bounded timeline/event log;
- NPC/mission/discovery inspection;
- backward migration for supported Stage-20.5/21.0 generated-world saves;
- deterministic new-world, mid-crisis, mid-transit, mid-operation and post-war save/load fixtures;
- representative corpus covering peaceful coexistence, alliance, coercion, limited war, territorial
  transition, recovery and renewed trade;
- **core-pair decision corpus:** at least one shared condition produces explainable divergent
  Empire/Industrial Union strategic ranking and at least one produces lawful convergence;
- no core-pair fixture may depend on faction-name production/combat/sensor bonuses or omniscience;
- bounded workload evidence with increasing faction/system/fleet/NPC counts;
- long-run economy/diplomacy/war/territory/NPC continuation without resource creation, ID duplication,
  deadline loss or immediate decision oscillation.

Stage 21I uses only minimal systemic/doctrine fixtures for the contrast pair. Final ship/industrial/visual content stays Stage 22.

## 5. Stage 22 execution detail

Canonical engineering/balance plan: `docs/stage22_content_balance_plan.md`.  
Canonical cross-media/content plan: `docs/content_production_plan_stage21_23.md`.

### 5.1 Stage 22.0 — content governance and faction identity migration

Before authoring breadth:

- produce complete current-ID inventory and reverse references;
- decide `PROMOTE / REAUTHOR / REPLACE / RETIRE` for every provisional Stage-17.5/19 definition;
- audit current generated faction stable IDs/display names and save references;
- decide exact mapping/migration for the Imperial and industrial runtime lineages;
- classify legacy neutral/trade/miner actors explicitly rather than treating every slot as a sovereign state;
- reserve five horizon factions as documented concepts without premature stable-ID promises;
- freeze content naming, units, manifests and asset folder conventions;
- validate IDs, authority links, localization, art metadata and provenance;
- confirm core-pair alpha floors and cut priority.

### 5.2 Stage 22.1 — Imperial gold slice

Production-complete package:

- political/economic/engineering doctrine;
- state procurement/reserve/mobilization content where ordinary authorities support it;
- six military and three civilian/support hulls;
- three signature station variants;
- complete required module/fit/industry links;
- reference fleet, support/replacement chain and market progression;
- six recurring NPCs plus generated role pool;
- ten faction-facing mission templates and two faction chains;
- heraldry, icons, VFX/audio subset and localized copy;
- actual-size silhouette and long-run faction acceptance;
- peaceful, crisis, battle, loss, recovery and save/load campaign evidence.

Mechanical identity must remain tied to heavy/serviceable/redundant engineering and institutional
continuity with real mass/cost/logistics/maintenance consequences.

### 5.3 Stage 22.2 — Industrial Union contrast slice

Production-complete package:

- political/systemic/industrial identity;
- standardization and repeated-series/commonality authority audit;
- only the minimum reusable production extension if Stage-18 cannot express the required tradeoff;
- six military and three civilian/support hulls;
- three signature station variants;
- reference industrial network, fleet and logistics train;
- real resource/route/component bottlenecks and replacement chain;
- independent visual bible, not an Imperial recolor;
- six recurring NPCs;
- ten faction-facing mission templates and two faction chains;
- VFX/audio/localization subset;
- peaceful, crisis, battle, bottleneck disruption, loss, replacement and save/load evidence.

Core acceptance:

```text
same simulation
→ Empire prefers institutional reserves / serviceable expensive capability
→ Industrial Union prefers standardized series / throughput / replaceability
→ each advantage has a physical/economic cost
→ neither receives a hidden faction stat bonus
```

### 5.4 Technology and hull breadth

Implement Stage-22 work packages in dependency order:

1. materials/components and manufacturing bottlenecks;
2. reactors, storage and distribution;
3. propulsion/FTL and thermal systems;
4. sensors/fire control/EW;
5. weapons/ammunition/protection;
6. hull/compartment/slot families;
7. shipyards/facilities and construction/maintenance economics;
8. doctrine fits, fleets and market availability.

Every content family closes its own physical, economic, UI, persistence and benchmark loop before breadth expands.

### 5.5 Stage 22.3 — shared civilian/minor ecosystem

- clarify legacy neutral/trade/miner political/legal roles;
- build common/licensed civilian hull and station ecosystem;
- add independent contacts, disputes, contracts and access networks;
- integrate cross-faction market/module/repair/refit availability;
- add locations, NPCs, missions and event copy required for peaceful/economic careers;
- reject hue-shift-only faction art and stat-bonus-only doctrine;
- do not open Directorate/League/Confederation/Consortium/Nomad production packages.

### 5.6 Stage 22.4 — core-pair alpha balance and freeze

Required combined evidence:

- anti-universal-fit and anti-obsolescence matrices;
- fleet saturation/endurance and cost exchange;
- ammunition/repair/reaction-mass logistics;
- Imperial reserve/serviceability and Industrial Union series/throughput advantages with measured costs;
- market/progression accessibility;
- full generated-world logistics and macroeconomic soak;
- wars with real replacement consequences;
- pairwise outcome diversity and bounded snowball;
- deterministic content fingerprints and save compatibility;
- no post-core architecture hardcode where a general representation is equally practical;
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

The core RC faction requirement is production-complete **Империя + Индустриальный Союз**. The five
post-core horizon factions are not 23J blockers.

The source-tree `run-generated-world.bat` remains useful for development, but the RC Windows package
must launch without Maven, git, network access or a separately installed JDK.

## 7. Cross-stage acceptance matrix

| Concern | Stage 21 | Stage 22 | Stage 23 |
|---|---|---|---|
| living factions | causal behavior complete + core-pair decision proof | two production core faction packages | explainability/UX hardening |
| military | lawful orders/operations/losses | production hulls/fits/pairwise fleet balance | readability/performance/final media |
| territory | claim→occupation→control | core faction doctrine/content reactions | overlay/onboarding polish |
| NPC/missions | authority and persistence | core pair + shared roster/template/story breadth | editorial/accessibility closure |
| economy | causal war/recovery use | expanded stable catalog and pairwise industrial progression | diagnostics/performance/save safety |
| visuals | functional projection | two core visual bibles + shared alpha assets | remaining prototype replacement |
| saves | new state and migrations | content/faction fingerprint/aliases | recovery, compatibility and user UX |
| launcher | source developer BAT complete | alpha runnable artifact | clean-machine distributable package |

## 8. Risk register and mitigation

### AI complexity explosion

Risk: every faction/NPC scans the world and plans each tick.  
Mitigation: persisted deadlines, event wakeups, actor knowledge snapshots, bounded candidate sets and workload benchmarks.

### Scripted war replacing causality

Risk: random relation rolls or narrative triggers recolour the map.  
Mitigation: persisted intent/crisis/war evidence, physical operations, ordinary territorial thresholds,
causal recovery and no outcome without the existing authority transition.

### Content breadth before stable schemas

Risk: dozens of assets/JSON entries require expensive rework.  
Mitigation: 22.0 governance → Imperial gold slice → Industrial Union contrast slice → shared horizontal breadth.

### Faction identity as recolor/bonus

Risk: visually or mechanically shallow factions.  
Mitigation: **two complete core visual/systemic bibles**, grayscale silhouette review, engineering/industry
trade-offs, pairwise decision/logistics/fleet tests and same-laws acceptance. The five future bibles are post-core work.

### Premature horizon implementation

Risk: Directorate/League/Confederation/Consortium/Nomad requirements pull private economy, finance or
mobile industry into core before the existing game is finished.  
Mitigation: preserve requirements in `docs/factions/post_core_faction_horizon.md`; implement only when a
current core/player need independently justifies a common extension or after Stage-23 RC.

### Mission system as hidden grant engine

Risk: rewards and targets exist only in text.  
Mitigation: issuer authority, escrow/transfer, saved target references and objective predicates over ordinary state.

### Save incompatibility during faction/content renaming

Risk: renamed IDs or fingerprints destroy campaigns.  
Mitigation: Stage-22.0 stable-ID reverse-reference audit, explicit aliases/migrations, backup-before-migrate and explicit unsupported-pack errors.

### RC becomes another feature stage

Risk: polish never converges.  
Mitigation: 23A scope lock, evidence-based severity, explicit deferred list and exact-package gate.

## 9. Definition of core-roadmap completion

The main roadmap is complete only when:

- autonomous factions generate diverse causal histories under bounded actor knowledge;
- diplomacy, war, territory, peace and replacement use ordinary persistent authority;
- NPCs/missions/reputation allow the player to participate without freezing or scripting the world;
- **Империя and Индустриальный Союз** are distinct, balanced production-complete sovereign faction packages using the same laws;
- peaceful and military choices have meaningful production content and progression;
- every shipped visual/narrative/audio element is bound to real state and has provenance;
- saves survive supported updates and failures without silent loss;
- a clean-machine RC package launches, remains readable at supported resolutions, passes long-run
  performance/continuation tests and exposes no remaining blocker.

After this gate, the documented five-faction horizon becomes eligible for normal post-core expansion work.
