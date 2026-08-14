package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.flight.FlightDynamics;

/**
 * Fixed-tick autonomous movement executor sharing the Stage-14E inertial flight model.
 *
 * <p>Stage 15 fleet orders can write {@link FlightCommandComponent} without gaining a separate AI
 * physics path. This executor is already headless-testable in Stage 14 so equivalent player/AI
 * movement can be compared before the autonomous-order feature set is implemented.</p>
 */
public final class AutonomousFlightSystem extends IteratingSystem {
    private final ComponentMapper<FlightCommandComponent> commandMapper =
            ComponentMapper.getFor(FlightCommandComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);

    /** Creates the shared autonomous flight executor. */
    public AutonomousFlightSystem() {
        super(Family.all(FlightCommandComponent.class, TransformComponent.class).get());
    }

    /** Applies one fixed-tick autonomous movement step through {@link FlightDynamics}. */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        FlightCommandComponent command = commandMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);
        if (command == null || transform == null
                || !Float.isFinite(command.speedCap) || command.speedCap <= 0f
                || !Float.isFinite(deltaTime) || deltaTime <= 0f) {
            return;
        }
        FlightDynamics.advance(
                transform,
                FlightDynamics.profile(entity, command.speedCap),
                command.axisX,
                command.axisY,
                deltaTime);
    }
}
