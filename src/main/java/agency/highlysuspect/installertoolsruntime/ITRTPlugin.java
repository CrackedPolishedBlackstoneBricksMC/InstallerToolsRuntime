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
			File universal = bom.universal;
			if(!client.exists()) throw new IllegalStateException("Can't find client at " + client);
			if(!universal.exists()) throw new IllegalStateException("Can't find universal at " + universal);
			
			//TODO lazy 2am coding
			log.info("Adding client at {} to project", client);
			project.getDependencies().add("implementation", project.files(client));
			log.info("Adding universal at {} to project", bom.universal);
			project.getDependencies().add("implementation", project.files(universal));
			for(File lib : bom.libs) {
				log.info("Adding lib at {} to project", lib);
				project.getDependencies().add("implementation", project.files(lib));
			}
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
	
	//a global cache in your ~/.gradle/caches which every minecraft plugin seems to litter
	//always good to share work between projects by using a global cache imo
	//todo i might need a lock file? lol
	private Path itrtGlobalCache(Project project) {
		return project.getGradle().getGradleUserHomeDir().toPath()
			.resolve("caches")
			.resolve("installertoolsruntime");
	}
	
	//the neoforge installer downloads libraries to a maven-style file tree, and also uses it
	//for scratch space occasionally if a library already exists in the libs dir, the installer
	//will avoid downloading it again, but this isn't true for files which are created during
	//the postprocessors phase
	private File getLibrariesDir(Project project, ResolvedArtifactResult neoDep) {
		//TODO: config option to make it a local location instead (mainly for testing purposes)
		return mkdirs(itrtGlobalCache(project).resolve("libs")).toFile();
	}
	
	//directory that the installer thinks it's installing the game into
	//normally this would be like, your vanilla launcher .minecraft, or the server dir
	//also used as a little bit of scratch space, mostly by the server installer, and also
	//by me since i need somewhere to stick the vanilla minecraft jar
	//TODO i should probably put the vanilla jar in the libraries folder actually
	private File getRootDir(Project project, ResolvedArtifactResult neoDep) {
		String friendlyName = friendlyName(neoDep);
		
		//TODO: config option to make it a local location instead (mainly for testing purposes)
		return mkdirs(itrtGlobalCache(project).resolve("work").resolve(friendlyName)).toFile();
	}
	
	//location of the "installer bill of materials" for a given installer artifact
	//this item serves as proof that the installer completed successfully, and also lists out
	//all the minecraft artifacts and libraries prepared by the installer
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
