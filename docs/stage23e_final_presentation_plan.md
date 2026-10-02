# Stage 23E — Final Presentation Production Plan

**Status:** CANONICAL FUTURE-STAGE EXECUTION CONTRACT  
**Milestone:** v0.7 Polish / Release Candidate  
**Stage owner:** 23E — final art, VFX, animation and audio replacement  
**Implementation gate:** do not begin 23E implementation before 23B, 23C and 23D are accepted unless a narrowly scoped release-blocking prerequisite must be prepared without changing presentation scope.  
**Primary release feature:** final_presentation / MUST_SHIP  
**Known mandatory release blocker:** GitHub issue #375 — explicit guided-ordnance detonation event seam.

## 1. Purpose

Stage 23E converts the already-authoritative Star Empires simulation into a production-quality release-facing presentation layer.

The stage does not create a new economy, combat model, damage model, movement model, world scale or information model. It replaces remaining prototype/schematic presentation, closes state-driven VFX and audio coverage, finalizes the finite RC art set, and proves that visual/audio output remains a read-only consequence of authoritative state.

The required direction is:

    authoritative state/event
    -> immutable/read-only presentation projection
    -> asset/VFX/animation/audio binding
    -> player-visible presentation

Never:

    art/sound/UI guess
    -> inferred gameplay event
    -> simulation mutation

Stage 23E is complete only when the release-facing product no longer depends on unreviewed schematic fallbacks for MUST_SHIP content and when presentation remains readable, accessible, bounded and state-correct under saturation.

## 2. Authority and source priority

23E implementation must preserve the repository-wide source priority:

1. current repository state and exact accepted content IDs;
2. docs/development_roadmap.md;
3. docs/stage23_release_candidate_roadmap.md;
4. this document;
5. existing simulation and presentation contracts/tests;
6. faction visual bibles and character art authority;
7. historical plans and chat context.

Relevant canonical references include:

- docs/content_production_plan_stage21_23.md;
- docs/tactical_vfx_foundation.md;
- docs/ui_ux_debt_and_polish_contract.md;
- docs/factions/empire_visual_bible.md;
- docs/factions/industrial_union_visual_bible.md;
- docs/characters/character_master_prompt.md;
- docs/stage20_5e_minimum_playable_sprite_pack_v1.md;
- docs/stage22_4_industrial_union_sprite_audit.md;
- docs/empire_sprite_refresh.md;
- docs/characters/core_faction_character_roster.md;
- docs/ui/sector-space-backgrounds.md;
- docs/stage20h_special_locations_v1.md;
- docs/release/rc_feature_manifest_v1.tsv;
- docs/release/rc_known_issues_v1.tsv.

Where this plan conflicts with a later accepted architecture decision, the later explicit decision wins and this file must be updated in the same change.

## 3. Non-negotiable boundaries

### 3.1 Presentation does not own simulation truth

Art, VFX, animation and audio may observe and present authoritative state but may not:

- create damage;
- decide collisions;
- change hit probability;
- add ammunition, propellant, cargo or resources;
- create or remove ships/stations/projectiles;
- change physical world dimensions;
- replace authoritative hardpoints or fitted modules;
- expose hidden targets or omniscient information;
- create a second debris/fragmentation damage model;
- synthesize a missile detonation from disappearance alone;
- create player-only physics/economy shortcuts;
- alter deterministic simulation fingerprints.

### 3.2 Physical scale remains authoritative

World-space size comes from engineering/world content. Presentation may use a bounded screen-space minimum marker at distant zoom only where already allowed by the presentation contract.

A sprite's painted bounds never redefine:

- hull length or width;
- collision;
- weapon range;
- sensor geometry;
- station footprint;
- hangar capacity;
- docking geometry.

### 3.3 Base art contains no transient event state

Base sprites must not bake in:

- exhaust;
- muzzle flashes;
- beams;
- projectiles;
- explosions;
- smoke trails;
- active damage effects;
- external starfields/backgrounds;
- UI labels.

