package com.spacesim.flight;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.model.ShipType;

import java.util.Objects;

/**
 * Shared Stage-14E translational flight model used by player and autonomous movement executors.
 *
 * <p>The baseline intentionally models game-friendly inertia rather than orbital mechanics. Every
 * ship has a role-derived dry hull mass and finite thrust. Real cargo from
 * {@link InventoryComponent} contributes normalized physical mass at one mass unit per cargo unit.
 * Therefore acceleration and braking degrade continuously as cargo is loaded, without a scripted
 * full-cargo penalty. Stage 17.5 may replace the normalized cargo unit with data-driven per-item,
 * equipment, armor and ammunition masses while preserving this controller contract.</p>
 *
 * <p>Callers express desired velocity/direction only. This class is the authoritative owner of the
 * acceleration/braking limit and fixed-tick Transform integration for the Stage-14 flight seam.</p>
 */
public final class FlightDynamics {
    private static final float EPSILON = 0.0001f;
    private static final float CARGO_MASS_PER_UNIT = 1f;

    private FlightDynamics() {
        throw new AssertionError("FlightDynamics does not create instances");
    }

    /**
     * Resolves the current physical profile from the real ship role and inventory.
     *
     * @param entity physical ship entity
     * @param speedCap maximum assisted speed supplied by the current hull/runtime movement data
     * @return immutable current profile
     * @throws IllegalArgumentException when required ship data or speed is invalid
     */
    public static Profile profile(Entity entity, float speedCap) {
        Entity checked = Objects.requireNonNull(entity, "Flight entity not set");
        ShipComponent ship = checked.getComponent(ShipComponent.class);
        InventoryComponent inventory = checked.getComponent(InventoryComponent.class);
        if (ship == null || ship.type == null) {
            throw new IllegalArgumentException("Flight entity requires a configured ShipComponent");
        }
        if (!Float.isFinite(speedCap) || speedCap <= 0f) {
            throw new IllegalArgumentException("Flight speed cap must be finite and positive");
        }
        int cargoUnits = inventory == null ? 0 : Math.max(0, inventory.getTotalStock());
        int cargoCapacity = inventory == null ? 0 : Math.max(0, inventory.capacity);
        float dryMass = dryMass(ship.type, cargoCapacity);
        float cargoMass = cargoUnits * CARGO_MASS_PER_UNIT;
        float totalMass = dryMass + cargoMass;
        float thrust = dryMass * dryAcceleration(ship.type);
        float brakingThrust = dryMass * dryBrakingAcceleration(ship.type);
        return new Profile(
                dryMass,
                cargoMass,
                totalMass,
                thrust,
                brakingThrust,
                speedCap,
                thrust / totalMass,
                brakingThrust / totalMass);
    }

    /**
     * Advances a ship toward a normalized desired movement vector under finite thrust.
     *
     * @param transform authoritative physical transform
     * @param profile current mass/thrust profile
     * @param axisX desired horizontal axis
     * @param axisY desired vertical axis
     * @param deltaSeconds authoritative fixed-tick delta
     */
    public static void advance(
            TransformComponent transform,
            Profile profile,
            float axisX,
            float axisY,
            float deltaSeconds) {
        TransformComponent checkedTransform = Objects.requireNonNull(transform, "Transform not set");
        Profile checkedProfile = Objects.requireNonNull(profile, "Flight profile not set");
        if (!Float.isFinite(axisX) || !Float.isFinite(axisY)
                || !Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("Flight intent/delta must be finite and non-negative");
        }
        if (deltaSeconds == 0f) {
            return;
        }

        Vector2 desired = new Vector2(axisX, axisY);
        if (desired.len2() > 1f) {
            desired.nor();
        }
        desired.scl(checkedProfile.speedCap());
        advanceTowardVelocity(checkedTransform, checkedProfile, desired, deltaSeconds);
    }

