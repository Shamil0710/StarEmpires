import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;

/** Pure audit of contacts at an actual saved berth; no shortage, stock or offer is injected. */
public final class Stage23BActualNpcContractInspect {
    public static void main(String[] args) throws Exception {
        var c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0])));
        var before = c.captureState(); var player = c.playerState().orElseThrow();
        System.out.println("ACTUAL tick=" + c.coordinator().runtime().world().getAuthoritativeWorldTick()
                + " dock=" + player.dockedAt() + " walletMilliCredits=" + player.walletMilliCredits());
        int contacts = 0, contracts = 0;
        for (var npc : c.coordinator().npcMissions().npcs()) {
            if (!c.canContactNpc(npc.npcId())) continue;
            contacts++;
            System.out.println("ACCESSIBLE NPC " + npc.npcId() + " availability=" + npc.availability());
            for (var mission : c.coordinator().npcMissions().missions()) {
                if (!mission.issuerNpcId().equals(npc.npcId())) continue;
                contracts++;
                System.out.println("LOCAL CONTRACT " + mission.missionId() + " status=" + mission.status()
                        + " objective=" + mission.objective() + " escrow=" + mission.escrowMilliCredits());
            }
        }
        var hold = c.coordinator().runtime().freight().cargoHoldSnapshot(player.activeFleetId());
        System.out.println("PERSONAL CARGO " + hold.commodityMassByIdKg());
        if (!before.equals(c.captureState())) throw new AssertionError("Read-only contact audit mutated the checkpoint");
        System.out.println("ACTUAL NPC AUDIT PASS accessibleContacts=" + contacts + " localContracts=" + contracts
                + "; ordinary completion is not claimed");
    }
}
