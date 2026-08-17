package com.spacesim.combat.acceptance;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.ShipProtectionCatalog.CompartmentDamageDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog.HeavyImpactModel;
import com.spacesim.content.ship.ShipProtectionCatalog.HullDamageLayout;
import com.spacesim.content.ship.ShipProtectionCatalog.MountDamageDefinition;
import com.spacesim.content.ship.ShipProtectionCatalogLoader;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalogLoader;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalogLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Immutable production-valid/content-provisional Stage-17.5I acceptance content bundle.
 *
 * <p>The bundle deliberately reuses the ordinary engineering, ammunition, launcher and protection
 * loaders. It adds no combat-only ship stats and no alternate fitting or damage model. The combined
 * semantic fingerprint lets the deterministic acceptance runner identify the exact test vocabulary
 * used for one result without promoting that vocabulary to Stage-22 canon.</p>
 */
public final class Stage175ICombatTestContentPack {
    /** Engineering hull/module/fit resource. */
    public static final String ENGINEERING_RESOURCE =
            "data/content/stage17_5i-combat-test-engineering-v1.json";
    /** Physical kinetic/guided ammunition resource. */
    public static final String AMMUNITION_RESOURCE =
            "data/content/stage17_5i-combat-test-ammunition-v1.json";
    /** Launcher timing/geometry resource. */
    public static final String LAUNCHER_RESOURCE =
            "data/content/stage17_5i-combat-test-launchers-v1.json";
    /** Local heavy-impact/compartment/subsystem damage resource. */
    public static final String PROTECTION_RESOURCE =
            "data/content/stage17_5i-combat-test-protection-v1.json";
    /** Five-fleet/matchup/variation manifest resource. */
    public static final String MANIFEST_RESOURCE =
            "data/content/stage17_5i-combat-test-manifest-v1.json";

    private final ShipEngineeringCatalog engineering;
    private final WeaponAmmunitionCatalog ammunition;
    private final WeaponLauncherCatalog launchers;
    private final ShipProtectionCatalog protection;
    private final Stage175ICombatTestManifest manifest;
    private final String fingerprint;

    private Stage175ICombatTestContentPack(
            ShipEngineeringCatalog engineering,
            WeaponAmmunitionCatalog ammunition,
            WeaponLauncherCatalog launchers,
            ShipProtectionCatalog protection,
            Stage175ICombatTestManifest manifest) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.ammunition = Objects.requireNonNull(ammunition, "ammunition");
        this.launchers = Objects.requireNonNull(launchers, "launchers");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        validateCrossReferences();
        this.fingerprint = computeFingerprint();
    }

    /**
     * Loads the built-in Stage-17.5I pack through the ordinary production loaders.
     *
     * @return immutable validated acceptance pack
     */
    public static Stage175ICombatTestContentPack loadDefault() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.parse(read(ENGINEERING_RESOURCE));
        WeaponAmmunitionCatalog ammunition = WeaponAmmunitionCatalogLoader.parse(
                read(AMMUNITION_RESOURCE), engineering);
        WeaponLauncherCatalog launchers = WeaponLauncherCatalogLoader.parse(
                read(LAUNCHER_RESOURCE), engineering);
        ShipProtectionCatalog protection = ShipProtectionCatalogLoader.parse(
                read(PROTECTION_RESOURCE), engineering);
        Stage175ICombatTestManifest manifest = Stage175ICombatTestManifestLoader.parse(
                read(MANIFEST_RESOURCE), engineering);
        return new Stage175ICombatTestContentPack(
                engineering, ammunition, launchers, protection, manifest);
    }

    /** @return ordinary production engineering catalog */
    public ShipEngineeringCatalog engineering() {
        return engineering;
    }

    /** @return ordinary production physical-ammunition catalog */
    public WeaponAmmunitionCatalog ammunition() {
        return ammunition;
    }

    /** @return ordinary production launcher-profile catalog */
    public WeaponLauncherCatalog launchers() {
        return launchers;
    }

    /** @return ordinary production protection/damage catalog */
    public ShipProtectionCatalog protection() {
        return protection;
    }

    /** @return data-driven representative fleet/scenario manifest */
    public Stage175ICombatTestManifest manifest() {
        return manifest;
    }

    /** @return lowercase SHA-256 fingerprint of all acceptance content semantics */
    public String fingerprint() {
        return fingerprint;
    }

    private void validateCrossReferences() {
        for (Stage175ICombatTestManifest.FleetDefinition fleet : manifest.fleets()) {
            for (Stage175ICombatTestManifest.ShipEntry row : fleet.ships()) {
                ShipEngineeringCatalog.DemonstratorFitDefinition fit =
                        engineering.findDemonstratorFit(row.fitId());
                if (fit == null) {
                    throw new IllegalArgumentException("Manifest lost engineering fit: " + row.fitId());
                }
                if (protection.findHullDamageLayout(fit.hullId()) == null) {
                    throw new IllegalArgumentException(
                            "Representative fit hull has no production damage layout: " + row.fitId());
                }
            }
        }
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(16_384);
        canonical.append("engineering|").append(engineering.getFingerprint()).append('\n');
        canonical.append("ammunition|").append(ammunition.getFingerprint()).append('\n');
        canonical.append("launchers|").append(launchers.getFingerprint()).append('\n');
        canonical.append("manifest|").append(manifest.fingerprint()).append('\n');
        canonical.append("protection-schema|").append(protection.getSchemaVersion()).append('\n');
        for (HeavyImpactModel model : protection.getHeavyImpactModels()) {
            canonical.append("impact|").append(model.responseSurfaceId()).append('|')
                    .append(bits(model.specificAbsorptionJPerKg())).append('|')
                    .append(bits(model.spallMassFraction())).append('|')
                    .append(bits(model.spallEnergyFraction())).append('|')
                    .append(bits(model.ricochetCriticalAngleRad())).append('|')
                    .append(bits(model.ricochetRetainedEnergyFraction())).append('\n');
        }
        for (HullDamageLayout layout : protection.getHullDamageLayouts()) {
            canonical.append("layout|").append(layout.hullId()).append('\n');
            for (CompartmentDamageDefinition compartment : layout.compartments()) {
                canonical.append("compartment|").append(compartment.compartmentId()).append('|')
                        .append(bits(compartment.structuralDamageCapacityJ())).append('|')
                        .append(bits(compartment.subsystemCouplingFraction())).append('\n');
            }
            for (MountDamageDefinition mount : layout.mounts()) {
                canonical.append("mount|").append(mount.mountId()).append('|')
                        .append(mount.compartmentId()).append('|')
                        .append(bits(mount.subsystemDamageCapacityJ())).append('\n');
            }
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }

    private static String bits(double value) {
        return Long.toUnsignedString(Double.doubleToLongBits(value), 16);
    }

    private static String read(String resource) {
        ClassLoader loader = Stage175ICombatTestContentPack.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-17.5I resource: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-17.5I resource: " + resource, exception);
        }
    }
}
