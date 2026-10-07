import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import com.spacesim.ui.GeneratedCampaignResourceMapUi;
import java.nio.file.Path;

/** Uses the ordinary journey's actual berth to buy an existing NPC report without fixtures. */
public final class Stage23BOrdinaryResourceMapJourney {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply actual journey checkpoint and output checkpoint");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        var campaign = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(campaign.captureState())) throw new AssertionError("Non-exact input checkpoint");
        var runtime = campaign.coordinator().runtime();
        if (!campaign.playerState().orElseThrow().docked()) throw new AssertionError("Actual seller contact required");
        var before = campaign.captureState();
        var archivesBefore = runtime.discoveryState();
        long tick = runtime.world().getAuthoritativeWorldTick();
        var rows = GeneratedCampaignResourceMapUi.rows(campaign);
        if (!before.equals(campaign.captureState())) throw new AssertionError("UI created knowledge");
        System.out.println("ACTUAL MAP OFFERS " + rows.size());
        for (var npc : campaign.coordinator().npcMissions().npcs()) {
            if (npc.npcId().startsWith("npc.stage23b.dispatcher:")) {
                var reports = runtime.discoveryState().knowledgeFor(npc.factionContentId()).entries().stream()
                        .filter(e -> e.object().resourceObject()).toList();
                System.out.println("DISPATCHER " + npc.npcId() + " contact=" + campaign.canContactNpc(npc.npcId())
                        + " system=" + npc.locationSystemId()
                        + " archivedResourceReports=" + reports.size());
            }
            for (var report : runtime.discoveryState().knowledgeFor(npc.factionContentId()).entries()) {
                String objectId = report.object().objectId();
                if (!campaign.canPurchaseResourceMap(npc.npcId(), objectId)) continue;
                String selection = "pilot-resource-map|" + npc.npcId() + '|' + objectId;
                if (rows.stream().noneMatch(row -> row.selection().stableId().equals(selection)))
                    throw new AssertionError("Actual map purchase is absent from the production UI");
                var preview = campaign.previewPilotAction("BUY_RESOURCE_MAP", npc.npcId(), objectId, 1);
                if (!preview.allowed() || !before.equals(campaign.captureState())) throw new AssertionError("Invalid map preview");
                long treasury = runtime.world().findFactionEconomicState(npc.factionContentId()).orElseThrow().treasuryMilliCredits();
                campaign.submitPilotAction(preview);
                var received = runtime.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
                        .knowledge(report.object()).orElseThrow();
                var originalEvidence = report.evidence().stream().max(java.util.Comparator.comparingDouble(e -> e.observedAtSeconds())).orElseThrow();
                var transferredEvidence = received.evidence().stream().filter(e -> e.provenanceId()
                        .equals("paid-resource-map:" + npc.npcId() + ':' + objectId)).findFirst().orElseThrow();
                if (!received.knownLocation().equals(report.knownLocation())
                        || !received.resourceKnowledge().equals(report.resourceKnowledge())
                        || transferredEvidence.observedAtSeconds() != originalEvidence.observedAtSeconds()
                        || !transferredEvidence.freshUntilSeconds().equals(originalEvidence.freshUntilSeconds())
                        || campaign.playerState().orElseThrow().walletMilliCredits() != before.playerState().walletMilliCredits() + preview.walletChangeMilliCredits()
                        || runtime.world().findFactionEconomicState(npc.factionContentId()).orElseThrow().treasuryMilliCredits() != treasury - preview.walletChangeMilliCredits()
                        || runtime.world().getAuthoritativeWorldTick() != tick)
                    throw new AssertionError("Non-conserved or invented map knowledge");
                var purchased = campaign.captureState();
                for (var archive : archivesBefore.knowledgeStates()) {
                    if (!archive.ownerId().equals(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
                            && !archive.equals(runtime.discoveryState().knowledgeFor(archive.ownerId())))
                        throw new AssertionError("Purchasing a copy changed another actor's archive");
                }
                Stage228GeneratedCampaignPersistenceCodec.write(Path.of(args[1]), purchased);
                var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[1])));
                if (!purchased.equals(restored.captureState())) throw new AssertionError("Non-exact purchased map checkpoint");
                System.out.println("ORDINARY RESOURCE MAP PASS seller=" + npc.npcId() + " object=" + objectId
                        + " system=" + report.object().systemId() + " location=" + received.knownLocation()
                        + " evidence=" + received.evidence());
                return;
            }
        }
        throw new AssertionError("No actual map offer at the ordinary journey berth; no contact or report fixture applied");
    }
}
