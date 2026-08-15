from pathlib import Path


def replace_once(path_str: str, old: str, new: str, label: str) -> None:
    path = Path(path_str)
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path_str} {label}: expected one anchor, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# TradeController: physical three-wallet customs settlement.
path = "src/main/java/com/spacesim/controllers/TradeController.java"
replace_once(path,
    "    private final EconomicLedger ledger;\n",
    "    private final EconomicLedger ledger;\n    private final TradeTransactionPolicy transactionPolicy;\n",
    "policy field")
replace_once(path,
    "    public TradeController() {\n        this(new EconomicLedger());\n    }",
    "    public TradeController() {\n        this(new EconomicLedger(), TradeTransactionPolicy.none());\n    }",
    "default constructor")
replace_once(path,
    "    public TradeController(EconomicLedger ledger) {\n        this.ledger = Objects.requireNonNull(ledger, \"EconomicLedger не задан\");\n    }",
    """    public TradeController(EconomicLedger ledger) {
        this(ledger, TradeTransactionPolicy.none());
    }

    /**
     * Creates a controller with an explicit non-mutating extra settlement policy.
     *
     * @param ledger shared economic ledger
     * @param transactionPolicy customs/settlement quote policy
     */
    public TradeController(EconomicLedger ledger, TradeTransactionPolicy transactionPolicy) {
        this.ledger = Objects.requireNonNull(ledger, "EconomicLedger не задан");
        this.transactionPolicy = Objects.requireNonNull(transactionPolicy, "TradeTransactionPolicy not set");
    }""",
    "policy constructor")
replace_once(path,
    """        float unitPrice = getEffectiveSellPrice(station, itemId, buyerReputation);
        long cost = safeTradeValue(unitPrice, amount);

        if (cost <= 0L
                || stationInventory.stock[itemId] < amount
                || getFreeCapacity(buyerInventory) < amount
                || !buyerWallet.canDebit(cost)
                || !stationWallet.canCredit(cost)
                || buyerInventory.stock[itemId] > Integer.MAX_VALUE - amount) {
            return false;
        }

        if (!buyerWallet.transferTo(stationWallet, cost)) {
            return false;
        }
        stationInventory.stock[itemId] -= amount;
        buyerInventory.stock[itemId] += amount;
        mm.get(station).isDirty = true;
        increaseReputation(station, buyerReputation);
        ledger.recordTrade(entityName(buyer), entityName(station), itemId, amount, cost);
        return true;""",
    """        float unitPrice = getEffectiveSellPrice(station, itemId, buyerReputation);
        long cost = safeTradeValue(unitPrice, amount);
        if (cost <= 0L) {
            return false;
        }
        TradeTransactionPolicy.Charge charge = transactionPolicy.quote(
                station, buyer, TradeTransactionPolicy.Direction.BUY_FROM_STATION, cost);
        long duty = charge.amountMilliCredits();
        long totalDebit = safeAdd(cost, duty);
        WalletComponent collector = charge.collectorWallet();

        if (totalDebit <= 0L
                || stationInventory.stock[itemId] < amount
                || getFreeCapacity(buyerInventory) < amount
                || !buyerWallet.canDebit(totalDebit)
                || !stationWallet.canCredit(cost)
                || (duty > 0L && (collector == buyerWallet
                || collector == stationWallet
                || !collector.canCredit(duty)))
                || buyerInventory.stock[itemId] > Integer.MAX_VALUE - amount) {
            return false;
        }

        if (!buyerWallet.transferTo(stationWallet, cost)) {
            return false;
        }
        if (duty > 0L && !buyerWallet.transferTo(collector, duty)) {
            stationWallet.transferTo(buyerWallet, cost);
            return false;
        }
        stationInventory.stock[itemId] -= amount;
        buyerInventory.stock[itemId] += amount;
        mm.get(station).isDirty = true;
        increaseReputation(station, buyerReputation);
        String buyerName = entityName(buyer);
        ledger.recordTrade(buyerName, entityName(station), itemId, amount, cost);
        if (duty > 0L) {
            ledger.recordMoneyTransfer(buyerName, charge.collectorLedgerName(), duty, charge.reason());
        }
        return true;""",
    "buy settlement")
