package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairStrategicMobilityProjection;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionEngineeringRuntimeResolverTest {
    private static final double EPSILON = 1.0e-9d;

    @Test
    void ordinaryPropulsionConsumesFiniteReactionMassThroughProductionRuntime() {
        Fixture fixture = fixture(1_000_000d);
        double before = fixture.component().runtimeState.consumables().reactionMassKg();

        var result = fixture.resolver().advancePropulsion(fixture.component(), 1d, 0.25d);

        assertTrue(result.actualThrustN() > 0d);
        assertTrue(result.massFlowKgPerS() > 0d);
        assertTrue(fixture.component().runtimeState.consumables().reactionMassKg() < before);
    }

    @Test
    void inertialCoastWithZeroThrottleConsumesNoReactionMass() {
        Fixture fixture = fixture(1_000_000d);
        double before = fixture.component().runtimeState.consumables().reactionMassKg();

        var result = fixture.resolver().advancePropulsion(fixture.component(), 0d, 1d);

        assertEquals(0d, result.actualThrustN(), EPSILON);
        assertEquals(before, fixture.component().runtimeState.consumables().reactionMassKg(), EPSILON);
    }

    @Test
    void smallTickDeltaVUsesPartialThrottleAndBurnsLessThanFullTick() {
        Fixture partial = fixture(1_000_000d);
        Fixture full = fixture(1_000_000d);
        double deltaSeconds = 0.25d;
        var derived = partial.resolver().derive(partial.component());
        double fullTickDeltaV = derived.availableThrustN() / derived.totalMassKg() * deltaSeconds;
        double requestedDeltaV = fullTickDeltaV * 0.2d;

        double throttle = partial.resolver().throttleForDeltaV(
                partial.component(), requestedDeltaV, deltaSeconds);
        assertTrue(throttle > 0d && throttle < 1d);

        double beforePartial = partial.component().runtimeState.consumables().reactionMassKg();
        partial.resolver().advancePropulsion(partial.component(), throttle, deltaSeconds);
        double partialBurn = beforePartial
                - partial.component().runtimeState.consumables().reactionMassKg();

        double beforeFull = full.component().runtimeState.consumables().reactionMassKg();
        full.resolver().advancePropulsion(full.component(), 1d, deltaSeconds);
        double fullBurn = beforeFull - full.component().runtimeState.consumables().reactionMassKg();

        assertTrue(partialBurn > 0d);
        assertTrue(partialBurn < fullBurn,
                "a fractional final velocity tick must not burn a full-throttle tick of reaction mass");
    }

    @Test
    void deltaVPreviewDoesNotMutateAuthoritativeStateAndEmptyTankFailsClosed() {
        Fixture fueled = fixture(1_000_000d);
        double before = fueled.component().runtimeState.consumables().reactionMassKg();

        var preview = fueled.resolver().planDeltaV(fueled.component(), 100d);

        assertTrue(preview.feasible());
        assertTrue(preview.resultingState().consumables().reactionMassKg() < before);
        assertEquals(before, fueled.component().runtimeState.consumables().reactionMassKg(), EPSILON,
                "planning must not spend authoritative propellant");

        Fixture empty = fixture(0d);
        var rejected = empty.resolver().planDeltaV(empty.component(), 1d);
        assertFalse(rejected.feasible());
        assertEquals(0d, rejected.resultingState().consumables().reactionMassKg(), EPSILON);
    }

    private static Fixture fixture(double reactionMassKg) {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var definition = catalog.findDemonstratorFit(
                Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT);
        InstalledFit fit = InstalledFit.fromDemonstrator(definition);
        ArrayList<ConsumableLoad> loads = new ArrayList<>();
        for (var installed : fit.installedModules()) {
            var module = catalog.findModule(installed.moduleId());
            for (var iface : module.interfaces()) {
                if (iface.kind() != InterfaceKind.REACTION_MASS) {
                    continue;
                }
                double load = Math.min(reactionMassKg, iface.capacity());
                loads.add(new ConsumableLoad(
                        installed.mountId(), iface.id(), iface.kind(), load, load, 0L));
            }
        }
        ConsumableState consumables = new ConsumableState(0d, 0d, 0d, 0d, List.copyOf(loads));
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        var state = runtime.initialize(fit, consumables, DamageState.pristine());
        EngineeringComponent component = new EngineeringComponent(fit, state);
        return new Fixture(
                new ProductionEngineeringRuntimeResolver(List.of(catalog)),
                component);
    }

    private record Fixture(
            ProductionEngineeringRuntimeResolver resolver,
            EngineeringComponent component) { }
}
