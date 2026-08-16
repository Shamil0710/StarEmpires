package com.spacesim.content.weapon;

import com.spacesim.ship.WeaponDefinition.Family;

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
 * Immutable Stage-17.5E capability-specific launcher profile catalog linked to engineering modules.
 *
 * <p>Profiles contain only weapon-family operating geometry/timing omitted by the generic module
 * schema. Common module mass, power, energy, heat, recoil and fitted identity remain authoritative
 * in {@code ShipEngineeringCatalog} and are revalidated by the loader/adapter.</p>
 */
public final class WeaponLauncherCatalog {
    private final int schemaVersion;
    private final int migrationVersion;
    private final List<LauncherProfile> profiles;
    private final Map<String, LauncherProfile> byModuleId;
    private final String fingerprint;

    WeaponLauncherCatalog(int schemaVersion, int migrationVersion, List<LauncherProfile> profiles) {
        this.schemaVersion = schemaVersion;
        this.migrationVersion = migrationVersion;
        Objects.requireNonNull(profiles, "profiles");
        List<LauncherProfile> copy = new ArrayList<>(profiles);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("profiles must not contain null");
        }
        copy.sort(Comparator.comparing(LauncherProfile::moduleId));
        this.profiles = List.copyOf(copy);
        LinkedHashMap<String, LauncherProfile> index = new LinkedHashMap<>();
        for (LauncherProfile profile : this.profiles) {
            if (index.put(profile.moduleId(), profile) != null) {
                throw new IllegalArgumentException("duplicate launcher profile for module: " + profile.moduleId());
            }
        }
        this.byModuleId = Map.copyOf(index);
        this.fingerprint = computeFingerprint();
    }

    /** @return launcher-profile schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return explicit launcher-profile migration version */
    public int getMigrationVersion() {
        return migrationVersion;
    }

    /** @return immutable deterministic launcher profiles */
    public List<LauncherProfile> getProfiles() {
        return profiles;
    }

    /** @return lowercase SHA-256 semantic fingerprint */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Finds the capability-specific profile linked to one engineering module.
     *
     * @param moduleId stable engineering module content ID
     * @return profile or {@code null}
     */
    public LauncherProfile findByModuleId(String moduleId) {
        return byModuleId.get(moduleId);
    }

    /**
     * Weapon-family operating profile linked to one physical engineering module.
     *
     * @param moduleId stable engineering module content ID
     * @param family physical weapon family
     * @param ammunitionInterfaceId concrete module-local ammunition interface ID
     * @param ammunitionAmountPerShot interface-native quantity consumed per shot
     * @param cycleTimeSeconds minimum physical launcher cycle time
     * @param supportChannelCount simultaneous fire-control/guidance support channels
     * @param pointingJitterRad one-sigma launcher pointing jitter
     * @param maxProjectileMassKg maximum supported launched body mass
     * @param maxProjectileLengthM maximum supported body length
     * @param maxProjectileDiameterM maximum supported body diameter
     */
    public record LauncherProfile(
            String moduleId,
            Family family,
            String ammunitionInterfaceId,
            double ammunitionAmountPerShot,
            double cycleTimeSeconds,
            int supportChannelCount,
            double pointingJitterRad,
            double maxProjectileMassKg,
            double maxProjectileLengthM,
            double maxProjectileDiameterM) {
        /**
         * Validates one immutable linked launcher profile.
         *
         * @param moduleId stable engineering module content ID
         * @param family physical weapon family
         * @param ammunitionInterfaceId concrete module-local ammunition interface ID
         * @param ammunitionAmountPerShot interface-native quantity consumed per shot
         * @param cycleTimeSeconds minimum physical launcher cycle time
         * @param supportChannelCount simultaneous fire-control/guidance support channels
         * @param pointingJitterRad one-sigma launcher pointing jitter
         * @param maxProjectileMassKg maximum supported launched body mass
         * @param maxProjectileLengthM maximum supported body length
         * @param maxProjectileDiameterM maximum supported body diameter
         */
        public LauncherProfile {
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(family, "family");
            requireNonBlank(ammunitionInterfaceId, "ammunitionInterfaceId");
            requirePositiveFinite(ammunitionAmountPerShot, "ammunitionAmountPerShot");
            requirePositiveFinite(cycleTimeSeconds, "cycleTimeSeconds");
            if (supportChannelCount <= 0) {
                throw new IllegalArgumentException("supportChannelCount must be positive");
            }
            requireNonNegativeFinite(pointingJitterRad, "pointingJitterRad");
            requirePositiveFinite(maxProjectileMassKg, "maxProjectileMassKg");
            requirePositiveFinite(maxProjectileLengthM, "maxProjectileLengthM");
            requirePositiveFinite(maxProjectileDiameterM, "maxProjectileDiameterM");
        }
    }

    private String computeFingerprint() {
        StringBuilder out = new StringBuilder(2048);
        out.append("schema|").append(schemaVersion).append('|').append(migrationVersion).append('\n');
        for (LauncherProfile profile : profiles) {
            out.append("launcher|").append(profile.moduleId()).append('|')
                    .append(profile.family()).append('|')
                    .append(profile.ammunitionInterfaceId()).append('|')
                    .append(bits(profile.ammunitionAmountPerShot())).append('|')
                    .append(bits(profile.cycleTimeSeconds())).append('|')
                    .append(profile.supportChannelCount()).append('|')
                    .append(bits(profile.pointingJitterRad())).append('|')
                    .append(bits(profile.maxProjectileMassKg())).append('|')
                    .append(bits(profile.maxProjectileLengthM())).append('|')
                    .append(bits(profile.maxProjectileDiameterM())).append('\n');
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
