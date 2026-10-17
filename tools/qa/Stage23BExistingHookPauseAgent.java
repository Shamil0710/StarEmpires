import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;

/** Requests the existing standalone QA tick-boundary save without redefining any class. */
public final class Stage23BExistingHookPauseAgent {
 public static void agentmain(String ignored, Instrumentation instrumentation) {
  boolean found=false;
  for(Class<?> type:instrumentation.getAllLoadedClasses())
   if(type.getName().equals("Stage23BOrdinaryMiningJourney"))found=true;
  if(!found)throw new IllegalStateException("Not the standalone mining QA process");
  System.setProperty("stage23b.qa.cooperativeStop","true");
 }
 public static void main(String[] args)throws Exception {
  if(args.length!=2)throw new IllegalArgumentException("PID and agent jar required");
  var vm=VirtualMachine.attach(args[0]);
  try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString());}
  finally{vm.detach();}
  System.out.println("Existing QA save hook requested.");
 }
}
