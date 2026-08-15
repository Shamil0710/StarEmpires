package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.FactionDoctrineState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage17FDoctrinePersistenceTest {
    private static final int MAGIC = 0x53544757;
    private static final int CUSTOMS_FILE_FORMAT_VERSION = 5;
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void fileFormatV5MigratesEveryFactionToNeutralDoctrineWithoutInventingPreferences() throws IOException {
        WorldState base = DemoGalaxyFactory.createState(17_602L, ContentCatalogLoader.loadDefault());
        FactionDoctrineState nonNeutral = new FactionDoctrineState(91, 14, 73, 28, 84, 37, 66);
        List<FactionStrategicState> customStrategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            customStrategies.add(copyWithDoctrine(
                    strategy,
                    strategy.factionContentId().equals(TRADE_LEAGUE)
                            ? nonNeutral
                            : new FactionDoctrineState(10, 20, 30, 40, 60, 70, 80)));
        }
        WorldState custom = new WorldState(
                base.schemaVersion(),
                base.topology(),
                base.systems(),
                base.factions(),
                customStrategies,
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                base.factionIdentities(),
                base.factionDiplomacyStates());

        WorldState migrated = WorldStateCodec.decode(encodeV5(custom));

        assertEquals(custom.topology(), migrated.topology());
        assertEquals(custom.systems(), migrated.systems());
        assertEquals(custom.factions(), migrated.factions());
        assertEquals(custom.factionDiplomacyStates(), migrated.factionDiplomacyStates());
        assertEquals(custom.factionStrategies().size(), migrated.factionStrategies().size());
        for (FactionStrategicState strategy : migrated.factionStrategies()) {
            assertEquals(FactionDoctrineState.neutral(), strategy.doctrine());
        }
    }

    private static byte[] encodeV5(WorldState state) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(CUSTOMS_FILE_FORMAT_VERSION);
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
        }
        return buffer.toByteArray();
    }

    private static FactionStrategicState copyWithDoctrine(
            FactionStrategicState source,
            FactionDoctrineState doctrine) {
        return new FactionStrategicState(
                source.factionContentId(),
                source.minimumMarketAccessRelation(),
                source.relations(),
                source.controlledSystems(),
                source.stationTaxBasisPoints(),
                source.foreignTerritoryTariffBasisPoints(),
                source.stockPolicies(),
                source.productionPolicies(),
                source.strategicGoals(),
                source.territorialClaims(),
                source.territorialControlStates(),
                source.territorialRecognitions(),
                source.constructionRightsGranted(),
                doctrine);
    }
}
