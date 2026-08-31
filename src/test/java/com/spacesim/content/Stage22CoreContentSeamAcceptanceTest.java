package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22CoreContentSeamCatalog.RoleDomain;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CoreContentSeamAcceptanceTest {
    @Test
    void commonSeamCoversNineRolesAndValidatesPhysicalAuthoringExemplar() {
        Stage22CoreContentSeamCatalog seam = Stage22CoreContentSeamLoader.loadDefault();
        Stage22CoreProductionManifestCatalog production = Stage22CoreProductionManifestLoader.loadDefault();
        Stage22CoreContentSeamValidator.ValidationReport report = Stage22CoreContentSeamValidator.validateDefault();

        assertEquals(9, seam.roles().size());
        assertEquals(9, seam.missionProfiles().size());
        assertEquals(6L, seam.roles().stream().filter(role -> role.domain() == RoleDomain.MILITARY).count());
        assertEquals(3L, seam.roles().stream().filter(role -> role.domain() == RoleDomain.SUPPORT).count());
        assertEquals(1, seam.authoringTemplates().size());
        assertEquals(1, production.productionManifests().size());
        assertEquals(3, production.supportEnduranceRequirements().size());
        assertEquals(64, seam.fingerprint().length());
        assertEquals(64, production.fingerprint().length());
        assertEquals(seam.fingerprint(), report.seamFingerprint());
        assertEquals(production.fingerprint(), report.productionFingerprint());

        var template = seam.findTemplate("authoring_template.shared.destroyer_end_to_end");
        assertNotNull(template);
        var mission = seam.findMission(template.missionProfileId());
        assertNotNull(mission);
        assertEquals(template.roleId(), mission.roleId());

        var manifest = production.findManifestForFit(template.fitId());
        assertNotNull(manifest);
        assertEquals(template.productionHullId(), manifest.hullId());
        assertEquals(ContentMaturity.CANDIDATE, manifest.contentMaturity());
        var visual = seam.findVisualBinding(template.visualBindingId());
        assertNotNull(visual);
        assertEquals(template.fitId(), visual.fitId());
        assertEquals(64, report.visualFitFingerprints().get(visual.id()).length());
    }

    @Test
    void physicalManifestExactlyMatchesInstalledModulesYardAndFacilities() {
        Stage22CoreProductionManifestCatalog production = Stage22CoreProductionManifestLoader.loadDefault();
        var manifest = production.findManifest("production_manifest.shared.escort_destroyer_schema_v1");
        assertNotNull(manifest);

        var engineering = ShipEngineeringCatalogLoader.loadDefault();
        var fit = engineering.findDemonstratorFit(manifest.fitId());
        assertNotNull(fit);
        Set<String> installedModules = fit.installedModules().stream()
                .map(module -> module.moduleId())
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(installedModules, new TreeSet<>(manifest.componentIds()));

        var manufacturing = Stage18ManufacturingProductRegistry.loadDefault();
        manifest.componentIds().forEach(id -> assertNotNull(manufacturing.findProduct(id), id));

        var shipyards = Stage18ShipyardCatalogLoader.loadDefault();
        var yard = shipyards.findYard(manifest.shipyardId());
        assertNotNull(yard);
        assertEquals(new TreeSet<>(yard.requiredSupportFacilityDefinitionIds()),
                new TreeSet<>(manifest.requiredFacilityIds()));

        var facilities = Stage18FacilityCatalogLoader.loadDefault();
        manifest.requiredFacilityIds().forEach(id -> assertNotNull(facilities.findFacility(id), id));
    }

    @Test
    void allThreeSupportRolesMeetDeclaredEnduranceFloorsWithNoHiddenBonus() {
        var report = Stage22CoreContentSeamValidator.validateDefault();
        assertEquals(Set.of(
                        "role.support.freight",
                        "role.support.tanker_replenishment",
                        "role.support.fleet_logistics_repair_salvage"),
                report.supportEnduranceMarginS().keySet());
        report.supportEnduranceMarginS().forEach((role, margin) -> {
            assertTrue(Double.isFinite(margin), role);
            assertTrue(margin >= 0d, role);
        });
    }

    @Test
    void commonContentIdsAndMetadataRemainFactionNeutral() {
        Stage22CoreContentSeamCatalog seam = Stage22CoreContentSeamLoader.loadDefault();
        Stage22CoreProductionManifestCatalog production = Stage22CoreProductionManifestLoader.loadDefault();
        String canonical = seam.roles() + "|" + seam.missionProfiles() + "|" + seam.lineages()
                + "|" + seam.visualBindings() + "|" + seam.localizationRules()
                + "|" + seam.telemetryHooks() + "|" + seam.authoringTemplates()
                + "|" + production.productionManifests() + "|" + production.supportEnduranceRequirements();
        String lower = canonical.toLowerCase(java.util.Locale.ROOT);

        assertFalse(lower.contains("core.empire"));
        assertFalse(lower.contains("imperial_directorate"));
        assertFalse(lower.contains("core.industrial_union"));
        assertFalse(lower.contains("industrial_combine"));
    }
}
