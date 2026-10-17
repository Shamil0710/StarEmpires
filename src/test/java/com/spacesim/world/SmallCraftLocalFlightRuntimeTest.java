package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.persistence.SmallCraftLocalFlightPersistenceCodec;
import com.spacesim.persistence.Stage228SmallCraftPersistenceMapper;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState;
import com.spacesim.ship.ShipInstanceRuntimeState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmallCraftLocalFlightRuntimeTest {
    @Test void completedDeckWorkWaitsForActualApertureThenHandsOffTheSameCraftAndCoastsExactlyAfterReload() {
        var f = fixture();
        f.deck.advanceFixedTick(1, .1, Map.of(f.bay.id(), f.bay));
        var missions = f.runtime.advanceFixedTick(1, .1, f.missions, Map.of());
        assertTrue(f.runtime.capture().flights().isEmpty());
        f.deck.advanceFixedTick(2, .1, Map.of(f.bay.id(), f.bay));
        missions = f.runtime.advanceFixedTick(2, .1, missions, Map.of());
        assertEquals(SmallCraftFlightDeckOperations.OperationPhase.AWAITING_HANDOFF, f.deck.activeFor(f.id).orElseThrow().phase());
        assertEquals(SmallCraftHangarCapacity.OccupancyState.LAUNCHING, f.hangars.find(f.id).orElseThrow().state());
        assertEquals(SmallCraftMissionState.MissionStatus.LAUNCH_QUEUED, missions.requireMission(1).status());
        var before = Stage228SmallCraftPersistenceMapper.capture(f.craft);
        var mouth = new LocalPhysicalKinematics(new LocalPhysicalPosition(4_000_000_000L, -9_000_000_000L, 25, -72), 123, -8);
        f.deck.advanceFixedTick(3, .1, Map.of(f.bay.id(), f.bay));
        missions = f.runtime.advanceFixedTick(3, .1, missions, Map.of(f.bay.id(),
                new SmallCraftLocalFlightRuntime.LaunchAperture(f.bay, new StarSystemId(16), "faction.empire", mouth, 3)));
        assertEquals(SmallCraftMissionState.MissionStatus.ACTIVE, missions.requireMission(1).status());
        assertTrue(f.hangars.find(f.id).isEmpty()); assertTrue(f.deck.active().isEmpty());
        assertEquals(before, Stage228SmallCraftPersistenceMapper.capture(f.craft), "Handoff must not create or refill a craft");
        assertEquals(mouth, f.runtime.capture().flights().get(0).physical(), "A new handoff cannot receive a second movement tick");
        var saved = f.runtime.capture();
        var decoded = SmallCraftLocalFlightPersistenceCodec.decode(SmallCraftLocalFlightPersistenceCodec.encode(saved));
        assertEquals(saved, decoded);
        var restored = new SmallCraftLocalFlightRuntime(f.craft, f.hangars, f.deck, f.catalog, missions, decoded);
        var individual = f.craft.find(f.id).orElseThrow();
        restored.advanceFixedTick(4, .1, missions, Map.of());
        var moved = restored.capture().flights().get(0);
        assertEquals(new LocalPhysicalKinematics(mouth.position().translated(12.3, -.8), 123, -8), moved.physical());
        assertEquals(individual.runtimeState().consumables().reactionMassKg(), f.craft.find(f.id).orElseThrow().runtimeState().consumables().reactionMassKg());
        assertEquals(before.nextCraftId(), Stage228SmallCraftPersistenceMapper.capture(f.craft).nextCraftId());
        var exact = restored.capture(); var owner = Stage228SmallCraftPersistenceMapper.capture(f.craft);
        assertThrows(IllegalArgumentException.class, () -> restored.advanceFixedTick(4, .1, f.missions, Map.of()));
        final var active = missions;
        assertThrows(IllegalArgumentException.class, () -> restored.advanceFixedTick(6, .1, active, Map.of()));
        assertEquals(exact, restored.capture()); assertEquals(owner, Stage228SmallCraftPersistenceMapper.capture(f.craft));
        var retasked = missions.replace(missions.requireMission(1).withStatus(SmallCraftMissionState.MissionStatus.CANCELLED))
                .add(new SmallCraftMissionState.MissionOrder(2, f.id, FleetCommandState.OrderSource.PLAYER,
                        SmallCraftMissionState.MissionType.RETURN, new SmallCraftMissionState.MissionTarget(SmallCraftMissionState.TargetKind.HOST, "carrier.actual"),
                        4, SmallCraftMissionState.MissionStatus.RETURNING));
        var continued = restored.advanceFixedTick(5, .1, retasked, Map.of());
        assertEquals(2, continued.activeMissionFor(f.id).orElseThrow().id());
        assertEquals(1, restored.capture().flights().get(0).missionId(), "Retasking retains the original physical handoff identity");
        assertEquals(moved.physical().position().translated(12.3, -.8), restored.capture().flights().get(0).physical().position());
    }

    @Test void staleOrForeignPhysicalApertureFailsBeforeOwnershipDeckOrLocalStateChanges() {
        var f = fixture(); f.deck.advanceFixedTick(1, .2, Map.of(f.bay.id(), f.bay));
        var before = f.runtime.capture(); var craft = Stage228SmallCraftPersistenceMapper.capture(f.craft);
        assertThrows(IllegalArgumentException.class, () -> new SmallCraftLocalFlightRuntime(f.craft, f.hangars, f.deck,
                com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader.loadDefault(), f.missions, SmallCraftLocalFlightState.empty()));
        var physical = LocalPhysicalKinematics.stationary(LocalPhysicalPosition.origin());
        assertThrows(IllegalArgumentException.class, () -> f.runtime.advanceFixedTick(1, .2, f.missions, Map.of(f.bay.id(),
                new SmallCraftLocalFlightRuntime.LaunchAperture(f.bay, new StarSystemId(16), "faction.empire", physical, 0))));
        assertThrows(IllegalStateException.class, () -> f.runtime.advanceFixedTick(1, .2, f.missions, Map.of(f.bay.id(),
                new SmallCraftLocalFlightRuntime.LaunchAperture(f.bay, new StarSystemId(16), "faction.industrial_union", physical, 1))));
        assertEquals(before, f.runtime.capture()); assertEquals(craft, Stage228SmallCraftPersistenceMapper.capture(f.craft));
        assertEquals(SmallCraftFlightDeckOperations.OperationPhase.AWAITING_HANDOFF, f.deck.activeFor(f.id).orElseThrow().phase());
        assertEquals(SmallCraftHangarCapacity.OccupancyState.LAUNCHING, f.hangars.find(f.id).orElseThrow().state());
    }

    @Test void physicalLossCannotLeaveAMovingGhostOrRewindTheCraftAllocator() {
        var f = fixture(); f.deck.advanceFixedTick(1, .2, Map.of(f.bay.id(), f.bay));
        var missions = f.runtime.advanceFixedTick(1, .2, f.missions, Map.of(f.bay.id(),
                new SmallCraftLocalFlightRuntime.LaunchAperture(f.bay, new StarSystemId(16), "faction.empire",
                        LocalPhysicalKinematics.stationary(LocalPhysicalPosition.origin()), 1)));
        f.craft.removeDestroyedCraft(f.id); // Explicit committed-loss fixture; no replacement is manufactured.
        var beforeLossTick = f.runtime.capture().lastProcessedTick();
        var afterLoss = f.runtime.reconcileCommittedLosses(missions);
        assertEquals(SmallCraftMissionState.MissionStatus.FAILED, afterLoss.requireMission(1).status());
        assertEquals(beforeLossTick, f.runtime.capture().lastProcessedTick(), "A committed loss does not invent elapsed time");
        assertTrue(f.runtime.capture().flights().isEmpty()); assertTrue(f.craft.find(f.id).isEmpty());
        assertTrue(f.craft.reserveIdentityForCompletedProduction().value() > f.id.value());
    }

    @Test void queuedPhysicalRecoveryCoastsAcrossReloadThenTransfersToDeckAndCompletesWithoutRefilling() {
        var f = fixture();
        f.deck.advanceFixedTick(1, .2, Map.of(f.bay.id(), f.bay));
        var mouth = new LocalPhysicalKinematics(LocalPhysicalPosition.origin(), 10, -2);
        var missions = f.runtime.advanceFixedTick(1, .2, f.missions, Map.of(f.bay.id(),
                new SmallCraftLocalFlightRuntime.LaunchAperture(f.bay, new StarSystemId(16), "faction.empire", mouth, 1)));
        // Explicit physical-gate fixture, not a player command or an authored world recovery mouth.
        var commands = new SmallCraftMissionCommandService(f.craft, f.hangars, f.deck, f.catalog);
        missions = commands.offerPhysicalRecovery(missions, 1, f.bay, 1);
        var initialConsumables = f.craft.find(f.id).orElseThrow().runtimeState().consumables();
        f.deck.advanceFixedTick(2, .1, Map.of()); // Unavailable bay cannot take possession yet.
        missions = f.runtime.advanceFixedTick(2, .1, missions, Map.of());
        assertEquals(mouth.position().translated(1, -.2), f.runtime.capture().flights().get(0).physical().position());
        assertTrue(f.hangars.find(f.id).isEmpty());
        assertEquals(initialConsumables, f.craft.find(f.id).orElseThrow().runtimeState().consumables());
        var physicalCraft = Stage228SmallCraftPersistenceMapper.capture(f.craft);
        var saved = SmallCraftLocalFlightPersistenceCodec.decode(SmallCraftLocalFlightPersistenceCodec.encode(f.runtime.capture()));
        var restored = new SmallCraftLocalFlightRuntime(f.craft, f.hangars, f.deck, f.catalog, missions, saved);
        f.deck.advanceFixedTick(3, .1, Map.of(f.bay.id(), f.bay));
        missions = restored.advanceFixedTick(3, .1, missions, Map.of());
        assertTrue(restored.capture().flights().isEmpty(), "Physical deck possession ends independent local flight");
        assertEquals(SmallCraftHangarCapacity.OccupancyState.RECOVERING, f.hangars.find(f.id).orElseThrow().state());
        assertEquals(SmallCraftMissionState.MissionStatus.RECOVERY_PENDING, missions.requireMission(1).status());
        var recovering = new SmallCraftLocalFlightRuntime(f.craft, f.hangars, f.deck, f.catalog, missions, restored.capture());
        f.deck.advanceFixedTick(4, .1, Map.of(f.bay.id(), f.bay));
        missions = recovering.advanceFixedTick(4, .1, missions, Map.of());
        assertEquals(SmallCraftMissionState.MissionStatus.COMPLETE, missions.requireMission(1).status());
        assertEquals(SmallCraftHangarCapacity.OccupancyState.SERVICING, f.hangars.find(f.id).orElseThrow().state());
        assertTrue(f.deck.active().isEmpty()); assertTrue(recovering.capture().flights().isEmpty());
        assertEquals(physicalCraft, Stage228SmallCraftPersistenceMapper.capture(f.craft));
        assertEquals(missions, recovering.advanceFixedTick(5, .1, missions, Map.of()));
    }

    private static Fixture fixture() {
        var catalog = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var craft = SmallCraftRegistry.empty(new SmallCraftFitAuthority(catalog));
        var id = craft.reserveIdentityForCompletedProduction();
        var design = catalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID);
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(design);
        craft.registerProducedCraft(new SmallCraftState(id, "faction.empire", design.id(), fit,
                new ShipEngineeringRuntime(catalog).initialize(fit, ShipEngineeringState.ConsumableState.empty()), ShipInstanceRuntimeState.legacyNeutral()));
        var hangars = SmallCraftHangarRegistry.empty(craft);
        var bay = new SmallCraftHangarCapacity.BayDefinition(new SmallCraftHangarCapacity.BayId("carrier.actual", "bay.1"),
                SmallCraftHangarCapacity.HostKind.SHIP, new ShipEngineeringCatalog.Dimensions3d(200, 100, 80), 2_000_000, 100_000_000, 1);
        hangars.assign(id, bay, SmallCraftHangarCapacity.OccupancyState.READY);
        var deck = new SmallCraftFlightDeckOperations(hangars, List.of(new SmallCraftFlightDeckOperations.DeckProfile(bay.id(), .2, .2)));
        deck.requestLaunch(id, bay.id(), 0);
        var missions = SmallCraftMissionState.empty().add(new SmallCraftMissionState.MissionOrder(1, id, FleetCommandState.OrderSource.PLAYER,
                SmallCraftMissionState.MissionType.CAP, new SmallCraftMissionState.MissionTarget(SmallCraftMissionState.TargetKind.AREA, "area.observed"),
                0, SmallCraftMissionState.MissionStatus.LAUNCH_QUEUED));
        return new Fixture(craft, id, hangars, deck, bay, catalog, missions,
                new SmallCraftLocalFlightRuntime(craft, hangars, deck, catalog, missions, SmallCraftLocalFlightState.empty()));
    }

    private record Fixture(SmallCraftRegistry craft, SmallCraftId id, SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations deck, SmallCraftHangarCapacity.BayDefinition bay, ShipEngineeringCatalog catalog,
            SmallCraftMissionState missions, SmallCraftLocalFlightRuntime runtime) { }
}
