package com.spacesim.campaign;

import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.StarSystemId;
import java.util.*;

/** Explicit new-game legal associations; never creates physical or financial capital. */
final class GeneratedCampaignInitialStationLegalCapital {
    static final String VERSION = "stage23b.initial-npc-station-legal-capital.v1";
    static final Set<String> ROLES = Set.of("station.infrastructure.refinery_complex",
            "station.infrastructure.frontier_multipurpose");
    record Plan(Map<String, String> assignments, List<String> unfilledSlots) {
        Plan { assignments = Map.copyOf(assignments); unfilledSlots = List.copyOf(unfilledSlots); }
        static Plan baseline() { return new Plan(Map.of(), List.of()); }
    }
    record Asset(String stationId, StarSystemId systemId, String role, Optional<String> existingOwner) { }
    private record Candidate(int hops, Asset asset, String faction) { }
    private GeneratedCampaignInitialStationLegalCapital() { }

    static Plan plan(LiveRuntime runtime, Stage228CampaignAuthority.StationLegalCapitalPolicy policy) {
        if (policy == Stage228CampaignAuthority.StationLegalCapitalPolicy.BASELINE) return Plan.baseline();
        var starts = new TreeMap<String, StarSystemId>();
        for (var row : runtime.captureState().campaign().materializedWorld().worldRows())
            if (row.domain().equals("FACTION_START") && runtime.world().findFactionEconomicState(row.stableId()).isPresent())
                starts.put(row.stableId(), new StarSystemId(Long.parseLong(row.values().get(0))));
        var assets = runtime.infrastructure().endpoints().stream().map(e ->
                new Asset(e.stationId(), e.systemId(), e.stationArchetypeId(), existingOwner(runtime, e))).toList();
        return select(runtime.world().getTopology(), starts, assets);
    }

    static Optional<String> existingOwner(LiveRuntime runtime, RuntimeEndpoint endpoint) {
        return runtime.industry().industrial().stations().stream().filter(s -> s.stationId().equals(endpoint.stationId()))
                .map(s -> s.stableFactionId()).findFirst().or(() -> runtime.world().controllingFaction(endpoint.systemId()));
    }

    static Optional<String> owner(LiveRuntime runtime, RuntimeEndpoint endpoint, Plan plan) {
        // A later territorial change does not transfer an explicitly authored economic asset.
        return runtime.industry().industrial().stations().stream().filter(s -> s.stationId().equals(endpoint.stationId()))
                .map(s -> s.stableFactionId()).findFirst()
                .or(() -> Optional.ofNullable(plan.assignments().get(endpoint.stationId())))
                .or(() -> runtime.world().controllingFaction(endpoint.systemId()));
    }

    static Plan select(GalaxyTopology topology, Map<String, StarSystemId> starts, List<Asset> assets) {
        var slots = new TreeSet<String>();
        for (var faction : starts.keySet()) for (var role : ROLES) slots.add(slot(faction, role));
        for (var asset : assets) asset.existingOwner().ifPresent(owner -> slots.remove(slot(owner, asset.role())));
        var candidates = new ArrayList<Candidate>();
        for (var start : starts.entrySet()) {
            var distances = new HashMap<StarSystemId, Integer>();
            var queue = new ArrayDeque<StarSystemId>();
            distances.put(start.getValue(), 0); queue.add(start.getValue());
            while (!queue.isEmpty()) {
                var current = queue.remove();
                for (var next : topology.neighbors(current)) if (!distances.containsKey(next)) {
                    distances.put(next, distances.get(current) + 1); queue.add(next);
                }
            }
            for (var asset : assets) if (asset.existingOwner().isEmpty() && ROLES.contains(asset.role())
                    && slots.contains(slot(start.getKey(), asset.role())) && distances.containsKey(asset.systemId()))
                candidates.add(new Candidate(distances.get(asset.systemId()), asset, start.getKey()));
        }
        candidates.sort(Comparator.comparingInt(Candidate::hops).thenComparing(c -> c.asset().stationId())
                .thenComparing(Candidate::faction));
        var assignments = new TreeMap<String, String>();
        for (var candidate : candidates) if (!assignments.containsKey(candidate.asset().stationId())
                && slots.remove(slot(candidate.faction(), candidate.asset().role())))
            assignments.put(candidate.asset().stationId(), candidate.faction());
        return new Plan(assignments, List.copyOf(slots));
    }

    private static String slot(String faction, String role) { return faction + '|' + role; }
}
