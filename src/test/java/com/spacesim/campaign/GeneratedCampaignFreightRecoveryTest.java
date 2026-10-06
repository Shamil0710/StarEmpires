package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.simulation.GeneratedWorldFreightAutopilot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignFreightRecoveryTest {
    @Test void anOutboundFreighterWaitsForRealFtlCooldownWithoutStoppingTheCampaignOrSpendingApproachFuel() {
        var session = GeneratedCampaignSession.create(1);
        for (int i = 0; i < 4; i++) session.advanceFrame(session.fixedStepSeconds());
        var runtime = session.runtime();
        var freight = runtime.freight().capture().freighters().stream()
                .filter(f -> f.phase() == FreightPhase.OUTBOUND && runtime.world().findFleetJump(f.fleetId()).isEmpty())
                .findFirst().orElseThrow();
        var placement = runtime.world().findFleet(freight.fleetId()).orElseThrow();
        var component = runtime.world().findSession(placement.systemId()).orElseThrow().getEntityRegistry()
                .require(placement.localEntityId()).getComponent(EngineeringComponent.class);
        var plan = runtime.world().previewFittedFleetJump(freight.fleetId()).orElseThrow();
        assertTrue(plan.allowed());
        var original = component.runtimeState;
        var cooldown = new java.util.TreeMap<>(original.ftlCooldownSecondsByMount());
        cooldown.put(plan.mountId(), 2d);
        component.setRuntimeState(new RuntimeState(original.consumables(), original.sharedBusEnergyJ(),
                original.shipHeatStoredJ(), original.localHeatJByMount(), original.thrustLimitNByMount(),
                original.coolantBusCapacityW(), cooldown));
        var waiting = component.runtimeState;
        var physical = runtime.arrival().materialization(placement.systemId()).physicalState(placement.localEntityId()).orElseThrow();
        assertDoesNotThrow(() -> new GeneratedWorldFreightAutopilot(runtime).advance(.4));
        assertTrue(runtime.world().findFleetJump(freight.fleetId()).isEmpty());
        assertEquals(waiting, component.runtimeState, "The policy must wait, without issuing energy or cooling down the drive itself");
        assertEquals(physical, runtime.arrival().materialization(placement.systemId()).physicalState(placement.localEntityId()).orElseThrow());
        var saved = session.captureState();
        var resumed = GeneratedCampaignSession.restore(saved);
        assertEquals(saved, resumed.captureState());
        for (int i = 0; i < 24; i++) {
            assertDoesNotThrow(() -> session.advanceFrame(session.fixedStepSeconds()));
            assertDoesNotThrow(() -> resumed.advanceFrame(resumed.fixedStepSeconds()));
        }
        assertEquals(session.captureState(), resumed.captureState(), "Recovery must survive saving and keep the same physical continuation");
        assertTrue(runtime.world().findFleetJump(freight.fleetId()).isPresent(),
                "Normal world ticks must recover the drive and the ordinary policy must resume the next hop");
    }
}
