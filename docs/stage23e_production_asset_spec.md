# Stage 23E — Production Asset and Animation Specification

**Status:** CANONICAL TECHNICAL PRODUCTION SPECIFICATION  
**Parent contract:** docs/stage23e_final_presentation_plan.md  
**Visual-reference freeze:** docs/stage23e_visual_reference_freeze.md  
**Generation/authoring catalog:** docs/stage23e_generation_prompt_catalog.md  
**Stage:** 23E — final art, VFX, animation and audio replacement  
**Scope:** exact authoring/package rules for release-facing art, animation, VFX and audio.  
**Important:** this document specifies presentation assets only. It does not create simulation authority.

## 1. Why this document exists

The Stage-23E execution plan defines what final presentation must communicate. This companion
specification defines what the production team must actually author, name, export, animate, bind and
review.

It is intentionally concrete:

- image dimensions;
- safe areas;
- layers;
- file names;
- frame counts;
- animation cadence;
- one-shot duration;
- marker/LOD exports;
- VFX sheet sizes;
- audio file/variant minimums;
- manifests;
- object-by-object deliverables.

A pretty concept image or four-frame preview does not satisfy this contract by itself. A production
object is complete only when the runtime package described here is bound to authoritative state and
passes the parent Stage-23E acceptance criteria.

## 2. Source-derived facts vs new 23E production decisions

The following are already accepted repository facts and are preserved:

- the nine Empire production ship families use 768x512 RGBA canvases;
- the nine Industrial Union production ship families use 768x512 RGBA canvases;
- current normalized ship safe length is approximately 648–660 px inside those canvases;
- ship runtime orientation is forward/right;
- Empire already has base + damage + emissive layers for the nine major families;
- Industrial Union currently has production base art but its damage/emissive/engine-state closure is
  owned by later visual polish;
- character roster runtime atlases are currently 336x84 with six 56x84 cells per faction;
- Stage-20.5 has four resource-body atlas roles;
- Stage-20H currently has three special-location kinds;
- production ordnance currently has ten sprite identities;
- presentation must remain read-only over simulation authority.

The following are **new Stage-23E production standards** introduced by this document:

- 512x256 production source canvases for carrier small craft;
- 1024x1024 production source canvases for stations;
- packed emissive-group masks for station idle/status animation;
- 128x128 object marker masters;
- explicit wreck art for production ship/station release roles;
- exact component-local sprite-sheet conventions;
- exact baseline frame counts/cadence for state animation;
- exact minimum audio variant budgets;
- exact naming/package layout.

If implementation-time evidence shows a technical reason to alter one of these new standards, the
change must update this file and the asset-gap matrix in the same PR.

## 3. General file and directory convention

Logical target layout:

    assets/production/
        shared/
            vfx/
            audio/
            ui/
            backgrounds/
        empire/
            ships/
            small_craft/
            stations/
            characters/
            audio/
        industrial_union/
            ships/
            small_craft/
            stations/
            characters/
            audio/
        world/
            resources/
            special_locations/
            ordnance/

Existing accepted resource paths do not need to be renamed merely for cosmetic consistency. If
current runtime catalogs already bind a path, 23E may keep that path and record the logical target
identity in presentation.json / manifest aliases.

### 3.1 Stable naming rules

Use lower snake_case ASCII filenames.

Required core names where applicable:

    base.png
    damage.png
    emissive.png
    emissive_groups.png
    alert_mask.png
    wreck.png
    marker.png
    engine_idle_strip.png
    engine_thrust_strip.png
    mechanical_<operation>.png
    operation_<type>.png
    presentation.json
    provenance.json

No filename should encode a transient runtime state that is not a real supported presentation state.

Bad:

    station_super_active_final2.png
    missile_hit_big.png

Good:

    mechanical_dock_clamp.png
    operation_salvage.png
    guided_detonation_medium.png

## 4. Global image format

Unless a section explicitly overrides it:

- PNG;
- RGBA 8-bit per channel;
- sRGB color data;
- genuine alpha;
- fully transparent unused canvas;
- no baked background;
- no baked UI;
- no baked drop shadow from an external scene light;
- no baked transient exhaust/projectile/explosion;
- no premultiplied-alpha fringe in source art;
- nearest or linear filtering selected per runtime use, but authoring master remains full resolution;
- generate mipmaps for world sprites where the renderer uses texture minification;
- pivot metadata is authoritative; visual centering is required unless a local component sheet states
  otherwise.

### 4.1 Safe alpha rule

At least 16 px transparent padding around the visible object at source resolution for 512-class
assets and at least 32 px for 768/1024-class assets, unless a current production test already defines
a stricter safe area.

No effect layer may contain opaque pixels outside its declared allowed region.

### 4.2 Orientation

Major ships retain the accepted runtime convention:

    physical forward +Y
    -> production sprite forward/right (+X on canvas)

Do not return to nose-up art for production integration unless a single global migration is accepted.

Stations have no forward axis unless the authoritative design requires one. Their pivot remains
centered.

## 5. Animation representation rules

### 5.1 Prefer layers over full-frame sprite sheets

Use a full-object sheet only when the entire object's visible geometry actually changes.

Default hierarchy:

    static base
    + packed emissive/status mask
    + local mechanical component animation
    + damage / alert / wreck layer
    + runtime VFX
    + audio

