# Stage 20A.6 — Formation / Station Spatial Calibration

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20A — Representative-Ship Scale Calibration  
**Date:** 2026-08-19

## 1. Purpose

Stage 20A.6 calibrates fleet-formation spatial evidence and inventories station physical geometry before Stage 20 begins authoring star-system placement distributions.

The slice deliberately does **not** turn Stage-19 acceptance-fixture distances or Stage-18 storage/throughput numbers into canonical world spacing.

```text
Stage-19 authored formation objective
+ production physical acceleration
→ measurable frontage / slot / recovery evidence

Stage-18 station infrastructure authority
+ Stage-18 shipyard physical berth authority
→ physical geometry inventory
→ explicit unresolved gaps where dimensions do not exist
```

## 2. Stage-19 formation authority boundary

`docs/stage19i_l_tactical_formation.md` explicitly defines the current tactical formation distances as acceptance-scenario geometry rather than final combat-balance/world constants.

Stage 20A.6 therefore preserves the current probes as `PROVISIONAL_STAGE19_TACTICAL_PROBE`:

- compact 4v4: 4 ships, 120 m center-to-center spacing, 5 m tolerance, 80 m break distance;
- dispersed 4v4: 4 ships, 240 m spacing, 5 m tolerance, 80 m break distance;
- scaled 16-ship side: 16 ships, 100 m spacing, 5 m tolerance, 80 m break distance.

The derived center-to-center line spans are consequently:

- compact 4v4: 360 m;
- dispersed 4v4: 720 m;
- compact 16-ship side: 1,500 m.

These are measurements of the accepted Stage-19 probes, **not** a global formation-spacing rule.

## 3. Physical recovery evidence

Formation recovery consumes the current production `ESCORT_DESTROYER` acceleration from `Stage20ScaleCalibrationProfile`; the Stage-20A.6 calculator does not duplicate ship mass/thrust arithmetic.

`TacticalFormationPlanner` remains the policy/geometry owner. Stage 20A.6 verifies that a beyond-break actor is classified `BROKEN` through that planner and derives one explicit calibration lower bound for a ship starting and ending at zero lateral speed:

```text
recovery distance = break distance - slot tolerance
ideal symmetric recovery time = 2 * sqrt(recovery distance / physical acceleration)
```

The time is an ideal bang-bang lower bound for scale calibration, not a guarantee that a live damaged/depleted ship always recovers in exactly that duration. Real tactical execution still flows through engineering, reaction mass and `FlightDynamics`.

Sensitivity tests prove:

- more ships at the same spacing increase fleet frontage;
- wider authored spacing increases fleet frontage;
- lower physical acceleration increases the recovery-time evidence;
- no fixed map constant hides those changes.

## 4. Stage-18 station geometry inventory

The Stage-18F station archetypes currently author physical/economic capability including:

- installed facility IDs;
- storage capacities by physical storage class;
- transfer-compatible storage classes;
- cargo-transfer mass rate;
- maximum handled unit mass;
- allowed location tags.

They do **not** currently author:

- station footprint length/width;
- docking-approach clearance;
- traffic clearance.

Stage 20A.6 therefore records every current station archetype with `UNRESOLVED` geometry and empty machine-readable optional dimensions.

The implementation explicitly forbids deriving a footprint from:

- storage capacity;
- facility count;
- transfer throughput;
- maximum handled cargo-unit mass.

Those values describe industrial/logistics capability, not spatial dimensions.

## 5. Existing authoritative infrastructure geometry

Stage 18G already owns one real spatial infrastructure envelope:

```text
yard.orbital_escort_v1 berth
length = 300 m
width  = 120 m
height = 70 m
```

Stage 20A.6 exposes that berth as `PRODUCTION_AUTHORITATIVE` physical evidence.

A berth envelope is **not** promoted to the footprint of the station containing the yard. The full station may require structure, storage, radiators, docking approaches, traffic lanes and other infrastructure outside the berth.

## 6. Minimum station-placement geometry schema

Because Stage 18 lacks full station dimensions, Stage 20A.6 defines only the minimum explicit input required before Stage-20 placement may calculate a conservative spatial envelope:

```text
stationArchetypeId
provenance
footprintLengthM
footprintWidthM
dockingApproachClearanceM
trafficClearanceM
```

No field can be synthesized from industrial capacity.

When all explicit physical fields exist, the calculator can derive a conservative top-down placement envelope:

```text
footprintHalfDiagonal = hypot(length, width) / 2
operationalClearance = max(dockingApproachClearance, trafficClearance)
operationalRadius = footprintHalfDiagonal + operationalClearance
sameClassMinimumCenterSeparation = 2 * operationalRadius
```

This is a collision/traffic lower-bound calibration envelope for later placement work, not a final gameplay station-spacing distribution.

Sensitivity tests prove that increasing an explicit footprint or explicit traffic clearance increases the derived placement separation.

## 7. Machine-readable implementation

Added:

- `Stage20FormationStationSpatialCalibrationProfile`;
- `Stage20FormationStationSpatialCalibrationCalculator`;
- `Stage20FormationStationSpatialCalibrationProfileTest`.

The profile separates:

1. `FormationProbeSample` — Stage-19 tactical probe + production acceleration evidence;
2. `StationGeometrySample` — Stage-18 station geometry inventory with explicit missing closure;
3. `ShipyardBerthSample` — already-authored production physical berth dimensions;
4. `StationPlacementGeometryInput` / `StationPlacementEnvelope` — minimum explicit future placement seam.

Current profile version:

```text
stage20a.formation-station-spatial.v1
```

## 8. Unresolved constraints retained intentionally

The current profile keeps these gaps machine-visible:

- Stage-19 formation distances are acceptance probes, not final world spacing;
- Stage-18 station archetypes lack physical footprint dimensions;
- docking-approach geometry is absent;
- station traffic-clearance geometry is absent;
- a shipyard berth cannot stand in for whole-station footprint.

Stage 20B must not begin placing full stations as physically closed objects until station dimensions/clearances are backed by accepted physical content or an explicitly accepted versioned geometry reference.

## 9. Acceptance criteria

Stage 20A.6 is accepted when exact-head CI proves simultaneously:

- identical accepted content produces identical Stage-20A.6 profile output;
- compact/dispersed/scaled probes produce their measured distinct spans;
- formation recovery evidence changes when physical acceleration changes;
- fleet frontage changes with ship count/spacing;
- all eight Stage-18 station archetypes remain unresolved rather than receiving inferred dimensions;
- the Stage-18G escort-yard berth remains 300 × 120 × 70 m and separate from station footprint;
- explicit footprint/clearance changes alter derived station placement evidence;
- no storage/facility/throughput value becomes a hidden station-size formula.

## 10. Next slice

After Stage 20A.6 passes the merge gate, the next implementation slice is **Stage 20A.7 — jump-arrival stand-off calibration**.

It should derive arrival/traffic/security stand-off evidence from already accepted FTL edge semantics, ship braking/response capability, sensor/weapon/PD geometry and station/infrastructure geometry closure without introducing a universal jump radius or teleport buffer.