replace_once(path,
    """        float unitPrice = getEffectiveBuyPrice(station, itemId, sellerReputation);
        long revenue = safeTradeValue(unitPrice, amount);

        if (revenue <= 0L
                || sellerInventory.stock[itemId] < amount
                || getFreeCapacity(stationInventory) < amount
                || !stationWallet.canDebit(revenue)
                || !sellerWallet.canCredit(revenue)
                || stationInventory.stock[itemId] > Integer.MAX_VALUE - amount) {
            return false;
        }

        if (!stationWallet.transferTo(sellerWallet, revenue)) {
            return false;
        }
        sellerInventory.stock[itemId] -= amount;
        stationInventory.stock[itemId] += amount;
        mm.get(station).isDirty = true;
        increaseReputation(station, sellerReputation);
        ledger.recordTrade(entityName(station), entityName(seller), itemId, amount, revenue);
        return true;""",
    """        float unitPrice = getEffectiveBuyPrice(station, itemId, sellerReputation);
        long revenue = safeTradeValue(unitPrice, amount);
        if (revenue <= 0L) {
            return false;
        }
        TradeTransactionPolicy.Charge charge = transactionPolicy.quote(
                station, seller, TradeTransactionPolicy.Direction.SELL_TO_STATION, revenue);
        long duty = charge.amountMilliCredits();
        long netRevenue = revenue - duty;
        WalletComponent collector = charge.collectorWallet();

        if (netRevenue <= 0L
                || sellerInventory.stock[itemId] < amount
                || getFreeCapacity(stationInventory) < amount
                || !stationWallet.canDebit(revenue)
                || !sellerWallet.canCredit(netRevenue)
                || (duty > 0L && (collector == sellerWallet
                || collector == stationWallet
                || !collector.canCredit(duty)))
                || stationInventory.stock[itemId] > Integer.MAX_VALUE - amount) {
            return false;
        }

        if (!stationWallet.transferTo(sellerWallet, netRevenue)) {
            return false;
        }
        if (duty > 0L && !stationWallet.transferTo(collector, duty)) {
            sellerWallet.transferTo(stationWallet, netRevenue);
            return false;
        }
        sellerInventory.stock[itemId] -= amount;
        stationInventory.stock[itemId] += amount;
        mm.get(station).isDirty = true;
        increaseReputation(station, sellerReputation);
        String stationName = entityName(station);
        String sellerName = entityName(seller);
        ledger.recordTrade(stationName, sellerName, itemId, amount, netRevenue);
        if (duty > 0L) {
            ledger.recordMoneyTransfer(stationName, charge.collectorLedgerName(), duty, charge.reason());
        }
        return true;""",
    "sell settlement")
replace_once(path,
    "    private long safeTradeValue(float unitPrice, int amount) {\n",
    """    private long safeAdd(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException exception) {
            return -1L;
        }
    }

    private long safeTradeValue(float unitPrice, int amount) {
""",
    "safe add")

# TradeAI: same policy for planning and execution.
path = "src/main/java/com/spacesim/systems/TradeAISystem.java"
replace_once(path, "import com.spacesim.controllers.TradeController;\n",
             "import com.spacesim.controllers.TradeController;\nimport com.spacesim.controllers.TradeTransactionPolicy;\n",
             "transaction import")
replace_once(path, "import com.spacesim.trade.TradeRoute;\n",
             "import com.spacesim.trade.TradeRoute;\nimport com.spacesim.trade.TradeRouteCostModel;\n",
             "cost import")
replace_once(path, "    private final TradeController tradeController;\n",
             "    private TradeController tradeController;\n", "controller mutable")
replace_once(path, "    private final TradeRoutePlanner routePlanner;\n",
             "    private TradeRoutePlanner routePlanner;\n    private final TradeRoutePlanner.ScoringMode scoringMode;\n",
             "planner mutable")
replace_once(path,
    """        this.tradeController = new TradeController(
                Objects.requireNonNull(ledger, "EconomicLedger не задан"));
        this.registry = Objects.requireNonNull(registry, "EntityRegistry не задан");
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        this.marketDirectory = new MarketDirectory(this.contentCatalog);
        this.routePlanner = new TradeRoutePlanner(
                this.contentCatalog,
                Objects.requireNonNull(scoringMode, "ScoringMode не задан"));""",
    """        this.tradeController = new TradeController(
                Objects.requireNonNull(ledger, "EconomicLedger не задан"));
        this.registry = Objects.requireNonNull(registry, "EntityRegistry не задан");
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        this.marketDirectory = new MarketDirectory(this.contentCatalog);
        this.scoringMode = Objects.requireNonNull(scoringMode, "ScoringMode не задан");
        this.routePlanner = new TradeRoutePlanner(this.contentCatalog, this.scoringMode);""",
    "scoring constructor")
