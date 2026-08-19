# Stage 20A.9 — Materialization / LOD Distance-Band Calibration

**Status:** ACCEPTED — exact-head Java-17 implementation CI green; final merge gate pending  
**Parent:** Stage 20A — Representative-Ship Scale Calibration  
**Date:** 2026-08-19

## 1. Purpose

Stage 20A.9 connects the accepted simulation-scalability architecture to the Stage-20 physical-coordinate domain without turning renderer distance, tactical test geometry or sensor/weapon probes into a world boundary.

The central rule is:

```text
one authoritative world state
→ different computational representations by relevance
```

not:

```text
nearby "real" world
+ separate approximate off-screen world
```

Representation changes may change update cadence and runtime detail. They may not create, delete, refill, repair or relocate authoritative physical/economic state.

## 2. Canonical representation levels

The accepted scalability architecture already defines the required order:

```text
DORMANT
→ STRATEGIC
→ ACTIVE_LOCAL
→ TACTICAL
```

and requires reverse transitions as well.

Stage 20A.9 makes that contract machine-readable:

### DORMANT

Persistent authoritative state remains retained. Work is event/on-demand only when there is no current local, tactical, strategic or due-event relevance.

### STRATEGIC

Persistent strategic/event-driven simulation is required because an order, transit, economy consequence or authoritative due event matters, but no local physical runtime is currently required.

### ACTIVE_LOCAL

A reduced local/system representation is permitted because nearby operational relevance exists, but there is not yet a direct interaction requiring the full tactical runtime.

### TACTICAL

Detailed physical execution is required for direct combat/sensor/weapon/docking interaction.

All four levels retain authoritative state. None intrinsically requires rendering.

## 3. Relevance outranks raw distance

Stage 20A.9 does not select LOD from a single radius.

The deterministic priority is:

```text
direct tactical interaction
→ TACTICAL

else local operational relevance
→ ACTIVE_LOCAL

else strategic relevance or due authoritative event
→ STRATEGIC

else
→ DORMANT
```

Distance can contribute to whether a physical interaction is approaching, but it cannot override causal relevance.

Examples:

- an off-screen missile engagement remains `TACTICAL` if exact physical execution is required;
- an off-screen scheduled economic/strategic event remains at least `STRATEGIC`;
- a visible decorative object is not automatically `TACTICAL` merely because the camera happens to render it;
- leaving the render window does not delete or demote an entity by itself.

## 4. Production cadence evidence

The current codebase already provides real timing evidence:

```text
LiveTacticalSimulationSession.TICK_SECONDS = 0.05 s
SimulationSession.DEFAULT_FIXED_STEP_SECONDS = 0.1 s
SimulationClock.advanceStrategicSteps(...) = production reduced strategic stepping seam
```

These are existing runtime cadences, not materialization-distance constants.

They prove that the project already distinguishes full tactical cadence, local fixed-step simulation and reduced strategic stepping.

They do **not** establish how long a future persistent→local materializer may take to wake an entity/group. No production bounded-wake materialization scheduler exists yet.

## 5. Why current numeric materialization bands remain unresolved

The Stage-20 plan requires `materializationDistanceBands`, but the existing authority is insufficient to safely assign a universal number.

Current missing closure includes:

- no production persistent→local materialization scheduler with a measured/accepted maximum wake latency;
- no production lossless local→persistent dematerialization service;
- Stage-20A.4 sensor interaction is target/channel/state dependent rather than one hard sensor radius;
- Stage-20A.5 beam effectiveness has no artificial hard range wall;
- guided/kinetic/PD geometry is weapon/target/motion dependent;
- station docking/traffic geometry remains incomplete from Stage-20A.6;
- render/camera distance is presentation policy, not physical interaction authority.

Therefore the current machine-readable closures for:

```text
ACTIVE_LOCAL activation distance
TACTICAL activation distance
```

are:

```text
authority = UNRESOLVED
activationDistanceM = absent
```

No viewport radius, `MAX_BODY_DISTANCE_M`, Stage-19 scenario distance, Stage-20A sensor probe or weapon probe is substituted.

## 6. Physical promotion-distance seam

When accepted inputs exist, Stage 20A.9 defines the minimum deterministic physical formula:

```text
closingDuringWake = maximumClosingSpeed × maximumWakeLatency

activationDistance =
    interactionEnvelopeRadius
    + closingDuringWake
```

Inputs must be explicit and provenance-backed:

```text
interactionEnvelopeRadiusM
maximumClosingSpeedMps
maximumWakeLatencyS
provenance
```

