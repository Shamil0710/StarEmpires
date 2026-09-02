package com.spacesim.content;

import com.spacesim.content.Stage22IndustrialUnionPackageCatalog.MissionTemplateDefinition;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog.ShipFamilyDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import com.spacesim.persistence.Stage22IndustrialUnionProductionStateCodec;
import com.spacesim.world.Stage21HNpcMissionState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.4 required solo B00-B14 smoke suite.
 *
 * <p>As in the accepted M22.3 Empire suite, these are causal integrity/content-legality smoke gates,
 * not final paired balance tuning. M22.6 remains responsible for multi-seed paired outcomes. M22.4
 * additionally owns the Industrial Union's declared systemic weakness, so B05 proves that correlated
 * degradation of shared assemblies/logistics/facilities is materially worse than isolated local loss.</p>
 */
class Stage22IndustrialUnionSoloSmokeAcceptanceTest {
    @Test
    void b00CatalogAuthorityAudit() {
        var first = Stage22IndustrialUnionPackageValidator.validateDefault();
        var second = Stage22IndustrialUnionPackageValidator.validateDefault();
        var profile = Stage22FactionProfileLoader.loadDefault();
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();

        assertEquals(first.packageFingerprint(), second.packageFingerprint());
        assertEquals(first.productionFingerprint(), second.productionFingerprint());
        assertEquals(9, first.familyMetrics().size());
        assertNotNull(profile.findProfileForFaction(Stage22IndustrialUnionProductionState.STABLE_FACTION_ID));
        assertEquals(7, union.recurringNpcs().size());
        assertEquals(11, union.missions().size());
        assertEquals(2, union.storyChains().size());
        union.storyChains().forEach(chain -> assertTrue(chain.missionTemplateIds().size() >= 3, chain.id()));
    }

    @Test
    void b01SaveLoadReplayRoundTrip() {
        var state = new Stage22IndustrialUnionProductionState(
                Stage22IndustrialUnionProductionState.CURRENT_VERSION,
                Stage22IndustrialUnionProductionState.STABLE_FACTION_ID,
                Stage22IndustrialUnionPackageLoader.loadDefault().fingerprint(),
                3L,
                java.util.List.of(Stage22IndustrialUnionProductionState.unqualifiedYard(
                        Stage22IndustrialUnionIndustrialProgram.YARD_ID)));
        byte[] encoded = Stage22IndustrialUnionProductionStateCodec.encode(state);
        var decoded = Stage22IndustrialUnionProductionStateCodec.decode(encoded);

        assertEquals(state.stableFactionId(), decoded.stableFactionId());
        assertEquals(state.packageFingerprint(), decoded.packageFingerprint());
        assertEquals(state.sequence(), decoded.sequence());
        assertEquals(state.yards(), decoded.yards());
        assertArrayEquals(encoded, Stage22IndustrialUnionProductionStateCodec.encode(decoded));
    }

    @Test
    void b02ViableColdStartRequiresFiniteQualificationInsteadOfHiddenProductionAccess() {
        var yard = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        var pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                yard, "ship_family.industrial_union.corvette");

