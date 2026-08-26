# Star Empires — content production plan for Stages 21–23

> Статус: **CANONICAL PLANNED CONTENT CONTRACT**.  
> Scope: authored data, factions, ships, stations, characters, missions, world locations, UI art,
> VFX, audio, localization and validation required to turn the accepted simulation into an alpha and
> then a release candidate.  
> **Production-complete sovereign faction scope through Stage 23: Империя + Индустриальный Союз.**

Faction roster/horizon authority:

- `docs/factions/faction_roster_and_development_horizon.md`;
- `docs/factions/faction_gameplay_visual_balance_bible.md`;
- `docs/factions/empire_systemic_identity.md`;
- `docs/factions/empire_visual_bible.md`;
- `docs/factions/industrial_union_systemic_identity.md`;
- `docs/factions/industrial_union_visual_bible.md`;
- `docs/factions/post_core_faction_horizon.md`;
- `docs/factions/faction_balance_validation_framework.md`;
- `docs/factions/faction_implementation_roadmap.md`;
- `docs/characters/character_master_prompt.md`.

## 1. Purpose

Content is not decoration added after mechanics. In Star Empires it must make the already accepted
physical, economic, informational and political systems legible and produce different lawful ways
to play them.

The production chain is:

```text
world/faction/gameplay need
→ authoritative content definition
→ physical/economic/knowledge validation
→ visual/narrative/audio brief
→ candidate production and review
→ runtime binding by stable content ID
→ actual-size/readability validation
→ deterministic integration and fingerprint
→ campaign/balance acceptance
```

An attractive asset without valid authority binding is not game content. A balanced JSON entry with
no readable player-facing identity is not production-complete content.

## 2. Current inventory and gaps

At the current pre-Stage-22 baseline the repository contains:

- eight persistent faction identities in the large generated-world profile;
- five legacy economic commodities and a much richer Stage-18 physical ontology;
- provisional Stage-17.5/19 engineering, protection, weapon, ammunition and doctrine packs;
- a minimum Stage-20.5 Imperial sprite pack for utility, freight, mining, two combat roles, three
  station roles, four resource-body regions and a derelict;
- reviewed, independent core-faction visual bibles for the Empire and Industrial Union;
- a canonical master prompt for grounded hand-painted character illustrations;
- responsive command UI and deterministic generated-world runtime.

The eight generated identities are **runtime compatibility actors**, not an automatic release roster.
The canonical design decision of 2026-08-26 narrows the production-complete sovereign scope to two
core factions while preserving five additional major factions as post-core horizon concepts.

The principal gaps are therefore:

- exact mapping/disposition of existing generated faction IDs to final core/runtime roles has not yet
  passed Stage-22.0 migration and reverse-reference review;
- both core visual languages are documented, but production assets still need fit binding, manifests,
  real-scale review and full runtime coverage;
- provisional combat test IDs are not automatically canon;
- no production-wide NPC/character roster, mission library or dialogue/event vocabulary exists;
- no release-wide ship/station/location art inventory exists;
- VFX, audio, icons, localization and asset provenance are not governed as one pipeline;
- current five-item catalog names are compatibility-era abstractions and must not bypass the Stage-18
  ontology during expansion;
- content breadth needs one shared cut priority and a measurable **core-pair** completion floor.

## 3. Content invariants

1. Stable content ID, simulation authority and presentation asset are separate identities.
2. A sprite, portrait, name or description never creates cargo, capability, reputation or ownership.
3. Every manufactured object resolves into Stage-18 inputs, facility capability, work, time and cost.
4. Every fitted ship resolves through the Stage-17.5 physical budget and Stage-19 combat authority.
5. Faction difference comes from doctrine, institutions, engineering, geography and behavior, not
   hidden universal bonuses.
6. NPC knowledge and mission text may reveal only facts available to the issuer/character.
7. Rewards are escrowed or lawfully transferred; text cannot promise unowned resources.
8. Variants that change only paint/name do not masquerade as new mechanical designs.
9. A mechanically different external module must be visually consistent with its count, location,
   firing arc, bay, aperture, radiator or thruster anchor.
10. Content changes participate in semantic fingerprinting and supported save decisions.
11. All shipped assets have authorship/license/provenance metadata.
12. Russian and English player-facing text use stable localization keys; no final copy is embedded in
    simulation logic.
13. Existing generated-world faction IDs cannot be silently repurposed because their current display
    names resemble a later lore faction.
