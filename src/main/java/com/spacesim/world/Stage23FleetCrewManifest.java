package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import java.util.List;
import java.util.Objects;

/** Durable commissioned staffing inputs for the existing Stage-21D readiness evaluator. */
public record Stage23FleetCrewManifest(String profileVersion, List<Entry> entries) {
    /** Explicit new-game staffing policy; never inferred while loading a historical fleet. */
    public static final String COMMISSIONED_PROFILE = "stage23b.new-game-commissioned-crews.v1";
    /** Historical files contain no commissioned staffing observation. */
    public static final String UNOBSERVED_PROFILE = "stage23b.unobserved-crews.v1";
    /** Bounded ordinary-fleet roster size. */
    public static final int MAX_ENTRIES = 100_000;

    /**
     * @param profileVersion recognized source of declared staffing
     * @param entries exact recorded individual fleet inputs
     */
    public Stage23FleetCrewManifest {
        Objects.requireNonNull(profileVersion); Objects.requireNonNull(entries);
        if (!COMMISSIONED_PROFILE.equals(profileVersion) && !UNOBSERVED_PROFILE.equals(profileVersion))
            throw new IllegalArgumentException("Unknown crew commissioning profile");
        if (entries.size() > MAX_ENTRIES || UNOBSERVED_PROFILE.equals(profileVersion) && !entries.isEmpty())
            throw new IllegalArgumentException("Crew roster exceeds bounds or claims unobserved staffing");
        entries = entries.stream().sorted(java.util.Comparator.comparing(Entry::fleetId)).toList();
        for (int i = 1; i < entries.size(); i++) if (entries.get(i - 1).fleetId().equals(entries.get(i).fleetId()))
            throw new IllegalArgumentException("Duplicate commissioned crew FleetId");
    }

    /**
     * @param fleetId original commissioned hull; personnel stay with that existing identity
     * @param availableCrew explicit finite staffing declared at commissioning
     */
    public record Entry(FleetId fleetId, int availableCrew) {
        /**
         * @param fleetId existing ordinary fleet identity
         * @param availableCrew declared available trained crew
         */
        public Entry {
            Objects.requireNonNull(fleetId);
            if (availableCrew < 0 || availableCrew > 1_000_000)
                throw new IllegalArgumentException("Crew availability outside finite bounds");
        }
    }

    /** @return historical unknown staffing, without a commissioning event */
    public static Stage23FleetCrewManifest unobserved() { return new Stage23FleetCrewManifest(UNOBSERVED_PROFILE, List.of()); }

    /**
     * Explicitly staffs the newly authored initial fleet at its reviewed nominal requirement.
     * This is a new-game input policy, not a runtime inference that capacity means actual personnel.
     * Unfitted historical/prototype hulls receive no observation. No other ship input is changed.
     * @param world newly created ordinary world at tick zero
     * @return explicit initial crew manifest, to be persisted thereafter
     */
    public static Stage23FleetCrewManifest commissionNewGame(WorldSimulation world) {
        if (world.getAuthoritativeWorldTick() != 0) throw new IllegalStateException("Initial staffing requires a new world at tick zero");
        var engineering = new ProductionEngineeringRuntimeResolver();
        var result = new java.util.ArrayList<Entry>();
        for (var fleet : world.getFleetPlacements()) {
            if (fleet.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            var fitted = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                    .getComponent(EngineeringComponent.class);
            if (fitted == null) continue;
            var capability = engineering.derive(fitted);
            if (capability.crewRequired() > capability.crewSupported())
                throw new IllegalStateException("Initial commissioning exceeds reviewed crew accommodation");
            result.add(new Entry(fleet.id(), capability.crewRequired()));
        }
        return new Stage23FleetCrewManifest(COMMISSIONED_PROFILE, result);
    }

    /**
     * Looks up only an explicitly recorded count. Caller must still validate the current live hull and ownership.
     * @param fleetId exact commissioned identity
     * @return known crew count or zero when no observation exists
     */
    public int availableCrew(FleetId fleetId) {
        return observedCrew(fleetId).orElse(0);
    }

    /**
     * @param fleetId exact commissioned identity
     * @return recorded staffing, including a known zero, or no observation
     */
    public java.util.OptionalInt observedCrew(FleetId fleetId) {
        Objects.requireNonNull(fleetId);
        int low = 0, high = entries.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1; var row = entries.get(middle);
            int compared = row.fleetId().compareTo(fleetId);
            if (compared == 0) return java.util.OptionalInt.of(row.availableCrew());
            if (compared < 0) low = middle + 1; else high = middle - 1;
        }
        return java.util.OptionalInt.empty();
    }
}
