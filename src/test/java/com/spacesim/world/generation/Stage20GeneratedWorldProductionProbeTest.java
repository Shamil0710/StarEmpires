package com.spacesim.world.generation;

import com.spacesim.world.GalaxyId;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Requirement;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.calibration.Stage20BootstrapRequirementCalibrationProfile;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.AcceptanceAuthority;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeInputs;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

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
                representativeInputs().acceptance().stableFactionIds()));
    }

    @Test
    void representativeTransportUsesAcceptedStage20ACalibrationInsteadOfArbitraryJumpNumbers() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();
        var transport = profile.inputs().transport();
        var reference = Stage20FtlCalibrationProfile.deriveCurrent().samples().stream()
                .filter(value -> value.representativeId().equals(profile.freightReferenceClass()))
                .filter(value -> value.requiredTranslationEnergyJ().isPresent())
                .filter(value -> value.spoolTimeS().isPresent())
                .findFirst()
                .orElseThrow();

        assertEquals(reference.translatedMassKg(), transport.loadedOutboundPlan().translatedMassKg(), 0d);
        assertEquals(reference.spoolTimeS().orElseThrow(), transport.loadedOutboundPlan().spoolSeconds(), 0d);
        assertEquals(reference.referenceEdgeTransitTimeS(), transport.loadedOutboundPlan().edgeTransitSeconds(), 0d);
        assertEquals(reference.cooldownS(), transport.loadedOutboundPlan().cooldownSeconds(), 0d);
        assertTrue(transport.fleetProfile().sourceEvidenceId().contains("stage20a"));
        assertTrue(transport.fleetProfile().stage22ReviewRequired());
    }

    private static ProbeInputs representativeInputs() {
        return Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent().inputs();
    }

    private static ProbeInputs inputsWithMacroRequest(Stage20MacroGalaxyGeometryGenerator.GenerationRequest request) {
        ProbeInputs current = representativeInputs();
        return new ProbeInputs(
                request,
                current.topologyQuality(),
                current.infrastructure(),
                current.acceptance(),
                current.transport());
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
