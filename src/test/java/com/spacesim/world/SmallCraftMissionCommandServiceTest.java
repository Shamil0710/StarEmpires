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
                contextFor(20L, DeploymentState.EMBARKED, 2_000d, 20_000d, true, 0d, "track.42", 19L)));

        assertTrue(fixture.deck.queued().isEmpty());
        assertEquals(OccupancyState.READY, fixture.hangars.find(fixture.craftId).orElseThrow().state());
    }

    @Test
    void futureDatedKnowledgeFailsClosedWithoutQueuingPhysicalLaunch() {
        Fixture fixture = fixture();
        MissionContext futureEvidence = new MissionContext(
                "faction.empire",
                21L,
                DeploymentState.EMBARKED,
                2_000d,
                20_000d,
                true,
                0d,
                "track.42",
                new ObservationEvidence(
                        ObservationChannel.LOCAL_SENSOR_REPORT,
                        "scan.future",
                        22L,
                        30L));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.INTERCEPTION, TargetKind.TRACK, "track.42"),
                futureEvidence));

        assertTrue(fixture.deck.queued().isEmpty());
        assertEquals(OccupancyState.READY, fixture.hangars.find(fixture.craftId).orElseThrow().state());
    }

    @Test
    void foreignFactionCannotCommandCraftOrMutateDeck() {
        Fixture fixture = fixture();
        MissionContext foreign = new MissionContext(
                "faction.industrial_union",
                22L,
                DeploymentState.EMBARKED,
                1_000d,
                20_000d,
                true,
                0d,
                "area.alpha",
                new ObservationEvidence(
                        ObservationChannel.OWNED_ASSET_REPORT,
                        "report.foreign",
                        22L,
                        22L));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.PLAYER, MissionType.CAP, TargetKind.AREA, "area.alpha"),
                foreign));

        assertTrue(fixture.deck.queued().isEmpty());
        assertEquals(OccupancyState.READY, fixture.hangars.find(fixture.craftId).orElseThrow().state());
    }

    @Test
    void embarkedCraftMustBePhysicallyReadyBeforeLaunchCommand() {
        Fixture fixture = fixture();
        fixture.hangars.transition(fixture.craftId, OccupancyState.SERVICING);

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.CAP, TargetKind.AREA, "area.alpha"),
                context(23L, DeploymentState.EMBARKED, 1_000d, 20_000d, true, 0d, 23L)));

        assertTrue(fixture.deck.queued().isEmpty());
        assertEquals(OccupancyState.SERVICING, fixture.hangars.find(fixture.craftId).orElseThrow().state());
    }

    @Test
    void embarkedLaunchAlsoRequiresLawfulCommandLink() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.PLAYER, MissionType.CAP, TargetKind.AREA, "area.alpha"),
                context(25L, DeploymentState.EMBARKED, 0d, 20_000d, false, 0d, 25L)));

        assertTrue(fixture.deck.queued().isEmpty());
    }

    @Test
    void deployedRetaskRequiresLawfulCommandLinkAndRange() {
        Fixture fixture = fixture();
        fixture.hangars.release(fixture.craftId);

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.PLAYER, MissionType.RECONNAISSANCE, TargetKind.AREA, "area.outer"),
                contextFor(30L, DeploymentState.DEPLOYED, 10_000d, 20_000d, false, 0d, "area.outer", 30L)));

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.PLAYER, MissionType.RECONNAISSANCE, TargetKind.AREA, "area.outer"),
                contextFor(30L, DeploymentState.DEPLOYED, 30_000d, 20_000d, true, 0d, "area.outer", 30L)));
    }

    @Test
    void physicallyInfeasibleMissionDeltaVIsRejectedBeforeLaunchMutation() {
        Fixture fixture = fixture(0d);

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.CAP, TargetKind.AREA, "area.alpha"),
                context(40L, DeploymentState.EMBARKED, 1_000d, 20_000d, true, 10d, 40L)));

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
                contextFor(51L, DeploymentState.DEPLOYED, 1_000d, 20_000d, true, 0d, "carrier.alpha", 51L));
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
    void emptyPhysicalMagazineCannotAuthorizeCombatMission() {
        Fixture fixture = fixture(200_000d, 1d, 0L);

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.INTERCEPTION, TargetKind.TRACK, "track.empty"),
                contextFor(
                        53L,
                        DeploymentState.EMBARKED,
                        0d,
                        20_000d,
                        true,
                        0d,
                        "track.empty",
                        53L)));

        assertTrue(fixture.deck.queued().isEmpty());
    }

    @Test
    void sensorOnlyFitCannotClaimEwSupportMission() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.EW_SUPPORT, TargetKind.AREA, "area.ew"),
                contextFor(
                        53L,
                        DeploymentState.EMBARKED,
                        0d,
                        20_000d,
                        true,
                        0d,
                        "area.ew",
                        53L)));

        assertTrue(fixture.deck.queued().isEmpty());
    }

    @Test
    void operationalSensorFitCanAuthorizeReconnaissance() {
        Fixture fixture = fixture();

        var accepted = fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.RECONNAISSANCE, TargetKind.AREA, "area.recon"),
                contextFor(
                        53L,
                        DeploymentState.EMBARKED,
                        0d,
                        20_000d,
                        true,
                        0d,
                        "area.recon",
                        53L));

        assertEquals(MissionStatus.LAUNCH_QUEUED, accepted.mission().status());
        assertEquals(1, fixture.deck.queued().size());
    }

    @Test
    void destroyedWeaponCannotAuthorizeCombatMission() {
        Fixture fixture = fixture(200_000d, 0d);

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.CAP, TargetKind.AREA, "area.alpha"),
                context(54L, DeploymentState.EMBARKED, 1_000d, 20_000d, true, 0d, 54L)));

        assertTrue(fixture.deck.queued().isEmpty());
    }

    @Test
    void evidenceForAnotherTargetCannotAuthorizeMission() {
        Fixture fixture = fixture();

        assertThrows(IllegalArgumentException.class, () -> fixture.service.submit(
                SmallCraftMissionState.empty(),
                command(fixture.craftId, OrderSource.AI, MissionType.INTERCEPTION, TargetKind.TRACK, "track.42"),
                contextFor(
                        55L,
                        DeploymentState.EMBARKED,
                        1_000d,
                        20_000d,
                        true,
                        0d,
                        "track.99",
                        55L)));

        assertTrue(fixture.deck.queued().isEmpty());
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
        return fixture(200_000d, 1d, 20L);
    }

    private static Fixture fixture(double reactionMassKg) {
        return fixture(reactionMassKg, 1d, 20L);
    }

    private static Fixture fixture(double reactionMassKg, double weaponIntegrity) {
        return fixture(reactionMassKg, weaponIntegrity, 20L);
    }

    private static Fixture fixture(
            double reactionMassKg,
            double weaponIntegrity,
            long ammunitionCount) {
        SmallCraftRegistry craft = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId id = craft.reserveIdentityForCompletedProduction();
        craft.registerProducedCraft(ProductionSmallCraftFixture.craft(
                id,
                ammunitionCount,
                ammunitionCount * 100d,
                reactionMassKg,
                weaponIntegrity,
                0d));

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
            double commandNodeDistanceM,
            double commandRangeM,
            boolean link,
            double deltaV,
            long freshUntilTick) {
        return contextFor(
                tick,
                deployment,
                commandNodeDistanceM,
                commandRangeM,
                link,
                deltaV,
                "area.alpha",
                freshUntilTick);
    }

    private static MissionContext contextFor(
            long tick,
            DeploymentState deployment,
            double commandNodeDistanceM,
            double commandRangeM,
            boolean link,
            double deltaV,
            String evidencedTargetReferenceId,
            long freshUntilTick) {
        return new MissionContext(
                "faction.empire",
                tick,
                deployment,
                commandNodeDistanceM,
                commandRangeM,
                link,
                deltaV,
                evidencedTargetReferenceId,
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
