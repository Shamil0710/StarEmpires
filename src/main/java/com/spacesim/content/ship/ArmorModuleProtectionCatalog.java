package com.spacesim.content.ship;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Data-driven mapping from fitted armor modules to additional external physical protection stacks.
 *
 * <p>The module still pays ordinary fitting mass/volume through {@link ShipEngineeringCatalog}. This
 * catalog only defines the material geometry that mass represents during protection resolution; it
 * does not add a generic resistance multiplier or a second ship-stat budget.</p>
 */
public final class ArmorModuleProtectionCatalog {
    private final int schemaVersion;
    private final List<ArmorProfile> profiles;
    private final Map<String, ArmorProfile> byModuleId;
    private final String fingerprint;

    ArmorModuleProtectionCatalog(int schemaVersion, List<ArmorProfile> profiles) {
        this.schemaVersion = schemaVersion;
        this.profiles = Objects.requireNonNull(profiles, "profiles").stream()
                .sorted(Comparator.comparing(ArmorProfile::moduleId))
                .toList();
        LinkedHashMap<String, ArmorProfile> index = new LinkedHashMap<>();
        for (ArmorProfile profile : this.profiles) {
            if (index.put(profile.moduleId(), profile) != null) {
                throw new IllegalArgumentException("Duplicate armor profile: " + profile.moduleId());
            }
        }
        this.byModuleId = Map.copyOf(index);
        this.fingerprint = fingerprint(this.profiles);
    }

    /** @return armor-profile schema version */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable fitted-armor profiles */
    public List<ArmorProfile> profiles() {
        return profiles;
    }

    /**
     * Finds a fitted armor response profile by ordinary module content ID.
     *
     * @param moduleId ordinary engineering module ID
     * @return profile or {@code null}
     */
    public ArmorProfile findByModuleId(String moduleId) {
        return byModuleId.get(moduleId);
    }

    /** @return lowercase SHA-256 semantic fingerprint */
    public String fingerprint() {
        return fingerprint;
    }

    /**
     * One fitted armor module's additional outside-to-inside protection layer group.
     *
     * @param moduleId ordinary {@code ARMOR_PROTECTION} module ID
     * @param externalProtectionStackId existing engineering protection-stack ID applied outside the hull stack
     */
    public record ArmorProfile(String moduleId, String externalProtectionStackId) {
        /**
         * Validates one immutable fitted armor profile.
         *
         * @param moduleId ordinary armor module ID
         * @param externalProtectionStackId engineering stack represented by the module
         */
        public ArmorProfile {
            requireNonBlank(moduleId, "moduleId");
            requireNonBlank(externalProtectionStackId, "externalProtectionStackId");
        }
    }

    private static String fingerprint(List<ArmorProfile> profiles) {
        StringBuilder canonical = new StringBuilder();
        for (ArmorProfile profile : profiles) {
            canonical.append(profile.moduleId()).append('|')
                    .append(profile.externalProtectionStackId()).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}