This avoids:

- geometry drift between generated frames;
- multiplied texture memory;
- animation seams;
- impossible hardpoint drift;
- art that visually changes physical dimensions.

### 5.2 Sprite-sheet packing

For component-local animation:

- frames use equal cell dimensions;
- frame order is left-to-right, then top-to-bottom;
- default layout is 4 columns;
- 8 frames -> 4x2;
- 10 frames -> 5x2 when texture width stays <= 4096;
- 12 frames -> 4x3;
- 16 frames -> 4x4;
- no per-frame trimming;
- every frame has identical local pivot.

### 5.3 Cadence classes

Use these defaults unless the per-object table overrides them:

| Class | Frames | Playback | Duration | Loop |
| --- | ---: | ---: | ---: | --- |
| slow status beacon | runtime mask | n/a | 2.0–4.0 s | yes |
| sensor sweep | runtime mask/shader or 12f | 8 fps | 1.5 s | yes |
| small mechanism | 8f | 8 fps | 1.0 s | no |
| heavy mechanism | 10f | 8 fps | 1.25 s | no |
| industrial cycle | 8f | 6 fps | 1.33 s | while active |
| fast weapon mechanism | 6f | 12 fps | 0.50 s | no |
| impact VFX | 8f | 16 fps | 0.50 s | no |
| guided detonation | 12f | 20 fps | 0.60 s | no |
| capital destruction | 16f | 16 fps | 1.00 s | no |
| jump major effect | 16f | 16 fps | 1.00 s | no |

Playback is presentation only and does not delay the authoritative state transition.

### 5.4 Deterministic phase

Looping idle masks use:

    phase = stable_hash(object_id) + presentation_tick

not wall-clock random initialization.

Identical objects therefore do not blink in synchrony, while deterministic captures remain
reproducible.

## 6. Marker and LOD standard

Every persistent release-facing ship, small craft, station and special-location role that can be
represented at strategic/distant scale receives a 128x128 marker master unless the accepted UI uses
an existing semantic icon instead.

Marker master:

- transparent 128x128 PNG;
- 80–104 px visible silhouette;
- 1-bit-readable silhouette at 32 px;
- faction/role must not depend on color alone;
- selected/hostile/disabled state should be runtime tint/stroke where possible, not duplicate files.

Do not make separate 32/64/128 source files. Build/downscale from the 128 master.

## 7. Major production ship package

### 7.1 Families

Per faction, the mandatory nine-family production set is:

1. corvette;
2. frigate;
3. destroyer;
4. cruiser;
5. battleship;
6. carrier;
7. freight;
8. tanker;
9. fleet_support.

Total: **18 major production hull families** across Empire and Industrial Union.

### 7.2 Canvas and files

Every family uses the already accepted:

- canvas: 768x512 RGBA;
- expected visible safe length: approximately 648–660 px for current normalized art;
- centered pivot;
- forward/right orientation.

Mandatory per family:

    base.png            768x512
    damage.png          768x512
    emissive.png        768x512
    wreck.png           768x512
    marker.png          128x128
    presentation.json
    provenance.json

Image count per family: **5 PNG**.

For 18 families: **90 PNG**.

Existing Empire base/damage/emissive files may be reused when they pass 23E audit. Industrial Union
base files are expected to be reused; missing layers are authored rather than replacing good bases.

### 7.3 Damage map behavior

damage.png is one aligned RGBA overlay, not separate light/medium/heavy full textures.

Recommended runtime mapping:

- light damage: 25–40% overlay contribution;
- medium damage: 45–70%;
- severe damage: 75–100% plus bounded runtime VFX;
- wreck: switch/transition to wreck.png.

Exact severity mapping comes from existing authoritative damage state, not from these percentages
alone.

### 7.4 Emissive

emissive.png contains only real visible emission sources:

- instruments;
- navigation/service lamps;
- sensor apertures that visibly emit;
- engine interior glow where it belongs to the hull rather than plume;
- hangar/berth lighting.

No painted armor highlight belongs in emissive.

### 7.5 Shared engine animation pack

Author one engine pack per faction visual family unless implementation-time propulsion content proves
that a shared neutral pack is more correct.

Per faction:

    engine_idle_strip.png
    engine_thrust_strip.png

Each strip:

- 8 frames;
- cell 256x128;
- sheet 2048x128;
- pivot at the nozzle/anchor edge;
- forward/right ship orientation means plume extends left from engine anchor;
- idle playback 8 fps / 1.0 s loop;
- thrust playback 12 fps / 0.67 s loop;
- runtime alpha/scale follows actual thrustFraction;
- animation can be paused entirely at true off state.

Two factions -> **4 engine-strip PNG**.

Major ship mandatory visual subtotal: **94 PNG**.

### 7.6 Per-family animation requirements

#### Corvette

Required:

- base/damage/emissive/wreck/marker;
- shared engine idle/thrust;
- RCS runtime VFX anchors;
- no mandatory mechanical sheet.

Optional only if actual hull art contains a justified mechanism:

- sensor panel local 8f / 6 fps / 1.33 s loop while scanning.

#### Frigate / recon-EW

Required:

- standard five PNG;
- shared engine pack;
- sensor/EW mask regions in emissive or presentation metadata.

