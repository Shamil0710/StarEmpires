# Stage 20A — Representative-Ship Scale Calibration Kickoff

**Status:** ACTIVE  
**Parent:** Stage 20 — Physical World Generation / Discovery  
**Started:** 2026-08-19

## 1. Purpose

Stage 20A begins the implementation of the accepted Stage-20 physical-world-generation plan. Its job is to derive a versioned, machine-readable spatial calibration profile from already accepted ship, sensor, weapon, station and logistics capabilities before procedural generation starts choosing world distances.

The generator must not invent arbitrary map scale and later compensate with hidden movement, sensor, logistics or weapon multipliers.

## 2. Immediate implementation target

The first Stage-20A implementation slice will build a deterministic calibration pipeline that evaluates representative civilian and military hull/fit profiles across canonical route and tactical geometries.

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

## 7. Immediate next implementation slice

The next code PR should introduce the first versioned Stage-20A calibration data model and deterministic calculator/test harness, initially covering the representative profiles that can be derived directly from existing production content.

Acceptance for that PR should prove that identical content + profile version produces identical calibration output and that changing physical ship capability changes the derived spatial/travel bands rather than being hidden by fixed map constants.
