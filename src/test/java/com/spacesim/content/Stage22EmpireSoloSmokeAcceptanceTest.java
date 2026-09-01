package com.spacesim.content;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.content.Stage22EmpirePackageCatalog.MissionTemplateDefinition;
import com.spacesim.content.Stage22EmpirePackageCatalog.ShipFamilyDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.persistence.Stage22FactionProfileBindingCodec;
import com.spacesim.persistence.Stage22FactionProfileBindingState;
import com.spacesim.world.FactionIdentityResolver;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.3 required solo B00-B14 smoke suite.
 *
 * <p>These are causal integrity/content-legality smoke gates, not final paired balance tuning. A
 * scenario passes when the expected physical dependency, authority path, support burden or recovery
 * mechanism is present and deterministic. M22.6 remains responsible for multi-seed paired outcome
 * tuning against Industrial Union.</p>
 */
class Stage22EmpireSoloSmokeAcceptanceTest {
    @Test
    void b00CatalogAuthorityAudit() {
        var first = Stage22EmpirePackageValidator.validateDefault();
        var second = Stage22EmpirePackageValidator.validateDefault();
        var profile = Stage22EmpireFactionProfileCatalog.loadDefault();

        assertEquals(first.packageFingerprint(), second.packageFingerprint());
        assertEquals(first.productionFingerprint(), second.productionFingerprint());
        assertEquals(9, first.familyMetrics().size());
        assertEquals(64, profile.fingerprint().length());
        assertFalse(FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(),
                LargeDemoGalaxyFactory.createState(22_300L, ContentCatalogLoader.loadDefault()).factionIdentities())
                .containsStableId("faction.empire"));
    }

    @Test
    void b01SaveLoadReplayRoundTrip() {
        Stage22FactionProfileCatalog profile = Stage22EmpireFactionProfileCatalog.loadDefault();
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        var world = LargeDemoGalaxyFactory.createState(22_301L, content);
        FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(content, world.factionIdentities());
        Stage22FactionProfileBindingState captured = Stage22FactionProfileBindingState.capture(profile, resolver);
        byte[] encoded = Stage22FactionProfileBindingCodec.encode(captured);
        Stage22FactionProfileBindingState decoded = Stage22FactionProfileBindingCodec.decode(encoded);
        decoded.validateAgainst(profile, resolver);
        assertArrayEquals(encoded, Stage22FactionProfileBindingCodec.encode(decoded));
    }

    @Test
    void b02ViableColdStartHasNoHiddenProductionDependency() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        var manifests = Stage22EmpireProductionCatalogs.loadManifests();
        var facilities = Stage18FacilityCatalogLoader.loadDefault();
        var yard = Stage22EmpireShipyardCatalogLoader.loadDefault()
                .findYard(Stage22EmpireProductionCatalogs.YARD_ID);

        assertNotNull(yard);
        assertEquals(9, manifests.productionManifests().size());
        assertEquals(9, empire.shipFamilies().size());
        yard.requiredSupportFacilityDefinitionIds().forEach(id -> assertNotNull(facilities.findFacility(id), id));
    }

    @Test
    void b03PlannedExpansionUsesRealStationAndFacilityDefinitions() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        var stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        var facilities = Stage18FacilityCatalogLoader.loadDefault();

        assertEquals(3, empire.stations().size());
        empire.stations().forEach(variant -> {
            var archetype = stations.findArchetype(variant.stage18ArchetypeId());
            assertNotNull(archetype, variant.id());
            variant.requiredFacilityIds().forEach(id -> {
                assertNotNull(facilities.findFacility(id), id);
                assertTrue(archetype.installedFacilityDefinitionIds().contains(id), variant.id() + " -> " + id);
            });
        });
    }

    @Test
    void b04CriticalMaterialShortageIsARealInputConstraint() {
        var engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        Set<String> authoredInputs = engineering.getModules().stream()
                .flatMap(module -> module.constructionInputs().stream())
                .map(input -> input.contentId())
                .collect(Collectors.toSet());
        assertTrue(authoredInputs.contains("component.precision"));
        assertTrue(authoredInputs.contains("component.heavy"));
        assertTrue(authoredInputs.contains("component.electrical"));
        assertFalse(authoredInputs.contains("faction.empire.free_resource"));
    }

    @Test
    void b05SingleHubLossExposesStrategicNodeDependency() {
        Stage22EmpireBalanceTelemetry.Report telemetry = Stage22EmpireBalanceTelemetry.deriveCurrent();
        assertEquals(1, telemetry.productionYardCount());
        assertTrue(telemetry.repairCoveredFamilyCount() > 0);
    }

    @Test
    void b06DistributedLowIntensityRaidsHavePatrolDefenseAndSupportPaths() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        assertNotNull(mission(empire, "mission.empire.route_defense"));
        assertNotNull(family(empire, "role.military.corvette"));
        assertNotNull(family(empire, "role.military.frigate"));
        assertNotNull(family(empire, "role.support.freight"));
    }

    @Test
    void b07EqualBurdenPatrolContestHasAStablePatrolFitWithPositiveMargins() {
        var metrics = Stage22EmpirePackageValidator.validateDefault().familyMetrics()
                .get("role.military.frigate");
        assertNotNull(metrics);
        assertTrue(metrics.remainingOperationalMassKg() >= 0d);
        assertTrue(metrics.continuousPowerMarginW() >= 0d);
        assertTrue(metrics.continuousThermalMarginW() >= 0d);
    }

    @Test
    void b08ConvoyEscortInterdictionUsesRealFleetAndFreightContent() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        MissionTemplateDefinition convoy = mission(empire, "mission.empire.convoy_guard");
        assertNotNull(convoy);
        assertEquals(com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority.FLEET, convoy.authority());
        assertEquals(com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind.ESCORT_FLEETS_PRESENT_IN_SYSTEM,
                convoy.objectiveKind());
        assertNotNull(family(empire, "role.support.freight"));
    }

    @Test
    void b09PreparedSystemDefenseHasCapitalReserveAndArsenalElements() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        assertNotNull(family(empire, "role.military.battleship"));
        assertNotNull(mission(empire, "mission.empire.route_defense"));
        assertTrue(empire.stations().stream().anyMatch(value -> value.id().equals("station_variant.empire.arsenal_depot")));
    }

    @Test
    void b10ForcedOffensiveProjectionCarriesVisibleSupportMassBurden() {
        Stage22EmpireBalanceTelemetry.Report telemetry = Stage22EmpireBalanceTelemetry.deriveCurrent();
        assertTrue(telemetry.projectionBundleMassKg() > telemetry.carrierMassKg());
        assertTrue(telemetry.supportMassShare() > 0d);
    }

    @Test
    void b11DegradedCommandAndSensorsRemainBoundedByKnowledgeAuthority() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        MissionTemplateDefinition recon = mission(empire, "mission.empire.frontier_recon");
        assertNotNull(recon);
        assertEquals(com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority.DISCOVERY, recon.authority());
        var profile = Stage22EmpireFactionProfileCatalog.loadDefault();
        var empireProfile = profile.findProfileForFaction("faction.imperial_directorate");
        assertEquals(Stage22FactionProfileCatalog.AuthoritySeam.DISCOVERY_KNOWLEDGE,
                profile.findPolicy(empireProfile.knowledgePolicyRef()).authoritySeam());
    }

    @Test
    void b12MagazineLimitedEngagementHasFiniteAmmunitionInterfaces() {
        var engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        long finiteWeaponInterfaces = engineering.getModules().stream()
                .filter(module -> module.family() == ModuleFamily.WEAPON_AMMUNITION)
                .flatMap(module -> module.interfaces().stream())
                .filter(value -> value.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> Double.isFinite(value.capacity()) && value.capacity() > 0d)
                .count();
        assertTrue(finiteWeaponInterfaces > 0L);
    }

    @Test
    void b13LongWarRollingAttritionRetainsRepairCoverageForEveryFamily() {
        Stage22EmpireBalanceTelemetry.Report telemetry = Stage22EmpireBalanceTelemetry.deriveCurrent();
        assertEquals(telemetry.familyCount(), telemetry.repairCoveredFamilyCount());
        var shipyards = Stage22EmpireShipyardCatalogLoader.loadDefault();
        Stage22EmpirePackageLoader.loadDefault().shipFamilies().forEach(family -> {
            var fit = Stage22EmpireEngineeringCatalogLoader.loadDefault().findDemonstratorFit(family.primaryFitId());
            assertNotNull(shipyards.findHullProfile(fit.hullId()), family.familyId());
        });
    }

    @Test
    void b14PostWarRecoveryUsesRepairSalvageAndFiniteConstructionAuthorities() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        ShipFamilyDefinition support = family(empire, "role.support.fleet_logistics_repair_salvage");
        assertNotNull(support);
        var engineering = Stage22EmpireEngineeringCatalogLoader.loadDefault();
        var supportFit = engineering.findDemonstratorFit(support.primaryFitId());
        assertTrue(supportFit.installedModules().stream()
                .map(value -> engineering.findModule(value.moduleId()))
                .anyMatch(module -> module.family() == ModuleFamily.MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE));
        assertNotNull(mission(empire, "mission.empire.yard_repair_inputs"));
        assertNotNull(mission(empire, "mission.empire.derelict_recovery"));
    }

    private static MissionTemplateDefinition mission(Stage22EmpirePackageCatalog empire, String id) {
        return empire.missions().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    private static ShipFamilyDefinition family(Stage22EmpirePackageCatalog empire, String roleId) {
        return empire.shipFamilies().stream().filter(value -> value.roleId().equals(roleId)).findFirst().orElse(null);
    }
}
