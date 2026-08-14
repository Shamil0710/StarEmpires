package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerOwnershipServiceTest {
    @Test
    void purchaseAndSaleMoveMoneyAndOwnershipWithoutDuplicatingFleet() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(12_001L);
        FleetPlacementState placement = world.getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .findFirst()
                .orElseThrow();
        SimulationSession session = world.findSession(placement.systemId()).orElseThrow();
        Entity fleetEntity = session.getEntityRegistry().find(placement.localEntityId());
        FactionComponent legalContext = fleetEntity.getComponent(FactionComponent.class);
        assertNotNull(legalContext);
        int factionBefore = legalContext.factionId;

        Entity counterparty = firstMarketWithWallet(session);
        WalletComponent counterpartyWallet = counterparty.getComponent(WalletComponent.class);
        long counterpartyBefore = counterpartyWallet.getBalanceMilliCredits();
        long playerBefore = 2_000_000L;
        PlayerRuntime runtime = PlayerRuntime.create(
                world,
                content,
                new PlayerState(
                        playerBefore,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        List.of(placement.systemId()),
                        List.of(),
                        placement.systemId()));
        PlayerOwnershipService ownership = new PlayerOwnershipService(runtime);
        int fleetCountBefore = world.getFleetPlacements().size();
        int entityCountBefore = session.getEngine().getEntities().size();

        long purchasePrice = 250_000L;
        assertTrue(ownership.purchaseFleet(
                placement.id(),
                counterpartyWallet,
                purchasePrice,
                session.getLedger(),
                "SHIPYARD"));
        assertEquals(playerBefore - purchasePrice, runtime.player().walletMilliCredits());
        assertEquals(List.of(placement.id()), runtime.player().ownedFleetIds());
        assertEquals(placement.id(), runtime.player().activeFleetId());
        assertEquals(counterpartyBefore + purchasePrice, counterpartyWallet.getBalanceMilliCredits());
        assertEquals(playerBefore + counterpartyBefore,
                runtime.player().walletMilliCredits() + counterpartyWallet.getBalanceMilliCredits());
        assertEquals(fleetCountBefore, world.getFleetPlacements().size());
        assertEquals(entityCountBefore, session.getEngine().getEntities().size());
        assertEquals(factionBefore, fleetEntity.getComponent(FactionComponent.class).factionId);

        PlayableWorldState persisted = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        assertEquals(List.of(placement.id()), persisted.playerState().ownedFleetIds());

        long salePrice = 150_000L;
        assertTrue(ownership.sellFleet(
                placement.id(),
                counterpartyWallet,
                salePrice,
                session.getLedger(),
                "SHIPYARD"));
        assertEquals(playerBefore - purchasePrice + salePrice, runtime.player().walletMilliCredits());
        assertTrue(runtime.player().ownedFleetIds().isEmpty());
        assertNull(runtime.player().activeFleetId());
        assertEquals(counterpartyBefore + purchasePrice - salePrice,
                counterpartyWallet.getBalanceMilliCredits());
        assertEquals(playerBefore + counterpartyBefore,
                runtime.player().walletMilliCredits() + counterpartyWallet.getBalanceMilliCredits());
        assertEquals(fleetCountBefore, world.getFleetPlacements().size());
        assertEquals(entityCountBefore, session.getEngine().getEntities().size());
        assertEquals(factionBefore, fleetEntity.getComponent(FactionComponent.class).factionId);
    }

    @Test
    void failedPurchaseIsAtomic() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(12_002L);
        FleetPlacementState placement = world.getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .findFirst()
                .orElseThrow();
        SimulationSession session = world.findSession(placement.systemId()).orElseThrow();
        WalletComponent seller = firstMarketWithWallet(session).getComponent(WalletComponent.class);
        long sellerBefore = seller.getBalanceMilliCredits();
        PlayerRuntime runtime = PlayerRuntime.create(
                world,
                content,
                new PlayerState(
                        10L,
                        null,
                        List.of(),
                        List.of(),
                        null,
                        List.of(placement.systemId()),
                        List.of(),
                        placement.systemId()));

        boolean purchased = new PlayerOwnershipService(runtime).purchaseFleet(
                placement.id(), seller, 1000L, session.getLedger(), "SHIPYARD");

        assertFalse(purchased);
        assertEquals(10L, runtime.player().walletMilliCredits());
        assertTrue(runtime.player().ownedFleetIds().isEmpty());
        assertEquals(sellerBefore, seller.getBalanceMilliCredits());
        assertTrue(world.findFleet(placement.id()).isPresent());
    }

    @Test
    void destructionReconcilesPersistentPlayerOwnership() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(12_003L);
        FleetPlacementState placement = world.getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .findFirst()
                .orElseThrow();
        PlayerRuntime runtime = PlayerRuntime.create(
                world,
                content,
                new PlayerState(
                        1000L,
                        null,
                        List.of(),
                        List.of(placement.id()),
                        placement.id(),
                        List.of(placement.systemId()),
                        List.of(),
                        placement.systemId()));

        world.destroyEntity(
                placement.systemId(),
                placement.localEntityId(),
                DestructionPolicy.destroyAll());

        assertTrue(runtime.player().ownedFleetIds().isEmpty());
        assertNull(runtime.player().activeFleetId());
        assertTrue(runtime.snapshot().playerState().ownedFleetIds().isEmpty());
        assertTrue(world.findFleet(placement.id()).isEmpty());
    }

    private static Entity firstMarketWithWallet(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(WalletComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Demo session has no market counterparty");
    }
}
