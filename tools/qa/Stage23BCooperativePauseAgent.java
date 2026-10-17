import com.sun.tools.attach.VirtualMachine;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stops only the standalone QA runner at its next normal tick boundary and native-save finally. */
public final class Stage23BCooperativePauseAgent {
 public static void agentmain(String replacement, Instrumentation instrumentation)throws Exception{
  Class<?> runner=null;
  for(Class<?> type:instrumentation.getAllLoadedClasses())if(type.getName().equals("Stage23BOrdinaryMiningJourney")){
   if(runner!=null)throw new IllegalStateException("Ambiguous QA runner");runner=type;
  }
  if(runner==null)throw new IllegalStateException("Not the standalone mining QA process");
  if(replacement.equals("existing-hook")){
   System.setProperty("stage23b.qa.cooperativeStop","true");
   return;
  }
  Path file=Path.of(replacement).toAbsolutePath().normalize();
  Path root=Path.of("C:/Users/Shalim/IdeaProjects/StarEmpires/target/qa").toAbsolutePath().normalize();
  if(!file.startsWith(root)||!file.getFileName().toString().equals("Stage23BOrdinaryMiningJourney.class"))throw new IllegalArgumentException("Replacement must be the reviewed workspace QA class");
  instrumentation.redefineClasses(new ClassDefinition(runner,Files.readAllBytes(file)));
  // Existing catch/finally writes the exact world state. No simulation object is touched here.
  System.setProperty("stage23b.qa.cooperativeStop","true");
 }
 public static void main(String[] args)throws Exception{
  if(args.length!=3)throw new IllegalArgumentException("PID, agent jar and reviewed QA class required");
  var vm=VirtualMachine.attach(args[0]);
  try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2].equals("existing-hook")?args[2]:Path.of(args[2]).toAbsolutePath().toString());}
  finally{vm.detach();}
  System.out.println("Cooperative standalone QA save requested; inspect the runner completion before resuming.");
 }
}