        assertTrue(pending.retooling());
        assertTrue(pending.retoolWorkRemainingSeconds() > 0L);
        assertTrue(pending.retoolEnergyRemainingJ() > 0L);
        assertFalse(pending.activeSeriesId().equals(pending.pendingSeriesId()));
    }

    @Test
    void b03PlannedExpansionUsesRealStationAndFacilityDefinitions() {
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();
        var stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        var facilities = Stage18FacilityCatalogLoader.loadDefault();

        assertEquals(3, union.stations().size());
        union.stations().forEach(variant -> {
            var archetype = stations.findArchetype(variant.stage18ArchetypeId());
            assertNotNull(archetype, variant.id());
            variant.requiredFacilityIds().forEach(id -> {
                assertNotNull(facilities.findFacility(id), id);
                assertTrue(archetype.installedFacilityDefinitionIds().contains(id), variant.id() + " -> " + id);
            });
        });
    }

    @Test
    void b04CriticalCommonAssemblyShortageIsARealMaterialConstraint() {
        var engineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        Stage22IndustrialUnionCommonalityNetwork.SHARED_ASSEMBLY_IDS.forEach(id -> {
            var module = engineering.findModule(id);
            assertNotNull(module, id);
            assertFalse(module.constructionInputs().isEmpty(), id);
            Set<String> inputs = module.constructionInputs().stream()
                    .map(value -> value.contentId())
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(inputs.contains("component.heavy"), id);
            assertTrue(inputs.contains("component.electrical"), id);
            assertTrue(inputs.contains("component.precision"), id);
            assertFalse(inputs.stream().anyMatch(value -> value.startsWith("faction.")), id);
        });
    }

    @Test
    void b05CorrelatedSharedNetworkLossExceedsMaterialDegradationFloor() {
        var yard = steadyLogisticsYard();
        var healthyAvailability = Stage22IndustrialUnionCommonalityNetwork.healthy();

        var isolatedAssemblies = new LinkedHashMap<>(healthyAvailability.sharedAssemblyAvailability());
        isolatedAssemblies.put("module.industrial_union_sensor_block_v1", 0.75d);
        var isolated = Stage22IndustrialUnionCommonalityNetwork.observe(
                yard,
                "ship_family.industrial_union.freight",
                new Stage22IndustrialUnionCommonalityNetwork.Availability(isolatedAssemblies, 1d, 1d));

        var correlatedAssemblies = new LinkedHashMap<String, Double>();
        Stage22IndustrialUnionCommonalityNetwork.SHARED_ASSEMBLY_IDS.stream().sorted()
                .forEach(id -> correlatedAssemblies.put(id, 0.75d));
        var correlated = Stage22IndustrialUnionCommonalityNetwork.observe(
                yard,
                "ship_family.industrial_union.freight",
                new Stage22IndustrialUnionCommonalityNetwork.Availability(correlatedAssemblies, 0.75d, 0.75d));

        assertFalse(isolated.correlatedDisruption());
        assertTrue(correlated.correlatedDisruption());
        assertTrue(isolated.throughputDegradation() < 0.10d);
        assertTrue(correlated.throughputDegradation() >= 0.25d);
        assertTrue(correlated.throughputDegradation() > isolated.throughputDegradation());
        assertTrue(correlated.workBurdenMultiplier() > isolated.workBurdenMultiplier());
    }

    @Test
    void b06DistributedLowIntensityRaidsHavePatrolDefenseAndSupportPaths() {
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();
        assertNotNull(mission(union, "mission.industrial_union.route_defense"));
        assertNotNull(family(union, "role.military.corvette"));
        assertNotNull(family(union, "role.military.frigate"));
        assertNotNull(family(union, "role.support.freight"));
    }

    @Test
    void b07EqualBurdenPatrolContestHasAStablePatrolFitWithPositiveMargins() {
        var metrics = Stage22IndustrialUnionPackageValidator.validateDefault().familyMetrics()
                .get("role.military.frigate");
        assertNotNull(metrics);
        assertTrue(metrics.remainingOperationalMassKg() >= 0d);
        assertTrue(metrics.continuousPowerMarginW() >= 0d);
        assertTrue(metrics.continuousThermalMarginW() >= 0d);
    }

    @Test
    void b08ConvoyEscortInterdictionUsesRealFleetAndFreightContent() {
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();
        MissionTemplateDefinition convoy = mission(union, "mission.industrial_union.corridor_escort");
        assertNotNull(convoy);
        assertEquals(Stage21HNpcMissionState.ObjectiveAuthority.FLEET, convoy.authority());
        assertEquals(Stage21HNpcMissionState.ObjectiveKind.ESCORT_FLEETS_PRESENT_IN_SYSTEM,
                convoy.objectiveKind());
        assertNotNull(family(union, "role.support.freight"));
    }

    @Test
    void b09PreparedSystemDefenseHasCapitalReserveAndIndustrialHubElements() {
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();
        assertNotNull(family(union, "role.military.battleship"));
        assertNotNull(mission(union, "mission.industrial_union.route_defense"));
        assertTrue(union.stations().stream().anyMatch(value ->
                value.id().equals("station_variant.industrial_union.bulk_hub")));
        assertTrue(union.stations().stream().anyMatch(value ->
                value.id().equals("station_variant.industrial_union.series_yard")));
    }

    @Test
    void b10ForcedOffensiveProjectionCarriesVisibleLogisticsSupportMassBurden() {
        var metrics = Stage22IndustrialUnionPackageValidator.validateDefault().familyMetrics();
        double carrier = metrics.get("role.military.carrier").fittedDryMassKg();
        double tanker = metrics.get("role.support.tanker_replenishment").fittedDryMassKg();
        double support = metrics.get("role.support.fleet_logistics_repair_salvage").fittedDryMassKg();

        assertTrue(tanker > 0d);
        assertTrue(support > 0d);
        assertTrue(carrier + tanker + support > carrier);
    }

    @Test
    void b11DegradedCommandAndSensorsRemainBoundedByKnowledgeAuthority() {
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();
        MissionTemplateDefinition recon = mission(union, "mission.industrial_union.hub_recon");
        assertNotNull(recon);
        assertEquals(Stage21HNpcMissionState.ObjectiveAuthority.DISCOVERY, recon.authority());
        var profiles = Stage22FactionProfileLoader.loadDefault();
        var unionProfile = profiles.findProfileForFaction(Stage22IndustrialUnionProductionState.STABLE_FACTION_ID);
        assertNotNull(unionProfile);
        assertEquals(Stage22FactionProfileCatalog.AuthoritySeam.DISCOVERY_KNOWLEDGE,
                profiles.findPolicy(unionProfile.knowledgePolicyRef()).authoritySeam());
    }

    @Test
    void b12MagazineLimitedEngagementHasFiniteAmmunitionInterfaces() {
        var engineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        long finiteWeaponInterfaces = engineering.getModules().stream()
                .filter(module -> module.family() == ModuleFamily.WEAPON_AMMUNITION)
                .flatMap(module -> module.interfaces().stream())
                .filter(value -> value.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> Double.isFinite(value.capacity()) && value.capacity() > 0d)
                .count();
        assertTrue(finiteWeaponInterfaces > 0L);
    }

    @Test
    void b13LongWarRollingAttritionRetainsPhysicalRepairCoverageForEveryFamily() {
        var shipyards = Stage22IndustrialUnionShipyardCatalogLoader.loadDefault();
        var engineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        Stage22IndustrialUnionPackageLoader.loadDefault().shipFamilies().forEach(family -> {
            var fit = engineering.findDemonstratorFit(family.primaryFitId());
            assertNotNull(fit, family.familyId());
            assertNotNull(shipyards.findHullProfile(fit.hullId()), family.familyId());
        });
    }

    @Test
    void b14PostWarRecoveryUsesWorkshopSalvageAndFiniteConstructionAuthorities() {
        var union = Stage22IndustrialUnionPackageLoader.loadDefault();
        ShipFamilyDefinition support = family(union, "role.support.fleet_logistics_repair_salvage");
        assertNotNull(support);
        var engineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        var supportFit = engineering.findDemonstratorFit(support.primaryFitId());
        assertTrue(supportFit.installedModules().stream()
                .map(value -> engineering.findModule(value.moduleId()))
                .anyMatch(module -> module.family() == ModuleFamily.MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE));
        assertNotNull(mission(union, "mission.industrial_union.line_repair_inputs"));
        assertNotNull(mission(union, "mission.industrial_union.salvage_feed"));
    }

    private static Stage22IndustrialUnionProductionState.YardSeriesState steadyLogisticsYard() {
        var yard = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        var pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                yard, "ship_family.industrial_union.freight");
        var paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                pending, pending.retoolWorkRemainingSeconds(), pending.retoolEnergyRemainingJ());
        yard = Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
        for (int index = 0; index < 3; index++) {
            yard = Stage22IndustrialUnionIndustrialProgram.recordCompletedUnit(
                    yard, "ship_family.industrial_union.freight");
        }
        return yard;
    }

    private static MissionTemplateDefinition mission(Stage22IndustrialUnionPackageCatalog union, String id) {
        return union.missions().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    private static ShipFamilyDefinition family(Stage22IndustrialUnionPackageCatalog union, String roleId) {
        return union.shipFamilies().stream().filter(value -> value.roleId().equals(roleId)).findFirst().orElse(null);
    }
}
