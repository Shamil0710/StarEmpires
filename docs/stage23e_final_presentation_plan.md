# Stage 23E — Final Presentation Production Plan

**Status:** CANONICAL FUTURE-STAGE EXECUTION CONTRACT  
**Milestone:** v0.7 Polish / Release Candidate  
**Stage owner:** 23E — final art, VFX, animation and audio replacement  
**Implementation gate:** do not begin 23E implementation before 23B, 23C and 23D are accepted unless a narrowly scoped release-blocking prerequisite must be prepared without changing presentation scope.  
**Primary release feature:** final_presentation / MUST_SHIP  
**Known mandatory release blocker:** GitHub issue #375 — explicit guided-ordnance detonation event seam.
**Canonical production asset specification:** [Stage 23E — Production Asset and Animation Specification](stage23e_production_asset_spec.md).

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


### 11.5 Global object animation contract

Every release-facing physical object receives an explicit presentation contract. "Animated" does not
mean that the whole sprite must be redrawn continuously. The preferred stack is:

    static base/albedo
    + emissive/status layer
    + optional local mechanical layer
    + state-specific damage/emergency layer
    + transient event VFX
    + semantic audio cue
    + distant marker/LOD

Use full-frame sprite-sheet animation only when the visible geometry itself changes and a local
layer cannot represent the motion cleanly.

For all animated packages:

- every frame/layer keeps the same declared pivot and physical orientation;
- animation never changes physical dimensions, collision, hardpoints or fitting authority;
- base geometry remains pixel-stable unless an authoritative mechanical state changes it;
- idle animation is asynchronous between objects using a stable phase offset derived from persistent
  object identity so identical stations do not blink in perfect unison;
- deterministic capture mode derives animation phase from simulation/presentation tick + stable
  object ID rather than wall-clock randomness;
- damage/emergency state may suppress ordinary idle loops;
- power loss suppresses non-emergency emissive and motorized presentation where authoritative state
  exposes such a condition;
- a hidden/undiscovered object produces no world-space presentation cue merely because an asset
  exists;
- animation state is reconstructed from current authoritative state after save/load and does not
  replay old transient actions.

Recommended visual cadence, subject to final profiling and accessibility:

- slow navigation/service beacons: roughly 0.3–1.5 Hz;
- scanner/sensor presentation: roughly 1–4 s per sweep/cycle;
- idle machinery that is legitimately running: roughly 2–6 visible state changes per second;
- doors/arms/clamps: event-duration animation, not an endless loop;
- weapon/reaction animation: event-driven, normally faster than idle loops;
- reduced-motion mode may replace mechanical loops with static state poses while preserving
  functional meaning.

### 11.6 Faction motion language

Animation must reinforce faction identity in addition to silhouette and palette.

#### 11.6.1 Empire

Imperial motion language is:

- deliberate;
- ordered;
- hierarchical;
- relatively slow;
- mechanically weighty;
- redundant rather than flashy.

Typical presentation:

- sequential service-light checks rather than rapid decorative chasing lights;
- slow synchronized docking beacon groups;
- protected doors/armor shutters moving with visible mass;
- sensor activity concentrated around command/citadel structures;
- machinery that appears maintained and controlled;
- emergency lighting that is strict and localized.

Avoid:

- frenetic blinking;
- neon strips used as decoration;
- light patterns that resemble entertainment signage;
- ornate ceremonial movement on ordinary operational hardware.

#### 11.6.2 Industrial Union

Industrial Union motion language is:

- modular;
- rhythmic;
- throughput-oriented;
- repeated;
- workshop/yard-like;
- visibly tied to material handling.

Typical presentation:

- repeating status lights across equivalent production modules;
- berth-by-berth work-state indication;
- gantry, clamp and cargo-system motion when active;
- standardized hazard-light sequences;
- processing/service cycles that communicate machine flow.

Avoid:

- aristocratic/ceremonial pacing;
- Imperial central-citadel animation grammar;
- random factory noise with no operational meaning;
- permanently moving cranes or doors when no job exists.

### 11.7 Large ship animation contracts

The following contracts apply to both sovereign factions. Faction visual/motion language changes the
presentation style, not the underlying event semantics.

