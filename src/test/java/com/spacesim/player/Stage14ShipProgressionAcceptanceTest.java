package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage14ShipProgressionAcceptanceTest {
    @Test
    void realStationSellsExistingFleetThenPlayerSwitchesControlAndPersistsProgression() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(14_200L);
        SaleSetup setup = findSaleSetup(world);
        SimulationSession session = world.findSession(setup.systemId()).orElseThrow();
        Entity starterEntity = session.getEntityRegistry().find(setup.starter().localEntityId());
        Entity candidateEntity = session.getEntityRegistry().find(setup.candidate().localEntityId());
        Entity sellerEntity = setup.seller();
        assertNotNull(starterEntity);
        assertNotNull(candidateEntity);

        TransformComponent sellerTransform = sellerEntity.getComponent(TransformComponent.class);
        TransformComponent starterTransform = starterEntity.getComponent(TransformComponent.class);
        starterTransform.position.set(sellerTransform.position);
        starterTransform.velocity.setZero();

        InventoryComponent candidateCargo = candidateEntity.getComponent(InventoryComponent.class);
        candidateCargo.stock[0] = 3;
        TransformComponent candidateTransform = candidateEntity.getComponent(TransformComponent.class);
        float candidateX = candidateTransform.position.x;
        float candidateY = candidateTransform.position.y;
        int candidateCargoBefore = candidateCargo.getTotalStock();
        int fleetCountBefore = world.getFleetPlacements().size();
        int entityCountBefore = session.getEngine().getEntities().size();
        long playerMoneyBefore = 5_000_000L;
        WalletComponent sellerWallet = sellerEntity.getComponent(WalletComponent.class);
        long sellerMoneyBefore = sellerWallet.getBalanceMilliCredits();

        PlayerRuntime runtime = PlayerRuntime.create(
                world,
                content,
                new PlayerState(
                        playerMoneyBefore,
                        null,
                        List.of(),
                        List.of(setup.starter().id()),
                        setup.starter().id(),
                        List.of(setup.systemId()),
                        List.of(),
                        setup.systemId()));
        EntityIdComponent sellerId = sellerEntity.getComponent(EntityIdComponent.class);
        assertTrue(runtime.dockAt(sellerId.id));

        long price = 1_500_000L;
        PlayerShipSaleOffer offer = new PlayerShipSaleOffer(
                setup.systemId(), sellerId.id, setup.candidate().id(), price);
        PlayerShipProgressionService progression = new PlayerShipProgressionService(runtime);
        PlayerShipPurchaseView beforePurchase = progression.inspect(offer);
        assertEquals(PlayerShipPurchaseView.Status.AVAILABLE, beforePurchase.status());
        assertEquals(candidateEntity.getComponent(ArchetypeComponent.class).contentId,
                beforePurchase.archetypeContentId());

        assertTrue(progression.purchase(offer));

        assertEquals(playerMoneyBefore - price, runtime.player().walletMilliCredits());
        assertEquals(sellerMoneyBefore + price, sellerWallet.getBalanceMilliCredits());
        assertEquals(playerMoneyBefore + sellerMoneyBefore,
                runtime.player().walletMilliCredits() + sellerWallet.getBalanceMilliCredits());
        assertEquals(2, runtime.player().ownedFleetIds().size());
        assertTrue(runtime.player().ownedFleetIds().contains(setup.starter().id()));
        assertTrue(runtime.player().ownedFleetIds().contains(setup.candidate().id()));
        assertEquals(setup.starter().id(), runtime.player().activeFleetId());
        assertEquals(fleetCountBefore, world.getFleetPlacements().size());
        assertEquals(entityCountBefore, session.getEngine().getEntities().size());
        assertSame(candidateEntity,
                session.getEntityRegistry().find(setup.candidate().localEntityId()));
        assertEquals(candidateCargoBefore, candidateCargo.getTotalStock());
        assertEquals(candidateX, candidateTransform.position.x, 0f);
        assertEquals(candidateY, candidateTransform.position.y, 0f);

        assertTrue(runtime.undock());
        runtime.stopMovement();
        assertTrue(progression.switchActiveFleet(setup.candidate().id()));

        assertEquals(setup.candidate().id(), runtime.player().activeFleetId());
        assertNull(starterEntity.getComponent(PlayerControlledComponent.class));
        assertNotNull(candidateEntity.getComponent(PlayerControlledComponent.class));
        assertEquals(setup.candidate().localEntityId(), runtime.activeShipView().orElseThrow().localEntityId());
        assertEquals(fleetCountBefore, world.getFleetPlacements().size());
        assertEquals(candidateCargoBefore, candidateCargo.getTotalStock());
        assertEquals(candidateX, candidateTransform.position.x, 0f);
        assertEquals(candidateY, candidateTransform.position.y, 0f);

        PlayableWorldState persisted = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        PlayerRuntime restored = PlayerRuntime.restore(persisted, content, setup.systemId());
        Entity restoredCandidate = restored.world().findSession(setup.systemId()).orElseThrow()
                .getEntityRegistry().find(setup.candidate().localEntityId());

        assertEquals(2, restored.player().ownedFleetIds().size());
        assertTrue(restored.player().ownedFleetIds().contains(setup.starter().id()));
        assertTrue(restored.player().ownedFleetIds().contains(setup.candidate().id()));
        assertEquals(setup.candidate().id(), restored.player().activeFleetId());
        assertEquals(playerMoneyBefore - price, restored.player().walletMilliCredits());
        assertEquals(candidateCargoBefore,
                restoredCandidate.getComponent(InventoryComponent.class).getTotalStock());
        assertEquals(candidateX,
                restoredCandidate.getComponent(TransformComponent.class).position.x, 0f);
        assertEquals(candidateY,
                restoredCandidate.getComponent(TransformComponent.class).position.y, 0f);
        assertNotNull(restoredCandidate.getComponent(PlayerControlledComponent.class));
    }

    @Test
    void invalidOrUnaffordableOffersFailWithoutMovingMoneyOrOwnership() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(14_201L);
        SaleSetup setup = findSaleSetup(world);
        SimulationSession session = world.findSession(setup.systemId()).orElseThrow();
        Entity starter = session.getEntityRegistry().find(setup.starter().localEntityId());
        TransformComponent stationTransform = setup.seller().getComponent(TransformComponent.class);
        starter.getComponent(TransformComponent.class).position.set(stationTransform.position);
        starter.getComponent(TransformComponent.class).velocity.setZero();

        PlayerRuntime runtime = PlayerRuntime.create(
                world,
                content,
                new PlayerState(
                        100L,
                        null,
                        List.of(),
                        List.of(setup.starter().id()),
                        setup.starter().id(),
                        List.of(setup.systemId()),
                        List.of(),
                        setup.systemId()));
        EntityIdComponent sellerId = setup.seller().getComponent(EntityIdComponent.class);
        PlayerShipSaleOffer offer = new PlayerShipSaleOffer(
                setup.systemId(), sellerId.id, setup.candidate().id(), 1_000L);
        PlayerShipProgressionService progression = new PlayerShipProgressionService(runtime);

        assertEquals(PlayerShipPurchaseView.Status.NOT_DOCKED_AT_SELLER,
                progression.inspect(offer).status());
        assertFalse(progression.purchase(offer));
        assertTrue(runtime.dockAt(sellerId.id));
        long sellerBefore = setup.seller().getComponent(WalletComponent.class).getBalanceMilliCredits();

        assertEquals(PlayerShipPurchaseView.Status.INSUFFICIENT_FUNDS,
                progression.inspect(offer).status());
        assertFalse(progression.purchase(offer));
        assertEquals(100L, runtime.player().walletMilliCredits());
        assertEquals(List.of(setup.starter().id()), runtime.player().ownedFleetIds());
        assertEquals(sellerBefore,
                setup.seller().getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertTrue(world.findFleet(setup.candidate().id()).isPresent());
    }

    @Test
    void activeSwitchRequiresUndockedOwnedMaterializedFleetAndStoppedCurrentShip() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(14_202L);
        SaleSetup setup = findSaleSetup(world);
        SimulationSession session = world.findSession(setup.systemId()).orElseThrow();
        Entity starter = session.getEntityRegistry().find(setup.starter().localEntityId());
        starter.getComponent(TransformComponent.class).velocity.setZero();

        PlayerRuntime runtime = PlayerRuntime.create(
                world,
                content,
                new PlayerState(
                        10_000L,
                        null,
                        List.of(),
                        List.of(setup.starter().id(), setup.candidate().id()),
                        setup.starter().id(),
                        List.of(setup.systemId()),
                        List.of(),
                        setup.systemId()));
        PlayerShipProgressionService progression = new PlayerShipProgressionService(runtime);

        assertTrue(runtime.setMovementIntent(1f, 0f));
        runtime.advanceFrame(0.1f);
        assertFalse(progression.switchActiveFleet(setup.candidate().id()));
        runtime.stopMovement();
        runtime.advanceFrame(0.1f);
        assertTrue(progression.switchActiveFleet(setup.candidate().id()));
        assertEquals(setup.candidate().id(), runtime.player().activeFleetId());

        FleetId unowned = world.getFleetPlacements().stream()
                .map(FleetPlacementState::id)
                .filter(id -> !runtime.player().ownedFleetIds().contains(id))
                .findFirst().orElseThrow();
        assertFalse(progression.switchActiveFleet(unowned));
    }

    private static SaleSetup findSaleSetup(WorldSimulation world) {
        for (FleetPlacementState candidate : world.getFleetPlacements()) {
            if (candidate.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            SimulationSession session = world.findSession(candidate.systemId()).orElseThrow();
            Entity candidateEntity = session.getEntityRegistry().find(candidate.localEntityId());
            FactionComponent candidateFaction = candidateEntity == null
                    ? null : candidateEntity.getComponent(FactionComponent.class);
            InventoryComponent candidateCargo = candidateEntity == null
                    ? null : candidateEntity.getComponent(InventoryComponent.class);
            if (candidateEntity == null
                    || candidateEntity.getComponent(ShipComponent.class) == null
                    || candidateEntity.getComponent(ArchetypeComponent.class) == null
                    || candidateFaction == null
                    || candidateCargo == null
                    || candidateCargo.getFreeCapacity() < 3) {
                continue;
            }
            Entity seller = null;
            for (Entity entity : session.getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                if (entity.getComponent(MarketComponent.class) != null
                        && entity.getComponent(WalletComponent.class) != null
                        && entity.getComponent(EntityIdComponent.class) != null
                        && entity.getComponent(TransformComponent.class) != null
                        && faction != null
                        && faction.factionId == candidateFaction.factionId) {
                    seller = entity;
                    break;
                }
            }
            if (seller == null) {
                continue;
            }
            FleetPlacementState starter = world.getFleetPlacements().stream()
                    .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                    .filter(value -> candidate.systemId().equals(value.systemId()))
                    .filter(value -> !candidate.id().equals(value.id()))
                    .findFirst().orElse(null);
            if (starter != null) {
                return new SaleSetup(candidate.systemId(), starter, candidate, seller);
            }
        }
        throw new AssertionError("Demo galaxy has no Stage-14B physical sale setup");
    }

    private record SaleSetup(
            com.spacesim.world.StarSystemId systemId,
            FleetPlacementState starter,
            FleetPlacementState candidate,
            Entity seller) {
    }
}
