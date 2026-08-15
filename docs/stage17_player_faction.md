# Star Empires — Stage 17: собственная фракция игрока

> Статус: **ACTIVE — 17A foundation**  
> Основание: Stage 16 завершён; игрок уже владеет persistent `FleetId`, construction projects и конкретными physical station `EntityId`, но ownership всё ещё отделён от faction/legal identity.

---

## 1. Цель Stage 17

Stage 17 переводит игрока из состояния независимого владельца активов в полноценного политико-экономического актора мира, не создавая отдельную player-only симуляцию.

Целевая цепочка:

```text
independent PlayerState
→ явная команда основания собственной faction
→ ordinary world-level faction economy + strategy
→ существующие owned assets получают affiliation без respawn
→ personal wallet и faction treasury остаются разными счетами
→ real treasury transfers / taxes / subsidies / policies
→ territory / diplomacy / market access
→ faction management через read-only model + ordinary commands
→ save/load сохраняет identity, assets, treasury, policies и relations
```

Главный инвариант Stage 17:

> **Основание фракции не создаёт деньги, ресурсы, корабли, станции или территорию. Оно создаёт только новый persistent political/economic identity и право игрока управлять им.**

---

## 2. Архитектурная база, уже существующая до Stage 17

Stage 8 уже предоставляет world-level фракционную экономику:

- `FactionEconomicState` / treasury;
- `FactionStrategicState`;
- directed relations;
- territory ownership;
- market access;
- station taxes и foreign-territory tariffs;
- stock / production policies;
- strategic goals;
- liquidity support;
- economic investment/expansion seams.

Stage 12–16 предоставляет player layer:

- личный `walletMilliCredits`;
- optional `PlayerState.factionContentId`;
- `ownedFleetIds`;
- `ownedConstructionProjectIds`;
- `ownedStations`;
- persistent save envelope;
- физические travel/trade/mining/combat/construction rules.

Stage 17 не должен дублировать эти системы. Он должен корректно связать их.

---

## 3. Обнаруженный identity gap

В текущем коде существуют два разных способа адресовать faction:

```text
world strategic/economic layer
→ stable String factionContentId

local ECS hot path
→ dense int FactionComponent.factionId
```

Встроенный `ContentCatalog` связывает эти представления для authored factions через `FactionDefinition(id, runtimeId, displayName)`.

Но player-created faction **не должна** добавляться в immutable content catalog во время игры:

- это изменило бы semantic content fingerprint;
- runtime-created political state стал бы частью authored game content;
- старые saves потребовали бы искусственной content migration;
- explicit founding превратился бы в скрытый заранее существующий player faction definition.

Поэтому Stage 17 использует следующий принцип:

> **Authored faction definitions остаются content data. Player-created faction identity является persistent world data. Runtime resolver объединяет оба источника только на границе локальной ECS.**

---

# 4. Подэтапы Stage 17

## 17A — player faction identity / explicit founding

### 17A.1 — persistent foundation state transition

Первый foundation slice реализует `PlayerFactionFoundationService.foundFaction(...)` как pure persistent transition.

Вход:

```text
current PlayableWorldState
+ immutable ContentCatalog
+ requested stable faction ID
```

Предусловия:

- `PlayerState` существует;
- игрок независим (`factionContentId == null`);
- stable ID имеет canonical `faction.*` syntax;
- ID не конфликтует с authored content faction;
- ID не конфликтует с уже существующим world economic/strategic actor.

Результат:

```text
new FactionEconomicState(
    treasury = 0,
    liquidity reserve = 0,
    policy budget = 0
)

new FactionStrategicState(
    neutral access threshold,
    no explicit relations,
    no controlled systems,
    zero tax/tariff,
    no stock/production/strategic policies
)

PlayerState.factionContentId = new stable faction ID
```

Не меняются:

- `PlayerState.walletMilliCredits`;
- `ownedFleetIds`;
- active `FleetId`;
- construction ownership;
- station ownership;
- system-local `EntityId`;
- inventories;
- wallets физических сущностей;
- territory;
- local `FactionComponent`.

