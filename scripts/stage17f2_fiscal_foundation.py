from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    text = target.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one anchor in {path}, found {count}")
    target.write_text(text.replace(old, new, 1))


Path("src/main/java/com/spacesim/world/FactionEconomicState.java").write_text(r'''package com.spacesim.world;

import java.util.Objects;

/**
 * Persistent faction treasury and bounded fiscal spending authorizations.
 *
 * <p>All amounts are stored in integer milli-credits. The reserve floor and construction budget are
 * policy authorizations over the same treasury wallet; neither creates a second account or money
 * source. A reserve floor may exceed the current treasury balance, in which case discretionary
 * spending is zero until the treasury recovers.</p>
 *
 * @param factionContentId stable faction content ID
 * @param treasuryMilliCredits current real treasury balance
 * @param stationLiquidityReserveMilliCredits desired minimum operating liquidity per owned station
 * @param maxLiquiditySupportPerDecisionMilliCredits maximum subsidy transfer per policy decision
 * @param treasuryReserveFloorMilliCredits protected treasury floor for discretionary spending
 * @param maxConstructionInvestmentPerDecisionMilliCredits maximum treasury construction funding per decision
 */
public record FactionEconomicState(
        String factionContentId,
        long treasuryMilliCredits,
        long stationLiquidityReserveMilliCredits,
        long maxLiquiditySupportPerDecisionMilliCredits,
        long treasuryReserveFloorMilliCredits,
        long maxConstructionInvestmentPerDecisionMilliCredits)
        implements Comparable<FactionEconomicState> {

    /** Legacy behavior before Stage 17F.2: no reserve floor and no construction budget ceiling. */
    public static final long LEGACY_UNBOUNDED_CONSTRUCTION_INVESTMENT = Long.MAX_VALUE;

    /**
     * Source-compatible Stage-8 constructor preserving historical spending behavior.
     *
     * @param factionContentId stable faction content ID
     * @param treasuryMilliCredits current real treasury balance
     * @param stationLiquidityReserveMilliCredits desired station liquidity floor
     * @param maxLiquiditySupportPerDecisionMilliCredits maximum subsidy transfer per decision
     */
    public FactionEconomicState(
            String factionContentId,
            long treasuryMilliCredits,
            long stationLiquidityReserveMilliCredits,
            long maxLiquiditySupportPerDecisionMilliCredits) {
        this(
                factionContentId,
                treasuryMilliCredits,
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits,
                0L,
                LEGACY_UNBOUNDED_CONSTRUCTION_INVESTMENT);
    }

    /** Validates stable identity and non-negative balances/authorizations. */
    public FactionEconomicState {
        factionContentId = Objects.requireNonNull(factionContentId, "Faction content ID не задан").strip();
        if (factionContentId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID не должен быть пустым");
        }
        if (treasuryMilliCredits < 0L
                || stationLiquidityReserveMilliCredits < 0L
                || maxLiquiditySupportPerDecisionMilliCredits < 0L
                || treasuryReserveFloorMilliCredits < 0L
                || maxConstructionInvestmentPerDecisionMilliCredits < 0L) {
            throw new IllegalArgumentException("Faction economic balances/policy cannot be negative");
        }
    }

    /**
     * Computes real treasury currently available above the protected reserve floor.
     *
     * @return non-negative discretionary balance
     */
    public long discretionaryTreasuryMilliCredits() {
        return treasuryMilliCredits <= treasuryReserveFloorMilliCredits
                ? 0L
                : treasuryMilliCredits - treasuryReserveFloorMilliCredits;
    }

    @Override
    public int compareTo(FactionEconomicState other) {
        return factionContentId.compareTo(
                Objects.requireNonNull(other, "FactionEconomicState не задан").factionContentId);
    }
}
''')

