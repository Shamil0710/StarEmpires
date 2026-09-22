package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.CombatantResult;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Result;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Termination;
import com.spacesim.ui.CarrierOperationsUiProjection;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class Stage228SmallCraftScaleAcceptanceTest {
    private static final String HOST = "carrier.scale.acceptance";
    private static final BayId BAY_ID = new BayId(HOST, "bay.dense");
    private static final BayDefinition DENSE_BAY = new BayDefinition(
            BAY_ID,
            HostKind.SHIP,
            new Dimensions3d(1_000d, 1_000d, 1_000d),
            1.0e12d,
            1.0e12d,
            1d);

    @Test
    void smallMediumAndDenseWingUiCaptureRemainDeterministicAndReadOnly() {
        for (int craftCount : List.of(16, 128, 512)) {
            DenseFixture fixture = denseEmbarkedFixture(craftCount);
            var craftBefore = fixture.registry.snapshot();
            var hangarsBefore = fixture.hangars.snapshot();
            var queueBefore = fixture.deck.queued();
            var activeBefore = fixture.deck.active();

            var view = new CarrierOperationsUiProjection(
                    Stage22CorePairEngineeringCatalogLoader.loadDefault())
                    .capture(
                            new CarrierWingAssignment(
                                    new FleetId(700L + craftCount),
                                    HOST,
                                    "faction.empire",
                                    fixture.ids),
                            fixture.registry,
                            fixture.hangars,
                            fixture.deck,
                            fixture.missions,
                            Map.of(BAY_ID, DENSE_BAY),
                            CarrierOperationsUiProjection.MissionTargetVisibility.hideAll());

            assertEquals(craftCount, view.craft().size());
            assertEquals(craftCount, view.bays().get(0).craftCount());
            assertEquals(List.of(), view.lostCraftIds());
            assertEquals(craftBefore, fixture.registry.snapshot());
            assertEquals(hangarsBefore, fixture.hangars.snapshot());
            assertEquals(queueBefore, fixture.deck.queued());
            assertEquals(activeBefore, fixture.deck.active());
            assertEquals(craftCount + 1L, fixture.missions.nextMissionId());
        }
    }

    @Test
    void exactTacticalMaterializationTouchesOnlySelectedLocalParticipantsInDenseRegistry() {
        DenseFixture fixture = denseDeployedFixture(512);
        SmallCraftId alpha = fixture.ids.get(0);
        SmallCraftId beta = fixture.ids.get(1);
        SmallCraftState dormantBefore = fixture.registry.find(fixture.ids.get(511)).orElseThrow();
        AtomicInteger importedCount = new AtomicInteger();

        SmallCraftTacticalEncounterService service = new SmallCraftTacticalEncounterService(
                fixture.registry,
                fixture.hangars,
                (imported, maximumTicks) -> {
                    importedCount.set(imported.size());
                    ArrayList<CombatantResult> rows = new ArrayList<>();
                    for (ImportedCombatantState row : imported) {
                        rows.add(new CombatantResult(
                                row.entityId(),
                                row.side(),
                                row.engineering().fit,
                                row.engineering().runtimeState,
                                row.engineering().instanceState,
                                row.xM(),
                                row.yM(),
                                row.velocityXMps(),
                                row.velocityYMps(),
                                false));
                    }
                    return new Result(
                            1L,
                            Termination.ENCOUNTER_HORIZON,
                            List.copyOf(rows));
                });

        service.resolve(
                fixture.missions,
                List.of(
                        new SmallCraftParticipant(
                                alpha,
                                Side.ALPHA,
                                new LocalFlightState(0d, 0d, 0d, 0d)),
                        new SmallCraftParticipant(
                                beta,
                                Side.BETA,
                                new LocalFlightState(1_000d, 0d, 0d, 0d))),
                List.of(),
                1L);

        assertEquals(2, importedCount.get(),
                "dense persistent registry must not become a world-wide tactical materialization set");
        assertEquals(512, fixture.registry.size());
        assertEquals(dormantBefore, fixture.registry.find(fixture.ids.get(511)).orElseThrow());
    }

    private static DenseFixture denseEmbarkedFixture(int craftCount) {
        DenseRegistry registry = denseRegistry(craftCount);
        ArrayList<SmallCraftHangarRegistry.Assignment> assignments = new ArrayList<>();
        ArrayList<MissionOrder> history = new ArrayList<>();
        for (int index = 0; index < registry.ids.size(); index++) {
            SmallCraftId id = registry.ids.get(index);
            assignments.add(new SmallCraftHangarRegistry.Assignment(
                    id, BAY_ID, HostKind.SHIP, OccupancyState.READY));
            history.add(new MissionOrder(
                    index + 1L,
                    id,
                    OrderSource.AI,
                    MissionType.CAP,
                    new MissionTarget(TargetKind.AREA, "area.scale." + index),
                    index,
                    MissionStatus.COMPLETE));
        }
        SmallCraftHangarRegistry hangars =
                SmallCraftHangarRegistry.restore(registry.registry, assignments);
        SmallCraftFlightDeckOperations deck = new SmallCraftFlightDeckOperations(
                hangars,
                List.of(new DeckProfile(BAY_ID, 1d, 1d)));
        return new DenseFixture(
                registry.registry,
                hangars,
                deck,
                new SmallCraftMissionState(craftCount + 1L, history),
                registry.ids);
    }

    private static DenseFixture denseDeployedFixture(int craftCount) {
        DenseRegistry registry = denseRegistry(craftCount);
        ArrayList<MissionOrder> missions = new ArrayList<>();
        for (int index = 0; index < craftCount; index++) {
            SmallCraftId id = registry.ids.get(index);
            missions.add(new MissionOrder(
                    index + 1L,
                    id,
                    OrderSource.AI,
                    MissionType.CAP,
                    new MissionTarget(TargetKind.AREA, "area.scale.deployed." + index),
                    index,
                    MissionStatus.ACTIVE));
        }
        SmallCraftHangarRegistry hangars =
                SmallCraftHangarRegistry.empty(registry.registry);
        SmallCraftFlightDeckOperations deck =
                new SmallCraftFlightDeckOperations(hangars, List.of());
        return new DenseFixture(
                registry.registry,
                hangars,
                deck,
                new SmallCraftMissionState(craftCount + 1L, missions),
                registry.ids);
    }

    private static DenseRegistry denseRegistry(int craftCount) {
        SmallCraftRegistry registry =
                SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftState template = ProductionSmallCraftFixture.craft(
                new SmallCraftId(1L),
                20L,
                2_000d,
                200_000d,
                1d,
                0d);
        ArrayList<SmallCraftId> ids = new ArrayList<>();
        for (int index = 0; index < craftCount; index++) {
            SmallCraftId id = registry.reserveIdentityForCompletedProduction();
            ids.add(id);
            registry.registerProducedCraft(new SmallCraftState(
                    id,
                    template.stableFactionId(),
                    template.designId(),
                    template.fit(),
                    template.runtimeState(),
                    template.instanceState()));
        }
        return new DenseRegistry(registry, List.copyOf(ids));
    }

    private record DenseRegistry(
            SmallCraftRegistry registry,
            List<SmallCraftId> ids) { }

    private record DenseFixture(
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations deck,
            SmallCraftMissionState missions,
            List<SmallCraftId> ids) { }
}
