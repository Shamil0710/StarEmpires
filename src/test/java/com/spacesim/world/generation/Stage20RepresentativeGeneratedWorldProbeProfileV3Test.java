package com.spacesim.world.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentativeGeneratedWorldProbeProfileV3Test {
    @Test
    void v3PreservesV2WorldAndAddsOnlyExplicitCoordinatedFreightPolicy() {
        var v2 = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        var v3 = Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent();

        assertEquals(v2.version(), v3.sourceRepresentativeProfileVersion());
        assertEquals(v2.inputs(), v3.inputs());
        assertEquals(13, v3.coordinatedFreightAcceptance().requiredFreighterCountPerFactionStart());
        assertEquals(2_000, v3.coordinatedFreightAcceptance().searchNodeBudgetPerCommodity());
        assertTrue(v3.stage22ReviewRequired());
    }
}
