import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.persistence.*;
import com.spacesim.ship.*;
import com.spacesim.world.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Explicit produced/deployed craft fixture for graphical native validation, not ordinary acquisition evidence. */
public final class Stage23BLocalFlightGraphicsFixture {
    public static void main(String[] args) throws Exception {
        var output = Path.of(args[0]);
        if (Files.exists(output)) throw new IllegalArgumentException("Preserve previous fixture");
        var c = Stage228CampaignAuthority.create(1); c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        c.coordinator().setPaused(true);
        var w = c.coordinator().runtime().world(); var p = c.playerState().orElseThrow();
        var host = w.findFleet(p.activeFleetId()).orElseThrow();
        var owner = w.findFactionStableId(w.findSession(host.systemId()).orElseThrow().getEntityRegistry().require(host.localEntityId())
                .getComponent(FactionComponent.class).factionId).orElseThrow();
        var catalog = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var definition = catalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID);
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(definition);
        var id = c.smallCraft().reserveIdentityForCompletedProduction();
        // This fixture supplies one existing produced/deployed craft and a physical pose explicitly.
        // It does not declare a working carrier, authored launch mouth, player launch command or normal production.
        c.smallCraft().registerProducedCraft(new SmallCraftState(id, owner, definition.id(), fit,
                new ShipEngineeringRuntime(catalog).initialize(fit, ShipEngineeringState.ConsumableState.empty()), ShipInstanceRuntimeState.legacyNeutral()));
        c.commitMissionState(SmallCraftMissionState.empty().add(new SmallCraftMissionState.MissionOrder(1, id, FleetCommandState.OrderSource.PLAYER,
                SmallCraftMissionState.MissionType.CAP, new SmallCraftMissionState.MissionTarget(SmallCraftMissionState.TargetKind.AREA, "area.graphical-fixture"),
                0, SmallCraftMissionState.MissionStatus.ACTIVE)));
        c.commitCarrierWings(List.of(new CarrierWingStrategicReadinessService.CarrierWingAssignment(host.id(), host.id().toString(), owner, List.of(id))));
        c.commitPersonalCraftOwnership(List.of(id));
        var physical = c.coordinator().runtime().arrival().materialization(host.systemId()).physicalState(host.localEntityId()).orElseThrow();
        var flights = new SmallCraftLocalFlightState(0, List.of(new SmallCraftLocalFlightState.Flight(id, host.systemId(), host.id().toString(), 1,
                new LocalPhysicalKinematics(physical.position().translated(1000, 500), 123, -8), 0)));
        var s = c.captureState();
        var saved = new Stage228GeneratedCampaignPersistentState(s.schemaVersion(), s.runtimeVersion(), s.stage21Runtime(), s.smallCraft(), s.hangars(),
                s.flightDeck(), s.operations(), s.playerState(), s.playerJournal(), s.moduleCustody(), s.repairQueue(), s.refitQueue(),
                s.moduleTransfers(), s.productTransfers(), s.yardConstruction(), s.timeSkip(), s.crewManifest(), flights, s.personalCraftOwnership());
        Stage228GeneratedCampaignPersistenceCodec.write(output, saved);
        if (!saved.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
            throw new AssertionError("Non-exact explicit local-flight graphical fixture");
        System.out.println("EXPLICIT LOCAL FLIGHT GRAPHICS FIXTURE PASS file=" + output + "; one supplied produced/deployed craft and pose, exact current-native reload; ordinary acquisition/launch not claimed");
    }
}
