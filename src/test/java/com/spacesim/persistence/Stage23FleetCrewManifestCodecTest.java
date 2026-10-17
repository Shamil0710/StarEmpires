package com.spacesim.persistence;

import com.spacesim.world.FleetId;
import com.spacesim.world.Stage23FleetCrewManifest;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage23FleetCrewManifestCodecTest {
    @Test void exactRosterIsBoundedCanonicalAndCannotDuplicatePersonnelInputs() {
        var a = new Stage23FleetCrewManifest.Entry(new FleetId(3), 12);
        var b = new Stage23FleetCrewManifest.Entry(new FleetId(1), 7);
        var roster = new Stage23FleetCrewManifest(Stage23FleetCrewManifest.COMMISSIONED_PROFILE, List.of(a, b));
        assertEquals(7, roster.availableCrew(new FleetId(1))); assertEquals(0, roster.availableCrew(new FleetId(2)));
        byte[] bytes = Stage23FleetCrewManifestCodec.encode(roster);
        assertEquals(roster, Stage23FleetCrewManifestCodec.decode(bytes));
        assertThrows(IllegalArgumentException.class, () -> new Stage23FleetCrewManifest(Stage23FleetCrewManifest.COMMISSIONED_PROFILE, List.of(a, a)));
        assertThrows(IllegalArgumentException.class, () -> new Stage23FleetCrewManifest(Stage23FleetCrewManifest.UNOBSERVED_PROFILE, List.of(a)));
        assertThrows(IllegalArgumentException.class, () -> new Stage23FleetCrewManifest.Entry(new FleetId(1), -1));
        assertThrows(IllegalArgumentException.class, () -> Stage23FleetCrewManifestCodec.decode(java.util.Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> Stage23FleetCrewManifestCodec.decode(java.util.Arrays.copyOf(bytes, bytes.length + 1)));
    }
}