    /**
     * Advances a Stage-17.5 physical ship using externally resolved mass and actually produced thrust.
     *
     * <p>This overload exists so Stage-19 tactical control can reuse the authoritative inertial
     * integrator without routing Stage-17.5 kilogram-native state through the legacy
     * {@link InventoryComponent} normalization in {@link #profile(Entity, float)}. The supplied
     * thrust must already have been resolved by the physical engineering runtime, including power,
     * thermal, damage and reaction-mass limits. A zero-thrust step preserves inertial drift and does
     * not manufacture acceleration. The speed cap is only the assisted command envelope used by this
     * game-friendly flight controller; it cannot increase the supplied physical thrust.</p>
     *
     * @param transform authoritative physical transform
     * @param totalMassKg current Stage-17.5 physical ship mass in kilograms
     * @param actualThrustN actual thrust produced by the engineering runtime in newtons
     * @param speedCap assisted command-speed envelope in meters per second
     * @param axisX desired normalized horizontal movement axis
     * @param axisY desired normalized vertical movement axis
     * @param deltaSeconds authoritative fixed-tick duration
     */
    public static void advancePhysical(
            TransformComponent transform,
            double totalMassKg,
            double actualThrustN,
            float speedCap,
            float axisX,
            float axisY,
            float deltaSeconds) {
        TransformComponent checkedTransform = Objects.requireNonNull(transform, "Transform not set");
        if (!Double.isFinite(totalMassKg) || totalMassKg <= 0d
                || !Double.isFinite(actualThrustN) || actualThrustN < 0d
                || totalMassKg > Float.MAX_VALUE || actualThrustN > Float.MAX_VALUE
                || !Float.isFinite(speedCap) || speedCap <= 0f
                || !Float.isFinite(axisX) || !Float.isFinite(axisY)
                || !Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("Physical flight inputs must be finite and physically valid");
        }
        if (deltaSeconds == 0f) {
            return;
        }
        if (actualThrustN == 0d) {
            checkedTransform.position.mulAdd(checkedTransform.velocity, deltaSeconds);
            return;
        }

        float mass = (float) totalMassKg;
        float thrust = (float) actualThrustN;
        float acceleration = thrust / mass;
        if (!Float.isFinite(acceleration) || acceleration <= 0f) {
            throw new IllegalArgumentException("Physical thrust/mass ratio is outside supported float range");
        }
        Profile profile = new Profile(
                mass,
                0f,
                mass,
                thrust,
                thrust,
                speedCap,
                acceleration,
                acceleration);
        advance(checkedTransform, profile, axisX, axisY, deltaSeconds);
    }

    /**
     * Advances toward an explicit desired velocity, useful for AI navigation/formation executors.
     *
     * @param transform authoritative physical transform
     * @param profile current mass/thrust profile
     * @param desiredVelocity desired velocity vector; magnitude is clamped to profile speed cap
     * @param deltaSeconds authoritative fixed-tick delta
     */
    public static void advanceTowardVelocity(
            TransformComponent transform,
            Profile profile,
            Vector2 desiredVelocity,
            float deltaSeconds) {
        TransformComponent checkedTransform = Objects.requireNonNull(transform, "Transform not set");
        Profile checkedProfile = Objects.requireNonNull(profile, "Flight profile not set");
        Vector2 desired = new Vector2(Objects.requireNonNull(desiredVelocity, "Desired velocity not set"));
        if (!Float.isFinite(desired.x) || !Float.isFinite(desired.y)
                || !Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("Desired velocity/delta must be finite and non-negative");
        }
        if (deltaSeconds == 0f) {
            return;
        }
        if (desired.len2() > checkedProfile.speedCap() * checkedProfile.speedCap()) {
            desired.nor().scl(checkedProfile.speedCap());
        }

        Vector2 current = checkedTransform.velocity;
        Vector2 deltaVelocity = new Vector2(desired).sub(current);
        if (deltaVelocity.len2() > EPSILON * EPSILON) {
            boolean braking = isBraking(current, desired);
            float acceleration = braking
                    ? checkedProfile.brakingAcceleration()
                    : checkedProfile.acceleration();
            float maximumDelta = acceleration * deltaSeconds;
            if (deltaVelocity.len2() > maximumDelta * maximumDelta) {
                deltaVelocity.nor().scl(maximumDelta);
            }
            current.add(deltaVelocity);
        } else {
            current.set(desired);
        }
        checkedTransform.position.mulAdd(current, deltaSeconds);
    }

