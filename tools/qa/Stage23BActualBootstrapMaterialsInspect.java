import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage23BootstrapMaterialsCatalog;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignConstructionUi;
import java.nio.file.Path;

/** Actual old checkpoint -> available authored bootstrap bill -> pure rejected request -> native v21. */
public final class Stage23BActualBootstrapMaterialsInspect {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Supply real input, separate v21 output and own station");
        Path input = Path.of(args[0]); Path output = Path.of(args[1]);
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())) throw new IllegalArgumentException("Preserve legacy source separately");
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
        var c = Stage228CampaignAuthority.restore(saved); String station = args[2];
        if (!saved.equals(c.captureState()) || !c.ownsProductionStation(station)) throw new AssertionError("Real exact acquired state required");
        String design = Stage23BootstrapMaterialsCatalog.FACILITY_ID;
        if (c.coordinator().runtime().industry().industrial().stations().stream().flatMap(s -> s.facilities().stream())
                .anyMatch(f -> f.definitionId().equals(design))) throw new AssertionError("Legacy restore created bootstrap equipment");
        if (GeneratedCampaignConstructionUi.rows(c).stream().noneMatch(r -> r.selection().stableId().equals("pilot-construction|" + station + '|' + design)))
            throw new AssertionError("Authored construction action missing");
        boolean compatible = c.hasFacilityConstructionLine(station, design);
        System.out.println("ACTUAL BOOTSTRAP LINE compatible=" + compatible);
        if (!compatible) throw new AssertionError("Existing acquired assembly must support the basic-alloy construction bill");
        var store = c.coordinator().runtime().infrastructure().endpoint(station).storage();
        boolean missing = false;
        for (var inputMass : Stage23BootstrapMaterialsCatalog.construction().requiredMassByCommodityKg(design).entrySet()) {
            double available = store.commodityMassKg(inputMass.getKey()); missing |= inputMass.getValue() > available;
            System.out.println("ACTUAL BOOTSTRAP INPUT commodity=" + inputMass.getKey() + " requiredKg=" + inputMass.getValue()
                    + " availableKg=" + available + " shortageKg=" + Math.max(0, inputMass.getValue() - available));
        }
        var preview = c.previewPilotAction("START_FACILITY_CONSTRUCTION", station, design, 1);
        if (!missing || preview.allowed() || !saved.equals(c.captureState())) throw new AssertionError("Missing physical bill must reject purely");
        preview = null;
        System.out.println("RUNTIME FACILITY FINGERPRINT " + Stage23BootstrapMaterialsCatalog.facilities().getFingerprint());
        System.out.println("RUNTIME CONSTRUCTION FINGERPRINT " + Stage23BootstrapMaterialsCatalog.construction().getFingerprint());
        c = null;
        Stage228GeneratedCampaignPersistenceCodec.write(output, saved);
        if (!saved.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
            throw new AssertionError("Native v21 admission changed old ownership, stock, clock or structures");
        System.out.println("ACTUAL BOOTSTRAP MATERIALS AUDIT PASS; old assets exact, real compatible assembly, rejected missing bill, no new structure or work granted");
    }
}