Optional:

    mechanical_sensor_array.png

- 8 frames;
- local cell 256x256;
- 6 fps / 1.33 s;
- loop only during visible/allowed scan state.

#### Destroyer / missile-PD

Required:

- standard five PNG;
- launch/PD anchors.

Optional when the accepted art exposes moving VLS doors:

    mechanical_vls_door.png

- 6 frames;
- local cell 256x256;
- 12 fps / 0.50 s;
- one-shot open/close around real launch event.

Do not fabricate a door merely because missiles exist.

#### Cruiser

Required:

- standard five PNG;
- no mandatory mechanical sheet;
- multiple anchored weapon/VFX regions.

#### Battleship

Required:

- standard five PNG;
- no mandatory continuous geometry animation;
- destruction uses the capital destruction VFX class.

Optional:

- one justified heavy shutter/turret-cover mechanism, 10f / 8 fps / 1.25 s, only if physically present.

#### Carrier

Required:

- standard five PNG;
- bay/approach emissive zones.

Mandatory if production carrier visibly has a door/shutter:

    mechanical_hangar_door.png

- 10 frames;
- local cell 512x512;
- sheet 2560x1024 as 5x2;
- 8 fps / 1.25 s one-shot;
- reverse frames for close;
- triggered by real launch/recovery operation.

If the final accepted carrier has no visible moving door, this file is omitted and the manifest
records N/A rather than inventing motion.

#### Freight

Required:

- standard five PNG;
- docking/transfer anchors;
- no mandatory mechanical loop.

Optional:

- cargo clamp/handling local sheet if visible hardware exists: 8f, 256x256, 6 fps.

#### Tanker

Required:

- standard five PNG;
- transfer/manifold anchors.

Optional:

    mechanical_transfer_boom.png

- 8 frames;
- 256x256;
- 6 fps / 1.33 s one-shot deploy;
- reverse retract;
- only when boom hardware is visibly present and authoritative transfer is active.

#### Fleet support

Required:

- standard five PNG;
- service/repair anchor metadata.

Recommended:

    mechanical_service_arm.png

- 8 frames;
- 512x512;
- 6 fps / 1.33 s deploy;
- hold active pose during real operation;
- reverse on completion/cancel.

## 8. Carrier small-craft package

### 8.1 Physical identity

Current production authority has:

- one Empire small-craft physical hull;
- one Industrial Union small-craft physical hull;
- three fits per faction: interceptor, defence, strike.

Do not author six unrelated base hulls.

### 8.2 Canvas

Per faction base:

- 512x256 RGBA;
- forward/right;
- 432–456 px preferred visible length;
- 24 px minimum transparent margin;
- centered pivot.

### 8.3 Files

Per faction:

    base.png                 512x256
    damage.png               512x256
    emissive.png             512x256
    wreck.png                512x256
    fit_interceptor.png      512x256
    fit_defence.png          512x256
    fit_strike.png           512x256
    marker_interceptor.png   128x128
    marker_defence.png       128x128
    marker_strike.png        128x128
    engine_idle_strip.png    8 x 128x64 -> 1024x64
    engine_thrust_strip.png  8 x 192x64 -> 1536x64
    presentation.json
    provenance.json

PNG count per faction: **12**.  
Two factions: **24 PNG**.

Playback:

- idle engine: 10 fps / 0.8 s loop;
- thrust engine: 14 fps / 0.57 s loop;
- runtime intensity follows authoritative thrust;
- no separate shield sprite sheet: defence-craft shield remains runtime VFX driven by real shield state.

## 9. Station production standard

### 9.1 Current release archetypes

The current Stage-18 release-facing station archetypes are:

1. mining_outpost;
2. volatile_depot;
3. refinery_complex;
4. industrial_station;
5. high_tech_hub;
6. trade_logistics_hub;
7. naval_ordnance_depot;
8. frontier_multipurpose.

Both sovereign factions require a production presentation path unless 23E.0 proves a specific
archetype can never be faction-owned in the RC campaign.

Baseline planning therefore assumes:

    8 archetypes x 2 factions = 16 station visual packages

### 9.2 Station canvas

Mandatory source canvas:

- 1024x1024 RGBA;
- centered pivot;
- 820–900 px target maximum visible span;
- minimum 32 px transparent safe margin;
- strict top-down orthographic;
- no baked visiting ships;
- no baked background.

Runtime physical scale comes from station authority, not the 1024 canvas.

### 9.3 Mandatory station files

Per station visual package:

    base.png             1024x1024
    damage.png           1024x1024
    emissive_groups.png  1024x1024 packed mask
    alert_mask.png       1024x1024 grayscale/RGBA mask
    wreck.png            1024x1024
    marker.png           128x128
    presentation.json
    provenance.json

Mandatory PNG count per station: **6**.

Sixteen station variants -> **96 PNG** before mechanical sheets.

### 9.4 Packed emissive-groups mask

emissive_groups.png uses channels as logical animation groups:

- R = service / interior work lights;
- G = navigation / exterior safety beacons;
- B = berth / operation-state lights;
- A = sensor / command / special-status group.

presentation.json assigns actual faction/status colors and phase curves.

This one packed mask replaces a four-frame full-station idle sprite sheet for ordinary light
animation.

Default idle timing:

