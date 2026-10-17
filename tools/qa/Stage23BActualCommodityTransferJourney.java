import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;

/** Actual acquired warehouse -> personal hold -> native reload -> owned warehouse, without grants. */
public final class Stage23BActualCommodityTransferJourney {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("Supply actual input, separate output, owned station and commodity");
        Path input = Path.of(args[0]); Path output = Path.of(args[1]);
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve the original acquired checkpoint");
        String station = args[2]; String commodity = args[3];
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        var c = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(c.captureState())) throw new AssertionError("Non-exact actual restore");
        var player = c.playerState().orElseThrow();
        var fleet = player.activeFleetId();
        if (!c.ownsProductionStation(station) || !c.pilotMarketReference(station).orElseThrow().equals(player.dockedAt()))
            throw new AssertionError("Real acquired station and berth required");
        double stock = c.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(commodity);
        double hold = c.coordinator().runtime().freight().cargoHoldSnapshot(fleet).commodityMassByIdKg().getOrDefault(commodity, 0d);
        var preview = c.previewPilotAction("LOAD_COMMODITY", station, commodity, 1);
        if (!saved.equals(c.captureState())) throw new AssertionError("Preview changed actual state");
        if (!preview.allowed()) throw new AssertionError("Existing stock cannot be loaded; no fixture is supplied");
        c.submitPilotAction(preview);
        preview = null;
        saved = null;
        if (c.playerState().orElseThrow().walletMilliCredits() != player.walletMilliCredits()
                || c.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(commodity) != stock - 1
                || c.coordinator().runtime().freight().cargoHoldSnapshot(fleet).commodityMassByIdKg().getOrDefault(commodity, 0d) != hold + 1)
            throw new AssertionError("Load violated actual stock or money conservation");
        var loaded = c.captureState();
        Path aboard = output.resolveSibling(output.getFileName() + ".aboard.s25");
        Stage228GeneratedCampaignPersistenceCodec.write(aboard, loaded);
        c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(aboard));
        if (!loaded.equals(c.captureState()) || c.previewPilotAction("UNLOAD_COMMODITY", station, commodity, 1).allowed())
            throw new AssertionError("Reload lost exact cargo or reused the completed handling interval");
        loaded = null;
        c.coordinator().setPaused(false);
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.coordinator().setPaused(true);
        var unload = c.previewPilotAction("UNLOAD_COMMODITY", station, commodity, 1);
        if (!unload.allowed()) throw new AssertionError("Next real interval cannot unload personal stock");
        c.submitPilotAction(unload);
        unload = null;
        if (c.playerState().orElseThrow().walletMilliCredits() != player.walletMilliCredits()
                || c.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(commodity) != stock
                || c.coordinator().runtime().freight().cargoHoldSnapshot(fleet).commodityMassByIdKg().getOrDefault(commodity, 0d) != hold
                || !c.playerState().orElseThrow().ownedFleetIds().equals(player.ownedFleetIds()))
            throw new AssertionError("Unload violated cargo, money or fleet conservation");
        var complete = c.captureState(); Stage228GeneratedCampaignPersistenceCodec.write(output, complete);
        if (!complete.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
            throw new AssertionError("Non-exact completed transfer restore");
        System.out.println("ACTUAL OWNED COMMODITY TRANSFER PASS station=" + station + " commodity=" + commodity
                + " kg=1 walletMilliCredits=" + player.walletMilliCredits()
                + "; load, exact aboard reload, shared handling guard, next real tick, unload, exact final reload; no material/ownership/geometry fixtures");
    }
}
