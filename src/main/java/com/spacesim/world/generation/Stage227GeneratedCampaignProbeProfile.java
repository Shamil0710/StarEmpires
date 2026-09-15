package com.spacesim.world.generation;

import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.AcceptanceAuthority;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeInputs;
import com.spacesim.world.generation.Stage20RepresentativeGeneratedWorldProbeProfileV3.DerivedProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * M22.7 production campaign profile that promotes the accepted Stage-20 generated participant
 * slots to the two canonical Stage-22 playable faction identities.
 *
 * <p>The historical Stage-20 representative profiles deliberately retain {@code faction.alpha}
 * and {@code faction.beta} for reproducible generation evidence. New ordinary campaigns must not
 * expose those diagnostic identities as player-facing factions, so this profile copies the exact
 * accepted V3 geometry, infrastructure, economic requirements, transport authority and coordinated
 * freight policy while replacing only the faction start identity authority.</p>
 */
public final class Stage227GeneratedCampaignProbeProfile {
    /** Stable production-profile identity for the integrated campaign handoff. */
    public static final String CURRENT_VERSION = "m22.7.generated-campaign-production-profile.v1";

    private Stage227GeneratedCampaignProbeProfile() {
        throw new AssertionError("No instances");
    }

    /**
     * Derives the M22.7 production profile from the accepted historical V3 authority.
     *
     * @return deterministic production profile using canonical Empire and Industrial Union IDs
     */
    public static DerivedProfile deriveCurrent() {
        DerivedProfile base = Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent();
        AcceptanceAuthority sourceAcceptance = base.inputs().acceptance();
        AcceptanceAuthority productionAcceptance = new AcceptanceAuthority(
                sourceAcceptance.bootstrapRequirements(),
                sourceAcceptance.dependencyRequirements(),
                sourceAcceptance.factionStartProfile(),
                List.of(
                        Stage22EmpirePackageCatalog.STABLE_FACTION_ID,
                        Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID));
        ProbeInputs productionInputs = new ProbeInputs(
                base.inputs().macroRequest(),
                base.inputs().topologyQuality(),
                base.inputs().infrastructure(),
                productionAcceptance,
                base.inputs().transport());

        ArrayList<String> evidence = new ArrayList<>(base.policyEvidenceIds());
        evidence.add("m22.7:canonical-playable-factions:"
                + Stage22EmpirePackageCatalog.STABLE_FACTION_ID + "+"
                + Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID);
        return new DerivedProfile(
                CURRENT_VERSION,
                base.version(),
                productionInputs,
                base.coordinatedFreightAcceptance(),
                evidence,
                base.stage22ReviewRequired());
    }
}
