# Star Empires — content production plan for Stages 21–23

> Статус: **CANONICAL PLANNED CONTENT CONTRACT**.
> Scope: authored data, factions, ships, stations, characters, missions, world locations, UI art,
> VFX, audio, localization and validation required to turn the accepted simulation into an alpha and
> then a release candidate.

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

At the start of Stage 21 the repository contains:

- eight persistent faction identities in the large generated-world profile;
- five legacy economic commodities and a much richer Stage-18 physical ontology;
- provisional Stage-17.5/19 engineering, protection, weapon, ammunition and doctrine packs;
- a minimum Stage-20.5 Imperial sprite pack for utility, freight, mining, two combat roles, three
  station roles, four resource-body regions and a derelict;
- one detailed Imperial visual language;
- a production-oriented master prompt for grounded hand-painted character illustrations;
- responsive command UI and deterministic generated-world runtime.

The principal gaps are:

- the eight faction identities do not yet form eight reviewed political/content packages;
- only the Imperial visual language is defined; recolouring it is not faction differentiation;
- provisional combat test IDs are not automatically canon;
- no production NPC/character roster, mission library or dialogue/event vocabulary exists;
- no release-wide ship/station/location art inventory exists;
- VFX, audio, icons, localization and asset provenance are not governed as one pipeline;
- current five-item catalog names are compatibility-era abstractions and must not bypass the
  Stage-18 ontology during expansion;
- content breadth has no shared cut priority or measurable completion floor.

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

## 4. Canonical faction roster decision

The existing generated large-world profile already provides the least disruptive release roster.
Stage 22.0 must review, rename only through migration, and then accept or explicitly replace it.

### Proposed sovereign major factions

| Stable ID | Current name | Production role |
|---|---|---|
| `faction.imperial_directorate` | Имперский директорат | old hierarchical state; heavy, redundant and serviceable engineering |
| `faction.frontier_union` | Союз пограничных миров | decentralized frontier defense, adaptable/refittable craft |
| `faction.industrial_combine` | Промышленный комбинат | mass production, towing, armor, logistics and industrial leverage |
| `faction.free_ports` | Лига свободных портов | trade access, modular ships, long-range commerce and private security |
| `faction.research_consortium` | Исследовательский консорциум | precision systems, sensors/EW, complex maintenance and scarce components |

### Proposed minor/transnational organizations

| Stable ID | Current name | Production role |
|---|---|---|
| `faction.neutral` | Нейтралы | independent settlements/non-aligned civil authority, not one magic empire |
| `faction.trade_league` | Торговая лига | commercial association, contracts, arbitration and convoy services |
| `faction.miners` | Шахтёры | extraction cooperatives/guilds and resource-frontier interests |

The three legacy actors must be modelled deliberately: if they are organizations rather than
territorial sovereigns, diplomacy and territory UI must say so. They must not accidentally receive
all major-state behavior merely because they occupy a dense runtime slot.

The accepted “Империя” visual bible applies to `faction.imperial_directorate`. Before art breadth,
each other major faction requires an equivalent bible covering visual idea, palette, material
language, ship silhouette, stations, people/rank, heraldry, UI accents, wear, forbidden motifs and
game-scale checklist. Minor organizations may use a narrower identity sheet but not a random recolor.

## 5. What constitutes a complete faction content package

Every major faction package contains:

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
- actual-size comparison sheet against every other faction;
- UI accent usage that preserves shared usability semantics.

### Human and narrative identity

- at least six recurring named NPCs for a major faction;
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
- content-reference, localization, asset, fingerprint and save tests.

Minor organizations may ship a reduced package: identity sheet, doctrine/legal role, three recurring
contacts, four operational ship/station roles, contract vocabulary and full authority validation.

## 6. Production breadth target

These are alpha floors used for planning and cut decisions, not permission to add low-value filler.

