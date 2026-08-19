# Stage 20A — Closure / Readiness Gate

**Status:** IMPLEMENTED — acceptance pending exact-head CI / merge gate  
**Parent:** Stage 20 — Physical World Generation / Discovery  
**Date:** 2026-08-19

## 1. Purpose

Stage 20A.1–A.9 established the calibration seams required before procedural physical geometry is authored. Those slices deliberately left unsupported physical capability unresolved instead of filling gaps with map, sensor, weapon, station or LOD constants.

The closure/readiness gate answers a narrower question:

> **Can the accepted Stage-20A calibration output serve as sufficient physical input for Stage 20B star-system geometry without inventing hidden capability or fallback distances?**

The gate is machine-readable and derives its answer from current accepted calibration profiles rather than from a manually maintained checklist.

Current result:

```text
BLOCKED_FOR_STAGE20B
```

## 2. Classification policy

Not every unresolved note is a Stage-20B blocker.

The gate separates four states:

```text
SATISFIED
BLOCKING_STAGE20B_ENTRY
DEFERRED_STAGE22_CONTENT
OWNED_BY_LATER_STAGE20
```

### SATISFIED

Current accepted calibration already provides enough authority for Stage-20B entry.

### BLOCKING_STAGE20B_ENTRY

Physical/calibration information is missing and Stage 20B would otherwise have to invent it, silently substitute a legacy constant or produce geometry that cannot be validated against the intended world model.

### DEFERRED_STAGE22_CONTENT

Stage 20A explicitly permits an accepted provisional physical reference to calibrate world scale while final faction/production content promotion remains a Stage-22 review item.

### OWNED_BY_LATER_STAGE20

The datum is world data intentionally authored by a later Stage-20 slice and therefore must not be fabricated during Stage 20A merely to make the gate green.

## 3. Representative propulsion coverage

The accepted Stage-20 plan requires nine functional representative roles:

1. early civilian freighter;
2. loaded bulk freighter;
3. mining ship;
4. patrol/corvette;
5. escort destroyer;
6. cruiser;
7. capital combatant;
8. fleet tanker/logistics support;
9. carrier/aviation group where relevant.

The current `Stage20ScaleCalibrationProfile` covers five:

```text
BULK_FREIGHTER_LOADED
TORPEDO_CORVETTE
ESCORT_DESTROYER
BATTLESHIP
FLEET_TANKER_LOADED
```

and keeps four missing rather than fabricating them:

```text
EARLY_CIVILIAN_FREIGHTER
MINING_SHIP
CRUISER
CARRIER_AVIATION_GROUP
```

Gate result:

```text
REPRESENTATIVE_PROPULSION_COVERAGE
= BLOCKING_STAGE20B_ENTRY
```

The existence of the legacy functional enum `ShipType.MINING_SHIP` does not close this requirement: it defines cargo/role behavior, not a physical engineering mass/thrust/delta-v representative.

## 4. Civilian ordinary FTL coverage

The accepted reference jump drive supports:

```text
max translated mass = 100,000,000 kg
```

Current civilian/logistics representatives include:

```text
BULK_FREIGHTER_LOADED = 143,000,000 kg
FLEET_TANKER_LOADED   = 170,000,000 kg
```

Both exceed the accepted one-drive translated-mass envelope. The missing early civilian freighter and mining representative provide no alternative current civilian FTL closure.

Stage 20 must not silently assume:

- multiple drives combine translated-mass capacity;
- civilian mass bypass;
- a larger invisible drive;
- special freight gates;
- non-neighbor teleport travel.

Gate result:

```text
CIVILIAN_ORDINARY_FTL_COVERAGE
= BLOCKING_STAGE20B_ENTRY
```

This is distinct from final production FTL-module promotion. The provisional reference drive is allowed for Stage-20 calibration, but the current calibration still needs at least one physically valid civilian/logistics ordinary-FTL path before generated logistics geography can be trusted.

