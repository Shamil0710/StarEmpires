package com.spacesim.persistence;

import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceEstimate;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledgeLevel;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20DiscoveryPersistenceCodecTest {
    @Test
    void staticKnowledgeRoundTripsDeterministicallyWithExactWorldBinding() throws Exception {
        StaticObjectRef depositRef = new StaticObjectRef(
                new StarSystemId(20_702L),
                StaticObjectKind.RESOURCE_OCCURRENCE,
                "occurrence.volatiles.9");
        DiscoveryEvidence mapReport = new DiscoveryEvidence(
                DiscoverySource.PURCHASED_OR_SHARED_MAP_DATA,
                "map-contract-77",
                80d,
                OptionalDouble.of(180d));
        DiscoveryEvidence survey = new DiscoveryEvidence(
                DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                "survey-ship-4/report-91",
                120d,
                OptionalDouble.empty());
        ResourceKnowledge resource = new ResourceKnowledge(
                ResourceKnowledgeLevel.SURVEYED_DEPOSIT,
                Optional.of("resource.family.volatiles"),
                Optional.of(new ResourceEstimate(0.12d, 0.28d, 5.0e8d, 9.5e8d, 0.9d)));
        StaticKnowledge deposit = new StaticKnowledge(
                depositRef,
                DiscoveryState.KNOWN_STATIC_LOCATION,
                Optional.of("resource.family.volatiles"),
                Optional.of(new LocalPhysicalPosition(9_000_000L, -8_000_000L, 45_678.125d, -12_345.75d)),
                resource,
                List.of(survey, mapReport),
                80d,
                120d);
        Stage20DiscoveryKnowledgeState beta =
                new Stage20DiscoveryKnowledgeState("faction.beta", List.of(deposit));
        Stage20DiscoveryKnowledgeState alpha =
                new Stage20DiscoveryKnowledgeState("faction.alpha", List.of());
        Stage20DiscoveryPersistentState state = new Stage20DiscoveryPersistentState(
                Stage20DiscoveryPersistentState.CURRENT_VERSION,
                -9_223_372_036_854_000_000L,
                "stage20e.production-seed-probe.v1",
                "sha256:generated-world-fingerprint-20702",
                List.of(beta, alpha));

        assertEquals("faction.alpha", state.knowledgeStates().get(0).ownerId());
        byte[] first = Stage20DiscoveryPersistenceCodec.encode(state);
        byte[] second = Stage20DiscoveryPersistenceCodec.encode(state);
        assertTrue(Arrays.equals(first, second));
        assertEquals(state, Stage20DiscoveryPersistenceCodec.decode(first));

        Path save = Files.createTempDirectory("stage20g-discovery-test-")
                .resolve("stage20g-discovery.bin");
        Stage20DiscoveryPersistenceCodec.write(save, state);
        assertEquals(state, Stage20DiscoveryPersistenceCodec.read(save));
        assertEquals(DiscoveryState.UNKNOWN,
                state.knowledgeFor("faction.unseen").discoveryState(depositRef));
    }

    @Test
    void decoderRejectsTruncationTrailingBytesAndInvalidEnvelopeVersion() {
        Stage20DiscoveryPersistentState state = new Stage20DiscoveryPersistentState(
                Stage20DiscoveryPersistentState.CURRENT_VERSION,
                20_703L,
                "stage20.world.v1",
                "fingerprint-20703",
                List.of(new Stage20DiscoveryKnowledgeState("player", List.of())));
        byte[] bytes = Stage20DiscoveryPersistenceCodec.encode(state);
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - 1);
        byte[] trailing = Arrays.copyOf(bytes, bytes.length + 1);

        assertThrows(IllegalArgumentException.class,
                () -> Stage20DiscoveryPersistenceCodec.decode(truncated));
        assertThrows(IllegalArgumentException.class,
                () -> Stage20DiscoveryPersistenceCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class,
                () -> new Stage20DiscoveryPersistentState(
                        Stage20DiscoveryPersistentState.CURRENT_VERSION + 1,
                        20_703L,
                        "stage20.world.v1",
                        "fingerprint-20703",
                        List.of()));
    }

    @Test
    void ownerAndEntryIdentityDuplicationFailClosed() {
        Stage20DiscoveryKnowledgeState owner =
                new Stage20DiscoveryKnowledgeState("faction.duplicate", List.of());
        IllegalArgumentException duplicateOwner = assertThrows(
                IllegalArgumentException.class,
                () -> new Stage20DiscoveryPersistentState(
                        Stage20DiscoveryPersistentState.CURRENT_VERSION,
                        20_704L,
                        "stage20.world.v1",
                        "fingerprint-20704",
                        List.of(owner, owner)));

        assertTrue(duplicateOwner.getMessage().contains("duplicate discovery knowledge owner"));
    }
}