| Content family | Stage-22 alpha floor | RC rule |
|---|---:|---|
| reviewed major factions | 5 | all packages complete |
| reviewed minor/transnational organizations | 3 | political role explicit |
| major-faction visual bibles | 5 | no placeholder bible |
| recurring named NPCs | 39 (6×5 + 3×3) | portrait/copy/authority complete |
| generated NPC role archetypes | 40+ | faction, role, knowledge and availability coverage |
| military base hulls | 30 (6×5) | every role/faction niche justified |
| faction civilian/support base hulls | 15 (3×5) | may share lawful modules/components |
| neutral/licensed civilian hulls | 8+ | common market/progression path |
| station exterior roles | 10+ | industrial capability readable |
| faction signature station variants | 15 (3×5) | not required to duplicate every functional role |
| special-location archetypes | 20+ | discovery/salvage/mission hooks validated |
| parametric mission templates | 48+ | all derive from real world state |
| authored faction story chains | 10 (2×5) | no frozen-world outcome scripting |
| public/private event templates | 80+ | information-scope tags required |
| core UI/status icons | 120+ as needed | semantic/readability audit, not count chasing |
| audio event families | 60+ as needed | state-aligned and mix-budgeted |

Base hull count does not include fit-only variants. A variant needs new art only when external
geometry or readable silhouette actually changes. This keeps mechanical breadth from multiplying
asset cost without player value.

## 7. Content production order

### Wave 0 — Governance and inventory

- enumerate every current content ID and runtime reference;
- classify each as `PROTOTYPE`, `CANDIDATE`, `ALPHA` or `RC`;
- for Stage-17.5/19 definitions record `PROMOTE`, `REAUTHOR`, `REPLACE` or `RETIRE`;
- build reverse-reference report: definition → fit/fixture/save/world/art;
- define schemas/manifests before bulk authoring;
- lock units, naming and localization conventions.

### Wave 1 — Imperial gold slice

Use the accepted Imperial visual bible to finish one end-to-end faction package first:

- political and engineering doctrine;
- one complete industrial chain and market access path;
- six military and three civilian/support hulls;
- three station roles;
- six recurring NPCs and core generated roles;
- ten mission templates and two short story chains;
- production UI accents, icons, VFX and audio subset;
- peaceful, crisis, battle, loss, recovery and save/load acceptance.

The gold slice proves pipeline quality and actual cost before multiplying work across factions.

### Wave 2 — Contrast pair

Complete the faction whose engineering and politics contrast most strongly with the Empire. Validate:

- silhouette recognition without color/heraldry;
- different lawful strategic decisions under the same world evidence;
- different fleet solution without magic modifiers;
- different NPC voice and visual hierarchy without breaking common art style;
- cross-faction diplomacy, trade and conflict content.

### Wave 3 — Five-major-faction breadth

- finalize remaining three major visual/systemic packages;
- close fleet/industry role matrix rather than duplicating equal rosters;
- integrate named NPCs, missions, relations and player access;
- run cross-faction comparison/anti-dominance matrix after each package.

### Wave 4 — Minor organizations and civilian world

- clarify legacy neutral/trade/miner political roles;
- build common/licensed civilian hull and station ecosystem;
- add independent contacts, disputes, contracts and access networks;
- ensure peaceful play has equal content density to war play.

### Wave 5 — World locations and campaign variety

- special locations, derelicts, anomalies and infrastructure variants;
- regional names, manufacturers, condition states and service histories;
- authored chains anchored to generated geography and living actors;
- news/event vocabulary and discovery presentation;
- representative-seed distribution and repetition checks.

### Wave 6 — Alpha balance and content freeze

- full economy/logistics/combat/progression soaks;
- remove dead content and universal best choices;
- settle market availability, rarity and replacement cadence;
- freeze stable IDs and alpha fingerprints;
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

## 9. Imperial ship and object rules

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

The same language extends to stations, equipment, interiors, icons and characters. It must not be
copied to other factions with a hue shift.

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

- produce five separate candidate variants for a recurring character;
- select for face distinctiveness, faction identity, role identity and restraint;
- derive UI portrait crop from the accepted master illustration where possible;
- validate transparent alpha and absence of accidental checkerboard/background;
- create expression/state variants only when dialogue UI truly consumes them;
- record prompt/version/provenance and disallow unreviewed face drift across variants.

## 13. NPC roster architecture

Each major faction should cover at least:

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

## 14. Mission and narrative content

### Mission families and alpha floor