- R service pulse: 4.0 s asymmetric cycle;
- G nav beacon: 2.0 s cycle;
- B berth/status: state-driven, not always looping;
- A sensor: 1.5–3.0 s sweep/pulse when allowed.

### 9.5 Alert mask

alert_mask.png is not permanently visible.

It is activated only by an authoritative warning/emergency state and normally uses Emergency Red or
approved warning amber.

Alert animation:

- 1.0 s pulse for warning;
- 0.5 s pulse for critical;
- Reduced VFX uses reduced intensity, not removal.

## 10. Per-station mechanical specification

Component sheets are local, transparent, and use the station-local component pivot.

### 10.1 Mining outpost

Mandatory visual package:

- six station PNG;
- extraction/contact VFX binding.

Recommended mechanical sheets per faction:

    mechanical_extractor.png
    mechanical_handling_arm.png

mechanical_extractor.png:

- 8 frames;
- 256x256 cell;
- 4x2 sheet = 1024x512;
- 6 fps / 1.33 s cycle;
- loop only while extraction is active.

mechanical_handling_arm.png:

- 8 frames;
- 256x256;
- 6 fps deploy / hold / reverse retract;
- omit if final station art has no external handling arm.

Maximum planned sheets per faction: 2.

### 10.2 Volatile / water depot

Recommended:

    mechanical_transfer_boom.png

- 8 frames;
- 512x512 cell;
- 4x2 sheet = 2048x1024;
- 6 fps / 1.33 s deploy;
- hold while transfer active;
- reverse retract.

No animated visible liquid level.

Planned sheets per faction: 1.

### 10.3 Refinery complex

Recommended:

    mechanical_radiator_shutter.png

- 8 frames;
- 256x256;
- 6 fps / 1.33 s;
- state-driven open/close only if the accepted refinery art has such hardware.

Process animation otherwise comes from emissive_groups and runtime VFX, not moving the entire
refinery.

Planned sheets per faction: 1.

### 10.4 Industrial station

Recommended:

    mechanical_gantry.png
    mechanical_assembly_rig.png

mechanical_gantry.png:

- 8 frames;
- 512x512;
- 6 fps / 1.33 s operation cycle;
- only while a real job uses that work zone.

mechanical_assembly_rig.png:

- 8 frames;
- 256x256;
- 6 fps / 1.33 s;
- loop while active.

Planned sheets per faction: 2.

### 10.5 High-tech manufacturing hub

Recommended:

    mechanical_precision_rig.png

- 8 frames;
- 256x256;
- 8 fps / 1.0 s active cycle;
- subtle displacement;
- no large theatrical holographic apparatus.

Planned sheets per faction: 1.

### 10.6 Trade / logistics hub

Recommended:

    mechanical_dock_clamp.png
    mechanical_berth_door.png

dock clamp:

- 8 frames;
- 256x256;
- 8 fps / 1.0 s one-shot.

berth door:

- 10 frames;
- 512x512;
- 8 fps / 1.25 s one-shot;
- reverse close.

Only the active berth component animates.

Planned sheets per faction: 2.

### 10.7 Naval ordnance depot

Recommended:

    mechanical_magazine_door.png
    mechanical_loading_arm.png

magazine door:

- 10 frames;
- 512x512;
- 8 fps / 1.25 s one-shot.

loading arm:

- 8 frames;
- 512x512;
- 6 fps / 1.33 s deploy;
- hold during actual handling;
- reverse retract.

The station never fires weapons merely because it contains ordnance.

Planned sheets per faction: 2.

### 10.8 Frontier multipurpose station

Recommended:

    mechanical_service_arm.png
    mechanical_module_door.png

service arm:

- 8 frames;
- 256x256;
- 6 fps deploy / hold / retract.

module door:

- 8 frames;
- 256x256;
- 8 fps / 1.0 s one-shot.

Only installed/active capability modules may animate.

Planned sheets per faction: 2.

### 10.9 Station mechanical-sheet budget

Per faction planned maximum:

- mining: 2;
- volatile depot: 1;
- refinery: 1;
- industrial: 2;
- high-tech: 1;
- trade/logistics: 2;
- ordnance: 2;
- frontier: 2.

Total planned mechanical sheets per faction: **13**.  
Two factions: **26 PNG sheets**.

Station visual planning subtotal:

- mandatory station package PNG: 96;
- planned mechanical sheets: 26;
- total: **122 PNG**.

A sheet is removed only when the accepted production design lacks that mechanism or runtime authority
cannot lawfully trigger it. Do not replace an omitted sheet with decorative motion merely to preserve
the count.

## 11. Station faction-specific motion targets

### 11.1 Empire

Idle:

- low density;
- slow ordered service sequences;
- grouped docking lights;
- sensor emphasis around protected core/command structures;
- visible weight in door/arm motion.

Default heavy mechanism easing:

- 15% opening acceleration;
- 65% steady motion;
- 20% settling;
- no bouncy overshoot.

### 11.2 Industrial Union

Idle:

- modular repeated light cadence;
- berth-by-berth work-state pattern;
- clear parallel production zones;
- standardized safety rhythm.

Default work mechanism:

- slightly faster start/stop than Empire;
- repeated cells may phase-shift by stable module index;
- no decorative perpetual crane motion.

Faction animation may differ in feel but never changes operation completion time.

