import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import java.nio.file.Path;

/** Replays a refused command only on an independently restored diagnostic owner. */
public final class Stage23BActualRejectedActionInspect {
    public static void main(String[] args) throws Exception {
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        var c = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(c.captureState())) throw new AssertionError("Non-exact rejected input");
        var before = c.captureState(); var preview = c.previewPilotAction(args[1], args[2], args[3], Integer.parseInt(args[4]));
        System.out.println("ACTUAL ACTION allowed=" + preview.allowed() + " pure=" + before.equals(c.captureState())
                + " tick=" + c.coordinator().runtime().world().getAuthoritativeWorldTick());
        var execute = Stage228CampaignAuthority.class.getDeclaredMethod("executePilotAction", String.class, String.class, String.class, int.class);
        execute.setAccessible(true);
        var isolated = Stage228CampaignAuthority.restore(saved);
        try { execute.invoke(isolated, args[1], args[2], args[3], Integer.parseInt(args[4])); isolated.captureState();
            System.out.println("ISOLATED ACTION ADMITTED"); }
        catch (java.lang.reflect.InvocationTargetException failure) { failure.getCause().printStackTrace(System.out); }
        catch (RuntimeException failure) { failure.printStackTrace(System.out); }
        if (!before.equals(c.captureState())) throw new AssertionError("Diagnostic changed original owner");
        System.out.println("ORIGINAL UNCHANGED; no live command committed");
    }
}
