import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.ship.*;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.*;
import com.spacesim.world.*;
import java.nio.file.*;
import java.util.List;

/** Explicit produced craft/occupancy/queue fixture; no ordinary carrier acquisition is claimed. */
public final class Stage23BQueuedCraftGraphicsFixture {
    public static void main(String[] args) throws Exception {
        var output = Path.of(args[0]); if (Files.exists(output)) throw new IllegalArgumentException("Preserve earlier fixture");
        var c = Stage228CampaignAuthority.create(1); c.submitIndependentPilotStart(c.previewIndependentPilotStart());
        c.coordinator().setPaused(true); var world = c.coordinator().runtime().world();
        var host = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var entity = world.findSession(host.systemId()).orElseThrow().getEntityRegistry().require(host.localEntityId());
        String owner = world.findFactionStableId(entity.getComponent(FactionComponent.class).factionId).orElseThrow();
        var catalog = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var design = catalog.findDemonstratorFit(Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID);
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(design); var id = c.smallCraft().reserveIdentityForCompletedProduction();
        c.smallCraft().registerProducedCraft(new SmallCraftState(id, owner, design.id(), fit,
                new ShipEngineeringRuntime(catalog).initialize(fit, ShipEngineeringState.ConsumableState.empty()), ShipInstanceRuntimeState.legacyNeutral()));
        var bay = new SmallCraftHangarCapacity.BayDefinition(new SmallCraftHangarCapacity.BayId(host.id().toString(), "explicit-queued-bay"),
                SmallCraftHangarCapacity.HostKind.SHIP, new ShipEngineeringCatalog.Dimensions3d(200, 100, 80), 2_000_000, 100_000_000, 1);
        var assign = SmallCraftHangarRegistry.class.getDeclaredMethod("assign", SmallCraftId.class,
                SmallCraftHangarCapacity.BayDefinition.class, SmallCraftHangarCapacity.OccupancyState.class);
        assign.setAccessible(true); assign.invoke(c.hangars(), id, bay, SmallCraftHangarCapacity.OccupancyState.READY);
        var deck = new SmallCraftFlightDeckOperations(c.hangars(), List.of(new SmallCraftFlightDeckOperations.DeckProfile(bay.id(), 1, 1)));
        deck.requestLaunch(id, bay.id(), 0);
        var field = Stage228CampaignAuthority.class.getDeclaredField("flightDeck"); field.setAccessible(true); field.set(c, deck);
        c.commitCarrierWings(List.of(new CarrierWingStrategicReadinessService.CarrierWingAssignment(host.id(), host.id().toString(), owner, List.of(id))));
        c.commitPersonalCraftOwnership(List.of(id));
        c.commitMissionState(SmallCraftMissionState.empty().add(new SmallCraftMissionState.MissionOrder(1, id, FleetCommandState.OrderSource.PLAYER,
                SmallCraftMissionState.MissionType.CAP, new SmallCraftMissionState.MissionTarget(SmallCraftMissionState.TargetKind.HOST, host.id().toString()),
                0, SmallCraftMissionState.MissionStatus.LAUNCH_QUEUED)));
        var saved = c.captureState(); Stage228GeneratedCampaignPersistenceCodec.write(output, saved);
        if (!saved.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output)).captureState()))
            throw new AssertionError("Non-exact explicit queued-craft fixture");
        System.out.println("EXPLICIT QUEUED CRAFT FIXTURE PASS; paid ordinary pilot opening, explicit produced craft/bay/queue, exact reload; ordinary acquisition/launch not claimed");
    }
}
