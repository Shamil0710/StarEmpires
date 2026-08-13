package com.spacesim.persistence;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void customCatalogSaveLoadПродолжаетсяТочноСТемЖеCatalog() throws IOException {
        ContentCatalog custom = changedCatalog();
        SimulationSession uninterrupted = SimulationSession.createDemo(0xC0FFEE42L, custom);
        SimulationSession saved = SimulationSession.createDemo(0xC0FFEE42L, custom);

        advanceBoth(uninterrupted, saved, 180, 0.1f);
        Path saveFile = tempDir.resolve("custom-save.stec");
        SimulationPersistence.save(saveFile, saved);
        SimulationSession loaded = SimulationPersistence.load(saveFile, custom);

        assertEquals(saved.snapshot(), loaded.snapshot());
        for (int frame = 0; frame < 240; frame++) {
            uninterrupted.advanceFrame(0.1f);
            loaded.advanceFrame(0.1f);
        }
        assertEquals(uninterrupted.snapshot(), loaded.snapshot());
    }

    @Test
    void тотЖеSaveОтклоняетсяНаCatalogСДругойСемантикой() throws IOException {
        ContentCatalog custom = changedCatalog();
        SimulationSession session = SimulationSession.createDemo(17L, custom);
        Path saveFile = tempDir.resolve("catalog-bound.stec");
        SimulationPersistence.save(saveFile, session);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SimulationPersistence.load(saveFile, ContentCatalogLoader.loadDefault()));
        assertTrue(error.getMessage().contains("несовместим"));
    }

    @Test
    void envelopeRoundTripСохраняетFingerprintИGameState() {
        SimulationSession session = SimulationSession.createDemo(91L);
        String fingerprint = session.getContentCatalog().getFingerprint();
        byte[] encoded = ContentBoundSaveCodec.encode(session.snapshot(), fingerprint);
        ContentBoundSaveCodec.DecodedSave decoded = ContentBoundSaveCodec.decode(encoded);

        assertEquals(fingerprint, decoded.contentFingerprint());
        assertEquals(session.snapshot(), decoded.state());
        assertFalse(decoded.legacyRawFormat());
    }

    @Test
    void rawGameStateCodecФайлОстаётсяЧитаемымКакLegacy() throws IOException {
        SimulationSession session = SimulationSession.createDemo(777L);
        Path raw = tempDir.resolve("legacy.stem");
        Files.write(raw, GameStateCodec.encode(session.snapshot()));

        ContentBoundSaveCodec.DecodedSave decoded = ContentBoundSaveCodec.read(raw);
        assertTrue(decoded.legacyRawFormat());
        assertEquals(ContentCatalogLoader.loadDefault().getFingerprint(), decoded.contentFingerprint());
        assertEquals(session.snapshot(), decoded.state());
        assertEquals(session.snapshot(), SimulationPersistence.load(raw).snapshot());
    }

    private void advanceBoth(
            SimulationSession first,
            SimulationSession second,
            int frames,
            float delta) {
        for (int frame = 0; frame < frames; frame++) {
            first.advanceFrame(delta);
            second.advanceFrame(delta);
        }
    }

    private ContentCatalog changedCatalog() throws IOException {
        String json = defaultJson().replaceFirst("\"basePrice\"\\s*:\s*10\\.0", "\"basePrice\": 11.0");
        return ContentCatalogLoader.parse(json);
    }

    private String defaultJson() throws IOException {
        try (InputStream stream = SimulationPersistenceTest.class.getClassLoader()
                .getResourceAsStream(ContentCatalogLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Не найден test resource: " + ContentCatalogLoader.DEFAULT_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
