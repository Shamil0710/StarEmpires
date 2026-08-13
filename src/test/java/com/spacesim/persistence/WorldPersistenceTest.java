package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldTopologyDefaults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void multiSystemSaveLoadСохраняетSchedulerИExactContinuation() throws IOException {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation uninterrupted = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0xABCDEF42L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                5,
                3);
        WorldSimulation saved = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0xABCDEF42L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                5,
                3);

        float[] beforeSave = {0.13f, 0.07f, 0.41f, 0.2f, 0.09f};
        for (int cycle = 0; cycle < 30; cycle++) {
            advanceBoth(uninterrupted, saved, beforeSave);
        }
        assertEquals(uninterrupted.snapshot(), saved.snapshot());

        Path file = tempDir.resolve("galaxy.stwc");
        WorldPersistence.save(file, saved);
        WorldSimulation loaded = WorldPersistence.load(file, content);

        assertEquals(saved.snapshot(), loaded.snapshot());
        assertEquals(5, loaded.getStrategicStepTicks());
        assertEquals(3, loaded.getRemoteUpdateBudgetPerFrame());
        assertEquals(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, loaded.getActiveSystemId());

        float[] afterLoad = {0.05f, 0.35f, 0.11f, 0.29f};
        for (int cycle = 0; cycle < 25; cycle++) {
            advanceBoth(uninterrupted, loaded, afterLoad);
            assertEquals(uninterrupted.snapshot(), loaded.snapshot(),
                    "World save continuation разошёлся после cycle " + cycle);
        }
    }

    @Test
    void worldSaveОтклоняетсяНаCatalogСДругойСемантикой() throws IOException {
        ContentCatalog changed = changedCatalog();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(19L, changed),
                changed,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                10,
                4);
        Path file = tempDir.resolve("custom-world.stwc");
        WorldPersistence.save(file, world);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> WorldPersistence.load(file, ContentCatalogLoader.loadDefault()));
        assertTrue(error.getMessage().contains("несовместим"));
    }

    @Test
    void legacyStecАвтоматическиОборачиваетсяВДефолтнуюСистему() throws IOException {
        SimulationSession legacy = SimulationSession.createDemo(777L);
        legacy.advanceFrame(2.3f);
        Path file = tempDir.resolve("legacy.stec");
        ContentBoundSaveCodec.write(
                file,
                legacy.snapshot(),
                legacy.getContentCatalog().getFingerprint());

        WorldSimulation loaded = WorldPersistence.load(file);

        assertEquals(WorldTopologyDefaults.DEFAULT_SYSTEM_ID, loaded.getActiveSystemId());
        assertEquals(1, loaded.getTopology().systems().size());
        assertEquals(legacy.snapshot(), loaded.findSession(
                WorldTopologyDefaults.DEFAULT_SYSTEM_ID).orElseThrow().snapshot());
    }

    @Test
    void legacyStemАвтоматическиОборачиваетсяВДефолтнуюСистему() throws IOException {
        SimulationSession legacy = SimulationSession.createDemo(888L);
        legacy.advanceFrame(1.7f);
        Path file = tempDir.resolve("legacy.stem");
        Files.write(file, GameStateCodec.encode(legacy.snapshot()));

        WorldSimulation loaded = WorldPersistence.load(file);

        assertEquals(WorldTopologyDefaults.DEFAULT_SYSTEM_ID, loaded.getActiveSystemId());
        assertEquals(legacy.snapshot(), loaded.findSession(
                WorldTopologyDefaults.DEFAULT_SYSTEM_ID).orElseThrow().snapshot());
    }

    @Test
    void повторнаяЗаписьАтомарноЗаменяетWorldSave() throws IOException {
        Path file = tempDir.resolve("slot.stwc");
        WorldSimulation first = DemoGalaxyFactory.create(100L);
        WorldSimulation second = DemoGalaxyFactory.create(200L);
        second.advanceFrame(1f);

        WorldPersistence.save(file, first);
        assertTrue(Files.isRegularFile(file));
        assertEquals(first.snapshot(), WorldPersistence.load(file).snapshot());

        WorldPersistence.save(file, second);
        assertEquals(second.snapshot(), WorldPersistence.load(file).snapshot());
    }

    private static void advanceBoth(
            WorldSimulation first,
            WorldSimulation second,
            float[] deltas) {
        for (float delta : deltas) {
            first.advanceFrame(delta);
            second.advanceFrame(delta);
        }
    }

    private ContentCatalog changedCatalog() throws IOException {
        String json = defaultJson().replaceFirst("\"basePrice\"\\s*:\s*10\\.0", "\"basePrice\": 11.0");
        return ContentCatalogLoader.parse(json);
    }

    private String defaultJson() throws IOException {
        try (InputStream stream = WorldPersistenceTest.class.getClassLoader()
                .getResourceAsStream(ContentCatalogLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Не найден test resource: " + ContentCatalogLoader.DEFAULT_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
