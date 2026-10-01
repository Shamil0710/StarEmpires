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
  game.render();processor.keyDown(Input.Keys.SPACE);game.render();
  int[] keys={Input.Keys.F1,Input.Keys.F2,Input.Keys.F3,Input.Keys.F4,Input.Keys.F5,Input.Keys.F6,Input.Keys.F7,Input.Keys.O};
  for(int k:keys){processor.keyDown(k);game.render();processor.keyDown(Input.Keys.DOWN);game.render();com.badlogic.gdx.graphics.Pixmap px=ScreenUtils.getFrameBufferPixmap(0,0,1280,720);com.badlogic.gdx.graphics.PixmapIO.writePNG(Gdx.files.absolute(System.getProperty("java.io.tmpdir")+"/stage23b-screen-"+k+".png"),px);px.dispose();if(GL11.glGetError()!=0)throw new IllegalStateException("GL error tab "+k);System.out.println("Rendered key "+k);}
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
  processor.keyDown(Input.Keys.F4);game.render();held.add(Input.Keys.CONTROL_LEFT);processor.keyDown(Input.Keys.F);held.clear();for(char c:"military".toCharArray())processor.keyTyped(c);processor.keyDown(Input.Keys.ENTER);game.render();
  for(int i=0;i<20;i++){processor.keyDown(Input.Keys.TAB);game.render();}
  processor.keyDown(Input.Keys.ESCAPE);game.render();game.dispose();System.out.println("Graphical probe passed");
 }
}
