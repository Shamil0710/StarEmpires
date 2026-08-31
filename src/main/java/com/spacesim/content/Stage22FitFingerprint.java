package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Computes the Stage-22 presentation fingerprint of one exact fitted engineering configuration.
 *
 * <p>The fingerprint is downstream of {@link ShipEngineeringCatalog}: it hashes the catalog semantic
 * fingerprint together with the exact fit ID, hull ID and canonical mount-to-module assignments.
 * It is therefore suitable for visual-authoring invalidation, but never becomes simulation identity,
 * fitting authority, combat state or a mutable gameplay owner.</p>
 */
public final class Stage22FitFingerprint {
    private Stage22FitFingerprint() {
        throw new AssertionError("utility class");
    }

    /**
     * Computes a lowercase SHA-256 fingerprint for one fit already registered in the supplied catalog.
     *
     * @param engineering accepted immutable engineering catalog
     * @param fitId registered demonstrator fit ID
     * @return deterministic lowercase SHA-256 semantic fingerprint
     */
    public static String compute(ShipEngineeringCatalog engineering, String fitId) {
        ShipEngineeringCatalog catalog = Objects.requireNonNull(engineering, "engineering");
        String checkedId = requireText(fitId, "fitId");
        DemonstratorFitDefinition fit = catalog.findDemonstratorFit(checkedId);
        if (fit == null) {
            throw new IllegalArgumentException("Unknown engineering fit: " + checkedId);
        }
        return compute(catalog, fit);
    }

    /**
     * Computes a fingerprint for an exact registered fit definition.
     *
     * @param engineering accepted immutable engineering catalog
     * @param fit exact registered fit definition
     * @return deterministic lowercase SHA-256 semantic fingerprint
     */
    public static String compute(ShipEngineeringCatalog engineering, DemonstratorFitDefinition fit) {
        ShipEngineeringCatalog catalog = Objects.requireNonNull(engineering, "engineering");
        DemonstratorFitDefinition checked = Objects.requireNonNull(fit, "fit");
        DemonstratorFitDefinition registered = catalog.findDemonstratorFit(checked.id());
        if (registered == null || !registered.equals(checked)) {
            throw new IllegalArgumentException("Fit is not the exact registered engineering definition: " + checked.id());
        }

        StringBuilder canonical = new StringBuilder(512);
        canonical.append("engineering=").append(catalog.getFingerprint()).append('\n');
        canonical.append("fit=").append(checked.id()).append('|').append(checked.hullId()).append('\n');
        List<InstalledModuleDefinition> installed = new ArrayList<>(checked.installedModules());
        installed.sort(Comparator.comparing(InstalledModuleDefinition::mountId)
                .thenComparing(InstalledModuleDefinition::moduleId));
        for (InstalledModuleDefinition module : installed) {
            canonical.append(module.mountId()).append('=').append(module.moduleId()).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return checked;
    }
}
