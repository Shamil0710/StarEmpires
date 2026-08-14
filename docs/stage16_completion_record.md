# Star Empires — Stage 16: запись о завершении

> Статус: **COMPLETE**  
> Дата закрытия: **2026-08-15**  
> Финальный функциональный PR: **#70 — Stage 16.15: final aggregate physical acceptance**  
> Merge commit: `74bb854a79226280f1770032c1725b9ff32fd40e`  
> Финальный CI: **#1337 / run `31849675260`** — **484/484 tests**, strict Javadoc, JaCoCo gates и shaded desktop JAR build green.
>
> Публикация GitHub Actions artifacts в финальном run упёрлась в quota хранилища. По действующей `docs/ci_artifact_publication_policy.md` это non-blocking publication step; обязательный `clean verify` gate завершился `BUILD SUCCESS`.

---

## 1. Результат Stage 16

Stage 16 завершает переход от владения флотами к владению настоящими производственными активами.

Игрок теперь способен построить **реальную persistent station entity** через тот же физический и экономический construction core, который использует мир:

```text
player wallet + owned fleet
→ authoritative placement/access validation
→ ordinary ConstructionProject + physical construction site
→ real wallet funding
→ open market demand
→ external NPC supply и/или owned SUPPLY_PROJECT
→ physical cargo delivery
→ complete material bill
→ BUILDING по fixed simulation ticks
→ remote continuation
→ save/load mid-build
→ ordinary completed station entity
→ exact OwnedStationRef
→ real station wallet / market / production behavior
→ player deposit/withdraw через ledger transfers
→ save/load completed asset
→ ordinary destruction
→ ownership reconciliation без refund/replacement grant
```

Stage 16 **не вводит отдельную player economy**, virtual construction logistics, instant station spawning или скрытую player faction.

---

## 2. Реализованные функциональные срезы

### 16.1 — persistent ownership foundation — PR #56

- `PlayerState.ownedConstructionProjectIds`;
- `PlayerState.ownedStations` через `OwnedStationRef`;
- playable schema migration со старых save;
- ownership отделён от faction affiliation;
- destroyed/non-station refs очищаются reconciliation без replacement grant.

### 16.2 — independent player construction authoring — PR #57

- world construction schema разделяет settlement/ownership и optional legal faction;
- independent construction site может существовать без `FactionComponent`;
- `PlayerConstructionService` создаёт ordinary world project/site;
- `WorldState` остаётся player-agnostic;
- world/save migration сохраняет существующие faction projects.

### 16.3 — player funding — PR #58

- personal player wallet → physical site `WalletComponent`;
- atomic rollback semantics;
- `EconomicLedger.MONEY_TRANSFER`;
- extra funding увеличивает liquidity, но не сокращает `buildDurationTicks`;
- save/load сохраняет balances и ownership.

### 16.4 — physical manual material delivery — PR #59

- source обязан быть реальным player-owned `FleetId`;
- та же система, отсутствие jump transit, physical range и малая относительная скорость;
- cargo реально уменьшается у корабля и появляется на site;
- remote transfer и non-owned transfer отклоняются.

### 16.5 — open-market construction supply — PR #60

- construction site публикует ordinary procurement demand;
- generic `TradeAI` другой faction способен увидеть opportunity;
- trader физически покупает, летит и продаёт через ordinary `TradeController`;
- site wallet реально платит поставщику;
- material state выводится из физического inventory.

### 16.6 — completion into ordinary owned station — PR #61

- `EXTERNAL_OWNER` project корректно завершается;
- construction materials уходят в явный construction resource sink;
- создаётся обычная station entity нужного archetype;
- остаток site wallet переходит в operating wallet станции;
- `PlayerRuntime` переводит ownership project → exact `OwnedStationRef`;
- completion и ownership переживают save/load.

### 16.7 — authoritative placement/access — PR #62

- `ConstructionPlacementPolicy` и `ConstructionAccessPolicy` вынесены из UI;
- bounds, jump-arrival exclusion, station/site/resource clearance;
- territory/diplomatic access использует существующие Stage-8 данные;
- rejected command не меняет world/player state.

### 16.8 — player-owned station finance — PR #63

- deposit: player wallet → station wallet;
- withdraw: station wallet → player wallet;
- операции требуют реальной owned station и docking;
- atomic transfer + ledger;
- passive income path отсутствует.

### 16.9 — safe project cancellation — PR #64

- пустая funded site может быть отменена с возвратом неиспользованной liquidity;
- world cancellation остаётся authoritative;
- rollback не допускает частичного денежного состояния;
- отмена после physical material delivery и во время `BUILDING` пока отклоняется, чтобы материалы не исчезали без salvage policy.

### 16.10 — persistent `SUPPLY_PROJECT` — PR #65

- owned inactive fleets получают durable construction supply order;
- planner использует discovered physical markets;
- supplier selection учитывает Stage-15 cumulative whole-route risk;
- покупка идёт ordinary market path;
- cargo физически движется через shared FlightDynamics и Stage-10 jump FSM;
- owner delivery не создаёт self-sale и деньги;
- stale supplier вызывает replan без hard reservation;
- order переживает save/load.

### 16.11 — authoritative construction management model — PR #66

- единый read-only model для UI/map;
- live funding, shortfall, material bill, progress, ETA, access, cancellation state;
- assigned supply fleets;
- completed owned stations читаются как ordinary world entities.

### 16.12 — construction assets на global map — PR #67

