package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FDoctrineContentAcceptanceTest {
    @Test
    void authoredFactionDoctrineIsDataDrivenAndMaterializedIntoInitialWorldState() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState world = DemoGalaxyFactory.createState(17_603L, content);
        Set<FactionDoctrineState> profiles = new HashSet<>();

        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            FactionDoctrineState expected = toWorldDoctrine(faction.doctrine());
            FactionDoctrineState actual = world.factionStrategies().stream()
                    .filter(strategy -> strategy.factionContentId().equals(faction.id()))
                    .findFirst()
                    .orElseThrow()
                    .doctrine();
            assertEquals(expected, actual);
            profiles.add(actual);
        }

        assertEquals(content.getFactions().size(), profiles.size());
        FactionDoctrineState tradeLeague = doctrine(world, "faction.trade_league");
        FactionDoctrineState miners = doctrine(world, "faction.miners");
        FactionDoctrineState neutral = doctrine(world, "faction.neutral");
        assertNotEquals(tradeLeague, miners);
        assertNotEquals(tradeLeague, neutral);
        assertTrue(tradeLeague.tradeOpenness() > miners.tradeOpenness());
        assertTrue(miners.economicResiliencePriority() > tradeLeague.economicResiliencePriority());
    }

    @Test
    void sourceCompatibleFactionDefinitionWithoutDoctrineUsesNeutralProfile() {
        ContentCatalog.FactionDefinition legacy = new ContentCatalog.FactionDefinition(
                "faction.compatibility",
                9,
                "Compatibility");

        assertEquals(ContentCatalog.FactionDoctrineDefinition.neutral(), legacy.doctrine());
        assertEquals(FactionDoctrineState.neutral(), toWorldDoctrine(legacy.doctrine()));
    }

    private static FactionDoctrineState doctrine(WorldState world, String factionId) {
        return world.factionStrategies().stream()
                .filter(strategy -> strategy.factionContentId().equals(factionId))
                .findFirst()
                .orElseThrow()
                .doctrine();
    }

    private static FactionDoctrineState toWorldDoctrine(ContentCatalog.FactionDoctrineDefinition doctrine) {
        return new FactionDoctrineState(
                doctrine.tradeOpenness(),
                doctrine.securityPosture(),
                doctrine.expansionPreference(),
                doctrine.sovereigntySensitivity(),
                doctrine.treatyLegalism(),
                doctrine.interventionism(),
                doctrine.economicResiliencePriority());
    }
}
