package com.spacesim.content.weapon;

import com.spacesim.ship.SignatureState;
import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import com.spacesim.ship.WeaponDefinition.KineticRound;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable versioned Stage-17.5E physical ammunition content catalog.
 *
 * <p>The catalog defines properties of ammunition bodies themselves. Launcher hardware remains in
 * the existing ship engineering module catalog, while quantity and carried mass remain in central
 * {@code ConsumableState}. This catalog therefore cannot create ammunition or duplicate magazine
 * inventory.</p>
 */
public final class WeaponAmmunitionCatalog {
    private final int schemaVersion;
    private final int migrationVersion;
    private final List<KineticAmmunitionDefinition> kineticAmmunition;
    private final List<GuidedAmmunitionDefinition> guidedAmmunition;
    private final Map<String, KineticAmmunitionDefinition> kineticById;
    private final Map<String, GuidedAmmunitionDefinition> guidedById;
    private final String fingerprint;

    WeaponAmmunitionCatalog(
            int schemaVersion,
            int migrationVersion,
            List<KineticAmmunitionDefinition> kineticAmmunition,
            List<GuidedAmmunitionDefinition> guidedAmmunition) {
        this.schemaVersion = schemaVersion;
        this.migrationVersion = migrationVersion;
        this.kineticAmmunition = sortedCopy(kineticAmmunition, Comparator.comparing(KineticAmmunitionDefinition::id));
        this.guidedAmmunition = sortedCopy(guidedAmmunition, Comparator.comparing(GuidedAmmunitionDefinition::id));
        this.kineticById = index(this.kineticAmmunition, KineticAmmunitionDefinition::id);
        this.guidedById = index(this.guidedAmmunition, GuidedAmmunitionDefinition::id);
        for (String id : kineticById.keySet()) {
            if (guidedById.containsKey(id)) {
                throw new IllegalArgumentException("duplicate ammunition content ID across families: " + id);
            }
        }
        this.fingerprint = computeFingerprint();
    }

    /** @return supported ammunition schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return explicit ammunition migration contract version */
    public int getMigrationVersion() {
        return migrationVersion;
    }

    /** @return deterministic immutable kinetic ammunition definitions */
    public List<KineticAmmunitionDefinition> getKineticAmmunition() {
        return kineticAmmunition;
    }

    /** @return deterministic immutable guided ammunition definitions */
    public List<GuidedAmmunitionDefinition> getGuidedAmmunition() {
        return guidedAmmunition;
    }

    /** @return lowercase SHA-256 semantic fingerprint */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Finds kinetic ammunition by stable content ID.
     *
     * @param id ammunition content ID
     * @return definition or {@code null}
     */
    public KineticAmmunitionDefinition findKinetic(String id) {
        return kineticById.get(id);
    }

    /**
     * Finds guided ammunition by stable content ID.
     *
     * @param id ammunition content ID
     * @return definition or {@code null}
     */
    public GuidedAmmunitionDefinition findGuided(String id) {
        return guidedById.get(id);
    }

    /** Authored tactical purpose of one guided ammunition body. */
    public enum GuidedEngagementRole {
        /** Guided body is intended to engage ship/large-object targets. */ STRIKE,
        /** Guided body is intended to engage incoming ordnance bodies. */ INTERCEPTOR
    }

    /**
     * Physical kinetic ammunition body independent from launcher muzzle hardware.
     *
     * @param id stable ammunition content ID
     * @param materialId stable engineering material content ID
     * @param shape physical projectile shape
     * @param lengthM projectile length in meters
     * @param diameterM projectile diameter in meters
     * @param massKg projectile mass in kilograms
     */
    public record KineticAmmunitionDefinition(
            String id,
            String materialId,
            ProjectileShape shape,
            double lengthM,
            double diameterM,
            double massKg) {
        /**
         * Validates one immutable kinetic ammunition definition.
         *
         * @param id stable ammunition content ID
         * @param materialId stable engineering material content ID
         * @param shape physical projectile shape
         * @param lengthM projectile length in meters
         * @param diameterM projectile diameter in meters
         * @param massKg projectile mass in kilograms
         */
        public KineticAmmunitionDefinition {
            requireNonBlank(id, "id");
            requireNonBlank(materialId, "materialId");
            Objects.requireNonNull(shape, "shape");
            requirePositiveFinite(lengthM, "lengthM");
            requirePositiveFinite(diameterM, "diameterM");
            requirePositiveFinite(massKg, "massKg");
        }

        /**
         * Combines this physical body with fitted launcher muzzle velocity.
         *
         * @param muzzleVelocityMps fitted launcher's muzzle-relative velocity
         * @return runtime kinetic round
         */
        public KineticRound toRuntimeRound(double muzzleVelocityMps) {
            return new KineticRound(id, materialId, shape, lengthM, diameterM, massKg, muzzleVelocityMps);
        }
    }