## 12. Resource body production specification

Current minimum presentation has four roles:

1. carbonaceous;
2. water_ice;
3. metallic;
4. mineral_silicate.

### 12.1 Source masters

Author/retain one master per role:

    carbonaceous.png
    water_ice.png
    metallic.png
    mineral_silicate.png

Each:

- 512x512 RGBA;
- 380–440 px visible diameter/extent;
- centered;
- no starfield;
- no resource quantity text;
- no glow used as value coding.

Source masters: **4 PNG**.

### 12.2 Runtime atlas

Build:

    resource_bodies_atlas.png

- 1024x1024;
- 2x2 cells of 512x512;
- exact source images copied without resampling.

Runtime atlas: **1 PNG**.

### 12.3 Markers

One 128x128 marker per known material-class presentation:

    marker_carbonaceous.png
    marker_water_ice.png
    marker_metallic.png
    marker_mineral_silicate.png

Markers: **4 PNG**.

Resource subtotal: **9 PNG**.

### 12.4 Animation

No mandatory idle frame animation.

Rationale: current simulation does not need art-authored asteroid rotation to imply physical angular
state.

Allowed close-zoom presentation:

- water/ice: restrained runtime specular pulse, 4–8 s;
- metallic: restrained specular pulse, 5–10 s;
- others: static.

Mining animation belongs to mining VFX, not the resource base.

## 13. Special locations

Current Stage-20H kinds:

1. energetic_anomaly;
2. escort_hull_derelict;
3. resonant_resource_phenomenon.

### 13.1 Energetic anomaly

Files:

    anomaly_loop.png
    marker.png

anomaly_loop.png:

- 16 frames;
- 256x256 cell;
- 4x4 sheet = 1024x1024;
- 12 fps / 1.33 s loop;
- transparent;
- no physical object silhouette requirement;
- visible only after allowed discovery/classification state.

marker.png:

- 128x128.

Count: **2 PNG**.

### 13.2 Escort-hull derelict

Files:

    base.png
    marker.png

base.png:

- 768x512;
- may derive visually from the accepted escort hull family but must be a dedicated persistent wreck
  presentation;
- no navigation lights;
- no fresh explosion baked in.

marker.png:

- 128x128.

Salvage animation uses shared salvage VFX.

Count: **2 PNG**.

### 13.3 Resonant resource phenomenon

Files:

    resonance_overlay.png
    marker.png

resonance_overlay.png:

- 12 frames;
- 256x256 cell;
- 4x3 sheet = 1024x768;
- 8 fps / 1.5 s loop;
- drawn around/over the linked resource occurrence;
- does not include a second resource body.

marker.png:

- 128x128.

Count: **2 PNG**.

Special-location subtotal: **6 PNG**.

## 14. Ordnance production specification

Current production identities:

1. fragmentation_shell;
2. guided_micro_missile;
3. guided_missile_a;
4. guided_rocket_a;
5. guided_rocket_b;
6. guided_torpedo;
7. interceptor_missile;
8. kinetic_penetrator_a;
9. kinetic_penetrator_b;
10. kinetic_shell.

Body sprites are static. Motor/exhaust/detonation belongs to runtime VFX.

### 14.1 Canvas sizes

| Ordnance | Source canvas |
| --- | ---: |
| fragmentation_shell | 64x32 |
| guided_micro_missile | 96x48 |
| guided_missile_a | 128x64 |
| guided_rocket_a | 128x64 |
| guided_rocket_b | 128x64 |
| guided_torpedo | 192x96 |
| interceptor_missile | 128x64 |
| kinetic_penetrator_a | 96x48 |
| kinetic_penetrator_b | 96x48 |
| kinetic_shell | 64x32 |

Rules:

- forward/right;
- genuine alpha;
- 6 px minimum transparent margin for <=96 assets;
- 8 px for >=128 assets;
- no baked trail;
- no baked glow halo;
- no baked impact.

Mandatory ordnance art: **10 PNG**.

Existing accepted ordnance sprites may be retained if they pass these readability/alpha rules; do not
resample solely to satisfy a nominal canvas if that would reduce quality. In that case the existing
dimension becomes a documented exception in the gap matrix.

## 15. Shared VFX production pack

VFX is semantic and event-driven. These files are shared unless a physically justified drive/weapon
family requires a separate authored variant.

### 15.1 Propulsion / motion

1. main_engine_idle.png
   - 8 frames;
   - 256x128;
   - 2048x128 strip;
   - 8 fps loop.

2. main_engine_thrust.png
   - 8 frames;
   - 256x128;
   - 2048x128;
   - 12 fps loop.

3. rcs_burst.png
   - 8 frames;
   - 128x128;
   - 4x2 -> 512x256;
   - 16 fps / 0.5 s one-shot.

### 15.2 Kinetic

4. kinetic_muzzle.png
   - 6f, 128x128, 12 fps / 0.5 s.

5. kinetic_armor_impact.png
   - 8f, 192x192, 16 fps / 0.5 s.

6. kinetic_penetration.png
   - 8f, 192x192, 16 fps / 0.5 s.

### 15.3 Beam

7. beam_source_flash.png
   - 6f, 128x128, 12 fps.

8. beam_endpoint_material.png
   - 8f, 192x192, 16 fps.

