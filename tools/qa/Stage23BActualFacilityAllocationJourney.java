import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignConstructionUi;
import java.nio.file.Path;
import java.util.List;

/** Actual purchased facilities -> half-rated operation -> native reload -> original full allocation. */
public final class Stage23BActualFacilityAllocationJourney {
    private static double[] totals(List<InstalledFacilityState> rows) {
        return new double[]{rows.stream().mapToDouble(InstalledFacilityState::allocatedProcessPowerW).sum(),
                rows.stream().mapToDouble(InstalledFacilityState::availableHeatRejectionW).sum(),
                rows.stream().mapToDouble(InstalledFacilityState::availableLaborUnits).sum(),
                rows.stream().mapToDouble(InstalledFacilityState::availableMaintenanceWorkRate).sum()};
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Supply actual input, separate output, station and target facility");
        Path input = Path.of(args[0]); Path output = Path.of(args[1]);
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve the acquired source separately");
        String station = args[2]; String target = args[3];
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        var c = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(c.captureState())) throw new AssertionError("Non-exact actual restore");
        var p = c.playerState().orElseThrow();
        if (!c.ownsProductionStation(station) || !c.pilotMarketReference(station).orElseThrow().equals(p.dockedAt()))
            throw new AssertionError("Actual acquired owner and berth required");
        var original = c.coordinator().runtime().industry().industrial().station(station).facilities();
        var raw = c.coordinator().runtime().infrastructure().endpoint(station).storage().snapshot();
        var total = totals(original);
        var rows = GeneratedCampaignConstructionUi.rows(c);
        for (var facility : original) if (rows.stream().filter(r -> r.selection().stableId().equals(
                "pilot-facility-resources|" + station + '|' + facility.facilityInstanceId())).count() != 1)
            throw new AssertionError("Original installation needs exactly one resource control without construction fixture");
        var preview = c.previewPilotAction("ALLOCATE_FACILITY_RESOURCES_HALF", station, target, 1);
        if (!preview.allowed() || !saved.equals(c.captureState())) throw new AssertionError("Half allocation preview unavailable or impure");
        c.submitPilotAction(preview); preview = null; saved = null;
        var halfFacilities = c.coordinator().runtime().industry().industrial().station(station).facilities();
        var capability = c.coordinator().runtime().industry().industrial().station(station).facilityCapabilities().stream()
                .filter(f -> f.facilityInstanceId().equals(target)).findFirst().orElseThrow();
        if (Math.abs(capability.effectiveThroughputFraction() - .5) > 1e-12
                || !java.util.Arrays.equals(total, totals(halfFacilities))
                || !raw.equals(c.coordinator().runtime().infrastructure().endpoint(station).storage().snapshot())
                || c.playerState().orElseThrow().walletMilliCredits() != p.walletMilliCredits())
            throw new AssertionError("Half operation changed stock/money/resources or did not halve real throughput");
        var half = c.captureState(); Path halfPath = output.resolveSibling(output.getFileName() + ".half.s25");
        Stage228GeneratedCampaignPersistenceCodec.write(halfPath, half);
        c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(halfPath));
        if (!half.equals(c.captureState())) throw new AssertionError("Half operation changed on native reload");
        half = null;
        var full = c.previewPilotAction("ALLOCATE_FACILITY_RESOURCES", station, target, 1);
        if (!full.allowed()) throw new AssertionError("Actual released resources cannot restore rated operation");
        c.submitPilotAction(full); full = null;
        if (!original.equals(c.coordinator().runtime().industry().industrial().station(station).facilities())
                || !raw.equals(c.coordinator().runtime().infrastructure().endpoint(station).storage().snapshot())
                || c.playerState().orElseThrow().walletMilliCredits() != p.walletMilliCredits())
            throw new AssertionError("Restoring full operation did not conserve original allocations, stock and money");
        var complete = c.captureState(); Stage228GeneratedCampaignPersistenceCodec.write(output, complete);
        if (!complete.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
            throw new AssertionError("Non-exact full allocation reload");
        System.out.println("ACTUAL FACILITY ALLOCATION PASS station=" + station + " target=" + target
                + " halfThroughput=" + capability.effectiveThroughputFraction() + " conservedResources=" + java.util.Arrays.toString(total)
                + "; original installations, pure preview, exact half/full native reload, unchanged materials and money; no operating-resource fixtures");
    }
}
