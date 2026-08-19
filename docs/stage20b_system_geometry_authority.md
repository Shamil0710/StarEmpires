# Stage 20B — System Geometry Authority v1

Status: **ACTIVE / first vertical slice**  
Production profile: `stage20b.system-geometry.v1`

## Purpose

This document defines the first Stage-20B authority for deterministic local star-system geometry in SI units. It intentionally reuses the accepted Stage-20A scale instead of introducing a second coordinate system or an independent set of world radii.

## Authoritative physical coordinates

`LocalPhysicalPosition` remains the authoritative local-system coordinate representation. Its hierarchical cell partition is numerical only and does not describe sectors, generated regions, LOD radii, content envelopes or movement boundaries.

`StarSystemNode.x/y` remain strategic galaxy-map coordinates and are not local SI coordinates.

Legacy `LocalSystemCoordinates` viewport-centering semantics are not an input to Stage-20B physical generation.

## Operational envelope

`Stage20SystemGeometryGenerator` derives a deterministic `OperationalEnvelope` for each `(rootSeed, StarSystemId)` pair.

The envelope radius is sampled only inside the already accepted `INNER_TO_OUTER_SYSTEM` Stage-20A route band:

- minimum accepted scale: `1.0e9 m`;
- maximum accepted scale: `1.0e10 m`;
- accepted major-infrastructure extent must fit inside the generated envelope;
- RNG comes from the existing `SimulationRandom`/`StatefulRandom` deterministic stream contract.

The operational envelope is a **descriptive default content-distribution policy**. It is not:

- a physical star-system edge;
- a movement wall;
- a simulation-validity boundary;
- a renderer/materialization boundary;
- a delete/reset/teleport trigger;
- a legal target for silent coordinate clamping.

The production model therefore stores and validates `hardBoundary=false` and `clampAllowed=false`. A `LocalPhysicalPosition` beyond the operational envelope remains physically valid.

## Boundary placement normalization

`WorldGenerationPlacementNormalizer.normalizeBoundaryPlacement(...)` exists only for explicit placement exclusion rules, such as a minimum stand-off from a physical anchor.

Rules:

1. If a candidate already satisfies the minimum radius, preserve it bit-for-bit.
2. If it is too close and has a defined radius vector, move it along that vector to the required radius. This is the minimum radial displacement.
3. If candidate and center coincide while a positive minimum radius is required, fail with `UNDEFINED_RADIAL_DIRECTION`; do not invent an arbitrary axis.
4. If an independent genuine hard maximum conflicts with the required minimum, fail with `HARD_CONSTRAINT_CONFLICT`.
5. If a candidate is already beyond such a genuine hard maximum, fail with `OUTSIDE_HARD_CONSTRAINT`; do not clamp it back.
6. The operational envelope must never be passed as a hard maximum merely to emulate a map edge.

## Provenance

The v1 generator consumes:

- `stage20a.local-route-semantic-bands.v1`;
- `stage20a.major-infrastructure-extents.v1`;
- `LocalPhysicalPosition` hierarchical SI coordinate semantics;
- the existing deterministic `SimulationRandom` stream derivation.

The current envelope distribution is a Stage-20B generation policy over an already accepted distance interval. Later Stage-20 slices may replace the simple v1 sampling policy with stellar-profile/orbital/content-driven distributions, but must preserve the non-boundary semantics and deterministic provenance.

## Verification

`WorldGenerationSystemGeometrySmokeTest` verifies:

- deterministic output for the same seed/system ID;
- SI envelope bounds inherited from Stage 20A;
- explicit non-boundary/non-clamp semantics;
- valid physical positions beyond the envelope;
- minimum radial placement normalization;
- explicit zero-direction and hard-constraint failures;
- constructor enforcement against accidental boundary reinterpretation.