#### 11.7.1 Corvette

Base package:

- base hull;
- emissive/service layer;
- damage layer;
- main-engine idle/thrust;
- RCS anchors;
- weapon/sensor anchors actually present on the hull.

Idle:

- sparse navigation/service lights;
- restrained sensor status pulse;
- no constant RCS firing;
- no decorative weapon movement.

Active movement:

- main plume follows actual thrust;
- short local RCS bursts only from authoritative maneuver/attitude state;
- high acceleration may increase engine emissive/plume intensity without enlarging the physical hull.

Combat:

- weapon cues are anchored to real hardpoints;
- rapid corvette reactions should remain visually compact;
- damage effects must not obscure the small silhouette.

Audio:

- light/compact propulsion signature;
- short weapon transients;
- minimal machinery layer;
- warning cues prioritized over engine bed.

LOD:

- close: full sprite/lights/thrust;
- medium: preserve engine direction, faction silhouette, damage severity;
- distant: semantic marker + only major thrust/destruction cue.

#### 11.7.2 Frigate / recon-EW frigate

Idle:

- sparse hull/service lights;
- low-intensity sensor/communications activity on real sensor locations;
- no visible EW pulse merely because the ship is an EW class.

Active recon/EW:

- sensor sweep, datalink or EW cue only when an allowed observed state exposes that activity;
- EW visuals may use restrained directional/pulsed overlays but cannot expose hidden target location;
- antenna/array mechanical movement is permitted only if the production art actually contains a
  moving structure and runtime state supports it.

Combat:

- preserve sensor-role readability even during weapons fire;
- effects must not turn the hull into a generic combat glow source.

Audio:

- restrained electronics/comms texture;
- EW cue must be subtle and non-positional if exact hidden target information is not known.

LOD:

- medium/distant presentation prioritizes recon/EW role iconography over minor surface animation.

#### 11.7.3 Destroyer / escort / missile-PD destroyer

Idle:

- protected VLS/launcher status lights;
- PD readiness/service lights at actual mounts;
- sparse sensor activity.

Weapon state:

- VLS door/cover animation only if represented as a visible moving component and tied to an actual
  launch sequence;
- missile launch flash/plume comes from real launch anchors;
- PD animation is event-driven and may aggregate under saturation;
- spinal/axial weapon cues remain aligned with physical firing direction.

Damage:

- localized weapon/launcher disable presentation only where subsystem authority supports it;
- generic damage must not falsely indicate loss of a specific VLS/PD mount.

Audio:

- strong short PD events;
- missile launch family;
- compact kinetic/beam family according to fit;
- launcher machinery may be represented only during actual launch/reload-like state that exists in
  authority.

#### 11.7.4 Cruiser

Idle:

- larger distributed service-light network;
- slow engineering-zone status cycle;
- restrained command/sensor activity around protected core.

Movement:

- multiple engine nozzles/groups may vary intensity only if runtime propulsion data can map them
  lawfully; otherwise use a coherent shared thrust fraction.

Combat:

- multiple independent weapon blocks may produce simultaneous anchored events;
- saturation policy preserves selected/targeted cruiser events before background minor sparks.

Damage:

- damage/emissive failure may be spatially richer than smaller ships;
- severe-damage hotspots remain bounded and tied to accepted damage regions;
- no invented compartment labels from art alone.

Audio:

- deeper machinery bed than escort classes;
- multiple impact voices aggregate by importance;
- critical system alarms duck ordinary machinery.

#### 11.7.5 Battleship

Idle:

- very restrained large-scale motion;
- slow service-light sequences emphasizing huge mass;
- command/citadel status remains visually stable.

Movement:

- engine response should feel heavy through plume growth/decay timing, but this is presentation only;
- no fake acceleration lag in simulation.

Combat:

- large weapon events may use stronger but still bounded flashes;
- simultaneous secondary batteries/PD aggregate without hiding axial/main-battery events;
- shield/armor/penetration hierarchy must remain especially clear.

Damage:

- progressive emissive loss;
- larger localized heat/scorch regions;
- bounded vent/fire presentation if supported;
- destruction may use the richest current destruction VFX budget, still capped.

