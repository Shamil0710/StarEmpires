package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionPolicyReviewCadence;
import com.spacesim.world.FactionPolicyReviewState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F6PolicyReviewPersistenceTest {
    private static final int WORLD_MAGIC = 0x53544757;
    private static final int LEGACY_FISCAL_FILE_FORMAT_VERSION = 7;

    @Test
    void claimedReviewWatermarkRoundTripsInCurrentFormat() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F60002L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        String faction = "faction.neutral";
        long tick = world.getAuthoritativeWorldTick();

        assertTrue(world.tryBeginFactionPolicyReview(
                faction, new FactionPolicyReviewCadence(100L, 0L)));
        WorldState snapshot = world.snapshot();
        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(snapshot));

        assertEquals(snapshot, decoded);
        FactionEconomicState decodedFaction = decoded.factions().stream()
                .filter(state -> state.factionContentId().equals(faction))
                .findFirst()
                .orElseThrow();
        assertEquals(new FactionPolicyReviewState(tick), decodedFaction.policyReviewState());
    }

    @Test
    void legacyV7FiscalSaveMigratesToNeverReviewedLifecycle() throws IOException {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState legacySource = DemoGalaxyFactory.createState(0x17F60003L, content);

        WorldState decoded = WorldStateCodec.decode(encodeV7(legacySource));

        assertEquals(legacySource.factions().size(), decoded.factions().size());
        for (FactionEconomicState faction : decoded.factions()) {
            assertEquals(FactionPolicyReviewState.INITIAL, faction.policyReviewState());
        }
    }

    @Test
    void pre17F6EconomicConstructorDefaultsToNeverReviewedLifecycle() {
        FactionEconomicState legacy = new FactionEconomicState(
                "faction.legacy",
                1_000_000L,
                100_000L,
                50_000L,
                25_000L,
                75_000L);

        assertEquals(FactionPolicyReviewState.INITIAL, legacy.policyReviewState());
    }

    private static byte[] encodeV7(WorldState state) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(WORLD_MAGIC);
            output.writeInt(LEGACY_FISCAL_FILE_FORMAT_VERSION);
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
            WorldFiscalPolicyBinary.write(output, state.factions());
        }
        return buffer.toByteArray();
    }
}
