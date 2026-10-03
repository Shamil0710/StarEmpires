package com.spacesim.campaign;

import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;

/** Immutable seed-one baseline; every command test receives independent restored owners. */
final class FoundedCampaignFixture {
    private FoundedCampaignFixture() { }

    static Stage228CampaignAuthority restore() {
        return Stage228CampaignAuthority.restore(Baseline.STATE);
    }

    private static final class Baseline {
        private static final Stage228GeneratedCampaignPersistentState STATE = create();

        private static Stage228GeneratedCampaignPersistentState create() {
            var campaign = Stage228CampaignAuthority.create(1L);
            campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
            return campaign.submitPlayerFactionFoundation(
                    campaign.previewPlayerFactionFoundation("faction.player", "Содружество")).captureState();
        }
    }
}
