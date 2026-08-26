package com.spacesim.world;

import com.spacesim.world.Stage21HNpcMissionState.KnowledgeKind;
import com.spacesim.world.Stage21HNpcMissionState.MissionContract;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.MissionStatus;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcKnowledgeFact;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage21HNpcMissionStateCodecTest {

    @Test
    void roundTripIsDeterministicAndRetainsRejectedLifecycle() {
        Stage21HNpcMissionState expected = state();

        byte[] first = Stage21HNpcMissionStateCodec.encode(expected);
        Stage21HNpcMissionState decoded = Stage21HNpcMissionStateCodec.decode(first);
        byte[] second = Stage21HNpcMissionStateCodec.encode(decoded);

        assertEquals(expected, decoded);
        assertArrayEquals(first, second);
        assertEquals(MissionStatus.REJECTED, decoded.missions().get(0).status());
    }

    @Test
    void futureCorruptTruncatedAndTrailingPayloadsFailClosed() {
        byte[] valid = Stage21HNpcMissionStateCodec.encode(state());

        byte[] futureFile = valid.clone();
        ByteBuffer.wrap(futureFile).putInt(4, 99);
        assertThrows(IllegalArgumentException.class, () -> Stage21HNpcMissionStateCodec.decode(futureFile));

        byte[] futureSchema = valid.clone();
        ByteBuffer.wrap(futureSchema).putInt(8, Stage21HNpcMissionState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> Stage21HNpcMissionStateCodec.decode(futureSchema));

        byte[] corruptMagic = valid.clone();
        corruptMagic[0] ^= 0x5a;
        assertThrows(IllegalArgumentException.class, () -> Stage21HNpcMissionStateCodec.decode(corruptMagic));

        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IllegalArgumentException.class, () -> Stage21HNpcMissionStateCodec.decode(truncated));

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> Stage21HNpcMissionStateCodec.decode(trailing));
    }

    private static Stage21HNpcMissionState state() {
        NpcKnowledgeFact fact = new NpcKnowledgeFact(
                "fact.derelict", "derelict.alpha", KnowledgeKind.DISCOVERY,
                "DISCOVERY.SPECIAL_LOCATION.DETECTED", 2500,
                "survey.alpha", 3L, -1L);
        NpcState npc = new NpcState(
                "npc.explorer", "npc.explorer.name", NpcRole.EXPLORATION_INTELLIGENCE,
                "faction.test", new StarSystemId(2L), NpcAvailability.AVAILABLE, List.of(fact));
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.INDUSTRY,
                ObjectiveKind.DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST,
                "derelict.alpha|salvage.alpha",
                2L,
                50L,
                "SPECIAL_LOCATION:KNOWN_STATIC_LOCATION");
        MissionContract mission = new MissionContract(
                "mission.stage21h.1",
                MissionTemplate.DERELICT_INVESTIGATION_RECOVERY,
                1,
                npc.npcId(),
                npc.factionContentId(),
                List.of(fact.factId()),
                objective,
                5L,
                30L,
                MissionStatus.REJECTED,
                6L,
                500L,
                0L,
                "rejected",
                List.of());
        return new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                10L,
                2L,
                List.of(npc),
                List.of(mission),
                List.of(),
                List.of());
    }
}
