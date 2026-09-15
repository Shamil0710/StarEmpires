package com.spacesim.content;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.world.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small declared world using the canonical large-demo identities for the actual core factions. */
final class Stage22CorePairWorldFixture {
    private Stage22CorePairWorldFixture() { }

    static WorldSimulation create(long seed) {
        var content = ContentCatalogLoader.loadDefault();
        var base = DemoGalaxyFactory.createState(seed, content);
        var identities = LargeDemoGalaxyFactory.createState(seed, content).factionIdentities();
        var factions = new ArrayList<>(base.factions());
        var strategies = new ArrayList<>(base.factionStrategies());
        var diplomacy = new ArrayList<>(base.factionDiplomacyStates());
        for (String id : List.of(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID,
                Stage22CorePairBalanceEvidence.UNION_FACTION_ID)) {
            factions.add(new FactionEconomicState(id, 0L, 0L, 0L));
            strategies.add(new FactionStrategicState(id, 50, List.of(), List.of()));
            diplomacy.add(new FactionDiplomacyState(id, List.of(), List.of(), List.of(), List.of(), 750));
        }
        return restore(new WorldState(WorldState.CURRENT_VERSION, base.topology(), base.systems(), factions,
                strategies, base.nextConstructionProjectIdValue(), base.constructionProjects(),
                base.factionEconomicPressures(), base.nextFleetIdValue(), base.fleets(), base.fleetJumps(),
                identities, diplomacy));
    }

    static WorldSimulation roundTrip(WorldSimulation world) {
        byte[] encoded = WorldStateCodec.encode(world.snapshot());
        var decoded = WorldStateCodec.decode(encoded);
        if (!Arrays.equals(encoded, WorldStateCodec.encode(decoded))) throw new AssertionError("Core world byte drift");
        return restore(decoded);
    }

    private static WorldSimulation restore(WorldState state) {
        return WorldSimulation.restore(state, ContentCatalogLoader.loadDefault(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS, WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }
}
