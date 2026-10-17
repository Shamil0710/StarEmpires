import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Files;
import java.nio.file.Path;

/** Pure file-write audit on a restored live campaign; no ticks, assets or knowledge are granted. */
public final class Stage23BActualCheckpointFileWrite {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply existing native input and separate output");
        Path input = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        if (input.equals(output)) throw new IllegalArgumentException("The original checkpoint must remain separate");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        var campaign = Stage228CampaignAuthority.restore(saved);
        var captured = campaign.captureState();
        if (!saved.equals(captured)) throw new AssertionError("Non-exact restored campaign");
        Stage228GeneratedCampaignPersistenceCodec.write(output, captured);
        if (Files.mismatch(input, output) != -1) throw new AssertionError("File writer changed native bytes");
        if (!captured.equals(campaign.captureState())) throw new AssertionError("File writer changed live campaign");
        System.out.println("ACTUAL STREAMED CHECKPOINT PASS bytes=" + Files.size(output)
                + " tick=" + campaign.coordinator().runtime().world().getAuthoritativeWorldTick()
                + " maxHeapBytes=" + Runtime.getRuntime().maxMemory() + "; original file and live state unchanged");
    }
}