- owned projects и stations добавлены в strategic snapshot;
- показываются status/progress/missing/funding/supply fleets;
- visibility ограничена player discovery;
- strategic UI остаётся view + command adapter.

### 16.13 — functional local construction UI — PR #68

- local placement ghost и valid/invalid preview;
- authoritative clearance visualization;
- archetype funding/material/ETA panel;
- project management panel;
- funding, cancellation, `SUPPLY_PROJECT`, save/load вызывают ordinary services;
- UI не мутирует `Transform`, inventory, progress или completion напрямую.

### 16.14 — remote construction/destruction hardening — PR #69

- mid-build player leaves construction system through real jump FSM;
- project продолжает работу в remote coarse simulation;
- save/load сохраняет `buildStartedTick`, `buildDurationTicks` и material state;
- remote scheduler создаёт ordinary station;
- ownership reconciles после remote completion;
- ordinary destruction удаляет station и `OwnedStationRef` без refund/replacement.

### 16.15 — final aggregate physical acceptance — PR #70

Один deterministic test свёл вместе основные Stage-16 boundaries:

- independent project creation/funding;
- external cross-faction market supply;
- owned persistent `SUPPLY_PROJECT`;
- manual player cargo delivery;
- BUILDING only after real fulfillment;
- physical jump away;
- save/load mid-build;
- remote completion;
- physical return и завершение jump FSM;
- docking at completed station;
- real deposit/withdraw;
- save/load completed asset;
- ordinary destruction и ownership cleanup.

Последний CI после исправления неверного test-arrival gate доказал **484/484 green**. Production docking semantics не ослаблялись: тест теперь ждёт не только materialized `IN_SYSTEM`, но и полного завершения Stage-10 jump FSM перед docking.

---

## 3. Definition of Done — итог

Stage 16 считается закрытым, потому что автоматизированно подтверждены:

- persistent project/station ownership и migration;
- authoring без hidden player faction;
- physical placement/access validation;
- real funding и money conservation;
- physical manual delivery;
- open-market external supply;
- autonomous owned-fleet supply;
- material-backed BUILDING transition;
- fixed-tick construction duration;
- remote continuation;
- mid-build save/load;
- ordinary completion entity;
- exact ownership reconciliation;
- station finance без passive income;
- completed-station persistence;
- destruction без free replacement;
- read-only UI boundaries;
- full aggregate physical acceptance.

Stage-16 CI gate: **PASS**.

---

## 4. Сохранённые архитектурные инварианты

### Player и AI не получают несовместимые миры

Construction site, market, cargo, station и destruction остаются обычными world objects. Player-facing services только валидируют intent и вызывают общий simulation core.

### Ownership != faction identity

`OwnedStationRef` и ownership project находятся в `PlayerState`. Наличие owned station не превращает игрока автоматически в faction до Stage 17.

### Нет passive income

Player-owned station имеет собственный operating wallet. Деньги переходят между player/station только явными atomic transfers.

### Нет virtual delivery

Order, route или buy demand не равны доставке. Материал считается delivered только после появления в physical site inventory.

### Нет instant construction

Completion определяется persisted simulation ticks и material-backed lifecycle.

### Remote simulation сохраняет совместимые последствия

Переход active ↔ remote не создаёт альтернативной construction economy и не пересчитывает уже resolved construction contract.

### Presentation остаётся read-only

Local/global UI показывает authoritative state и отправляет commands, но не завершает construction и не изменяет inventory/progress самостоятельно.

---

## 5. Сознательно отложенный долг

Следующие пункты **не являются незаметно недоделанными требованиями Stage 16**; они явно отложены до появления корректной общей механики.

### 5.1. Отмена после physical material delivery

Текущий безопасный baseline:

```text
empty / funded site → cancellation разрешена
physical required materials already delivered → cancellation rejected
BUILDING → voluntary cancellation rejected
```

Причина: до появления authoritative salvage/material-fate-by-progress нельзя удалять доставленные материалы или возвращать их «из воздуха».

Будущая модель должна создать physical recoverable cargo/salvage и сохранять resource conservation.

### 5.2. Remote project placement

Stage 16 гарантирует remote **continuation** уже физически созданного проекта. Создание новой site из другой системы не должно появляться как UI teleport. Оно требует реального construction fleet/builder/facility capability и остаётся будущим расширением.

### 5.3. Construction capability и tech tiers

Текущая `ConstructionDurationPolicy` использует material work + baseline assembly rate и сохраняет resolved duration. Реальные builder/shipyard capabilities, specialized components и technology tiers вводятся позже через общую data-driven capability model.

### 5.4. Station fitting / shipbuilding

Stage 16 строит station archetypes, но не вводит полноценный station fitting, shipyard production или ship fitting. Эти основы относятся к Stage 17.5 и последующим этапам.

---

## 6. Переход к Stage 17

После Stage 16 проект имеет физическую цепочку:

```text
один player FleetId
→ несколько owned fleets
→ persistent autonomous orders
→ physical construction project
→ owned ordinary station
```

Следующий основной этап — **Stage 17: собственная фракция игрока**.

Stage 17 должен переиспользовать Stage-8 faction economy/politics и перевести уже существующие owned assets под player faction **без замены их `FleetId` / `EntityId`**.

Минимальная целевая цепочка:

```text
independent player
+ owned fleets
+ owned stations
→ create player faction identity
→ faction treasury / policies / territory / diplomacy
→ explicit asset affiliation transition
→ shared AI/player faction rules
```

До появления Stage-17 contracts нельзя обходить переход созданием скрытой специальной player faction или перепривязкой физических объектов через respawn.
