package com.spacesim.content;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CivilianMinorMigrationAcceptanceTest {
    private static final Set<String> MINOR_IDS = Set.of(
            "faction.neutral",
            "faction.trade_league",
            "faction.miners");

    @Test
    void supportedWorldRoundTripPreservesMinorStableIdsRuntimeSlotsAndNoCorePackageBinding() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState before = LargeDemoGalaxyFactory.createState(22_500L, content);
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
        Stage22CivilianMinorEcosystemCatalog ecosystem = Stage22CivilianMinorEcosystemCatalog.loadDefault();

        byte[] encoded = WorldStateCodec.encode(before);
        WorldState restored = WorldStateCodec.decode(encoded);
        byte[] reencoded = WorldStateCodec.encode(restored);

        assertArrayEquals(encoded, reencoded);
        FactionIdentityResolver beforeResolver = FactionIdentityResolver.createDefault(content, before.factionIdentities());
        FactionIdentityResolver restoredResolver = FactionIdentityResolver.createDefault(content, restored.factionIdentities());

        for (String stableId : MINOR_IDS) {
            assertTrue(beforeResolver.containsStableId(stableId), stableId);
            assertTrue(restoredResolver.containsStableId(stableId), stableId);
            assertEquals(beforeResolver.runtimeId(stableId), restoredResolver.runtimeId(stableId), stableId);
            assertEquals(beforeResolver.displayName(stableId), restoredResolver.displayName(stableId), stableId);
            assertNull(governance.canonicalPackageKey(stableId), stableId);

            var actor = ecosystem.minorActor(stableId);
            assertTrue(actor.preserveStableId(), stableId);
            assertFalse(actor.majorPackageFallbackAllowed(), stableId);
        }
    }
}
