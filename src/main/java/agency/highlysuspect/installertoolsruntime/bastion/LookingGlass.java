package agency.highlysuspect.installertoolsruntime.bastion;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The point-of-contact between the Gradle classloader and the installer classloader.
 * Only Java platform classes, and/or other classes which are very careful about this
 * classloading situation, may be used in this class!!!
 */
public class LookingGlass {
	//plugin -> installer
	public File neoforgeInstaller;
	public File librariesDir;
	public File rootDir;
	public BiConsumer<String, Object[]> lifecycle; //logging facade
	public BiConsumer<String, Object[]> info;
	
	//installer -> plugin
	public File clientPatched, serverPatched, clientExtra, serverExtra, nfUniversal;
	public List<File> libs;
	
	//For inter-classloader communication...
	public Map<String, Object> toMap() {
		HashMap<String, Object> map = new HashMap<>();
		map.put("neoforgeInstaller", neoforgeInstaller);
		map.put("librariesDir", librariesDir);
		map.put("rootDir", rootDir);
		map.put("lifecycle", lifecycle);
		map.put("info", info);
		map.put("clientPatched", clientPatched);
		map.put("serverPatched", serverPatched);
		map.put("clientExtra", clientExtra);
		map.put("serverExtra", serverExtra);
		map.put("nfUniversal", nfUniversal);
		map.put("libs", libs);
		
		return map;
	}
	
	@SuppressWarnings("unchecked")
	public static LookingGlass fromMap(Map<String, Object> map) {
		LookingGlass glass = new LookingGlass();
		glass.neoforgeInstaller = (File) map.get("neoforgeInstaller");
		glass.librariesDir = (File) map.get("librariesDir");
		glass.rootDir = (File) map.get("rootDir");
		glass.lifecycle = (BiConsumer<String, Object[]>) map.get("lifecycle");
		glass.info = (BiConsumer<String, Object[]>) map.get("info");
		glass.clientPatched = (File) map.get("clientPatched");
		glass.serverPatched = (File) map.get("serverPatched");
		glass.clientExtra = (File) map.get("clientExtra");
		glass.serverExtra = (File) map.get("serverExtra");
		glass.nfUniversal = (File) map.get("nfUniversal");
		glass.libs = (List<File>) map.get("libs");
		return glass;
	}
	
	public void lifecycle(String s, Object... args) {
		lifecycle.accept(s, args);
	}
	
	public void info(String s, Object... args) {
		info.accept(s, args);
	}
}
