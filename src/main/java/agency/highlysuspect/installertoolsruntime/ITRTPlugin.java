package agency.highlysuspect.installertoolsruntime;

import agency.highlysuspect.installertoolsruntime.bastion.LookingGlass;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

public class ITRTPlugin implements Plugin<Project> {
	@Override
	public void apply(Project project) {
		project.getLogger().lifecycle("Applying InstallerToolsRuntime to {}", project.getDisplayName());
		
		//TODO make the configuration name itself configurable
		// so u can depend on multiple different neoforge versions at the same time,
		// and put the fruits in separate configurations/sourcesets?
		//Basically I shouldn't hardcode any configuration names, plugin users can pass me a map of "input config" -> "output config containing neoforge"
		project.getConfigurations().maybeCreate("neoforgeInstaller");
		
		project.afterEvaluate(this::afterEvaluate);
	}
	
	private void afterEvaluate(Project project) {
		if(project.getState().getFailure() != null) return; //whuh
		
		ResolvableDependencies neoInstallerConfig = project.getConfigurations().getByName("neoforgeInstaller").getIncoming();
		Set<ResolvedArtifactResult> installers = neoInstallerConfig.getArtifacts().getArtifacts();
		if(installers.isEmpty()) {
			project.getLogger().warn("The 'neoforgeInstaller' configuration did not have any artifacts");
		}
		for(ResolvedArtifactResult dep : installers) {
			File neoforgeInstaller = dep.getFile();
			project.getLogger().lifecycle("Got neoforge file: {}", neoforgeInstaller);
			
			//data to pass into the classloader containing the neoforge installer
			LookingGlass window = new LookingGlass();
			window.neoforgeInstaller = neoforgeInstaller;
			
			//use ~/.m2/repository as the libraries dir
			String home = System.getProperty("user.home");
			if(home == null) throw new RuntimeException("user.home didn't have a value");
			window.librariesDir = Paths.get(home, ".m2", "repository").toFile();
			
			//for the "root dir"... eehh??
			File buildDir = project.getLayout().getBuildDirectory().getAsFile().get();
			window.rootDir = buildDir.toPath()
				.resolve("itrt-work")
				.resolve(dep.getId().getDisplayName().replaceAll("[^A-Za-z0-9]", "_"))
				.toFile();
			try {
				Files.createDirectories(window.rootDir.toPath());
			} catch (Exception e) {
				throw new RuntimeException("failed to mkdirs " + window.rootDir, e);
			}
			
			//serialize it to types which can cross the classloader membrane
			Map<String, Object> windowSafe = window.toMap();
			
			//prepare to build a classloader containing me and the neoforge installer
			URL myUrl, neoforgeUrl;
			try {
				project.getLogger().info("Trying to find my url");
				myUrl = this.getClass().getProtectionDomain().getCodeSource().getLocation();
				project.getLogger().info("Got my url: {}", myUrl);
				
				project.getLogger().info("Trying to find neoforge installer url");
				neoforgeUrl = neoforgeInstaller.toURI().toURL();
				project.getLogger().info("Got neoforge url: {}", myUrl);
			} catch (Exception e) {
				throw new RuntimeException("Couldn't get url", e);
			}
			
			//Use the platform classloader as the parent, so we get a blank slate of classes.
			//Otherwise it'll just delegate back to this classloader and we can't load Bastion inside it,
			//since we'll instead get a copy of Bastion from *this* classloader. and it can't see the installer.
			try(URLClassLoader urlClassLoader = new URLClassLoader(new URL[] {myUrl, neoforgeUrl}, ClassLoader.getPlatformClassLoader())) {
				//create the bastion inside this classloader and call hello()
				Class<?> bastionClass = urlClassLoader.loadClass("agency.highlysuspect.installertoolsruntime.bastion.Bastion");
				Object bastion = bastionClass.getConstructor().newInstance();
				@SuppressWarnings("unchecked")
				Map<String, Object> result = (Map<String, Object>) bastionClass.getDeclaredMethod("hello", Map.class).invoke(bastion, windowSafe);
				window = LookingGlass.fromMap(result);
			} catch (Exception e) {
				throw new RuntimeException("Installer failed", e);
			}
			
			project.getLogger().lifecycle("peering through the window i see...");
			project.getLogger().lifecycle("Got Neoforge Universal: " + window.nfUniversal);
			project.getLogger().lifecycle("Got Client Patched: " + window.clientPatched);
			project.getLogger().lifecycle("Got Server Patched: " + window.serverPatched);
			project.getLogger().lifecycle("Got Client Extra: " + window.clientExtra);
			project.getLogger().lifecycle("Got Server Extra: " + window.serverExtra);
		}
	}
}
