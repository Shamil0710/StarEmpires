package com.spacesim.ship;

import java.util.Objects;

/**
 * Immutable Stage-17.5E physical weapon definitions shared by player and AI fire-control paths.
 *
 * <p>The definitions intentionally contain physical quantities rather than authoritative accuracy,
 * hit-chance or hard-range fields. Kinetic, beam and guided families expose the inputs needed by
 * their downstream physical solvers while launcher state remains a separate runtime concern.</p>
 */
public final class WeaponDefinition {
    private WeaponDefinition() {
        throw new AssertionError("utility namespace");
    }

    /** Broad physical weapon family; the enum itself grants no performance bonus. */
    public enum Family {
        /** Ballistic or electromagnetic launcher producing an independent kinetic body. */ KINETIC,
        /** Directed-energy emitter resolved by beam geometry, dwell and material response. */ BEAM,
        /** Propelled seeker/guidance weapon producing an independent guided body. */ GUIDED
    }

    /** Authoritative projectile shape category used by later material-response lookup. */
    public enum ProjectileShape {
        /** Long dense penetrator. */ DART,
        /** Slender rod-like penetrator. */ ROD,
        /** General shell or compact projectile body. */ SHELL,
        /** Irregular fragment/debris body. */ FRAGMENT
    }

    /**
     * Physical round fired by a kinetic launcher.
     *
     * @param id stable ammunition content ID
     * @param materialId stable engineering material content ID
     * @param shape physical projectile shape category
     * @param lengthM projectile length in meters
     * @param diameterM projectile diameter in meters
     * @param massKg projectile mass in kilograms
     * @param muzzleVelocityMps muzzle-relative velocity in meters per second
     */
    public record KineticRound(
            String id,
            String materialId,
            ProjectileShape shape,
            double lengthM,
            double diameterM,
            double massKg,
            double muzzleVelocityMps) {
        /**
         * Validates one immutable physical kinetic round.
         *
         * @param id stable ammunition content ID
         * @param materialId stable engineering material content ID
         * @param shape physical projectile shape category
         * @param lengthM projectile length in meters
         * @param diameterM projectile diameter in meters
         * @param massKg projectile mass in kilograms
         * @param muzzleVelocityMps muzzle-relative velocity in meters per second
         */
        public KineticRound {
            requireNonBlank(id, "id");
            requireNonBlank(materialId, "materialId");
            Objects.requireNonNull(shape, "shape");
            requirePositiveFinite(lengthM, "lengthM");
            requirePositiveFinite(diameterM, "diameterM");
            requirePositiveFinite(massKg, "massKg");
            requirePositiveFinite(muzzleVelocityMps, "muzzleVelocityMps");
        }

        /** @return launch-frame momentum magnitude in newton-seconds */
        public double momentumNs() {
            return massKg * muzzleVelocityMps;
        }

        /** @return launch-frame kinetic energy in joules */
        public double kineticEnergyJ() {
            return 0.5d * massKg * muzzleVelocityMps * muzzleVelocityMps;
        }
    }

    /**
     * Physical directed-energy emitter definition.
     *
     * @param id stable weapon content ID
     * @param wavelengthM emitted wavelength in meters
     * @param apertureDiameterM clear emitter aperture diameter in meters
     * @param pointingJitterRad one-sigma pointing jitter in radians
     * @param beamPowerW delivered beam power in watts
     * @param electricalPowerDemandW electrical demand while firing in watts
     * @param wasteHeatW local waste heat while firing in watts
     * @param maxContinuousDwellSeconds thermally supported continuous authored dwell before a new duty decision
     */
    public record BeamWeapon(
            String id,
            double wavelengthM,
            double apertureDiameterM,
            double pointingJitterRad,
            double beamPowerW,
            double electricalPowerDemandW,
            double wasteHeatW,
            double maxContinuousDwellSeconds) {
        /**
         * Validates one immutable beam definition without introducing a hard range wall.
         *
         * @param id stable weapon content ID
         * @param wavelengthM emitted wavelength in meters
         * @param apertureDiameterM clear emitter aperture diameter in meters
         * @param pointingJitterRad one-sigma pointing jitter in radians
         * @param beamPowerW delivered beam power in watts
         * @param electricalPowerDemandW electrical demand while firing in watts
         * @param wasteHeatW local waste heat while firing in watts
         * @param maxContinuousDwellSeconds thermally supported continuous authored dwell before a new duty decision
         */
        public BeamWeapon {
            requireNonBlank(id, "id");
            requirePositiveFinite(wavelengthM, "wavelengthM");
            requirePositiveFinite(apertureDiameterM, "apertureDiameterM");
            requireNonNegativeFinite(pointingJitterRad, "pointingJitterRad");
            requirePositiveFinite(beamPowerW, "beamPowerW");
            requirePositiveFinite(electricalPowerDemandW, "electricalPowerDemandW");
            requireNonNegativeFinite(wasteHeatW, "wasteHeatW");
            requirePositiveFinite(maxContinuousDwellSeconds, "maxContinuousDwellSeconds");
        }

        /**
         * Returns the diffraction-limited angular radius using the Airy-disc 1.22 lambda / D seed.
         *
         * @return diffraction angular radius in radians
         */
        public double diffractionAngleRad() {
            return 1.22d * wavelengthM / apertureDiameterM;
        }
    }

