package com.spacesim;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.AsteroidFieldId;
import com.spacesim.world.AsteroidFieldNode;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.PlanetId;
import com.spacesim.world.PlanetNode;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;

import java.util.List;
import java.util.Objects;

/**
 * Детерминированный production bootstrap минимального multi-system мира.
 *
 * <p>Каждая звёздная система получает обычную {@link SimulationSession}. Strategic topology
 * содержит persistent landmarks, а Stage-8 bootstrap дополнительно задаёт реальные faction
 * treasuries и первый liquidity-support budget. Это scenario initial state, а не денежный source во
 * время симуляции: суммы существуют с начала authoritative world snapshot.</p>
 */
public final class DemoGalaxyFactory {
    /** Система, которую desktop показывает и симулирует на полном local rate. */
    public static final StarSystemId ACTIVE_SYSTEM_ID = new StarSystemId(1L);
    /** Вторая экономически живая система demo galaxy. */
    public static final StarSystemId INNER_SYSTEM_ID = new StarSystemId(2L);
    /** Удалённая frontier-система demo galaxy. */
    public static final StarSystemId FRONTIER_SYSTEM_ID = new StarSystemId(3L);

    private DemoGalaxyFactory() {
        throw new AssertionError("DemoGalaxyFactory не создаёт экземпляров");
    }

    /**
     * Создаёт runtime demo galaxy на встроенном content catalog.
     *
     * @param rootSeed корневой seed demo world; active system сохраняет его без преобразования
     * @return multi-system runtime с active system на полном local rate
     */
    public static WorldSimulation create(long rootSeed) {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        return WorldSimulation.restore(
                createState(rootSeed, content),
                content,
                ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    /**
     * Создаёт persistent demo world на явно заданном semantic catalog.
     *
     * @param rootSeed общий seed bootstrap
     * @param contentCatalog единый catalog всех локальных simulation sessions
     * @return WorldState с тремя системами, strategic landmarks и faction treasuries
     */
    public static WorldState createState(long rootSeed, ContentCatalog contentCatalog) {
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        SimulationSession active = SimulationSession.createDemo(rootSeed, content);
        SimulationSession inner = SimulationSession.createDemo(derivedSeed(rootSeed, 2L), content);
        SimulationSession frontier = SimulationSession.createDemo(derivedSeed(rootSeed, 3L), content);

        StarSystemNode anchor = new StarSystemNode(
                ACTIVE_SYSTEM_ID,
                "Anchor",
                0d,
                0d,
                List.of(
                        new PlanetNode(new PlanetId(1L), "Anchor Prime", 1.0d),
                        new PlanetNode(new PlanetId(2L), "Vesper", 2.6d)),
                List.of(new AsteroidFieldNode(
                        new AsteroidFieldId(1L), "Anchor Belt", 0.8d, -0.4d, 1.4d)));
        StarSystemNode corona = new StarSystemNode(
                INNER_SYSTEM_ID,
                "Corona",
                18d,
                7d,
                List.of(new PlanetNode(new PlanetId(3L), "Corona II", 1.8d)),
                List.of(new AsteroidFieldNode(
                        new AsteroidFieldId(2L), "Corona Trojans", -1.2d, 0.7d, 0.9d)));
        StarSystemNode frontierNode = new StarSystemNode(
                FRONTIER_SYSTEM_ID,
                "Frontier",
                43d,
                -11d,
                List.of(new PlanetNode(new PlanetId(4L), "Prospect", 1.3d)),
                List.of(new AsteroidFieldNode(
                        new AsteroidFieldId(3L), "Frontier Field", 1.6d, 1.1d, 2.1d)));
        SectorNode core = new SectorNode(
                new SectorId(1L),
                "Core Sector",
                List.of(anchor, corona));
        SectorNode rim = new SectorNode(
                new SectorId(2L),
                "Outer Rim",
                List.of(frontierNode));
        GalaxyTopology topology = new GalaxyTopology(
                new GalaxyId(1L),
                "Star Empires Demo Galaxy",
                List.of(core, rim),
                List.of(
                        new JumpConnection(ACTIVE_SYSTEM_ID, INNER_SYSTEM_ID),
                        new JumpConnection(INNER_SYSTEM_ID, FRONTIER_SYSTEM_ID)));

        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.of(
                        new StarSystemSimulationState(ACTIVE_SYSTEM_ID, active.snapshot()),
                        new StarSystemSimulationState(INNER_SYSTEM_ID, inner.snapshot()),
                        new StarSystemSimulationState(FRONTIER_SYSTEM_ID, frontier.snapshot())),
                List.of(
                        factionState("faction.neutral", 500_000d),
                        factionState("faction.trade_league", 1_000_000d),
                        factionState("faction.miners", 750_000d)));
    }

    private static FactionEconomicState factionState(String contentId, double treasuryCredits) {
        return new FactionEconomicState(
                contentId,
                Money.fromCredits(treasuryCredits),
                Money.fromCredits(300_000d),
                Money.fromCredits(100_000d));
    }

    private static long derivedSeed(long rootSeed, long systemOrdinal) {
        long value = rootSeed + 0x9E3779B97F4A7C15L * systemOrdinal;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
