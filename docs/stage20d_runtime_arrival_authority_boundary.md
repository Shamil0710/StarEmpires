# Stage 20D — Runtime arrival authority boundary

Status: **DOWNSTREAM RUNTIME INTEGRATION / PERSISTENCE DEBT — NOT A STAGE-20D DoD BLOCKER**

Stage 20D may be complete while this boundary remains unresolved because its roadmap DoD requires explicit ordinary hops, connected accepted topology, physical route consequences and measurable redundancy/anti-chain quality; it does not require the legacy ECS float transform migration. This document remains normative for any later live-runtime/materialization wiring.

Observed production HEAD: `e557457ad05c78a6d07a8c8439b8bf3b5d6d2641`

## Decision

Generated Stage-20 arrival geometry is authoritative as `LocalPhysicalPosition` and must not be silently reduced to legacy float local coordinates.

The currently observed runtime path still carries:

```text
FleetJumpState.arrivalX / arrivalY
FleetWorldService.completeTransfer(..., float, float)
FleetTransferService.attach(..., float, float)
```

while Stage 20B/20C authority uses hierarchical `LocalPhysicalPosition` with long cells and normalized double offsets.

Before generated Stage-20D arrival geometry may drive authoritative live jumps, runtime placement must preserve that hierarchical physical position end-to-end or use an equivalent proven representation with bounded conversion error.

## Why this remains important

`LocalPhysicalPosition` exists specifically so very large local-system coordinates retain meter-scale local precision. A direct cast of generated coordinates to floats can collapse distinct arrival lanes or introduce large absolute placement errors at large offsets. The issue is therefore physical authority, not rendering convenience.

## Allowed future implementations

A later materialization/integration slice may use one of these patterns, provided equivalence is proven:

1. migrate the authoritative local transform to `LocalPhysicalPosition`;
2. add an authoritative physical-position sidecar and derive legacy/render float transforms only as bounded local projections;
3. use another hierarchical/reference-frame representation with equivalent precision and deterministic persistence.

Save migration may map legacy finite float coordinates into the zero cell of the new representation, but schema ownership belongs to the persistence/materialization stage rather than Stage 20D itself.

## Forbidden bridges

The runtime bridge must not:

- cast hierarchical world coordinates to float without a documented error bound;
- clamp generated arrival positions to a viewport envelope;
- replace a generated endpoint with `(0,0)` or `LocalSystemCoordinates.ARRIVAL_*`;
- reuse one arrival lane because the runtime cannot represent the generated lane;
- skip intermediate jump nodes;
- reset engineering heat/energy/cooldown consequences during the coordinate handoff.

## Ownership

Stage 20D owns topology, stable ordinary edge identity, physical edge metadata, destination-local arrival endpoints and hop-by-hop replanning semantics.

The live ECS/materialization and save-schema migration belongs downstream (notably Stage 20K persistence/materialization work or an explicit integration slice before generated worlds are made live). Until then, Stage-20D data may be generated, validated and used for planning without falsely claiming that the legacy float transfer path is sufficient for authoritative generated arrivals.
