package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.ConstructionSettlementKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16PlayerConstructionAuthoringAcceptanceTest {
    @Test
    void independentPlayerCreatesPhysicalExternalProjectAndPersistsOwnership() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_201L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState original = runtime.player();
        PlayerState independent = new PlayerState(
                original.walletMilliCredits(),
                null,
                original.reputations(),
                original.ownedFleetIds(),
                original.activeFleetId(),
                original.discoveredSystemIds(),
                original.discoveredObjects(),
                original.homeSystemId(),
                original.dockedAt(),
                original.fleetOrders(),
                original.threatIntel(),
                original.ownedConstructionProjectIds(),
                original.ownedStations());
        runtime.replacePlayerState(independent);

        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionArchetypeView miningBase = construction.buildableArchetypes().stream()
                .filter(view -> "station.mining_base".equals(view.archetypeContentId()))
                .findFirst()
                .orElseThrow();

        ConstructionProjectId projectId = construction.createProject(
                miningBase.archetypeContentId(), 520f, 480f);
        ConstructionProjectState project = runtime.world().findConstructionProject(projectId).orElseThrow();

        assertEquals(ConstructionSettlementKind.EXTERNAL_OWNER, project.settlementKind());
        assertNull(project.ownerFactionContentId());
        assertNull(project.legalFactionContentId());
        assertEquals(ConstructionProjectStatus.PLANNED, project.status());
        assertTrue(runtime.player().ownedConstructionProjectIds().contains(projectId));
        assertTrue(runtime.player().ownedStations().isEmpty());
        assertEquals(miningBase.minimumFundingMilliCredits(), project.minimumFundingMilliCredits());
        assertTrue(project.buildDurationTicks() > 0L);

        Entity site = runtime.world().findSession(project.systemId()).orElseThrow()
                .getEntityRegistry().find(project.constructionSiteEntityId());
        assertTrue(site != null);
        assertNull(site.getComponent(FactionComponent.class));

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        ConstructionProjectState restoredProject = decoded.worldState().constructionProjects().stream()
                .filter(candidate -> candidate.id().equals(projectId))
                .findFirst()
                .orElseThrow();
        assertEquals(project, restoredProject);
        assertEquals(List.of(projectId), decoded.playerState().ownedConstructionProjectIds());
        assertFalse(decoded.playerState().affiliated());
    }

    @Test
    void authoringRequiresPhysicalActiveFleetAndExposesCalculatedOptions() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_202L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerConstructionService construction = new PlayerConstructionService(runtime);

        List<PlayerConstructionArchetypeView> options = construction.buildableArchetypes();

        assertFalse(options.isEmpty());
        assertTrue(options.stream().allMatch(option -> option.minimumFundingMilliCredits() > 0L));
        assertTrue(options.stream().allMatch(option -> option.materialWorkUnits() > 0d));
        assertTrue(options.stream().allMatch(option -> option.estimatedBuildSeconds() > 0d));
        assertTrue(options.stream().allMatch(option -> !option.requiredMaterials().isEmpty()));
    }
}
