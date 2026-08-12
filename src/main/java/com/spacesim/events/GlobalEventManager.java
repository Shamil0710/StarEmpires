package com.spacesim.events;

import com.badlogic.gdx.graphics.Color;
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
 * <p>Время менеджера является игровым временем симуляции, а не wall-clock. Весь mutable state,
 * кроме состояния самого RNG-потока, сериализуется через {@link State}. RNG сохраняется владельцем
 * игровой сессии отдельно как {@code StatefulRandom.state}; восстановительный конструктор не
 * выполняет дополнительного случайного вызова.</p>
 */
public class GlobalEventManager {
    private static final double DEFAULT_SPAWN_RATE_PER_SECOND = -60d * Math.log1p(-0.001d);
    private static final float DEFAULT_EVENT_DURATION_SECONDS = 30f;
    private static final double EVENT_TIME_EPSILON_SECONDS = 1e-9d;
    private static final int MAX_AUTOMATIC_EVENTS_PER_UPDATE = 1_024;

    /**
     * Сериализуемое состояние одного активного экономического события.
     *
     * @param name название события
     * @param targetItemId товар
     * @param priceMultiplier множитель цены
     * @param consumptionMultiplier множитель потребления
     * @param remainingDurationSeconds оставшееся время
     * @param x координата X
     * @param y координата Y
     * @param radius радиус действия
     */
    public record EventState(
            String name,
            int targetItemId,
            float priceMultiplier,
            float consumptionMultiplier,
            float remainingDurationSeconds,
            float x,
            float y,
            float radius) {
        /**
         * Проверяет состояние через те же инварианты, что и runtime-событие.
         *
         * @param name название события
         * @param targetItemId товар
         * @param priceMultiplier множитель цены
         * @param consumptionMultiplier множитель потребления
         * @param remainingDurationSeconds оставшееся время
         * @param x координата X
         * @param y координата Y
         * @param radius радиус действия
         */
        public EventState {
            new EconomyEvent(
                    name,
                    targetItemId,
                    priceMultiplier,
                    consumptionMultiplier,
                    remainingDurationSeconds,
                    new Vector2(x, y),
                    radius);
        }

        EconomyEvent toEvent() {
            return new EconomyEvent(
                    name,
                    targetItemId,
                    priceMultiplier,
                    consumptionMultiplier,
                    remainingDurationSeconds,
                    new Vector2(x, y),
                    radius);
        }
    }

    /**
     * Сериализуемое состояние ожидающей показа новости.
     *
     * @param headline заголовок либо {@code null}
     * @param content текст либо {@code null}
     * @param hasColor был ли задан цвет
     * @param red красный канал
     * @param green зелёный канал
     * @param blue синий канал
     * @param alpha прозрачность
     * @param timestamp игровой timestamp в миллисекундах
     */
    public record NewsState(
            String headline,
            String content,
            boolean hasColor,
            float red,
            float green,
            float blue,
            float alpha,
            long timestamp) {
        /**
         * Проверяет сериализуемые данные новости.
         *
         * @param headline заголовок либо {@code null}
         * @param content текст либо {@code null}
         * @param hasColor был ли задан цвет
         * @param red красный канал
         * @param green зелёный канал
         * @param blue синий канал
         * @param alpha прозрачность
         * @param timestamp игровой timestamp в миллисекундах
         */
        public NewsState {
            if (timestamp < 0L) {
                throw new IllegalArgumentException("Timestamp новости не может быть отрицательным");
            }
            if (hasColor && (!Float.isFinite(red)
                    || !Float.isFinite(green)
                    || !Float.isFinite(blue)
                    || !Float.isFinite(alpha))) {
                throw new IllegalArgumentException("Цвет новости должен быть конечным");
            }
        }

        static NewsState fromArticle(NewsArticle article) {
            Color color = article.color;
            return new NewsState(
                    article.headline,
                    article.content,
                    color != null,
                    color == null ? 0f : color.r,
                    color == null ? 0f : color.g,
                    color == null ? 0f : color.b,
                    color == null ? 0f : color.a,
                    article.timestamp);
        }

        NewsArticle toArticle() {
            Color color = hasColor ? new Color(red, green, blue, alpha) : null;
            return new NewsArticle(headline, content, color, timestamp);
        }
    }