Это сознательно **не весь 17A**: новый world-defined faction ещё нельзя материализовать в local ECS через существующий catalog-only runtime mapping. Следующий slice закрывает этот bridge.

### 17A.2 — persistent world faction identity directory + runtime resolver

Нужно добавить bounded world-owned metadata для non-content factions, минимум:

```text
stableFactionId
runtimeFactionId
publicDisplayName
origin = PLAYER_CREATED
```

Требования:

- authored content factions продолжают использовать свои существующие runtime IDs;
- dynamic IDs выделяются детерминированно из bounded faction slot-capacity;
- ID переживает save/load и не зависит от порядка HashMap/iteration;
- collision с authored runtime ID невозможен;
- display name не изменяет content fingerprint;
- отсутствие free runtime slot даёт явный rejection, а не overwrite;
- resolver умеет `stable ID ↔ runtime ID ↔ display name` для обоих типов faction.

На этом же срезе `WorldSimulation.restore` должен перестать считать valid только catalog factions и использовать unified resolver.

### 17A.3 — live PlayerFactionService

После появления resolver pure foundation transition становится live command boundary:

```text
PlayerFactionService.foundFaction(...)
→ authoritative validation
→ ordinary world faction actor creation
→ PlayerState affiliation update
→ no asset affiliation yet
```

Команда должна быть atomic: failure не оставляет half-created strategic/economic state.

---

## 17B — affiliation существующих owned assets

После основания faction уже существующие player assets переводятся под неё **без замены физических identity**.

### Fleets

Для каждого owned `FleetId`:

- тот же `FleetId` сохраняется;
- local `EntityId` не меняется при affiliation;
- transit fleet payload получает ту же faction identity без отмены jump;
- cargo, wallet, damage, orders и velocity не сбрасываются.

### Stations

Для каждого `OwnedStationRef`:

- тот же `EntityId` сохраняется;
- station inventory/wallet/production/market state сохраняются;
- добавляется/меняется только legal faction affiliation;
- никакого station respawn/clone;
- independent Stage-16 station становится ordinary station новой faction.

Ownership в `PlayerState` **не удаляется**: player ownership и faction affiliation остаются разными слоями.

---

## 17C — personal wallet ↔ faction treasury

Личный кошелёк и faction treasury остаются отдельными счетами.

```text
player wallet
↔ explicit capital contribution / withdrawal boundary
faction treasury
```

Правила:

- основание faction даёт treasury = 0;
- начальный капитал появляется только через реальный player→treasury transfer;
- каждое движение денег имеет `EconomicLedger.MONEY_TRANSFER` semantics;
- withdrawal не может превысить treasury;
- никакого автоматического слияния personal wallet и treasury;
- ownership станции не означает автоматический sweep её wallet в treasury.

---

## 17D — territory / sovereignty

Наличие станции само по себе не создаёт sovereign territory.

Baseline territory acquisition должен использовать explicit Stage-8 strategic state и отдельную legal rule/policy.

Нужно различать:

```text
physical property ownership
!=
station faction affiliation
!=
territorial sovereignty
```

Unclaimed system может быть заявлена только через явное действие/условия. Foreign-controlled system не меняет owner от самого факта строительства player station.

---

## 17E — diplomacy / market access

Player faction становится обычным участником directed relations.

Требования:

- relations хранятся world-level;
- self relation не хранится и считается 100;
- missing relation имеет обычный neutral baseline;
- market access материализуется через тот же policy runtime;
- запрет/доступ применяется одинаково к player и AI faction;
- player не получает omniscient diplomacy data вне known-information rules UI.

---

## 17F — policies / treasury budgets / faction economy

После 17B/17C существующие Stage-8 механики должны работать для player faction без special-case economy:

- liquidity support;
- station tax;
- foreign-territory tariff;
- stock policy;
- production policy;
- strategic goals;
- construction/investment budget seams.

Player UI только задаёт policy state/commands. Реальные последствия исполняет тот же world core.

---

## 17G — faction management model / UI

Сначала authoritative read-only model, затем UI.

Минимум отображать:

- stable/public faction identity;
- personal wallet отдельно от treasury;
- owned/affiliated fleets;
- owned/affiliated stations;
- controlled territory;
- directed relations / access;
- taxes/tariffs;
- liquidity reserve и policy budgets;
- active strategic/economic policies.