Transient state belongs to dedicated layers or runtime VFX.

### 3.4 Stage 23 scope freeze

23E finalizes the frozen RC surface. It does not open an unbounded new content family.

The two sovereign release factions are:

- Empire;
- Industrial Union.

The five post-core factions remain outside Stage 23 blockers.

## 4. Current baseline and known gaps

The implementation must begin with a fresh repository audit and write the result to a machine-readable gap matrix. The following baseline is known at the time this contract is authored and must be re-verified before implementation.

### 4.1 Empire production ships

Nine production ship families already have detailed base, damage and emissive layers under the Empire production asset tree, with shared idle/thrust engine art.

23E should not replace these bases merely to create new art. Required work is a final integration/readability/state pass:

- alignment of base/damage/emissive;
- engine/thruster binding;
- anchor validation;
- real-scale readability;
- damage-state presentation;
- mip/zoom behavior;
- fit/painted-hardware consistency;
- final provenance and release manifest.

### 4.2 Industrial Union production ships

Nine production base sprites are already reviewed as production-grade and intentionally distinct from Empire construction language.

Known gap: the M22.4 audit explicitly leaves damage, emissive and engine-state overlays for later visual polish. 23E owns that closure unless a newer accepted asset already closes it.

### 4.3 Carrier small craft

Authoritative production content currently has two physical small-craft hulls and six fits:

Empire:
- interceptor;
- defence;
- strike.

Industrial Union:
- interceptor;
- defence;
- strike.

The current presentation resolver uses role markers such as interceptor wedge, defence diamond and strike spear. Close-range release presentation must replace geometric marker-only representation with production small-craft art while preserving the two-hull physical authority.

Preferred production structure:

    faction base hull
    + fit-specific equipment/role overlay where physically justified
    + runtime engine/emissive/damage state

Do not invent six different physical hulls merely to obtain six visual variants.

### 4.4 Stations and infrastructure

The Stage-18 station catalog currently includes eight authoritative infrastructure archetypes:

1. mining outpost;
2. volatile/water depot;
3. refinery complex;
4. industrial station;
5. high-tech manufacturing hub;
6. trade/logistics hub;
7. naval ordnance depot;
8. frontier multipurpose station.

The older minimum playable pack covers only a subset through broad role presentation. 23E must close release-facing station role coverage with production presentation that communicates real installed/function role without granting capacity.

### 4.5 Special locations

Current Stage-20H kinds are:

- energetic anomaly;
- derelict;
- resonant resource phenomenon.

Presentation must respect discovery/knowledge scope. A special visual or audio cue must not reveal a location before the authoritative discovery layer allows it.

### 4.6 Tactical ordnance

A production ordnance sprite catalog already contains kinetic, guided and interceptor art. 23E should audit/readability-bind it rather than reflexively replacing it.

The mandatory gap is event-driven guided-ordnance detonation presentation, tracked by issue #375.

### 4.7 Characters

The release core currently has six Empire and six Industrial Union recurring character portraits/roster cells bound by stable faction profile identity.

Character art follows docs/characters/character_master_prompt.md. 23E may improve weak masters/crops where necessary but must not expand the roster without an RC requirement.

### 4.8 Sector backgrounds

Four deterministic sector-space backgrounds already exist and are presentation-only. 23E owns final grading/readability/provenance review, not arbitrary background-count expansion.

### 4.9 Audio

At contract authoring time there is no production audio asset pack or established Gdx audio presentation layer visible in the release asset tree. 23E therefore plans an audio presentation foundation, but implementation must re-audit the repository first and reuse any accepted subsystem that lands before 23E begins.

## 5. 23E.0 — Final presentation inventory and freeze

Create:

- docs/release/stage23e_asset_gap_matrix.tsv;
- docs/release/stage23e_asset_manifest.tsv or equivalent versioned manifest;
- a human-readable Stage-23E execution/acceptance record during implementation.