The beam line/body itself is preferably runtime drawn from authoritative source/end positions rather
than a sprite sheet.

### 15.4 Shield

9. shield_contact.png
   - 8f, 256x256, 16 fps / 0.5 s.

10. shield_overload.png
    - 12f, 256x256, 16 fps / 0.75 s.

### 15.5 Guided / PD

11. guided_launch.png
    - 8f, 192x192, 16 fps / 0.5 s.

12. guided_detonation.png
    - 12f, 256x256, 20 fps / 0.6 s.

13. guided_intercept.png
    - 10f, 192x192, 20 fps / 0.5 s.

14. decoy_activation.png
    - 8f, 192x192, 12 fps / 0.67 s.

### 15.6 Damage / destruction

15. subsystem_failure.png
    - 8f, 192x192, 16 fps.

16. ship_destruction_standard.png
    - 16f, 384x384, 16 fps / 1.0 s.

17. ship_destruction_capital.png
    - 16f, 512x512, 16 fps / 1.0 s.

18. wreck_residual_hotspot.png
    - 8f, 128x128, 6 fps / 1.33 s;
    - used only for bounded post-destruction interval, not indefinitely.

### 15.7 Operations

19. mining_contact.png
    - 8f, 192x192, 12 fps.

20. salvage_work.png
    - 8f, 192x192, 12 fps.

21. repair_weld.png
    - 8f, 128x128, 12 fps.

22. construction_work.png
    - 8f, 192x192, 12 fps.

23. cargo_transfer_indicator.png
    - 8f, 128x128, 8 fps.

24. docking_contact.png
    - 8f, 128x128, 16 fps / 0.5 s one-shot.

### 15.8 Jump

25. jump_spool.png
    - 16f, 512x512, 16 fps / 1.0 s loop/phase controlled by real spool state.

26. jump_departure.png
    - 12f, 512x512, 16 fps / 0.75 s one-shot.

27. jump_arrival.png
    - 12f, 512x512, 16 fps / 0.75 s one-shot.

Baseline shared VFX sheets: **27 PNG**.

A shader/procedural implementation may replace an individual sheet when it preserves the same
semantic contract and reduces memory/performance cost. The manifest still records the semantic VFX
ID.

## 16. Wreck art rule

Every major production ship family and every station visual package receives a persistent wreck
presentation.

Wreck is:

- same pivot/orientation;
- same physical-scale binding;
- visibly nonfunctional;
- no normal service/engine emissive;
- no baked active flames;
- no endless explosion;
- readable as the original family/faction where practical.

A wreck is not required for transient ordnance bodies.

Current planned wreck images are already counted inside:

- 18 major ship packages;
- 2 small-craft faction bases;
- 16 station packages;
- 1 Stage-20H escort derelict has its own dedicated art.

## 17. Character production outputs

Current release core has 12 recurring characters: six per faction.

### 17.1 Master portrait/character art

For each current recurring character:

    <stable_character_id>_master.png

Recommended source:

- 1024x1536 RGBA;
- transparent background;
- full or three-quarter body;
- canonical Character Master Prompt;
- faction/role readability;
- no text.

Total master PNG: **12**.

### 17.2 Runtime roster atlases

Preserve current runtime contract unless 23B/23C explicitly changes portrait layout:

    empire/character_roster.png
    industrial_union/character_roster.png

Each:

- 336x84;
- six 56x84 cells;
- transparent.

Runtime atlas PNG: **2**.

Character visual subtotal: **14 PNG**.

Expression sheets are not mandatory unless the accepted UI actually consumes them.

## 18. Sector backgrounds

Keep the accepted four deterministic backgrounds unless 23E.0 finds a quality/provenance blocker.

Runtime format remains the current packaged background format.

23E work:

- provenance/license review;
- readability grading under grid/markers;
- consistent dimming/contrast;
- no arbitrary increase in background count.

No additional background art is required by this specification.

## 19. UI icon production format

The exact semantic icon **count cannot be frozen before the accepted 23B/23C information architecture
and settings surface are final**. This is an intentional dependency, not a missing design decision.

Once 23B/23C are accepted, 23E.0 freezes the exact icon list.

Technical standard per semantic icon:

- source master: SVG preferred, or 128x128 transparent PNG if vector source is inappropriate;
- runtime atlas cell: 64x64;
- 8 px logical safe margin at 64x64;
- monochrome-readable;
- no baked selected/disabled/warning variants unless geometry changes;
- state color/tint comes from UI theme/accessibility layer.

Required runtime atlas:

    ui_semantic_icons.png

Atlas dimensions are selected after final icon count, power-of-two only when required by runtime
tooling. Do not force a huge empty atlas just for power-of-two aesthetics.

## 20. Exact world-object visual backlog summary

Baseline planned PNG count from this spec:

| Domain | PNG count |
| --- | ---: |
| 18 major ship families | 90 |
| major ship faction engine strips | 4 |
| 2 faction small-craft packages | 24 |
| 16 station mandatory packages | 96 |
| station mechanical sheets | 26 |
| resource-body masters/atlas/markers | 9 |
| current special locations | 6 |
| current ordnance body sprites | 10 |
| shared VFX sheets | 27 |
| current core character masters/atlases | 14 |
| **Baseline planned total** | **306** |

Important:

