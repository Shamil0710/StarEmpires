package com.spacesim.ship;

import com.spacesim.components.SensorKnowledgeComponent;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensorKnowledgeRuntimeTest {
    private static final ShipSensorRuntime SENSOR_RUNTIME = new ShipSensorRuntime();
    private static final SensorKnowledgeRuntime KNOWLEDGE_RUNTIME = new SensorKnowledgeRuntime(SENSOR_RUNTIME);

    @Test
    void remoteMeasurementIsInvisibleUntilPhysicalDatalinkLatencyExpires() {
        SensorMeasurement measurement = SENSOR_RUNTIME.observe(
                1L, 9L,
                ShipSensorGeometryTest.passiveThermal(),
                SensorRuntimeState.nominal(),
                new Position2d(0d, 0d),
                new Position2d(1_000_000d, 500_000d),
                ShipSensorGeometryTest.brightTarget(),
                ElectronicWarfareState.empty(),
                10d).measurement().orElseThrow();
        SensorKnowledgeComponent recipient = new SensorKnowledgeComponent();
        DatalinkState link = new DatalinkState(5d, 120d, 10d);

        KNOWLEDGE_RUNTIME.transmit(recipient, measurement, link, 10d);

        assertTrue(KNOWLEDGE_RUNTIME.updateTarget(
                recipient, 9L, link, TrackQualityPolicy.defaultPolicy(), 14.999d).isEmpty());
        TrackState delivered = KNOWLEDGE_RUNTIME.updateTarget(
                recipient, 9L, link, TrackQualityPolicy.defaultPolicy(), 15d).orElseThrow();

        assertFalse(delivered.positionKnown());
        assertEquals(TrackState.InformationState.CLASSIFIED, delivered.informationState());
        assertTrue(recipient.pendingMeasurements().isEmpty());
        assertEquals(1, recipient.receivedMeasurements().size());
    }

    @Test
    void distributedObserversShareMeasurementsThenTriangulateRatherThanGrantRange() {
        Position2d target = new Position2d(1_000_000d, 1_000_000d);
        SensorMeasurement first = SENSOR_RUNTIME.observe(
                1L, 17L, ShipSensorGeometryTest.passiveThermal(), SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), target, ShipSensorGeometryTest.brightTarget(),
                ElectronicWarfareState.empty(), 30d).measurement().orElseThrow();
        SensorMeasurement second = SENSOR_RUNTIME.observe(
                2L, 17L, ShipSensorGeometryTest.passiveThermal(), SensorRuntimeState.nominal(),
                new Position2d(0d, 2_000_000d), target, ShipSensorGeometryTest.brightTarget(),
                ElectronicWarfareState.empty(), 30d).measurement().orElseThrow();
        SensorKnowledgeComponent network = new SensorKnowledgeComponent();
        DatalinkState link = new DatalinkState(2d, 120d, 5d);

        KNOWLEDGE_RUNTIME.transmit(network, first, link, 30d);
        KNOWLEDGE_RUNTIME.transmit(network, second, link, 30d);
        TrackState shared = KNOWLEDGE_RUNTIME.updateTarget(
                network, 17L, link, TrackQualityPolicy.defaultPolicy(), 32d).orElseThrow();

        assertFalse(first.hasRange());
        assertFalse(second.hasRange());
        assertTrue(shared.positionKnown());
        assertEquals(2, shared.contributingObservers());
        assertEquals(1_000_000d, shared.estimatedXM(), 1e-6);
        assertEquals(1_000_000d, shared.estimatedYM(), 1e-6);
    }

    @Test
    void localKnowledgeSnapshotIsDeterministicAndExplicitlyClearedAcrossSystemIdentityDomain() {
        SensorMeasurement measurement = SENSOR_RUNTIME.observe(
                4L, 22L, ShipSensorGeometryTest.activeRadar(), SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), new Position2d(500_000d, 0d),
                ShipSensorGeometryTest.radarTarget(), ElectronicWarfareState.empty(), 50d)
                .measurement().orElseThrow();
        SensorKnowledgeComponent knowledge = new SensorKnowledgeComponent();
        KNOWLEDGE_RUNTIME.receiveLocal(knowledge, measurement);
        KNOWLEDGE_RUNTIME.updateTarget(
                knowledge, 22L, DatalinkState.local(), TrackQualityPolicy.defaultPolicy(), 50d).orElseThrow();

        assertEquals(1, knowledge.tracks().size());
        assertEquals(1, knowledge.receivedMeasurements().size());

        knowledge.clearLocalKnowledge();

        assertTrue(knowledge.tracks().isEmpty());
        assertTrue(knowledge.receivedMeasurements().isEmpty());
        assertTrue(knowledge.pendingMeasurements().isEmpty());
    }
}