Each release-facing asset row should capture at least:

- stable asset ID;
- authoritative content ID or event family;
- domain;
- faction/shared ownership;
- gameplay role;
- current runtime path;
- quality state: FINAL / NEEDS_LAYER / PROTOTYPE / MISSING;
- base/damage/emissive/engine coverage where applicable;
- hardpoint/thruster/radiator/bay/module-anchor coverage;
- VFX event binding;
- audio event binding;
- accessibility variant;
- provenance/license;
- MUST_SHIP / MAY_SHIP;
- replacement/migration alias;
- automated validation status;
- manual review status.

No asset production batch should begin before its rows and acceptance conditions are explicit.

## 6. 23E.1 — Technical art standard

### 6.1 Ship package

Where applicable, a production ship package contains:

    base
    damage
    emissive
    engine idle
    engine thrust
    optional physically justified fit/module overlays
    deterministic anchor metadata
    asset/provenance manifest

Existing accepted conventions remain authoritative unless a single explicit migration changes them globally.

Validation includes:

- genuine alpha;
- transparent corners/padding where required;
- centered/declared pivot;
- known orientation;
- accepted canvas/atlas dimensions;
- bounded visible occupancy;
- damage/emissive masks aligned to base;
- no effect pixels outside allowed base/event region;
- physical aspect-ratio sanity;
- small-scale silhouette readability.

### 6.2 Anchors

Presentation metadata must support the subset actually required by each design:

- main engines;
- maneuvering/RCS thrusters;
- weapon hardpoints;
- VLS/missile launch points;
- PD positions;
- sensor emitters/apertures;
- radiators;
- hangars/launch/recovery points;
- docking points;
- service/repair points.

An anchor is presentation metadata tied to existing physical content; it does not create a new fitted system.

### 6.3 Faction readability

Faction identity must remain readable without relying only on hue.

Empire remains characterized by heavy axial engineering, protected central citadel, repairable long-service construction, restrained graphite/gunmetal, warm ivory, burgundy and rare brass.

Industrial Union remains characterized by standardized repeated sections, modular manufacturing grammar, service/handling interfaces and logistics/yard identity. It must not become a hue-shifted Empire.

## 7. 23E.2 — Guided-ordnance event seam / issue #375

This is a MUST_SHIP release blocker and should be implemented early.

Add one explicit read-only event seam from authoritative guided-ordnance resolution into tactical presentation.

Minimum semantic payload:

- stable deterministic event ID;
- guided body ID;
- source entity ID;
- event kind;
- authoritative world-space event position;
- authoritative tick/order;
- target/interceptor identity when already lawfully available;
- physically derived energy/intensity input where existing authority already provides it.

Minimum event distinction:

- ship impact;
- physical interception/destruction.

A generic destruction kind may be added only if it corresponds to a real authoritative lifecycle event.

Required rules:

- body disappearance alone remains visually quiet;
- no inferred detonation;
- no presentation-authored damage value;
- no new fragmentation physics;
- no historical event replay after save/load;
- deterministic ordering for multiple same-tick events;
- one event produces one presentation trigger;
- enabling/disabling VFX/audio consumers cannot change simulation fingerprint/outcome.

The event seam should be reusable by both VFX and audio consumers.

## 8. 23E.3 — Ship and small-craft art closure

### 8.1 Empire

Perform final release integration of the nine production families:

- base/damage/emissive alignment;
- shared/role-appropriate engine states;
- hardpoint/thruster/radiator/hangar anchors;
- damage-state compositing;
- real gameplay-scale capture;
- saturated-fleet readability;
- source/provenance/license completion;
- no visual module implying unavailable authoritative capability.

Avoid wholesale replacement without a documented defect.

### 8.2 Industrial Union

For all nine production families, close the layer gap:

- damage;
- emissive;
- engine idle/thrust;
- required anchor metadata;
- runtime state integration;
- release validation.

Preserve the accepted base silhouettes unless a specific production defect is demonstrated.

