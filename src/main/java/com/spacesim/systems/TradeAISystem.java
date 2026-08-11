package com.spacesim.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.controllers.TradeController;
import com.spacesim.util.SpatialHashGrid;
import java.util.List;

public class TradeAISystem extends IteratingSystem {
    private static final float FLEET_SPEED = 100f;
    private static final float ARRIVAL_DISTANCE = 10f;
    private static final int ROUTE_SEARCH_RADIUS_CELLS = 5;

    private final SpatialHashGrid grid;
    private final TradeController tradeController = new TradeController();
    private ImmutableArray<Entity> spatialEntities;

    private ComponentMapper<TradeAIComponent> am = ComponentMapper.getFor(TradeAIComponent.class);
    private ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private ComponentMapper<ReputationComponent> rm = ComponentMapper.getFor(ReputationComponent.class);

    public TradeAISystem(SpatialHashGrid grid) {
        super(Family.all(TradeAIComponent.class, TransformComponent.class, InventoryComponent.class).get());
        this.grid = grid;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        spatialEntities = engine.getEntitiesFor(Family.all(TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        rebuildSpatialIndex();
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TradeAIComponent ai = am.get(entity);
        TransformComponent trans = tm.get(entity);

        switch (ai.state) {
            case IDLE:
                findRoute(entity, ai, trans);
                break;
            case TRAVEL_TO_BUY:
                move(trans, ai.buyStation, deltaTime, ai, TradeAIComponent.State.BUYING);
                break;
            case BUYING:
                buyCargo(entity, ai);
                break;
            case TRAVEL_TO_SELL:
                move(trans, ai.sellStation, deltaTime, ai, TradeAIComponent.State.SELLING);
                break;
            case SELLING:
                sellCargo(entity, ai);
                break;
        }
    }

    private void rebuildSpatialIndex() {
        grid.clear();
        for (Entity entity : spatialEntities) {
            TransformComponent transform = tm.get(entity);
            grid.insert(entity, transform.position);
        }
    }

    private void findRoute(Entity fleet, TradeAIComponent ai, TransformComponent pos) {
        List<Entity> nearby = grid.getNearby(pos.position, ROUTE_SEARCH_RADIUS_CELLS);
        float bestProfit = 0f;

        for(Entity buyStation : nearby) {
            if(!isMarketStation(fleet, buyStation)) continue;

            InventoryComponent buyInventory = im.get(buyStation);
            ReputationComponent reputation = rm.get(fleet);

            for(Entity sellStation : nearby) {
                if(sellStation == buyStation || !isMarketStation(fleet, sellStation)) continue;

                InventoryComponent sellInventory = im.get(sellStation);

                for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
                    float profitPerUnit = tradeController.getEffectiveBuyPrice(sellStation, itemId, reputation)
                            - tradeController.getEffectiveSellPrice(buyStation, itemId, reputation);
                    if (profitPerUnit <= 0f) continue;

                    int amount = calculateTradeAmount(ai, buyStation, buyInventory, sellInventory, mm.get(sellStation), itemId, reputation);
                    if (amount <= 0) continue;

                    float routeProfit = profitPerUnit * amount;

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

        if (bestProfit > 0f) {
            ai.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        }
    }

    private boolean isMarketStation(Entity fleet, Entity entity) {
        return entity != fleet && mm.has(entity) && im.has(entity) && tm.has(entity);
    }

    private int calculateTradeAmount(TradeAIComponent ai, Entity buyStation, InventoryComponent buyInventory,
                                     InventoryComponent sellInventory, MarketComponent sellMarket, int itemId,
                                     ReputationComponent reputation) {
        int freeCargo = Math.min(ai.cargoSpace - ai.cargoAmount, tradeController.getFreeCapacity(sellInventory));
        int stationDemand = Math.max(0, sellMarket.targetStock[itemId] - sellInventory.stock[itemId]);
        int affordableAmount = Math.max(0, (int)(ai.credits / Math.max(0.01f,
                tradeController.getEffectiveSellPrice(buyStation, itemId, reputation))));
        int desiredAmount = stationDemand > 0 ? stationDemand : freeCargo;

        return Math.min(Math.min(freeCargo, buyInventory.stock[itemId]), Math.min(affordableAmount, desiredAmount));
    }

    private void move(TransformComponent fleetPos, Entity target, float dt, TradeAIComponent ai, TradeAIComponent.State arrivalState) {
        if(target == null || !tm.has(target)) {
            ai.resetRoute();
            ai.state = TradeAIComponent.State.IDLE;
            return;
        }

        Vector2 targetPos = tm.get(target).position;
        Vector2 toTarget = targetPos.cpy().sub(fleetPos.position);
        float distance = toTarget.len();
        float step = FLEET_SPEED * dt;

        if (distance <= step || distance < ARRIVAL_DISTANCE) {
            fleetPos.position.set(targetPos);
            ai.targetStation = target;
            ai.state = arrivalState;
            return;
        }

        fleetPos.position.mulAdd(toTarget.nor(), step);
    }

    private void buyCargo(Entity fleet, TradeAIComponent ai) {
        if (!isRouteValid(ai)) {
            ai.resetRoute();
            ai.state = TradeAIComponent.State.IDLE;
            return;
        }

        InventoryComponent stationInventory = im.get(ai.buyStation);
        MarketComponent stationMarket = mm.get(ai.buyStation);

        int itemId = ai.targetItem;
        int amount = Math.min(ai.targetAmount, Math.min(ai.cargoSpace - ai.cargoAmount, stationInventory.stock[itemId]));
        float cost = stationMarket.sellPrices[itemId] * amount;

        ReputationComponent reputation = rm.get(fleet);
        TradeController.CreditAccount credits = new TradeController.CreditAccount(ai.credits);
        if (amount <= 0 || ai.credits < cost || !tradeController.buyFromStation(ai.buyStation, im.get(fleet), itemId, amount, credits, reputation)) {
            ai.resetRoute();
            ai.state = TradeAIComponent.State.IDLE;
            return;
        }

        ai.cargoAmount += amount;
        ai.credits = credits.credits;
        ai.targetAmount = amount;

        ai.targetStation = ai.sellStation;
        ai.state = TradeAIComponent.State.TRAVEL_TO_SELL;
    }

    private void sellCargo(Entity fleet, TradeAIComponent ai) {
        if (!isRouteValid(ai)) {
            ai.resetRoute();
            ai.state = TradeAIComponent.State.IDLE;
            return;
        }

        InventoryComponent fleetInventory = im.get(fleet);
        int itemId = ai.targetItem;
        int amount = Math.min(ai.targetAmount, Math.min(ai.cargoAmount, fleetInventory.stock[itemId]));
        ReputationComponent reputation = rm.get(fleet);
        TradeController.CreditAccount credits = new TradeController.CreditAccount(ai.credits);

        if (amount > 0 && tradeController.sellToStation(ai.sellStation, fleetInventory, itemId, amount, credits, reputation)) {
            ai.cargoAmount -= amount;
            ai.credits = credits.credits;
        }

        ai.resetRoute();
        ai.state = TradeAIComponent.State.IDLE;
    }

    private boolean isRouteValid(TradeAIComponent ai) {
        return ai.buyStation != null
                && ai.sellStation != null
                && ai.targetItem >= 0
                && ai.targetItem < Constants.MAX_ITEMS
                && isMarketStation(null, ai.buyStation)
                && isMarketStation(null, ai.sellStation);
    }
}
