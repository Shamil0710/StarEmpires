# Star Empires — Stage 20.5 Runtime + Visual Integration Plan v1

> Статус: **COMPLETE — accepted post-Stage-20 gate; Stage 21 unblocked**
> Основание: `docs/stage20_physical_world_generation_plan.md` — **Stage 20A–20L COMPLETE**; `docs/stage20l_physical_world_acceptance_matrix_v1.md` — accepted final Stage-20 composition gate.  
> Назначение: превратить завершённый Stage-20 generated-world authority в минимально цельный playable runtime с production-bound 2D presentation до начала Stage 21 RPG / Living World.

---

## 1. Почему Stage 20.5 существует

Stage 20 завершил generation, physical/economic bootstrap, discovery, special locations, intelligence latency, cadence, persistence и final world-quality acceptance. Это означает, что мир уже определён как authoritative state.

Однако Stage 20L намеренно оставляет пять downstream integration seams:

1. `SOURCE_SUPPLY_MATERIALIZATION`;
2. `FREIGHT_FLEET_MATERIALIZATION`;
3. `CARGO_ORDER_AND_LOT_MATERIALIZATION`;
4. `INDUSTRIAL_ENTITY_MATERIALIZATION`;
5. `LIVE_ARRIVAL_AUTHORITY_INTEGRATION`.

Эти seams не являются незавершённой генерацией. Они являются переходом от принятых планов/идентичностей/фингерпринтов к уже существующим production runtime systems.

Одновременно игра всё ещё может опираться на schematic/placeholder presentation, тогда как Stage 21 должен добавлять NPC, missions, reputation и living-world behavior поверх реального игрового мира.

Поэтому Stage 20.5 является обязательным integration gate:

```text
Stage-20 accepted generated world
+ exact runtime-materialization seams
+ stable entity identities
+ accepted physical hull/station/resource dimensions
+ project faction visual language
→ live persistent runtime entities
→ minimum coherent top-down sprite presentation
→ representative playable generated-world session
→ Stage 21
```

Stage 20.5 не переопределяет world generation, ownership, logistics cadence, fitting physics, resource ontology, production recipes, combat physics или faction policy.

---

## 2. Главные инварианты

### 2.1. Generated authority не пересчитывается при materialization

Runtime bridge обязан потреблять принятые Stage-20 планы и stable IDs.

Запрещено:

- повторно генерировать ресурсы, станции или routes;
- менять owner ради удобства runtime;
- заменять accepted freight allocation виртуальной доставкой;
- создавать hidden cargo/stock;
- выдавать freighter без физического `FleetId`;
- менять arrival position/velocity ради render convenience;
- создавать player-only materialization path.

### 2.2. Presentation не является simulation authority

```text
sprite / VFX / icon / animation
≠ hull stats
≠ fitting
≠ cargo
≠ sensor state
≠ collision authority
≠ world identity
```

Смена visual asset не меняет authoritative state.

### 2.3. Persistent identity сохраняется

Один и тот же generated/runtime объект сохраняет identity через:

```text
materialization
→ ordinary runtime updates
→ dematerialization / LOD
→ save
→ load
→ rematerialization
```

без ID replacement и бесплатного state reset.

---

## 3. Stage 20.5A — Source supply materialization — COMPLETE

Закрывает `SOURCE_SUPPLY_MATERIALIZATION`.

Accepted Stage-20 finite occurrences, extraction ownership/capacity и industrial supply evidence должны материализоваться как ordinary production source state либо как явно существующий физический stock.

Обязательные условия:

- точная ссылка на accepted generated occurrence/source identity;
- finite reserve не увеличивается при bridge;
- extraction/producer throughput не превращается в уже существующий cargo;
- capacity reservations не являются inventory;
- source state использует Stage-18 resource/facility semantics;
- repeated materialization идемпотентна по identity;
- cross-seed/cross-fingerprint input rejected fail-closed.

DoD 20.5A:

```text
accepted source authority
→ live source runtime state
→ ordinary extraction/production consumption
→ no hidden stock grant
```

---

## 4. Stage 20.5B — Freight fleet + cargo materialization — COMPLETE

Закрывает одновременно:

- `FREIGHT_FLEET_MATERIALIZATION`;
- `CARGO_ORDER_AND_LOT_MATERIALIZATION`.

Stage-20E/20F ownership ordinals становятся реальными persistent `FleetId` assets через существующий fleet/ownership runtime.

Требования:

- каждый accepted freight ownership slot материализуется ровно один раз;
- уже занятые essential-service slots не переиспользуются;
- faction owner сохраняется точно;
- hull/fit выбирается только из явной accepted/runtime-authorized mapping;
- cargo capacity выводится из реального hull/fitting state, а не из route label;
- retained physical route становится ordinary ordered transport route;
- cargo появляется только через source inventory/extraction + loading operation;
- cargo lot имеет conserved quantity и stable provenance;
- transport order имеет cadence/deadline derived from Stage-20J evidence;
- loss/interdiction/destruction ordinary runtime ship действительно меняет future supply.

DoD 20.5B:

```text
ownership ordinal
→ persistent FleetId
→ physical cargo lot
→ ordinary transport order
→ actual route execution
→ delivery / delay / loss consequences
```

---

## 5. Stage 20.5C — Industrial entity materialization — COMPLETE

Закрывает `INDUSTRIAL_ENTITY_MATERIALIZATION`.

Accepted generated stations, facility slots, canonical storage snapshots и installed yards должны стать ordinary runtime station/facility/storage/yard entities без повторного принятия industrial decisions.

Требования:

- exact generated station identity сохраняется;
- owner сохраняется;
- local physical position сохраняется;
- facility definition/instance identity соответствует Stage-18 canonical state;
- storage inventory начинается ровно с accepted initial inventory;
- yard presence/absence соответствует accepted Stage-20F plan;
- shared power/labor/heat/service limits продолжают действовать в runtime;
- specialization role является derived projection, не bonus-tag;
- save/load round-trip не создаёт duplicate station/facility/yard.

DoD 20.5C:

```text
generated industrial authority
→ live station/facility/storage/yard entities
→ ordinary Stage-18 runtime
→ same constraints after save/load
```

---

## 6. Stage 20.5D — Live arrival authority integration — COMPLETE

Закрывает `LIVE_ARRIVAL_AUTHORITY_INTEGRATION`.

Каждый ordinary inter-system arrival обязан применять уже accepted/persisted Stage-20D endpoint semantics к live transition authority.

Требования:

- immediate jump остаётся neighbor-only;
- route executor не пропускает intermediate systems;
- arrival `StarSystemId` соответствует explicit edge destination;
- persisted physical arrival position применяется без screen-space clamp;
- persisted arrival velocity применяется без reset;
- camera/materialization origin не меняет physical coordinate;
- jump energy/spool/cooldown/damage constraints остаются production authority;
- live arrival не раскрывает скрытую discovery information автоматически.

DoD 20.5D:

```text
ordinary edge transition
→ exact persisted arrival endpoint
→ live position + velocity
→ continued physical runtime
```

---

## 7. Stage 20.5E — Minimum Playable Sprite Pack — COMPLETE

Это минимальный production-ready visual pack, достаточный для проверки accepted world/runtime как одной игры. Это не финальный fleet catalogue и не Stage-23 polish.

### 7.1. Минимальный набор ролей

Минимум:

- light/player utility hull;
- cargo/transport hull;
- mining/industrial hull;
- light combat/escort hull;
- medium combat hull;
- one support/specialist hull, если он требуется выбранным representative runtime scenario;
- generic station core / trade-dock presentation;
- industrial/extraction station presentation;
- shipyard/major construction presentation;
- Stage-20 resource-body sprite family;
- wreck/derelict sprite family для Stage-20H special-location path.

Количество конкретных hull IDs фиксируется только после проверки production-valid content authority. Provisional Stage-17.5/19 test identities не становятся canon автоматически.

### 7.2. Sprite production contract

Все gameplay sprites:

- strict top-down orthographic;
- no perspective tilt;
- transparent background;
- clean alpha;
- stable orientation convention;
- stable pivot/center metadata;
- physical/readability scale derived from accepted dimensions;
- readable silhouette at normal gameplay camera zoom;
- no baked starfield/background/UI/text/frame;
- no baked exhaust, muzzle flash, beam, missile trail, smoke, debris or transient combat FX;
- emissive, engine, damage and transient effects are separate overlays/VFX;
- visible hardpoints/module bays/sensor blocks use stable authored anchors;
- externally visible module count/placement must correspond to authoritative fitting data;
- interactive elements must remain large/readable enough for dedicated sprite/animation/damage treatment;
- faction markings may alter presentation but may not obscure hull/module gameplay readability.

### 7.3. Runtime asset binding

Stage 20.5 не считается закрытым, если PNG просто лежат в assets.

Требуется:

- deterministic mapping from stable hull/station/resource identity to presentation asset;
- explicit versioned fallback for content outside the minimum pack;
- sprite filename never becomes simulation identity;
- correct world scale/orientation/pivot;
- authoritative hardpoints align with authored anchors;
- render swap leaves collision/sensor/weapon/economy state unchanged;
- entity keeps visual identity through save/load and materialization boundaries;
- minimum pack works in existing tactical viewer and generated-world playable path without alternate simulation.

---

## 8. Acceptance scenarios

