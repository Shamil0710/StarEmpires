# Star Empires — Stage 17H Persistence / Migration / Transition Completion Record

> Статус: **COMPLETE — canonical after the required merge gate reaches main**  
> Дата: **2026-08-16**  
> Scope: финальный Stage-17 transition gate перед активацией Stage 17.5.

## 1. Цель

Stage 17H закрывает не новую gameplay-feature, а целостность перехода:

```text
independent player with Stage-16 assets
→ found faction
→ same physical assets affiliated
→ transfer real capital
→ apply ordinary policy
→ economy reacts through ordinary systems
→ territory/access only by legal rules
→ binary save/load
→ diplomacy/access persist
→ no duplication/reset/resources created
```

Stage 17G уже доказал correctness management facade и object-snapshot round trip. Stage 17H добавляет то, чего 17G намеренно не заменял:

- inventory фактических persistence layers и version markers;
- pre-Stage17 binary migration fixture;
- реальную Stage-16 physical asset prerequisite;
- full transition через настоящий `PlayableWorldStateCodec` boundary;
- deterministic decode/re-encode;
- unsupported/corrupt version rejection;
- final resource/identity conservation gate.

## 2. Persistence inventory после аудита

### Local simulation — `GameState`

Current logical schema: **v3**.

Содержит локальный authoritative ECS/economy snapshot:

- simulation clock;
- named RNG state;
- system timers;
- ledger;
- allocator watermark;
- persistent `EntityState` values.

`SimulationSession` владеет этим уровнем, но не player/faction/territory/diplomacy world state.

### World — `WorldState`

Current logical schema: **v9**.

Historical anchors:

- v7 — Stage-10/15 jump-capable world;
- v8 — Stage-16 construction ownership/settlement world;
- v9 — Stage-17 world-defined faction identity directory.

Current `WorldStateCodec` file format: **v8**.

File-format trailers последовательно добавляют:

```text
v2 strategic growth
v3 territory / claims / recognition / construction concessions
v4 institutional diplomacy
v5 customs tariff policy
v6 doctrine
v7 fiscal reserve / construction authorization
v8 policy-review watermark
```

Missing later trailers migrate to deterministic neutral/zero/never-reviewed semantics instead of invented rights/resources.

### Playable envelope — `PlayableWorldState`

Current logical schema remains **v5**.

Historical anchors:

- v1 — Stage-12A player;
- v2 — docking;
- v3 — fleet orders;
- v4 — threat intelligence;
- v5 — Stage-16 construction-project and completed-station ownership.

`PlayableWorldState` embeds the versioned `WorldStateCodec` payload and stores `PlayerState`.

### Why Stage 17 does not bump playable schema

Stage 17 added no new serialized field to `PlayerState` or outer playable framing.

The pre-existing player field

```text
nullable factionContentId
```

already represents independent vs affiliated player identity, while new Stage-17 authoritative data belongs to `WorldState`:

- dynamic faction identity;
- faction economy/treasury;
- strategic state;
- claims/control/recognition/concessions;
- diplomacy/treaties/embargoes/access;
- doctrine/fiscal/stock-production/resilience state;
- policy-review lifecycle.

Therefore raising `PlayableWorldState` from v5 only to mark a milestone would add a meaningless migration branch without changing payload semantics. Stage 17H explicitly freezes the rule:

> **Schema version changes when serialized authoritative shape/semantics require migration, not merely when roadmap stage number changes.**

## 3. Pre-Stage17 migration contract

`Stage17HPreStage17MigrationAcceptanceTest` constructs historical bytes rather than only current object values.

Fixture shape:

```text
Playable file format v1
Playable schema v5 (Stage 16)
    ↓ embeds
World file format v2
World schema v8 (Stage 16)
```

The Stage-16 world payload intentionally has:

- no dynamic faction identity directory;
- no Stage-17 territory trailer;
- no institutional diplomacy trailer;
- no customs/doctrine/fiscal-review trailers.

Decode must produce current state with:

- player still independent;
- personal wallet unchanged;
- owned FleetIds/active FleetId unchanged;
- local physical `GameState` snapshots unchanged;
- fleet placements/jump state unchanged;
- construction state/allocator watermarks unchanged;
- zero invented dynamic factions;
- neutral diplomacy defaults only;
- no hidden territory, treaty, embargo, treasury or resource grant.

After migration:

```text
legacy bytes
→ decode current state
→ encode current bytes
→ decode
→ encode
```

must settle into one deterministic canonical representation.

## 4. Unsupported/corrupt persistence behavior

Stage 17H explicitly verifies fail-fast behavior before runtime restoration for:

- future unsupported playable schema;
- future unsupported embedded world schema;
- truncated playable payload;
- trailing unexpected bytes.

The loader must never partially materialize a live world from unsupported transition state.

## 5. Final transition acceptance

`Stage17HEndToEndTransitionAcceptanceTest` starts with a real Stage-16 physical asset instead of a faction-only fixture.

Setup uses ordinary Stage-16 construction mechanics:

```text
existing player fleet
→ create construction project through PlayerConstructionService
→ real personal funding
→ physical material inventory
→ physical delivery to construction site
→ build time
→ completed persistent station EntityId
→ OwnedStationRef
→ ordinary player↔station working-capital transfer
```

Only test bootstrap inventory is authored directly; all Stage-17 assertions begin after the completed Stage-16 asset exists.

Then the acceptance performs:

1. clear affiliation while preserving the Stage-16 fleet/station/money state;
2. restore as an independent player;
3. found a new world-defined faction;
4. verify new treasury is exactly zero and no territory appears;
5. affiliate already-existing fleets and station;
6. verify FleetIds, placements, station EntityId/OwnedStationRef and allocator watermarks do not change;
7. capitalize treasury through conserved personal→treasury transfer;
8. author fiscal policy through the shared faction-policy command path;
9. apply ordinary fiscal runtime to the affiliated station;
10. verify station→treasury tax transfer is exactly conserved;
11. create diplomacy through shared treaty/embargo commands;
12. verify effective market access changes through ordinary diplomacy resolver;
13. declare a claim and verify it starts with zero stabilization and grants no instant sovereignty;
14. save through `PlayableWorldStateCodec`;
15. decode and deterministically re-encode identical bytes;
16. restore a new `PlayerRuntime`;
17. verify faction economy/policy/diplomacy/territory/access/asset projection persists;
18. verify physical money/cargo totals, persistent FleetIds, station refs and allocator watermarks remain unchanged.

## 6. Economic-reaction proof

Stage 17H does not treat policy authoring itself as a resource mutation.

The test deliberately separates:

```text
UpdateFiscalPolicy
→ persistent institutional rule

applyFiscalPolicy
→ ordinary WorldSimulation economic operation
→ completed affiliated market station wallet
→ faction treasury
→ ledgered MONEY_TRANSFER
```

This proves the required `policy → ordinary economy reaction` seam without a player-only shortcut.

## 7. Identity and conservation invariants

The final gate checks:

- same player-owned FleetIds before/after affiliation and save/load;
- same fleet placements;
- same completed station EntityId through `OwnedStationRef`;
- no FleetId allocator movement caused by affiliation/save/load;
- no ConstructionProjectId allocator movement caused by affiliation/save/load;
- no duplicate owned FleetIds;
- no duplicate station refs;
- founding creates a zero treasury only;
- capitalization conserves personal + public money;
- fiscal collection conserves station + treasury money;
- full Stage-17 transition conserves total tracked money;
- Stage-17 management operations do not create/delete inventory;
- binary save/load does not create/reset cargo, money or assets.

## 8. Files added/updated by 17H

Implementation acceptance:

- `src/test/java/com/spacesim/player/Stage17HEndToEndTransitionAcceptanceTest.java`;
- `src/test/java/com/spacesim/persistence/Stage17HPreStage17MigrationAcceptanceTest.java`.

Documentation:

- `docs/persistence_model.md` — synchronized persistence architecture;
- `docs/stage17h_persistence_transition_completion_record.md` — this closeout;
- `docs/development_roadmap.md` — Stage 17 completion / 17.5 activation gate;
- `README.md` — current status.

No production schema field was added solely for milestone bookkeeping.

## 9. Stage 17 completion gate

The implementation is considered canonical only through the mandatory repository gate:

```text
exact PR head
→ full Java-17 clean verification green
→ exact diff/head reviewed
→ merge exact SHA
→ post-merge CI green on exact new main SHA
```

Once this record is present on a post-merge-green `main`, Stage 17 is closed and the next implementation slice is:

```text
Stage 17.5A
→ production MaterialDefinition / HullDefinition / ModuleDefinition
→ protection / slots / hardpoints / compartments schema
```
