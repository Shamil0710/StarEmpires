# Stage 20A — Closure / Readiness Gate

**Status:** ACCEPTED — exact-head implementation CI green; final merge gate pending  
**Parent:** Stage 20 — Physical World Generation / Discovery  
**Date:** 2026-08-19

## 1. Purpose

Stage 20A.1–A.9 established the principal calibration seams required before procedural physical geometry is authored. They deliberately retained unsupported capability as unresolved instead of filling gaps with arbitrary map, sensor, weapon, station or LOD constants.

The closure/readiness gate answers:

> **Does current Stage-20A output satisfy the complete accepted DoD 20A strongly enough for Stage 20B to author star-system physical geometry without inventing missing physics, route bands, topology thresholds or fallback radii?**

Current result:

```text
BLOCKED_FOR_STAGE20B
```

The first draft of this gate checked only unresolved A.1–A.9 implementation seams and found six blockers. A direct audit against `docs/stage20_physical_world_generation_plan.md` showed that this was too narrow: DoD 20A additionally requires semantic local/inter-system route bands, topology-quality bands, representative-target effectiveness, endurance/thrust consequences and other machine-readable calibration closure. The accepted gate therefore audits the full DoD, not merely the existence of A.1–A.9 classes.

## 2. Requirement classification

Every requirement is classified as one of:

```text
SATISFIED
BLOCKING_STAGE20B_ENTRY
DEFERRED_STAGE22_CONTENT
OWNED_BY_LATER_STAGE20
```

- `SATISFIED` — current accepted calibration is sufficient for Stage-20B entry.
- `BLOCKING_STAGE20B_ENTRY` — Stage 20B would otherwise have to guess a physical/cadence/quality value or consume an inappropriate legacy/probe constant.
- `DEFERRED_STAGE22_CONTENT` — Stage 20A explicitly permits a provisional accepted physical reference while final production/faction content promotion remains a Stage-22 responsibility.
- `OWNED_BY_LATER_STAGE20` — the datum is generated world data owned by a later Stage-20 slice; Stage 20A calibrates its acceptance band rather than fabricating the generated distribution itself.

## 3. Complete current blocker set

The machine-readable gate currently expects **16 Stage-20B entry blockers**:

```text
1.  REPRESENTATIVE_PROPULSION_COVERAGE
2.  REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE
3.  CIVILIAN_ORDINARY_FTL_COVERAGE
4.  INTERSYSTEM_CADENCE_CALIBRATION_BANDS
5.  SENSOR_TARGET_CLASS_COVERAGE
6.  FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE
7.  WEAPON_REPRESENTATIVE_TARGET_COVERAGE
8.  PD_SAFE_INTERCEPT_GEOMETRY
9.  FORMATION_SPACING_BAND_CLOSURE
10. STATION_PHYSICAL_GEOMETRY
11. STATION_DEFENSIVE_SENSOR_GEOMETRY
12. STATION_JUMP_ARRIVAL_STANDOFF
13. LOCAL_ROUTE_SEMANTIC_BANDS
14. TOPOLOGY_QUALITY_CALIBRATION_BANDS
15. MAJOR_INFRASTRUCTURE_EXTENT_BANDS
16. MATERIALIZATION_LOD_CLOSURE
```

Several blockers are dependency-related and may clear together; they are still represented separately because each corresponds to a distinct DoD 20A output that must not disappear behind a single umbrella flag.

## 4. Representative physical coverage

The accepted plan requires nine representative roles:

```text
early civilian freighter
loaded bulk freighter
mining ship
patrol/corvette
escort destroyer
cruiser
capital combatant
fleet tanker/logistics support
carrier/aviation group where relevant
```

Current scale calibration physically covers five:

```text
BULK_FREIGHTER_LOADED
TORPEDO_CORVETTE
ESCORT_DESTROYER
BATTLESHIP
FLEET_TANKER_LOADED
```

Missing:

```text
EARLY_CIVILIAN_FREIGHTER
MINING_SHIP
CRUISER
CARRIER_AVIATION_GROUP
```

Therefore:

```text
REPRESENTATIVE_PROPULSION_COVERAGE = BLOCKING_STAGE20B_ENTRY
```

The legacy `ShipType.MINING_SHIP` enum is not physical closure: it describes role/cargo behavior, not mass, thrust, delta-v, reaction mass, signature or endurance.

DoD 20A also requires representative stores/endurance and sustained-vs-maximum-thrust consequences. Current `Stage20ScaleCalibrationProfile` exposes propulsion/route measurements but no complete machine-readable endurance/stores/sustained-thrust matrix, so:

```text
REPRESENTATIVE_ENDURANCE_THRUST_COVERAGE = BLOCKING_STAGE20B_ENTRY
```

## 5. Civilian FTL and inter-system cadence

The accepted reference jump drive supports:

