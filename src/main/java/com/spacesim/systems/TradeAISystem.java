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
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.controllers.TradeController;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;
import com.spacesim.util.SpatialHashGrid;

import java.util.List;
import java.util.Objects;

/**
 * Управляет автономными торговыми флотами и исполняет маршруты через физические склады и кошельки.
 *
 * <p>Authoritative деньги хранятся только в {@link WalletComponent}. При выборе маршрута система
 * проверяет ликвидность обеих сторон: флот должен оплатить покупку, станция назначения — иметь
 * средства выкупить груз, а кошельки-получатели — свободный диапазон {@code long}. Фактические
 * сделки выполняет {@link TradeController}, поэтому товар и деньги переходят атомарно и записываются
 * в общий {@link EconomicLedger}.</p>
 *
 * <p>Критерий маршрута в этом этапе остаётся прежним — максимальная валовая прибыль. Учёт времени,
 * топлива и риска относится к Stage 5.</p>
 */
public class TradeAISystem extends IteratingSystem {
    private static final float ARRIVAL_DISTANCE = 10f;
    private static final float ROUTE_SEARCH_RETRY_SECONDS = 1f;
    private static final int ROUTE_SEARCH_RADIUS_CELLS =
            (int) Math.ceil(Constants.WORLD_WIDTH / Constants.CELL_SIZE);

    private final SpatialHashGrid grid;
    private final TradeController tradeController;
    private ImmutableArray<Entity> marketStations;

    private final ComponentMapper<TradeAIComponent> am = ComponentMapper.getFor(TradeAIComponent.class);
    private final ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<ReputationComponent> rm = ComponentMapper.getFor(ReputationComponent.class);
    private final ComponentMapper<ShipComponent> sm = ComponentMapper.getFor(ShipComponent.class);
    private final ComponentMapper<WalletComponent> wm = ComponentMapper.getFor(WalletComponent.class);

    /**
     * Создаёт торговую AI-систему с собственным ledger.
     *
     * @param grid пространственный индекс рыночных станций
     * @throws NullPointerException если индекс не задан
     */
    public TradeAISystem(SpatialHashGrid grid) {
        this(grid, new EconomicLedger());
    }

    /**
     * Создаёт торговую AI-систему, использующую общий журнал игровой сессии.
     *
     * @param grid пространственный индекс рыночных станций
     * @param ledger общий экономический журнал
     * @throws NullPointerException если зависимость не задана
     */
    public TradeAISystem(SpatialHashGrid grid, EconomicLedger ledger) {
        super(Family.all(
                TradeAIComponent.class,
                TransformComponent.class,
                InventoryComponent.class,
                WalletComponent.class).get());
        this.grid = Objects.requireNonNull(grid, "SpatialHashGrid не задан");
        this.tradeController = new TradeController(Objects.requireNonNull(ledger, "EconomicLedger не задан"));
    }

    /** @return ledger, в который система записывает успешные сделки */
    public EconomicLedger getLedger() {
        return tradeController.getLedger();
    }

