package com.spacesim.world.generation;

import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.Stage20EconomicBootstrapValidator.BootstrapRequirementProfile;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Requirement;
import com.spacesim.world.Stage20GeneratedWorldBatchAcceptance;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.FreightFleetProfile;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.AcceptanceAuthority;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.InitialInfrastructureProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.PhysicalTransportAuthority;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeInputs;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20GeneratedWorldProductionProbeTest {
    @Test
    void acceptedTopologyMaterializesTheRealDownstreamGenerationChain() {
        ProbeInputs inputs = representativeInputs();
        long seed = firstAcceptedTopologySeed(inputs, 1L, 128L);

        ProbeResult result = Stage20GeneratedWorldProductionProbe.run(seed, inputs);

        assertEquals(Stage20GeneratedWorldProductionProbe.CURRENT_VERSION, result.version());
        assertEquals(seed, result.rootSeed());
        assertEquals(Stage20JumpTopologyGenerationResult.Status.ACCEPTED, result.topology().status());
        assertTrue(result.localLayouts().isPresent());
        assertTrue(result.physicalHosts().isPresent());
        assertTrue(result.resourceWorld().isPresent());
        assertTrue(result.logisticsReport().isPresent());
        assertTrue(result.supplyThroughput().isPresent());
        assertTrue(result.candidateEvaluations().isPresent());
        assertTrue(result.placement().isPresent());
        assertTrue(result.economicAcceptance().isPresent());

        int systemCount = result.topology().requireAcceptedTopology().systems().size();
        assertEquals(systemCount, result.localLayouts().orElseThrow().size());
        assertEquals(systemCount, result.candidateEvaluations().orElseThrow().size());
        assertEquals(
                systemCount * inputs.infrastructure().resourceAnchorCountPerSystem(),
                result.physicalHosts().orElseThrow().hosts().size());
        assertFalse(result.resourceWorld().orElseThrow().occurrences().isEmpty());
        assertEquals(seed, result.seedAcceptance().rootSeed());
        assertEquals(
                Stage20GeneratedWorldSeedAcceptance.CURRENT_VERSION,
                result.seedAcceptance().version());

        result.localLayouts().orElseThrow().forEach(layout -> {
            assertEquals(1L, layout.placements().stream()
                    .filter(value -> value.kind() == PlacementKind.JUMP_ARRIVAL_ANCHOR)
                    .count());
            assertEquals(inputs.infrastructure().resourceAnchorCountPerSystem(), layout.placements().stream()
                    .filter(value -> value.kind() == PlacementKind.RESOURCE_FIELD_ANCHOR)
                    .count());
        });
        result.resourceWorld().orElseThrow().occurrences().forEach(occurrence -> {
            var host = result.physicalHosts().orElseThrow()
                    .host(occurrence.systemId(), occurrence.hostAnchorId());
            assertEquals(host.position(), occurrence.position());
            assertEquals(host.hostClass().hostClassId(), occurrence.hostClassId());
        });
    }

    @Test
    void topologyRejectedSeedStopsBeforeAnyDownstreamMaterialization() {
        ProbeInputs inputs = inputsWithMacroRequest(
                new Stage20MacroGalaxyGeometryGenerator.GenerationRequest(2, 1, 1));

        ProbeResult result = Stage20GeneratedWorldProductionProbe.run(9L, inputs);

        assertEquals(Stage20JumpTopologyGenerationResult.Status.REJECTED_SEED, result.topology().status());
        assertTrue(result.localLayouts().isEmpty());
        assertTrue(result.physicalHosts().isEmpty());
        assertTrue(result.resourceWorld().isEmpty());
        assertTrue(result.logisticsReport().isEmpty());
        assertTrue(result.supplyThroughput().isEmpty());
        assertTrue(result.candidateEvaluations().isEmpty());
        assertTrue(result.placement().isEmpty());
        assertTrue(result.economicAcceptance().isEmpty());
        assertEquals(Stage20GeneratedWorldSeedAcceptance.Status.REJECTED_SEED, result.seedAcceptance().status());
        assertEquals(
                Stage20GeneratedWorldSeedAcceptance.FailureReason.TOPOLOGY_QUALITY_REJECTED,
                result.seedAcceptance().failures().get(0).reason());
    }

    @Test
    void batchObservabilityCanMeasureRealProbeResultsWithoutInventingAPassFraction() {
        ProbeInputs inputs = representativeInputs();
        long acceptedTopologySeed = firstAcceptedTopologySeed(inputs, 1L, 128L);
        long secondSeed = acceptedTopologySeed == 1L ? 2L : 1L;
        long thirdSeed = acceptedTopologySeed == 3L || secondSeed == 3L ? 4L : 3L;
        List<Long> corpus = List.of(acceptedTopologySeed, secondSeed, thirdSeed);

        var report = Stage20GeneratedWorldBatchAcceptance.run(
                corpus,
                seed -> Stage20GeneratedWorldProductionProbe.run(seed, inputs).seedAcceptance());

        assertEquals(corpus.size(), report.seedResults().size());
        assertEquals(corpus.size(),
                report.acceptedSeedCount()
                        + report.rejectedSeedCount()
                        + report.unresolvedAuthoritySeedCount());
        assertEquals(1d,
                report.acceptedFraction()
                        + report.rejectedFraction()
                        + report.unresolvedAuthorityFraction(),
                1e-12d);
        assertEquals(report.requestedSeeds().stream().sorted().toList(), report.requestedSeeds());
        assertTrue(report.requestedSeeds().contains(acceptedTopologySeed));
    }

    @Test
    void dependencyProjectionCannotSilentlyChangeBootstrapDemand() {
        BootstrapRequirementProfile bootstrap = bootstrapRequirements();

        assertThrows(IllegalArgumentException.class, () -> new AcceptanceAuthority(
                bootstrap,
                List.of(
                        new Requirement("commodity.feedstock.water_ice", "family.water", 2d, 1.0e9d),
                        new Requirement("commodity.feedstock.metallic_ore", "family.metals", 1d, 1.0e9d)),
                Stage20FactionStartAcceptanceProfile.current(),
                List.of("faction.alpha", "faction.beta")));
    }

    private static ProbeInputs representativeInputs() {
        return inputsWithMacroRequest(Stage20MacroGalaxyGeometryGenerator.GenerationRequest.representative());
    }

    private static ProbeInputs inputsWithMacroRequest(Stage20MacroGalaxyGeometryGenerator.GenerationRequest request) {
        return new ProbeInputs(
                request,
                Stage20TopologyQualityCalibrationProfile.deriveCurrent(),
                new InitialInfrastructureProfile(
                        "stage20e.production-probe.integration-infrastructure.v1",
                        "station.infrastructure.trade_logistics_hub",
                        List.of(
                                "station.infrastructure.frontier_multipurpose",
                                "station.infrastructure.high_tech_hub",
                                "station.infrastructure.industrial_station",
                                "station.infrastructure.refinery_complex"),
                        4),
                new AcceptanceAuthority(
                        bootstrapRequirements(),
                        List.of(
                                new Requirement(
                                        "commodity.feedstock.water_ice",
                                        "family.water",
                                        1d,
                                        1.0e9d),
                                new Requirement(
                                        "commodity.feedstock.metallic_ore",
                                        "family.metals",
                                        1d,
                                        1.0e9d)),
                        Stage20FactionStartAcceptanceProfile.current(),
                        List.of("faction.alpha", "faction.beta")),
                new PhysicalTransportAuthority(
                        fittedPlan(900d, 3_600d, 1_800d),
                        fittedPlan(900d, 3_600d, 1_800d),
                        new FreightFleetProfile(
                                "stage20e.production-probe.integration-freight.v1",
                                1_000_000d,
                                8,
                                "integration-test explicit freight authority",
                                true)));
    }

    private static BootstrapRequirementProfile bootstrapRequirements() {
        return new BootstrapRequirementProfile(
                "stage20e.production-probe.integration-demand.v1",
                1.0e9d,
                1d,
                List.of(
                        new CommodityRequirement("commodity.feedstock.water_ice", 1.0e9d, 1d),
                        new CommodityRequirement("commodity.feedstock.metallic_ore", 1.0e9d, 1d)));
    }

    private static JumpPlan fittedPlan(double spoolSeconds, double cooldownSeconds, double transitSeconds) {
        return new JumpPlan(
                true,
                JumpFailure.NONE,
                "ftl",
                10_000d,
                1_000d,
                1_000d,
                0d,
                100d,
                spoolSeconds,
                transitSeconds,
                cooldownSeconds,
                50d);
    }

    private static long firstAcceptedTopologySeed(ProbeInputs inputs, long firstSeed, long lastSeed) {
        for (long seed = firstSeed; seed <= lastSeed; seed++) {
            var macro = Stage20MacroGalaxyGeometryGenerator.generate(seed, inputs.macroRequest());
            var topology = Stage20JumpTopologyGenerator.generate(
                    new GalaxyId(20L),
                    "production-probe-test",
                    macro.sectors(),
                    seed,
                    inputs.topologyQuality());
            if (topology.status() == Stage20JumpTopologyGenerationResult.Status.ACCEPTED) {
                return seed;
            }
        }
        throw new AssertionError("No accepted representative Stage-20D topology found in bounded seed corpus");
    }
}