14. Post-core faction requirements cannot expand Stage 22/23 unless a common core/player need independently justifies the architecture change.

## 4. Canonical faction roster decision

### 4.1 Production-complete core pair

The main development stage through Stage 23 ships two reviewed sovereign major-faction packages:

| Canonical faction | Current runtime lineage candidate | Production role |
|---|---|---|
| **Империя** | `faction.imperial_directorate` lineage | primary gold slice; hierarchical state, heavy redundant/serviceable engineering, reserves and state procurement |
| **Индустриальный Союз** | `faction.industrial_combine` lineage | mandatory contrast; standardized mass production, repeated series, bulk logistics and resource/bottleneck pressure |

The table records **design intent, not an already-approved rename/migration**. Stage 22.0 must inspect
all reverse references and supported saves before deciding whether to retain a stable ID with a new
display identity, add an alias/migration, or use another compatible mapping.

### 4.2 Canonical post-core major-faction horizon

These factions are fixed as future major designs but are **not Stage-22/23 content packages**:

1. **Директорат** — precision/automation/high-complexity industrial model;
2. **Лига Свободных Систем** — private economy/freight/risk/credit horizon;
3. **Пограничная Конфедерация** — salvage/refit/substitution/scarcity resilience;
4. **Консорциум** — ownership/concessions/debt separate from sovereignty;
5. **Кочевой Флот** — mobile economic nodes and non-territorial civilization.

Their future requirements are preserved in `docs/factions/post_core_faction_horizon.md`. No production
sprite quota, NPC quota, hull roster or stable-ID migration is required for them before Stage 23.

### 4.3 Existing minor/transnational runtime actors

Existing actors such as:

- `faction.neutral`;
- `faction.trade_league`;
- `faction.miners`;

must be modelled deliberately. If they are organizations, cooperatives or independent-settlement
abstractions rather than territorial sovereigns, diplomacy and territory UI must say so. They must
not accidentally receive all major-state behavior merely because they occupy runtime faction slots.

They may receive a reduced identity/contact/content sheet needed for the civilian ecosystem; that is
not equivalent to a third/fourth/fifth production-complete sovereign package.

## 5. What constitutes a complete major-faction content package

This package definition applies to the two core factions now and becomes the template for any future
post-core faction when that faction is activated for development.

### Political and systemic identity

- origin and present material interests;
- political structure and lawful decision actors;
- sovereignty/claims/treaty/legal culture;
- economic comparative advantages and dependencies;
- security doctrine, acceptable coercion and escalation preferences;
- war aims, peace terms, grievance/memory vocabulary;
- expansion, colonization and occupation posture;
- attitude to outsiders, player faction and transnational organizations;
- failure/recovery behavior, not only ideal-state lore.

### Engineering and fleet identity

- accepted technology availability and industrial bottlenecks;
- reactor/drive/thermal/protection/sensor/EW/weapon preferences;
- crew, maintenance, automation and endurance assumptions;
- fleet roles, command group composition and reserve policy;
- six signature military base hulls minimum;
- three faction-specific civilian/support designs minimum;
- doctrine fits and refit families using ordinary modules;
- ship/station naming, registry and manufacturer rules.

### Visual identity

- faction visual bible and negative prompt;
- heraldry at state, military and small technical-mark levels;
- palette with usage ratios, not only HEX swatches;
- silhouette grammar for small/medium/large ships;
- station/interior/equipment language;
- rank, profession, condition and regional variation rules;
- actual-size comparison sheet against the other shipped core faction and shared civilian content;
- UI accent usage that preserves shared usability semantics.

### Human and narrative identity

- at least six recurring named NPCs per core faction;
- role/archetype pool for generated officials, military, commerce, labor, science and fringe actors;
- speaking style, terms of address, taboo/values and institutional vocabulary;
- two short faction story chains grounded in ordinary world events;
- reaction copy for trade, access, treaty, aid, betrayal, loss, occupation and peace;
- public-news and private-report voices;
- portrait/character-art roster with rank and role readability.

### Acceptance evidence

- one reference economy/start in the generated corpus;
- one peaceful/diplomatic and one conflict/recovery history;
- one reference fleet plus industrial/logistics support;
- one player progression/access path;
- pairwise comparison against the other shipped core faction;
- content-reference, localization, asset, fingerprint and save tests.

