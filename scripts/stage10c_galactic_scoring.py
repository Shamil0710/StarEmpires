from pathlib import Path
p=Path("src/main/java/com/spacesim/trade/TradeRoutePlanner.java")
t=p.read_text()
def r(a,b,label):
    global t
    if t.count(a)!=1: raise SystemExit(f"{label}: marker mismatch {t.count(a)}")
    t=t.replace(a,b,1)

r("import java.util.Objects;\nimport java.util.Optional;",
  "import java.util.List;\nimport java.util.Objects;\nimport java.util.Optional;", "imports")

marker="    private int calculateAmount(\n"
method="""    /**
     * Scores a bounded set of cross-system opportunities with the same economy and cost model as
     * local routes. Candidate discovery remains outside this method and is bounded by Stage 10D.
     *
     * @param fleet immutable fleet planning profile
     * @param opportunities bounded cross-system supplier-consumer candidates
     * @return best economically valid galactic route or empty
     */
    public Optional<GalacticTradeRoute> findBestGalacticRoute(
            FleetTradeProfile fleet,
            List<GalacticTradeOpportunity> opportunities) {
        Objects.requireNonNull(fleet, "FleetTradeProfile не задан");
        Objects.requireNonNull(opportunities, "Galactic opportunities не заданы");
        if (fleet.routeCargoCapacity() <= 0) {
            return Optional.empty();
        }

        GalacticTradeRoute best = null;
        for (GalacticTradeOpportunity opportunity : opportunities) {
            Objects.requireNonNull(opportunity, "Galactic opportunity не задана");
            ContentCatalog.ItemDefinition item = findItem(opportunity.itemId());
            if (item == null || !fleet.canPurchase(item)) {
                continue;
            }
            MarketDirectory.StationMarket supplier = opportunity.supplier().market();
            MarketDirectory.StationMarket consumer = opportunity.consumer().market();
            if (!supplier.canTrade(fleet.factionId()) || !consumer.canTrade(fleet.factionId())) {
                continue;
            }
            float purchasePrice = effectiveSellPrice(supplier, opportunity.itemId(), fleet);
            float salePrice = effectiveBuyPrice(consumer, opportunity.itemId(), fleet);
            if (!isPositiveFinite(purchasePrice)
                    || !isPositiveFinite(salePrice)
                    || salePrice <= purchasePrice) {
                continue;
            }
            int amount = calculateAmount(
                    fleet, supplier, consumer, opportunity.itemId(), purchasePrice, salePrice);
            if (amount <= 0) {
                continue;
            }
            long purchaseCost = safeTradeValue(purchasePrice, amount);
            long saleRevenue = safeTradeValue(salePrice, amount);
            if (purchaseCost <= 0L || saleRevenue <= purchaseCost) {
                continue;
            }
            long grossProfit = saleRevenue - purchaseCost;
            double seconds = opportunity.totalExpectedSeconds();
            long routeCost = estimateRouteCost(
                    fleet,
                    new TradeRouteCostModel.Context(
                            supplier.id(),
                            consumer.id(),
                            supplier.factionId(),
                            consumer.factionId(),
                            opportunity.itemId(),
                            amount,
                            purchaseCost,
                            saleRevenue,
                            opportunity.localTravelDistance(),
                            seconds,
                            opportunity.supplier().systemId(),
                            opportunity.consumer().systemId(),
                            opportunity.jumpPath(),
                            opportunity.routeRiskBasisPoints()));
            if (routeCost >= grossProfit) {
                continue;
            }
            GalacticTradeRoute candidate = new GalacticTradeRoute(
                    opportunity.supplier().systemId(),
                    supplier.id(),
                    opportunity.consumer().systemId(),
                    consumer.id(),
                    opportunity.itemId(),
                    amount,
                    purchaseCost,
                    saleRevenue,
                    grossProfit,
                    routeCost,
                    grossProfit - routeCost,
                    opportunity.localTravelDistance(),
                    opportunity.jumpPath().strategicDistance(),
                    seconds,
                    opportunity.routeRiskBasisPoints(),
                    opportunity.jumpPath());
            if (isBetter(candidate, best)) {
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    private ContentCatalog.ItemDefinition findItem(int itemId) {
        for (ContentCatalog.ItemDefinition item : contentCatalog.getItems()) {
            if (item.runtimeId() == itemId) {
                return item;
            }
        }
        return null;
    }

"""
r(marker, method+marker, "galactic method")

marker2="    private boolean isBetter(TradeRoute candidate, TradeRoute current) {"
helper="""    private boolean isBetter(GalacticTradeRoute candidate, GalacticTradeRoute current) {
        if (current == null) {
            return true;
        }
        int primary = scoringMode == ScoringMode.GROSS_PROFIT
                ? Long.compare(candidate.netProfitMilliCredits(), current.netProfitMilliCredits())
                : Double.compare(candidate.netProfitPerSecond(), current.netProfitPerSecond());
        if (primary != 0) {
            return primary > 0;
        }
        int netTie = Long.compare(candidate.netProfitMilliCredits(), current.netProfitMilliCredits());
        if (netTie != 0) {
            return netTie > 0;
        }
        int timeTie = Double.compare(candidate.expectedDurationSeconds(), current.expectedDurationSeconds());
        if (timeTie != 0) {
            return timeTie < 0;
        }
        int itemTie = Integer.compare(candidate.itemId(), current.itemId());
        if (itemTie != 0) {
            return itemTie < 0;
        }
        int buySystemTie = candidate.buySystemId().compareTo(current.buySystemId());
        if (buySystemTie != 0) {
            return buySystemTie < 0;
        }
        int buyStationTie = candidate.buyStationId().compareTo(current.buyStationId());
        if (buyStationTie != 0) {
            return buyStationTie < 0;
        }
        int sellSystemTie = candidate.sellSystemId().compareTo(current.sellSystemId());
        if (sellSystemTie != 0) {
            return sellSystemTie < 0;
        }
        return candidate.sellStationId().compareTo(current.sellStationId()) < 0;
    }

"""
r(marker2, helper+marker2, "galactic comparator")
p.write_text(t)