Path("src/main/java/com/spacesim/world/FactionEconomicAccount.java").write_text(r'''package com.spacesim.world;

import com.spacesim.components.WalletComponent;

import java.util.Objects;

/** Runtime mutable treasury and fiscal authorization state for one persistent faction economy. */
final class FactionEconomicAccount {
    private final String factionContentId;
    private final WalletComponent treasury;
    private long stationLiquidityReserveMilliCredits;
    private long maxLiquiditySupportPerDecisionMilliCredits;
    private long treasuryReserveFloorMilliCredits;
    private long maxConstructionInvestmentPerDecisionMilliCredits;

    FactionEconomicAccount(FactionEconomicState state) {
        FactionEconomicState checked = Objects.requireNonNull(state, "FactionEconomicState не задан");
        factionContentId = checked.factionContentId();
        treasury = new WalletComponent(checked.treasuryMilliCredits());
        applyPolicy(checked);
    }

    String factionContentId() {
        return factionContentId;
    }

    WalletComponent treasury() {
        return treasury;
    }

    long stationLiquidityReserveMilliCredits() {
        return stationLiquidityReserveMilliCredits;
    }

    long maxLiquiditySupportPerDecisionMilliCredits() {
        return maxLiquiditySupportPerDecisionMilliCredits;
    }

    long treasuryReserveFloorMilliCredits() {
        return treasuryReserveFloorMilliCredits;
    }

    long maxConstructionInvestmentPerDecisionMilliCredits() {
        return maxConstructionInvestmentPerDecisionMilliCredits;
    }

    long discretionaryTreasuryMilliCredits() {
        long balance = treasury.getBalanceMilliCredits();
        return balance <= treasuryReserveFloorMilliCredits ? 0L : balance - treasuryReserveFloorMilliCredits;
    }

    long constructionInvestmentAuthorizationMilliCredits() {
        return Math.min(discretionaryTreasuryMilliCredits(), maxConstructionInvestmentPerDecisionMilliCredits);
    }

    void updatePolicy(
            long stationLiquidityReserveMilliCredits,
            long maxLiquiditySupportPerDecisionMilliCredits,
            long treasuryReserveFloorMilliCredits,
            long maxConstructionInvestmentPerDecisionMilliCredits) {
        FactionEconomicState checked = new FactionEconomicState(
                factionContentId,
                treasury.getBalanceMilliCredits(),
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits,
                treasuryReserveFloorMilliCredits,
                maxConstructionInvestmentPerDecisionMilliCredits);
        applyPolicy(checked);
    }

    FactionEconomicState snapshot() {
        return new FactionEconomicState(
                factionContentId,
                treasury.getBalanceMilliCredits(),
                stationLiquidityReserveMilliCredits,
                maxLiquiditySupportPerDecisionMilliCredits,
                treasuryReserveFloorMilliCredits,
                maxConstructionInvestmentPerDecisionMilliCredits);
    }

    private void applyPolicy(FactionEconomicState state) {
        stationLiquidityReserveMilliCredits = state.stationLiquidityReserveMilliCredits();
        maxLiquiditySupportPerDecisionMilliCredits = state.maxLiquiditySupportPerDecisionMilliCredits();
        treasuryReserveFloorMilliCredits = state.treasuryReserveFloorMilliCredits();
        maxConstructionInvestmentPerDecisionMilliCredits = state.maxConstructionInvestmentPerDecisionMilliCredits();
    }
}
''')

Path("src/main/java/com/spacesim/world/FactionFiscalPolicyState.java").write_text(r'''package com.spacesim.world;

import java.util.Objects;

/**
 * Shared Stage-17F.2 player/AI fiscal-policy command and read model.
 *
 * <p>The record combines policy values persisted in the strategic, diplomacy and economic
 * aggregates without introducing another treasury. Tax/levy/customs rates authorize ordinary
 * real transfers; reserve and budget values limit spending from the existing treasury wallet.</p>
 *
 * @param factionContentId authored or world-defined stable faction ID
 * @param ownStationTaxBasisPoints own-station fiscal levy, 0..10000
 * @param territorialForeignStationLevyBasisPoints foreign-station levy inside controlled territory, 0..10000
 * @param customsTariffBasisPoints transaction/customs tariff before treaty exemptions, 0..10000
 * @param treasuryReserveFloorMilliCredits protected treasury floor for discretionary spending
 * @param stationLiquidityReserveMilliCredits desired station operating-liquidity floor
 * @param maxLiquiditySupportPerDecisionMilliCredits maximum treasury subsidy per decision
 * @param maxConstructionInvestmentPerDecisionMilliCredits maximum treasury construction funding per decision
 */
public record FactionFiscalPolicyState(
        String factionContentId,
        int ownStationTaxBasisPoints,
        int territorialForeignStationLevyBasisPoints,
        int customsTariffBasisPoints,
        long treasuryReserveFloorMilliCredits,
        long stationLiquidityReserveMilliCredits,
        long maxLiquiditySupportPerDecisionMilliCredits,
        long maxConstructionInvestmentPerDecisionMilliCredits) {

    /** Validates bounded rates and non-negative spending authorizations. */
    public FactionFiscalPolicyState {
        factionContentId = Objects.requireNonNull(factionContentId, "Fiscal policy faction ID not set").strip();
        if (factionContentId.isEmpty()) {
            throw new IllegalArgumentException("Fiscal policy faction ID cannot be blank");
        }
        requireBasisPoints(ownStationTaxBasisPoints, "Own-station tax");
        requireBasisPoints(territorialForeignStationLevyBasisPoints, "Territorial foreign-station levy");
        requireBasisPoints(customsTariffBasisPoints, "Customs tariff");
        if (treasuryReserveFloorMilliCredits < 0L
                || stationLiquidityReserveMilliCredits < 0L
                || maxLiquiditySupportPerDecisionMilliCredits < 0L
                || maxConstructionInvestmentPerDecisionMilliCredits < 0L) {
            throw new IllegalArgumentException("Fiscal spending policy cannot be negative");
        }
    }

    private static void requireBasisPoints(int value, String label) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(label + " must be in range 0..10000 bps");
        }
    }
}
''')

