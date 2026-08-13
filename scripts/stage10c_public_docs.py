from pathlib import Path

def patch(path_name, old, new):
    p=Path(path_name); t=p.read_text()
    if t.count(old)!=1: raise SystemExit(f"marker mismatch: {path_name}")
    p.write_text(t.replace(old,new,1))

patch("src/main/java/com/spacesim/trade/GalacticTradeOpportunity.java",
"    /** Validates one immutable galactic candidate. */\n    public GalacticTradeOpportunity {",
"""    /**
     * @param supplier supplier market in the fleet's current system
     * @param consumer remote consumer market
     * @param itemId runtime item ID
     * @param jumpPath deterministic supplier-to-consumer jump path
     * @param localTravelDistance explicit in-system travel distance estimate
     * @param localTravelSeconds explicit in-system travel time estimate
     * @param routeRiskBasisPoints expected route risk in basis points
     */
    public GalacticTradeOpportunity {""")

patch("src/main/java/com/spacesim/trade/GalacticTradeRoute.java",
"    /** Validates the immutable galactic route result. */\n    public GalacticTradeRoute {",
"""    /**
     * @param buySystemId supplier StarSystem
     * @param buyStationId supplier local EntityId
     * @param sellSystemId consumer StarSystem
     * @param sellStationId consumer local EntityId
     * @param itemId runtime item ID
     * @param amount planned cargo amount
     * @param purchaseCostMilliCredits expected purchase cost
     * @param saleRevenueMilliCredits expected sale revenue
     * @param grossProfitMilliCredits revenue minus purchase cost
     * @param routeCostMilliCredits external route cost
     * @param netProfitMilliCredits gross profit minus route cost
     * @param localTravelDistance explicit in-system distance estimate
     * @param strategicJumpDistance topology distance of jump path
     * @param expectedDurationSeconds local plus jump time
     * @param routeRiskBasisPoints route risk in basis points
     * @param jumpPath deterministic supplier-to-consumer path
     */
    public GalacticTradeRoute {""")

patch("src/main/java/com/spacesim/trade/TradeRouteCostModel.java",
"        /** Validates local or galactic cost context. */\n        public Context {",
"""        /**
         * @param buyStationId supplier ID or {@code null} for existing cargo
         * @param sellStationId consumer ID
         * @param buyFactionId supplier runtime faction ID or {@code -1}
         * @param sellFactionId consumer runtime faction ID or {@code -1}
         * @param itemId runtime item ID
         * @param amount cargo amount
         * @param purchaseCostMilliCredits purchase cost
         * @param saleRevenueMilliCredits sale revenue
         * @param travelDistance explicit local distance estimate
         * @param travelSeconds full expected movement duration
         * @param buySystemId supplier system for galactic route or {@code null}
         * @param sellSystemId consumer system for galactic route or {@code null}
         * @param jumpPath galactic path or {@code null}
         * @param routeRiskBasisPoints expected route risk in basis points
         */
        public Context {""")
