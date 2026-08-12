package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime Stage-7 orchestrator нескольких локальных {@link SimulationSession}.
 *
 * <p>Active/local StarSystem продолжает исполняться через точный fixed-rate
 * {@link SimulationSession#advanceFrame(float)}. Удалённые системы догоняют active game time
 * coarse-пакетами через {@link SimulationSession#advanceStrategicSteps(int)}. Каждый пакет вызывает
 * их economic Ashley Engine ровно один раз, поэтому число object-level updates уменьшается в
 * {@link #getStrategicStepTicks()} раз относительно local cadence.</p>
 *
 * <p>CPU work ограничивается {@link #getRemoteUpdateBudgetPerFrame() budget} на каждый вызов.
 * Если удалённых систем больше, чем budget успевает обслужить, их lag остаётся в разнице clock ticks
 * и автоматически догоняется в последующих frames. Выбор всегда largest-lag first, tie-break —
 * canonical {@link StarSystemId}, поэтому scheduler не имеет скрытого mutable cursor и exact
 * continuation определяется только сохранёнными GameState clocks.</p>
 */
public final class WorldSimulation {
    /** По умолчанию remote Engine обновляется раз на десять эквивалентных local ticks. */
    public static final int DEFAULT_STRATEGIC_STEP_TICKS = 10;
    /** По умолчанию один frame может выполнить не более восьми remote coarse updates. */
    public static final int DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME = 8;

    private final GalaxyTopology topology;
    private final List<StarSystemId> systemOrder;
    private final Map<StarSystemId, SimulationSession> sessionsById;
    private final StarSystemId activeSystemId;
    private final int strategicStepTicks;
    private final int remoteUpdateBudgetPerFrame;

    private long totalLocalFixedTicksExecuted;
    private long totalStrategicUpdatesExecuted;

    private WorldSimulation(
            GalaxyTopology topology,
            List<StarSystemId> systemOrder,
            Map<StarSystemId, SimulationSession> sessionsById,
            StarSystemId activeSystemId,
            int strategicStepTicks,
            int remoteUpdateBudgetPerFrame) {
        this.topology = topology;
        this.systemOrder = List.copyOf(systemOrder);
        this.sessionsById = Map.copyOf(sessionsById);
        this.activeSystemId = activeSystemId;
        this.strategicStepTicks = strategicStepTicks;
        this.remoteUpdateBudgetPerFrame = remoteUpdateBudgetPerFrame;
    }

    /**
     * Восстанавливает world runtime на встроенном content catalog и стандартном scheduler budget.
     *
     * @param state persistent world snapshot
     * @param activeSystemId StarSystem, исполняемая на полном local tick
     * @return новый независимый world runtime
     */
    public static WorldSimulation restore(
            WorldState state,
            StarSystemId activeSystemId) {
        return restore(
                state,
                ContentCatalogLoader.loadDefault(),
                activeSystemId,
                DEFAULT_STRATEGIC_STEP_TICKS,
                DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    /**
     * Восстанавливает world runtime с явно заданными content catalog и scheduler параметрами.
     *
     * @param state persistent world snapshot
     * @param contentCatalog единый semantic catalog всех локальных sessions
     * @param activeSystemId StarSystem, исполняемая на полном local tick
     * @param strategicStepTicks число local ticks в одном remote coarse update; должно быть больше 1
     * @param remoteUpdateBudgetPerFrame максимальное число remote updates за один frame
     * @return новый независимый world runtime
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException если active system неизвестна, fixed steps несовместимы,
     *         remote clock уже впереди active или scheduler параметры некорректны
     */
    public static WorldSimulation restore(
            WorldState state,
            ContentCatalog contentCatalog,
            StarSystemId activeSystemId,
            int strategicStepTicks,
            int remoteUpdateBudgetPerFrame) {
        WorldState checked = Objects.requireNonNull(state, "WorldState не задан");
        ContentCatalog content = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        StarSystemId activeId = Objects.requireNonNull(activeSystemId, "Active StarSystemId не задан");
        if (strategicStepTicks <= 1) {
            throw new IllegalArgumentException(
                    "Strategic step должен агрегировать больше одного local tick");
        }
        if (remoteUpdateBudgetPerFrame <= 0) {
            throw new IllegalArgumentException("Remote update budget должен быть положительным");
        }
        if (checked.topology().findSystem(activeId).isEmpty()) {
            throw new IllegalArgumentException("Active StarSystem отсутствует в topology: " + activeId);
        }

        Map<StarSystemId, SimulationSession> sessions = new HashMap<>();
        List<StarSystemId> order = new ArrayList<>(checked.systems().size());
        for (StarSystemSimulationState systemState : checked.systems()) {
            SimulationSession session = SimulationSession.restore(
                    systemState.simulationState(),
                    content);
            sessions.put(systemState.systemId(), session);
            order.add(systemState.systemId());
        }
        SimulationSession active = sessions.get(activeId);
        if (active == null) {
            throw new IllegalArgumentException("Для active StarSystem отсутствует SimulationSession");
        }

        int fixedStepBits = Float.floatToIntBits(active.getClock().getFixedStepSeconds());
        long activeTick = active.getClock().getTick();
        for (StarSystemId systemId : order) {
            SimulationSession session = sessions.get(systemId);
            if (Float.floatToIntBits(session.getClock().getFixedStepSeconds()) != fixedStepBits) {
                throw new IllegalArgumentException(
                        "StarSystem sessions используют разные fixed-step durations");
            }
            if (!systemId.equals(activeId) && session.getClock().getTick() > activeTick) {
                throw new IllegalArgumentException(
                        "Remote StarSystem не может опережать active system: " + systemId);
            }
        }

        return new WorldSimulation(
                checked.topology(),
                order,
                sessions,
                activeId,
                strategicStepTicks,
                remoteUpdateBudgetPerFrame);
    }

    /**
     * Продвигает active system на обычном render frame и расходует ограниченный remote budget.
     *
     * @param realDeltaSeconds реальный render delta active system
     * @return статистика фактически исполненной работы этого frame
     */
    public AdvanceReport advanceFrame(float realDeltaSeconds) {
        SimulationSession active = sessionsById.get(activeSystemId);
        int localTicks = active.advanceFrame(realDeltaSeconds);
        totalLocalFixedTicksExecuted = safeAdd(totalLocalFixedTicksExecuted, localTicks);

        int strategicUpdates = 0;
        long activeTick = active.getClock().getTick();
        while (strategicUpdates < remoteUpdateBudgetPerFrame) {
            StarSystemId candidate = mostLaggingDueSystem(activeTick);
            if (candidate == null) {
                break;
            }
            sessionsById.get(candidate).advanceStrategicSteps(strategicStepTicks);
            strategicUpdates++;
            totalStrategicUpdatesExecuted = safeAdd(totalStrategicUpdatesExecuted, 1L);
        }
        return new AdvanceReport(
                localTicks,
                strategicUpdates,
                maximumRemoteLagTicks(activeTick));
    }

    /**
     * Возвращает текущий immutable world snapshot. Scheduler cursor сохранять не требуется: порядок
     * следующего update полностью выводится из persistent clock lag + system IDs.
     *
     * @return WorldState всех систем в canonical порядке
     */
    public WorldState snapshot() {
        List<StarSystemSimulationState> systemStates = new ArrayList<>(systemOrder.size());
        for (StarSystemId systemId : systemOrder) {
            systemStates.add(new StarSystemSimulationState(
                    systemId,
                    sessionsById.get(systemId).snapshot()));
        }
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.copyOf(systemStates));
    }

    /**
     * Ищет local simulation session системы.
     *
     * @param systemId устойчивый system ID
     * @return session либо empty для неизвестной системы
     */
    public Optional<SimulationSession> findSession(StarSystemId systemId) {
        return Optional.ofNullable(systemId == null ? null : sessionsById.get(systemId));
    }

    /** @return immutable Galaxy topology этого runtime world */
    public GalaxyTopology getTopology() {
        return topology;
    }

    /** @return ID системы, исполняемой на полном local tick */
    public StarSystemId getActiveSystemId() {
        return activeSystemId;
    }

    /** @return число эквивалентных local ticks в одном remote update */
    public int getStrategicStepTicks() {
        return strategicStepTicks;
    }

    /** @return максимальное число remote coarse updates за frame */
    public int getRemoteUpdateBudgetPerFrame() {
        return remoteUpdateBudgetPerFrame;
    }

    /** @return число local fixed ticks, исполненных этим runtime после restore */
    public long getTotalLocalFixedTicksExecuted() {
        return totalLocalFixedTicksExecuted;
    }

    /** @return число remote coarse Engine updates, исполненных после restore */
    public long getTotalStrategicUpdatesExecuted() {
        return totalStrategicUpdatesExecuted;
    }

    /** @return максимальное текущее отставание remote system от active clock в fixed ticks */
    public long getMaximumRemoteLagTicks() {
        return maximumRemoteLagTicks(sessionsById.get(activeSystemId).getClock().getTick());
    }

    /**
     * Возвращает clock lag конкретной системы относительно active.
     *
     * @param systemId существующая StarSystem
     * @return неотрицательный lag в fixed ticks; для active равен нулю
     * @throws IllegalArgumentException если система неизвестна или неожиданно опережает active
     */
    public long getLagTicks(StarSystemId systemId) {
        SimulationSession session = sessionsById.get(
                Objects.requireNonNull(systemId, "StarSystemId lag не задан"));
        if (session == null) {
            throw new IllegalArgumentException("Неизвестная StarSystem: " + systemId);
        }
        long activeTick = sessionsById.get(activeSystemId).getClock().getTick();
        long remoteTick = session.getClock().getTick();
        if (remoteTick > activeTick) {
            throw new IllegalArgumentException("StarSystem clock опережает active clock: " + systemId);
        }
        return activeTick - remoteTick;
    }

    private StarSystemId mostLaggingDueSystem(long activeTick) {
        StarSystemId best = null;
        long bestLag = strategicStepTicks - 1L;
        for (StarSystemId systemId : systemOrder) {
            if (systemId.equals(activeSystemId)) {
                continue;
            }
            SimulationSession session = sessionsById.get(systemId);
            long remoteTick = session.getClock().getTick();
            if (remoteTick > activeTick) {
                throw new IllegalStateException(
                        "Remote StarSystem clock опередил active clock: " + systemId);
            }
            long lag = activeTick - remoteTick;
            if (lag > bestLag) {
                best = systemId;
                bestLag = lag;
            }
        }
        return best;
    }

    private long maximumRemoteLagTicks(long activeTick) {
        long maximum = 0L;
        for (StarSystemId systemId : systemOrder) {
            if (systemId.equals(activeSystemId)) {
                continue;
            }
            long remoteTick = sessionsById.get(systemId).getClock().getTick();
            if (remoteTick > activeTick) {
                throw new IllegalStateException(
                        "Remote StarSystem clock опередил active clock: " + systemId);
            }
            maximum = Math.max(maximum, activeTick - remoteTick);
        }
        return maximum;
    }

    private static long safeAdd(long current, long delta) {
        if (delta < 0L || current > Long.MAX_VALUE - delta) {
            throw new IllegalStateException("Diagnostic world scheduler counter переполнен");
        }
        return current + delta;
    }

    /**
     * Диагностика одного world frame.
     *
     * @param localFixedTicks число точных fixed ticks active system
     * @param strategicUpdates число remote coarse Engine updates
     * @param maximumRemoteLagTicks максимальный lag после расходования budget
     */
    public record AdvanceReport(
            int localFixedTicks,
            int strategicUpdates,
            long maximumRemoteLagTicks) {
        /**
         * Валидирует diagnostic counters.
         *
         * @param localFixedTicks число точных fixed ticks active system
         * @param strategicUpdates число remote coarse updates
         * @param maximumRemoteLagTicks максимальный remote lag
         */
        public AdvanceReport {
            if (localFixedTicks < 0 || strategicUpdates < 0 || maximumRemoteLagTicks < 0L) {
                throw new IllegalArgumentException("World advance counters не могут быть отрицательными");
            }
        }
    }
}
