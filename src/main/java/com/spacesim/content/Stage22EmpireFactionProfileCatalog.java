package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22FactionProfileCatalog.ManifestReferenceDefinition;
import com.spacesim.content.Stage22FactionProfileCatalog.RoleProductionBindingDefinition;
import com.spacesim.content.Stage22FactionProfileCatalog.VisualProfileDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * M22.3 production view of the common Stage-22 faction-profile catalog.
 *
 * <p>The accepted M22.1 baseline remains an immutable two-profile seed contract. This projection
 * promotes only the Empire authored-content manifest and visual maturity after M22.3 package
 * validation while preserving the Industrial Union SEED boundary for M22.4. The existing generic
 * Stage-22.1 persistence sidecar can therefore bind this derived semantic fingerprint without a
 * second mutable faction/profile owner.</p>
 */
public final class Stage22EmpireFactionProfileCatalog {
    /** Semantic catalog version of the M22.3 promoted profile view. */
    public static final String CATALOG_VERSION = "stage22.faction_profiles.empire_promoted.v1";

    private Stage22EmpireFactionProfileCatalog() {
        throw new AssertionError("utility class");
    }

    /**
     * Builds the deterministic two-core-profile view with Empire promoted and Union still SEED.
     *
     * @return immutable promoted profile catalog
     */
    public static Stage22FactionProfileCatalog loadDefault() {
        Stage22FactionProfileCatalog base = Stage22FactionProfileLoader.loadDefault();
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        ShipEngineeringCatalog engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        Stage22EmpireCharacterLineup.Catalog characters = Stage22EmpireCharacterLineup.loadDefault();
        Stage22EmpirePackageValidator.ValidationReport packageReport = Stage22EmpirePackageValidator.validateDefault();
        if (!packageReport.packageFingerprint().equals(empire.fingerprint())
                || characters.fingerprint().length() != 64) {
            throw new IllegalStateException("Empire package/profile promotion evidence is inconsistent");
        }

        var empireProfile = base.findProfileForFaction("faction.imperial_directorate");
        if (empireProfile == null || !"core.empire".equals(empireProfile.packageKey())) {
            throw new IllegalStateException("M22.1 Empire profile identity is unavailable");
        }

        List<RoleProductionBindingDefinition> roleBindings = new ArrayList<>();
        for (Stage22EmpirePackageCatalog.ShipFamilyDefinition family : empire.shipFamilies()) {
            var fit = engineering.findDemonstratorFit(family.primaryFitId());
            if (fit == null) {
                throw new IllegalStateException("Empire profile role references missing fit: " + family.primaryFitId());
            }
            roleBindings.add(new RoleProductionBindingDefinition(
                    family.roleId(),
                    family.primaryFitId(),
                    fit.hullId(),
                    empireProfile.shipVisualProfileRef()));
        }

        ManifestReferenceDefinition promotedManifest = new ManifestReferenceDefinition(
                empireProfile.authoredContentManifestRef(),
                empireProfile.packageKey(),
                Stage22FactionProfileCatalog.PackageScope.CORE,
                ContentMaturity.VALIDATED,
                roleBindings);

        List<ManifestReferenceDefinition> manifests = new ArrayList<>();
        for (ManifestReferenceDefinition manifest : base.manifestReferences()) {
            manifests.add(manifest.id().equals(promotedManifest.id()) ? promotedManifest : manifest);
        }
        List<VisualProfileDefinition> visuals = new ArrayList<>();
        for (VisualProfileDefinition visual : base.visualProfiles()) {
            if (visual.packageKey().equals("core.empire")) {
                visuals.add(new VisualProfileDefinition(
                        visual.id(),
                        visual.kind(),
                        visual.packageKey(),
                        AssetStatus.ENGINEERING_APPROVED,
                        visual.authorityDocument()));
            } else {
                visuals.add(visual);
            }
        }

        Stage22FactionProfileCatalog promoted = new Stage22FactionProfileCatalog(
                base.schemaVersion(),
                CATALOG_VERSION,
                base.doctrineProfiles(),
                base.policyBindings(),
                manifests,
                visuals,
                base.localizations(),
                base.systemicProfiles());
        validatePromotion(promoted);
        return promoted;
    }

    private static void validatePromotion(Stage22FactionProfileCatalog catalog) {
        var empire = catalog.findProfileForFaction("faction.imperial_directorate");
        var union = catalog.findProfileForFaction("faction.industrial_combine");
        var empireManifest = catalog.findManifest(empire.authoredContentManifestRef());
        var unionManifest = catalog.findManifest(union.authoredContentManifestRef());
        if (empireManifest.maturity() != ContentMaturity.VALIDATED
                || empireManifest.roleBindings().size() != Stage22EmpirePackageCatalog.REQUIRED_SHIP_FAMILIES) {
            throw new IllegalStateException("Empire promoted profile must bind the exact nine-role package");
        }
        if (unionManifest.maturity() != ContentMaturity.SEED || !unionManifest.roleBindings().isEmpty()) {
            throw new IllegalStateException("M22.3 must not promote Industrial Union content early");
        }
        if (catalog.findVisual(empire.shipVisualProfileRef()).status() != AssetStatus.ENGINEERING_APPROVED
                || catalog.findVisual(empire.characterVisualProfileRef()).status() != AssetStatus.ENGINEERING_APPROVED) {
            throw new IllegalStateException("Empire promoted profile requires approved ship and character visual contracts");
        }
        if (catalog.findVisual(union.shipVisualProfileRef()).status() != AssetStatus.CONCEPT
                || catalog.findVisual(union.characterVisualProfileRef()).status() != AssetStatus.CONCEPT) {
            throw new IllegalStateException("M22.3 must preserve Industrial Union visual boundary");
        }
        if (catalog.fingerprint().length() != 64) {
            throw new IllegalStateException("Promoted Stage-22 profile fingerprint is invalid");
        }
    }
}
