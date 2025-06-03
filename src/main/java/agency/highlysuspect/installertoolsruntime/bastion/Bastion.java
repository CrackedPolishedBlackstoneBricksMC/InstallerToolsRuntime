package agency.highlysuspect.installertoolsruntime.bastion;

import net.minecraftforge.installer.DownloadUtils;
import net.minecraftforge.installer.actions.PostProcessors;
import net.minecraftforge.installer.actions.ProgressCallback;
import net.minecraftforge.installer.json.Artifact;
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
		System.out.println("Bastion clinit");
		System.out.println("I was loaded by " + this.getClass().getClassLoader());
		try {
			actionClass = Class.forName("net.minecraftforge.installer.actions.Action");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Bastion loaded without net.minecraftforge.installer.actions.Action", e);
		}
	}
	
	public Map<String, Object> hello(Map<String, Object> map) {
		LookingGlass window = LookingGlass.fromMap(map);
		
		System.out.println("Hello from Bastion");
		System.out.println("I was loaded by: " + this.getClass().getClassLoader());
		System.out.println("Action is: " + actionClass);
		System.out.println("Action was loaded by: " + actionClass.getClassLoader());
		System.out.println("Through the window, I see: " + window.neoforgeInstaller);
		
		InstallV1 installationManifest = Util.loadInstallProfile();
		System.out.println("Found installation manifest for Minecraft " + installationManifest.getMinecraft() + " and NeoForge " + installationManifest.getVersion());
		
		String mc = installationManifest.getMinecraft();
		ProgressCallback monitor = ProgressCallback.TO_STD_OUT;
		
		//find urls of client and server
		System.out.println("Finding client and server urls");
		
		File versionJson = new File(window.rootDir, mc + ".json");
		Version vanilla = Util.getVanillaVersion(monitor, mc, versionJson);
		if(vanilla == null) {
			throw new RuntimeException("Failed to get vanilla version manifest");
		}
		Version.Download client = vanilla.getDownload("client");
		Version.Download server = vanilla.getDownload("server");
		
		//download client
		File versionVanilla = new File(window.rootDir, mc);
		File clientTarget = new File(versionVanilla, mc + ".jar");
		
		System.out.println("Downloading client to " + clientTarget);
		if(!monitor.downloader(client.getUrl())
			.sha(client.getSha1())
			.localPath("minecraft/" + mc + "/client.jar")
			.download(clientTarget)
		) {
			clientTarget.delete();
			throw new RuntimeException("failed to download client (invalid checksum?)");
		}
		
		//download server.
		//this weird path-munging is used in the ServerInstall action. todo is it needed
		Map<String, String> tokens = Map.of(
			"ROOT", window.rootDir.getAbsolutePath(),
			"MINECRAFT_VERSION", mc,
			"LIBRARY_DIR", window.librariesDir.getAbsolutePath()
		);
		File serverTarget = new File(Util.replaceTokens(tokens, installationManifest.getServerJarPath()));
		
		System.out.println("Downloading server to " + serverTarget);
		if(!monitor.downloader(server.getUrl())
			.sha(server.getSha1())
			.localPath("minecraft/" + mc + "/server.jar")
			.download(serverTarget)
		) {
			serverTarget.delete();
			throw new RuntimeException("failed to download server (invalid checksum?)");
		}
		
		//create postprocessors
		System.out.println("Creating postprocessors");
		PostProcessors clientPostProcessors = new PostProcessors(installationManifest, true, monitor);
		PostProcessors serverPostProcessors = new PostProcessors(installationManifest, false, monitor);
		
		//fetch all the required libraries
		System.out.println("Fetching libraries");
		//see Action#downloadLibraries
		Set<Version.Library> libraries = new LinkedHashSet<>();
		libraries.addAll(Arrays.asList(vanilla.getLibraries()));
		libraries.addAll(Arrays.asList(clientPostProcessors.getLibraries()));
		libraries.addAll(Arrays.asList(serverPostProcessors.getLibraries()));
		List<Artifact> grabbed = new ArrayList<>();
		List<File> additionalLibDirs = List.of(window.librariesDir);
		
		for(Version.Library lib : libraries) {
			File resolved = lib.getName().getLocalPath(window.librariesDir);
			if(resolved.exists()) {
				System.out.println("Already downloaded " + lib.getName());
			} else {
				System.out.println("Downloading " + lib.getName());
				DownloadUtils.downloadLibrary(monitor, lib, window.librariesDir, s -> true, grabbed, additionalLibDirs);
			}
		}
		
		//TODO: processors run even if the file already exists, since they weren't intended to be used by gradle like this
		// but afaik there isn't a reliable way to find the PATCHED path before calling .process, since .process
		// is what actually performs the variable substitutions. One way to fix this would be simply putting caching somewhere
		// else... like, simply copy the output into the project dir, and only run the installer if that output doesn't exist.
		// Which is probably something i should do anyway. The other approach (what forgewrapper does) is simply to copy the
		// code related to variable substitution
		
		System.out.println("Running client processors");
		clientPostProcessors.process(window.librariesDir, clientTarget, window.rootDir, window.neoforgeInstaller);
		System.out.println("Running server processors");
		serverPostProcessors.process(window.librariesDir, serverTarget, window.rootDir, window.neoforgeInstaller);
		
		//TODO: a more reliable way to find the patched jar? lol.
		Map<String, String> clientData = getData(clientPostProcessors);
		Map<String, String> serverData = getData(serverPostProcessors);
		window.clientPatched = new File(clientData.get("PATCHED"));
		window.serverPatched = new File(serverData.get("PATCHED"));
		window.clientExtra = new File(clientData.get("MC_EXTRA"));
		window.serverExtra = new File(serverData.get("MC_EXTRA"));
		
		//yeah this is grody
		//i feel like parsing the JVM arguments provided by the installer is somehow a *less* bad idea
		for(Version.Library lib : libraries) {
			Artifact artifact = lib.getName();
			if("net.neoforged".equals(artifact.getDomain()) && "universal".equals(getClassifier(artifact))) {
				window.nfUniversal = lib.getName().getLocalPath(window.librariesDir);
			}
		}
		
		return window.toMap();
	}
	
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
