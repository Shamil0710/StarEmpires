package com.spacesim.world;

import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability;
import com.spacesim.ship.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage23StationBroadcastRuntimeTest {
    @Test void onlyPaidPhysicallyDecodableRadioExposesTheAdvertisedStationPosition() {
        var runtime = new Stage23StationBroadcastRuntime();
        var object = new Stage20DiscoveryKnowledgeState.StaticObjectRef(new StarSystemId(1),
                Stage20DiscoveryKnowledgeState.StaticObjectKind.INFRASTRUCTURE, "station");
        var position = LocalPhysicalPosition.origin().translated(9_000_000d, 0);
        var budget = new ManufacturingCapability("actual-line", Set.of("powered"), 1000d, 25d, 4d).openInterval(1);
        var transmitter = new Stage23StationBroadcastRuntime.Transmitter(750d, 500d);
        var packet = runtime.emit(object, "station.infrastructure.industrial", position, 10, transmitter, budget, 250d, 0).orElseThrow();
        assertEquals(250d, budget.remainingEnergyJ()); assertEquals(25d, budget.remainingWorkSeconds());
        assertEquals(4d, budget.remainingMaintenanceWorkSeconds());
        assertTrue(runtime.emit(object, "station.infrastructure.industrial", position, 10, transmitter, budget, 250d, 0).isEmpty());
        assertEquals(250d, budget.remainingEnergyJ());
        var received = runtime.receive(packet, 20, LocalPhysicalPosition.origin(), radio(), SensorRuntimeState.nominal(), ElectronicWarfareState.empty(), 1).orElseThrow();
        assertEquals(object, received.object()); assertEquals(position, received.knownLocation().orElseThrow());
        assertEquals(Stage20DiscoveryKnowledgeState.DiscoverySource.PERSISTENT_INFRASTRUCTURE_BROADCAST, received.evidence().source());
        assertEquals(Stage20DiscoveryKnowledgeState.ResourceKnowledge.none(), received.resourceKnowledge());
        assertTrue(runtime.receive(packet, 20, LocalPhysicalPosition.origin().translated(1e15, 0), radio(),
                SensorRuntimeState.nominal(), ElectronicWarfareState.empty(), 1).isEmpty());
        assertTrue(runtime.receive(packet, 20, LocalPhysicalPosition.origin(), radio(),
                new SensorRuntimeState(false, false, 1, 1), ElectronicWarfareState.empty(), 1).isEmpty());
        var jammed = new ElectronicWarfareState(List.of(new ElectronicWarfareState.NoiseJammer(30, 0, 0, 1e6, 1, 1)), List.of());
        assertTrue(runtime.receive(packet, 20, LocalPhysicalPosition.origin(), radio(), SensorRuntimeState.nominal(), jammed, 1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> runtime.receive(packet, 20, LocalPhysicalPosition.origin(), radio(),
                SensorRuntimeState.nominal(), ElectronicWarfareState.empty(), 2));
    }

    @Test void unavailableCoolingDoesNotSpendEnergyAndInvalidTransmittersCannotGrantPackets() {
        var runtime = new Stage23StationBroadcastRuntime();
        var object = new Stage20DiscoveryKnowledgeState.StaticObjectRef(new StarSystemId(1),
                Stage20DiscoveryKnowledgeState.StaticObjectKind.INFRASTRUCTURE, "station");
        var budget = new ManufacturingCapability("line", Set.of("powered"), 1000, 1, 1).openInterval(1);
        assertTrue(runtime.emit(object, "station.infrastructure.industrial", LocalPhysicalPosition.origin(), 10,
                new Stage23StationBroadcastRuntime.Transmitter(750, 500), budget, 249, 0).isEmpty());
        assertEquals(1000, budget.remainingEnergyJ());
        assertThrows(IllegalArgumentException.class, () -> new Stage23StationBroadcastRuntime.Transmitter(10, 11));
        assertThrows(IllegalArgumentException.class, () -> new Stage23StationBroadcastRuntime.Transmitter(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Stage23StationBroadcastRuntime.Transmitter(Double.NaN, 1));
    }

    private static SensorDefinition radio() {
        return new SensorDefinition("radio", SensorDefinition.Mode.PASSIVE_RADIO, SignatureState.Channel.RADAR,
                10, 1e-16, 5, 20, 100, 500, .001, .001, 0, 1, 0, 0, 1, 0, 0);
    }
}
