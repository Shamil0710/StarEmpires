package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21EGeneratedMilitaryMobilityAcceptanceTest {
    @Test
    void generatedMilitaryUsesOrdinaryTopologyJumpAndCarriesPhysicalFtlConsequences() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        ShipEngineeringCatalog strategicCatalog =
                Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
        FleetPlacementState beforePlacement = runtime.world().getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(value -> isStrategicMilitary(runtime, strategicCatalog, value))
                .min(Comparator.comparing(FleetPlacementState::id))
                .orElseThrow(() -> new AssertionError("generated world lacks Stage-21 strategic military"));
        FleetId fleetId = beforePlacement.id();
        StarSystemId origin = beforePlacement.systemId();
        StarSystemId destination = runtime.world().getTopology().neighbors(origin).stream()
                .sorted()
                .findFirst()
                .orElseThrow(() -> new AssertionError("generated military origin has no topology neighbor"));
        EngineeringComponent beforeEngineering = engineering(runtime, beforePlacement);
        var beforeState = beforeEngineering.runtimeState;
        InstalledFit beforeFit = beforeEngineering.fit;

        runtime.world().requestFleetJump(fleetId, destination);
        assertTrue(runtime.world().findFleetJump(fleetId).isPresent(),
                "ordinary WorldSimulation request must start the existing FleetJumpService FSM");
        for (int attempt = 0;
                attempt < 800 && runtime.world().findFleetJump(fleetId).isPresent();
                attempt++) {
            runtime.advanceFrame(0.25f);
        }

        assertTrue(runtime.world().findFleetJump(fleetId).isEmpty(),
                "ordinary fitted military jump must complete through approach/spool/transit/arrival");
        FleetPlacementState afterPlacement = runtime.world().findFleet(fleetId).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, afterPlacement.locationKind());
        assertEquals(destination, afterPlacement.systemId());
        assertEquals(fleetId, afterPlacement.id(), "ordinary movement must preserve the persistent FleetId");

        EngineeringComponent afterEngineering = engineering(runtime, afterPlacement);
        assertEquals(beforeFit, afterEngineering.fit, "movement cannot substitute or recreate the fitted ship");
        assertNotEquals(beforeState, afterEngineering.runtimeState,
                "ordinary FTL execution must commit physical engineering state instead of teleporting");
        boolean survivingCostEvidence =
                afterEngineering.runtimeState.sharedBusEnergyJ() < beforeState.sharedBusEnergyJ()
                        || afterEngineering.runtimeState.localHeatJByMount()
                                .getOrDefault("utility_datalink", 0d)
                                > beforeState.localHeatJByMount().getOrDefault("utility_datalink", 0d)
                        || afterEngineering.runtimeState.ftlCooldownSecondsByMount()
                                .getOrDefault("utility_datalink", 0d) > 0d;
        assertTrue(survivingCostEvidence,
                "completed ordinary jump must retain energy, heat or cooldown evidence of physical FTL use");
    }

    private static boolean isStrategicMilitary(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            ShipEngineeringCatalog catalog,
            FleetPlacementState placement) {
        EngineeringComponent engineering = engineeringOrNull(runtime, placement);
        if (engineering == null) return false;
        return catalog.getDemonstratorFits().stream()
                .filter(Stage175ICombatTestContentPack::isStage21StrategicFit)
                .map(InstalledFit::fromDemonstrator)
                .anyMatch(engineering.fit::equals);
    }

    private static EngineeringComponent engineering(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        EngineeringComponent engineering = engineeringOrNull(runtime, placement);
        if (engineering == null) {
            throw new AssertionError("fleet has no EngineeringComponent: " + placement.id());
        }
        return engineering;
    }

    private static EngineeringComponent engineeringOrNull(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement) {
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) return null;
        Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        return entity.getComponent(EngineeringComponent.class);
    }
}
