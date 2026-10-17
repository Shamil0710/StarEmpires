import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;
import java.util.TreeMap;

/** Read-only diagnostic dependency/stock audit; foreign inventory is never granted as personal knowledge. */
public final class Stage23BActualSectionSupplyInspect {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Supply actual checkpoint and owned station");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        var c = Stage228CampaignAuthority.restore(saved);
        String station = args[1];
        if (!c.ownsProductionStation(station)) throw new AssertionError("Actual ownership required");
        var catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
        var refining = Stage18RefiningCatalogLoader.loadDefault();
        String product = "module.industrial_union_cargo_section_v1";
        double mass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(product).unitMassKg();
        var direct = new TreeMap<String, Double>();
        catalog.findProductProfile(catalog.findProductBinding(product).profileId()).inputs()
                .forEach(i -> direct.merge(i.commodityId(), mass * i.fractionOfOutputMass(), Double::sum));
        var material = new TreeMap<String, Double>();
        for (var demand : direct.entrySet()) {
            var recipe = catalog.getComponentRecipes().stream().filter(r -> r.outputCommodityId().equals(demand.getKey())).findFirst();
            if (recipe.isEmpty()) material.merge(demand.getKey(), demand.getValue(), Double::sum);
            else recipe.orElseThrow().inputs().forEach(i -> material.merge(i.commodityId(), demand.getValue() * i.fractionOfOutputMass(), Double::sum));
        }
        var feedstock = new TreeMap<String, Double>();
        for (var installedStation : c.coordinator().runtime().industry().industrial().stations()) {
            System.out.println("DIAGNOSTIC INSTALLED STATION station=" + installedStation.stationId()
                    + " class=" + installedStation.stationArchetypeId() + " facilities=" + installedStation.facilities());
        }
        for (var demand : material.entrySet()) {
            var recipe = refining.getRecipes().stream().filter(r -> r.outputCommodityId().equals(demand.getKey())).findFirst().orElseThrow();
            double gross = demand.getValue() / recipe.outputMassFraction();
            recipe.inputs().forEach(i -> feedstock.merge(i.commodityId(), gross * i.fractionOfInputMass(), Double::sum));
            System.out.println("MATERIAL demand=" + demand + " recipe=" + recipe.id() + " grossKg=" + gross
                    + " ownLine=" + c.hasRefiningLine(station, recipe.id()));
            for (var candidate : c.coordinator().runtime().industry().industrial().stations()) {
                if (!c.hasRefiningLine(candidate.stationId(), recipe.id())) continue;
                System.out.println("DIAGNOSTIC PROCESS LINE recipe=" + recipe.id() + " station=" + candidate.stationId()
                        + " system=" + candidate.systemId().value() + " own=" + c.ownsProductionStation(candidate.stationId())
                        + " saleMilliCredits=" + c.stationSalePriceMilliCredits(candidate.stationId()));
            }
        }
        System.out.println("DIRECT BILL " + direct);
        System.out.println("EXPANDED MATERIAL BILL " + material);
        System.out.println("EXPANDED FEEDSTOCK BILL " + feedstock);
        var knowledge = c.coordinator().runtime().discoveryState().knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        var ids = new java.util.TreeSet<String>(); ids.addAll(direct.keySet()); ids.addAll(material.keySet()); ids.addAll(feedstock.keySet());
        for (var endpoint : c.coordinator().runtime().infrastructure().endpoints()) {
            var available = new TreeMap<String, Double>();
            for (String id : ids) if (endpoint.storage().commodityMassKg(id) > 0) available.put(id, endpoint.storage().commodityMassKg(id));
            if (available.isEmpty()) continue;
            boolean known = knowledge.entries().stream().anyMatch(k -> k.object().objectId().equals(endpoint.stationId()) && k.knownLocation().isPresent());
            System.out.println("DIAGNOSTIC SOURCE station=" + endpoint.stationId() + " system=" + endpoint.systemId().value()
                    + " personallyKnown=" + known + " stock=" + available);
        }
        System.out.println("PERSONAL DISCOVERIES " + knowledge.entries().stream().filter(k -> k.object().resourceObject() && k.knownLocation().isPresent()).toList());
        if (!saved.equals(c.captureState())) throw new AssertionError("Supply audit changed actual checkpoint");
        System.out.println("ACTUAL SECTION SUPPLY AUDIT PASS; dependency quantities are authored, foreign stocks diagnostic only");
    }
}
