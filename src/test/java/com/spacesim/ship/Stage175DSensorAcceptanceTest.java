package com.spacesim.ship;

import com.spacesim.components.SensorKnowledgeComponent;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175DSensorAcceptanceTest {
    private static final ShipSensorRuntime SENSOR_RUNTIME = new ShipSensorRuntime();
    private static final SensorKnowledgeRuntime KNOWLEDGE_RUNTIME = new SensorKnowledgeRuntime(SENSOR_RUNTIME);

    @Test
    void playerAndAiKnowledgeNodesProduceIdenticalTracksFromIdenticalPhysicalEvidence() {
        Position2d target = new Position2d(900_000d, 600_000d);
        SensorMeasurement first = SENSOR_RUNTIME.observe(
                101L, 707L, ShipSensorGeometryTest.passiveThermal(), SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), target, ShipSensorGeometryTest.brightTarget(),
                ElectronicWarfareState.empty(), 100d).measurement().orElseThrow();
        SensorMeasurement second = SENSOR_RUNTIME.observe(
                202L, 707L, ShipSensorGeometryTest.passiveThermal(), SensorRuntimeState.nominal(),
                new Position2d(0d, 1_200_000d), target, ShipSensorGeometryTest.brightTarget(),
                ElectronicWarfareState.empty(), 100d).measurement().orElseThrow();
        SensorKnowledgeComponent playerKnowledge = new SensorKnowledgeComponent();
        SensorKnowledgeComponent aiKnowledge = new SensorKnowledgeComponent();
        for (SensorMeasurement measurement : List.of(first, second)) {
            KNOWLEDGE_RUNTIME.receiveLocal(playerKnowledge, measurement);
            KNOWLEDGE_RUNTIME.receiveLocal(aiKnowledge, measurement);
        }

        TrackState playerTrack = KNOWLEDGE_RUNTIME.updateTarget(
                playerKnowledge, 707L, DatalinkState.local(), TrackQualityPolicy.defaultPolicy(), 100d)
                .orElseThrow();
        TrackState aiTrack = KNOWLEDGE_RUNTIME.updateTarget(
                aiKnowledge, 707L, DatalinkState.local(), TrackQualityPolicy.defaultPolicy(), 100d)
                .orElseThrow();

        assertEquals(playerTrack, aiTrack);
        assertTrue(playerTrack.positionKnown());
        assertEquals(2, playerTrack.contributingObservers());
    }

    @Test
    void directBearingEvidenceRemainsRangeUnknownUntilGeometryOrRangingSuppliesIt() {
        SensorMeasurement bearing = SENSOR_RUNTIME.observe(
                303L, 808L, ShipSensorGeometryTest.passiveThermal(), SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), new Position2d(750_000d, 250_000d),
                ShipSensorGeometryTest.brightTarget(), ElectronicWarfareState.empty(), 200d)
                .measurement().orElseThrow();

        assertFalse(bearing.hasRange());
        TrackState track = SENSOR_RUNTIME.fuse(
                808L, List.of(bearing), DatalinkState.local(), TrackQualityPolicy.defaultPolicy(), 200d);
        assertFalse(track.positionKnown());
        assertFalse(track.covariance().hasRangeCovariance());
    }
}
