package com.spacesim.persistence;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.Stage22FactionProfileCatalog;
import com.spacesim.content.Stage22FactionProfileLoader;
import com.spacesim.world.FactionIdentityResolver;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage22FactionProfileBindingCodecTest {
    @Test
    void coreProfileBindingsRoundTripByteStablyAndRemainIdentityBound() throws Exception {
        Fixture fixture = fixture();
        Stage22FactionProfileBindingState before = Stage22FactionProfileBindingState.capture(
                fixture.profiles(), fixture.identities());

        byte[] first = Stage22FactionProfileBindingCodec.encode(before);
        Stage22FactionProfileBindingState restored = Stage22FactionProfileBindingCodec.decode(first);
        byte[] second = Stage22FactionProfileBindingCodec.encode(restored);

        assertArrayEquals(first, second);
        assertEquals(before, restored);
        assertEquals(2, restored.bindings().size());
        restored.validateAgainst(fixture.profiles(), fixture.identities());

        Path sidecar = Files.createTempDirectory("stage22-profile-binding-").resolve("profiles.s22p");
        Stage22FactionProfileBindingCodec.write(sidecar, before);
        Stage22FactionProfileBindingState fromDisk = Stage22FactionProfileBindingCodec.read(sidecar);
        assertEquals(before, fromDisk);
        fromDisk.validateAgainst(fixture.profiles(), fixture.identities());
    }

    @Test
    void decoderRejectsTruncationTrailingBytesFutureEnvelopeAndBadMagic() {
        Fixture fixture = fixture();
        byte[] valid = Stage22FactionProfileBindingCodec.encode(
                Stage22FactionProfileBindingState.capture(fixture.profiles(), fixture.identities()));
        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        byte[] futureEnvelope = valid.clone();
        futureEnvelope[11] = 2;
        byte[] badMagic = valid.clone();
        badMagic[0] = 0;

        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileBindingCodec.decode(truncated));
        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileBindingCodec.decode(trailing));
        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileBindingCodec.decode(futureEnvelope));
        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileBindingCodec.decode(badMagic));
    }

    @Test
    void currentAuthorityValidationRejectsFingerprintVersionAndRuntimeIdentityDrift() {
        Fixture fixture = fixture();
        Stage22FactionProfileBindingState valid = Stage22FactionProfileBindingState.capture(
                fixture.profiles(), fixture.identities());

        Stage22FactionProfileBindingState wrongFingerprint = new Stage22FactionProfileBindingState(
                valid.envelopeVersion(),
                valid.profileSchemaVersion(),
                valid.catalogVersion(),
                "0".repeat(64),
                valid.bindings());
        assertThrows(IllegalArgumentException.class,
                () -> wrongFingerprint.validateAgainst(fixture.profiles(), fixture.identities()));

        var first = valid.bindings().get(0);
        var second = valid.bindings().get(1);
        Stage22FactionProfileBindingState wrongRuntime = new Stage22FactionProfileBindingState(
                valid.envelopeVersion(),
                valid.profileSchemaVersion(),
                valid.catalogVersion(),
                valid.catalogFingerprint(),
                java.util.List.of(
                        new Stage22FactionProfileBindingState.Binding(
                                first.stableFactionId(), second.runtimeFactionId(), first.profileId(), first.profileVersion()),
                        new Stage22FactionProfileBindingState.Binding(
                                second.stableFactionId(), first.runtimeFactionId(), second.profileId(), second.profileVersion())));
        assertThrows(IllegalArgumentException.class,
                () -> wrongRuntime.validateAgainst(fixture.profiles(), fixture.identities()));
    }

    private static Fixture fixture() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        var world = LargeDemoGalaxyFactory.createState(22_102L, content);
        return new Fixture(
                Stage22FactionProfileLoader.loadDefault(),
                FactionIdentityResolver.createDefault(content, world.factionIdentities()));
    }

    private record Fixture(
            Stage22FactionProfileCatalog profiles,
            FactionIdentityResolver identities) {
    }
}
