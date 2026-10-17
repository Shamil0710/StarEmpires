import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignManufacturingUi;
import com.spacesim.ui.GeneratedCampaignRefitUi;
import com.spacesim.ui.GeneratedCampaignRepairUi;
import java.nio.file.Path;

/** Pure operational audit of an actually acquired station; never supplies berth, stock or ownership. */
public final class Stage23BActualOwnedYardInspect {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply actual purchased checkpoint and station ID");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        var campaign = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(campaign.captureState())) throw new AssertionError("Non-exact acquired station checkpoint");
        String stationId = args[1];
        if (!campaign.ownsProductionStation(stationId)) throw new AssertionError("Actual personal station ownership required");
        var player = campaign.playerState().orElseThrow();
        if (!campaign.pilotMarketReference(stationId).orElseThrow().equals(player.dockedAt()))
            throw new AssertionError("Actual acquired station berth required");
        var station = campaign.coordinator().runtime().industry().industrial().station(stationId);
        var runtime = campaign.coordinator().runtime();
        var fleet = runtime.world().findFleet(player.activeFleetId()).orElseThrow();
        var ship = runtime.world().findSession(fleet.systemId()).orElseThrow().getEntityRegistry()
                .require(fleet.localEntityId()).getComponent(com.spacesim.components.EngineeringComponent.class);
        var targetFit = com.spacesim.ship.ShipEngineeringState.InstalledFit.fromDemonstrator(
                Stage22FreightStrategicEngineeringCatalogLoader.loadDefault().findDemonstratorFit(
                        Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT));
        for (var yard : station.yardCapabilities()) {
            System.out.println("ACTUAL REFIT PHYSICAL PLAN " + com.spacesim.economy.ShipyardRefitWorkQueue
                    .plan(fleet.localEntityId(), ship, targetFit, yard));
        }
        System.out.println("ACTUAL COMMODITY STOCK " + station.storage().snapshot().commodityMassByIdKg());
        System.out.println("ACQUIRED STATION station=" + stationId + " wallet=" + player.walletMilliCredits()
                + " tick=" + campaign.coordinator().runtime().world().getAuthoritativeWorldTick());
        System.out.println("ACTUAL YARDS " + station.yardCapabilities());
        System.out.println("REPAIR ACCESS " + campaign.hasPersonalRepairAccess(stationId));
        for (String target : java.util.List.of(Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT,
                Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT)) {
            System.out.println("REFIT ACCESS target=" + target + " permitted=" + campaign.hasPersonalRefitAccess(stationId, target));
        }
        System.out.println("MANUFACTURING UI " + GeneratedCampaignManufacturingUi.rows(campaign));
        System.out.println("REPAIR UI " + GeneratedCampaignRepairUi.rows(campaign));
        System.out.println("REFIT UI " + GeneratedCampaignRefitUi.rows(campaign));
        String cargo = "module.industrial_union_cargo_section_v1";
        var products = com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts();
        var manufacturing = com.spacesim.content.Stage22CivilianMiningProductionPath.loadManufacturing();
        var profile = manufacturing.findProductProfile(manufacturing.findProductBinding(cargo).profileId());
        double cargoMass = products.findProduct(cargo).unitMassKg();
        System.out.println("CARGO LINE compatible=" + campaign.hasManufacturingLine(stationId, cargo));
        for (var input : profile.inputs()) {
            double required = input.fractionOfOutputMass() * cargoMass;
            double available = station.storage().commodityMassKg(input.commodityId());
            System.out.println("CARGO MATERIAL commodity=" + input.commodityId() + " requiredKg=" + required
                    + " availableKg=" + available + " shortageKg=" + Math.max(0, required - available));
        }
        var cargoPreview = campaign.previewPilotAction("START_MANUFACTURING", stationId, cargo, 1);
        System.out.println("CARGO MANUFACTURING PREVIEW allowed=" + cargoPreview.allowed());
        var refitPreview = campaign.previewPilotAction("START_REFIT", stationId,
                Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT, 1);
        System.out.println("CARGO REFIT PREVIEW allowed=" + refitPreview.allowed());
        if (!saved.equals(campaign.captureState())) throw new AssertionError("Read/preview audit changed actual acquired campaign");
        System.out.println("ACTUAL OWNED YARD AUDIT PASS; operation completion is not claimed");
    }
}
