import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignRefiningUi;
import java.nio.file.Path;

/** Actual acquired station -> reserved existing ore -> partial native reload -> real refined material. */
public final class Stage23BActualRefiningJourney {
    private static Stage228CampaignAuthority campaign;
    private static final String RECIPE = "refining.conductor_metal";
    private static final String INPUT = "commodity.feedstock.conductor_ore";
    private static final String OUTPUT = "commodity.material.conductor_metal";
    private static com.spacesim.economy.Stage18StationStorage store(String station) {
        return campaign.coordinator().runtime().industry().industrial().station(station).storage();
    }
    private static void tick() {
        campaign.coordinator().setPaused(false);
        campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        campaign.coordinator().setPaused(true);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Supply acquired input, separate output and actual station ID");
        int kilograms = Integer.getInteger("stage23b.refining.kg", 100);
        if (kilograms <= 0) throw new IllegalArgumentException("Positive real gross input mass required");
        Path output = Path.of(args[1]);
        if (output.toAbsolutePath().normalize().equals(Path.of(args[0]).toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve the acquired source separately");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        campaign = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(campaign.captureState())) throw new AssertionError("Non-exact acquired checkpoint migration");
        String station = args[2];
        var player = campaign.playerState().orElseThrow();
        if (!campaign.ownsProductionStation(station)
                || !campaign.pilotMarketReference(station).orElseThrow().equals(player.dockedAt()))
            throw new AssertionError("Actual owned physical berth required");
        double inputBefore = store(station).commodityMassKg(INPUT);
        double outputBefore = store(station).commodityMassKg(OUTPUT);
        long firstTick = campaign.coordinator().runtime().world().getAuthoritativeWorldTick();
        var rows = GeneratedCampaignRefiningUi.rows(campaign);
        if (rows.stream().noneMatch(r -> r.selection().stableId().equals("pilot-refining|" + station + '|' + RECIPE)))
            throw new AssertionError("Actual refining action absent from personal industry UI");
        var preview = campaign.previewPilotAction("START_REFINING", station, RECIPE, kilograms);
        if (!saved.equals(campaign.captureState())) throw new AssertionError("Projection or preview changed the actual campaign");
        if (!preview.allowed()) throw new IllegalStateException("Actual refining request rejected; no stock or line is supplied");
        campaign.submitPilotAction(preview);
        if (Math.abs(store(station).commodityMassKg(INPUT) - (inputBefore - kilograms)) > 1e-8
                || store(station).commodityMassKg(OUTPUT) != outputBefore)
            throw new AssertionError("Reservation created material or did not reserve the exact existing ore");
        tick();
        var partial = campaign.coordinator().runtime().manufacturingQueue().capture().stream()
                .filter(o -> o.stationId().equals(station) && o.operationId().equals(RECIPE)).findFirst().orElseThrow();
        if (partial.completedFraction() <= 0 || partial.completedFraction() >= 1)
            throw new AssertionError("Expected real partial processing before native reload");
        var pendingPath = output.resolveSibling(output.getFileName() + ".pending.s25");
        Stage228GeneratedCampaignPersistenceCodec.write(pendingPath, campaign.captureState());
        var pending = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(pendingPath);
        campaign = Stage228CampaignAuthority.restore(pending);
        if (!pending.equals(campaign.captureState())) throw new AssertionError("Non-exact partial refining reload");
        int remainingTicks = 0;
        while (campaign.coordinator().runtime().manufacturingQueue().capture().stream()
                .anyMatch(o -> o.stationId().equals(station) && o.operationId().equals(RECIPE))) {
            if (++remainingTicks > 100_000) throw new IllegalStateException("Actual refining work did not complete within the finite probe budget");
            tick();
        }
        var recipe = com.spacesim.content.Stage18RefiningCatalogLoader.loadDefault().findRecipe(RECIPE);
        double produced = store(station).commodityMassKg(OUTPUT) - outputBefore;
        double discarded = kilograms * recipe.discardedMassFraction();
        if (Math.abs(produced - kilograms * recipe.outputMassFraction()) > 1e-8
                || Math.abs(produced + discarded - kilograms) > 1e-8
                || campaign.playerState().orElseThrow().walletMilliCredits() != player.walletMilliCredits()
                || !campaign.playerState().orElseThrow().ownedFleetIds().equals(player.ownedFleetIds())
                || campaign.playerJournal().entries().stream().noneMatch(e -> e.action().equals("REFINING_COMPLETED")))
            throw new AssertionError("Actual material output, tailings, wallet or fleet continuity mismatch");
        if (Boolean.getBoolean("stage23b.refining.restoreRated")) {
            var target = campaign.coordinator().runtime().industry().industrial().station(station).facilities().stream()
                    .filter(f -> f.definitionId().equals("facility.processing.bulk_refinery")).findFirst().orElseThrow();
            var allocation = campaign.previewPilotAction("ALLOCATE_FACILITY_RESOURCES", station, target.facilityInstanceId(), 1);
            if (!allocation.allowed()) throw new AssertionError("Actual resources cannot restore rated refining operation");
            campaign.submitPilotAction(allocation);
            if (store(station).commodityMassKg(OUTPUT) != outputBefore + produced)
                throw new AssertionError("Resource restoration changed completed physical output");
        }
        var complete = campaign.captureState();
        Stage228GeneratedCampaignPersistenceCodec.write(output, complete);
        campaign = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
        if (!complete.equals(campaign.captureState())) throw new AssertionError("Non-exact completed refining reload");
        System.out.println("ORDINARY OWNED REFINING PASS inputKg=" + kilograms + " outputKg=" + produced
                + " tailingsKg=" + discarded + " elapsedTicks="
                + (campaign.coordinator().runtime().world().getAuthoritativeWorldTick() - firstTick)
                + "; actual acquired line and ore, partial/completed native reload, no ownership/material/work fixtures");
    }
}
