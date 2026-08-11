package com.spacesim.events;

import com.badlogic.gdx.math.Vector2;
import com.spacesim.constants.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Управляет жизненным циклом глобальных экономических событий и связанными с ними новостями.
 *
 * <p>Менеджер рассчитан на последовательное использование из игрового цикла. Все добавления и
 * удаления событий проходят через него и изменяют ревизию, по которой зависимые системы могут
 * определить необходимость пересчёта.</p>
 */
public class GlobalEventManager {
    private static final double DEFAULT_SPAWN_RATE_PER_SECOND = -60d * Math.log1p(-0.001d);
    private static final float DEFAULT_EVENT_DURATION_SECONDS = 30f;

    private final List<EconomyEvent> activeEvents = new ArrayList<>();
    private final List<EconomyEvent> activeEventsView = Collections.unmodifiableList(activeEvents);
    private final List<NewsArticle> pendingNews = new ArrayList<>();
    private final RandomGenerator random;
    private final double spawnRatePerSecond;

    private long eventRevision;
    private double secondsUntilNextSpawn;

    /**
     * Создаёт менеджер со стандартной средней частотой автоматических событий.
     */
    public GlobalEventManager() {
        this(new Random(), DEFAULT_SPAWN_RATE_PER_SECOND);
    }

    /**
     * Создаёт менеджер с заданной средней частотой автоматических событий.
     *
     * <p>Значение {@code 0} удобно для сценариев с полностью управляемыми событиями, в том числе
     * для детерминированных симуляций и тестов.</p>
     *
     * @param spawnRatePerSecond среднее число автоматически создаваемых событий в секунду
     * @throws IllegalArgumentException если частота отрицательна или не является конечным числом
     */
    public GlobalEventManager(double spawnRatePerSecond) {
        this(new Random(), spawnRatePerSecond);
    }

    /**
     * Создаёт менеджер с заданным источником случайности и средней частотой событий.
     *
     * <p>Значение {@code spawnRatePerSecond == 0} полностью отключает автоматическое создание
     * событий, но не мешает ручной активации и завершению уже активных событий.</p>
     *
     * @param random источник случайных чисел
     * @param spawnRatePerSecond среднее число автоматически создаваемых событий в секунду
     * @throws NullPointerException если источник случайных чисел не задан
     * @throws IllegalArgumentException если частота отрицательна или не является конечным числом
     */
    public GlobalEventManager(RandomGenerator random, double spawnRatePerSecond) {
        this.random = Objects.requireNonNull(random, "Источник случайных чисел не задан");
        if (!Double.isFinite(spawnRatePerSecond) || spawnRatePerSecond < 0d) {
            throw new IllegalArgumentException("Частота событий должна быть конечной и неотрицательной");
        }
        this.spawnRatePerSecond = spawnRatePerSecond;
        this.secondsUntilNextSpawn = sampleNextSpawnDelay();
    }

    /**
     * Продвигает время активных событий и создаёт события, запланированные на прошедший интервал.
     *
     * <p>Моменты автоматического появления задаются экспоненциально распределёнными интервалами.
     * Поэтому их расписание при одинаковом источнике случайности не зависит от частоты кадров.</p>
     *
     * @param deltaSeconds прошедшее время в секундах
     * @throws IllegalArgumentException если время отрицательно, бесконечно или равно {@code NaN}
     */
    public void update(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("Прошедшее время должно быть конечным и неотрицательным");
        }

        double remainingSeconds = deltaSeconds;
        while (remainingSeconds > 0d) {
            if (secondsUntilNextSpawn > remainingSeconds) {
                advanceActiveEvents((float) remainingSeconds);
                secondsUntilNextSpawn -= remainingSeconds;
                return;
            }

            double stepUntilSpawn = secondsUntilNextSpawn;
            advanceActiveEvents((float) stepUntilSpawn);
            remainingSeconds -= stepUntilSpawn;

            activateEvent(createDefaultEvent());
            secondsUntilNextSpawn = sampleNextSpawnDelay();
        }
    }

    /**
     * Активирует событие и помещает соответствующую статью в очередь новостей.
     *
     * @param event новое событие
     * @throws NullPointerException если событие не задано
     * @throws IllegalArgumentException если событие завершено или этот экземпляр уже активен
     */
    public void activateEvent(EconomyEvent event) {
        EconomyEvent checkedEvent = Objects.requireNonNull(event, "Событие не задано");
        if (checkedEvent.isExpired()) {
            throw new IllegalArgumentException("Завершённое событие нельзя активировать");
        }
        if (activeEvents.contains(checkedEvent)) {
            throw new IllegalArgumentException("Этот экземпляр события уже активен");
        }

        NewsArticle article = NewsGenerator.generate(checkedEvent);
        activeEvents.add(checkedEvent);
        pendingNews.add(article);
        eventRevision++;
    }

    /**
     * Досрочно отменяет активное событие.
     *
     * @param event отменяемое событие
     * @return {@code true}, если событие было активно и удалено
     */
    public boolean cancelEvent(EconomyEvent event) {
        if (event == null || !activeEvents.remove(event)) {
            return false;
        }
        eventRevision++;
        return true;
    }

    /**
     * Возвращает доступное только для чтения представление активных событий.
     *
     * <p>Представление является «живым»: последующие изменения менеджера отражаются в ранее
     * полученном списке. Добавлять и удалять элементы через него нельзя.</p>
     *
     * @return неизменяемое представление списка активных событий
     */
    public List<EconomyEvent> getActiveEvents() {
        return activeEventsView;
    }

    /**
     * Возвращает ревизию набора активных событий.
     *
     * <p>Значение изменяется при каждой успешной активации, отмене или естественном завершении.
     * Его следует использовать только для сравнения с ранее сохранённым значением.</p>
     *
     * @return текущая ревизия набора событий
     */
    public long getEventRevision() {
        return eventRevision;
    }

    /**
     * Извлекает все накопленные новости и очищает внутреннюю очередь.
     *
     * @return новый изменяемый список накопленных новостей
     */
    public List<NewsArticle> consumePendingNews() {
        List<NewsArticle> news = new ArrayList<>(pendingNews);
        pendingNews.clear();
        return news;
    }

    private void advanceActiveEvents(float deltaSeconds) {
        if (deltaSeconds <= 0f || activeEvents.isEmpty()) {
            return;
        }

        Iterator<EconomyEvent> iterator = activeEvents.iterator();
        while (iterator.hasNext()) {
            EconomyEvent event = iterator.next();
            if (event.advance(deltaSeconds)) {
                iterator.remove();
                eventRevision++;
            }
        }
    }

    private double sampleNextSpawnDelay() {
        if (spawnRatePerSecond == 0d) {
            return Double.POSITIVE_INFINITY;
        }

        double uniform = random.nextDouble();
        if (!Double.isFinite(uniform) || uniform < 0d || uniform >= 1d) {
            throw new IllegalStateException("Источник случайных чисел вернул значение вне диапазона [0, 1)");
        }
        uniform = Math.max(uniform, Double.MIN_VALUE);
        return -Math.log(uniform) / spawnRatePerSecond;
    }

    private EconomyEvent createDefaultEvent() {
        return new EconomyEvent(
                "CRISIS",
                Constants.ITEM_FOOD,
                3f,
                2f,
                DEFAULT_EVENT_DURATION_SECONDS,
                new Vector2(100f, 100f),
                500f);
    }
}
