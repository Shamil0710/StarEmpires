package com.spacesim.persistence;

import com.spacesim.components.SensorKnowledgeComponent;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Generated-world sensor extension preserves explicit knowledge and rejects invalid references. */
@Tag("slow")
class Stage20GeneratedWorldSensorPersistenceTest {
    @Test void sensorExtensionIsBoundedAndVersionFourRetainsItsSchedulerWithoutNewKnowledge() {
        var runtime = Stage20PlayableGeneratedWorldFactory.create(1L).runtime();
        var baseline = runtime.captureState();
        byte[] current = Stage20GeneratedWorldRuntimePersistenceCodec.encode(baseline);
        assertEquals(0, ByteBuffer.wrap(current).getInt(current.length - 4));
        byte[] v4 = Arrays.copyOf(current, current.length - 4);
        ByteBuffer.wrap(v4).putInt(4, 4);
        assertEquals(baseline, Stage20GeneratedWorldRuntimePersistenceCodec.decode(v4));

        var system = baseline.worldState().systems().stream()
                .filter(value -> !value.simulationState().entities().isEmpty()).findFirst().orElseThrow();
        var id = system.simulationState().entities().get(0).id();
        runtime.world().findSession(system.systemId()).orElseThrow().getEntityRegistry().require(id)
                .add(new SensorKnowledgeComponent());
        var state = runtime.captureState();
        byte[] bytes = Stage20GeneratedWorldRuntimePersistenceCodec.encode(state);
        assertEquals(state, Stage20GeneratedWorldRuntimePersistenceCodec.decode(bytes));
        int start = bytes.length - 32; // count, system/entity IDs, three empty knowledge lists
        assertEquals(1, ByteBuffer.wrap(bytes).getInt(start));
        byte[] missingEntity = bytes.clone();
        ByteBuffer.wrap(missingEntity).putLong(start + 12, Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> Stage20GeneratedWorldRuntimePersistenceCodec.decode(missingEntity));
        byte[] missingSystem = bytes.clone();
        ByteBuffer.wrap(missingSystem).putLong(start + 4, Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> Stage20GeneratedWorldRuntimePersistenceCodec.decode(missingSystem));
        byte[] badCount = bytes.clone();
        ByteBuffer.wrap(badCount).putInt(start, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> Stage20GeneratedWorldRuntimePersistenceCodec.decode(badCount));
        byte[] duplicate = Arrays.copyOf(bytes, bytes.length + 28);
        ByteBuffer.wrap(duplicate).putInt(start, 2);
        System.arraycopy(bytes, start + 4, duplicate, bytes.length, 28);
        assertThrows(IllegalArgumentException.class, () -> Stage20GeneratedWorldRuntimePersistenceCodec.decode(duplicate));
        assertThrows(IllegalArgumentException.class, () -> Stage20GeneratedWorldRuntimePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> Stage20GeneratedWorldRuntimePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
    }
}
