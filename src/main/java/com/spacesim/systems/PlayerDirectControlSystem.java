package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.flight.FlightDynamics;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;

/**
 * Fixed-tick physical movement executor for the directly controlled player ship.
 *
 * <p>Input code never writes Transform directly. It changes only transient
 * {@link PlayerControlledComponent} intent; this Ashley system applies the shared Stage-14E
 * mass/thrust model through {@link FlightDynamics}. Releasing input therefore requests zero desired
 * velocity and produces finite counter-thrust braking rather than an instantaneous stop.</p>
 */
public final class PlayerDirectControlSystem extends IteratingSystem {
    private static final float THRUST_EPSILON = 1.0e-4f;
    private final ProductionEngineeringRuntimeResolver engineering =
            new ProductionEngineeringRuntimeResolver();
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

        EngineeringComponent fitted = entity.getComponent(EngineeringComponent.class);
        if (fitted == null) {
            // Historical non-fitted entities remain an explicit migration compatibility seam.
            FlightDynamics.advance(
                    transform,
                    FlightDynamics.profile(entity, control.movementSpeed),
                    control.axisX,
                    control.axisY,
                    deltaTime);
            return;
        }

        double throttle = requiresThrust(
                transform, control.axisX, control.axisY, control.movementSpeed) ? 1d : 0d;
        var result = engineering.advancePropulsion(fitted, throttle, deltaTime);
        FlightDynamics.advancePhysical(
                transform,
                result.derivedState().totalMassKg(),
                result.actualThrustN(),
                control.movementSpeed,
                control.axisX,
                control.axisY,
                deltaTime);
    }
    private static boolean requiresThrust(
            TransformComponent transform,
            float axisX,
            float axisY,
            float speedCap) {
        float lengthSquared = axisX * axisX + axisY * axisY;
        float desiredX = axisX;
        float desiredY = axisY;
        if (lengthSquared > 1f) {
            float inverseLength = 1f / (float) Math.sqrt(lengthSquared);
            desiredX *= inverseLength;
            desiredY *= inverseLength;
        }
        desiredX *= speedCap;
        desiredY *= speedCap;
        float dx = desiredX - transform.velocity.x;
        float dy = desiredY - transform.velocity.y;
        return dx * dx + dy * dy > THRUST_EPSILON * THRUST_EPSILON;
    }
}