```text
max translated mass = 100,000,000 kg
```

Current civilian/logistics references include:

```text
BULK_FREIGHTER_LOADED = 143,000,000 kg
FLEET_TANKER_LOADED   = 170,000,000 kg
```

Both exceed the one-drive envelope; missing early freighter/miner references provide no alternative current civilian closure. The generator may not assume hidden drive multiplicity, mass bypass, enlarged civilian drives, gates or teleport rules.

Therefore:

```text
CIVILIAN_ORDINARY_FTL_COVERAGE = BLOCKING_STAGE20B_ENTRY
```

Neighbor-edge semantics themselves are explicit and accepted:

```text
FTL_TOPOLOGY_SEMANTICS = SATISFIED
NEIGHBOR_EDGE_ONLY
```

However DoD 20A requires calibrated machine-readable cadence for:

```text
system → neighboring system
regional 3–5 hop route
fleet reinforcement route
```

The current FTL profile contains one accepted-reference edge cadence, not semantic acceptance bands for those route classes. Therefore:

```text
INTERSYSTEM_CADENCE_CALIBRATION_BANDS = BLOCKING_STAGE20B_ENTRY
```

The eventual generated edge-transit distribution remains later Stage-20 world data:

```text
FTL_EDGE_TRANSIT_DISTRIBUTION = OWNED_BY_LATER_STAGE20
```

Final production FTL-module promotion and the numeric production heat coefficient remain explicitly deferred content work:

```text
PRODUCTION_FTL_MODULE_PROMOTION = DEFERRED_STAGE22_CONTENT
FTL_HEAT_COEFFICIENT            = DEFERRED_STAGE22_CONTENT
```

## 6. Sensor / track closure

A.4 currently uses the production escort destroyer as both observer and target and explicitly reports:

```text
representative_sensor_and_target_class_coverage_incomplete
```

Therefore:

```text
SENSOR_TARGET_CLASS_COVERAGE = BLOCKING_STAGE20B_ENTRY
```

A.4 also retained:

```text
final_fused_track_quality_policy_pending_weapon_geometry
```

A.5 weapon geometry now exists, but the final fused TRACKED/FIRE_CONTROL policy has not yet been re-derived/accepted against it. Therefore:

```text
FUSED_TRACK_FIRE_CONTROL_POLICY_CLOSURE = BLOCKING_STAGE20B_ENTRY
```

This does not request a universal sensor radius. It requests enough representative physical signatures and track-quality closure to author world scale without calibrating the entire universe around one destroyer-only measurement case.

## 7. Weapon / PD / formation closure

A.5 provides real production-runtime evidence for kinetic, beam, guided and layered-defense geometry:

```text
WEAPON_PD_SPATIAL_EVIDENCE = SATISFIED
```

But DoD 20A requires weapon time-of-flight/effectiveness **by representative target**. Current A.5 samples do not close an explicit representative-target/material-response matrix, so:

```text
WEAPON_REPRESENTATIVE_TARGET_COVERAGE = BLOCKING_STAGE20B_ENTRY
```

A.5 also deliberately retained safe-intercept distance as a scheduler probe input until fragmentation/blast/debris physics derives it. Therefore:

```text
PD_SAFE_INTERCEPT_GEOMETRY = BLOCKING_STAGE20B_ENTRY
```

A.6 proves formation frontage/recovery sensitivity:

```text
FORMATION_SPATIAL_EVIDENCE = SATISFIED
```

but all current spacing values remain `PROVISIONAL_STAGE19_TACTICAL_PROBE`, not accepted Stage-20 formation bands. Therefore:

```text
FORMATION_SPACING_BAND_CLOSURE = BLOCKING_STAGE20B_ENTRY
```

## 8. Station / infrastructure closure

A.6 inventories all eight Stage-18 station archetypes. Current placement-ready result:

```text
0 / 8
```

Missing fields include footprint, docking-approach and traffic geometry. The physical `300 × 120 × 70 m` escort-yard berth cannot be promoted to the containing station footprint.

```text
STATION_PHYSICAL_GEOMETRY = BLOCKING_STAGE20B_ENTRY
```

DoD 20A also requires station defensive/sensor spatial capability. Current Stage-18/A.6 station archetypes do not author this per station class:

```text
STATION_DEFENSIVE_SENSOR_GEOMETRY = BLOCKING_STAGE20B_ENTRY
```

A.7 therefore correctly has no physically closed station-specific jump stand-off:

```text
closed station stand-offs = 0 / 8
STATION_JUMP_ARRIVAL_STANDOFF = BLOCKING_STAGE20B_ENTRY
```

Because whole-station footprints remain absent, calibrated major-infrastructure extents also cannot be closed:

```text
MAJOR_INFRASTRUCTURE_EXTENT_BANDS = BLOCKING_STAGE20B_ENTRY
```

