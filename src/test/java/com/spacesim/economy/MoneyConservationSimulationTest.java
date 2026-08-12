package com.spacesim.economy;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoWorldFactory;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.simulation.SimulationClock;
import com.spacesim.simulation.SimulationLoop;
import com.spacesim.simulation.SimulationRandom;
import com.spacesim.systems.AsteroidSpawnSystem;
import com.spacesim.systems.ConsumptionSystem;
import com.spacesim.systems.MarketSystem;
import com.spacesim.systems.MiningSystem;
import com.spacesim.systems.PriceRecorderSystem;
import com.spacesim.systems.ProductionSystem;
import com.spacesim.systems.TradeAISystem;
import com.spacesim.util.SpatialHashGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyConservationSimulationTest {
    @Test
    void demoWorldНеСоздаётИНЕУничтожаетДеньгиОбычнойЭкономическойАктивностью() {
        SimulationRandom random = new SimulationRandom(0xCA5E_2026L);
        GlobalEventManager events = new GlobalEventManager(random.createStream("economy-events"), 0d);
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = new Engine();

        engine.addSystem(new MarketSystem(events));
        engine.addSystem(new ConsumptionSystem(events, ledger));
        engine.addSystem(new ProductionSystem(ledger));
        engine.addSystem(new AsteroidSpawnSystem(
                AsteroidSpawnConfig.demoWorld(),
                random.createStream("asteroid-spawn"),
                ledger));
        engine.addSystem(new MiningSystem(ledger));
        engine.addSystem(new TradeAISystem(new SpatialHashGrid(Constants.CELL_SIZE), ledger));
        engine.addSystem(new PriceRecorderSystem());

        for (Entity entity : DemoWorldFactory.createEntities()) {
            engine.addEntity(entity);
        }

        long moneyBefore = totalMoney(engine);
        SimulationClock clock = new SimulationClock(0.1f);
        SimulationLoop loop = new SimulationLoop(clock, events, engine);
        for (int frame = 0; frame < 60; frame++) {
            loop.advanceFrame(1f);
        }
        long moneyAfter = totalMoney(engine);

        assertEquals(600L, clock.getTick());
        assertEquals(moneyBefore, moneyAfter);
        assertTrue(ledger.getEntries().stream().anyMatch(entry ->
                entry.type() == EconomicTransaction.Type.TRADE));
        assertFalse(ledger.getEntries().stream().anyMatch(entry ->
                entry.type() == EconomicTransaction.Type.MONEY_SOURCE
                        || entry.type() == EconomicTransaction.Type.MONEY_SINK));
    }

    private long totalMoney(Engine engine) {
        long total = 0L;
        for (Entity entity : engine.getEntities()) {
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            if (wallet != null) {
                total = Math.addExact(total, wallet.getBalanceMilliCredits());
            }
        }
        return total;
    }
}
