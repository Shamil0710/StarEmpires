# Stage 23E — Visual Reference Freeze Catalog

**Status:** CANONICAL PRE-PRODUCTION VISUAL REFERENCE CONTRACT  
**Parent execution contract:** docs/stage23e_final_presentation_plan.md  
**Production asset specification:** docs/stage23e_production_asset_spec.md  
**Generation/authoring catalog:** docs/stage23e_generation_prompt_catalog.md  
**Faction authorities:** docs/factions/empire_visual_bible.md and docs/factions/industrial_union_visual_bible.md  
**Purpose:** freeze the visual geometry and identity of every significant Stage-23E object before mass production of layers, animation, VFX bindings and derived runtime art.  
**Live registry:** `docs/art/reference_freeze/stage23e_reference_manifest.tsv`  
**Generation reviews:** `docs/art/reference_freeze/reviews/`

## 1. Why a visual-reference freeze exists

Stage 23E has many derived outputs per object:

    base
    damage
    emissive
    alert
    wreck
    marker
    fit overlay
    mechanical animation
    anchor metadata
    VFX attachment
    audio binding

If those outputs are authored without one accepted visual source, geometry will drift. A docking arm
may move to a different side, a hangar may change shape between frames, a wreck may no longer resemble
the base ship, or a damage mask may imply a module that never existed.

The reference freeze therefore establishes:

    authoritative gameplay/content role
    -> canonical visual reference
    -> frozen geometry
    -> all production derivatives

The reference is presentation authority only. It never overrides physical dimensions, fitted systems,
capacity, hardpoints, collision, resource content, damage state or visibility authority.

## 2. What counts as a canonical visual reference

A canonical visual reference is the selected, reviewed, geometry-frozen visual master for one
significant object or one derived fit identity.

It is not:

- a cinematic concept painting;
- a mood illustration;
- a perspective beauty shot;
- a sprite sheet;
- a damage state;
- a VFX frame;
- a final proof that the runtime implementation is correct.

It must be close enough to production art that later layers can be derived without redesigning the
object.

### 2.1 Canonical reference package

For a new base visual family, freeze:

    reference_master.png
    reference_silhouette.png
    reference_anchor_review.png
    reference_record.md or manifest row
    provenance record

Only reference_master.png is the artistic source image. The silhouette and anchor review are derived
review artifacts and may be produced programmatically/manual-review tooling.

### 2.2 Geometry freeze

After a reference is accepted, the following are frozen unless a documented reference revision is
approved:

- overall silhouette;
- primary mass placement;
- bow/stern or functional orientation;
- central structural spine/citadel arrangement;
- major module locations;
- visible hangar/berth positions;
- visible tank/process/cargo blocks;
- engine/nozzle locations;
- physically represented weapon/sensor/service zones;
- major mechanical-component roots and tracks;
- faction construction grammar.

Micro-detail may still be refined during production if it does not change the frozen items.

## 3. Reference creation modes

Every significant object uses exactly one of these modes.

### 3.1 PROMOTE_EXISTING

Use when a current production sprite/master is already good enough to define the visual family.

Process:

1. audit current art against faction bible, role, scale and production spec;
2. verify no known defect requires a redesign;
3. create derived silhouette and anchor-review artifacts;
4. assign a canonical reference ID;
5. record current production asset checksum/path as the source;
6. freeze it without generating five unnecessary replacements.

### 3.2 GENERATE_5_SELECT_1

Use for a new base visual family or when an existing object is still a placeholder/prototype.

Process:

1. assemble the canonical reference prompt;
2. generate **exactly five genuinely different candidates**;
3. each candidate is a separate image;
4. all five share the same role, physical envelope, output lock and faction law;
5. hard-reject invalid candidates;
6. review surviving candidates;
7. select one;
8. normalize it to the production canvas;
9. create silhouette/anchor review;
10. assign canonical reference ID and freeze geometry.

The five images are alternatives for one family, not animation frames.

### 3.3 DERIVE_FROM_REFERENCE

Use when the visual identity is a fit/module variant of an already frozen physical base.

Process:

1. load the frozen base reference;
2. author only the fit/module overlay;
3. preserve base physical silhouette and pivot unless the authoritative fitted hardware physically
   protrudes;
4. review capability readability;
5. freeze the composite as a derived reference;
6. never regenerate an unrelated "new hull" merely to distinguish the fit.

This mode is mandatory for the six carrier small-craft fits.


### 3.4 Generator multi-candidate normalization exception

The production target remains one separate image per candidate. However, if the connected image
backend returns several **physically disjoint candidates on a genuinely transparent canvas despite a
separate-image request**, the following deterministic normalization is allowed **for candidate review
only**:

1. archive/record the source generation ID;
2. segment only disconnected alpha components;
3. do not redraw, repaint, inpaint or merge candidate geometry;
4. place each component on its own transparent production-aspect canvas;
5. preserve relative pixels inside the extracted component apart from uniform scaling/padding;
6. mark the extracted images as review candidates;
7. the source multi-object canvas itself can never become a canonical reference;
8. the selected candidate still requires an independent clean final master before status may become
   `FROZEN`.

This exception exists to handle tool-output packaging, not to permit sprite-sheet-style art direction.

If the backend adds a flat/smooth presentation background rather than real alpha, technical background
matting is also allowed **only for review normalization**, provided that:

- no station/ship pixels are intentionally repainted or invented;
- only crop, alpha matting, uniform scale and padding are applied;
- the review record identifies the source generation and the normalization;
- any ambiguous segmentation is a hard reject for that candidate;
- the normalized review candidate still cannot become `reference_master.png`;
- a separately accepted clean transparent master is mandatory before `FROZEN`.

## 4. Canonical reference output lock

Use for all GENERATE_5_SELECT_1 world-object reference candidates.