### 8.3 Small craft

Create production art for the two physical hulls, with fit-specific overlays/state where needed for role readability.

Close-range art must visibly distinguish:

- interceptor;
- defence;
- strike;

for both factions without altering physical hull dimensions.

Distant zoom may continue using bounded screen-space semantic markers if necessary for readability, but the marker is not the close-range release sprite.

### 8.4 Markers and strategic LOD

Strategic/distant markers should derive from the same accepted design family and stable identity.

Markers:

- remain color-independent where possible;
- cannot change world scale;
- must not reveal hidden fit/ownership/condition information;
- degrade visual detail before gameplay authority.

## 9. 23E.4 — Stations, infrastructure and world art

Every release-facing Stage-18 station archetype must have a production presentation path.

Art should expose major functional differences through visible modules without inventing capacity.

Required role coverage:

- mining outpost;
- volatile/water depot;
- refinery complex;
- industrial station;
- high-tech manufacturing hub;
- trade/logistics hub;
- naval ordnance depot;
- frontier multipurpose station.

Faction variants may share a role framework but must preserve faction construction grammar.

Recommended distinction:

Empire:
- protected service core;
- layered armor;
- long-service retrofit language;
- restrained command/administrative hierarchy.

Industrial Union:
- repeated processing/assembly units;
- explicit material-handling paths;
- standardized berths/gantries;
- parallel work positions;
- logistics-first readability.

World/special-location closure also includes:

- derelict final presentation;
- energetic anomaly presentation tied to discovered sensor knowledge;
- resonant resource phenomenon presentation tied to the existing occurrence rather than suggesting a new resource deposit;
- final resource-body/background grading where needed.

## 10. 23E.5 — Damage, wreck and persistent visual-state presentation

Presentation should communicate existing damage authority through bounded visual states.

Suggested visual progression:

    healthy
    -> used/light damage
    -> damaged
    -> severe damage
    -> wreck

This is not a new global HP ladder. Mapping must consume existing authoritative damage/subsystem/wreck state.

Possible visual cues:

- localized scorch;
- armor scars;
- darkened/breached regions;
- emissive failure;
- heat hotspots;
- bounded venting;
- residual wreck heat;
- restrained non-authoritative cosmetic debris.

Do not depict a specific destroyed subsystem/compartment unless authoritative state identifies it strongly enough to support that cue.

Existing wreck transition rules remain valid: a wreck first materialized from historical state must not trigger a fresh explosion.

## 11. 23E.6 — Animation and operational state

Star Empires should prefer state-driven layered 2D animation over expensive animation systems that do not improve gameplay readability.

### 11.1 Propulsion

Engine output follows actual projected propulsion/thrust state.

Required behavior:

- zero/off or restrained idle where appropriate;
- low thrust;
- nominal thrust;
- high/full thrust;
- no constant decorative full exhaust.

If continuous authoritative thrust fraction is available, presentation may map it to continuous or quantized visual intensity.

### 11.2 Maneuvering thrusters

RCS/maneuvering effects are emitted only where authoritative motion/attitude state supports the cue.

### 11.3 Weapons

Kinetic:
- muzzle/launch flash;
- physical projectile art;
- optional bounded recoil/thermal presentation where state supports it;
- armor/penetration response.

Beam:
- emitter/source cue;
- physical beam path/dwell;
- endpoint/material response;
- restrained afterglow.

Guided:
- launch;
- motor/exhaust phase where projected;
- flight;
- terminal/detonation only from explicit event.

PD/interceptor:
- compact high-frequency visual language;
- bounded aggregation under saturation;
- explicit intercept event for terminal effect.

### 11.4 Non-combat operations

Production feedback is required for ordinary state transitions where they are visible to the player:

- docking/undocking;
- cargo transfer;
- mining/extraction;
- salvage;
- repair/refit;
- construction;
- carrier launch/recovery;
- jump spool/departure/arrival.

Effects end when the authoritative operation ends.

