package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage23RuntimeEngineeringCatalogLoader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage23CarrierEngineeringRuntimeTest {
    @Test void combinedVocabularyRetainsEveryAcceptedDefinitionWithoutChangingSharedIds() {
        var combined = Stage23RuntimeEngineeringCatalogLoader.loadDefault();
        for (var original : originals()) {
            original.getMaterials().forEach(value -> assertEquals(value, combined.findMaterial(value.id())));
            original.getResponseSurfaces().forEach(value -> assertEquals(value, combined.findResponseSurface(value.id())));
            original.getProtectionStacks().forEach(value -> assertEquals(value, combined.findProtectionStack(value.id())));
            original.getHulls().forEach(value -> assertEquals(value, combined.findHull(value.id())));
            original.getModules().forEach(value -> assertEquals(value, combined.findModule(value.id())));
            original.getDemonstratorFits().forEach(value -> assertEquals(value, combined.findDemonstratorFit(value.id())));
        }
    }

    @Test void ordinaryRuntimeMatchesAcceptedFreightAndCraftPhysicsIncludingFinitePropellantAndCoasting() {
        var ordinary = new ProductionEngineeringRuntimeResolver();
        for (var catalog : originals()) for (var definition : catalog.getDemonstratorFits()) {
            var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(definition);
            var loads = new ArrayList<ShipEngineeringState.ConsumableLoad>();
            for (var installed : fit.installedModules()) for (var iface : catalog.findModule(installed.moduleId()).interfaces()) {
                if (iface.kind() == ShipEngineeringCatalog.InterfaceKind.REACTION_MASS)
                    loads.add(new ShipEngineeringState.ConsumableLoad(installed.mountId(), iface.id(), iface.kind(), iface.capacity() * .5, iface.capacity() * .5, 0));
            }
            var state = new ShipEngineeringRuntime(catalog).initialize(fit, new ShipEngineeringState.ConsumableState(0, 0, 0, 0, loads));
            var live = new EngineeringComponent(fit, state);
            var reference = new EngineeringComponent(fit, state);
            var accepted = new ProductionEngineeringRuntimeResolver(List.of(catalog));
            assertEquals(accepted.derive(reference), ordinary.derive(live), definition.id());
            assertEquals(state, live.runtimeState, "Reading capability must not manufacture consumables");
            assertEquals(accepted.advancePropulsion(reference, .5, .25), ordinary.advancePropulsion(live, .5, .25), definition.id());
            assertEquals(reference.runtimeState, live.runtimeState, definition.id());
            assertTrue(live.runtimeState.consumables().reactionMassKg() <= state.consumables().reactionMassKg());
            double remaining = live.runtimeState.consumables().reactionMassKg();
            assertEquals(accepted.advancePropulsion(reference, 0, .25), ordinary.advancePropulsion(live, 0, .25), definition.id());
            assertEquals(remaining, live.runtimeState.consumables().reactionMassKg());
        }
    }

    private static List<ShipEngineeringCatalog> originals() {
        return List.of(Stage22FreightStrategicEngineeringCatalogLoader.loadDefault(), Stage228SmallCraftEngineeringCatalogLoader.loadDefault());
    }
}
