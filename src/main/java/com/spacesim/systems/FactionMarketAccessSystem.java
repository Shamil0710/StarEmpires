package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityRegistry;

import java.util.Objects;

/**
 * Post-planner safety gate faction market access для автономного торгового FSM.
 *
 * <p>Система выполняется после обычного {@link TradeAISystem}. Если planner выбрал рынок, который
 * strategic diplomacy запрещает участнику, route немедленно сбрасывается до следующего movement
 * tick. Authoritative сделка дополнительно проверяется TradeController, поэтому restored stale
 * route в BUYING/SELLING также не может совершить запрещённый transfer.</p>
 */
public final class FactionMarketAccessSystem extends IteratingSystem {
    /** Priority после стандартных simulation systems с default priority 0. */
    public static final int PRIORITY = 100;
    private static final float REPLAN_COOLDOWN_SECONDS = 1f;

    private final EntityRegistry registry;
    private final ComponentMapper<TradeAIComponent> tradeAiMapper =
            ComponentMapper.getFor(TradeAIComponent.class);
    private final ComponentMapper<FactionComponent> factionMapper =
            ComponentMapper.getFor(FactionComponent.class);
    private final ComponentMapper<FactionMarketAccessComponent> accessMapper =
            ComponentMapper.getFor(FactionMarketAccessComponent.class);

    /**
     * Создаёт deterministic post-planner gate.
     *
     * @param registry persistent EntityId resolver локальной simulation session
     */
    public FactionMarketAccessSystem(EntityRegistry registry) {
        super(Family.all(TradeAIComponent.class).get(), PRIORITY);
        this.registry = Objects.requireNonNull(registry, "EntityRegistry не задан");
    }

    /** Проверяет текущий persistent route одного торгового флота. */
    @Override
    protected void processEntity(Entity fleet, float deltaTime) {
        TradeAIComponent ai = tradeAiMapper.get(fleet);
        if (ai == null || ai.state == TradeAIComponent.State.IDLE) {
            return;
        }
        int participantFactionId = factionId(fleet);
        if (isDenied(ai.buyStationId, participantFactionId)
                || isDenied(ai.sellStationId, participantFactionId)
                || isDenied(ai.targetStationId, participantFactionId)) {
            ai.state = TradeAIComponent.State.IDLE;
            ai.resetRoute();
            ai.routeSearchCooldown = Math.max(ai.routeSearchCooldown, REPLAN_COOLDOWN_SECONDS);
        }
    }

    private boolean isDenied(EntityId stationId, int participantFactionId) {
        if (stationId == null) {
            return false;
        }
        Entity station = registry.find(stationId);
        if (station == null) {
            return false;
        }
        FactionMarketAccessComponent access = accessMapper.get(station);
        return access != null && !access.canTrade(participantFactionId);
    }

    private int factionId(Entity entity) {
        FactionComponent faction = factionMapper.get(entity);
        return faction == null ? -1 : faction.factionId;
    }
}