## 12. 23E.7 — Tactical VFX final pass

Reuse and extend docs/tactical_vfx_foundation.md rather than creating a competing VFX authority.

Required physical-event families:

- thrust/plume;
- kinetic launch/projectile/impact/penetration;
- beam source/path/dwell/material response;
- guided launch/motor/intercept/detonation;
- PD/interceptor;
- decoy/EW/datalink presentation without hidden-info leakage;
- shield coupling/overload/restart;
- subsystem/compartment failure where supported;
- ship destruction;
- residual wreck state.

Current foundation budgets are the baseline until 23F profiling revises them:

- active particles: 512;
- active flashes: 96;
- remembered impact event IDs: 2048;
- simultaneous lingering wreck trackers: 48.

23E must keep bounded degradation under saturation.

Suggested visual language remains restrained:

- engines/beams/shields: cool blue/cyan;
- armor interaction: amber;
- penetration/hot fragments: red-orange;
- destruction core: warm ivory/orange;
- wreck heat: dim red-orange;
- decoys: muted violet.

Avoid neon overload and screen-filling cinematic effects that reduce combat readability.

## 13. 23E.8 — Non-combat VFX

Non-combat feedback is a MUST_SHIP presentation concern because Star Empires is a sandbox, not only a battle viewer.

Mining:
- tool/contact effect;
- bounded particulate cue;
- transfer/progress indication only from real extraction state.

Salvage:
- cutting/work effect;
- finite recovery feedback;
- no fabricated recovered material.

Repair/refit:
- service lights;
- welding/maintenance cues;
- stop when job stops.

Construction:
- incomplete/active work presentation;
- progress state from authoritative construction;
- completion transition.

Docking/cargo:
- approach/contact/clamp or equivalent cue where supported;
- transfer activity;
- undock transition.

Jump:
- spool;
- departure;
- transit presentation;
- exact arrival cue.

Presentation must not make ordinary travel look instantaneous when the authoritative FSM is not.

## 14. 23E.9 — UI art and iconography

23B owns production information architecture and validated actions. 23C owns accessibility/settings/localization closure. 23E provides the final visual asset layer for those accepted surfaces without reopening their interaction model.

Required semantic icon families:

- object classes and ship roles;
- faction/relationship/knowledge state;
- cargo/storage/commodity/industry;
- power/heat/thrust/propellant/ammunition/maintenance;
- sensors/EW/track quality/communications;
- orders/operations/routes/access/blockade;
- claim/occupation/stabilization/control;
- NPC/mission/deadline/reward/reputation;
- warning/error/validation/disabled/queued/in-progress.

Each semantic icon requires:

- stable semantic ID;
- maintainable source/master where practical;
- normal/selected/disabled rules;
- warning/critical usage where relevant;
- monochrome/small-size readability;
- localized tooltip key;
- no dependence on color alone.

Faction accents must not change safety semantics.

## 15. 23E.10 — Character-art finalization

Use the canonical Character Master Prompt and faction visual bibles.

For the existing twelve core-faction recurring characters:

- audit master quality;
- preserve stable identity;
- improve/re-author only where needed;
- retain faction/role readability;
- preserve transparent background;
- produce/rebuild portrait crops from a higher-quality master where practical;
- validate alpha/fringe;
- record prompt/version/provenance;
- prevent face drift across variants.

Do not make expression-sheet breadth a blocker unless the actual 23B/23D dialogue UI consumes those states.

No mandatory animated portraits or lip sync.

## 16. 23E.11 — Audio presentation foundation

Audio is a presentation consumer of observed state/events.

Preferred architecture:

    authoritative/read-only presentation event
    -> semantic AudioCue
    -> AudioDirector
    -> category mixer/priority/voice budget
    -> output

The audio subsystem must not mutate simulation.

Re-audit current code before implementation. If an accepted audio layer exists by then, extend it rather than creating a duplicate.

## 17. Mandatory sound families

### 17.1 UI

Minimum semantic families:

