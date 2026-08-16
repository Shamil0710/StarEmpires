package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldFieldRuntimeTest {
    private static final ShieldFieldRuntime.Definition DEFINITION = new ShieldFieldRuntime.Definition(
            "shield_mount", 1_000_000d, 500_000d, 200_000d, 0.8d, 0.1d, 2d, 0d, Math.PI / 2d);

    @Test
    void coveragePowerReserveHeatCollapseAndRechargeArePhysical() {
        ShieldFieldRuntime runtime = new ShieldFieldRuntime();
        ShieldFieldRuntime.State initial = ShieldFieldRuntime.State.charged(DEFINITION);

        ShieldFieldRuntime.Interaction outside = runtime.interact(
                DEFINITION, initial, 100_000d, Math.PI, 1d);
        assertFalse(outside.covered());
        assertEquals(0d, outside.absorbedEnergyJ());
        assertEquals(100_000d, outside.residualEnergyJ());

        ShieldFieldRuntime.Interaction first = runtime.interact(
                DEFINITION, initial, 800_000d, 0d, 1d);
        assertTrue(first.covered());
        assertEquals(500_000d, first.absorbedEnergyJ(), 1e-9d);
        assertEquals(300_000d, first.residualEnergyJ(), 1e-9d);
        assertEquals(50_000d, first.generatedHeatJ(), 1e-9d);
        assertFalse(first.state().collapsed());

        ShieldFieldRuntime.Interaction second = runtime.interact(
                DEFINITION, first.state(), 900_000d, 0d, 2d);
        assertEquals(500_000d, second.absorbedEnergyJ(), 1e-9d);
        assertTrue(second.state().collapsed());
        assertEquals(0d, second.state().reserveJ(), 1e-9d);

        ShieldFieldRuntime.State locked = runtime.step(DEFINITION, second.state(), 1d, 200_000d);
        assertTrue(locked.collapsed());
        assertEquals(0d, locked.reserveJ(), 1e-9d);

        ShieldFieldRuntime.State restarted = runtime.step(DEFINITION, locked, 1.5d, 200_000d);
        assertFalse(restarted.collapsed());
        assertEquals(240_000d, restarted.reserveJ(), 1e-9d);
    }

    @Test
    void emitterDamageOnlyRemovesCapability() {
        ShieldFieldRuntime runtime = new ShieldFieldRuntime();
        ShieldFieldRuntime.State healthy = ShieldFieldRuntime.State.charged(DEFINITION);
        ShieldFieldRuntime.State damaged = runtime.withEmitterIntegrity(DEFINITION, healthy, 0.5d);

        assertEquals(500_000d, damaged.reserveJ(), 1e-9d);
        ShieldFieldRuntime.Interaction interaction = runtime.interact(
                DEFINITION, damaged, 900_000d, 0d, 1d);
        assertEquals(250_000d, interaction.absorbedEnergyJ(), 1e-9d);

        ShieldFieldRuntime.State destroyed = runtime.withEmitterIntegrity(DEFINITION, damaged, 0d);
        assertTrue(destroyed.collapsed());
        assertEquals(0d, destroyed.reserveJ(), 1e-9d);
        assertEquals(900_000d,
                runtime.interact(DEFINITION, destroyed, 900_000d, 0d, 1d).residualEnergyJ(), 1e-9d);
    }

    @Test
    void invalidStateAndInputsRejectInsteadOfClampingSilently() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShieldFieldRuntime.Definition("", 1d, 1d, 1d, 1d, 0d, 0d, 0d, 1d));
        assertThrows(IllegalArgumentException.class,
                () -> new ShieldFieldRuntime.State(-1d, 0d, false, 0d, 1d));
        assertThrows(IllegalArgumentException.class,
                () -> new ShieldFieldRuntime().interact(DEFINITION, ShieldFieldRuntime.State.charged(DEFINITION), -1d, 0d, 1d));
    }
}
