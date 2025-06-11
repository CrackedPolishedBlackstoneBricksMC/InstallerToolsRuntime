package agency.highlysuspect.installertoolsruntime.bastion;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.actions.PostProcessors;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Artifact;
import net.minecraftforge.installer.json.Install;
import net.minecraftforge.installer.json.InstallV1;
import net.minecraftforge.installer.json.Util;
import net.minecraftforge.installer.json.Version;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Bastion {
	Class<?> actionClass;
	
	{
		System.out.println("Bastion clinit, I was loaded by " + this.getClass().getClassLoader());
		try {
			actionClass = Class.forName("net.minecraftforge.installer.actions.Action");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Bastion loaded without net.minecraftforge.installer.actions.Action", e);
		}
	}
	
	@SuppressWarnings("unused")
	public Map<String, Object> hello(Map<String, Object> map) {
		LookingGlass glass = LookingGlass.fromMap(map);
		glass.lifecycle("Hello from Bastion");
		
		//ok we're on the classloader with the installer, start interacting with it
		ProgressCallback monitor = new GradleLogProgressCallback(glass);
		InstallV1 installManifest = getInstallManifest();
		String mc = installManifest.getMinecraft();
		monitor.message("Found install manifest for Minecraft " + mc + " and NeoForge " + installManifest.getVersion());
		
		monitor.setCurrentStep("Finding vanilla manifest");
		Version vanilla = getVanillaManifest(monitor, installManifest, glass.rootDir);
		
		//find where to put the client and server - use the root dir as scratch space?
		//this weird path-munging is used in the ServerInstall action. todo is it needed
//		File clientTarget = new File(versionVanilla, mc + ".jar");
//		Map<String, String> tokens = Map.of(
//			"ROOT", glass.rootDir.getAbsolutePath(),
//			"MINECRAFT_VERSION", mc,
//			"LIBRARY_DIR", glass.librariesDir.getAbsolutePath()
//		);
//		File serverTarget = new File(Util.replaceTokens(tokens, installManifest.getServerJarPath()));
		File clientTarget = new File(glass.rootDir, mc + ".client.jar");
		File serverTarget = new File(glass.rootDir, mc + ".server.jar");
		
		monitor.setCurrentStep("Downloading " + mc + " client to " + clientTarget);
		download(monitor, installManifest, vanilla, true, clientTarget);
		monitor.setCurrentStep("Downloading " + mc + " server to " + serverTarget);
		download(monitor, installManifest, vanilla, false, serverTarget);
		
		//postprocessor creation
		monitor.setCurrentStep("Creating postprocessors");
		PostProcessors clientPostProcessors = new PostProcessors(installManifest, true, monitor);
		PostProcessors serverPostProcessors = new PostProcessors(installManifest, false, monitor);
		
		//libs
		monitor.setCurrentStep("Downloading libraries");
		Set<Version.Library> resolvedLibraries = fetchLibraries(monitor, glass.librariesDir, vanilla, clientPostProcessors, serverPostProcessors);
		
		//running those processors
		monitor.setCurrentStep("Running client processors");
		clientPostProcessors.process(glass.librariesDir, clientTarget, glass.rootDir, glass.neoforgeInstaller);
		monitor.setCurrentStep("Running server processors");
		serverPostProcessors.process(glass.librariesDir, serverTarget, glass.rootDir, glass.neoforgeInstaller);
		
		monitor.setCurrentStep("Finishing up");
		
		//TODO: a more reliable way to find the patched jar? lol.
		Map<String, String> clientData = getData(clientPostProcessors);
		Map<String, String> serverData = getData(serverPostProcessors);
		glass.clientPatched = new File(clientData.get("PATCHED"));
		glass.serverPatched = new File(serverData.get("PATCHED"));
		glass.clientExtra = new File(clientData.get("MC_EXTRA"));
		glass.serverExtra = new File(serverData.get("MC_EXTRA"));
		
		//yeah this is grody
		//i feel like parsing the JVM arguments provided by the installer is somehow a *less* bad idea
		//(later) no it's not, there's just a bunch of wrapper jars
		for(Version.Library lib : resolvedLibraries) {
			Artifact artifact = lib.getName();
			if("net.neoforged".equals(artifact.getDomain()) && "universal".equals(getClassifier(artifact))) {
				glass.nfUniversal = lib.getName().getLocalPath(glass.librariesDir);
			}
		}
		
		return glass.toMap();
	}
	
	public InstallV1 getInstallManifest() {
		try {
			return Util.loadInstallProfile();
		} catch (Exception e) {
			throw new RuntimeException("Failed to load install manifest from the installer", e);
		}
	}
	
	public Version getVanillaManifest(ProgressCallback monitor, Install installManifest, File rootDir) {
		String mc = installManifest.getMinecraft();
		File versionJson = new File(rootDir, mc + ".json");
		Version vanilla = Util.getVanillaVersion(monitor, mc, versionJson);
		if(vanilla == null) throw new RuntimeException("Failed to get vanilla version manifest");
		return vanilla;
	}
	
	public void download(ProgressCallback monitor, Install installManifest, Version vanilla, boolean client, File target) {
		String cs = client ? "client" : "server";
		Version.Download dl = vanilla.getDownload(cs);
		//local path is used by installer fatjars to pull from the fatjar instead of making a download
		String localPath = "minecraft/" + installManifest.getVersion() + "/" + cs + ".jar";
		
		if(!monitor.downloader(dl.getUrl()).sha(dl.getSha1()).localPath(localPath).download(target)) {
			//todo, boy error reporting here needs to be better
			target.delete();
			throw new RuntimeException("failed to download " + cs + " (invalid checksum?)");
		}
	}
	
	public Set<Version.Library> fetchLibraries(ProgressCallback monitor, File librariesDir, Version vanilla, PostProcessors clientPostProcessors, PostProcessors serverPostProcessors) {
		//see Action#downloadLibraries
		Set<Version.Library> libraries = new LinkedHashSet<>();
		libraries.addAll(Arrays.asList(vanilla.getLibraries()));
		libraries.addAll(Arrays.asList(clientPostProcessors.getLibraries()));
		libraries.addAll(Arrays.asList(serverPostProcessors.getLibraries()));
		
		//args for the downloader
		List<Artifact> grabbed = new ArrayList<>();
		List<File> additionalLibDirs = List.of(librariesDir);
		
		for(Version.Library lib : libraries) {
			File resolved = lib.getName().getLocalPath(librariesDir);
			if(resolved.exists()) {
				monitor.message("Already downloaded library " + lib.getName());
			} else {
				//monitor.setCurrentStep("Downloading library " + lib.getName()); //DownloadUtils already loads something like this
				DownloadUtils.downloadLibrary(monitor, lib, librariesDir, s -> true, grabbed, additionalLibDirs);
			}
		}
		
		return libraries;
	}
	
	//we have access wideners at home
	@SuppressWarnings("unchecked")
	public Map<String, String> getData(PostProcessors pp) {
		try {
			Field f = PostProcessors.class.getDeclaredField("data");
			f.setAccessible(true);
			return (Map<String, String>) f.get(pp);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public @Nullable String getClassifier(Artifact art) {
		try {
			Field f = Artifact.class.getDeclaredField("classifier");
			f.setAccessible(true);
			return (String) f.get(art);
		} catch (Exception e) {
			throw new RuntimeException("Failed to get classifier", e);
		}
	}
}
