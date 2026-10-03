import org.lwjgl.system.*;
import org.lwjgl.system.linux.DynamicLinkLoader;
import org.lwjgl.opengl.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.utils.*;
import java.lang.reflect.*;
import java.util.*;
/** Engineering smoke on an EGL pbuffer. Uses the real renderer, not B18 human acceptance. */
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
 static void selectRow(com.spacesim.ui.ProductionUiWorkspace workspace, com.spacesim.GeneratedWorldCommandGame game, String id){
  for(int i=0;i<100;i++)processor.keyDown(Input.Keys.UP);game.render();
  int moves=0;while(!workspace.view().selection().stableId().equals(id)){processor.keyDown(Input.Keys.DOWN);game.render();if(++moves>200)throw new AssertionError("Row unreachable "+id);}
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
