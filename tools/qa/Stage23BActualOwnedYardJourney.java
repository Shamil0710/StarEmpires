import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ui.GeneratedCampaignManufacturingUi;
import com.spacesim.ui.GeneratedCampaignRefitUi;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real acquired berth -> existing material bill -> manufactured cargo section -> same-hull refit. */
public final class Stage23BActualOwnedYardJourney {
    private static final String CARGO = "module.industrial_union_cargo_section_v1";
    private static Stage228CampaignAuthority campaign;
    private static Path output;
    private static long started;
    private static final long MAX_TICKS = Long.getLong("stage23b.yard.maxTicks", 20_000_000L);

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || MAX_TICKS <= 0) throw new IllegalArgumentException("Supply acquired checkpoint, output and station ID");
        output = Path.of(args[1]);
        var source = Files.exists(output) ? output : Path.of(args[0]);
        var saved = Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(source);
        campaign = Stage228CampaignAuthority.restore(saved);
        if (!saved.equals(campaign.captureState())) throw new AssertionError("Non-exact owned-yard input");
        String stationId = args[2];
        var p = campaign.playerState().orElseThrow();
        if (!campaign.ownsProductionStation(stationId)
                || !campaign.pilotMarketReference(stationId).orElseThrow().equals(p.dockedAt()))
            throw new AssertionError("Actual acquired station and berth required; no fixture is supplied");
        var runtime = campaign.coordinator().runtime();
        var fleetId = p.activeFleetId();
        var placement = runtime.world().findFleet(fleetId).orElseThrow();
        var engineering = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId()).getComponent(EngineeringComponent.class);
        String hull = engineering.fit.hullId();
        var cargoLots = runtime.freight().capture().cargoLots().stream().filter(l -> l.fleetId().equals(fleetId)).toList();
        var cargo = runtime.freight().cargoHoldSnapshot(fleetId).commodityMassByIdKg();
        var station = runtime.industry().industrial().station(stationId);
        campaign.coordinator().setTimeScale(1); campaign.coordinator().setPaused(true);
        started = runtime.world().getAuthoritativeWorldTick();
        try {
            if (Boolean.getBoolean("stage23b.yard.restoreMining")) {
                String miningFit = Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT;
                var existingMiningSection = campaign.moduleCustody().modules().stream().filter(m -> m.stationId().equals(stationId)
                        && Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID.equals(m.ownerActorId())
                        && m.condition().assignment().moduleId().equals(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID))
                        .findFirst().orElse(null);
                if (!runtime.freight().findFreighter(fleetId).orElseThrow().fitId().equals(miningFit)) {
                    if (campaign.refitQueue().orders().isEmpty()) {
                        var module = campaign.moduleCustody().modules().stream().filter(m -> m.stationId().equals(stationId)
                                && Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID.equals(m.ownerActorId())
                                && m.condition().assignment().moduleId().equals(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID))
                                .findFirst().orElseThrow(() -> new IllegalStateException("No actual personal removed mining section"));
                        action("START_REFIT_USED", stationId, module.custodyId());
                        checkpoint();
                    }
                    while (campaign.refitQueue().orders().stream().anyMatch(o -> o.fleetId() == fleetId.value())) tick();
                }
                if (!runtime.freight().findFreighter(fleetId).orElseThrow().fitId().equals(miningFit)
                        || !cargo.equals(runtime.freight().cargoHoldSnapshot(fleetId).commodityMassByIdKg())
                        || !cargoLots.equals(runtime.freight().capture().cargoLots().stream().filter(l -> l.fleetId().equals(fleetId)).toList())
                        || p.walletMilliCredits() != campaign.playerState().orElseThrow().walletMilliCredits()
                        || !p.ownedFleetIds().equals(campaign.playerState().orElseThrow().ownedFleetIds())
                        || !hull.equals(engineering.fit.hullId())
                        || campaign.moduleCustody().modules().stream().noneMatch(m -> m.stationId().equals(stationId)
                            && Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID.equals(m.ownerActorId())
                            && m.condition().assignment().moduleId().equals(CARGO)))
                    throw new AssertionError("Used mining restore changed existing assets or lost the actual cargo section");
                if (existingMiningSection != null) {
                    var retained = existingMiningSection.condition();
                    var actualShip = runtime.world().findSession(placement.systemId()).orElseThrow()
                            .getEntityRegistry().require(placement.localEntityId()).getComponent(EngineeringComponent.class);
                    double integrity = actualShip.instanceState.damage().moduleDamage().moduleIntegrityByMount().getOrDefault("mission_primary", 1d);
                    double age = actualShip.instanceState.maintenance().secondsSinceServiceByMount().getOrDefault("mission_primary", 0d);
                    if (Math.abs(integrity - retained.integrity()) > 1e-12 || age + 1e-6 < retained.secondsSinceService())
                        throw new AssertionError("Used mining section was repaired or its actual service age was reset");
                }
                checkpoint();
                var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
                if (!campaign.captureState().equals(restored.captureState())) throw new AssertionError("Non-exact used mining restore");
                System.out.println("ORDINARY USED MINING RESTORE PASS tick=" + runtime.world().getAuthoritativeWorldTick()
                        + "; same personal removed equipment, cargo section retained in custody, no condition or work fixtures");
                return;
            }
            if (!runtime.freight().findFreighter(fleetId).orElseThrow().fitId().equals(
                    Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT)) {
                if (campaign.refitQueue().orders().isEmpty()) {
                    if (station.storage().productCount(CARGO) == 0) {
                        if (runtime.manufacturingQueue().capture().stream().noneMatch(o -> o.stationId().equals(stationId)
                                && o.operationId().equals(CARGO))) {
                            String selection = "pilot-manufacturing|" + stationId + '|' + CARGO;
                            if (GeneratedCampaignManufacturingUi.rows(campaign).stream().noneMatch(r -> r.selection().stableId().equals(selection)))
                                throw new AssertionError("Actual cargo manufacture is absent from personal UI");
                            action("START_MANUFACTURING", stationId, CARGO);
                            checkpoint();
                        }
                        while (runtime.manufacturingQueue().capture().stream().anyMatch(o -> o.stationId().equals(stationId)
                                && o.operationId().equals(CARGO))) tick();
                        if (station.storage().productCount(CARGO) < 1) throw new AssertionError("No actual manufactured cargo section");
                        System.out.println("ACTUAL CARGO MANUFACTURED tick=" + runtime.world().getAuthoritativeWorldTick());
                        checkpoint();
                    }
                    String target = Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT;
                    if (GeneratedCampaignRefitUi.rows(campaign).stream().noneMatch(r -> r.selection().stableId()
                            .equals("pilot-refit|" + stationId + '|' + target)))
                        throw new AssertionError("Actual same-hull cargo refit is absent from personal UI");
                    action("START_REFIT", stationId, target);
                    checkpoint();
                }
                while (campaign.refitQueue().orders().stream().anyMatch(o -> o.fleetId() == fleetId.value())) tick();
            }
            if (!runtime.freight().findFreighter(fleetId).orElseThrow().fitId().equals(
                    Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT)
                    || !hull.equals(engineering.fit.hullId())
                    || !cargo.equals(runtime.freight().cargoHoldSnapshot(fleetId).commodityMassByIdKg())
                    || !cargoLots.equals(runtime.freight().capture().cargoLots().stream().filter(l -> l.fleetId().equals(fleetId)).toList())
                    || p.walletMilliCredits() != campaign.playerState().orElseThrow().walletMilliCredits()
                    || !p.ownedFleetIds().equals(campaign.playerState().orElseThrow().ownedFleetIds()))
                throw new AssertionError("Owned yard work changed hull, cargo provenance, wallet or personal roster");
            if (campaign.moduleCustody().modules().stream().noneMatch(m -> m.stationId().equals(stationId)
                    && Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID.equals(m.ownerActorId())
                    && m.condition().assignment().moduleId().equals(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID)))
                throw new AssertionError("The actual removed mining section is not in personal custody");
            checkpoint();
            var finalState = campaign.captureState();
            var loaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(output));
            if (!finalState.equals(loaded.captureState())) throw new AssertionError("Non-exact completed owned-yard checkpoint");
            System.out.println("ORDINARY OWNED YARD REFIT PASS tick=" + runtime.world().getAuthoritativeWorldTick()
                    + " elapsedTicks=" + (runtime.world().getAuthoritativeWorldTick() - started)
                    + "; no stock, equipment, geometry, money or work-progress fixtures");
        } finally { checkpoint(); }
    }

    private static void action(String verb, String station, String subject) {
        var before = campaign.captureState();
        var token = campaign.previewPilotAction(verb, station, subject, 1);
        if (!before.equals(campaign.captureState())) throw new AssertionError("Owned-yard preview changed live state");
        if (!token.allowed()) throw new IllegalStateException("Actual stock/capability does not permit " + verb + " " + subject);
        campaign.submitPilotAction(token);
        System.out.println("ACTUAL YARD ACTION " + verb + " " + subject);
    }

    private static void tick() throws Exception {
        var world = campaign.coordinator().runtime().world();
        if (world.getAuthoritativeWorldTick() - started >= MAX_TICKS)
            throw new IllegalStateException("Owned yard work remains incomplete; saved for continuation");
        campaign.coordinator().setPaused(false);
        campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        campaign.coordinator().setPaused(true);
        if (world.getAuthoritativeWorldTick() % 10_000 == 0)
            System.out.println("ACTUAL YARD PROGRESS tick=" + world.getAuthoritativeWorldTick());
        if (world.getAuthoritativeWorldTick() % 50_000 == 0) checkpoint();
    }

    private static void checkpoint() throws Exception {
        campaign.coordinator().setPaused(true);
        Stage228GeneratedCampaignPersistenceCodec.write(output, campaign.captureState());
    }
}
