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

## 10. Immediate next implementation slice

Stage 20A.4 should connect already-production-authoritative sensing/signature logic to spatial calibration before inventing any new sensor-range abstraction:

1. passive detection geometry against representative physical signatures;
2. active radar detection/classification/track geometry;
3. fire-control-quality track geometry and uncertainty/time sensitivity;
4. damage/EW sensitivity where the production observation runtime already owns the behavior.

After the sensing layer is closed, Stage 20A should continue into weapon time-of-flight/effectiveness, PD/interceptor geometry, formation/station stand-off and jump-arrival geometry.

Missing representative ship roles continue to remain explicit gaps until production content or accepted reference physics exists. Stage 20A cannot be declared complete while required representative coverage or required calibration domains remain unresolved.

Acceptance continues to require that identical content + profile version produces identical output and that changing physical capability changes derived spatial/travel/FTL bands rather than being hidden by fixed map constants.
