package com.spacesim.world.generation;

import com.spacesim.world.calibration.Stage20BootstrapRequirementCalibrationProfile;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalogLoader;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativeGeneratedWorldProbeProfileTest {
    @Test
    void currentProfileIsDeterministicAndMakesRepresentativePolicyExplicit() {
        var first = Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();
        var second = Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20RepresentativeGeneratedWorldProbeProfile.CURRENT_VERSION, first.version());
        assertTrue(first.stage22ReviewRequired());
        assertEquals(Stage20MacroGalaxyGeometryGenerator.GenerationRequest.representative(),
                first.inputs().macroRequest());
        assertEquals(Stage20RepresentativeGeneratedWorldProbeProfile.INFRASTRUCTURE_POLICY_VERSION,
                first.inputs().infrastructure().version());
        assertEquals(Stage20RepresentativeGeneratedWorldProbeProfile.RESOURCE_ANCHOR_COUNT_PER_SYSTEM,
                first.inputs().infrastructure().resourceAnchorCountPerSystem());
        assertEquals("station.infrastructure.trade_logistics_hub",
                first.inputs().infrastructure().majorHubArchetypeId());
        assertEquals(List.of(
                        "station.infrastructure.frontier_multipurpose",
                        "station.infrastructure.high_tech_hub",
                        "station.infrastructure.industrial_station",
                        "station.infrastructure.refinery_complex"),
                first.inputs().infrastructure().industrialStationArchetypeIds());
        assertEquals(List.of("faction.alpha", "faction.beta"),
                first.inputs().acceptance().stableFactionIds());
    }

    @Test
    void physicalConsequencesRetainCurrentStage18AndStage20AAuthority() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();
        var demand = Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();
        var topology = Stage20TopologyQualityCalibrationProfile.deriveCurrent();
        var ftl = Stage20FtlCalibrationProfile.deriveCurrent();
        var propulsion = Stage20RepresentativePropulsionCatalogLoader.loadDefault();
        var sample = ftl.samples().stream()
                .filter(value -> value.representativeId().equals(
                        Stage20RepresentativeGeneratedWorldProbeProfile.FREIGHT_REFERENCE_CLASS))
                .filter(value -> value.requiredTranslationEnergyJ().isPresent())
                .filter(value -> value.spoolTimeS().isPresent())
                .findFirst()
                .orElseThrow();

        assertEquals(demand.version(), profile.bootstrapRequirementVersion());
        assertEquals(Stage20FactionStartAcceptanceProfile.CURRENT_VERSION, profile.factionStartProfileVersion());
        assertEquals(topology.version(), profile.topologyQualityVersion());
        assertEquals(ftl.version(), profile.ftlCalibrationVersion());
        assertEquals(propulsion.version(), profile.propulsionReferenceVersion());
        assertEquals(Stage20RepresentativeGeneratedWorldProbeProfile.ACTIVE_FREIGHTER_COUNT,
                profile.activeFreighterCount());
        assertEquals(sample.translatedMassKg(), profile.inputs().transport().loadedOutboundPlan().translatedMassKg(), 0d);
        assertEquals(sample.spoolTimeS().orElseThrow(), profile.inputs().transport().loadedOutboundPlan().spoolSeconds(), 0d);
        assertEquals(sample.referenceEdgeTransitTimeS(),
                profile.inputs().transport().loadedOutboundPlan().edgeTransitSeconds(), 0d);
        assertEquals(sample.cooldownS(), profile.inputs().transport().loadedOutboundPlan().cooldownSeconds(), 0d);
        assertTrue(profile.inputs().transport().fleetProfile().stage22ReviewRequired());
    }
}
