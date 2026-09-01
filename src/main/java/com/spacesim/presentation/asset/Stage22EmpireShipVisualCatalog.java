package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22EmpirePackageLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.CompartmentDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;

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
 * Presentation-only M22.3 production visual catalog for the nine Imperial ship families.
 *
 * <p>The catalog deliberately derives world dimensions and external attachment anchors from the
 * accepted engineering catalog. PNG pixels never become hull, hardpoint, collision, fitting or
 * simulation authority. The production pack adds only player-facing base/emissive/damage layers,
 * shared engine VFX, marker silhouettes and deterministic presentation metadata.</p>
 */
public final class Stage22EmpireShipVisualCatalog {
    /** Repository/classpath root for M22.3 production-like Imperial ship sprites. */
    public static final String PRODUCTION_ROOT = "assets/ships/empire/production/";
    /** Shared Imperial idle engine VFX resource. */
    public static final String ENGINE_IDLE = PRODUCTION_ROOT + "common/engine_idle.png";
    /** Shared Imperial full-thrust engine VFX resource. */
    public static final String ENGINE_THRUST = PRODUCTION_ROOT + "common/engine_thrust.png";
    /** Canonical provenance authority for the Imperial ship visual language. */
    public static final String PROVENANCE_REF = "docs/factions/empire_visual_bible.md";

    private Stage22EmpireShipVisualCatalog() {
        throw new AssertionError("utility class");
    }

