package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Acceptance coverage for the M22.6 common thermal-runtime completion. */
class Stage22CorePairThermalRuntimeProjectionTest {

    @Test
    void mapsAuthoredRadiatorRejectionToCommonCoolantBusWithoutFactionBonus() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        int thermalModules = 0;
        for (ModuleDefinition module : catalog.getModules()) {
            if (module.family() != ModuleFamily.THERMAL_CONTROL) continue;
            thermalModules++;
            assertEquals(
                    module.heatRejectionW(),
                    module.capabilityParameters().get(ShipEngineeringRuntime.COOLANT_BUS_CAPACITY_W),
                    0d,
                    "M22.6 coolant-bus closure must not exceed authored radiator rejection");
        }
        assertTrue(thermalModules >= 2, "both core packages must contribute thermal-control hardware");
    }

    @Test
    void bothStrategicDestroyersRecoverOrdinaryJumpCooldownAndRetainPropulsion() {
        ShipEngineeringCatalog catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        for (String fitId : List.of(
                Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT,
                Stage22CorePairStrategicMobilityProjection.UNION_DESTROYER_STRATEGIC_FIT)) {
            DemonstratorFitDefinition definition = catalog.findDemonstratorFit(fitId);
            InstalledFit fit = InstalledFit.fromDemonstrator(definition);
            ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
            var state = runtime.initialize(fit, fullReactionMass(catalog, definition), DamageState.pristine());
            var plan = runtime.planJump(fit, state, DamageState.pristine());
            assertTrue(plan.allowed(), "strategic destroyer must plan through the ordinary fitted FTL authority");
            state = runtime.commitJump(state, plan);

            for (int second = 0; second < 60; second++) {
                state = runtime.advance(
                        fit,
                        state,
                        DamageState.pristine(),
                        OperatingCommand.idle(),
                        1d).state();
            }

            var burn = runtime.advance(
                    fit,
                    state,
                    DamageState.pristine(),
                    new OperatingCommand(Map.of("core_drive", 1d), Map.of(), Set.of()),
                    1d);
            assertTrue(burn.actualThrustN() > 0d, fitId + " must retain reactor-backed propulsion after cooldown");
            assertTrue(burn.massFlowKgPerS() > 0d, fitId + " must consume physical reaction mass after cooldown");
        }
    }

    private static ConsumableState fullReactionMass(
            ShipEngineeringCatalog catalog,
            DemonstratorFitDefinition fit) {
        ArrayList<ConsumableLoad> loads = new ArrayList<>();
        for (InstalledModuleDefinition assignment : fit.installedModules()) {
            ModuleDefinition module = catalog.findModule(assignment.moduleId());
            for (InterfaceDefinition definition : module.interfaces()) {
                if (definition.kind() != InterfaceKind.REACTION_MASS) continue;
                loads.add(new ConsumableLoad(
                        assignment.mountId(),
                        definition.id(),
                        InterfaceKind.REACTION_MASS,
                        definition.capacity(),
                        definition.capacity(),
                        0L));
            }
        }
        return new ConsumableState(0d, 0d, 0d, 0d, loads);
    }
}