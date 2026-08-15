package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage17F2FiscalPolicyPersistenceTest {
    private static final int MAGIC = 0x53544757;
    private static final int DOCTRINE_FILE_FORMAT_VERSION = 6;
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void fileFormatV7RoundTripsReserveAndConstructionAuthorizationExactly() {
        WorldState base = DemoGalaxyFactory.createState(17_702L, ContentCatalogLoader.loadDefault());
        List<FactionEconomicState> economics = new ArrayList<>();
        for (FactionEconomicState economy : base.factions()) {
            economics.add(economy.factionContentId().equals(TRADE_LEAGUE)
                    ? new FactionEconomicState(
                            economy.factionContentId(),
                            economy.treasuryMilliCredits(),
                            economy.stationLiquidityReserveMilliCredits(),
                            economy.maxLiquiditySupportPerDecisionMilliCredits(),
                            123_456_789L,
                            45_678_901L)
                    : economy);
        }
        WorldState explicit = copyWithEconomics(base, economics);

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(explicit));

        assertEquals(explicit, decoded);
        FactionEconomicState persisted = decoded.factions().stream()
                .filter(state -> state.factionContentId().equals(TRADE_LEAGUE))
                .findFirst()
                .orElseThrow();
        assertEquals(123_456_789L, persisted.treasuryReserveFloorMilliCredits());
        assertEquals(45_678_901L, persisted.maxConstructionInvestmentPerDecisionMilliCredits());
    }

    @Test
    void fileFormatV6MigratesToLegacySpendingBehaviorWithoutInventingNewLimits() throws IOException {
        WorldState base = DemoGalaxyFactory.createState(17_703L, ContentCatalogLoader.loadDefault());
        List<FactionEconomicState> economics = new ArrayList<>();
        for (FactionEconomicState economy : base.factions()) {
            economics.add(new FactionEconomicState(
                    economy.factionContentId(),
                    economy.treasuryMilliCredits(),
                    economy.stationLiquidityReserveMilliCredits(),
                    economy.maxLiquiditySupportPerDecisionMilliCredits(),
                    777L,
                    888L));
        }
        WorldState explicit = copyWithEconomics(base, economics);

        WorldState migrated = WorldStateCodec.decode(encodeV6(explicit));

        assertEquals(explicit.topology(), migrated.topology());
        assertEquals(explicit.systems(), migrated.systems());
        assertEquals(explicit.factionStrategies(), migrated.factionStrategies());
        assertEquals(explicit.factionDiplomacyStates(), migrated.factionDiplomacyStates());
        for (FactionEconomicState source : explicit.factions()) {
            FactionEconomicState restored = migrated.factions().stream()
                    .filter(state -> state.factionContentId().equals(source.factionContentId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(source.treasuryMilliCredits(), restored.treasuryMilliCredits());
            assertEquals(source.stationLiquidityReserveMilliCredits(), restored.stationLiquidityReserveMilliCredits());
            assertEquals(
                    source.maxLiquiditySupportPerDecisionMilliCredits(),
                    restored.maxLiquiditySupportPerDecisionMilliCredits());
            assertEquals(0L, restored.treasuryReserveFloorMilliCredits());
            assertEquals(
                    FactionEconomicState.LEGACY_UNBOUNDED_CONSTRUCTION_INVESTMENT,
                    restored.maxConstructionInvestmentPerDecisionMilliCredits());
        }
    }

    private static WorldState copyWithEconomics(WorldState base, List<FactionEconomicState> economics) {
        return new WorldState(
                base.schemaVersion(),
                base.topology(),
                base.systems(),
                economics,
                base.factionStrategies(),
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                base.factionIdentities(),
                base.factionDiplomacyStates());
    }

    private static byte[] encodeV6(WorldState state) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(DOCTRINE_FILE_FORMAT_VERSION);
            output.writeInt(state.schemaVersion());
            WorldTopologyBinary.write(output, state.topology());
            WorldSystemBinary.write(output, state.systems());
            WorldFactionBinary.writeEconomic(output, state.factions());
            WorldFactionBinary.writeStrategies(output, state.factionStrategies());
            output.writeLong(state.nextConstructionProjectIdValue());
            WorldConstructionBinary.write(output, state.constructionProjects());
            WorldFactionBinary.writePressures(output, state.factionEconomicPressures());
            output.writeLong(state.nextFleetIdValue());
            WorldFleetBinary.write(output, state.fleets());
            WorldFleetBinary.writeJumps(output, state.fleetJumps());
            WorldFactionIdentityBinary.write(output, state.factionIdentities());
            WorldStrategicGrowthBinary.write(output, state.factionStrategies());
            WorldTerritoryBinary.write(output, state.factionStrategies());
            WorldDiplomacyBinary.write(output, state.factionDiplomacyStates());
            WorldCustomsBinary.write(output, state.factionDiplomacyStates());
            WorldDoctrineBinary.write(output, state.factionStrategies());
        }
        return buffer.toByteArray();
    }
}
