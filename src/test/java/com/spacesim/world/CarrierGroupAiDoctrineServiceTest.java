package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.world.CarrierGroupAiDoctrineService.CarrierDisposition;
import com.spacesim.world.CarrierGroupAiDoctrineService.CarrierGroupObservation;
import com.spacesim.world.CarrierGroupAiDoctrineService.DecisionReason;
import com.spacesim.world.CarrierGroupAiDoctrineService.DoctrinePolicy;
import com.spacesim.world.CarrierGroupAiDoctrineService.EscortDirective;
import com.spacesim.world.CarrierGroupAiDoctrineService.MissionOpportunity;
import com.spacesim.world.CarrierGroupAiDoctrineService.ThreatObservation;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.SmallCraftFlightDeckOperations.DeckProfile;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftMissionCommandService.DeploymentState;
import com.spacesim.world.SmallCraftMissionCommandService.MissionContext;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CarrierGroupAiDoctrineServiceTest {

    private static final long TICK = 100L;
    private static final FleetId CARRIER = new FleetId(100L);
    private static final FleetId ESCORT = new FleetId(101L);

    @Test
    void staleThreatCannotTriggerInterceptStrikeOrThreatDrivenStandoff() {
        Fixture fixture = fixture(2);
        ThreatObservation stale = threat(
                "track.hostile", 5_000d, 2_000d, 10_000d, 9_000, TICK - 1L);
        DoctrinePolicy policy = policy(0, 1_000, 1_000, 0);

        var result = fixture.doctrine.advance(
                fixture.commandState,
                SmallCraftMissionState.empty(),
                observation(fixture, fullReadiness(), fullReadiness(), stale),
                List.of(
                        opportunity(
                                fixture.craftIds.get(0),
                                MissionType.INTERCEPTION,
                                TargetKind.TRACK,
                                "track.hostile",
                                DeploymentState.EMBARKED,
                                "axis.intercept",
                                9_000),
                        opportunity(
                                fixture.craftIds.get(1),
                                MissionType.ANTI_SHIP_STRIKE,
                                TargetKind.TRACK,
                                "track.hostile",
                                DeploymentState.EMBARKED,
                                "axis.strike",
                                8_000)),
                policy);

        assertEquals(CarrierDisposition.HOLD, result.decision().carrierDisposition());
        assertEquals(DecisionReason.NO_FRESH_THREAT, result.decision().reason());
        assertTrue(result.decision().selectedMissionOptional().isEmpty());
        assertEquals(0, fixture.deck.queued().size());
    }

    @Test
    void freshThreatUsesSharedAiPathForInterceptThenMaintainsCap() {
        Fixture fixture = fixture(2);
        ThreatObservation fresh = threat(
                "track.hostile", 5_000d, 2_000d, 10_000d, 6_000, TICK);
        SmallCraftMissionState state = SmallCraftMissionState.empty();
        List<MissionOpportunity> opportunities = List.of(
                opportunity(
                        fixture.craftIds.get(0),
                        MissionType.QRA,
                        TargetKind.TRACK,
                        "track.hostile",
                        DeploymentState.EMBARKED,
                        "axis.qra",
                        9_000),
                opportunity(
                        fixture.craftIds.get(1),
                        MissionType.CAP,
                        TargetKind.AREA,
                        "area.cap",
                        DeploymentState.EMBARKED,
                        "axis.cap",
                        8_000));

        var first = fixture.doctrine.advance(
                fixture.commandState,
                state,
                observation(fixture, fullReadiness(), fullReadiness(), fresh),
                opportunities,
                DoctrinePolicy.standard());
        state = first.missionState();

        assertEquals(MissionType.QRA, first.decision().selectedMission().type());
        assertEquals(EscortDirective.INTERCEPT_SCREEN, first.decision().escortDirective());
        assertEquals(FleetCommandState.OrderSource.AI,
                state.requireMission(first.acceptedMissionId()).source());

        var second = fixture.doctrine.advance(
                fixture.commandState,
                state,
                observation(fixture, fullReadiness(), fullReadiness(), fresh),
                opportunities,
                DoctrinePolicy.standard());

        assertEquals(MissionType.CAP, second.decision().selectedMission().type());
        assertEquals(2, fixture.deck.queued().size());
        assertEquals(MissionStatus.LAUNCH_QUEUED,
                second.missionState().requireMission(second.acceptedMissionId()).status());
    }

    @Test
    void repeatedDoctrineTicksCanBuildSequentialMultiAxisStrikeThroughOrdinaryDeckQueue() {
        Fixture fixture = fixture(2);
        ThreatObservation fresh = threat(
                "track.hostile", 8_000d, 1_000d, 12_000d, 7_000, TICK);
        DoctrinePolicy strikePolicy = policy(0, 10_000, 1_000, 0);
        List<MissionOpportunity> opportunities = List.of(
                opportunity(
                        fixture.craftIds.get(0),
                        MissionType.ANTI_SHIP_STRIKE,
                        TargetKind.TRACK,
                        "track.hostile",
                        DeploymentState.EMBARKED,
                        "axis.port",
                        8_000),
                opportunity(
                        fixture.craftIds.get(1),
                        MissionType.ANTI_SHIP_STRIKE,
                        TargetKind.TRACK,
                        "track.hostile",
                        DeploymentState.EMBARKED,
                        "axis.starboard",
                        8_000));

        var first = fixture.doctrine.advance(
                fixture.commandState,
                SmallCraftMissionState.empty(),
                observation(fixture, fullReadiness(), fullReadiness(), fresh),
                opportunities,
                strikePolicy);
        var second = fixture.doctrine.advance(
                fixture.commandState,
                first.missionState(),
                observation(fixture, fullReadiness(), fullReadiness(), fresh),
                opportunities,
                strikePolicy);

        assertEquals("axis.port", first.decision().selectedMission().axisId());
        assertEquals("axis.starboard", second.decision().selectedMission().axisId());
        assertEquals(EscortDirective.STRIKE_SCREEN, first.decision().escortDirective());
        assertEquals(EscortDirective.STRIKE_SCREEN, second.decision().escortDirective());
        assertEquals(2, fixture.deck.queued().size(),
                "C owns finite sequencing; F only submits ordinary missions one decision at a time");
    }

    @Test
    void depletedDeployedCraftIsRetaskedToRecoveryThroughSharedMissionPath() {
        Fixture fixture = fixture(1);
        SmallCraftId craft = fixture.craftIds.get(0);
        fixture.registry.replacePhysicalState(ProductionSmallCraftFixture.craft(
                craft, 0L, 0d, 0.5d, 0.7d, 0d));
        fixture.hangars.release(craft);

        SmallCraftMissionState state = SmallCraftMissionState.empty().add(
                activeMission(1L, craft, MissionType.INTERCEPTION));
        MissionOpportunity recover = opportunity(
                craft,
                MissionType.RECOVER,
                TargetKind.HOST,
                "carrier.alpha",
                DeploymentState.DEPLOYED,
                "axis.home",
                10_000);

        var result = fixture.doctrine.advance(
                fixture.commandState,
                state,
                observation(fixture, fullReadiness(), fullReadiness(), null),
                List.of(recover),
                DoctrinePolicy.standard());

        assertEquals(1L, result.supersededMissionId());
        assertEquals(MissionType.RECOVER, result.decision().selectedMission().type());
        assertEquals(MissionStatus.CANCELLED, result.missionState().requireMission(1L).status());
        assertEquals(MissionStatus.RETURNING,
                result.missionState().requireMission(result.acceptedMissionId()).status());
        assertTrue(fixture.hangars.find(craft).isEmpty(),
                "F may order return but cannot teleport recovery into the bay");
    }

    @Test
    void degradedCarrierWithdrawsAndDoesNotLaunchFreshStrike() {
        Fixture fixture = fixture(1);
        SmallCraftId craft = fixture.craftIds.get(0);
        ThreatObservation fresh = threat(
                "track.hostile", 4_000d, 5_000d, 10_000d, 9_000, TICK);
        FleetReadinessState degradedCarrier =
                new FleetReadinessState(2_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000);

        var result = fixture.doctrine.advance(
                fixture.commandState,
                SmallCraftMissionState.empty(),
                observation(fixture, degradedCarrier, fullReadiness(), fresh),
                List.of(opportunity(
                        craft,
                        MissionType.ANTI_SHIP_STRIKE,
                        TargetKind.TRACK,
                        "track.hostile",
                        DeploymentState.EMBARKED,
                        "axis.strike",
                        10_000)),
                policy(0, 1_000, 1_000, 0));

        assertEquals(CarrierDisposition.WITHDRAW, result.decision().carrierDisposition());
        assertEquals(DecisionReason.CARRIER_READINESS, result.decision().reason());
        assertEquals(EscortDirective.COVER_WITHDRAWAL, result.decision().escortDirective());
        assertTrue(result.decision().selectedMissionOptional().isEmpty());
        assertEquals(0, fixture.deck.queued().size());
    }

    @Test
    void strikeOutsideCurrentWingEnduranceIsNotScheduled() {
        Fixture fixture = fixture(1);
        ThreatObservation beyondWingReach = threat(
                "track.hostile", 8_000d, 1_000d, 7_000d, 7_000, TICK);

        var result = fixture.doctrine.advance(
                fixture.commandState,
                SmallCraftMissionState.empty(),
                observation(fixture, fullReadiness(), fullReadiness(), beyondWingReach),
                List.of(opportunity(
                        fixture.craftIds.get(0),
                        MissionType.ANTI_SHIP_STRIKE,
                        TargetKind.TRACK,
                        "track.hostile",
                        DeploymentState.EMBARKED,
                        "axis.strike",
                        10_000)),
                policy(0, 10_000, 1_000, 0));

        assertEquals(CarrierDisposition.HOLD, result.decision().carrierDisposition());
        assertTrue(result.decision().selectedMissionOptional().isEmpty());
        assertEquals(0, fixture.deck.queued().size(),
                "actor-known target outside current physical wing reach cannot receive a strike");
    }

    @Test
    void degradedEscortReadinessForcesCarrierGroupWithdrawal() {
        Fixture fixture = fixture(1);
        FleetReadinessState degradedEscorts =
                new FleetReadinessState(2_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000);

        var result = fixture.doctrine.advance(
                fixture.commandState,
                SmallCraftMissionState.empty(),
                observation(fixture, fullReadiness(), degradedEscorts, null),
                List.of(),
                DoctrinePolicy.standard());

        assertEquals(CarrierDisposition.WITHDRAW, result.decision().carrierDisposition());
        assertEquals(DecisionReason.ESCORT_READINESS, result.decision().reason());
        assertEquals(EscortDirective.COVER_WITHDRAWAL, result.decision().escortDirective());
    }

    @Test
    void freshThreatInsideStandoffEnvelopeRequestsSeparationWithoutOffensiveLaunch() {
        Fixture fixture = fixture(1);
        ThreatObservation fresh = threat(
                "track.hostile", 2_200d, 2_000d, 10_000d, 9_000, TICK);

        var result = fixture.doctrine.advance(
                fixture.commandState,
                SmallCraftMissionState.empty(),
                observation(fixture, fullReadiness(), fullReadiness(), fresh),
                List.of(opportunity(
                        fixture.craftIds.get(0),
                        MissionType.ANTI_SHIP_STRIKE,
                        TargetKind.TRACK,
                        "track.hostile",
                        DeploymentState.EMBARKED,
                        "axis.strike",
                        10_000)),
                policy(0, 10_000, 1_000, 0));

        assertEquals(CarrierDisposition.STANDOFF, result.decision().carrierDisposition());
        assertEquals(DecisionReason.THREAT_INSIDE_STANDOFF, result.decision().reason());
        assertEquals(EscortDirective.SCREEN_CARRIER, result.decision().escortDirective());
        assertTrue(result.decision().selectedMissionOptional().isEmpty());
        assertEquals(0, fixture.deck.queued().size());
    }

    @Test
    void carrierAndEscortsMustRemainOrdinaryMembersOfStage21CommandGroup() {
        Fixture fixture = fixture(1);
        CarrierGroupObservation invalid = new CarrierGroupObservation(
                TICK,
                1L,
                CARRIER,
                List.of(new FleetId(999L)),
                fixture.craftIds,
                fullReadiness(),
                fullReadiness(),
                null);

        assertThrows(IllegalArgumentException.class, () -> fixture.doctrine.advance(
                fixture.commandState,
                SmallCraftMissionState.empty(),
                invalid,
                List.of(),
                DoctrinePolicy.standard()));
    }

    private static Fixture fixture(int craftCount) {
        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        BayDefinition bay = new BayDefinition(
                new BayId("carrier.alpha", "bay.flight"),
                HostKind.SHIP,
                new Dimensions3d(250d, 250d, 250d),
                100_000_000d,
                100_000_000d,
                1d);

        java.util.ArrayList<SmallCraftId> craftIds = new java.util.ArrayList<>();
        for (int index = 0; index < craftCount; index++) {
            SmallCraftId id = registry.reserveIdentityForCompletedProduction();
            registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                    id, 20L, 2_000d, 200_000d, 1d, 0d));
            hangars.assign(id, bay, OccupancyState.READY);
            craftIds.add(id);
        }

        SmallCraftFlightDeckOperations deck = new SmallCraftFlightDeckOperations(
                hangars,
                List.of(new DeckProfile(bay.id(), 0.25d, 0.25d)));
        SmallCraftMissionCommandService missionCommands = new SmallCraftMissionCommandService(
                registry,
                hangars,
                deck,
                Stage22CorePairEngineeringCatalogLoader.loadDefault());
        CarrierGroupAiDoctrineService doctrine =
                new CarrierGroupAiDoctrineService(registry, hangars, missionCommands);

        FleetCommandState commandState = FleetCommandState.empty().addGroup(
                new CommandGroupState(
                        1L,
                        1,
                        "Carrier Group Alpha",
                        List.of(CARRIER, ESCORT),
                        new StarSystemId(1L),
                        false,
                        false,
                        10_000));
        return new Fixture(
                registry,
                hangars,
                deck,
                doctrine,
                commandState,
                List.copyOf(craftIds));
    }

    private static CarrierGroupObservation observation(
            Fixture fixture,
            FleetReadinessState carrier,
            FleetReadinessState escorts,
            ThreatObservation threat) {
        return new CarrierGroupObservation(
                TICK,
                1L,
                CARRIER,
                List.of(ESCORT),
                fixture.craftIds,
                carrier,
                escorts,
                threat);
    }

    private static ThreatObservation threat(
            String referenceId,
            double distanceM,
            double effectiveRangeM,
            double strikeReachM,
            int severityBps,
            long freshUntilTick) {
        return new ThreatObservation(
                referenceId,
                distanceM,
                effectiveRangeM,
                strikeReachM,
                severityBps,
                new ObservationEvidence(
                        ObservationChannel.LOCAL_SENSOR_REPORT,
                        "sensor." + referenceId,
                        TICK - 5L,
                        freshUntilTick));
    }

    private static MissionOpportunity opportunity(
            SmallCraftId craftId,
            MissionType type,
            TargetKind targetKind,
            String referenceId,
            DeploymentState deployment,
            String axisId,
            int priorityBps) {
        return new MissionOpportunity(
                craftId,
                type,
                new MissionTarget(targetKind, referenceId),
                new MissionContext(
                        "faction.empire",
                        TICK,
                        deployment,
                        1_000d,
                        100_000d,
                        true,
                        0d,
                        referenceId,
                        new ObservationEvidence(
                                ObservationChannel.OWNED_ASSET_REPORT,
                                "mission." + referenceId + "." + craftId.value(),
                                TICK - 1L,
                                TICK)),
                axisId,
                priorityBps);
    }

    private static MissionOrder activeMission(
            long id,
            SmallCraftId craftId,
            MissionType type) {
        return new MissionOrder(
                id,
                craftId,
                FleetCommandState.OrderSource.AI,
                type,
                new MissionTarget(TargetKind.TRACK, "track.hostile"),
                TICK - 1L,
                MissionStatus.ACTIVE);
    }

    private static FleetReadinessState fullReadiness() {
        return new FleetReadinessState(
                10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000);
    }

    private static DoctrinePolicy policy(
            int minimumCapCraft,
            int interceptThreatSeverityBps,
            int strikeThreatSeverityBps,
            int wingWithdrawReadinessBps) {
        return new DoctrinePolicy(
                3_500,
                2_500,
                wingWithdrawReadinessBps,
                minimumCapCraft,
                2,
                4,
                interceptThreatSeverityBps,
                strikeThreatSeverityBps,
                8_000,
                1.25d,
                true,
                1d,
                0.45d,
                0.35d);
    }

    private record Fixture(
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations deck,
            CarrierGroupAiDoctrineService doctrine,
            FleetCommandState commandState,
            List<SmallCraftId> craftIds) { }
}
