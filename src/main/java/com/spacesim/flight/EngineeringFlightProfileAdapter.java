package com.spacesim.flight;

import com.spacesim.ship.ShipEngineeringState.DerivedShipState;

/**
 * Stage-17.5B bridge from the production derived ship state into the existing shared
 * {@link FlightDynamics} integration controller.
 *
 * <p>All carried physical mass (cargo, ammunition, stores, mission payload and reaction mass) is
 * included in the resulting profile. The temporary braking thrust equals available translational
 * thrust for a flip-and-burn maneuver; directional/throttle/thermal thrust envelopes are completed
 * in Stage 17.5C without changing the common flight integrator.</p>
 */
public final class EngineeringFlightProfileAdapter {
    private EngineeringFlightProfileAdapter() {
        throw new AssertionError("utility class");
    }

    /**
     * Converts production engineering state to the common flight profile.
     *
     * @param state authoritative derived ship state
     * @param speedCap current assisted speed cap used by the Stage-14 integrator
     * @return common flight profile whose acceleration is thrust divided by total physical mass
     */
    public static FlightDynamics.Profile profile(DerivedShipState state, float speedCap) {
        if (state == null) {
            throw new IllegalArgumentException("Derived ship state must not be null");
        }
        if (!Float.isFinite(speedCap) || speedCap <= 0f) {
            throw new IllegalArgumentException("Flight speed cap must be finite and positive");
        }
        float installedDryMass = toPositiveFloat(state.installedDryMassKg(), "installedDryMassKg");
        float carriedMass = toNonNegativeFloat(state.carriedMassKg(), "carriedMassKg");
        float totalMass = toPositiveFloat(state.totalMassKg(), "totalMassKg");
        float thrust = toPositiveFloat(state.availableThrustN(), "availableThrustN");
        return new FlightDynamics.Profile(
                installedDryMass,
                carriedMass,
                totalMass,
                thrust,
                thrust,
                speedCap,
                thrust / totalMass,
                thrust / totalMass);
    }

    private static float toPositiveFloat(double value, String field) {
        float result = toFiniteFloat(value, field);
        if (result <= 0f) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return result;
    }

    private static float toNonNegativeFloat(double value, String field) {
        float result = toFiniteFloat(value, field);
        if (result < 0f) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return result;
    }

    private static float toFiniteFloat(double value, String field) {
        if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE) {
            throw new IllegalArgumentException(field + " cannot be represented by FlightDynamics float profile");
        }
        float result = (float) value;
        if (!Float.isFinite(result)) {
            throw new IllegalArgumentException(field + " cannot be represented by FlightDynamics float profile");
        }
        return result;
    }
}
