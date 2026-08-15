package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.MarketComponent;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameStateCodecTest {
    private static final long ROOT_SEED = 0x5A7E_C0DEL;

    @TempDir
    Path temporaryDirectory;

    @Test
    void binaryEncodeDecodeДаётExactGameStateRoundTrip() {
        SimulationSession session = progressedSession();
        MarketComponent market = firstMarket(session);
        market.configuredTargetStock[0] = 17;
        market.targetStock[0] = 33;
        GameState state = session.snapshot();

        byte[] first = GameStateCodec.encode(state);
        byte[] second = GameStateCodec.encode(state);
        GameState decoded = GameStateCodec.decode(first);

        assertArrayEquals(first, second);
        assertEquals(state, decoded);
        assertArrayEquals(first, GameStateCodec.encode(decoded));
        EntityState decodedMarket = decoded.entities().stream()
                .filter(entity -> entity.market() != null)
                .findFirst()
                .orElseThrow();
        assertEquals(17, decodedMarket.market().configuredTargetStock().get(0));
        assertEquals(33, decodedMarket.market().targetStock().get(0));
    }

    @Test
    void файлСохраненияЗаписываетсяЧитаетсяИБезопасноЗаменяется() throws IOException {
        Path save = temporaryDirectory.resolve("slot-1.starsave");
        SimulationSession firstSession = SimulationSession.createDemo(ROOT_SEED);
        firstSession.advanceFrame(0.37f);
        GameState firstState = firstSession.snapshot();

        GameStateCodec.write(save, firstState);
        assertTrue(Files.isRegularFile(save));
        assertEquals(firstState, GameStateCodec.read(save));

        firstSession.advanceFrame(1.23f);
        GameState secondState = firstSession.snapshot();
        GameStateCodec.write(save, secondState);

        assertEquals(secondState, GameStateCodec.read(save));
    }

    @Test
    void simulateFileSaveLoadContinueЭквивалентенНепрерывнойСимуляции() throws IOException {
        Path save = temporaryDirectory.resolve("continuation.starsave");
        SimulationSession uninterrupted = SimulationSession.createDemo(ROOT_SEED);
        SimulationSession saveSource = SimulationSession.createDemo(ROOT_SEED);

        float[] beforeSave = {0.37f, 0.11f, 0.53f, 1f, 0.07f, 0.29f, 0.41f};
        for (int cycle = 0; cycle < 40; cycle++) {
            for (float delta : beforeSave) {
                uninterrupted.advanceFrame(delta);
                saveSource.advanceFrame(delta);
            }
        }
        assertEquals(uninterrupted.snapshot(), saveSource.snapshot());

        GameStateCodec.write(save, saveSource.snapshot());
        SimulationSession loaded = SimulationSession.restore(GameStateCodec.read(save));
        assertEquals(saveSource.snapshot(), loaded.snapshot());

        float[] afterLoad = {0.13f, 0.87f, 0.2f, 0.44f, 0.31f, 1.2f, 0.05f};
        for (int cycle = 0; cycle < 35; cycle++) {
            for (float delta : afterLoad) {
                uninterrupted.advanceFrame(delta);
                loaded.advanceFrame(delta);
            }
            assertEquals(uninterrupted.snapshot(), loaded.snapshot(),
                    "Файловый continuation разошёлся после cycle " + cycle);
        }
    }

    @Test
    void повреждённыйMagicVersionTruncationИТrailingBytesОтклоняются() {
        byte[] valid = GameStateCodec.encode(progressedSession().snapshot());

        byte[] badMagic = valid.clone();
        badMagic[0] ^= 0x7f;
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(badMagic));

        byte[] badFileVersion = valid.clone();
        ByteBuffer.wrap(badFileVersion).putInt(4, 999);
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(badFileVersion));

        byte[] badSchemaVersion = valid.clone();
        ByteBuffer.wrap(badSchemaVersion).putInt(8, GameState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(badSchemaVersion));

        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(truncated));

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        trailing[trailing.length - 1] = 1;
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(trailing));
    }

    @Test
    void пустойNullИUnsupportedStateОтклоняютсяДоЗаписи() {
        assertThrows(NullPointerException.class, () -> GameStateCodec.encode(null));
        assertThrows(NullPointerException.class, () -> GameStateCodec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.decode(new byte[0]));

        GameState state = progressedSession().snapshot();
        GameState unsupported = new GameState(
                state.schemaVersion() + 1,
                state.rootSeed(),
                state.clock(),
                state.nextEntityIdValue(),
                state.eventRandomState(),
                state.asteroidRandomState(),
                state.events(),
                state.asteroidSpawner(),
                state.priceRecorder(),
                state.ledger(),
                state.entities());
        assertThrows(IllegalArgumentException.class, () -> GameStateCodec.encode(unsupported));
    }

    private static MarketComponent firstMarket(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            MarketComponent market = entity.getComponent(MarketComponent.class);
            if (market != null) {
                return market;
            }
        }
        throw new AssertionError("Demo session has no market entity");
    }

    private SimulationSession progressedSession() {
        SimulationSession session = SimulationSession.createDemo(ROOT_SEED);
        for (int index = 0; index < 53; index++) {
            session.advanceFrame(index % 3 == 0 ? 0.37f : 0.11f);
        }
        return session;
    }
}
