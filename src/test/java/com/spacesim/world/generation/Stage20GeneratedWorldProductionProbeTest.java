package com.spacesim.world.generation;

import com.spacesim.ship.ShipEngineeringRuntime.JumpFailure;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Requirement;
import com.spacesim.world.Stage20GeneratedWorldBatchAcceptance;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.FreightFleetProfile;
import com.spacesim.world.calibration.Stage20BootstrapRequirementCalibrationProfile;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile.JumpEdgeCalibrationSample;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalogLoader;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.AcceptanceAuthority;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.InitialInfrastructureProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.PhysicalTransportAuthority;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeInputs;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20GeneratedWorldProductionProbeTest {
    private static final String FREIGHT_REFERENCE = "EARLY_CIVILIAN_FREIGHTER";

    @Test
    void acceptedTopologyMaterializesTheRealDownstreamGenerationChain() {
        ProbeInputs inputs = representativeInputs();
        long seed = firstAcceptedTopologySeed(inputs, 1L, 128L);

        ProbeResult result = Stage20GeneratedWorldProductionProbe.run(seed, inputs);

        assertEquals(Stage20GeneratedWorldProductionProbe.CURRENT_VERSION, result.version());
        assertEquals(seed, result.rootSeed());
        assertEquals(Stage20JumpTopologyGenerationResult.Status.ACCEPTED, result.topology().status());
        assertTrue(result.jumpEdges().isPresent());
        assertTrue(result.localLayouts().isPresent());
        assertTrue(result.physicalHosts().isPresent());
        assertTrue(result.resourceWorld().isPresent());
        assertTrue(result.logisticsReport().isPresent());
        assertTrue(result.supplyThroughput().isPresent());
        assertTrue(result.candidateEvaluations().isPresent());
        assertTrue(result.placement().isPresent());
        assertTrue(result.economicAcceptance().isPresent());

        var topology = result.topology().requireAcceptedTopology();
        int systemCount = topology.systems().size();
        assertEquals(systemCount, result.localLayouts().orElseThrow().size());
        assertEquals(systemCount, result.candidateEvaluations().orElseThrow().size());
        assertEquals(topology.connections().size(), result.jumpEdges().orElseThrow().edges().size());
        assertEquals(
                systemCount * inputs.infrastructure().resourceAnchorCountPerSystem(),
                result.physicalHosts().orElseThrow().hosts().size());
        assertFalse(result.resourceWorld().orElseThrow().occurrences().isEmpty());
        assertEquals(seed, result.seedAcceptance().rootSeed());
        assertEquals(
                Stage20GeneratedWorldSeedAcceptance.CURRENT_VERSION,
                result.seedAcceptance().version());
        assertEquals(Stage20BootstrapRequirementCalibrationProfile.CURRENT_VERSION,
                inputs.acceptance().bootstrapRequirements().version());

        result.localLayouts().orElseThrow().forEach(layout -> {
            assertEquals(topology.neighbors(layout.systemId()).size(), layout.placements().stream()
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
        assertTrue(result.jumpEdges().isEmpty());
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
    void dependencyProjectionCannotSilentlyChangeDerivedBootstrapDemand() {
        var derived = Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();
        ArrayList<Requirement> altered = new ArrayList<>(derived.dependencyRequirements());
        Requirement original = altered.get(0);
        altered.set(0, new Requirement(
                original.commodityId(),
                original.familyId(),
                original.requiredKgPerSecond() * 2d,
                original.maxSupplierRouteTimeS()));

        assertThrows(IllegalArgumentException.class, () -> new AcceptanceAuthority(
                derived.bootstrapRequirements(),
                altered,
                Stage20FactionStartAcceptanceProfile.current(),
                List.of("faction.alpha", "faction.beta")));
    }

    @Test
    void transportFixtureUsesAcceptedStage20ACalibrationInsteadOfArbitraryJumpNumbers() {
        PhysicalTransportAuthority transport = calibratedTransport();
        JumpEdgeCalibrationSample reference = calibratedSample(FREIGHT_REFERENCE);

        assertEquals(reference.translatedMassKg(), transport.loadedOutboundPlan().translatedMassKg(), 0d);
        assertEquals(reference.spoolTimeS().orElseThrow(), transport.loadedOutboundPlan().spoolSeconds(), 0d);
        assertEquals(reference.referenceEdgeTransitTimeS(), transport.loadedOutboundPlan().edgeTransitSeconds(), 0d);
        assertEquals(reference.cooldownS(), transport.loadedOutboundPlan().cooldownSeconds(), 0d);
        assertTrue(transport.fleetProfile().sourceEvidenceId().contains("stage20a"));
        assertTrue(transport.fleetProfile().stage22ReviewRequired());
    }

    private static ProbeInputs representativeInputs() {
        return inputsWithMacroRequest(Stage20MacroGalaxyGeometryGenerator.GenerationRequest.representative());
    }

    private static ProbeInputs inputsWithMacroRequest(Stage20MacroGalaxyGeometryGenerator.GenerationRequest request) {
        var derivedDemand = Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();
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
                        derivedDemand.bootstrapRequirements(),
                        derivedDemand.dependencyRequirements(),
                        Stage20FactionStartAcceptanceProfile.current(),
                        List.of("faction.alpha", "faction.beta")),
                calibratedTransport());
    }

    private static PhysicalTransportAuthority calibratedTransport() {
        JumpPlan plan = calibratedJumpPlan(FREIGHT_REFERENCE);
        FreightFleetProfile fleet = FreightFleetProfile.fromMissionCargoStoresReference(
                Stage20RepresentativePropulsionCatalogLoader.loadDefault(),
                FREIGHT_REFERENCE,
                8);
        return new PhysicalTransportAuthority(plan, plan, fleet);
    }

    private static JumpPlan calibratedJumpPlan(String representativeId) {
        JumpEdgeCalibrationSample sample = calibratedSample(representativeId);
        double requiredEnergy = sample.requiredTranslationEnergyJ().orElseThrow();
        double spoolSeconds = sample.spoolTimeS().orElseThrow();
        return new JumpPlan(
                true,
                JumpFailure.NONE,
                "ftl.calibration." + representativeId,
                sample.translatedMassKg(),
                requiredEnergy,
                requiredEnergy,
                0d,
                requiredEnergy / spoolSeconds,
                spoolSeconds,
                sample.referenceEdgeTransitTimeS(),
                sample.cooldownS(),
                0d);
    }

    private static JumpEdgeCalibrationSample calibratedSample(String representativeId) {
        return Stage20FtlCalibrationProfile.deriveCurrent().samples().stream()
                .filter(value -> value.representativeId().equals(representativeId))
                .filter(value -> value.requiredTranslationEnergyJ().isPresent())
                .filter(value -> value.spoolTimeS().isPresent())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing compatible accepted Stage-20A FTL calibration for " + representativeId));
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