    /**
     * Получает живое представление станций, способных физически торговать товаром и деньгами.
     *
     * @param engine Ashley-движок
     */
    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        marketStations = engine.getEntitiesFor(Family.all(
                TransformComponent.class,
                MarketComponent.class,
                InventoryComponent.class,
                WalletComponent.class).get());
    }

    /**
     * Перестраивает пространственный индекс и обновляет торговые автоматы.
     *
     * @param deltaTime прошедшее игровое время в секундах
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
     * Исполняет текущее состояние одного торгового флота.
     *
     * @param fleet флот с обязательными компонентами системы
     * @param deltaTime прошедшее игровое время в секундах
     */
    @Override
    protected void processEntity(Entity fleet, float deltaTime) {
        TradeAIComponent ai = am.get(fleet);
        TransformComponent transform = tm.get(fleet);
        if (ai.state == null) {
            abandonRoute(ai);
            return;
        }

        switch (ai.state) {
            case IDLE -> processIdle(fleet, ai, transform, deltaTime);
            case TRAVEL_TO_BUY -> move(transform, ai.buyStation, deltaTime, ai, TradeAIComponent.State.BUYING);
            case BUYING -> buyCargo(fleet, ai);
            case TRAVEL_TO_SELL -> move(transform, ai.sellStation, deltaTime, ai, TradeAIComponent.State.SELLING);
            case SELLING -> sellCargo(fleet, ai);
        }
    }

    private void processIdle(Entity fleet, TradeAIComponent ai, TransformComponent position, float deltaTime) {
        if (ai.routeSearchCooldown > 0f) {
            ai.routeSearchCooldown = Math.max(0f, ai.routeSearchCooldown - Math.max(0f, deltaTime));
            if (ai.routeSearchCooldown > 0f) {
                return;
            }
        }

        boolean routeFound = im.get(fleet).getTotalStock() > 0
                ? findSellRoute(fleet, ai, position)
                : findTradeRoute(fleet, ai, position);
        if (!routeFound) {
            ai.routeSearchCooldown = ROUTE_SEARCH_RETRY_SECONDS;
        }
    }

    private void rebuildSpatialIndex() {
        grid.clear();
        for (Entity station : marketStations) {
            grid.insert(station, tm.get(station).position);
        }
    }

    private boolean findTradeRoute(Entity fleet, TradeAIComponent ai, TransformComponent position) {
        ai.resetRoute();
        List<Entity> nearby = grid.getNearby(position.position, ROUTE_SEARCH_RADIUS_CELLS);
        ReputationComponent reputation = rm.get(fleet);
        long bestProfit = 0L;

        for (Entity buyStation : nearby) {
            if (!isActiveMarketStation(fleet, buyStation)) {
                continue;
            }
            MarketComponent buyMarket = mm.get(buyStation);

            for (Entity sellStation : nearby) {
                if (sellStation == buyStation || !isActiveMarketStation(fleet, sellStation)) {
                    continue;
                }
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
                            fleet, ai, buyStation, sellStation, itemId, purchasePrice, salePrice);
                    if (amount <= 0) {
                        continue;
                    }
                    long purchaseCost = safeTradeValue(purchasePrice, amount);
                    long saleRevenue = safeTradeValue(salePrice, amount);
                    if (purchaseCost <= 0L || saleRevenue <= purchaseCost) {
                        continue;
                    }
                    long profit = saleRevenue - purchaseCost;
                    if (profit > bestProfit) {
                        bestProfit = profit;
                        ai.buyStation = buyStation;
                        ai.sellStation = sellStation;
                        ai.targetStation = buyStation;
                        ai.targetItem = itemId;
                        ai.targetAmount = amount;
                        ai.expectedProfitMilliCredits = profit;
                    }
                }
            }
        }

        if (bestProfit <= 0L) {
            return false;
        }
        ai.routeSearchCooldown = 0f;
        ai.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        return true;
    }

    private boolean findSellRoute(Entity fleet, TradeAIComponent ai, TransformComponent position) {
        ai.resetRoute();
        List<Entity> nearby = grid.getNearby(position.position, ROUTE_SEARCH_RADIUS_CELLS);
        InventoryComponent fleetInventory = im.get(fleet);
        WalletComponent fleetWallet = wm.get(fleet);
        ReputationComponent reputation = rm.get(fleet);
        long bestRevenue = 0L;

        for (Entity station : nearby) {
            if (!isActiveMarketStation(fleet, station)) {
                continue;
            }
            InventoryComponent stationInventory = im.get(station);
            MarketComponent market = mm.get(station);
            WalletComponent stationWallet = wm.get(station);
            int freeCapacity = stationInventory.getFreeCapacity();
            if (freeCapacity <= 0 || stationWallet.getBalanceMilliCredits() <= 0L) {
                continue;
            }

            for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                int cargo = fleetInventory.stock[itemId];
                if (cargo <= 0 || !market.isTradable(itemId)) {
                    continue;
                }
                float salePrice = tradeController.getEffectiveBuyPrice(station, itemId, reputation);
                if (!isPositiveFinitePrice(salePrice)) {
                    continue;
                }
                int maxAmount = Math.min(cargo, freeCapacity);
                maxAmount = Math.min(maxAmount,
                        safeMaximumAffordable(stationWallet.getBalanceMilliCredits(), salePrice, maxAmount));
                maxAmount = Math.min(maxAmount,
                        safeMaximumAffordable(Long.MAX_VALUE - fleetWallet.getBalanceMilliCredits(), salePrice, maxAmount));
                if (maxAmount <= 0) {
                    continue;
                }
                long revenue = safeTradeValue(salePrice, maxAmount);
                if (revenue > bestRevenue) {
                    bestRevenue = revenue;
                    ai.buyStation = null;
                    ai.sellStation = station;
                    ai.targetStation = station;
                    ai.targetItem = itemId;
                    ai.targetAmount = maxAmount;
                    ai.expectedProfitMilliCredits = revenue;
                }
            }
        }

        if (bestRevenue <= 0L) {
            return false;
        }
        ai.routeSearchCooldown = 0f;
        ai.state = TradeAIComponent.State.TRAVEL_TO_SELL;
        return true;
    }

    private int calculateTradeAmount(
            Entity fleet,
            TradeAIComponent ai,
            Entity buyStation,
            Entity sellStation,
            int itemId,
            float purchasePrice,
            float salePrice) {
        InventoryComponent fleetInventory = im.get(fleet);
        InventoryComponent buyInventory = im.get(buyStation);
        InventoryComponent sellInventory = im.get(sellStation);
        MarketComponent sellMarket = mm.get(sellStation);
        WalletComponent fleetWallet = wm.get(fleet);
        WalletComponent buyWallet = wm.get(buyStation);
        WalletComponent sellWallet = wm.get(sellStation);

        int fleetFree = Math.max(0,
                Math.min(ai.cargoSpace - fleetInventory.getTotalStock(), fleetInventory.getFreeCapacity()));
        int transferable = Math.min(fleetFree, sellInventory.getFreeCapacity());
        transferable = Math.min(transferable, buyInventory.stock[itemId]);
        int demand = Math.max(0, sellMarket.targetStock[itemId] - sellInventory.stock[itemId]);
        if (demand > 0) {
            transferable = Math.min(transferable, demand);
        }
        if (transferable <= 0) {
            return 0;
        }

        transferable = Math.min(transferable,
                safeMaximumAffordable(fleetWallet.getBalanceMilliCredits(), purchasePrice, transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(Long.MAX_VALUE - buyWallet.getBalanceMilliCredits(), purchasePrice, transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(sellWallet.getBalanceMilliCredits(), salePrice, transferable));
        transferable = Math.min(transferable,
                safeMaximumAffordable(Long.MAX_VALUE - fleetWallet.getBalanceMilliCredits(), salePrice, transferable));
        return Math.max(0, transferable);
    }

    private void move(
            TransformComponent fleetPosition,
            Entity target,
            float deltaTime,
            TradeAIComponent ai,
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

    private void buyCargo(Entity fleet, TradeAIComponent ai) {
        if (!isBuyRouteValid(fleet, ai)) {
            abandonRoute(ai);
            return;
        }
        ReputationComponent reputation = rm.get(fleet);
        float purchasePrice = tradeController.getEffectiveSellPrice(ai.buyStation, ai.targetItem, reputation);
        float salePrice = tradeController.getEffectiveBuyPrice(ai.sellStation, ai.targetItem, reputation);
        if (!isPositiveFinitePrice(purchasePrice)
                || !isPositiveFinitePrice(salePrice)
                || salePrice <= purchasePrice) {
            abandonRoute(ai);
            return;
        }

        int amount = Math.min(ai.targetAmount, calculateTradeAmount(
                fleet,
                ai,
                ai.buyStation,
                ai.sellStation,
                ai.targetItem,
                purchasePrice,
                salePrice));
        if (amount <= 0 || !tradeController.buyFromStation(
                ai.buyStation, fleet, ai.targetItem, amount, reputation)) {
            abandonRoute(ai);
            return;
        }

        ai.targetAmount = amount;
        ai.targetStation = ai.sellStation;
        ai.state = TradeAIComponent.State.TRAVEL_TO_SELL;
    }

    private void sellCargo(Entity fleet, TradeAIComponent ai) {
        if (!isSellRouteValid(ai)) {
            abandonRoute(ai);
            return;
        }

        InventoryComponent fleetInventory = im.get(fleet);
        InventoryComponent stationInventory = im.get(ai.sellStation);
        WalletComponent stationWallet = wm.get(ai.sellStation);
        WalletComponent fleetWallet = wm.get(fleet);
        ReputationComponent reputation = rm.get(fleet);
        float salePrice = tradeController.getEffectiveBuyPrice(ai.sellStation, ai.targetItem, reputation);
        if (!isPositiveFinitePrice(salePrice)) {
            abandonRoute(ai);
            return;
        }

        int amount = Math.min(ai.targetAmount,
                Math.min(fleetInventory.stock[ai.targetItem], stationInventory.getFreeCapacity()));
        amount = Math.min(amount,
                safeMaximumAffordable(stationWallet.getBalanceMilliCredits(), salePrice, amount));
        amount = Math.min(amount,
                safeMaximumAffordable(Long.MAX_VALUE - fleetWallet.getBalanceMilliCredits(), salePrice, amount));
        if (amount <= 0 || !tradeController.sellToStation(
                ai.sellStation, fleet, ai.targetItem, amount, reputation)) {
            abandonRoute(ai);
            return;
        }
        finishRoute(ai);
    }

    private boolean isBuyRouteValid(Entity fleet, TradeAIComponent ai) {
        return isValidItem(ai.targetItem)
                && canShipPurchaseItem(fleet, ai.targetItem)
                && ai.buyStation != ai.sellStation
                && isActiveMarketStation(null, ai.buyStation)
                && isActiveMarketStation(null, ai.sellStation)
                && mm.get(ai.buyStation).isTradable(ai.targetItem)
                && mm.get(ai.sellStation).isTradable(ai.targetItem);
    }

    private boolean isSellRouteValid(TradeAIComponent ai) {
        return isValidItem(ai.targetItem)
                && isActiveMarketStation(null, ai.sellStation)
                && mm.get(ai.sellStation).isTradable(ai.targetItem);
    }

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

    private boolean acceptsNewRouteItem(Entity fleet, TradeAIComponent ai, int itemId) {
        return (ai.specializedItem == -1 || ai.specializedItem == itemId)
                && canShipPurchaseItem(fleet, itemId);
    }

    private boolean canShipPurchaseItem(Entity fleet, int itemId) {
        ShipComponent ship = sm.get(fleet);
        return ship == null || ship.canPurchaseItem(itemId);
    }

    private boolean isValidItem(int itemId) {
        return itemId >= 0 && itemId < Constants.MAX_ITEMS;
    }

    private boolean isPositiveFinitePrice(float price) {
        return Float.isFinite(price) && price > 0f;
    }

    private long safeTradeValue(float price, int amount) {
        try {
            return Money.tradeValue(price, amount);
        } catch (IllegalArgumentException exception) {
            return -1L;
        }
    }

    private int safeMaximumAffordable(long balance, float price, int maxAmount) {
        if (balance < 0L || maxAmount <= 0 || !isPositiveFinitePrice(price)) {
            return 0;
        }
        try {
            return Money.maximumAffordable(balance, price, maxAmount);
        } catch (IllegalArgumentException exception) {
            return 0;
        }
    }

    private void abandonRoute(TradeAIComponent ai) {
        ai.resetRoute();
        ai.state = TradeAIComponent.State.IDLE;
        ai.routeSearchCooldown = ROUTE_SEARCH_RETRY_SECONDS;
    }

    private void finishRoute(TradeAIComponent ai) {
        ai.resetRoute();
        ai.state = TradeAIComponent.State.IDLE;
        ai.routeSearchCooldown = 0f;
    }
}
