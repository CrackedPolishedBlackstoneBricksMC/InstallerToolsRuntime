package agency.highlysuspect.installertoolsruntime;

import agency.highlysuspect.installertoolsruntime.bastion.LookingGlass;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Map;

public class InstallerRunner {
	
	public InstallerRunner(Project project) {
		this(project.getLogger());
	}
	
	public InstallerRunner(Logger log) {
		this.log = log;
	}
	
	public File rootDir, librariesDir, neoforgeInstaller;
	protected Logger log;
	
	public InstallerRunner setRootDir(File rootDir) {
		this.rootDir = rootDir;
		return this;
	}
	
	public InstallerRunner setLibrariesDir(File librariesDir) {
		this.librariesDir = librariesDir;
		return this;
	}
	
	public InstallerRunner setNeoforgeInstaller(File neoforgeInstaller) {
		this.neoforgeInstaller = neoforgeInstaller;
		return this;
	}
	
	public InstallerBom runInstaller() throws Exception {
		//data to pass into the classloader containing the neoforge installer
		LookingGlass glass = new LookingGlass();
		glass.neoforgeInstaller = neoforgeInstaller;
		glass.librariesDir = librariesDir;
		glass.rootDir = rootDir;
		glass.lifecycle = log::lifecycle;
		glass.info = log::info;
		
		//serialize it to types which can cross the classloader membrane
		Map<String, Object> windowSafe = glass.toMap();
		
		try(URLClassLoader cl = makeClassloader()) {
			//create the bastion inside this classloader and call hello()
			Class<?> bastionClass = cl.loadClass("agency.highlysuspect.installertoolsruntime.bastion.Bastion");
			Object bastion = bastionClass.getConstructor().newInstance();
			@SuppressWarnings("unchecked")
			Map<String, Object> result = (Map<String, Object>) bastionClass.getDeclaredMethod("hello", Map.class).invoke(bastion, windowSafe);
			glass = LookingGlass.fromMap(result);
		} catch (Exception e) {
			throw ITRTPlugin.re("Failed to call the Neoforge installer at " + neoforgeInstaller, e);
		}
		
		//todo logspam
		log.lifecycle("peering through the window i see...");
		log.lifecycle("Got Neoforge Universal: " + glass.nfUniversal);
		log.lifecycle("Got Client Patched: " + glass.clientPatched);
		log.lifecycle("Got Server Patched: " + glass.serverPatched);
		log.lifecycle("Got Client Extra: " + glass.clientExtra);
		log.lifecycle("Got Server Extra: " + glass.serverExtra);
		for(File lib : glass.libs) log.lifecycle("Got Library: " + lib);
		
		//produce bom
		InstallerBom bom = new InstallerBom();
		bom.client = glass.clientPatched;
		bom.clientExtra = glass.clientExtra;
		bom.server = glass.serverPatched;
		bom.serverExtra = glass.serverExtra;
		bom.universal = glass.nfUniversal;
		bom.libs = new ArrayList<>(glass.libs);
		
		return bom;
	}
	
	//build a classloader containing me and the neoforge installer
	private URLClassLoader makeClassloader() {
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
