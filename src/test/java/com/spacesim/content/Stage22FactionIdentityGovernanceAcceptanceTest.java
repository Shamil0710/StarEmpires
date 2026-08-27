package com.spacesim.content;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class Stage22FactionIdentityGovernanceAcceptanceTest {
    @Test
    void governanceCoversAuthoredLargeDemoAndSeparateGeneratedWorldCompatibilityLineages() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState state = LargeDemoGalaxyFactory.createState(22_000L, content);
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();

        Set<String> authored = content.getFactions().stream()
                .map(ContentCatalog.FactionDefinition::id)
                .collect(Collectors.toSet());
        Set<String> bootstrap = state.factionIdentities().stream()
                .map(WorldFactionIdentityState::stableFactionId)
                .collect(Collectors.toSet());
        Set<String> largeDemoIdentities = java.util.stream.Stream.concat(authored.stream(), bootstrap.stream())
                .collect(Collectors.toUnmodifiableSet());
        Set<String> governed = governance.getFactionIdentities().stream()
                .map(Stage22ContentGovernanceCatalog.FactionIdentityDefinition::stableFactionId)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(3, authored.size());
        assertEquals(5, bootstrap.size());
        assertEquals(8, largeDemoIdentities.size());
        assertEquals(Set.of("faction.alpha", "faction.beta"),
                governed.stream().filter(id -> !largeDemoIdentities.contains(id)).collect(Collectors.toSet()));
        assertEquals(10, governed.size());
    }

    @Test
    void corePublicPackageBindingDoesNotCreateSecondFactionStateOwner() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState state = LargeDemoGalaxyFactory.createState(22_001L, content);
        FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(content, state.factionIdentities());
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();

        Map<String, Integer> expectedRuntimeIds = new LinkedHashMap<>();
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            expectedRuntimeIds.put(faction.id(), faction.runtimeId());
        }
        for (WorldFactionIdentityState faction : state.factionIdentities()) {
            expectedRuntimeIds.put(faction.stableFactionId(), faction.runtimeFactionId());
        }

        expectedRuntimeIds.forEach((stableId, runtimeId) -> {
            assertEquals(runtimeId, resolver.runtimeId(stableId).orElseThrow());
            assertEquals(stableId, resolver.stableId(runtimeId).orElseThrow());
        });

        assertFalse(resolver.containsStableId("faction.empire"));
        assertFalse(resolver.containsStableId("faction.industrial-union"));
        assertEquals("core.empire", governance.canonicalPackageKey("faction.imperial_directorate"));
        assertEquals("Империя", governance.canonicalDisplayName(
                "faction.imperial_directorate",
                resolver.displayName("faction.imperial_directorate").orElseThrow()));
        assertEquals("core.industrial_union", governance.canonicalPackageKey("faction.industrial_combine"));
        assertEquals("Индустриальный Союз", governance.canonicalDisplayName(
                "faction.industrial_combine",
                resolver.displayName("faction.industrial_combine").orElseThrow()));

        assertNull(governance.canonicalPackageKey("faction.frontier_union"));
        assertNull(governance.canonicalPackageKey("faction.free_ports"));
        assertNull(governance.canonicalPackageKey("faction.research_consortium"));
        assertNull(governance.canonicalPackageKey("faction.alpha"));
        assertNull(governance.canonicalPackageKey("faction.beta"));
    }

    @Test
    void largeDemoWorldSaveRoundTripKeepsStableIdsRuntimeSlotsAndExactBytes() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState before = LargeDemoGalaxyFactory.createState(22_002L, content);
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();

        byte[] firstBytes = WorldStateCodec.encode(before);
        WorldState restored = WorldStateCodec.decode(firstBytes);
        byte[] secondBytes = WorldStateCodec.encode(restored);

        assertArrayEquals(firstBytes, secondBytes);
        assertEquals(before.factionIdentities(), restored.factionIdentities());
        assertEquals(before.factions(), restored.factions());
        assertEquals(before.factionStrategies(), restored.factionStrategies());

        FactionIdentityResolver beforeResolver = FactionIdentityResolver.createDefault(content, before.factionIdentities());
        FactionIdentityResolver restoredResolver = FactionIdentityResolver.createDefault(content, restored.factionIdentities());
        for (Stage22ContentGovernanceCatalog.FactionIdentityDefinition identity : governance.getFactionIdentities()) {
            String id = identity.stableFactionId();
            assertEquals(beforeResolver.runtimeId(id), restoredResolver.runtimeId(id), id);
            assertEquals(beforeResolver.displayName(id), restoredResolver.displayName(id), id);
            assertEquals(
                    governance.canonicalDisplayName(id, beforeResolver.displayName(id).orElse(id)),
                    governance.canonicalDisplayName(id, restoredResolver.displayName(id).orElse(id)),
                    id);
        }
    }
}
