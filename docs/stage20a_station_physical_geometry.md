# Stage 20A — Station Physical Geometry v1

**Status:** PROVISIONAL ACCEPTED DESIGN INPUT — exact-head CI required before merge  
**Requirement:** `STATION_PHYSICAL_GEOMETRY`  
**Profile:** `stage20a.station-physical-geometry.v1`  
**Date:** 2026-08-19

## Why this document authors new values

Stage 18 defines the eight station infrastructure archetypes in economic/industrial terms: installed facilities, storage mass, transfer classes, transfer rate, maximum transfer-unit mass and allowed locations. It does **not** define whole-station physical dimensions, docking-approach clearance or traffic-separation clearance.

Stage 18G separately defines a 300 × 120 × 70 m production shipyard berth. A berth is not a whole-station footprint and there is no accepted yard-to-station mapping that would justify deriving all station dimensions from it.

Therefore Stage 20A must make a real design decision rather than disguise an estimate as a derived result. The values below are intentionally authored as a new versioned physical-world calibration authority.

```text
authority = PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

They are **not derived from**:

- storage capacity;
- transfer throughput;
- facility count;
- maximum transfer-unit mass;
- sprite or map scale;
- ship mass;
- shipyard berth dimensions.

Any future revision changes the profile version rather than silently changing the meaning of generated seeds/content.

## Geometry semantics

`footprintLengthM` and `footprintWidthM` are the conservative top-down structural footprint used by physical placement.

`dockingApproachClearanceM` is clear approach space outside the structural footprint reserved for docking/arrival manoeuvre geometry.

`trafficClearanceM` is conservative separation outside the structural footprint for ordinary local traffic, hazardous operations or security exclusion as appropriate.

The already accepted Stage-20A.6 formula derives:

```text
footprintHalfDiagonal = hypot(length, width) / 2
operationalClearance = max(dockingApproachClearance, trafficClearance)
operationalRadius = footprintHalfDiagonal + operationalClearance
sameClassMinimumCenterSeparation = 2 * operationalRadius
```

This PR reuses that formula and does not create a parallel station-spacing model.

## Authored v1 station geometry

| Station archetype | Footprint L×W | Docking approach | Traffic clearance | Design rationale |
|---|---:|---:|---:|---|
| Mining outpost | 420 × 260 m | 650 m | 900 m | compact free-body industrial outpost |
| Volatile/water depot | 620 × 420 m | 850 m | 1,400 m | hazardous tankage requires expanded traffic separation |
| Refinery complex | 950 × 620 m | 1,000 m | 1,600 m | distributed processing and bulk-transfer complex |
| Industrial station | 1,200 × 780 m | 1,250 m | 1,800 m | heavy fabrication and assembly complex |
| High-tech manufacturing hub | 1,050 × 700 m | 1,200 m | 1,700 m | precision manufacturing hub with controlled approach |
| Trade/logistics hub | 1,600 × 1,000 m | 1,800 m | 3,000 m | high-traffic multi-berth logistics hub |
| Naval ordnance depot | 1,100 × 750 m | 1,400 m | 2,800 m | ordnance security and hazard separation |
| Frontier multipurpose station | 850 × 560 m | 1,000 m | 1,500 m | self-contained mixed frontier operations |

These values are intended to produce physically readable kilometre-scale operational envelopes around stations while keeping the structural stations themselves in the hundreds-of-metres to low-kilometres regime appropriate to the current ship/berth scale. That statement is design rationale, not a reverse derivation from Stage-18 economics.

## What this closes

After acceptance, all eight required Stage-18 station archetypes have explicit physical placement inputs and conservative placement envelopes. The machine-readable readiness change is:

```text
STATION_PHYSICAL_GEOMETRY:
  BLOCKING_STAGE20B_ENTRY -> SATISFIED

blocking requirement count:
  8 -> 7
```

## What this does not close

This profile deliberately does **not** define:

- station-specific defensive sensor/radar/weapon geometry;
- jump-arrival stand-off from a station;
- semantic local-route distance bands;
- topology quality bands;
- major infrastructure distribution extent around a star system;
- materialization/LOD activation distance.

Those remain separate Stage-20A requirements so one physical design decision cannot silently turn into several unrelated constants.

## Stage-22 review

Stage 22 may promote, revise or replace these station geometry values when full production content and final art/asset dimensions exist. Until then Stage 20B may consume them as explicit provisional physical-world authority, with provenance preserved per station archetype.
