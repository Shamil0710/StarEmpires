import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.*;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignConstructionUi;
import java.nio.file.Path;

/** Pure construction/shortage audit on an actual acquired station; never supplies its bill or work. */
public final class Stage23BActualStorageExtensionInspect {
    public static void main(String[] args) throws Exception {
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        var c = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(c.captureState())) throw new AssertionError("Non-exact migrated ordinary checkpoint");
        String id = args[1]; var r = c.coordinator().runtime();
        if (!c.ownsProductionStation(id) || !c.pilotMarketReference(id).orElseThrow().equals(c.playerState().orElseThrow().dockedAt()))
            throw new IllegalStateException("Actual owned station and saved physical berth required");
        var station = r.industry().industrial().station(id); var endpoint = r.infrastructure().endpoint(id);
        if (station.storage() != endpoint.storage() || station.stationNode().storage() != endpoint.storage())
            throw new AssertionError("Station storage owner split");
        System.out.println("ACTUAL STORAGE HOST station=" + id + " archetype=" + station.stationArchetypeId()
                + " tick=" + r.world().getAuthoritativeWorldTick() + " capacity=" + endpoint.storage().snapshotCapacityByStorageClassKg());
        System.out.println("RUNTIME STORAGE FACILITIES " + Stage23StationStorageCatalog.facilities().getFingerprint());
        System.out.println("RUNTIME STORAGE CONSTRUCTION " + Stage23StationStorageCatalog.construction().getFingerprint());
        System.out.println("RUNTIME STORAGE EFFECT " + Stage23StationStorageCatalog.effectFingerprint());
        String design = Stage23StationStorageCatalog.FACILITY_ID;
        var row = GeneratedCampaignConstructionUi.rows(c).stream().filter(v -> v.selection().stableId()
                .equals("pilot-construction|" + id + '|' + design)).findFirst().orElseThrow();
        System.out.println("ACTUAL STORAGE UI " + row.name() + " sections=" + row.sections());
        boolean shortage = false;
        for (var bill : Stage23StationStorageCatalog.construction().requiredMassByCommodityKg(design).entrySet()) {
            double available = endpoint.storage().commodityMassKg(bill.getKey());
            shortage |= available < bill.getValue();
            System.out.println("ACTUAL STORAGE BILL commodity=" + bill.getKey() + " requiredKg=" + bill.getValue()
                    + " availableKg=" + available + " shortageKg=" + Math.max(0, bill.getValue() - available));
        }
        var preview = c.previewPilotAction("START_FACILITY_CONSTRUCTION", id, design, 1);
        if (shortage && preview.allowed()) throw new AssertionError("Missing physical bill was admitted");
        if (!saved.equals(c.captureState())) throw new AssertionError("Construction read or preview changed actual campaign");
        System.out.println("ACTUAL STORAGE EXTENSION AUDIT PASS line=" + c.hasFacilityConstructionLine(id, design)
                + " previewAllowed=" + preview.allowed() + "; ordinary full bill/work not supplied; construction not claimed");
    }
}