## 5. FTL items that are not Stage-20B blockers

### Neighbor-only topology

Current accepted semantics are explicit:

```text
NEIGHBOR_EDGE_ONLY
```

Result:

```text
FTL_TOPOLOGY_SEMANTICS = SATISFIED
```

### Production FTL module

No final production `FTL_JUMP` module has yet replaced the accepted reference drive.

Stage 20A already allows explicitly provisional accepted references when authority/provenance remain visible and Stage-22 review is mandatory.

Result:

```text
PRODUCTION_FTL_MODULE_PROMOTION
= DEFERRED_STAGE22_CONTENT
```

### Numeric FTL heat coefficient

The accepted reference requires heat accounting but lacks the final numeric production coefficient.

This remains production/content promotion debt rather than permission to invent a world-scale distance.

Result:

```text
FTL_HEAT_COEFFICIENT
= DEFERRED_STAGE22_CONTENT
```

### Edge-transit distribution

Generated neighboring-edge transit distributions are world data to be authored/calibrated by the later inter-system topology work.

Result:

```text
FTL_EDGE_TRANSIT_DISTRIBUTION
= OWNED_BY_LATER_STAGE20
```

## 6. Sensor / target coverage

The accepted A.4 production matrix currently measures the production escort destroyer as both observer and target and keeps the explicit gap:

```text
representative_sensor_and_target_class_coverage_incomplete
```

One production-quality military target is insufficient to calibrate world geometry around all required visibility/use cases such as civilian freight, mining, small craft and larger combatants.

Gate result:

```text
SENSOR_TARGET_CLASS_COVERAGE
= BLOCKING_STAGE20B_ENTRY
```

The gate does not require a universal `sensorRange`; it requires enough representative target/signature coverage that Stage 20B does not place world objects based on one destroyer-only visibility case.

## 7. Weapon / PD and formation evidence

A.5 provides deterministic production-runtime probes for:

- kinetic fire-control/time-of-flight;
- beam spot/dwell/irradiance;
- guided navigation/propellant use;
- layered-defense intercept geometry.

A.6 provides deterministic formation frontage and recovery evidence while retaining authored Stage-19 spacing only as provisional tactical probes.

Results:

```text
WEAPON_PD_SPATIAL_EVIDENCE = SATISFIED
FORMATION_SPATIAL_EVIDENCE = SATISFIED
```

These results do not promote the probe distances into universal world constants.

## 8. Station physical geometry

A.6 inventories all eight current Stage-18 station archetypes.

Current result:

```text
placement-ready stations = 0 / 8
```

Missing authority includes:

- footprint length/width;
- docking-approach clearance;
- traffic clearance.

The physical `300 × 120 × 70 m` escort-yard berth remains useful infrastructure evidence but cannot stand in for the containing station footprint.

Gate result:

```text
STATION_PHYSICAL_GEOMETRY
= BLOCKING_STAGE20B_ENTRY
```

This follows the accepted A.6 rule that Stage 20B must not place full stations as physically closed objects until the minimum explicit geometry schema is populated by accepted physical content/reference data.

## 9. Jump-arrival station stand-off

Because station geometry remains unresolved, A.7 correctly retains all current station-specific stand-offs as absent:

```text
closed station stand-offs = 0 / 8
```

No viewport center, weapon probe, shipyard berth or universal jump radius may fill that gap.

Gate result:

```text
STATION_JUMP_ARRIVAL_STANDOFF
= BLOCKING_STAGE20B_ENTRY
```

This blocker is downstream of station geometry: once accepted station footprint/traffic/defense inputs exist, A.7 already provides the deterministic stand-off derivation seam.

## 10. Far-coordinate numerical precision

A.8 established:

- hierarchical `long numerical cell + double local offset` physical positions;
- 1 cm versioned local numerical error budget;
- camera-relative float projection only after physical subtraction;
- cell-boundary continuity;
- presentation rebasing without physical mutation.

