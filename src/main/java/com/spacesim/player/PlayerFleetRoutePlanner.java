package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.flight.FlightDynamics;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.JumpTransitTiming;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Deterministic Stage-15 route planner over discovered topology and stored player intelligence.
 *
 * <p>Every candidate path accumulates danger for all traversed systems and links; destination-only
 * scoring is impossible by construction. Observed danger is confidence- and age-weighted. Unknown
 * segments carry an explicit uncertainty premium rather than being assumed safe. Raw danger remains
 * an arbitrary exposure score, never a probability.</p>
 *
 * <p>The risk multiplier is actor-specific: current real cargo utilization, current damage and
 * shared inertial acceleration affect willingness to use a dangerous route. The planner never
 * reads combatants in unobserved remote systems.</p>
 */
public final class PlayerFleetRoutePlanner {
    private static final double RISK_TO_TICKS = 35d;
    private static final double UNKNOWN_SYSTEM_EXPOSURE = 0.65d;
    private static final double UNKNOWN_LINK_EXPOSURE = 0.45d;
    private static final long INTEL_HALF_LIFE_TICKS = 3_000L;
    private static final double COST_EPSILON = 1e-9d;

    private final PlayerRuntime runtime;
    private final WorldSimulation world;
    private final ContentCatalog content;

    /**
     * Creates a route planner for current player knowledge and physical fleet state.
     *
     * @param runtime current playable runtime
     */
    public PlayerFleetRoutePlanner(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.world = runtime.world();
        this.content = runtime.content();
    }

    /**
     * Chooses the lowest travel+risk-cost path using only discovered systems and stored intel.
     *
     * @param fleetId actor whose real cargo/damage/mobility determine vulnerability
     * @param origin discovered origin system
     * @param destination discovered destination system
     * @return full route diagnostics, or empty when no discovered route exists
     */
    public Optional<PlayerRouteRiskView> plan(
            FleetId fleetId,
            StarSystemId origin,
            StarSystemId destination) {
        FleetId actor = Objects.requireNonNull(fleetId, "Route fleet not set");
        StarSystemId from = Objects.requireNonNull(origin, "Route origin not set");
        StarSystemId to = Objects.requireNonNull(destination, "Route destination not set");
        PlayerState player = runtime.player();
        Set<StarSystemId> discovered = new HashSet<>(player.discoveredSystemIds());
        if (!discovered.contains(from) || !discovered.contains(to)) {
            return Optional.empty();
        }
        if (from.equals(to)) {
            double vulnerability = vulnerability(actor);
            return Optional.of(new PlayerRouteRiskView(
                    List.of(from), 0L, 0d, 0d, 0d, vulnerability, 0d, 0d));
        }

        long currentTick = currentTick();
        double vulnerability = vulnerability(actor);
        PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator
                .comparingDouble(Node::totalCost)
                .thenComparing(Node::path, PlayerFleetRoutePlanner::comparePaths));
        Map<StarSystemId, Node> best = new HashMap<>();
        Node start = new Node(from, List.of(from), 0L, 0d, 0d, 0d, 0d, 0d);
        frontier.add(start);
        best.put(from, start);

