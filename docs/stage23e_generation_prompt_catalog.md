# Stage 23E — Generation Prompt Catalog

**Status:** CANONICAL GENERATION / AUTHORING CATALOG  
**Parent execution contract:** docs/stage23e_final_presentation_plan.md  
**Technical production spec:** docs/stage23e_production_asset_spec.md  
**Visual-reference freeze:** docs/stage23e_visual_reference_freeze.md  
**Faction authorities:** docs/factions/empire_visual_bible.md and docs/factions/industrial_union_visual_bible.md  
**Character authority:** docs/characters/character_master_prompt.md  
**Scope:** production prompts and authoring briefs for every currently known Stage-23E release-facing visual family.

## 1. Purpose

This catalog closes the gap between:

    "we know what asset is required"
    and
    "we have a production-ready prompt/brief to create it"

Every section is keyed by a stable logical asset ID. Future Stage-23E implementation should use:

    asset gap matrix row
    -> asset_id
    -> this catalog
    -> candidate generation / authoring
    -> review
    -> production-layer preparation
    -> runtime binding

This file does not make generated pixels authoritative. Physics, content identity, dimensions,
hardpoints, fitting, damage, visibility and event timing still come from simulation/content authority.

## 2. Prompt inheritance model

To prevent dozens of drifting copies of the same faction/style instructions, production prompts are
assembled from canonical blocks.

### 2.1 Object prompt formula

For Empire world objects:

    GLOBAL WORLD-ASSET OUTPUT LOCK
    + IMPERIAL FACTION VISUAL DNA
    + relevant Imperial object overlay
    + ASSET-SPECIFIC PROMPT from this document
    + GLOBAL NEGATIVE LOCK
    + ASSET-SPECIFIC NEGATIVES

For Industrial Union world objects:

    GLOBAL WORLD-ASSET OUTPUT LOCK
    + INDUSTRIAL UNION FACTION VISUAL DNA
    + relevant Union object overlay
    + ASSET-SPECIFIC PROMPT from this document
    + GLOBAL NEGATIVE LOCK
    + ASSET-SPECIFIC NEGATIVES

For characters:

    CANONICAL CHARACTER MASTER PROMPT
    + faction character overlay
    + CHARACTER-SPECIFIC PROMPT from this document

For VFX:

    GLOBAL VFX OUTPUT LOCK
    + VFX-SPECIFIC PROMPT

A prompt package is considered complete when all inherited blocks named above are included in the
actual generation request. The catalog entry itself is intentionally concise enough to remain
maintainable but specific enough to determine geometry, role and exclusions.

## 3. Global world-asset output lock

Use verbatim unless an asset entry explicitly overrides it:

\`\`\`text
STAR EMPIRES PRODUCTION WORLD-ASSET OUTPUT LOCK:

Create one production-ready 2D game asset for a grounded hard-science-fiction strategy/sandbox game.

CAMERA AND GEOMETRY:
- strict top-down orthographic view
- no perspective tilt
- no cinematic camera angle
- preserve a strong readable silhouette at actual gameplay scale
- all large visible structures must have a plausible engineering purpose
- no unexplained fantasy geometry
- center the object on its declared canvas
- preserve the production orientation stated by the asset specification

BACKGROUND:
- genuinely transparent background
- clean alpha
- no checkerboard baked into the image
- no stars, planets, nebulae, floor, hangar scene, UI, labels, borders or decorative backdrop
- no external cast shadow implying a scene floor

TRANSIENT-EFFECT RULE:
Do not bake in exhaust, muzzle flashes, projectiles, beams, explosions, smoke trails, debris clouds,
damage fire, welding sparks, mining effects, jump effects or other transient runtime VFX unless this
specific request is explicitly for a VFX layer.

MATERIAL AND READABILITY:
- grounded near-future / interplanetary engineering
- practical materials
- readable large forms before micro-detail
- restrained surface detail
- maintained service wear
- no random greeble noise
- no visual feature that implies gameplay capability not specified in the brief

OUTPUT:
- exactly one object unless the request explicitly asks for an animation sheet
- no sprite-sheet collage for base-object candidate selection
- no text or watermark
\`\`\`

## 4. Global world-asset negative lock

\`\`\`text
GLOBAL NEGATIVE LOCK:

perspective view,
three-quarter view,
isometric view,
cinematic scene,
starfield background,
planet background,
nebula background,
floor,
hangar environment,
UI overlay,
labels,
text,
watermark,
baked checkerboard,
baked exhaust,
baked projectile,
baked beam,
baked explosion,
baked smoke,
baked debris trail,
lens flare,
excessive bloom,
neon cyberpunk,
fantasy wings,
ornamental fins,
meaningless antenna forest,
random greebles,
impossible service geometry,
glossy toy-like plastic,
generic concept-art scene,
unreadable silhouette,
asymmetric clutter without function
\`\`\`

## 5. Global layer prompts

These prompts are reused after a base design has been accepted.

### 5.1 DAMAGE overlay

\`\`\`text
Create a transparent DAMAGE OVERLAY for the supplied accepted production sprite.

CRITICAL:
- preserve exact canvas, pivot, orientation and silhouette alignment
- do not redraw or replace the base object
- transparent everywhere except damage marks
- damage remains inside the accepted object alpha unless a tiny physically plausible exposed edge is
  explicitly allowed
- use localized scorch, ablation, chipped coating, impact pitting, scratches, cracked panels, darkened
  armor and limited exposed internal structure
- do not depict a specific destroyed subsystem unless the authoritative asset brief explicitly allows it
- no active flame, smoke, sparks or debris; those are runtime VFX
- no silhouette-scale structural destruction
- readable at gameplay scale but restrained
\`\`\`

### 5.2 WRECK layer/base

\`\`\`text
Create the persistent WRECK presentation of the supplied accepted production object.

CRITICAL:
- preserve exact canvas, pivot, physical orientation and recognizable family silhouette
- object must be clearly nonfunctional
- retain enough faction and role identity to recognize the source hull/station
- show severe structural failure, darkened/dead service zones, broken armor, limited exposed internals,
  displaced panels and localized thermal discoloration
- no normal navigation lights
- no active engines
- no large flames
- no explosion
- no flying debris
- no transient destruction event baked into the image
- wreck must work as a long-lived persistent world object
\`\`\`

### 5.3 EMISSIVE mask

\`\`\`text
Create an EMISSIVE-ONLY layer aligned exactly to the supplied accepted base sprite.

Only include surfaces that genuinely emit light:
- instrument lights
- navigation/service lamps
- sensor apertures that visibly emit
- hangar/berth lights
- engine interior glow where applicable

Everything else must be fully transparent.
No armor highlights, reflections, painted bright colors, bloom halo, exhaust or background.
Preserve exact canvas and pixel alignment.
\`\`\`

### 5.4 MARKER

\`\`\`text
Create a simplified strategic-map marker derived from the accepted object silhouette.

OUTPUT:
- 128x128 transparent PNG master
- centered
- 80–104 px visible silhouette
- readable at 32x32
- preserve faction/role shape identity without relying only on color
- remove micro-detail
- use one coherent silhouette and a few internal cut lines at most
- no text
- no external glow
- no scene/background
\`\`\`

### 5.5 Mechanical component animation

\`\`\`text
Create a local mechanical animation sheet for the specified component of the supplied accepted object.

CRITICAL:
- animate ONLY the named component
- all unrelated geometry must remain pixel-stable / absent from this local component sheet
- transparent background
- identical local pivot in every frame
- no camera motion
- no perspective shift
- no lighting drift unrelated to the mechanism
- movement must be mechanically plausible
- first/last frame must match the declared closed/deployed state
- no transient VFX such as sparks or smoke unless separately requested
\`\`\`

## 6. Major ship prompts — Empire

For all entries in this section prepend the canonical Imperial Visual DNA and IMPERIAL SPACECRAFT
DESIGN from docs/factions/empire_visual_bible.md.

### 6.1 empire.ship.corvette

**Role:** compact military corvette.

\`\`\`text
ASSET-SPECIFIC PROMPT — IMPERIAL CORVETTE:

Create one compact Imperial military corvette production sprite.

Geometry:
- strict forward/right directional hull
- narrow armored prow
- one clear primary weapon or sensor emphasis
- compact protected central citadel, visibly stronger than nose and stern but proportional to a small hull
- short modular engineering stern
- several readable main-engine nozzles at the left/rear
- distributed small RCS points
- protected compact PD positions
- recessed service access
- no large hangar
- no broad carrier-like wings

The corvette should read as fast and compact while still unmistakably Imperial: durable, repairable,
slightly conservative and armored around the center.

Keep the silhouette narrow, purposeful and immediately distinguishable from frigate/destroyer.
Production canvas and orientation follow the Stage-23E asset specification.
\`\`\`

Specific negatives: oversized citadel, cruiser mass, broad carrier body, giant missile farm.

### 6.2 empire.ship.frigate

\`\`\`text
ASSET-SPECIFIC PROMPT — IMPERIAL RECON / EW FRIGATE:

Create one Imperial reconnaissance / electronic-warfare frigate production sprite.

Geometry:
- longer and slimmer than the corvette
- protected axial prow
- medium protected central citadel
- one dominant but physically protected mission/sensor block
- redundant sensor apertures and communications housings
- practical antenna structures kept short and robust
- distributed RCS
- compact defensive weapon positions
- modular engineering stern
- no giant exposed radar dish
- no fragile fantasy antenna forest

The role should be readable from the specialized sensor/mission section even in grayscale.
It must remain a believable warship, not a satellite covered in random electronics.
\`\`\`

### 6.3 empire.ship.destroyer

\`\`\`text
ASSET-SPECIFIC PROMPT — IMPERIAL ESCORT / MISSILE / PD DESTROYER:

Create one Imperial escort destroyer emphasizing missile and point-defence duty.

Geometry:
- elongated armored prow with clear axial weapon/sensor line
- compact but strong central citadel
- two protected missile/VLS blocks with plausible service access
- multiple compact PD mount positions with rational arcs
- redundant sensor block
- armored engineering stern and engine cluster
- no decorative launcher towers
- VLS must look protected and maintainable

The silhouette should immediately communicate fleet escort: forward combat emphasis, dense defensive
coverage and controlled Imperial engineering.
\`\`\`

### 6.4 empire.ship.cruiser

\`\`\`text
ASSET-SPECIFIC PROMPT — IMPERIAL GENERAL-PURPOSE CRUISER:

Create one Imperial general-purpose cruiser production sprite.

Geometry:
- strong armored prow
- clearly dominant multi-layer central citadel
- several independent combat/mission blocks around the citadel
- multiple protected weapon positions
- redundant sensors
- protected sectional radiators
- extensive maintenance access
- large modular engineering belt/stern
- visible reserve/redundancy without random surface clutter

The cruiser must feel like the canonical Imperial middle-weight capital combatant: durable, adaptable
and capable of surviving damage through protected central systems.
\`\`\`

### 6.5 empire.ship.battleship

\`\`\`text
ASSET-SPECIFIC PROMPT — IMPERIAL BATTLESHIP:

Create one massive Imperial battleship production sprite.

Geometry:
- huge protected axial hull
- heavily armored prow capable of housing/protecting major axial weapon systems
- enormous layered central citadel, the dominant visual mass
- redundant independent combat sections
- protected VLS/secondary weapon blocks
- dense compact PD positions
- protected/recessed radiator zones
- massive modular engineering stern with multiple main-engine nozzles
- visible service access despite scale

The result should feel like a flying fortress through engineering mass and survivability, NOT through
fantasy castle architecture. Restrained heraldic/brass details may appear only in tiny command/state
areas. Preserve a clean gameplay silhouette.
\`\`\`

### 6.6 empire.ship.carrier

\`\`\`text
ASSET-SPECIFIC PROMPT — IMPERIAL FLEET CARRIER:

Create one Imperial fleet carrier production sprite.

Geometry:
- protected command/citadel core
- large readable internal-volume/hangar blocks
- two or more clearly defined launch/recovery interfaces integrated into the hull
- protected service spaces around hangars
- strong PD / defensive sensor coverage
- relatively little heavy offensive armament compared with battleship
- large engineering stern
- hangar geometry must be practical for small-craft traffic
- moving doors/shutters, if present, must have clear mechanical tracks and protected stowage

The carrier must read as a military aviation/spacecraft-support ship through volume and bay geometry,
not through decorative flight-deck wings.
\`\`\`

### 6.7 empire.ship.freight

\`\`\`text
ASSET-SPECIFIC PROMPT — IMPERIAL FREIGHT TRANSPORT:

Create one Imperial bulk/general freight transport production sprite.

Geometry:
- strong central load-bearing spine
- standardized cargo modules attached in a clear serviceable pattern
- protected command/crew core
- practical docking interfaces
- restrained armor around critical systems
- less armor and fewer combat structures than warships
- robust modular engineering stern
- obvious cargo-handling access without deployed cranes

The design should communicate state-regulated long-service logistics: orderly, durable and bureaucratic,
not a warship with boxes glued on.
\`\`\`

### 6.8 empire.ship.tanker

\`\`\`text
ASSET-SPECIFIC PROMPT — IMPERIAL FLEET TANKER:

Create one Imperial fleet tanker production sprite.

Geometry:
- protected central structural spine
- repeated tank/pressure-vessel modules inside armored/service frames
- clear manifold and transfer-interface zones
- protected crew/command block
- safety segmentation between tank sections
- practical docking/refuel points
- modular engine stern
- no exposed decorative pipes crossing the whole silhouette

The tanker must visibly prioritize containment, isolation, maintainability and controlled fleet
replenishment.
\`\`\`

### 6.9 empire.ship.fleet_support

\`\`\`text
ASSET-SPECIFIC PROMPT — IMPERIAL FLEET SUPPORT / REPLENISHMENT SHIP:

Create one Imperial fleet logistics / repair / replenishment support ship.

Geometry:
- protected service/command core
- workshop and stores modules
- stowed service arms / repair gantries integrated inside safe silhouette
- docking/service interfaces on multiple zones
- cargo/replenishment modules
- robust engine block
- limited defensive weapon positions
- no permanently deployed crane arms outside the safe hull envelope

The ship must read as a mobile naval workshop and supply vessel, with Imperial durability and
long-service repair culture.
\`\`\`

## 7. Major ship prompts — Industrial Union

For every entry prepend INDUSTRIAL UNION VISUAL DNA + INDUSTRIAL UNION SPACECRAFT DESIGN.

### 7.1 industrial_union.ship.corvette

\`\`\`text
ASSET-SPECIFIC PROMPT — INDUSTRIAL UNION CORVETTE:

Create one compact Industrial Union escort corvette.

Use:
- two or three large standardized structural modules
- one clear kinetic/weapon cassette
- common standardized reactor/drive/sensor housings
- replaceable armor/service panels
- compact repeatable engine bank
- strong directional silhouette
- minimal unused exterior volume

Make it visibly mass-producible and related to larger Union military hulls.
Do not use an Imperial central citadel.
\`\`\`

### 7.2 industrial_union.ship.frigate

\`\`\`text
ASSET-SPECIFIC PROMPT — INDUSTRIAL UNION FRIGATE:

Create one longer Industrial Union escort/recon frigate.

Use:
- repeated standardized spine sections
- one dominant central mission/sensor module
- one clear weapon cassette
- standardized engine family
- accessible service gaps
- practical sensor/communications housings
- restrained modular armor

Longer and more specialized than the corvette while retaining obvious family commonality.
\`\`\`

### 7.3 industrial_union.ship.destroyer

\`\`\`text
ASSET-SPECIFIC PROMPT — INDUSTRIAL UNION DESTROYER:

Create one forward-heavy Industrial Union missile / escort / PD destroyer.

Use:
- strong forward combat module
- clearly separated weapon and defensive cassettes
- protected standardized launcher blocks
- repeated PD housings
- standardized sensor module
- modular engine bank
- replaceable armor packages
- visible service access between systems

The ship should look like a serially manufactured fleet escort, not a bespoke fortress.
\`\`\`

### 7.4 industrial_union.ship.cruiser

\`\`\`text
ASSET-SPECIFIC PROMPT — INDUSTRIAL UNION CRUISER:

Create one Industrial Union general-purpose cruiser.

Use:
- several repeated independent mission/combat sections
- standardized structural interfaces between sections
- redundant service access
- common weapon/sensor cassette language
- modular propulsion bank
- clear replaceable armor packages
- broad enough hull for multiple systems without creating an Imperial citadel

Make the silhouette communicate scalable modular combat power.
\`\`\`

### 7.5 industrial_union.ship.battleship

\`\`\`text
ASSET-SPECIFIC PROMPT — INDUSTRIAL UNION BATTLESHIP:

Create one massive Industrial Union battleship.

Use:
- repeated heavy combat modules
- multiple standardized weapon/defence cassettes
- redundant parallel service pathways
- common large engine-bank modules
- replaceable heavy armor packages
- no singular aristocratic central citadel
- no unique sculptural centerpiece

It must feel enormous because many standardized heavy systems are combined at scale, demonstrating
industrial capacity and maintainability.
\`\`\`

### 7.6 industrial_union.ship.carrier

\`\`\`text
ASSET-SPECIFIC PROMPT — INDUSTRIAL UNION FLEET CARRIER:

Create one Industrial Union fleet carrier.

Use:
- broad standardized hangar/service blocks
- multiple protected launch/recovery lanes
- modular small-craft service zones
- common defensive/PD cassette family
- standardized engine bank
- visible maintainable interfaces between hangar modules
- no ceremonial command superstructure

The carrier should read as a reproducible fleet-support platform built around throughput and servicing.
\`\`\`

### 7.7 industrial_union.ship.freight

\`\`\`text
ASSET-SPECIFIC PROMPT — INDUSTRIAL UNION FREIGHTER:

Create one signature Industrial Union freight transport.

Use:
- exposed but protected central load spine
- repeated standardized cargo modules
- obvious lifting/handling interfaces
- modular docking structures
- common standardized engine bank
- clean industrial panel rhythm
- minimal unnecessary armor

Freight handling and mass logistics must dominate the silhouette.
This is a signature faction asset, not generic civilian background art.
\`\`\`

### 7.8 industrial_union.ship.tanker

\`\`\`text
ASSET-SPECIFIC PROMPT — INDUSTRIAL UNION TANKER:

Create one Industrial Union tanker.

Use:
- repeated standardized cylindrical or faceted pressure modules
- protective service frame
- clear manifold/service interfaces
- segmented isolation geometry
- standardized docking/refuel structures
- common engine bank

The design must visually communicate a repeatable fleet logistics standard, clean and professional,
not improvised or rusty.
\`\`\`

### 7.9 industrial_union.ship.fleet_support

\`\`\`text
ASSET-SPECIFIC PROMPT — INDUSTRIAL UNION FLEET SUPPORT SHIP:

Create one Industrial Union mobile workshop / repair / replenishment vessel.

Use:
- repeated workshop/service modules
- stowed standardized cranes and repair manipulators
- visible docking/service interfaces
- stores/replenishment blocks
- common modular engine family
- practical access lanes
- no deployed arms outside the safe silhouette in base state

The ship should look like a mobile industrial service node derived from Union yard equipment.
\`\`\`

## 8. Carrier small-craft prompts

All small craft use strict forward/right orthographic production sprites and the 512x256 contract.

### 8.1 empire.small_craft.base

\`\`\`text
Create one compact Imperial carrier small-craft base hull.

Use:
- narrow armored prow
- small protected cockpit/sensor block
- compact central protected systems body
- short rear engine cluster
- minimal protrusions
- clear modular attachment zones for role equipment
- durable naval-service construction

Do not bake in fit-specific weapons or shield hardware beyond common attachment structure.
\`\`\`

### 8.2 empire.small_craft.fit_interceptor

\`\`\`text
Create a transparent FIT OVERLAY aligned to the accepted Imperial small-craft base hull for the
INTERCEPTOR fit.

Add only physically justified interceptor equipment:
- compact beam/emitter hardware at real attachment zones
- light sensor/targeting package
- no shield hardware
- no heavy kinetic strike package
- preserve base silhouette envelope as much as possible
- transparent everywhere except added/changed fit hardware
\`\`\`

### 8.3 empire.small_craft.fit_defence

\`\`\`text
Create a transparent DEFENCE FIT OVERLAY aligned to the accepted Imperial small-craft base hull.

Add:
- beam/defensive weapon hardware at lawful attachment zones
- restrained visible shield-emitter housings because this fit carries shield capability
- defensive sensor package
- no heavy strike package
- preserve physical base-hull envelope
\`\`\`

### 8.4 empire.small_craft.fit_strike

\`\`\`text
Create a transparent STRIKE FIT OVERLAY aligned to the accepted Imperial small-craft base hull.

Add:
- compact kinetic/strike weapon package
- strengthened hardpoint housings where physically plausible
- no shield-emitter package
- preserve base physical hull identity
- visually heavier role than interceptor without becoming a new hull
\`\`\`

### 8.5 industrial_union.small_craft.base

\`\`\`text
Create one Industrial Union carrier small-craft base hull.

Use:
- compact standardized modular fuselage
- clear replaceable forward mission block
- common engine module
- visible but restrained service-panel rhythm
- practical standardized attachment interfaces
- no Imperial citadel language
- no decorative wings
\`\`\`

### 8.6 industrial_union.small_craft.fit_interceptor

\`\`\`text
Create an INTERCEPTOR FIT OVERLAY for the accepted Industrial Union small-craft base.

Add:
- standardized beam weapon cassette
- compact targeting/sensor module
- no shield package
- no heavy strike equipment
- transparent outside fit-specific hardware
\`\`\`

### 8.7 industrial_union.small_craft.fit_defence

\`\`\`text
Create a DEFENCE FIT OVERLAY for the accepted Industrial Union small-craft base.

Add:
- standardized defensive beam/weapon cassette
- visible but compact shield-emitter housings
- defensive control/sensor module
- preserve base hull
\`\`\`

### 8.8 industrial_union.small_craft.fit_strike

\`\`\`text
Create a STRIKE FIT OVERLAY for the accepted Industrial Union small-craft base.

Add:
- standardized kinetic strike cassette
- reinforced attachment/hardpoint modules
- no shield-emitter package
- maintain the same physical base hull silhouette
\`\`\`

## 9. Station prompts — Empire

Prepend Imperial Visual DNA. Stations use the 1024x1024 production contract.

### 9.1 empire.station.mining_outpost

\`\`\`text
Create one Imperial mining outpost.

Required functional zones:
- protected central service/crew core
- extraction/tool interface zone
- ore receiving/handling area
- finite storage modules
- docking/service connection
- survey/sensor equipment
- protected power/engineering modules
- stowed handling arm/extractor mechanism suitable for later local animation

Imperial identity:
- heavy protected service housings
- orderly axial or strongly organized layout
- maintained legacy/retrofit continuity
- graphite/gunmetal + warm ivory with restrained burgundy/brass
- slow, durable machinery rather than exposed factory clutter

The outpost must visibly be an extraction facility but must not imply reserve quantity.
\`\`\`

### 9.2 empire.station.volatile_depot

\`\`\`text
Create one Imperial volatile / water storage depot.

Required:
- multiple protected tank/pressure modules
- physical isolation between storage sections
- armored central control/service core
- clearly readable transfer manifold zone
- docking/refuel points
- protected/stowed transfer boom
- safety beacon positions
- emergency isolation hardware

Emphasize containment, regulation, redundancy and long-service maintenance.
Do not show exposed decorative piping across open space.
\`\`\`

### 9.3 empire.station.refinery_complex

\`\`\`text
Create one Imperial refinery complex.

Required:
- clear intake zone
- multiple process modules
- output/storage zone
- thermal/radiator structures
- docking/cargo handling
- protected central administrative/service core
- protected piping/service trunks
- optional sectional radiator shutters with mechanically plausible tracks

The station should look old, upgraded and heavily maintained: newer process equipment integrated into a
durable older structural framework.
\`\`\`

### 9.4 empire.station.industrial_station

\`\`\`text
Create one Imperial industrial manufacturing station.

Required:
- protected central administration/service core
- several heavy manufacturing/assembly modules
- raw-material receiving
- component storage
- loading/output berths
- stowed gantry and assembly mechanisms
- maintenance access
- controlled work-zone hierarchy

Use robust enclosed production blocks rather than exposed chaotic factory machinery.
\`\`\`

### 9.5 empire.station.high_tech_hub

\`\`\`text
Create one Imperial high-tech manufacturing hub.

Required:
- protected precision-production modules
- dense but organized sensor/communications equipment
- clean controlled service interfaces
- protected power/thermal sections
- command/quality-control core
- docking/material interfaces
- restrained precision rig suitable for local animation

Make it visibly higher precision and better maintained than a heavy refinery while remaining practical
and non-utopian.
\`\`\`

### 9.6 empire.station.trade_logistics_hub

\`\`\`text
Create one Imperial trade / logistics hub.

Required:
- multiple clearly readable berths
- protected central administration/traffic-control core
- cargo staging and warehouse modules
- docking approach structures
- clamp/door mechanisms suitable for animation
- customs/service zones
- traffic/sensor structures
- orderly logistics geometry

The station should communicate bureaucratic organization, hierarchy and controlled throughput.
No visiting ships baked into the sprite.
\`\`\`

### 9.7 empire.station.naval_ordnance_depot

\`\`\`text
Create one Imperial naval ordnance depot / military logistics station.

Required:
- heavily protected central citadel/service core
- separated armored magazine modules
- safe handling corridors
- reinforced military docking/loading berths
- protected sensor/security structures
- visible stowed loading arm
- massive armored magazine doors or shutters with plausible tracks
- strict safety-zone geometry
- clear isolation between magazines
- no weapon firing state

Visual tone:
old institutional naval power, heavy engineering, controlled hierarchy, restrained heraldry, gunmetal,
warm ivory, burgundy, rare brass, muted cyan and service amber.

This should be the flagship station example for Imperial Stage-23E animation.
\`\`\`

### 9.8 empire.station.frontier_multipurpose

\`\`\`text
Create one Imperial frontier multipurpose station.

Required:
- central protected service/crew core
- modular add-on sections from different service generations
- docking
- compact cargo/storage
- limited repair/service module
- limited production/utility module
- sensor/communications structure
- visible retrofit history without chaotic kitbash
- stowed service arm and module door where practical

It must look adaptable and upgraded over decades, while only showing plausible installed roles.
\`\`\`

## 10. Station prompts — Industrial Union

Prepend Union Visual DNA + INDUSTRIAL UNION STATION DESIGN.

### 10.1 industrial_union.station.mining_outpost

\`\`\`text
Create one Industrial Union mining outpost.

Required:
- standardized extractor/service blocks
- repeated ore-handling modules
- clear cargo flow path
- modular storage
- standardized docking interface
- survey/sensor block
- visible stowed extractor and handling arm
- replaceable service panels

Make the layout read as a small industrial machine for extraction throughput, clean and professional.
\`\`\`

### 10.2 industrial_union.station.volatile_depot

\`\`\`text
Create one Industrial Union volatile / water depot.

Required:
- repeated standardized tank modules
- protective service frame
- clear isolation gaps
- repeated manifold interfaces
- standardized docking/refuel ports
- stowed transfer boom
- consistent hazard/service marking zones

The silhouette should be immediately understandable as a storage/transfer facility.
\`\`\`

### 10.3 industrial_union.station.refinery_complex

\`\`\`text
Create one Industrial Union refinery complex.

Required:
- visible repeated process trains
- input handling modules
- parallel processing blocks
- output/storage interfaces
- radiator/thermal modules
- standardized service corridors
- docking/cargo handling
- optional modular radiator shutters

The station is a machine for continuous material flow. Avoid one dominant ceremonial core.
\`\`\`

### 10.4 industrial_union.station.industrial_station

\`\`\`text
Create one Industrial Union industrial manufacturing station / shipyard-like production hub.

Required:
- repeated parallel production bays
- standardized assembly cells
- cargo/component staging
- clear material-handling routes
- multiple service gantries
- modular utility/power blocks
- standardized docking interfaces
- visible stowed gantry and assembly rig suitable for animation

This is a signature Industrial Union asset. The player should immediately read scalable serial
production and organized throughput.
\`\`\`

### 10.5 industrial_union.station.high_tech_hub

\`\`\`text
Create one Industrial Union high-tech manufacturing hub.

Required:
- repeated precision manufacturing cells
- clean diagnostic/service interfaces
- standardized sensor/communications blocks
- controlled material input/output
- compact precision rig
- modular utilities
- no giant holographic spectacle

High technology is expressed through precision, standardization and instrumentation, not neon.
\`\`\`

### 10.6 industrial_union.station.trade_logistics_hub

\`\`\`text
Create one Industrial Union trade / logistics hub.

Required:
- many standardized berths
- repeating dock/clamp geometry
- cargo staging grids
- modular warehouse/storage blocks
- traffic/sensor module
- clear arrival -> handling -> storage -> departure flow
- local berth doors/clamps suitable for animation
- no baked visiting ships

Make freight flow the dominant visual identity.
\`\`\`

### 10.7 industrial_union.station.naval_ordnance_depot

\`\`\`text
Create one Industrial Union naval ordnance depot.

Required:
- standardized armored magazine modules
- repeated safe-handling interfaces
- separated transfer corridors
- military docking/loading points
- standardized security/sensor block
- stowed loading arms
- modular magazine doors
- clear procedural safety zones

It should feel like an efficient industrial military depot, not an Imperial fortress.
\`\`\`

### 10.8 industrial_union.station.frontier_multipurpose

\`\`\`text
Create one Industrial Union frontier multipurpose station.

Required:
- standardized expandable central spine
- several modular capability blocks
- docking/logistics module
- service/repair module
- compact production/utility module
- sensor/comms module
- standardized add-on interfaces
- stowed service arm

It should look intentionally expandable using common Union modules, not improvised scrap.
\`\`\`

## 11. Station animation component prompts

Use the accepted base as reference and prepend the Mechanical Component Animation prompt.

### 11.1 mining_outpost.extractor

\`\`\`text
Animate only the station's accepted extraction mechanism through 8 frames:
stowed/idle -> engage -> working contact position.
Keep the attachment root fixed.
Movement should be slow, heavy and mechanically plausible.
No mining particles or sparks in this sheet.
\`\`\`

### 11.2 mining_outpost.handling_arm

\`\`\`text
Animate only the accepted ore/cargo handling arm through 8 frames:
stowed -> deploy -> transfer-ready pose.
No cargo object is created in the sheet.
\`\`\`

### 11.3 volatile_depot.transfer_boom

\`\`\`text
Animate only the accepted transfer boom through 8 frames:
fully stowed -> unlocked -> extended transfer-ready.
No visible fluid, propellant stream or quantity indicator.
\`\`\`

### 11.4 refinery.radiator_shutter

\`\`\`text
Animate only the accepted sectional radiator/protective shutter through 8 frames:
protected/closed -> partially open -> operational/open.
Do not change radiator size or add thermal glow.
\`\`\`

### 11.5 industrial_station.gantry

\`\`\`text
Animate only the accepted manufacturing gantry through 8 frames as a bounded working cycle.
The gantry moves along its real rail/track and returns to a repeatable active pose.
No sparks, cargo or new workpiece.
\`\`\`

### 11.6 industrial_station.assembly_rig

\`\`\`text
Animate only the accepted assembly rig through 8 frames with a restrained repetitive work cycle.
No large object appears/disappears; this is mechanism motion only.
\`\`\`

### 11.7 high_tech.precision_rig

\`\`\`text
Animate only the accepted precision-manufacturing mechanism through 8 subtle frames.
Small controlled travel only; no hologram, energy beam or magical glow.
\`\`\`

### 11.8 trade_hub.dock_clamp

\`\`\`text
Animate only the accepted docking clamp through 8 frames:
open -> approach-ready -> locked.
No ship included.
\`\`\`

### 11.9 trade_hub.berth_door

\`\`\`text
Animate only the accepted berth/hangar door through 10 frames:
closed -> mechanically opened.
Preserve exact housing geometry and rails.
No ship included.
\`\`\`

### 11.10 ordnance_depot.magazine_door

\`\`\`text
Animate only the accepted armored magazine door through 10 heavy frames:
sealed -> unlocked -> fully open.
The movement must convey mass and safety interlocks without changing the surrounding station.
\`\`\`

### 11.11 ordnance_depot.loading_arm

\`\`\`text
Animate only the accepted ammunition/loading service arm through 8 frames:
stowed -> deployed loading-ready.
No ammunition object, projectile or weapon firing effect.
\`\`\`

### 11.12 frontier.service_arm

\`\`\`text
Animate only the accepted multipurpose service arm through 8 frames:
stowed -> deployed service-ready.
No welding effect in this sheet.
\`\`\`

### 11.13 frontier.module_door

\`\`\`text
Animate only the accepted utility/module door through 8 frames:
closed -> open.
No interior scene beyond physically visible dark service cavity.
\`\`\`

## 12. Resource-body prompts

These prompts do not use faction DNA.

### 12.1 world.resource.carbonaceous

\`\`\`text
Create one grounded carbonaceous asteroid/resource-body game sprite.

Strict top-down/orthographic presentation, transparent background, irregular natural rocky silhouette,
dark charcoal-brown/gray material, chipped edges, shallow craters, fractured carbon-rich-looking
surface texture without glowing ore veins. No artificial structures. No text. No resource-value glow.
Readable at game scale.
\`\`\`

### 12.2 world.resource.water_ice

\`\`\`text
Create one water/ice-rich asteroid or small resource body.

Irregular rocky-ice silhouette, pale gray, muted blue-gray and dirty off-white ice/mineral surfaces,
fractures and embedded darker rocky material. Restrained natural specular character but no glow,
no crystal fantasy aesthetic, no magical blue core.
Transparent background.
\`\`\`

### 12.3 world.resource.metallic

\`\`\`text
Create one metallic asteroid/resource body.

Dense irregular rock/metal silhouette with dark iron-gray, gunmetal, oxidized muted brown-gray regions,
fractures, impact scars and restrained metallic reflectance. No bright treasure-like ore veins, no
gold chunks, no artificial machinery.
Transparent background.
\`\`\`

### 12.4 world.resource.mineral_silicate

\`\`\`text
Create one silicate/mineral asteroid/resource body.

Irregular gray-beige / gray-brown rocky silhouette, layered fractures, chips, craters, rockfall texture
and believable stony material variation. No glowing crystals, no artificial structures, no fantasy ore.
Transparent background.
\`\`\`

## 13. Special-location prompts

### 13.1 world.special.energetic_anomaly

\`\`\`text
Create a transparent 2D VFX-style loop for a detected energetic anomaly in grounded hard sci-fi.

Visual language:
- no solid magical object
- restrained localized distortion/field pattern
- muted thermal/plasma-like gradients
- subtle cyan/amber-white diagnostic-looking energy only where necessary
- irregular but bounded pulse
- center must remain readable over a space background
- no lightning storm filling the screen
- no resource crystals
- no portal architecture
- no text

The effect represents a known sensor phenomenon, not supernatural magic.
\`\`\`

### 13.2 world.special.escort_hull_derelict

\`\`\`text
Create one persistent derelict escort-hull sprite derived from a grounded production escort design.

Requirements:
- recognizable former military escort silhouette
- catastrophic long-term nonfunctional state
- dead engines
- no normal navigation lights
- broken armor and limited exposed internals
- no current explosion
- no large active fire
- no flying debris
- suitable for persistent finite salvage site
- transparent background
\`\`\`

### 13.3 world.special.resonant_resource_phenomenon

\`\`\`text
Create a transparent resonance overlay animation for a surveyed resource occurrence.

Visual language:
- subtle concentric or interference-like localized field response
- bounded around the existing resource body
- muted cyan/teal/ivory diagnostic energy
- clearly distinct from the energetic anomaly
- no second asteroid/resource object
- no extra ore mass
- no magical crystals
- no giant glow halo
\`\`\`

## 14. Ordnance prompts

All ordnance is strict top-down forward/right, transparent, no trail/glow/impact.

### 14.1 world.ordnance.fragmentation_shell

\`\`\`text
Create one tiny fragmentation artillery shell sprite: compact dense metal projectile, practical
manufactured body, small segmentation/casing cues, no fins unless physically justified, no explosion,
no trail, no glow.
\`\`\`

### 14.2 world.ordnance.guided_micro_missile

\`\`\`text
Create one very compact guided micro-missile sprite: small cylindrical/faceted body, compact seeker
nose, tiny control surfaces or thruster ports only if plausible, rear motor nozzle, no plume, no target
indicator.
\`\`\`

### 14.3 world.ordnance.guided_missile_a

\`\`\`text
Create one standard guided missile sprite: practical seeker nose, protected guidance/body section,
motor casing, restrained control/thruster geometry, rear nozzle, mass-produced military construction,
no plume or glow.
\`\`\`

### 14.4 world.ordnance.guided_rocket_a

\`\`\`text
Create one compact guided rocket variant A: simpler robust body than long-range missile, strong motor
section, compact guidance head, minimal control geometry, no plume, transparent background.
\`\`\`

### 14.5 world.ordnance.guided_rocket_b

\`\`\`text
Create one visually distinct guided rocket variant B within the same engineering family: different
body proportions / motor casing / control arrangement but equally practical, no implication of magical
superiority, no plume.
\`\`\`

### 14.6 world.ordnance.guided_torpedo

\`\`\`text
Create one heavy guided space torpedo sprite: larger elongated body, reinforced guidance/warhead
section, substantial propulsion casing, robust maneuvering/thruster ports, heavy military engineering,
no plume, no explosion.
\`\`\`

### 14.7 world.ordnance.interceptor_missile

\`\`\`text
Create one compact high-acceleration interceptor missile sprite: narrow body, large propulsion fraction,
compact seeker/intercept package, practical control/thruster ports, no plume, no explosion.
\`\`\`

### 14.8 world.ordnance.kinetic_penetrator_a

\`\`\`text
Create one dense kinetic penetrator variant A: long compact high-density dart/projectile, reinforced
nose, minimal casing, no engine, no fins unless stabilization hardware is physically justified, no glow.
\`\`\`

### 14.9 world.ordnance.kinetic_penetrator_b

\`\`\`text
Create one kinetic penetrator variant B: distinct practical penetrator geometry from variant A while
remaining a dense inert projectile, no engine, no flame, no glowing tracer.
\`\`\`

### 14.10 world.ordnance.kinetic_shell

\`\`\`text
Create one conventional kinetic shell sprite: compact armored projectile body, practical nose/casing,
no engine, no flame, no tracer glow, no impact effect.
\`\`\`

## 15. Global VFX output lock

\`\`\`text
STAR EMPIRES VFX OUTPUT LOCK:

Create a production 2D transparent VFX sprite sheet for a grounded hard-science-fiction strategy game.

- transparent background
- exact frame grid requested
- identical frame cell dimensions
- effect centered on a stable local origin
- no camera motion
- no object/ship body unless explicitly required
- restrained physically suggestive light/particle behavior
- strong readability at gameplay scale
- limited bloom contained inside alpha
- no cinematic full-screen explosion
- no fantasy magic symbols
- no text
- first and last frames must support clean one-shot/loop behavior as specified
\`\`\`

## 16. Shared VFX prompts

### 16.1 vfx.engine.idle

\`\`\`text
8-frame engine-idle plume strip. Short low-intensity blue-white/cyan ion/plasma-like plume, narrow core,
soft restrained fringe, very little turbulence, transparent, no nozzle/body. Stable anchor at plume root.
\`\`\`

### 16.2 vfx.engine.thrust

\`\`\`text
8-frame main-engine thrust strip. Longer brighter blue-white/cyan plume than idle, coherent high-energy
core, restrained turbulent edge, no giant halo, no smoke, no flame-orange chemical aesthetic unless
future propulsion authority requires it.
\`\`\`

### 16.3 vfx.rcs.burst

\`\`\`text
8-frame compact maneuvering-thruster burst. Very short directional blue-white/cyan impulse, fast rise
and decay, minimal halo, no smoke.
\`\`\`

### 16.4 vfx.kinetic.muzzle

\`\`\`text
6-frame kinetic weapon muzzle event. Compact bright ivory-white core with restrained amber hot-gas /
electromagnetic discharge accents, very short duration, no long flame plume, no projectile body.
\`\`\`

### 16.5 vfx.kinetic.armor_impact

\`\`\`text
8-frame armor impact. Local amber/yellow-white contact flash, short directional sparks and tiny hot
fragments, rapid decay, no explosion cloud, no shield arc.
\`\`\`

### 16.6 vfx.kinetic.penetration

\`\`\`text
8-frame penetration event. Smaller intense red-orange/ivory contact core, directional hot fragments
passing through/away from impact, sharper than armor impact, bounded and brief.
\`\`\`

### 16.7 vfx.beam.source

\`\`\`text
6-frame beam-emitter source flash. Tight cool blue/cyan-white aperture flare at a fixed emitter anchor,
minimal bloom, rapid rise/fall, no beam line in the sheet.
\`\`\`

### 16.8 vfx.beam.endpoint

\`\`\`text
8-frame beam material-response endpoint. Small intense blue-white contact transitioning to warm
amber/red material heating, localized vapor/spark particles, no large explosion.
\`\`\`

### 16.9 vfx.shield.contact

\`\`\`text
8-frame shield-contact effect. Localized curved cyan/blue energy arc around impact point, small bright
contact core, fade without drawing a full permanent bubble.
\`\`\`

### 16.10 vfx.shield.overload

\`\`\`text
12-frame shield-overload effect. Larger but bounded broken cyan/blue arc network, brief unstable
flicker/collapse, no magical lightning filling the screen.
\`\`\`

### 16.11 vfx.guided.launch

\`\`\`text
8-frame guided-ordnance launch transient. Compact motor ignition at fixed launcher origin: bright
blue-white core and brief plume growth, no missile body.
\`\`\`

### 16.12 vfx.guided.detonation

\`\`\`text
12-frame guided-ordnance detonation. Physically suggestive compact high-energy flash: ivory-white core,
orange/red expanding hot fragment/plasma envelope, bounded fragments, rapid decay, transparent,
no mushroom cloud, no debris damage implication beyond presentation.
\`\`\`

### 16.13 vfx.guided.intercept

\`\`\`text
10-frame physical interception/destruction flash. Smaller sharper than a major ship-impact detonation:
white-orange contact flash, a few divergent hot fragments, rapid extinguish.
\`\`\`

### 16.14 vfx.decoy.activation

\`\`\`text
8-frame decoy activation effect. Muted violet/cool-lilac electronic/thermal presentation pulse,
compact and deliberately lower intensity than weapons, no target-direction arrow, no hidden-target cue.
\`\`\`

### 16.15 vfx.subsystem.failure

\`\`\`text
8-frame localized subsystem-failure presentation: short red-orange electrical/thermal burst, a few
sparks/hot particles, fast decay, no major hull explosion.
\`\`\`

### 16.16 vfx.destruction.standard

\`\`\`text
16-frame standard ship destruction transition. Bounded warm ivory/orange primary flash, limited
structural hot fragments, 2–3 visually secondary pockets integrated into the one-second effect,
no huge cinematic fireball, transparent.
\`\`\`

### 16.17 vfx.destruction.capital

\`\`\`text
16-frame capital-ship destruction transition. Larger layered ivory/orange flash, multiple bounded
secondary hot zones, restrained fragments, readable large-scale failure without filling the screen.
No persistent wreck in the sheet.
\`\`\`

### 16.18 vfx.wreck.hotspot

\`\`\`text
8-frame residual wreck hotspot loop. Dim red-orange localized heat/flicker, very low intensity,
no flame plume, no explosion, suitable only for a bounded post-destruction interval.
\`\`\`

### 16.19 vfx.mining.contact

\`\`\`text
8-frame mining contact effect. Local tool/material interaction: restrained white/amber contact,
rock/dust particles appropriate to hard vacuum, compact and directional, no giant laser spectacle.
\`\`\`

### 16.20 vfx.salvage.work

\`\`\`text
8-frame salvage cutting/work effect. Small white/amber cutting contact, short sparks/hot fragments,
controlled tool-scale intensity, no recovered cargo object.
\`\`\`

### 16.21 vfx.repair.weld

\`\`\`text
8-frame repair/welding effect. Tiny intense white-blue welding arc with restrained amber sparks,
localized and readable, no smoke cloud.
\`\`\`

### 16.22 vfx.construction.work

\`\`\`text
8-frame construction-work effect. Small localized tool/assembly activity: white/amber weld/contact
points and restrained particles, suitable for active yard work.
\`\`\`

### 16.23 vfx.cargo.transfer

\`\`\`text
8-frame abstract cargo-transfer status effect. Restrained service-light / transfer pulse at a docking
interface, no material teleport beam, no visible cargo quantity.
\`\`\`

### 16.24 vfx.docking.contact

\`\`\`text
8-frame docking-contact cue. Very small mechanical-contact/status flash at docking point, muted
service cyan/amber, no explosion and no ship body.
\`\`\`

### 16.25 vfx.jump.spool

\`\`\`text
16-frame jump-spool effect. Grounded localized field/energy buildup centered on the ship position:
restrained cool-white/cyan distortion, increasing coherence rather than giant magic portal, transparent.
\`\`\`

### 16.26 vfx.jump.departure

\`\`\`text
12-frame jump-departure transition. Rapid localized spatial/energy collapse around the departure
position, cool-white/cyan with restrained residual streak/distortion, no permanent portal.
\`\`\`

### 16.27 vfx.jump.arrival

\`\`\`text
12-frame jump-arrival transition. Localized spatial/energy expansion settling into a sharp arrival
flash, cool-white/cyan, bounded, no permanent portal architecture.
\`\`\`

## 17. Character prompt packages

The current roster source supports **names and roles** but does not define canonical age, hair, eye
color, skin tone, face shape or other individual appearance data for most characters in text.

Therefore these prompts must **preserve the accepted existing roster identity** rather than inventing
new appearance. When regenerating a higher-quality master, provide the existing accepted portrait as
visual reference if the generation workflow supports it.

All character prompts prepend the canonical Character Master Prompt and the relevant faction
character overlay.

### 17.1 character.empire.viktor_arden

\`\`\`text
CHARACTER-SPECIFIC PROMPT:
Name: Admiral Viktor Arden.
Faction: Empire.
Role: higher military command / admiral.

Preserve the currently accepted roster identity and recognizable facial appearance.
Do not invent a different age, hair color, eye color, ethnicity or facial structure without an
explicit later character-authority decision.

Role presentation:
- highest naval command status
- excellent precise tailoring
- pressure-compatible dark inner uniform
- warm ivory-gray command outer layer
- restrained burgundy detail
- small old-brass rank insignia
- almost no tools or personal clutter
- strict controlled posture and expression
- authority through material quality and restraint, not decoration
\`\`\`

### 17.2 character.empire.elizaveta_kern

\`\`\`text
Name: Elizaveta Kern.
Faction: Empire.
Role: imperial diplomat / plenipotentiary.

Preserve accepted roster facial identity; do not invent unrecorded biological appearance.

Role presentation:
- senior state diplomatic authority
- formal but pressure-compatible practical clothing
- very clean Imperial tailoring
- warm ivory / midnight navy base
- restrained burgundy and rare brass status details
- small secure communications/document accessory at most
- calm, controlled, observant demeanor
- no ceremonial gown, historical costume or excessive jewelry
\`\`\`

### 17.3 character.empire.marek_volin

\`\`\`text
Name: Captain Marek Volin.
Faction: Empire.
Role: combat ship / escort-group commander.

Preserve accepted roster identity.

Role presentation:
- senior operational naval officer below admiral level
- dark pressure-compatible uniform
- structured command jacket
- practical rank tabs and service identification
- restrained burgundy piping
- minimal equipment
- disciplined tired/alert command demeanor
- believable shipboard clothing rather than parade uniform
\`\`\`

### 17.4 character.empire.anton_velsky

\`\`\`text
Name: Anton Velsky.
Faction: Empire.
Role: junior naval engineer.

Preserve accepted roster identity.

Role presentation:
- practical engineering uniform
- dark navy / blue-gray technical layers
- dirty/worn ivory protective elements
- repair patches
- organized tools or diagnostic device
- small burgundy faction marking
- lower-status brass/rank detail minimal
- used but regulated and maintained clothing
- competent, tired technical demeanor
\`\`\`

### 17.5 character.empire.sofia_radan

\`\`\`text
Name: Dr. Sofia Radan.
Faction: Empire.
Role: military / ship medic.

Preserve accepted roster identity.

Role presentation:
- practical shipboard medical uniform integrated with Imperial service clothing
- clean functional ivory/gray medical protective layer over dark pressure-compatible base
- restrained faction burgundy
- small medical role identifiers
- compact diagnostic/medical kit, one practical prop maximum
- no fantasy medic armor, no giant red-cross costume language
- calm focused professional demeanor
\`\`\`

### 17.6 character.empire.tomas_reichel

\`\`\`text
Name: Tomas Reichel.
Faction: Empire.
Role: state logistics / procurement controller.

Preserve accepted roster identity.

Role presentation:
- administrative/logistics authority
- precise practical service clothing
- midnight/navy and warm gray/ivory
- restrained rank/status marks
- compact data/manifest device as practical prop
- less martial than combat officers but clearly inside Imperial hierarchy
- orderly, analytical presentation
\`\`\`

### 17.7 character.union.hana_markovic

\`\`\`text
Name: Commander Hana Markovic.
Faction: Industrial Union.
Role: fleet operational command.

Preserve accepted roster identity; do not invent unrecorded appearance.

Role presentation:
- senior standardized military utility uniform
- graphite/slate base
- structured pressure-compatible construction
- geometric rank/function markings
- limited oxide-ochre / technical-teal accents
- high-quality practical material
- authority through responsibility and precision, not aristocratic ornament
\`\`\`

### 17.8 character.union.idris_kamal

\`\`\`text
Name: Idris Kamal.
Faction: Industrial Union.
Role: large production-program coordinator.

Preserve accepted roster identity.

Role presentation:
- senior industrial-program authority
- clean structured technical jacket
- standardized qualification/access markings
- graphite/slate/assembly-gray palette
- restrained ochre accents
- compact production/data planning device as optional prop
- professional coordinator, not factory laborer stereotype
\`\`\`

### 17.9 character.union.mira_chen

\`\`\`text
Name: Mira Chen.
Faction: Industrial Union.
Role: senior shipyard engineer.

Preserve accepted roster identity.

Role presentation:
- high-level engineering specialist
- durable but well-maintained technical jacket/coverall
- diagnostic equipment
- precise qualification badges
- tool-compatible construction
- graphite/slate/muted olive with technical teal/ochre accents
- practical shipyard wear
- no post-apocalyptic grime
\`\`\`

### 17.10 character.union.alexey_moreno

\`\`\`text
Name: Alexey Moreno.
Faction: Industrial Union.
Role: strategic cargo-convoy commander.

Preserve accepted roster identity.

Role presentation:
- standardized operational uniform combining fleet and logistics language
- practical pressure-compatible layers
- convoy/traffic role markings
- restrained ochre/teal instrumentation accents
- professional command posture
- no Imperial ceremonial tailoring
\`\`\`

### 17.11 character.union.lina_okafor

\`\`\`text
Name: Lina Okafor.
Faction: Industrial Union.
Role: intersystem logistics dispatcher / coordinator.

Preserve accepted roster identity.

Role presentation:
- professional logistics/traffic technical clothing
- graphite/slate/assembly-gray
- standardized access/qualification markings
- compact communications/data accessory
- practical, organized, high-responsibility civilian/industrial presentation
- no military armor unless later authority explicitly adds it
\`\`\`

### 17.12 character.union.david_stein

\`\`\`text
Name: David Stein.
Faction: Industrial Union.
Role: senior emergency-repair foreman.

Preserve accepted roster identity.

Role presentation:
- durable emergency-repair technical gear
- reinforced work clothing
- organized tool-compatible belt/pockets
- visible repair/use wear but maintained condition
- high qualification markings
- safety ochre/amber accents used functionally
- practical gloves/boots
- senior technical authority without executive ornament
\`\`\`

## 18. Marker prompts by domain

Use the global MARKER prompt plus one role cue:

| Asset family | Required marker cue |
| --- | --- |
| corvette | narrow fast escort wedge/axis |
| frigate | longer sensor/mission silhouette |
| destroyer | forward combat / missile-PD mass |
| cruiser | strong central multi-role mass |
| battleship | massive heavy axial silhouette |
| carrier | broad hangar/volume silhouette |
| freight | cargo module/spine rhythm |
| tanker | repeated tank silhouette |
| fleet support | service/workshop geometry |
| interceptor | narrow fast role cue |
| defence craft | compact shield/escort cue |
| strike craft | heavier forward weapon cue |
| mining outpost | extractor/handling cue |
| volatile depot | tank/storage cue |
| refinery | repeated process/radiator cue |
| industrial station | production/gantry cue |
| high-tech hub | compact precision/sensor cue |
| trade hub | multiple berth cue |
| ordnance depot | protected magazine/military cue |
| frontier station | modular mixed-role cue |
| energetic anomaly | non-solid field symbol |
| derelict | broken escort silhouette |
| resonant phenomenon | resource-body + resonance ring |

## 19. Damage/wreck application matrix

Use the global layer prompts with these object-specific emphases:

| Family | Damage emphasis | Wreck emphasis |
| --- | --- | --- |
| small military ship | impacts/scorch, limited exposed internals | broken drive/armor, recognizable hull |
| capital ship | multiple localized scars/emissive loss | major structural breaks, dead citadel/engineering |
| carrier | hangar/service damage only if authority permits | recognizable bay volume, dead operations |
| freight | structural/cargo-frame damage, no invented cargo loss | collapsed frame/modules, no drifting cargo unless authoritative |
| tanker | containment/armor scars, no leak plume baked in | ruptured/dead tank modules, no active fire |
| support | service-zone damage | disabled arms/workshops, persistent dead hull |
| station | localized module/armor damage | large persistent dead infrastructure, no explosion |
| small craft | minimal readable scars | compact dead hull |

## 20. Candidate-generation rules

For every **new base visual family**:

1. generate exactly five genuinely different candidates;
2. each candidate is a separate image;
3. same canvas/orientation/role/physical envelope;
4. no collage;
5. review silhouette without color;
6. review engineering plausibility;
7. review faction identity;
8. review actual-size readability;
9. choose one;
10. record rejection reasons.

Do not generate five variants for damage masks, emissive masks or animation frames after a base has
been selected.

## 21. Prompt package output record

Every accepted asset should store or reference:

    asset_id
    prompt_catalog_version
    inherited_global_block
    inherited_faction_block
    object_prompt_section
    negative_block
    generation_model/tool
    generation date
    candidate IDs
    selected candidate ID
    rejection reasons
    postprocess steps
    reviewer
    provenance/license

## 22. UI icon prompt dependency

The Stage-23E production spec intentionally does not freeze the exact icon count until 23B/23C are
accepted. Therefore this catalog provides the icon **style prompt**, but not invented final icon IDs.

\`\`\`text
STAR EMPIRES UI ICON STYLE PROMPT:

Create one semantic UI icon for a grounded hard-science-fiction strategy game.

- simple geometric/technical pictogram
- readable at 32x32 and 64x64
- 128x128 transparent master or SVG-like clean source
- monochrome-readable
- no text
- no gradient-dependent meaning
- no faction-specific decorative styling unless the semantic meaning itself is faction identity
- no color-only status meaning
- restrained naval/industrial technical visual language
- clear filled/outlined silhouette
- minimal internal detail
- no glossy app-icon treatment
- no 3D bevel
- no neon cyberpunk
- no decorative circle HUD unless the circle is semantically required
\`\`\`

23E.0 must append the exact per-icon semantic prompts after 23B/23C freezes the UI surface.

## 23. Audio generation briefs

Audio is not image generation, but Stage-23E asset production needs equivalent prompt/brief
traceability. Use these as semantic sound-authoring prompts.

### 23.1 UI family brief

\`\`\`text
Create restrained professional spacecraft/strategy UI sounds: short, precise, non-fatiguing, modern
industrial/naval interface language, no retro computer bleeps, no cyberpunk neon synth clichés.
Confirm is soft-positive, reject is dry/clear, warning is amber-level urgency, critical warning is
immediately distinct and mix-priority safe.
\`\`\`

### 23.2 Empire sonic brief

\`\`\`text
Empire sonic identity: heavy, precise, institutional naval engineering, durable mechanical transients,
controlled machinery, restrained ceremonial undertone, quiet prestige. No literal historical fanfare,
no steampunk gears, no fantasy choir.
\`\`\`

### 23.3 Industrial Union sonic brief

\`\`\`text
Industrial Union sonic identity: modular, technical, industrial, repeatable, efficient machine-floor
rhythm, standardized actuators and handling machinery, professional modern production culture.
No Soviet-retro caricature, no scrapyard clatter, no cyberpunk club synth.
\`\`\`

### 23.4 Combat sonic brief

\`\`\`text
Combat sounds are player-interface representations of authoritative events, not atmospheric vacuum
simulation. Keep transients readable, short and variant-consistent. Kinetic = sharp mechanical/electric
impulse; beam = controlled high-energy electrical/thermal tone; guided launch = compact motor/launcher
transient; shield = clean energetic coupling; armor = dense material impact; penetration = sharper hot
material failure; destruction = layered but bounded. Critical UI warnings must remain audible.
\`\`\`

### 23.5 Operations sonic brief

\`\`\`text
Docking/cargo/mining/repair/construction audio should sound functional and procedural: clamps, actuators,
pumps, tooling, cutting/welding, handling machinery and work-state loops. Avoid cinematic factory noise.
Every loop must have a clean start/stop and must be quiet enough to sit below warnings/communications.
\`\`\`

## 24. Coverage checklist

Current explicitly cataloged production prompt families:

- [x] Empire major ships: 9
- [x] Industrial Union major ships: 9
- [x] carrier small-craft base hulls: 2
- [x] carrier small-craft fit overlays: 6
- [x] Empire stations: 8
- [x] Industrial Union stations: 8
- [x] station mechanical component families: 13
- [x] resource bodies: 4
- [x] special locations: 3
- [x] ordnance bodies: 10
- [x] shared VFX families: 27
- [x] current core characters: 12
- [x] generic damage/wreck/emissive/marker prompts
- [x] UI icon style prompt
- [x] audio semantic/faction briefs

Known intentionally deferred exact prompts:

- per-UI-icon IDs, until 23B/23C freeze the final semantic icon list;
- any post-core faction assets, outside Stage-23 RC scope;
- optional music breadth beyond currently approved restrained stingers.

## 25. Definition of Done for prompt readiness

A Stage-23E asset is prompt-ready when:

- asset_id exists;
- authoritative role/content reference exists;
- correct inherited faction/global block is known;
- object-specific prompt exists in this catalog;
- canvas/orientation comes from the production spec;
- negative constraints are explicit;
- layer prompts are defined where required;
- candidate-generation count is known;
- no prompt invents unsupported simulation capability;
- provenance can reference prompt section/version;
- animation frames can be derived from an accepted base/component rather than independently redrawn.

This catalog is the canonical starting point for Stage-23E generation/authoring work.