    /**
     * Authored physical observability of one guided ammunition body.
     *
     * <p>These are source strengths used by the ordinary production sensor equations. They are not
     * detection ranges or target-priority bonuses. Dynamic burn/jammer state may later add to this
     * static body signature but cannot replace these content-authored physical terms.</p>
     *
     * @param thermalRadiantPowerW passive thermal radiant source power
     * @param enginePlumeRadiantPowerW authored powered-plume radiant source power
     * @param radarCrossSectionM2 active-radar cross section
     * @param reflectedOpticalPowerW passive reflected-optical source power
     * @param activeRadioEmissionPowerW active radio/seeker emission source power
     * @param jammerEmissionPowerW authored jammer emission source power
     */
    public record GuidedSignatureDefinition(
            double thermalRadiantPowerW,
            double enginePlumeRadiantPowerW,
            double radarCrossSectionM2,
            double reflectedOpticalPowerW,
            double activeRadioEmissionPowerW,
            double jammerEmissionPowerW) {
        /**
         * Validates one non-negative physical signature definition.
         *
         * @param thermalRadiantPowerW passive thermal radiant source power
         * @param enginePlumeRadiantPowerW powered-plume radiant source power
         * @param radarCrossSectionM2 active-radar cross section
         * @param reflectedOpticalPowerW passive reflected-optical source power
         * @param activeRadioEmissionPowerW active radio/seeker emission source power
         * @param jammerEmissionPowerW jammer emission source power
         */
        public GuidedSignatureDefinition {
            requireNonNegativeFinite(thermalRadiantPowerW, "thermalRadiantPowerW");
            requireNonNegativeFinite(enginePlumeRadiantPowerW, "enginePlumeRadiantPowerW");
            requireNonNegativeFinite(radarCrossSectionM2, "radarCrossSectionM2");
            requireNonNegativeFinite(reflectedOpticalPowerW, "reflectedOpticalPowerW");
            requireNonNegativeFinite(activeRadioEmissionPowerW, "activeRadioEmissionPowerW");
            requireNonNegativeFinite(jammerEmissionPowerW, "jammerEmissionPowerW");
        }

        /** @return legacy-safe zero signature for schema-v1 content that predates ordnance sensing */
        public static GuidedSignatureDefinition zero() {
            return new GuidedSignatureDefinition(0d, 0d, 0d, 0d, 0d, 0d);
        }

        /** @return ordinary production sensor signature state */
        public SignatureState toRuntimeSignature() {
            return new SignatureState(
                    thermalRadiantPowerW,
                    enginePlumeRadiantPowerW,
                    radarCrossSectionM2,
                    reflectedOpticalPowerW,
                    activeRadioEmissionPowerW,
                    jammerEmissionPowerW);
        }
    }

