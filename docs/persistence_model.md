# Star Empires — Persistence Model

> Статус: **authoritative persistence architecture**  
> Синхронизация: **2026-08-16 / Stage 17.5C**

## 1. Core rule

Save — это **value snapshot authoritative simulation**, а не сериализованный Ashley object graph.

Persistent state может содержать:

- primitive values;
- stable strings/enums/content IDs;
- immutable value records/lists;
- stable `EntityId`, `FleetId`, `ConstructionProjectId`, `StarSystemId` references;
- explicit simulation-time/RNG/scheduler state.

Persistent state не должен зависеть от:

- Ashley `Entity` object identity;
- Scene2D/libGDX presentation objects;
- runtime-only caches/indexes;
- wall-clock time как authoritative game time;
- reflection-extracted JVM RNG state.

Derived runtime indexes and presentation objects rebuild after load.

---

## 2. Persistence is layered

В текущем проекте нет одной глобальной цифры schema для всего save. Есть три вложенных уровня с разными responsibilities.

```text
PlayableWorldState
├── PlayerState
└── WorldState
    ├── topology / factions / diplomacy / territory / fleets / construction
    └── StarSystemSimulationState[]
        └── GameState
            └── local ECS/economy/entities/engineering state
```

Версия меняется на том уровне, где реально изменился serialized authoritative contract.

### Current versions

| Layer | Logical schema | File format | Responsibility |
| --- | ---: | ---: | --- |
| `GameState` | **v4** | codec-owned current format | local fixed-step ECS/economy + fitted engineering state |
| `WorldState` | **v9** | **v8** | galaxy/world strategic state |
| `PlayableWorldState` | **v5** | **v1** | optional player envelope + embedded world |

Stage 17.5C повышает только локальный `GameState`, потому что fitted ship engineering является состоянием конкретной physical ECS entity. `WorldState` и `PlayableWorldState` не повышаются ради номера milestone: их serialized authoritative shape не изменился.

---

## 3. Local `GameState` contract

`SimulationSession` является authoritative owner локального simulation core.

`GameState` current schema v4 сохраняет как минимум:

- root simulation seed;
- exact `SimulationClock.State`;
- `EntityIdAllocator` watermark;
- named simulation RNG streams;
- global event manager state;
- asteroid-spawn state;
- price-recorder state;
- economic ledger state;
- persistent `EntityState` records sorted by stable identity;
- optional fitted Stage-17.5 engineering state per entity.

### Local schema history

```text
v1 — historical Stage-3 entity/item shape
v2 — expandable item slots + stable archetype IDs
v3 — configured market target provenance
v4 — fitted Stage-17.5 physical engineering state
```

### Stage-17.5 engineering payload

When an entity has an authoritative fitted engineering state, v4 stores physical source state only:

- fitted hull content ID;
- deterministic module-to-mount assignments;
- physical cargo/stores/mission payload and interface-bound consumable loads;
- shared `ENERGY_STORAGE` bus energy;
- ship-bus stored heat;
- module-local heat by mount;
- current physical thrust ceilings by mount;
- current coolant-bus transfer capacity;
- FTL cooldown by mount.

It deliberately does **not** persist derived acceleration, delta-v, total mass, power margin, heat margin, DPS, sensor range or other derived capability caches. Those values recompute deterministically from content + fitted modules + physical mutable state.

### Legacy v1–v3 migration into v4

Historical files contain no fitted engineering payload. Migration therefore sets:

```text
engineering = null
```

It must never infer a fit, reaction mass, battery charge, heat state, coolant capacity or FTL cooldown from a legacy class/archetype name. A legacy fleet remains on the explicit compatibility path until an ordinary authoritative migration/refit operation supplies real fitted state.

### Entity identity

Каждая persistent runtime entity имеет один stable positive `EntityId`.

Load path:

```text
GameState
→ new Ashley Engine
→ new EntityRegistry
→ canonical systems
→ reconstruct EntityState values
→ rebuild EntityId → Entity index
```

Loader rejects duplicate IDs and invalid allocator watermark.

### Local continuation

Clock state сохраняет unfinished fixed-step accumulation, pause/time scale and tick. Named RNG streams сохраняют exact next-value continuation. Gameplay-significant subsystem timers and fitted engineering mutable state are persistent.

Следствие:

```text
simulate A
→ save/load
→ simulate B
```

должно быть authoritative-equivalent uninterrupted `A+B`.

---

## 4. `WorldState` contract

`WorldState` current logical schema: **v9**.

Он хранит authoritative state, который не принадлежит одному local Ashley session:

- galaxy topology;
- one persistent local `GameState` per `StarSystem`;
- faction economic accounts/treasuries;
- faction strategic state;
- construction projects and allocator watermark;
- economic-pressure/hysteresis state;
- world-level persistent `FleetId` placements;
- active jump state;
- world-defined faction identity directory;
- institutional diplomacy;
- territory/claims/control/recognition/concessions;
- doctrine;
- fiscal/stock-production/resilience policy state;
- policy-review lifecycle.

### World logical schema history relevant to current migration

