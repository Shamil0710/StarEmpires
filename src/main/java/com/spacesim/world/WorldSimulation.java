package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime orchestrator нескольких локальных {@link SimulationSession} и strategic faction state.
 *
 * <p>Active/local StarSystem исполняется через точный fixed-rate
 * {@link SimulationSession#advanceFrame(float)}. Удалённые системы догоняют active game time
 * coarse-пакетами через {@link SimulationSession#advanceStrategicSteps(int)}. Stage 8 добавляет
 * world-level faction treasury, но не создаёт отдельный финансовый контур: policy переводит деньги
 * атомарно в существующие station wallets и фиксирует обычный money transfer в локальном ledger.</p>
 */
public final class WorldSimulation {
    /** По умолчанию remote Engine обновляется раз на десять эквивалентных local ticks. */
    public static final int DEFAULT_STRATEGIC_STEP_TICKS = 10;
    /** По умолчанию один frame может выполнить не более восьми remote coarse updates. */
    public static final int DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME = 8;

    private final GalaxyTopology topology;
    private final List<StarSystemId> systemOrder;
    private final Map<StarSystemId, SimulationSession> sessionsById;
    private final ContentCatalog contentCatalog;
    private final List<String> factionOrder;
    private final Map<String, FactionEconomicAccount> factionAccountsById;
    private final StarSystemId activeSystemId;
    private final int strategicStepTicks;
    private final int remoteUpdateBudgetPerFrame;

    private long totalLocalFixedTicksExecuted;
    private long totalStrategicUpdatesExecuted;

    private WorldSimulation(
            GalaxyTopology topology,
            List<StarSystemId> systemOrder,
            Map<StarSystemId, SimulationSession> sessionsById,
            ContentCatalog contentCatalog,
            List<String> factionOrder,
            Map<String, FactionEconomicAccount> factionAccountsById,
            StarSystemId activeSystemId,
            int strategicStepTicks,
            int remoteUpdateBudgetPerFrame) {
        this.topology = topology;
        this.systemOrder = List.copyOf(systemOrder);
        this.sessionsById = Map.copyOf(sessionsById);
        this.contentCatalog = contentCatalog;
        this.factionOrder = List.copyOf(factionOrder);
        this.factionAccountsById = Map.copyOf(factionAccountsById);
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
     * @param state persistent world snapshot текущей schema
     * @param contentCatalog единый semantic catalog всех локальных sessions и factions
     * @param activeSystemId StarSystem, исполняемая на полном local tick
     * @param strategicStepTicks число local ticks в одном remote coarse update; должно быть больше 1
     * @param remoteUpdateBudgetPerFrame максимальное число remote updates за один frame
     * @return новый независимый world runtime
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException если active system/faction неизвестны, fixed steps несовместимы,
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

        Map<String, FactionEconomicAccount> factionAccounts = new HashMap<>();
        List<String> factionIds = new ArrayList<>(checked.factions().size());
        for (FactionEconomicState factionState : checked.factions()) {
            if (content.findFaction(factionState.factionContentId()) == null) {
                throw new IllegalArgumentException(
                        "WorldState содержит неизвестную content faction: "
                                + factionState.factionContentId());
            }
            FactionEconomicAccount account = new FactionEconomicAccount(factionState);
            factionAccounts.put(account.factionContentId(), account);
            factionIds.add(account.factionContentId());
        }

        return new WorldSimulation(
                checked.topology(),
                order,
                sessions,
                content,
                factionIds,
                factionAccounts,
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
     * Выполняет одно deterministic решение поддержки ликвидности для указанной faction.
     *
     * <p>Решение рассматривает только market stations с совпадающим runtime faction ID. Системы
     * идут в canonical {@link StarSystemId}-порядке, станции внутри системы — по persistent
     * EntityId. Из treasury расходуется не больше policy budget. Каждая станция пополняется только
     * до liquidity reserve; transfer выполняется {@link WalletComponent#transferTo(WalletComponent,
     * long)} и затем фиксируется как MONEY_TRANSFER в ledger соответствующей local session.</p>
     *
     * @param factionContentId stable faction content ID
     * @return отчёт о физически выполненных денежных переводах
     * @throws NullPointerException если ID не задан
     * @throws IllegalArgumentException если faction не имеет persistent economic state
     */
    public LiquiditySupportReport applyLiquiditySupport(String factionContentId) {
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID не задан").strip();
        FactionEconomicAccount account = factionAccountsById.get(factionId);
        if (account == null) {
            throw new IllegalArgumentException("Faction не имеет economic state: " + factionId);
        }
        ContentCatalog.FactionDefinition faction = contentCatalog.findFaction(factionId);
        if (faction == null) {
            throw new IllegalStateException("Economic faction отсутствует в content catalog: " + factionId);
        }

        long remainingBudget = Math.min(
                account.maxLiquiditySupportPerDecisionMilliCredits(),
                account.treasury().getBalanceMilliCredits());
        if (remainingBudget <= 0L || account.stationLiquidityReserveMilliCredits() <= 0L) {
            return new LiquiditySupportReport(0L, 0);
        }

        long transferred = 0L;
        int supportedStations = 0;
        for (StarSystemId systemId : systemOrder) {
            if (remainingBudget <= 0L) {
                break;
            }
            SimulationSession session = sessionsById.get(systemId);
            List<Entity> ownedStations = ownedMarketStations(session, faction.runtimeId());
            for (Entity station : ownedStations) {
                if (remainingBudget <= 0L) {
                    break;
                }
                WalletComponent wallet = station.getComponent(WalletComponent.class);
                long balance = wallet.getBalanceMilliCredits();
                long reserve = account.stationLiquidityReserveMilliCredits();
                if (balance >= reserve) {
                    continue;
                }
                long deficit = reserve - balance;
                long amount = Math.min(deficit, remainingBudget);
                amount = Math.min(amount, account.treasury().getBalanceMilliCredits());
                if (amount <= 0L || !account.treasury().transferTo(wallet, amount)) {
                    continue;
                }
                session.getLedger().recordMoneyTransfer(
                        "faction:" + factionId + ":treasury",
                        diagnosticStationName(systemId, station),
                        amount,
                        "faction-liquidity-support");
                transferred = Math.addExact(transferred, amount);
                remainingBudget -= amount;
                supportedStations++;
            }
        }
        return new LiquiditySupportReport(transferred, supportedStations);
    }

    /**
     * Возвращает current persistent faction economy.
     *
     * @param factionContentId stable content ID
     * @return current immutable faction state либо empty
     */
    public Optional<FactionEconomicState> findFactionEconomicState(String factionContentId) {
        if (factionContentId == null) {
            return Optional.empty();
        }
        FactionEconomicAccount account = factionAccountsById.get(factionContentId);
        return account == null ? Optional.empty() : Optional.of(account.snapshot());
    }

    /**
     * Возвращает текущий immutable world snapshot.
     *
     * @return WorldState всех систем и faction treasuries в canonical порядке
     */
    public WorldState snapshot() {
        List<StarSystemSimulationState> systemStates = new ArrayList<>(systemOrder.size());
        for (StarSystemId systemId : systemOrder) {
            systemStates.add(new StarSystemSimulationState(
                    systemId,
                    sessionsById.get(systemId).snapshot()));
        }
        List<FactionEconomicState> factionStates = new ArrayList<>(factionOrder.size());
        for (String factionId : factionOrder) {
            factionStates.add(factionAccountsById.get(factionId).snapshot());
        }
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.copyOf(systemStates),
                List.copyOf(factionStates));
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

    private List<Entity> ownedMarketStations(SimulationSession session, int runtimeFactionId) {
        List<Entity> stations = new ArrayList<>();
        for (Entity entity : session.getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (faction == null
                    || faction.factionId != runtimeFactionId
                    || entity.getComponent(MarketComponent.class) == null
                    || entity.getComponent(WalletComponent.class) == null
                    || entity.getComponent(EntityIdComponent.class) == null) {
                continue;
            }
            stations.add(entity);
        }
        stations.sort(Comparator.comparingLong(entity ->
                entity.getComponent(EntityIdComponent.class).id.value()));
        return stations;
    }

    private String diagnosticStationName(StarSystemId systemId, Entity station) {
        IdentityComponent identity = station.getComponent(IdentityComponent.class);
        EntityIdComponent id = station.getComponent(EntityIdComponent.class);
        String name = identity == null || identity.name == null || identity.name.isBlank()
                ? "entity-" + id.id.value()
                : identity.name;
        return "system:" + systemId.value() + "/" + name;
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
     * @param strategicUpdates число remote coarse updates
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

    /**
     * Результат одного faction liquidity-support decision.
     *
     * @param transferredMilliCredits фактически переведённые деньги
     * @param supportedStations число станций-получателей
     */
    public record LiquiditySupportReport(long transferredMilliCredits, int supportedStations) {
        /**
         * Проверяет diagnostic report.
         *
         * @param transferredMilliCredits неотрицательная сумма transfer
         * @param supportedStations неотрицательное число станций
         */
        public LiquiditySupportReport {
            if (transferredMilliCredits < 0L || supportedStations < 0) {
                throw new IllegalArgumentException("Liquidity support report не может быть отрицательным");
            }
        }
    }
}
