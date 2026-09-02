package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22CoreContentSeamCatalog.VisualBindingDefinition;
import com.spacesim.content.Stage22CoreProductionManifestCatalog.ProductionManifestDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Exact-fit M22.4 Union production-manifest and visual-binding projection over common authorities. */
public final class Stage22IndustrialUnionProductionCatalogs {
    public static final String YARD_ID=Stage22IndustrialUnionIndustrialProgram.YARD_ID;
    private Stage22IndustrialUnionProductionCatalogs(){throw new AssertionError("utility class");}

    public static Stage22CoreProductionManifestCatalog loadManifests(){
        Stage22IndustrialUnionPackageCatalog union=Stage22IndustrialUnionPackageLoader.loadDefault();
        ShipEngineeringCatalog engineering=Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        Stage18ShipyardCatalog yards=Stage22IndustrialUnionShipyardCatalogLoader.loadDefault();
        var yard=Objects.requireNonNull(yards.findYard(YARD_ID),"Industrial Union series yard");
        List<ProductionManifestDefinition> manifests=new ArrayList<>();
        for(var family:union.shipFamilies()){
            DemonstratorFitDefinition fit=requireFit(engineering,family.primaryFitId());
            manifests.add(new ProductionManifestDefinition(family.productionManifestId(),fit.id(),fit.hullId(),
                    fit.installedModules().stream().map(value->value.moduleId()).toList(),YARD_ID,
                    yard.requiredSupportFacilityDefinitionIds().stream().sorted().toList(),ContentMaturity.VALIDATED,
                    "M22.4 Union primary fit uses ordinary Stage-17.5 engineering and Stage-18 manufacturing/shipyard authority."));
        }
        return new Stage22CoreProductionManifestCatalog(1,"stage22.industrial_union_production_manifests.v1",manifests,List.of());
    }

    public static List<VisualBindingDefinition> loadVisualBindings(){
        Stage22IndustrialUnionPackageCatalog union=Stage22IndustrialUnionPackageLoader.loadDefault();
        ShipEngineeringCatalog engineering=Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        List<VisualBindingDefinition> result=new ArrayList<>();
        for(var family:union.shipFamilies()){
            String asset=assetRef(family.familyId());
            result.add(binding(family.visualBindingId(),family.primaryFitId(),asset,engineering));
            result.add(binding(family.visualBindingId()+".refit",family.refitFitId(),asset,engineering));
        }
        result.sort(Comparator.comparing(VisualBindingDefinition::id));
        return List.copyOf(result);
    }

    private static VisualBindingDefinition binding(String id,String fitId,String asset,ShipEngineeringCatalog engineering){
        requireFit(engineering,fitId);
        return new VisualBindingDefinition(id,fitId,AssetStatus.PRODUCTION,Stage22FitFingerprint.compute(engineering,fitId),asset,
                "docs/factions/industrial_union_visual_bible.md");
    }
    private static String assetRef(String familyId){String p="ship_family.industrial_union."; if(!familyId.startsWith(p))throw new IllegalArgumentException(familyId); String s=familyId.substring(p.length()); return "assets/ships/industrial_union/production/"+s+"/"+s+"_base.png";}
    private static DemonstratorFitDefinition requireFit(ShipEngineeringCatalog engineering,String id){var fit=engineering.findDemonstratorFit(id); if(fit==null)throw new IllegalArgumentException("Unknown Industrial Union fit: "+id); return fit;}
}
