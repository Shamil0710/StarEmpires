package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.TransformComponent;

/**
 * Fixed-tick physical movement executor for the directly controlled player ship.
 *
 * <p>Input code never writes Transform directly. It changes only transient
 * {@link PlayerControlledComponent} intent; this Ashley system applies movement using simulation
 * delta time, preserving pause/time-scale/fixed-step semantics.</p>
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

    /** Applies one fixed-tick movement step. */
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
            if (transform != null) {
                transform.velocity.setZero();
            }
            return;
        }

        float velocityX = control.axisX * control.movementSpeed;
        float velocityY = control.axisY * control.movementSpeed;
        transform.velocity.set(velocityX, velocityY);
        transform.position.mulAdd(transform.velocity, deltaTime);
    }
}
