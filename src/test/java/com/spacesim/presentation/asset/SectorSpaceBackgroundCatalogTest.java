package com.spacesim.presentation.asset;

import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorSpaceBackgroundCatalogTest {
    @Test
    void sameWorldAndSystemAlwaysResolveSameBackground() {
        long worldSeed = 0x5EEDC0DEL;
        StarSystemId systemId = new StarSystemId(37L);

        String first = SectorSpaceBackgroundCatalog.texturePath(worldSeed, systemId);
        for (int attempt = 0; attempt < 100; attempt++) {
            assertEquals(first, SectorSpaceBackgroundCatalog.texturePath(worldSeed, systemId));
        }
    }

    @Test
    void sectorSelectionUsesEntirePackAcrossOrdinarySystemIds() {
        Set<Integer> selected = new HashSet<>();
        for (long systemId = 1L; systemId <= 64L; systemId++) {
            selected.add(SectorSpaceBackgroundCatalog.textureIndex(42L, new StarSystemId(systemId)));
        }

        assertEquals(SectorSpaceBackgroundCatalog.allTexturePaths().size(), selected.size());
    }

    @Test
    void worldSeedParticipatesInSelection() {
        boolean anyChanged = false;
        for (long systemId = 1L; systemId <= 64L; systemId++) {
            StarSystemId id = new StarSystemId(systemId);
            if (SectorSpaceBackgroundCatalog.textureIndex(11L, id)
                    != SectorSpaceBackgroundCatalog.textureIndex(12L, id)) {
                anyChanged = true;
                break;
            }
        }

        assertTrue(anyChanged, "different generated worlds should not share a forced sector mapping");
    }

    @Test
    void packagedBackgroundsExistAndAreJpegFiles() throws IOException {
        assertEquals(4, SectorSpaceBackgroundCatalog.allTexturePaths().size());
        assertEquals(4, new HashSet<>(SectorSpaceBackgroundCatalog.allTexturePaths()).size());

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String path : SectorSpaceBackgroundCatalog.allTexturePaths()) {
            try (InputStream stream = loader.getResourceAsStream(path)) {
                assertNotNull(stream, "missing packaged background: " + path);
                assertEquals(0xFF, stream.read(), "JPEG SOI byte 1: " + path);
                assertEquals(0xD8, stream.read(), "JPEG SOI byte 2: " + path);
            }
        }
    }

    @Test
    void nearbySystemsAreNotForcedToSameChoice() {
        int first = SectorSpaceBackgroundCatalog.textureIndex(42L, new StarSystemId(1L));
        int second = SectorSpaceBackgroundCatalog.textureIndex(42L, new StarSystemId(2L));

        assertNotEquals(first, second);
    }
}