        while (!frontier.isEmpty()) {
            Node current = frontier.poll();
            if (best.get(current.system()) != current) {
                continue;
            }
            if (current.system().equals(to)) {
                double riskCost = (current.systemExposure()
                        + current.linkExposure()
                        + current.uncertaintyExposure()) * vulnerability * RISK_TO_TICKS;
                return Optional.of(new PlayerRouteRiskView(
                        current.path(),
                        current.travelTicks(),
                        current.systemExposure(),
                        current.linkExposure(),
                        current.uncertaintyExposure(),
                        vulnerability,
                        riskCost,
                        current.travelTicks() + riskCost));
            }

            List<StarSystemId> neighbors = new ArrayList<>(world.getTopology().neighbors(current.system()));
            neighbors.sort(Comparator.naturalOrder());
            for (StarSystemId neighbor : neighbors) {
                if (!discovered.contains(neighbor)) {
                    continue;
                }
                EdgeCost edge = edgeCost(player, current.system(), neighbor, currentTick, vulnerability);
                List<StarSystemId> path = new ArrayList<>(current.path());
                path.add(neighbor);
                long travelTicks = Math.addExact(current.travelTicks(), edge.travelTicks());
                double systemExposure = current.systemExposure() + edge.systemExposure();
                double linkExposure = current.linkExposure() + edge.linkExposure();
                double uncertainty = current.uncertaintyExposure() + edge.uncertaintyExposure();
                double riskCost = (systemExposure + linkExposure + uncertainty)
                        * vulnerability * RISK_TO_TICKS;
                double totalCost = travelTicks + riskCost;
                Node candidate = new Node(
                        neighbor,
                        List.copyOf(path),
                        travelTicks,
                        systemExposure,
                        linkExposure,
                        uncertainty,
                        riskCost,
                        totalCost);
                Node previous = best.get(neighbor);
                if (previous == null || better(candidate, previous)) {
                    best.put(neighbor, candidate);
                    frontier.add(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private EdgeCost edgeCost(
            PlayerState player,
            StarSystemId from,
            StarSystemId to,
            long currentTick,
            double vulnerability) {
        float fixedStep = world.findSession(from).orElseThrow().getClock().getFixedStepSeconds();
        long transit = JumpTransitTiming.DEFAULT.transitTicks(world.getTopology(), from, to, fixedStep);
        long travelTicks = Math.addExact(transit,
                JumpTransitTiming.DEFAULT.approachTicks()
                        + JumpTransitTiming.DEFAULT.pendingTicks()
                        + JumpTransitTiming.DEFAULT.arrivalTicks());

        PlayerThreatIntelState systemIntel = findSystemIntel(player, to);
        PlayerThreatIntelState linkIntel = findLinkIntel(player, from, to);
        double systemExposure = systemIntel == null
                ? 0d : effectiveDanger(systemIntel, currentTick);
        double linkTimeScale = Math.max(1d, transit * fixedStep / 10d);
        double linkExposure = linkIntel == null
                ? 0d : effectiveDanger(linkIntel, currentTick) * linkTimeScale;
        double uncertainty = (systemIntel == null ? UNKNOWN_SYSTEM_EXPOSURE : 0d)
                + (linkIntel == null ? UNKNOWN_LINK_EXPOSURE * linkTimeScale : 0d);
        return new EdgeCost(travelTicks, systemExposure, linkExposure, uncertainty * Math.max(1d, vulnerability * 0.15d));
    }

    private static PlayerThreatIntelState findSystemIntel(PlayerState player, StarSystemId systemId) {
        for (PlayerThreatIntelState intel : player.threatIntel()) {
            if (intel.kind() == PlayerThreatIntelKind.SYSTEM && intel.systemA().equals(systemId)) {
                return intel;
            }
        }
        return null;
    }

    private static PlayerThreatIntelState findLinkIntel(
            PlayerState player,
            StarSystemId first,
            StarSystemId second) {
        for (PlayerThreatIntelState intel : player.threatIntel()) {
            if (intel.matchesLink(first, second)) {
                return intel;
            }
        }
        return null;
    }

    private static double effectiveDanger(PlayerThreatIntelState intel, long currentTick) {
        long age = Math.max(0L, currentTick - intel.observedTick());
        double ageFactor = Math.pow(0.5d, age / (double) INTEL_HALF_LIFE_TICKS);
        return intel.dangerScore() * intel.confidence() * ageFactor;
    }

    private long currentTick() {
        long maximum = 0L;
        for (var system : world.getTopology().systems()) {
            maximum = Math.max(maximum, world.findSession(system.id()).orElseThrow().getClock().getTick());
        }
        return maximum;
    }

    private double vulnerability(FleetId fleetId) {
        FleetPlacementState placement = world.findFleet(fleetId).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return 1d;
        }
        SimulationSession session = world.findSession(placement.systemId()).orElse(null);
        Entity entity = session == null ? null : session.getEntityRegistry().find(placement.localEntityId());
        if (entity == null) {
            return 1d;
        }
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        double cargoUtilization = inventory == null || inventory.capacity <= 0
                ? 0d : Math.min(1d, inventory.getTotalStock() / (double) inventory.capacity);
        CombatComponent combat = entity.getComponent(CombatComponent.class);
        double damageFraction = 0d;
        if (combat != null && combat.maxHull > 0f) {
            double hullDamage = 1d - Math.max(0d, Math.min(1d, combat.hull / combat.maxHull));
            double shieldDamage = combat.maxShields <= 0f
                    ? 0d : 1d - Math.max(0d, Math.min(1d, combat.shields / combat.maxShields));
            damageFraction = hullDamage + shieldDamage * 0.5d;
        }
        double mobilityPenalty = 0d;
        float speedCap = movementSpeed(entity);
        if (speedCap > 0f) {
            try {
                FlightDynamics.Profile profile = FlightDynamics.profile(entity, speedCap);
                mobilityPenalty = Math.max(0d, Math.min(1.5d, 50d / profile.acceleration() - 1d));
            } catch (IllegalArgumentException ignored) {
                mobilityPenalty = 0d;
            }
        }
        return Math.max(0.5d, 1d + cargoUtilization * 1.5d + damageFraction * 2d + mobilityPenalty);
    }

    private float movementSpeed(Entity entity) {
        TradeAIComponent trade = entity.getComponent(TradeAIComponent.class);
        if (trade != null && Float.isFinite(trade.movementSpeed) && trade.movementSpeed > 0f) {
            return trade.movementSpeed;
        }
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        if (mining != null && Float.isFinite(mining.movementSpeed) && mining.movementSpeed > 0f) {
            return mining.movementSpeed;
        }
        ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
        ContentCatalog.ShipArchetypeDefinition ship = archetype == null
                ? null : content.findShipArchetype(archetype.contentId);
        return ship == null ? 0f : ship.movementSpeed();
    }

    private static boolean better(Node candidate, Node previous) {
        if (candidate.totalCost() + COST_EPSILON < previous.totalCost()) {
            return true;
        }
        return Math.abs(candidate.totalCost() - previous.totalCost()) <= COST_EPSILON
                && comparePaths(candidate.path(), previous.path()) < 0;
    }

    private static int comparePaths(List<StarSystemId> first, List<StarSystemId> second) {
        int shared = Math.min(first.size(), second.size());
        for (int index = 0; index < shared; index++) {
            int compared = first.get(index).compareTo(second.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(first.size(), second.size());
    }

    private record EdgeCost(
            long travelTicks,
            double systemExposure,
            double linkExposure,
            double uncertaintyExposure) {
    }

    private record Node(
            StarSystemId system,
            List<StarSystemId> path,
            long travelTicks,
            double systemExposure,
            double linkExposure,
            double uncertaintyExposure,
            double riskCost,
            double totalCost) {
    }
}