| Family | Minimum templates | World-state sources |
|---|---:|---|
| haul/supply/procurement | 8 | shortage, stock target, project, relief, military supply |
| mining/salvage/recovery | 6 | finite occurrence, wreck, missing cargo, repair demand |
| escort/rescue/security | 8 | route risk, convoy, stranded fleet, threat report |
| recon/discovery/intelligence | 6 | unknown system/object, stale intel, target observation |
| combat/coercion | 6 | raid, interception, blockade, bounty, defense operation |
| industry/construction/repair | 6 | real site inputs, yard queue, damaged asset, capability gap |
| diplomacy/access/reputation | 4 | proposal, treaty, crisis, recognition, prisoner/aid exchange |
| faction story-chain steps | 4 reusable structural patterns | living actors plus authored character conflicts |

The table yields at least 48 parametric templates. It is a coverage floor; a template that merely
changes a noun does not count as a different gameplay contract.

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

Each major faction receives two compact chains, ideally 3–5 steps each:

- one internal/economic/institutional conflict;
- one external/diplomatic/security conflict.

Authored characters supply motives and interpretation, while branch outcomes remain grounded in
the live economy, relations, travel, battles and territorial state. If the world already resolved or
destroyed a required object, the chain adapts or closes honestly; it must not respawn a prop.

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
- unexpected semantic fingerprint change.

Human review remains required for silhouette, role readability, faction identity, language quality,
audio mix and actual gameplay value.

## 22. Balance and content-quality acceptance

Content is accepted as a system, using:

- role coverage matrix;
- anti-universal-fit and anti-linear-obsolescence tests;
- cost, availability, maintenance, endurance and replacement comparisons;
- fleet-vs-fleet and saturation scenarios, not DPS only;
- peaceful economy and wartime logistics soaks;
- player progression paths across lawful markets, reputation, salvage and own industry;
- faction decision/outcome diversity over representative seeds;
- content repetition telemetry from long campaigns;
- actual-size visual captures under normal and saturated scenes;
- save/load continuity after content migration.

No arbitrary equal win-rate target is required between asymmetric factions. Each faction must have
credible strengths, costs, counters and recovery paths within the same laws.

## 23. Stage mapping

### Stage 21 owns

- minimum actor/faction vocabulary required to explain interests and decisions;
- NPC identity, role, knowledge, availability and reputation semantics;
- mission templates and two gold-slice character chains needed to prove the living-world loop;
- UI projections for living actors, missions, information scope and consequences;
- no mass final hull/technology roster.

### Stage 22 owns

- faction roster review and all faction content packages;
- production technology/module/material/facility catalog;
- hull, fit, station and fleet-composition breadth;
- full NPC/mission/location breadth and world distribution;
- alpha art/icon/VFX/audio set sufficient for all shipped content;
- balance, progression, long-run content and fingerprint freeze;
- explicit disposition of every provisional Stage-17.5/19 definition.

### Stage 23 owns

- final replacement of the finite remaining prototype asset list;
- UI/audio/VFX consistency, accessibility and localization closure;
- onboarding/editorial polish;
- content-reference, package, migration and clean-machine release validation;
- no unbounded new content family after freeze.

## 24. Cut priority

When time or production capacity is limited, cut in this order:

1. duplicate cosmetic variants with no gameplay/readability value;
2. additional expression/paint variants;
3. extra special-location themes beyond coverage floor;
4. third/later story chain per faction;
5. niche hull that duplicates an existing role;
6. minor-organization bespoke visuals that can lawfully use common civilian manufacture;
7. optional music breadth.

Do not cut:

- authority/persistence/knowledge correctness;
- one complete Imperial gold slice and one contrast faction;
- role/counterplay coverage;
- readable object selection/inspection;
- mission reward/target conservation;
- faction distinction without color;
- save/content migration and provenance;
- required accessibility/localization path.

## 25. Definition of content-complete RC

Content is RC-complete when every shipped definition is physically/economically valid, reachable
through the live world, visually and verbally readable, bound by stable identity, localized,
licensed, save-compatible and exercised by representative campaigns; all five major factions and
three minor organizations have intentional identities; the Imperial package follows its accepted
visual bible; character art follows the shared grounded hand-painted style; provisional content has
an explicit final disposition; and no shipped mission, asset or text invents state outside simulation
authority.
