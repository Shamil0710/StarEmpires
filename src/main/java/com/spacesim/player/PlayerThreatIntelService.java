package com.spacesim.player;

import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Updates persistent player threat knowledge without reading unobserved remote state.
 *
 * <p>The caller must provide an actual observation; this service never scans the galaxy. Newer
 * observations replace the same SYSTEM/LINK key. A score is not interpreted as probability.</p>
 */
public final class PlayerThreatIntelService {
    private final PlayerRuntime runtime;

    /**
     * Creates a threat-intel update service.
     *
     * @param runtime current playable runtime
     */
    public PlayerThreatIntelService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Records observed danger inside one discovered system.
     *
     * @param systemId observed system
     * @param dangerScore non-negative raw exposure score
     * @param confidence confidence in {@code [0,1]}
     * @param observedTick observation tick
     * @return true when the observation was accepted
     */
    public boolean observeSystem(
            StarSystemId systemId,
            float dangerScore,
            float confidence,
            long observedTick) {
        PlayerThreatIntelState observation = PlayerThreatIntelState.system(
                systemId, dangerScore, confidence, observedTick);
        return upsert(observation);
    }

    /**
     * Records observed danger on one discovered topology link.
     *
     * @param first first link endpoint
     * @param second second link endpoint
     * @param dangerScore non-negative raw exposure score
     * @param confidence confidence in {@code [0,1]}
     * @param observedTick observation tick
     * @return true when both endpoints are discovered and connected and the observation was accepted
     */
    public boolean observeLink(
            StarSystemId first,
            StarSystemId second,
            float dangerScore,
            float confidence,
            long observedTick) {
        PlayerThreatIntelState observation = PlayerThreatIntelState.link(
                first, second, dangerScore, confidence, observedTick);
        if (!runtime.world().getTopology().neighbors(observation.systemA()).contains(observation.systemB())) {
            return false;
        }
        return upsert(observation);
    }

    private boolean upsert(PlayerThreatIntelState observation) {
        PlayerState player = runtime.player();
        if (!player.discoveredSystemIds().contains(observation.systemA())
                || observation.systemB() != null
                && !player.discoveredSystemIds().contains(observation.systemB())) {
            return false;
        }
        List<PlayerThreatIntelState> intel = new ArrayList<>(player.threatIntel());
        for (int index = 0; index < intel.size(); index++) {
            PlayerThreatIntelState current = intel.get(index);
            if (sameKey(current, observation)) {
                if (observation.observedTick() < current.observedTick()) {
                    return false;
                }
                intel.set(index, observation);
                runtime.replacePlayerState(copy(player, intel));
                return true;
            }
        }
        intel.add(observation);
        runtime.replacePlayerState(copy(player, intel));
        return true;
    }

    private static boolean sameKey(PlayerThreatIntelState first, PlayerThreatIntelState second) {
        return first.kind() == second.kind()
                && first.systemA().equals(second.systemA())
                && Objects.equals(first.systemB(), second.systemB());
    }

    private static PlayerState copy(PlayerState player, List<PlayerThreatIntelState> intel) {
        return new PlayerState(
                player.walletMilliCredits(),
                player.factionContentId(),
                player.reputations(),
                player.ownedFleetIds(),
                player.activeFleetId(),
                player.discoveredSystemIds(),
                player.discoveredObjects(),
                player.homeSystemId(),
                player.dockedAt(),
                player.fleetOrders(),
                intel,
                player.ownedConstructionProjectIds(),
                player.ownedStations());
    }
}
