package com.spacesim.presentation.asset;

import com.spacesim.world.SectorId;
import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Deterministic presentation-only catalogue for sector space backgrounds. */
public final class SectorSpaceBackgroundCatalog {
    private static final List<String> TEXTURE_PATHS = List.of(
            "assets/backgrounds/sector_space_01.jpg",
            "assets/backgrounds/sector_space_02.jpg",
            "assets/backgrounds/sector_space_03.jpg",
            "assets/backgrounds/sector_space_04.jpg");
    private static final ConcurrentMap<StarSystemId, SectorId> SYSTEM_SECTORS = new ConcurrentHashMap<>();

    private SectorSpaceBackgroundCatalog() {
    }

    /** @return immutable ordered list of packaged background texture paths */
    public static List<String> allTexturePaths() {
        return TEXTURE_PATHS;
    }

    /**
     * Records the authoritative topology relationship used only to resolve a system to its sector
     * at the presentation boundary. Re-registering the same system with the same sector is harmless.
     *
     * @param systemId stable star-system identity
     * @param sectorId stable containing-sector identity
     */
    public static void registerSystemSector(StarSystemId systemId, SectorId sectorId) {
        SYSTEM_SECTORS.put(
                Objects.requireNonNull(systemId, "systemId"),
                Objects.requireNonNull(sectorId, "sectorId"));
    }

    /**
     * Chooses one background as a pure function of persistent campaign/sector identity.
     *
     * <p>No shared or stateful RNG is consumed. Rendering order, application restarts and save/load
     * therefore cannot change the selected background for the same world and sector. Every star
     * system belonging to the same sector resolves the same texture.</p>
     *
     * @param worldSeed persistent generated-world seed
     * @param sectorId stable sector identity
     * @return index in {@link #allTexturePaths()}
     */
    public static int textureIndex(long worldSeed, SectorId sectorId) {
        long sector = Objects.requireNonNull(sectorId, "sectorId").value();
        long value = worldSeed
                ^ Long.rotateLeft(sector * 0x9E3779B97F4A7C15L, 21)
                ^ 0xD1B54A32D192ED03L;
        return (int) Long.remainderUnsigned(mix64(value), TEXTURE_PATHS.size());
    }

    /**
     * @param worldSeed persistent generated-world seed
     * @param sectorId stable sector identity
     * @return packaged texture path selected for that sector
     */
    public static String texturePath(long worldSeed, SectorId sectorId) {
        return TEXTURE_PATHS.get(textureIndex(worldSeed, sectorId));
    }

    /**
     * Resolves a system through the authoritative topology binding, then applies sector selection.
     * This overload exists so the command renderer does not need to duplicate topology lookup logic.
     *
     * @param worldSeed persistent generated-world seed
     * @param systemId stable active-system identity
     * @return packaged texture path selected for the containing sector
     */
    public static String texturePath(long worldSeed, StarSystemId systemId) {
        StarSystemId checked = Objects.requireNonNull(systemId, "systemId");
        SectorId sectorId = SYSTEM_SECTORS.get(checked);
        if (sectorId == null) {
            throw new IllegalStateException("No sector binding registered for star system " + checked.value());
        }
        return texturePath(worldSeed, sectorId);
    }

    private static long mix64(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
