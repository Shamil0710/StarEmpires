# Stage 20C — Local infrastructure spacing authority v1

Status: **PROVISIONAL STAGE-20 WORLD-GENERATION IMPLEMENTATION**  
Implementation: `stage20c.local-infrastructure-spacing.v1`  
Stage-22 content/balance review remains required for the provisional calibration inputs consumed here.

## Purpose

Stage 20C turns accepted Stage-20A logistics calibration into deterministic local physical placement.
It does **not** create a second strategic-distance coordinate system and does not reinterpret the
Stage-20B operational envelope as a world edge.

The v1 implementation is intentionally a **relative infrastructure layout**. A caller supplies an
already valid physical anchor for a major hub. The generator then places independent stations,
resource-field anchors and jump-arrival anchors relative to that hub in canonical
`LocalPhysicalPosition` SI coordinates.

This avoids inventing stellar radii, orbital bands or resource-field physical extents before those
concepts have their own accepted authority.

## Canonical semantic mapping

A caller chooses a semantic target role, never a raw distance:

| Stage-20C target | Accepted Stage-20A route meaning |
|---|---|
| independent station | `STATION_TO_STATION` |
| resource-field anchor | `STATION_TO_RESOURCE_FIELD` |
| jump-arrival anchor | `JUMP_ARRIVAL_TO_MAJOR_HUB` |

The physical separation is sampled deterministically inside the accepted SI interval from
`stage20a.local-route-semantic-bands.v1`.

The route profile already contains representative physical consequences derived through the shared
propulsion/endurance route solver. Every generated connection therefore records the accepted
representative envelope for:

- civilian/logistics routine rest-to-rest travel time;
- military max-thrust response time;
- two-leg civilian/logistics delta-v;
- two-leg transit-only cargo-cycle time.

The cargo-cycle value deliberately excludes docking, loading, market dwell and industrial handling
because Stage 20C has no authority to invent those times.

## Station placement safety

Default independent-station generation must satisfy, for every station pair:

```text
center separation >= STATION_TO_STATION.minDistanceM
center separation >= operationalRadiusA + operationalRadiusB
center separation >= defensiveExclusionReferenceA
center separation >= defensiveExclusionReferenceB
```

`STATION_TO_STATION` therefore remains the dominant logistics rule with current calibration, while
station physical/defensive references remain explicit safeguards rather than being discarded.

This default prevents accidental unavoidable mutual point-blank station geometry. The roadmap allows
an intentionally fortified gateway/base to overlap a route or approach, but v1 does not silently
weaken the default to create that special case. Such placement needs a later explicit semantic rule.

## Jump-arrival constraint

`JUMP_ARRIVAL_TO_MAJOR_HUB` placement also checks the already closed maximum station jump-arrival
stand-off. The accepted route-band lower bound currently exceeds that stand-off, but the explicit
check prevents later calibration changes from silently invalidating the relationship.

## Resource and jump geometry scope

Resource fields and jump arrivals are **point anchors** in this v1 layout. Their station geometry
fields are exactly zero/empty. This is deliberate: no accepted Stage-20 authority currently defines a
generic resource-field collision radius or a universal jump-zone physical footprint.

Future content may attach accepted extents to those anchors without changing their authoritative
physical identity.

## Determinism

Each target receives an independent RNG stream derived from:

```text
root world seed
+ StarSystemId
+ major hub ID
+ target ID
```

Requests are sorted by stable ID before constraint resolution. Reordering the same request set
therefore does not shift generated results, and adding unrelated RNG calls elsewhere cannot perturb
this subsystem.

If the bounded deterministic placement search cannot satisfy all accepted station/logistics
constraints, generation fails explicitly. It does not clamp, teleport or weaken the constraints.

## Unbounded-space invariant

The Stage-20B operational/content envelope remains descriptive. Stage 20C neither clamps the supplied
hub anchor nor rejects an otherwise valid `LocalPhysicalPosition` because it lies outside that
envelope. Generated physical space therefore remains conceptually unbounded; the envelope continues
to describe normal content distribution only.

## Provenance consumed

- `Stage20SystemGeometry.CURRENT_VERSION = stage20b.system-geometry.v1`
- `Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION = stage20a.local-route-semantic-bands.v1`
- `Stage20StationPhysicalGeometryProfile.CURRENT_VERSION = stage20a.station-physical-geometry.v1`
- `Stage20StationDefensiveSensorGeometryProfile.CURRENT_VERSION = stage20a.station-defensive-sensor-geometry.v1`

The implementation records those versions in every generated layout so a save/audit can identify the
exact geometry policy behind the physical placement.