Audio:

- low-frequency heavy machinery and weapon transients;
- dynamic compression/ducking prevents battleship events from masking critical UI warnings.

LOD:

- silhouette and damage state remain visible at farther distance than small ships due to physical size;
- decorative micro-lights disappear early.

#### 11.7.6 Carrier

Idle:

- hangar/service-zone illumination;
- approach/docking beacons;
- restrained command/sensor lights.

Carrier operations:

- hangar door/shutter animation only when a launch/recovery state exists;
- deck/approach lights change to launch/recovery pattern during the actual operation;
- launch/recovery VFX and audio bind to small-craft events;
- service lights may mark occupied/active bays only from lawful state;
- never show "busy deck" traffic when no craft operation exists.

Combat:

- PD/defensive weapon effects remain separate from carrier operations;
- carrier small craft remain their own authoritative entities.

Damage:

- damaged hangar presentation only where state supports a hangar/bay impairment;
- generic damage cannot visually close a specific bay and imply lost capacity without authority.

Audio:

- launch/recovery machinery;
- berth/traffic cue;
- muted flight-control/handling ambience for selected carrier;
- no continuous "air traffic" loop if operations are idle.

#### 11.7.7 Freight transport

Idle:

- cargo-module/service lights;
- docking readiness indicators;
- almost no decorative animation.

Docking/cargo transfer:

- berth/clamp status lights;
- local cargo handling/transfer cue;
- gantry/arm motion only when a corresponding visible handling device exists;
- cargo flow is represented abstractly unless the simulation exposes individual moved units.

Movement:

- engine/plume follows real thrust;
- loaded vs unloaded visual difference is allowed only if the physical/content model supports a
  corresponding external module/state.

Damage:

- do not show cargo venting or container loss unless such loss/event exists.

Audio:

- machinery/handling emphasis;
- restrained engine family;
- transfer/clamp sounds when actual operations begin/end.

#### 11.7.8 Fleet tanker

Idle:

- manifold/service lights;
- safety beacons;
- restrained thermal/engineering indicators.

Transfer operation:

- docking/transfer link presentation only while an actual propellant/volatile transfer exists;
- hose/boom animation is allowed only if such external hardware is part of the accepted sprite;
- flow indication must not imply quantity beyond the authoritative transfer state;
- emergency/safety presentation may override ordinary idle lights.

Audio:

- pumps/compressors represented as interface ambience during active transfer;
- clamp/valve/state transition cues;
- no looping transfer sound when no transfer is active.

#### 11.7.9 Fleet support / replenishment / repair support

Idle:

- service-module readiness;
- workshop/repair-bay lights;
- no permanently active welding or cranes.

Repair/refit/replenishment:

- local work lights;
- welding/cutting/repair VFX;
- articulated service arms/gantries only during a real operation;
- cargo/ammunition/maintenance transfer presentation matches the actual operation family;
- cease immediately when job pauses/cancels/completes.

Audio:

- service machinery;
- intermittent tool/welding cues;
- transfer family;
- completion/abort transition.

#### 11.7.10 Utility/player craft compatibility role

If this Stage-20.5 role remains release-facing after the 23E.0 inventory:

- promote it to an explicit production hull/role binding or map it to an accepted production hull;
- provide basic emissive, thrust, damage and docking presentation;
- preserve player-selected readability at all camera scales;
- do not let the generic compatibility sprite survive as an unreviewed MUST_SHIP fallback.

#### 11.7.11 Mining/industrial craft compatibility role

If this Stage-20.5 role remains release-facing:

Idle:

- tool-head/service readiness lights;
- industrial work lamps only at low intensity.

Mining:

- contact/tool beam or mechanical work cue at the real interaction location;
- bounded particulate/ejecta cue;
- extraction/progress indication only while actual extraction runs;
- no ore stream or cargo count implied by art alone.

Movement:

- normal propulsion contract.

Audio:

- mining machinery/contact family;
- transfer/storage feedback only when real transfer state exists.

### 11.8 Carrier small-craft animation contracts

