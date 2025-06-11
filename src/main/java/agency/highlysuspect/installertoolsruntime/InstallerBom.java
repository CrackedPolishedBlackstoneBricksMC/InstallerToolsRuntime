package agency.highlysuspect.installertoolsruntime;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * an "installer bill of materials", just a listing of files created by the neoforge installer
 * if the bom exists, i assume it is up-to-date and i don't need to rerun the installer
 */
public class InstallerBom {
	File client, clientExtra, server, serverExtra, universal;
	List<File> libs = new ArrayList<>();
	
	public static InstallerBom load(File file) throws IOException {
		InstallerBom bom = new InstallerBom();
		
		for(String line : Files.readAllLines(file.toPath())) {
			if(line.isEmpty()) continue;
			
			String[] split = line.split(":", 2);
			if(split.length != 2) continue;
			
			File f = new File(split[1]);
			switch(split[0]) {
				case "cli" -> bom.client = f;
				case "clx" -> bom.clientExtra = f;
				case "svr" -> bom.server = f;
				case "svx" -> bom.serverExtra = f;
				case "uni" -> bom.universal = f;
				case "lib", "dep" -> bom.libs.add(f);
				case null, default -> {
					//idgaf bro
				}
			}
		}
		
		return bom;
	}
	
	private void a(StringBuilder bob, String prefix, File f) {
		if(f != null) bob.append(prefix).append(':').append(f.getAbsolutePath()).append('\n');
	}
	
	public String write() {
		StringBuilder bob = new StringBuilder();
		a(bob, "cli", client);
		a(bob, "clx", clientExtra);
		a(bob, "svr", server);
		a(bob, "svx", serverExtra);
		a(bob, "uni", universal);
		for(File dep : libs) a(bob, "lib", dep);
		return bob.toString();
	}
	
	public void save(File file) throws IOException {
		Files.writeString(file.toPath(), write(), StandardCharsets.UTF_8);
	}
}
