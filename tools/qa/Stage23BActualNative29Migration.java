import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.economy.ShipyardSmallCraftBuildQueueState;
import com.spacesim.ui.GeneratedCampaignSmallCraftBuildUi;
import com.spacesim.ui.GeneratedCampaignManufacturingUi;
import java.nio.file.*;

/** Exact real-player migration and pure owner-local production projection, without grants. */
public final class Stage23BActualNative29Migration {
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output) || input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve existing checkpoints");
        var migrated = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        var c = Stage228CampaignAuthority.restore(migrated);
        if (!migrated.equals(c.captureState())) throw new AssertionError("Non-exact actual migration");
        if (!c.smallCraftBuilds().equals(ShipyardSmallCraftBuildQueueState.empty()))
            throw new AssertionError("Historical migration inferred work or build custody");
        int craftRows = GeneratedCampaignSmallCraftBuildUi.rows(c).size();
        int moduleRows = GeneratedCampaignManufacturingUi.rows(c).size();
        if (!migrated.equals(c.captureState())) throw new AssertionError("Production projection changed actual state");
        Stage228GeneratedCampaignPersistenceCodec.write(output, migrated);
        var reloaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
        if (!migrated.equals(reloaded.captureState())) throw new AssertionError("Non-exact native v29 round trip");
        System.out.println("ACTUAL NATIVE29 MIGRATION PASS tick=" + c.coordinator().runtime().world().getAuthoritativeWorldTick()
                + " walletMilliCredits=" + c.playerState().orElseThrow().walletMilliCredits()
                + " fleet=" + c.playerState().orElseThrow().activeFleetId() + " craft=" + c.smallCraft().size()
                + " buildRows=" + craftRows + " equipmentRows=" + moduleRows + "; no stock, work, craft, money or delivery grants; exact reload");
    }
}
