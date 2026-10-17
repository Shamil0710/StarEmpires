import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.world.*;
import java.util.*;

/** Physical journey probe. Guidance chooses itinerary coordinates; it never changes world geometry or assets. */
public final class Stage23BOrdinaryMiningJourney {
 private static Stage228CampaignAuthority campaign;
 private static final ProductionEngineeringRuntimeResolver ENGINEERING=new ProductionEngineeringRuntimeResolver();
 private static final java.nio.file.Path CHECKPOINT=java.nio.file.Path.of(System.getProperty("stage23b.journey.checkpoint","target/stage23b-ordinary-mining-journey.s25"));
 private static final double CRUISE_LIMIT_MPS=Double.parseDouble(System.getProperty("stage23b.journey.cruiseMps","1500"));
 private static final long MAX_TICKS=Long.getLong("stage23b.journey.maxTicks",Long.MAX_VALUE);
 private static final long MAX_FLIGHT_TICKS=Long.getLong("stage23b.journey.maxFlightTicks",20_000_000L);
 private static final long CHECKPOINT_INTERVAL=Long.getLong("stage23b.journey.checkpointInterval",100_000L);
 private static final int MINING_TARGET_KG=Integer.getInteger("stage23b.journey.miningKg",1);
 private static final int SALE_BATCH_KG=Integer.getInteger("stage23b.journey.saleBatchKg",1000);
 private static final int SALE_TARGET_KG=Integer.getInteger("stage23b.journey.saleKg",MINING_TARGET_KG);
 private static final int MAX_ANONYMOUS_APPROACHES=Integer.getInteger("stage23b.journey.maxAnonymousApproaches",8);
 private static long startedTick;
 private static boolean budgetActive;
 public static void main(String[] args) throws Exception {
  if(MAX_TICKS<=0 || MAX_FLIGHT_TICKS<=0 || CHECKPOINT_INTERVAL<=0 || MINING_TARGET_KG<=0 || SALE_BATCH_KG<=0 || SALE_TARGET_KG<=0 || MAX_ANONYMOUS_APPROACHES<=0)throw new IllegalArgumentException("Probe tick budgets and mining/sale requests must be positive");
  try { if(Boolean.getBoolean("stage23b.journey.resourceVisit")) resourceVisitJourney();
        else if(Boolean.getBoolean("stage23b.journey.unknownRadarStation")) unknownRadarStationJourney();
        else if(Boolean.getBoolean("stage23b.journey.stationRadar")) stationRadarJourney();
        else if(Boolean.getBoolean("stage23b.journey.stationPurchase")) stationPurchaseJourney();
        else if(Boolean.getBoolean("stage23b.journey.resourceMap")) resourceMapJourney();
        else if(Boolean.getBoolean("stage23b.journey.materialSupply")) materialSupplyJourney();
        else if(Boolean.getBoolean("stage23b.journey.localFuelSearch")) localFuelSearchJourney(); else journey(); }
  catch(ProbePaused paused){System.out.println("JOURNEY PAUSED tick="+world().getAuthoritativeWorldTick()+" checkpoint="+CHECKPOINT+"; requested journey is not yet proven");}
  catch(Exception|Error failure){System.err.println("JOURNEY FAILURE before final checkpoint:");failure.printStackTrace();throw failure;}
  finally {if(campaign!=null){campaign.coordinator().setPaused(true);Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,campaign.captureState());}}
 }
 private static void resourceVisitJourney() throws Exception {
  var input=java.nio.file.Files.exists(CHECKPOINT)?CHECKPOINT:java.nio.file.Path.of(System.getProperty("stage23b.journey.input"));
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
  campaign=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact resource visit input");
  var initialPlayer=campaign.playerState().orElseThrow();
  String objectId=System.getProperty("stage23b.journey.resourceId");
  var known=campaign.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
    .entries().stream().filter(k->k.object().resourceObject()&&k.object().objectId().equals(objectId)
      &&k.knownLocation().isPresent()).findFirst().orElseThrow(()->new IllegalStateException("No personal resource waypoint"));
  if(!known.object().systemId().equals(fleet().systemId()))throw new IllegalStateException("Reach the personally mapped system first");
  var resourceBefore=known.resourceKnowledge();
  var evidenceBefore=known.evidence();
  saved=null;
  campaign.coordinator().setTimeScale(1d);startedTick=world().getAuthoritativeWorldTick();budgetActive=true;
  if(initialPlayer.docked())action("UNDOCK","","",0);
  System.out.println("PERSONAL RESOURCE APPROACH target="+objectId+" evidence="+evidenceBefore);
  fly(known.knownLocation().orElseThrow(),500);
  var after=campaign.playerState().orElseThrow();
  if(after.walletMilliCredits()!=initialPlayer.walletMilliCredits()
      ||!after.ownedFleetIds().equals(initialPlayer.ownedFleetIds()))throw new AssertionError("Visit granted money or ships");
  var current=campaign.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
    .knowledge(known.object()).orElseThrow();
  if(!current.resourceKnowledge().equals(resourceBefore)||!current.evidence().equals(evidenceBefore))
    throw new AssertionError("Approach fabricated resource evidence");
  var complete=campaign.captureState();
  Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,complete);
  if(!complete.equals(Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(CHECKPOINT)).captureState()))
    throw new AssertionError("Non-exact physical resource visit reload");
  System.out.println("ORDINARY PERSONAL RESOURCE VISIT PASS target="+objectId+" distanceM="
    +physical().position().distanceTo(known.knownLocation().orElseThrow())+" tick="+world().getAuthoritativeWorldTick()
    +"; paid waypoint, physical thrust and braking, exact reload; no extraction or deposit survey claimed");
 }
 private static void unknownRadarStationJourney() throws Exception {
  var input=java.nio.file.Files.exists(CHECKPOINT)?CHECKPOINT:java.nio.file.Path.of(System.getProperty("stage23b.journey.input"));
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
  campaign=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact unknown-contact input");
  saved=null;
  var initialKnown=new HashSet<>(campaign.playerState().orElseThrow().discoveredObjects());
  var initialArchive=campaign.coordinator().runtime().discoveryState()
    .knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries().stream()
    .map(k->k.object().objectId()).collect(java.util.stream.Collectors.toSet());
  var attempted=new HashSet<Long>();
  var anchor=physical().position();
  campaign.coordinator().setTimeScale(1d);startedTick=world().getAuthoritativeWorldTick();budgetActive=true;
  if(campaign.playerState().orElseThrow().docked())action("UNDOCK","","",0);
  // Predetermined search offsets use no endpoint, source or generated target coordinates.
  double[][] offsets={{0,0},{10000,0},{0,10000},{-10000,0},{0,-10000}};
  for(var offset:offsets) {
   fly(anchor.translated(offset[0],offset[1]),200);
   action("SCAN","","",1);
   var f=fleet();
   var sensor=world().findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId())
     .getComponent(com.spacesim.components.SensorKnowledgeComponent.class);
   var candidates=sensor.tracks().stream().filter(t->t.positionKnown()&&!attempted.contains(t.targetId()))
     .filter(t->initialKnown.stream().noneMatch(k->k.systemId().equals(f.systemId())&&k.entityId().value()==t.targetId()))
     .filter(t->sensor.receivedMeasurements().stream().anyMatch(m->m.targetId()==t.targetId()&&m.hasRange()
       &&m.timestampSeconds()==t.lastMeasurementSeconds()))
     .sorted(Comparator.comparingLong(com.spacesim.ship.TrackState::targetId)).toList();
   for(var track:candidates) {
    if(attempted.size()>=MAX_ANONYMOUS_APPROACHES)throw new ProbePaused();
    attempted.add(track.targetId());
    // Only a personally received estimated position chooses this approach; identity is unknown.
    var estimate=LocalPhysicalPosition.origin().translated(track.estimatedXM(),track.estimatedYM());
    System.out.println("ANONYMOUS CONTACT APPROACH estimate="+estimate+"; measured position only");
    fly(estimate,200);
    for(int refinement=0;refinement<3;refinement++) {
     action("SCAN","","",1);
     var currentFleet=fleet();
     var currentSensor=world().findSession(currentFleet.systemId()).orElseThrow().getEntityRegistry().require(currentFleet.localEntityId())
       .getComponent(com.spacesim.components.SensorKnowledgeComponent.class);
     var updated=currentSensor.tracks().stream().filter(t->t.targetId()==track.targetId()&&t.positionKnown())
       .filter(t->currentSensor.receivedMeasurements().stream().anyMatch(m->m.targetId()==t.targetId()
         &&m.hasRange()&&m.timestampSeconds()==t.lastMeasurementSeconds())).findFirst();
     if(updated.isEmpty())break;
     var refined=LocalPhysicalPosition.origin().translated(updated.orElseThrow().estimatedXM(),updated.orElseThrow().estimatedYM());
     if(physical().position().distanceTo(refined)<=200)break;
     System.out.println("ANONYMOUS CONTACT REFINEMENT estimate="+refined+"; newly paid range/bearing only");
     fly(refined,200);
    }
    var contact=campaign.coordinator().runtime().infrastructure().endpoints().stream()
      .filter(e->campaign.canDockAtPersonalStation(e.stationId()))
      .filter(e->campaign.pilotMarketReference(e.stationId()).orElseThrow().entityId().value()==track.targetId())
      .filter(e->!initialArchive.contains(e.stationId()))
      .filter(e->!initialKnown.contains(campaign.pilotMarketReference(e.stationId()).orElseThrow()))
      .sorted(Comparator.comparing(e->e.stationId())).findFirst();
    if(contact.isEmpty())continue;
    var station=contact.orElseThrow();
    action("DOCK",station.stationId(),"",0);
    var complete=campaign.captureState();
    var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
      Stage228GeneratedCampaignPersistenceCodec.encode(complete)));
    if(!complete.equals(restored.captureState()))throw new AssertionError("Non-exact measured approach checkpoint");
    System.out.println("UNKNOWN STATION RADAR APPROACH PASS station="+station.stationId()+" tick="+world().getAuthoritativeWorldTick()
      +"; paid pulse, measured guidance, physical contact and dock, exact reload; no hidden target coordinates");
    return;
   }
  }
  throw new ProbePaused();
 }
 private static void stationRadarJourney() throws Exception {
  var input=java.nio.file.Files.exists(CHECKPOINT)?CHECKPOINT:java.nio.file.Path.of(System.getProperty("stage23b.journey.input"));
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
  campaign=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact radar journey input");
  String station=System.getProperty("stage23b.journey.radarStation","hub.14");
  var coordinate=personalCoordinate(station).orElseThrow(()->new IllegalStateException("Physical test approach needs existing personal station coordinates"));
  var reference=campaign.pilotMarketReference(station).orElseThrow();
  if(!reference.systemId().equals(fleet().systemId()))throw new IllegalStateException("Radar journey must start in the actual station system");
  campaign.coordinator().setTimeScale(1d);startedTick=world().getAuthoritativeWorldTick();budgetActive=true;
  if(campaign.playerState().orElseThrow().docked())action("UNDOCK","","",0);
  fly(coordinate.translated(5000,0),200);
  boolean thrust=Boolean.getBoolean("stage23b.journey.radarThrust");
  var expectedPhysical=physical();
  var fitted=world().findSession(fleet().systemId()).orElseThrow().getEntityRegistry().require(fleet().localEntityId())
   .getComponent(com.spacesim.components.EngineeringComponent.class);
  var beforeOperating=fitted.runtimeState;
  com.spacesim.components.EngineeringComponent referenceEngineering=null;
  if(thrust) {
   campaign.setPilotThrust(.5f,0,false);
   referenceEngineering=new com.spacesim.components.EngineeringComponent(fitted.fit,fitted.runtimeState,fitted.instanceState);
   expectedPhysical=new com.spacesim.systems.PlayerDirectControlSystem().advanceExact(
    new com.badlogic.ashley.core.Entity().add(referenceEngineering),expectedPhysical,.5f,0,false,campaign.coordinator().session().fixedStepSeconds());
  }
  long pulseTick=world().getAuthoritativeWorldTick();
  var before=campaign.captureState();
  var pulse=campaign.previewPilotAction("SCAN","","",1);
  if(!pulse.allowed() || !before.equals(campaign.captureState()))throw new AssertionError("Ordinary fitted radar preview failed or mutated state");
  campaign.submitPilotAction(pulse);
  if(thrust && (!expectedPhysical.equals(physical())
    ||!referenceEngineering.runtimeState.consumables().equals(fitted.runtimeState.consumables())
    ||fitted.runtimeState.sharedBusEnergyJ()>referenceEngineering.runtimeState.sharedBusEnergyJ()
    ||new ProductionEngineeringRuntimeResolver().derive(fitted).reactionMassKg()>=new ProductionEngineeringRuntimeResolver().derive(
     new com.spacesim.components.EngineeringComponent(fitted.fit,beforeOperating,fitted.instanceState)).reactionMassKg()))
   throw new AssertionError("Radar with thrust duplicated movement/fuel or electrical energy");
  campaign.setPilotThrust(0,0,false);
  var sensor=world().findSession(fleet().systemId()).orElseThrow().getEntityRegistry().require(fleet().localEntityId())
   .getComponent(com.spacesim.components.SensorKnowledgeComponent.class);
  var measurement=sensor.receivedMeasurements().stream().filter(m->m.targetId()==reference.entityId().value()&&m.hasRange())
   .max(java.util.Comparator.comparingDouble(com.spacesim.ship.SensorMeasurement::timestampSeconds)).orElseThrow();
  if(measurement.rangeM()<=1000 || world().getAuthoritativeWorldTick()!=pulseTick+1)
   throw new AssertionError("Radar must measure beyond docking range on one real tick");
  var complete=campaign.captureState();
  var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(complete)));
  if(!complete.equals(restored.captureState()))throw new AssertionError("Non-exact ordinary radar round trip");
  System.out.println("ORDINARY STATION RADAR PASS station="+station+" measuredRangeM="+measurement.rangeM()+" tick="+world().getAuthoritativeWorldTick()+" thrust="+thrust+"; existing fit and coordinates, physical flight, pulse, exact reload; no grants");
 }
 private static void localFuelSearchJourney() throws Exception {
  var input=java.nio.file.Files.exists(CHECKPOINT)?CHECKPOINT:java.nio.file.Path.of(System.getProperty("stage23b.journey.input"));
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
  campaign=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact local search checkpoint");
  saved=null;
  campaign.coordinator().setTimeScale(1d);startedTick=world().getAuthoritativeWorldTick();budgetActive=true;
  if(campaign.playerState().orElseThrow().docked())action("UNDOCK","","",0);
  // A predetermined coordinate-system origin is a search waypoint, not an unpaid station location.
  // No endpoint/source/catalog coordinates are read to choose or adjust this waypoint.
  System.out.println("LOCAL SEARCH WAYPOINT origin=(0,0); no hidden station navigation granted");
  fly(new LocalPhysicalPosition(0,0,0,0),500);
  var contact=campaign.coordinator().runtime().infrastructure().endpoints().stream()
    .filter(e->e.systemId().equals(fleet().systemId())&&campaign.canDockAtPersonalStation(e.stationId()))
    .sorted(Comparator.comparing(e->e.stationId())).findFirst().orElseThrow(()->new IllegalStateException("No actual immediate dock contact at search waypoint"));
  action("DOCK",contact.stationId(),"",0);
  System.out.println("ACTUAL LOCAL DOCK SEARCH PASS station="+contact.stationId()+" waterStockKg="+contact.storage().commodityMassKg("commodity.material.purified_water")
    +" tick="+world().getAuthoritativeWorldTick()+"; physical visit only, refueling not claimed");
 }
 private static void materialSupplyJourney() throws Exception {
  var input=java.nio.file.Files.exists(CHECKPOINT)?CHECKPOINT:java.nio.file.Path.of(System.getProperty("stage23b.journey.input"));
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
  campaign=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact actual supply checkpoint");
  saved=null;
  campaign.coordinator().setTimeScale(1d);startedTick=world().getAuthoritativeWorldTick();budgetActive=true;
  String commodity=System.getProperty("stage23b.journey.commodity","commodity.feedstock.conductor_ore");
  String station=System.getProperty("stage23b.journey.stationId","industry.2");
  if(!campaign.ownsProductionStation(station))throw new IllegalStateException("Actual owned receiving station required");
  double goal=Double.parseDouble(System.getProperty("stage23b.journey.stockGoalKg"));
  double initialStock=campaign.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(commodity);
  double missing=goal-initialStock;
  if(!Double.isFinite(goal)||missing < -1e-8||Math.abs(missing-Math.rint(missing))>1e-8||missing>MINING_TARGET_KG)throw new IllegalStateException("Supply goal must be a fixed whole-kg increment within the requested bound");
  int requested=(int)Math.rint(missing);
  if(requested==0){System.out.println("ACTUAL MATERIAL SUPPLY ALREADY COMPLETE stockKg="+initialStock);return;}
  System.out.println("ACTUAL SUPPLY FUEL cargoWaterKg="+cargoMass("commodity.material.purified_water")
    +" ownWaterStockKg="+campaign.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg("commodity.material.purified_water"));
  for(var row:com.spacesim.ui.GeneratedCampaignSupplyUi.rows(campaign))System.out.println("ACTUAL CONSUMABLE INTERFACE "+row.selection().stableId()+" "+row.sections());
  var known=campaign.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
    .entries().stream().filter(k->k.object().resourceObject()&&k.knownLocation().isPresent()
      &&k.classificationId().filter(commodity::equals).isPresent()).findFirst().orElseThrow();
  long wallet=campaign.playerState().orElseThrow().walletMilliCredits();
  var active=campaign.coordinator().runtime().freight().capture().personalMiningOrders().stream().filter(o->o.fleetId().equals(fleetId())).findFirst();
  double cargo=cargoMass(commodity);
  if(cargo<requested || active.isPresent()) {
   if(campaign.playerState().orElseThrow().docked())action("UNDOCK","","",0);
   travel(known.object().systemId());fly(known.knownLocation().orElseThrow(),500);
   if(active.isEmpty())action("START_MINING",known.object().objectId(),"",(int)Math.ceil(requested-cargo));
   while(campaign.coordinator().runtime().freight().capture().personalMiningOrders().stream().anyMatch(o->o.fleetId().equals(fleetId())))tick();
   if(cargoMass(commodity)<requested-1e-8)throw new AssertionError("Actual source failed requested material extraction");
  }
  var berth=campaign.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
    .entries().stream().filter(k->k.object().objectId().equals(station)&&k.knownLocation().isPresent()).findFirst().orElseThrow();
  travel(berth.object().systemId());fly(berth.knownLocation().orElseThrow(),500);
  if(!campaign.playerState().orElseThrow().docked())action("DOCK",station,"",0);
  double before=cargoMass(commodity);
  double stock=campaign.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(commodity);
  // Respect the shared finite dock-handling limit; do not bypass the command boundary.
  int left=requested;
  while(left>0){
   int batch=Math.min(left,1000);
   while(batch>0&&!campaign.previewPilotAction("UNLOAD_COMMODITY",station,commodity,batch).allowed())batch/=2;
   if(batch==0){tick();continue;}
   action("UNLOAD_COMMODITY",station,commodity,batch);left-=batch;if(left>0)tick();
  }
  if(Math.abs(cargoMass(commodity)-(before-requested))>1e-8
      ||Math.abs(campaign.coordinator().runtime().infrastructure().endpoint(station).storage().commodityMassKg(commodity)-(stock+requested))>1e-8
      ||campaign.playerState().orElseThrow().walletMilliCredits()!=wallet)throw new AssertionError("Non-conserved own material delivery");
  System.out.println("ACTUAL MATERIAL SUPPLY PASS commodity="+commodity+" deliveredThisContinuationKg="+requested+" stockGoalKg="+goal+" tick="+world().getAuthoritativeWorldTick());
 }
 private static double cargoMass(String commodity){return campaign.coordinator().runtime().freight().findFreighter(fleetId()).orElseThrow().cargoStorage().commodityMassByIdKg().getOrDefault(commodity,0d);}
 private static void stationPurchaseJourney() throws Exception {
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(CHECKPOINT);
  campaign=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact station journey checkpoint");
  campaign.coordinator().setTimeScale(1d);startedTick=world().getAuthoritativeWorldTick();budgetActive=true;
  String station=System.getProperty("stage23b.journey.stationId","industry.2");
  if(campaign.ownsProductionStation(station)){System.out.println("STATION ALREADY PERSONALLY OWNED "+station);return;}
  long price=campaign.stationSalePriceMilliCredits(station);
  if(price<=0 || campaign.playerState().orElseThrow().walletMilliCredits()<price)throw new IllegalStateException("No affordable personally known existing station offer");
  var known=campaign.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
      .entries().stream().filter(k->k.object().objectId().equals(station)&&k.knownLocation().isPresent()).findFirst()
      .orElseThrow(()->new IllegalStateException("No personal station navigation archive"));
  if(campaign.playerState().orElseThrow().docked()
      &&!campaign.pilotMarketReference(station).orElseThrow().equals(campaign.playerState().orElseThrow().dockedAt()))action("UNDOCK","","",0);
  travel(known.object().systemId());fly(known.knownLocation().orElseThrow(),500);
  if(!campaign.playerState().orElseThrow().docked())action("DOCK",station,"",0);
  var runtime=campaign.coordinator().runtime();var industrial=runtime.captureState().campaign().industrialState();
  String seller=runtime.industry().industrial().station(station).stableFactionId();
  long wallet=campaign.playerState().orElseThrow().walletMilliCredits();
  long treasury=world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits();
  int fleetCount=world().getFleetPlacements().size();
  var owned=campaign.playerState().orElseThrow().ownedFleetIds();
  long delta=action("PURCHASE_STATION",station,"",0);
  if(delta!=-price || campaign.playerState().orElseThrow().walletMilliCredits()!=wallet-price
      ||world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits()!=treasury+price
      ||!campaign.ownsProductionStation(station)||!owned.equals(campaign.playerState().orElseThrow().ownedFleetIds())
      ||world().getFleetPlacements().size()!=fleetCount||!industrial.equals(runtime.captureState().campaign().industrialState()))
      throw new AssertionError("Non-conserved ordinary station acquisition or changed physical industry");
  var acquired=campaign.captureState();
  var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(acquired)));
  if(!acquired.equals(restored.captureState()))throw new AssertionError("Non-exact purchased station checkpoint");
  Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT.resolveSibling(CHECKPOINT.getFileName()+".purchased.s25"),acquired);
  System.out.println("ORDINARY STATION PURCHASE PASS station="+station+" priceMilliCredits="+price
      +" walletMilliCredits="+campaign.playerState().orElseThrow().walletMilliCredits()+" tick="+world().getAuthoritativeWorldTick());
 }
 private static void resourceMapJourney() throws Exception {
  var saved=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(CHECKPOINT);
  campaign=Stage228CampaignAuthority.restore(saved);
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact resource-map journey checkpoint");
  campaign.coordinator().setTimeScale(1d);
  startedTick=world().getAuthoritativeWorldTick();budgetActive=true;
  var runtime=campaign.coordinator().runtime();
  var personal=runtime.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
  var npc=campaign.coordinator().npcMissions().npcs().stream()
   .filter(n->n.npcId().startsWith("npc.stage23b.dispatcher:"))
   .filter(n->runtime.discoveryState().knowledgeFor(n.factionContentId()).entries().stream()
    .anyMatch(e->e.object().resourceObject()&&e.knownLocation().isPresent()&&personal.knowledge(e.object()).filter(k->k.knownLocation().isPresent()).isEmpty()))
   .findFirst().orElseThrow();
  var posting=npc.knowledge().stream().filter(k->k.factId().equals(npc.npcId()+":posting")).findFirst().orElseThrow();
  var known=personal.entries().stream().filter(k->k.object().objectId().equals(posting.subjectId())&&k.knownLocation().isPresent()).findFirst()
   .orElseThrow(()->new IllegalStateException("No personal navigation knowledge for actual map seller; truth coordinates are forbidden"));
  if(campaign.playerState().orElseThrow().docked()&&!campaign.canContactNpc(npc.npcId()))action("UNDOCK","","",0);
  travel(known.object().systemId());
  fly(known.knownLocation().orElseThrow(),500);
  if(!campaign.playerState().orElseThrow().docked())action("DOCK",posting.subjectId(),"",0);
  if(!campaign.canContactNpc(npc.npcId()))throw new AssertionError("Physical arrival did not establish actual seller contact");
  Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,campaign.captureState());
  var purchased=CHECKPOINT.resolveSibling(CHECKPOINT.getFileName()+".map.s25");
  Stage23BOrdinaryResourceMapJourney.main(new String[]{CHECKPOINT.toString(),purchased.toString()});
  campaign=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(purchased));
 }
 private static void journey() throws Exception {
  if(java.nio.file.Files.exists(CHECKPOINT) || System.getProperty("stage23b.journey.input")!=null) {
   var input=java.nio.file.Files.exists(CHECKPOINT)?CHECKPOINT:java.nio.file.Path.of(System.getProperty("stage23b.journey.input"));
   var loaded=Stage228GeneratedCampaignPersistenceCodec.readOrMigrate(input);
   campaign=Stage228CampaignAuthority.restore(loaded);
   if(!loaded.equals(campaign.captureState()))throw new AssertionError("Non-exact resumed journey checkpoint");
   System.out.println("JOURNEY RESUMED tick="+world().getAuthoritativeWorldTick()+" jump="+world().findFleetJump(fleetId()));
  }
  else {
   campaign=Stage228CampaignAuthority.create(Long.getLong("stage23b.journey.seed",1L)); campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
   String startingDock="hub."+fleet().systemId().value();
   action("DOCK",startingDock,"",0); tick(); action("BUY",startingDock,"commodity.material.purified_water",10);
   action("UNDOCK","","",0);
  }
  campaign.coordinator().setTimeScale(1d);
  startedTick=world().getAuthoritativeWorldTick();
  budgetActive=true;
  var miner=campaign.coordinator().runtime().freight().capture().freighters().stream()
   .filter(f->MINING_TARGET_KG==1 || campaign.playerState().orElseThrow().ownedFleetIds().contains(f.fleetId()))
   .filter(f->f.fitId().equals(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT)).findFirst().orElseThrow();
  String miningDock=System.getProperty("stage23b.journey.saleStation","hub."+miner.currentSystemId().value());
  var original=miner;
  if(!campaign.playerState().orElseThrow().ownedFleetIds().contains(miner.fleetId())) {
   travel(miner.currentSystemId());
   System.out.println("SELLER ARRIVAL "+physical()+" wallet="+campaign.playerState().orElseThrow().walletMilliCredits());
   fly(campaign.coordinator().runtime().infrastructure().endpoint(miningDock).position(),500);
   if(!campaign.playerState().orElseThrow().docked())action("DOCK",miningDock,"",0);
   long walletBefore=campaign.playerState().orElseThrow().walletMilliCredits();
   long treasuryBefore=world().findFactionEconomicState(miner.stableFactionId()).orElseThrow().treasuryMilliCredits();
   long hullsBefore=world().getFleetPlacements().size();
   long paid=action("PURCHASE",Long.toString(miner.fleetId().value()),"",0);
   if(paid>=0 || campaign.playerState().orElseThrow().walletMilliCredits()!=walletBefore+paid
       || world().findFactionEconomicState(miner.stableFactionId()).orElseThrow().treasuryMilliCredits()!=treasuryBefore-paid
       || world().getFleetPlacements().size()!=hullsBefore)throw new AssertionError("Non-conserved existing miner purchase");
  }
  if(!original.equals(campaign.coordinator().runtime().freight().findFreighter(miner.fleetId()).orElseThrow())) throw new AssertionError("Purchase changed physical mining asset");
  if(!fleetId().equals(miner.fleetId())) {
   if(campaign.playerState().orElseThrow().docked())action("UNDOCK","","",0);
   action("SWITCH",Long.toString(miner.fleetId().value()),"",0);
  }
  System.out.println("MINER PURCHASED "+miner.fleetId()+" wallet="+campaign.playerState().orElseThrow().walletMilliCredits());
  var activeMining=campaign.coordinator().runtime().freight().capture().personalMiningOrders().stream().filter(o->o.fleetId().equals(fleetId())).findFirst();
  boolean sellOnly=Boolean.getBoolean("stage23b.journey.sellOnly");
  if(sellOnly && System.getProperty("stage23b.journey.saleStation")==null && (!campaign.playerState().orElseThrow().docked()
      || !campaign.pilotMarketReference(miningDock).orElseThrow().equals(campaign.playerState().orElseThrow().dockedAt())))
      throw new IllegalStateException("Sale-only continuation requires the actual saved market berth");
  if(sellOnly && System.getProperty("stage23b.journey.saleStation")!=null) {
   personalCoordinate(miningDock).orElseThrow(()->new IllegalStateException("Sale destination needs existing personal navigation"));
   if(!campaign.pilotMarketReference(miningDock).orElseThrow().equals(campaign.playerState().orElseThrow().dockedAt())) {
    if(campaign.playerState().orElseThrow().docked())action("UNDOCK","","",0);
    travel(campaign.coordinator().runtime().infrastructure().endpoint(miningDock).systemId());
   }
  }
  boolean hasOre=hasMinedOre();
  if(!sellOnly && (!hasOre || activeMining.isPresent())) {
   if(campaign.playerState().orElseThrow().docked())action("UNDOCK","","",0);
  var source=campaign.coordinator().runtime().industry().sourceOutposts().sources().sources().stream()
   .filter(s->s.systemId().equals(fleet().systemId()) && s.sourceState().environment()==com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment.FREE_BODY)
   .filter(s->activeMining.isEmpty() || activeMining.orElseThrow().sourceId().equals(s.sourceId()))
   .filter(s->MINING_TARGET_KG==1 || personalCoordinate(s.sourceId()).isPresent())
   .min(Comparator.comparingDouble(s->physical().position().distanceTo(MINING_TARGET_KG==1 ? s.position()
        : personalCoordinate(s.sourceId()).orElseThrow()))).orElseThrow();
  fly(MINING_TARGET_KG==1 ? source.position() : personalCoordinate(source.sourceId()).orElseThrow(),500);
  if(com.spacesim.ui.GeneratedCampaignMiningUi.rows(campaign).stream().noneMatch(row->row.selection().stableId().equals("pilot-mining|"+source.sourceId())))
   throw new AssertionError("Actual source contact is not reachable through production mining UI");
  double sourceBefore=source.sourceState().remainingAccessibleMassKg();
  if(activeMining.isEmpty())action("START_MINING",source.sourceId(),"",MINING_TARGET_KG);
  for(int i=0;i<Math.max(100,MINING_TARGET_KG*100L) && !hasMinedOre();i++)tick();
  if(campaign.coordinator().runtime().freight().capture().personalMiningOrders().stream().anyMatch(o->o.fleetId().equals(fleetId())))action("STOP_MINING","","",0);
  if(!hasMinedOre() || (!hasOre && source.sourceState().remainingAccessibleMassKg()>=sourceBefore))throw new AssertionError("No physical finite-source mining result");
  }
  System.out.println("MINED cargo="+campaign.coordinator().runtime().freight().findFreighter(fleet().id()).orElseThrow().cargoStorage().commodityMassByIdKg());
  var saved=campaign.captureState();
  campaign=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
  if(!saved.equals(campaign.captureState()))throw new AssertionError("Non-exact ordinary save/load");
  System.out.println("JOURNEY CHECKPOINT PASS tick="+world().getAuthoritativeWorldTick());
  Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,saved);
  // The restored authority owns its state. Do not retain a second full world snapshot
  // throughout a multi-million-tick flight and every subsequent checkpoint encoding.
  saved=null;
  fly(MINING_TARGET_KG==1 ? campaign.coordinator().runtime().infrastructure().endpoint(miningDock).position()
      : personalCoordinate(miningDock).orElseThrow(),500);
  if(!campaign.playerState().orElseThrow().docked())action("DOCK",miningDock,"",0);tick();
  var cargo=campaign.coordinator().runtime().freight().findFreighter(fleetId()).orElseThrow().cargoStorage();
  var ore=cargo.commodityMassByIdKg().entrySet().stream().filter(e->e.getKey().startsWith("commodity.feedstock.") && e.getValue()>=1).findFirst().orElseThrow();
  int quantity=(int)Math.min(SALE_TARGET_KG,Math.floor(ore.getValue()));
  double beforeCargo=cargo.commodityMassByIdKg().getOrDefault(ore.getKey(),0d);
  double beforeStock=campaign.coordinator().runtime().infrastructure().endpoint(miningDock).storage().commodityMassKg(ore.getKey());
  long beforeMoney=campaign.playerState().orElseThrow().walletMilliCredits();
  long income=0;
  for(int left=quantity;left>0;){
   var endpoint=campaign.coordinator().runtime().infrastructure().endpoint(miningDock);
   var ref=campaign.pilotMarketReference(miningDock).orElseThrow();
   long liquid=world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
       .getComponent(com.spacesim.components.WalletComponent.class).getBalanceMilliCredits();
   long quote=campaign.pilotCommodityPrice(miningDock,ore.getKey(),false);
   long physicalLimit=(long)Math.floor(endpoint.handlingCapability().massRateKgPerSecond()*campaign.coordinator().session().fixedStepSeconds());
   int batch=(int)Math.min(left,Math.min(SALE_BATCH_KG,Math.min(physicalLimit,liquid/quote)));
   if(batch<=0)throw new IllegalStateException("No actual sale budget: handlingKg="+physicalLimit+" stationMilliCredits="+liquid+" quote="+quote);
   var sell=campaign.previewPilotAction("SELL",miningDock,ore.getKey(),batch);
   if(!sell.allowed())throw new AssertionError("Ordinary mined cargo cannot be sold at its actual market");
   campaign.submitPilotAction(sell);income=Math.addExact(income,sell.walletChangeMilliCredits());left-=batch;
   if(left>0)tick();
  }
  double remainingCargo=campaign.coordinator().runtime().freight().findFreighter(fleetId()).orElseThrow().cargoStorage().commodityMassByIdKg().getOrDefault(ore.getKey(),0d);
  if(Math.abs(remainingCargo-(beforeCargo-quantity))>1e-8
      ||Math.abs(campaign.coordinator().runtime().infrastructure().endpoint(miningDock).storage().commodityMassKg(ore.getKey())-(beforeStock+quantity))>1e-8
      ||campaign.playerState().orElseThrow().walletMilliCredits()!=beforeMoney+income
      ||income<=0)throw new AssertionError("Non-conserved ordinary ore sale");
  var completed=campaign.captureState();
  var restored=Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(completed)));
  if(!completed.equals(restored.captureState()))throw new AssertionError("Non-exact sale checkpoint");
  Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT.resolveSibling(CHECKPOINT.getFileName()+".sale.s25"),completed);
  System.out.println("ORDINARY MINING SALE PASS quantity="+quantity+" commodity="+ore.getKey()+" incomeMilliCredits="+income+" tick="+world().getAuthoritativeWorldTick());
 }
 private static WorldSimulation world(){return campaign.coordinator().runtime().world();}
 private static FleetPlacementState fleet(){return world().findFleet(campaign.playerState().orElseThrow().activeFleetId()).orElseThrow();}
 private static LocalPhysicalKinematics physical(){var f=fleet();return campaign.coordinator().runtime().arrival().materialization(f.systemId()).physicalState(f.localEntityId()).orElseThrow();}
 private static boolean hasMinedOre(){return campaign.coordinator().runtime().freight().findFreighter(fleetId()).orElseThrow().cargoStorage().commodityMassByIdKg().entrySet().stream()
   .anyMatch(e->e.getKey().startsWith("commodity.feedstock.") && e.getValue()>=MINING_TARGET_KG
      && campaign.coordinator().runtime().freight().cargoLots().stream()
         .filter(l->l.fleetId().equals(fleetId())&&l.commodityId().equals(e.getKey())
           &&(l.orderId().equals(com.spacesim.persistence.Stage20FreightPersistentState.personalExtractionOrderId(fleetId()))
             ||l.orderId().equals(com.spacesim.persistence.Stage20FreightPersistentState.manualCargoOrderId(fleetId()))))
         .mapToDouble(com.spacesim.persistence.CargoLotFifo::availableMassKg).sum()>=MINING_TARGET_KG);}
 private static Optional<LocalPhysicalPosition> personalCoordinate(String id){
  return campaign.coordinator().runtime().discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
    .entries().stream().filter(k->k.object().objectId().equals(id)).flatMap(k->k.knownLocation().stream()).findFirst();
 }
 private static long action(String verb,String target,String subject,int amount){
  var p=campaign.previewPilotAction(verb,target,subject,amount);
  if(!p.allowed()){
   // Diagnostic only: reproduce rejection on an isolated restored copy, never bypass live confirmation.
   try {
    var isolated=Stage228CampaignAuthority.restore(campaign.captureState());
    var execute=Stage228CampaignAuthority.class.getDeclaredMethod("executePilotAction",String.class,String.class,String.class,int.class);
    execute.setAccessible(true);execute.invoke(isolated,verb,target,subject,amount);isolated.captureState();
   }catch(java.lang.reflect.InvocationTargetException e){throw new IllegalStateException("Rejected "+verb+" target="+target,e.getCause());}
   catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot diagnose rejected action",e);}
   throw new IllegalStateException("Rejected "+verb+" target="+target+" at tick="+world().getAuthoritativeWorldTick());
  }
  campaign.submitPilotAction(p); System.out.println("ACTION "+verb+" "+target+" at tick="+world().getAuthoritativeWorldTick());
  return p.walletChangeMilliCredits();
 }
 private static void tick(){
  if(Boolean.getBoolean("stage23b.qa.cooperativeStop"))throw new ProbePaused();
  if(budgetActive && world().getAuthoritativeWorldTick()-startedTick>=MAX_TICKS)throw new ProbePaused();
  campaign.coordinator().setPaused(false);campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());campaign.coordinator().setPaused(true);
  if(world().getAuthoritativeWorldTick()%10_000==0)System.out.println("JOURNEY PROGRESS tick="+world().getAuthoritativeWorldTick()+" jump="+world().findFleetJump(fleetId()));
  if(world().getAuthoritativeWorldTick()%CHECKPOINT_INTERVAL==0)try{Stage228GeneratedCampaignPersistenceCodec.write(CHECKPOINT,campaign.captureState());}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
 }
 private static final class ProbePaused extends RuntimeException { }
 private static void travel(StarSystemId destination){
  int resumedTicks=0;
  while(world().findFleetJump(fleetId()).isPresent()&&resumedTicks++<50_000_000)tick();
  if(world().findFleetJump(fleetId()).isPresent())throw new IllegalStateException("Resumed jump exceeded finite probe budget");
  var paths=new HashMap<StarSystemId,List<StarSystemId>>();var pending=new ArrayDeque<StarSystemId>();
  paths.put(fleet().systemId(),List.of(fleet().systemId()));pending.add(fleet().systemId());
  while(!pending.isEmpty()&&!paths.containsKey(destination)){var current=pending.remove();for(var next:world().getTopology().neighbors(current))if(!paths.containsKey(next)){
   var path=new ArrayList<>(paths.get(current));path.add(next);paths.put(next,path);pending.add(next);}}
  boolean personalRoute=Boolean.getBoolean("stage23b.journey.materialSupply")
    || Boolean.getBoolean("stage23b.journey.stationPurchase") || System.getProperty("stage23b.journey.saleStation")!=null;
  var selected=personalRoute
    ? new com.spacesim.player.PlayerFleetRoutePlanner(world(),campaign.coordinator().content(),campaign.playerState().orElseThrow())
       .plan(fleetId(),fleet().systemId(),destination).orElseThrow(()->new IllegalStateException("No personally known feasible material route")).path()
    : paths.get(destination);
  if(personalRoute&&!campaign.playerState().orElseThrow().discoveredSystemIds().containsAll(selected))throw new AssertionError("Unowned navigation route");
  for(var hop:selected.subList(1,selected.size())){
   for(int wait=0;!world().previewFittedFleetJump(fleetId()).orElseThrow().allowed()&&wait<10_000;wait++)tick();
   if(world().findFleetJump(fleetId()).isEmpty()){
    if(Boolean.getBoolean("stage23b.journey.materialSupply"))System.out.println("ACTUAL DIRECT FUEL PLAN "+world().planFleetRouteFuel(fleetId(),List.of(fleet().systemId(),hop)));
    action("JUMP",Long.toString(hop.value()),"",0);
   }
   int ticks=0;while(world().findFleetJump(fleetId()).isPresent()&&ticks++<50_000_000){tick();if(ticks%10000==0)System.out.println("JUMP PROGRESS "+hop+" ticks="+ticks+" world="+world().getAuthoritativeWorldTick()+" state="+world().findFleetJump(fleetId()));}
   if(world().findFleetJump(fleetId()).isPresent()||!fleet().systemId().equals(hop))throw new IllegalStateException("Travel stalled "+hop+" world="+world().getAuthoritativeWorldTick()+" state="+world().findFleetJump(fleetId()));
   System.out.println("ARRIVAL "+hop+" tick="+world().getAuthoritativeWorldTick());
  }
 }
 private static FleetId fleetId(){return campaign.playerState().orElseThrow().activeFleetId();}
 private static void fly(LocalPhysicalPosition target,double radius){
  if(!Double.isFinite(CRUISE_LIMIT_MPS)||CRUISE_LIMIT_MPS<=0)throw new IllegalArgumentException("Cruise limit must be positive and finite");
  var initial=physical();var placement=fleet();
  if(initial.position().distanceTo(target)<=radius && Math.hypot(initial.velocityXMps(),initial.velocityYMps())<=.01){campaign.setPilotThrust(0,0,false);return;}
  var fitted=world().findSession(placement.systemId()).orElseThrow().getEntityRegistry().require(placement.localEntityId()).getComponent(EngineeringComponent.class);
  double cruise=CRUISE_LIMIT_MPS;
  var initialDisplacement=initial.position().displacementTo(target);
  double initialDistance=Math.hypot(initialDisplacement.deltaXM(),initialDisplacement.deltaYM());
  double ux=initialDistance>0?initialDisplacement.deltaXM()/initialDistance:0;
  double uy=initialDistance>0?initialDisplacement.deltaYM()/initialDistance:0;
  var plan=ENGINEERING.planDeltaV(fitted,Math.hypot(ux*cruise-initial.velocityXMps(),uy*cruise-initial.velocityYMps())+cruise);
  while(!plan.feasible()){
   cruise*=.5;
   if(cruise<.1)throw new IllegalStateException("No safe finite flight/braking plan from checkpoint; preserve it and use a fresh journey checkpoint with a lower cruise limit");
   plan=ENGINEERING.planDeltaV(fitted,Math.hypot(ux*cruise-initial.velocityXMps(),uy*cruise-initial.velocityYMps())+cruise);
  }
  double brakingAcceleration=.25*plan.deliveredDeltaVMps()/plan.burnSeconds();
  if(!Double.isFinite(brakingAcceleration) || brakingAcceleration<=0)throw new IllegalStateException("No finite sustainable braking capability");
  System.out.println("FLIGHT PLAN distance="+initialDistance+" cruise="+cruise+" deltaV="+plan.requiredDeltaVMps()+" burnSeconds="+plan.burnSeconds()+" conservativeBrakingAcceleration="+brakingAcceleration);
  for(long i=0;i<MAX_FLIGHT_TICKS;i++){
   var state=physical();var d=state.position().displacementTo(target);double distance=Math.hypot(d.deltaXM(),d.deltaYM());
   double speed=Math.hypot(state.velocityXMps(),state.velocityYMps());
   if(distance<=radius && speed<=0.01){campaign.setPilotThrust(0,0,false);System.out.println("FLIGHT ARRIVAL distance="+distance+" ticks="+i);return;}
   var f=fleet();var component=world().findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId()).getComponent(EngineeringComponent.class);
   var engineering=ENGINEERING.derive(component);double acceleration=Math.min(engineering.accelerationMps2(),brakingAcceleration);
   double approach=Math.min(cruise,Math.sqrt(acceleration*Math.max(0,distance-radius*0.5)));
   double dx=(distance>0?d.deltaXM()/distance*approach:0)-state.velocityXMps();
   double dy=(distance>0?d.deltaYM()/distance*approach:0)-state.velocityYMps();
   double dv=Math.hypot(dx,dy);double throttle=ENGINEERING.throttleForDeltaV(component,dv,campaign.coordinator().session().fixedStepSeconds());
   campaign.setPilotThrust((float)(dv>0?dx/dv*throttle:0),(float)(dv>0?dy/dv*throttle:0),false);tick();
   if(i%10000==0)System.out.println("FLIGHT PROGRESS distance="+distance+" speed="+speed+" tick="+world().getAuthoritativeWorldTick());
   if(acceleration<=0 || engineering.reactionMassKg()<=0)throw new IllegalStateException("No finite flight capability");
  }
  System.out.println("FLIGHT SEGMENT BUDGET EXHAUSTED ticks="+MAX_FLIGHT_TICKS+" target="+target+"; physical progress retained");
  throw new ProbePaused();
 }
}
