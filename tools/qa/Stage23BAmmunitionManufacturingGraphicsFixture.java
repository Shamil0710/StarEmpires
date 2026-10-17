import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage23AmmunitionProduction;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;

/** Exports explicitly supplied ordnance construction/material/berth fixtures, not ordinary acquisition. */
public final class Stage23BAmmunitionManufacturingGraphicsFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected separate output checkpoint path");
        var type = Class.forName("com.spacesim.campaign.GeneratedCampaignAmmunitionManufacturingTest");
        var fixture = type.getDeclaredMethod("fixture", boolean.class); fixture.setAccessible(true);
        var campaign = (Stage228CampaignAuthority) fixture.invoke(null, true);
        var supply = type.getDeclaredMethod("supply", Stage228CampaignAuthority.class, String.class, int.class); supply.setAccessible(true);
        supply.invoke(null, campaign, Stage23AmmunitionProduction.EMPIRE, 3);
        var state = campaign.captureState();
        Stage228GeneratedCampaignPersistenceCodec.write(Path.of(args[0]), state);
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.read(Path.of(args[0])));
        if (!state.equals(loaded.captureState())) throw new AssertionError("Manufacturing fixture changed on reload");
        System.out.println("EXPLICIT AMMUNITION MANUFACTURING FIXTURE PASS " + args[0]);
    }
}
