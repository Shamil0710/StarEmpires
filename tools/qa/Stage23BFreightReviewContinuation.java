import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;

/** Identical real-world tick sequence runnable against the old and new jars for byte-level comparison. */
public final class Stage23BFreightReviewContinuation {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Supply immutable input, output and tick count");
        int ticks = Integer.parseInt(args[2]);
        if (ticks <= 0) throw new IllegalArgumentException("Positive tick count required");
        var state = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        var campaign = Stage228CampaignAuthority.restore(state);
        if (!state.equals(campaign.captureState())) throw new AssertionError("Non-exact input state");
        long initial = campaign.coordinator().runtime().world().getAuthoritativeWorldTick();
        campaign.coordinator().setPaused(false); campaign.coordinator().setTimeScale(1);
        long start = System.nanoTime();
        for (int i = 0; i < ticks; i++) campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        long elapsed = System.nanoTime() - start;
        campaign.coordinator().setPaused(true);
        if (campaign.coordinator().runtime().world().getAuthoritativeWorldTick() != initial + ticks)
            throw new AssertionError("Changed authoritative tick sequence");
        Stage228GeneratedCampaignPersistenceCodec.write(Path.of(args[1]), campaign.captureState());
        System.out.println("CONTINUATION ticks=" + ticks + " start=" + initial + " elapsedNanos=" + elapsed);
    }
}