Minor/transnational organizations may ship a reduced package: explicit legal/systemic role, limited
visual identity, contacts and contract vocabulary, shared civilian assets where lawful, and full authority validation.

## 6. Production breadth target

These are alpha floors used for planning and cut decisions, not permission to add low-value filler.

| Content family | Stage-22 alpha floor | RC rule |
|---|---:|---|
| production-complete sovereign core factions | **2** | Империя + Индустриальный Союз complete |
| production-complete post-core major factions | **0 required** | explicitly outside core RC |
| core-faction visual bibles | **2** | no placeholder bible |
| recurring named core-faction NPCs | **12 (6×2)** | portrait/copy/authority complete |
| shared/minor/independent recurring contacts | 6+ where gameplay requires | legal role explicit; no fake sovereign package |
| generated NPC role archetypes | 24+ | core faction, role, knowledge and availability coverage |
| military base hulls | **12 (6×2)** | every role/faction niche justified |
| faction civilian/support base hulls | **6 (3×2)** | may share lawful modules/components |
| neutral/licensed/shared civilian hulls | 8+ | common market/progression path |
| station exterior roles | 10+ combined | industrial capability readable |
| faction signature station variants | **6 (3×2)** | not required to duplicate every functional role |
| special-location archetypes | 20+ | discovery/salvage/mission hooks validated |
| parametric mission templates | 48+ game-wide | all derive from real world state |
| authored core-faction story chains | **4 (2×2)** | no frozen-world outcome scripting |
| public/private event templates | 60+ as needed | information-scope tags required |
| core UI/status icons | as needed | semantic/readability audit, not count chasing |
| audio event families | as needed | state-aligned and mix-budgeted |

Base hull count does not include fit-only variants. A variant needs new art only when external
geometry or readable silhouette actually changes. This keeps mechanical breadth from multiplying
asset cost without player value.

The five horizon factions do not contribute to any Stage-22 alpha quota.

## 7. Content production order

### Wave 0 — Governance, inventory and migration

- enumerate every current content ID and runtime reference;
- classify each as `PROTOTYPE`, `CANDIDATE`, `ALPHA` or `RC`;
- for Stage-17.5/19 definitions record `PROMOTE`, `REAUTHOR`, `REPLACE` or `RETIRE`;
- build reverse-reference report: definition → fit/fixture/save/world/art;
- audit every current generated faction stable ID/display name;
- record explicit core-pair mapping/migration and legacy actor disposition;
- reserve the five post-core faction concepts without forcing stable runtime IDs now;
- define schemas/manifests before bulk authoring;
- lock units, naming and localization conventions.

### Wave 1 — Imperial gold slice

Use the accepted Imperial visual/systemic bibles to finish one end-to-end faction package first:

- political and engineering doctrine;
- one complete industrial chain and market access path;
- six military and three civilian/support hulls;
- three signature station variants;
- six recurring NPCs and core generated roles;
- ten faction-facing mission templates and two short story chains;
- production UI accents, icons, VFX and audio subset;
- peaceful, crisis, battle, loss, recovery and save/load acceptance.

The gold slice proves pipeline quality and actual cost before multiplying work.

### Wave 2 — Industrial Union contrast pair

The contrast faction is no longer selected later: it is canonically **Индустриальный Союз**.

Complete:

- systemic/political/industrial identity;
- production-series/commonality implementation or the minimum reusable extension proven necessary;
- independent visual bible, not an Imperial recolor;
- six military and three civilian/support hulls;
- three signature station variants;
- reference industrial network, fleet and logistics train;
- six recurring NPCs;
- ten faction-facing mission templates and two short story chains;
- production UI accents, icons, VFX and audio subset;
- peaceful, crisis, battle, bottleneck disruption, loss, replacement and save/load acceptance.

Pairwise validation must prove:

- silhouette recognition without color/heraldry;
- different lawful strategic decisions under at least one shared world condition;
- lawful convergence under at least one shared optimum;
- different viable fleet/industrial solution without magic modifiers;
- different NPC voice and visual hierarchy without breaking common project art style;
- cross-faction diplomacy, trade and conflict content;
- no globally dominant faction across combat + economy + logistics + recovery.

### Wave 3 — Shared civilian/minor ecosystem

- clarify legacy neutral/trade/miner political roles;
- build common/licensed civilian hull and station ecosystem;
- add independent contacts, disputes, contracts and access networks;
- integrate cross-faction markets, repair/refit and manufacturers;
- ensure peaceful play has content density comparable to war play;
- do **not** open production packages for the five post-core factions.

