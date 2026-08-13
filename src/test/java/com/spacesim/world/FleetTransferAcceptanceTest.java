package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.FleetTransferStateMapper;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetTransferAcceptanceTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);

    @Test
    void fleetSurvivesMidTransitSaveLoadWithoutDuplicationOrEconomicValueLoss() {
        WorldState initial = initialWorld();
        Map<StarSystemId, SimulationSession> sessions = restoreSessions(initial);
        FleetWorldService fleets = new FleetWorldService(
                sessions, initial.nextFleetIdValue(), initial.fleets());

        FleetPlacementState sourcePlacement = initial.fleets().stream()
                .filter(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(placement -> placement.systemId().equals(ALPHA))
                .filter(placement -> hasTransferableEconomicState(sessions.get(ALPHA), placement.localEntityId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Demo world must contain a transferable fleet in Alpha"));

        Entity sourceEntity = sessions.get(ALPHA).getEntityRegistry().require(sourcePlacement.localEntityId());
        InventoryComponent sourceInventory = sourceEntity.getComponent(InventoryComponent.class);
        int addedCargo = Math.min(5, sourceInventory.getFreeCapacity());
        assertTrue(addedCargo > 0, "Acceptance fleet must have free cargo capacity");
        sourceInventory.stock[0] += addedCargo;

        EntityState expectedPayload = FleetTransferStateMapper.sanitize(EntityStateMapper.capture(sourceEntity));
        assertNotNull(expectedPayload.wallet());
        assertTrue(expectedPayload.wallet().balanceMilliCredits() > 0L);
        assertNotNull(expectedPayload.inventory());
        assertTrue(expectedPayload.inventory().stock().stream().mapToInt(Integer::intValue).sum() > 0);

        EntityId oldLocalId = sourcePlacement.localEntityId();
        int originLedgerEntries = sessions.get(ALPHA).getLedger().size();
        int destinationLedgerEntries = sessions.get(BETA).getLedger().size();

        FleetPlacementState transit = fleets.beginTransfer(sourcePlacement.id(), BETA);

        assertEquals(sourcePlacement.id(), transit.id());
        assertEquals(FleetLocationKind.IN_TRANSIT, transit.locationKind());
        assertEquals(ALPHA, transit.transitState().originSystemId());
        assertEquals(BETA, transit.transitState().destinationSystemId());
        assertEquals(expectedPayload, transit.transitState().entityState());
        assertFalse(sessions.get(ALPHA).getEntityRegistry().contains(oldLocalId));
        assertTrue(fleets.findByLocal(ALPHA, oldLocalId).isEmpty());
        assertEquals(originLedgerEntries, sessions.get(ALPHA).getLedger().size());
        assertEquals(destinationLedgerEntries, sessions.get(BETA).getLedger().size());

        WorldState midTransit = snapshot(initial, sessions, fleets);
        byte[] encoded = WorldStateCodec.encode(midTransit);
        WorldState decoded = WorldStateCodec.decode(encoded);
        assertEquals(midTransit, decoded);
        assertArrayEquals(encoded, WorldStateCodec.encode(decoded));
        assertEquals(1L, decoded.fleets().stream()
                .filter(placement -> placement.id().equals(sourcePlacement.id()))
                .count());
        assertEquals(FleetLocationKind.IN_TRANSIT,
                decoded.fleets().stream()
                        .filter(placement -> placement.id().equals(sourcePlacement.id()))
                        .findFirst().orElseThrow().locationKind());

        Map<StarSystemId, SimulationSession> loadedSessions = restoreSessions(decoded);
        FleetWorldService loadedFleets = new FleetWorldService(
                loadedSessions, decoded.nextFleetIdValue(), decoded.fleets());
        long expectedDestinationLocalId = loadedSessions.get(BETA).getNextEntityIdValue();
        FleetPlacementState arrived = loadedFleets.completeTransfer(sourcePlacement.id(), 123.5f, -44.25f);

        assertEquals(sourcePlacement.id(), arrived.id());
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(BETA, arrived.systemId());
        assertEquals(expectedDestinationLocalId, arrived.localEntityId().value());
        assertTrue(loadedSessions.get(BETA).getEntityRegistry().contains(arrived.localEntityId()));
        assertFalse(loadedSessions.get(ALPHA).getEntityRegistry().contains(oldLocalId));
        assertEquals(1L, loadedFleets.snapshots().stream()
                .filter(placement -> placement.id().equals(sourcePlacement.id()))
                .count());

        Entity arrivedEntity = loadedSessions.get(BETA).getEntityRegistry().require(arrived.localEntityId());
        EntityState arrivedPayload = FleetTransferStateMapper.sanitize(EntityStateMapper.capture(arrivedEntity));
        assertEquals(expectedPayload.inventory(), arrivedPayload.inventory());
        assertEquals(expectedPayload.wallet(), arrivedPayload.wallet());
        assertEquals(expectedPayload.faction(), arrivedPayload.faction());
        assertEquals(expectedPayload.ship(), arrivedPayload.ship());
        assertEquals(expectedPayload.combat(), arrivedPayload.combat());
        assertEquals(123.5f, arrivedEntity.getComponent(com.spacesim.components.TransformComponent.class).position.x);
        assertEquals(-44.25f, arrivedEntity.getComponent(com.spacesim.components.TransformComponent.class).position.y);
        assertEquals(originLedgerEntries, loadedSessions.get(ALPHA).getLedger().size());
        assertEquals(destinationLedgerEntries, loadedSessions.get(BETA).getLedger().size());

        WorldState completed = snapshot(decoded, loadedSessions, loadedFleets);
        assertEquals(completed, WorldStateCodec.decode(WorldStateCodec.encode(completed)));
    }

    private static boolean hasTransferableEconomicState(SimulationSession session, EntityId entityId) {
        Entity entity = session.getEntityRegistry().find(entityId);
        if (entity == null) {
            return false;
        }
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        WalletComponent wallet = entity.getComponent(WalletComponent.class);
        return inventory != null
                && inventory.getFreeCapacity() > 0
                && wallet != null
                && wallet.getBalanceMilliCredits() > 0L;
    }

    private static WorldState snapshot(
            WorldState template,
            Map<StarSystemId, SimulationSession> sessions,
            FleetWorldService fleets) {
        List<StarSystemSimulationState> systemStates = new ArrayList<>(template.systems().size());
        for (StarSystemSimulationState system : template.systems()) {
            systemStates.add(new StarSystemSimulationState(
                    system.systemId(), sessions.get(system.systemId()).snapshot()));
        }
        return new WorldState(
                WorldState.CURRENT_VERSION,
                template.topology(),
                List.copyOf(systemStates),
                template.factions(),
                template.factionStrategies(),
                template.nextConstructionProjectIdValue(),
                template.constructionProjects(),
                template.factionEconomicPressures(),
                fleets.nextIdValue(),
                fleets.snapshots());
    }

    private static Map<StarSystemId, SimulationSession> restoreSessions(WorldState state) {
        Map<StarSystemId, SimulationSession> sessions = new HashMap<>();
        for (StarSystemSimulationState system : state.systems()) {
            sessions.put(system.systemId(), SimulationSession.restore(system.simulationState(), CONTENT));
        }
        return sessions;
    }

    private static WorldState initialWorld() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "Transfer Test Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta))),
                List.of(new JumpConnection(ALPHA, BETA)));
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(ALPHA, SimulationSession.createDemo(0xA11A1L, CONTENT).snapshot()),
                        new StarSystemSimulationState(BETA, SimulationSession.createDemo(0xBE7A1L, CONTENT).snapshot())));
    }
}
