package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.CombatantResult;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Result;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Termination;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.SmallCraftFlightDeckOperations.DeckProfile;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;
import com.spacesim.world.SmallCraftTacticalEncounterService.LocalFlightState;
import com.spacesim.world.SmallCraftTacticalEncounterService.SmallCraftParticipant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage228CarrierScaleIsolationTest {
    private static final int DENSE_PERSISTENT_CRAFT = 256;
    private static final int BAY_COUNT = 16;
    private static final int CRAFT_PER_BAY = 4;

    @Test
    void densePersistentRegistryMaterializesOnlyExplicitLocalEncounterParticipants() {
        SmallCraftRegistry registry =
                SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        ArrayList<SmallCraftId> ids = new ArrayList<>();
        for (int index = 0; index < DENSE_PERSISTENT_CRAFT; index++) {
            SmallCraftId id = registry.reserveIdentityForCompletedProduction();
            SmallCraftState state = ProductionSmallCraftFixture.craft(
                    id, 20L, 2_000d, 200_000d, 1d, 0d);
            if (index == 1) {
                state = new SmallCraftState(
                        state.id(),
                        "faction.industrial_combine",
                        state.designId(),
                        state.fit(),
                        state.runtimeState(),
                        state.instanceState());
            }
            registry.registerProducedCraft(state);
            ids.add(id);
        }
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        SmallCraftId alpha = ids.get(0);
        SmallCraftId beta = ids.get(1);
        SmallCraftId dormant = ids.get(200);
        SmallCraftState dormantBefore = registry.find(dormant).orElseThrow();
        long allocatorBefore = registry.nextIdValue();

        SmallCraftMissionState missions = SmallCraftMissionState.empty()
                .add(activeMission(1L, alpha))
                .add(activeMission(2L, beta));
        AtomicInteger importedRows = new AtomicInteger();
        SmallCraftTacticalEncounterService service = new SmallCraftTacticalEncounterService(
                registry,
                hangars,
                (imported, maximumTicks) -> {
                    importedRows.set(imported.size());
                    return unchangedOutcome(imported);
                });

        var result = service.resolve(
                missions,
                List.of(
                        new SmallCraftParticipant(
                                alpha, Side.ALPHA, new LocalFlightState(0d, 0d, 0d, 0d)),
                        new SmallCraftParticipant(
                                beta, Side.BETA, new LocalFlightState(10_000d, 0d, 0d, 0d))),
                List.of(),
                1L);

        assertEquals(2, importedRows.get(),
                "dense persistent registry must not become a world-wide exact tactical roster");
        assertEquals(2, result.smallCraft().size());
        assertEquals(DENSE_PERSISTENT_CRAFT, registry.size());
        assertEquals(dormantBefore, registry.find(dormant).orElseThrow(),
                "non-local persistent craft must remain untouched by exact local resolution");
        assertEquals(allocatorBefore, registry.nextIdValue());
    }

    @Test
    void denseLaunchQueueRemainsLinearInQueuedRowsPlusPhysicalBays() {
        SmallCraftRegistry registry =
                SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        ArrayList<SmallCraftId> ids = new ArrayList<>();
        for (int index = 0; index < BAY_COUNT * CRAFT_PER_BAY; index++) {
            SmallCraftId id = registry.reserveIdentityForCompletedProduction();
            registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                    id, 20L, 2_000d, 200_000d, 1d, 0d));
            ids.add(id);
        }

        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        ArrayList<DeckProfile> profiles = new ArrayList<>();
        LinkedHashMap<BayId, BayDefinition> bays = new LinkedHashMap<>();
        LinkedHashMap<SmallCraftId, BayId> craftBay = new LinkedHashMap<>();
        for (int bayIndex = 0; bayIndex < BAY_COUNT; bayIndex++) {
            BayId bayId = new BayId("carrier.scale." + bayIndex, "bay.flight");
            BayDefinition bay = new BayDefinition(
                    bayId,
                    HostKind.SHIP,
                    new Dimensions3d(500d, 500d, 500d),
                    100_000_000d,
                    100_000_000d,
                    1d);
            bays.put(bayId, bay);
            profiles.add(new DeckProfile(bayId, 10d, 10d));
            for (int slot = 0; slot < CRAFT_PER_BAY; slot++) {
                SmallCraftId id = ids.get(bayIndex * CRAFT_PER_BAY + slot);
                hangars.assign(id, bay, OccupancyState.READY);
                craftBay.put(id, bayId);
            }
        }

        SmallCraftFlightDeckOperations deck =
                new SmallCraftFlightDeckOperations(hangars, profiles);
        for (SmallCraftId id : ids) {
            deck.requestLaunch(id, craftBay.get(id), 0L);
        }

        var diagnostics = deck.advanceFixedTickMeasured(1L, 1d, Map.copyOf(bays));

        assertEquals(BAY_COUNT, diagnostics.profilesVisited());
        assertEquals(BAY_COUNT * CRAFT_PER_BAY, diagnostics.queuedRequestsScanned());
        assertEquals(
                diagnostics.profilesVisited() + diagnostics.queuedRequestsScanned(),
                diagnostics.totalLinearWorkUnits());
        assertEquals(BAY_COUNT, diagnostics.startableCandidates());
        assertEquals(BAY_COUNT, diagnostics.startedOperations());
        assertEquals(BAY_COUNT, diagnostics.activeOperationsAdvanced());
        assertEquals(BAY_COUNT, deck.active().size());
        assertEquals(BAY_COUNT * (CRAFT_PER_BAY - 1), deck.queued().size());
        assertTrue(deck.active().stream().allMatch(value ->
                value.phase() == SmallCraftFlightDeckOperations.OperationPhase.CYCLING));
    }

    @Test
    void deterministicScaleEvidenceGrowsPersistentPopulationWithoutDormantRenderWork() {
        var rows = Stage228CarrierScaleEvidence.deriveCurrent();
        assertEquals(3, rows.size());
        var small = rows.get(0);
        var medium = rows.get(1);
        var dense = rows.get(2);

        assertTrue(small.persistentCraftCount() < medium.persistentCraftCount());
        assertTrue(medium.persistentCraftCount() < dense.persistentCraftCount());
        assertTrue(dense.dormantCraftCount() > medium.dormantCraftCount());
        assertEquals(0, small.dormantRenderRateUpdates());
        assertEquals(0, medium.dormantRenderRateUpdates());
        assertEquals(0, dense.dormantRenderRateUpdates());
        rows.forEach(row -> {
            assertEquals(
                    row.deckProfileCount() + row.queuedDeckOperationCount(),
                    row.deckSchedulingWorkUpperBound());
            assertEquals(
                    row.localTacticalCraftCount() + row.dormantCraftCount(),
                    row.persistentCraftCount());
        });
    }

    private static MissionOrder activeMission(long id, SmallCraftId craftId) {
        return new MissionOrder(
                id,
                craftId,
                OrderSource.AI,
                MissionType.CAP,
                new MissionTarget(TargetKind.AREA, "area.scale"),
                10L,
                MissionStatus.ACTIVE);
    }

    private static Result unchangedOutcome(List<ImportedCombatantState> imported) {
        List<CombatantResult> rows = imported.stream()
                .map(row -> new CombatantResult(
                        row.entityId(),
                        row.side(),
                        row.engineering().fit,
                        row.engineering().runtimeState,
                        row.engineering().instanceState,
                        row.xM(),
                        row.yM(),
                        row.velocityXMps(),
                        row.velocityYMps(),
                        false))
                .toList();
        return new Result(1L, Termination.ENCOUNTER_HORIZON, rows);
    }
}
