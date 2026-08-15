package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.controllers.TradeController;
import com.spacesim.controllers.TradeTransactionPolicy;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.trade.TradeRouteCostModel;
import com.spacesim.trade.TradeRoutePlanner;
import com.spacesim.systems.TradeAISystem;

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
 *
 * <p>Stage 17 resolves authored and world-defined factions through one immutable
 * {@link FactionIdentityResolver}. Stable string IDs remain authoritative at the strategic layer;
 * dense runtime IDs are used only at the local ECS boundary.</p>
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
    private final FactionIdentityResolver factionIdentityResolver;
    private final List<String> factionOrder;
    private final Map<String, FactionEconomicAccount> factionAccountsById;
    private final ConstructionProjectService constructionProjectService;
    private final TerritorialControlRuntime territorialControlRuntime;
    private final FactionDiplomacyRuntime diplomacyRuntime;
    private final DestructionService destructionService;
    private final FactionEconomicPressureTracker economicPressureTracker;
    private final FleetWorldService fleetWorldService;
    private final FleetJumpService fleetJumpService;
    private StarSystemId activeSystemId;
    private final int strategicStepTicks;
    private final int remoteUpdateBudgetPerFrame;

    private long totalLocalFixedTicksExecuted;
    private long totalStrategicUpdatesExecuted;

    private WorldSimulation(
            GalaxyTopology topology,
            List<StarSystemId> systemOrder,
            Map<StarSystemId, SimulationSession> sessionsById,
            ContentCatalog contentCatalog,
            FactionIdentityResolver factionIdentityResolver,
            List<String> factionOrder,
            Map<String, FactionEconomicAccount> factionAccountsById,
            List<FactionStrategicState> factionStrategies,
            List<FactionDiplomacyState> factionDiplomacyStates,
            long nextConstructionProjectIdValue,
            List<ConstructionProjectState> constructionProjects,
            List<FactionEconomicPressureState> factionEconomicPressures,
            long nextFleetIdValue,
            List<FleetPlacementState> fleetPlacements,
            List<FleetJumpState> fleetJumpStates,
            StarSystemId activeSystemId,
            int strategicStepTicks,
            int remoteUpdateBudgetPerFrame) {
        this.topology = topology;
        this.systemOrder = List.copyOf(systemOrder);
        this.sessionsById = Map.copyOf(sessionsById);
        this.contentCatalog = contentCatalog;
        this.factionIdentityResolver = Objects.requireNonNull(
                factionIdentityResolver, "FactionIdentityResolver не задан");
        this.factionOrder = List.copyOf(factionOrder);
        this.factionAccountsById = Map.copyOf(factionAccountsById);
        this.constructionProjectService = new ConstructionProjectService(
                contentCatalog,
                this.factionIdentityResolver,
                this.sessionsById,
                this.factionAccountsById,
                nextConstructionProjectIdValue,
                constructionProjects);
        this.territorialControlRuntime = new TerritorialControlRuntime(
                topology,
                this.sessionsById,
                this.factionIdentityResolver,
                this.constructionProjectService,
                factionStrategies);
        this.diplomacyRuntime = new FactionDiplomacyRuntime(
                this.factionIdentityResolver, factionDiplomacyStates);
        this.destructionService = new DestructionService(
                contentCatalog,
                this.sessionsById,
                this.factionAccountsById,
                this.constructionProjectService);
        this.economicPressureTracker = new FactionEconomicPressureTracker(factionEconomicPressures);
        this.fleetWorldService = new FleetWorldService(
                this.sessionsById, nextFleetIdValue, fleetPlacements);
        this.fleetJumpService = new FleetJumpService(
                topology, this.sessionsById, this.fleetWorldService,
                JumpTransitTiming.DEFAULT, fleetJumpStates);
        this.activeSystemId = activeSystemId;
        this.strategicStepTicks = strategicStepTicks;
        this.remoteUpdateBudgetPerFrame = remoteUpdateBudgetPerFrame;
        configureTradePolicies();
        refreshFactionMarketAccess();
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

        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                content,
                checked.factionIdentities());

        Map<String, FactionEconomicAccount> factionAccounts = new HashMap<>();
        List<String> factionIds = new ArrayList<>(checked.factions().size());
        for (FactionEconomicState factionState : checked.factions()) {
            if (!identities.containsStableId(factionState.factionContentId())) {
                throw new IllegalArgumentException(
                        "WorldState содержит неизвестную faction: " + factionState.factionContentId());
            }
            FactionEconomicAccount account = new FactionEconomicAccount(factionState);
            factionAccounts.put(account.factionContentId(), account);
            factionIds.add(account.factionContentId());
        }

        for (FactionStrategicState strategy : checked.factionStrategies()) {
            if (!identities.containsStableId(strategy.factionContentId())) {
                throw new IllegalArgumentException(
                        "Strategic state содержит неизвестную faction: " + strategy.factionContentId());
            }
            for (FactionRelationState relation : strategy.relations()) {
                if (!identities.containsStableId(relation.targetFactionContentId())) {
                    throw new IllegalArgumentException(
                            "Faction relation содержит неизвестную target faction: "
                                    + relation.targetFactionContentId());
                }
            }
        }

        Map<StarSystemId, SimulationSession> sessions = new HashMap<>();
        List<StarSystemId> order = new ArrayList<>(checked.systems().size());
        for (StarSystemSimulationState systemState : checked.systems()) {
            SimulationSession session = SimulationSession.restore(systemState.simulationState(), content);
            FactionPolicyRuntime.install(session, identities, checked.factionStrategies());
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

        for (FleetJumpState jump : checked.fleetJumps()) {
            if (jump.phaseStartedTick() > activeTick || jump.phaseEndsTick() <= activeTick) {
                throw new IllegalArgumentException(
                        "Active jump phase does not cover authoritative world tick: " + jump.fleetId());
            }
        }

        return new WorldSimulation(
                checked.topology(),
                order,
                sessions,
                content,
                identities,
                factionIds,
                factionAccounts,
                checked.factionStrategies(),
                checked.factionDiplomacyStates(),
                checked.nextConstructionProjectIdValue(),
                checked.constructionProjects(),
                checked.factionEconomicPressures(),
                checked.nextFleetIdValue(),
                checked.fleets(),
                checked.fleetJumps(),
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
        int localTicks = active.advanceFrame(realDeltaSeconds, this::advanceJumpTransitionsAtTick);
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
        territorialControlRuntime.advance(activeTick);
        boolean diplomacyLifecycleChanged = diplomacyRuntime.advanceTime(activeTick);
        if (diplomacyLifecycleChanged || diplomacyRuntime.marketAccessTransitionCrossed(activeTick)) {
            refreshFactionMarketAccess();
        }
        return new AdvanceReport(localTicks, strategicUpdates, maximumRemoteLagTicks(activeTick));
    }

    /**
     * Выполняет один deterministic Stage-9D economic investment decision для всех factions.
     *
     * <p>Сначала измеряются physical bottlenecks и обновляется persistent hysteresis,
     * затем каждая faction может создать не более одного обычного Stage-9B ConstructionProject.</p>
     *
     * @return число новых construction projects, созданных этим decision
     */
    public int applyEconomicInvestmentDecision() {
        EconomicBottleneckReport report = EconomicBottleneckAnalyzer.analyze(this, contentCatalog);
        long tick = sessionsById.get(activeSystemId).getClock().getTick();
        economicPressureTracker.observe(territorialControlRuntime.snapshots(), report, tick);
        int createdProjects = 0;
        for (String factionId : factionOrder) {
            if (FactionInvestmentPlanner.evaluateFaction(this, contentCatalog, economicPressureTracker, factionId)
                    .isPresent()) {
                createdProjects++;
            }
        }
        return createdProjects;
    }

    /** @return canonical immutable persistent Stage-9D pressure states */
    public List<FactionEconomicPressureState> getFactionEconomicPressureStates() {
        return economicPressureTracker.snapshots();
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
        int runtimeFactionId = requireRuntimeFactionId(factionId);

        long remainingBudget = Math.min(
                account.maxLiquiditySupportPerDecisionMilliCredits(),
                account.spendableTreasuryMilliCredits());
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
            for (Entity station : ownedMarketStations(systemId, session, runtimeFactionId)) {
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
                amount = Math.min(amount, account.spendableTreasuryMilliCredits());
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
        int runtimeFactionId = requireRuntimeFactionId(factionId);
        FactionStrategicState strategy = territorialControlRuntime.find(factionId);
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
            boolean controlled = factionId.equals(territorialControlRuntime.controller(systemId));
            for (Entity station : completedMarketStations(systemId, session)) {
                FactionComponent owner = station.getComponent(FactionComponent.class);
                if (owner == null) {
                    continue;
                }
                final int basisPoints;
                final boolean tax;
                final String reason;
                if (owner.factionId == runtimeFactionId) {
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
     * Returns the common persistent Stage-17F.6 policy-review watermark for one faction.
     *
     * @param factionContentId stable faction content ID
     * @return immutable review state or empty for an unknown faction
     */
    public Optional<FactionPolicyReviewState> findFactionPolicyReviewState(String factionContentId) {
        if (factionContentId == null) {
            return Optional.empty();
        }
        FactionEconomicAccount account = factionAccountsById.get(factionContentId);
        return account == null ? Optional.empty() : Optional.of(account.policyReviewState());
    }

    /**
     * Atomically claims the current common policy-review window for one faction when cadence allows it.
     *
     * <p>Claiming a review does not itself change tax rates, stock floors, recipes, wallets, cargo or
     * other policy values. It only persists the authoritative tick watermark so repeated calls in the
     * same observation window and save/load continuation cannot apply another bounded policy step.</p>
     *
     * @param factionContentId stable faction content ID
     * @param cadence deterministic authoritative-tick review cadence
     * @return {@code true} when this call claimed a due review, otherwise {@code false}
     */
    public boolean tryBeginFactionPolicyReview(
            String factionContentId,
            FactionPolicyReviewCadence cadence) {
        String factionId = normalizedFactionId(factionContentId);
        FactionEconomicAccount account = requireFactionAccount(factionId);
        FactionPolicyReviewCadence checkedCadence = Objects.requireNonNull(
                cadence, "Faction policy review cadence not set");
        long tick = getAuthoritativeWorldTick();
        FactionPolicyReviewState previous = account.policyReviewState();
        if (!checkedCadence.isDue(previous, tick)) {
            return false;
        }
        account.updatePolicyReviewState(checkedCadence.claim(previous, tick));
        return true;
    }

    /**
     * Returns the unified live fiscal policy for one authored or world-defined faction.
     *
     * @param factionContentId stable faction ID
     * @return current policy or empty when the faction lacks economic/strategic state
     */
    public Optional<FactionFiscalPolicyState> findFactionFiscalPolicy(String factionContentId) {
        if (factionContentId == null) {
            return Optional.empty();
        }
        FactionEconomicAccount account = factionAccountsById.get(factionContentId);
        FactionStrategicState strategy = territorialControlRuntime.find(factionContentId);
        if (account == null || strategy == null) {
            return Optional.empty();
        }
        return Optional.of(account.fiscalPolicy(
                strategy.stationTaxBasisPoints(),
                strategy.foreignTerritoryTariffBasisPoints()));
    }

    /**
     * Applies one common player/AI fiscal-policy update without moving money or assets.
     *
     * <p>The update changes future tax/levy rates and treasury spending authorization only. Existing
     * balances, station wallets, construction projects and ledger history remain untouched.</p>
     *
     * @param factionContentId stable authored or world-defined faction ID
     * @param policy bounded fiscal policy
     * @return installed canonical policy
     */
    public FactionFiscalPolicyState updateFactionFiscalPolicy(
            String factionContentId,
            FactionFiscalPolicyState policy) {
        String factionId = normalizedFactionId(factionContentId);
        FactionFiscalPolicyState checked = Objects.requireNonNull(policy, "Faction fiscal policy not set");
        FactionEconomicAccount account = requireFactionAccount(factionId);
        if (territorialControlRuntime.find(factionId) == null) {
            throw new IllegalArgumentException("Faction has no strategic state: " + factionId);
        }
        territorialControlRuntime.updateFiscalRates(
                factionId,
                checked.stationTaxBasisPoints(),
                checked.foreignTerritoryLevyBasisPoints());
        account.updateFiscalPolicy(checked);
        return findFactionFiscalPolicy(factionId).orElseThrow();
    }

    /**
     * Возвращает persistent diplomacy/territory/economic policy faction.
     *
     * @param factionContentId stable content ID
     * @return strategic state либо empty
     */
    public Optional<FactionStrategicState> findFactionStrategicState(String factionContentId) {
        return Optional.ofNullable(factionContentId == null ? null : territorialControlRuntime.find(factionContentId));
    }

    /**
     * Returns the common persistent strategic stock/production policy for one live faction.
     *
     * @param factionContentId authored or world-defined stable faction ID
     * @return current stock/production policy or empty when strategic state is absent
     */
    public Optional<FactionStockProductionPolicyState> findFactionStockProductionPolicy(String factionContentId) {
        FactionStrategicState strategy = factionContentId == null
                ? null
                : territorialControlRuntime.find(factionContentId);
        return strategy == null
                ? Optional.empty()
                : Optional.of(new FactionStockProductionPolicyState(
                        strategy.stockPolicies(), strategy.productionPolicies()));
    }

    /**
     * Replaces strategic stock floors and production recipe preferences without physical mutation.
     *
     * <p>All semantic content references are validated before persistent state changes. Installing
     * policy does not alter wallets, inventories, market targets, production progress or output.
     * Player/UI and AI therefore author the same persistent contract and may explicitly apply it
     * through {@link #applyFactionStrategicPolicy(String)}.</p>
     *
     * @param factionContentId authored or world-defined stable faction ID
     * @param policy canonical stock/production policy
     * @return installed canonical policy
     */
    public FactionStockProductionPolicyState updateFactionStockProductionPolicy(
            String factionContentId,
            FactionStockProductionPolicyState policy) {
        String factionId = normalizedFactionId(factionContentId);
        if (factionIdentityResolver.runtimeId(factionId).isEmpty()) {
            throw new IllegalArgumentException("Unknown faction identity: " + factionId);
        }
        FactionStockProductionPolicyState checked = Objects.requireNonNull(
                policy, "Faction stock/production policy not set");
        FactionStrategicPolicyEngine.validatePolicy(contentCatalog, checked);
        territorialControlRuntime.updateStockProductionPolicies(factionId, checked);
        return findFactionStockProductionPolicy(factionId).orElseThrow();
    }

    /**
     * Returns the persistent automatic resilience stock-demand overlay for one faction.
     *
     * <p>Resilience demand is stored separately from operator-authored base stock policy.
     * Multiple malformed resilience goals are defensively aggregated by item maximum; the
     * update boundary canonicalizes them back to one goal.</p>
     *
     * @param factionContentId stable authored or world-defined faction ID
     * @return canonical item-sorted resilience demand floors
     */
    public List<FactionStockPolicyState> findFactionResilienceDemandFloors(
            String factionContentId) {
        FactionStrategicState strategy = factionContentId == null
                ? null
                : territorialControlRuntime.find(factionContentId);
        if (strategy == null) {
            return List.of();
        }
        Map<String, Integer> floors = new java.util.TreeMap<>();
        for (FactionStrategicGoalState goal : strategy.strategicGoals()) {
            if (goal.type() != FactionStrategicGoalState.GoalType.RESILIENCE) {
                continue;
            }
            for (FactionStockPolicyState floor : goal.demandFloors()) {
                floors.merge(floor.itemContentId(), floor.targetStockFloor(), Math::max);
            }
        }
        List<FactionStockPolicyState> result = new ArrayList<>(floors.size());
        for (Map.Entry<String, Integer> floor : floors.entrySet()) {
            result.add(new FactionStockPolicyState(floor.getKey(), floor.getValue()));
        }
        return List.copyOf(result);
    }

    /**
     * Replaces only the automatic resilience stock-demand overlay.
     *
     * <p>Base stock policy and all non-resilience strategic goals are preserved. Item
     * references are validated before mutation. An empty list removes the automatic
     * overlay. This operation authors persistent demand only and does not change cargo,
     * wallets, production output or physical market targets until ordinary strategic
     * policy application is explicitly requested.</p>
     *
     * @param factionContentId stable authored or world-defined faction ID
     * @param demandFloors canonical automatic resilience floors; empty removes overlay
     * @return installed canonical resilience floors
     */
    public List<FactionStockPolicyState> updateFactionResilienceDemandFloors(
            String factionContentId,
            List<FactionStockPolicyState> demandFloors) {
        String factionId = normalizedFactionId(factionContentId);
        if (factionIdentityResolver.runtimeId(factionId).isEmpty()) {
            throw new IllegalArgumentException("Unknown faction identity: " + factionId);
        }
        List<FactionStockPolicyState> checked = List.copyOf(Objects.requireNonNull(
                demandFloors, "Faction resilience demand floors not set"));
        FactionStrategicPolicyEngine.validatePolicy(
                contentCatalog,
                new FactionStockProductionPolicyState(checked, List.of()));
        FactionStrategicState current = territorialControlRuntime.find(factionId);
        if (current == null) {
            throw new IllegalArgumentException("Faction has no strategic state: " + factionId);
        }
        List<FactionStrategicGoalState> goals = new ArrayList<>();
        for (FactionStrategicGoalState goal : current.strategicGoals()) {
            if (goal.type() != FactionStrategicGoalState.GoalType.RESILIENCE) {
                goals.add(goal);
            }
        }
        if (!checked.isEmpty()) {
            goals.add(new FactionStrategicGoalState(
                    "policy.resilience",
                    FactionStrategicGoalState.GoalType.RESILIENCE,
                    checked));
        }
        territorialControlRuntime.updateStrategicGoals(factionId, goals);
        return findFactionResilienceDemandFloors(factionId);
    }

    /**
     * Applies the currently authored strategic stock/production policy to ordinary ECS configuration.
     *
     * <p>The executor only adjusts existing market target floors and production recipes. It does not
     * create goods, money, demand orders or assets; ordinary market/logistics/production systems own
     * all subsequent physical consequences.</p>
     *
     * @param factionContentId authored or world-defined stable faction ID
     * @return report of physical configuration changes
     */
    public FactionStrategicPolicyEngine.ApplicationReport applyFactionStrategicPolicy(
            String factionContentId) {
        return FactionStrategicPolicyEngine.apply(
                this, contentCatalog, normalizedFactionId(factionContentId));
    }

    /**
     * Replaces one faction's persistent institutional doctrine through the common player/AI boundary.
     *
     * <p>This operation changes decision preferences only. It does not refresh legal market access,
     * transfer money, move cargo, alter territory, create assets or rewrite diplomatic history.
     * Subsequent common evaluators read the new profile directly from strategic state.</p>
     *
     * @param factionContentId authored or world-defined stable faction ID
     * @param doctrine new bounded institutional profile
     * @return installed canonical strategic state
     */
    public FactionStrategicState updateFactionDoctrine(
            String factionContentId,
            FactionDoctrineState doctrine) {
        return territorialControlRuntime.updateDoctrine(factionContentId, doctrine);
    }

    /**
     * Resolves authored or world-defined stable faction ID to its local ECS runtime slot.
     *
     * @param stableFactionId stable faction ID or {@code null}
     * @return dense runtime ID or empty
     */
    public Optional<Integer> findFactionRuntimeId(String stableFactionId) {
        return factionIdentityResolver.runtimeId(stableFactionId);
    }

    /**
     * Transfers real money from a caller-owned authoritative wallet into the ordinary Stage-8 faction treasury.
     *
     * <p>The operation is a pure money transfer: no source/sink is recorded. Both wallets are validated
     * before mutation and one {@code MONEY_TRANSFER} is appended to the active-system ledger. If ledger
     * recording unexpectedly fails after the wallet move, the wallet move is reversed before the exception
     * escapes.</p>
     *
     * @param factionContentId stable destination faction identity
     * @param sourceWallet authoritative source wallet owned by the caller
     * @param sourceLedgerName non-empty diagnostic source name
     * @param amountMilliCredits strictly positive full-transfer amount
     * @param reason non-empty ledger reason
     * @return true when the full amount was transferred; false when either wallet rejects it
     */
    public boolean transferToFactionTreasury(
            String factionContentId,
            WalletComponent sourceWallet,
            String sourceLedgerName,
            long amountMilliCredits,
            String reason) {
        String factionId = normalizedFactionId(factionContentId);
        FactionEconomicAccount account = requireFactionAccount(factionId);
        WalletComponent source = Objects.requireNonNull(sourceWallet, "Treasury source wallet not set");
        String sourceName = Objects.requireNonNull(
                sourceLedgerName, "Treasury source ledger name not set").strip();
        String transferReason = Objects.requireNonNull(
                reason, "Treasury transfer reason not set").strip();
        if (sourceName.isEmpty() || transferReason.isEmpty()) {
            throw new IllegalArgumentException("Treasury transfer ledger labels cannot be blank");
        }
        if (amountMilliCredits <= 0L) {
            throw new IllegalArgumentException("Treasury transfer amount must be positive");
        }

        WalletComponent treasury = account.treasury();
        if (!source.canDebit(amountMilliCredits) || !treasury.canCredit(amountMilliCredits)) {
            return false;
        }
        if (!source.transferTo(treasury, amountMilliCredits)) {
            return false;
        }
        try {
            sessionsById.get(activeSystemId).getLedger().recordMoneyTransfer(
                    sourceName,
                    treasuryName(factionId),
                    amountMilliCredits,
                    transferReason);
            return true;
        } catch (RuntimeException exception) {
            if (!treasury.transferTo(source, amountMilliCredits)) {
                exception.addSuppressed(new IllegalStateException(
                        "Faction treasury transfer rollback could not restore money"));
            }
            throw exception;
        }
    }

    /**
     * Transfers real money from the ordinary Stage-8 faction treasury into a caller-owned wallet.
     *
     * <p>The operation is a pure money transfer: no source/sink is recorded. Both wallets are validated
     * before mutation and one {@code MONEY_TRANSFER} is appended to the active-system ledger. If ledger
     * recording unexpectedly fails after the wallet move, the wallet move is reversed before the exception
     * escapes.</p>
     *
     * @param factionContentId stable source faction identity
     * @param destinationWallet authoritative destination wallet owned by the caller
     * @param destinationLedgerName non-empty diagnostic destination name
     * @param amountMilliCredits strictly positive full-transfer amount
     * @param reason non-empty ledger reason
     * @return true when the full amount was transferred; false when either wallet rejects it
     */
    public boolean transferFromFactionTreasury(
            String factionContentId,
            WalletComponent destinationWallet,
            String destinationLedgerName,
            long amountMilliCredits,
            String reason) {
        String factionId = normalizedFactionId(factionContentId);
        FactionEconomicAccount account = requireFactionAccount(factionId);
        WalletComponent destination = Objects.requireNonNull(
                destinationWallet, "Treasury destination wallet not set");
        String destinationName = Objects.requireNonNull(
                destinationLedgerName, "Treasury destination ledger name not set").strip();
        String transferReason = Objects.requireNonNull(
                reason, "Treasury transfer reason not set").strip();
        if (destinationName.isEmpty() || transferReason.isEmpty()) {
            throw new IllegalArgumentException("Treasury transfer ledger labels cannot be blank");
        }
        if (amountMilliCredits <= 0L) {
            throw new IllegalArgumentException("Treasury transfer amount must be positive");
        }
    
        WalletComponent treasury = account.treasury();
        if (!treasury.canDebit(amountMilliCredits) || !destination.canCredit(amountMilliCredits)) {
            return false;
        }
        if (!treasury.transferTo(destination, amountMilliCredits)) {
            return false;
        }
        try {
            sessionsById.get(activeSystemId).getLedger().recordMoneyTransfer(
                    treasuryName(factionId),
                    destinationName,
                    amountMilliCredits,
                    transferReason);
            return true;
        } catch (RuntimeException exception) {
            if (!destination.transferTo(treasury, amountMilliCredits)) {
                exception.addSuppressed(new IllegalStateException(
                        "Faction treasury withdrawal rollback could not restore money"));
            }
            throw exception;
        }
    }

    /**
     * Resolves a dense local ECS runtime faction slot back to stable identity.
     *
     * @param runtimeFactionId dense runtime faction ID
     * @return stable faction ID or empty
     */
    public Optional<String> findFactionStableId(int runtimeFactionId) {
        return factionIdentityResolver.stableId(runtimeFactionId);
    }

    /** @return canonical immutable world-defined faction identities */
    public List<WorldFactionIdentityState> getWorldFactionIdentities() {
        return factionIdentityResolver.dynamicIdentities();
    }

    /** @return canonical immutable Stage-17E faction diplomacy aggregates */
    public List<FactionDiplomacyState> getFactionDiplomacyStates() {
        return diplomacyRuntime.snapshots();
    }

    /**
     * Finds persistent institutional diplomacy for one authored or world-defined faction.
     *
     * @param factionContentId stable faction ID
     * @return diplomacy aggregate or empty
     */
    public Optional<FactionDiplomacyState> findFactionDiplomacyState(String factionContentId) {
        return Optional.ofNullable(diplomacyRuntime.find(factionContentId));
    }

    /**
 * Computes current read-only structural economic dependence of one faction on another.
 *
 * <p>The result is derived from physical inventories, market targets/quotes, active production,
 * legal access and topology at the authoritative world tick. It does not mutate world state and
 * does not pretend that current structural exposure is historical import/export share.</p>
 *
 * @param sourceFactionContentId faction whose exposure is measured
 * @param partnerFactionContentId supplier/market partner being tested
 * @return immutable authoritative diagnostics snapshot
 */
public FactionEconomicDependenceDiagnostics analyzeEconomicDependence(
        String sourceFactionContentId,
        String partnerFactionContentId) {
    return FactionEconomicDependenceAnalyzer.analyze(
            this, contentCatalog, sourceFactionContentId, partnerFactionContentId);
}

    /**
     * Evaluates explainable effective legal market access at the authoritative world tick.
     *
     * @param marketOwnerFactionContentId faction owning the market
     * @param participantFactionContentId participant faction, or null for unfactioned
     * @return precedence decision: embargo, treaty right, or relation threshold
     */
    public DiplomaticMarketAccessResolver.Decision evaluateFactionMarketAccess(
            String marketOwnerFactionContentId,
            String participantFactionContentId) {
        return DiplomaticMarketAccessResolver.evaluate(
                territorialControlRuntime.snapshots(),
                diplomacyRuntime.snapshots(),
                marketOwnerFactionContentId,
                participantFactionContentId,
                getAuthoritativeWorldTick());
    }

    /**
     * Finds one persistent treaty by globally unique treaty ID.
     *
     * @param treatyId stable treaty ID
     * @return current treaty snapshot or empty
     */
    public Optional<DiplomaticTreatyState> findDiplomaticTreaty(String treatyId) {
        return Optional.ofNullable(diplomacyRuntime.findTreaty(treatyId));
    }

    /**
     * Applies one common player/AI treaty lifecycle command atomically to persistent diplomacy.
     *
     * <p>Natural treaty expiry is materialized first at the same authoritative tick. A successful
     * command immediately rebuilds transient market-access policy so accepted/breached agreements
     * affect the ordinary trade boundary without save/load. If command validation fails after a
     * natural expiry was materialized, the expiry remains authoritative and the projection is still
     * refreshed before the exception is propagated.</p>
     *
     * @param command treaty lifecycle command from player/UI or AI
     * @return immutable successful transition result
     */
    public DiplomaticTreatyCommandResult applyDiplomaticTreatyCommand(DiplomaticTreatyCommand command) {
        long worldTick = getAuthoritativeWorldTick();
        boolean lifecycleChanged = diplomacyRuntime.advanceTime(worldTick);
        try {
            DiplomaticTreatyCommandResult result = diplomacyRuntime.apply(command, worldTick);
            refreshFactionMarketAccess();
            return result;
        } catch (RuntimeException exception) {
            if (lifecycleChanged) {
                refreshFactionMarketAccess();
            }
            throw exception;
        }
    }

    /**
     * Applies one common player/AI unilateral market-access embargo command.
     *
     * <p>The command mutates only persistent diplomacy and immediately re-materializes ordinary
     * market-access policy. It performs no wallet transfer, cargo mutation, price mutation or
     * abstract economic damage.</p>
     *
     * @param command embargo impose/revoke command
     * @return immutable successful legal transition result
     */
    public DiplomaticEmbargoCommandResult applyDiplomaticEmbargoCommand(DiplomaticEmbargoCommand command) {
        long worldTick = getAuthoritativeWorldTick();
        boolean lifecycleChanged = diplomacyRuntime.advanceTime(worldTick);
        try {
            DiplomaticEmbargoCommandResult result = diplomacyRuntime.apply(command, worldTick);
            refreshFactionMarketAccess();
            return result;
        } catch (RuntimeException exception) {
            if (lifecycleChanged) {
                refreshFactionMarketAccess();
            }
            throw exception;
        }
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
        return Optional.ofNullable(systemId == null ? null : territorialControlRuntime.controller(systemId));
    }

    /** @return authoritative world tick used by territorial stabilization and legal expiry */
    public long getAuthoritativeWorldTick() {
        return sessionsById.get(activeSystemId).getClock().getTick();
    }

    /**
     * Declares a political claim without granting sovereignty.
     *
     * @param factionContentId authored or world-defined claimant faction
     * @param systemId claimed system
     * @return persistent claim state
     */
    public TerritorialClaimState declareTerritorialClaim(
            String factionContentId,
            StarSystemId systemId) {
        return territorialControlRuntime.declareClaim(
                factionContentId, systemId, getAuthoritativeWorldTick());
    }

    /**
     * Withdraws a non-established political claim.
     *
     * @param factionContentId claimant faction
     * @param systemId claimed system
     * @return true when a claim was removed
     */
    public boolean withdrawTerritorialClaim(String factionContentId, StarSystemId systemId) {
        return territorialControlRuntime.withdrawClaim(factionContentId, systemId);
    }

    /**
     * Voluntarily relinquishes established control without creating any replacement controller.
     *
     * @param factionContentId current controller
     * @param systemId controlled system
     * @return true when control was relinquished
     */
    public boolean relinquishTerritorialControl(String factionContentId, StarSystemId systemId) {
        return territorialControlRuntime.relinquishControl(
                factionContentId, systemId, getAuthoritativeWorldTick());
    }

    /**
     * Records directed diplomatic recognition of another faction's claim.
     *
     * @param recognizingFactionContentId recognizing faction
     * @param targetFactionContentId recognized claimant
     * @param systemId claimed system
     * @return persistent recognition
     */
    public TerritorialRecognitionState recognizeTerritorialClaim(
            String recognizingFactionContentId,
            String targetFactionContentId,
            StarSystemId systemId) {
        return territorialControlRuntime.recognize(
                recognizingFactionContentId,
                targetFactionContentId,
                systemId,
                TerritorialRecognitionState.Kind.CLAIM);
    }

    /**
     * Records directed diplomatic recognition of another faction's established control.
     *
     * @param recognizingFactionContentId recognizing faction
     * @param targetFactionContentId recognized controller
     * @param systemId controlled system
     * @return persistent recognition
     */
    public TerritorialRecognitionState recognizeTerritorialControl(
            String recognizingFactionContentId,
            String targetFactionContentId,
            StarSystemId systemId) {
        return territorialControlRuntime.recognize(
                recognizingFactionContentId,
                targetFactionContentId,
                systemId,
                TerritorialRecognitionState.Kind.CONTROL);
    }

    /**
     * Grants an explicit foreign construction concession in territory controlled by the grantor.
     *
     * @param grantorFactionContentId current controller
     * @param granteeFactionContentId foreign builder
     * @param systemId controlled system
     * @param expiresTick exclusive expiry tick or -1 for indefinite
     * @return persistent construction right
     */
    public TerritorialConstructionRightState grantTerritorialConstructionRight(
            String grantorFactionContentId,
            String granteeFactionContentId,
            StarSystemId systemId,
            long expiresTick) {
        return territorialControlRuntime.grantConstructionRight(
                grantorFactionContentId,
                granteeFactionContentId,
                systemId,
                getAuthoritativeWorldTick(),
                expiresTick);
    }

    /**
     * Revokes a previously granted foreign construction concession.
     *
     * @param grantorFactionContentId controller/grantor
     * @param granteeFactionContentId foreign grantee
     * @param systemId affected system
     * @return true when a right was removed
     */
    public boolean revokeTerritorialConstructionRight(
            String grantorFactionContentId,
            String granteeFactionContentId,
            StarSystemId systemId) {
        return territorialControlRuntime.revokeConstructionRight(
                grantorFactionContentId, granteeFactionContentId, systemId);
    }

    /**
     * Checks a live explicit construction concession from the current controller.
     *
     * @param grantorFactionContentId expected controller/grantor
     * @param granteeFactionContentId proposed foreign builder
     * @param systemId target system
     * @return true only for a current controller and unexpired matching right
     */
    public boolean hasTerritorialConstructionRight(
            String grantorFactionContentId,
            String granteeFactionContentId,
            StarSystemId systemId) {
        FactionStrategicState grantor = territorialControlRuntime.find(grantorFactionContentId);
        return grantor != null
                && grantor.controls(systemId)
                && grantor.grantsConstructionRightTo(
                        granteeFactionContentId, systemId, getAuthoritativeWorldTick());
    }

    /**
     * Reports whether material rival claims currently make the system territorially contested.
     *
     * @param systemId target system
     * @return true when ordinary uncontested jurisdiction does not exist
     */
    public boolean isTerritoriallyContested(StarSystemId systemId) {
        return territorialControlRuntime.isContested(systemId);
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
                territorialControlRuntime.snapshots(),
                constructionProjectService.nextIdValue(),
                constructionProjectService.snapshots(),
                economicPressureTracker.snapshots(),
                fleetWorldService.nextIdValue(),
                fleetWorldService.snapshots(),
                fleetJumpService.snapshots(),
                factionIdentityResolver.dynamicIdentities(),
                diplomacyRuntime.snapshots());
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
        return createConstructionProject(
                ownerFactionContentId,
                ownerFactionContentId,
                ownerFactionContentId,
                stationArchetypeContentId,
                systemId,
                x,
                y);
    }

    /**
     * Creates a project whose authorization principal and asset faction are identical.
     *
     * @param ownerFactionContentId faction treasury payer, or {@code null} for external settlement
     * @param legalFactionContentId faction exercising territorial rights and affiliating the asset
     * @param stationArchetypeContentId constructible station archetype
     * @param systemId target system
     * @param x finite X coordinate
     * @param y finite Y coordinate
     * @return stable project ID
     */
    public ConstructionProjectId createConstructionProject(
            String ownerFactionContentId,
            String legalFactionContentId,
            String stationArchetypeContentId,
            StarSystemId systemId,
            float x,
            float y) {
        return createConstructionProject(
                ownerFactionContentId,
                legalFactionContentId,
                legalFactionContentId,
                stationArchetypeContentId,
                systemId,
                x,
                y);
    }

    /**
     * Creates one ordinary project after shared territorial authorization.
     *
     * <p>Economic payer, authorization principal and resulting asset affiliation are separate
     * dimensions. Personal projects can exercise membership rights without becoming assets of
     * the member faction, while a world-defined player faction can explicitly affiliate them.</p>
     *
     * @param ownerFactionContentId faction treasury payer, or {@code null} for external settlement
     * @param authorizationFactionContentId faction whose territorial right is exercised, or {@code null}
     * @param legalFactionContentId faction assigned to site/completed asset, or {@code null}
     * @param stationArchetypeContentId constructible station archetype
     * @param systemId target system
     * @param x finite X coordinate
     * @param y finite Y coordinate
     * @return stable project ID
     * @throws IllegalStateException when ordinary construction is not legally authorized
     */
    public ConstructionProjectId createConstructionProject(
            String ownerFactionContentId,
            String authorizationFactionContentId,
            String legalFactionContentId,
            String stationArchetypeContentId,
            StarSystemId systemId,
            float x,
            float y) {
        TerritorialConstructionAuthorization.Decision authorization =
                TerritorialConstructionAuthorization.evaluate(
                        this, authorizationFactionContentId, systemId);
        if (!authorization.allowed()) {
            throw new IllegalStateException(
                    "Construction denied by territorial law: " + authorization.reason()
                            + " controller=" + authorization.controllingFactionContentId());
        }
        return constructionProjectService.create(
                ownerFactionContentId,
                legalFactionContentId,
                stationArchetypeContentId,
                systemId,
                x,
                y);
    }

    /**
     * Physically transfers faction treasury money into a project-site wallet after fiscal authorization.
     *
     * @param projectId target construction project
     * @param amountMilliCredits positive transfer amount
     * @return transferred amount or zero when treasury/fiscal policy cannot fund it atomically
     */
    public long fundConstructionProject(
            ConstructionProjectId projectId, long amountMilliCredits) {
        ConstructionProjectState project = constructionProjectService.find(projectId).orElse(null);
        if (project != null && project.settlementKind() == ConstructionSettlementKind.FACTION_TREASURY) {
            FactionEconomicAccount account = requireFactionAccount(project.ownerFactionContentId());
            if (amountMilliCredits > account.maxConstructionInvestmentPerDecisionMilliCredits()
                    || amountMilliCredits > account.spendableTreasuryMilliCredits()) {
                return 0L;
            }
        }
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

    /** @return immutable world-level fleet placements sorted by stable FleetId */
    public List<FleetPlacementState> getFleetPlacements() {
        return fleetWorldService.snapshots();
    }

    /**
     * Ищет world-level placement fleet.
     *
     * @param fleetId stable world FleetId or {@code null}
     * @return current placement or empty
     */
    public Optional<FleetPlacementState> findFleet(FleetId fleetId) {
        return fleetWorldService.find(fleetId);
    }

    /**
     * Разрешает system-local fleet entity в стабильный world FleetId.
     *
     * @param systemId local StarSystem or {@code null}
     * @param entityId system-local EntityId or {@code null}
     * @return stable FleetId or empty
     */
    public Optional<FleetId> findFleetByLocal(StarSystemId systemId, EntityId entityId) {
        return fleetWorldService.findByLocal(systemId, entityId);
    }

    /** @return immutable active jump states sorted by stable FleetId */
    public List<FleetJumpState> getFleetJumpStates() {
        return fleetJumpService.snapshots();
    }

    /**
     * Ищет active jump operation fleet.
     *
     * @param fleetId stable FleetId or {@code null}
     * @return current jump phase or empty
     */
    public Optional<FleetJumpState> findFleetJump(FleetId fleetId) {
        return fleetJumpService.find(fleetId);
    }

    /**
     * Запрашивает authoritative direct jump по topology edge.
     *
     * <p>Если fleet находится в remote system, её local session сначала догоняется ровно до
     * текущего world tick через тот же coarse simulation core. После этого jump FSM использует
     * абсолютные tick boundaries и не зависит от render-frame partitioning.</p>
     *
     * @param fleetId stable fleet identity
     * @param destinationSystemId directly connected destination
     * @param arrivalX finite destination-local arrival X
     * @param arrivalY finite destination-local arrival Y
     * @return persistent MOVING_TO_JUMP state
     */
    public FleetJumpState requestFleetJump(
            FleetId fleetId,
            StarSystemId destinationSystemId,
            float arrivalX,
            float arrivalY) {
        FleetPlacementState placement = fleetWorldService.find(
                Objects.requireNonNull(fleetId, "FleetId jump request не задан")).orElseThrow(
                () -> new IllegalArgumentException("Unknown FleetId: " + fleetId));
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("Jump request требует fleet в local StarSystem: " + fleetId);
        }
        long worldTick = sessionsById.get(activeSystemId).getClock().getTick();
        synchronizeSystemToTick(placement.systemId(), worldTick);
        return fleetJumpService.requestJump(
                fleetId, destinationSystemId, worldTick, arrivalX, arrivalY);
    }

    /**
     * Changes only the detached persistent faction affiliation of an in-transit fleet.
     *
     * <p>The fleet remains detached under the same FleetId, origin/destination and jump FSM state.
     * No local entity is materialized and cargo, wallet, transform and all other payload fields are
     * preserved. The stable faction ID is resolved through the unified Stage-17 directory.</p>
     *
     * @param fleetId stable world FleetId currently in transit
     * @param stableFactionId authored or world-defined stable faction ID
     * @return true when the detached payload faction changed; false when already affiliated
     * @throws IllegalArgumentException if faction identity is unknown
     * @throws IllegalStateException if fleet is not in transit
     */
    public boolean affiliateTransitFleetFaction(FleetId fleetId, String stableFactionId) {
        String factionId = normalizedFactionId(stableFactionId);
        int runtimeFactionId = factionIdentityResolver.runtimeId(factionId).orElseThrow(
                () -> new IllegalArgumentException(
                        "Unknown faction for transit affiliation: " + factionId));
        return fleetWorldService.affiliateTransitFaction(
                Objects.requireNonNull(fleetId, "FleetId transit affiliation not set"),
                runtimeFactionId);
    }

    /**
     * Передаёт fleet из local SimulationSession во world-owned transit state.
     *
     * <p>Stage 10A выполняет только identity/location handoff без travel clock. Stage 10B will
     * schedule this boundary through the authoritative jump-transit FSM and deterministic travel
     * duration.</p>
     *
     * @param fleetId stable fleet identity
     * @param destinationSystemId destination StarSystem
     * @return persistent transit placement containing the detached physical fleet snapshot
     */
    public FleetPlacementState beginFleetTransfer(FleetId fleetId, StarSystemId destinationSystemId) {
        return fleetWorldService.beginTransfer(fleetId, destinationSystemId);
    }

    /**
     * Материализует transit fleet в destination local SimulationSession.
     *
     * @param fleetId stable fleet identity
     * @param arrivalX finite destination X coordinate
     * @param arrivalY finite destination Y coordinate
     * @return local placement with a freshly allocated destination EntityId
     */
    public FleetPlacementState completeFleetTransfer(FleetId fleetId, float arrivalX, float arrivalY) {
        return fleetWorldService.completeTransfer(fleetId, arrivalX, arrivalY);
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
     * Экономически разрешает destruction policy и затем структурно удаляет persistent Entity.
     *
     * @param systemId target StarSystem
     * @param entityId destroyed persistent local Entity ID
     * @param policy explicit resource/money fate
     * @return immutable accounting/result diagnostics
     */
    public DestructionResult destroyEntity(
            StarSystemId systemId, EntityId entityId, DestructionPolicy policy) {
        Optional<FleetId> fleetId = fleetWorldService.findByLocal(systemId, entityId);
        DestructionResult result = destructionService.destroy(systemId, entityId, policy);
        fleetId.ifPresent(fleetJumpService::remove);
        if (fleetId.isPresent() && !fleetWorldService.unregisterLocal(systemId, entityId)) {
            throw new IllegalStateException(
                    "Destroyed fleet lost world mapping before unregister: " + fleetId.orElseThrow());
        }
        return result;
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
        StarSystemId checkedSystemId = Objects.requireNonNull(systemId, "StarSystemId lifecycle не задан");
        SimulationSession session = requireLifecycleSession(checkedSystemId);
        Entity checkedEntity = Objects.requireNonNull(entity, "Создаваемая Entity не задана");
        IdentityComponent identity = checkedEntity.getComponent(IdentityComponent.class);
        boolean fleet = identity != null && identity.kind == IdentityComponent.Kind.FLEET;
        EntityId id = session.createEntity(checkedEntity);
        if (!fleet) {
            return id;
        }
        try {
            fleetWorldService.registerLocal(checkedSystemId, id);
            return id;
        } catch (RuntimeException exception) {
            if (!session.removeEntity(id)) {
                exception.addSuppressed(new IllegalStateException(
                        "Fleet registration rollback could not remove local entity: " + id));
            }
            throw exception;
        }
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
        StarSystemId checkedSystemId = Objects.requireNonNull(systemId, "StarSystemId lifecycle не задан");
        SimulationSession session = requireLifecycleSession(checkedSystemId);
        Optional<FleetId> fleetId = fleetWorldService.findByLocal(checkedSystemId, entityId);
        boolean removed = session.removeEntity(entityId);
        if (removed && fleetId.isPresent()) {
            FleetId removedFleetId = fleetId.orElseThrow();
            fleetJumpService.remove(removedFleetId);
            if (!fleetWorldService.unregisterLocal(checkedSystemId, entityId)) {
                throw new IllegalStateException(
                        "Removed fleet lost world mapping before unregister: " + removedFleetId);
            }
        }
        return removed;
    }

    /**
     * Creates the canonical Stage-10C galactic trade planner for this world.
     *
     * @param scoringMode route ranking policy
     * @return planner configured with world content and strategic cost policy
     */
    public TradeRoutePlanner createGalacticTradeRoutePlanner(TradeRoutePlanner.ScoringMode scoringMode) {
        return new TradeRoutePlanner(
                contentCatalog,
                Objects.requireNonNull(scoringMode, "Trade route scoring mode не задан"),
                liveTradeRouteCostModel());
    }

    /**
     * Creates the ordinary TradeController configured with this world's live customs settlement policy.
     *
     * @param session local session whose ledger records the physical transaction
     * @return controller sharing the same tariff law as autonomous trade AI
     */
    public TradeController createTradeController(SimulationSession session) {
        SimulationSession checked = Objects.requireNonNull(session, "SimulationSession not set");
        if (!sessionsById.containsValue(checked)) {
            throw new IllegalArgumentException("SimulationSession does not belong to this world");
        }
        return new TradeController(checked.getLedger(), liveTradeTransactionPolicy());
    }

    /** @return path planner whose edge timing matches Stage-10B jump execution */
    public GalacticPathPlanner createGalacticPathPlanner() {
        float fixedStep = sessionsById.get(activeSystemId).getClock().getFixedStepSeconds();
        return new GalacticPathPlanner(topology, JumpTransitTiming.DEFAULT, fixedStep);
    }

    /** @return immutable Galaxy topology этого runtime world */
    public GalaxyTopology getTopology() {
        return topology;
    }

    /** @return ID системы полного local tick */
    public StarSystemId getActiveSystemId() {
        return activeSystemId;
    }

    /**
     * Переключает StarSystem полного local tick без изменения transit state других fleets.
     *
     * <p>Target session сначала детерминированно догоняется до текущего authoritative world tick;
     * после этого прежняя active system становится обычной remote session с тем же tick.</p>
     *
     * @param systemId target StarSystem
     */
    public void activateSystem(StarSystemId systemId) {
        StarSystemId target = Objects.requireNonNull(systemId, "Target active StarSystem не задан");
        if (!sessionsById.containsKey(target)) {
            throw new IllegalArgumentException("Неизвестная StarSystem: " + target);
        }
        if (target.equals(activeSystemId)) {
            return;
        }
        long worldTick = sessionsById.get(activeSystemId).getClock().getTick();
        synchronizeSystemToTick(target, worldTick);
        activeSystemId = target;
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

    private void advanceJumpTransitionsAtTick(long worldTick) {
        for (FleetJumpState jump : fleetJumpService.snapshots()) {
            if (jump.phaseEndsTick() > worldTick) {
                continue;
            }
            switch (jump.phase()) {
                case JUMP_PENDING ->
                        synchronizeSystemToTick(jump.originSystemId(), jump.phaseEndsTick());
                case IN_TRANSIT ->
                        synchronizeSystemToTick(jump.destinationSystemId(), jump.phaseEndsTick());
                case MOVING_TO_JUMP, ARRIVING -> {
                    // No cross-session physical handoff occurs at these boundaries.
                }
            }
        }
        fleetJumpService.advance(worldTick);
    }

    private SimulationSession requireLifecycleSession(StarSystemId systemId) {
        StarSystemId checked = Objects.requireNonNull(systemId, "StarSystemId lifecycle не задан");
        SimulationSession session = sessionsById.get(checked);
        if (session == null) {
            throw new IllegalArgumentException("Неизвестная StarSystem: " + checked);
        }
        return session;
    }

    private void synchronizeSystemToTick(StarSystemId systemId, long worldTick) {
        SimulationSession session = requireLifecycleSession(systemId);
        long tick = session.getClock().getTick();
        if (tick > worldTick) {
            throw new IllegalStateException(
                    "StarSystem clock опережает authoritative world tick: " + systemId);
        }
        while (tick < worldTick) {
            long remaining = worldTick - tick;
            int step = (int) Math.min((long) strategicStepTicks, remaining);
            session.advanceStrategicSteps(step);
            totalStrategicUpdatesExecuted = safeAdd(totalStrategicUpdatesExecuted, 1L);
            tick = session.getClock().getTick();
        }
    }

    private FactionEconomicAccount requireFactionAccount(String factionId) {
        FactionEconomicAccount account = factionAccountsById.get(factionId);
        if (account == null) {
            throw new IllegalArgumentException("Faction не имеет economic state: " + factionId);
        }
        return account;
    }

    private int requireRuntimeFactionId(String factionId) {
        return factionIdentityResolver.runtimeId(factionId).orElseThrow(
                () -> new IllegalStateException("Economic faction отсутствует в faction identity directory: "
                        + factionId));
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
    private TradeTransactionPolicy liveTradeTransactionPolicy() {
        return new WorldTradeTransactionPolicy(
                factionIdentityResolver,
                factionAccountsById,
                diplomacyRuntime::snapshots,
                this::getAuthoritativeWorldTick);
    }

    private TradeRouteCostModel liveTradeRouteCostModel() {
        return new WorldTradeRouteCostModel(
                factionIdentityResolver,
                territorialControlRuntime::snapshots,
                diplomacyRuntime::snapshots,
                this::getAuthoritativeWorldTick);
    }

    private void configureTradePolicies() {
        TradeTransactionPolicy transactionPolicy = liveTradeTransactionPolicy();
        TradeRouteCostModel costModel = liveTradeRouteCostModel();
        for (SimulationSession session : sessionsById.values()) {
            TradeAISystem tradeAI = session.getEngine().getSystem(TradeAISystem.class);
            if (tradeAI != null) {
                tradeAI.configureTradePolicies(transactionPolicy, costModel);
            }
        }
    }

    private void refreshFactionMarketAccess() {
        long worldTick = getAuthoritativeWorldTick();
        for (SimulationSession session : sessionsById.values()) {
            FactionPolicyRuntime.install(
                    session,
                    factionIdentityResolver,
                    territorialControlRuntime.snapshots(),
                    diplomacyRuntime.snapshots(),
                    worldTick);
        }
        diplomacyRuntime.noteMarketAccessPolicyRefreshed(worldTick);
    }

}
