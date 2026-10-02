import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
/** Fixed seed 1..16 opening-profile engineering evidence; never a human acceptance substitute. */
public final class Stage23BPilotOpeningCorpus {
    public static void main(String[] args) {
        int failures = 0;
        for (long seed = 1; seed <= 16; seed++) {
            boolean generated = false;
            try {
                var campaign = Stage228CampaignAuthority.create(seed);
                generated = true;
                var before = campaign.captureState();
                var preview = campaign.previewIndependentPilotStart();
                if (!preview.allowed() || !before.equals(campaign.captureState())) throw new AssertionError("No pure reserve offer");
                var player = campaign.submitIndependentPilotStart(preview);
                if (player.walletMilliCredits() != 75_000_000L || player.ownedFleetIds().size() != 1
                        || player.factionContentId() != null) throw new AssertionError("Invalid personal start");
                var after = campaign.captureState();
                var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                        Stage228GeneratedCampaignPersistenceCodec.encode(after)));
                if (!after.equals(loaded.captureState()) || loaded.canStartIndependentPilot()) throw new AssertionError("Non-exact or granting restore");
                System.out.println("{\"seed\":" + seed + ",\"result\":\"PASS\"}");
            } catch (RuntimeException | AssertionError exception) {
                failures++;
                System.out.println("{\"seed\":" + seed + ",\"result\":\"" + (generated ? "OPENING_FAIL" : "GENERATION_REJECTED") + "\",\"exception\":\"" + exception.getClass().getSimpleName() + "\"}");
                exception.printStackTrace(System.err);
            }
        }
        if (failures != 0) throw new AssertionError("Opening corpus failures: " + failures);
    }
}
