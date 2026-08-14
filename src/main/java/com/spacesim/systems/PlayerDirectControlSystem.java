package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.flight.FlightDynamics;

/**
 * Fixed-tick physical movement executor for the directly controlled player ship.
 *
 * <p>Input code never writes Transform directly. It changes only transient
 * {@link PlayerControlledComponent} intent; this Ashley system applies the shared Stage-14E
 * mass/thrust model through {@link FlightDynamics}. Releasing input therefore requests zero desired
 * velocity and produces finite counter-thrust braking rather than an instantaneous stop.</p>
 */
public final class PlayerDirectControlSystem extends IteratingSystem {
    private final ComponentMapper<PlayerControlledComponent> controlMapper =
            ComponentMapper.getFor(PlayerControlledComponent.class);
    private final ComponentMapper<TransformComponent> transformMapper =
            ComponentMapper.getFor(TransformComponent.class);

    /** Creates the transient direct-control movement system. */
    public PlayerDirectControlSystem() {
        super(Family.all(PlayerControlledComponent.class, TransformComponent.class).get());
    }

    /** Applies one fixed-tick movement step under finite acceleration/braking limits. */
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PlayerControlledComponent control = controlMapper.get(entity);
        TransformComponent transform = transformMapper.get(entity);
        if (control == null || transform == null
                || control.docked
                || !Float.isFinite(control.movementSpeed)
                || control.movementSpeed <= 0f
                || !Float.isFinite(deltaTime)
                || deltaTime <= 0f) {
            return;
        }

        FlightDynamics.advance(
                transform,
                FlightDynamics.profile(entity, control.movementSpeed),
                control.axisX,
                control.axisY,
                deltaTime);
    }
}