- this is a production backlog count, not a claim that 306 new files must be generated from scratch;
- many existing files are reused and reviewed;
- existing accepted Empire/Union bases, Empire damage/emissive, ordnance, resource atlas, characters and
  backgrounds reduce actual new-authoring work;
- conditional mechanical sheets are omitted when the final design lacks the corresponding mechanism;
- the exact UI icon count is intentionally excluded until 23B/23C freezes the semantic surface.

## 21. Audio production format

### 21.1 Runtime formats

Short latency-critical SFX:

- WAV PCM;
- 48 kHz;
- 16-bit;
- mono unless stereo image is semantically useful;
- trim leading silence;
- no baked master reverb that prevents spatial/mix control.

Long ambience/music:

- OGG Vorbis;
- 48 kHz;
- stereo;
- seamless loop where specified.

Source masters may be stored externally or under a provenance workflow at higher bit depth, but every
shipped file must have license/provenance.

### 21.2 Loudness/mix preparation

Final mastering is tuned in-engine, but source discipline should target:

- critical warnings: clear transient, peak below clipping, intentionally mix-priority protected;
- UI: short, low-tail, non-fatiguing;
- weapon impacts: variant-matched peak levels so randomization does not create accidental loudness
  advantage;
- ambience: loop-safe, low dynamic dominance;
- faction stingers: restrained, not louder than critical warning hierarchy.

No file is allowed to normalize the entire mix by itself.

## 22. Minimum audio variant budget

The following is the **minimum unique-file budget**, not the maximum.

### 22.1 UI — 28 short files

- focus: 3;
- confirm: 3;
- cancel/back: 2;
- command accepted: 3;
- command rejected: 3;
- warning: 3;
- critical warning: 3;
- notification: 3;
- mission update: 2;
- invalid/insufficient resource: 3.

Subtotal: **28**.

### 22.2 Ship/system — 26 files

- light engine loop variants: 3;
- medium engine loop variants: 3;
- heavy engine loop variants: 3;
- RCS burst: 3;
- machinery/system loop: 3;
- thermal warning: 2;
- low propellant: 2;
- power failure: 2;
- damage alarm: 3;
- critical system alarm: 2.

Subtotal: **26** if all listed variants are separately authored.

To avoid ambiguity, Stage-23E baseline is **26**.

### 22.3 Combat — 51 files

- kinetic fire light/medium/heavy: 3 variants each = 9;
- beam fire: 3;
- guided launch: 4;
- PD/interceptor fire: 4;
- shield impact: 4;
- armor impact: 4;
- penetration: 4;
- interceptor kill: 3;
- guided detonation: 4;
- subsystem failure: 3;
- destruction light/medium/heavy: 3 variants each = 9.

Subtotal: **51**.

### 22.4 Operations — 34 files

- docking: 3;
- undocking: 3;
- cargo transfer loop: 2;
- mining machinery/contact: 3;
- salvage: 3;
- repair/refit: 3;
- construction: 3;
- carrier launch: 3;
- carrier recovery: 3;
- jump spool loop: 2;
- jump departure: 3;
- jump arrival: 3.

Subtotal: **34**.

### 22.5 Communications/events — 15 files

- incoming contact: 3;
- mission update: 3;
- diplomatic event: 3;
- distress/security alert: 3;
- major objective/state transition: 3.

Subtotal: **15**.

### 22.6 Ambience — 12 loops

- deep/system-space selected-ship ambience: 2;
- trade/logistics station: 2;
- industrial/refinery: 2;
- shipyard: 2;
- military base/depot: 2;
- energetic anomaly: 1;
- resonant phenomenon: 1.

Subtotal: **12**.

### 22.7 Faction stingers — 4 files

- Empire identity/state stingers: 2;
- Industrial Union identity/state stingers: 2.

Subtotal: **4**.

### 22.8 Minimum audio file count

    UI               28
    ship/system      26
    combat           51
    operations       34
    comms/events     15
    ambience         12
    faction stingers  4
    -------------------
    total            170

Baseline minimum: **170 shipped audio files**.

This number may decrease if a parametric runtime synthesis system legitimately replaces a family, but
a reduction must preserve variety, readability and the semantic event contract. It may increase after
final mix review.

## 23. Audio object binding

Each production object presentation.json must list semantic audio cues, not raw filenames.

Example logical bindings:

    engine.loop
    engine.thrust
    damage.warning
    docking.start
    docking.complete
    transfer.loop
    mining.contact
    repair.work
    ordnance.launch
    ordnance.detonate
    destruction.major

AudioDirector resolves the semantic cue to variants and mix policy.

No simulation code selects a random sound file directly.

## 24. presentation.json minimum schema

Each object package records at least:

    asset_id
    authoritative_content_id_or_role
    faction
    domain
    canvas_width
    canvas_height
    pivot_x
    pivot_y
    orientation
    visible_bounds
    base_path
    damage_path
    emissive_path_or_mask
    alert_mask_path
    wreck_path
    marker_path
    engine_pack_id
    anchors
    animation_clips
    vfx_bindings
    audio_bindings
    lod_policy
    accessibility_policy
    provenance_id
    version

animation_clips entry includes:

    semantic_id
    sheet_path
    frame_width
    frame_height
    frame_count
    fps
    loop
    trigger_state_or_event
    hold_frame
    reverse_on_exit
    local_pivot

