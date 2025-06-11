package agency.highlysuspect.installertoolsruntime;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class ITRTPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		Logger log = project.getLogger();
		log.lifecycle("Applying InstallerToolsRuntime to {}", project.getDisplayName());
		project.getConfigurations().maybeCreate("neoforgeInstaller");
		project.afterEvaluate(this::afterEvaluate);
	}
	
	private void afterEvaluate(Project project) {
		if(project.getState().getFailure() != null) return; //whuh
		Logger log = project.getLogger();
		log.info("Beginning InstallerToolsRuntime afterEvaluate for {}", project.getDisplayName());
		
		//TODO make the configuration name itself configurable
		// so u can depend on multiple different neoforge versions at the same time,
		// and put the fruits in separate configurations/sourcesets?
		//Basically I shouldn't hardcode any configuration names, plugin users can pass me a map of "input config" -> "output config containing neoforge"
		Configuration neoInstallerConfig = project.getConfigurations().maybeCreate("neoforgeInstaller");
		ResolvableDependencies neoInstallerDeps = neoInstallerConfig.getIncoming();
		Set<ResolvedArtifactResult> installers = neoInstallerDeps.getArtifacts().getArtifacts();
		if(installers.isEmpty()) log.warn("The '{}' configuration did not have any artifacts", neoInstallerConfig.getName());
		
		for(ResolvedArtifactResult dep : installers) {
			File neoInstaller = dep.getFile();
			log.lifecycle("Got neoforge file: {}", neoInstaller);
			
			File bomFile = getInstallerBomLocation(project, dep);
			log.info("installer bom: {}", bomFile);
			InstallerBom bom;
			
			//If we have a bom, no need to run installer again
			if(bomFile.exists()) {
				try {
					bom = InstallerBom.load(bomFile);
				} catch (Exception e) {
					throw re("Failed to load installer bom", e);
				}
			} else {
				//Run installer
				log.lifecycle("No bom at " + bomFile + " exists, need to run the installer");
				InstallerRunner ir = new InstallerRunner(log)
					.setNeoforgeInstaller(neoInstaller)
					.setLibrariesDir(getLibrariesDir(project, dep))
					.setRootDir(getRootDir(project, dep));
				try {
					bom = ir.runInstaller();
				} catch (Exception e) {
					throw re("Failed to run NeoForge installer", e);
				}
				
				//Save bom for next time
				try {
					bom.save(bomFile);
				} catch (Exception e) {
					throw re("Failed to save installer bom to " + bomFile, e);
				}
			}
			
			File client = bom.client;
			if(!client.exists()) throw new IllegalStateException("Can't find client at " + client + ", bom outdated?");
			
			log.info("Adding client at {} to project", client);
			project.getDependencies().add("implementation", project.files(client));
		}
	}
	
	private Path mkdirs(Path p) {
		try {
			Files.createDirectories(p);
			return p;
		} catch (Exception e) {
			throw new RuntimeException("Failed to mkdirs " + p + ": " + e.getMessage(), e);
		}
	}
	
	private Path itrtGlobalCache(Project project) {
		return project.getGradle().getGradleUserHomeDir().toPath()
			.resolve("caches")
			.resolve("installertoolsruntime");
	}
	
	private File getLibrariesDir(Project project, ResolvedArtifactResult neoDep) {
//		String home = System.getProperty("user.home");
//		if(home == null) throw new RuntimeException("user.home didn't have a value? Not sure where ~/.m2/repository is then?");
//		return mkdirs(Paths.get(home, ".m2", "repository")).toFile();
		//TODO: config option to make it a local location instead (mainly for testing purposes)
		return mkdirs(itrtGlobalCache(project).resolve("libs")).toFile();
	}
	
	private File getRootDir(Project project, ResolvedArtifactResult neoDep) {
		String friendlyName = friendlyName(neoDep);
		
		//install neoforge to a global location
		//TODO: config option to make it a local location instead (mainly for testing purposes)
		return mkdirs(itrtGlobalCache(project).resolve("work").resolve(friendlyName)).toFile();
	}
	
	private File getInstallerBomLocation(Project project, ResolvedArtifactResult neoDep) {
		return new File(getRootDir(project, neoDep), "bom.txt");
	}
	
	public static String friendlyName(ResolvedArtifactResult dep) {
		//if we have a maven coordinate, pick a friendly name based off the maven coordinate,
		//otherwise guess and use the resolved artifact's filename
		String name;
		ComponentIdentifier owner = dep.getVariant().getOwner();
		if(owner instanceof ModuleComponentIdentifier mci) {
			name = mci.getGroup() + "-" + mci.getModule() + "-" + mci.getVersion();
		} else {
			name = dep.getFile().getName(); //shrug
		}
		
		return name.replaceAll("[^A-Za-z0-9-]", "_"); //filename safe
	}
	
	//put the cause's message in my message, to work around gradle not displaying it at all without --stacktrace,
	//because gradle is a WELL DESIGNED PIECE OF SOFTWARE
	public static RuntimeException re(String message, Throwable cause) {
		return new RuntimeException(message + ": " + cause.getMessage(), cause);
	}
}
