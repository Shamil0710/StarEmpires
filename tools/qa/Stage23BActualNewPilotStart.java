import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Files;
import java.nio.file.Path;

/** Separate ordinary new campaign for exploration evidence; never edits an existing checkpoint. */
public final class Stage23BActualNewPilotStart {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        if (Files.exists(output)) throw new IllegalArgumentException("Preserve existing checkpoint");
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 1;
        var c = Stage228CampaignAuthority.create(seed);
        var opening = c.captureState();
        var preview = c.previewIndependentPilotStart();
        if (!preview.allowed() || !opening.equals(c.captureState())) throw new AssertionError("Ordinary pilot start rejected or impure");
        c.submitIndependentPilotStart(preview);
        var saved = c.captureState();
        Stage228GeneratedCampaignPersistenceCodec.write(output, saved);
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
        if (!saved.equals(restored.captureState())) throw new AssertionError("Non-exact ordinary new pilot checkpoint");
        System.out.println("ACTUAL NEW PILOT START PASS seed=" + seed + " tick=" + c.coordinator().runtime().world().getAuthoritativeWorldTick()
                + " walletMilliCredits=" + saved.playerState().walletMilliCredits() + " fleet=" + saved.playerState().activeFleetId()
                + "; normal authored opening and paid purchase, exact reload; no additional assets or knowledge");
    }
}
