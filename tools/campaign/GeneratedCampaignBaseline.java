import com.spacesim.campaign.GeneratedCampaignCoordinator;
import com.spacesim.ui.GeneratedWorldUiModel;
import java.lang.management.ManagementFactory;
import java.util.Locale;

/** Read-only timing diagnostics; wall time is never passed to campaign simulation. */
public class GeneratedCampaignBaseline {
    public static void main(String[] args) {
        if (args.length != 1 || !args[0].matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Supply the exact measured source commit SHA");
        }
        Locale.setDefault(Locale.ROOT);
        System.out.println("source_sha=" + args[0]);
        System.out.println("java=" + System.getProperty("java.runtime.version"));
        System.out.println("vm=" + System.getProperty("java.vm.name"));
        System.out.println("os=" + System.getProperty("os.name") + "/" + System.getProperty("os.arch"));
        System.out.println("processors=" + Runtime.getRuntime().availableProcessors());
        System.out.println("max_heap_bytes=" + Runtime.getRuntime().maxMemory());
        System.out.println("jvm_arguments=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.println("seed=1");
        long start = System.nanoTime();
        var campaign = GeneratedCampaignCoordinator.create(1L);
        metric("generation_ms", start);
        var runtime = campaign.session().runtime();
        var physical = campaign.session().captureState();
        System.out.println("world_fingerprint=" + physical.campaign().materializedWorld().worldFingerprint());
        System.out.println("systems=" + physical.worldState().systems().size());
        System.out.println("freighters=" + physical.freight().freighters().size());
        System.out.println("fleets=" + physical.worldState().fleets().size());
        System.out.println("scheduler_step_ticks=" + physical.strategicStepTicks());
        System.out.println("remote_updates_per_slice=" + physical.remoteUpdateBudgetPerFrame());
        start = System.nanoTime();
        byte[] saved = campaign.encode();
        metric("encode_ms", start);
        System.out.println("save_bytes=" + saved.length);
        start = System.nanoTime();
        var restored = GeneratedCampaignCoordinator.decodeOrMigrate(saved);
        metric("restore_ms", start);
        if (!campaign.captureState().equals(restored.captureState())) {
            throw new AssertionError("composed restore changed authoritative state");
        }
        var model = new GeneratedWorldUiModel(1L, runtime, campaign.session().content());
        campaign.session().setPaused(true);
        start = System.nanoTime();
        for (int i = 0; i < 100; i++) model.capture();
        metric("paused_projection_100_ms", start);
        campaign.session().setPaused(false);
        start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            campaign.session().advanceFrame(0.1f);
            model.capture();
        }
        metric("running_advance_and_projection_100_ms", start);
        for (int scale : new int[] {1, 8}) {
            var session = GeneratedCampaignCoordinator.decodeOrMigrate(saved).session();
            session.setTimeScale(scale);
            long initial = session.runtime().world().getAuthoritativeWorldTick();
            start = System.nanoTime();
            for (int i = 0; i < 800 / scale; i++) session.advanceFrame(0.1f);
            metric("equal_simulation_interval_" + scale + "x_ms", start);
            long ticks = session.runtime().world().getAuthoritativeWorldTick() - initial;
            System.out.println("equal_simulation_interval_" + scale + "x_ticks=" + ticks);
            if (ticks != 800) throw new AssertionError("time scale changed fixed tick count: " + ticks);
        }
        var order = physical.freight().orders().get(0);
        System.out.println("first_order_id=" + order.orderId());
        System.out.println("first_order_one_way_seconds=" + order.oneWayDeliverySeconds());
        System.out.println("first_order_round_trip_seconds=" + order.roundTripCycleSeconds());
        System.out.println("heap_used_bytes=" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        System.out.println("scope=default generated traffic; headless; no graphical FPS or tactical performance claim");
    }
    private static void metric(String key, long start) {
        System.out.printf("%s=%.3f%n", key, (System.nanoTime() - start) / 1_000_000d);
    }
}
