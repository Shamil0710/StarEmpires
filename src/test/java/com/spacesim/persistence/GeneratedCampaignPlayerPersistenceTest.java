package com.spacesim.persistence;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.campaign.GeneratedCampaignFixture;
import com.spacesim.player.DiscoveredObjectRef;
import com.spacesim.player.FleetOrderType;
import com.spacesim.player.OwnedStationRef;
import com.spacesim.player.PlayerFleetOrderState;
import com.spacesim.player.PlayerReputationState;
import com.spacesim.player.PlayerState;
import com.spacesim.player.PlayerThreatIntelKind;
import com.spacesim.player.PlayerThreatIntelState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignPlayerPersistenceTest {
    @Test
    void playerPayloadPreservesEveryExistingPlayerFieldAndCanonicalBytes() {
        var system = new StarSystemId(1L);
        var second = new StarSystemId(2L);
        var fleet = new FleetId(1L);
        var dock = new DiscoveredObjectRef(system, new EntityId(9L));
        var order = new PlayerFleetOrderState(fleet, FleetOrderType.MOVE,
                system, null, null, null, null, null, 12f, 14f, List.of());
        var player = new PlayerState(1_234_567L, "faction.alpha",
                List.of(new PlayerReputationState("faction.beta", -4f)),
                List.of(fleet), fleet, List.of(second, system), List.of(dock), system, dock,
                List.of(order), List.of(new PlayerThreatIntelState(
                        PlayerThreatIntelKind.LINK, second, system, 5f, 0.5f, 9L)),
                List.of(new ConstructionProjectId(6L)),
                List.of(new OwnedStationRef(system, new EntityId(11L))));
        byte[] bytes = GeneratedCampaignPlayerStateCodec.encode(player);
        var restored = GeneratedCampaignPlayerStateCodec.decode(bytes);
        assertEquals(player, restored);
        assertArrayEquals(bytes, GeneratedCampaignPlayerStateCodec.encode(restored));
        assertFalse(Arrays.equals(bytes, GeneratedCampaignPlayerStateCodec.encode(null)));
    }

    @Test
    void explicitAbsentPlayerIsDifferentFromInitializedZeroWallet() {
        assertEquals(null, GeneratedCampaignPlayerStateCodec.decode(
                GeneratedCampaignPlayerStateCodec.encode(null)));
        var zero = new PlayerState(0L, null, List.of(), List.of(), null,
                List.of(), List.of(), null);
        assertEquals(zero, GeneratedCampaignPlayerStateCodec.decode(
                GeneratedCampaignPlayerStateCodec.encode(zero)));
    }

    @Test
    void playerPayloadRejectsCorruptTruncatedFutureAndTrailingBytes() {
        byte[] absent = GeneratedCampaignPlayerStateCodec.encode(null);
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCampaignPlayerStateCodec.decode(new byte[0]));
        for (int length = 1; length < absent.length; length++) {
            byte[] truncated = Arrays.copyOf(absent, length);
            assertThrows(IllegalArgumentException.class,
                    () -> GeneratedCampaignPlayerStateCodec.decode(truncated));
        }
        for (int offset : new int[]{0, 4, 8}) {
            byte[] corrupt = absent.clone();
            ByteBuffer.wrap(corrupt).putInt(offset, Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,
                    () -> GeneratedCampaignPlayerStateCodec.decode(corrupt));
        }
        byte[] badPresence = absent.clone();
        badPresence[12] = 2;
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCampaignPlayerStateCodec.decode(badPresence));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCampaignPlayerStateCodec.decode(Arrays.copyOf(absent, absent.length + 1)));
        byte[] negativeWallet = GeneratedCampaignPlayerStateCodec.encode(new PlayerState(
                1L, null, List.of(), List.of(), null, List.of(), List.of(), null));
        ByteBuffer.wrap(negativeWallet).putLong(13, -1L);
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedCampaignPlayerStateCodec.decode(negativeWallet));
    }

    @Test
    void generatedCheckpointRetainsPlayerWithoutChangingWorldOrGrantingAffiliation() {
        var authority = GeneratedCampaignFixture.restoreAuthority();
        var baseline = authority.captureState();
        var home = authority.coordinator().runtime().world().getActiveSystemId();
        var player = independent(home, 123_000L);
        var composed = withPlayer(baseline, player);
        var decoded = Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(composed));
        assertEquals(composed, decoded);
        var restored = Stage228CampaignAuthority.restore(decoded);
        assertEquals(player, restored.playerState().orElseThrow());
        assertEquals(decoded, restored.captureState());
        assertEquals(baseline.stage21Runtime(), decoded.stage21Runtime());
        assertTrue(player.ownedFleetIds().isEmpty());
        assertFalse(player.affiliated());
        assertTrue(authority.playerState().isEmpty());
    }

    @Test
    void v4MigrationPreservesAllSidecarsAndWorldWithoutInitializingPlayer() throws Exception {
        var baseline = GeneratedCampaignFixture.checkpoint();
        var craft = com.spacesim.world.SmallCraftRegistry.empty(
                com.spacesim.world.ProductionSmallCraftFixture.fitAuthority());
        var id = craft.reserveIdentityForCompletedProduction();
        craft.registerProducedCraft(com.spacesim.world.ProductionSmallCraftFixture.craft(
                id, 6L, 60d, 300d, 0.75d, 20d));
        craft.reserveIdentityForCompletedProduction();
        var state = Stage228GeneratedCampaignPersistentState.compose(
                baseline.stage21Runtime(), Stage228SmallCraftPersistenceMapper.capture(craft),
                baseline.hangars(), baseline.flightDeck(), baseline.operations());
        byte[] original = legacyV4(state);
        byte[] untouched = original.clone();
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decodeOrMigrate(original);
        assertArrayEquals(untouched, original);
        assertEquals(state, migrated);
        assertEquals(null, migrated.playerState());
        assertTrue(Stage228CampaignAuthority.restore(migrated).playerState().isEmpty());
        assertArrayEquals(Stage228GeneratedCampaignPersistenceCodec.encode(migrated),
                Stage228GeneratedCampaignPersistenceCodec.encode(state));
        byte[] mismatch = original.clone();
        ByteBuffer.wrap(mismatch).putInt(8, 5);
        assertThrows(IllegalArgumentException.class,
                () -> Stage228GeneratedCampaignPersistenceCodec.decode(mismatch));
    }

    @Test
    void olderStage21MigrationAlsoKeepsPlayerAbsent() {
        var state = GeneratedCampaignFixture.checkpoint();
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decodeOrMigrate(
                Stage21IGeneratedWorldRuntimePersistenceCodec.encode(state.stage21Runtime()));
        assertEquals(null, migrated.playerState());
        assertEquals(state.stage21Runtime(), migrated.stage21Runtime());
    }

    @Test
    void checkpointRejectsForeignReferencesAndFutureObservationsWithoutMutatingAuthority() {
        var authority = GeneratedCampaignFixture.restoreAuthority();
        var state = authority.captureState();
        var home = authority.coordinator().runtime().world().getActiveSystemId();
        var nonexistent = new FleetId(Long.MAX_VALUE);
        var absentFleet = new PlayerState(0L, null, List.of(), List.of(nonexistent), nonexistent,
                List.of(home), List.of(), home);
        assertThrows(IllegalArgumentException.class, () -> withPlayer(state, absentFleet));
        assertThrows(IllegalArgumentException.class,
                () -> withPlayer(state, independent(new StarSystemId(Long.MAX_VALUE), 0L)));
        var unknownFaction = new PlayerState(0L, "faction.absent", List.of(), List.of(), null,
                List.of(home), List.of(), home);
        assertThrows(IllegalArgumentException.class, () -> withPlayer(state, unknownFaction));
        var futureIntel = new PlayerState(0L, null, List.of(), List.of(), null,
                List.of(home), List.of(), home, null, List.of(), List.of(
                        new PlayerThreatIntelState(PlayerThreatIntelKind.SYSTEM,
                                home, null, 1f, 1f, Long.MAX_VALUE)));
        assertThrows(IllegalArgumentException.class, () -> withPlayer(state, futureIntel));
        var dock = new DiscoveredObjectRef(home, new EntityId(Long.MAX_VALUE));
        var impossibleDock = new PlayerState(0L, null, List.of(), List.of(), null,
                List.of(home), List.of(dock), home, dock);
        assertThrows(IllegalArgumentException.class, () -> withPlayer(state, impossibleDock));
        assertEquals(state, authority.captureState());
    }

    @Test
    void playerAndCampaignContinuationShareTheSameCheckpointAndClock() {
        var initial = GeneratedCampaignFixture.restoreAuthority();
        var home = initial.coordinator().runtime().world().getActiveSystemId();
        var authority = Stage228CampaignAuthority.restore(withPlayer(
                initial.captureState(), independent(home, 456_000L)));
        authority.advanceFrame(0.17f);
        var checkpoint = authority.captureState();
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(checkpoint)));
        for (float delta : new float[]{0.1f, 0.2f, 0.17f, 0.3f}) {
            authority.advanceFrame(delta);
            restored.advanceFrame(delta);
        }
        assertEquals(authority.captureState(), restored.captureState());
        assertEquals(456_000L, restored.playerState().orElseThrow().walletMilliCredits());
        assertTrue(restored.playerState().orElseThrow().ownedFleetIds().isEmpty());
    }

    @Test
    void currentEnvelopeRejectsMissingOrOversizedPlayerPayloadAndFutureFile() {
        var state = GeneratedCampaignFixture.checkpoint();
        byte[] valid = Stage228GeneratedCampaignPersistenceCodec.encode(state);
        int playerLength = GeneratedCampaignPlayerStateCodec.encode(null).length;
        byte[] missing = Arrays.copyOf(valid, valid.length - playerLength - Integer.BYTES);
        assertThrows(IllegalArgumentException.class,
                () -> Stage228GeneratedCampaignPersistenceCodec.decode(missing));
        byte[] corruptLength = valid.clone();
        ByteBuffer.wrap(corruptLength).putInt(valid.length - playerLength - Integer.BYTES,
                Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> Stage228GeneratedCampaignPersistenceCodec.decode(corruptLength));
        byte[] future = valid.clone();
        ByteBuffer.wrap(future).putInt(4, 6);
        assertThrows(IllegalArgumentException.class,
                () -> Stage228GeneratedCampaignPersistenceCodec.decode(future));
    }

    @Test
    void nativeV5RetainsOwnedFleetOrdersAndHistoricalDiscoveryExactly() {
        var authority = GeneratedCampaignFixture.restoreAuthority();
        var world = authority.coordinator().runtime().world();
        var fleet = world.getFleetPlacements().get(0);
        var discovery = new DiscoveredObjectRef(fleet.systemId(), new EntityId(Long.MAX_VALUE));
        var player = new PlayerState(25_000L, null, List.of(), List.of(fleet.id()), fleet.id(),
                List.of(fleet.systemId()), List.of(discovery), fleet.systemId(), null,
                List.of(new PlayerFleetOrderState(fleet.id(), FleetOrderType.MOVE,
                        fleet.systemId(), null, null, null, null, null, 10f, 20f, List.of())));
        var state = withPlayer(authority.captureState(), player);
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(state)));
        assertEquals(state, restored.captureState());
        assertEquals(player, restored.playerState().orElseThrow());
        assertTrue(authority.playerState().isEmpty(), "test checkpoint must not grant live authority ownership");
    }

    private static PlayerState independent(StarSystemId home, long wallet) {
        return new PlayerState(wallet, null, List.of(), List.of(), null, List.of(home), List.of(), home);
    }

    private static Stage228GeneratedCampaignPersistentState withPlayer(
            Stage228GeneratedCampaignPersistentState state, PlayerState player) {
        return Stage228GeneratedCampaignPersistentState.compose(state.stage21Runtime(), state.smallCraft(),
                state.hangars(), state.flightDeck(), state.operations(), player);
    }

    private static byte[] legacyV4(Stage228GeneratedCampaignPersistentState state) throws Exception {
        var buffer = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(buffer)) {
            out.writeInt(0x53323843);
            out.writeInt(4);
            out.writeInt(4);
            out.writeUTF("m22.8.generated-campaign.v4");
            for (byte[] payload : List.of(
                    Stage21IGeneratedWorldRuntimePersistenceCodec.encode(state.stage21Runtime()),
                    Stage228SmallCraftPersistenceCodec.encode(state.smallCraft()),
                    Stage228HangarPersistenceCodec.encode(state.hangars()),
                    Stage228FlightDeckPersistenceCodec.encode(state.flightDeck()),
                    Stage228OperationsPersistenceCodec.encode(state.operations()))) {
                out.writeInt(payload.length);
                out.write(payload);
            }
        }
        return buffer.toByteArray();
    }
}