\`\`\`text
STAR EMPIRES — CANONICAL VISUAL REFERENCE OUTPUT LOCK

Create ONE canonical-reference candidate for a grounded hard-science-fiction 2D strategy/sandbox game.

PURPOSE:
This image is not a cinematic concept illustration. It is a geometry-defining production reference
from which the final sprite, damage layer, emissive layer, wreck, marker and local mechanical
animations will be derived.

CAMERA:
- strict top-down orthographic
- absolutely no perspective tilt
- no isometric angle
- no three-quarter view
- no cinematic camera
- preserve the production orientation declared in the asset specification
- object centered

COMPOSITION:
- exactly ONE object
- entire silhouette visible
- no cropped appendages
- use the same aspect ratio as the production canvas
- object occupies the production-safe area without touching canvas edges
- strong silhouette readable at gameplay scale

BACKGROUND:
- genuine transparent background
- clean alpha
- no checkerboard baked into pixels
- no stars
- no nebulae
- no planets
- no floor
- no environment
- no UI
- no text
- no labels
- no border
- no watermark

DESIGN:
- grounded engineering
- every major visible element has a plausible function
- prioritize large functional masses over surface micro-noise
- show enough service/access/structural logic for later production layers
- keep moving mechanisms in their declared BASE / STOWED / CLOSED state
- preserve clear locations for the mechanisms named in the role brief
- show realistic maintained wear only
- no damage state
- no wreck state

TRANSIENT STATE:
DO NOT bake in:
- engine exhaust
- RCS plume
- muzzle flash
- projectile
- beam
- explosion
- smoke trail
- active fire
- debris cloud
- welding
- mining particles
- jump effect
- alert glow

LIGHT:
- neutral soft presentation illumination
- enough contrast to read material and structure
- no dramatic external shadow
- no cinematic rim-light spectacle
- emissive lights may be visible only as restrained base-state sources

CRITICAL CONSISTENCY:
The candidate must be suitable for later exact-alignment production work.
Do not use visual tricks that cannot survive conversion into a clean top-down game sprite.
\`\`\`

## 5. Global reference negative prompt

\`\`\`text
perspective,
three-quarter view,
isometric,
cinematic angle,
beauty shot,
concept-art environment,
multiple objects,
sprite sheet,
collage,
turnaround sheet,
starfield,
planet,
nebula,
hangar floor,
UI,
labels,
text,
watermark,
checkerboard baked into image,
baked exhaust,
baked weapon fire,
baked explosion,
baked smoke,
damage,
wreck,
active fire,
debris trail,
lens flare,
huge bloom,
neon cyberpunk,
fantasy spaceship,
fantasy station,
ornamental wings,
ornamental fins,
baroque machinery,
steampunk,
random greebles,
meaningless pipes,
impossible docking geometry,
fragile unsupported appendages,
unreadable silhouette,
decorative asymmetry without engineering purpose
\`\`\`

## 6. Candidate hard-rejection rules

Reject a candidate immediately if any one of the following is true:

- perspective or camera tilt is visible;
- background is not genuinely transparent/cleanly extractable;
- object is cropped or touches the unsafe canvas edge;
- silhouette contradicts the role;
- faction identity is carried only by color;
- a major element has no plausible function;
- design invents a gameplay capability absent from the role;
- ship/station geometry cannot support required anchors/mechanisms;
- moving components have no plausible root/track/stowage;
- object duplicates the other faction's construction grammar;
- candidate relies on exhaust/glow/VFX to look good;
- generated labels/text/watermarks are embedded;
- candidate is too noisy to read at gameplay scale;
- candidate visibly violates authoritative physical envelope/aspect requirements.

A hard-rejected candidate is not scored.

## 7. Reference review scorecard

Score surviving candidates out of 100.

| Dimension | Weight | Review question |
| --- | ---: | --- |
| engineering plausibility | 25 | Can every major mass/interface be explained and serviced? |
| role readability | 20 | Is the gameplay role obvious from silhouette/modules without text? |
| faction identity | 20 | Is the faction readable without relying only on palette? |
| gameplay-scale silhouette | 15 | Does it remain distinct when downscaled? |
| derivative readiness | 10 | Can damage/emissive/wreck/mechanisms/anchors be derived cleanly? |
| family distinctiveness | 10 | Is it distinct from neighboring classes while staying in faction family? |

Selection rule:

- minimum total: 80/100;
- engineering plausibility must be at least 20/25;
- role readability must be at least 16/20;
- faction identity must be at least 16/20;
- no hard-rejection condition may be present.

If none of the five candidates passes, generate a new batch after documenting why the first batch
failed. Do not select the "least bad" candidate.

## 8. Reference visual QA views

For every selected base reference produce these review views:

### 8.1 Native reference

The selected transparent reference master at final normalized production canvas.

### 8.2 Grayscale

Used to test silhouette/faction/role without palette.

### 8.3 25% scale

Used to test gameplay readability.

### 8.4 12.5% scale

Used to detect over-dependence on micro-detail.

### 8.5 Silhouette-only

Solid monochrome fill of base alpha.

### 8.6 Anchor review

A review-only overlay marking:

- engines;
- RCS;
- weapon/launcher zones;
- sensors;
- radiators;
- hangars;
- docking points;
- service/mechanical roots.

This overlay is not a runtime sprite.

## 9. Reference revision policy

Reference IDs are versioned.

Example:

    ref.empire.station.naval_ordnance_depot.v1

A revision to micro-texture that preserves frozen geometry does not require v2 if the reference image
checksum/provenance is updated before production begins.

Create v2 when changing:

- silhouette;
- major module placement;
- mechanical root/track;
- hangar/docking geometry;
- engine placement;
- major role-defining visual structure;
- faction construction grammar.

All derived assets tied to the prior reference must then be reviewed for regeneration.

## 10. Reference manifest fields

Each reference row records:

    reference_id
    asset_id
    mode
    faction
    role
    authoritative_content_or_role_id
    source_asset_path_if_promoted
    source_asset_checksum
    prompt_catalog_section
    reference_prompt_version
    production_canvas
    orientation
    candidate_count
    selected_candidate
    rejection_notes
    score
    reviewer
    geometry_frozen
    selected_reference_path
    silhouette_review_path
    anchor_review_path
    provenance_id
    status
    supersedes_reference_id

Allowed status:

    PLANNED
    CANDIDATES_READY
    SELECTED
    FROZEN
    SUPERSEDED
    REJECTED

## 11. Freeze inventory summary

The Stage-23E visual-reference registry contains **49 significant world-object/fit references**:

- 18 major ship references;
- 16 faction station references;
- 2 carrier-small-craft base references;
- 6 carrier-small-craft derived fit references;
- 3 special-location references;
- 4 resource-body references.

The current 12 recurring characters use their accepted/master character illustrations as character
visual references and are governed separately by the Character Master Prompt. They are not counted in
the 49 world-object reference total.

Ordinary ordnance bodies, VFX, markers, damage masks, emissive masks and individual animation frames
do not receive separate concept-reference images. Their production assets are governed by the accepted
base reference and the Generation Prompt Catalog.

## 12. Major ship reference registry

The current repository already describes both factions' nine major ship base sets as production-grade.
Therefore the default plan is **PROMOTE_EXISTING**, not wasteful regeneration.

### 12.1 Empire — 9 references

| Reference ID | Asset ID | Mode | Reference source / prompt |
| --- | --- | --- | --- |
| ref.empire.ship.corvette.v1 | empire.ship.corvette | PROMOTE_EXISTING | current Empire production corvette; prompt fallback: Generation Catalog §6.1 |
| ref.empire.ship.frigate.v1 | empire.ship.frigate | PROMOTE_EXISTING | current Empire production frigate; fallback §6.2 |
| ref.empire.ship.destroyer.v1 | empire.ship.destroyer | PROMOTE_EXISTING | current Empire production destroyer; fallback §6.3 |
| ref.empire.ship.cruiser.v1 | empire.ship.cruiser | PROMOTE_EXISTING | current Empire production cruiser; fallback §6.4 |
| ref.empire.ship.battleship.v1 | empire.ship.battleship | PROMOTE_EXISTING | current Empire production battleship; fallback §6.5 |
| ref.empire.ship.carrier.v1 | empire.ship.carrier | PROMOTE_EXISTING | current Empire production carrier; fallback §6.6 |
| ref.empire.ship.freight.v1 | empire.ship.freight | PROMOTE_EXISTING | current Empire production freight; fallback §6.7 |
| ref.empire.ship.tanker.v1 | empire.ship.tanker | PROMOTE_EXISTING | current Empire production tanker; fallback §6.8 |
| ref.empire.ship.fleet_support.v1 | empire.ship.fleet_support | PROMOTE_EXISTING | current Empire production fleet support; fallback §6.9 |

Promotion gate:

- production detail remains acceptable;
- silhouette passes current faction/role review;
- no known physical/anchor mismatch requires redesign;
- alpha/pivot/orientation pass;
- reference QA views pass.

If a specific ship fails promotion, only that family switches to GENERATE_5_SELECT_1 using the prompt
below.

#### Empire ship reference prompt assembly

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
+
[IMPERIAL FACTION VISUAL DNA]
+
[IMPERIAL SPACECRAFT DESIGN]
+
[matching ASSET-SPECIFIC PROMPT from Generation Prompt Catalog §6]
+
[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 12.2 Industrial Union — 9 references

| Reference ID | Asset ID | Mode | Reference source / prompt |
| --- | --- | --- | --- |
| ref.industrial_union.ship.corvette.v1 | industrial_union.ship.corvette | PROMOTE_EXISTING | current Union production corvette; fallback Generation Catalog §7.1 |
| ref.industrial_union.ship.frigate.v1 | industrial_union.ship.frigate | PROMOTE_EXISTING | current Union production frigate; fallback §7.2 |
| ref.industrial_union.ship.destroyer.v1 | industrial_union.ship.destroyer | PROMOTE_EXISTING | current Union production destroyer; fallback §7.3 |
| ref.industrial_union.ship.cruiser.v1 | industrial_union.ship.cruiser | PROMOTE_EXISTING | current Union production cruiser; fallback §7.4 |
| ref.industrial_union.ship.battleship.v1 | industrial_union.ship.battleship | PROMOTE_EXISTING | current Union production battleship; fallback §7.5 |
| ref.industrial_union.ship.carrier.v1 | industrial_union.ship.carrier | PROMOTE_EXISTING | current Union production carrier; fallback §7.6 |
| ref.industrial_union.ship.freight.v1 | industrial_union.ship.freight | PROMOTE_EXISTING | current Union production freight; fallback §7.7 |
| ref.industrial_union.ship.tanker.v1 | industrial_union.ship.tanker | PROMOTE_EXISTING | current Union production tanker; fallback §7.8 |
| ref.industrial_union.ship.fleet_support.v1 | industrial_union.ship.fleet_support | PROMOTE_EXISTING | current Union production fleet support; fallback §7.9 |

#### Union ship reference prompt assembly

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
+
[INDUSTRIAL UNION FACTION VISUAL DNA]
+
[INDUSTRIAL UNION SPACECRAFT DESIGN]
+
[matching ASSET-SPECIFIC PROMPT from Generation Prompt Catalog §7]
+
[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

## 13. Carrier small-craft reference registry

### 13.1 Base hulls — 2 references

Both are new close-range production families and therefore use GENERATE_5_SELECT_1.

| Reference ID | Asset ID | Mode | Prompt |
| --- | --- | --- | --- |
| ref.empire.small_craft.base.v1 | empire.small_craft.base | GENERATE_5_SELECT_1 | §13.2 |
| ref.industrial_union.small_craft.base.v1 | industrial_union.small_craft.base | GENERATE_5_SELECT_1 | §13.3 |

### 13.2 Empire small-craft base reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]

[IMPERIAL FACTION VISUAL DNA]

Create one compact Imperial carrier-operated small-craft BASE HULL visual-reference candidate.

The physical hull must support three later fits: interceptor, defence and strike.

Required geometry:
- strict forward/right orientation
- narrow armored prow
- compact central protected system/crew block
- short dense rear propulsion section
- clearly readable common engine positions
- small robust sensor apertures
- several explicit standardized equipment attachment zones
- no baked fit-specific weapon that would make another fit impossible
- no built-in shield visual
- no large wings
- no atmospheric-aircraft styling
- no decorative fins

The design should look like a small naval craft belonging to the same old, durable Imperial
engineering culture as the fleet, but not like a miniature battleship.

Keep all fit attachment points serviceable and visually clean.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 13.3 Industrial Union small-craft base reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]

[INDUSTRIAL UNION FACTION VISUAL DNA]

Create one compact Industrial Union carrier-operated small-craft BASE HULL visual-reference candidate.

The same physical base hull must support interceptor, defence and strike fits.

Required geometry:
- strict forward/right orientation
- compact standardized modular body
- replaceable forward mission block/interface
- common propulsion module
- clear standardized equipment attachment points
- service-panel rhythm and maintainable module boundaries
- no fit-specific shield visual
- no permanent heavy strike weapon
- no Imperial central-citadel language
- no atmospheric wings or fantasy fins

The craft should look inexpensive to reproduce and maintain without looking primitive or disposable.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 13.4 Derived small-craft fits — 6 references

These use DERIVE_FROM_REFERENCE. Do not generate five new hull candidates.

| Reference ID | Asset ID | Base reference | Role prompt |
| --- | --- | --- | --- |
| ref.empire.small_craft.interceptor.v1 | empire.small_craft.fit_interceptor | ref.empire.small_craft.base.v1 | Generation Catalog §8.2 |
| ref.empire.small_craft.defence.v1 | empire.small_craft.fit_defence | ref.empire.small_craft.base.v1 | §8.3 |
| ref.empire.small_craft.strike.v1 | empire.small_craft.fit_strike | ref.empire.small_craft.base.v1 | §8.4 |
| ref.industrial_union.small_craft.interceptor.v1 | industrial_union.small_craft.fit_interceptor | ref.industrial_union.small_craft.base.v1 | §8.6 |
| ref.industrial_union.small_craft.defence.v1 | industrial_union.small_craft.fit_defence | ref.industrial_union.small_craft.base.v1 | §8.7 |
| ref.industrial_union.small_craft.strike.v1 | industrial_union.small_craft.fit_strike | ref.industrial_union.small_craft.base.v1 | §8.8 |

Derived reference prompt assembly:

\`\`\`text
Use the supplied frozen faction small-craft base reference as immutable geometry.

Create only the role-specific visual equipment defined by the matching Generation Prompt Catalog
section.

CRITICAL:
- do not redesign the hull
- preserve pivot/orientation
- preserve common engine/system geometry
- preserve physical dimensions unless real fitted hardware must protrude
- make the fit readable through actual equipment, not arbitrary paint
- transparent outside added/changed fit hardware
\`\`\`

## 14. Station reference registry

All 16 faction station variants are **GENERATE_5_SELECT_1** by default.

Reason: the older minimum playable pack provides only broad station-role coverage and is insufficient
to define all eight faction-specific release production families. Existing station art may still be
used as a quality/engineering reference, but should not silently freeze missing role/faction geometry.

### 14.1 Empire stations — 8 references

| Reference ID | Asset ID | Mode | Specific prompt section |
| --- | --- | --- | --- |
| ref.empire.station.mining_outpost.v1 | empire.station.mining_outpost | GENERATE_5_SELECT_1 | §14.3 |
| ref.empire.station.volatile_depot.v1 | empire.station.volatile_depot | GENERATE_5_SELECT_1 | §14.4 |
| ref.empire.station.refinery_complex.v1 | empire.station.refinery_complex | GENERATE_5_SELECT_1 | §14.5 |
| ref.empire.station.industrial_station.v1 | empire.station.industrial_station | GENERATE_5_SELECT_1 | §14.6 |
| ref.empire.station.high_tech_hub.v1 | empire.station.high_tech_hub | GENERATE_5_SELECT_1 | §14.7 |
| ref.empire.station.trade_logistics_hub.v1 | empire.station.trade_logistics_hub | GENERATE_5_SELECT_1 | §14.8 |
| ref.empire.station.naval_ordnance_depot.v1 | empire.station.naval_ordnance_depot | GENERATE_5_SELECT_1 | §14.9 |
| ref.empire.station.frontier_multipurpose.v1 | empire.station.frontier_multipurpose | GENERATE_5_SELECT_1 | §14.10 |

### 14.2 Industrial Union stations — 8 references

| Reference ID | Asset ID | Mode | Specific prompt section |
| --- | --- | --- | --- |
| ref.industrial_union.station.mining_outpost.v1 | industrial_union.station.mining_outpost | GENERATE_5_SELECT_1 | §14.11 |
| ref.industrial_union.station.volatile_depot.v1 | industrial_union.station.volatile_depot | GENERATE_5_SELECT_1 | §14.12 |
| ref.industrial_union.station.refinery_complex.v1 | industrial_union.station.refinery_complex | GENERATE_5_SELECT_1 | §14.13 |
| ref.industrial_union.station.industrial_station.v1 | industrial_union.station.industrial_station | GENERATE_5_SELECT_1 | §14.14 |
| ref.industrial_union.station.high_tech_hub.v1 | industrial_union.station.high_tech_hub | GENERATE_5_SELECT_1 | §14.15 |
| ref.industrial_union.station.trade_logistics_hub.v1 | industrial_union.station.trade_logistics_hub | GENERATE_5_SELECT_1 | §14.16 |
| ref.industrial_union.station.naval_ordnance_depot.v1 | industrial_union.station.naval_ordnance_depot | GENERATE_5_SELECT_1 | §14.17 |
| ref.industrial_union.station.frontier_multipurpose.v1 | industrial_union.station.frontier_multipurpose | GENERATE_5_SELECT_1 | §14.18 |

### 14.3 Empire mining outpost reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[IMPERIAL FACTION VISUAL DNA]

Create an Imperial MINING OUTPOST canonical-reference candidate.

Build the station around a protected central service/crew core. Include a clearly readable extraction
work side, ore receiving/handling area, finite storage modules, docking/service interface, survey
equipment and protected power/engineering modules.

A later animated extractor and handling arm must have obvious stowed positions, mechanical roots and
clear tracks/pivots. Keep them fully stowed in the reference.

The station should feel durable, repaired over decades, regulated and naval-industrial rather than a
chaotic open factory.

Do not show active mining, ore streams or resource quantity.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.4 Empire volatile depot reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[IMPERIAL FACTION VISUAL DNA]

Create an Imperial VOLATILE / WATER DEPOT canonical-reference candidate.

Use multiple protected pressure/tank modules with visible isolation between sections, an armored
control/service core, protected docking/refuel interfaces, a readable manifold/service zone and a
stowed transfer boom with clear deployment geometry.

Safety and containment must dominate the design.
Use redundancy and protective housings rather than exposed decorative pipes.

No fluid stream, leak, fire or emergency state.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.5 Empire refinery reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[IMPERIAL FACTION VISUAL DNA]

Create an Imperial REFINERY COMPLEX canonical-reference candidate.

Make intake, processing, thermal-control and output/storage zones visibly distinguishable.
Use a protected central service/administrative core, robust process housings, protected service trunks,
docking/cargo interfaces and sectional radiator structures.

If radiator shutters are included, their stowed/closed geometry and mechanical tracks must be obvious.

The station should show old durable structure modernized with newer process equipment.

No smoke, flame, atmospheric stacks or active processing VFX.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.6 Empire industrial station reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[IMPERIAL FACTION VISUAL DNA]

Create an Imperial INDUSTRIAL MANUFACTURING STATION canonical-reference candidate.

Use a protected administrative/service core surrounded by several heavy enclosed production/assembly
modules, raw-material receiving, component stores and loading/output berths.

Include one stowed heavy gantry and one stowed assembly mechanism with clear rails/roots for later
local animation. Keep them inside a safe practical silhouette.

Express controlled hierarchy and robust long-service production, not exposed chaotic factory clutter.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.7 Empire high-tech hub reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[IMPERIAL FACTION VISUAL DNA]

Create an Imperial HIGH-TECH MANUFACTURING HUB canonical-reference candidate.

Use protected precision-production modules, dense but orderly sensor/communications equipment,
controlled clean service interfaces, protected power/thermal sections, command/quality-control core
and material docking interfaces.

Include a compact stowed precision rig with a plausible motion path.
Show higher maintenance quality and precision than the heavy industrial station without becoming
sterile utopian sci-fi.

No giant holograms or neon spectacle.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.8 Empire trade/logistics hub reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[IMPERIAL FACTION VISUAL DNA]

Create an Imperial TRADE / LOGISTICS HUB canonical-reference candidate.

Use multiple readable berths, protected central traffic-control/administration core, cargo staging,
warehouse modules, customs/service zones and practical traffic sensors.

At least one representative berth must visibly include a docking clamp and a mechanically plausible
berth door in their open/idle base configuration suitable for later local animation.

The whole station should read as orderly bureaucratic throughput and controlled traffic.

No visiting ships or cargo objects baked into the reference.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.9 Empire naval ordnance depot reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[IMPERIAL FACTION VISUAL DNA]

Create an Imperial NAVAL ORDNANCE DEPOT canonical-reference candidate.

This is a major visual identity station.

Required:
- heavily protected central citadel/service core
- separated armored magazine modules
- physically isolated handling paths
- reinforced military docking/loading berths
- protected sensor/security structures
- visible but stowed loading arm
- large armored magazine door/shutter with explicit mechanical track and protected recess
- strict safety-zone geometry
- clear separation between ammunition storage and traffic/service areas

Visual language:
graphite/gunmetal structure, warm ivory armor, restrained burgundy state markings, very rare brass
authority details, muted cyan instruments and service amber.

The station must feel like an old naval institution built for safe handling and decades of service.
No weapon firing, no exposed missiles, no active loading.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.10 Empire frontier multipurpose station reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[IMPERIAL FACTION VISUAL DNA]

Create an Imperial FRONTIER MULTIPURPOSE STATION canonical-reference candidate.

Use a protected central service/crew core with several coherent add-on modules visibly belonging to
different modernization generations. Include docking, modest cargo/storage, limited repair/service,
limited utility/production and sensor/communications capability.

Include a stowed service arm and one practical module/service door with clear roots for later
animation.

Show retrofit history and long service without turning the station into random kitbash.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.11 Union mining outpost reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[INDUSTRIAL UNION FACTION VISUAL DNA]
[INDUSTRIAL UNION STATION DESIGN]

Create an Industrial Union MINING OUTPOST canonical-reference candidate.

Use standardized extractor/service blocks, repeated ore-handling modules, modular storage, a clear
material-flow path, standardized docking, survey/sensor block and maintainable utility sections.

Include a stowed extractor and handling arm with explicit common industrial pivots/rails for later
animation.

It should look like a small clean machine for extraction throughput, not a rough frontier scrapyard.
No active mining or ore stream.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.12 Union volatile depot reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[INDUSTRIAL UNION FACTION VISUAL DNA]
[INDUSTRIAL UNION STATION DESIGN]

Create an Industrial Union VOLATILE / WATER DEPOT canonical-reference candidate.

Use repeated standardized pressure/tank modules inside a protective service framework, visible
isolation gaps, repeated manifold interfaces, standardized docking/refuel points and one stowed
transfer boom with a clear common mechanical root.

The layout must make storage, isolation and transfer flow immediately legible.
No active fluid transfer or leak.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.13 Union refinery reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[INDUSTRIAL UNION FACTION VISUAL DNA]
[INDUSTRIAL UNION STATION DESIGN]

Create an Industrial Union REFINERY COMPLEX canonical-reference candidate.

Use repeated parallel process trains, standardized input handling, processing blocks, output/storage
interfaces, thermal/radiator modules, service corridors and cargo docking.

If radiator shutters exist, use repeated standardized shutter units with explicit hinges/tracks.

The station must look like a scalable material-processing machine with clear flow from intake to
output. No ceremonial core and no active smoke/flame.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.14 Union industrial station reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[INDUSTRIAL UNION FACTION VISUAL DNA]
[INDUSTRIAL UNION STATION DESIGN]

Create an Industrial Union INDUSTRIAL STATION / PRODUCTION HUB canonical-reference candidate.

This is a signature faction reference.

Use:
- repeated parallel production bays
- standardized assembly cells
- component/cargo staging
- clear material-handling routes
- modular utility/power blocks
- standardized docking interfaces
- one representative stowed gantry with real rail
- one representative stowed assembly rig with clear mechanical root

The silhouette should communicate serial production and throughput even in grayscale.
No moving machinery or work sparks in the base reference.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.15 Union high-tech hub reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[INDUSTRIAL UNION FACTION VISUAL DNA]
[INDUSTRIAL UNION STATION DESIGN]

Create an Industrial Union HIGH-TECH MANUFACTURING HUB canonical-reference candidate.

Use repeated precision manufacturing cells, clean standardized diagnostic/service interfaces,
sensor/communications blocks, controlled material input/output and modular utilities.

Include one compact stowed precision rig with realistic travel path.

Express advanced technology through precise repeatable engineering and instrumentation, not neon,
holograms or fantasy energy structures.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.16 Union trade/logistics hub reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[INDUSTRIAL UNION FACTION VISUAL DNA]
[INDUSTRIAL UNION STATION DESIGN]

Create an Industrial Union TRADE / LOGISTICS HUB canonical-reference candidate.

Use many standardized berths, repeating docking/clamp geometry, cargo staging grids, modular storage
blocks and a traffic/sensor module. Make arrival -> handling -> storage -> departure flow visually
obvious.

Include one representative docking clamp and berth door with clear standardized mechanical geometry
for later local animation.

No visiting ships, no floating cargo, no active handling VFX.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.17 Union naval ordnance depot reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[INDUSTRIAL UNION FACTION VISUAL DNA]
[INDUSTRIAL UNION STATION DESIGN]

Create an Industrial Union NAVAL ORDNANCE DEPOT canonical-reference candidate.

Use standardized armored magazine modules, repeated safe-handling interfaces, isolated transfer
corridors, military loading/docking points and a standardized sensor/security block.

Include a stowed loading arm and modular magazine door with explicit common industrial tracks.
Safety process and repeatability must be readable.

Do not create an Imperial fortress silhouette.
No exposed missiles, weapon firing or active transfer.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

### 14.18 Union frontier multipurpose reference prompt

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
[INDUSTRIAL UNION FACTION VISUAL DNA]
[INDUSTRIAL UNION STATION DESIGN]

Create an Industrial Union FRONTIER MULTIPURPOSE STATION canonical-reference candidate.

Use a standardized expandable central spine with modular docking/logistics, service/repair,
utility/production and sensor/communications blocks.

Show standardized expansion interfaces and one stowed service arm plus one service/module door with
clear common mechanical geometry.

It should look intentionally expandable from catalogued Union modules, not improvised scrap.

[GLOBAL REFERENCE NEGATIVE PROMPT]
\`\`\`

## 15. Special-location reference registry

These three receive canonical references because they need a stable visual identity distinct from
ordinary VFX/objects.

### 15.1 Energetic anomaly

Reference ID:

    ref.world.special.energetic_anomaly.v1

Mode: GENERATE_5_SELECT_1.

\`\`\`text
Create ONE canonical visual-reference candidate for a DETECTED ENERGETIC ANOMALY in grounded hard
science fiction.

This is not a solid artificial object and not supernatural magic.

Reference appearance:
- compact localized phenomenon
- irregular but bounded field/distortion volume
- physically suggestive thermal/plasma/interference structure
- restrained muted cyan / ivory / faint amber diagnostic-visible energy
- strong central/local silhouette-like footprint for gameplay readability
- no giant screen-filling glow
- no portal ring
- no architecture
- no crystals
- no lightning storm
- no resource-value symbolism

Transparent background, one phenomenon only.
The selected reference defines the shape language from which the later loop is derived.
\`\`\`

### 15.2 Escort-hull derelict

Reference ID:

    ref.world.special.escort_hull_derelict.v1

Mode: PROMOTE_EXISTING if the current Stage-20.5 derelict passes quality; otherwise
GENERATE_5_SELECT_1.

Fallback prompt:

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]

Create one persistent ESCORT-HULL DERELICT canonical-reference candidate.

Use a grounded military escort hull identity with:
- recognizable original axial hull
- dead propulsion
- severe old structural failure
- broken armor
- limited exposed internals
- missing/dead service lighting
- salvageable-looking persistent structure

This is an already-existing wreck:
- no fresh explosion
- no flame
- no smoke
- no flying debris
- no active weapon
- no active engine

The object must remain readable as a finite salvage site and former escort.
\`\`\`

### 15.3 Resonant resource phenomenon

Reference ID:

    ref.world.special.resonant_resource_phenomenon.v1

Mode: GENERATE_5_SELECT_1 for the **overlay shape language**, not a second resource body.

\`\`\`text
Create ONE canonical reference for a RESONANT RESOURCE PHENOMENON overlay in grounded hard science
fiction.

The phenomenon surrounds an existing resource occurrence; do NOT include or invent a second asteroid.

Use:
- restrained localized interference/resonance geometry
- subtle concentric, standing-wave or phase-pattern structure
- muted teal/cyan/ivory diagnostic-visible field
- compact bounded extent
- visually distinct language from the energetic anomaly
- no magical rune
- no crystal
- no extra resource mass
- no giant halo

Transparent background.
This selected reference defines the later animated overlay language.
\`\`\`

## 16. Resource-body reference registry

The four existing Stage-20.5 resource-body roles already have production imagery. Default mode is
PROMOTE_EXISTING after quality/knowledge-leak review.

| Reference ID | Asset ID | Mode | Prompt fallback |
| --- | --- | --- | --- |
| ref.world.resource.carbonaceous.v1 | world.resource.carbonaceous | PROMOTE_EXISTING | Generation Catalog §12.1 |
| ref.world.resource.water_ice.v1 | world.resource.water_ice | PROMOTE_EXISTING | §12.2 |
| ref.world.resource.metallic.v1 | world.resource.metallic | PROMOTE_EXISTING | §12.3 |
| ref.world.resource.mineral_silicate.v1 | world.resource.mineral_silicate | PROMOTE_EXISTING | §12.4 |

Promotion rejects art that leaks:

- reserve amount;
- grade;
- hidden exact composition beyond known classification;
- artificial "high value" glow.

If replacement is required, use GENERATE_5_SELECT_1 with:

\`\`\`text
[CANONICAL VISUAL REFERENCE OUTPUT LOCK]
+
[matching resource-body prompt from Generation Prompt Catalog §12]
+
Do not encode reserve quantity, grade or hidden value through brightness, glowing veins or visual
rarity.
\`\`\`

## 17. Objects that intentionally do NOT get separate visual-reference images

### 17.1 Ordnance

The ten ordnance bodies are small production assets. Their production sprite itself is the visual
reference after audit.

Do not create ten additional concept/reference images.

### 17.2 VFX

The 27 VFX families are transient event presentations. Their accepted sprite sheet / procedural effect
is the production reference.

### 17.3 Damage/emissive/alert layers

They derive from a frozen base reference and must not become independent visual designs.

### 17.4 Wreck

Wreck derives from the frozen base reference except the Stage-20H derelict, which is itself a persistent
special-location identity.

### 17.5 Markers

Markers derive from silhouette/reference and should not drive the base design.

### 17.6 Mechanical animation frames

Mechanism geometry is frozen in the base reference. The animation sheet only moves that accepted
component.

## 18. Character reference policy

The twelve current recurring NPCs use the accepted/master character illustration itself as the
canonical character visual reference.

No separate "concept reference" image is required on top of the master.

For any character whose current roster crop lacks a high-quality master:

1. use current accepted roster identity as visual reference;
2. use Character Master Prompt + faction overlay + character prompt package;
3. create candidate masters only if a rebuild is actually required;
4. preserve recognizable identity;
5. do not invent missing biological appearance data merely because text authority is incomplete.

Character reference IDs should use:

    ref.character.empire.viktor_arden.v1
    ...
    ref.character.union.david_stein.v1

They are tracked separately from the 49 world-object reference count.

## 19. Reference production order

Freeze references in this order because later work depends on earlier geometry:

1. audit/promote 18 existing major ships;
2. generate/select 2 small-craft base hulls;
3. derive/freeze 6 small-craft fit references;
4. generate/select the two station pilot references:
   - Empire naval ordnance depot;
   - Industrial Union industrial station;
5. validate the full reference -> layer -> animation pipeline on the two pilots;
6. generate/select remaining 14 station references;
7. promote/rebuild four resource-body references;
8. freeze the three special-location references;
9. freeze/verify character masters where needed;
10. only then begin broad production-layer generation.

This order prevents spending large amounts of art effort before the reference pipeline itself has
proven stable.

## 20. First production pilot pair

### 20.1 Empire naval ordnance depot

Why:

- strongest Imperial station identity test;
- heavy door;
- loading arm;
- docking;
- safety lights;
- alert layer;
- damage/wreck;
- marker;
- strong faction differentiation.

Required candidate batch:

    candidate_01.png
    candidate_02.png
    candidate_03.png
    candidate_04.png
    candidate_05.png

No collage.

### 20.2 Industrial Union industrial station

Why:

- strongest Union production identity test;
- repeated production cells;
- gantry;
- assembly rig;
- material flow;
- operational lights;
- damage/wreck;
- marker;
- direct contrast with Imperial hierarchy.

Required candidate batch:

    candidate_01.png
    candidate_02.png
    candidate_03.png
    candidate_04.png
    candidate_05.png

No collage.

## 21. Reference selection record template

Use for each GENERATE_5_SELECT_1 batch:

\`\`\`text
REFERENCE ID:
ASSET ID:
DATE:
PROMPT VERSION:
FACTION BIBLE VERSION:
PRODUCTION CANVAS:
ORIENTATION:

CANDIDATE 01
Hard rejection: YES/NO
Engineering: /25
Role readability: /20
Faction identity: /20
Gameplay silhouette: /15
Derivative readiness: /10
Family distinctiveness: /10
Total: /100
Notes:

CANDIDATE 02
...

SELECTED CANDIDATE:
Selection reason:

REJECTED CANDIDATE REASONS:
01:
02:
03:
04:
05:

GEOMETRY FREEZE:
Silhouette:
Major modules:
Moving component roots:
Anchors:
Known constraints:

STATUS:
FROZEN / REJECTED / NEW BATCH REQUIRED
\`\`\`

## 22. Storage convention

Recommended documentation/source structure:

    docs/art/reference_freeze/
        stage23e_reference_manifest.tsv
        reviews/
            <reference_id>.md

    art_sources/stage23e/references/
        <reference_id>/
            candidates/
                candidate_01.png
                candidate_02.png
                candidate_03.png
                candidate_04.png
                candidate_05.png
            selected/
                reference_master.png
                reference_silhouette.png
                reference_anchor_review.png
            provenance.json

If raw art sources are intentionally kept outside the runtime repository, the manifest must still
store stable provenance/content references.

Do not place rejected candidate bitmaps into the shipped runtime resource tree.

## 23. Definition of Done — reference freeze

The reference phase is complete when:

- all 49 world-object/fit reference rows exist;
- all 18 major ships are either promoted or explicitly regenerated;
- both small-craft base references are frozen;
- all six fit overlays are frozen against those two bases;
- all 16 faction station references are frozen;
- all four resource references are promoted/rebuilt;
- all three special-location references are frozen;
- every generated base family has a five-candidate selection record;
- every frozen reference has grayscale, downscale, silhouette and anchor review;
- every reference has provenance;
- no reference invents simulation capability;
- pilot pair successfully produces aligned base/damage/emissive/wreck/marker/mechanical derivatives;
- reference IDs are linked from the Stage-23E asset-gap matrix during implementation.

Only after this gate should Stage 23E begin broad production-layer generation.


## 24. Reference-generation execution log

The freeze contract is now in active pre-production use.

Current recorded run:

- `stage23e_reference_generation_run_000.md`;
- broad multi-object review generation;
- repository review artifact: `docs/art/reference_freeze/generated/stage23e_visual_reference_set_review_v0.svg`;
- result: **REJECTED as canonical reference input**, retained only as an art-direction review sheet;
- no reference ID was frozen;
- the next valid generation must use the strict pilot one-object/five-candidate flow.

This execution log does not waive the Stage-23E implementation gate. It prepares and validates the
art-production process only.

### Run 001 — Imperial naval ordnance depot candidate selection

- reference: `ref.empire.station.naval_ordnance_depot.v1`;
- five candidate designs generated and normalized into separate transparent review candidates;
- 192x192 repository previews committed under the reference candidate directory;
- candidates 01, 03, 04 and 05 rejected for detached generator debris;
- candidate 02 scored **83/100** and is `SELECTED`;
- geometry remains **not frozen**;
- mandatory next pass: preserve candidate-02 primary geometry while making the stowed loading arm and
  protected magazine door/track explicit, then create full-resolution master + silhouette + anchor QA.
- review record: `docs/art/reference_freeze/reviews/ref.empire.station.naval_ordnance_depot.v1_batch_001.md`.


### Run 002 — Industrial Union industrial station candidate selection

- reference: `ref.industrial_union.station.industrial_station.v1`;
- source generation: `47c6c305-f2f3-4ae3-981b-bdc12212ad15`;
- backend returned a two-faction presentation board rather than independent files;
- lower-row Union designs were deterministically normalized into five transparent review candidates;
- candidate 01 scored **92/100** and is `SELECTED`;
- geometry remains **not frozen**;
- clean-master attempt `dee4b31f-9980-42b1-a5b4-3ae0d2974a20` was rejected because it generated a new five-object board instead of preserving selected geometry;
- next gate: clean transparent candidate-01 master + grayscale/downscale/silhouette/anchor QA;
- review: `docs/art/reference_freeze/reviews/ref.industrial_union.station.industrial_station.v1_batch_001.md`.


### Run 003 — Empire carrier small-craft base selection

- reference: `ref.empire.small_craft.base.v1`;
- five separate single-object candidates successfully generated;
- candidate 03 scored **91/100** and is `SELECTED`;
- normalized 512x256 draft matches authoritative 28x12 m L/W within 7.8%;
- grayscale, 25%, 12.5%, silhouette and review-anchor checks passed locally;
- candidate 05 hard-rejected for wing/fin-like geometry;
- geometry remains **not frozen** until the canonical normalized master is persisted in the repository;
- no interceptor/defence/strike overlay generation may begin before that freeze.


### Run 004 — Major-ship promoted-reference freeze gate

- scope: all 18 `PROMOTE_EXISTING` major-ship base references;
- no base PNG geometry regenerated or modified;
- added `Stage23EMajorShipReferenceFreezeTest` as the executable Stage-23E reference gate;
- gate covers alpha/padding, grayscale readability, 25% and 12.5% downscale readability, silhouette
  uniqueness/near-duplicate detection and engineering-derived anchor projection;
- manifest rows move from `SELECTED` to `FROZEN` in the same acceptance batch;
- exact-head required CI remains mandatory before this freeze batch is accepted;
- review: `docs/art/reference_freeze/reviews/stage23e_major_ship_reference_freeze_qa.md`.

### Run 005 — Industrial Union small-craft backend drift

- reference: `ref.industrial_union.small_craft.base.v1`;
- generation ID: `af37623a-c4c0-4a60-9ac9-088a2ea75fd2`;
- requested a strict top-down Union spacecraft base candidate;
- backend returned an unrelated Imperial medic character sheet;
- output hard-rejected before candidate numbering/scoring;
- manifest remains `PLANNED`, `geometry_frozen=false`;
- review: `docs/art/reference_freeze/reviews/ref.industrial_union.small_craft.base.v1_generation_attempt_001.md`.


### Run 006 — Resource-body promoted-reference freeze gate

- references: four Stage-20.5 resource-body atlas regions;
- source atlas geometry is unchanged;
- added `Stage23EResourceReferenceFreezeTest` for transparent-corner, grayscale, 25%/12.5%,
  silhouette-occupancy and near-duplicate QA;
- prior manual knowledge-scope audit remains the authority for the no-reserve/no-grade/no-yield rule;
- all four manifest rows move from `SELECTED` to `FROZEN` in the same acceptance batch;
- exact-head CI remains mandatory;
- review: `docs/art/reference_freeze/reviews/stage23e_resource_reference_freeze_qa.md`.

### Run 007 — Escort-hull derelict dead-state recheck

- reference: `ref.world.special.escort_hull_derelict.v1`;
- source geometry remains suitable for promotion;
- main engines read non-thrusting and no fresh explosion/fire/smoke is baked into the base;
- small cyan/amber service/navigation-like lights remain and can still read as powered emission;
- reference therefore stays `SELECTED`, `geometry_frozen=false`;
- next action is deterministic dead-emissive cleanup without geometry redesign;
- review: `docs/art/reference_freeze/reviews/ref.world.special.escort_hull_derelict.v1_dead_state_recheck.md`.

### Run 008 — Industrial Union carrier small-craft base selection

- reference: `ref.industrial_union.small_craft.base.v1`;
- after the earlier target-drift rejection, generation was reseeded with accepted Union production
  corvette/carrier construction language;
- five separate one-object top-down transparent candidates were generated successfully;
- candidate 03 hard-rejected for wing-like/fragile lateral booms;
- candidate 05 scored **94/100** and is `SELECTED`;
- authoritative physical envelope: 29 x 14 x 6.5 m, 200 t;
- normalized candidate-05 draft: 512x256, visible bounds 428x220, physical-aspect error 6.1%;
- local grayscale, 25%, 12.5% and silhouette QA passed;
- geometry remains **not frozen** until the canonical normalized master is persisted in the repository;
- no interceptor/defence/strike overlay generation may begin before that freeze;
- review: `docs/art/reference_freeze/reviews/ref.industrial_union.small_craft.base.v1_batch_001.md`.


### Run 009 — Industrial Union small-craft exact-source correction and freeze

- reference: `ref.industrial_union.small_craft.base.v1`;
- the locally retained source PNGs from batch 001 were re-opened and measured directly with an
  alpha > 16 visible-pixel threshold;
- this found that Run 008's recorded normalized measurements for `candidate_05` were inconsistent
  with the actual source pixels: its source silhouette L/W is 2.794 versus the authoritative
  29/14 = 2.071 physical L/W, a 34.9% relative error;
- because the reference contract hard-rejects a candidate that visibly violates the authoritative
  physical envelope/aspect, the Run-008 `candidate_05` selection is superseded before geometry freeze;
- surviving source aspects were rechecked: candidate 01 = 2.722 (31.4% error), candidate 02 = 2.605
  (25.8%), candidate 03 = 2.788 (34.6%, already hard-rejected for fragile lateral booms),
  candidate 04 = 2.317 (11.8%), candidate 05 = 2.794 (34.9%);
- `candidate_04` retains its existing 92/100 qualitative score and becomes the authoritative selected
  design because it is the only non-hard-rejected candidate in the batch with acceptable physical
  envelope fidelity;
- candidate-04 generation ID: `92f9d865-c4a3-403e-ac9d-5c919b71e60b`;
- candidate 04 was alpha-trimmed and uniformly normalized without redrawing onto the canonical
  512x256 canvas;
- persisted master visible bounds: 480x207; visual L/W = 2.319; relative error to physical L/W =
  11.94%; minimum horizontal transparent padding = 16 px;
- persisted review artifacts include the reference master, silhouette, anchor review and QA sheet;
- `Stage23EUnionSmallCraftReferenceFreezeTest` pins the canonical master SHA-256 and checks alpha
  padding, centering, physical-aspect sanity, grayscale 25%/12.5% readability and reviewed
  presentation-anchor support;
- manifest status becomes `FROZEN`, `geometry_frozen=true` in the same acceptance batch;
- no simulation authority is inferred from the review anchors or sprite pixels;
- review: `docs/art/reference_freeze/reviews/ref.industrial_union.small_craft.base.v1_freeze_recheck.md`.


### Run 009 — Empire carrier small-craft missing-master recovery attempt

- reference: `ref.empire.small_craft.base.v1`;
- reason for reopening: batch-001 candidate 03 is documented as selected but no canonical/source PNG
  is present in the repository;
- requested a fresh five-independent-image small-craft batch using the accepted 28 x 12 x 6 m
  physical envelope and Imperial small-craft constraints;
- generation ID `c14b4992-9723-4a6e-8704-f3a2fc16bd10` returned a five-object **station** sheet;
- attempt hard-rejected for object-class and output-format drift;
- no candidate number or score assigned;
- row remains `SELECTED`, `geometry_frozen=false`;
- review: `docs/art/reference_freeze/reviews/ref.empire.small_craft.base.v1_generation_attempt_002.md`.


### Run 010 — Empire small-craft recovery attempt 003

- reference: `ref.empire.small_craft.base.v1`;
- retry used the real Empire production corvette/carrier images as visual-DNA context;
- generation ID `5dd5f188-9a21-4f1e-b37c-afb3c25d2827` again returned a station board;
- hard-rejected for wrong object class and single-board packaging;
- no candidate number/score assigned;
- row remains `SELECTED`, `geometry_frozen=false`;
- review: `docs/art/reference_freeze/reviews/ref.empire.small_craft.base.v1_generation_attempt_003.md`.

### Run 011 — Existing Empire exact-role station promotion

- direct repository visual audit found two Stage-20.5 assets that already satisfy the identity-discovery
  purpose of Stage-23E base reference generation;
- `ref.empire.station.industrial_station.v1` now uses
  `src/main/resources/assets/stage20_5/stations/imperial_industrial_station_v1.png`;
- `ref.empire.station.trade_logistics_hub.v1` now uses
  `src/main/resources/assets/stage20_5/stations/imperial_trade_hub_v1.png`;
- both rows change from `GENERATE_5_SELECT_1 / PLANNED` to
  `PROMOTE_EXISTING / SELECTED`;
- neither is frozen until canonical normalization plus grayscale/downscale/silhouette/anchor QA;
- the existing shipyard asset is deliberately not reused for a mismatched Stage-23E station role;
- review: `docs/art/reference_freeze/reviews/stage23e_existing_empire_station_promotion_audit.md`.


### Run 013 — Empire existing-station freeze accepted

- exact-head commit: `81edf75c66f1976c326f9dc4be880a202e5bf1b1`;
- CI run `37112721779`: **SUCCESS**;
- `ref.empire.station.industrial_station.v1` -> `FROZEN`;
- `ref.empire.station.trade_logistics_hub.v1` -> `FROZEN`;
- source production PNGs remain canonical geometry authority;
- committed Stage-23E silhouette/anchor/QA review artifacts remain presentation-only;
- no replacement five-candidate generation is required for these two references.


### Run 014 — Special-location existing-asset audit and derelict cleanup failure

- repository audit confirms only the escort-hull derelict has an existing special-location bitmap;
- energetic anomaly and resonant resource phenomenon still require dedicated generation;
- derelict cleanup generation `8e849311-ebbb-48cd-b571-6984c5ffd51a` returned an unrelated station sheet;
- result hard-rejected; derelict remains `SELECTED`, `geometry_frozen=false`;
- reviews:
  - `docs/art/reference_freeze/reviews/stage23e_special_existing_asset_audit.md`;
  - `docs/art/reference_freeze/reviews/ref.world.special.escort_hull_derelict.v1_cleanup_attempt_001.md`.

### Run 015 — Industrial Union fit-reference authority lock

- recovered retained Union candidate-04 source SHA-256 exactly matches frozen provenance:
  `bca6d8404a6654c928ab5c04af29429e6a800fcf223205a1a39add233d29d423`;
- M22.8 content confirms all three fits share reactor/drive/sensor/radiator;
- interceptor difference: one beam mount;
- defence difference: same beam mount plus shield emitter;
- strike difference: one kinetic mount, no shield;
- single weapon hardpoint projects to review anchor approximately `x=0.80, y=0.50`;
- attempted aligned interceptor edit `59068005-bd36-4626-aa20-757734717d5d` returned an unrelated station sheet and is hard-rejected;
- added `Stage23EUnionSmallCraftFitReferenceAuthorityTest` to prevent future reference art from inventing
  unauthored fit differences;
- fit rows remain `PLANNED` until real aligned visual masters exist;
- review: `docs/art/reference_freeze/reviews/stage23e_union_small_craft_fit_authority_audit.md`.


### Run 016 — Industrial Union technical fit-reference selection

The image edit backend remains unsuitable for exact aligned craft edits, so the three Union fit rows
advance only to **SELECTED**, not `FROZEN`, using deterministic technical geometry overlays on the
already frozen base master.

Selected reference composites:

- interceptor:
  `art_sources/stage23e/references/ref.industrial_union.small_craft.interceptor.v1/selected/reference_composite.svg`;
- defence:
  `art_sources/stage23e/references/ref.industrial_union.small_craft.defence.v1/selected/reference_composite.svg`;
- strike:
  `art_sources/stage23e/references/ref.industrial_union.small_craft.strike.v1/selected/reference_composite.svg`.

All three overlays:

- preserve the 512x256 frozen base geometry;
- keep the common reactor/drive/sensor/radiator visually unchanged;
- use the single `weapon_primary` review anchor at approximately `x=0.80, y=0.50`;
- represent only the authored beam / shield / kinetic fit differences;
- remain presentation/reference authority only;
- require a final painted aligned pass before `geometry_frozen=true`.

This reduces ambiguity without falsely treating a technical overlay as finished production art.


### Run 017 — Empire small-craft Library recovery audit

- searched current conversation files and personal Library before spending another generation batch;
- found `/Stage23E_tmp/batch002` with five previews and `candidate_03_selected_draft.png`;
- historical context ties that folder to the Empire small-craft recovery attempt;
- direct image inspection proves all recovered batch002 candidates are station geometry, not small craft;
- broad Visual Reference Set thumbnails remain non-canonical rejected review material;
- no provable batch-001 Empire candidate-03 source/master was recovered;
- Empire base remains `SELECTED`, `geometry_frozen=false`;
- review: `docs/art/reference_freeze/reviews/ref.empire.small_craft.base.v1_library_recovery_audit.md`.


### Run 018 — Empire mining outpost batch 001

- reference: `ref.empire.station.mining_outpost.v1`;
- generation ID: `4798a103-b990-4594-a7fe-1bf42d13dbb1`;
- backend returned a five-Imperial-station board rather than five separate files;
- object class was correct, so five candidates were reviewed after deterministic board-cell isolation;
- selected: `candidate_03`, score **92/100**;
- candidate 03 provides the strongest mining/extraction role through protected service structure,
  handling/extractor arm roots, finite storage/handling blocks and survey/service geometry;
- selected geometry was technically normalized, not redrawn, into a transparent 1024x1024 master;
- canonical master SHA-256:
  `160f5d5779eb762a43feb43109619a14260e587ee068512860a4154eb1863811`;
- added executable `Stage23EEmpireMiningOutpostReferenceFreezeTest`;
- manifest advances `PLANNED -> SELECTED`;
- `geometry_frozen=false` until exact-head CI succeeds;
- review: `docs/art/reference_freeze/reviews/ref.empire.station.mining_outpost.v1_batch_001.md`.


### Run 019 — Empire volatile depot batch 001

- reference: `ref.empire.station.volatile_depot.v1`;
- generation ID `958326e2-a119-4402-916c-129f18f7aadc`;
- transparent five-candidate Empire station source board;
- selected `candidate_04`, score **95/100**;
- strongest evidence: repeated protected pressure/tank groups, isolation/service framing, central control
  spine, manifold/truss routing, docking/transfer interfaces and stowed transfer-boom geometry;
- source alpha allowed direct connected-component isolation rather than opaque-background matting;
- canonical 1024x1024 master SHA-256:
  `846c3cb4f27cc6c115b3959d6d18ee81b59222559c415a120ca8de98ae5e7090`;
- added `Stage23EEmpireVolatileDepotReferenceFreezeTest`;
- manifest `PLANNED -> SELECTED`, `geometry_frozen=false` pending exact-head CI;
- review: `docs/art/reference_freeze/reviews/ref.empire.station.volatile_depot.v1_batch_001.md`.


### Run 020 — Industrial Union mining-outpost faction drift

- reference: `ref.industrial_union.station.mining_outpost.v1`;
- generation ID `a2ab3446-7063-47da-ab82-9ff38c900bd5`;
- requested Union standardized extraction/logistics construction language;
- backend reproduced the Imperial cream/burgundy/heraldic station family;
- entire batch hard-rejected before candidate scoring;
- row remains `PLANNED`, `geometry_frozen=false`;
- review: `docs/art/reference_freeze/reviews/ref.industrial_union.station.mining_outpost.v1_generation_attempt_001.md`.

### Run 021 — Empire refinery selection

- reference: `ref.empire.station.refinery_complex.v1`;
- generation ID `8bfb7e78-9299-411f-83d0-c797a893cce7`;
- selected `candidate_02`, score **94/100**;
- primary role evidence: repeated process towers, protected process/service trunks, intermediate
  vessels, central service region and distinct input/output-side module masses;
- row advances to `SELECTED`;
- local normalized working master is not treated as authority until persisted in Git;
- `geometry_frozen=false`;
- review: `docs/art/reference_freeze/reviews/ref.empire.station.refinery_complex.v1_batch_001.md`.

### Run 022 — Empire high-tech hub selection

- reference: `ref.empire.station.high_tech_hub.v1`;
- generation ID `101b50b6-ca78-46ed-9308-5b3b9e03bf3c`;
- selected `candidate_05`, score **94/100**;
- primary role evidence: compact instrumented protected core, clean precision/thermal panels,
  organized sensor/comms and controlled service interfaces;
- row advances to `SELECTED`;
- local normalized working master is not repository authority until persisted;
- `geometry_frozen=false`;
- review: `docs/art/reference_freeze/reviews/ref.empire.station.high_tech_hub.v1_batch_001.md`.

### Run 023 — Empire frontier multipurpose selection

- reference: `ref.empire.station.frontier_multipurpose.v1`;
- generation ID `39b5a286-e7bc-4ff8-b174-5969598dddf0`;
- selected `candidate_03`, score **95/100**;
- primary role evidence: mixed-generation modules, protected service core, compact cargo/utility,
  service crane/arm, sensor mast and ordered retrofit history;
- row advances to `SELECTED`;
- local normalized working master is not repository authority until persisted;
- `geometry_frozen=false`;
- review: `docs/art/reference_freeze/reviews/ref.empire.station.frontier_multipurpose.v1_batch_001.md`.


### Run 024 — Union mining-outpost reseed retry

- reference: `ref.industrial_union.station.mining_outpost.v1`;
- generation ID: `8a772f8e-836a-4bd2-858f-ecbc61ed6a06`;
- retry was explicitly reseeded with real Union production corvette and freight sprites;
- backend still returned an Imperial cream/burgundy/heraldic station board;
- hard-rejected for persistent faction/style drift and board packaging;
- row remains `PLANNED`;
- further Union station generation is suspended until a clean image-context reset is available;
- review: `docs/art/reference_freeze/reviews/ref.industrial_union.station.mining_outpost.v1_generation_attempt_002.md`.

### Run 025 — Empire naval-ordnance clean-master retry

- reference: `ref.empire.station.naval_ordnance_depot.v1`;
- generation ID: `25ef5eb1-94f1-41d3-b6a8-82a6837cbf8a`;
- requested a clean five-candidate replacement discovery batch because the accepted old selection has
  only small previews in Git;
- backend returned eight concepts on one sheet;
- hard-rejected for finite-candidate/packaging contract violation;
- original selected candidate remains preferred;
- row remains `SELECTED`, `geometry_frozen=false`;
- review: `docs/art/reference_freeze/reviews/ref.empire.station.naval_ordnance_depot.v1_clean_master_attempt_002.md`.


### Run 026 — Empire mining + volatile station freeze accepted

- exact-head commit: `455ebed0cc7e8686b1c9ba097bf3f295ad4e6869`;
- CI run `37120429027`: **SUCCESS**;
- `ref.empire.station.mining_outpost.v1` -> `FROZEN`;
- `ref.empire.station.volatile_depot.v1` -> `FROZEN`;
- both references have repository-persisted 1024x1024 canonical master PNGs;
- SHA-256, alpha/padding, grayscale, 25%/12.5%, anchor and physical-authority tests passed;
- source pixels remain presentation authority only and do not redefine simulation capacities, reserves or collision.


### Run 027 — Image-context contamination proof

Two additional calls establish that current failures are not isolated candidate-quality issues.

Industrial Union mining outpost:

- generation `e829d0e2-6bad-424a-85c8-9d8c6eedd64f`;
- deliberately requested one candidate rather than a board;
- explicitly prohibited Imperial palette/heraldry;
- backend still returned seven Imperial station concepts;
- hard reject; Union station generation is now blocked pending clean-context proof.

Energetic anomaly:

- generation `c0092715-d1f0-4c5e-b42d-1dcecd723ac4`;
- requested transparent localized non-solid field/VFX candidates;
- backend returned a Stage-23E status/dashboard infographic with text and station thumbnails;
- hard reject; special-location generation is blocked in the same context.

Blocker ledger:
`docs/art/reference_freeze/stage23e_reference_blockers.md`.
