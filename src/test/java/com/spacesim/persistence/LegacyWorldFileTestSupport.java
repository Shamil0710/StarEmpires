package com.spacesim.persistence;

import com.spacesim.world.WorldState;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Exact package-local encoders used only to verify historical world-file migration. */
final class LegacyWorldFileTestSupport {
    private static final int MAGIC = 0x53544757;

    private LegacyWorldFileTestSupport() {
        throw new AssertionError("Test utility class");
    }

    /**
     * Encodes current in-memory state using historical file-format v1 framing and no trailers.
     *
     * @param state current valid world state whose base schema payload is representable by v1
     * @return deterministic historical bytes
     */
    static byte[] encodeV1(WorldState state) {
        WorldState checked = Objects.requireNonNull(state, "WorldState not set");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(MAGIC);
                output.writeInt(1);
                output.writeInt(checked.schemaVersion());
                WorldTopologyBinary.write(output, checked.topology());
                WorldSystemBinary.write(output, checked.systems());
                WorldFactionBinary.writeEconomic(output, checked.factions());
                WorldFactionBinary.writeStrategies(output, checked.factionStrategies());
                output.writeLong(checked.nextConstructionProjectIdValue());
                WorldConstructionBinary.write(output, checked.constructionProjects());
                WorldFactionBinary.writePressures(output, checked.factionEconomicPressures());
                output.writeLong(checked.nextFleetIdValue());
                WorldFleetBinary.write(output, checked.fleets());
                WorldFleetBinary.writeJumps(output, checked.fleetJumps());
                WorldFactionIdentityBinary.write(output, checked.factionIdentities());
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory legacy encoding failure", exception);
        }
    }
}
