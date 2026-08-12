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
 * определить необходимость пересчёта. Время менеджера является игровым временем симуляции, а не
 * wall-clock временем компьютера.</p>
 *
 * <p>Класс изменяем и не является потокобезопасным. Возвращаемое методом {@link #getActiveEvents()}
 * представление также нельзя обходить одновременно с обновлением менеджера из другого потока.
 * Очередь новостей и генератор случайных чисел принадлежат менеджеру и обслуживаются тем же
 * последовательным потоком.</p>
 */
public class GlobalEventManager {
    private static final double DEFAULT_SPAWN_RATE_PER_SECOND = -60d * Math.log1p(-0.001d);
    private static final float DEFAULT_EVENT_DURATION_SECONDS = 30f;
    private static final double EVENT_TIME_EPSILON_SECONDS = 1e-9d;
    /** Верхняя граница материализованных автособытий за один вызов {@link #update(float)}. */
    private static final int MAX_AUTOMATIC_EVENTS_PER_UPDATE = 1_024;

    private final List<EconomyEvent> activeEvents = new ArrayList<>();
    private final List<EconomyEvent> activeEventsView = Collections.unmodifiableList(activeEvents);
    private final List<NewsArticle> pendingNews = new ArrayList<>();
    private final RandomGenerator random;
    private final double spawnRatePerSecond;

    private long eventRevision;
    private double secondsUntilNextSpawn;
    private double simulationTimeSeconds;

    /** Создаёт менеджер со стандартной средней частотой автоматических событий и независимым RNG. */
    public GlobalEventManager() {
        this(new Random(), DEFAULT_SPAWN_RATE_PER_SECOND);
    }

    /**
     * Создаёт менеджер со стандартной средней частотой и переданным источником случайности.
     *
     * @param random источник случайных чисел игровой сессии
     * @throws NullPointerException если источник не задан
     * @throws IllegalStateException если источник нарушает контракт {@link RandomGenerator}
     */
    public GlobalEventManager(RandomGenerator random) {
        this(random, DEFAULT_SPAWN_RATE_PER_SECOND);
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
     * @param random источник случайных чисел
     * @param spawnRatePerSecond среднее число автоматически создаваемых событий в секунду
     * @throws NullPointerException если источник случайных чисел не задан
     * @throws IllegalArgumentException если частота отрицательна или не является конечным числом
     * @throws IllegalStateException если источник случайных чисел нарушает контракт и возвращает
     *         значение вне диапазона {@code [0, 1)}
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
     * Продвигает игровое время активных событий и создаёт события, запланированные на интервал.
     *
     * <p>Входной {@code float} канонизируется через его десятичное представление перед накоплением
     * игрового времени. Моменты автоматического появления задаются экспоненциально распределёнными
     * интервалами. Сравнение точных границ использует наносекундный epsilon исключительно для
     * компенсации арифметики {@code double}; расписание и RNG-последовательность не меняются.</p>
     *
     * @param deltaSeconds прошедшее игровое время в секундах
     * @throws IllegalArgumentException если время отрицательно, бесконечно или равно {@code NaN}
     * @throws IllegalStateException если пользовательский источник случайных чисел возвращает
     *         значение вне диапазона {@code [0, 1)} при планировании следующего события
     */
    public void update(float deltaSeconds) {
        if (!Float.isFinite(deltaSeconds) || deltaSeconds < 0f) {
            throw new IllegalArgumentException("Прошедшее время должно быть конечным и неотрицательным");
        }

        double remainingSeconds = canonicalSeconds(deltaSeconds);
        int spawnedEvents = 0;
        while (remainingSeconds > 0d) {
            if (secondsUntilNextSpawn > remainingSeconds + EVENT_TIME_EPSILON_SECONDS) {
                advanceSimulationTime(remainingSeconds);
                secondsUntilNextSpawn -= remainingSeconds;
                return;
            }

            double stepUntilSpawn = Math.min(secondsUntilNextSpawn, remainingSeconds);
            advanceSimulationTime(stepUntilSpawn);
            remainingSeconds -= stepUntilSpawn;
            if (remainingSeconds < EVENT_TIME_EPSILON_SECONDS) {
                remainingSeconds = 0d;
            }

            activateEvent(createDefaultEvent());
            spawnedEvents++;
            secondsUntilNextSpawn = sampleNextSpawnDelay();

            if (spawnedEvents >= MAX_AUTOMATIC_EVENTS_PER_UPDATE
                    && secondsUntilNextSpawn <= remainingSeconds + EVENT_TIME_EPSILON_SECONDS) {
                advanceSimulationTime(remainingSeconds);
                secondsUntilNextSpawn = sampleNextSpawnDelay();
                return;
            }
        }
    }

    /**
     * Активирует событие и помещает соответствующую статью с текущим game timestamp в очередь.
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

        NewsArticle article = NewsGenerator.generate(checkedEvent, simulationTimeSeconds);
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
     * @return неизменяемое живое представление списка активных событий
     */
    public List<EconomyEvent> getActiveEvents() {
        return activeEventsView;
    }

    /** @return текущая ревизия набора событий */
    public long getEventRevision() {
        return eventRevision;
    }

    /** @return неотрицательное игровое время в секундах от начала симуляции */
    public double getSimulationTimeSeconds() {
        return simulationTimeSeconds;
    }

    /**
     * Извлекает все накопленные новости и очищает внутреннюю очередь.
     *
     * @return новый изменяемый список накопленных новостей в порядке их постановки в очередь
     */
    public List<NewsArticle> consumePendingNews() {
        List<NewsArticle> news = new ArrayList<>(pendingNews);
        pendingNews.clear();
        return news;
    }

    private void advanceSimulationTime(double deltaSeconds) {
        if (deltaSeconds <= 0d) {
            return;
        }
        advanceActiveEvents((float) deltaSeconds);
        simulationTimeSeconds += deltaSeconds;
        if (!Double.isFinite(simulationTimeSeconds)) {
            throw new IllegalStateException("Игровое время событий вышло за допустимый диапазон");
        }
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

    private double canonicalSeconds(float seconds) {
        return Double.parseDouble(Float.toString(seconds));
    }
}
