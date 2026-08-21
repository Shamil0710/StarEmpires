# Stage 20D.1 — Persistent Jump Connection Runtime Foundation

## Status

ACTIVE implementation slice after Stage 20 world generation activation.

## Goal

Convert generated graph edges into persistent world entities that can support:

- physical travel;
- discovery and access state;
- logistics routing;
- faction control and interdiction;
- future warfare and exploration systems.

## Design rules

The jump connection is not only a graph relation. It is a physical part of the simulated world.

No system may create:

- instant travel exceptions;
- player-only routes;
- hidden logistics shortcuts;
- free access bypasses.

## Persistent model

Conceptual model:

```text
JumpConnection
 ├─ id
 ├─ sourceSystemId
 ├─ targetSystemId
 ├─ physicalDistance
 ├─ nominalTransitTime
 ├─ stabilityProfile
 ├─ hazardProfile
 ├─ discoveryState
 ├─ accessState
 └─ trafficState
```

## Runtime lifecycle

```text
planned route
 -> jump preparation
 -> transit
 -> arrival
 -> cooldown / recovery
 -> available again
```

All transitions must use authoritative simulation time.

## Integration points

### Navigation

Consumes persistent connection data and produces ordinary travel operations.

### Logistics

Uses the same routes as player and AI fleets.

### Discovery

Allows unknown routes to become known through sensors, exploration and faction information.

### Warfare

Enables future blockades, interdiction and strategic control without special-case rules.

## Acceptance criteria

- deterministic serialization of connection state;
- identical player and AI route semantics;
- no virtual movement during ordinary travel;
- no hidden connection metadata mutation from UI;
- compatibility with future resource geography and faction simulation.

## Next implementation slices

1. persistent access/discovery state;
2. travel runtime adapter;
3. route recalculation after world-state changes;
4. deterministic persistence tests.
