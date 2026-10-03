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

    /**
     * Advances the same fitted propulsion over exact generated-world kinematics.
     * Zero input coasts; explicit braking consumes finite reaction mass. The campaign supplies
     * one completed fixed interval, so this method never advances a second clock.
     * @param entity existing player ship with engineering
     * @param physical exact Stage-20 kinematics
     * @param axisX finite normalized horizontal thrust
     * @param axisY finite normalized vertical thrust
     * @param braking whether to request finite counter-thrust
     * @param deltaSeconds positive fixed interval
     * @return exact next physical state
     */
    public com.spacesim.world.LocalPhysicalKinematics advanceExact(
            Entity entity, com.spacesim.world.LocalPhysicalKinematics physical,
            float axisX, float axisY, boolean braking, double deltaSeconds) {
        if (!Float.isFinite(axisX) || !Float.isFinite(axisY)
                || !Double.isFinite(deltaSeconds) || deltaSeconds <= 0)
            throw new IllegalArgumentException("Invalid physical control interval or axes");
        EngineeringComponent fitted = entity.getComponent(EngineeringComponent.class);
        if (fitted == null) throw new IllegalStateException("Physical player movement requires fitted propulsion");
        double speed = Math.hypot(physical.velocityXMps(), physical.velocityYMps());
        double magnitude = Math.min(1d, Math.hypot(axisX, axisY));
        double x = axisX, y = axisY;
        double norm = Math.hypot(x, y);
        if (norm > 0) { x /= norm; y /= norm; }
        double throttle = magnitude;
        if (braking) {
            x = speed > 0 ? -physical.velocityXMps() / speed : 0d;
            y = speed > 0 ? -physical.velocityYMps() / speed : 0d;
            throttle = engineering.throttleForDeltaV(fitted, speed, deltaSeconds);
        }
        var result = engineering.advancePropulsion(fitted, throttle, deltaSeconds);
        double deltaV = result.actualThrustN() / result.derivedState().totalMassKg() * deltaSeconds;
        if (braking) deltaV = Math.min(speed, deltaV);
        double vx = physical.velocityXMps() + x * deltaV;
        double vy = physical.velocityYMps() + y * deltaV;
        return new com.spacesim.world.LocalPhysicalKinematics(physical.position().translated(
                (physical.velocityXMps() + vx) * 0.5d * deltaSeconds,
                (physical.velocityYMps() + vy) * 0.5d * deltaSeconds), vx, vy);
    }

    /** Applies one fixed-tick movement step under finite acceleration/braking limits. */
    /**
     * Steers an inactive owned hull toward the exact moving target using the same finite propulsion.
     * Separation is guidance only; the result never snaps to a target position or velocity.
     * @param entity existing fitted ship
     * @param physical current exact kinematics
     * @param target current exact target kinematics
     * @param standOffMeters non-negative desired separation
     * @param deltaSeconds one completed authoritative interval
     * @return physically integrated next kinematics
     */
    public com.spacesim.world.LocalPhysicalKinematics followExact(Entity entity,
            com.spacesim.world.LocalPhysicalKinematics physical,
            com.spacesim.world.LocalPhysicalKinematics target, double standOffMeters, double deltaSeconds) {
        if (!Double.isFinite(standOffMeters) || standOffMeters < 0) throw new IllegalArgumentException("Invalid separation");
        var fitted = entity.getComponent(EngineeringComponent.class);
        var displacement = physical.position().displacementTo(target.position());
        double distance = Math.hypot(displacement.deltaXM(), displacement.deltaYM());
        double acceleration = engineering.derive(fitted).accelerationMps2();
        double approach = Math.min(100d, Math.sqrt(2d * acceleration * Math.max(0d, distance - standOffMeters)));
        double vx = target.velocityXMps() + (distance > 0 ? displacement.deltaXM() / distance * approach : 0);
        double vy = target.velocityYMps() + (distance > 0 ? displacement.deltaYM() / distance * approach : 0);
        double dx = vx - physical.velocityXMps(), dy = vy - physical.velocityYMps();
        double deltaV = Math.hypot(dx, dy);
        double throttle = engineering.throttleForDeltaV(fitted, deltaV, deltaSeconds);
        return advanceExact(entity, physical, (float)(deltaV > 0 ? dx / deltaV * throttle : 0),
                (float)(deltaV > 0 ? dy / deltaV * throttle : 0), false, deltaSeconds);
    }

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

        double requiredDeltaV = requiredDeltaV(
                transform, control.axisX, control.axisY, control.movementSpeed);
        double throttle = engineering.throttleForDeltaV(fitted, requiredDeltaV, deltaTime);
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
    private static double requiredDeltaV(
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
        double deltaV = Math.hypot(dx, dy);
        return deltaV <= THRUST_EPSILON ? 0d : deltaV;
    }
}