Production authority currently consists of two physical faction hulls and six fits. Close-range art
must preserve the two physical hull identities and differentiate fit through lawful equipment/role
presentation.

#### 11.8.1 Empire interceptor

- compact Imperial base hull;
- minimal idle lights;
- high-readability engine plume;
- beam/weapon cue from actual fit;
- no shield visual because the current interceptor fit does not carry one;
- fast, sparse visual language rather than decorative animation.

#### 11.8.2 Empire defence craft

- same Imperial physical hull envelope;
- fit-specific defensive/beam overlay where physically justified;
- shield presentation only because the current defence fit includes shield capability;
- defensive readiness/status cue must not imply global invulnerability;
- interception/escort cues remain event-driven.

#### 11.8.3 Empire strike craft

- same Imperial physical hull envelope;
- kinetic/strike fit overlay;
- no shield presentation if the current fit has none;
- heavier launch/weapon transient than interceptor, still bounded.

#### 11.8.4 Industrial Union interceptor

- Union hull with standardized modular construction language;
- fit-specific beam/weapon cue;
- rhythmic but minimal instrumentation;
- no shield cue for the current interceptor fit.

#### 11.8.5 Industrial Union defence craft

- Union base hull + lawful defence equipment overlay;
- shield state presentation;
- standardized readiness/status lights;
- no decorative production-line animation while in flight.

#### 11.8.6 Industrial Union strike craft

- Union base hull + kinetic strike equipment overlay;
- strong role-readability at close range;
- weapon cue from actual hardpoint;
- no shield cue where not fitted.

Small-craft LOD:

- close: production hull + fit overlay + thrust/weapon/shield state;
- medium: simplified silhouette + engine + major state;
- distant: role marker (INTERCEPTOR / DEFENCE / STRIKE) with faction/selection semantics;
- screen-space marker never alters physical world dimensions.

### 11.9 Station animation contracts

Every current Stage-18 station archetype receives a production presentation path. The same functional
archetype may have Empire and Industrial Union art variants, but its operational animation is driven
by the same authoritative facility/capability state.

#### 11.9.1 Mining outpost

Base visual:

- extraction-oriented structure;
- ore handling/storage interfaces;
- service/docking connection;
- sensor/survey equipment where actually present.

Idle:

- sparse navigation and safety lights;
- slow survey/status cycle;
- stationary extraction hardware unless work is active.

Active extraction:

- mining tool/contact cue at real work location;
- conveyor/handling/gantry motion if visible hardware supports it;
- work lights activate by zone;
- bounded particulate/debris presentation;
- transfer/storage indicators follow actual operation state.

Empire motion language:

- slower protected machinery;
- deliberate sequential bay lights;
- armored service housings.

Union motion language:

- repeated extractor/handling status rhythm;
- more visibly modular gantries;
- throughput-zone lighting.

Audio:

- low industrial bed only while selected/near enough;
- extraction/contact/transfer layers during active work.

#### 11.9.2 Volatile / water depot

Base visual:

- storage tanks/modules;
- protected transfer interfaces;
- safety isolation zones;
- docking/transfer hardware.

Idle:

- low-intensity safety beacons;
- manifold status indicators;
- no visible fluid motion required.

Active transfer:

- clamp/connection state;
- pump/transfer status lighting;
- optional hose/boom mechanical animation only if the sprite contains one;
- transfer progress conveyed by UI/state, not by inventing visible quantity.

Emergency:

- localized warning lighting when a relevant fault state exists;
- no flames/explosions without an authoritative damage event.

Empire:

- armored tank housings, slower lock/unlock sequence.

Union:

- repeated standardized tanks, sequential manifold indicators.

Audio:

- pump/compressor interface layer;
- connection/disconnection cue;
- warning hierarchy.

#### 11.9.3 Refinery complex

Base visual:

- distinct intake, process and output zones;
- thermal/radiator structures where installed;
- service access and cargo interfaces.

Idle:

- low steady process indicators only if the refinery is operational;
- thermal/emissive activity restrained.

Active processing:

- zone-by-zone process lights;
- radiator/thermal presentation only from available state;
- material handling cues at real input/output interfaces;
- no arbitrary smoke, flames or atmospheric exhaust.

