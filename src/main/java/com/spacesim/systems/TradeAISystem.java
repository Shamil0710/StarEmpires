package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.controllers.TradeController;
import com.spacesim.util.SpatialHashGrid;

import java.util.List;

/**
 * Управляет автономными торговыми флотами и выполняет полный цикл покупки и продажи груза.
 *
 * <p>Система обрабатывает сущности с {@link TradeAIComponent}, {@link TransformComponent} и
 * {@link InventoryComponent}. Перед каждым корректным обновлением она заново строит
 * {@link SpatialHashGrid} по всем активным рыночным станциям, а затем обновляет конечный автомат
 * каждого флота:</p>
 * <pre>
 * IDLE -&gt; TRAVEL_TO_BUY -&gt; BUYING -&gt; TRAVEL_TO_SELL -&gt; SELLING -&gt; IDLE
 * </pre>
 * <p>Если флот уже несёт груз, из {@code IDLE} он ищет только станцию продажи и переходит сразу в
 * {@code TRAVEL_TO_SELL}. Любая недействительная ссылка на станцию, некорректная цена, баланс или
 * невозможная операция отменяет маршрут, возвращает флот в {@code IDLE} и включает секундную
 * задержку повторного поиска. Успешное завершение маршрута снимает задержку.</p>
 *
 * <p>При выборе нового полного маршрута обе станции должны находиться в пределах текущего
 * пространственного запроса, быть различными активными рынками и торговать выбранным товаром.
 * Эффективная цена продажи должна превышать цену покупки с учётом репутации. Объём ограничивается
 * запасом станции-источника, средствами флота, его грузовым лимитом и физической вместимостью
 * инвентаря, свободным местом назначения и, если он положителен, спросом назначения. Среди
 * исполнимых вариантов выбирается маршрут с наибольшей конечной ожидаемой прибылью. Поле
 * {@link TradeAIComponent#specializedItem} может сузить новые маршруты до одного товара, а
 * {@link ShipComponent} — до допустимой для корпуса категории груза. Отсутствующий корабельный
 * компонент сохраняет прежнее универсальное поведение. Оба ограничения действуют только на новые
 * покупки и не мешают аварийно продать уже имеющийся груз.</p>
 *
 * <p>Перемещение выполняется по прямой с постоянной скоростью, без поиска пути и учёта препятствий.
 * Цены и доступный объём повторно проверяются непосредственно перед сделкой. Все денежные расчёты
 * отбрасывают {@code NaN}, бесконечность, переполнение {@code float} и операции, которые из-за
 * точности числа не изменили бы баланс.</p>
 */
public class TradeAISystem extends IteratingSystem {
    /** Расстояние, меньше которого флот считается прибывшим без дополнительного шага. */
    private static final float ARRIVAL_DISTANCE = 10f;
    /** Задержка нового поиска после отмены или отсутствия исполнимого маршрута. */
    private static final float ROUTE_SEARCH_RETRY_SECONDS = 1f;
    /**
     * Радиус пространственного запроса, охватывающий расширенный мир по самой длинной оси.
     */
    private static final int ROUTE_SEARCH_RADIUS_CELLS =
            (int) Math.ceil(Constants.WORLD_WIDTH / Constants.CELL_SIZE);

    /** Перестраиваемый индекс рыночных станций. */
    private final SpatialHashGrid grid;
    /** Контроллер, атомарно проверяющий и выполняющий перемещение товаров и кредитов. */
    private final TradeController tradeController = new TradeController();
    /** Живое представление сущностей, способных выступать рыночными станциями. */
    private ImmutableArray<Entity> marketStations;

