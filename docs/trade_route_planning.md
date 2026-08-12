# Trade Route Planning — Stage 5 contract

## 1. Назначение

Stage 5 отделяет **решение, куда и что везти**, от **исполнения рейса конкретным кораблём**.

До этого `TradeAISystem` одновременно:

1. сканировал ECS-станции;
2. перебирал пары supplier/consumer;
3. рассчитывал размер партии и прибыль;
4. выбирал маршрут;
5. двигал корабль;
6. выполнял покупку и продажу.

Теперь route discovery вынесен в pure/value-layer, а `TradeAISystem` остаётся execution/FSM-слоем.

## 2. Поток данных

```text
Ashley ECS markets
        |
        v
MarketDirectory.rebuild()          один общий snapshot на simulation tick
        |
        +--> StationMarket[]        immutable defensive snapshots
        +--> suppliersByItem
        +--> consumersByItem
        +--> TradeOpportunity[]     bounded shared shortlist
                    |
                    v
FleetTradeProfile                  immutable snapshot конкретного корабля
                    |
                    v
TradeRoutePlanner                  pure route decision
        |                           cargo + wallet + demand + liquidity
        |                           reputation price + distance/time
        |                           optional route cost model
        v
TradeRoute / TradeSaleRoute        immutable value objects, EntityId only
        |
        v
TradeAISystem                      cooldown + movement + stale revalidation + FSM
        |
        v
TradeController                    authoritative atomic transfer
        |
        v
EconomicLedger
```

Ни `TradeRoutePlanner`, ни route value objects не содержат Ashley `Entity` references.

## 3. MarketDirectory

`MarketDirectory` перестраивает read-only market snapshot один раз перед обновлением торговых FSM.

Каждая `StationMarket` содержит:

- persistent `EntityId`;
- координаты;
- faction ID;
- wallet balance;
- inventory capacity и stock;
- target stock;
- raw buy/sell prices;
- tradable mask.

Все mutable ECS-массивы копируются. Изменение ECS после `rebuild()` не меняет уже построенный snapshot.

### 3.1. Shared opportunity shortlist

Полный supplier × consumer перебор больше не выполняется каждым торговым кораблём.

Для каждого supplier и товара `MarketDirectory` сохраняет максимум:

```text
MAX_CONSUMERS_PER_SUPPLIER = 8
```

Кандидаты формируются из двух классов:

1. лучшие buyers по raw buy price;
2. лучшие buyers по optimistic `margin / supplier->consumer distance`.

Оставшиеся slots заполняются ценовыми кандидатами. В shortlist не попадает пара, которая не может стать прибыльной даже при максимально допустимом reputation price bonus.

Таким образом expensive supplier/consumer pairing выполняется **один раз на общий market snapshot**, а работа одного fleet planner при фиксированном cap растёт линейно с числом suppliers, а не квадратично с числом всех станций.

Это не является финальной оптимизацией. Stage 6 обязан измерить фактическую стоимость на 100+ станциях / 500+ агентах и при необходимости заменить heuristic shortlist более специализированным индексом.

## 4. FleetTradeProfile

`FleetTradeProfile` — defensive immutable snapshot данных конкретного корабля, необходимых planner-у:

- position;
- movement speed;
- wallet;
- physical inventory capacity;
- current total cargo;
- AI cargo-space limit;
- item specialization;
- ship cargo policy;
- stock by item;
- reputation by faction.

Planner не читает ECS во время route evaluation.

## 5. Новый груз: TradeRoute

Для нового рейса planner проверяет:

- cargo specialization;
- `ShipType` cargo policy;
- свободный трюм;
- stock supplier;
- свободную capacity consumer;
- consumer demand до target stock;
- доступный капитал fleet;
- отсутствие overflow supplier wallet;
- ликвидность consumer;
- отсутствие overflow fleet wallet;
- effective prices после reputation modifier;
- положительный spread;
- route costs.

### 5.1. Денежные величины

```text
purchaseCost = effectiveSupplierSellPrice * amount
saleRevenue  = effectiveConsumerBuyPrice * amount
grossProfit  = saleRevenue - purchaseCost
netProfit    = grossProfit - estimatedRouteCost
```

Все authoritative monetary totals хранятся в `long` milli-credits через существующий `Money` API.

Маршрут с `netProfit <= 0` не считается допустимым.

### 5.2. Время маршрута

```text
travelDistance = distance(fleet, supplier)
               + distance(supplier, consumer)

travelSeconds = travelDistance / movementSpeed
```

Production scoring mode Stage 5:

```text
score = netProfit / travelSeconds
```

При нулевой дистанции score рассматривается как positive infinity только для сравнения planner-а; это значение не сохраняется в persistent state.

Legacy `GROSS_PROFIT` scoring оставлен как явная regression policy и не является production default.

## 6. Уже купленный cargo: TradeSaleRoute

