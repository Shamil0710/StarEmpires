import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only verification of an immutable copy of an actual ordinary-play checkpoint. */
public final class Stage23BActualCheckpointIntegrity {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply an immutable actual checkpoint copy");
        Path path = Path.of(args[0]);
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(path);
        var campaign = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(campaign.captureState())) throw new AssertionError("Loading changed actual campaign state");
        var runtime = campaign.coordinator().runtime();
        var player = campaign.playerState().orElseThrow();
        var knowledge = runtime.discoveryState();
        long tick = runtime.world().getAuthoritativeWorldTick();
        System.out.println("ACTUAL CHECKPOINT EXACT bytes=" + Files.size(path) + " tick=" + tick
                + " activeFleet=" + player.activeFleetId() + " jump=" + runtime.world().findFleetJump(player.activeFleetId())
                + " personalRecords=" + knowledge.knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().size());
        // Only this separately restored owner advances; the original file and live journey are untouched.
        campaign.coordinator().setTimeScale(1);
        campaign.coordinator().setPaused(false);
        campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        campaign.coordinator().setPaused(true);
        if (runtime.world().getAuthoritativeWorldTick() <= tick) throw new AssertionError("Loaded ordinary world did not advance");
        if (campaign.playerState().orElseThrow().walletMilliCredits() != player.walletMilliCredits())
            throw new AssertionError("Continuing this flight unexpectedly changed the personal wallet");
        if (!campaign.playerState().orElseThrow().ownedFleetIds().equals(player.ownedFleetIds()))
            throw new AssertionError("Continuing this flight unexpectedly changed the personal fleet roster");
        System.out.println("ACTUAL CHECKPOINT CONTINUATION PASS tick=" + runtime.world().getAuthoritativeWorldTick()
                + " jump=" + runtime.world().findFleetJump(player.activeFleetId()));
    }
}