## 9. Required semantic local-route bands

A.2 has valuable physical route probes at several SI distances, but DoD 20A specifically requires semantic machine-readable calibration for:

```text
station → station
station → resource field
jump arrival → major hub
inner → outer system
```

Those semantic acceptance bands do not yet exist. Raw `10 Mm / 100 Mm / 1 Gm / 10 Gm` sensitivity probes may not silently become them.

```text
LOCAL_ROUTE_SEMANTIC_BANDS = BLOCKING_STAGE20B_ENTRY
```

## 10. Topology-quality acceptance bands

DoD 20A requires versioned calibration bands for topology quality before Stage 20D generates topology, including at minimum:

```text
maxLinearCorridorLength
maxDegreeOneFraction
minRegionalCycleCoverage
minCoreRouteRedundancy
maxSingleGatewayDependency
sectorExitBand
hubDegreeBand
regionalHopDistanceBand
```

No current Stage-20A machine-readable profile owns these thresholds.

```text
TOPOLOGY_QUALITY_CALIBRATION_BANDS = BLOCKING_STAGE20B_ENTRY
```

This is distinct from generating the topology itself. Stage 20A must calibrate the quality envelope; Stage 20D later generates/repairs/rejects seeds against that envelope.

## 11. Far-coordinate precision

A.8 established hierarchical `long numerical cell + double local offset` physical coordinates, a 1 cm local numerical-error budget and camera-relative float presentation after physical subtraction.

```text
FAR_COORDINATE_PRECISION = SATISFIED
```

Legacy global-float ECS flight remains migration work and cannot become Stage-20B far-coordinate physical authority.

## 12. Materialization / LOD closure

A.9 established the canonical relevance hierarchy and physical promotion-look-ahead formula, but production still lacks:

- persistent→local materialization with accepted bounded wake latency;
- lossless local→persistent dematerialization;
- physically closed numeric `ACTIVE_LOCAL` / `TACTICAL` activation bands.

Therefore:

```text
MATERIALIZATION_LOD_CLOSURE = BLOCKING_STAGE20B_ENTRY
```

`EntityLifecycleService.remove(...)` is real structural deletion/reference invalidation and must never be repurposed as reversible LOD dematerialization.

## 13. Dependency-ordered remediation

The 16 blocker flags do not imply 16 independent projects. The preferred order is:

### Workstream 1 — code-first physical continuity

Implement lossless persistent ↔ local/tactical materialization with explicit Stage-20 physical-coordinate snapshot and measured bounded wake latency. Then derive numeric A.9 activation bands.

This work can proceed without inventing ship or station physical numbers.

### Workstream 2 — representative ship/endurance/FTL coverage

Close:

```text
EARLY_CIVILIAN_FREIGHTER
MINING_SHIP
CRUISER
CARRIER_AVIATION_GROUP
```

using production content or explicitly accepted physical references only. Add stores/endurance and sustained-vs-max-thrust calibration. Ensure at least one civilian/logistics ordinary-FTL path is physically valid.

### Workstream 3 — sensor/weapon/formation closure

Use the expanded representative set to close:

- sensor/target class matrix;
- fused TRACKED/FIRE_CONTROL policy against A.5 weapon geometry;
- representative-target weapon effectiveness;
- physical PD safe-intercept geometry;
- accepted formation-spacing bands beyond Stage-19 fixture distances.

### Workstream 4 — station geometry

Author/accept physical station footprint, docking, traffic and defensive/sensor geometry. Then derive A.6 placement, A.7 arrival stand-off and major-infrastructure extent bands.

### Workstream 5 — semantic route/cadence/topology calibration

With representative capability and infrastructure closure available, publish machine-readable bands for:

- local route classes;
- system-neighbor / 3–5 hop / reinforcement cadence;
- topology-quality metrics required by DoD 20A.

### Workstream 6 — rerun gate

Only after every `BLOCKING_STAGE20B_ENTRY` requirement becomes satisfied may:

```text
BLOCKED_FOR_STAGE20B
→ READY_FOR_STAGE20B
```

be accepted and Stage 20B begin.

## 14. Machine-readable implementation

Added:

- `Stage20ACalibrationReadinessProfile`;
- `Stage20ACalibrationReadinessCalculator`;
- `Stage20ACalibrationReadinessProfileTest`.

Current version:

```text
stage20a.closure-readiness.v1
```

The test suite locks all current requirement classifications and the exact 16-blocker result, preventing later documentation drift or accidental readiness through fallback constants.

## 15. Immediate next action

After this gate itself passes exact-head CI and merge review, Stage 20 remains in **Stage 20A closure**.

The next implementation slice should begin with **Workstream 1 — lossless materialization lifecycle**, because it can be closed using existing persistence/state contracts plus A.8 physical coordinates without fabricating missing ship or station design data.
