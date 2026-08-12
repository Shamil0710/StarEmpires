package com.spacesim.persistence;

import com.spacesim.content.ContentCatalogLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Файловый envelope поверх {@link GameStateCodec}, связывающий save с semantic content catalog.
 *
 * <p>{@link GameState} остаётся чистым authoritative состоянием симуляции. Fingerprint внешнего
 * каталога хранится отдельно вместе с бинарным payload GameStateCodec. Reader также принимает
 * исторические raw {@code STEM} файлы Stage 3; для них ожидается встроенный legacy-compatible
 * каталог. Новые записи всегда используют {@code STEC} envelope и atomic replace.</p>
 */
public final class ContentBoundSaveCodec {
    private static final int ENVELOPE_MAGIC = 0x53544543; // STEC
    private static final int LEGACY_GAMESTATE_MAGIC = 0x5354454D; // STEM
    private static final int ENVELOPE_VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = 32 * 1024 * 1024;
    private static final int FINGERPRINT_BYTES = 64;

    private ContentBoundSaveCodec() {
        throw new AssertionError("ContentBoundSaveCodec не создаёт экземпляров");
    }

    /**
     * Кодирует current GameState и semantic fingerprint в content-bound envelope.
     *
     * @param state authoritative snapshot текущей schema
     * @param contentFingerprint lowercase SHA-256 hex fingerprint
     * @return новый бинарный envelope
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException если fingerprint некорректен
     */
    public static byte[] encode(GameState state, String contentFingerprint) {
        Objects.requireNonNull(state, "GameState не задан");
        String fingerprint = requireFingerprint(contentFingerprint);
        byte[] payload = GameStateCodec.encode(state);
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("GameState payload превышает допустимый размер");
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(payload.length + 80);
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(ENVELOPE_MAGIC);
                output.writeInt(ENVELOPE_VERSION);
                output.write(fingerprint.getBytes(StandardCharsets.US_ASCII));
                output.writeInt(payload.length);
                output.write(payload);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Неожиданная ошибка памяти при кодировании save-envelope", exception);
        }
    }

    /**
     * Декодирует content-bound envelope либо исторический raw Stage-3 save.
     *
     * @param bytes бинарное содержимое файла
     * @return decoded state и ожидаемый fingerprint каталога
     * @throws NullPointerException если bytes не заданы
     * @throws IllegalArgumentException если формат повреждён или неизвестен
     */
    public static DecodedSave decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Байты сохранения не заданы");
        if (bytes.length < Integer.BYTES) {
            throw new IllegalArgumentException("Файл сохранения слишком короткий");
        }
        int magic = readLeadingInt(bytes);
        if (magic == LEGACY_GAMESTATE_MAGIC) {
            return new DecodedSave(
                    GameStateCodec.decode(bytes),
                    ContentCatalogLoader.loadDefault().getFingerprint(),
                    true);
        }
        if (magic != ENVELOPE_MAGIC) {
            throw new IllegalArgumentException("Неизвестный magic save-envelope");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            input.readInt();
            int version = input.readInt();
            if (version != ENVELOPE_VERSION) {
                throw new IllegalArgumentException("Неподдерживаемая версия save-envelope: " + version);
            }
            byte[] fingerprintBytes = input.readNBytes(FINGERPRINT_BYTES);
            if (fingerprintBytes.length != FINGERPRINT_BYTES) {
                throw new EOFException("Content fingerprint оборван");
            }
            String fingerprint = requireFingerprint(
                    new String(fingerprintBytes, StandardCharsets.US_ASCII));
            int payloadLength = input.readInt();
            if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Некорректная длина GameState payload");
            }
            byte[] payload = input.readNBytes(payloadLength);
            if (payload.length != payloadLength) {
                throw new EOFException("GameState payload оборван");
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("После save-envelope обнаружен лишний бинарный хвост");
            }
            return new DecodedSave(GameStateCodec.decode(payload), fingerprint, false);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Save-envelope оборван", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Save-envelope невозможно декодировать", exception);
        }
    }

    /**
     * Атомарно записывает content-bound save рядом с целевым файлом.
     *
     * @param path целевой файл
     * @param state authoritative snapshot
     * @param contentFingerprint semantic fingerprint каталога
     * @throws IOException если файловая система не позволила записать/заменить файл
     */
    public static void write(Path path, GameState state, String contentFingerprint) throws IOException {
        Path target = Objects.requireNonNull(path, "Путь сохранения не задан").toAbsolutePath();
        byte[] bytes = encode(state, contentFingerprint);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String prefix = target.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = "save-" + prefix;
        }
        Path temp = Files.createTempFile(parent, prefix, ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Читает save-envelope или legacy raw save с диска.
     *
     * @param path существующий файл
     * @return decoded content-bound state
     * @throws IOException если файл нельзя прочитать
     */
    public static DecodedSave read(Path path) throws IOException {
        Path source = Objects.requireNonNull(path, "Путь сохранения не задан").toAbsolutePath();
        long size = Files.size(source);
        long maxEnvelopeBytes = MAX_PAYLOAD_BYTES + 128L;
        if (size <= 0L || size > maxEnvelopeBytes) {
            throw new IllegalArgumentException("Размер save-envelope находится вне допустимого диапазона");
        }
        return decode(Files.readAllBytes(source));
    }

    private static int readLeadingInt(byte[] bytes) {
        return ((bytes[0] & 0xff) << 24)
                | ((bytes[1] & 0xff) << 16)
                | ((bytes[2] & 0xff) << 8)
                | (bytes[3] & 0xff);
    }

    private static String requireFingerprint(String value) {
        Objects.requireNonNull(value, "Content fingerprint не задан");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Content fingerprint должен быть lowercase SHA-256 hex");
        }
        return value;
    }

    /**
     * Результат декодирования save-файла.
     *
     * @param state migrated authoritative GameState
     * @param contentFingerprint ожидаемый semantic fingerprint каталога
     * @param legacyRawFormat был ли вход историческим raw GameStateCodec save
     */
    public record DecodedSave(
            GameState state,
            String contentFingerprint,
            boolean legacyRawFormat) {
        /**
         * Проверяет обязательные значения decoded envelope.
         *
         * @param state migrated authoritative GameState
         * @param contentFingerprint ожидаемый semantic fingerprint каталога
         * @param legacyRawFormat признак raw Stage-3 формата
         */
        public DecodedSave {
            Objects.requireNonNull(state, "Decoded GameState не задан");
            contentFingerprint = requireFingerprint(contentFingerprint);
        }
    }
}
