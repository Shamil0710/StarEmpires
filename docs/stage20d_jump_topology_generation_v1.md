# Stage 20D — Jump topology / physical edge authority v1

Status: **STAGE 20D COMPLETE AGAINST ROADMAP DOD — STRUCTURAL GRAPH + QUALITY GATE + PHYSICAL EDGE METADATA + HOP-BY-HOP PLANNING**  
Implementation families:

- `stage20d.jump-topology-structural.v1`
- `stage20d.jump-edge-metadata.v1`
- `stage20d.jump-edge-catalog.v1`

Consumes accepted/provisional Stage-20 calibration already present on the observed production HEAD:

- `stage20a.topology-quality-bands.v1`;
- `stage20a.intersystem-cadence.v1`;
- `stage20a.ftl-edge-cadence.v2`;
- `stage20a.local-route-semantic-bands.v1`;
- `stage20a.jump-arrival-spatial.v2`;
- `stage20c.local-infrastructure-spacing.v1`.

Stage-22 playability/economy review remains required for provisional calibration budgets.

## 1. Purpose

Stage 20D converts already generated sectors/systems into the existing production
`GalaxyTopology` / `JumpConnection` graph and adds deterministic physical metadata without creating a
parallel navigation universe.

Hard invariant:

```text
one ordinary inter-system hop
= one explicit JumpConnection
= destination in topology.neighbors(currentSystem)
```

A distant goal remains:

```text
A -> B -> C -> D
= three separately revalidated world actions
```

not an atomic `A -> D` relocation.

## 2. Structural generation pipeline

```text
world seed + sector/system coordinates
-> deterministic intra-sector cyclic core
-> bounded frontier attachment
-> local topology hub shaping
-> spatially ranked inter-sector backbone
-> bounded sector-exit redundancy
-> Stage20TopologyQualityAnalyzer
-> stage20a.topology-quality-bands.v1
-> bounded strictly-improving edge-addition repair
-> ACCEPTED or REJECTED_SEED
-> ordinary GalaxyTopology
```

No failed budget is silently clamped or relaxed. If bounded repair cannot strictly improve the
calibrated quality score, the seed is rejected.

## 3. Quality authority

`Stage20TopologyQualityReport` covers:

- connected components / unreachable systems and sectors;
- degree distribution and degree-1 / degree-2 fractions;
- calibrated hub distribution;
- bridge-only linear corridors, longest and p90 corridor;
- cycle participation;
- regional-core alternate-route coverage;
- articulation systems and bridge edges;
- sector exit counts;
- per-sector internal cycle/bridge structure;
- single-gateway dependency proxy;
- adjacent-sector regional hub hop probes;
- structural motif fingerprints;
- normalized violations consumed by deterministic repair.

The implementation consumes `Stage20TopologyQualityCalibrationProfile` directly. It does not copy
its numeric thresholds into a second set of constants.

## 4. Physical fitted route planning

`Stage20PhysicalGalacticRoutePlanner` consumes an executable live-style
`ShipEngineeringRuntime.JumpPlan`; it does not duplicate FTL physics.

Cadence:

```text
arrival(h hops)
= h * spool
+ sum(edge transit)
+ (h - 1) * cooldown
```

The route reports:

- ordered systems;
- ordered explicit `JumpConnection` edges;
- final-arrival and ready-again ETA;
- gross required jump energy;
- cumulative jump heat;
- translated mass from the fitted planning snapshot;
- mandatory per-hop revalidation.

Without Stage-20D edge metadata the current fitted `edgeTransitSeconds` is used uniformly, matching
Stage-20A's explicit decision not to author a generated per-edge duration distribution.

## 5. Stable physical edge metadata

`Stage20JumpEdgeState` supplies the data intentionally absent from bare `JumpConnection`:

```text
stable edge ID
canonical JumpConnection
world-global physical OPEN / PHYSICALLY_CLOSED state
physical discoverability policy class
fitted-transit parameters
directional destination-local arrival endpoints
hazard/security observation state
calibration provenance
```

Stable ordinary ID is derived only from canonical endpoint IDs:

```text
ordinary:<min-system-id>:<max-system-id>
```

It is therefore independent from list order, generator pass count and caller ordering.

### Access ownership

`OperationalAccessState` is physical world state only. It does **not** encode faction legal access.
Stage-17 diplomacy/territorial law can deny a faction while the edge remains physically open.

### Discovery ownership

`DiscoveryPolicy` describes whether the physical edge participates in ordinary discovery. It does
**not** mean that a particular observer currently knows the edge. Observer-relative knowledge belongs
to Stage 20G.

### Hazard/security ownership

V1 materialization emits explicit `UNASSESSED` metadata with empty tags. Random hazard/security
ratings are not invented simply because the Stage-20D record has fields for them. Later physically
observed values require provenance.

### Transit parameters

Stage-20A intentionally authors only a reference one-edge transit law. V1 therefore records:

```text
fittedTransitMultiplier = 1.0
```

and computes edge transit from the current fitted `JumpPlan.edgeTransitSeconds`. A later accepted
non-uniform physical law may revise the metadata/profile version; V1 does not fabricate one.

## 6. Arrival geometry binding

`Stage20JumpEdgeStateMaterializer` binds every incident ordinary edge to one distinct
Stage-20C `JUMP_ARRIVAL_ANCHOR` in each endpoint system.

For a system with graph degree `d`:

```text
required calibrated local jump-arrival anchors >= d
```