Empire:

- processing sections protected inside heavier service shells;
- slower staged process-light sequence.

Union:

- visible repeated processing trains;
- rhythmic parallel module activity.

Audio:

- low machinery/process layer;
- pump/valve/handling accents;
- critical alarms override process ambience.

#### 11.9.4 Industrial station

Base visual:

- production/assembly blocks;
- loading/storage interfaces;
- utility/service spine.

Idle:

- workshop/service readiness;
- restrained work-zone indicators.

Active production:

- active production modules illuminate independently where state permits;
- gantry/crane/assembly motion only on actual work;
- cargo input/output handling cue;
- construction-completion transition if this station performs the relevant authoritative work.

Empire:

- production modules subordinate to protected central administration/service core.

Union:

- this is a signature asset: repeated bays, parallel work cells, visible logistics flow;
- animation should emphasize modular throughput without becoming a decorative conveyor show.

Audio:

- machinery/workshop family;
- intermittent tool impacts;
- transfer/completion cue.

#### 11.9.5 High-tech manufacturing hub

Base visual:

- precision manufacturing blocks;
- denser sensor/communications and clean service geometry;
- controlled material interfaces.

Idle:

- restrained high-frequency instrumentation;
- small cyan/teal status cues;
- fewer large mechanical movements than a heavy industrial yard.

Active production/research-like work:

- localized clean-room/process light changes;
- precision tool/assembly activity where visible;
- no holographic spectacle unrelated to a real process.

Empire:

- high-quality, carefully maintained modules with restrained command accents.

Union:

- standardized precision cells and diagnostic/status cadence.

Audio:

- quieter, higher-detail machinery/electronics layer;
- no loud generic factory roar by default.

#### 11.9.6 Trade / logistics hub

Base visual:

- many berths/docking faces;
- cargo staging/storage;
- traffic-control/sensor structures;
- administration/service core.

Idle:

- berth availability lights;
- navigation beacons;
- sparse traffic-control indicators.

Docking/cargo activity:

- only occupied/active berths animate;
- approach lights change by docking state;
- clamps/doors/gantries move only during real operations;
- cargo handling cue is local to active berth;
- background decorative ships are not baked into the sprite.

Empire:

- orderly, hierarchical berth-light sequencing;
- command core visually dominant but not ornamental.

Union:

- berth-by-berth modular status rhythm;
- strong freight-flow identity.

Audio:

- docking/handling bed;
- traffic/comms ambience only within allowed player information scope;
- critical warning priority preserved.

#### 11.9.7 Naval ordnance depot

Base visual:

- protected magazines/arsenal modules;
- separated handling paths;
- reinforced docking/loading areas;
- military sensor/security structures.

Idle:

- strict safety/status lights;
- very restrained animation;
- red/amber safety cues used semantically, not decoratively.

Active loading/supply:

- loading-arm/gantry motion only during actual ordnance/ammunition service;
- berth warning pattern changes during active handling;
- no visible ammunition count beyond known state;
- no weapon firing just because the station stores weapons.

Empire:

- ideal showcase for slow armored doors, deliberate safety sequencing, citadel-like protected core.

Union:

- standardized magazine modules, repeated safe-handling interfaces, procedural work-light cadence.

Audio:

- clamp/handling machinery;
- safety confirmation/warning cues;
- no random weapon sounds.

#### 11.9.8 Frontier multipurpose station

Base visual:

- mixed but coherent modules;
- visible evidence of multiple installed roles;
- no fake capability that is not installed.

Idle:

- modest navigation/service cycle;
- different modules may be dark/inactive according to state.

Active:

- only the module currently performing work animates;
- docking, transfer, repair, production or extraction reuse their shared operation language;
- avoid turning the station into a permanently busy "everything hub".

Empire:

- retrofit/legacy-module continuity is acceptable and desirable.

Union:

- modular expansion blocks and standardized add-ons should be readable.

Audio:

- compose ambience from active module families rather than one universal loop.

### 11.10 Resource-body animation contracts

The minimum playable pack currently exposes four deterministic resource-body atlas roles. Resource
visuals must not reveal reserve truth, grade or hidden composition beyond the information scope already
available to the player.

