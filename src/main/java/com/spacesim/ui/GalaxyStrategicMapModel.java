package com.spacesim.ui;

import com.spacesim.content.ContentCatalog;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldSimulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds deterministic read-only global-map/faction presentation snapshots from authoritative world state. */
public final class GalaxyStrategicMapModel {
    private GalaxyStrategicMapModel() {
    }

    /**
     * Captures deterministic strategic presentation data from the current authoritative world.
     *
     * @param world authoritative world simulation
     * @param content active content catalog used to resolve authored faction names
     * @param activeSystemId currently active system, or {@code null} while no system is active
     * @param selectedNeighborId currently selected direct jump neighbor, or {@code null}
     * @return immutable strategic map snapshot
     * @throws IllegalArgumentException if the active system is unknown or the selected marker is not a direct neighbor
     */
    public static GalaxyStrategicMapSnapshot capture(
            WorldSimulation world,
            ContentCatalog content,
            StarSystemId activeSystemId,
            StarSystemId selectedNeighborId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "World simulation not set");
        ContentCatalog checkedContent = Objects.requireNonNull(content, "Content catalog not set");
        GalaxyTopology topology = checkedWorld.getTopology();
        if (activeSystemId != null && topology.findSystem(activeSystemId).isEmpty()) {
            throw new IllegalArgumentException("Active system is outside authoritative topology: " + activeSystemId);
        }
        if (selectedNeighborId != null) {
            if (activeSystemId == null || !topology.neighbors(activeSystemId).contains(selectedNeighborId)) {
                throw new IllegalArgumentException("Selected jump destination is not a direct active-system neighbor");
            }
        }

        List<GalaxyStrategicMapSnapshot.SystemView> systems = new ArrayList<>(topology.systems().size());
        for (StarSystemNode system : topology.systems()) {
            String controllerId = checkedWorld.controllingFaction(system.id()).orElse(null);
            systems.add(new GalaxyStrategicMapSnapshot.SystemView(
                    system.id(),
                    system.name(),
                    topology.sectorOf(system.id()).map(sector -> sector.name()).orElse("Unassigned"),
                    system.x(),
                    system.y(),
                    controllerId,
                    controllerId == null ? "Unclaimed" : displayName(checkedWorld, checkedContent, controllerId),
                    topology.neighbors(system.id()).size(),
                    system.id().equals(activeSystemId),
                    system.id().equals(selectedNeighborId)));
        }

        List<GalaxyStrategicMapSnapshot.EdgeView> edges = new ArrayList<>(topology.connections().size());
        for (JumpConnection connection : topology.connections()) {
            edges.add(new GalaxyStrategicMapSnapshot.EdgeView(
                    connection.first(),
                    connection.second(),
                    activeSystemId != null
                            && (connection.first().equals(activeSystemId) || connection.second().equals(activeSystemId))));
        }

        Set<String> factionIds = new LinkedHashSet<>();
        checkedContent.getFactions().stream()
                .sorted(Comparator.comparingInt(ContentCatalog.FactionDefinition::runtimeId))
                .map(ContentCatalog.FactionDefinition::id)
                .forEach(factionIds::add);
        checkedWorld.getWorldFactionIdentities().stream()
                .sorted()
                .map(WorldFactionIdentityState::stableFactionId)
                .forEach(factionIds::add);

        List<GalaxyStrategicMapSnapshot.FactionView> factions = new ArrayList<>(factionIds.size());
        for (String factionId : factionIds) {
            FactionStrategicState strategy = checkedWorld.findFactionStrategicState(factionId).orElse(null);
            FactionEconomicState economy = checkedWorld.findFactionEconomicState(factionId).orElse(null);
            FactionDiplomacyState diplomacy = checkedWorld.findFactionDiplomacyState(factionId).orElse(null);
            int controlled = strategy == null ? countControlledSystems(checkedWorld, topology, factionId)
                    : strategy.controlledSystems().size();
            if (strategy == null && economy == null && diplomacy == null && controlled == 0) {
                continue;
            }
            factions.add(new GalaxyStrategicMapSnapshot.FactionView(
                    factionId,
                    displayName(checkedWorld, checkedContent, factionId),
                    controlled,
                    economy == null ? 0L : economy.treasuryMilliCredits(),
                    strategy == null ? 0 : strategy.stationTaxBasisPoints(),
                    strategy == null ? 0 : strategy.foreignTerritoryTariffBasisPoints(),
                    diplomacy == null ? 0 : diplomacy.customsTariffBasisPoints(),
                    strategy == null ? 0 : strategy.territorialClaims().size(),
                    strategy == null ? 0 : strategy.strategicGoals().size(),
                    diplomacy == null ? 0 : diplomacy.treaties().size(),
                    diplomacy == null ? 0 : diplomacy.embargoes().size()));
        }
        factions.sort(Comparator.comparing(GalaxyStrategicMapSnapshot.FactionView::factionId));

        return new GalaxyStrategicMapSnapshot(
                topology.name(), systems, edges, factions, activeSystemId, selectedNeighborId);
    }

    private static int countControlledSystems(WorldSimulation world, GalaxyTopology topology, String factionId) {
        int count = 0;
        for (StarSystemNode system : topology.systems()) {
            if (world.controllingFaction(system.id()).filter(factionId::equals).isPresent()) {
                count++;
            }
        }
        return count;
    }

    private static String displayName(WorldSimulation world, ContentCatalog content, String factionId) {
        ContentCatalog.FactionDefinition authored = content.findFaction(factionId);
        if (authored != null) {
            return authored.displayName();
        }
        for (WorldFactionIdentityState identity : world.getWorldFactionIdentities()) {
            if (identity.stableFactionId().equals(factionId)) {
                return identity.displayName();
            }
        }
        return factionId;
    }
}