Path("src/main/java/com/spacesim/persistence/WorldFiscalPolicyBinary.java").write_text(r'''package com.spacesim.persistence;

import com.spacesim.world.FactionEconomicState;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Stage-17F.2 bounded v7 trailer for treasury reserve and construction spending authorization. */
final class WorldFiscalPolicyBinary {
    private static final int MAX_FACTIONS = 10_000;

    private WorldFiscalPolicyBinary() {
        throw new AssertionError("Utility class");
    }

    static void write(DataOutputStream out, List<FactionEconomicState> states) throws IOException {
        WorldIoSupport.writeCount(out, states.size(), MAX_FACTIONS, "fiscalPolicyFactions");
        for (FactionEconomicState state : states) {
            WorldIoSupport.writeString(out, state.factionContentId());
            out.writeLong(state.treasuryReserveFloorMilliCredits());
            out.writeLong(state.maxConstructionInvestmentPerDecisionMilliCredits());
        }
    }

    static List<FactionEconomicState> readAndAttach(
            DataInputStream in,
            List<FactionEconomicState> states) throws IOException {
        int count = WorldIoSupport.readCount(in, MAX_FACTIONS, "fiscalPolicyFactions");
        Map<String, Payload> payloads = new HashMap<>();
        for (int index = 0; index < count; index++) {
            String factionId = WorldIoSupport.readString(in);
            long reserveFloor = in.readLong();
            long constructionBudget = in.readLong();
            if (reserveFloor < 0L || constructionBudget < 0L) {
                throw new IllegalArgumentException("Fiscal policy trailer values cannot be negative");
            }
            if (payloads.putIfAbsent(factionId, new Payload(reserveFloor, constructionBudget)) != null) {
                throw new IllegalArgumentException("Duplicate fiscal policy faction trailer: " + factionId);
            }
        }
        if (payloads.size() != states.size()) {
            throw new IllegalArgumentException("Fiscal policy trailer does not cover every faction economy");
        }
        List<FactionEconomicState> result = new ArrayList<>(states.size());
        for (FactionEconomicState state : states) {
            Payload payload = payloads.remove(state.factionContentId());
            if (payload == null) {
                throw new IllegalArgumentException(
                        "Fiscal policy trailer missing faction economy: " + state.factionContentId());
            }
            result.add(new FactionEconomicState(
                    state.factionContentId(),
                    state.treasuryMilliCredits(),
                    state.stationLiquidityReserveMilliCredits(),
                    state.maxLiquiditySupportPerDecisionMilliCredits(),
                    payload.reserveFloorMilliCredits(),
                    payload.maxConstructionInvestmentPerDecisionMilliCredits()));
        }
        if (!payloads.isEmpty()) {
            throw new IllegalArgumentException("Fiscal policy trailer references unknown factions");
        }
        result.sort(null);
        return List.copyOf(result);
    }

    private record Payload(
            long reserveFloorMilliCredits,
            long maxConstructionInvestmentPerDecisionMilliCredits) {
    }
}
''')