### Wave 4 — World locations and campaign variety

- special locations, derelicts, anomalies and infrastructure variants;
- regional names, manufacturers, condition states and service histories;
- authored chains anchored to generated geography and living actors;
- news/event vocabulary and discovery presentation;
- representative-seed distribution and repetition checks.

### Wave 5 — Core-pair alpha balance and content freeze

- full economy/logistics/combat/progression soaks;
- pairwise Imperial/Industrial Union comparison;
- remove dead content and universal best choices;
- settle market availability, rarity and replacement cadence;
- freeze stable IDs and alpha fingerprints;
- run post-core architecture compatibility checklist without implementing the five horizon factions;
- enter Stage 23 only with an explicit remaining production-art list.

## 8. Ship content pipeline

### 8.1 Design brief

Every ship brief states:

- stable ID, role, faction/manufacturer and intended operators;
- expected threat/mission environment;
- physical hull size, dry mass, volume and compartment plan;
- crew/automation/endurance assumption;
- slots, hardpoints, bays, apertures, radiators and thrusters;
- power, thermal, propulsion, signature, delta-v and maintenance budgets;
- manufacturing inputs, facility capability, time, price and OPEX;
- doctrine fits, counterplay and roles it must not replace;
- required game-scale silhouette cues.

### 8.2 Concept exploration

For each new visual family:

- generate/draw five genuinely different candidates as separate images, not one collage;
- preserve the same role and physical envelope so comparison is meaningful;
- reject candidates with unexplained wings, greebles or impossible firing/service geometry;
- select one candidate using engineering, faction and actual-size readability review;
- store rejection reason to avoid recreating the same invalid direction.

### 8.3 Production sprite package

Required where applicable:

- clean base/albedo sprite with genuine alpha and no baked exhaust/projectiles/background;
- damage overlay/mask aligned to compartments or at least accepted damage regions;
- emissive/sensor/service light layer;
- engine idle/thrust states driven by actual propulsion state;
- hardpoint/module/bay/thruster/radiator anchors in deterministic metadata;
- centered pivot, known physical orientation and safe transparent padding;
- small marker/icon silhouette derived from the same design;
- source/provenance manifest and accepted content ID binding.

The current accepted orientation law remains authoritative: physical forward `+Y` maps to the
sprite-pack forward-right axis used by Stage 20.5. Any future atlas convention change must be an
explicit global migration, not a per-artist guess.

### 8.4 Review gates

1. physics/fitting validity;
2. faction silhouette without palette;
3. external module count/location agreement;
4. 1× gameplay scale and zoomed-out marker readability;
5. alpha/pivot/orientation/atlas automation;
6. damage/emissive/thrust state capture;
7. performance/texture budget;
8. save/load rebinding by stable ID.

## 9. Core faction visual/systemic production rules

### 9.1 Империя

The Imperial production pack follows the accepted formula:

```text
heavy engineering
+ protected axial prow
+ visibly stronger central citadel
+ modular engineering stern
+ repairability and redundancy
+ graphite/gunmetal + warm ivory + restrained burgundy/brass
+ maintained service wear
```

Required qualities:

- long axial silhouette with clear bow/citadel/stern separation;
- large functional access and service zones;
- protected, recessed or sectional radiators;
- distributed maneuvering thrusters and redundant sensors;
- rational weapon arcs: axial kinetic, protected VLS and compact PD are preferred motifs;
- status through precision and rare brass, never gold-covered machinery;
- old hull/new subsystem continuity through replacement panels and service markings;
- no fantasy wings, baroque ornament, steampunk, neon or random surface noise.

Systemic authority: `docs/factions/empire_systemic_identity.md`.  
Visual authority: `docs/factions/empire_visual_bible.md`.  
Character authority: `docs/characters/character_master_prompt.md`.

### 9.2 Индустриальный Союз

Wave 2 bulk art must use the reviewed production visual bible rather than inventing a new palette,
silhouette or status language per asset.

Canonical visual formula:

```text
standardized family resemblance
+ repeatable modular construction
+ visible industrial handling/service interfaces
+ practical mass-production grammar
+ strong freight/yard identity
+ no aristocratic Imperial silhouette language
```

Required constraints:

