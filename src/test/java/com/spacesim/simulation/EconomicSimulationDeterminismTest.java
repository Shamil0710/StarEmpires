package com.spacesim.simulation;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoWorldFactory;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.events.EconomyEvent;
import com.spacesim.events.GlobalEventManager;
import com.spacesim.model.AsteroidSpawnConfig;
import com.spacesim.systems.AsteroidSpawnSystem;
import com.spacesim.systems.ConsumptionSystem;
import com.spacesim.systems.MarketSystem;
import com.spacesim.systems.MiningSystem;
import com.spacesim.systems.PriceRecorderSystem;
import com.spacesim.systems.ProductionSystem;
import com.spacesim.systems.TradeAISystem;
import com.spacesim.util.SpatialHashGrid;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomicSimulationDeterminismTest {
    private static final long ROOT_SEED = 0x51A5_2026L;
    private static final float FIXED_STEP = 0.1f;

    @Test
    void одинаковыеSeedИТикиДаютОдинаковоеЭкономическоеСостояниеПриРазномRenderPattern() {
        Fixture coarseFrames = fixture();
        Fixture fineFrames = fixture();

        for (int frame = 0; frame < 30; frame++) {
            coarseFrames.loop.advanceFrame(1f);
        }
        for (int frame = 0; frame < 300; frame++) {
            fineFrames.loop.advanceFrame(0.1f);
        }

        assertEquals(300L, coarseFrames.clock.getTick());
        assertEquals(coarseFrames.clock.getTick(), fineFrames.clock.getTick());
        assertEquals(coarseFrames.clock.getSimulationTimeSeconds(),
                coarseFrames.events.getSimulationTimeSeconds(), 1e-9);
        assertEquals(snapshot(coarseFrames), snapshot(fineFrames));
    }

    private Fixture fixture() {
        SimulationRandom random = new SimulationRandom(ROOT_SEED);
        GlobalEventManager events = new GlobalEventManager(random.createStream("economy-events"), 0.5d);
        Engine engine = new Engine();
        SpatialHashGrid grid = new SpatialHashGrid(Constants.CELL_SIZE);
        EconomicLedger ledger = new EconomicLedger();

        engine.addSystem(new MarketSystem(events));
        engine.addSystem(new ConsumptionSystem(events));
        engine.addSystem(new ProductionSystem());
        engine.addSystem(new AsteroidSpawnSystem(
                AsteroidSpawnConfig.demoWorld(),
                random.createStream("asteroid-spawn")));
        engine.addSystem(new MiningSystem(ledger));
        engine.addSystem(new TradeAISystem(grid, ledger));
        engine.addSystem(new PriceRecorderSystem());

        for (Entity entity : DemoWorldFactory.createEntities()) {
            engine.addEntity(entity);
        }

        SimulationClock clock = new SimulationClock(FIXED_STEP);
        SimulationLoop loop = new SimulationLoop(clock, events, engine);
        return new Fixture(engine, events, clock, loop, ledger);
    }

    private String snapshot(Fixture fixture) {
        StringBuilder snapshot = new StringBuilder();
        snapshot.append("tick=").append(fixture.clock.getTick())
                .append(";eventTime=").append(Double.doubleToLongBits(fixture.events.getSimulationTimeSeconds()))
                .append(";eventRevision=").append(fixture.events.getEventRevision())
                .append(";ledgerSize=").append(fixture.ledger.size()).append('\n');

        for (EconomyEvent event : fixture.events.getActiveEvents()) {
            snapshot.append("event=")
                    .append(event.getName()).append('|')
                    .append(event.getTargetItemId()).append('|')
                    .append(Float.floatToIntBits(event.getRemainingDurationSeconds())).append('|')
                    .append(Float.floatToIntBits(event.getLocation().x)).append('|')
                    .append(Float.floatToIntBits(event.getLocation().y)).append('\n');
        }

        List<Entity> entities = new ArrayList<>();
        for (Entity entity : fixture.engine.getEntities()) {
            entities.add(entity);
        }
        entities.sort(Comparator.comparing(this::identityName));

        for (Entity entity : entities) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            snapshot.append("entity=").append(identity.name).append('|').append(identity.kind);

            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (transform != null) {
                snapshot.append(";pos=")
                        .append(Float.floatToIntBits(transform.position.x)).append(',')
                        .append(Float.floatToIntBits(transform.position.y));
            }

            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (inventory != null) {
                snapshot.append(";stock=").append(Arrays.toString(inventory.stock));
            }

            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            if (wallet != null) {
                snapshot.append(";wallet=").append(wallet.getBalanceMilliCredits());
            }

            MarketComponent market = entity.getComponent(MarketComponent.class);
            if (market != null) {
                snapshot.append(";sell=").append(floatBits(market.sellPrices))
                        .append(";buy=").append(floatBits(market.buyPrices))
                        .append(";target=").append(Arrays.toString(market.targetStock));
            }

            TradeAIComponent trade = entity.getComponent(TradeAIComponent.class);
            if (trade != null) {
                snapshot.append(";trade=").append(trade.state)
                        .append(',').append(trade.targetItem)
                        .append(',').append(trade.targetAmount)
                        .append(',').append(trade.specializedItem)
                        .append(',').append(trade.expectedProfitMilliCredits)
                        .append(',').append(identityName(trade.buyStation))
                        .append(',').append(identityName(trade.sellStation))
                        .append(',').append(identityName(trade.targetStation));
            }

            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            if (asteroid != null) {
                snapshot.append(";asteroid=").append(asteroid.spawnPointId)
                        .append(',').append(asteroid.initialResource)
                        .append(',').append(asteroid.remainingResource);
            }
            snapshot.append('\n');
        }
        return snapshot.toString();
    }

    private String floatBits(float[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(Float.floatToIntBits(values[index]));
        }
        return result.append(']').toString();
    }

    private String identityName(Entity entity) {
        if (entity == null) {
            return "-";
        }
        IdentityComponent identity = entity.getComponent(IdentityComponent.class);
        return identity == null ? "?" : identity.name;
    }

    private record Fixture(
            Engine engine,
            GlobalEventManager events,
            SimulationClock clock,
            SimulationLoop loop,
            EconomicLedger ledger) {
    }
}