- focus/hover where useful;
- confirm;
- cancel/back;
- command accepted;
- command rejected;
- warning;
- critical warning;
- notification;
- mission update;
- invalid/insufficient-resource action.

### 17.2 Ship/system feedback

- engine/machinery state;
- acceleration/load transition where useful;
- low propellant;
- thermal warning;
- power failure;
- damage alarm;
- critical hull/system condition.

Space-combat/ship audio is an interface representation of state, not a claim that vacuum transmits sound.

### 17.3 Combat

Distinct families for:

- kinetic fire;
- beam fire;
- guided launch;
- PD/interceptor fire;
- shield hit;
- armor impact;
- penetration;
- interceptor kill;
- guided detonation;
- subsystem failure;
- ship destruction.

### 17.4 Operations

- docking;
- undocking;
- cargo transfer;
- mining;
- salvage;
- repair;
- refit;
- construction;
- carrier launch;
- carrier recovery;
- jump spool;
- departure;
- arrival.

### 17.5 Communications/events

- incoming contact;
- mission update;
- diplomatic message;
- distress/security alert;
- major objective/state transition.

## 18. Ambient sound

Ambience is contextual and subordinate to actionable information.

Required environment families should be driven only by known/active context:

Deep/system space:
- restrained ship/system hum;
- sparse instruments/comms.

Trade/logistics:
- distant cargo machinery;
- berth/transfer activity;
- bounded communication traffic.

Industrial/refinery:
- machinery;
- pumps/compressors;
- heavy transfer equipment.

Shipyard:
- fabrication/service activity;
- clamps/gantries/tools.

Military base:
- controlled comm traffic;
- readiness/service machinery.

Special locations:
- only after discovery/knowledge authority permits presentation.

Ambience may never reveal an undiscovered anomaly, hidden enemy or unknown event.

## 19. Faction sonic identity

Faction sound language must communicate identity without creating gameplay advantage.

Empire:
- heavy;
- precise;
- naval/institutional;
- durable mechanical transients;
- restrained ceremonial undertone;
- quiet prestige, not literal historical pastiche.

Industrial Union:
- modular;
- technical;
- industrial;
- repeatable;
- efficient mechanical/percussive patterns;
- standardized machine-floor rhythm;
- no Soviet-retro caricature.

Different timbre cannot alter reaction timing or command latency.

## 20. Music scope

Optional music breadth remains MAY_SHIP and must not delay mandatory cue hierarchy/state correctness.

Mandatory 23E should at most require restrained identity/event stingers if needed for final presentation, for example:

- Empire identity stinger;
- Industrial Union identity stinger;
- neutral discovery/transition cue;
- major victory/loss/state cue where the accepted UX uses it.

A full adaptive OST is not a 23E blocker unless release governance is explicitly amended.

## 21. Audio mixing and saturation

Mix priority:

    critical warning
    > direct player-ship feedback
    > selected/targeted object
    > important tactical event
    > nearby combat
    > background combat
    > ambience/music

Required controls:

- category volume;
- distance/importance attenuation where appropriate;
- event cooldown/de-duplication;
- per-family voice caps;
- global voice budget;
- bounded aggregation for simultaneous similar events;
- ambience ducking under critical warnings;
- no loss of authoritative gameplay events, only presentation aggregation.

Twenty simultaneous kinetic events do not require twenty identical full-volume sounds.

## 22. Accessibility presentation profiles

23C owns settings/accessibility contract; 23E consumes it.

At minimum support the settings accepted by 23C for:

Standard:
- full approved presentation.

Reduced VFX:
- fewer secondary particles;
- lower glow/flash intensity;
- fewer secondary detonations;
- preserved semantic cues.

Reduced motion:
- reduced nonessential transitions/motion;
- no mandatory screen shake;
- preserved state readability.

Audio accessibility:
- independent UI/combat/ambience/music volume where supported;
- category mute;
- visual equivalent for critical warning cues.

