# Persistence model

## Status

Stage 3 persistence architecture. The current logical save schema is **version 1**.

This document defines the authoritative save/load contract. It is intentionally stricter than the
current UI requirements because later galaxy generation, factions, ownership, orders and strategic
AI will build on the same identity and continuation guarantees.

## Core rule

A save file is a value snapshot of the simulation, **not a serialized Ashley object graph**.

Persistent state may contain:

- primitive values;
- strings and enum names;
- immutable lists of value records;
- stable `EntityId` references.

Persistent state must not contain:

- Ashley `Entity` references;
- libGDX rendering objects or Scene2D actors;
- `Vector2` instances;
- Java object identity as a reference mechanism;
- wall-clock timestamps used as simulation time;
- JVM-private RNG state obtained by reflection.

`TradeAIComponent` and `MiningComponent` are protected by a regression test that rejects any future
field assignable from Ashley `Entity`.

## Entity identity

Every persistent runtime entity has exactly one `EntityIdComponent`.

`EntityId` is a positive `long`. A session owns one monotonic `EntityIdAllocator` shared by bootstrap
entities and dynamically created entities. The allocator's next value is part of the save state.

On load:

1. a new Ashley `Engine` is created;
2. a new `EntityRegistry` is attached to it;
3. systems are created in the canonical order;
4. new Ashley entities are reconstructed from `EntityState` values;
5. `EntityRegistry` rebuilds the runtime `EntityId -> Entity` index through normal lifecycle events.

The loader rejects a save whose `nextEntityIdValue` is not greater than every stored entity ID.
Duplicate IDs are rejected by the registry.

## GameState schema v1

`GameState` contains:

- schema version;
- root simulation seed;
- exact `SimulationClock.State`;
- next `EntityId` value;
- exact RNG state for the economy-event stream;
- exact RNG state for the asteroid-spawn stream;
- `GlobalEventManager.State`;
- `AsteroidSpawnSystem.State`;
- `PriceRecorderSystem.State`;
- `EconomicLedger.State`;
- all persistent entities as `EntityState`, sorted by `EntityId` when captured.

### EntityState

The entity snapshot currently supports all simulation components used by the demo vertical slice:

- identity;
- transform position and velocity;
- inventory capacity and stock;
- wallet balance;
- market targets, prices, consumption rates, fractional remainders, tradable flags and dirty state;
- production recipes, active recipe and progress;
- price history and retention limit;
- faction;
- reputation;
- ship type;
- TradeAI FSM, route IDs, target item/amount, movement, expected profit and cooldown;
- Mining FSM, target/base IDs, extraction remainder and lifetime counters;
- combat state;
- asteroid origin and finite remaining resource.

Adding a new authoritative component or a new mutable authoritative field requires either adding it
to the current capture/restore path before release or incrementing the schema for an incompatible
change.

## Exact time continuation

`SimulationClock.State` includes more than the completed tick number:

- fixed-step duration;
- whole nanoseconds accumulated toward the next tick;
- fractional nanoseconds left by time scaling;
- time scale;
- pause state;
- completed tick number.

This matters because saving between fixed-tick boundaries must not change the time of the next tick.

`GlobalEventManager.simulationTimeSeconds` is cross-validated against the restored clock. A mismatch
is treated as corrupted state.

## Exact RNG continuation

Simulation-owned random streams use `StatefulRandom`, a SplitMix64-based generator with an explicit
64-bit state. Saving a stream does not consume a random number. Restoring a stream from that state
must make its **next** generated value exactly equal to the next value from the uninterrupted stream.

Named streams remain isolated through `SimulationRandom`:

- `economy-events`;
- `asteroid-spawn`.

New random subsystems should receive their own stable stream name and must add their stream state to
`GameState` before becoming authoritative.

## Stateful system timers

Not all authoritative state lives directly on entities. Schema v1 also saves:

- the event manager's next automatic-event countdown and revision;
- active events and pending news;
- the asteroid spawner's initialized flag, refill timer and spawn counters;
- the price recorder's fractional interval timer;
- the economic ledger and its next sequence number.

Derived caches such as spatial indexes and `EntityRegistry` mappings are rebuilt instead of saved.

## Economic ledger

The ledger remains continuous across save/load. Its next sequence number is saved separately from
the currently retained diagnostic entries, so a diagnostic `clear()` does not reset economic
identity.

Normal demo-world activity contains no implicit money source/sink. Trade transfers money between
wallets; resource source/sink/transform operations remain explicit ledger entries.

## File format

`GameStateCodec` uses a dependency-free deterministic binary format.

The file contains:

1. a fixed magic header;
2. file-format version;
3. logical `GameState` schema version;
4. fields in a fixed documented code order.

Strings are length-prefixed UTF-8. Optional components are represented explicitly. Lists are
length-prefixed and bounded. The decoder rejects:

- wrong magic;
- unknown file format version;
- unknown logical schema version;
- negative or excessive collection/string lengths;
- truncated files;
- trailing bytes;
- invalid record invariants.

The current maximum save-file size is 32 MiB. This is a defensive parsing limit, not a design target.

File replacement uses a temporary file in the target directory followed by atomic move when the
filesystem supports it, with replace fallback otherwise.

## Runtime integration

`SimulationSession` is the single authoritative owner of the headless simulation and exposes:

- `snapshot()` / `restore(GameState)`;
- `save(Path)` / `load(Path)`;
- fixed-step frame advancement.

`SpaceSimGame` is now a desktop UI shell over `SimulationSession`; it no longer creates a separate
copy of the economic pipeline. Loading a save replaces the session and rebuilds UI objects that hold
runtime registry/entity references. Selection and previously displayed UI news are transient and are
not authoritative save state.

## Required regression guarantees

Stage 3 is not complete unless all of the following stay green:

1. every persistent economic entity has a stable unique `EntityId`;
2. persistent components contain no Ashley `Entity` field;
3. `Entity -> EntityState -> new Entity` gives the same value snapshot;
4. `GameState -> binary -> GameState` is exact and deterministic;
5. `simulate(A) -> snapshot -> restore -> simulate(B)` equals uninterrupted simulation;
6. `simulate(A) -> file save -> file load -> simulate(B)` equals uninterrupted simulation;
7. the equality comparison covers entities, wallets, markets, FSMs, RNG, events, system timers,
   ledger and unfinished fixed-step time;
8. malformed and unsupported files fail before becoming a live simulation session.

These guarantees are architectural gates for later save migrations, galaxy-scale simulation and
long-running benchmark tests.
