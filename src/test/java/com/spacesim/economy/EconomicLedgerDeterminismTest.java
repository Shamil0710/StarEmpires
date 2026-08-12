package com.spacesim.economy;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoWorldFactory;
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

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicLedgerDeterminismTest {
    private static final long ROOT_SEED = 0xEC0_2026L;

    @Test
    void sharedLedgerИдентиченПриРазномRenderPattern() {
        Fixture coarse = fixture();
        Fixture fine = fixture();

        for (int frame = 0; frame < 30; frame++) {
            coarse.loop.advanceFrame(1f);
        }
        for (int frame = 0; frame < 300; frame++) {
            fine.loop.advanceFrame(0.1f);
        }

        assertEquals(300L, coarse.clock.getTick());
        assertEquals(coarse.clock.getTick(), fine.clock.getTick());
        assertEquals(coarse.ledger.getEntries(), fine.ledger.getEntries());
        assertTrue(coarse.ledger.size() > 0);

        Set<EconomicTransaction.Type> observedTypes = EnumSet.noneOf(EconomicTransaction.Type.class);
        for (EconomicTransaction transaction : coarse.ledger.getEntries()) {
            observedTypes.add(transaction.type());
        }
        assertTrue(observedTypes.contains(EconomicTransaction.Type.RESOURCE_SOURCE));
        assertTrue(observedTypes.contains(EconomicTransaction.Type.RESOURCE_SINK));
        assertTrue(observedTypes.contains(EconomicTransaction.Type.RESOURCE_TRANSFORM));
        assertTrue(observedTypes.contains(EconomicTransaction.Type.TRADE));
    }

    private Fixture fixture() {
        SimulationRandom random = new SimulationRandom(ROOT_SEED);
        GlobalEventManager events = new GlobalEventManager(random.createStream("economy-events"), 0d);
        EconomicLedger ledger = new EconomicLedger();
        Engine engine = new Engine();
        SpatialHashGrid grid = new SpatialHashGrid(Constants.CELL_SIZE);

        engine.addSystem(new MarketSystem(events));
        engine.addSystem(new ConsumptionSystem(events, ledger));
        engine.addSystem(new ProductionSystem(ledger));
        engine.addSystem(new AsteroidSpawnSystem(
                AsteroidSpawnConfig.demoWorld(),
                random.createStream("asteroid-spawn"),
                ledger));
        engine.addSystem(new MiningSystem(ledger));
        engine.addSystem(new TradeAISystem(grid, ledger));
        engine.addSystem(new PriceRecorderSystem());

        for (Entity entity : DemoWorldFactory.createEntities()) {
            engine.addEntity(entity);
        }

        SimulationClock clock = new SimulationClock(0.1f);
        return new Fixture(
                ledger,
                clock,
                new SimulationLoop(clock, events, engine));
    }

    private record Fixture(EconomicLedger ledger, SimulationClock clock, SimulationLoop loop) {
    }
}
