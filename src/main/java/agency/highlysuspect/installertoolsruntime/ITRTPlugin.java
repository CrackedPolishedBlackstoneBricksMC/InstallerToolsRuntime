package agency.highlysuspect.installertoolsruntime;

import agency.highlysuspect.installertoolsruntime.bastion.LookingGlass;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

public class ITRTPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		Logger log = project.getLogger();
		log.lifecycle("Applying InstallerToolsRuntime to {}", project.getDisplayName());
		
		//TODO make the configuration name itself configurable
		// so u can depend on multiple different neoforge versions at the same time,
		// and put the fruits in separate configurations/sourcesets?
		//Basically I shouldn't hardcode any configuration names, plugin users can pass me a map of "input config" -> "output config containing neoforge"
		project.getConfigurations().maybeCreate("neoforgeInstaller");
		
		project.afterEvaluate(this::afterEvaluate);
	}
	
	private void afterEvaluate(Project project) {
		if(project.getState().getFailure() != null) return; //whuh
		Logger log = project.getLogger();
		
		ResolvableDependencies neoInstallerConfig = project.getConfigurations().getByName("neoforgeInstaller").getIncoming();
		Set<ResolvedArtifactResult> installers = neoInstallerConfig.getArtifacts().getArtifacts();
		if(installers.isEmpty()) log.warn("The 'neoforgeInstaller' configuration did not have any artifacts");
		
		for(ResolvedArtifactResult dep : installers) {
			File neoforgeInstaller = dep.getFile();
			log.lifecycle("Got neoforge file: {}", neoforgeInstaller);
			
			//data to pass into the classloader containing the neoforge installer
			LookingGlass glass = new LookingGlass();
			glass.neoforgeInstaller = neoforgeInstaller;
			glass.librariesDir = getLibrariesDir();
			glass.rootDir = getRootDir(project, dep.getId().toString());
			glass.lifecycle = log::lifecycle;
			glass.info = log::info;
			
			//serialize it to types which can cross the classloader membrane
			Map<String, Object> windowSafe = glass.toMap();
			
			try(URLClassLoader cl = makeClassloader(log, neoforgeInstaller)) {
				//create the bastion inside this classloader and call hello()
				Class<?> bastionClass = cl.loadClass("agency.highlysuspect.installertoolsruntime.bastion.Bastion");
				Object bastion = bastionClass.getConstructor().newInstance();
				@SuppressWarnings("unchecked")
				Map<String, Object> result = (Map<String, Object>) bastionClass.getDeclaredMethod("hello", Map.class).invoke(bastion, windowSafe);
				glass = LookingGlass.fromMap(result);
			} catch (Exception e) {
				//put the cause exception's message in my message, to work around gradle not displaying it at all without --stacktrace,
				//because gradle is a WELL DESIGNED PIECE OF SOFTWARE
				throw new RuntimeException("Failed to call the Neoforge " + dep.getId().getDisplayName() + " installer: " + e.getMessage(), e);
			}
			
			log.lifecycle("peering through the window i see...");
			log.lifecycle("Got Neoforge Universal: " + glass.nfUniversal);
			log.lifecycle("Got Client Patched: " + glass.clientPatched);
			log.lifecycle("Got Server Patched: " + glass.serverPatched);
			log.lifecycle("Got Client Extra: " + glass.clientExtra);
			log.lifecycle("Got Server Extra: " + glass.serverExtra);
		}
	}
	
	//TODO: make this configurable somehow?
	private File getLibrariesDir() {
		String home = System.getProperty("user.home");
		if(home == null) throw new RuntimeException("user.home didn't have a value? Not sure where ~/.m2/repository is then");
		
		//IF m2 IS SO GOOD HOW COME THERE's NO
		Path m2Repository = Paths.get(home, ".m3", "repository");
		try {
			Files.createDirectories(m2Repository);
		} catch (Exception e) {
			throw new RuntimeException("Failed to mkdirs libraries dir " + m2Repository, e);
		}
		
		return m2Repository.toFile();
	}
	
	private File getRootDir(Project project, String neoforgeDisplayName) {
		//TODO what ends up in here exactly...
		
		File buildDir = project.getLayout().getBuildDirectory().getAsFile().get();
		Path rootDir = buildDir.toPath()
			.resolve("itrt-work")
			.resolve(neoforgeDisplayName.replaceAll("[^A-Za-z0-9]", "_"));
		try {
			Files.createDirectories(rootDir);
		} catch (Exception e) {
			throw new RuntimeException("failed to mkdirs " + rootDir, e);
		}
		return rootDir.toFile();
	}
	
	//build a classloader containing me and the neoforge installer
	private URLClassLoader makeClassloader(Logger log, File neoforgeInstaller) {
		URL myUrl, neoforgeUrl;
		
		log.info("Trying to find my url");
		try {
			myUrl = this.getClass().getProtectionDomain().getCodeSource().getLocation();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to find my URL", ex);
		}
		log.info("Got my url: {}", myUrl);
		
		log.info("Trying to find neoforge installer url");
		try {
			neoforgeUrl = neoforgeInstaller.toURI().toURL();
		} catch (Exception e) {
			throw new RuntimeException("Failed to find NeoForge url", e);
		}
		log.info("Got neoforge url: {}", myUrl);
		
		//Use the platform classloader as the parent, so we get a blank slate of classes.
		//Otherwise it'll just delegate back to this classloader and we can't load Bastion inside it,
		//since we'll instead get a copy of Bastion from *this* classloader, and it can't see the installer
		return new URLClassLoader(new URL[]{myUrl, neoforgeUrl}, ClassLoader.getPlatformClassLoader());
	}
}