    /**
     * Physical guided ammunition body and self-propulsion/seeker content.
     *
     * @param id stable ammunition content ID
     * @param materialId stable engineering material content ID of residual missile body
     * @param shape physical residual-body shape
     * @param engagementRole authored tactical purpose used for explicit strike/interceptor routing
     * @param signature authored physical sensor signature source strengths
     * @param lengthM body length in meters
     * @param diameterM body diameter in meters
     * @param impactPayloadId optional future Stage-17.5F warhead/impact payload content seam
     * @param seekerId stable seeker/content ID
     * @param dryMassKg dry body mass in kilograms
     * @param propellantMassKg onboard propellant mass in kilograms
     * @param thrustN guided-body thrust in newtons
     * @param exhaustVelocityMps effective exhaust velocity in meters per second
     * @param burnTimeSeconds maximum powered burn duration in seconds
     * @param seekerAngularSigmaRad one-sigma seeker angular uncertainty
     * @param terminalReserveMps delta-v reserved for terminal maneuver policy
     */
    public record GuidedAmmunitionDefinition(
            String id,
            String materialId,
            ProjectileShape shape,
            GuidedEngagementRole engagementRole,
            GuidedSignatureDefinition signature,
            double lengthM,
            double diameterM,
            String impactPayloadId,
            String seekerId,
            double dryMassKg,
            double propellantMassKg,
            double thrustN,
            double exhaustVelocityMps,
            double burnTimeSeconds,
            double seekerAngularSigmaRad,
            double terminalReserveMps) {
        /**
         * Validates one immutable guided ammunition definition.
         *
         * @param id stable ammunition content ID
         * @param materialId stable engineering material content ID
         * @param shape physical residual-body shape
         * @param engagementRole authored tactical purpose
         * @param signature authored physical sensor signature source strengths
         * @param lengthM body length in meters
         * @param diameterM body diameter in meters
         * @param impactPayloadId optional future warhead/impact payload content seam
         * @param seekerId stable seeker/content ID
         * @param dryMassKg dry body mass in kilograms
         * @param propellantMassKg onboard propellant mass in kilograms
         * @param thrustN guided-body thrust in newtons
         * @param exhaustVelocityMps effective exhaust velocity in meters per second
         * @param burnTimeSeconds maximum powered burn duration in seconds
         * @param seekerAngularSigmaRad one-sigma seeker angular uncertainty
         * @param terminalReserveMps delta-v reserved for terminal maneuver policy
         */
        public GuidedAmmunitionDefinition {
            requireNonBlank(id, "id");
            requireNonBlank(materialId, "materialId");
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(engagementRole, "engagementRole");
            Objects.requireNonNull(signature, "signature");
            requirePositiveFinite(lengthM, "lengthM");
            requirePositiveFinite(diameterM, "diameterM");
            if (impactPayloadId != null && impactPayloadId.isBlank()) {
                throw new IllegalArgumentException("impactPayloadId must be null or non-blank");
            }
            requireNonBlank(seekerId, "seekerId");
            new GuidedWeapon(
                    id,
                    seekerId,
                    dryMassKg,
                    propellantMassKg,
                    thrustN,
                    exhaustVelocityMps,
                    burnTimeSeconds,
                    seekerAngularSigmaRad,
                    terminalReserveMps);
        }

        /** @return immutable runtime propulsion/seeker definition */
        public GuidedWeapon toRuntimeWeapon() {
            return new GuidedWeapon(
                    id,
                    seekerId,
                    dryMassKg,
                    propellantMassKg,
                    thrustN,
                    exhaustVelocityMps,
                    burnTimeSeconds,
                    seekerAngularSigmaRad,
                    terminalReserveMps);
        }

        /** @return launch wet mass in kilograms */
        public double wetMassKg() {
            return dryMassKg + propellantMassKg;
        }
    }

    private String computeFingerprint() {
        StringBuilder out = new StringBuilder(4096);
        out.append("schema|").append(schemaVersion).append('|').append(migrationVersion).append('\n');
        for (KineticAmmunitionDefinition value : kineticAmmunition) {
            out.append("kinetic|").append(value.id()).append('|')
                    .append(value.materialId()).append('|')
                    .append(value.shape()).append('|')
                    .append(bits(value.lengthM())).append('|')
                    .append(bits(value.diameterM())).append('|')
                    .append(bits(value.massKg())).append('\n');
        }
        for (GuidedAmmunitionDefinition value : guidedAmmunition) {
            GuidedSignatureDefinition signature = value.signature();
            out.append("guided|").append(value.id()).append('|')
                    .append(value.materialId()).append('|')
                    .append(value.shape()).append('|')
                    .append(value.engagementRole()).append('|')
                    .append(bits(signature.thermalRadiantPowerW())).append('|')
                    .append(bits(signature.enginePlumeRadiantPowerW())).append('|')
                    .append(bits(signature.radarCrossSectionM2())).append('|')
                    .append(bits(signature.reflectedOpticalPowerW())).append('|')
                    .append(bits(signature.activeRadioEmissionPowerW())).append('|')
                    .append(bits(signature.jammerEmissionPowerW())).append('|')
                    .append(bits(value.lengthM())).append('|')
                    .append(bits(value.diameterM())).append('|')
                    .append(value.impactPayloadId() == null ? "~" : value.impactPayloadId()).append('|')
                    .append(value.seekerId()).append('|')
                    .append(bits(value.dryMassKg())).append('|')
                    .append(bits(value.propellantMassKg())).append('|')
                    .append(bits(value.thrustN())).append('|')
                    .append(bits(value.exhaustVelocityMps())).append('|')
                    .append(bits(value.burnTimeSeconds())).append('|')
                    .append(bits(value.seekerAngularSigmaRad())).append('|')
                    .append(bits(value.terminalReserveMps())).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(out.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static long bits(double value) {
        return Double.doubleToLongBits(value == 0d ? 0d : value);
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<T> comparator) {
        Objects.requireNonNull(values, "values");
        List<T> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("catalog lists must not contain null");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static <T> Map<String, T> index(List<T> values, java.util.function.Function<T, String> idFunction) {
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = idFunction.apply(value);
            if (result.put(id, value) != null) {
                throw new IllegalArgumentException("duplicate ammunition content ID: " + id);
            }
        }
        return Map.copyOf(result);
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
