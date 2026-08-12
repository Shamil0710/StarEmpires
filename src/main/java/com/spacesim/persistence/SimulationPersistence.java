package com.spacesim.persistence;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Content-aware файловая граница authoritative {@link SimulationSession}.
 *
 * <p>Новая запись сохраняет semantic fingerprint текущего {@link ContentCatalog}. При загрузке
 * fingerprint проверяется до вызова {@link SimulationSession#restore(GameState, ContentCatalog)}.
 * Поэтому save нельзя незаметно продолжить на каталоге с другими ценами, рецептами или entity
 * archetypes. Raw Stage-3 saves поддерживаются через default legacy-compatible catalog.</p>
 */
public final class SimulationPersistence {
    private SimulationPersistence() {
        throw new AssertionError("SimulationPersistence не создаёт экземпляров");
    }

    /**
     * Атомарно сохраняет текущую simulation session вместе с fingerprint её каталога.
     *
     * @param path целевой save-файл
     * @param session сохраняемая сессия
     * @throws IOException если файл нельзя записать
     */
    public static void save(Path path, SimulationSession session) throws IOException {
        SimulationSession checked = Objects.requireNonNull(session, "SimulationSession не задана");
        ContentBoundSaveCodec.write(
                Objects.requireNonNull(path, "Путь сохранения не задан"),
                checked.snapshot(),
                checked.getContentCatalog().getFingerprint());
    }

    /**
     * Загружает save с встроенным production catalog.
     *
     * @param path существующий save-файл
     * @return восстановленная независимая сессия
     * @throws IOException если файл нельзя прочитать
     * @throws IllegalArgumentException если content fingerprint не совпадает
     */
    public static SimulationSession load(Path path) throws IOException {
        return load(path, ContentCatalogLoader.loadDefault());
    }

    /**
     * Загружает save на явно заданном catalog после semantic compatibility check.
     *
     * @param path существующий save-файл
     * @param contentCatalog catalog, которым должна продолжиться сессия
     * @return восстановленная независимая сессия
     * @throws IOException если файл нельзя прочитать
     * @throws IllegalArgumentException если fingerprint каталога не совпадает с сохранением
     */
    public static SimulationSession load(Path path, ContentCatalog contentCatalog) throws IOException {
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        ContentBoundSaveCodec.DecodedSave decoded = ContentBoundSaveCodec.read(
                Objects.requireNonNull(path, "Путь сохранения не задан"));
        if (!decoded.contentFingerprint().equals(content.getFingerprint())) {
            throw new IllegalArgumentException(
                    "Content catalog несовместим с сохранением: expected="
                            + decoded.contentFingerprint() + ", actual=" + content.getFingerprint());
        }
        return SimulationSession.restore(decoded.state(), content);
    }
}
