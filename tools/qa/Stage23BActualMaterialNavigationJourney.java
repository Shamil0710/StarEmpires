import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.player.PlayerFleetRoutePlanner;
import com.spacesim.ui.GeneratedCampaignResourceMapUi;
import com.spacesim.ui.GeneratedCampaignRouteMapUi;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import java.nio.file.Path;
import java.util.*;

/** Actual contact -> choose advertised raw-material reports -> paid navigation -> exact native reload. */
public final class Stage23BActualMaterialNavigationJourney {
    private static Stage228CampaignAuthority c;
    private static Stage20DiscoveryKnowledgeState knowledge() {
        return c.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
    }
    private static Optional<Stage20DiscoveryKnowledgeState.StaticKnowledge> source(String commodity) {
        return knowledge().entries().stream().filter(k -> k.object().resourceObject() && k.knownLocation().isPresent()
                && k.classificationId().filter(commodity::equals).isPresent()).findFirst();
    }
    private static boolean routeAvailable(Stage20DiscoveryKnowledgeState.StaticKnowledge destination) {
        var p = c.playerState().orElseThrow(); var world = c.coordinator().runtime().world();
        var origin = world.findFleet(p.activeFleetId()).orElseThrow().systemId();
        var route = new PlayerFleetRoutePlanner(world, c.coordinator().content(), p).plan(p.activeFleetId(), origin, destination.object().systemId());
        if (route.isEmpty()) return false;
        if (!p.discoveredSystemIds().containsAll(route.orElseThrow().path())) throw new AssertionError("Planner used unowned navigation knowledge");
        System.out.println("PERSONAL MATERIAL ROUTE commodity=" + destination.classificationId().orElseThrow()
                + " path=" + route.orElseThrow().path() + " travelTicks=" + route.orElseThrow().travelTicks());
        return true;
    }
    private static boolean knownTopologyConnects(Stage20DiscoveryKnowledgeState.StaticKnowledge destination) {
        var p = c.playerState().orElseThrow(); var world = c.coordinator().runtime().world();
        var origin = world.findFleet(p.activeFleetId()).orElseThrow().systemId();
        var seen = new HashSet<com.spacesim.world.StarSystemId>(); var pending = new ArrayDeque<com.spacesim.world.StarSystemId>();
        seen.add(origin); pending.add(origin);
        while (!pending.isEmpty()) {
            var current = pending.remove(); if (current.equals(destination.object().systemId())) return true;
            for (var next : world.getTopology().neighbors(current))
                if (p.discoveredSystemIds().contains(next) && seen.add(next)) pending.add(next);
        }
        return false;
    }
    private static void buy(String verb, String selection) {
        String[] fields = selection.split("\\|", 3);
        var before = c.captureState();
        var preview = c.previewPilotAction(verb, fields[1], fields[2], 1);
        if (!preview.allowed() || !before.equals(c.captureState())) throw new AssertionError("Actual map preview unavailable or impure");
        c.submitPilotAction(preview);
        System.out.println("ACTUAL NAVIGATION PURCHASE action=" + verb + " costMilliCredits=" + -preview.walletChangeMilliCredits());
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply actual input and separate output");
        Path input = Path.of(args[0]); Path output = Path.of(args[1]);
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) throw new IllegalArgumentException("Preserve the actual source separately");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        c = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(c.captureState()) || !c.playerState().orElseThrow().docked()) throw new AssertionError("Exact actual seller berth required");
        var player = c.playerState().orElseThrow();
        var industrial = c.coordinator().runtime().captureState().campaign().industrialState();
        long tick = c.coordinator().runtime().world().getAuthoritativeWorldTick();
        saved = null;
        var commodities = List.of("commodity.feedstock.metallic_ore", "commodity.feedstock.light_metal_minerals", "commodity.feedstock.conductor_ore");
        var unavailable = new ArrayList<String>(); int purchases = 0;
        for (String commodity : commodities) {
            if (source(commodity).isPresent()) continue;
            var offers = GeneratedCampaignResourceMapUi.rows(c);
            var offer = offers.stream().filter(row -> {
                String[] fields = row.selection().stableId().split("\\|", 3);
                var npc = c.coordinator().npcMissions().npcs().stream().filter(n -> n.npcId().equals(fields[1])).findFirst().orElseThrow();
                return c.coordinator().runtime().discoveryState().knowledgeFor(npc.factionContentId()).entries().stream()
                        .anyMatch(k -> k.object().objectId().equals(fields[2]) && k.classificationId().filter(commodity::equals).isPresent());
            }).findFirst();
            for (var row : offers) System.out.println("PUBLIC RESOURCE OFFER " + row.name());
            if (offer.isEmpty()) { unavailable.add(commodity); continue; }
            buy("BUY_RESOURCE_MAP", offer.orElseThrow().selection().stableId()); purchases++;
            if (source(commodity).isEmpty()) throw new AssertionError("Paid classified source not in personal archive");
        }
        for (String commodity : commodities) {
            var destination = source(commodity); if (destination.isEmpty() || routeAvailable(destination.orElseThrow())) continue;
            // Choose visible offers in UI order; never inspect unpaid paths to find a secret shortcut.
            int charts = 0;
            while (!routeAvailable(destination.orElseThrow())) {
                if (knownTopologyConnects(destination.orElseThrow())) { unavailable.add("fuel-or-state:" + commodity); break; }
                var offers = GeneratedCampaignRouteMapUi.offers(c);
                if (offers.isEmpty() || ++charts > 20) { unavailable.add("route:" + commodity); break; }
                buy("BUY_ROUTE_MAP", offers.get(0).selection().stableId()); purchases++;
            }
        }
        if (!industrial.equals(c.coordinator().runtime().captureState().campaign().industrialState())
                || c.coordinator().runtime().world().getAuthoritativeWorldTick() != tick
                || !player.ownedFleetIds().equals(c.playerState().orElseThrow().ownedFleetIds())
                || player.walletMilliCredits() - c.playerState().orElseThrow().walletMilliCredits() != purchases * 100_000L)
            throw new AssertionError("Navigation purchase granted stock, work, assets or changed its price");
        var complete = c.captureState(); c = null;
        Stage228GeneratedCampaignPersistenceCodec.write(output, complete);
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
        if (!complete.equals(restored.captureState())) throw new AssertionError("Material navigation changed on native reload");
        System.out.println("ACTUAL MATERIAL NAVIGATION " + (unavailable.isEmpty() ? "PASS" : "PARTIAL")
                + " purchases=" + purchases + " walletMilliCredits=" + complete.playerState().walletMilliCredits()
                + " unavailable=" + unavailable + "; only actual paid contact offers, no truth coordinates or material/geometry fixtures");
    }
}
