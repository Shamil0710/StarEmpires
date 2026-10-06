import org.lwjgl.system.*;
import org.lwjgl.system.linux.DynamicLinkLoader;
import org.lwjgl.opengl.*;
import org.lwjgl.glfw.GLFW;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.utils.*;
import java.lang.reflect.*;
import java.util.*;
/** Engineering smoke on an EGL pbuffer or hidden Windows GLFW window; not human acceptance. */
public class Stage23BSoftwareGraphicsSmoke {
 static long lib; static InputProcessor processor; static Set<Integer> held=new HashSet<>();
 static long f(String n){return DynamicLinkLoader.dlsym(lib,n);}
 static Object fallback(Class<?> c){if(c==boolean.class)return false;if(c==int.class)return 0;if(c==float.class)return 0f;if(c==long.class)return 0L;return null;}
 static void click(com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget t){var b=t.bounds();processor.touchDown((int)(b.x()+b.width()/2),(int)(720-b.y()-b.height()/2),0,Input.Buttons.LEFT);}
 static void keyboardAction(com.spacesim.ui.GeneratedWorldCommandUiRenderer renderer, com.spacesim.GeneratedWorldCommandGame game, String id){
  int moves=0;do{processor.keyDown(Input.Keys.TAB);game.render();if(++moves>100)throw new AssertionError("Keyboard action unreachable "+id);}while(renderer.keyboardTarget()==null||!renderer.keyboardTarget().id().equals(id));
  processor.keyDown(Input.Keys.ENTER);game.render();
 }
 static com.spacesim.campaign.Stage228CampaignAuthority campaign(com.spacesim.GeneratedWorldCommandGame game)throws Exception{
  var field=game.getClass().getDeclaredField("campaign");field.setAccessible(true);return (com.spacesim.campaign.Stage228CampaignAuthority)field.get(game);
 }
 static void screenshot(String name){
  var px=new com.badlogic.gdx.graphics.Pixmap(1280,720,com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
  px.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,1280,720,true)).flip();
  com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("java.io.tmpdir")+"/"+name+".png"),px);px.dispose();
 }
 static void moduleCustody(com.spacesim.GeneratedWorldCommandGame game,
   com.spacesim.ui.GeneratedWorldCommandUiRenderer renderer, com.spacesim.ui.ProductionUiWorkspace workspace,
   java.nio.file.Path path)throws Exception{
  var original=campaign(game).captureState();
  var products=com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts();
  var product=products.getProducts().stream().filter(p->p.kind()==com.spacesim.content.Stage18ManufacturingProductRegistry.ProductKind.MODULE)
    .min(java.util.Comparator.comparingDouble(com.spacesim.content.Stage18ManufacturingProductRegistry.ProductDefinition::unitMassKg)).orElseThrow();
  var current=campaign(game);
  var station=current.coordinator().runtime().industry().industrial().stations().stream()
    .filter(s->s.storage().remainingCapacityKg(product.storageClassId())>=product.unitMassKg())
    .filter(s->current.pilotMarketReference(s.stationId()).isPresent()).findFirst().orElseThrow();
  var ref=current.pilotMarketReference(station.stationId()).orElseThrow();
  var p=original.playerState();var known=new java.util.ArrayList<>(p.discoveredSystemIds());
  if(!known.contains(ref.systemId()))known.add(ref.systemId());
  var owned=new java.util.ArrayList<>(p.ownedStations());
  var ownerRef=new com.spacesim.player.OwnedStationRef(ref.systemId(),ref.entityId());
  if(!owned.contains(ownerRef))owned.add(ownerRef);
  // Explicit equipment/ownership fixture verifies rendering and loading, not ordinary player acquisition.
  var owner=new com.spacesim.player.PlayerState(p.walletMilliCredits(),p.factionContentId(),p.reputations(),p.ownedFleetIds(),
    p.activeFleetId(),known,p.discoveredObjects(),p.homeSystemId(),p.dockedAt(),p.fleetOrders(),p.threatIntel(),p.ownedConstructionProjectIds(),owned);
  var custody=new com.spacesim.economy.ShipyardModuleCustodyState(java.util.List.of(
    new com.spacesim.economy.ShipyardModuleCustodyState.StoredModule("fixture.graphical/removal.mount",station.stationId(),31,
      current.coordinator().runtime().world().getAuthoritativeWorldTick(),
      new com.spacesim.ship.ShipyardRefitContinuity.RemovedModuleState(
        new com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition("removal.mount",product.contentId()),.35,920))));
  var fixture=com.spacesim.persistence.Stage228GeneratedCampaignPersistentState.compose(original.stage21Runtime(),original.smallCraft(),
    original.hangars(),original.flightDeck(),original.operations(),owner,original.playerJournal(),custody);
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,fixture);
  processor.keyDown(Input.Keys.F9);game.render();
  if(!fixture.equals(campaign(game).captureState()))throw new AssertionError("Individual equipment lost on actual UI native load");
  var hf=renderer.getClass().getDeclaredField("hitTargets");hf.setAccessible(true);
  var hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  selectRow(workspace,game,"stored-module|fixture.graphical/removal.mount");screenshot("stage23b-removed-module-custody");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(!fixture.equals(campaign(game).captureState()))throw new AssertionError("Individual equipment changed on UI save/load");
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,original);
  processor.keyDown(Input.Keys.F9);game.render();
  if(!original.equals(campaign(game).captureState()))throw new AssertionError("Equipment fixture affected ordinary campaign journey");
  System.out.println("Individual removed-equipment rendering/native save/load passed with explicit ownership/equipment fixture");
 }
 static String difference(Object a,Object b,String path)throws Exception{
  if(java.util.Objects.equals(a,b))return "equal";
  if(a==null||b==null||!a.getClass().equals(b.getClass()))return path;
  if(a instanceof java.util.List<?> x && b instanceof java.util.List<?> y){
   if(x.size()!=y.size())return path+".size";
   for(int i=0;i<x.size();i++)if(!java.util.Objects.equals(x.get(i),y.get(i)))return difference(x.get(i),y.get(i),path+"["+i+"]");
  }
  if(a.getClass().isRecord())for(var field:a.getClass().getRecordComponents()){
   Object x=field.getAccessor().invoke(a),y=field.getAccessor().invoke(b);
   if(!java.util.Objects.equals(x,y))return difference(x,y,path+"."+field.getName());
  }
  return path;
 }
 static void repair(com.spacesim.GeneratedWorldCommandGame game,
   com.spacesim.ui.GeneratedWorldCommandUiRenderer renderer, com.spacesim.ui.ProductionUiWorkspace workspace,
   java.nio.file.Path path)throws Exception{
  var original=campaign(game).captureState();
  var fixture=com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.read(java.nio.file.Path.of("target/stage23b-repair-fixture.s28c"));
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,fixture);
  processor.keyDown(Input.Keys.F9);game.render();
  if(!fixture.equals(campaign(game).captureState())){
   var sf=game.getClass().getDeclaredField("status");sf.setAccessible(true);
   var actual=campaign(game).captureState();
   throw new AssertionError("Repair fixture changed on actual UI load; status="+sf.get(game)
     +" difference="+difference(fixture,actual,"checkpoint"));
  }
  var current=campaign(game);
  String station=current.coordinator().runtime().industry().industrial().stations().stream()
    .filter(s->current.ownsProductionStation(s.stationId())).findFirst().orElseThrow().stationId();
  var raw=current.coordinator().runtime().infrastructure().endpoint(station).storage().snapshotCommodityMassByIdKg();
  var hf=renderer.getClass().getDeclaredField("hitTargets");hf.setAccessible(true);
  var hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  selectRow(workspace,game,"pilot-repair|"+station+"|");screenshot("stage23b-repair-start");
  keyboardAction(renderer,game,"pilot.start-repair");
  if(!fixture.equals(campaign(game).captureState()))throw new AssertionError("Repair UI preview changed physical state");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  var queued=campaign(game).captureState();
  if(queued.repairQueue().orders().size()!=1||queued.repairQueue().orders().get(0).completedWorkSeconds()!=0)
    throw new AssertionError("Repair UI did not reserve a finite pending job");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(!queued.equals(campaign(game).captureState()))throw new AssertionError("Pending repair changed on UI save/load");
  hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  String job=queued.repairQueue().orders().get(0).orderId();
  selectRow(workspace,game,"pilot-repair-cancel|"+station+"|"+job);screenshot("stage23b-repair-pending");
  keyboardAction(renderer,game,"pilot.cancel-repair");
  if(!queued.equals(campaign(game).captureState()))throw new AssertionError("Cancel preview changed physical state");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  if(!campaign(game).repairQueue().orders().isEmpty()||!raw.equals(campaign(game).coordinator().runtime().infrastructure().endpoint(station).storage().snapshotCommodityMassByIdKg()))
    throw new AssertionError("Repair cancellation did not return exact material escrow");
  if(fixture.playerState().walletMilliCredits()!=campaign(game).playerState().orElseThrow().walletMilliCredits())
    throw new AssertionError("Own-station repair invented a monetary charge");
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,original);
  processor.keyDown(Input.Keys.F9);game.render();
  if(!original.equals(campaign(game).captureState()))throw new AssertionError("Repair fixture affected ordinary journey");
  System.out.println("Repair UI preview/reservation/native save/load/cancellation passed with explicit station, power and material fixture");
 }
 static void refit(com.spacesim.GeneratedWorldCommandGame game,
   com.spacesim.ui.GeneratedWorldCommandUiRenderer renderer, com.spacesim.ui.ProductionUiWorkspace workspace,
   java.nio.file.Path path)throws Exception{
  var original=campaign(game).captureState();
  var fixture=com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.read(java.nio.file.Path.of("target/stage23b-refit-fixture.s28c"));
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,fixture);
  processor.keyDown(Input.Keys.F9);game.render();
  if(!fixture.equals(campaign(game).captureState()))throw new AssertionError("Refit fixture changed on actual UI load");
  var current=campaign(game);
  String station=current.coordinator().runtime().industry().industrial().stations().stream()
    .filter(s->current.ownsProductionStation(s.stationId())).findFirst().orElseThrow().stationId();
  var stock=current.coordinator().runtime().infrastructure().endpoint(station).storage().snapshot();
  var hf=renderer.getClass().getDeclaredField("hitTargets");hf.setAccessible(true);
  var hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  String target=com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT;
  selectRow(workspace,game,"pilot-refit|"+station+"|"+target);screenshot("stage23b-refit-start");
  keyboardAction(renderer,game,"pilot.start-refit");
  if(!fixture.equals(campaign(game).captureState()))throw new AssertionError("Refit UI preview changed physical state");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  var queued=campaign(game).captureState();
  if(queued.refitQueue().orders().size()!=1||queued.refitQueue().orders().get(0).completedWorkSeconds()!=0)
    throw new AssertionError("Refit UI did not reserve a finite pending job");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(!queued.equals(campaign(game).captureState()))throw new AssertionError("Pending refit changed on UI native save/load");
  hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  selectRow(workspace,game,"pilot-refit-cancel|"+station+"|"+queued.refitQueue().orders().get(0).orderId());screenshot("stage23b-refit-pending");
  keyboardAction(renderer,game,"pilot.cancel-refit");
  if(!queued.equals(campaign(game).captureState()))throw new AssertionError("Refit cancel preview changed physical state");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  if(!campaign(game).refitQueue().orders().isEmpty()
    ||!stock.equals(campaign(game).coordinator().runtime().infrastructure().endpoint(station).storage().snapshot()))
    throw new AssertionError("Refit cancellation did not return exact equipment escrow");
  if(fixture.playerState().walletMilliCredits()!=campaign(game).playerState().orElseThrow().walletMilliCredits())
    throw new AssertionError("Own-station refit invented a monetary charge");
  var usedFixture=com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.read(java.nio.file.Path.of("target/stage23b-used-refit-fixture.s28c"));
  // The completion fixture is running; freeze its restored authority before the UI can render a tick.
  var usedAuthority=com.spacesim.campaign.Stage228CampaignAuthority.restore(usedFixture);
  usedAuthority.coordinator().setPaused(true);usedFixture=usedAuthority.captureState();
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,usedFixture);
  processor.keyDown(Input.Keys.F9);game.render();
  hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  var used=usedFixture.moduleCustody().modules().get(0);
  selectRow(workspace,game,"stored-module|"+used.custodyId());screenshot("stage23b-used-refit-start");
  keyboardAction(renderer,game,"pilot.start-refit-used");
  if(!usedFixture.equals(campaign(game).captureState()))throw new AssertionError("Used refit preview changed custody");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  var usedQueued=campaign(game).captureState();
  if(usedQueued.refitQueue().orders().size()!=1||!campaign(game).isStoredModuleReserved(used.custodyId())
    ||!usedQueued.moduleCustody().equals(usedFixture.moduleCustody()))throw new AssertionError("Used refit did not reserve exact equipment");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(!usedQueued.equals(campaign(game).captureState()))throw new AssertionError("Used reservation changed on UI save/load");
  hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  selectRow(workspace,game,"pilot-refit-cancel|"+station+"|"+usedQueued.refitQueue().orders().get(0).orderId());
  keyboardAction(renderer,game,"pilot.cancel-refit");keyboardAction(renderer,game,"pilot.physical-confirm");
  if(!campaign(game).refitQueue().orders().isEmpty()||campaign(game).isStoredModuleReserved(used.custodyId())
    ||!usedFixture.moduleCustody().equals(campaign(game).moduleCustody()))throw new AssertionError("Used cancellation lost or altered equipment");
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,original);
  processor.keyDown(Input.Keys.F9);game.render();
  if(!original.equals(campaign(game).captureState()))throw new AssertionError("Refit fixture affected ordinary campaign");
  System.out.println("Refit keyboard preview/confirm/native save/load/cancel passed with explicit station, power and supplied module fixture");
 }
 static void moduleTransport(com.spacesim.GeneratedWorldCommandGame game,
   com.spacesim.ui.GeneratedWorldCommandUiRenderer renderer,com.spacesim.ui.ProductionUiWorkspace workspace,
   java.nio.file.Path path)throws Exception{
  var original=campaign(game).captureState();
  var fixture=com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.read(java.nio.file.Path.of("target/stage23b-module-transport-fixture.s28c"));
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,fixture);
  processor.keyDown(Input.Keys.F9);game.render();
  var hf=renderer.getClass().getDeclaredField("hitTargets");hf.setAccessible(true);
  var hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  var row=fixture.moduleCustody().modules().get(0);String station=row.stationId();
  selectRow(workspace,game,"stored-module|"+row.custodyId());screenshot("stage23b-module-load");
  keyboardAction(renderer,game,"pilot.load-module");
  if(!fixture.equals(campaign(game).captureState()))throw new AssertionError("Loading preview changed equipment");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  var queued=campaign(game).captureState();
  if(queued.moduleTransfers().orders().size()!=1||!queued.moduleCustody().equals(fixture.moduleCustody()))throw new AssertionError("Loading did not reserve exact source");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(!queued.equals(campaign(game).captureState()))throw new AssertionError("Loading changed on native save/load");
  hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  selectRow(workspace,game,"module-transfer-cancel|"+station+"|"+queued.moduleTransfers().orders().get(0).orderId());
  keyboardAction(renderer,game,"pilot.cancel-module-transfer");keyboardAction(renderer,game,"pilot.physical-confirm");
  if(!campaign(game).moduleTransfers().orders().isEmpty()||!campaign(game).moduleCustody().equals(fixture.moduleCustody()))throw new AssertionError("Loading cancel lost equipment");
  var aboard=com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.read(java.nio.file.Path.of("target/stage23b-module-aboard-fixture.s28c"));
  var authority=com.spacesim.campaign.Stage228CampaignAuthority.restore(aboard);
  authority.coordinator().setPaused(false);authority.advanceFrame(authority.coordinator().session().fixedStepSeconds());
  authority.coordinator().setPaused(true);aboard=authority.captureState();
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,aboard);
  processor.keyDown(Input.Keys.F9);game.render();
  hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
    &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INDUSTRY).findFirst().orElseThrow());game.render();
  selectRow(workspace,game,"carried-module|"+row.custodyId());screenshot("stage23b-module-unload");
  keyboardAction(renderer,game,"pilot.unload-module");
  if(!aboard.equals(campaign(game).captureState()))throw new AssertionError("Unloading preview changed equipment");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  if(campaign(game).moduleTransfers().orders().size()!=1)throw new AssertionError("Unloading did not start from actual hold");
  processor.keyDown(Input.Keys.F8);game.render();var pending=campaign(game).captureState();processor.keyDown(Input.Keys.F9);game.render();
  if(!pending.equals(campaign(game).captureState()))throw new AssertionError("Unloading changed on native save/load");
  com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.write(path,original);
  processor.keyDown(Input.Keys.F9);game.render();
  if(!original.equals(campaign(game).captureState()))throw new AssertionError("Transport fixture affected ordinary campaign");
  System.out.println("Module loading/unloading keyboard preview/confirm/native save/load/cancel passed");
 }
 static void selectRow(com.spacesim.ui.ProductionUiWorkspace workspace, com.spacesim.GeneratedWorldCommandGame game, String id)throws Exception{
  var field=game.getClass().getDeclaredField("production");field.setAccessible(true);
  int total=workspace.page((com.spacesim.ui.ProductionUiSnapshot)field.get(game),1).total();
  for(int i=0;i<total;i++)processor.keyDown(Input.Keys.UP);game.render();
  int moves=0;while(!workspace.view().selection().stableId().equals(id)){processor.keyDown(Input.Keys.DOWN);game.render();if(++moves>total)throw new AssertionError("Row unreachable "+id);}
 }
 static void ships(com.spacesim.ui.GeneratedWorldCommandUiRenderer renderer, com.spacesim.GeneratedWorldCommandGame game)throws Exception{
  var field=renderer.getClass().getDeclaredField("hitTargets");field.setAccessible(true);
  var hits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)field.get(renderer);
  click(hits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB&&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.SHIPS).findFirst().orElseThrow());game.render();
 }
 static void selectMission(com.spacesim.ui.ProductionUiWorkspace workspace,com.spacesim.GeneratedWorldCommandGame game){
  processor.keyDown(Input.Keys.F6);game.render();int moves=0;while(!workspace.view().selection().stableId().startsWith("player-mission:")){processor.keyDown(Input.Keys.DOWN);game.render();if(++moves>50)throw new AssertionError("Personal mission unreachable");}
 }
 public static void main(String[] args) throws Exception {
  long hiddenWindow=0;
  if(System.getProperty("os.name", "").startsWith("Windows")) {
   if(!GLFW.glfwInit())throw new IllegalStateException("glfwInit failed");
   GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
   GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,2);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,0);
   hiddenWindow=GLFW.glfwCreateWindow(1280,720,"Stage23B engineering smoke",0,0);
   if(hiddenWindow==0){GLFW.glfwTerminate();throw new IllegalStateException("Hidden GLFW context failed");}
   GLFW.glfwMakeContextCurrent(hiddenWindow);
  } else {
  lib=DynamicLinkLoader.dlopen("libEGL.so.1",2);
  try(MemoryStack s=MemoryStack.stackPush()){
   long d=JNI.invokePP(0L,f("eglGetDisplay"));
   if(JNI.invokePPPI(d,0L,0L,f("eglInitialize"))==0)throw new IllegalStateException("eglInitialize "+JNI.invokeI(f("eglGetError")));
   JNI.invokeI(0x30A2,f("eglBindAPI"));
   var attrs=s.ints(0x3033,1,0x3040,8,0x3024,8,0x3023,8,0x3022,8,0x3038);var configs=s.mallocPointer(1);var count=s.mallocInt(1);
   if(JNI.invokePPPPI(d,MemoryUtil.memAddress(attrs),MemoryUtil.memAddress(configs),1,MemoryUtil.memAddress(count),f("eglChooseConfig"))==0||count.get(0)==0)throw new IllegalStateException("config");
   var pb=s.ints(0x3057,1280,0x3056,720,0x3038);
   long surf=JNI.invokePPPP(d,configs.get(0),MemoryUtil.memAddress(pb),f("eglCreatePbufferSurface"));
   long ctx=JNI.invokePPPPP(d,configs.get(0),0L,0L,f("eglCreateContext"));
   if(JNI.invokePPPPI(d,surf,surf,ctx,f("eglMakeCurrent"))==0)throw new IllegalStateException("makecurrent");
  }
  }
  try { run(args); }
  finally { if(hiddenWindow!=0){GL.setCapabilities(null);GLFW.glfwDestroyWindow(hiddenWindow);GLFW.glfwTerminate();} }
 }
 static void run(String[] args) throws Exception {
  GL.createCapabilities(); System.out.println("Renderer: "+GL11.glGetString(GL11.GL_RENDERER));
  GdxNativesLoader.load();Gdx.files=new Lwjgl3Files();var glCtor=Class.forName("com.badlogic.gdx.backends.lwjgl3.Lwjgl3GL20").getDeclaredConstructor();glCtor.setAccessible(true);Gdx.gl20=(com.badlogic.gdx.graphics.GL20)glCtor.newInstance();Gdx.gl=Gdx.gl20;
  Gdx.graphics=(Graphics)Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class[]{Graphics.class},(p,m,a)->switch(m.getName()){
   case "getWidth","getBackBufferWidth"->1280;case "getHeight","getBackBufferHeight"->720;case "getDensity"->1f;case "getDeltaTime","getRawDeltaTime"->1f/60;case "getGL20"->Gdx.gl20;
   case "getGLVersion"->new GLVersion(Application.ApplicationType.Desktop,GL11.glGetString(GL11.GL_VERSION),GL11.glGetString(GL11.GL_VENDOR),GL11.glGetString(GL11.GL_RENDERER));default->fallback(m.getReturnType());});
  Gdx.app=(Application)Proxy.newProxyInstance(Application.class.getClassLoader(),new Class[]{Application.class},(p,m,a)->{if(m.getName().equals("getType"))return Application.ApplicationType.Desktop;if(m.getName().equals("error"))System.err.println(Arrays.toString(a));return fallback(m.getReturnType());});
  Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class[]{Input.class},(p,m,a)->{if(m.getName().equals("setInputProcessor")){processor=(InputProcessor)a[0];return null;}if(m.getName().equals("isKeyPressed"))return held.contains((Integer)a[0]);return fallback(m.getReturnType());});
  var game=new com.spacesim.GeneratedWorldCommandGame(1);game.create();
  var save=game.getClass().getDeclaredField("savePath");save.setAccessible(true);
  save.set(game,java.nio.file.Files.createTempDirectory("stage23b-smoke-").resolve("campaign.s25"));
  var pendingSave=(java.nio.file.Path)save.get(game);
  if(Boolean.getBoolean("stage23b.repairOnly")){
   var rf=game.getClass().getDeclaredField("renderer");rf.setAccessible(true);
   var wf=game.getClass().getDeclaredField("workspace");wf.setAccessible(true);
   repair(game,(com.spacesim.ui.GeneratedWorldCommandUiRenderer)rf.get(game),
     (com.spacesim.ui.ProductionUiWorkspace)wf.get(game),pendingSave);
   if(GL11.glGetError()!=0)throw new AssertionError("Repair GL error");
   game.dispose();System.out.println("Repair graphical probe passed");return;
  }
  if(Boolean.getBoolean("stage23b.moduleTransportOnly")){
   var rf=game.getClass().getDeclaredField("renderer");rf.setAccessible(true);
   var wf=game.getClass().getDeclaredField("workspace");wf.setAccessible(true);
   moduleTransport(game,(com.spacesim.ui.GeneratedWorldCommandUiRenderer)rf.get(game),
     (com.spacesim.ui.ProductionUiWorkspace)wf.get(game),pendingSave);
   if(GL11.glGetError()!=0)throw new AssertionError("Equipment transport GL error");
   game.dispose();System.out.println("Equipment transport graphical probe passed");return;
  }
  if(Boolean.getBoolean("stage23b.refitOnly")){
   System.out.println("Freight engineering fingerprint: "+com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault().getFingerprint());
   System.out.println("Civilian mining engineering fingerprint: "+com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader.loadDefault().getFingerprint());
   System.out.println("Runtime manufacturing fingerprint: "+com.spacesim.content.Stage22CivilianMiningProductionPath.loadManufacturing().getFingerprint());
   System.out.println("Runtime shipyards fingerprint: "+com.spacesim.content.Stage22CivilianMiningProductionPath.loadRuntimeShipyards().getFingerprint());
   var rf=game.getClass().getDeclaredField("renderer");rf.setAccessible(true);
   var wf=game.getClass().getDeclaredField("workspace");wf.setAccessible(true);
   refit(game,(com.spacesim.ui.GeneratedWorldCommandUiRenderer)rf.get(game),
     (com.spacesim.ui.ProductionUiWorkspace)wf.get(game),pendingSave);
   if(GL11.glGetError()!=0)throw new AssertionError("Refit GL error");
   game.dispose();System.out.println("Refit graphical probe passed");return;
  }
  byte[] previousSave={1,2,3};java.nio.file.Files.write(pendingSave,previousSave);
  game.render();processor.keyDown(Input.Keys.F8);game.render();
  if(!java.util.Arrays.equals(previousSave,java.nio.file.Files.readAllBytes(pendingSave)))throw new AssertionError("Unconfirmed creation overwrote the previous save");
  var startRf=game.getClass().getDeclaredField("renderer");startRf.setAccessible(true);
  var startRenderer=(com.spacesim.ui.GeneratedWorldCommandUiRenderer)startRf.get(game);
  keyboardAction(startRenderer,game,"pilot.preview");
  if(campaign(game).playerState().isPresent())throw new AssertionError("Start preview mutated live player");
  keyboardAction(startRenderer,game,"pilot.confirm");
  var startingPlayer=campaign(game).playerState().orElseThrow();
  if(startingPlayer.walletMilliCredits()!=75_000_000L||startingPlayer.ownedFleetIds().size()!=1
          ||startingPlayer.factionContentId()!=null)throw new AssertionError("Fresh independent start failed");
  var playerPlacement=campaign(game).coordinator().runtime().world().findFleet(startingPlayer.activeFleetId()).orElseThrow();
  if(!playerPlacement.systemId().equals(campaign(game).coordinator().runtime().world().getActiveSystemId()))throw new AssertionError("Start did not open the personal home system");
  keyboardAction(startRenderer,game,"focus");
  held.add(Input.Keys.W);
  processor.keyDown(Input.Keys.SPACE);for(int i=0;i<12;i++)game.render();held.clear();
  var physical=campaign(game).coordinator().runtime().arrival().materialization(playerPlacement.systemId()).physicalState(playerPlacement.localEntityId()).orElseThrow();
  if(physical.velocityYMps()<=0d)throw new AssertionError("Keyboard thrust did not reach real fitted ship");
  processor.keyDown(Input.Keys.SPACE);game.render();
  System.out.println("Fresh pilot keyboard preview/purchase/focus/thrust passed without fixture");
  processor.keyDown(Input.Keys.SPACE);game.render();
  int[] keys={Input.Keys.F1,Input.Keys.F2,Input.Keys.F3,Input.Keys.F4,Input.Keys.F5,Input.Keys.F6,Input.Keys.F7,Input.Keys.O};
  for(int k:keys){processor.keyDown(k);game.render();processor.keyDown(Input.Keys.DOWN);game.render();com.badlogic.gdx.graphics.Pixmap px=new com.badlogic.gdx.graphics.Pixmap(1280,720,com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);px.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,1280,720,true)).flip();com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("java.io.tmpdir")+"/stage23b-screen-"+k+".png"),px);px.dispose();if(GL11.glGetError()!=0)throw new IllegalStateException("GL error tab "+k);System.out.println("Rendered key "+k);}
  var rf=game.getClass().getDeclaredField("renderer");rf.setAccessible(true);
  var renderer=(com.spacesim.ui.GeneratedWorldCommandUiRenderer)rf.get(game);
  var hf=renderer.getClass().getDeclaredField("hitTargets");hf.setAccessible(true);
  var wf=game.getClass().getDeclaredField("workspace");wf.setAccessible(true);
  var workspace=(com.spacesim.ui.ProductionUiWorkspace)wf.get(game);
  for(var tab:com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.values()){
   int moves=0;do{processor.keyDown(Input.Keys.TAB);game.render();if(++moves>100)throw new AssertionError("Keyboard unreachable "+tab);}while(renderer.keyboardTarget()==null||renderer.keyboardTarget().kind()!=com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB||renderer.keyboardTarget().tab()!=tab);
   processor.keyDown(Input.Keys.ENTER);game.render();if(workspace.tab()!=tab)throw new AssertionError("Keyboard activate "+tab);System.out.println("Keyboard-only navigation passed "+tab);
  }
  for(var tab:com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.values()){
   var list=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
   var target=list.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB && h.tab()==tab).findFirst().orElseThrow();
   click(target);game.render();if(workspace.tab()!=tab)throw new AssertionError("Mouse tab "+tab);
   list=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
   var row=list.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.SURFACE_ROW||h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.MILITARY||h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.FREIGHT).findFirst();
   if(row.isPresent()){click(row.get());game.render();}
   processor.keyDown(Input.Keys.PAGE_DOWN);held.add(Input.Keys.CONTROL_LEFT);processor.keyDown(Input.Keys.PAGE_DOWN);held.clear();game.render();if(GL11.glGetError()!=0)throw new AssertionError("GL error mouse "+tab);
   System.out.println("Mouse/scroll passed "+tab);
  }
  campaign(game).coordinator().setPaused(true);
  var cameraCheckpoint=campaign(game).captureState();
  String activeObject="fleet:"+campaign(game).playerState().orElseThrow().activeFleetId().value();
  for(var tab:com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.values()){
   var list=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
   click(list.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB&&h.tab()==tab).findFirst().orElseThrow());game.render();
   keyboardAction(renderer,game,"focus-player");
   if(workspace.tab()!=com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.SYSTEM||!workspace.view().selection().stableId().equals(activeObject))throw new AssertionError("Return to player from "+tab);
   processor.keyDown(Input.Keys.F3);game.render();
   held.add(Input.Keys.CONTROL_LEFT);processor.keyDown(Input.Keys.C);held.clear();game.render();
   if(workspace.tab()!=com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.SYSTEM||!workspace.view().selection().stableId().equals(activeObject))throw new AssertionError("Ctrl+C return to player");
  }
  if(!campaign(game).captureState().equals(cameraCheckpoint))throw new AssertionError("Camera return mutated campaign");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  var sf=game.getClass().getDeclaredField("status");sf.setAccessible(true);System.out.println("Save/load status: "+sf.get(game));if(!sf.get(game).toString().contains("загруж"))throw new AssertionError("Save/load failed");
  // Physical docking setup is an explicit geometry fixture; creation and UI commands remain real.
  var pilotCampaign=campaign(game);var pilotRuntime=pilotCampaign.coordinator().runtime();
  var pilotFleet=pilotRuntime.world().findFleet(pilotCampaign.playerState().orElseThrow().activeFleetId()).orElseThrow();
  String water="commodity.material.purified_water";
  var endpoint=pilotRuntime.infrastructure().endpoints().stream().filter(e->e.systemId().equals(pilotFleet.systemId())
          &&e.storage().commodityMassKg(water)>1&&pilotCampaign.pilotMarketReference(e.stationId()).isPresent()).findFirst().orElseThrow();
  pilotCampaign.coordinator().setPaused(true);
  pilotRuntime.arrival().materialization(pilotFleet.systemId()).updatePhysicalState(pilotFleet.localEntityId(),
          com.spacesim.world.LocalPhysicalKinematics.stationary(endpoint.position().translated(500,0)));
  processor.keyDown(Input.Keys.F5);game.render();int pilotMoves=0;
  while(!workspace.view().selection().stableId().equals("pilot-station|"+endpoint.stationId())){
   processor.keyDown(Input.Keys.DOWN);game.render();if(++pilotMoves>100)throw new AssertionError("Personal station unreachable");}
  keyboardAction(renderer,game,"focus");
  if(workspace.tab()!=com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.SYSTEM
          ||!workspace.view().selection().stableId().equals("station:"+endpoint.stationId()))throw new AssertionError("Station focus failed");
  processor.keyDown(Input.Keys.F5);game.render();pilotMoves=0;
  while(!workspace.view().selection().stableId().equals("pilot-station|"+endpoint.stationId())){
   processor.keyDown(Input.Keys.DOWN);game.render();if(++pilotMoves>100)throw new AssertionError("Personal station unreachable");}
  keyboardAction(renderer,game,"pilot.dock");
  if(pilotCampaign.playerState().orElseThrow().docked())throw new AssertionError("Dock preview mutated state");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  if(!pilotCampaign.playerState().orElseThrow().docked())throw new AssertionError("Dock confirmation failed");
  processor.keyDown(Input.Keys.SPACE);for(int i=0;i<10;i++)game.render();
  for(int i=0;i<30;i++){processor.keyDown(Input.Keys.UP);game.render();}
  pilotMoves=0;while(!workspace.view().selection().stableId().equals("pilot-market|"+endpoint.stationId()+"|"+water)){
   processor.keyDown(Input.Keys.DOWN);game.render();if(++pilotMoves>100)throw new AssertionError("Water offer unreachable");}
  long money=pilotCampaign.playerState().orElseThrow().walletMilliCredits();
  long initialQuote=pilotCampaign.pilotCommodityPrice(endpoint.stationId(),water,true);
  keyboardAction(renderer,game,"pilot.buy");
  if(pilotCampaign.playerState().orElseThrow().walletMilliCredits()!=money)throw new AssertionError("Trade preview mutated wallet");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  if(pilotCampaign.playerState().orElseThrow().walletMilliCredits()!=money-initialQuote
          ||pilotRuntime.freight().cargoHoldSnapshot(pilotFleet.fleetId()).commodityMassByIdKg().getOrDefault(water,0d)!=1d)throw new AssertionError("UI physical purchase failed");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(campaign(game).coordinator().runtime().freight().cargoHoldSnapshot(pilotFleet.fleetId()).commodityMassByIdKg().getOrDefault(water,0d)!=1d)throw new AssertionError("Purchased cargo was not saved");
  System.out.println("Keyboard station focus/dock/preview/physical purchase/save/load passed with explicit docking geometry fixture");
  // Additional purchase and handover use the existing local reserve and real treasury.
  var assetsCampaign=campaign(game);var assetsRuntime=assetsCampaign.coordinator().runtime();
  var second=assetsRuntime.freight().capture().freighters().stream().filter(f->f.phase()==com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE
          &&f.currentSystemId().equals(pilotFleet.systemId())&&!assetsCampaign.playerState().orElseThrow().ownedFleetIds().contains(f.fleetId())).findFirst().orElseThrow();
  ships(renderer,game);selectRow(workspace,game,"pilot-reserve|"+second.fleetId().value());
  long beforeReserve=campaign(game).playerState().orElseThrow().walletMilliCredits();
  keyboardAction(renderer,game,"pilot.purchase");
  if(campaign(game).playerState().orElseThrow().walletMilliCredits()!=beforeReserve)throw new AssertionError("Reserve preview changed wallet");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  if(campaign(game).playerState().orElseThrow().walletMilliCredits()!=beforeReserve-25_000_000L)throw new AssertionError("Reserve UI purchase failed");
  processor.keyDown(Input.Keys.F5);game.render();selectRow(workspace,game,"pilot-station|"+endpoint.stationId());
  keyboardAction(renderer,game,"pilot.undock");keyboardAction(renderer,game,"pilot.physical-confirm");
  ships(renderer,game);selectRow(workspace,game,"personal-ship:"+second.fleetId().value());
  keyboardAction(renderer,game,"pilot.switch");keyboardAction(renderer,game,"pilot.physical-confirm");
  if(!campaign(game).playerState().orElseThrow().activeFleetId().equals(second.fleetId()))throw new AssertionError("Personal handover failed");
  processor.keyDown(Input.Keys.F4);game.render();
  selectRow(workspace,game,"player-fleet|follow|"+pilotFleet.fleetId().value()+"|"+second.fleetId().value());
  var beforeFleetOrder=campaign(game).captureState();keyboardAction(renderer,game,"pilot.government-follow");screenshot("stage23b-personal-fleet-preview");
  if(!beforeFleetOrder.equals(campaign(game).captureState()))throw new AssertionError("Fleet preview mutated world");
  keyboardAction(renderer,game,"pilot.government-confirm");
  var followerBefore=campaign(game).coordinator().runtime().arrival().materialization(pilotFleet.systemId()).physicalState(pilotFleet.localEntityId()).orElseThrow();
  processor.keyDown(Input.Keys.SPACE);for(int i=0;i<60;i++)game.render();processor.keyDown(Input.Keys.SPACE);game.render();
  var followerAfter=campaign(game).coordinator().runtime().arrival().materialization(pilotFleet.systemId()).physicalState(pilotFleet.localEntityId()).orElseThrow();
  if(followerBefore.position().distanceTo(followerAfter.position())<=0)throw new AssertionError("Following inactive hull did not move physically");
  keyboardAction(renderer,game,"pilot.government-escort");keyboardAction(renderer,game,"pilot.government-confirm");
  selectRow(workspace,game,"player-fleet|hold|"+pilotFleet.fleetId().value());keyboardAction(renderer,game,"pilot.government-hold");keyboardAction(renderer,game,"pilot.government-confirm");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(campaign(game).playerState().orElseThrow().fleetOrders().stream().noneMatch(o->o.fleetId().equals(pilotFleet.fleetId())&&o.type()==com.spacesim.player.FleetOrderType.HOLD))throw new AssertionError("Personal order was not restored");
  System.out.println("Independent personal follow/escort/hold keyboard preview, real finite flight and order reload passed");
  processor.keyDown(Input.Keys.F7);game.render();selectRow(workspace,game,"pilot-faction-foundation");
  keyboardAction(renderer,game,"pilot.faction-preview");screenshot("stage23b-faction-foundation-preview");
  if(campaign(game).playerState().orElseThrow().factionContentId()!=null)throw new AssertionError("Foundation preview changed faction");
  keyboardAction(renderer,game,"pilot.faction-confirm");
  if(!"faction.player".equals(campaign(game).playerState().orElseThrow().factionContentId()))throw new AssertionError("Foundation UI confirmation failed");
  selectRow(workspace,game,"pilot-faction-finance");screenshot("stage23b-faction-finance");
  long personalBefore=campaign(game).playerState().orElseThrow().walletMilliCredits();
  keyboardAction(renderer,game,"pilot.faction-capitalize");keyboardAction(renderer,game,"pilot.physical-confirm");
  if(campaign(game).coordinator().runtime().world().findFactionEconomicState("faction.player").orElseThrow().treasuryMilliCredits()!=1_000_000L)throw new AssertionError("Capitalization failed");
  keyboardAction(renderer,game,"pilot.faction-withdraw");keyboardAction(renderer,game,"pilot.physical-confirm");
  if(campaign(game).playerState().orElseThrow().walletMilliCredits()!=personalBefore)throw new AssertionError("Treasury return failed");
  // Explicit own registration updates world legal affiliation and the versioned freight mirror together.
  processor.keyDown(Input.Keys.F3);game.render();selectRow(workspace,game,"player-government|affiliation");
  var beforeAffiliation=campaign(game).captureState();
  keyboardAction(renderer,game,"pilot.government-affiliate");screenshot("stage23b-own-registration-preview");
  if(!beforeAffiliation.equals(campaign(game).captureState()))throw new AssertionError("Affiliation preview mutated world");
  keyboardAction(renderer,game,"pilot.government-confirm");
  for(var owned:campaign(game).playerState().orElseThrow().ownedFleetIds())if(!campaign(game).coordinator().runtime().freight().findFreighter(owned).orElseThrow().legalFactionId().equals("faction.player"))throw new AssertionError("UI legal affiliation mirror failed");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(campaign(game).playerState().orElseThrow().walletMilliCredits()!=personalBefore)throw new AssertionError("Affiliation changed money on reload");
  System.out.println("Explicit two-hull world/freight legal affiliation preview, confirmation and reload passed without assets or funds");
  // Personal political decisions use shared persistent rules; no fixture faction authority is supplied.
  processor.keyDown(Input.Keys.F3);game.render();selectRow(workspace,game,"player-government|doctrine|0");
  keyboardAction(renderer,game,"pilot.government-more");screenshot("stage23b-own-policy-preview");
  if(campaign(game).coordinator().runtime().world().findFactionStrategicState("faction.player").orElseThrow().doctrine().tradeOpenness()!=50)throw new AssertionError("Policy preview mutated live doctrine");
  keyboardAction(renderer,game,"pilot.government-confirm");
  if(campaign(game).coordinator().runtime().world().findFactionStrategicState("faction.player").orElseThrow().doctrine().tradeOpenness()!=55)throw new AssertionError("Doctrine UI command failed");
  selectRow(workspace,game,"player-government|fiscal|0");keyboardAction(renderer,game,"pilot.government-more");keyboardAction(renderer,game,"pilot.government-confirm");
  if(campaign(game).coordinator().runtime().world().findFactionFiscalPolicy("faction.player").orElseThrow().stationTaxBasisPoints()!=100)throw new AssertionError("Fiscal UI command failed");
  selectRow(workspace,game,"player-government|stock|item.energy");
  var beforeStockCheckpoint=campaign(game).captureState();keyboardAction(renderer,game,"pilot.government-more");screenshot("stage23b-own-stock-preview");
  if(!beforeStockCheckpoint.equals(campaign(game).captureState()))throw new AssertionError("Stock preview changed authoritative state");
  keyboardAction(renderer,game,"pilot.government-confirm");
  if(campaign(game).coordinator().runtime().world().findFactionStockProductionPolicy("faction.player").orElseThrow().stockPolicies().stream().noneMatch(p->p.itemContentId().equals("item.energy")&&p.targetStockFloor()==100))throw new AssertionError("Stock UI intent failed");
  selectRow(workspace,game,"player-government|production|station.arsenal");keyboardAction(renderer,game,"pilot.government-more");screenshot("stage23b-own-production-preview");keyboardAction(renderer,game,"pilot.government-confirm");
  var stockPolicy=campaign(game).coordinator().runtime().world().findFactionStockProductionPolicy("faction.player").orElseThrow();
  if(stockPolicy.productionPolicies().size()!=1)throw new AssertionError("Production UI intent failed");
  selectRow(workspace,game,"player-government|apply-production");var beforeApply=campaign(game).captureState();
  keyboardAction(renderer,game,"pilot.government-apply");screenshot("stage23b-own-production-apply");keyboardAction(renderer,game,"pilot.government-confirm");
  var afterApply=campaign(game).captureState();
  var expectedApply=com.spacesim.persistence.Stage228GeneratedCampaignPersistentState.compose(beforeApply.stage21Runtime(),
   beforeApply.smallCraft(),beforeApply.hangars(),beforeApply.flightDeck(),beforeApply.operations(),beforeApply.playerState(),afterApply.playerJournal());
  if(!expectedApply.equals(afterApply)||afterApply.playerJournal().nextSequence()!=beforeApply.playerJournal().nextSequence()+1
    ||!afterApply.playerJournal().entries().get(afterApply.playerJournal().entries().size()-1).action().equals("POLICY"))
   throw new AssertionError("Applying without an owned consumer must preserve physical state and append exactly one committed policy event");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(!stockPolicy.equals(campaign(game).coordinator().runtime().world().findFactionStockProductionPolicy("faction.player").orElseThrow()))throw new AssertionError("Stock/production intent lost on reload");
  System.out.println("Personal stock/recipe UI authoring, pure previews, explicit zero-consumer apply and exact reload passed; no physical production grant claimed");
  processor.keyDown(Input.Keys.F3);game.render();
  selectRow(workspace,game,"player-government|embargo|faction.alpha");keyboardAction(renderer,game,"pilot.government-impose");keyboardAction(renderer,game,"pilot.government-confirm");
  if(campaign(game).coordinator().runtime().world().findFactionDiplomacyState("faction.player").orElseThrow().embargoes().isEmpty())throw new AssertionError("Embargo UI command failed");
  keyboardAction(renderer,game,"pilot.government-revoke");keyboardAction(renderer,game,"pilot.government-confirm");
  if(!campaign(game).coordinator().runtime().world().findFactionDiplomacyState("faction.player").orElseThrow().embargoes().isEmpty())throw new AssertionError("Embargo UI revocation failed");
  selectRow(workspace,game,"player-government|offer|faction.alpha|MARKET_ACCESS|");keyboardAction(renderer,game,"pilot.government-mutual");screenshot("stage23b-own-treaty-preview");keyboardAction(renderer,game,"pilot.government-confirm");
  var ownTreaty=campaign(game).coordinator().runtime().world().findFactionDiplomacyState("faction.player").orElseThrow().treaties().get(0);
  if(ownTreaty.status()!=com.spacesim.world.DiplomaticTreatyState.Status.PROPOSED)throw new AssertionError("Offer was not merely proposed");
  var home=campaign(game).playerState().orElseThrow().homeSystemId();
  var beforeController=campaign(game).coordinator().runtime().world().controllingFaction(home);
  selectRow(workspace,game,"player-government|territory|"+home.value());keyboardAction(renderer,game,"pilot.government-claim");keyboardAction(renderer,game,"pilot.government-confirm");
  if(campaign(game).coordinator().runtime().world().findFactionStrategicState("faction.player").orElseThrow().claimFor(home)==null
          ||!beforeController.equals(campaign(game).coordinator().runtime().world().controllingFaction(home)))throw new AssertionError("Claim UI command granted sovereignty or lost claim");
  keyboardAction(renderer,game,"pilot.government-withdraw");keyboardAction(renderer,game,"pilot.government-confirm");
  if(campaign(game).coordinator().runtime().world().findFactionStrategicState("faction.player").orElseThrow().claimFor(home)!=null)throw new AssertionError("Claim UI withdrawal failed");
  if(campaign(game).playerState().orElseThrow().walletMilliCredits()!=personalBefore)throw new AssertionError("Political commands changed personal money");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(campaign(game).coordinator().runtime().world().findFactionStrategicState("faction.player").orElseThrow().doctrine().tradeOpenness()!=55)throw new AssertionError("Policy lost on reload");
  System.out.println("Personal doctrine/fiscal, actual embargo/revocation, proposed treaty, claim/withdrawal and unchanged resources/reload UI commands passed");
  // Return control to the original cargo-bearing hull; the purchased reserve remains at home.
  ships(renderer,game);selectRow(workspace,game,"personal-ship:"+pilotFleet.fleetId().value());
  keyboardAction(renderer,game,"pilot.switch");keyboardAction(renderer,game,"pilot.physical-confirm");
  // Explicit departure geometry fixture, followed by ordinary campaign ticks and real UI preview/confirmation.
  var travelCampaign=campaign(game);var travelRuntime=travelCampaign.coordinator().runtime();var traveler=travelRuntime.world().findFleet(pilotFleet.fleetId()).orElseThrow();
  var destination=travelRuntime.world().getTopology().neighbors(traveler.systemId()).get(0);
  var departure=travelRuntime.arrival().resolve(destination,traveler.systemId());
  travelRuntime.arrival().materialization(traveler.systemId()).updatePhysicalState(traveler.localEntityId(),com.spacesim.world.LocalPhysicalKinematics.stationary(departure.physicalState().position()));
  ships(renderer,game);selectRow(workspace,game,"pilot-jump|"+destination.value());
  keyboardAction(renderer,game,"pilot.jump");screenshot("stage23b-jump-preview");
  if(travelRuntime.world().findFleetJump(pilotFleet.fleetId()).isPresent())throw new AssertionError("Jump preview started travel");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  if(travelRuntime.world().findFleetJump(pilotFleet.fleetId()).isEmpty())throw new AssertionError("Jump UI confirmation failed");
  processor.keyDown(Input.Keys.SPACE);game.render();
  for(int i=0;i<2000&&travelRuntime.world().findFleetJump(pilotFleet.fleetId()).isPresent();i++)travelCampaign.advanceFrame(0.25f);
  game.render();
  if(travelRuntime.world().findFleetJump(pilotFleet.fleetId()).isPresent()||!travelRuntime.world().findFleet(pilotFleet.fleetId()).orElseThrow().systemId().equals(destination)
          ||!travelCampaign.playerState().orElseThrow().discoveredSystemIds().contains(destination))throw new AssertionError("Real departure failed to arrive");
  processor.keyDown(Input.Keys.SPACE);game.render();processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(campaign(game).playerState().orElseThrow().ownedFleetIds().size()!=2
          ||campaign(game).coordinator().runtime().freight().cargoHoldSnapshot(pilotFleet.fleetId()).commodityMassByIdKg().getOrDefault(water,0d)!=1d)throw new AssertionError("Travel/faction/assets lost on reload");
  var sellCampaign=campaign(game);var sellRuntime=sellCampaign.coordinator().runtime();
  var sellFleet=sellRuntime.world().findFleet(pilotFleet.fleetId()).orElseThrow();
  var buyer=sellRuntime.infrastructure().endpoints().stream().filter(e->e.systemId().equals(destination)
          &&e.stationArchetypeId().equals("station.infrastructure.refinery_complex")).findFirst().orElseThrow();
  // Explicit docking geometry fixture; market stock, cash, cargo and UI sale remain ordinary authority.
  sellRuntime.arrival().materialization(destination).updatePhysicalState(sellFleet.localEntityId(),com.spacesim.world.LocalPhysicalKinematics.stationary(buyer.position()));
  processor.keyDown(Input.Keys.F5);game.render();selectRow(workspace,game,"pilot-station|"+buyer.stationId());
  keyboardAction(renderer,game,"pilot.dock");keyboardAction(renderer,game,"pilot.physical-confirm");
  selectRow(workspace,game,"pilot-market|"+buyer.stationId()+"|"+water);screenshot("stage23b-destination-market");
  long bid=sellCampaign.pilotCommodityPrice(buyer.stationId(),water,false);
  long beforeSale=sellCampaign.playerState().orElseThrow().walletMilliCredits();
  double beforeStock=buyer.storage().commodityMassKg(water);
  if(bid<=initialQuote)throw new AssertionError("Destination does not offer conserved profit");
  keyboardAction(renderer,game,"pilot.sell");screenshot("stage23b-profitable-sale-preview");
  if(sellCampaign.playerState().orElseThrow().walletMilliCredits()!=beforeSale)throw new AssertionError("Sale preview changed money");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  if(sellCampaign.playerState().orElseThrow().walletMilliCredits()!=beforeSale+bid
          ||buyer.storage().commodityMassKg(water)!=beforeStock+1d
          ||sellRuntime.freight().cargoHoldSnapshot(pilotFleet.fleetId()).commodityMassByIdKg().getOrDefault(water,0d)!=0d)throw new AssertionError("UI profitable physical sale failed");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(campaign(game).playerState().orElseThrow().walletMilliCredits()!=beforeSale+bid)throw new AssertionError("Sale payment lost on reload");
  // Both systems were actually visited. No remote knowledge fixture is installed for this route.
  processor.keyDown(Input.Keys.F2);game.render();
  String routeDestination=Long.toString(campaign(game).playerState().orElseThrow().homeSystemId().value());
  int routeSelections=0;
  while(!workspace.view().selection().stableId().equals(routeDestination)){
   processor.keyDown(Input.Keys.DOWN);game.render();
   if(++routeSelections>campaign(game).coordinator().runtime().world().getTopology().systems().size())throw new AssertionError("Home route selection unreachable");
  }
  var routeBefore=campaign(game).captureState();
  keyboardAction(renderer,game,"pilot.route-preview");screenshot("stage23b-personal-route-preview");
  var routeField=game.getClass().getDeclaredField("pendingRoute");routeField.setAccessible(true);
  var routePreview=(com.spacesim.campaign.Stage228CampaignAuthority.PilotRoutePreview)routeField.get(game);
  if(routePreview==null||routePreview.departure().allowed()||!routePreview.route().path().get(0).equals(destination)
          ||!routePreview.route().path().get(routePreview.route().path().size()-1).equals(campaign(game).playerState().orElseThrow().homeSystemId()))throw new AssertionError("Docked home route preview invalid");
  if(!routeBefore.equals(campaign(game).captureState()))throw new AssertionError("Route preview mutated paused campaign");
  var routeHits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  if(routeHits.stream().anyMatch(h->h.id().equals("pilot.route-confirm")))throw new AssertionError("Docked route confirmation enabled");
  System.out.println("Personally discovered home route preview and docked departure refusal passed");
  // Actual earlier docking produced personal evidence; no discovery fixture is installed.
  var visitedCampaign=campaign(game);
  var visitedRuntime=visitedCampaign.coordinator().runtime();
  var visitedObject=new com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef(destination,
          com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind.INFRASTRUCTURE,buyer.stationId());
  if(visitedRuntime.discoveryState().knowledgeFor(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
          .discoveryState(visitedObject)!=com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState.KNOWN_STATIC_LOCATION)
   throw new AssertionError("Actual docking evidence lost on reload");
  var intelHits=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(intelHits.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
          &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.INTELLIGENCE).findFirst().orElseThrow());game.render();
  selectRow(workspace,game,"station:"+buyer.stationId());screenshot("stage23b-personal-station-discovery");
  // The preceding real journey consumed reaction mass. Buy one additional kilogram normally,
  // then use the same installed interface, without a tank-depletion or stock fixture.
  visitedCampaign.coordinator().setPaused(false);
  if(visitedCampaign.advanceFrame(visitedCampaign.coordinator().session().fixedStepSeconds()).fixedTicks()!=1)
   throw new AssertionError("Physical supply purchase requires a new actual completed tick");
  visitedCampaign.coordinator().setPaused(true);
  processor.keyDown(Input.Keys.F5);game.render();selectRow(workspace,game,"pilot-market|"+buyer.stationId()+"|"+water);
  keyboardAction(renderer,game,"pilot.buy");keyboardAction(renderer,game,"pilot.physical-confirm");
  var supplyFleet=visitedRuntime.world().findFleet(pilotFleet.fleetId()).orElseThrow();
  var supplyEngineering=visitedRuntime.world().findSession(destination).orElseThrow().getEntityRegistry()
          .require(supplyFleet.localEntityId()).getComponent(com.spacesim.components.EngineeringComponent.class);
  var supplyBinding=com.spacesim.content.Stage22ShipConsumableCatalogLoader.loadDefault().getBindings().stream()
          .filter(b->b.commodityId().equals(water)&&b.interfaceKind()==com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.REACTION_MASS
                  &&supplyEngineering.fit.installedModules().stream().anyMatch(m->m.moduleId().equals(b.moduleId())))
          .findFirst().orElseThrow();
  String supplyMount=supplyEngineering.fit.installedModules().stream().filter(m->m.moduleId().equals(supplyBinding.moduleId()))
          .findFirst().orElseThrow().mountId();
  double supplyMass=new com.spacesim.ship.ProductionEngineeringRuntimeResolver().derive(supplyEngineering).totalMassKg();
  double supplyAmount=supplyEngineering.runtimeState.consumables().interfaceLoads().stream()
          .filter(l->l.mountId().equals(supplyMount)&&l.interfaceId().equals(supplyBinding.interfaceId()))
          .mapToDouble(l->l.amount()).sum();
  var supplyBefore=visitedCampaign.captureState();
  ships(renderer,game);selectRow(workspace,game,"pilot-supply|"+supplyBinding.id()+"|"+supplyMount);
  keyboardAction(renderer,game,"pilot.load-consumable");screenshot("stage23b-personal-supply-preview");
  if(!supplyBefore.equals(visitedCampaign.captureState()))throw new AssertionError("Supply preview mutated campaign");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  if(!supplyBefore.playerState().equals(visitedCampaign.playerState().orElseThrow())
          ||visitedRuntime.freight().cargoHoldSnapshot(pilotFleet.fleetId()).commodityMassByIdKg().getOrDefault(water,0d)!=0d
          ||Math.abs(supplyEngineering.runtimeState.consumables().interfaceLoads().stream()
                  .filter(l->l.mountId().equals(supplyMount)&&l.interfaceId().equals(supplyBinding.interfaceId()))
                  .mapToDouble(l->l.amount()).sum()-supplyAmount-supplyBinding.amountPerKg())>1e-6
          ||Math.abs(supplyMass-new com.spacesim.ship.ProductionEngineeringRuntimeResolver().derive(supplyEngineering).totalMassKg())>1e-6)
   throw new AssertionError("UI supply failed to conserve money/physical mass or consume purchased cargo");
  var suppliedState=visitedCampaign.captureState();
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(!suppliedState.equals(campaign(game).captureState()))throw new AssertionError("Personal supply lost on UI reload");
  System.out.println("Personal station evidence and purchased cargo-to-tank UI supply/reload passed");
  var journalCampaign=campaign(game);
  var committedJournal=journalCampaign.playerJournal();
  if(committedJournal.unreadCount()==0||committedJournal.entries().stream().noneMatch(e->e.action().equals("LOAD_CONSUMABLE")))
   throw new AssertionError("Ordinary physical supply did not create a personal receipt");
  var journalTabs=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
  click(journalTabs.stream().filter(h->h.kind()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitKind.TAB
   &&h.tab()==com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab.HISTORY).findFirst().orElseThrow());game.render();
  selectRow(workspace,game,"pilot-journal|"+(committedJournal.nextSequence()-1));
  var beforeAcknowledgement=campaign(game).captureState();
  screenshot("stage23b-personal-journal-unread");
  keyboardAction(renderer,game,"pilot.acknowledge-journal");
  if(!beforeAcknowledgement.equals(campaign(game).captureState()))throw new AssertionError("Journal preview changed live authority");
  keyboardAction(renderer,game,"pilot.physical-confirm");
  var acknowledgedState=campaign(game).captureState();
  if(acknowledgedState.playerJournal().unreadCount()!=0
    ||!committedJournal.entries().equals(acknowledgedState.playerJournal().entries())
    ||!beforeAcknowledgement.playerState().equals(acknowledgedState.playerState())
    ||!beforeAcknowledgement.stage21Runtime().equals(acknowledgedState.stage21Runtime()))
   throw new AssertionError("Journal acknowledgement changed physical state or discarded history");
  screenshot("stage23b-personal-journal-read");
  processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();
  if(!acknowledgedState.equals(campaign(game).captureState()))throw new AssertionError("Journal/read state lost on UI reload");
  System.out.println("Personal committed journal keyboard preview/acknowledgement and exact UI save/load passed");
  moduleCustody(game,renderer,workspace,(java.nio.file.Path)save.get(game));
  repair(game,renderer,workspace,(java.nio.file.Path)save.get(game));
  processor.keyDown(Input.Keys.F7);game.render();selectRow(workspace,game,"glossary-physical");
  screenshot("stage23b-physical-glossary");
  selectRow(workspace,game,"glossary-authority");screenshot("stage23b-authority-glossary");
  if(!acknowledgedState.equals(campaign(game).captureState()))throw new AssertionError("Glossary navigation changed authority");
  System.out.println("Additional reserve purchase, handover, faction foundation, treasury transfers, direct jump and profitable physical UI sale/reload passed; geometry fixtures labelled; profit milli-credits="+(bid-initialQuote));
  if(args.length>0){
   // Optional exact test checkpoint, copied before load. This is command-path engineering evidence,
   // not proof of a newly generated player start, authored contracts, or B18 human acceptance.
   var path=(java.nio.file.Path)save.get(game);
   java.nio.file.Files.copy(java.nio.file.Path.of(args[0]),path,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
   processor.keyDown(Input.Keys.F9);game.render();selectMission(workspace,game);
   keyboardAction(renderer,game,"mission.accept");
   if(campaign(game).coordinator().npcMissions().missions().get(0).status()!=com.spacesim.world.Stage21HNpcMissionState.MissionStatus.OFFERED)throw new AssertionError("Preview mutated live mission");
   com.badlogic.gdx.graphics.Pixmap px=new com.badlogic.gdx.graphics.Pixmap(1280,720,com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);px.getPixels().put(ScreenUtils.getFrameBufferPixels(0,0,1280,720,true)).flip();com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("java.io.tmpdir")+"/stage23b-mission-preview.png"),px);px.dispose();
   keyboardAction(renderer,game,"mission.confirm");
   if(campaign(game).coordinator().npcMissions().missions().get(0).status()!=com.spacesim.world.Stage21HNpcMissionState.MissionStatus.ACCEPTED)throw new AssertionError("Keyboard acceptance failed");
   processor.keyDown(Input.Keys.F8);game.render();processor.keyDown(Input.Keys.F9);game.render();selectMission(workspace,game);
   if(campaign(game).coordinator().npcMissions().missions().get(0).status()!=com.spacesim.world.Stage21HNpcMissionState.MissionStatus.ACCEPTED)throw new AssertionError("Accepted contract not saved");
   var targets=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
   click(targets.stream().filter(h->h.id().equals("mission.cancel")).findFirst().orElseThrow());game.render();
   targets=(java.util.List<com.spacesim.ui.GeneratedWorldCommandUiRenderer.HitTarget>)hf.get(renderer);
   click(targets.stream().filter(h->h.id().equals("mission.confirm")).findFirst().orElseThrow());game.render();
   if(campaign(game).coordinator().npcMissions().missions().get(0).status()!=com.spacesim.world.Stage21HNpcMissionState.MissionStatus.CANCELLED)throw new AssertionError("Mouse cancellation failed");
   java.nio.file.Files.copy(java.nio.file.Path.of(args[0]),path,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
   processor.keyDown(Input.Keys.F9);game.render();selectMission(workspace,game);
   keyboardAction(renderer,game,"mission.reject");keyboardAction(renderer,game,"mission.confirm");
   if(campaign(game).coordinator().npcMissions().missions().get(0).status()!=com.spacesim.world.Stage21HNpcMissionState.MissionStatus.REJECTED)throw new AssertionError("Keyboard rejection failed");
   if(GL11.glGetError()!=0)throw new AssertionError("Mission GL error");
   System.out.println("Personal mission keyboard preview/accept/save/load/reject and mouse preview/cancel passed");
  }
  processor.keyDown(Input.Keys.F4);game.render();held.add(Input.Keys.CONTROL_LEFT);processor.keyDown(Input.Keys.F);held.clear();for(char c:"military".toCharArray())processor.keyTyped(c);processor.keyDown(Input.Keys.ENTER);game.render();
  for(int i=0;i<20;i++){processor.keyDown(Input.Keys.TAB);game.render();}
  processor.keyDown(Input.Keys.ESCAPE);game.render();game.dispose();System.out.println("Graphical probe passed");
 }
}
