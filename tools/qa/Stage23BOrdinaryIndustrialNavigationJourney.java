import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.player.PlayerFleetRoutePlanner;
import com.spacesim.ui.*;
import com.spacesim.world.*;
import java.nio.file.*;
import java.util.*;

/** Explores production UI exits to an already personally known destination; never supplies a hidden route. */
public final class Stage23BOrdinaryIndustrialNavigationJourney {
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output) || input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Preserve previous checkpoints");
        var initial = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        var c = Stage228CampaignAuthority.restore(initial);
        if (!initial.equals(c.captureState())) throw new AssertionError("Non-exact input");
        var p = c.playerState().orElseThrow(); var fleet = p.activeFleetId(); var w = c.coordinator().runtime().world();
        long availableWallet = p.walletMilliCredits();
        String selectedObject = System.getProperty("stage23b.navigation.objectId", "");
        var target = c.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
                .entries().stream().filter(k -> k.knownLocation().isPresent())
                .filter(k -> { if (!selectedObject.isEmpty()) return k.object().objectId().equals(selectedObject);
                    if (k.object().resourceObject()) return false;
                    try { long price = c.stationSalePriceMilliCredits(k.object().objectId()); return price > 0 && price <= availableWallet; }
                    catch (IllegalArgumentException notStation) { return false; } })
                .findFirst().orElseThrow(() -> new IllegalStateException("No personally received destination with coordinates"));
        System.out.println("PERSONAL NAVIGATION TARGET object=" + target.object().objectId() + " system=" + target.object().systemId()
                + " evidence=" + target.evidence());
        long started = w.getAuthoritativeWorldTick(); long budget = Long.getLong("stage23b.navigation.maxTicks", 2_000_000L);
        var observedExits = new LinkedHashMap<StarSystemId, List<StarSystemId>>();
        String stop = "TICK_BUDGET"; int hops = 0;
        c.coordinator().setTimeScale(1); c.coordinator().setPaused(true);
        if (p.docked()) c.submitPilotAction(c.previewPilotAction("UNDOCK", "", "", 0));
        try {
            while (w.getAuthoritativeWorldTick() - started < budget) {
                // Continue an already committed departure when a previous bounded run ended in transit.
                if (w.findFleetJump(fleet).isPresent()) {
                    c.coordinator().setPaused(false);
                    c.advanceFrame(c.coordinator().session().fixedStepSeconds());
                    c.coordinator().setPaused(true);
                    long elapsed = w.getAuthoritativeWorldTick() - started;
                    if (elapsed % 10_000 == 0) System.out.println("ORDINARY RESUMED FLIGHT ticks=" + elapsed
                            + " phase=" + w.findFleetJump(fleet).map(j -> j.phase().toString()).orElse("ARRIVED"));
                    if (elapsed % 100_000 == 0) Stage228GeneratedCampaignPersistenceCodec.write(output, c.captureState());
                    if (w.findFleetJump(fleet).isEmpty()) {
                        hops++;
                        System.out.println("ORDINARY INDUSTRIAL RESUMED ARRIVAL system=" + w.findFleet(fleet).orElseThrow().systemId()
                                + " tick=" + w.getAuthoritativeWorldTick());
                    }
                    continue;
                }
                var current = w.findFleet(fleet).orElseThrow().systemId();
                if (current.equals(target.object().systemId())) { stop = "DESTINATION_SYSTEM_REACHED"; break; }
                p = c.playerState().orElseThrow();
                var model = new GeneratedWorldUiModel(c.coordinator().rootSeed(), c.coordinator().runtime(), c.coordinator().content(),
                        GeneratedCampaignCarrierUi.source(c), () -> c.playerState().orElse(null));
                var rows = new ProductionUiProjector("faction.alpha").capture(c, model.capture())
                        .rows(GeneratedWorldCommandUiRenderer.Tab.SHIPS).stream()
                        .filter(row -> row.selection().stableId().startsWith("pilot-jump|")).toList();
                var publishedExits = rows.stream().map(row -> new StarSystemId(Long.parseLong(row.selection().stableId().substring("pilot-jump|".length())))).toList();
                // A public exit is an offer, not a guarantee that this fitted ship can reach it.
                // Read only the actual onboard fuel estimate for each already published neighbor.
                var exits = publishedExits.stream().filter(exit -> w.planFleetRouteFuel(fleet, List.of(current, exit)).feasible()).toList();
                if (Boolean.getBoolean("stage23b.navigation.fuelEconomy")) exits = exits.stream().sorted(
                        Comparator.comparingDouble((StarSystemId exit) -> w.planFleetRouteFuel(fleet, List.of(current, exit)).consumedReactionMassKg())
                                .thenComparingLong(StarSystemId::value)).toList();
                if (exits.isEmpty()) { stop = "NO_FUEL_SAFE_PUBLISHED_EXIT"; break; }
                observedExits.put(current, exits);
                var planner = new PlayerFleetRoutePlanner(w, c.coordinator().content(), p);
                var knownRoute = planner.plan(fleet, current, target.object().systemId());
                StarSystemId next = knownRoute.filter(r -> r.path().size() > 1).map(r -> r.path().get(1)).orElse(null);
                if (next != null && !exits.contains(next)) next = null;
                if (next == null) for (var exit : exits) if (!p.discoveredSystemIds().contains(exit)) { next = exit; break; }
                if (next == null) {
                    // Return only along personally discovered routes to a desk whose exits we actually observed.
                    for (var entry : observedExits.entrySet()) {
                        boolean unexplored = false;
                        for (var exit : entry.getValue()) if (!p.discoveredSystemIds().contains(exit)) unexplored = true;
                        if (!unexplored) continue;
                        var route = planner.plan(fleet, current, entry.getKey());
                        if (route.isPresent() && route.orElseThrow().path().size() > 1 && exits.contains(route.orElseThrow().path().get(1))) { next = route.orElseThrow().path().get(1); break; }
                    }
                }
                if (next == null) {
                    // A previous run's personally known systems may contain branches not inspected in this run.
                    for (var known : p.discoveredSystemIds()) {
                        if (observedExits.containsKey(known)) continue;
                        var route = planner.plan(fleet, current, known);
                        if (route.isPresent() && route.orElseThrow().path().size() > 1 && exits.contains(route.orElseThrow().path().get(1))) { next = route.orElseThrow().path().get(1); break; }
                    }
                }
                if (next == null) { System.out.println("NO ROUTE current=" + current + " knownRoute=" + knownRoute + " observed=" + observedExits + " discovered=" + p.discoveredSystemIds()); for (var exit : publishedExits) System.out.println("EXIT " + exit + " fuel=" + w.planFleetRouteFuel(fleet, List.of(current, exit))); stop = "NO_PERSONAL_EXPLORATION_ROUTE"; break; }
                if (!exits.contains(next)) throw new AssertionError("Navigation used an unpublished local exit");
                // Arrival does not reset the installed FTL cooldown or its physical readiness.
                int readinessTicks = 0;
                while (!w.previewFittedFleetJump(fleet).orElseThrow().allowed() && readinessTicks < 10_000
                        && w.getAuthoritativeWorldTick() - started < budget) {
                    c.coordinator().setPaused(false);
                    c.advanceFrame(c.coordinator().session().fixedStepSeconds());
                    c.coordinator().setPaused(true); readinessTicks++;
                }
                if (readinessTicks > 0) System.out.println("ORDINARY FTL READINESS WAIT ticks=" + readinessTicks);
                var before = c.captureState(); var preview = c.previewPilotAction("JUMP", Long.toString(next.value()), "", 1);
                if (!before.equals(c.captureState())) throw new AssertionError("Impure jump preview");
                if (!preview.allowed()) {
                    System.out.println("PUBLISHED EXIT REJECTED next=" + next + " fuelPlan="
                            + w.planFleetRouteFuel(fleet, List.of(current, next)));
                    var isolated = Stage228CampaignAuthority.restore(c.captureState());
                    var execute = Stage228CampaignAuthority.class.getDeclaredMethod("executePilotAction", String.class, String.class, String.class, int.class);
                    execute.setAccessible(true);
                    try { execute.invoke(isolated, "JUMP", Long.toString(next.value()), "", 1); isolated.captureState(); }
                    catch (java.lang.reflect.InvocationTargetException rejected) {
                        System.out.println("PUBLISHED EXIT REJECTION reason=" + rejected.getCause());
                    }
                    stop = "PUBLISHED_EXIT_NOT_ADMITTED"; break;
                }
                System.out.println("ORDINARY INDUSTRIAL DEPARTURE from=" + current + " selectedUiExit=" + next + " known=" + p.discoveredSystemIds().contains(next));
                c.submitPilotAction(preview); c.coordinator().setPaused(false);
                do {
                    c.advanceFrame(c.coordinator().session().fixedStepSeconds());
                    long elapsed = w.getAuthoritativeWorldTick() - started;
                    if (elapsed % 10_000 == 0) System.out.println("ORDINARY INDUSTRIAL PROGRESS ticks=" + elapsed
                            + " phase=" + w.findFleetJump(fleet).map(j -> j.phase().toString()).orElse("ARRIVED"));
                    if (elapsed % 100_000 == 0) Stage228GeneratedCampaignPersistenceCodec.write(output, c.captureState());
                    if (w.getAuthoritativeWorldTick() - started >= budget) break;
                } while (w.findFleetJump(fleet).isPresent());
                c.coordinator().setPaused(true);
                if (w.findFleetJump(fleet).isPresent()) break;
                hops++;
                System.out.println("ORDINARY INDUSTRIAL ARRIVAL system=" + w.findFleet(fleet).orElseThrow().systemId() + " tick=" + w.getAuthoritativeWorldTick());
                Stage228GeneratedCampaignPersistenceCodec.write(output, c.captureState());
            }
        } finally {
            c.coordinator().setPaused(true); var saved = c.captureState();
            if (saved.playerState().walletMilliCredits() != initial.playerState().walletMilliCredits()
                    || !saved.playerState().ownedFleetIds().equals(initial.playerState().ownedFleetIds())
                    || !saved.personalCraftOwnership().equals(initial.personalCraftOwnership())) throw new AssertionError("Navigation granted assets or money");
            Stage228GeneratedCampaignPersistenceCodec.write(output, saved);
            if (!saved.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
                throw new AssertionError("Non-exact explored route reload");
        }
        System.out.println("ORDINARY INDUSTRIAL NAVIGATION " + (stop.equals("DESTINATION_SYSTEM_REACHED") ? "PASS" : "PARTIAL")
                + " station=" + target.object().objectId() + " hops=" + hops + " stop=" + stop + "; only personally observed UI exits and discovered routes; acquisition not claimed");
    }
}
