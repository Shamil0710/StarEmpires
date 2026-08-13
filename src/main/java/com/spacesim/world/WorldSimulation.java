package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
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
 * <p>Active StarSystem исполняется на точном fixed-rate, удалённые системы — bounded coarse
 * updates. Faction treasury использует только реальные wallet transfers. Persistent diplomacy,
 * territory и market access материализуются поверх локальных economic sessions, а fiscal policy
 * переносит деньги между station wallets и тем же authoritative treasury без source/sink.</p>
 */
public final class WorldSimulation {
    /** По умолчанию remote Engine обновляется раз на десять эквивалентных local ticks. */
    public static final int DEFAULT_STRATEGIC_STEP_TICKS = 10;
    /** По умолчанию один frame может выполнить не более восьми remote coarse updates. */
    public static final int DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME = 8;

    private static final int BASIS_POINTS_DENOMINATOR = 10_000;

    private final GalaxyTopology topology;
    private final List<StarSystemId> systemOrder;
    private final Map<StarSystemId, SimulationSession> sessionsById;
    private final ContentCatalog contentCatalog;
    private final List<String> factionOrder;
    private final Map<String, FactionEconomicAccount> factionAccountsById;
    private final List<FactionStrategicState> factionStrategies;
    private final Map<String, FactionStrategicState> factionStrategiesById;
    private final Map<StarSystemId, String> territoryOwnerBySystem;
    private final ConstructionProjectService constructionProjectService;
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
            List<FactionStrategicState> factionStrategies,
            Map<String, FactionStrategicState> factionStrategiesById,
            Map<StarSystemId, String> territoryOwnerBySystem,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> constructionProjects,
            StarSystemId activeSystemId,
            int strategicStepTicks,
            int remoteUpdateBudgetPerFrame) {
        this.topology = topology;
        this.systemOrder = List.copyOf(systemOrder);
        this.sessionsById = Map.copyOf(sessionsById);
        this.contentCatalog = contentCatalog;
        this.factionOrder = List.copyOf(factionOrder);
        this.factionAccountsById = Map.copyOf(factionAccountsById);
        this.factionStrategies = List.copyOf(factionStrategies);
        this.factionStrategiesById = Map.copyOf(factionStrategiesById);
        this.territoryOwnerBySystem = Map.copyOf(territoryOwnerBySystem);
        this.constructionProjectService = new ConstructionProjectService(
                contentCatalog,
                this.sessionsById,
                this.factionAccountsById,
                nextConstructionProjectIdValue,
                constructionProjects);
        this.activeSystemId = activeSystemId;
        this.strategicStepTicks = strategicStepTicks;
        this.remoteUpdateBudgetPerFrame = remoteUpdateBudgetPerFrame;
    }

    /**
     * Восстанавливает world runtime на встроенном catalog и standard scheduler budget.
     *
     * @param state persistent world snapshot
     * @param activeSystemId StarSystem полного local tick
     * @return новый независимый world runtime
     */
    public static WorldSimulation restore(WorldState state, StarSystemId activeSystemId) {
        return restore(
                state,
                ContentCatalogLoader.loadDefault(),
                activeSystemId,
                DEFAULT_STRATEGIC_STEP_TICKS,
                DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    /**
     * Восстанавливает world runtime с явно заданными catalog и scheduler параметрами.
     *
     * @param state persistent world snapshot текущей schema
     * @param contentCatalog единый semantic catalog
     * @param activeSystemId StarSystem полного local tick
     * @param strategicStepTicks число local ticks в remote coarse update
     * @param remoteUpdateBudgetPerFrame максимум remote updates за frame
     * @return новый независимый world runtime
     * @throws NullPointerException если обязательное значение не задано
     * @throws IllegalArgumentException если topology/content/clocks/scheduler state некорректны
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
            throw new IllegalArgumentException("Strategic step должен агрегировать больше одного local tick");
        }
        if (remoteUpdateBudgetPerFrame <= 0) {
            throw new IllegalArgumentException("Remote update budget должен быть положительным");
        }
        if (checked.topology().findSystem(activeId).isEmpty()) {
            throw new IllegalArgumentException("Active StarSystem отсутствует в topology: " + activeId);
        }

        Map<String, ContentCatalog.FactionDefinition> contentFactions = new HashMap<>();
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            contentFactions.put(faction.id(), faction);
        }

        Map<String, FactionEconomicAccount> factionAccounts = new HashMap<>();
        List<String> factionIds = new ArrayList<>(checked.factions().size());
        for (FactionEconomicState factionState : checked.factions()) {
            if (!contentFactions.containsKey(factionState.factionContentId())) {
                throw new IllegalArgumentException(
                        "WorldState содержит неизвестную content faction: " + factionState.factionContentId());
            }
            FactionEconomicAccount account = new FactionEconomicAccount(factionState);
            factionAccounts.put(account.factionContentId(), account);
            factionIds.add(account.factionContentId());
        }

        Map<String, FactionStrategicState> strategiesById = new HashMap<>();
        Map<StarSystemId, String> territoryOwners = new HashMap<>();
        for (FactionStrategicState strategy : checked.factionStrategies()) {
            if (!contentFactions.containsKey(strategy.factionContentId())) {
                throw new IllegalArgumentException(
                        "Strategic state содержит неизвестную content faction: " + strategy.factionContentId());
            }
            for (FactionRelationState relation : strategy.relations()) {
                if (!contentFactions.containsKey(relation.targetFactionContentId())) {
                    throw new IllegalArgumentException(
                            "Faction relation содержит неизвестную target faction: "
                                    + relation.targetFactionContentId());
                }
            }
            strategiesById.put(strategy.factionContentId(), strategy);
            for (StarSystemId controlled : strategy.controlledSystems()) {
                territoryOwners.put(controlled, strategy.factionContentId());
            }
        }

        Map<StarSystemId, SimulationSession> sessions = new HashMap<>();
        List<StarSystemId> order = new ArrayList<>(checked.systems().size());
        for (StarSystemSimulationState systemState : checked.systems()) {
            SimulationSession session = SimulationSession.restore(systemState.simulationState(), content);
            FactionPolicyRuntime.install(session, content, checked.factionStrategies());
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
                throw new IllegalArgumentException("StarSystem sessions используют разные fixed-step durations");
            }
            if (!systemId.equals(activeId) && session.getClock().getTick() > activeTick) {
                throw new IllegalArgumentException("Remote StarSystem не может опережать active system: " + systemId);
            }
        }

        return new WorldSimulation(
                checked.topology(),
                order,
                sessions,
                content,
                factionIds,
                factionAccounts,
                checked.factionStrategies(),
                strategiesById,
                territoryOwners,
                checked.nextConstructionProjectIdValue(),
                checked.constructionProjects(),
                activeId,
                strategicStepTicks,
                remoteUpdateBudgetPerFrame);
    }

    /**
     * Продвигает active system и расходует ограниченный remote budget.
     *
     * @param realDeltaSeconds реальный render delta active system
     * @return статистика фактически исполненной работы
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
        constructionProjectService.advance();
        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));
    }

    /**
     * Выполняет deterministic subsidy decision поддержки station liquidity.
     *
     * @param factionContentId stable faction content ID
     * @return отчёт о treasury→station transfers
     */
    public LiquiditySupportReport applyLiquiditySupport(String factionContentId) {
        String factionId = normalizedFactionId(factionContentId);
        FactionEconomicAccount account = requireFactionAccount(factionId);
        ContentCatalog.FactionDefinition faction = requireContentFaction(factionId);

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
            for (Entity station : ownedMarketStations(systemId, session, faction.runtimeId())) {
                if (remainingBudget <= 0L) {
                    break;
                }
                WalletComponent wallet = station.getComponent(WalletComponent.class);
                long balance = wallet.getBalanceMilliCredits();
                long reserve = account.stationLiquidityReserveMilliCredits();
                if (balance >= reserve) {
                    continue;
                }
                long amount = Math.min(reserve - balance, remainingBudget);
                amount = Math.min(amount, account.treasury().getBalanceMilliCredits());
                if (amount <= 0L || !account.treasury().transferTo(wallet, amount)) {
                    continue;
                }
                session.getLedger().recordMoneyTransfer(
                        treasuryName(factionId),
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
     * Выполняет deterministic fiscal decision: own-station tax и foreign-territory tariff.
     *
     * <p>Налоговая база — только station balance выше защищённого liquidity reserve. Tax применяется
     * к собственным market stations во всех системах. Tariff применяется к чужим faction markets
     * только внутри StarSystem, которую контролирует собирающая faction. Каждый levy является
     * обычным station→treasury wallet transfer и записывается как MONEY_TRANSFER.</p>
     *
     * @param factionContentId stable collector faction content ID
     * @return отчёт о физически собранных налогах и тарифах
     */
    public FiscalPolicyReport applyFiscalPolicy(String factionContentId) {
        String factionId = normalizedFactionId(factionContentId);
        FactionEconomicAccount account = requireFactionAccount(factionId);
        ContentCatalog.FactionDefinition faction = requireContentFaction(factionId);
        FactionStrategicState strategy = factionStrategiesById.get(factionId);
        if (strategy == null
                || (strategy.stationTaxBasisPoints() == 0
                && strategy.foreignTerritoryTariffBasisPoints() == 0)) {
            return new FiscalPolicyReport(0L, 0L, 0, 0);
        }

        long taxCollected = 0L;
        long tariffCollected = 0L;
        int taxedStations = 0;
        int tariffedStations = 0;
        for (StarSystemId systemId : systemOrder) {
            SimulationSession session = sessionsById.get(systemId);
            boolean controlled = factionId.equals(territoryOwnerBySystem.get(systemId));
            for (Entity station : completedMarketStations(systemId, session)) {
                FactionComponent owner = station.getComponent(FactionComponent.class);
                if (owner == null) {
                    continue;
                }
                final int basisPoints;
                final boolean tax;
                final String reason;
                if (owner.factionId == faction.runtimeId()) {
                    basisPoints = strategy.stationTaxBasisPoints();
                    tax = true;
                    reason = "faction-station-tax";
                } else if (controlled) {
                    basisPoints = strategy.foreignTerritoryTariffBasisPoints();
                    tax = false;
                    reason = "faction-territory-tariff";
                } else {
                    continue;
                }
                if (basisPoints <= 0) {
                    continue;
                }
                WalletComponent stationWallet = station.getComponent(WalletComponent.class);
                long amount = calculateLevy(
                        stationWallet.getBalanceMilliCredits(),
                        account.stationLiquidityReserveMilliCredits(),
                        basisPoints,
                        account.treasury().getBalanceMilliCredits());
                if (amount <= 0L || !stationWallet.transferTo(account.treasury(), amount)) {
                    continue;
                }
                session.getLedger().recordMoneyTransfer(
                        diagnosticStationName(systemId, station),
                        treasuryName(factionId),
                        amount,
                        reason);
                if (tax) {
                    taxCollected = Math.addExact(taxCollected, amount);
                    taxedStations++;
                } else {
                    tariffCollected = Math.addExact(tariffCollected, amount);
                    tariffedStations++;
                }
            }
        }
        return new FiscalPolicyReport(taxCollected, tariffCollected, taxedStations, tariffedStations);
    }

    /**
     * Возвращает current persistent faction economy.
     *
     * @param factionContentId stable content ID
     * @return immutable faction state либо empty
     */
    public Optional<FactionEconomicState> findFactionEconomicState(String factionContentId) {
        if (factionContentId == null) {
            return Optional.empty();
        }
        FactionEconomicAccount account = factionAccountsById.get(factionContentId);
        return account == null ? Optional.empty() : Optional.of(account.snapshot());
    }

    /**
     * Возвращает persistent diplomacy/territory/economic policy faction.
     *
     * @param factionContentId stable content ID
     * @return strategic state либо empty
     */
    public Optional<FactionStrategicState> findFactionStrategicState(String factionContentId) {
        return Optional.ofNullable(factionContentId == null ? null : factionStrategiesById.get(factionContentId));
    }

    /**
     * Возвращает strategic владельца StarSystem.
     *
     * @param systemId stable system ID
     * @return faction content ID либо empty для neutral/unclaimed system
     */
    public Optional<String> controllingFaction(StarSystemId systemId) {
        if (systemId != null && topology.findSystem(systemId).isEmpty()) {
            throw new IllegalArgumentException("Неизвестная StarSystem: " + systemId);
        }
        return Optional.ofNullable(systemId == null ? null : territoryOwnerBySystem.get(systemId));
    }

    /** @return текущий immutable world snapshot */
    public WorldState snapshot() {
        List<StarSystemSimulationState> systemStates = new ArrayList<>(systemOrder.size());
        for (StarSystemId systemId : systemOrder) {
            systemStates.add(new StarSystemSimulationState(systemId, sessionsById.get(systemId).snapshot()));
        }
        List<FactionEconomicState> factionStates = new ArrayList<>(factionOrder.size());
        for (String factionId : factionOrder) {
            factionStates.add(factionAccountsById.get(factionId).snapshot());
        }
        return new WorldState(
                WorldState.CURRENT_VERSION,
                topology,
                List.copyOf(systemStates),
                List.copyOf(factionStates),
                factionStrategies,
                constructionProjectService.nextIdValue(),
                constructionProjectService.snapshots());
    }

    /**
     * Создаёт persistent physical construction project и его пустую ECS стройплощадку.
     *
     * @param ownerFactionContentId faction treasury owner
     * @param stationArchetypeContentId constructible station archetype
     * @param systemId target StarSystem
     * @param x world X coordinate
     * @param y world Y coordinate
     * @return stable world-level project ID
     */
    public ConstructionProjectId createConstructionProject(
            String ownerFactionContentId,
            String stationArchetypeContentId,
            StarSystemId systemId,
            float x,
            float y) {
        return constructionProjectService.create(
                ownerFactionContentId, stationArchetypeContentId, systemId, x, y);
    }

    /**
     * Физически переводит деньги faction treasury в project-site wallet.
     *
     * @param projectId target construction project
     * @param amountMilliCredits positive transfer amount
     * @return transferred amount or zero when treasury cannot fund it atomically
     */
    public long fundConstructionProject(
            ConstructionProjectId projectId, long amountMilliCredits) {
        return constructionProjectService.fund(projectId, amountMilliCredits);
    }

    /**
     * Физически переносит construction material из local entity inventory на стройплощадку.
     *
     * <p>Это manual/owner delivery path; обычный TradeAI может снабжать тот же site через market
     * transaction без вызова этого API.</p>
     *
     * @param projectId target project
     * @param sourceEntityId source entity in the target system
     * @param itemContentId required material content ID
     * @param amount requested positive units
     * @return accepted units bounded by remaining requirement
     */
    public int deliverConstructionMaterial(
            ConstructionProjectId projectId,
            EntityId sourceEntityId,
            String itemContentId,
            int amount) {
        return constructionProjectService.deliver(projectId, sourceEntityId, itemContentId, amount);
    }

    /**
     * Отменяет проект до первой material delivery, полностью возвращая remaining wallet в treasury.
     *
     * @param projectId target non-terminal project
     * @return true after successful cancellation
     */
    public boolean cancelConstructionProject(ConstructionProjectId projectId) {
        return constructionProjectService.cancel(projectId);
    }

    /**
     * Ищет immutable synchronized construction project snapshot.
     *
     * @param projectId project ID or null
     * @return project snapshot or empty
     */
    public Optional<ConstructionProjectState> findConstructionProject(ConstructionProjectId projectId) {
        return constructionProjectService.find(projectId);
    }

    /** @return immutable construction projects sorted by stable project ID */
    public List<ConstructionProjectState> getConstructionProjects() {
        return constructionProjectService.snapshots();
    }

    /**
     * Ищет local simulation session системы.
     *
     * @param systemId устойчивый system ID
     * @return session либо empty
     */
    public Optional<SimulationSession> findSession(StarSystemId systemId) {
        return Optional.ofNullable(systemId == null ? null : sessionsById.get(systemId));
    }

    /**
     * Создаёт persistent Entity в указанной active или remote StarSystem.
     *
     * @param systemId существующая StarSystem
     * @param entity detached экономически пустая Entity без persistent ID
     * @return новый system-local persistent EntityId
     * @throws NullPointerException если system ID или Entity не заданы
     * @throws IllegalArgumentException если StarSystem отсутствует
     */
    public EntityId createEntity(StarSystemId systemId, Entity entity) {
        return requireLifecycleSession(systemId).createEntity(
                Objects.requireNonNull(entity, "Создаваемая Entity не задана"));
    }

    /**
     * Структурно удаляет экономически пустую Entity из active или remote StarSystem.
     *
     * @param systemId существующая StarSystem
     * @param entityId persistent ID либо {@code null}
     * @return {@code true}, если Entity существовала и была удалена
     * @throws NullPointerException если system ID не задан
     * @throws IllegalArgumentException если StarSystem отсутствует
     */
    public boolean removeEntity(StarSystemId systemId, EntityId entityId) {
        return requireLifecycleSession(systemId).removeEntity(entityId);
    }

    /** @return immutable Galaxy topology этого runtime world */
    public GalaxyTopology getTopology() {
        return topology;
    }

    /** @return ID системы полного local tick */
    public StarSystemId getActiveSystemId() {
        return activeSystemId;
    }

    /** @return число local ticks в одном remote update */
    public int getStrategicStepTicks() {
        return strategicStepTicks;
    }

    /** @return максимальное число remote coarse updates за frame */
    public int getRemoteUpdateBudgetPerFrame() {
        return remoteUpdateBudgetPerFrame;
    }

    /** @return число local fixed ticks после restore */
    public long getTotalLocalFixedTicksExecuted() {
        return totalLocalFixedTicksExecuted;
    }

    /** @return число remote coarse Engine updates после restore */
    public long getTotalStrategicUpdatesExecuted() {
        return totalStrategicUpdatesExecuted;
    }

    /** @return максимальное текущее отставание remote systems */
    public long getMaximumRemoteLagTicks() {
        return maximumRemoteLagTicks(sessionsById.get(activeSystemId).getClock().getTick());
    }

    /**
     * Возвращает clock lag конкретной системы.
     *
     * @param systemId существующая StarSystem
     * @return неотрицательный lag в fixed ticks
     */
    public long getLagTicks(StarSystemId systemId) {
        SimulationSession session = sessionsById.get(Objects.requireNonNull(systemId, "StarSystemId lag не задан"));
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

    private SimulationSession requireLifecycleSession(StarSystemId systemId) {
        StarSystemId checked = Objects.requireNonNull(systemId, "StarSystemId lifecycle не задан");
        SimulationSession session = sessionsById.get(checked);
        if (session == null) {
            throw new IllegalArgumentException("Неизвестная StarSystem: " + checked);
        }
        return session;
    }

    private FactionEconomicAccount requireFactionAccount(String factionId) {
        FactionEconomicAccount account = factionAccountsById.get(factionId);
        if (account == null) {
            throw new IllegalArgumentException("Faction не имеет economic state: " + factionId);
        }
        return account;
    }

    private ContentCatalog.FactionDefinition requireContentFaction(String factionId) {
        ContentCatalog.FactionDefinition faction = contentCatalog.findFaction(factionId);
        if (faction == null) {
            throw new IllegalStateException("Economic faction отсутствует в content catalog: " + factionId);
        }
        return faction;
    }

    private static String normalizedFactionId(String value) {
        String factionId = Objects.requireNonNull(value, "Faction content ID не задан").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID не может быть пустым");
        }
        return factionId;
    }

    private List<Entity> ownedMarketStations(
            StarSystemId systemId, SimulationSession session, int runtimeFactionId) {
        List<Entity> result = new ArrayList<>();
        for (Entity entity : completedMarketStations(systemId, session)) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (faction != null && faction.factionId == runtimeFactionId) {
                result.add(entity);
            }
        }
        return result;
    }

    private List<Entity> completedMarketStations(StarSystemId systemId, SimulationSession session) {
        List<Entity> stations = new ArrayList<>();
        for (Entity entity : session.getEngine().getEntities()) {
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            if (entity.getComponent(MarketComponent.class) == null
                    || entity.getComponent(WalletComponent.class) == null
                    || id == null
                    || constructionProjectService.isConstructionSite(systemId, id.id)) {
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

    private static String treasuryName(String factionId) {
        return "faction:" + factionId + ":treasury";
    }

    private static long calculateLevy(
            long stationBalance,
            long protectedReserve,
            int basisPoints,
            long treasuryBalance) {
        if (stationBalance <= protectedReserve || basisPoints <= 0) {
            return 0L;
        }
        long surplus = stationBalance - protectedReserve;
        long whole = surplus / BASIS_POINTS_DENOMINATOR;
        long remainder = surplus % BASIS_POINTS_DENOMINATOR;
        long levy = Math.addExact(
                Math.multiplyExact(whole, (long) basisPoints),
                (remainder * basisPoints) / BASIS_POINTS_DENOMINATOR);
        return Math.min(levy, Long.MAX_VALUE - treasuryBalance);
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
                throw new IllegalStateException("Remote StarSystem clock опередил active clock: " + systemId);
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
                throw new IllegalStateException("Remote StarSystem clock опередил active clock: " + systemId);
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
     * @param maximumRemoteLagTicks максимальный lag после budget
     */
    public record AdvanceReport(int localFixedTicks, int strategicUpdates, long maximumRemoteLagTicks) {
        /**
         * @param localFixedTicks неотрицательное число local ticks
         * @param strategicUpdates неотрицательное число strategic updates
         * @param maximumRemoteLagTicks неотрицательный lag
         */
        public AdvanceReport {
            if (localFixedTicks < 0 || strategicUpdates < 0 || maximumRemoteLagTicks < 0L) {
                throw new IllegalArgumentException("World advance counters не могут быть отрицательными");
            }
        }
    }

    /**
     * Результат одного liquidity-support decision.
     *
     * @param transferredMilliCredits фактически переведённые деньги
     * @param supportedStations число станций-получателей
     */
    public record LiquiditySupportReport(long transferredMilliCredits, int supportedStations) {
        /**
         * @param transferredMilliCredits неотрицательная сумма transfer
         * @param supportedStations неотрицательное число станций
         */
        public LiquiditySupportReport {
            if (transferredMilliCredits < 0L || supportedStations < 0) {
                throw new IllegalArgumentException("Liquidity support report не может быть отрицательным");
            }
        }
    }

    /**
     * Результат одного fiscal decision.
     *
     * @param taxCollectedMilliCredits own-station tax transfer total
     * @param tariffCollectedMilliCredits foreign-territory tariff transfer total
     * @param taxedStations число собственных станций, заплативших tax
     * @param tariffedStations число чужих станций, заплативших tariff
     */
    public record FiscalPolicyReport(
            long taxCollectedMilliCredits,
            long tariffCollectedMilliCredits,
            int taxedStations,
            int tariffedStations) {
        /**
         * @param taxCollectedMilliCredits неотрицательный tax total
         * @param tariffCollectedMilliCredits неотрицательный tariff total
         * @param taxedStations неотрицательное число taxed stations
         * @param tariffedStations неотрицательное число tariffed stations
         */
        public FiscalPolicyReport {
            if (taxCollectedMilliCredits < 0L
                    || tariffCollectedMilliCredits < 0L
                    || taxedStations < 0
                    || tariffedStations < 0) {
                throw new IllegalArgumentException("Fiscal report counters не могут быть отрицательными");
            }
        }

        /** @return суммарный station→treasury transfer */
        public long totalCollectedMilliCredits() {
            return Math.addExact(taxCollectedMilliCredits, tariffCollectedMilliCredits);
        }
    }
}