    /**
     * Returns the desired speed required to stop at a target boundary with current braking ability.
     *
     * @param remainingDistance non-negative distance to the desired boundary
     * @param profile current profile
     * @return speed not exceeding the hull speed cap
     */
    public static float stoppingLimitedSpeed(float remainingDistance, Profile profile) {
        Profile checked = Objects.requireNonNull(profile, "Flight profile not set");
        if (!Float.isFinite(remainingDistance) || remainingDistance <= 0f) {
            return 0f;
        }
        double stoppingSpeed = Math.sqrt(2d * checked.brakingAcceleration() * remainingDistance);
        return (float) Math.min(checked.speedCap(), stoppingSpeed);
    }

    private static boolean isBraking(Vector2 current, Vector2 desired) {
        float currentSpeed2 = current.len2();
        float desiredSpeed2 = desired.len2();
        return desiredSpeed2 + EPSILON < currentSpeed2
                || (currentSpeed2 > EPSILON && current.dot(desired) <= 0f);
    }

    private static float dryMass(ShipType type, int cargoCapacity) {
        float base = switch (type) {
            case FINISHED_GOODS_CARRIER -> 95f;
            case MATERIAL_CARRIER -> 125f;
            case GAS_LIQUID_CARRIER -> 145f;
            case MINING_SHIP -> 90f;
            case COMBAT_SHIP -> 75f;
        };
        float structurePerCapacity = switch (type) {
            case FINISHED_GOODS_CARRIER -> 0.30f;
            case MATERIAL_CARRIER -> 0.38f;
            case GAS_LIQUID_CARRIER -> 0.42f;
            case MINING_SHIP -> 0.28f;
            case COMBAT_SHIP -> 0.10f;
        };
        return base + cargoCapacity * structurePerCapacity;
    }

    private static float dryAcceleration(ShipType type) {
        return switch (type) {
            case FINISHED_GOODS_CARRIER -> 52f;
            case MATERIAL_CARRIER -> 44f;
            case GAS_LIQUID_CARRIER -> 40f;
            case MINING_SHIP -> 50f;
            case COMBAT_SHIP -> 82f;
        };
    }

    private static float dryBrakingAcceleration(ShipType type) {
        return switch (type) {
            case FINISHED_GOODS_CARRIER -> 62f;
            case MATERIAL_CARRIER -> 54f;
            case GAS_LIQUID_CARRIER -> 50f;
            case MINING_SHIP -> 60f;
            case COMBAT_SHIP -> 92f;
        };
    }

    /**
     * Immutable physical diagnostics for one ship at its current cargo load.
     *
     * @param dryMass hull/structure mass before cargo
     * @param cargoMass mass contributed by real inventory contents
     * @param totalMass total translational mass
     * @param thrust available acceleration thrust
     * @param brakingThrust available counter-thrust
     * @param speedCap assisted maximum speed
     * @param acceleration current maximum acceleration at this mass
     * @param brakingAcceleration current maximum braking acceleration at this mass
     */
    public record Profile(
            float dryMass,
            float cargoMass,
            float totalMass,
            float thrust,
            float brakingThrust,
            float speedCap,
            float acceleration,
            float brakingAcceleration) {
        /**
         * Validates the resolved immutable physical profile.
         *
         * @param dryMass hull/structure mass before cargo
         * @param cargoMass mass contributed by real inventory contents
         * @param totalMass total translational mass
         * @param thrust available acceleration thrust
         * @param brakingThrust available counter-thrust
         * @param speedCap assisted maximum speed
         * @param acceleration current maximum acceleration at this mass
         * @param brakingAcceleration current maximum braking acceleration at this mass
         */
        public Profile {
            if (!positive(dryMass) || cargoMass < 0f || !Float.isFinite(cargoMass)
                    || !positive(totalMass) || !positive(thrust) || !positive(brakingThrust)
                    || !positive(speedCap) || !positive(acceleration) || !positive(brakingAcceleration)
                    || totalMass + EPSILON < dryMass) {
                throw new IllegalArgumentException("Invalid flight profile");
            }
        }

        private static boolean positive(float value) {
            return Float.isFinite(value) && value > 0f;
        }
    }
}
