package com.spacesim.player;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerRuntimeContinuationTest {
    @Test
    void saveLoadContinuationKeepsWorldAndPlayerDeterministic() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState initialWorld = DemoGalaxyFactory.createState(77_331L, content);
        FleetPlacementState placement = initialWorld.fleets().get(0);
        PlayerState initialPlayer = new PlayerState(
                250_000L,
                null,
                List.of(new PlayerReputationState("faction.trade_league", 3f)),
                List.of(placement.id()),
                placement.id(),
                List.of(placement.systemId()),
                List.of(new DiscoveredObjectRef(placement.systemId(), placement.localEntityId())),
                placement.systemId());
        WorldSimulation world = WorldSimulation.restore(
                initialWorld,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        PlayerRuntime uninterrupted = PlayerRuntime.create(world, content, initialPlayer);

        uninterrupted.advanceFrame(0.35f);
        uninterrupted.advanceFrame(0.42f);
        byte[] save = PlayableWorldStateCodec.encode(uninterrupted.snapshot());
        PlayerRuntime restored = PlayerRuntime.restore(
                PlayableWorldStateCodec.decode(save),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);

        float[] continuation = {0.17f, 0.8f, 0.05f, 0.31f};
        for (float delta : continuation) {
            uninterrupted.advanceFrame(delta);
            restored.advanceFrame(delta);
        }

        assertEquals(uninterrupted.snapshot(), restored.snapshot());
    }

    @Test
    void rejectsPlayerOwnershipReferenceToUnknownFleet() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(991L);
        PlayerState player = new PlayerState(
                0L,
                null,
                List.of(),
                List.of(new FleetId(Long.MAX_VALUE)),
                new FleetId(Long.MAX_VALUE),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);

        assertThrows(IllegalArgumentException.class, () -> PlayerRuntime.create(world, content, player));
    }
}
