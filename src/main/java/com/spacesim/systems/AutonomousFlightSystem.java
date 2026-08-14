package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.flight.FlightDynamics;

import java.util.Objects;

/**
 * Fixed-tick autonomous movement executor sharing the Stage-14E inertial flight model.
 *
 * <p>All autonomous navigation layers — generic TradeAI/Mining and Stage-15 player-owned fleet
 * orders — write {@link FlightCommandComponent} only. This late-priority system executes after
 * those intent writers and is the sole normal-flight Transform integrator for autonomous ships.</p>
 */
public final class AutonomousFlightSystem extends IteratingSystem {
    /** Runs after default-priority AI/intent systems so the newest command is integrated this tick. */
    public static final int FLIGHT_INTEGRATION_PRIORITY = 100;

    private final ComponentMapper<FlightCommandComponent> commandMapper =
            ComponentMapper.getFor(FlightCommandComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);

    /** Creates the shared autonomous flight executor. */
    public AutonomousFlightSystem() {
        super(Family.all(FlightCommandComponent.class, TransformComponent.class).get(),
                FLIGHT_INTEGRATION_PRIORITY);
    }

    /**
     * Ensures an Engine has exactly one shared autonomous flight integration system.
     *
     * @param engine local authoritative simulation Engine
     */
    public static void installIfMissing(Engine engine) {
        Engine checked = Objects.requireNonNull(engine, "Autonomous flight Engine not set");
        if (checked.getSystem(AutonomousFlightSystem.class) == null) {
            checked.addSystem(new AutonomousFlightSystem());
        }
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