```text
v7 — Stage-10/15 jump-capable world
v8 — Stage-16 external/player construction settlement and ownership era
v9 — Stage-17 world-defined faction identity directory
```

Current `WorldStateCodec` continues to accept older supported schemas v1–v8 and migrates them to v9 through explicit constructors/default semantics.

---

## 5. World file-format trailers

World logical schema and file-format version deliberately evolve independently.

Current world file format: **v8**.

Historical additive trailers:

```text
v1 base world payload
v2 strategic-growth state
v3 Stage-17D territory / claims / recognition / construction rights
v4 Stage-17E institutional diplomacy
v5 transaction/customs tariff state
v6 Stage-17F doctrine
v7 fiscal reserve / construction investment authorization
v8 Stage-17F.6 policy-review watermark
```

When an old file lacks a later trailer, migration uses conservative defaults:

- no invented claim/control/recognition/right;
- neutral diplomacy, no invented treaties/grievances/embargoes;
- zero customs tariff;
- neutral doctrine where absent;
- previous-compatible fiscal defaults;
- never-reviewed policy lifecycle where absent.

A missing historical field may never become a hidden resource or permission grant.

---

## 6. Stage-16 → Stage-17 migration

World schema v8 represents the pre-Stage17 boundary.

Migration `WorldState.fromLegacyStage16(...)` preserves:

- topology;
- all local physical `GameState` snapshots;
- faction economic states;
- existing strategic state representable by the historical file;
- construction projects;
- economic pressure;
- `FleetId` allocator and placements;
- active jump state.

It adds an **empty** world-defined faction identity directory rather than inventing a player faction.

Later Stage-17 file trailers not present in the historical file receive only neutral/zero defaults described above.

---

## 7. `PlayableWorldState` contract

Current playable logical schema remains **v5**.

History:

```text
v1 Stage-12A player state
v2 docking
v3 persistent fleet orders
v4 non-omniscient threat intelligence
v5 Stage-16 construction-project + completed-station ownership
```

Playable envelope contains:

- embedded `WorldStateCodec` payload;
- optional `PlayerState`.

Raw pre-player `WorldState` bytes remain supported and migrate to a current playable envelope with `playerState = null`; no player is invented.

### `PlayerState`

Current persistent player data includes:

- personal wallet;
- nullable `factionContentId`;
- reputations;
- owned `FleetId`s and active fleet;
- discoveries;
- home/docking state;
- fleet orders;
- threat intelligence;
- owned construction-project IDs;
- completed `OwnedStationRef`s.

`factionContentId == null` is the authoritative independent-player state.

Stage 17 uses the existing nullable affiliation field. Dynamic faction metadata and institutional state are world state, so Stage 17 does **not** require a new outer player payload.

---

## 8. Schema-version policy

Version numbers are not milestone counters.

Increment a logical/file schema when at least one is true:

- authoritative serialized shape changes incompatibly;
- old bytes need a new migration rule;
- existing field semantics change such that old values need reinterpretation;
- a new required persistent value cannot be deterministically reconstructed.

Do **not** increment merely because a roadmap stage completed if serialized shape is unchanged.

Every incompatible change requires:

1. explicit version marker;
2. bounded decoder branch;
3. deterministic migration/default semantics;
4. fixture/regression test;
5. corruption/future-version rejection;
6. documentation update.

---

## 9. Exact identity continuation

Persistent references use stable value IDs, not runtime object identity.

Important allocator invariants:

- `nextEntityIdValue > all stored EntityId`;
- `nextFleetIdValue > all stored FleetId`;
- `nextConstructionProjectIdValue > all stored project IDs`.

Founding a faction, affiliating assets or loading a save must not consume FleetId/ConstructionProjectId merely to rebuild ownership/affiliation.

A refit/affiliation/materialization operation changes the same physical object unless an explicit lifecycle rule creates a new one.

Stage 17.5C extends this invariant across system transfer: detach → transit snapshot → attach preserves the same `FleetId` and the same fitted engineering payload rather than respawning a replacement ship.

---

## 10. Money, cargo and conservation across save/load

Persistence is not an economic or engineering event.

Save/load/materialization may not:

- add/remove money;
- refill cargo/ammunition/reaction mass;
- recharge shared engineering energy;
- erase or add stored/local heat;
- repair drive/coolant capability;
- reset FTL cooldown;
- repair damage;
- complete construction;
- reset production progress;
- invent territory/access;
- replace destroyed/missing assets;
- reset treaty/embargo/policy lifecycle.

Economic transfers before save and after load continue through the same ordinary ledger/wallet/inventory systems. Engineering continuation similarly resumes from the persisted physical state.

---

## 11. Stage-17 institutional persistence

Stage 17 intentionally distributes state across existing authoritative layers rather than creating a separate player-faction save blob.

### Player layer

```text
PlayerState.factionContentId
```

records whether the player is independent or affiliated.

### World identity/economy layer

Stores:

- dynamic stable faction identity and runtime-ID mapping metadata;
- treasury/economic state;
- fiscal reserve/construction authorization;
- policy review state.

