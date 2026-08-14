package com.spacesim.flight;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;

import java.util.Objects;

/**
 * Shared local-navigation intent helper for autonomous ship systems.
 *
 * <p>The helper never mutates {@link TransformComponent#position} or
 * {@link TransformComponent#velocity}. It only writes a transient {@link FlightCommandComponent};
 * {@link com.spacesim.systems.AutonomousFlightSystem} and {@link FlightDynamics} remain the sole
 * normal-flight integration boundary for autonomous ships.</p>
 */
public final class InertialNavigation {
    /** Default velocity magnitude below which a ship is considered physically stopped. */
    public static final float DEFAULT_STOP_SPEED = 0.25f;
    private static final float MIN_APPROACH_SPEED_CAP = 1f;
    private static final float EPSILON = 0.0001f;

    private InertialNavigation() {
        throw new AssertionError("InertialNavigation does not create instances");
    }

    /**
     * Writes an inertial command toward a target boundary.
     *
     * @param ship physical ship entity
     * @param target target transform
     * @param speedCap positive hull/runtime assisted speed cap
     * @param arrivalRange non-negative physical interaction radius
     * @return current navigation state before the next physics integration step
     */
    public static Status approach(
            Entity ship,
            TransformComponent target,
            float speedCap,
            float arrivalRange) {
        Entity checkedShip = Objects.requireNonNull(ship, "Navigation ship not set");
        TransformComponent transform = checkedShip.getComponent(TransformComponent.class);
        if (transform == null || target == null || checkedShip.getComponent(ShipComponent.class) == null
                || !Float.isFinite(speedCap) || speedCap <= 0f
                || !Float.isFinite(arrivalRange) || arrivalRange < 0f
                || !validTransform(transform) || !validTransform(target)) {
            clear(checkedShip);
            return Status.INVALID;
        }

        float dx = target.position.x - transform.position.x;
        float dy = target.position.y - transform.position.y;
        float distanceSquared = dx * dx + dy * dy;
        float allowedSquared = arrivalRange * arrivalRange;
        if (!Float.isFinite(distanceSquared)) {
            stop(checkedShip, speedCap);
            return Status.INVALID;
        }
        if (distanceSquared <= allowedSquared + EPSILON) {
            stop(checkedShip, speedCap);
            return transform.velocity.len2() <= DEFAULT_STOP_SPEED * DEFAULT_STOP_SPEED
                    ? Status.ARRIVED
                    : Status.BRAKING;
        }

        float distance = (float) Math.sqrt(distanceSquared);
        FlightDynamics.Profile profile;
        try {
            profile = FlightDynamics.profile(checkedShip, speedCap);
        } catch (IllegalArgumentException exception) {
            clear(checkedShip);
            return Status.INVALID;
        }
        float stoppingLimited = FlightDynamics.stoppingLimitedSpeed(
                Math.max(0f, distance - arrivalRange), profile);
        float requestedSpeedCap = Math.min(
                speedCap,
                Math.max(MIN_APPROACH_SPEED_CAP, stoppingLimited));
        ensureCommand(checkedShip).set(dx / distance, dy / distance, requestedSpeedCap);
        return Status.APPROACHING;
    }

    /**
     * Requests physical braking without directly zeroing velocity.
     *
     * @param ship physical ship entity
     * @param speedCap positive assisted hull/runtime speed cap
     */
    public static void stop(Entity ship, float speedCap) {
        Entity checked = Objects.requireNonNull(ship, "Navigation ship not set");
        if (checked.getComponent(TransformComponent.class) == null
                || checked.getComponent(ShipComponent.class) == null
                || !Float.isFinite(speedCap) || speedCap <= 0f) {
            clear(checked);
            return;
        }
        FlightCommandComponent command = ensureCommand(checked);
        if (!Float.isFinite(command.speedCap) || command.speedCap <= 0f) {
            command.set(0f, 0f, speedCap);
        } else {
            command.stop();
        }
    }

    /**
     * Removes stale autonomous intent when no valid physical flight profile exists.
     *
     * @param ship ship whose transient autonomous command should be removed
     */
    public static void clear(Entity ship) {
        if (ship != null) {
            ship.remove(FlightCommandComponent.class);
        }
    }

    /**
     * Checks whether a physical ship is already inside an interaction radius and nearly stopped.
     *
     * @param ship physical ship entity
     * @param target target transform
     * @param arrivalRange non-negative interaction radius
     * @return true when the ship is inside range and below the shared stop-speed threshold
     */
    public static boolean arrived(Entity ship, TransformComponent target, float arrivalRange) {
        if (ship == null || target == null || !Float.isFinite(arrivalRange) || arrivalRange < 0f) {
            return false;
        }
        TransformComponent transform = ship.getComponent(TransformComponent.class);
        if (!validTransform(transform) || !validTransform(target)) {
            return false;
        }
        return transform.position.dst2(target.position) <= arrivalRange * arrivalRange + EPSILON
                && transform.velocity.len2() <= DEFAULT_STOP_SPEED * DEFAULT_STOP_SPEED;
    }

    private static FlightCommandComponent ensureCommand(Entity entity) {
        FlightCommandComponent command = entity.getComponent(FlightCommandComponent.class);
        if (command == null) {
            command = new FlightCommandComponent();
            entity.add(command);
        }
        return command;
    }

    private static boolean validTransform(TransformComponent transform) {
        return transform != null
                && transform.position != null
                && transform.velocity != null
                && Float.isFinite(transform.position.x)
                && Float.isFinite(transform.position.y)
                && Float.isFinite(transform.velocity.x)
                && Float.isFinite(transform.velocity.y);
    }

    /** Local navigation result before the shared inertial physics step. */
    public enum Status {
        /** Ship is outside the interaction boundary and has an approach command. */
        APPROACHING,
        /** Ship is inside the boundary but still has physical velocity to remove. */
        BRAKING,
        /** Ship is inside the boundary and physically stopped. */
        ARRIVED,
        /** Required physical/configuration state is invalid. */
        INVALID
    }
}
