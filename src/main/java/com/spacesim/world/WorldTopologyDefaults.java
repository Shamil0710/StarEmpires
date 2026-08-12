package com.spacesim.world;

import java.util.List;

/**
 * Стабильные IDs минимального single-system мира для legacy/demo bootstrap.
 *
 * <p>Stage 7 не меняет существующую экономическую {@code SimulationSession}: старые сохранения и
 * demo-world оборачиваются в одну систему с этими IDs. Позднее полноценный world bootstrap может
 * создавать дополнительные системы, не меняя идентичность исходной.</p>
 */
public final class WorldTopologyDefaults {
    /** Устойчивый ID минимальной галактики. */
    public static final GalaxyId DEFAULT_GALAXY_ID = new GalaxyId(1L);
    /** Устойчивый ID минимального сектора. */
    public static final SectorId DEFAULT_SECTOR_ID = new SectorId(1L);
    /** Устойчивый ID исходной локальной системы. */
    public static final StarSystemId DEFAULT_SYSTEM_ID = new StarSystemId(1L);

    private WorldTopologyDefaults() {
        throw new AssertionError("WorldTopologyDefaults не создаёт экземпляров");
    }

    /**
     * Создаёт новый immutable topology-объект минимального legacy/demo мира.
     *
     * @return topology с одной галактикой, одним сектором и одной звёздной системой
     */
    public static GalaxyTopology singleSystem() {
        StarSystemNode system = new StarSystemNode(
                DEFAULT_SYSTEM_ID,
                "Default System",
                0d,
                0d);
        SectorNode sector = new SectorNode(
                DEFAULT_SECTOR_ID,
                "Default Sector",
                List.of(system));
        return new GalaxyTopology(
                DEFAULT_GALAXY_ID,
                "Star Empires",
                List.of(sector),
                List.of());
    }
}