Stage 20.5 final acceptance uses at least one deterministic generated seed already accepted by Stage 20L and exercises one shared runtime chain.

Mandatory scenario coverage:

### Generated economy session

- accepted generated system materializes live industrial/source entities;
- at least one remote input route owns a real freight fleet;
- real cargo lot is loaded, transported and delivered;
- inventory changes are conserved;
- destroying/delaying the freighter changes the supply outcome rather than triggering virtual replacement.

### Inter-system travel session

- multi-hop route executes edge-by-edge;
- each arrival uses accepted endpoint position/velocity;
- no map-edge clamp/teleport/reset;
- save/load between hops preserves route and physical state.

### Visual integration session

At normal gameplay zoom:

- utility/cargo/mining/combat ships are distinguishable without debug labels;
- light and medium combat hulls remain readable in multi-ship battle;
- trade/industrial/shipyard infrastructure is distinguishable;
- resource bodies and wreck/derelict entities are recognizable;
- hardpoint/module alignment matches authoritative fitting for minimum-pack hulls;
- transient VFX are not baked into base sprites.

---

## 9. Stage 20.5 completion gate

Stage 20.5 is **COMPLETE**. The accepted implementation satisfies:

1. all five Stage-20L runtime bridge seams are closed by production code;
2. one accepted generated world can materialize into ordinary runtime without regeneration or hidden grants;
3. physical freight exists as persistent fleets + cargo lots + ordinary orders;
4. industrial stations/facilities/storage/yards exist as ordinary runtime entities;
5. live inter-system arrival consumes accepted endpoint authority;
6. the minimum role-complete sprite pack is actually bound to runtime;
7. save/load/materialization preserves physical and visual identity;
8. Stage-19 combat and Stage-20 generated-world path share the same simulation authority;
9. CI/acceptance proves no player-only bridge, virtual delivery, hidden restock or presentation-owned state;
10. remaining placeholder assets are allowed only outside the explicitly accepted minimum set.

---

## 10. Что остаётся после Stage 20.5

Stage 20.5 не закрывает:

- final faction fleet catalogue;
- final technology/content balance;
- all faction-specific hull variants;
- final projectile/VFX/animation library;
- final UI/UX polish;
- final art replacement for every placeholder.

Эти задачи остаются у Stage 22/23.

После Stage 20.5 Stage 21 получает уже live generated world с реальными runtime entities и coherent minimum presentation, поэтому NPC/missions/reputation могут ссылаться на существующие корабли, станции, cargo, locations, discovery и travel state, а не на отдельный scripted layer.

---

## 11. Accepted implementation order and evidence

```text
20.5A Source supply materialization
→ 20.5B Freight fleet + cargo/order materialization
→ 20.5C Industrial entity materialization
→ 20.5D Live arrival authority integration
→ 20.5E Minimum Playable Sprite Pack + runtime binding
→ 20.5 final generated-world playable acceptance
→ Stage 21 RPG / Living World
```

Если seam dependency требует небольшой перестановки внутри A–D, merge order может меняться, но final gate обязан закрывать все пять Stage-20L boundaries до Stage 21.

The accepted merge order was `20.5C → 20.5B → 20.5D → 20.5E → 20.5A → final acceptance`.
The order did not alter the locked causal authority: the final composition consumes the already
accepted Stage-20 generation, ownership, cadence and persistence state.

| Slice | Merge evidence | Primary acceptance evidence |
| --- | --- | --- |
| 20.5A | PR #314 | `Stage20SourceSupplyMaterializerTest`, `Stage20SourceOutpostMaterializerTest`, `Stage20SourceOutpostCampaignPersistenceTest` |
| 20.5B | PR #315 | `Stage20FreightRuntimeMaterializerTest` |
| 20.5C | PR #313 | `Stage20IndustrialEntityMaterializerTest`, `Stage20GeneratedIndustrialRuntimeBridgeTest` |
| 20.5D | PR #316 | `Stage20LiveArrivalAuthorityIntegrationTest` |
| 20.5E | PR #317 | `Stage20MinimumPlayableSpriteCatalogTest` and asset-contract tests |
| Final composition | PR #318 | `Stage205GeneratedWorldPlayableAcceptanceTest` |

The final acceptance materializes an accepted generated seed into one ordinary runtime, stages
physically extracted cargo from source outpost to hub, loads a conserved cargo lot, traverses every
neighbor edge through the production jump FSM, applies exact saved arrival position/velocity,
round-trips an in-transit checkpoint and delivers into the generated industrial endpoint. A branch
destroys the same persistent freight fleet and proves cargo loss without replacement or virtual
delivery. The complete evidence and remaining boundaries are recorded in
`docs/stage20_5_completion_record.md`.