- not a hue-shifted Imperial ship family;
- related hull classes should visibly share manufactured subassemblies/structural grammar where physically plausible;
- industrial and logistics vessels are signature assets, not generic background craft;
- practical maintainability/assembly cues must correspond to actual module/compartment/service geometry;
- no fantasy factory aesthetic, arbitrary pipes or decorative machinery that cannot be serviced or justified.

Palette, materials, markings, uniforms, ship hierarchy and negative prompts are authoritative in
`docs/factions/industrial_union_visual_bible.md`; they remain presentation rules, not simulation stats.

Systemic authority: `docs/factions/industrial_union_systemic_identity.md`.  
Visual authority: `docs/factions/industrial_union_visual_bible.md`.  
Character authority: `docs/characters/character_master_prompt.md`.

## 10. Stations, infrastructure and special locations

### Functional station roles

- trade/administration hub;
- extraction and ore handling;
- refinery/material processing;
- energy/fuel/propellant complex;
- agriculture/habitation;
- component/precision manufacturing;
- arsenal/ammunition depot;
- repair/refit yard;
- light shipyard;
- capital integration yard;
- research/sensor/communications facility;
- military base/forward logistics node.

Roles may coexist in one physical station only through installed Stage-18 capabilities. Art variants
must reveal major functional modules without changing capacity authority.

### Special-location families

- derelict civilian and military hulls;
- abandoned station/yard sections;
- depleted or contested extraction sites;
- hidden depots and caches with finite inventories;
- sensor phenomena and unusual resource hosts;
- historical wreck fields;
- failed colonies/outposts;
- research observatories/probes;
- navigation hazards and disrupted gateways where supported;
- politically significant memorial, border and treaty locations.

Every location needs discovery requirements, persistent identity, finite salvage/resources,
knowledge scope, possible stakeholders and post-interaction state. A background illustration alone
is not a location.

## 11. Technology, module and commodity content

Stage 22 engineering families are authored as trade-off sets, not upgrade ladders. Each definition
must carry:

- quantitative accepted physical parameters;
- compatible slot/size/capability constraints;
- Stage-18 materials/components/facility/work requirements;
- maintenance, repair, failure and salvage semantics;
- faction availability/procurement doctrine;
- human-readable benefits, costs and counterplay;
- calibrated domain and benchmark coverage.

The five compatibility-era commodities remain only where their abstraction is still meaningful.
New economy content must reference the richer Stage-18 resource/component/facility grammar and must
not create duplicate “ore/steel/weapons” chains beside it.

Core-faction technology distinction must remain manufacturable:

- Imperial preferences may spend mass/volume/cost on redundancy, serviceability and mature support;
- Industrial Union preferences may spend flexibility/tooling freedom on standardization, throughput and commonality;
- neither preference grants output or performance by faction name.

## 12. Character and portrait pipeline

### Shared project style lock

Production character art uses the accepted master style:

- hand-painted 2D RPG illustration with traditional ink-and-paint feeling;
- subtle restrained anime and European graphic-novel influence;
- thin, slightly irregular dark linework and visible human variation;
- muted opaque watercolor/gouache-like color, dry-brush texture and limited shading;
- distinctive, slightly asymmetric faces rather than universal model beauty;
- practical role/status clothing with wear, repairs, fasteners, insignia and believable equipment;
- soft ambient light, no glossy skin, photorealism, 3D render or cinematic glow;
- transparent background, no scenery, frame or baked text;
- full or three-quarter body with readable centered silhouette and natural pose.

Faction colors are restrained accents. Rank and profession must remain readable in grayscale and at
actual dialogue-panel scale.

### Character data before art

Every recurring NPC requires:

- stable NPC/content ID;
- faction, institution, rank, profession and lawful authority;
- age range, background, present interest and personality tension;
- location/availability rules and knowledge boundaries;
- relationship/reputation hooks;
- speech register, address forms and forbidden knowledge;
- persistent consequences if transferred, captured, killed, promoted or displaced;
- visual brief: face, silhouette, clothing, practical prop and condition.

Generated archetypes use bounded authored pools. A name/portrait combination must be deterministic
from saved identity and cannot reroll on load.

### Art production

- produce five separate candidate variants for a recurring character where a new production character visual is being selected;
- select for face distinctiveness, faction identity, role identity and restraint;
- derive UI portrait crop from the accepted master illustration where possible;
- validate transparent alpha and absence of accidental checkerboard/background;
- create expression/state variants only when dialogue UI truly consumes them;
- record prompt/version/provenance and disallow unreviewed face drift across variants.

