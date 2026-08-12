package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.content.ArchetypeEntityFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityIdAllocator;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.GameState;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Детерминированно создаёт масштабный Stage-6 economic benchmark world через production archetypes.
 *
 * <p>Factory не собирает альтернативный Engine. Она материализует обычные data-driven entities,
 * присваивает им штатные persistent ID, преобразует их через {@link EntityStateMapper} и затем
 * восстанавливает обычную {@link SimulationSession}. Поэтому benchmark использует тот же bootstrap,
 * registry, save-state и simulation systems, что и production headless session.</p>
 */
public final class BenchmarkWorldFactory {
    /** Число market stations в масштабном scenario. */
    public static final int SCALE_STATION_COUNT = 100;
    /** Число автономных торговых кораблей. */
    public static final int SCALE_TRADER_COUNT = 450;
    /** Число автономных добывающих кораблей. */
    public static final int SCALE_MINER_COUNT = 50;
    /** Общее число экономических агентов. */
    public static final int SCALE_ECONOMIC_AGENT_COUNT = SCALE_TRADER_COUNT + SCALE_MINER_COUNT;

    private static final int GRID_SIDE = 10;
    private static final float GRID_ORIGIN = 250f;
    private static final float GRID_SPACING = 450f;

    private static final String[] STATION_ARCHETYPES = {
            "station.mining_base",
            "station.power_plant",
            "station.agrodome",
            "station.foundry",
            "station.arsenal",
            "station.colony"
    };

    private static final TraderDefinition[] TRADER_DEFINITIONS = {
            new TraderDefinition("ship.ore_hauler", "item.ore", "faction.miners"),
            new TraderDefinition("ship.energy_tanker", "item.energy", "faction.neutral"),
            new TraderDefinition("ship.food_container", "item.food", "faction.trade_league"),
            new TraderDefinition("ship.steel_hauler", "item.steel", "faction.miners"),
            new TraderDefinition("ship.weapons_container", "item.weapons", "faction.trade_league")
    };

    private BenchmarkWorldFactory() {
        throw new AssertionError("BenchmarkWorldFactory не создаёт экземпляров");
    }

    /**
     * Создаёт benchmark session с 100 market stations и 500 economic agents.
     *
     * <p>Станции размещаются на стабильной сетке 10×10 и циклически используют шесть production
     * archetypes. 450 traders равномерно распределены по пяти товарам. 50 miners получают
     * предпочтительные mining bases round-robin и поставляют руду через существующий asteroid /
     * mining pipeline.</p>
     *
     * @param rootSeed deterministic root seed всех simulation RNG streams
     * @return полностью восстановленная production {@link SimulationSession}
     */
    public static SimulationSession createScale100x500(long rootSeed) {
        ContentCatalog catalog = ContentCatalogLoader.loadDefault();
        SimulationSession template = SimulationSession.createDemo(rootSeed, catalog);
        GameState baseline = template.snapshot();
        EntityIdAllocator ids = new EntityIdAllocator();
        List<EntityState> states = new ArrayList<>(
                SCALE_STATION_COUNT + SCALE_ECONOMIC_AGENT_COUNT);
        List<StationPlacement> stations = new ArrayList<>(SCALE_STATION_COUNT);
        List<StationPlacement> miningBases = new ArrayList<>();

        for (int index = 0; index < SCALE_STATION_COUNT; index++) {
            int column = index % GRID_SIDE;
            int row = index / GRID_SIDE;
            float x = GRID_ORIGIN + column * GRID_SPACING;
            float y = GRID_ORIGIN + row * GRID_SPACING;
            String archetypeId = STATION_ARCHETYPES[index % STATION_ARCHETYPES.length];
            EntityId id = ids.allocate();
            Entity station = ArchetypeEntityFactory.createStation(
                    catalog,
                    archetypeId,
                    "Benchmark Station " + (index + 1),
                    x,
                    y)
                    .add(new EntityIdComponent(id));
            StationPlacement placement = new StationPlacement(id, x, y);
            stations.add(placement);
            if ("station.mining_base".equals(archetypeId)) {
                miningBases.add(placement);
            }
            states.add(EntityStateMapper.capture(station));
        }

        for (int index = 0; index < SCALE_TRADER_COUNT; index++) {
            TraderDefinition definition = TRADER_DEFINITIONS[index % TRADER_DEFINITIONS.length];
            StationPlacement home = stations.get(index % stations.size());
            float x = home.x() + deterministicOffsetX(index);
            float y = home.y() + deterministicOffsetY(index);
            Entity trader = ArchetypeEntityFactory.createTrader(
                    catalog,
                    definition.shipArchetypeId(),
                    "Benchmark Trader " + (index + 1),
                    x,
                    y,
                    definition.itemId(),
                    definition.factionId())
                    .add(new EntityIdComponent(ids.allocate()));
            states.add(EntityStateMapper.capture(trader));
        }

        if (miningBases.isEmpty()) {
            throw new IllegalStateException("Benchmark catalog не создал ни одной mining base");
        }
        for (int index = 0; index < SCALE_MINER_COUNT; index++) {
            StationPlacement home = miningBases.get(index % miningBases.size());
            Entity miner = ArchetypeEntityFactory.createMiner(
                    catalog,
                    "ship.basic_miner",
                    "Benchmark Miner " + (index + 1),
                    home.x() + deterministicOffsetX(index + SCALE_TRADER_COUNT),
                    home.y() + deterministicOffsetY(index + SCALE_TRADER_COUNT),
                    "item.ore",
                    "faction.miners")
                    .add(new EntityIdComponent(ids.allocate()));
            MiningComponent mining = miner.getComponent(MiningComponent.class);
            mining.homeBaseId = home.id();
            states.add(EntityStateMapper.capture(miner));
        }

        GameState benchmarkState = new GameState(
                GameState.CURRENT_VERSION,
                rootSeed,
                baseline.clock(),
                ids.getNextValue(),
                baseline.eventRandomState(),
                baseline.asteroidRandomState(),
                baseline.events(),
                baseline.asteroidSpawner(),
                baseline.priceRecorder(),
                baseline.ledger(),
                List.copyOf(states));
        return SimulationSession.restore(benchmarkState, catalog);
    }

    private static float deterministicOffsetX(int index) {
        return ((index % 5) - 2) * 7f;
    }

    private static float deterministicOffsetY(int index) {
        return (((index / 5) % 5) - 2) * 7f;
    }

    private record StationPlacement(EntityId id, float x, float y) {
    }

    private record TraderDefinition(String shipArchetypeId, String itemId, String factionId) {
    }
}