    /**
     * Полный mutable state менеджера событий, кроме состояния RNG.
     *
     * @param spawnRatePerSecond средняя частота автоматических событий
     * @param eventRevision ревизия набора событий
     * @param secondsUntilNextSpawn время до следующего автоматического события
     * @param simulationTimeSeconds текущее game time
     * @param activeEvents активные события
     * @param pendingNews очередь ещё не потреблённых новостей
     */
    public record State(
            double spawnRatePerSecond,
            long eventRevision,
            double secondsUntilNextSpawn,
            double simulationTimeSeconds,
            List<EventState> activeEvents,
            List<NewsState> pendingNews) {
        /**
         * Нормализует коллекции и проверяет численные инварианты.
         *
         * @param spawnRatePerSecond средняя частота автоматических событий
         * @param eventRevision ревизия набора событий
         * @param secondsUntilNextSpawn время до следующего автоматического события
         * @param simulationTimeSeconds текущее game time
         * @param activeEvents активные события
         * @param pendingNews очередь новостей
         */
        public State {
            if (!Double.isFinite(spawnRatePerSecond) || spawnRatePerSecond < 0d) {
                throw new IllegalArgumentException("Частота событий состояния некорректна");
            }
            if (eventRevision < 0L || !Double.isFinite(simulationTimeSeconds) || simulationTimeSeconds < 0d) {
                throw new IllegalArgumentException("Время или ревизия событий состояния некорректны");
            }
            boolean validDelay = spawnRatePerSecond == 0d
                    ? secondsUntilNextSpawn == Double.POSITIVE_INFINITY
                    : Double.isFinite(secondsUntilNextSpawn) && secondsUntilNextSpawn >= 0d;
            if (!validDelay) {
                throw new IllegalArgumentException("Таймер следующего события состояния некорректен");
            }
            activeEvents = List.copyOf(Objects.requireNonNull(activeEvents, "Список событий не задан"));
            pendingNews = List.copyOf(Objects.requireNonNull(pendingNews, "Список новостей не задан"));
        }
    }

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
     */
    public GlobalEventManager(RandomGenerator random) {
        this(random, DEFAULT_SPAWN_RATE_PER_SECOND);
    }

    /**
     * Создаёт менеджер с заданной средней частотой автоматических событий.
     *
     * @param spawnRatePerSecond среднее число автоматически создаваемых событий в секунду
     */
    public GlobalEventManager(double spawnRatePerSecond) {
        this(new Random(), spawnRatePerSecond);
    }

    /**
     * Создаёт новый менеджер с заданным RNG и средней частотой событий.
     *
     * @param random источник случайных чисел
     * @param spawnRatePerSecond среднее число автоматически создаваемых событий в секунду
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
     * Восстанавливает менеджер из сохранённого состояния без дополнительного RNG-вызова.
     *
     * @param random RNG-поток уже восстановленный в точное сохранённое состояние
     * @param state сериализуемое состояние менеджера
     * @throws NullPointerException если зависимость не задана
     */
    public GlobalEventManager(RandomGenerator random, State state) {
        this.random = Objects.requireNonNull(random, "Источник случайных чисел не задан");
        State checked = Objects.requireNonNull(state, "Состояние событий не задано");
        this.spawnRatePerSecond = checked.spawnRatePerSecond();
        this.eventRevision = checked.eventRevision();
        this.secondsUntilNextSpawn = checked.secondsUntilNextSpawn();
        this.simulationTimeSeconds = checked.simulationTimeSeconds();
        for (EventState event : checked.activeEvents()) {
            activeEvents.add(event.toEvent());
        }
        for (NewsState news : checked.pendingNews()) {
            pendingNews.add(news.toArticle());
        }
    }

    /**
     * Возвращает полный snapshot менеджера без изменения очереди новостей или RNG.
     *
     * @return immutable состояние, пригодное для сериализации
     */
    public State snapshotState() {
        List<EventState> events = new ArrayList<>(activeEvents.size());
        for (EconomyEvent event : activeEvents) {
            Vector2 location = event.getLocation();
            events.add(new EventState(
                    event.getName(),
                    event.getTargetItemId(),
                    event.getPriceMultiplier(),
                    event.getConsumptionMultiplier(),
                    event.getRemainingDurationSeconds(),
                    location.x,
                    location.y,
                    event.getRadius()));
        }
        List<NewsState> news = new ArrayList<>(pendingNews.size());
        for (NewsArticle article : pendingNews) {
            news.add(NewsState.fromArticle(article));
        }
        return new State(
                spawnRatePerSecond,
                eventRevision,
                secondsUntilNextSpawn,
                simulationTimeSeconds,
                events,
                news);
    }

    /**
     * Продвигает игровое время активных событий и создаёт события, запланированные на интервал.
     *
     * @param deltaSeconds прошедшее игровое время в секундах
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

    /** @return неизменяемое живое представление списка активных событий */
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
     * @return новый изменяемый список накопленных новостей
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
