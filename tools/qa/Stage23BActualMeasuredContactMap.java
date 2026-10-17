import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignSensorContactUi;
import com.spacesim.ui.GeneratedWorldUiModel;
import java.nio.file.Path;

/** Actual fitted pulse and measured map projection, preserving the historical input. */
public final class Stage23BActualMeasuredContactMap {
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve input");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        var c = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(c.captureState())) throw new AssertionError("Non-exact actual input");
        saved = null;
        var archive = c.coordinator().runtime().discoveryState();
        var player = c.playerState().orElseThrow();
        long tick = c.coordinator().runtime().world().getAuthoritativeWorldTick();
        var pulse = c.previewPilotAction("SCAN", "", "", 1);
        if (!pulse.allowed()) throw new IllegalStateException("Actual fitted pulse rejected before commitment");
        c.submitPilotAction(pulse);
        if (c.coordinator().runtime().world().getAuthoritativeWorldTick() != tick + 1
                || !archive.equals(c.coordinator().runtime().discoveryState())
                || player.walletMilliCredits() != c.playerState().orElseThrow().walletMilliCredits())
            throw new AssertionError("Pulse granted archive or money, or advanced an incorrect interval");
        // Opening the observer's local map is the same navigation operation as client focus.
        var observer = c.coordinator().runtime().world().findFleet(player.activeFleetId()).orElseThrow();
        c.coordinator().runtime().world().activateSystem(observer.systemId());
        var baseline = c.captureState();
        var model = new GeneratedWorldUiModel(c.coordinator().rootSeed(), c.coordinator().runtime(), c.coordinator().content(),
                GeneratedWorldUiModel.CarrierUiSource.none(), () -> c.playerState().orElse(null));
        var snapshot = model.capture();
        var rows = GeneratedCampaignSensorContactUi.rows(c);
        int count = 0;
        for (var row : rows) if (row.focusSystem() != null) {
            var marker = snapshot.localObjects().stream()
                    .filter(m -> m.stableId().equals(row.selection().stableId())).findFirst().orElseThrow();
            if (row.focusFleet() != 0 || !marker.factionId().isEmpty() || marker.sprite() != null)
                throw new AssertionError("Anonymous contact disclosed target identity or artwork");
            count++;
        }
        if (count == 0 || !baseline.equals(c.captureState())) throw new AssertionError("No pure measured navigation path");
        Stage228GeneratedCampaignPersistenceCodec.write(output, baseline);
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
        if (!baseline.equals(restored.captureState()) || !rows.equals(GeneratedCampaignSensorContactUi.rows(restored)))
            throw new AssertionError("Non-exact measured-contact reload");
        System.out.println("ACTUAL MEASURED CONTACT MAP PASS contacts=" + count + " tick=" + (tick + 1)
                + "; real fitted pulse, shared physical tick, anonymous estimated markers, exact reload; new station docking not claimed");
    }
}
