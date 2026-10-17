import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.*;
import java.nio.file.*;

/** Ordinary blind exploration using only jump rows already offered by production UI. */
public final class Stage23BOrdinaryBlindJumpJourney {
    public static void main(String[] args) throws Exception {
        var input = Path.of(args[0]); var output = Path.of(args[1]);
        if (Files.exists(output) || input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve existing checkpoints");
        int budget = Integer.getInteger("stage23b.exploration.maxTicks", 500000);
        int desired = Integer.getInteger("stage23b.exploration.hops", 3);
        if (budget <= 0 || desired <= 0) throw new IllegalArgumentException("Positive probe budgets required");
        var c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input));
        var initial = c.captureState(); var fleet = c.playerState().orElseThrow().activeFleetId();
        long tick = c.coordinator().runtime().world().getAuthoritativeWorldTick();
        if (c.playerState().orElseThrow().docked()) {
            var before = c.captureState(); var undock = c.previewPilotAction("UNDOCK", "", "", 0);
            if (!undock.allowed() || !before.equals(c.captureState())) throw new AssertionError("Actual undock unavailable or impure");
            c.submitPilotAction(undock);
        }
        int elapsed = 0, completed = 0; String stop = "TICK_BUDGET";
        while (elapsed < budget && completed < desired) {
            var world = c.coordinator().runtime().world();
            if (world.findFleetJump(fleet).isEmpty()) {
                var current = c;
                var model = new GeneratedWorldUiModel(c.coordinator().rootSeed(), c.coordinator().runtime(), c.coordinator().content(),
                        GeneratedCampaignCarrierUi.source(c), () -> current.playerState().orElse(null));
                var rows = new ProductionUiProjector("faction.alpha").capture(c, model.capture())
                        .rows(GeneratedWorldCommandUiRenderer.Tab.SHIPS);
                var offered = rows.stream().filter(row -> row.selection().stableId().startsWith("pilot-jump|")
                        && row.name().startsWith("Неизвестное направление")).toList();
                boolean accepted = false;
                for (var row : offered) {
                    var before = c.captureState(); var destination = row.selection().stableId().substring("pilot-jump|".length());
                    var preview = c.previewPilotAction("JUMP", destination, "", 1);
                    if (!before.equals(c.captureState())) throw new AssertionError("Jump preview changed personal knowledge");
                    if (!preview.allowed()) continue;
                    c.submitPilotAction(preview); accepted = true;
                    System.out.println("ORDINARY BLIND DEPARTURE publicLabel=" + row.name() + " tick=" + world.getAuthoritativeWorldTick());
                    break; // No hidden target selection, route search or unpaid coordinates.
                }
                if (!accepted) { stop = "NO_ADMITTED_UNKNOWN_UI_DIRECTION"; break; }
            }
            c.coordinator().setTimeScale(1); c.coordinator().setPaused(false);
            c.advanceFrame(c.coordinator().session().fixedStepSeconds()); elapsed++;
            if (c.coordinator().runtime().world().findFleetJump(fleet).isEmpty()) {
                completed++;
                var destination = c.coordinator().runtime().world().findFleet(fleet).orElseThrow().systemId();
                if (!c.playerState().orElseThrow().discoveredSystemIds().contains(destination))
                    throw new AssertionError("Actual arrival did not record personal discovery");
                System.out.println("ORDINARY BLIND ARRIVAL system=" + destination + " tick=" + (tick + elapsed));
            }
            if (elapsed % 100000 == 0) System.out.println("ORDINARY EXPLORATION progressTicks=" + elapsed + " completed=" + completed);
        }
        c.coordinator().setPaused(true); var saved = c.captureState();
        if (completed >= desired) stop = "HOP_BUDGET";
        if (c.coordinator().runtime().world().getAuthoritativeWorldTick() != tick + elapsed
                || saved.playerState().walletMilliCredits() != initial.playerState().walletMilliCredits()
                || !saved.playerState().ownedFleetIds().equals(initial.playerState().ownedFleetIds())
                || !saved.crewManifest().equals(initial.crewManifest())) throw new AssertionError("Exploration granted time, money, assets or staffing");
        Stage228GeneratedCampaignPersistenceCodec.write(output, saved);
        if (!saved.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
            throw new AssertionError("Non-exact ordinary exploration checkpoint");
        System.out.println("ORDINARY BLIND EXPLORATION " + (completed > 0 ? "PASS" : "PARTIAL") + " completedHops=" + completed
                + " ticks=" + elapsed + " stop=" + stop + "; real UI directions, finite propulsion, exact reload; full resource route not claimed");
    }
}