Reduced presentation may remove decorative detail but may not remove the only indicator of a critical state.

## 23. Saturation and declutter policy

When presentation budgets are pressured, preserve in priority order:

- player/selected ship;
- critical warnings;
- target/track state allowed by information authority;
- lethal/major hit;
- guided/interceptor events;
- destruction;
- mission/operation state.

Degrade first:

- secondary sparks;
- minor smoke/plasma;
- distant engine glow;
- repeated background impacts;
- cosmetic debris;
- duplicate background audio.

Presentation detail degrades before gameplay/simulation authority.

## 24. Provenance and licensing

Every shipped art/audio asset must have traceable provenance.

Generated/created art manifest should include where practical:

- stable asset ID;
- content/event binding;
- creation date;
- source/generation workflow;
- canonical prompt/visual-bible version;
- post-processing;
- author/reviewer;
- provenance/license status;
- checksum;
- runtime path;
- replacement alias.

Audio manifest should include:

- original/generated/licensed source;
- license;
- modifications;
- duration;
- sample rate/channels;
- loop points when used;
- loudness/mix metadata where applicable;
- checksum;
- runtime path.

Unknown-origin release files are blockers.

## 25. Automated acceptance

CI/content validation should fail on relevant release defects.

### 25.1 Art

- unresolved/missing asset ID;
- missing texture;
- invalid dimensions;
- missing genuine alpha where required;
- opaque invalid corners/padding;
- wrong/unknown orientation;
- invalid pivot;
- overlay escaping allowed base region;
- missing required damage/emissive/engine layer;
- invalid anchor metadata;
- duplicate asset ID;
- missing provenance/license;
- unresolved release-facing schematic fallback.

### 25.2 VFX

- one authoritative event causing multiple unintended triggers;
- missing deterministic ordering;
- detonation inferred from disappearance;
- historical transient replay after save/load/materialization;
- unbounded particle/flash memory;
- reduced-VFX semantic loss;
- hidden-information leak.

### 25.3 Audio

- unresolved cue ID;
- orphan mandatory audio;
- audio-driven simulation mutation;
- wrong semantic family for event;
- sound emitted with no observed/allowed event;
- replay after save/load;
- voice-budget/priority failure under saturation;
- crash on absent optional MAY_SHIP audio.

### 25.4 Simulation-invariance gate

For representative deterministic scenarios:

    same seed + same commands + presentation enabled
    versus
    same seed + same commands + presentation disabled

must produce identical authoritative final outcome/state fingerprint.

## 26. Manual deterministic capture matrix

Archive reproducible visual/audio evidence for at least:

| Scenario | Required observation |
| --- | --- |
| 1v1 tactical | base readability and event correctness |
| 4v4 | multiple weapon families |
| 8v8 | damage/destruction hierarchy |
| 16v16 saturated | declutter and voice budgets |
| guided ship impact | launch/flight/explicit detonation |
| guided interception | issue #375 explicit intercept event |
| carrier engagement | production small-craft presentation |
| damaged capital ship | damage/emissive/wreck state |
| mining | operation feedback |
| salvage | finite recovery feedback |
| docking | docking state |
| cargo transfer | transfer feedback |
| repair/refit | work-state presentation |
| construction | progress/completion presentation |
| jump | spool/departure/arrival |
| station cluster | station-role readability |
| Empire vs Union | grayscale silhouette/faction distinction |
| Reduced VFX | accessibility semantics preserved |
| audio saturation | priority/ducking/voice cap |
| save/load materialization | no historical transient replay |

Captures must use actual gameplay size as well as selected zoomed inspection. Concept-scale beauty shots alone are not acceptance evidence.

## 27. Handoff to Stage 23F

23E establishes bounded presentation budgets and functional degradation policy.

23F owns measured profiler-driven optimization and may revise numerical budgets based on final content density/hardware evidence, provided:

- semantic presentation remains correct;
- no optimization changes authoritative outcome;
- presentation degrades before simulation;
- any revised budget is documented and tested.

