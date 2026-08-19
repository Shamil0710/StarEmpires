# Stage 20A — Representative-Ship Scale Calibration Kickoff

**Status:** ACTIVE  
**Parent:** Stage 20 — Physical World Generation / Discovery  
**Started:** 2026-08-19

## 1. Purpose

Stage 20A begins the implementation of the accepted Stage-20 physical-world-generation plan. Its job is to derive a versioned, machine-readable spatial calibration profile from already accepted ship, sensor, weapon, station and logistics capabilities before procedural generation starts choosing world distances.

The generator must not invent arbitrary map scale and later compensate with hidden movement, sensor, logistics or weapon multipliers.

## 2. Immediate implementation target

The Stage-20A implementation builds a deterministic calibration pipeline that evaluates representative civilian and military hull/fit profiles across canonical route and tactical geometries.

The resulting profile must become the input to later Stage-20 topology, system-geometry, resource-geography and world-quality gates.

## 3. Required representative profiles

The initial reference set follows the canonical Stage-20 plan:

- early civilian freighter;
- loaded bulk freighter;
- mining ship;
- patrol/corvette;
- escort destroyer;
- cruiser;
- capital combatant;
- fleet tanker/logistics support;
- carrier/aviation group where supported by existing authored content.

Where an exact production content definition does not yet exist, Stage 20A must not fabricate a final canonical hull. It may use an explicitly provisional representative profile derived from accepted physical capability ranges and must mark it as provisional for Stage 22 content review.

## 4. First machine-readable outputs

At minimum the Stage-20A profile must expose deterministic bands or measured samples for:

```text
local travel time
braking time / distance
required delta-v
reaction-mass fraction
jump spool / transit / cooldown cadence
sensor detection / classification / track / fire-control geometry
weapon effectiveness / time-of-flight geometry
PD / interceptor geometry
formation spacing
station physical footprint / spacing
jump-arrival stand-off
far-coordinate numerical precision budget
materialization / LOD distance bands
regional multi-hop route cadence
```

The profile must preserve SI units internally.

## 5. Authority and dependency rules

Stage 20A consumes accepted production capability; it does not redefine it.

```text
Stage 17.5 ship/sensor/weapon capability
+ Stage 18 infrastructure/logistics capability
+ Stage 19 tactical/response behavior
→ Stage 20A calibration profile
→ Stage 20B+ world generation
```

No player-only scale rules, no hidden travel multipliers, no screen-space weapon/sensor ranges, no system-edge teleport and no emergency resource placement are permitted.

## 6. UI boundary during Stage 20

The project has separately recorded `docs/ui_ux_debt_and_polish_contract.md`.

Stage 20 may introduce diagnostic maps, calibration tables and minimal interaction required to validate generated worlds, but production UI/UX redesign is deliberately deferred. The implementation priority is authoritative world generation and measurable quality, not polishing temporary validation screens.

## 7. Stage 20A.1 — representative local propulsion envelope

The first implementation slice introduced:

- `Stage20ScaleCalibrationProfile`, a versioned and deterministically ordered machine-readable calibration profile;
- `Stage20ScaleCalibrationCalculator`, which consumes `DerivedShipState` rather than duplicating production mass, thrust, mass-flow or rocket-equation calculations;
- the current production escort-destroyer fit with its accepted full reaction-mass load as the first directly authored representative profile;
- a mass-varying equal-delta-v rest-to-rest calibration manoeuvre exposing reaction-mass fraction, burn cadence, peak speed, acceleration distance, braking distance and characteristic no-coast transfer distance;
- deterministic and physical-sensitivity tests proving that changed reaction-mass capability changes the resulting spatial envelope.

The characteristic rest-to-rest measurement is a calibration scale, not a maximum reachable distance. Longer transfers may add coast time.

## 8. Stage 20A.2 — provisional representative set and local route bands

The second implementation slice expands propulsion calibration without promoting benchmark seeds into production ship content.

`data/calibration/stage20-representative-propulsion-v1.json` packages the five already accepted Ship Mathematics v1.0 reference designs with explicit provenance, schema/version metadata, `PROVISIONAL_ACCEPTED_REFERENCE` status and mandatory Stage-22 review. The loader physically closes each reference against its accepted departure mass, acceleration and delta-v before it may enter Stage-20 calibration.

The effective current representative set is:

- `ESCORT_DESTROYER` — current production engineering fit; this deliberately supersedes the older v1.0 escort reference;
- `TORPEDO_CORVETTE` — provisional accepted reference;
- `BATTLESHIP` — provisional accepted reference;
- `BULK_FREIGHTER_LOADED` — provisional accepted reference;
- `FLEET_TANKER_LOADED` — provisional accepted reference.

