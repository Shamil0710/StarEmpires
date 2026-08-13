package com.spacesim.simulation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.GameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityLifecycleServiceTest {
    private static final long ROOT_SEED = 0x9A2026L;

    @Test
    void createВыдаётDeterministicIdРегистрируетИПереживаетSaveLoad() {
        SimulationSession session = SimulationSession.createDemo(ROOT_SEED);
        long expectedIdValue = session.getNextEntityIdValue();
        int ledgerSizeBefore = session.getLedger().size();

        Entity created = emptyPersistentCandidate("Runtime Probe", 222f, 333f);
        EntityId id = session.createEntity(created);

        assertEquals(expectedIdValue, id.value());
        assertSame(created, session.getEntityRegistry().require(id));
        assertEquals(expectedIdValue + 1L, session.getNextEntityIdValue());
        assertEquals(ledgerSizeBefore, session.getLedger().size(),
                "Пустой structural create не должен создавать economic ledger запись");

        GameState saved = session.snapshot();
        SimulationSession restored = SimulationSession.restore(saved);
        Entity restoredEntity = restored.getEntityRegistry().find(id);
        assertNotNull(restoredEntity);
        assertEquals("Runtime Probe", restoredEntity.getComponent(IdentityComponent.class).name);
        assertEquals(saved, restored.snapshot());

        EntityId next = restored.createEntity(emptyPersistentCandidate("Runtime Probe 2", 444f, 555f));
        assertEquals(expectedIdValue + 1L, next.value());
    }

    @Test
    void createОтклоняетLivePreidentifiedИEconomicallyNonEmptyEntity() {
        SimulationSession session = SimulationSession.createDemo(ROOT_SEED);
        Entity live = session.getEngine().getEntities().first();
        assertThrows(IllegalArgumentException.class, () -> session.createEntity(live));

        Entity preidentified = emptyPersistentCandidate("Pre-ID", 1f, 2f)
                .add(new EntityIdComponent(new EntityId(999_999L)));
        assertThrows(IllegalArgumentException.class, () -> session.createEntity(preidentified));

        Entity funded = emptyPersistentCandidate("Funded", 1f, 2f)
                .add(new WalletComponent(1L));
        assertThrows(IllegalStateException.class, () -> session.createEntity(funded));

        Entity stocked = emptyPersistentCandidate("Stocked", 1f, 2f)
                .add(new InventoryComponent());
        stocked.getComponent(InventoryComponent.class).stock[0] = 1;
        assertThrows(IllegalStateException.class, () -> session.createEntity(stocked));
    }

    @Test
    void removeНемедленноОчищаетTradeReferencesИНеОставляетEntityВSnapshot() {
        SimulationSession session = SimulationSession.createDemo(ROOT_SEED);
        int ledgerSizeBefore = session.getLedger().size();

        Entity market = emptyMarket("Ephemeral Market", 700f, 700f);
        EntityId marketId = session.createEntity(market);
        Entity trader = findFirst(session, TradeAIComponent.class);
        TradeAIComponent trade = trader.getComponent(TradeAIComponent.class);
        trade.buyStationId = marketId;
        trade.sellStationId = marketId;
        trade.targetStationId = marketId;
        trade.targetItem = 0;
        trade.targetAmount = 10;
        trade.expectedProfitMilliCredits = 123L;
        trade.state = TradeAIComponent.State.TRAVEL_TO_BUY;
        trade.routeSearchCooldown = 9f;

        assertTrue(session.removeEntity(marketId));
        assertFalse(session.getEntityRegistry().contains(marketId));
        assertNull(trade.buyStationId);
        assertNull(trade.sellStationId);
        assertNull(trade.targetStationId);
        assertEquals(-1, trade.targetItem);
        assertEquals(0, trade.targetAmount);
        assertEquals(0L, trade.expectedProfitMilliCredits);
        assertEquals(TradeAIComponent.State.IDLE, trade.state);
        assertEquals(0f, trade.routeSearchCooldown, 0f);
        assertEquals(ledgerSizeBefore, session.getLedger().size());
        assertFalse(session.snapshot().entities().stream().anyMatch(state -> state.id().equals(marketId)));
        assertFalse(session.removeEntity(marketId));
    }

    @Test
    void removeОчищаетMiningBaseReferenceДоСледующегоSnapshot() {
        SimulationSession session = SimulationSession.createDemo(ROOT_SEED);
        Entity base = emptyMarket("Temporary Mining Base", 500f, 500f);
        EntityId baseId = session.createEntity(base);
        Entity miner = findFirst(session, MiningComponent.class);
        MiningComponent mining = miner.getComponent(MiningComponent.class);
        mining.homeBaseId = baseId;
        mining.state = MiningComponent.State.UNLOADING;

        assertTrue(session.removeEntity(baseId));
        assertNull(mining.homeBaseId);
        assertEquals(MiningComponent.State.RETURNING_TO_BASE, mining.state);

        GameState saved = session.snapshot();
        SimulationSession restored = SimulationSession.restore(saved);
        MiningComponent restoredMining = findFirst(restored, MiningComponent.class)
                .getComponent(MiningComponent.class);
        assertNull(restoredMining.homeBaseId);
    }

    @Test
    void removeОтклоняетEntityСДеньгамиИлиТоваромНеМеняяRegistry() {
        SimulationSession session = SimulationSession.createDemo(ROOT_SEED);
        Entity station = findFirst(session, WalletComponent.class);
        EntityId stationId = station.getComponent(EntityIdComponent.class).id;
        WalletComponent wallet = station.getComponent(WalletComponent.class);
        InventoryComponent inventory = station.getComponent(InventoryComponent.class);
        assertTrue(wallet.getBalanceMilliCredits() > 0L || inventory.getTotalStock() > 0);

        assertThrows(IllegalStateException.class, () -> session.removeEntity(stationId));
        assertSame(station, session.getEntityRegistry().require(stationId));
    }

    private static Entity emptyPersistentCandidate(String name, float x, float y) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(transform);
    }

    private static Entity emptyMarket(String name, float x, float y) {
        return emptyPersistentCandidate(name, x, y)
                .add(new InventoryComponent())
                .add(new WalletComponent())
                .add(new MarketComponent());
    }

    private static <T> Entity findFirst(SimulationSession session, Class<T> componentType) {
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(componentType.asSubclass(com.badlogic.ashley.core.Component.class)) != null) {
                return entity;
            }
        }
        throw new AssertionError("Компонент не найден в demo session: " + componentType.getSimpleName());
    }
}
