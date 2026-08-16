package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShieldThreatCouplingTest {
    @Test
    void uncoupledThreatEnergyPassesThroughInsteadOfDisappearing() {
        ShieldFieldRuntime runtime = new ShieldFieldRuntime();
        ShieldFieldRuntime.Definition definition = new ShieldFieldRuntime.Definition(
                "shield", 1_000_000d, 1_000_000d, 0d, 1d, 0d, 0d, 0d, Math.PI);
        ShieldFieldRuntime.State state = ShieldFieldRuntime.State.charged(definition);
        ShieldThreatCoupling.Profile profile = new ShieldThreatCoupling.Profile(0.5d, 0.25d, 0.75d);
        ShieldThreatCoupling coupling = new ShieldThreatCoupling();

        ShieldThreatCoupling.Interaction kinetic = coupling.interact(
                runtime,
                definition,
                state,
                profile,
                ShieldThreatCoupling.ThreatKind.KINETIC,
                800_000d,
                0d,
                1d);

        assertEquals(0.5d, kinetic.couplingFraction(), 0d);
        assertEquals(400_000d, kinetic.absorbedEnergyJ(), 1e-9d);
        assertEquals(400_000d, kinetic.residualEnergyJ(), 1e-9d);
        assertEquals(600_000d, kinetic.fieldInteraction().state().reserveJ(), 1e-9d);
    }

    @Test
    void couplingProfileRejectsInvalidFractions() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShieldThreatCoupling.Profile(-0.1d, 0.5d, 0.5d));
        assertThrows(IllegalArgumentException.class,
                () -> new ShieldThreatCoupling.Profile(0.5d, 1.1d, 0.5d));
    }
}