replace_once(
    "src/main/java/com/spacesim/persistence/WorldStateCodec.java",
    " * Stage-17E institutional diplomacy, v5 — отдельную transaction/customs tariff policy, а v6 —\n * Stage-17F persistent institutional doctrine profiles. v1-v3 детерминированно мигрируют в\n * neutral explicit diplomacy без выдуманных treaties, grievances или embargoes; v1-v4 получают\n * нулевой customs tariff, а v1-v5 — neutral doctrine с midpoint 50 по каждой оси. Local entity payload\n",
    " * Stage-17E institutional diplomacy, v5 — отдельную transaction/customs tariff policy, v6 —\n * Stage-17F persistent institutional doctrine profiles, а v7 — treasury reserve floor и construction\n * spending authorization. v1-v3 детерминированно мигрируют в neutral explicit diplomacy без\n * выдуманных treaties, grievances или embargoes; v1-v4 получают нулевой customs tariff, v1-v5 —\n * neutral doctrine с midpoint 50 по каждой оси, а v1-v6 сохраняют legacy fiscal behavior: reserve\n * floor 0 и construction budget без дополнительного ceiling. Local entity payload\n",
)
replace_once(
    "src/main/java/com/spacesim/persistence/WorldStateCodec.java",
    "    private static final int CUSTOMS_FILE_FORMAT_VERSION = 5;\n    private static final int FILE_FORMAT_VERSION = 6;",
    "    private static final int CUSTOMS_FILE_FORMAT_VERSION = 5;\n    private static final int DOCTRINE_FILE_FORMAT_VERSION = 6;\n    private static final int FILE_FORMAT_VERSION = 7;",
)
replace_once(
    "src/main/java/com/spacesim/persistence/WorldStateCodec.java",
    "                WorldCustomsBinary.write(output, checked.factionDiplomacyStates());\n                WorldDoctrineBinary.write(output, checked.factionStrategies());",
    "                WorldCustomsBinary.write(output, checked.factionDiplomacyStates());\n                WorldDoctrineBinary.write(output, checked.factionStrategies());\n                WorldFiscalPolicyBinary.write(output, checked.factions());",
)
replace_once(
    "src/main/java/com/spacesim/persistence/WorldStateCodec.java",
    "            if (fileVersion != FILE_FORMAT_VERSION\n                    && fileVersion != CUSTOMS_FILE_FORMAT_VERSION",
    "            if (fileVersion != FILE_FORMAT_VERSION\n                    && fileVersion != DOCTRINE_FILE_FORMAT_VERSION\n                    && fileVersion != CUSTOMS_FILE_FORMAT_VERSION",
)
replace_once(
    "src/main/java/com/spacesim/persistence/WorldStateCodec.java",
    "            if (fileVersion >= FILE_FORMAT_VERSION) {\n                state = withStrategies(\n                        state,\n                        WorldDoctrineBinary.readAndAttach(input, state.factionStrategies()));\n            }\n\n            if (input.read() != -1)",
    "            if (fileVersion >= DOCTRINE_FILE_FORMAT_VERSION) {\n                state = withStrategies(\n                        state,\n                        WorldDoctrineBinary.readAndAttach(input, state.factionStrategies()));\n            }\n            if (fileVersion >= FILE_FORMAT_VERSION) {\n                state = withFactions(\n                        state,\n                        WorldFiscalPolicyBinary.readAndAttach(input, state.factions()));\n            }\n\n            if (input.read() != -1)",
)
replace_once(
    "src/main/java/com/spacesim/persistence/WorldStateCodec.java",
    "    private static WorldState withStrategies(\n",
    "    private static WorldState withFactions(\n            WorldState state,\n            List<FactionEconomicState> factions) {\n        return new WorldState(\n                state.schemaVersion(),\n                state.topology(),\n                state.systems(),\n                factions,\n                state.factionStrategies(),\n                state.nextConstructionProjectIdValue(),\n                state.constructionProjects(),\n                state.factionEconomicPressures(),\n                state.nextFleetIdValue(),\n                state.fleets(),\n                state.fleetJumps(),\n                state.factionIdentities(),\n                state.factionDiplomacyStates());\n    }\n\n    private static WorldState withStrategies(\n",
)