### Strategic/political layer

Stores:

- doctrine;
- stock/production/resilience state;
- claims/control/stabilization;
- recognition/concessions;
- strategic goals/growth state.

### Diplomacy layer

Stores:

- standings/history inputs where modeled;
- treaties and clauses;
- embargoes;
- customs tariff state;
- lifecycle/revision data.

Effective market access is derived from persistent law/diplomacy on restore; it is not saved as a player-only permission bit.

---

## 12. Stage-17H binary migration guarantee

Stage 17H introduces a historical binary acceptance with this shape:

```text
Playable file format v1
Playable schema v5
    ↓
World file format v2
World schema v8
```

This represents a pre-Stage17 playable save whose world has no Stage-17 identity/territory/diplomacy trailers.

Required migration result:

- player remains independent;
- wallet and existing player FleetIds are unchanged;
- embedded local physical simulation states are unchanged except later schema-neutral migration defaults;
- construction/fleet allocator watermarks are unchanged;
- no dynamic player faction is invented;
- no territory/treaty/embargo/treasury grant is invented;
- missing diplomacy becomes neutral;
- subsequent current encode/decode is deterministic.

When those embedded historical local `GameState` values are promoted to v4, fitted engineering remains absent (`engineering=null`); no physical capability is fabricated.

See `Stage17HPreStage17MigrationAcceptanceTest` and `GameStateMigrationTest`.

---

## 13. Stage-17H full transition guarantee

The final transition acceptance starts from an actual completed Stage-16 player station and owned fleet, then executes:

```text
independent
→ found faction
→ affiliate same FleetId + station EntityId
→ conserved capitalization
→ shared fiscal policy authoring
→ ordinary station→treasury fiscal transfer
→ ordinary treaty/embargo access law
→ non-instant territorial claim
→ binary save/decode/re-encode
→ runtime restore
```

It verifies persistence of economy, policy, diplomacy, access, territory and ownership while money/cargo/IDs remain conserved.

See `Stage17HEndToEndTransitionAcceptanceTest`.

---

## 14. Stage-17.5C engineering / FTL continuation guarantee

Stage 17.5C adds three persistence guarantees at the physical ship boundary.

### Local engineering round-trip

```text
Ashley EngineeringComponent
→ EntityState.EngineeringState
→ binary GameState v4
→ decode/re-encode
→ Ashley EngineeringComponent
```

must preserve fit, consumables, shared energy, heat, physical thrust/coolant limits and FTL cooldown exactly enough for deterministic authoritative continuation.

### Fleet materialization boundary

```text
IN_SYSTEM entity
→ detach
→ FleetTransitState.EntityState
→ optional WorldState binary save/load
→ attach
→ destination entity
```

must preserve the same physical engineering payload and world `FleetId`.

### Active fitted FTL save boundary

Once a fitted jump has crossed `JUMP_PENDING → IN_TRANSIT`, its physical energy/heat/cooldown consequences are already committed. Saving and restoring while `IN_TRANSIT` must not execute that commit a second time on arrival.

See:

- `EngineeringPersistenceTest`;
- `FleetTransferAcceptanceTest`;
- `FleetJumpPersistenceTest`.

---

## 15. File safety / bounded decoding

All codecs use deterministic bounded binary parsing.

Decoders reject before live runtime creation:

- wrong magic;
- unknown file-format version;
- unsupported future logical schema;
- impossible collection/string lengths;
- truncated payload;
- unexpected trailing bytes;
- invalid record invariants;
- duplicate/unknown persistent references;
- incompatible allocator watermarks.

File replacement uses a temporary sibling file and atomic move where supported, with replacement fallback otherwise.

---

## 16. Runtime ownership boundaries

```text
SimulationSession
→ owns one local GameState runtime

WorldSimulation
→ owns topology + multiple SimulationSessions + strategic world state

PlayerRuntime
→ owns WorldSimulation + PlayerState playable interaction boundary
```

UI remains presentation/read-model layer. Loading replaces authoritative runtime values and rebuilds runtime/presentation references rather than restoring Java object identity.

---

## 17. Required regression guarantees

The persistence architecture is considered healthy only while all relevant tests maintain:

1. stable unique entity/fleet/project identity;
2. no Ashley references in persistent records;
3. exact/local continuation through `GameState`;
4. deterministic binary round-trip;
5. bounded legacy migration;
6. pre-Stage17 Stage-16 save compatibility;
7. no invented player/faction/territory/access during migration;
8. Stage-17 full binary transition round-trip;
9. diplomacy/policy/access continuation;
10. resource and money conservation;
11. future/corrupt version fail-fast behavior;
12. canonical re-save after migration;
13. fitted engineering v4 round-trip without derived-stat cache;
14. v1–v3 → v4 migration never invents engineering capability/resources;
15. fleet detach/transit/attach preserves engineering state and world `FleetId`;
16. active fitted FTL save/load preserves committed energy/heat/cooldown without double commit.

These guarantees are prerequisites for later Stage-17.5 combat/fitting persistence and large-universe materialization.