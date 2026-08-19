# Stage 20A — Station Defensive / Sensor Geometry v1

**Status:** PROVISIONAL ACCEPTED REFERENCE — exact-head CI required before merge  
**Requirement:** `STATION_DEFENSIVE_SENSOR_GEOMETRY`  
**Profile:** `stage20a.station-defensive-sensor-geometry.v1`  
**Date:** 2026-08-19

## Purpose

Stage 20 needs station-specific warning and defensive-response geometry before generated jump-arrival, traffic and infrastructure placement can be considered physically closed.

Stage 18 station archetypes define industry/storage/transfer semantics but do not author station sensors or weapons. Stage 22 owns final technology/content promotion. Therefore Stage 20A must not fabricate production station modules merely to obtain map radii.

This profile closes the geometry gap by selecting only already accepted physical references:

- **sensor floor:** production escort observation runtime consumed through `Stage20SensorTargetClassCoverageProfile`;
- **defensive response:** accepted v0.3 kinetic P50 rows consumed through `Stage20WeaponTargetClassCoverageProfile`;
- **new Stage-20 authoring:** only the mapping from each station archetype to one provisional security/reference tier.

No new station-only sensor equation, weapon equation or hidden range multiplier is introduced.

## Sensor geometry

For ordinary stations the conservative minimum reference target is `TORPEDO_CORVETTE`, because it has both accepted thermal output and authored non-zero RCS in the Stage-20 target matrix. The naval ordnance depot uses `BATTLESHIP` as its strategic reference target.

For each selected target the profile records:

```text
passive thermal DETECTED
active radar DETECTED
active radar CLASSIFIED
active radar TRACKED
active radar FIRE_CONTROL
```

The values are measured by the production observation runtime; the profile does not copy or hand-author numeric sensor distances.

## Defensive-response geometry

The defensive response envelope is an accepted v0.3 P50 direct-fire range, not a hard weapon range wall and not final station lethality content.

Security tiers:

| Tier | Accepted weapon/target reference | Meaning |
|---|---|---|
| `BASIC_SECURITY` | M coilgun vs corvette | local outpost / depot security reference |
| `HARDENED_SECURITY` | M coilgun vs frigate | hardened civil / industrial security reference |
| `NAVAL_FORTIFIED` | XL capital kinetic vs battleship | strategic naval-site reference |

Current archetype mapping:

| Stage-18 station | Tier | Sensor target | Defensive target |
|---|---|---|---|
| Mining outpost | BASIC | Torpedo corvette | Corvette |
| Volatile/water depot | BASIC | Torpedo corvette | Corvette |
| Refinery complex | HARDENED | Torpedo corvette | Frigate |
| Industrial station | HARDENED | Torpedo corvette | Frigate |
| High-tech manufacturing hub | HARDENED | Torpedo corvette | Frigate |
| Trade/logistics hub | HARDENED | Torpedo corvette | Frigate |
| Naval ordnance depot | NAVAL_FORTIFIED | Battleship | Battleship |
| Frontier multipurpose station | HARDENED | Torpedo corvette | Frigate |

The profile additionally requires the selected active-radar FIRE_CONTROL envelope to cover the selected P50 defensive-response envelope. A station assignment therefore cannot claim an engagement stand-off larger than the accepted sensor reference can support.

## Authority boundary

```text
authority = PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

This does **not** mean the production station already contains the referenced escort sensor module or benchmark gun. It means Stage 20 world geometry may use these accepted envelopes as provisional physical capability floors until Stage 22 authors or promotes actual station modules.

Stage 22 may:

- promote the reference;
- replace it with station-specific modules;
- split one archetype into doctrine/faction variants;
- change the capability tier and therefore require a new profile version.

## Readiness effect

After acceptance:

```text
STATION_DEFENSIVE_SENSOR_GEOMETRY:
  BLOCKING_STAGE20B_ENTRY -> SATISFIED

blocking requirement count:
  7 -> 6
```

The following remain separate blockers:

- `PD_SAFE_INTERCEPT_GEOMETRY`;
- `STATION_JUMP_ARRIVAL_STANDOFF`;
- `LOCAL_ROUTE_SEMANTIC_BANDS`;
- `TOPOLOGY_QUALITY_CALIBRATION_BANDS`;
- `MAJOR_INFRASTRUCTURE_EXTENT_BANDS`;
- `MATERIALIZATION_LOD_CLOSURE`.

The immediate dependent next step is `STATION_JUMP_ARRIVAL_STANDOFF`, because its existing physical formula can now consume both the accepted station operational radius and the station defensive-response envelope.
