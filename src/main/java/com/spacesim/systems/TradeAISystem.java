package com.spacesim.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.*;
import com.spacesim.util.SpatialHashGrid;
import java.util.List;

public class TradeAISystem extends IteratingSystem {
    private final SpatialHashGrid grid;
    private ImmutableArray<Entity> spatialEntities;

    private ComponentMapper<TradeAIComponent> am = ComponentMapper.getFor(TradeAIComponent.class);
    private ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);

    public TradeAISystem(SpatialHashGrid grid) {
        super(Family.all(TradeAIComponent.class, TransformComponent.class).get());
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
            case TRAVEL_TO_SELL:
                move(trans, ai.targetStation, deltaTime, ai);
                break;
            case TRADING:
                executeTrade(entity, ai);
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
        List<Entity> nearby = grid.getNearby(pos.position, 2);
        // Упрощенный поиск: ищем первого попавшегося продавца
        for(Entity e : nearby) {
            if(e != fleet && mm.has(e)) { // Это станция
                ai.targetStation = e;
                ai.targetItem = 2; // Food
                ai.state = TradeAIComponent.State.TRAVEL_TO_BUY;
                return;
            }
        }
    }

    private void move(TransformComponent fleetPos, Entity target, float dt, TradeAIComponent ai) {
        if(target == null || !tm.has(target)) { ai.state = TradeAIComponent.State.IDLE; return; }

        Vector2 targetPos = tm.get(target).position;
        Vector2 toTarget = targetPos.cpy().sub(fleetPos.position);
        float distance = toTarget.len();
        float step = 100 * dt; // Скорость 100

        if (distance <= step || distance < 10) {
            fleetPos.position.set(targetPos);
            ai.state = TradeAIComponent.State.TRADING;
            return;
        }

        fleetPos.position.mulAdd(toTarget.nor(), step);
    }

    private void executeTrade(Entity fleet, TradeAIComponent ai) {
        // Логика трансфера инвентаря
        InventoryComponent stationInv = im.get(ai.targetStation);
        MarketComponent stationMarket = mm.get(ai.targetStation);

        // Покупаем или продаем
        stationMarket.isDirty = true; // Триггер пересчета цены
        ai.state = TradeAIComponent.State.IDLE;
    }
}