Если на корабле уже находится товар, его первоначальная закупочная цена считается sunk cost.

Planner выбирает consumer по:

```text
saleRevenue = effectiveConsumerBuyPrice * sellableAmount
netRevenue  = saleRevenue - estimatedRouteCost

score = netRevenue / travelSeconds
```

При этом по-прежнему проверяются:

- фактический cargo;
- consumer free capacity;
- consumer wallet liquidity;
- fleet wallet overflow;
- current effective buy price.

Продажа уже имеющегося груза не ограничивается cargo policy корабля: это позволяет безопасно реализовать несовместимый cargo после изменения конфигурации/правил.

## 7. Route cost extension seam

`TradeRouteCostModel` позволяет позднее учитывать дополнительные экономические расходы без изменения FSM и market search.

`Context` уже предоставляет:

- supplier/consumer IDs;
- supplier/consumer faction IDs;
- item и amount;
- purchase cost;
- sale revenue;
- travel distance;
- travel time.

Планируемые будущие реализации:

- fuel cost по distance/time и типу двигателя;
- faction tariffs/taxes;
- expected risk penalty по опасности маршрута и стоимости груза;
- insurance/escort cost;
- gate/jump fees.

Stage 5 production использует `TradeRouteCostModel.none()`, поэтому новый seam сам по себе не меняет текущий денежный баланс.

Любая cost model обязана возвращать неотрицательное число milli-credits. Маршрут, для которого cost уничтожает всю ожидаемую прибыль/выручку, отбрасывается.

## 8. Stale-route / replan policy

План маршрута является **advisory snapshot**, а не резервированным контрактом.

Мир может измениться после планирования:

- станция уничтожена/удалена;
- товар перестал торговаться;
- supplier stock закончился;
- consumer capacity закончилась;
- кошелёк одной из сторон изменился;
- цена изменилась;
- прибыльный spread закрылся.

Поэтому `TradeAISystem` повторно проверяет authoritative ECS state перед каждой сделкой.

Перед покупкой повторно проверяются:

- обе станции существуют в `EntityRegistry` и остаются active markets;
- item существует и разрешён cargo policy;
- item торгуется на обеих станциях;
- текущие effective purchase/sale prices конечны и spread остаётся положительным;
- текущие capacity, stock и wallets позволяют фактическую партию.

Перед продажей повторно проверяются consumer, item, price, capacity и liquidity.

Если проверка не проходит:

```text
resetRoute()
state = IDLE
routeSearchCooldown = 1 second
```

Никакого частичного transfer не выполняется. Следующий маршрут будет рассчитан на свежем `MarketDirectory` snapshot.

Автоматическая reservation/contract система **не вводится** в Stage 5; если она понадобится, это отдельный экономический механизм будущих stages.

## 9. Determinism

Маршрутный planner не использует случайность.

Для одинаковых:

- market snapshot;
- fleet profile;
- content catalog;
- scoring policy;
- route cost model;

результат детерминирован.

Tie-break для нового груза использует последовательно:

1. primary net score;
2. net absolute profit;
3. gross profit;
4. меньшее travel time;
5. меньший item ID;
6. меньший supplier `EntityId`;
7. меньший consumer `EntityId`.

Sale-route использует аналогичный deterministic порядок без supplier ID.

## 10. Persistence

Stage 5 не меняет persistent route representation `TradeAIComponent`:

- `buyStationId`;
- `sellStationId`;
- `targetStationId`;
- `targetItem`;
- `targetAmount`;
- `expectedProfitMilliCredits`;
- state/cooldown.

Следовательно, Stage-3/4 save/load infrastructure не требует новой save schema только из-за route planner extraction.

Planner-specific transient snapshots (`MarketDirectory`, `FleetTradeProfile`, `TradeOpportunity`, `TradeRoute`) намеренно не сохраняются: после загрузки они строятся заново из authoritative state.

## 11. Инварианты Stage 2

Route planner ничего не переводит между экономическими агентами.

Только `TradeController` выполняет authoritative покупку/продажу. Поэтому существующие инварианты сохраняются:

- trade не создаёт и не уничтожает товар;
- trade не создаёт и не уничтожает деньги;
- transaction атомарна;
- EconomicLedger получает запись только после успешного transfer.

## 12. Граница Stage 5 / Stage 6

Stage 5 отвечает за архитектуру и ограничение очевидно не масштабируемого per-agent route search.

Stage 6 должен количественно проверить:

- стоимость `MarketDirectory.rebuild()`;
- число opportunities;
- planner evaluations per agent;
- CPU time / allocations;
- качество маршрутов при cap = 8;
- trade volume и stockouts;
- изменение общей эффективности экономики при разных shortlist policies.

До benchmark не следует усложнять shortlist дополнительными spatial trees, auctions или глобальным optimiser без измеряемого bottleneck.
