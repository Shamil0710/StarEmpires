# Stage 10B — Jump Transit

Status: COMPLETE — PR #20 pending merge. Implementation and acceptance gates are complete.

## Purpose

Stage 10B turns the Stage 10A world-level fleet handoff into a deterministic direct-jump state machine driven by authoritative simulation ticks.

## Runtime model

Physical placement and jump execution remain separate layers:

- `FleetPlacementState` answers where the physical asset currently exists: `IN_SYSTEM` or `IN_TRANSIT`;
- `FleetJumpState` answers which jump phase is active and its absolute tick boundaries.

The jump FSM is:

```text
MOVING_TO_JUMP
    ↓
JUMP_PENDING
    ↓
IN_TRANSIT
    ↓
ARRIVING
    ↓
complete / IN_SYSTEM
```

All structural phase durations are positive. `IN_TRANSIT` duration is derived deterministically from immutable topology coordinates, direct jump-edge distance, configured strategic jump speed and the authoritative fixed-step duration.

## Exact-tick orchestration

A jump transition must not occur once per render frame. One render frame may execute many fixed simulation ticks, so frame-end processing could overshoot a physical detach/arrival boundary.

`SimulationLoop` therefore supports an optional callback after every completed fixed tick. `WorldSimulation` uses this boundary to:

1. inspect active jump states whose `phaseEndsTick` has been reached;
2. synchronize the relevant remote origin or destination system exactly to that boundary tick when a cross-session handoff is about to occur;
3. advance `FleetJumpService` at that authoritative tick;
4. only afterwards allow the ordinary remote scheduler to continue.

This guarantees that a fleet is removed from the origin and materialized in the destination at deterministic simulation boundaries even when a render frame contains many local ticks.

## Persistence

`WorldState` schema v7 appends active `FleetJumpState` records to the Stage 10A fleet-placement layer.

Migration from v6 is neutral:

- existing `FleetId` values and placements are preserved;
- the active jump list starts empty;
- no fleet, money, cargo or travel progress is invented.

A restored active jump phase must satisfy:

```text
phaseStartedTick <= authoritativeWorldTick < phaseEndsTick
```

Expired or future-inconsistent jump phases are rejected instead of being repaired implicitly.

## Topology rules

A jump request is valid only when origin and destination are connected by a direct `JumpConnection`. Stage 10B intentionally does not perform multi-hop route planning; that belongs to Stage 10C.

## Acceptance evidence

The Stage 10B suite verifies:

- deterministic direct jump duration;
- rejection of non-connected systems;
- persistent v7 round-trip with a fleet physically detached in `IN_TRANSIT`;
- `A → jump → save/load while IN_TRANSIT → B` through real `WorldPersistence`;
- cargo, wallet, faction and ship state survive the jump unchanged;
- the fleet is never present in both systems simultaneously;
- the stable `FleetId` survives while the destination receives a new local `EntityId`;
- changing the active StarSystem during transit does not reset or alter the jump timeline;
- a large multi-tick render frame cannot skip detach/arrival boundaries.

Final branch verification after cleanup passed the complete Java 17 CI gate: tests, coverage, strict Javadoc and desktop packaging.

## Scope deliberately deferred

Stage 10B does not yet provide:

- multi-hop galactic path finding;
- supplier/consumer route planning across systems;
- bounded cross-system market discovery;
- route risk;
- player-facing jump visuals or local jump-anchor geometry.

Those concerns continue in Stage 10C/10D and later presentation/combat stages.