territory_anchor = '''        replaceStrategy(replacementState);\n        return replacementState;\n    }\n\n    String controller(StarSystemId systemId) {'''
territory_insert = '''        replaceStrategy(replacementState);\n        return replacementState;\n    }\n\n    FactionStrategicState updateFiscalRates(\n            String factionContentId,\n            int stationTaxBasisPoints,\n            int foreignTerritoryLevyBasisPoints) {\n        String factionId = requireFaction(factionContentId);\n        FactionStrategicState current = strategiesById.get(factionId);\n        if (current.stationTaxBasisPoints() == stationTaxBasisPoints\n                && current.foreignTerritoryTariffBasisPoints() == foreignTerritoryLevyBasisPoints) {\n            return current;\n        }\n        FactionStrategicState replacementState = new FactionStrategicState(\n                current.factionContentId(),\n                current.minimumMarketAccessRelation(),\n                current.relations(),\n                current.controlledSystems(),\n                stationTaxBasisPoints,\n                foreignTerritoryLevyBasisPoints,\n                current.stockPolicies(),\n                current.productionPolicies(),\n                current.strategicGoals(),\n                current.territorialClaims(),\n                current.territorialControlStates(),\n                current.territorialRecognitions(),\n                current.constructionRightsGranted(),\n                current.doctrine());\n        replaceStrategy(replacementState);\n        return replacementState;\n    }\n\n    String controller(StarSystemId systemId) {'''
replace_once("src/main/java/com/spacesim/world/TerritorialControlRuntime.java", territory_anchor, territory_insert)

diplomacy_anchor = '''    FactionDiplomacyState find(String factionContentId) {\n        return factionContentId == null ? null : byId.get(factionContentId.strip());\n    }\n\n    DiplomaticTreatyState findTreaty(String treatyId) {'''
diplomacy_insert = '''    FactionDiplomacyState find(String factionContentId) {\n        return factionContentId == null ? null : byId.get(factionContentId.strip());\n    }\n\n    FactionDiplomacyState updateCustomsTariff(String factionContentId, int customsTariffBasisPoints) {\n        String factionId = requireFaction(factionContentId);\n        FactionDiplomacyState current = byId.get(factionId);\n        if (current.customsTariffBasisPoints() == customsTariffBasisPoints) {\n            return current;\n        }\n        FactionDiplomacyState replacement = new FactionDiplomacyState(\n                current.factionContentId(),\n                current.standings(),\n                current.grievances(),\n                current.treaties(),\n                current.embargoes(),\n                customsTariffBasisPoints);\n        List<FactionDiplomacyState> updated = new ArrayList<>(states.size());\n        for (FactionDiplomacyState state : states) {\n            updated.add(state.factionContentId().equals(factionId) ? replacement : state);\n        }\n        install(updated);\n        return replacement;\n    }\n\n    DiplomaticTreatyState findTreaty(String treatyId) {'''
replace_once("src/main/java/com/spacesim/world/FactionDiplomacyRuntime.java", diplomacy_anchor, diplomacy_insert)

replace_once(
    "src/main/java/com/spacesim/world/WorldSimulation.java",
    "        long remainingBudget = Math.min(\n                account.maxLiquiditySupportPerDecisionMilliCredits(),\n                account.treasury().getBalanceMilliCredits());",
    "        long remainingBudget = Math.min(\n                account.maxLiquiditySupportPerDecisionMilliCredits(),\n                account.discretionaryTreasuryMilliCredits());",
)

