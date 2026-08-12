package com.spacesim;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
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
 * Детерминированный production bootstrap минимального Stage-7 multi-system мира.
 *
 * <p>Каждая звёздная система получает обычную {@link SimulationSession}; никакой отдельной
 * стратегической экономики фабрика не создаёт. Различаются только deterministic root seeds
 * локальных sessions, а {@link WorldSimulation} решает, какая из них исполняется full-rate.</p>
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
     * @return WorldState с тремя системами и двумя jump connections
     */
    public static WorldState createState(long rootSeed, ContentCatalog contentCatalog) {
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        SimulationSession active = SimulationSession.createDemo(rootSeed, content);
        SimulationSession inner = SimulationSession.createDemo(derivedSeed(rootSeed, 2L), content);
        SimulationSession frontier = SimulationSession.createDemo(derivedSeed(rootSeed, 3L), content);

        StarSystemNode anchor = new StarSystemNode(ACTIVE_SYSTEM_ID, "Anchor", 0d, 0d);
        StarSystemNode corona = new StarSystemNode(INNER_SYSTEM_ID, "Corona", 18d, 7d);
        StarSystemNode frontierNode = new StarSystemNode(FRONTIER_SYSTEM_ID, "Frontier", 43d, -11d);
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
                        new StarSystemSimulationState(FRONTIER_SYSTEM_ID, frontier.snapshot())));
    }

    private static long derivedSeed(long rootSeed, long systemOrdinal) {
        long value = rootSeed + 0x9E3779B97F4A7C15L * systemOrdinal;
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