Assignments are deterministic:

```text
incident JumpConnections sorted canonically
<->
JUMP_ARRIVAL_ANCHOR placements sorted by stable anchor ID
```

Each selected anchor must:

- belong to the current Stage-20C layout version;
- carry current Stage-20B / station / local-route provenance;
- have an explicit `JUMP_ARRIVAL_TO_MAJOR_HUB` connection;
- use the current accepted distance band/source evidence;
- respect the current closed station stand-off;
- retain `LocalPhysicalPosition` directly.

Insufficient anchors, stale provenance or missing calibrated connections reject world assembly.
There is no fallback to the legacy viewport arrival anchor and no default reuse of one lane for
multiple edges.

## 7. Exact-coverage catalog

`Stage20JumpEdgeCatalog` requires a strict 1:1 mapping:

```text
every GalaxyTopology JumpConnection
<-> exactly one Stage20JumpEdgeState
```

Missing metadata and extra metadata are both invalid. This prevents an unknown/unindexed connection
from accidentally becoming a routing shortcut.

When supplied to `Stage20PhysicalGalacticRoutePlanner`, physically closed edges are excluded while the
underlying `GalaxyTopology` identity remains unchanged.

## 8. Hop-by-hop execution planning

`Stage20JumpRouteExecutionPlanner.planNextHop(...)` deliberately returns only one direct-neighbor
`Stage20NextJumpExecutionPlan`.

Every call consumes current:

- system;
- fitted `JumpPlan`;
- physical edge catalog;
- final route destination.

After a hop, caller invokes the planner again from the new system. A changed edge state can therefore
change the route naturally before the next jump. No multi-hop execution reservation exists.

The next-hop plan carries the destination's `ArrivalEndpoint(LocalPhysicalPosition)` and never converts
it to legacy float coordinates.

## 9. Runtime physical-position boundary

The observed Stage-20C production HEAD still persists/materializes active jump arrival through
`float arrivalX/arrivalY` in `FleetJumpState`, `FleetWorldService.completeTransfer` and
`FleetTransferService.attach`.

That seam is not sufficient for Stage-20 generated `LocalPhysicalPosition` authority. Silent
conversion would violate the accepted unbounded/local-precision contract.

Therefore direct generated-arrival execution wiring is intentionally deferred behind the integration
boundary documented in:

`docs/stage20d_runtime_arrival_authority_boundary.md`.

This is an explicit correctness boundary, not a missing convenience adapter.

## 10. Determinism

Generation canonicalizes sectors, systems and connections; stable geometry ordering uses deterministic
math/tie-breaking. Edge metadata assignment is independent from map insertion order. Repair is monotonic
in physical edges and cannot move systems, weaken quality budgets or create non-neighbor adjacency.

## 11. Validation in the local implementation bundle

Java-17 API-compatible headless validation currently covers 12 tests:

1. accidental linear chain fails dead-end/corridor/cycle budgets;
2. ordinary cycle passes applicable structural budgets;
3. representative four-sector region passes calibrated v1 quality gate;
4. sector caller ordering does not change same-seed topology;
5. impossible two-singleton seed rejects rather than relaxing budgets;
6. multi-hop fitted route preserves explicit intermediate edges and physical consequences;
7. physical transit cost may choose more hops without creating adjacency;
8. rejected fitted capability cannot produce a route;
9. edge metadata is deterministic across local-layout map ordering;
10. each incident edge receives a distinct physical arrival anchor;
11. insufficient arrival anchors reject without legacy fallback;
12. exact metadata coverage is mandatory and physical edge closure causes next-hop replanning.

Additional distribution sweep remains unchanged after the metadata slice:

```text
100 developed-region synthetic seeds
3-8 sectors, 6-12 systems/sector
accepted: 97
rejected: 3 (REGIONAL_HOP_BELOW_BAND only)
average repair passes: 1.34
max repair passes: 4
```

The deliberately small-region probe remains mostly rejected because the current developed-region
3-5-hop calibration cannot physically fit many 2-6-system sectors. No quality budget is relaxed.

`javac -Xlint:all` and `javadoc -Xdoclint:all -Werror` pass for the current local production slice.
This is not a claim of repository-exact Maven CI because the execution container cannot clone the
repository over DNS.

## 12. Stage 20D completion and downstream integration debt

Stage 20D is complete against its roadmap DoD: ordinary hops are explicit, fitted physical ETA/energy
consequences are preserved, intended generated regions are connectivity-gated, and anti-chain /
redundancy quality is measured and deterministically repaired or rejected.

The following work remains important but belongs to downstream integration/persistence slices rather
than Stage-20D completion itself:

1. migrate live local-position/jump-arrival authority so generated `LocalPhysicalPosition` survives
   detach/transit/save-load/attach without float reduction;
2. wire generated `Stage20JumpEdgeCatalog` into live world assembly/runtime after that migration;
3. execute automatic route continuation edge-by-edge through the existing authoritative jump FSM,
   replanning after each arrival and honoring engineering/access changes;
4. finalize world-save schema ownership and same-seed reconstruction in Stage 20K while preserving
   Stage-20D stable edge IDs and metadata semantics;
5. retain repository-exact CI and broader composed-world sweeps as integration evidence.

None of these items authorizes a fallback to legacy viewport coordinates, skipped hops, or topology-
independent movement. They are explicit integration debt carried forward from a completed Stage-20D
generation/navigation slice.
