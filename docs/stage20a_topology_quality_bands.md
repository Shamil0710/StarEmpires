# Stage 20A — Topology Quality Calibration Bands v1

**Status:** PROVISIONAL ACCEPTED GENERATION POLICY — exact-head CI required before merge  
**Requirement:** `TOPOLOGY_QUALITY_CALIBRATION_BANDS`  
**Profile:** `stage20a.topology-quality-bands.v1`  
**Date:** 2026-08-19

## Purpose

The accepted galaxy-topology contract defines the diagnostics that generated jump graphs must expose, but deliberately leaves their numeric acceptance budgets to Stage 20 calibration.

This profile authors those budgets before Stage 20D so the later graph generator has deterministic repair/reject criteria rather than subjective visual judgement.

It does **not** generate a graph and does not change the ordinary navigation invariant:

```text
one ordinary jump = one explicit neighbor edge
```

## Calibration anchor

The accepted `Stage20IntersystemCadenceCalibrationProfile` already defines:

```text
NEIGHBOR_EDGE             = 1 hop
REGIONAL_3_HOP            = 3 hops
REGIONAL_5_HOP            = 5 hops
FLEET_REINFORCEMENT_3_HOP = 3 hops
```

Therefore the topology profile inherits:

```text
regionalHopDistanceBand = 3..5 hops
```

rather than inventing a second regional route scale.

The remaining graph-shape budgets are explicit provisional generation-policy choices constrained by the accepted topology goals: developed regions need alternate paths, frontier dead ends/chokepoints remain allowed in a bounded share, and no single gateway should normally carry a majority dependency.

## v1 acceptance budgets

```text
maxLinearCorridorLength      = 3 edges
maxDegreeOneFraction         = 0.20
minRegionalCycleCoverage     = 0.50
minCoreRouteRedundancy       = 2 edge-disjoint routes
maxSingleGatewayDependency   = 0.45
sectorExitBand               = 2..4 ordinary exits
hubDegreeBand                = 3..6
regionalHopDistanceBand      = 3..5 hops
```

### Why these are policy budgets, not physics constants

Ships determine the temporal cost of every hop. The topology policy determines how often the generator is allowed to force those costs through one route structure.

- `maxLinearCorridorLength = 3` prevents a choice-free accidental chain from consuming an entire lower regional route and most of the accepted five-hop upper regional route.
- `maxDegreeOneFraction = 0.20` preserves bounded frontier pockets/dead ends without letting them dominate ordinary topology.
- `minRegionalCycleCoverage = 0.50` requires alternate/cyclic routing to be a normal regional property, not a rare exception.
- `minCoreRouteRedundancy = 2` means major core nodes normally retain at least one alternate edge-disjoint path.
- `maxSingleGatewayDependency = 0.45` permits valuable chokepoints but prevents one gateway from being the normal majority dependency of a developed region.
- `sectorExitBand = 2..4` gives developed sectors meaningful borders with alternate exits without turning every border into a dense mesh.
- `hubDegreeBand = 3..6` distinguishes real local hubs from degree-2 corridor nodes while keeping graph density bounded.
- `regionalHopDistanceBand = 3..5` is inherited directly from accepted physical cadence calibration.

## Stage 20D usage

Later generated topology must calculate machine-readable diagnostics including:

```text
connected components
unreachable systems/sectors
degree distribution
linear corridor lengths
cycle participation
edge-disjoint route availability
articulation systems / bridge edges
gateway concentration
sector exit counts
hub degree
regional hop distance
```

Then:

```text
candidate graph
→ diagnostics
→ compare to v1 budgets
→ bounded deterministic repair
→ re-measure
→ accept or reject seed
```

An intentional authored frontier/chokepoint may use a separate explicit exception contract; it must not silently weaken the ordinary generation gate.

## Authority

```text
authority = PROVISIONAL_ACCEPTED_REFERENCE
stage22ReviewRequired = true
```

Stage 22/playable economy review may tune these numbers after real trade-flow, blockade and reinforcement behavior is observed. A change requires a new profile version.

## Readiness effect

After acceptance:

```text
TOPOLOGY_QUALITY_CALIBRATION_BANDS:
  BLOCKING_STAGE20B_ENTRY -> SATISFIED

blocking requirement count:
  4 -> 3
```

Remaining blockers:

1. `PD_SAFE_INTERCEPT_GEOMETRY`
2. `MAJOR_INFRASTRUCTURE_EXTENT_BANDS`
3. `MATERIALIZATION_LOD_CLOSURE`

This closes only the numeric generation-quality policy required before later topology generation; it does not prematurely implement Stage 20D.
