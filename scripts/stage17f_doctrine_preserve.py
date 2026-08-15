from pathlib import Path


def replace_once(path, old, new):
    target = Path(path)
    text = target.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one anchor in {path}, found {count}")
    target.write_text(text.replace(old, new, 1))


# Every production rebuild of a strategic state must preserve the already-installed doctrine.
for path in [
    "src/main/java/com/spacesim/world/TerritorialControlRuntime.java",
    "src/main/java/com/spacesim/persistence/WorldStrategicGrowthBinary.java",
    "src/main/java/com/spacesim/persistence/WorldTerritoryBinary.java",
    "src/main/java/com/spacesim/world/StrategicGrowthPlanService.java",
]:
    replace_once(
        path,
        "                state.constructionRightsGranted());",
        "                state.constructionRightsGranted(),\n                state.doctrine());",
    )

# Restore source compatibility only after production copies are doctrine-safe.
path = "src/main/java/com/spacesim/world/FactionStrategicState.java"
anchor = """    /**
     * Валидирует state и нормализует canonical ordering.
"""
constructor = """    /**
     * Source-compatible pre-Stage-17F canonical constructor.
     *
     * <p>Callers using the former complete strategic shape migrate to a neutral institutional
     * doctrine. Runtime copy paths must use the canonical constructor and explicitly preserve the
     * existing doctrine.</p>
     *
     * @param factionContentId stable owner faction content ID
     * @param minimumMarketAccessRelation market access relation threshold
     * @param relations directed relations
     * @param controlledSystems controlled systems
     * @param stationTaxBasisPoints own-station tax rate
     * @param foreignTerritoryTariffBasisPoints foreign-market territorial levy
     * @param stockPolicies base stock floors
     * @param productionPolicies production policies
     * @param strategicGoals active strategic goals
     * @param territorialClaims political claim states
     * @param territorialControlStates maintenance state for controlled systems
     * @param territorialRecognitions directed territorial recognition states
     * @param constructionRightsGranted foreign construction concessions
     */
    public FactionStrategicState(
            String factionContentId,
            int minimumMarketAccessRelation,
            List<FactionRelationState> relations,
            List<StarSystemId> controlledSystems,
            int stationTaxBasisPoints,
            int foreignTerritoryTariffBasisPoints,
            List<FactionStockPolicyState> stockPolicies,
            List<FactionProductionPolicyState> productionPolicies,
            List<FactionStrategicGoalState> strategicGoals,
            List<TerritorialClaimState> territorialClaims,
            List<TerritorialControlState> territorialControlStates,
            List<TerritorialRecognitionState> territorialRecognitions,
            List<TerritorialConstructionRightState> constructionRightsGranted) {
        this(
                factionContentId,
                minimumMarketAccessRelation,
                relations,
                controlledSystems,
                stationTaxBasisPoints,
                foreignTerritoryTariffBasisPoints,
                stockPolicies,
                productionPolicies,
                strategicGoals,
                territorialClaims,
                territorialControlStates,
                territorialRecognitions,
                constructionRightsGranted,
                FactionDoctrineState.neutral());
    }

""" + anchor
replace_once(path, anchor, constructor)
