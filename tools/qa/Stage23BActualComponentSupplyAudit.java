import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignComponentUi;
import java.nio.file.Path;

/** Pure audit of component access and shortages on an actual acquired/refined campaign. */
public final class Stage23BActualComponentSupplyAudit {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply real checkpoint and owned station");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        var campaign = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(campaign.captureState())) throw new AssertionError("Non-exact migrated actual campaign");
        String stationId = args[1];
        if (!campaign.ownsProductionStation(stationId)
                || !campaign.pilotMarketReference(stationId).orElseThrow().equals(campaign.playerState().orElseThrow().dockedAt()))
            throw new AssertionError("Actual acquired station and berth required");
        var rows = GeneratedCampaignComponentUi.rows(campaign);
        var store = campaign.coordinator().runtime().industry().industrial().station(stationId).storage();
        for (var recipe : Stage22CivilianMiningProductionPath.loadManufacturing().getComponentRecipes()) {
            if (rows.stream().noneMatch(r -> r.selection().stableId().equals("pilot-components|" + stationId + '|' + recipe.id())))
                throw new AssertionError("Actual component action absent from personal UI");
            boolean shortage = false;
            System.out.println("COMPONENT LINE recipe=" + recipe.id() + " compatible=" + campaign.hasComponentLine(stationId, recipe.id()));
            for (var input : recipe.inputs()) {
                double mass = 100 * input.fractionOfOutputMass();
                double available = store.commodityMassKg(input.commodityId());
                shortage |= available < mass;
                System.out.println("COMPONENT MATERIAL recipe=" + recipe.id() + " commodity=" + input.commodityId()
                        + " requiredKg=" + mass + " availableKg=" + available + " shortageKg=" + Math.max(0, mass - available));
            }
            var preview = campaign.previewPilotAction("START_COMPONENT_MANUFACTURING", stationId, recipe.id(), 100);
            System.out.println("COMPONENT PREVIEW recipe=" + recipe.id() + " allowed=" + preview.allowed());
            if (shortage && preview.allowed()) throw new AssertionError("Component request ignored actual material shortage");
        }
        if (!saved.equals(campaign.captureState())) throw new AssertionError("Component reads/previews changed actual campaign");
        System.out.println("ACTUAL COMPONENT SUPPLY AUDIT PASS; production is not claimed and no inputs are supplied");
    }
}