    /** Быстрый доступ к состоянию конечного автомата флота. */
    private final ComponentMapper<TradeAIComponent> am = ComponentMapper.getFor(TradeAIComponent.class);
    /** Быстрый доступ к позициям флотов и станций. */
    private final ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);
    /** Быстрый доступ к торговым настройкам и ценам станций. */
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    /** Быстрый доступ к запасам флотов и станций. */
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    /** Быстрый доступ к необязательной репутации торгового флота. */
    private final ComponentMapper<ReputationComponent> rm = ComponentMapper.getFor(ReputationComponent.class);
    /** Доступ к необязательному типу корабля и его ограничениям грузового отсека. */
    private final ComponentMapper<ShipComponent> sm = ComponentMapper.getFor(ShipComponent.class);

    /**
     * Создаёт торговую AI-систему.
     *
     * @param grid пространственный индекс, предназначенный для перестроения этой системой на каждом
     *             обновлении; во время работы не должен быть {@code null}
     */
    public TradeAISystem(SpatialHashGrid grid) {
        super(Family.all(TradeAIComponent.class, TransformComponent.class, InventoryComponent.class).get());
        this.grid = grid;
    }

    /**
     * Сохраняет живое представление всех сущностей, пригодных для роли рыночной станции.
     *
     * <p>Станции должны одновременно иметь положение, рынок и инвентарь. Ashley поддерживает
     * представление актуальным при изменении состава движка.</p>
     *
     * @param engine движок, к которому добавлена система
     * @throws NullPointerException если {@code engine} равен {@code null}
     */
    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        marketStations = engine.getEntitiesFor(Family.all(
                TransformComponent.class,
                MarketComponent.class,
                InventoryComponent.class
        ).get());
    }

    /**
     * Перестраивает индекс станций и обновляет конечные автоматы всех флотов.
     *
     * <p>Отрицательное, бесконечное время и {@code NaN} полностью игнорируются. Нулевой интервал
     * допустим: движение не продвигается, но поиск маршрутов и сделки в дискретных состояниях могут
     * быть выполнены.</p>
     *
     * @param deltaTime прошедшее с предыдущего обновления время в секундах
     */
    @Override
    public void update(float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime < 0f) {
            return;
        }

        rebuildSpatialIndex();
        super.update(deltaTime);
    }

    /**
     * Выполняет действие, соответствующее текущему состоянию одного флота.
     *
     * <p>Отсутствующее состояние, неконечный или отрицательный баланс считаются повреждённым
     * маршрутом и переводят флот в ожидание с задержкой повторного поиска.</p>
     *
     * @param entity сущность флота с обязательными компонентами семейства системы
     * @param deltaTime прошедшее время в секундах, уже проверенное методом {@link #update(float)}
     */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TradeAIComponent ai = am.get(entity);
        TransformComponent transform = tm.get(entity);

        if (ai.state == null || !Float.isFinite(ai.credits) || ai.credits < 0f) {
            abandonRoute(ai);
            return;
        }

        switch (ai.state) {
            case IDLE:
                processIdle(entity, ai, transform, deltaTime);
                break;
            case TRAVEL_TO_BUY:
                move(transform, ai.buyStation, deltaTime, ai, TradeAIComponent.State.BUYING);
                break;
            case BUYING:
                buyCargo(entity, ai);
                break;
            case TRAVEL_TO_SELL:
                move(transform, ai.sellStation, deltaTime, ai, TradeAIComponent.State.SELLING);
                break;
            case SELLING:
                sellCargo(entity, ai);
                break;
        }
    }

    /**
     * Обрабатывает ожидание и, когда задержка истекла, запускает поиск маршрута.
     *
     * <p>Флот с любым положительным суммарным запасом ищет только вариант продажи имеющегося груза;
     * пустой флот ищет полную пару покупки и продажи. Неудачный поиск откладывается на одну секунду,
     * чтобы не выполнять дорогой перебор на каждом кадре.</p>
     *
     * @param fleet сущность флота
     * @param ai состояние торгового автомата
     * @param position положение флота
     * @param deltaTime прошедшее время в секундах
     */
    private void processIdle(Entity fleet, TradeAIComponent ai, TransformComponent position, float deltaTime) {
        if (ai.routeSearchCooldown > 0f) {
            ai.routeSearchCooldown = Math.max(0f, ai.routeSearchCooldown - Math.max(0f, deltaTime));
            if (ai.routeSearchCooldown > 0f) {
                return;
            }
        }

        boolean routeFound;
        if (im.get(fleet).getTotalStock() > 0) {
            routeFound = findSellRoute(fleet, ai, position);
        } else {
            routeFound = findTradeRoute(fleet, ai, position);
        }

        if (!routeFound) {
            ai.routeSearchCooldown = ROUTE_SEARCH_RETRY_SECONDS;
        }
    }

    /**
     * Полностью перестраивает пространственный индекс по текущему набору рыночных станций.
     *
     * <p>Очистка перед вставкой исключает устаревшие позиции станций, перемещённых или удалённых с
     * предыдущего кадра.</p>
     */
    private void rebuildSpatialIndex() {
        grid.clear();
        for (Entity station : marketStations) {
            grid.insert(station, tm.get(station).position);
        }
    }

    /**
     * Ищет наиболее прибыльный полный маршрут для пустого флота.
     *
     * <p>Перед поиском прежние данные маршрута очищаются. Перебираются все упорядоченные пары
     * различных активных станций из окрестности флота и все товары. В компонент AI записывается
     * только вариант с максимальной строго положительной исполнимой прибылью. Успех переводит автомат
     * в {@code TRAVEL_TO_BUY}; при неудаче компонент остаётся без маршрута.</p>
     *
     * @param fleet сущность ищущего маршрут флота
     * @param ai изменяемое состояние AI
     * @param position текущее положение флота, являющееся центром пространственного запроса
     * @return {@code true}, если прибыльный исполнимый маршрут найден и записан
     */
    private boolean findTradeRoute(Entity fleet, TradeAIComponent ai, TransformComponent position) {
        ai.resetRoute();
        List<Entity> nearby = grid.getNearby(position.position, ROUTE_SEARCH_RADIUS_CELLS);
        InventoryComponent fleetInventory = im.get(fleet);
        ReputationComponent reputation = rm.get(fleet);
        float bestProfit = 0f;

        for (Entity buyStation : nearby) {
            if (!isActiveMarketStation(fleet, buyStation)) {
                continue;
            }

            InventoryComponent buyInventory = im.get(buyStation);
            MarketComponent buyMarket = mm.get(buyStation);

            for (Entity sellStation : nearby) {
                if (sellStation == buyStation || !isActiveMarketStation(fleet, sellStation)) {
                    continue;
                }

                InventoryComponent sellInventory = im.get(sellStation);
                MarketComponent sellMarket = mm.get(sellStation);

                for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                    if (!acceptsNewRouteItem(fleet, ai, itemId)
                            || !buyMarket.isTradable(itemId)
                            || !sellMarket.isTradable(itemId)) {
                        continue;
                    }

                    float purchasePrice = tradeController.getEffectiveSellPrice(buyStation, itemId, reputation);
                    float salePrice = tradeController.getEffectiveBuyPrice(sellStation, itemId, reputation);
                    if (!isPositiveFinitePrice(purchasePrice)
                            || !isPositiveFinitePrice(salePrice)
                            || salePrice <= purchasePrice) {
                        continue;
                    }

                    int amount = calculateTradeAmount(
                            ai,
                            fleetInventory,
                            buyInventory,
                            sellInventory,
                            sellMarket,
                            itemId,
                            purchasePrice
                    );
                    if (amount <= 0) {
                        continue;
                    }

                    float purchaseCost = getExecutableCost(ai.credits, purchasePrice, amount);
                    if (!isPositiveFinitePrice(purchaseCost)) {
                        continue;
                    }
                    float creditsAfterPurchase = ai.credits - purchaseCost;
                    float saleRevenue = getExecutableRevenue(creditsAfterPurchase, salePrice, amount);
                    if (!isPositiveFinitePrice(saleRevenue)) {
                        continue;
                    }

                    float routeProfit = saleRevenue - purchaseCost;
                    if (routeProfit > bestProfit) {
                        bestProfit = routeProfit;
                        ai.buyStation = buyStation;
                        ai.sellStation = sellStation;
                        ai.targetStation = buyStation;
                        ai.targetItem = itemId;
                        ai.targetAmount = amount;
                        ai.expectedProfit = routeProfit;
                    }
                }
            }
        }

        if (bestProfit <= 0f) {
            return false;
        }

        ai.routeSearchCooldown = 0f;
        ai.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        return true;
    }

    /**
     * Ищет лучший вариант продажи уже имеющегося груза.
     *
     * <p>Для каждого товара в инвентаре перебираются ближайшие активные рынки. Объём ограничивается
     * наличием груза и свободной вместимостью станции; выбирается вариант с наибольшей конечной
     * выручкой. Успех переводит автомат непосредственно в {@code TRAVEL_TO_SELL}.</p>
     *
     * @param fleet сущность флота с грузом
     * @param ai изменяемое состояние AI
     * @param position положение флота, являющееся центром пространственного запроса
     * @return {@code true}, если найдена станция, способная принять часть груза по корректной цене
     */
    private boolean findSellRoute(Entity fleet, TradeAIComponent ai, TransformComponent position) {
        ai.resetRoute();
        List<Entity> nearby = grid.getNearby(position.position, ROUTE_SEARCH_RADIUS_CELLS);
        InventoryComponent fleetInventory = im.get(fleet);
        ReputationComponent reputation = rm.get(fleet);
        float bestRevenue = 0f;

        for (Entity sellStation : nearby) {
            if (!isActiveMarketStation(fleet, sellStation)) {
                continue;
            }

            InventoryComponent stationInventory = im.get(sellStation);
            MarketComponent stationMarket = mm.get(sellStation);
            int freeCapacity = stationInventory.getFreeCapacity();
            if (freeCapacity <= 0) {
                continue;
            }

            for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                int cargoAmount = fleetInventory.stock[itemId];
                if (cargoAmount <= 0 || !stationMarket.isTradable(itemId)) {
                    continue;
                }

                float salePrice = tradeController.getEffectiveBuyPrice(sellStation, itemId, reputation);
                if (!isPositiveFinitePrice(salePrice)) {
                    continue;
                }

                int amount = Math.min(cargoAmount, freeCapacity);
                float revenue = getExecutableRevenue(ai.credits, salePrice, amount);
                if (revenue > bestRevenue) {
                    bestRevenue = revenue;
                    ai.buyStation = null;
                    ai.sellStation = sellStation;
                    ai.targetStation = sellStation;
                    ai.targetItem = itemId;
                    ai.targetAmount = amount;
                    ai.expectedProfit = revenue;
                }
            }
        }

        if (bestRevenue <= 0f) {
            return false;
        }

        ai.routeSearchCooldown = 0f;
        ai.state = TradeAIComponent.State.TRAVEL_TO_SELL;
        return true;
    }

    /**
     * Рассчитывает максимальный исполнимый объём покупки для выбранного товара.
     *
     * <p>Результат одновременно ограничивается грузовым лимитом AI, общей вместимостью инвентаря
     * флота, вместимостью станции назначения, запасом станции покупки и доступными кредитами. Если
     * целевой рынок испытывает дефицит относительно {@link MarketComponent#targetStock}, объём также
     * ограничивается этим спросом; иначе разрешено заполнить весь переносимый объём.</p>
     *
     * @param ai состояние флота с балансом и грузовым лимитом
     * @param fleetInventory инвентарь флота
     * @param buyInventory инвентарь станции покупки
     * @param sellInventory инвентарь станции назначения
     * @param sellMarket рынок станции назначения
     * @param itemId идентификатор товара
     * @param purchasePrice эффективная цена одной единицы при покупке
     * @return неотрицательное число единиц, которое можно приобрести и затем разместить
     */
    private int calculateTradeAmount(TradeAIComponent ai, InventoryComponent fleetInventory,
                                     InventoryComponent buyInventory, InventoryComponent sellInventory,
                                     MarketComponent sellMarket, int itemId, float purchasePrice) {
        int fleetFreeCapacity = Math.max(0,
                Math.min(ai.cargoSpace - fleetInventory.getTotalStock(), fleetInventory.getFreeCapacity()));
        int destinationFreeCapacity = sellInventory.getFreeCapacity();
        int transferableAmount = Math.min(fleetFreeCapacity, destinationFreeCapacity);
        int stationDemand = Math.max(0, sellMarket.targetStock[itemId] - sellInventory.stock[itemId]);
        int affordableAmount = Math.max(0, (int)Math.floor(ai.credits / purchasePrice));
        int desiredAmount = stationDemand > 0 ? stationDemand : transferableAmount;

        return Math.min(
                Math.min(transferableAmount, buyInventory.stock[itemId]),
                Math.min(affordableAmount, desiredAmount)
        );
    }

    /**
     * Перемещает флот по прямой к целевой станции.
     *
     * <p>Если цель больше не является активной рыночной станцией, маршрут отменяется. Когда
     * оставшееся расстояние не превышает шаг текущего кадра либо порог прибытия, позиция точно
     * совмещается со станцией и автомат переводится в заданное состояние. Иначе выполняется шаг со
     * скоростью из {@link TradeAIComponent#movementSpeed}. Некорректная скорость приостанавливает
     * движение, не сбрасывая маршрут.</p>
     *
     * @param fleetPosition изменяемое положение флота
     * @param target целевая рыночная станция
     * @param deltaTime прошедшее время в секундах
     * @param ai изменяемое состояние AI
     * @param arrivalState состояние, устанавливаемое после прибытия
     */
    private void move(TransformComponent fleetPosition, Entity target, float deltaTime, TradeAIComponent ai,
                      TradeAIComponent.State arrivalState) {
        if (!isActiveMarketStation(null, target)) {
            abandonRoute(ai);
            return;
        }

        if (!Float.isFinite(ai.movementSpeed) || ai.movementSpeed < 0f) {
            return;
        }

        Vector2 targetPosition = tm.get(target).position;
        Vector2 toTarget = targetPosition.cpy().sub(fleetPosition.position);
        float distance = toTarget.len();
        float step = ai.movementSpeed * Math.max(0f, deltaTime);

        if (distance <= step || distance < ARRIVAL_DISTANCE) {
            fleetPosition.position.set(targetPosition);
            ai.targetStation = target;
            ai.state = arrivalState;
            return;
        }

        fleetPosition.position.mulAdd(toTarget.nor(), step);
    }

    /**
     * Повторно проверяет маршрут покупки и выполняет фактическую передачу груза.
     *
     * <p>Перед сделкой заново вычисляются эффективные цены и доступный объём. Это защищает маршрут
     * от изменений запасов, спроса, репутации и цен, произошедших во время перелёта. После успешной
     * покупки обновлённый баланс возвращается в компонент AI, а целью становится станция продажи.
     * Любая неисполняемая операция отменяет маршрут.</p>
     *
     * @param fleet сущность покупающего флота
     * @param ai изменяемое состояние и параметры выбранного маршрута
     */
    private void buyCargo(Entity fleet, TradeAIComponent ai) {
        if (!isBuyRouteValid(fleet, ai)) {
            abandonRoute(ai);
            return;
        }

        InventoryComponent fleetInventory = im.get(fleet);
        InventoryComponent buyInventory = im.get(ai.buyStation);
        InventoryComponent sellInventory = im.get(ai.sellStation);
        MarketComponent sellMarket = mm.get(ai.sellStation);
        ReputationComponent reputation = rm.get(fleet);
        int itemId = ai.targetItem;

        float purchasePrice = tradeController.getEffectiveSellPrice(ai.buyStation, itemId, reputation);
        float salePrice = tradeController.getEffectiveBuyPrice(ai.sellStation, itemId, reputation);
        if (!isPositiveFinitePrice(purchasePrice)
                || !isPositiveFinitePrice(salePrice)
                || salePrice <= purchasePrice) {
            abandonRoute(ai);
            return;
        }

        int amount = Math.min(ai.targetAmount, calculateTradeAmount(
                ai,
                fleetInventory,
                buyInventory,
                sellInventory,
                sellMarket,
                itemId,
                purchasePrice
        ));
        TradeController.CreditAccount credits = new TradeController.CreditAccount(ai.credits);

        if (amount <= 0 || !tradeController.buyFromStation(
                ai.buyStation,
                fleetInventory,
                itemId,
                amount,
                credits,
                reputation
        )) {
            abandonRoute(ai);
            return;
        }

        ai.credits = credits.credits;
        ai.targetAmount = amount;
        ai.targetStation = ai.sellStation;
        ai.state = TradeAIComponent.State.TRAVEL_TO_SELL;
    }

    /**
     * Продаёт доступную часть целевого груза станции назначения.
     *
     * <p>Количество повторно ограничивается фактическим грузом и текущей свободной вместимостью
     * станции. После успешной сделки баланс переносится обратно в компонент AI, маршрут очищается и
     * флот немедленно возвращается в {@code IDLE}. Ошибка сделки отменяет маршрут с задержкой
     * повторного поиска.</p>
     *
     * @param fleet сущность продающего флота
     * @param ai изменяемое состояние и параметры выбранного маршрута
     */
    private void sellCargo(Entity fleet, TradeAIComponent ai) {
        if (!isSellRouteValid(ai)) {
            abandonRoute(ai);
            return;
        }

        InventoryComponent fleetInventory = im.get(fleet);
        InventoryComponent stationInventory = im.get(ai.sellStation);
        ReputationComponent reputation = rm.get(fleet);
        int itemId = ai.targetItem;
        float salePrice = tradeController.getEffectiveBuyPrice(ai.sellStation, itemId, reputation);
        int amount = Math.min(
                ai.targetAmount,
                Math.min(fleetInventory.stock[itemId], stationInventory.getFreeCapacity())
        );

        if (amount <= 0 || !isPositiveFinitePrice(salePrice)) {
            abandonRoute(ai);
            return;
        }

        TradeController.CreditAccount credits = new TradeController.CreditAccount(ai.credits);
        if (!tradeController.sellToStation(
                ai.sellStation,
                fleetInventory,
                itemId,
                amount,
                credits,
                reputation
        )) {
            abandonRoute(ai);
            return;
        }

        ai.credits = credits.credits;
        finishRoute(ai);
    }

    /**
     * Проверяет структурную пригодность маршрута перед покупкой.
     *
     * @param fleet покупающий флот с необязательным типом корпуса
     * @param ai состояние с выбранными станциями и товаром
     * @return {@code true}, если корпус принимает товар, станции различны, всё ещё активны и обе
     *         торгуют им
     */
    private boolean isBuyRouteValid(Entity fleet, TradeAIComponent ai) {
        return isValidItem(ai.targetItem)
                && canShipPurchaseItem(fleet, ai.targetItem)
                && ai.buyStation != ai.sellStation
                && isActiveMarketStation(null, ai.buyStation)
                && isActiveMarketStation(null, ai.sellStation)
                && mm.get(ai.buyStation).isTradable(ai.targetItem)
                && mm.get(ai.sellStation).isTradable(ai.targetItem);
    }

    /**
     * Проверяет структурную пригодность маршрута перед продажей.
     *
     * @param ai состояние с выбранной станцией и товаром
     * @return {@code true}, если товар допустим, а станция активна и продолжает им торговать
     */
    private boolean isSellRouteValid(TradeAIComponent ai) {
        return isValidItem(ai.targetItem)
                && isActiveMarketStation(null, ai.sellStation)
                && mm.get(ai.sellStation).isTradable(ai.targetItem);
    }

    /**
     * Проверяет принадлежность сущности текущему живому набору рыночных станций.
     *
     * <p>Проверка выполняется по идентичности объектов. При переданном флоте он дополнительно не
     * может выступать собственной станцией; значение {@code null} отключает только это исключение.</p>
     *
     * @param fleet флот, который следует исключить, либо {@code null}
     * @param entity проверяемая сущность
     * @return {@code true}, если сущность присутствует в наборе активных рыночных станций
     */
    private boolean isActiveMarketStation(Entity fleet, Entity entity) {
        if (entity == null || entity == fleet || marketStations == null) {
            return false;
        }

        for (Entity station : marketStations) {
            if (station == entity) {
                return true;
            }
        }
        return false;
    }

    /**
     * Проверяет границы идентификатора товара.
     *
     * @param itemId идентификатор товара
     * @return {@code true}, если идентификатор адресует массивы товарных компонентов
     */
    private boolean isValidItem(int itemId) {
        return itemId >= 0 && itemId < Constants.MAX_ITEMS;
    }

    /**
     * Проверяет товарную специализацию перед планированием новой покупки.
     *
     * <p>Универсальное значение {@code -1} разрешает любой товар. Уже имеющийся груз этим
     * фильтром не ограничивается и может быть продан системой восстановления маршрута.</p>
     *
     * @param fleet сущность флота с необязательным ограничением типа корабля
     * @param ai состояние флота с конфигурацией специализации
     * @param itemId проверяемый допустимый идентификатор товара
     * @return {@code true}, если товар разрешён для нового полного маршрута
     */
    private boolean acceptsNewRouteItem(Entity fleet, TradeAIComponent ai, int itemId) {
        return (ai.specializedItem == -1 || ai.specializedItem == itemId)
                && canShipPurchaseItem(fleet, itemId);
    }

    /**
     * Проверяет физическую пригодность корабля для новой покупки.
     *
     * <p>Сущности старого формата без {@link ShipComponent} считаются универсальными. Наличие
     * компонента включает строгую политику его типа: добывающие и боевые корпуса не закупают товар,
     * а транспорт принимает только свою категорию. Проверка повторяется перед самой покупкой, чтобы
     * смена типа во время полёта не позволила загрузить несовместимый груз.</p>
     *
     * @param fleet проверяемая сущность флота
     * @param itemId допустимый идентификатор планируемого товара
     * @return {@code true}, если покупка разрешена либо тип корабля не задан
     */
    private boolean canShipPurchaseItem(Entity fleet, int itemId) {
        ShipComponent ship = sm.get(fleet);
        return ship == null || ship.canPurchaseItem(itemId);
    }

    /**
     * Проверяет пригодность цены или денежного результата для сделки.
     *
     * @param price проверяемое значение
     * @return {@code true} только для конечного строго положительного числа
     */
    private boolean isPositiveFinitePrice(float price) {
        return Float.isFinite(price) && price > 0f;
    }

    /**
     * Вычисляет стоимость покупки и проверяет представимость уменьшенного баланса.
     *
     * @param balance исходный баланс флота
     * @param unitPrice цена одной единицы
     * @param amount число единиц
     * @return конечная положительная стоимость либо {@link Float#NaN}, если параметры некорректны,
     *         средств недостаточно, возникает переполнение или точности {@code float} недостаточно
     *         для фактического уменьшения баланса
     */
    private float getExecutableCost(float balance, float unitPrice, int amount) {
        float cost = getFiniteTotal(unitPrice, amount);
        float resultingBalance = balance - cost;
        if (!Float.isFinite(balance)
                || balance < 0f
                || !isPositiveFinitePrice(cost)
                || !Float.isFinite(resultingBalance)
                || resultingBalance < 0f
                || resultingBalance >= balance) {
            return Float.NaN;
        }
        return cost;
    }

    /**
     * Вычисляет выручку и проверяет представимость увеличенного баланса.
     *
     * @param balance исходный баланс флота
     * @param unitPrice цена одной единицы
     * @param amount число единиц
     * @return конечная положительная выручка либо {@link Float#NaN}, если параметры некорректны,
     *         возникает переполнение или прибавление не изменяет баланс в точности {@code float}
     */
    private float getExecutableRevenue(float balance, float unitPrice, int amount) {
        float revenue = getFiniteTotal(unitPrice, amount);
        float resultingBalance = balance + revenue;
        if (!Float.isFinite(balance)
                || balance < 0f
                || !isPositiveFinitePrice(revenue)
                || !Float.isFinite(resultingBalance)
                || resultingBalance <= balance) {
            return Float.NaN;
        }
        return revenue;
    }

    /**
     * Безопасно умножает цену единицы на целое количество через промежуточный {@code double}.
     *
     * @param unitPrice цена одной единицы
     * @param amount число единиц
     * @return представимое конечное положительное значение {@code float} либо {@link Float#NaN}
     */
    private float getFiniteTotal(float unitPrice, int amount) {
        double total = (double) unitPrice * amount;
        if (!isPositiveFinitePrice(unitPrice)
                || amount <= 0
                || !Double.isFinite(total)
                || total <= 0d
                || total > Float.MAX_VALUE) {
            return Float.NaN;
        }
        return (float) total;
    }

    /**
     * Отменяет текущий маршрут и откладывает следующую попытку поиска.
     *
     * @param ai очищаемое состояние AI
     */
    private void abandonRoute(TradeAIComponent ai) {
        ai.resetRoute();
        ai.state = TradeAIComponent.State.IDLE;
        ai.routeSearchCooldown = ROUTE_SEARCH_RETRY_SECONDS;
    }

    /**
     * Завершает успешный маршрут и разрешает новый поиск без задержки.
     *
     * @param ai очищаемое состояние AI
     */
    private void finishRoute(TradeAIComponent ai) {
        ai.resetRoute();
        ai.state = TradeAIComponent.State.IDLE;
        ai.routeSearchCooldown = 0f;
    }
}
