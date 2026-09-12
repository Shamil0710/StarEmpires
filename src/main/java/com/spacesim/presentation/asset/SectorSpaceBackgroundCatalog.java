package com.spacesim.presentation.asset;

import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;

/** Deterministic presentation-only catalogue for current-system space backgrounds. */
public final class SectorSpaceBackgroundCatalog {
    private static final List<String> TEXTURE_PATHS = List.of(
            "assets/backgrounds/sector_space_01.jpg",
            "assets/backgrounds/sector_space_02.jpg",
            "assets/backgrounds/sector_space_03.jpg",
            "assets/backgrounds/sector_space_04.jpg");

    private SectorSpaceBackgroundCatalog() {
    }

    /** @return immutable ordered list of packaged background texture paths */
    public static List<String> allTexturePaths() {
        return TEXTURE_PATHS;
    }

    /**
     * Chooses one background as a pure function of persistent campaign/system identity.
     *
     * <p>No shared or stateful RNG is consumed. Rendering order, application restarts and save/load
     * therefore cannot change the selected background for the same world and system.</p>
     *
     * @param worldSeed persistent generated-world seed
     * @param systemId stable star-system identity
     * @return index in {@link #allTexturePaths()}
     */
    public static int textureIndex(long worldSeed, StarSystemId systemId) {
        long system = Objects.requireNonNull(systemId, "systemId").value();
        long value = worldSeed
                ^ Long.rotateLeft(system * 0x9E3779B97F4A7C15L, 21)
                ^ 0xD1B54A32D192ED03L;
        return (int) Long.remainderUnsigned(mix64(value), TEXTURE_PATHS.size());
    }

    /**
     * @param worldSeed persistent generated-world seed
     * @param systemId stable star-system identity
     * @return packaged texture path selected for that sector/system
     */
    public static String texturePath(long worldSeed, StarSystemId systemId) {
        return TEXTURE_PATHS.get(textureIndex(worldSeed, systemId));
    }

    private static long mix64(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
