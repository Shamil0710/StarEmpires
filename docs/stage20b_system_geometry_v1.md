# Stage 20B Candidate — Star-System Physical Geometry v1

**Status:** IMPLEMENTED — exact-head Java-17 CI pending  
**Parent:** Stage 20 Physical World Generation / Discovery  
**Date:** 2026-08-19

## Purpose

Implement the first versioned deterministic runtime star-system geometry required by Stage 20B while preserving the accepted unbounded-local-space contract.

The slice introduces:

```text
GeneratorVersion
SystemSpec
SystemSpecGenerator
SystemSpecValidator
```

All authoritative positions are `LocalPhysicalPosition`; all authored/generated linear values are SI meters.

## Authority boundary

This slice owns deterministic **local system geometry**, not final Stage-20C logistics balance and not Stage-20D jump-edge topology.

In particular:

```text
content extents != world boundary
jump site != JumpConnectionSpec
render/materialization window != physical space extent
```

`ContentExtents` are descriptive statistics only:

```text
coreActivityRadiusPercentileM
majorInfrastructureExtentM
resourceFieldExtentM
jumpArrivalExtentM
surveyedContentExtentM
expectedTrafficExtentM
```

No clamp/delete/teleport API is attached to them.

## Generated physical concepts

The v1 generator deterministically creates:

- central stellar reference at the local-system origin;
- 3–6 physical planet anchors with versioned orbital radii;
- deterministic optional moons with explicit planet parents;
- two station/infrastructure sites;
- one or more resource-field sites;
- two physical jump arrival/departure sites;
- derelict content and optional anomaly content;
- core activity, resource belt, patrol/security and empty-transit operational regions;
- descriptive content/traffic extents;
- versioned physical validation parameters.

The implementation deliberately uses a private fixed SplitMix64 stream rather than JDK `Random`/`SplittableRandom`, so an existing `(GeneratorVersion, seed)` pair does not silently change because a library RNG implementation changes.

## Physical validation

`SystemSpecValidator` checks at minimum:

- generated ID uniqueness;
- planet → central-body and moon → planet parent integrity;
- stored orbital radius against actual `LocalPhysicalPosition` separation;
- parent/body clearance;
- local-site footprint/clearance overlap;
- jump-arrival stand-off from stations;
- descriptive extent metrics do not under-report generated content.

Generated coordinates are validated through `LocalPhysicalPosition`, retaining the accepted hierarchical far-coordinate numerical strategy from Stage 20A.

## Unbounded-space evidence

The regression suite explicitly creates a valid authoritative `LocalPhysicalPosition` several generated extents beyond `surveyedContentExtentM` and verifies that physical separation remains finite and larger than the descriptive extent.

This is intentional proof that Stage 20B does **not** reintroduce a hidden `systemRadius` wall.

## Determinism / diversity evidence

Tests require:

- same seed + same `GeneratorVersion` => equal `SystemSpec`;
- different seeds => different valid systems;
- a deterministic seed sweep satisfies physical placement invariants;
- every generated system contains required stations, jump sites, resources and operational-region kinds;
- jump/station stand-off remains physical;
- validator rejects an intentionally overlapping site.

A local Java-17-compatible source compile plus a 100,000-seed generator smoke sweep passed before publication. Repository acceptance still requires the exact PR head to pass the canonical `./mvnw clean verify` CI gate.

## Deferred ownership

The following remain intentionally outside this slice:

- Stage 20C calibration of station/resource/jump spacing against representative logistics cadence;
- final resource geography and economic bootstrap;
- `JumpConnectionSpec` and explicit neighbor graph (Stage 20D);
- final edge-transit distribution;
- orbital-mechanics simulation;
- presentation scaling and renderer materialization policy beyond the already accepted `LocalPhysicalPosition` seam.
