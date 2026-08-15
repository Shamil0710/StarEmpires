package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Re-materializes transient local market-access components from persistent faction policy.
 *
 * <p>This is a presentation/runtime boundary operation, not an economic transaction. It changes no
 * wallet, inventory, ownership, territory or persistent diplomacy. Live affiliation/diplomacy
 * changes therefore receive the same access projection that save/load restoration would produce.</p>
 */
public final class FactionPolicyRefreshService {
    private FactionPolicyRefreshService() {
        throw new AssertionError("FactionPolicyRefreshService does not create instances");
    }

    /**
     * Refreshes faction market-access policy in every local simulation session.
     *
     * @param world authoritative live world
     * @param content immutable authored content catalog used by that world
     * @return number of local sessions refreshed
     */
    public static int refresh(WorldSimulation world, ContentCatalog content) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        ContentCatalog checkedContent = Objects.requireNonNull(content, "ContentCatalog not set");
        FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(
                checkedContent,
                checkedWorld.getWorldFactionIdentities());

        List<FactionStrategicState> strategies = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (ContentCatalog.FactionDefinition faction : checkedContent.getFactions()) {
            if (visited.add(faction.id())) {
                checkedWorld.findFactionStrategicState(faction.id()).ifPresent(strategies::add);
            }
        }
        for (WorldFactionIdentityState identity : checkedWorld.getWorldFactionIdentities()) {
            if (visited.add(identity.stableFactionId())) {
                checkedWorld.findFactionStrategicState(identity.stableFactionId()).ifPresent(strategies::add);
            }
        }
        strategies.sort(FactionStrategicState::compareTo);

        int refreshed = 0;
        for (StarSystemNode node : checkedWorld.getTopology().systems()) {
            SimulationSession session = checkedWorld.findSession(node.id()).orElseThrow(
                    () -> new IllegalStateException("World topology lost SimulationSession: " + node.id()));
            FactionPolicyRuntime.install(
                    session,
                    resolver,
                    strategies,
                    checkedWorld.getFactionDiplomacyStates(),
                    checkedWorld.getAuthoritativeWorldTick());
            refreshed++;
        }
        return refreshed;
    }
}