## 25. provenance.json minimum schema

At least:

    asset_id
    version
    created_at
    source_type
    source_reference
    generation_prompt_or_brief_reference
    visual_bible_version
    postprocess_steps
    author_or_tool
    reviewer
    license
    redistribution_status
    checksum
    accepted_for_release

For generated assets, retain the production prompt/brief or a stable reference to it.

## 26. Generation prompt package rule

For every new recurring visual family, preserve the existing project rule:

- generate/draw five genuinely different candidate images;
- separate images, not a collage;
- same role and physical envelope;
- review engineering/faction/readability;
- select one;
- record rejection reason;
- convert only the accepted candidate into production layers.

The five-candidate rule applies to selection of a new visual family, not to every animation frame.

Animation frames should be authored from the accepted base/component so geometry remains stable.

## 27. Animation generation/authoring rule

Do **not** independently prompt an image generator for frame 1, frame 2, frame 3, frame 4 and hope
that they align.

Preferred process:

1. accept the base sprite;
2. isolate the moving/emissive component;
3. author or generate the component states against fixed geometry;
4. normalize every frame to one local pivot;
5. compare difference images;
6. reject any frame that changes unrelated geometry;
7. pack frames;
8. validate alpha and bounds;
9. bind to state/event;
10. capture runtime playback.

For light-only animation, do not generate frames at all; use emissive_groups.png + runtime phase.

## 28. Memory and atlas policy

23E must produce assets in a way 23F can optimize.

Rules:

- do not preload every 1024 station texture for the whole galaxy;
- package by faction/domain/role to permit lazy loading;
- mip world sprites;
- UI atlas separately from world art;
- VFX atlas may group equal-size cells by family;
- never upscale source art at runtime to create fake detail;
- keep source masters and runtime artifacts clearly distinguished if an offline build step is added.

23F owns measured texture-memory budgets and may change packing/compression, not semantic coverage.

## 29. Reduced VFX / Reduced Motion asset behavior

No duplicate full asset pack is required.

Reduced VFX:

- lower particle counts;
- lower glow intensity;
- fewer cosmetic secondary events;
- preserve event silhouette.

Reduced Motion:

- freeze nonessential idle mechanism at stable pose;
- keep state lights;
- shorten or simplify decorative loops;
- keep one-shot operational state transition legible;
- no required screen shake.

Audio category mutes do not alter visual authority.

## 30. Object production checklist

For every row in stage23e_asset_gap_matrix.tsv:

- [ ] stable role/content ID verified;
- [ ] final source canvas chosen;
- [ ] base approved;
- [ ] alpha/pivot/orientation pass;
- [ ] damage layer pass;
- [ ] emissive/mask pass;
- [ ] wreck pass if persistent object;
- [ ] marker pass;
- [ ] required local mechanical sheets authored;
- [ ] frame count/FPS matches this spec or documented exception;
- [ ] anchors validated;
- [ ] VFX semantic IDs bound;
- [ ] audio semantic IDs bound;
- [ ] LOD behavior captured;
- [ ] Reduced VFX/Motion captured;
- [ ] save/load materialization does not replay transient clips;
- [ ] provenance/license complete;
- [ ] deterministic capture accepted.

## 31. Recommended production order

1. freeze the asset-gap matrix against current repo;
2. reuse/approve existing Empire major-ship layers;
3. author Industrial Union missing major-ship layers;
4. author two small-craft base families + six fit overlays;
5. author one station archetype for both factions as the pipeline pilot;
6. validate station packed-emissive and mechanical-component runtime path;
7. produce the remaining fourteen station packages;
8. close resource/special-location art;
9. audit/normalize ordnance bodies;
10. build shared VFX pack;
11. close wreck art;
12. close character source masters/crops where needed;
13. freeze 23B/23C icon list and build UI atlas;
14. build audio foundation and 170-file minimum cue library;
15. run deterministic capture matrix;
16. hand off measured memory/performance optimization to 23F.

## 32. Pilot recommendation

Use these two stations as the first end-to-end production pilot:

- Empire naval_ordnance_depot;
- Industrial Union industrial_station.

They exercise the two faction motion languages and most of the required system:

- large 1024 source art;
- emissive group animation;
- alert mask;
- local heavy/light mechanisms;
- docking/handling;
- ambient audio;
- operation audio;
- state-driven animation;
- LOD marker;
- damage/wreck;
- provenance;
- deterministic capture.

The four-frame station examples previously generated in design discussion should be treated as visual
communication only. Production implementation should rebuild them using this layered contract rather
than shipping the generated 2x2 sheet as-is.

## 33. Definition of Done for the production specification

This specification is satisfied at Stage-23E completion only when:

- every MUST_SHIP release-facing world object has an explicit package row;
- every required file exists or is explicitly marked N/A with a justified design/authority reason;
- current accepted art is reused where it already passes quality;
- no full-frame idle sheet exists merely to blink lights;
- every mechanical clip has one lawful trigger;
- every transient VFX has an event/state source;
- every audio cue has an observed semantic event;
- all animation frame geometry remains aligned;
- all persistent wrecks do not replay destruction on materialization;
- actual gameplay-scale captures prove readability;
- exact-head CI and the parent Stage-23E Definition of Done pass.