23E must not pre-claim 23F performance acceptance.

## 28. Definition of Done

Stage 23E is COMPLETE only when all mandatory rows below are closed:

- [ ] final asset gap matrix is complete and every MUST_SHIP row has disposition;
- [ ] no release-facing MUST_SHIP object uses an unreviewed schematic fallback;
- [ ] all release ship roles have production presentation;
- [ ] Empire production ship package passes final state/anchor/readability review;
- [ ] Industrial Union has required final damage/emissive/engine-state coverage;
- [ ] carrier small craft have production close-range art for the two physical hulls and six fit roles;
- [ ] every release station archetype has production presentation;
- [ ] all shipped special-location kinds have knowledge-correct production presentation;
- [ ] issue #375 is closed through an explicit deterministic guided-ordnance event seam;
- [ ] kinetic/beam/guided/PD/shield/damage/destruction VFX are state/event driven;
- [ ] non-combat mining/salvage/docking/repair/construction/jump feedback is integrated where player-visible;
- [ ] VFX/accessibility profiles preserve semantic cues;
- [ ] production audio foundation is integrated or an already-accepted equivalent is reused;
- [ ] mandatory UI/ship/combat/operation audio families resolve;
- [ ] ambience cannot leak hidden state;
- [ ] Empire/Industrial Union sonic identity does not create gameplay advantage;
- [ ] dynamic mixing and voice budgets remain readable under saturation;
- [ ] all mandatory art/audio has provenance/license;
- [ ] save/load/first materialization does not replay historical transient presentation;
- [ ] presentation enabled/disabled produces identical authoritative outcomes;
- [ ] deterministic capture matrix is archived and reviewed;
- [ ] automated art/VFX/audio/content-reference validations pass;
- [ ] exact-head repository CI is green at the actual Stage-23E merge gate;
- [ ] Stage-23E completion record and roadmap status are synchronized only after all above criteria pass.

## 29. Explicitly out of scope

Unless release governance is deliberately amended, 23E does not own:

- the five post-core sovereign faction packages;
- new gameplay content families;
- new weapon types created only to justify effects;
- new damage physics;
- gameplay-authoritative debris;
- full adaptive OST;
- large cosmetic variant libraries;
- mandatory expression sheets for all NPCs;
- animated portraits/lip sync;
- cinematic cutscenes;
- mandatory full-screen bloom/post-processing;
- volumetric-space rendering;
- a 3D asset pipeline;
- balance changes disguised as visual polish.

Optional music breadth and extra cinematic VFX remain MAY_SHIP and cannot block mandatory state-correct presentation.

## 30. Recommended implementation order

Execute 23E in this order unless repository evidence at implementation time requires a documented adjustment:

1. 23E.0 — fresh inventory, gap matrix, manifest freeze;
2. 23E.1 — technical art/event/audio contracts and validation harness;
3. 23E.2 — issue #375 explicit guided-ordnance event seam;
4. ship closure — Industrial Union layers + small-craft production art + Empire final integration;
5. station/infrastructure/special-location production art;
6. damage/wreck/runtime animation integration;
7. tactical and non-combat VFX finalization;
8. audio foundation and mandatory cue families;
9. ambience, faction sonic identity, mixing and saturation;
10. UI/icon/character finalization on accepted 23B/23C surfaces;
11. accessibility/declutter integration;
12. automated reference/provenance/state-invariance gates;
13. deterministic capture matrix and final human review;
14. exact-head CI, completion record, merge and post-merge verification.

## 31. Implementation rule for future sessions

When Stage 23E becomes the current roadmap stage, the implementation session must begin by reading this document and creating an internal checklist from Sections 5–30.

No subsection is considered complete merely because assets exist on disk. Completion requires runtime binding, authority correctness, automated validation where possible, actual-size capture/readability evidence, provenance/license and the relevant Definition-of-Done item.

This document is therefore the canonical detailed execution checklist for Stage 23E.
