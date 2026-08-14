# Stage 12A — Player State

Status: **COMPLETE — PR #29, main `3a7efe1`.**

## Goal

Introduce a durable human-player layer without making the independent world simulation depend on player concepts.

The architectural boundary is:

```text
PlayableWorldState
├── WorldState          <- Stage 7–11 simulation remains player-agnostic
└── PlayerState         <- Stage 12+ human actor state
```

## PlayerState contract

`PlayerState` owns persistent player-only information:

- personal wallet in integer milli-credits;
- optional faction/legal affiliation (`null` means independent);
- reputation keyed by stable faction content IDs;
- owned world-level `FleetId` values;
- active `FleetId`;
- discovered `StarSystemId` values;
- discovered system-qualified `EntityId` references;
- optional home/start system.

All collections are immutable and canonically sorted. Duplicate reputation/fleet/system/object entries are rejected. The active fleet must be player-owned. Home must be discovered. A discovered object must belong to a discovered system.

Discovery deliberately does not require the referenced entity to still exist: the player may remember a station, wreck or other object that was later destroyed.

## Save ownership

Stage 12 does **not** add player fields to `WorldState`.

`PlayableWorldState` is a new schema owner above the world layer. This preserves the design invariant that the galaxy can exist and continue to simulate without a human actor.

`PlayableWorldStateCodec` embeds an ordinary `WorldStateCodec` payload and adds a bounded player payload. The entire playable state is written atomically.

### Migration

A pre-Stage-12 raw `WorldState` save is accepted directly by `PlayableWorldStateCodec.decode(...)` and migrates to:

```text
PlayableWorldState.CURRENT_VERSION
worldState = decoded legacy world
playerState = null
```

No ship, wallet balance, discovery or faction affiliation is invented during migration. Player creation/bootstrap remains an explicit application action.

## Runtime boundary

`PlayerRuntime` wraps an existing `WorldSimulation` and forwards time advancement unchanged.

On create/restore it validates:

- player affiliation exists in the content catalog;
- every reputation faction exists in the content catalog;
- every owned `FleetId` exists in the authoritative world fleet layer;
- every discovered/home system exists in galaxy topology.

A migrated save without an initialized player cannot be restored as a playable runtime until player bootstrap is explicitly performed.

## Acceptance

Stage-12A tests cover:

- canonical ordering and invariant rejection;
- full playable save round-trip;
- migration from pre-player `WorldState` bytes;
- no fabricated player during migration;
- invalid ownership reference rejection;
- deterministic world + player continuation after save/load.

## Follow-up integration

12B–12D subsequently built ownership transfer, destruction cleanup, direct control, persistent docking, Stage-10 jump travel and manual market interaction on this schema without introducing another player-state source of truth.
