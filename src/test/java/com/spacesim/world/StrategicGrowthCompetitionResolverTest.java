package com.spacesim.world;

import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicGrowthCompetitionResolverTest {
    private static final StarSystemId SOURCE = new StarSystemId(1L);
    private static final StarSystemId TARGET = new StarSystemId(2L);

    @Test
    void earlierPhysicalCompletionWinsRegardlessOfPlanId() {
        StrategicGrowthState.Plan slowLowId = plan("faction.a", 1L, 11L);
        StrategicGrowthState.Plan fastHighId = plan("faction.b", 9L, 22L);

        assertEquals(
                fastHighId.id(),
                StrategicGrowthCompetitionResolver.chooseWinner(
                        TARGET,
                        "",
                        List.of(slowLowId, fastHighId),
                        List.of(
                                completedProject(slowLowId, 200L, 101L),
                                completedProject(fastHighId, 100L, 102L)))
                        .orElseThrow());
    }

    @Test
    void equalCompletionTickUsesStablePlanId() {
        StrategicGrowthState.Plan first = plan("faction.a", 3L, 31L);
        StrategicGrowthState.Plan second = plan("faction.b", 1L, 32L);

        assertEquals(
                first.id(),
                StrategicGrowthCompetitionResolver.chooseWinner(
                        TARGET,
                        "",
                        List.of(second, first),
                        List.of(
                                completedProject(second, 100L, 201L),
                                completedProject(first, 100L, 202L)))
                        .orElseThrow());
    }

    @Test
    void foreignControllerCannotBeAutoConquered() {
        StrategicGrowthState.Plan challenger = plan("faction.a", 1L, 41L);

        assertTrue(StrategicGrowthCompetitionResolver.chooseWinner(
                TARGET,
                "faction.third",
                List.of(challenger),
                List.of(completedProject(challenger, 100L, 301L)))
                .isEmpty());
    }

    private static StrategicGrowthState.Plan plan(String owner, long sequence, long projectId) {
        return new StrategicGrowthState.Plan(
                new StrategicGrowthState.PlanId(owner, sequence),
                SOURCE,
                TARGET,
                StrategicGrowthState.Reason.BALANCED,
                "station.agrodome",
                new ConstructionProjectId(projectId),
                0,
                List.of(),
                List.of(new StrategicGrowthState.StockTarget("item.steel", 1)),
                1_000L,
                StrategicGrowthState.Status.EXECUTING,
                0L,
                1L,
                -1L);
    }

    private static ConstructionProjectState completedProject(
            StrategicGrowthState.Plan plan,
            long completedTick,
            long stationEntityId) {
        return new ConstructionProjectState(
                plan.anchorProjectId(),
                plan.id().ownerContentId(),
                plan.anchorArchetypeContentId(),
                TARGET,
                0f,
                0f,
                null,
                List.of(new ConstructionMaterialState("item.steel", 1, 1)),
                1_000L,
                0L,
                1L,
                ConstructionProjectStatus.COMPLETED,
                0L,
                completedTick,
                1L,
                completedTick,
                new EntityId(stationEntityId));
    }
}
