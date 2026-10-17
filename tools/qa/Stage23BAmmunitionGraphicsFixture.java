import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;

/** Exports the tested explicit weapon/stock/ownership/berth fixture; never an ordinary acquisition claim. */
public final class Stage23BAmmunitionGraphicsFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected separate output checkpoint path");
        var fixture = Class.forName("com.spacesim.campaign.GeneratedCampaignPersonalAmmunitionTest")
                .getDeclaredMethod("fixture", int.class);
        fixture.setAccessible(true);
        var campaign = (Stage228CampaignAuthority) fixture.invoke(null, 4);
        var state = campaign.captureState();
        Stage228GeneratedCampaignPersistenceCodec.write(Path.of(args[0]), state);
        var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.read(Path.of(args[0])));
        if (!state.equals(loaded.captureState())) throw new AssertionError("Ammunition fixture restore changed state");
        System.out.println("EXPLICIT AMMUNITION GRAPHICS FIXTURE PASS " + args[0]);
    }
}
