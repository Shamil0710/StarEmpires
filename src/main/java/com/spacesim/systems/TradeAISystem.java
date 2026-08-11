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
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.controllers.TradeController;
import com.spacesim.util.SpatialHashGrid;

import java.util.List;

public class TradeAISystem extends IteratingSystem {
    private static final float FLEET_SPEED = 100f;
    private static final float ARRIVAL_DISTANCE = 10f;
    private static final float ROUTE_SEARCH_RETRY_SECONDS = 1f;
    private static final int ROUTE_SEARCH_RADIUS_CELLS = 5;

    private final SpatialHashGrid grid;
    private final TradeController tradeController = new TradeController();
    private ImmutableArray<Entity> marketStations;

    private final ComponentMapper<TradeAIComponent> am = ComponentMapper.getFor(TradeAIComponent.class);
    private final ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<ReputationComponent> rm = ComponentMapper.getFor(ReputationComponent.class);

    public TradeAISystem(SpatialHashGrid grid) {
        super(Family.all(TradeAIComponent.class, TransformComponent.class, InventoryComponent.class).get());
        this.grid = grid;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        marketStations = engine.getEntitiesFor(Family.all(
                TransformComponent.class,
                MarketComponent.class,
                InventoryComponent.class
        ).get());
    }

    @Override
    public void update(float deltaTime) {
        rebuildSpatialIndex();
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TradeAIComponent ai = am.get(entity);
        TransformComponent transform = tm.get(entity);

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

    private void rebuildSpatialIndex() {
        grid.clear();
        for (Entity station : marketStations) {
            grid.insert(station, tm.get(station).position);
        }
    }

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
                    if (!buyMarket.isTradable(itemId) || !sellMarket.isTradable(itemId)) {
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

                    float routeProfit = (salePrice - purchasePrice) * amount;
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
                float revenue = salePrice * amount;
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

    private void move(TransformComponent fleetPosition, Entity target, float deltaTime, TradeAIComponent ai,
                      TradeAIComponent.State arrivalState) {
        if (!isActiveMarketStation(null, target)) {
            abandonRoute(ai);
            return;
        }

        Vector2 targetPosition = tm.get(target).position;
        Vector2 toTarget = targetPosition.cpy().sub(fleetPosition.position);
        float distance = toTarget.len();
        float step = FLEET_SPEED * Math.max(0f, deltaTime);

        if (distance <= step || distance < ARRIVAL_DISTANCE) {
            fleetPosition.position.set(targetPosition);
            ai.targetStation = target;
            ai.state = arrivalState;
            return;
        }

        fleetPosition.position.mulAdd(toTarget.nor(), step);
    }

    private void buyCargo(Entity fleet, TradeAIComponent ai) {
        if (!isBuyRouteValid(ai)) {
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

    private boolean isBuyRouteValid(TradeAIComponent ai) {
        return isValidItem(ai.targetItem)
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

    private boolean isValidItem(int itemId) {
        return itemId >= 0 && itemId < Constants.MAX_ITEMS;
    }

    private boolean isPositiveFinitePrice(float price) {
        return Float.isFinite(price) && price > 0f;
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
