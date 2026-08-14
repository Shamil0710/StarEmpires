package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionSettlementKind;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage16WorldConstructionMigrationTest {
    private static final int MAGIC = 0x53544757;
    private static final int FILE_FORMAT_VERSION = 2;

    @Test
    void schemaV7FactionProjectMigratesToExplicitTreasurySettlement() throws IOException {
        WorldSimulation world = DemoGalaxyFactory.create(16_203L);
        WorldState before = world.snapshot();
        String factionId = before.factions().get(0).factionContentId();
        ConstructionProjectId projectId = world.createConstructionProject(
                factionId,
                "station.mining_base",
                world.getActiveSystemId(),
                420f,
                360f);
        WorldState state = world.snapshot();

        WorldState migrated = WorldStateCodec.decode(encodeSchemaV7(state));
        ConstructionProjectState project = migrated.constructionProjects().stream()
                .filter(candidate -> candidate.id().equals(projectId))
                .findFirst()
                .orElseThrow();

        assertEquals(WorldState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(ConstructionSettlementKind.FACTION_TREASURY, project.settlementKind());
        assertEquals(factionId, project.ownerFactionContentId());
        assertEquals(factionId, project.legalFactionContentId());
        assertEquals(state.fleetJumps(), migrated.fleetJumps());
    }

    private static byte[] encodeSchemaV7(WorldState state) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(MAGIC);
            output.writeInt(FILE_FORMAT_VERSION);
            output.writeInt(WorldState.LEGACY_STAGE10_JUMP_VERSION);
            WorldTopologyBinary.write(output, state.topology());
            WorldSystemBinary.write(output, state.systems());
            WorldFactionBinary.writeEconomic(output, state.factions());
            WorldFactionBinary.writeStrategies(output, state.factionStrategies());
            output.writeLong(state.nextConstructionProjectIdValue());
            WorldConstructionBinary.writeLegacy(output, state.constructionProjects());
            WorldFactionBinary.writePressures(output, state.factionEconomicPressures());
            output.writeLong(state.nextFleetIdValue());
            WorldFleetBinary.write(output, state.fleets());
            WorldFleetBinary.writeJumps(output, state.fleetJumps());
            WorldStrategicGrowthBinary.write(output, state.factionStrategies());
        }
        return buffer.toByteArray();
    }
}
