import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage23SmallCraftProductionCatalogs;
import com.spacesim.economy.ShipyardSmallCraftBuildWorkQueue;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;

/** Pure audit of real owned capacity and full bills; never supplies materials or work. */
public final class Stage23BActualSmallCraftSupplyAudit {
    public static void main(String[] args) throws Exception {
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        var c = Stage228CampaignAuthority.restore(saved);
        var rows = com.spacesim.ui.GeneratedCampaignManufacturingUi.rows(c);
        for (String module : Stage23SmallCraftProductionCatalogs.moduleIds()) {
            String expected = com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader.loadDefault()
                    .findModule(module).displayName();
            for (var row : rows) if (row.selection().stableId().endsWith("|" + module) && !row.name().equals(expected))
                throw new AssertionError("Module manufacturing card has the wrong authored name: " + module);
        }
        System.out.println("AUTHORED MODULE CARD NAMES PASS");
        for (var station : c.coordinator().runtime().industry().industrial().stations()) {
            if (!c.ownsProductionStation(station.stationId())) continue;
            var store = station.storage();
            station.yardCapabilities().forEach(y -> System.out.println("INSTALLED_YARD station=" + station.stationId()
                    + " instance=" + y.yardInstanceId() + " definition=" + y.yardDefinitionId() + " status=" + y.status()));
            for (String design : Stage23SmallCraftProductionCatalogs.designIds()) {
                long yards = station.yardCapabilities().stream().filter(y -> y.active()
                        && ShipyardSmallCraftBuildWorkQueue.plan(design, y).feasibility().feasible()).count();
                System.out.println("CRAFT station=" + station.stationId() + " design=" + design + " activeCompatibleYards=" + yards);
                for (var d : Stage23SmallCraftProductionCatalogs.shipyards().getYards()) {
                    var rated = new com.spacesim.economy.Stage18ShipyardRuntime.YardCapabilitySnapshot("design.only", d.id(),
                            com.spacesim.economy.Stage18ShipyardRuntime.YardStatus.ACTIVE,
                            new com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability("design.only", d.berthDimensionsM(),
                                    d.maxServiceMassKg(), d.stage175FabricationCapabilities(), d.stage175HandledRequirementIds(),
                                    d.toolingTags(), d.precisionCapability(), d.ratedEngineeringWorkRate(), d.laborCapacity(),
                                    d.automationCapacity(), d.ratedIntegrationPowerW()), d.handledStorageClassIds(), d.maxHandledUnitMassKg());
                    if (!ShipyardSmallCraftBuildWorkQueue.plan(design, rated).feasibility().feasible()) continue;
                    var construction = com.spacesim.content.Stage23YardConstructionCatalog.loadDefault().find(d.id());
                    System.out.println("COMPATIBLE_YARD_DESIGN design=" + design + " yard=" + d.id()
                            + " constructionBinding=" + (construction != null));
                    if (construction != null) construction.requiredMassByCommodityKg().forEach((id, kg) ->
                            System.out.println("YARD_BILL yard=" + d.id() + " commodity=" + id + " requiredKg=" + kg
                                    + " availableKg=" + store.commodityMassKg(id)));
                }
                var fit = Stage23SmallCraftProductionCatalogs.fit(design);
                for (var i : Stage23SmallCraftProductionCatalogs.shipyards().findHullProfile(fit.hullId()).buildInputsKg())
                    System.out.println("HULL commodity=" + i.commodityId() + " requiredKg=" + i.massKg()
                            + " availableKg=" + store.commodityMassKg(i.commodityId()));
                for (var m : fit.installedModules()) {
                    System.out.println("MODULE product=" + m.moduleId() + " required=1 available=" + store.productCount(m.moduleId())
                            + " manufacturingLine=" + c.hasManufacturingLine(station.stationId(), m.moduleId()));
                    var catalogs = Stage23SmallCraftProductionCatalogs.manufacturing();
                    var recipe = catalogs.findProductProfile(catalogs.findProductBinding(m.moduleId()).profileId());
                    double mass = Stage23SmallCraftProductionCatalogs.products().findProduct(m.moduleId()).unitMassKg();
                    for (var i : recipe.inputs())
                        System.out.println("MODULE_BILL product=" + m.moduleId() + " commodity=" + i.commodityId()
                                + " requiredKg=" + mass * i.fractionOfOutputMass()
                                + " availableKg=" + store.commodityMassKg(i.commodityId()));
                }
            }
        }
        if (!saved.equals(c.captureState())) throw new AssertionError("Supply audit changed actual state");
        System.out.println("ACTUAL SMALL CRAFT SUPPLY AUDIT PASS; no production, delivery or ordinary acquisition claimed");
    }
}