## 13. NPC roster architecture

Each production-complete core faction covers at least:

- senior political/diplomatic representative;
- fleet/defense commander;
- intelligence/recon contact;
- industrial/shipyard authority;
- trade/logistics operator;
- civilian/frontier/labor voice.

Generated role pools additionally cover station operators, captains, miners, brokers, engineers,
medics/rescue crews, salvagers, scientists, inspectors, smugglers, mercenaries and displaced actors.

The roster must include disagreement within a faction. NPC personality changes offers, risk,
language and priorities within lawful bounds; it does not add magic combat/economy bonuses.

Minor/transnational actors receive only the contacts necessary for their real gameplay role and do
not need to mimic the six-role sovereign structure.

## 14. Mission and narrative content

### Mission families and game-wide alpha floor

| Family | Minimum templates | World-state sources |
|---|---:|---|
| haul/supply/procurement | 8 | shortage, stock target, project, relief, military supply |
| mining/salvage/recovery | 6 | finite occurrence, wreck, missing cargo, repair demand |
| escort/rescue/security | 8 | route risk, convoy, stranded fleet, threat report |
| recon/discovery/intelligence | 6 | unknown system/object, stale intel, target observation |
| combat/coercion | 6 | raid, interception, blockade, bounty, defense operation |
| industry/construction/repair | 6 | real site inputs, yard queue, damaged asset, capability gap |
| diplomacy/access/reputation | 4 | proposal, treaty, crisis, recognition, prisoner/aid exchange |
| faction/story structural patterns | 4 reusable patterns | living actors plus authored character conflicts |

The table yields at least 48 parametric templates. It is a game-wide coverage floor; a template that
merely changes a faction name or noun does not count as a different gameplay contract.

Each core faction package must exercise at least ten suitable templates; the remaining templates may
be shared/independent where the same lawful world-state contract applies.

### Mission contract

Every mission stores:

- persistent mission ID and template version;
- issuer NPC/faction and lawful authority;
- target object/location/actor references;
- issuer knowledge/evidence used to create the offer;
- objective state predicates over ordinary world authority;
- accepted/rejected/expired/completed/failed/cancelled lifecycle;
- deadlines and event wakeups in simulation time;
- reserved reward/escrow and permitted non-material terms;
- failure, collateral and reputation consequences;
- text variables derived from saved facts;
- update behavior if the underlying target changes before acceptance/completion.

Mission completion is observed, never self-declared by UI. “Deliver 100 t” must refer to an actual
transfer; “destroy” to an actual entity loss; “survey” to acquired knowledge; “escort” to physical
co-presence and arrival; “negotiate” to an accepted political state transition.

### Authored story chains

Each core faction receives two compact chains, ideally 3–5 steps each:

- one internal/economic/institutional conflict;
- one external/diplomatic/security conflict.

Authored characters supply motives and interpretation, while branch outcomes remain grounded in
the live economy, relations, travel, battles and territorial state. If the world already resolved or
destroyed a required object, the chain adapts or closes honestly; it must not respawn a prop.

Future horizon factions receive equivalent chains only when their post-core packages enter active production.

## 15. Names, copy and procedural text

Name generation requires separate deterministic pools for:

- factions/institutions/manufacturers;
- people by culture/region without caricature;
- ship class, registry and personal name;
- stations, systems, colonies and special locations;
- operations, crises, treaties and wars.

Rules:

- stable generated identity keeps the same name across save/load;
- uniqueness is bounded within relevant scope and collision handling deterministic;
- names do not leak hidden ownership or object class;
- generated text is assembled from grammatically valid localized templates;
- player-visible copy distinguishes confirmed fact, report, estimate, allegation and unknown;
- no infinite combinatorial text generation is required for alpha; authored clarity wins.

## 16. UI content and iconography

The shared UI remains faction-neutral in interaction grammar. Faction accents cannot change the
meaning of red/amber/cyan safety and status signals.

Required icon families:

- object classes and ship roles;
- faction/relationship/knowledge state;
- cargo/storage/commodity and industry;
- power, heat, thrust, propellant, ammunition and maintenance;
- sensors, EW, track quality and communications latency;
- orders, operations, route, access and blockade;
- claim, occupation, stabilization and control;
- NPC, mission, deadline, reward and reputation;
- warnings, errors and validation reasons.