UI не мутирует world/player state напрямую.

---

## 17H — persistence / aggregate acceptance

Финальный deterministic Stage-17 scenario:

```text
independent player with existing physical assets
→ explicitly found faction
→ no free money/assets/territory
→ same FleetId/EntityId become affiliated
→ explicit player→treasury capital transfer
→ ordinary faction policy changes real economy
→ legal territory/access action
→ diplomacy relation change
→ save/load
→ same faction identity/runtime mapping
→ same physical assets and balances
→ continue simulation
→ no duplication / reset / hidden grant
```

---

# 5. Persistence strategy

## 5.1. Foundation slice

17A.1 использует уже существующие `FactionEconomicState`, `FactionStrategicState` и `PlayerState.factionContentId`.

Это намеренно позволяет проверить главный no-grant contract до изменения local ECS schema.

## 5.2. Identity-directory slice

17A.2 потребует новой world schema, потому что dynamic faction metadata/runtime mapping является authoritative persistent world state.

Legacy migration:

```text
old world save
→ dynamic faction identity list = empty
→ authored content factions resolve exactly as before
```

## 5.3. Local faction capacity

Текущие:

```text
Constants.MAX_FACTIONS = 3
ReputationComponent = float[3]
FactionMarketAccessComponent = boolean[3]
```

не подходят для dynamic faction runtime ID.

При расширении capacity нельзя просто изменить constant. Нужна local `GameState` schema migration, которая:

- сохраняет существующие первые authored slots побитово;
- дополняет новые reputation slots нулём;
- сохраняет old faction IDs;
- валидирует bounded capacity;
- не меняет item arrays.

---

# 6. Acceptance matrix Stage 17

## 17A

- independent player может явно основать faction;
- affiliated player не может молча основать вторую faction;
- ID collision с content/world faction rejected;
- treasury после founding = 0;
- controlled systems после founding = empty;
- personal wallet неизменен;
- owned `FleetId`/`EntityId` неизменны;
- save/load сохраняет founded stable ID;
- legacy save не получает hidden dynamic faction;
- runtime resolver детерминирован после его введения.

## 17B

- affiliation не respawn-ит fleet/station;
- mid-jump fleet сохраняет transit state;
- cargo/wallet/damage/order не сбрасываются;
- station production/market не сбрасываются;
- destroyed asset не возвращается через faction reconciliation.

## 17C

- founding treasury не mint-ит деньги;
- player→treasury transfer сохраняет сумму;
- treasury→player transfer сохраняет сумму;
- insufficient balance rejected atomically;
- save/load сохраняет оба счета отдельно.

## 17D–17F

- station ownership != automatic territory;
- explicit claim obeys legal/access rules;
- diplomacy affects ordinary market access;
- taxes/tariffs/subsidies use real wallet transfers;
- player faction policies reuse Stage-8 mechanics.

## 17H

Полный aggregate scenario должен пройти без hidden grants, virtual assets, ID replacement или player-only fiscal path.

---

# 7. Что не входит в Stage 17

Stage 17 не должен преждевременно реализовывать:

- advanced ship fitting/combat depth — Stage 17.5;
- strategic war/front AI — Stage 18;
- galaxy generation/discovery breadth — Stage 19;
- NPC/mission/reputation RPG layer — Stage 20;
- широкую technology ladder/content balance — Stage 21.

Ship mathematics/design documents могут развиваться параллельно как authoring baseline, но не считаются runtime Stage-17 implementation.

---

# 8. Immediate implementation order

```text
17A.1 pure founding state transition + persistence acceptance
→ 17A.2 dynamic faction identity directory + bounded runtime resolver + migrations
→ 17A.3 live founding command boundary
→ 17B existing asset affiliation without identity replacement
→ 17C treasury capitalization/transfers
→ 17D territory
→ 17E diplomacy/access
→ 17F policies
→ 17G management model/UI
→ 17H aggregate acceptance + completion record
```

До завершения 17A.2 **не** назначать player-created faction raw `factionId = 3` и не увеличивать `MAX_FACTIONS` без migration coverage.