Gate result:

```text
FAR_COORDINATE_PRECISION = SATISFIED
```

Legacy global-float ECS flight remains migration work and is not allowed to become the Stage-20B far-coordinate physical authority.

## 11. Materialization / LOD closure

A.9 establishes the canonical representation hierarchy and physical formula for future promotion look-ahead, but current production lacks:

- persistent→local materialization scheduler with accepted bounded wake latency;
- lossless local→persistent dematerialization service;
- physically closed numeric `ACTIVE_LOCAL` / `TACTICAL` activation bands.

Current result:

```text
numeric activation bands closed = false
lossless materialization lifecycle closed = false
```

Gate result:

```text
MATERIALIZATION_LOD_CLOSURE
= BLOCKING_STAGE20B_ENTRY
```

`EntityLifecycleService.remove(...)` cannot satisfy this requirement because it performs real structural deletion and reference invalidation rather than reversible LOD representation change.

## 12. Current exact blocker set

The readiness calculator currently expects exactly six Stage-20B blockers:

```text
1. REPRESENTATIVE_PROPULSION_COVERAGE
2. CIVILIAN_ORDINARY_FTL_COVERAGE
3. SENSOR_TARGET_CLASS_COVERAGE
4. STATION_PHYSICAL_GEOMETRY
5. STATION_JUMP_ARRIVAL_STANDOFF
6. MATERIALIZATION_LOD_CLOSURE
```

The regression suite locks this set so a future change cannot accidentally declare Stage 20A ready while one of these inputs remains absent.

## 13. Recommended remediation order

The blockers have dependencies and should not be attacked in arbitrary order.

### Closure Workstream 1 — representative physical coverage

Close the missing functional representatives with production content or explicitly accepted physical references:

```text
EARLY_CIVILIAN_FREIGHTER
MINING_SHIP
CRUISER
CARRIER_AVIATION_GROUP
```

At the same time, obtain at least one valid ordinary-FTL civilian/logistics representative under explicit accepted FTL semantics.

Do not invent numbers merely to satisfy the gate.

### Closure Workstream 2 — sensor/target matrix

Expand A.4 across the representative signatures made available by Workstream 1.

This should close civilian/miner/small/large target visibility without introducing a scalar sensor radius.

### Closure Workstream 3 — station geometry and jump stand-off

Author/accept explicit physical footprint, docking-approach and traffic geometry for the eight Stage-18 station archetypes.

Then rerun A.6 placement envelopes and A.7 station-specific arrival stand-off derivation.

### Closure Workstream 4 — lossless materialization lifecycle

Implement reversible persistent ↔ local/tactical representation transitions with bounded measured wake latency and no economic/physical mutation.

Use that measured wake latency plus explicit interaction envelopes/closing capability to populate A.9 numeric activation bands.

### Closure Workstream 5 — rerun gate

Only after all six blockers clear may:

```text
BLOCKED_FOR_STAGE20B
→ READY_FOR_STAGE20B
```

be accepted.

## 14. Machine-readable implementation

Added:

- `Stage20ACalibrationReadinessProfile`;
- `Stage20ACalibrationReadinessCalculator`;
- `Stage20ACalibrationReadinessProfileTest`.

Current profile version:

```text
stage20a.closure-readiness.v1
```

The gate consumes A.1–A.9 outputs directly and preserves provenance/status classification for each requirement.

## 15. Immediate next action

After this readiness gate itself passes the merge gate, Stage 20 remains in **Stage 20A closure**, not Stage 20B.

The next implementation work should begin with a blocker that can be closed without fabricated physical content. Where representative/station numbers require new accepted design data, they remain blocked until that data exists rather than being guessed.

A lossless materialization lifecycle is currently the strongest code-first blocker candidate because its semantics can be implemented and tested from existing authoritative persistence/state contracts without inventing station sizes or missing ship physics.
