package com.spacesim.world;

import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SmallCraftMissionStateTest {

    @Test
    void rejectsMultipleActiveMissionsForOnePhysicalCraft() {
        SmallCraftId craft = new SmallCraftId(1L);
        MissionOrder first = mission(1L, craft, MissionStatus.ACTIVE);
        MissionOrder second = mission(2L, craft, MissionStatus.RETURNING);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SmallCraftMissionState(3L, List.of(first, second)));
    }

    @Test
    void allocatorMustRemainAboveEveryMissionIdentity() {
        SmallCraftId craft = new SmallCraftId(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> new SmallCraftMissionState(
                        1L,
                        List.of(mission(1L, craft, MissionStatus.COMPLETE))));
    }

    @Test
    void completedMissionDoesNotBlockLaterActiveMissionForSameCraft() {
        SmallCraftId craft = new SmallCraftId(1L);
        SmallCraftMissionState state = new SmallCraftMissionState(
                2L,
                List.of(mission(1L, craft, MissionStatus.COMPLETE)));

        state = state.add(mission(2L, craft, MissionStatus.ACTIVE));

        assertEquals(3L, state.nextMissionId());
        assertEquals(2L, state.activeMissionFor(craft).orElseThrow().id());
    }

    private static MissionOrder mission(
            long id,
            SmallCraftId craft,
            MissionStatus status) {
        return new MissionOrder(
                id,
                craft,
                OrderSource.AI,
                MissionType.CAP,
                new MissionTarget(TargetKind.AREA, "area.alpha"),
                10L,
                status);
    }
}