#### 11.10.1 Carbonaceous body

- mostly static base silhouette;
- optional extremely slow cosmetic light/rotation phase only if it cannot be confused with physical
  movement authority;
- muted dark material response;
- mining contact VFX only while extraction occurs;
- no sparkling resource-value cue.

#### 11.10.2 Water / ice body

- mostly static base;
- restrained specular/ice glint cycle at close zoom;
- extraction cue may use pale particulate/crystal fragments, bounded;
- no blue glow implying magical/energy resource.

#### 11.10.3 Metallic body

- static physical silhouette;
- occasional restrained metallic highlight at inspection zoom;
- mining impact may use brighter spark/fragment response than carbonaceous material;
- no exaggerated glowing ore veins unless the underlying information model explicitly exposes them.

#### 11.10.4 Mineral / silicate body

- static rock silhouette;
- subtle surface-light variation only;
- dusty/rock-fragment extraction response;
- no color-coded reserve quantity embedded in the asteroid sprite.

For all resource bodies:

- distant mode should favor shape/material class readability only if that classification is known;
- extraction animation attaches to actual mining interaction, not to the body simply existing;
- cosmetic rotation may be disabled entirely if it harms deterministic readability.

### 11.11 Special-location animation contracts

#### 11.11.1 Energetic anomaly

Authority:

- current kind has passive thermal signature and passive classification;
- no resource or salvage value.

Before discovery:

- no world-space cue.

Detected/classified:

- restrained pulse/distortion/field visualization may represent known sensor evidence;
- animation intensity must not encode hidden "value";
- no salvage/resource particles;
- no security implication.

Audio:

- optional low restrained diagnostic/phenomenon cue only after knowledge permits it;
- must not reveal the anomaly from outside allowed information scope.

#### 11.11.2 Escort-hull derelict

Base:

- production-identity wreck/derelict presentation;
- largely static;
- no normal navigation lights by default.

Residual state:

- dim heat/emissive residue only if supported by current wreck presentation policy;
- no fresh explosion when first materialized.

Salvage operation:

- cutting/work lights;
- localized fragment/recovery VFX;
- finite operation feedback;
- no visual recovery amount beyond known state.

Audio:

- salvage tool/structure cue during active work;
- otherwise mostly quiet.

#### 11.11.3 Resonant resource phenomenon

Authority:

- references an existing finite Stage-20E resource occurrence;
- does not create a second deposit.

Before discovery:

- no cue.

After classification/survey:

- restrained resonance/pulse pattern around the existing occurrence;
- visual language must remain distinct from the energetic anomaly;
- no duplicate "resource node" sprite implying extra mass;
- extraction uses the ordinary linked resource-body mining feedback.

Audio:

- subtle resonance/diagnostic cue only after allowed discovery state.

### 11.12 Ordnance and projectile animation contracts

The current production ordnance catalog contains ten release-facing sprites. Their runtime animation is
primarily event/motion/VFX driven; projectile base art should not become a tiny looping cartoon.

#### 11.12.1 Fragmentation shell

- rigid projectile body in flight;
- optional spin highlight only if useful at close inspection;
- no fragment burst until an authoritative fragmentation/detonation event exists;
- disappearance alone is not a burst.

#### 11.12.2 Guided micro-missile

- compact motor plume while authoritative motor/thrust state is active;
- no decorative seeker blinking that leaks target direction;
- terminal flash only from explicit detonation/intercept event.

#### 11.12.3 Guided missile A

- launch transient;
- motor-phase plume;
- coast/no-plume if the authority distinguishes that phase;
- terminal detonation only from explicit event;
- stable event ID prevents duplicate presentation.

#### 11.12.4 Guided rocket A

- shorter/stronger launch-motor presentation than long-range missile where the physical profile
  supports that distinction;
- no invented guidance behavior from sprite wobble;
- terminal event contract identical to guided ordnance rules.

#### 11.12.5 Guided rocket B

- visually distinct production sprite may have a distinct plume shape/brightness;
- event semantics remain the same as its authoritative propulsion/guidance profile;
- do not communicate hidden damage superiority through VFX alone.

