import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.Stage21HPlayerMissionAuthority;

/** Read-only ordinary checkpoint offer/navigation audit; never supplies missing coordinates. */
public final class Stage23BActualStationOfferInspect {
    public static void main(String[] args) throws Exception {
        var c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(
                java.nio.file.Path.of(args[0])));
        var p = c.playerState().orElseThrow(); var r = c.coordinator().runtime();
        var before = c.captureState();
        var fleet = r.world().findFleet(p.activeFleetId()).orElseThrow();
        var physical = r.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
        System.out.println("PLAYER wallet=" + p.walletMilliCredits() + " system=" + fleet.systemId() + " dock=" + p.dockedAt());
        var knowledge = r.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        for (var station : r.industry().industrial().stations()) {
            long price = c.stationSalePriceMilliCredits(station.stationId());
            if (price <= 0) continue;
            var known = knowledge.entries().stream().filter(k -> k.object().objectId().equals(station.stationId())
                    && k.knownLocation().isPresent()).findFirst()
                    .orElseThrow(() -> new AssertionError("Offered station has no personal navigation archive: " + station.stationId()));
            System.out.println("OFFER station=" + station.stationId() + " class=" + station.stationArchetypeId()
                    + " price=" + price + " affordable=" + (price <= p.walletMilliCredits()) + " system=" + known.object().systemId()
                    + " personalDistance=" + (fleet.systemId().equals(known.object().systemId())
                        ? physical.position().distanceTo(known.knownLocation().orElseThrow()) : "other-system"));
            var paths = new java.util.HashMap<com.spacesim.world.StarSystemId, java.util.List<com.spacesim.world.StarSystemId>>();
            var pending = new java.util.ArrayDeque<com.spacesim.world.StarSystemId>();
            paths.put(fleet.systemId(), java.util.List.of(fleet.systemId())); pending.add(fleet.systemId());
            while (!pending.isEmpty() && !paths.containsKey(known.object().systemId())) {
                var current = pending.remove();
                for (var next : r.world().getTopology().neighbors(current)) if (!paths.containsKey(next)) {
                    var path = new java.util.ArrayList<>(paths.get(current)); path.add(next);
                    paths.put(next, java.util.List.copyOf(path)); pending.add(next);
                }
            }
            System.out.println("AUDIT TOPOLOGY ROUTE " + paths.get(known.object().systemId())
                    + "; diagnostic itinerary, not newly granted personal navigation knowledge");
            // Diagnostic capabilities are not transferred to the player's archive by this audit.
            System.out.println("AUDIT INDUSTRY station=" + station.stationId() + " installedYards=" + station.yards()
                    + " facilities=" + station.facilityCapabilities());
            for (var specification : com.spacesim.content.Stage23YardConstructionCatalog.loadDefault().specifications()) {
                System.out.println("AUDIT YARD DESIGN station=" + station.stationId() + " design=" + specification.yardDefinitionId()
                        + " compatibleLine=" + c.hasYardConstructionLine(station.stationId(), specification.yardDefinitionId())
                        + " specification=" + specification);
            }
        }
        if (!before.equals(c.captureState())) throw new AssertionError("Offer/capability audit changed the actual checkpoint");
    }
}
