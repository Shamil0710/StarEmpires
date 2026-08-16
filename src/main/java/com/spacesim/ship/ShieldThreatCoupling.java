package com.spacesim.ship;

import java.util.Objects;

/**
 * Explicit Stage-17.5F threat-to-field coupling layer.
 *
 * <p>{@link ShieldFieldRuntime} owns finite reserve/power/heat/coverage accounting once energy is
 * coupled into the field. This class owns the threat-family coupling fraction so an uncoupled part
 * of a threat cannot be silently deleted by the shield.</p>
 */
public final class ShieldThreatCoupling {
    /** Threat families relevant to the Stage-17.5F protection boundary. */
    public enum ThreatKind {
        /** Physical projectile kinetic-energy interaction. */ KINETIC,
        /** Directed-energy/beam interaction. */ BEAM,
        /** Guided-weapon terminal interaction after guidance/propulsion resolution. */ GUIDED
    }

    /** Immutable fitted coupling coefficients. */
    public static final class Profile {
        private final double kineticCoupling;
        private final double beamCoupling;
        private final double guidedCoupling;

        /**
         * Creates a bounded coupling profile.
         *
         * @param kineticCoupling kinetic coupling fraction in [0,1]
         * @param beamCoupling beam coupling fraction in [0,1]
         * @param guidedCoupling guided-threat coupling fraction in [0,1]
         */
        public Profile(double kineticCoupling, double beamCoupling, double guidedCoupling) {
            this.kineticCoupling = unitInterval(kineticCoupling, "kineticCoupling");
            this.beamCoupling = unitInterval(beamCoupling, "beamCoupling");
            this.guidedCoupling = unitInterval(guidedCoupling, "guidedCoupling");
        }

        /**
         * Returns the fitted coupling fraction for one threat family.
         *
         * @param kind threat family
         * @return coupling fraction in [0,1]
         */
        public double coupling(ThreatKind kind) {
            return switch (Objects.requireNonNull(kind, "kind")) {
                case KINETIC -> kineticCoupling;
                case BEAM -> beamCoupling;
                case GUIDED -> guidedCoupling;
            };
        }
    }

    /**
     * Combined threat/field result.
     *
     * @param fieldInteraction finite field interaction for the coupled portion
     * @param couplingFraction applied threat-family coupling
     * @param absorbedEnergyJ total raw-threat energy absorbed by the field
     * @param residualEnergyJ total raw-threat energy continuing beyond the field
     */
    public record Interaction(
            ShieldFieldRuntime.Interaction fieldInteraction,
            double couplingFraction,
            double absorbedEnergyJ,
            double residualEnergyJ) { }

    /**
     * Couples one raw threat-energy packet into a finite shield field.
     *
     * @param runtime finite shield runtime
     * @param definition fitted shield definition
     * @param state current shield state
     * @param profile fitted coupling profile
     * @param kind threat family
     * @param incomingEnergyJ raw incoming threat energy
     * @param threatDirectionRad hull-local incoming direction
     * @param interactionSeconds positive interaction duration
     * @return combined coupling/field result
     */
    public Interaction interact(
            ShieldFieldRuntime runtime,
            ShieldFieldRuntime.Definition definition,
            ShieldFieldRuntime.State state,
            Profile profile,
            ThreatKind kind,
            double incomingEnergyJ,
            double threatDirectionRad,
            double interactionSeconds) {
        ShieldFieldRuntime checkedRuntime = Objects.requireNonNull(runtime, "runtime");
        ShieldFieldRuntime.Definition checkedDefinition = Objects.requireNonNull(definition, "definition");
        ShieldFieldRuntime.State checkedState = Objects.requireNonNull(state, "state");
        Profile checkedProfile = Objects.requireNonNull(profile, "profile");
        ThreatKind checkedKind = Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(incomingEnergyJ) || incomingEnergyJ < 0d) {
            throw new IllegalArgumentException("incomingEnergyJ must be finite and non-negative");
        }
        double coupling = checkedProfile.coupling(checkedKind);
        double coupledEnergyJ = incomingEnergyJ * coupling;
        ShieldFieldRuntime.Interaction field = checkedRuntime.interact(
                checkedDefinition,
                checkedState,
                coupledEnergyJ,
                threatDirectionRad,
                interactionSeconds);
        double absorbed = field.absorbedEnergyJ();
        return new Interaction(field, coupling, absorbed, incomingEnergyJ - absorbed);
    }

    private static double unitInterval(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
        return value;
    }
}
