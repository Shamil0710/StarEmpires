from pathlib import Path


def replace_once(path, old, new):
    target = Path(path)
    text = target.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one anchor in {path}, found {count}")
    target.write_text(text.replace(old, new, 1))


territorial = "src/main/java/com/spacesim/world/TerritorialControlRuntime.java"
anchor = """    FactionStrategicState find(String factionContentId) {
        return factionContentId == null ? null : strategiesById.get(factionContentId);
    }

    String controller(StarSystemId systemId) {
"""
replacement = """    FactionStrategicState find(String factionContentId) {
        return factionContentId == null ? null : strategiesById.get(factionContentId);
    }

    FactionStrategicState updateDoctrine(String factionContentId, FactionDoctrineState doctrine) {
        String factionId = requireFaction(factionContentId);
        FactionDoctrineState checked = Objects.requireNonNull(doctrine, "Faction doctrine not set");
        FactionStrategicState current = strategiesById.get(factionId);
        if (current.doctrine().equals(checked)) {
            return current;
        }
        FactionStrategicState replacementState = new FactionStrategicState(
                current.factionContentId(),
                current.minimumMarketAccessRelation(),
                current.relations(),
                current.controlledSystems(),
                current.stationTaxBasisPoints(),
                current.foreignTerritoryTariffBasisPoints(),
                current.stockPolicies(),
                current.productionPolicies(),
                current.strategicGoals(),
                current.territorialClaims(),
                current.territorialControlStates(),
                current.territorialRecognitions(),
                current.constructionRightsGranted(),
                checked);
        replaceStrategy(replacementState);
        return replacementState;
    }

    String controller(StarSystemId systemId) {
"""
replace_once(territorial, anchor, replacement)

world = "src/main/java/com/spacesim/world/WorldSimulation.java"
anchor = """    public Optional<FactionStrategicState> findFactionStrategicState(String factionContentId) {
        return Optional.ofNullable(factionContentId == null ? null : territorialControlRuntime.find(factionContentId));
    }

    /**
     * Resolves authored or world-defined stable faction ID to its local ECS runtime slot.
"""
replacement = """    public Optional<FactionStrategicState> findFactionStrategicState(String factionContentId) {
        return Optional.ofNullable(factionContentId == null ? null : territorialControlRuntime.find(factionContentId));
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
"""
replace_once(world, anchor, replacement)