world_anchor = '''    public Optional<FactionStrategicState> findFactionStrategicState(String factionContentId) {\n        return Optional.ofNullable(factionContentId == null ? null : territorialControlRuntime.find(factionContentId));\n    }\n\n    /**\n     * Replaces one faction's persistent institutional doctrine through the common player/AI boundary.'''
world_insert = '''    public Optional<FactionStrategicState> findFactionStrategicState(String factionContentId) {\n        return Optional.ofNullable(factionContentId == null ? null : territorialControlRuntime.find(factionContentId));\n    }\n\n    /**\n     * Returns the shared persistent fiscal-policy projection for one faction.\n     *\n     * @param factionContentId authored or world-defined stable faction ID\n     * @return current policy or empty when the faction lacks a complete fiscal aggregate\n     */\n    public Optional<FactionFiscalPolicyState> findFactionFiscalPolicy(String factionContentId) {\n        if (factionContentId == null) {\n            return Optional.empty();\n        }\n        String factionId = factionContentId.strip();\n        FactionEconomicAccount account = factionAccountsById.get(factionId);\n        FactionStrategicState strategy = territorialControlRuntime.find(factionId);\n        FactionDiplomacyState diplomacy = diplomacyRuntime.find(factionId);\n        if (account == null || strategy == null || diplomacy == null) {\n            return Optional.empty();\n        }\n        FactionEconomicState economy = account.snapshot();\n        return Optional.of(new FactionFiscalPolicyState(\n                factionId,\n                strategy.stationTaxBasisPoints(),\n                strategy.foreignTerritoryTariffBasisPoints(),\n                diplomacy.customsTariffBasisPoints(),\n                economy.treasuryReserveFloorMilliCredits(),\n                economy.stationLiquidityReserveMilliCredits(),\n                economy.maxLiquiditySupportPerDecisionMilliCredits(),\n                economy.maxConstructionInvestmentPerDecisionMilliCredits()));\n    }\n\n    /**\n     * Installs one common player/AI fiscal policy without moving money or changing physical state.\n     *\n     * <p>The command only changes rates and spending authorizations. Actual taxes, customs duties,\n     * subsidies and construction funding continue to execute through ordinary conserved wallet\n     * transfers at their existing settlement boundaries.</p>\n     *\n     * @param policy complete bounded fiscal policy for an existing faction\n     * @return installed canonical policy\n     */\n    public FactionFiscalPolicyState updateFactionFiscalPolicy(FactionFiscalPolicyState policy) {\n        FactionFiscalPolicyState checked = Objects.requireNonNull(policy, "Faction fiscal policy not set");\n        String factionId = normalizedFactionId(checked.factionContentId());\n        FactionEconomicAccount account = requireFactionAccount(factionId);\n        if (territorialControlRuntime.find(factionId) == null || diplomacyRuntime.find(factionId) == null) {\n            throw new IllegalArgumentException("Faction has incomplete fiscal policy state: " + factionId);\n        }\n        territorialControlRuntime.updateFiscalRates(\n                factionId,\n                checked.ownStationTaxBasisPoints(),\n                checked.territorialForeignStationLevyBasisPoints());\n        diplomacyRuntime.updateCustomsTariff(factionId, checked.customsTariffBasisPoints());\n        account.updatePolicy(\n                checked.stationLiquidityReserveMilliCredits(),\n                checked.maxLiquiditySupportPerDecisionMilliCredits(),\n                checked.treasuryReserveFloorMilliCredits(),\n                checked.maxConstructionInvestmentPerDecisionMilliCredits());\n        return findFactionFiscalPolicy(factionId).orElseThrow();\n    }\n\n    /**\n     * Replaces one faction's persistent institutional doctrine through the common player/AI boundary.'''
replace_once("src/main/java/com/spacesim/world/WorldSimulation.java", world_anchor, world_insert)

fund_anchor = '''    public long fundConstructionProject(\n            ConstructionProjectId projectId, long amountMilliCredits) {\n        return constructionProjectService.fund(projectId, amountMilliCredits);\n    }'''
fund_insert = '''    public long fundConstructionProject(\n            ConstructionProjectId projectId, long amountMilliCredits) {\n        ConstructionProjectState project = constructionProjectService.find(projectId).orElse(null);\n        if (project != null && project.ownerFactionContentId() != null && amountMilliCredits > 0L) {\n            FactionEconomicAccount account = requireFactionAccount(project.ownerFactionContentId());\n            if (amountMilliCredits > account.constructionInvestmentAuthorizationMilliCredits()) {\n                return 0L;\n            }\n        }\n        return constructionProjectService.fund(projectId, amountMilliCredits);\n    }'''
replace_once("src/main/java/com/spacesim/world/WorldSimulation.java", fund_anchor, fund_insert)

replace_once(
    "src/main/java/com/spacesim/world/FactionInvestmentPlanner.java",
    "            if (economy.treasuryMilliCredits() < selected.fundingMilliCredits()) {\n                continue;\n            }",
    "            long investmentAuthorization = Math.min(\n                    economy.discretionaryTreasuryMilliCredits(),\n                    economy.maxConstructionInvestmentPerDecisionMilliCredits());\n            if (investmentAuthorization < selected.fundingMilliCredits()) {\n                continue;\n            }",
)

replace_once(
    "src/main/java/com/spacesim/player/PlayerFactionFoundationService.java",
    "        economics.add(new FactionEconomicState(id, 0L, 0L, 0L));",
    "        economics.add(new FactionEconomicState(id, 0L, 0L, 0L, 0L, 0L));",
)