replace_once(path,
    "    /** @return ledger, в который система записывает успешные сделки */\n",
    """    /**
     * Installs world-backed transaction and route-cost policy after this local session joins a world.
     *
     * @param transactionPolicy authoritative transaction settlement policy
     * @param costModel matching pre-route external cost model
     */
    public void configureTradePolicies(
            TradeTransactionPolicy transactionPolicy,
            TradeRouteCostModel costModel) {
        tradeController = new TradeController(
                tradeController.getLedger(),
                Objects.requireNonNull(transactionPolicy, "TradeTransactionPolicy not set"));
        routePlanner = new TradeRoutePlanner(
                contentCatalog,
                scoringMode,
                Objects.requireNonNull(costModel, "TradeRouteCostModel not set"));
        marketDirectory.invalidate();
    }

    /** @return ledger, в который система записывает успешные сделки */
""",
    "configure method")

# WorldSimulation: install live policies and expose the same controller to player adapters.
path = "src/main/java/com/spacesim/world/WorldSimulation.java"
replace_once(path, "import com.spacesim.content.ContentCatalogLoader;\n",
             "import com.spacesim.content.ContentCatalogLoader;\nimport com.spacesim.controllers.TradeController;\nimport com.spacesim.controllers.TradeTransactionPolicy;\n",
             "world controller imports")
replace_once(path, "import com.spacesim.trade.TradeRoutePlanner;\n",
             "import com.spacesim.trade.TradeRouteCostModel;\nimport com.spacesim.trade.TradeRoutePlanner;\nimport com.spacesim.systems.TradeAISystem;\n",
             "world trade imports")
replace_once(path,
    "        this.remoteUpdateBudgetPerFrame = remoteUpdateBudgetPerFrame;\n        refreshFactionMarketAccess();",
    "        this.remoteUpdateBudgetPerFrame = remoteUpdateBudgetPerFrame;\n        configureTradePolicies();\n        refreshFactionMarketAccess();",
    "world constructor configure")
replace_once(path,
    """    public TradeRoutePlanner createGalacticTradeRoutePlanner(TradeRoutePlanner.ScoringMode scoringMode) {
        return new TradeRoutePlanner(
                contentCatalog,
                Objects.requireNonNull(scoringMode, "Trade route scoring mode не задан"),
                new WorldTradeRouteCostModel(
                        factionIdentityResolver, territorialControlRuntime.snapshots()));
    }
""",
    """    public TradeRoutePlanner createGalacticTradeRoutePlanner(TradeRoutePlanner.ScoringMode scoringMode) {
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
""",
    "world planner/controller")
replace_once(path,
    "    private void refreshFactionMarketAccess() {\n",
    """    private TradeTransactionPolicy liveTradeTransactionPolicy() {
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
""",
    "world live policy helpers")

# Player adapters: unified dynamic faction resolution and same tariff-enabled TradeController.
for path in [
    "src/main/java/com/spacesim/player/PlayerMarketService.java",
    "src/main/java/com/spacesim/player/PlayerFleetEconomyService.java",
]:
    replace_once(path, "import com.spacesim.content.ContentCatalog;\n",
                 "import com.spacesim.content.ContentCatalog;\nimport com.spacesim.constants.Constants;\n",
                 "player constants import")

path = "src/main/java/com/spacesim/player/PlayerMarketService.java"
replace_once(path,
    """        if (player.factionContentId() != null) {
            ContentCatalog.FactionDefinition faction = content.findFaction(player.factionContentId());
            if (faction == null) {
                return null;
            }
            proxy.add(new FactionComponent(faction.runtimeId()));
        }
        ReputationComponent proxyReputation = new ReputationComponent();
        for (PlayerReputationState reputation : player.reputations()) {
            ContentCatalog.FactionDefinition faction = content.findFaction(reputation.factionContentId());
            if (faction == null) {
                return null;
            }
            proxyReputation.addReputation(faction.runtimeId(), reputation.value());
        }
        proxy.add(proxyReputation);
        return new TradeContext(
                player, session, ship, station, shipInventory, stationInventory, market,
                new TradeController(session.getLedger()), proxy, proxyWallet, proxyReputation);""",
    """        if (player.factionContentId() != null) {
            int runtimeFactionId = runtime.world().findFactionRuntimeId(player.factionContentId()).orElse(-1);
            if (runtimeFactionId < 0) {
                return null;
            }
            proxy.add(new FactionComponent(runtimeFactionId));
        }
        ReputationComponent proxyReputation = new ReputationComponent();
        for (PlayerReputationState reputation : player.reputations()) {
            int runtimeFactionId = runtime.world().findFactionRuntimeId(reputation.factionContentId()).orElse(-1);
            if (runtimeFactionId < 0) {
                return null;
            }
            proxyReputation.addReputation(runtimeFactionId, reputation.value());
        }
        proxy.add(proxyReputation);
        return new TradeContext(
                player, session, ship, station, shipInventory, stationInventory, market,
                runtime.world().createTradeController(session), proxy, proxyWallet, proxyReputation);""",
    "manual context")