#### 11.12.6 Guided torpedo

- heavier/larger guided-ordnance presentation;
- restrained persistent motor trail if actual thrust persists;
- larger terminal VFX may scale from physical/authoritative energy input;
- still bounded and event-driven.

#### 11.12.7 Interceptor missile

- very high-readability short-lived motor cue;
- interception terminal VFX only from explicit physical interception/destruction event;
- no "success" explosion from interceptor disappearance.

#### 11.12.8 Kinetic penetrator A

- rigid high-speed projectile;
- no flame-like exhaust;
- optional restrained streak is a camera/readability effect, not propulsion;
- penetration response belongs to impact event, not projectile loop.

#### 11.12.9 Kinetic penetrator B

- same rules as penetrator A;
- sprite/form may communicate a different physical design but VFX intensity follows authoritative
  event data rather than arbitrary art ranking.

#### 11.12.10 Kinetic shell

- rigid shell;
- optional roll/specular cue at close zoom;
- material-specific impact VFX on authoritative collision;
- no persistent glowing tracer unless presentation policy deliberately uses a bounded visibility aid.

### 11.13 Wreck and destroyed-object animation contracts

Ship and station wrecks are persistent states, not endlessly replaying destruction animations.

On alive -> wreck transition:

- one bounded destruction event;
- primary flash/structural failure presentation;
- deterministic bounded secondary detonations where already allowed;
- cosmetic fragments only;
- transition into persistent wreck sprite/layer.

Persistent wreck:

- mostly static;
- dim residual heat;
- intermittent small electrical/emissive failure only for a bounded post-destruction interval;
- no repeated large explosion loop;
- no functional engine/service animation;
- salvage VFX only during actual salvage.

First materialization/load of an already-existing wreck:

- show only the persistent wreck state;
- never replay the original destruction.

### 11.14 Station/ship animation package layout

Preferred logical asset layout:

    assets/<faction>/<domain>/<stable_role>/
        base.png
        damage.png
        emissive.png
        optional_idle_sheet.png
        optional_mechanical_<operation>.png
        engine_idle.png
        engine_thrust.png
        marker.png
        presentation.json
        provenance.json

Stations generally omit engine layers and may add:

    berth_status.png
    operation_<type>.png
    alert_emissive.png
    mechanical_<door_or_arm>.png

Do not require a full-frame sheet for simple light animation. A single emissive mask + runtime phase
is preferred when it produces the same result with less memory and perfect base alignment.

### 11.15 Per-object presentation state model

Where applicable, runtime binding should map each object into a bounded presentation state set:

    HIDDEN
    DISCOVERED_STATIC
    IDLE
    ACTIVE_OPERATION
    ALERT
    DAMAGED
    CRITICAL
    WRECK

Not every object uses every state. For example:

- resource bodies normally use DISCOVERED_STATIC / ACTIVE_OPERATION;
- anomalies use HIDDEN / DISCOVERED_STATIC;
- ordnance uses flight phases + explicit terminal event rather than station-style states;
- wrecks use WRECK + optional ACTIVE_OPERATION when salvaged.

The presentation layer may simplify or merge states for performance, but it may not invent a state
that contradicts authority.

### 11.16 Object-level acceptance matrix

Each release-facing object/role must have an entry in the Stage-23E gap matrix with explicit answers
to the following:

1. What is the stable authoritative content/role ID?
2. What is the close-range production sprite?
3. What is the distant marker/LOD?
4. Which idle animation, if any, is justified?
5. Which operational animations exist?
6. Which events/states trigger them?
7. Which damage/emergency layers exist?
8. Which VFX families attach to the object?
9. Which audio families attach to the object?
10. What information must never be leaked?
11. What happens when power/operation stops?
12. What happens after save/load/materialization?
13. What is the Reduced VFX/Reduced Motion behavior?
14. What is the provenance/license source?
15. What deterministic capture proves the implementation?

An object is not accepted merely because it has a visually appealing sprite sheet. It is accepted
only when its runtime animation, VFX, audio, LOD and state transitions all agree with the authoritative
object lifecycle.


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
