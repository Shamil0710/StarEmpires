package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.SmallCraftFlightDeckOperations.DeckProfile;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationPhase;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftMissionCommandService.DeploymentState;
import com.spacesim.world.SmallCraftMissionCommandService.MissionCommand;
import com.spacesim.world.SmallCraftMissionCommandService.MissionContext;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmallCraftMissionCommandServiceTest {

    @Test
    void playerAndAiUseSameReadyCraftLaunchValidationPath() {
        Fixture player = fixture();
        Fixture ai = fixture();

        var playerResult = player.service.submit(
                SmallCraftMissionState.empty(),
                command(player.craftId, OrderSource.PLAYER, MissionType.CAP, TargetKind.AREA, "area.alpha"),
                context(10L, DeploymentState.EMBARKED, 5_000d, 50_000d, true, 0d, 10L));
        var aiResult = ai.service.submit(
                SmallCraftMissionState.empty(),
                command(ai.craftId, OrderSource.AI, MissionType.CAP, TargetKind.AREA, "area.alpha"),
                context(10L, DeploymentState.EMBARKED, 5_000d, 50_000d, true, 0d, 10L));

        assertEquals(MissionStatus.LAUNCH_QUEUED, playerResult.mission().status());
        assertEquals(MissionStatus.LAUNCH_QUEUED, aiResult.mission().status());
        assertEquals(1, player.deck.queued().size());
        assertEquals(1, ai.deck.queued().size());
        assertEquals(OrderSource.PLAYER, playerResult.mission().source());
        assertEquals(OrderSource.AI, aiResult.mission().source());
    }

    @Test
    void staleKnowledgeFailsClosedWithoutQueuingPhysicalLaunch() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.INTERCEPTION, TargetKind.TRACK, "track.42"),
                context(20L, DeploymentState.EMBARKED, 2_000d, 20_000d, true, 0d, 5L)));

        assertTrue(fixture.deck.queued().isEmpty());
        assertEquals(OccupancyState.READY, fixture.hangars.find(fixture.craftId).orElseThrow().state());
    }

    @Test
    void deployedRetaskRequiresLawfulCommandLinkAndRange() {
        Fixture fixture = fixture();
        fixture.hangars.release(fixture.craftId);

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.PLAYER, MissionType.RECONNAISSANCE, TargetKind.AREA, "area.outer"),
                context(30L, DeploymentState.DEPLOYED, 10_000d, 20_000d, false, 0d, 30L)));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.PLAYER, MissionType.RECONNAISSANCE, TargetKind.AREA, "area.outer"),
                context(30L, DeploymentState.DEPLOYED, 30_000d, 20_000d, true, 0d, 30L)));
    }

    @Test
    void physicallyInfeasibleMissionDeltaVIsRejectedBeforeLaunchMutation() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.CAP, TargetKind.AREA, "area.alpha"),
                context(40L, DeploymentState.EMBARKED, 1_000d, 20_000d, true, 1_000_000_000d, 40L)));

        assertTrue(fixture.deck.queued().isEmpty());
        assertEquals(OccupancyState.READY, fixture.hangars.find(fixture.craftId).orElseThrow().state());
    }

    @Test
    void launchAndRecoveryLifecycleCannotTeleportCraftAcrossBayBoundary() {
        Fixture fixture = fixture();
        SmallCraftMissionState state = SmallCraftMissionState.empty();

        var launch = fixture.service.submit(
                state,
                command(fixture.craftId, OrderSource.PLAYER, MissionType.CAP, TargetKind.AREA, "area.alpha"),
                context(50L, DeploymentState.EMBARKED, 1_000d, 20_000d, true, 0d, 50L));
        state = launch.state();

        fixture.deck.advanceFixedTick(50L, 1d, Map.of(fixture.bay.id(), fixture.bay));
        assertEquals(
                OperationPhase.AWAITING_HANDOFF,
                fixture.deck.activeFor(fixture.craftId).orElseThrow().phase());
        assertTrue(fixture.hangars.find(fixture.craftId).isPresent());

        state = fixture.service.confirmPhysicalLaunch(state, launch.mission().id());
        assertEquals(MissionStatus.ACTIVE, state.requireMission(launch.mission().id()).status());
        assertFalse(fixture.hangars.find(fixture.craftId).isPresent());

        var recover = fixture.service.submit(
                state,
                command(fixture.craftId, OrderSource.PLAYER, MissionType.RECOVER, TargetKind.HOST, "carrier.alpha"),
                context(51L, DeploymentState.DEPLOYED, 1_000d, 20_000d, true, 0d, 51L));
        state = recover.state();
        assertEquals(launch.mission().id(), recover.supersededMissionId());
        assertEquals(MissionStatus.RETURNING, recover.mission().status());

        state = fixture.service.offerPhysicalRecovery(
                state, recover.mission().id(), fixture.bay, 52L);
        assertEquals(MissionStatus.RECOVERY_PENDING, state.requireMission(recover.mission().id()).status());
        assertFalse(fixture.hangars.find(fixture.craftId).isPresent());

        fixture.deck.advanceFixedTick(52L, 1d, Map.of(fixture.bay.id(), fixture.bay));
        assertEquals(
                OccupancyState.SERVICING,
                fixture.hangars.find(fixture.craftId).orElseThrow().state());

        state = fixture.service.completePhysicalRecovery(state, recover.mission().id());
        assertEquals(MissionStatus.COMPLETE, state.requireMission(recover.mission().id()).status());
    }

    @Test
    void wrongTargetFamilyFailsBeforeAnyDeckMutation() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.PLAYER, MissionType.ANTI_SHIP_STRIKE, TargetKind.AREA, "area.alpha"),
                context(60L, DeploymentState.EMBARKED, 1_000d, 20_000d, true, 0d, 60L)));

        assertTrue(fixture.deck.queued().isEmpty());
    }

    private static Fixture fixture() {
        SmallCraftRegistry craft = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId id = craft.reserveIdentityForCompletedProduction();
        craft.registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, 20L, 2_000d, 200_000d, 1d, 0d));

        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(craft);
        BayId bayId = new BayId("carrier.alpha", "bay.1");
        BayDefinition bay = new BayDefinition(
                bayId,
                HostKind.SHIP,
                new Dimensions3d(200d, 100d, 80d),
                2_000_000d,
                100_000_000d,
                1d);
        hangars.assign(id, bay, OccupancyState.READY);
        SmallCraftFlightDeckOperations deck = new SmallCraftFlightDeckOperations(
                hangars,
                List.of(new DeckProfile(bayId, 0.25d, 0.25d)));
        SmallCraftMissionCommandService service = new SmallCraftMissionCommandService(
                craft,
                hangars,
                deck,
                Stage22CorePairEngineeringCatalogLoader.loadDefault());
        return new Fixture(id, hangars, deck, service, bay);
    }

    private static MissionCommand command(
            SmallCraftId craftId,
            OrderSource source,
            MissionType type,
            TargetKind targetKind,
            String referenceId) {
        return new MissionCommand(
                craftId,
                source,
                type,
                new MissionTarget(targetKind, referenceId));
    }

    private static MissionContext context(
            long tick,
            DeploymentState deployment,
            double targetDistanceM,
            double commandRangeM,
            boolean link,
            double deltaV,
            long freshUntilTick) {
        return new MissionContext(
                "faction.empire",
                tick,
                deployment,
                targetDistanceM,
                commandRangeM,
                link,
                deltaV,
                new ObservationEvidence(
                        ObservationChannel.OWNED_ASSET_REPORT,
                        "report." + tick,
                        Math.max(0L, tick - 5L),
                        freshUntilTick));
    }

    private record Fixture(
            SmallCraftId craftId,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations deck,
            SmallCraftMissionCommandService service,
            BayDefinition bay) { }
}
