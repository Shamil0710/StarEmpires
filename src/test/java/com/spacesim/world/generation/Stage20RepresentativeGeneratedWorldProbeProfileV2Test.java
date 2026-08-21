package com.spacesim.world.generation;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativeGeneratedWorldProbeProfileV2Test {
    @Test
    void candidateKeepsEveryRepresentativeWorldInputExceptBootstrapTimeAuthority() {
        var v1 = Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();
        var v2 = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();

        assertEquals(v1.version(), v2.sourceRepresentativeProfileVersion());
        assertEquals(v1.inputs().macroRequest(), v2.inputs().macroRequest());
        assertEquals(v1.inputs().topologyQuality(), v2.inputs().topologyQuality());
        assertEquals(v1.inputs().infrastructure(), v2.inputs().infrastructure());
        assertEquals(v1.inputs().transport(), v2.inputs().transport());
        assertEquals(v1.inputs().acceptance().factionStartProfile(),
                v2.inputs().acceptance().factionStartProfile());
        assertEquals(v1.inputs().acceptance().stableFactionIds(),
                v2.inputs().acceptance().stableFactionIds());

        Map<String, CommodityRequirement> oldRequirements =
                v1.inputs().acceptance().bootstrapRequirements().essentialCommodities().stream()
                        .collect(Collectors.toMap(CommodityRequirement::commodityId, Function.identity()));
        Map<String, CommodityRequirement> correctedRequirements =
                v2.inputs().acceptance().bootstrapRequirements().essentialCommodities().stream()
                        .collect(Collectors.toMap(CommodityRequirement::commodityId, Function.identity()));
        assertEquals(oldRequirements.keySet(), correctedRequirements.keySet());
        oldRequirements.forEach((commodityId, oldRequirement) -> {
            CommodityRequirement corrected = correctedRequirements.get(commodityId);
            assertEquals(oldRequirement.minSupplierThroughputKgPerSecond(),
                    corrected.minSupplierThroughputKgPerSecond(), 0d);
            assertNotEquals(oldRequirement.maxSupplierRouteTimeS(), corrected.maxSupplierRouteTimeS());
        });
        assertTrue(v2.stage22ReviewRequired());
    }
}
