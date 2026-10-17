import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import java.nio.file.Path;

/** Read-only diagnostics of the player's own movement, finances and received system knowledge. */
public final class Stage23BPersonalNavigationStatus {
    public static void main(String[] args) throws Exception {
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(Path.of(args[0]));
        var c = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(c.captureState())) throw new AssertionError("Non-exact navigation checkpoint");
        var p = c.playerState().orElseThrow(); var w = c.coordinator().runtime().world();
        var fleet = w.findFleet(p.activeFleetId()).orElseThrow();
        var jump = w.findFleetJump(fleet.id());
        System.out.println("PERSONAL NAVIGATION tick=" + w.getAuthoritativeWorldTick() + " location=" + fleet.locationKind()
                + " knownSystems=" + p.discoveredSystemIds() + " walletMilliCredits=" + p.walletMilliCredits()
                + " ownedStations=" + p.ownedStations());
        if (jump.isPresent()) {
            var movement = jump.orElseThrow();
            System.out.println("OWN PHYSICAL FLIGHT phase=" + movement.phase() + " phaseStartedTick=" + movement.phaseStartedTick()
                    + " phaseEndsTick=" + movement.phaseEndsTick() + " remainingPhaseTicks=" + (movement.phaseEndsTick() - w.getAuthoritativeWorldTick()));
        }
        if (fleet.locationKind() == com.spacesim.world.FleetLocationKind.IN_SYSTEM) {
            var entity = w.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId());
            var engineering = entity.getComponent(com.spacesim.components.EngineeringComponent.class);
            System.out.println("OWN REACTION MASS kg=" + new ProductionEngineeringRuntimeResolver().derive(engineering).reactionMassKg());
        }
        if (!saved.equals(c.captureState())) throw new AssertionError("Reading personal movement changed the campaign");
        System.out.println("PERSONAL NAVIGATION STATUS PASS; no route, stock or geometry supplied");
    }
}