    /**
     * Physical guided missile/interceptor definition.
     *
     * @param id stable ammunition content ID
     * @param seekerId stable seeker/content reference
     * @param dryMassKg dry body mass in kilograms
     * @param propellantMassKg carried propellant mass in kilograms
     * @param thrustN main propulsion thrust in newtons
     * @param exhaustVelocityMps effective exhaust velocity in meters per second
     * @param burnTimeSeconds maximum powered burn duration in seconds
     * @param seekerAngularSigmaRad one-sigma seeker angular measurement uncertainty
     * @param terminalReserveMps delta-v reserved by guidance policy for terminal maneuver
     */
    public record GuidedWeapon(
            String id,
            String seekerId,
            double dryMassKg,
            double propellantMassKg,
            double thrustN,
            double exhaustVelocityMps,
            double burnTimeSeconds,
            double seekerAngularSigmaRad,
            double terminalReserveMps) {
        /**
         * Validates one immutable physical guided-weapon definition.
         *
         * @param id stable ammunition content ID
         * @param seekerId stable seeker/content reference
         * @param dryMassKg dry body mass in kilograms
         * @param propellantMassKg carried propellant mass in kilograms
         * @param thrustN main propulsion thrust in newtons
         * @param exhaustVelocityMps effective exhaust velocity in meters per second
         * @param burnTimeSeconds maximum powered burn duration in seconds
         * @param seekerAngularSigmaRad one-sigma seeker angular measurement uncertainty
         * @param terminalReserveMps delta-v reserved by guidance policy for terminal maneuver
         */
        public GuidedWeapon {
            requireNonBlank(id, "id");
            requireNonBlank(seekerId, "seekerId");
            requirePositiveFinite(dryMassKg, "dryMassKg");
            requireNonNegativeFinite(propellantMassKg, "propellantMassKg");
            requirePositiveFinite(thrustN, "thrustN");
            requirePositiveFinite(exhaustVelocityMps, "exhaustVelocityMps");
            requirePositiveFinite(burnTimeSeconds, "burnTimeSeconds");
            requireNonNegativeFinite(seekerAngularSigmaRad, "seekerAngularSigmaRad");
            requireNonNegativeFinite(terminalReserveMps, "terminalReserveMps");
            if (massFlowKgPerS() * burnTimeSeconds > propellantMassKg + 1e-9d) {
                throw new IllegalArgumentException("burn time requires more propellant than carried");
            }
            if (terminalReserveMps > idealDeltaVMps() + 1e-9d) {
                throw new IllegalArgumentException("terminal reserve cannot exceed ideal delta-v");
            }
        }

        /** @return launch wet mass in kilograms */
        public double wetMassKg() {
            return dryMassKg + propellantMassKg;
        }

        /** @return ideal main-drive mass flow in kilograms per second */
        public double massFlowKgPerS() {
            return thrustN / exhaustVelocityMps;
        }

        /** @return ideal rocket-equation delta-v in meters per second */
        public double idealDeltaVMps() {
            if (propellantMassKg == 0d) {
                return 0d;
            }
            return exhaustVelocityMps * Math.log(wetMassKg() / dryMassKg);
        }
    }

    /**
     * Physical launcher/feed definition shared by kinetic and guided ammunition.
     *
     * @param id stable launcher definition ID
     * @param ammunitionInterfaceId module-local physical ammunition interface ID
     * @param ammunitionAmountPerShot authored interface-native amount consumed per launched round
     * @param cycleTimeSeconds minimum physical launch/reload cycle duration
     * @param supportChannelCount simultaneous supported guidance/fire-control channels
     */
    public record Launcher(
            String id,
            String ammunitionInterfaceId,
            double ammunitionAmountPerShot,
            double cycleTimeSeconds,
            int supportChannelCount) {
        /**
         * Validates one immutable launcher/feed definition.
         *
         * @param id stable launcher definition ID
         * @param ammunitionInterfaceId module-local physical ammunition interface ID
         * @param ammunitionAmountPerShot authored interface-native amount consumed per launched round
         * @param cycleTimeSeconds minimum physical launch/reload cycle duration
         * @param supportChannelCount simultaneous supported guidance/fire-control channels
         */
        public Launcher {
            requireNonBlank(id, "id");
            requireNonBlank(ammunitionInterfaceId, "ammunitionInterfaceId");
            requirePositiveFinite(ammunitionAmountPerShot, "ammunitionAmountPerShot");
            requirePositiveFinite(cycleTimeSeconds, "cycleTimeSeconds");
            if (supportChannelCount <= 0) {
                throw new IllegalArgumentException("supportChannelCount must be positive");
            }
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
