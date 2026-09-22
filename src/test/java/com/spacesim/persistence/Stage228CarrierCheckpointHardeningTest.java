package com.spacesim.persistence;

import com.spacesim.campaign.GeneratedCampaignCoordinator;
import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetId;
import com.spacesim.world.ProductionSmallCraftFixture;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftFlightDeckOperations;
import com.spacesim.world.SmallCraftFlightDeckOperations.DeckProfile;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationKind;
import com.spacesim.world.SmallCraftFlightDeckOperations.Request;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftMissionState;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.LogisticsState;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.PendingDelivery;
import com.spacesim.world.SmallCraftRegistry;
import com.spacesim.world.SmallCraftState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage228CarrierCheckpointHardeningTest {
    private static final String HOST = "carrier.save.alpha";

    @Test
    void canonicalCarrierLifecycleCheckpointRoundTripsWithoutGrantingOrResettingState() {
        GeneratedCampaignCoordinator coordinator = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        SmallCraftFitAuthority fitAuthority = ProductionSmallCraftFixture.fitAuthority();
        SmallCraftRegistry registry = SmallCraftRegistry.empty(fitAuthority);

        SmallCraftId parked = register(registry, 0L, 0d, 0d, 1d, 0d);
        SmallCraftId ready = register(registry, 12L, 48d, 240d, 1d, 0d);
        SmallCraftId servicing = register(registry, 1L, 4d, 20d, 1d, 120d);
        SmallCraftId damaged = register(registry, 6L, 24d, 180d, 0.42d, 500d);
        SmallCraftId launchQueued = register(registry, 10L, 40d, 220d, 1d, 0d);
        SmallCraftId recoveryQueued = register(registry, 4L, 16d, 90d, 0.80d, 300d);
        SmallCraftId deployed = register(registry, 8L, 32d, 160d, 0.91d, 200d);
        SmallCraftId replacement = register(registry, 0L, 0d, 0d, 1d, 0d);
        SmallCraftId lostOriginal = registry.reserveIdentityForCompletedProduction();

        BayId parkedBayId = new BayId(HOST, "bay.parked");
        BayId readyBayId = new BayId(HOST, "bay.ready");
        BayId serviceBayId = new BayId(HOST, "bay.service");
        BayId repairBayId = new BayId(HOST, "bay.repair");
        BayId launchBayId = new BayId(HOST, "bay.launch");
        BayId recoveryBayId = new BayId(HOST, "bay.recovery");
        List<BayDefinition> definitions = List.of(
                bay(parkedBayId), bay(readyBayId), bay(serviceBayId),
                bay(repairBayId), bay(launchBayId), bay(recoveryBayId));

        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.restore(
                registry,
                List.of(
                        new SmallCraftHangarRegistry.Assignment(
                                parked, parkedBayId, HostKind.SHIP, OccupancyState.PARKED),
                        new SmallCraftHangarRegistry.Assignment(
                                ready, readyBayId, HostKind.SHIP, OccupancyState.READY),
                        new SmallCraftHangarRegistry.Assignment(
                                servicing, serviceBayId, HostKind.SHIP, OccupancyState.SERVICING),
                        new SmallCraftHangarRegistry.Assignment(
                                damaged, repairBayId, HostKind.SHIP, OccupancyState.SERVICING),
                        new SmallCraftHangarRegistry.Assignment(
                                launchQueued, launchBayId, HostKind.SHIP, OccupancyState.READY)));

        SmallCraftFlightDeckOperations deck = SmallCraftFlightDeckOperations.restore(
                hangars,
                definitions.stream()
                        .map(value -> new DeckProfile(value.id(), 12d, 14d))
                        .toList(),
                List.of(
                        new Request(launchQueued, launchBayId, OperationKind.LAUNCH, 40L),
                        new Request(recoveryQueued, recoveryBayId, OperationKind.RECOVERY, 40L)),
                List.of(),
                -1L);

        SmallCraftMissionState missions = new SmallCraftMissionState(
                4L,
                List.of(
                        mission(1L, launchQueued, MissionType.CAP, MissionStatus.LAUNCH_QUEUED),
                        mission(2L, recoveryQueued, MissionType.RECOVER, MissionStatus.RECOVERY_PENDING),
                        mission(3L, deployed, MissionType.INTERCEPTION, MissionStatus.ACTIVE)));

        LogisticsState logistics = new LogisticsState(List.of(new PendingDelivery(
                replacement,
                "station.replacement.yard",
                registry.find(replacement).orElseThrow().designId())));

        List<CarrierWingAssignment> wings = List.of(new CarrierWingAssignment(
                new FleetId(100L),
                HOST,
                registry.find(ready).orElseThrow().stableFactionId(),
                List.of(
                        parked, ready, servicing, damaged,
                        launchQueued, recoveryQueued, deployed, lostOriginal)));

        Stage228GeneratedCampaignPersistentState original =
                Stage228GeneratedCampaignPersistentState.compose(
                        coordinator.captureState(),
                        Stage228SmallCraftPersistenceMapper.capture(registry),
                        Stage228HangarPersistenceMapper.capture(hangars),
                        Stage228FlightDeckPersistenceMapper.capture(deck),
                        Stage228OperationsPersistenceMapper.capture(missions, logistics, wings));

        byte[] bytes = Stage228GeneratedCampaignPersistenceCodec.encode(original);
        Stage228GeneratedCampaignPersistentState decoded =
                Stage228GeneratedCampaignPersistenceCodec.decode(bytes);
        Stage228CampaignAuthority restored = Stage228CampaignAuthority.restore(decoded);

        assertEquals(original, restored.captureState());
        assertEquals(OccupancyState.PARKED,
                restored.hangars().find(parked).orElseThrow().state());
        assertEquals(OccupancyState.READY,
                restored.hangars().find(ready).orElseThrow().state());
        assertEquals(OccupancyState.SERVICING,
                restored.hangars().find(servicing).orElseThrow().state());
        assertEquals(OccupancyState.SERVICING,
                restored.hangars().find(damaged).orElseThrow().state());
        assertTrue(restored.flightDeck().queued().stream()
                .anyMatch(value -> value.craftId().equals(launchQueued)
                        && value.kind() == OperationKind.LAUNCH));
        assertTrue(restored.flightDeck().queued().stream()
                .anyMatch(value -> value.craftId().equals(recoveryQueued)
                        && value.kind() == OperationKind.RECOVERY));
        assertEquals(MissionStatus.ACTIVE,
                restored.missions().activeMissionFor(deployed).orElseThrow().status());
        assertEquals(replacement,
                restored.logistics().pendingDeliveries().get(0).craftId());
        assertTrue(restored.smallCraft().find(lostOriginal).isEmpty(),
                "lost identity must remain lost after save/load");
        assertTrue(restored.carrierWings().get(0).craftIds().contains(lostOriginal),
                "wing roster must retain lost identity as loss evidence");
        assertFalse(restored.carrierWings().get(0).craftIds().contains(replacement),
                "pending replacement must not appear in wing before physical delivery");
        assertEquals(lostOriginal.value() + 1L, restored.smallCraft().nextIdValue(),
                "allocator watermark must preserve consumed/lost identity");
    }

    @Test
    void v3MigrationAddsEmptyOperationsWithoutSynthesizingMissionsDeliveriesOrWings() {
        GeneratedCampaignCoordinator coordinator = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        byte[] legacyV3 = encodeLegacyV3(
                coordinator.captureState(),
                Stage228SmallCraftPersistentState.empty(),
                Stage228HangarPersistentState.empty(),
                Stage228FlightDeckPersistentState.empty());

        Stage228GeneratedCampaignPersistentState migrated =
                Stage228GeneratedCampaignPersistenceCodec.decode(legacyV3);

        assertTrue(migrated.operations().missions().isEmpty());
        assertEquals(1L, migrated.operations().nextMissionId());
        assertTrue(migrated.operations().pendingDeliveries().isEmpty());
        assertTrue(migrated.operations().carrierWings().isEmpty());
    }

    @Test
    void malformedOperationsFailBeforeLiveCampaignCanBeRestored() {
        GeneratedCampaignCoordinator coordinator = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        SmallCraftFitAuthority fitAuthority = ProductionSmallCraftFixture.fitAuthority();
        SmallCraftRegistry registry = SmallCraftRegistry.empty(fitAuthority);
        SmallCraftId craft = register(registry, 1L, 4d, 20d, 1d, 0d);

        Stage228OperationsPersistentState malformed =
                new Stage228OperationsPersistentState(
                        Stage228OperationsPersistentState.CURRENT_VERSION,
                        Stage228OperationsPersistentState.CURRENT_RUNTIME_VERSION,
                        Stage228OperationsPersistentState.CURRENT_SEMANTIC_CONTRACT,
                        2L,
                        List.of(new Stage228OperationsPersistentState.MissionState(
                                1L,
                                new SmallCraftId(9999L),
                                OrderSource.AI,
                                MissionType.CAP,
                                TargetKind.AREA,
                                "area.malformed",
                                10L,
                                MissionStatus.ACTIVE)),
                        List.of(),
                        List.of());

        Stage228GeneratedCampaignPersistentState checkpoint =
                Stage228GeneratedCampaignPersistentState.compose(
                        coordinator.captureState(),
                        Stage228SmallCraftPersistenceMapper.capture(registry),
                        Stage228HangarPersistentState.empty(),
                        Stage228FlightDeckPersistentState.empty(),
                        malformed);

        assertThrows(IllegalArgumentException.class,
                () -> Stage228CampaignAuthority.restore(checkpoint));
        assertTrue(registry.find(craft).isPresent(),
                "failed restore validation must not mutate source live registry");
    }

    private static SmallCraftId register(
            SmallCraftRegistry registry,
            long ammoCount,
            double ammoMass,
            double reactionMass,
            double weaponIntegrity,
            double serviceAge) {
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, ammoCount, ammoMass, reactionMass, weaponIntegrity, serviceAge));
        return id;
    }

    private static BayDefinition bay(BayId id) {
        return new BayDefinition(
                id,
                HostKind.SHIP,
                new Dimensions3d(500d, 500d, 500d),
                100_000_000d,
                100_000_000d,
                1d);
    }

    private static MissionOrder mission(
            long id,
            SmallCraftId craftId,
            MissionType type,
            MissionStatus status) {
        TargetKind kind = type == MissionType.RECOVER ? TargetKind.HOST : TargetKind.AREA;
        return new MissionOrder(
                id,
                craftId,
                OrderSource.AI,
                type,
                new MissionTarget(kind, type == MissionType.RECOVER ? HOST : "area.m22_8m"),
                40L,
                status);
    }

    private static byte[] encodeLegacyV3(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState craft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState deck) {
        try {
            byte[] stage21Bytes = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(stage21);
            byte[] craftBytes = Stage228SmallCraftPersistenceCodec.encode(craft);
            byte[] hangarBytes = Stage228HangarPersistenceCodec.encode(hangars);
            byte[] deckBytes = Stage228FlightDeckPersistenceCodec.encode(deck);
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            try (java.io.DataOutputStream out = new java.io.DataOutputStream(buffer)) {
                out.writeInt(0x53323843);
                out.writeInt(3);
                out.writeInt(3);
                out.writeUTF("m22.8.generated-campaign.v3");
                writePayload(out, stage21Bytes);
                writePayload(out, craftBytes);
                writePayload(out, hangarBytes);
                writePayload(out, deckBytes);
            }
            return buffer.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void writePayload(
            java.io.DataOutputStream out,
            byte[] payload) throws java.io.IOException {
        out.writeInt(payload.length);
        out.write(payload);
    }
}
