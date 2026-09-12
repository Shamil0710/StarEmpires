package com.spacesim.presentation.asset;

import com.spacesim.world.SectorId;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorSpaceBackgroundCatalogTest {
    @Test
    void sameWorldAndSectorAlwaysResolveSameBackground() {
        long worldSeed = 0x5EEDC0DEL;
        SectorId sectorId = new SectorId(37L);

        String first = SectorSpaceBackgroundCatalog.texturePath(worldSeed, sectorId);
        for (int attempt = 0; attempt < 100; attempt++) {
            assertEquals(first, SectorSpaceBackgroundCatalog.texturePath(worldSeed, sectorId));
        }
    }

    @Test
    void differentSystemsInSameSectorResolveSameBackground() {
        long worldSeed = 918273645L;
        SectorId sector = new SectorId(7001L);
        StarSystemId firstSystem = new StarSystemId(8001L);
        StarSystemId secondSystem = new StarSystemId(8002L);
        SectorSpaceBackgroundCatalog.registerSystemSector(firstSystem, sector);
        SectorSpaceBackgroundCatalog.registerSystemSector(secondSystem, sector);

        assertEquals(
                SectorSpaceBackgroundCatalog.texturePath(worldSeed, firstSystem),
                SectorSpaceBackgroundCatalog.texturePath(worldSeed, secondSystem));
        assertEquals(
                SectorSpaceBackgroundCatalog.texturePath(worldSeed, sector),
                SectorSpaceBackgroundCatalog.texturePath(worldSeed, firstSystem));
    }

    @Test
    void sectorSelectionUsesEntirePackAcrossOrdinarySectorIds() {
        Set<Integer> selected = new HashSet<>();
        for (long sectorId = 1L; sectorId <= 64L; sectorId++) {
            selected.add(SectorSpaceBackgroundCatalog.textureIndex(42L, new SectorId(sectorId)));
        }

        assertEquals(SectorSpaceBackgroundCatalog.allTexturePaths().size(), selected.size());
    }

    @Test
    void worldSeedParticipatesInSelection() {
        boolean anyChanged = false;
        for (long sectorId = 1L; sectorId <= 64L; sectorId++) {
            SectorId id = new SectorId(sectorId);
            if (SectorSpaceBackgroundCatalog.textureIndex(11L, id)
                    != SectorSpaceBackgroundCatalog.textureIndex(12L, id)) {
                anyChanged = true;
                break;
            }
        }

        assertTrue(anyChanged, "different generated worlds should not share a forced sector mapping");
    }

    @Test
    void unregisteredSystemCannotSilentlyFallBackToSystemIdentity() {
        StarSystemId unregistered = new StarSystemId(Long.MAX_VALUE - 100L);

        assertThrows(IllegalStateException.class,
                () -> SectorSpaceBackgroundCatalog.texturePath(42L, unregistered));
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
}
