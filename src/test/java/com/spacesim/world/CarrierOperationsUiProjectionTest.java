package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.ui.CarrierOperationsUiProjection;
import com.spacesim.ui.CarrierOperationsUiProjection.OperationalState;
import com.spacesim.ui.CarrierPlayerCommandAdapter;
import com.spacesim.ui.CarrierPlayerCommandAdapter.DiagnosticCode;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CarrierOperationsUiProjectionTest {
    private static final FleetId CARRIER = new FleetId(501L);
    private static final String HOST = "carrier.ui.alpha";

    @Test
    void captureExposesPhysicalCarrierStateWithoutMutatingAuthoritiesOrLeakingHiddenTarget() {
        Fixture fixture = fixture();
        SmallCraftId lost = new SmallCraftId(99L);
        CarrierWingAssignment assignment = new CarrierWingAssignment(
                CARRIER,
                HOST,
                "faction.empire",
                List.of(fixture.alpha, fixture.beta, lost));
        SmallCraftMissionState missions = SmallCraftMissionState.empty().add(new MissionOrder(
                1L,
                fixture.alpha,
                FleetCommandState.OrderSource.PLAYER,
                MissionType.CAP,
                new MissionTarget(TargetKind.AREA, "area.secret-cap"),
                10L,
                MissionStatus.LAUNCH_QUEUED));
        fixture.deck.requestLaunch(fixture.alpha, fixture.bay.id(), 10L);

        var craftBefore = fixture.registry.snapshot();
        var hangarsBefore = fixture.hangars.snapshot();
        var queueBefore = fixture.deck.queued();
        var activeBefore = fixture.deck.active();

        var view = fixture.projection.capture(
                assignment,
                fixture.registry,
                fixture.hangars,
                fixture.deck,
                missions,
                Map.of(fixture.bay.id(), fixture.bay),
                CarrierOperationsUiProjection.MissionTargetVisibility.hideAll());

        assertEquals(CARRIER, view.carrierFleetId());
        assertEquals(1, view.bays().size());
        assertEquals(2, view.bays().get(0).craftCount());
        assertEquals(1, view.bays().get(0).queuedOperations());
        assertEquals(List.of(lost), view.lostCraftIds());
        assertEquals(2, view.craft().size());

        var alpha = view.craft().stream()
                .filter(value -> value.craftId().equals(fixture.alpha))
                .findFirst().orElseThrow();
        assertEquals(OperationalState.LAUNCH_QUEUED, alpha.operationalState());
        assertFalse(alpha.installedModules().isEmpty());
        assertTrue(alpha.installedModules().stream().allMatch(value -> value.contains("=")));
        assertTrue(alpha.ammunition().applicable());
        assertTrue(alpha.propellant().applicable());
        assertTrue(alpha.repairRequired());
        assertEquals(MissionType.CAP.name(), alpha.mission().type());
        assertFalse(alpha.mission().targetVisible());
        assertEquals("", alpha.mission().targetReferenceId());

        var beta = view.craft().stream()
                .filter(value -> value.craftId().equals(fixture.beta))
                .findFirst().orElseThrow();
        assertEquals(OperationalState.READY, beta.operationalState());
        assertEquals(OccupancyState.READY.name(), beta.occupancyState());

        assertEquals(craftBefore, fixture.registry.snapshot());
        assertEquals(hangarsBefore, fixture.hangars.snapshot());
        assertEquals(queueBefore, fixture.deck.queued());
        assertEquals(activeBefore, fixture.deck.active());
        assertEquals(MissionStatus.LAUNCH_QUEUED, missions.requireMission(1L).status());
    }

    @Test
    void visibleActorKnownMissionTargetCanBeDisplayedWithoutChangingMission() {
        Fixture fixture = fixture();
        CarrierWingAssignment assignment = new CarrierWingAssignment(
                CARRIER, HOST, "faction.empire", List.of(fixture.alpha, fixture.beta));
        SmallCraftMissionState missions = SmallCraftMissionState.empty().add(new MissionOrder(
                1L,
                fixture.alpha,
                FleetCommandState.OrderSource.AI,
                MissionType.INTERCEPTION,
                new MissionTarget(TargetKind.TRACK, "track.known-7"),
                20L,
                MissionStatus.ACTIVE));
        fixture.hangars.release(fixture.alpha);

        var view = fixture.projection.capture(
                assignment,
                fixture.registry,
                fixture.hangars,
                fixture.deck,
                missions,
                Map.of(fixture.bay.id(), fixture.bay),
                CarrierOperationsUiProjection.MissionTargetVisibility.revealAll());

        var alpha = view.craft().stream()
                .filter(value -> value.craftId().equals(fixture.alpha))
                .findFirst().orElseThrow();
        assertEquals(OperationalState.MISSION, alpha.operationalState());
        assertTrue(alpha.mission().targetVisible());
        assertEquals("track.known-7", alpha.mission().targetReferenceId());
        assertEquals("track.known-7",
                missions.requireMission(1L).target().referenceId());
    }

    @Test
    void playerMissionUsesSharedValidatorAndQueuesPhysicalLaunch() {
        Fixture fixture = fixture();
        CarrierPlayerCommandAdapter adapter =
                new CarrierPlayerCommandAdapter(fixture.commands);

        var result = adapter.submitMission(
                SmallCraftMissionState.empty(),
                fixture.alpha,
                MissionType.CAP,
                new MissionTarget(TargetKind.AREA, "area.cap"),
                context(
                        30L,
                        DeploymentState.EMBARKED,
                        "area.cap",
                        "faction.empire",
                        30L));

        assertTrue(result.accepted());
        assertEquals(DiagnosticCode.NONE, result.diagnosticCode());
        assertEquals(MissionStatus.LAUNCH_QUEUED,
                result.state().requireMission(result.missionId()).status());
        assertEquals(FleetCommandState.OrderSource.PLAYER,
                result.state().requireMission(result.missionId()).source());
        assertEquals(1, fixture.deck.queued().size());
        assertEquals(fixture.alpha, fixture.deck.queued().get(0).craftId());
    }

    @Test
    void invalidPlayerMissionReturnsDiagnosticAndCannotMutateDeckOrMissionState() {
        Fixture fixture = fixture();
        CarrierPlayerCommandAdapter adapter =
                new CarrierPlayerCommandAdapter(fixture.commands);
        SmallCraftMissionState before = SmallCraftMissionState.empty();

        var rejected = adapter.submitMission(
                before,
                fixture.alpha,
                MissionType.INTERCEPTION,
                new MissionTarget(TargetKind.TRACK, "track.hostile"),
                context(
                        40L,
                        DeploymentState.EMBARKED,
                        "track.hostile",
                        "faction.other",
                        40L));

        assertFalse(rejected.accepted());
        assertEquals(before, rejected.state());
        assertEquals(DiagnosticCode.INVALID_MISSION, rejected.diagnosticCode());
        assertTrue(rejected.diagnosticDetail().contains("does not own"));
        assertTrue(fixture.deck.queued().isEmpty());
        assertEquals(OccupancyState.READY,
                fixture.hangars.find(fixture.alpha).orElseThrow().state());
    }

    @Test
    void invalidRecoveryAttemptReturnsMeaningfulDiagnosticWithoutMutatingPhysicalState() {
        Fixture fixture = fixture();
        CarrierPlayerCommandAdapter adapter =
                new CarrierPlayerCommandAdapter(fixture.commands);
        SmallCraftMissionState before = SmallCraftMissionState.empty();

        var rejected = adapter.submitMission(
                before,
                fixture.alpha,
                MissionType.RECOVER,
                new MissionTarget(TargetKind.HOST, HOST),
                context(
                        45L,
                        DeploymentState.EMBARKED,
                        HOST,
                        "faction.empire",
                        45L));

        assertFalse(rejected.accepted());
        assertEquals(before, rejected.state());
        assertEquals(DiagnosticCode.INVALID_MISSION, rejected.diagnosticCode());
        assertTrue(rejected.diagnosticDetail().contains("require physically deployed craft"));
        assertTrue(fixture.deck.queued().isEmpty());
        assertEquals(OccupancyState.READY,
                fixture.hangars.find(fixture.alpha).orElseThrow().state());
    }

    @Test
    void launchCancellationAfterPhysicalHandoffBoundaryIsRejectedWithoutMissionRewrite() {
        Fixture fixture = fixture();
        CarrierPlayerCommandAdapter adapter =
                new CarrierPlayerCommandAdapter(fixture.commands);
        var accepted = adapter.submitMission(
                SmallCraftMissionState.empty(),
                fixture.alpha,
                MissionType.CAP,
                new MissionTarget(TargetKind.AREA, "area.cap"),
                context(
                        50L,
                        DeploymentState.EMBARKED,
                        "area.cap",
                        "faction.empire",
                        50L));
        fixture.deck.advanceFixedTick(
                50L,
                1d,
                Map.of(fixture.bay.id(), fixture.bay));

        var rejected = adapter.cancelQueuedLaunch(
                accepted.state(),
                accepted.missionId());

        assertFalse(rejected.accepted());
        assertEquals(accepted.state(), rejected.state());
        assertEquals(DiagnosticCode.CANCELLATION_BLOCKED, rejected.diagnosticCode());
        assertTrue(rejected.diagnosticDetail().contains("cancellable boundary"));
        assertEquals(
                SmallCraftFlightDeckOperations.OperationPhase.AWAITING_HANDOFF,
                fixture.deck.activeFor(fixture.alpha).orElseThrow().phase());
        assertEquals(
                MissionStatus.LAUNCH_QUEUED,
                accepted.state().requireMission(accepted.missionId()).status());
    }

    private static Fixture fixture() {
        SmallCraftRegistry registry =
                SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId alpha = registry.reserveIdentityForCompletedProduction();
        SmallCraftId beta = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                alpha, 20L, 2_000d, 200_000d, 1d, 0d));
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                beta, 20L, 2_000d, 200_000d, 1d, 0d));

        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        BayId bayId = new BayId(HOST, "bay.flight");
        BayDefinition bay = new BayDefinition(
                bayId,
                HostKind.SHIP,
                new Dimensions3d(500d, 500d, 500d),
                100_000_000d,
                100_000_000d,
                1d);
        hangars.assign(alpha, bay, OccupancyState.READY);
        hangars.assign(beta, bay, OccupancyState.READY);

        SmallCraftFlightDeckOperations deck = new SmallCraftFlightDeckOperations(
                hangars,
                List.of(new DeckProfile(bayId, 1d, 1d)));
        var catalog = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        SmallCraftMissionCommandService commands = new SmallCraftMissionCommandService(
                registry, hangars, deck, catalog);
        return new Fixture(
                registry,
                hangars,
                deck,
                bay,
                alpha,
                beta,
                commands,
                new CarrierOperationsUiProjection(catalog));
    }

    private static MissionContext context(
            long tick,
            DeploymentState deployment,
            String targetReference,
            String faction,
            long freshUntilTick) {
        return new MissionContext(
                faction,
                tick,
                deployment,
                1_000d,
                20_000d,
                true,
                0d,
                targetReference,
                new ObservationEvidence(
                        ObservationChannel.OWNED_ASSET_REPORT,
                        "ui.report." + tick,
                        Math.max(0L, tick - 1L),
                        freshUntilTick));
    }

    private record Fixture(
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations deck,
            BayDefinition bay,
            SmallCraftId alpha,
            SmallCraftId beta,
            SmallCraftMissionCommandService commands,
            CarrierOperationsUiProjection projection) { }
}
