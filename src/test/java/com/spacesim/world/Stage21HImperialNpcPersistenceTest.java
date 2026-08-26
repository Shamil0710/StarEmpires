package com.spacesim.world;

import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage21HImperialNpcPersistenceTest {

    @Test
    void allSixRecurringRolesLocationAndAvailabilityRoundTripDeterministically() {
        StarSystemId initialPosting = new StarSystemId(11L);
        StarSystemId displacedPosting = new StarSystemId(12L);
        ArrayList<NpcState> contacts = new ArrayList<>(
                Stage21HImperialGoldSlice.recurringImperialContacts(initialPosting));
        NpcState displaced = contacts.get(contacts.size() - 1);
        contacts.set(contacts.size() - 1, new NpcState(
                displaced.npcId(),
                displaced.nameKey(),
                displaced.role(),
                displaced.factionContentId(),
                displacedPosting,
                NpcAvailability.DISPLACED,
                displaced.knowledge()));
        Stage21HNpcMissionState state = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                20L,
                1L,
                contacts,
                List.of(),
                List.of(),
                List.of());

        byte[] first = Stage21HNpcMissionStateCodec.encode(state);
        Stage21HNpcMissionState decoded = Stage21HNpcMissionStateCodec.decode(first);
        byte[] second = Stage21HNpcMissionStateCodec.encode(decoded);

        assertArrayEquals(first, second);
        assertEquals(state, decoded);
        assertEquals(EnumSet.allOf(NpcRole.class), decoded.npcs().stream()
                .map(NpcState::role)
                .collect(Collectors.toSet()));
        assertEquals(displacedPosting, decoded.npcs().stream()
                .filter(value -> value.npcId().equals(displaced.npcId()))
                .findFirst().orElseThrow().locationSystemId());
        assertEquals(NpcAvailability.DISPLACED, decoded.npcs().stream()
                .filter(value -> value.npcId().equals(displaced.npcId()))
                .findFirst().orElseThrow().availability());
    }
}
