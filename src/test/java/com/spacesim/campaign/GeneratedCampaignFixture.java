package com.spacesim.campaign;

import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;

/** Historical freight baseline stored as private bytes; callers receive independent checkpoint graphs. */
public final class GeneratedCampaignFixture {
    private GeneratedCampaignFixture() { }

    public static Stage228GeneratedCampaignPersistentState checkpoint() {
        return Stage228GeneratedCampaignPersistenceCodec.decode(Baseline.BYTES);
    }

    public static Stage21IGeneratedWorldRuntimePersistentState coordinatorCheckpoint() {
        return checkpoint().stage21Runtime();
    }

    public static Stage228CampaignAuthority restoreAuthority() {
        return Stage228CampaignAuthority.restore(checkpoint());
    }

    public static GeneratedCampaignCoordinator restoreCoordinator() {
        return GeneratedCampaignCoordinator.restore(coordinatorCheckpoint());
    }

    private static final class Baseline {
        private static final byte[] BYTES = Stage228GeneratedCampaignPersistenceCodec.encode(
                Stage228CampaignAuthority.create(1L, java.util.List.of(),
                        com.spacesim.persistence.Stage20FreightRuntimeMaterializer.ReserveLoadoutPolicy.BASELINE).captureState());
    }
}