Every icon has:

- stable semantic ID;
- vector/source or maintainable master where possible;
- normal, selected, disabled and warning usage rules;
- monochrome/small-size test;
- tooltip/localization key;
- no dependence on color alone.

## 17. VFX content

VFX packages are authored by physical event family:

- thrust/plume by drive and throttle state;
- kinetic launch, projectile, impact, ricochet/fragment and penetration;
- beam wavelength/aperture/dwell and material response;
- guided launch, motor phase, seeker/terminal behavior and intercept;
- PD, decoy, ECM and datalink cues without revealing hidden targets;
- shield coupling, overload, restart and directional coverage;
- compartment/subsystem damage, venting, fire and destruction;
- docking, transfer, mining, salvage, repair and construction work;
- jump spool, departure, transit indication and exact arrival.

Base sprites never bake transient VFX. Saturation budgets define importance/aggregation so a large
battle remains readable without deleting authoritative projectiles.

## 18. Audio content

Audio is tied to observed events and player information scope.

Required families:

- UI focus/confirm/cancel/validation/warning/emergency;
- ship propulsion, machinery, alarms and damage state;
- weapon launch/fire/impact/interception;
- docking, cargo, mining, repair and construction feedback;
- communications, mission and diplomatic event cues;
- station/system ambience;
- restrained faction stingers and music palette if music is in RC scope.

Mixing priorities protect speech/critical warning over ambience and saturation. Space-combat audio is
an interface representation for the player, not proof of atmospheric sound propagation.

## 19. Localization and editorial plan

### Source language and keys

- Russian is the initial authored source language;
- English is the required second-language production path;
- stable semantic keys bind UI, content and dialogue;
- variables are typed and unit-aware;
- translators receive faction glossary, character voice and technical terminology.

### Editorial passes

1. mechanical truth and authority check;
2. clarity and actionability;
3. faction/character voice;
4. terminology/glossary consistency;
5. grammar/plural/layout;
6. spoiler/knowledge-scope check;
7. final in-context capture review.

## 20. Content repository and manifest policy

Recommended logical layout:

```text
data/content/<domain>-vN.json
assets/factions/<faction-id>/ships/<hull-id>/
assets/factions/<faction-id>/stations/<station-id>/
assets/factions/<faction-id>/characters/<npc-or-archetype-id>/
assets/ui/icons/<semantic-family>/
assets/vfx/<physical-event-family>/
assets/audio/<event-family>/
localization/ru/<domain>.properties
localization/en/<domain>.properties
```

Every asset pack manifest records:

- stable asset and content IDs;
- version/status;
- role/faction/variant;
- dimensions, pivot, orientation, atlas region and layer semantics;
- expected physical anchor bindings;
- source/provenance/license;
- content/art review decisions;
- checksum and runtime path;
- replacement/compatibility aliases where needed.

Filenames are not simulation identity. Renaming a source file cannot silently create a new ship.

Post-core horizon factions do not need empty asset directories or placeholder IDs before their own production phase.

## 21. Automated validation

Content CI must fail on:

- duplicate/invalid IDs, unresolved references or schema version;
- non-finite/out-of-domain physical values;
- missing Stage-18 inputs/facility capability for manufactured content;
- invalid slot/hardpoint/module fit;
- absent weapon/ammunition/launcher compatibility;
- orphaned localization/icon/art/audio references;
- PNG without genuine alpha where transparency is required;
- invalid sprite dimensions/pivot/orientation/anchor metadata;
- missing provenance/license manifest;
- mission without issuer authority, target predicate, deadline policy or reward source;
- NPC/dialogue fact outside declared knowledge scope;
- unsupported save alias/removal;
- unexpected semantic fingerprint change;
- a production core faction missing its required systemic/visual/content manifest;
- a faction profile granting hidden authoritative resources/capability by faction name.

Human review remains required for silhouette, role readability, faction identity, language quality,
audio mix and actual gameplay value.

## 22. Balance and content-quality acceptance

The reproducible protocol, result vectors, scenario suite and gates are authoritative in
`docs/factions/faction_balance_validation_framework.md`.

Content is accepted as a system, using:

- role coverage matrix;
- anti-universal-fit and anti-linear-obsolescence tests;
- cost, availability, maintenance, endurance and replacement comparisons;
- fleet-vs-fleet and saturation scenarios, not DPS only;
- peaceful economy and wartime logistics soaks;
- player progression paths across lawful markets, reputation, salvage and own industry;
- faction decision/outcome diversity over representative seeds;
- Imperial/Industrial Union pairwise industrial, fleet, logistics and recovery comparison;
- at least one pairwise scenario where their lawful priorities diverge and one where they converge;
- content repetition telemetry from long campaigns;
- actual-size visual captures under normal and saturated scenes;
- save/load continuity after content migration.

No arbitrary equal win-rate target is required between asymmetric factions. Each shipped core faction
must have credible strengths, costs, counters and recovery paths within the same laws.

The post-core horizon is checked only for obvious architecture lockout: Stage-22 code/content schemas
must not unnecessarily encode assumptions such as “every sovereign economy is centralized” or “all
industrial nodes are permanently static” when a more general representation is equally practical.
This is compatibility review, not implementation.

## 23. Stage mapping

### Stage 21 owns

- minimum actor/faction vocabulary required to explain interests and decisions;
- NPC identity, role, knowledge, availability and reputation semantics;
- mission templates and Imperial gold-slice character content needed to prove the living-world loop;
- Stage-21I systemic core-pair decision/corpus fixtures;
- UI projections for living actors, missions, information scope and consequences;
- no mass final hull/technology roster.

### Stage 22 owns

- core-pair roster/migration review and production-complete packages for **Империя + Индустриальный Союз**;
- production technology/module/material/facility catalog;
- hull, fit, station and fleet-composition breadth for the core pair and shared civilian ecosystem;
- full game-wide NPC/mission/location breadth needed for alpha without requiring post-core sovereign packages;
- alpha art/icon/VFX/audio set sufficient for shipped content;
- balance, progression, long-run content and fingerprint freeze;
- explicit disposition of every provisional Stage-17.5/19 definition;
- explicit disposition of legacy generated-world faction IDs/roles.

### Stage 23 owns

- final replacement of the finite remaining prototype asset list;
- UI/audio/VFX consistency, accessibility and localization closure;
- onboarding/editorial polish;
- content-reference, package, migration and clean-machine release validation;
- no unbounded new content family after freeze;
- RC completion with the two sovereign core factions, not the five post-core horizon packages.

### Post-core development owns

Each future major faction receives a separate architecture/content package after the main stage:

- Directorate;
- League of Free Systems;
- Frontier Confederation;
- Consortium;
- Nomad Fleet.

Their activation requires a fresh architecture audit and may add reusable mechanics only through normal authority/change policy.

## 24. Cut priority

When time or production capacity is limited, cut in this order:

1. duplicate cosmetic variants with no gameplay/readability value;
2. additional expression/paint variants;
3. extra special-location themes beyond coverage floor;
4. third/later story chain per core faction;
5. niche hull that duplicates an existing role;
6. minor-organization bespoke visuals that can lawfully use common civilian manufacture;
7. optional music breadth;
8. nonessential content prototypes for post-core factions — they should normally not enter core production at all.

Do not cut:

- authority/persistence/knowledge correctness;
- one complete Imperial gold slice;
- one complete Industrial Union contrast package;
- role/counterplay coverage;
- readable object selection/inspection;
- mission reward/target conservation;
- core-faction distinction without color;
- save/content migration and provenance;
- required accessibility/localization path;
- post-core architectural extensibility where preserving it is low-cost and does not expand scope.

## 25. Definition of content-complete RC

Content is RC-complete when every shipped definition is physically/economically valid, reachable
through the live world, visually and verbally readable, bound by stable identity, localized,
licensed, save-compatible and exercised by representative campaigns; **Империя and Индустриальный
Союз are both production-complete sovereign faction packages with intentional and mechanically
non-magical identities**; legacy minor/transnational actors have explicit lawful roles sufficient for
the shipped civilian ecosystem; the Imperial package follows its accepted visual/systemic bibles;
the Industrial Union has an independent reviewed visual/systemic bible; character art follows the
shared grounded hand-painted style; provisional content has an explicit final disposition; supported
faction/content IDs have explicit migration policy; and no shipped mission, asset or text invents
state outside simulation authority.

The five canonical post-core factions — Директорат, Лига Свободных Систем, Пограничная Конфедерация,
Консорциум and Кочевой Флот — remain documented future expansion horizons and are **not required for
content-complete Stage-23 RC**.
