package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionShipyardIndustrialCatalogLoader;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22IndustrialUnionPackageAcceptanceTest {
    @Test
    void authoredPackageLoadsAcrossAcceptedEngineeringAndIndustryAuthorities() {
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();
        var common = Stage22CoreContentSeamLoader.loadDefault();
        var engineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        var industrial = Stage22IndustrialUnionShipyardIndustrialCatalogLoader.loadDefault();
        var physical = Stage22IndustrialUnionShipyardCatalogLoader.loadDefault();
        var manufacturing = Stage22IndustrialUnionManufacturingCatalogLoader.loadDefault();
        var manifests = Stage22IndustrialUnionProductionCatalogs.loadManifests();
        var visuals = Stage22IndustrialUnionProductionCatalogs.loadVisualBindings();
        var characters = Stage22IndustrialUnionCharacterLineup.loadDefault();

        assertEquals(Stage22IndustrialUnionPackageCatalog.REQUIRED_SHIP_FAMILIES, union.shipFamilies().size());
        assertEquals(9, engineering.getHulls().size());
        assertEquals(10, engineering.getModules().size());
        assertEquals(18, engineering.getDemonstratorFits().size());
        assertEquals(9, industrial.getHullProfiles().size());
        assertEquals(10, industrial.getModuleProfiles().size());
        assertEquals(1, physical.getYards().size());
        assertEquals(9, physical.getHullProfiles().size());
        assertEquals(10, physical.getModuleProfiles().size());
        assertEquals(9, manifests.productionManifests().size());
        assertEquals(18, visuals.size());
        assertEquals(7, union.recurringNpcs().size());
        assertEquals(7, characters.overlays().size());
        assertEquals(64, union.fingerprint().length());
        assertEquals(64, engineering.getFingerprint().length());
        assertEquals(64, physical.getFingerprint().length());
        assertEquals(64, characters.fingerprint().length());

        Set<String> commonRoles = common.roles().stream()
                .map(Stage22CoreContentSeamCatalog.RoleDefinition::id)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> unionRoles = union.shipFamilies().stream()
                .map(Stage22IndustrialUnionPackageCatalog.ShipFamilyDefinition::roleId)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(commonRoles, unionRoles);

        Set<NpcRole> recurringRoles = union.recurringNpcs().stream()
                .map(Stage22IndustrialUnionPackageCatalog.RecurringNpcDefinition::role)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                NpcRole.OFFICIAL,
                NpcRole.MILITARY,
                NpcRole.TRADE_LOGISTICS,
                NpcRole.INDUSTRY_YARD,
                NpcRole.EXPLORATION_INTELLIGENCE,
                NpcRole.INDEPENDENT_FRONTIER), recurringRoles);
        assertNotNull(union.findNpc("npc.industrial_union.plant_director"));

        var registry = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        engineering.getModules().forEach(module -> {
            assertEquals(Provenance.STAGE22_AUTHORED, registry.findProduct(module.id()).provenance(), module.id());
            var binding = manufacturing.findProductBinding(module.id());
            assertNotNull(binding, module.id());
            assertNotNull(manufacturing.findProductProfile(binding.profileId()), binding.profileId());
            assertNotNull(industrial.findModuleProfile(module.id()), module.id());
            assertNotNull(physical.findModuleProfile(module.id()), module.id());
        });
    }

    @Test
    void everyFamilyUsesRepeatedCoreAssembliesAndDistinctLegalRefit() {
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();
        var engineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        Set<String> commonCore = Set.of(
                "module.industrial_union_reactor_bank_v1",
                "module.industrial_union_drive_bank_v1",
                "module.industrial_union_sensor_block_v1",
                "module.industrial_union_radiator_panel_v1");

        for (var family : union.shipFamilies()) {
            var primary = engineering.findDemonstratorFit(family.primaryFitId());
            var refit = engineering.findDemonstratorFit(family.refitFitId());
            assertNotNull(primary, family.familyId());
            assertNotNull(refit, family.familyId());
            assertEquals(primary.hullId(), refit.hullId(), family.familyId());
            Set<String> primaryModules = primary.installedModules().stream()
                    .map(value -> value.moduleId()).collect(Collectors.toSet());
            Set<String> refitModules = refit.installedModules().stream()
                    .map(value -> value.moduleId()).collect(Collectors.toSet());
            assertTrue(primaryModules.containsAll(commonCore), family.familyId());
            assertTrue(refitModules.containsAll(commonCore), family.familyId());
            assertNotEquals(
                    Stage22FitFingerprint.compute(engineering, primary.id()),
                    Stage22FitFingerprint.compute(engineering, refit.id()),
                    family.familyId());
        }
    }

    @Test
    void missionsRemainInsideStage21HLifecycleAndMeetM224CategoryFloor() {
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();
        assertEquals(11, union.missions().size());
        union.missions().forEach(mission -> {
            var issuer = union.findNpc(mission.issuerNpcId());
            assertNotNull(issuer, mission.id());
            assertTrue(Stage21HNpcMissionState.canIssue(issuer.role(), mission.runtimeTemplate()), mission.id());
            Stage21HNpcMissionState.validateTemplateObjective(mission.runtimeTemplate(), mission.objectiveKind());
            assertEquals(Stage21HNpcMissionState.expectedAuthority(mission.objectiveKind()), mission.authority(), mission.id());
        });

        long combatSecurity = countTemplates(union, Set.of(
                MissionTemplate.CONVOY_ESCORT,
                MissionTemplate.STRANDED_FLEET_RESCUE_REFUEL,
                MissionTemplate.INTERCEPTION_DEFENSE));
        long economyProduction = countTemplates(union, Set.of(
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                MissionTemplate.ORDINARY_MARKET_PROCUREMENT,
                MissionTemplate.CONSTRUCTION_REPAIR_INPUT_DELIVERY));
        long diplomacyPolitics = countTemplates(union, Set.of(MissionTemplate.IMPERIAL_ACCESS_NEGOTIATION));
        long explorationAnomaly = countTemplates(union, Set.of(
                MissionTemplate.SYSTEM_OBJECT_RECONNAISSANCE,
                MissionTemplate.DERELICT_INVESTIGATION_RECOVERY));

        assertTrue(combatSecurity >= 2L);
        assertTrue(economyProduction >= 2L);
        assertTrue(diplomacyPolitics >= 2L);
        assertTrue(explorationAnomaly >= 2L);
        assertEquals(2, union.storyChains().size());
        union.storyChains().forEach(chain -> {
            assertTrue(chain.missionTemplateIds().size() >= 2, chain.id());
            assertTrue(chain.missionTemplateIds().size() <= 4, chain.id());
        });
    }

    private static long countTemplates(
            Stage22IndustrialUnionPackageCatalog union,
            Set<MissionTemplate> templates) {
        return union.missions().stream()
                .filter(mission -> templates.contains(mission.runtimeTemplate()))
                .count();
    }
}
