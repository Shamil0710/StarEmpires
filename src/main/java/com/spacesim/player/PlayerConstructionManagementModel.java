package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.FleetId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Authoritative Stage-16 read model for player construction and completed-station management.
 *
 * <p>The model reads ordinary world entities plus the playable ownership envelope and returns no
 * mutable ECS object. Project wallet values come from the live physical site when it exists;
 * progress comes from the target system's authoritative simulation tick and the persisted
 * {@code buildStartedTick/buildDurationTicks}. Completed station balances likewise come from their
 * real operating {@link WalletComponent}.</p>
 */
public final class PlayerConstructionManagementModel {
    private final PlayerRuntime runtime;
    private final ContentCatalog content;
    private final PlayerConstructionCancellationService cancellation;

    /**
     * Creates the management read model for one playable runtime.
     *
     * @param runtime current authoritative player/world runtime
     */
    public PlayerConstructionManagementModel(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.content = runtime.content();
        this.cancellation = new PlayerConstructionCancellationService(runtime);
    }

    /**
     * Captures all currently live player-owned projects and completed stations.
     *
     * @return immutable canonical management snapshot
     */
    public PlayerConstructionManagementSnapshot capture() {
        PlayerState player = runtime.player();
        List<PlayerConstructionProjectView> projects = new ArrayList<>();
        for (ConstructionProjectId projectId : player.ownedConstructionProjectIds()) {
            ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElse(null);
            if (project != null && !terminal(project.status())) {
                projects.add(projectView(player, project));
            }
        }
        List<PlayerOwnedStationView> stations = new ArrayList<>();
        for (OwnedStationRef station : player.ownedStations()) {
            PlayerOwnedStationView view = stationView(station);
            if (view != null) {
                stations.add(view);
            }
        }
        return new PlayerConstructionManagementSnapshot(projects, stations);
    }

    private PlayerConstructionProjectView projectView(PlayerState player, ConstructionProjectState project) {
        ContentCatalog.StationArchetypeDefinition archetype =
                content.findStationArchetype(project.stationArchetypeContentId());
        if (archetype == null) {
            throw new IllegalStateException("Owned construction project references unknown station archetype: "
                    + project.stationArchetypeContentId());
        }
        SimulationSession session = runtime.world().findSession(project.systemId()).orElseThrow();
        Entity site = session.getEntityRegistry().find(project.constructionSiteEntityId());
        WalletComponent wallet = site == null ? null : site.getComponent(WalletComponent.class);
        long siteBalance = wallet == null ? project.projectWalletMilliCredits() : wallet.getBalanceMilliCredits();
        List<PlayerConstructionMaterialView> materials = new ArrayList<>();
        for (ConstructionMaterialState material : project.materials()) {
            ContentCatalog.ItemDefinition item = content.findItem(material.itemContentId());
            if (item == null) {
                throw new IllegalStateException("Owned construction project references unknown item: "
                        + material.itemContentId());
            }
            materials.add(new PlayerConstructionMaterialView(
                    item.id(),
                    item.displayName(),
                    material.requiredAmount(),
                    material.deliveredAmount(),
                    material.remainingAmount()));
        }
        long elapsed = elapsedBuildTicks(project, session.getClock().getTick());
        long remaining = project.buildDurationTicks() - elapsed;
        double progress = (double) elapsed / (double) project.buildDurationTicks();
        List<FleetId> supplyFleets = supplyFleets(player, project);
        return new PlayerConstructionProjectView(
                project.id(),
                archetype.id(),
                archetype.displayName(),
                project.systemId(),
                project.constructionSiteEntityId(),
                project.x(),
                project.y(),
                project.status(),
                project.minimumFundingMilliCredits(),
                siteBalance,
                Math.max(0L, project.minimumFundingMilliCredits() - siteBalance),
                materials,
                project.buildDurationTicks(),
                elapsed,
                remaining,
                progress,
                project.legalFactionContentId(),
                ConstructionAccessPolicy.allows(runtime.world(), player, project.systemId()),
                cancellation.preview(project.id()),
                supplyFleets);
    }

    private PlayerOwnedStationView stationView(OwnedStationRef reference) {
        SimulationSession session = runtime.world().findSession(reference.systemId()).orElse(null);
        Entity station = session == null ? null : session.getEntityRegistry().find(reference.stationEntityId());
        ArchetypeComponent archetypeComponent = station == null ? null : station.getComponent(ArchetypeComponent.class);
        TransformComponent transform = station == null ? null : station.getComponent(TransformComponent.class);
        WalletComponent wallet = station == null ? null : station.getComponent(WalletComponent.class);
        if (archetypeComponent == null || transform == null || wallet == null) {
            return null;
        }
        ContentCatalog.StationArchetypeDefinition archetype = content.findStationArchetype(archetypeComponent.contentId);
        if (archetype == null) {
            throw new IllegalStateException("Owned station references unknown archetype: " + archetypeComponent.contentId);
        }
        FactionComponent faction = station.getComponent(FactionComponent.class);
        return new PlayerOwnedStationView(
                reference,
                archetype.id(),
                archetype.displayName(),
                reference.systemId(),
                transform.position.x,
                transform.position.y,
                wallet.getBalanceMilliCredits(),
                faction == null ? null : factionContentId(faction.factionId));
    }

    private List<FleetId> supplyFleets(PlayerState player, ConstructionProjectState project) {
        List<FleetId> result = new ArrayList<>();
        for (PlayerFleetOrderState order : player.fleetOrders()) {
            if (order.type() == FleetOrderType.SUPPLY_PROJECT
                    && project.systemId().equals(order.targetSystemId())
                    && project.constructionSiteEntityId().equals(order.targetEntityId())) {
                result.add(order.fleetId());
            }
        }
        return result;
    }

    private String factionContentId(int runtimeId) {
        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            if (faction.runtimeId() == runtimeId) {
                return faction.id();
            }
        }
        throw new IllegalStateException("Owned station references unknown faction runtime ID: " + runtimeId);
    }

    private static long elapsedBuildTicks(ConstructionProjectState project, long currentTick) {
        if (project.status() != ConstructionProjectStatus.BUILDING || project.buildStartedTick() < 0L) {
            return 0L;
        }
        long raw = Math.max(0L, currentTick - project.buildStartedTick());
        return Math.min(project.buildDurationTicks(), raw);
    }

    private static boolean terminal(ConstructionProjectStatus status) {
        return status == ConstructionProjectStatus.COMPLETED
                || status == ConstructionProjectStatus.CANCELLED
                || status == ConstructionProjectStatus.FAILED;
    }
}
