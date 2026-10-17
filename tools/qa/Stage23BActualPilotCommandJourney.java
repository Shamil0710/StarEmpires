import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.*;

/** Commits one ordinary confirmed command on a separately restored actual checkpoint. */
public final class Stage23BActualPilotCommandJourney {
    public static void main(String[] args) throws Exception {
        var input = Path.of(args[0]); var output = Path.of(args[1]);
        if (Files.exists(output) || input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve actual previous checkpoints");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        var c = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(c.captureState())) throw new AssertionError("Non-exact actual command input");
        var before = c.captureState(); var command = c.previewPilotAction(args[2], args[3], args[4], Integer.parseInt(args[5]));
        if (!command.allowed() || !before.equals(c.captureState())) throw new IllegalStateException("Ordinary command rejected or preview impure");
        c.submitPilotAction(command);
        var complete = c.captureState(); Stage228GeneratedCampaignPersistenceCodec.write(output, complete);
        if (!complete.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
            throw new AssertionError("Non-exact actual command result");
        System.out.println("ACTUAL COMMAND PASS action=" + args[2] + " target=" + args[3] + " quantity=" + args[5]
                + " tick=" + c.coordinator().runtime().world().getAuthoritativeWorldTick()
                + " wallet=" + c.playerState().orElseThrow().walletMilliCredits() + "; ordinary preview/submit, exact reload, no state fixtures");
    }
}
