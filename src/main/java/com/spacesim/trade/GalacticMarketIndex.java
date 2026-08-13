package com.spacesim.trade;

import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.WorldSimulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * World-level immutable-snapshot index used by bounded cross-system market discovery.
 *
 * <p>Each StarSystem keeps its existing {@link MarketDirectory}; this class does not create a
 * second market representation. {@link #rebuild()} refreshes those local immutable snapshots and
 * exposes one aggregate revision that changes whenever any local market snapshot changes. Sector
 * membership is indexed once from immutable {@link GalaxyTopology}, so discovery can traverse a
 * deterministic regional structure without scanning the whole galaxy for every fleet.</p>
 */
public final class GalacticMarketIndex {
    private final WorldSimulation world;
    private final GalaxyTopology topology;
    private final Map<StarSystemId, MarketDirectory> directoriesBySystem = new LinkedHashMap<>();
    private final Map<StarSystemId, Long> observedDirectoryRevisions = new LinkedHashMap<>();
    private final Map<SectorId, List<StarSystemId>> systemsBySector = new LinkedHashMap<>();
    private long revision;

    /**
     * Creates an index bound to one runtime world and its immutable topology.
     *
     * @param world authoritative world runtime
     */
    public GalacticMarketIndex(WorldSimulation world) {
        this.world = Objects.requireNonNull(world, "WorldSimulation не задан");
        this.topology = world.getTopology();
        for (StarSystemNode system : topology.systems()) {
            directoriesBySystem.put(
                    system.id(),
                    new MarketDirectory(world.findSession(system.id()).orElseThrow(
                            () -> new IllegalArgumentException(
                                    "Для topology system отсутствует SimulationSession: " + system.id()))
                            .getContentCatalog()));
        }
        for (SectorNode sector : topology.sectors()) {
            List<StarSystemId> systems = new ArrayList<>(sector.systems().size());
            for (StarSystemNode system : sector.systems()) {
                systems.add(system.id());
            }
            systems.sort(StarSystemId::compareTo);
            systemsBySector.put(sector.id(), List.copyOf(systems));
        }
    }

    /**
     * Refreshes all per-system market snapshots in deterministic StarSystemId order.
     *
     * <p>The aggregate revision changes at most once per rebuild call even if multiple systems
     * changed. Calling this method repeatedly against an unchanged world is therefore stable and
     * suitable for stale-discovery checks.</p>
     *
     * @return {@code true} when at least one local market snapshot changed
     * @throws IllegalStateException when the aggregate revision range is exhausted
     */
    public boolean rebuild() {
        boolean changed = false;
        for (StarSystemNode system : topology.systems()) {
            StarSystemId systemId = system.id();
            MarketDirectory directory = directoriesBySystem.get(systemId);
            directory.rebuild(world.findSession(systemId).orElseThrow().getEngine().getEntities());
            long localRevision = directory.revision();
            Long previousRevision = observedDirectoryRevisions.put(systemId, localRevision);
            if (previousRevision == null || previousRevision.longValue() != localRevision) {
                changed = true;
            }
        }
        if (changed) {
            if (revision == Long.MAX_VALUE) {
                throw new IllegalStateException("Диапазон GalacticMarketIndex revision исчерпан");
            }
            revision++;
        }
        return changed;
    }

    /**
     * Returns the immutable local directory for a known StarSystem.
     *
     * @param systemId stable system ID
     * @return directory owned by this world index
     * @throws IllegalArgumentException when the system is not part of this topology
     */
    public MarketDirectory directory(StarSystemId systemId) {
        MarketDirectory directory = directoriesBySystem.get(
                Objects.requireNonNull(systemId, "StarSystemId market index не задан"));
        if (directory == null) {
            throw new IllegalArgumentException("Неизвестная StarSystem: " + systemId);
        }
        return directory;
    }

    /**
     * Returns systems belonging to one sector in deterministic StarSystemId order.
     *
     * @param sectorId stable sector ID
     * @return immutable systems list or an empty list for an unknown sector
     */
    public List<StarSystemId> systemsInSector(SectorId sectorId) {
        return sectorId == null ? List.of() : systemsBySector.getOrDefault(sectorId, List.of());
    }

    /** @return aggregate monotonic market snapshot revision */
    public long revision() {
        return revision;
    }

    /** @return immutable topology used by this index */
    public GalaxyTopology topology() {
        return topology;
    }

    WorldSimulation world() {
        return world;
    }
}