    /**
     * Builds the immutable production visual catalog from existing package and engineering truth.
     *
     * @return validated nine-family presentation catalog
     */
    public static Catalog loadDefault() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        ArrayList<FamilyVisual> visuals = new ArrayList<>();
        for (Stage22EmpirePackageCatalog.ShipFamilyDefinition family : empire.shipFamilies()) {
            DemonstratorFitDefinition primary = requireFit(engineering, family.primaryFitId());
            HullDefinition hull = requireHull(engineering, primary.hullId());
            String suffix = familySuffix(family.familyId());
            ShipVisualAssetSet assets = new ShipVisualAssetSet(
                    familyPath(suffix, "base"),
                    familyPath(suffix, "emissive"),
                    familyPath(suffix, "damage"),
                    ENGINE_IDLE,
                    ENGINE_THRUST);
            ShipSpriteSpec sprite = buildSpriteSpec(family, primary, hull, engineering, assets);
            visuals.add(new FamilyVisual(
                    family.familyId(),
                    family.roleId(),
                    family.primaryFitId(),
                    family.refitFitId(),
                    "assets/ships/empire/" + suffix + "_silhouette.svg",
                    assets,
                    sprite,
                    PROVENANCE_REF));
        }
        visuals.sort(Comparator.comparing(FamilyVisual::familyId));
        return new Catalog(engineering.getFingerprint(), visuals);
    }

    private static ShipSpriteSpec buildSpriteSpec(
            Stage22EmpirePackageCatalog.ShipFamilyDefinition family,
            DemonstratorFitDefinition fit,
            HullDefinition hull,
            ShipEngineeringCatalog engineering,
            ShipVisualAssetSet assets) {
        Map<String, ModuleDefinition> installedByMount = new LinkedHashMap<>();
        fit.installedModules().forEach(installed -> installedByMount.put(
                installed.mountId(),
                Objects.requireNonNull(engineering.findModule(installed.moduleId()), installed.moduleId())));

        ArrayList<VisualHardpoint> anchors = new ArrayList<>();
        for (HardpointDefinition hardpoint : hull.hardpoints()) {
            ModuleDefinition installed = installedByMount.get(hardpoint.id());
            VisualHardpointType type = installed == null
                    ? VisualHardpointType.UTILITY
                    : visualType(installed.family());
            anchors.add(new VisualHardpoint(
                    "engineering_" + hardpoint.id(),
                    type,
                    normalizedForward(hardpoint.positionM().yM(), hull.boundingDimensionsM().lengthM()),
                    normalizedTransverse(hardpoint.positionM().xM(), hull.boundingDimensionsM().widthM()),
                    (float) Math.toDegrees(hardpoint.arc().azimuthCenterRad() - Math.PI * 0.5d)));
        }
        anchors.add(new VisualHardpoint("engine_main", VisualHardpointType.ENGINE, 0.06f, 0.50f, 180f));
        for (CompartmentDefinition compartment : hull.compartments()) {
            anchors.add(new VisualHardpoint(
                    "service_" + compartment.id(),
                    VisualHardpointType.UTILITY,
                    normalizedForward(compartment.centerM().yM(), hull.boundingDimensionsM().lengthM()),
                    normalizedTransverse(compartment.centerM().xM(), hull.boundingDimensionsM().widthM()),
                    0f));
        }
        anchors.sort(Comparator.comparing(VisualHardpoint::id));

        float length = checkedFloat(hull.boundingDimensionsM().lengthM(), "hull length");
        float width = checkedFloat(hull.boundingDimensionsM().widthM(), "hull width");
        return new ShipSpriteSpec(
                "ship_visual.empire." + familySuffix(family.familyId()) + ".production_v1",
                assets.baseTexturePath(),
                assets.emissiveTexturePath(),
                length,
                width,
                0.5f,
                0.5f,
                length,
                width,
                SourceFacing.RIGHT,
                anchors);
    }

    private static VisualHardpointType visualType(ModuleFamily family) {
        return switch (family) {
            case WEAPON_AMMUNITION -> VisualHardpointType.WEAPON;
            case MAIN_DRIVE, MANEUVER_THRUSTERS -> VisualHardpointType.ENGINE;
            default -> VisualHardpointType.UTILITY;
        };
    }

    private static float normalizedForward(double yM, double lengthM) {
        return clamp01((float) (0.5d + yM / lengthM));
    }

    private static float normalizedTransverse(double xM, double widthM) {
        return clamp01((float) (0.5d + xM / widthM));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float checkedFloat(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d || value > Float.MAX_VALUE) {
            throw new IllegalArgumentException(label + " must be finite, positive and representable");
        }
        return (float) value;
    }

    private static String familyPath(String suffix, String layer) {
        return PRODUCTION_ROOT + suffix + "/" + suffix + "_" + layer + ".png";
    }

    private static String familySuffix(String familyId) {
        String prefix = "ship_family.empire.";
        if (!familyId.startsWith(prefix) || familyId.length() == prefix.length()) {
            throw new IllegalArgumentException("Unexpected Empire ship family ID: " + familyId);
        }
        return familyId.substring(prefix.length());
    }

    private static DemonstratorFitDefinition requireFit(ShipEngineeringCatalog engineering, String id) {
        DemonstratorFitDefinition fit = engineering.findDemonstratorFit(id);
        if (fit == null) {
            throw new IllegalArgumentException("Missing Empire visual fit: " + id);
        }
        return fit;
    }

    private static HullDefinition requireHull(ShipEngineeringCatalog engineering, String id) {
        HullDefinition hull = engineering.findHull(id);
        if (hull == null) {
            throw new IllegalArgumentException("Missing Empire visual hull: " + id);
        }
        return hull;
    }

    private static String fingerprint(String engineeringFingerprint, List<FamilyVisual> visuals) {
        StringBuilder canonical = new StringBuilder(4096).append(engineeringFingerprint).append('\n');
        for (FamilyVisual visual : visuals) {
            canonical.append(visual.familyId()).append('|').append(visual.roleId()).append('|')
                    .append(visual.primaryFitId()).append('|').append(visual.refitFitId()).append('|')
                    .append(visual.markerSilhouettePath()).append('|')
                    .append(visual.assets().allTexturePaths()).append('|')
                    .append(visual.sprite().assetId()).append('|')
                    .append(visual.sprite().worldWidth()).append('|').append(visual.sprite().worldHeight()).append('|')
                    .append(visual.sprite().hardpoints().stream().map(VisualHardpoint::id).toList()).append('|')
                    .append(visual.provenanceRef()).append('\n');
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /**
     * Immutable production visual catalog and its engineering-coupled presentation fingerprint.
     *
     * @param engineeringFingerprint semantic fingerprint of the engineering source used for geometry
     * @param families exact nine-family production visual definitions
     * @param fingerprint deterministic presentation catalog fingerprint
     */
    public record Catalog(String engineeringFingerprint, List<FamilyVisual> families, String fingerprint) {
        /**
         * Canonicalizes one catalog and computes its deterministic presentation fingerprint.
         *
         * @param engineeringFingerprint engineering source fingerprint
         * @param families authored production family visuals
         */
        public Catalog(String engineeringFingerprint, List<FamilyVisual> families) {
            this(
                    requireText(engineeringFingerprint, "engineeringFingerprint"),
                    canonicalFamilies(families),
                    "");
        }

        /** Validates the exact M22.3 family floor and computes the presentation fingerprint. */
        public Catalog {
            if (families.size() != Stage22EmpirePackageCatalog.REQUIRED_SHIP_FAMILIES) {
                throw new IllegalArgumentException("Empire production visual catalog must contain exactly nine families");
            }
            fingerprint = Stage22EmpireShipVisualCatalog.fingerprint(engineeringFingerprint, families);
        }

        /**
         * Finds one production visual by common role ID.
         *
         * @param roleId common Stage-22 role ID
         * @return matching family visual, or {@code null} when absent
         */
        public FamilyVisual findByRole(String roleId) {
            for (FamilyVisual visual : families) {
                if (visual.roleId().equals(roleId)) {
                    return visual;
                }
            }
            return null;
        }
    }

    /**
     * One family-level production visual package bound downstream of exact engineering fits.
     *
     * @param familyId stable Imperial family ID
     * @param roleId common Stage-22 role ID
     * @param primaryFitId exact primary engineering fit
     * @param refitFitId exact refit engineering fit
     * @param markerSilhouettePath grayscale marker/silhouette resource
     * @param assets five-layer production visual asset set
     * @param sprite engineering-derived presentation geometry and anchors
     * @param provenanceRef canonical faction visual authority
     */
    public record FamilyVisual(
            String familyId,
            String roleId,
            String primaryFitId,
            String refitFitId,
            String markerSilhouettePath,
            ShipVisualAssetSet assets,
            ShipSpriteSpec sprite,
            String provenanceRef) {
        /** Validates one production family visual binding. */
        public FamilyVisual {
            familyId = requireText(familyId, "familyId");
            roleId = requireText(roleId, "roleId");
            primaryFitId = requireText(primaryFitId, "primaryFitId");
            refitFitId = requireText(refitFitId, "refitFitId");
            markerSilhouettePath = requireText(markerSilhouettePath, "markerSilhouettePath");
            assets = Objects.requireNonNull(assets, "assets");
            sprite = Objects.requireNonNull(sprite, "sprite");
            provenanceRef = requireText(provenanceRef, "provenanceRef");
        }
    }

    private static List<FamilyVisual> canonicalFamilies(List<FamilyVisual> source) {
        ArrayList<FamilyVisual> copy = new ArrayList<>(Objects.requireNonNull(source, "families"));
        copy.replaceAll(value -> Objects.requireNonNull(value, "family visual"));
        copy.sort(Comparator.comparing(FamilyVisual::familyId));
        if (copy.stream().map(FamilyVisual::familyId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("Duplicate Empire production visual family ID");
        }
        if (copy.stream().map(FamilyVisual::roleId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("Duplicate Empire production visual role ID");
        }
        return List.copyOf(copy);
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return checked;
    }
}
