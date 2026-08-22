# Star Empires — Stage 20.5 Runtime + Visual Integration Completion Record

> Статус: **COMPLETE — canonical after PR #318 passes the required exact-head merge gate and reaches `main`**
> Дата закрытия: **2026-08-22**
> Scope: обязательный integration gate между принятым Stage-20 generated world и Stage 21.

## 1. Результат

Stage 20.5 превращает accepted generated-world authority в один persistent playable runtime:

```text
accepted Stage-20 world + stable identities
→ ordinary Stage-18 sources and industrial entities
→ persistent freight FleetIds, orders and conserved cargo lots
→ ordinary neighbor-only jump FSM with exact physical arrivals
→ stable identity-based sprite binding
→ atomic save/load of the composed runtime
→ Stage 21
```

Materialization не перезапускает генератор и не переигрывает ownership, routes, industrial
specialization или cadence. Capacity и reserved throughput не превращаются в подаренный inventory.
Presentation остаётся projection и не владеет collision, fitting, cargo, sensor или economy state.

## 2. Закрытые slices

| Slice | Production result | Evidence |
| --- | --- | --- |
| 20.5A | finite occurrence/source authority → ordinary Stage-18 extraction outpost and storage; compatible surface/deep extraction; exact reserve depletion | PR #314 and source/outpost persistence tests |
| 20.5B | accepted ownership ordinal → one persistent `FleetId`; physical empty cargo hold, order, route, cadence and cargo-lot persistence | PR #315 and `Stage20FreightRuntimeMaterializerTest` |
| 20.5C | generated station/facility/storage/yard plans → canonical Stage-18 industrial runtime without duplicate entities | PR #313 and industrial materializer/bridge tests |
| 20.5D | ordinary edge transition → exact saved hierarchical position and velocity; same `FleetId` across local `EntityId` replacement and save/load | PR #316 and `Stage20LiveArrivalAuthorityIntegrationTest` |
| 20.5E | utility, cargo, mining, light/medium combat, support, infrastructure, resource and wreck roles → production asset catalog with real alpha and stable identity binding | PR #317 and `Stage20MinimumPlayableSpriteCatalogTest` plus asset-contract tests |
| Final composition | A–E execute as one generated-world runtime with atomic campaign/world/freight persistence | PR #318 and `Stage205GeneratedWorldPlayableAcceptanceTest` |

## 3. Final runtime authority

`Stage20GeneratedWorldRuntimeBridge` is the final composition boundary. It:

- consumes an already accepted Stage-20 campaign and its exact fingerprints;
- materializes source outposts and generated industrial infrastructure through ordinary Stage-18
  state;
- allocates freight fleets sequentially from the authoritative world allocator and preserves the
  same `FleetId` in the Stage-20 freight sidecar;
- starts every cargo hold empty;
- exposes extraction, outpost-to-hub staging, physical load/unload and delivery operations;
- requests every route hop through `WorldSimulation.requestFleetJump`;
- synchronizes position and velocity only from exact Stage-20 arrival materialization;
- destroys the same world fleet and its cargo lots without replacement;
- resolves presentation through stable content identity rather than filename authority.

The composed checkpoint is `Stage20GeneratedWorldRuntimePersistentState`. Its codec stores campaign,
ordinary `WorldState`, active system and complete freight state as one bounded deterministic payload.
Restore cross-validates world/freight `FleetId` sets, transit state and destroyed assets and does not
invoke Stage-20 generation or freight planning.

## 4. Aggregate acceptance

`Stage205GeneratedWorldPlayableAcceptanceTest` uses an already accepted deterministic Stage-20
fixture and proves the complete causal chain:

1. all accepted freight slots become ordinary world fleets with the same identities;
2. a compatible finite source is physically extracted without a hidden cargo grant;
3. cargo moves from extraction outpost to the canonical hub and becomes a conserved cargo lot;
4. every route hop is requested through the ordinary jump FSM;
5. each arrival applies the exact persisted destination, hierarchical position and velocity;
6. save/load during `IN_TRANSIT` preserves campaign, world, freight and presentation identity;
7. delivery unloads into the generated industrial endpoint's canonical inventory;
8. the alternative destruction branch removes the real fleet and cargo, with no replacement or
   virtual delivery;
9. repeated encoding is byte-deterministic and truncated payloads fail closed.

This acceptance closes the five Stage-20L seams exactly:

- `SOURCE_SUPPLY_MATERIALIZATION`;
- `FREIGHT_FLEET_MATERIALIZATION`;
- `CARGO_ORDER_AND_LOT_MATERIALIZATION`;
- `INDUSTRIAL_ENTITY_MATERIALIZATION`;
- `LIVE_ARRIVAL_AUTHORITY_INTEGRATION`.

## 5. Completion gate

All Stage-20.5 completion criteria are covered by production code and automated acceptance. The
final repository gate remains the project-wide Java-17 `clean verify`: tests, JaCoCo checks, strict
Javadoc and desktop packaging must pass on the exact PR #318 head before that head is merged to
`main`.

Stage 21 may now build NPC, mission and reputation behavior over existing generated ships, stations,
cargo, locations, discovery and travel state. Final faction catalog breadth, technology/content
balance, remaining asset replacement, VFX/animation and UX polish remain Stage 22/23 work.