replace_once(path,
    """    private List<PlayerReputationState> snapshotReputation(ReputationComponent reputation) {
        List<PlayerReputationState> result = new ArrayList<>();
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            float value = reputation.getReputation(faction.runtimeId());
            if (value != 0f) {
                result.add(new PlayerReputationState(faction.id(), value));
            }
        }
        return result;
    }""",
    """    private List<PlayerReputationState> snapshotReputation(ReputationComponent reputation) {
        List<PlayerReputationState> result = new ArrayList<>();
        for (int runtimeId = 0; runtimeId < Constants.FACTION_RUNTIME_CAPACITY; runtimeId++) {
            String stableId = runtime.world().findFactionStableId(runtimeId).orElse(null);
            float value = reputation.getReputation(runtimeId);
            if (stableId != null && value != 0f) {
                result.add(new PlayerReputationState(stableId, value));
            }
        }
        return result;
    }""",
    "manual reputation snapshot")

path = "src/main/java/com/spacesim/player/PlayerFleetEconomyService.java"
replace_once(path,
    """        if (player.factionContentId() != null) {
            ContentCatalog.FactionDefinition faction = content.findFaction(player.factionContentId());
            if (faction == null) {
                return null;
            }
            proxy.add(new FactionComponent(faction.runtimeId()));
        }
        ReputationComponent proxyReputation = new ReputationComponent();
        for (PlayerReputationState reputation : player.reputations()) {
            ContentCatalog.FactionDefinition faction = content.findFaction(reputation.factionContentId());
            if (faction == null) {
                return null;
            }
            proxyReputation.addReputation(faction.runtimeId(), reputation.value());
        }
        proxy.add(proxyReputation);
        return new Context(
                player,
                physical.shipRole(),
                physical.station(),
                physical.shipInventory(),
                physical.stationInventory(),
                physical.stationWallet(),
                item,
                new TradeController(physical.session().getLedger()),
                proxy,
                proxyWallet,
                proxyReputation);""",
    """        if (player.factionContentId() != null) {
            int runtimeFactionId = runtime.world().findFactionRuntimeId(player.factionContentId()).orElse(-1);
            if (runtimeFactionId < 0) {
                return null;
            }
            proxy.add(new FactionComponent(runtimeFactionId));
        }
        ReputationComponent proxyReputation = new ReputationComponent();
        for (PlayerReputationState reputation : player.reputations()) {
            int runtimeFactionId = runtime.world().findFactionRuntimeId(reputation.factionContentId()).orElse(-1);
            if (runtimeFactionId < 0) {
                return null;
            }
            proxyReputation.addReputation(runtimeFactionId, reputation.value());
        }
        proxy.add(proxyReputation);
        return new Context(
                player,
                physical.shipRole(),
                physical.station(),
                physical.shipInventory(),
                physical.stationInventory(),
                physical.stationWallet(),
                item,
                runtime.world().createTradeController(physical.session()),
                proxy,
                proxyWallet,
                proxyReputation);""",
    "delegated context")
replace_once(path,
    """    private List<PlayerReputationState> snapshotReputation(ReputationComponent reputation) {
        List<PlayerReputationState> result = new ArrayList<>();
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            float value = reputation.getReputation(faction.runtimeId());
            if (value != 0f) {
                result.add(new PlayerReputationState(faction.id(), value));
            }
        }
        return result;
    }""",
    """    private List<PlayerReputationState> snapshotReputation(ReputationComponent reputation) {
        List<PlayerReputationState> result = new ArrayList<>();
        for (int runtimeId = 0; runtimeId < Constants.FACTION_RUNTIME_CAPACITY; runtimeId++) {
            String stableId = runtime.world().findFactionStableId(runtimeId).orElse(null);
            float value = reputation.getReputation(runtimeId);
            if (stableId != null && value != 0f) {
                result.add(new PlayerReputationState(stableId, value));
            }
        }
        return result;
    }""",
    "delegated reputation snapshot")