The meaning is straightforward: promotion must occur early enough that an actor closing at the accepted maximum relevant speed cannot enter the detailed interaction envelope before the materializer has completed its accepted wake latency.

This threshold is a **promotion look-ahead**, not a physical wall.

An entity can exist arbitrarily beyond it. Crossing it does not change system membership, physical coordinates or identity.

## 7. No hidden use of tactical probe geometry

Existing live/tactical code contains values such as scenario start positions, `TACTICAL_REFERENCE_RANGE_M`, `MAX_BODY_DISTANCE_M`, sensor intervals and viewer-related bounds.

Those values belong to their acceptance/runtime scenario context.

Stage 20A.9 does not reinterpret them as:

```text
SYSTEM_RADIUS
TACTICAL_LOD_RADIUS
ACTIVE_LOCAL_RADIUS
RENDER_RADIUS
```

Likewise Stage-20A.4/A.5 probe maxima remain calibration evidence, not universal materialization distances.

## 8. Structural deletion is not dematerialization

`EntityLifecycleService.remove(...)` is explicitly a structural entity-removal operation.

It:

- requires the target to be economically empty;
- invalidates persistent references;
- removes the Ashley entity;
- unregisters its persistent ID from the live registry.

That behavior is correct for actual structural removal and **incorrect** for LOD dematerialization of a still-existing ship, station, asteroid or convoy.

Stage 20A.9 therefore records:

```text
EntityLifecycleService.remove
!= local → persistent dematerialization
```

A future dematerializer must preserve authoritative identity/state and round-trip back into local runtime without free repair, refills, relocation or order reset.

## 9. Rendering / culling boundary

Rendering is deliberately evaluated separately from simulation relevance.

The machine-readable decision contains:

```text
requiredRepresentationLevel
rendered
authoritativeStateRetained = true
```

Thus the same `TACTICAL` interaction may be rendered or culled without changing its required simulation representation.

Render culling may reduce presentation cost. It may not:

- delete authoritative state;
- reset damage/velocity/consumables;
- change current system;
- suppress due events;
- force a lower simulation LOD while direct interaction still requires higher detail.

## 10. Machine-readable implementation

Added:

- `Stage20MaterializationLodCalibrationProfile`;
- `Stage20MaterializationLodCalibrationCalculator`;
- `Stage20MaterializationLodCalibrationProfileTest`.

Current profile version:

```text
stage20a.materialization-lod.v1
```

The profile exposes:

- the four canonical representation levels;
- immutable representation policies;
- production cadence evidence;
- current numeric distance-band closure status;
- explicit physical promotion-distance input/output schemas;
- render/culling independence;
- unresolved materialization lifecycle constraints.

## 11. Acceptance evidence

Exact-head Java-17 CI on implementation head `91337e3a6096b496bd62af65d8b10e802e60f570` completed successfully before this status finalization.

The accepted regression evidence proves that:

- identical accepted inputs produce identical profile output;
- canonical representation order is stable;
- all levels retain authoritative state;
- due events cannot disappear because an object is off-screen;
- direct tactical relevance selects `TACTICAL` even while culled;
- current numeric bands remain absent rather than receiving fallback radii;
- increasing closing speed increases explicit promotion distance;
- increasing accepted wake latency increases explicit promotion distance;
- increasing the accepted interaction envelope increases explicit promotion distance;
- render visibility changes presentation only, not required simulation representation.

## 12. Remaining Stage-20A closure

Stage 20A.9 completes the planned **calibration seam** for materialization/LOD, but it intentionally exposes that the numeric production bands are not yet physically closed.

Stage 20A as a whole also still carries earlier machine-visible gaps, including representative-role coverage and incomplete station physical geometry.

Therefore Stage 20B must **not** begin merely because A.9 code exists.

The immediate next action after A.9 acceptance is the **Stage-20A closure/readiness gate**:

1. audit every Stage-20A DoD field and machine-readable unresolved constraint;
2. separate blockers required before Stage 20B from gaps explicitly deferred to Stage 22 content review;
3. close required materialization lifecycle/wake-latency authority rather than inventing distance bands;
4. close required station footprint/traffic geometry needed by physical placement;
5. resolve the minimum representative ship-role coverage needed for system geometry;
6. verify the combined calibration profile can actually serve as input to Stage-20B without hidden map, speed, sensor, weapon or LOD constants.

Only an accepted closure gate may advance the roadmap from Stage 20A to Stage 20B.