Early civilian freighter, mining ship, cruiser and carrier/aviation profiles remain unresolved rather than fabricated. They may enter Stage 20 only when backed by accepted production content or an explicitly accepted physical reference.

`Stage20RouteCalibrationCalculator` replaces the older constant-acceleration world-scale approximation with a variable-mass route calculation consistent with the Stage-20A.1 propulsion envelope:

- short routes solve the partial reaction-mass load needed for accelerate/flip/brake with no coast;
- the flip occurs at the geometric-mean mass so acceleration and braking spend equal delta-v;
- long routes use the full symmetric burn and add only physical coast time at the resulting peak speed;
- travel time, braking geometry, required delta-v and consumed reaction-mass fraction remain measurable outputs.

The profile currently probes `10 Mm`, `100 Mm`, `1 Gm` and `10 Gm`. These distances are inherited sensitivity probes from the accepted v0.9/v1.0 world-scale evidence. They are **not** fixed future system sizes or generator constants. Stage 20B+ will use calibrated capability/pacing constraints to choose generated geometry.

The versioned profile emits both per-representative route samples and aggregate route bands across the current representative set. Production versus provisional authority and exact provenance remain machine-visible in every propulsion envelope.

## 9. Stage 20A.3 — FTL translated-mass compatibility and one-edge cadence

The third implementation slice adds a separate versioned FTL calibration layer because a production `FTL_JUMP` module has not yet replaced the accepted Ship Mathematics v1.0 reference drive.

`data/calibration/stage20-ftl-jump-reference-v1.json` preserves the accepted reference-drive inputs and closure evidence:

```text
max translated mass       100,000,000 kg
translation energy        25,000 J/kg
charge input power        5,000,000,000 W
charge efficiency         0.80
cooldown                  90 s
reference destroyer mass  21,927,000 kg
reference energy          548,175,000,000 J
reference spool           137.04375 s
example edge transit      30 s
```

`Stage20FtlCalibrationReferenceLoader` closes energy and spool against those accepted equations before use. `Stage20FtlCalibrationProfile` then evaluates every current representative departure mass against one reference drive while keeping ship-mass provenance and FTL-law provenance separate.

Ordinary FTL semantics are machine-recorded as `NEIGHBOR_EDGE_ONLY`: one jump traverses one neighboring-system topology edge. The 30-second value is an accepted **example edge transit**, not a universal range/time law and not permission for direct multi-hop travel. Generated edge-transit distributions remain Stage-20 world data to be authored/calibrated later.

The current reference drive is mass-compatible with:

- `TORPEDO_CORVETTE`;
- current production `ESCORT_DESTROYER`.

It is explicitly incompatible with the current representative departure masses for:

- `BATTLESHIP`;
- `BULK_FREIGHTER_LOADED`;
- `FLEET_TANKER_LOADED`.

Stage 20 does not hide that result by assuming drive multiplicity, mass bypass, special civilian rules or an increased mass limit. Compatible samples expose translation energy, spool and `spool + edge transit + cooldown` ready-again cadence; incompatible samples keep those derived fields absent instead of extrapolating the accepted drive outside its domain.

The FTL resource also keeps three unresolved gaps explicit:

- production FTL module not yet authored;
- generated edge-transit distribution not yet world-authored;
- numeric FTL heat coefficient absent from the v1.0 reference despite the architectural requirement that FTL pays heat.

## 10. Stage 20A.4 — production sensor/signature spatial calibration

The fourth implementation slice connects the existing production sensing chain to spatial calibration without introducing a hard `sensorRange`.

`Stage20SensorCalibrationCalculator` measures the maximum physical separation supporting each existing measurement evidence state by repeatedly invoking the ordinary `ShipSensorRuntime.observe(...)` chain:

```text
physical fitted sensor
+ physical target signature
+ SI separation
+ damage-aware aperture/noise
+ physical EW interference where present
+ ECCM state where present
→ production SNR/evidence calculation
→ DETECTED / CLASSIFIED / TRACKED / FIRE_CONTROL boundary
```

The first production-authoritative observer/target matrix uses the current `fit.escort_destroyer_schema_v1` because it already exposes fitted passive-thermal and active-radar modes plus a channelized physical signature through the ordinary engineering pipeline.

The profile includes:

- pristine passive-thermal and active-radar envelopes;
- the same sensor mount re-derived at 50% surviving `utility_sensor` integrity through ordinary `DamageState`;
- active-radar suppression by the already-authored Stage-17.5I `D_DEFENSIVE_EW` physical jammer;
- the same jammed geometry with fitted ECCM enabled, retaining its real electrical demand and waste heat;
- explicit SNR thresholds and bearing/range uncertainty parameters from the fitted sensor definition;
- deterministic profile ordering and provenance.

The jammer is placed 1,000,000 m cross-range from the observer as an explicit sensitivity probe. That probe is not a future station spacing, sensor range or generated-world constant. The jammer remains `PROVISIONAL_ACCEPTED_COMBAT_TEST`; Stage 20 does not promote Stage-17.5I combat-test content to final Stage-22 faction content.

A single passive bearing legitimately has no direct TRACKED/FIRE_CONTROL range boundary. Stage 20 does not invent exact range to complete a table. Distributed passive triangulation remains an explicit follow-up geometry problem.

The currently consumed `ShipSensorRuntime.TrackQualityPolicy.defaultPolicy()` is recorded as `PROVISIONAL_PRE_STAGE20_DEFAULT` because the production code itself defines it as a pre-Stage20 default. Its position-sigma, age and process-noise values remain visible, but they are not promoted to final calibration constants before weapon geometry justifies the required fire-control precision.

Detailed implementation/authority record: `docs/stage20a4_sensor_spatial_calibration.md`.

Open machine-readable gaps after 20A.4:

- final fused-track quality policy pending weapon geometry;
- distributed passive triangulation geometry not yet profiled;
- representative sensor/target-class coverage is still incomplete.

## 11. Stage 20A.5 — production weapon / PD spatial calibration — ACCEPTED

The fifth implementation slice connects production combat execution to spatial calibration without introducing hard Stage-20 weapon ranges or hit-chance constants.

`Stage20WeaponSpatialCalibrationCalculator` drives the existing production seams directly:

```text
fitted kinetic round
→ WeaponFireControl.planKinetic(...)
→ time of flight + aim uncertainty + maneuver envelope

fitted beam emitter
→ BeamWeaponRuntime.plan(...)
→ spot radius + dwell energy + irradiance

authored guided body
→ GuidanceRuntime.planLeadPursuit(...) / execute(...)
→ intercept horizon + terminal reserve + physical propellant burn

observed inbound threat
→ LayeredDefenseScheduler.scheduleObserved(...)
→ reachable intercept timing + safe-intercept geometry
```

The profile retains deterministic provenance and records missing physical closure instead of silently filling it with balance constants. In particular:

- beam effectiveness remains continuous and target-dependent because production beam runtime has no artificial hard range wall;
- safe-intercept distance remains a scheduler input until fragmentation/blast/debris physics derives it;
- the current range-owning layered-defense path is guided interception rather than an invented independent kinetic-PD range model;
- final TRACKED/FIRE_CONTROL admissibility remains weapon/target/motion dependent rather than a single global sigma/age constant.

Detailed implementation/authority record: `docs/stage20a5_weapon_pd_spatial_calibration.md`.

## 12. Immediate next implementation slice

Stage 20A.6 should calibrate **formation spacing and station physical footprint/spacing** from existing production/runtime evidence before world geometry is authored.

Required work:

1. identify current Stage-19 formation objectives as authored tactical probe geometry, not canonical world spacing;
2. derive measurable fleet frontage/slot/recovery envelopes from physical ship count, spacing, acceleration and formation-control behavior where production runtime owns them;
3. inventory Stage-18 station/facility infrastructure for actual physical dimensions, docking/transfer approach geometry and traffic-clearance inputs;
4. keep any absent station footprint/docking dimensions machine-visible rather than inventing a final radius from storage capacity or facility count;
5. define only the minimum accepted physical geometry schema needed by later Stage-20 system placement if current Stage-18 content lacks it, preserving Stage-18 industry authority and Stage-20 placement authority separation;
6. produce deterministic profile/tests proving that changed physical footprint or formation capability changes spacing evidence rather than being hidden by a fixed map constant.

After formation/station geometry, subsequent Stage-20A slices still own jump-arrival stand-off, far-coordinate numerical precision and materialization/LOD bands before Stage 20B star-system physical geometry begins.

Missing representative ship roles continue to remain explicit gaps until production content or accepted reference physics exists. Stage 20A cannot be declared complete while required representative coverage or required calibration domains remain unresolved.

Acceptance continues to require that identical content + profile version produces identical output and that changing physical capability changes derived spatial/travel/FTL/sensor/weapon/formation/station bands rather than being hidden by fixed map constants.
