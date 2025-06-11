import org.gradle.testkit.runner.GradleRunner;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

public class ToyProject implements AutoCloseable {
	protected final Path testWorkdirs = Path.of("build", "test-workdirs");
	protected final Path projectDir = testWorkdirs.resolve(slug());

	protected final Path gradleProperties = projectDir.resolve("gradle.properties");
	protected final Path buildGradle = projectDir.resolve("build.gradle");
	protected final Path settingsGradle = projectDir.resolve("settings.gradle");
	{
		try {
			Files.createDirectories(projectDir);
			
			String prefix = """
				plugins {
					id "agency.highlysuspect.installertoolsruntime"
				}
			
				repositories {
					mavenCentral()
					maven {
						url "https://maven.neoforged.net/releases"
					}
				}
			""".stripIndent();
			Files.writeString(buildGradle, prefix, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("couldn't make test workdirs", e);
		}
	}
	
	public GradleRunner makeGradleRunner() {
		System.out.println("Building a project inside " + projectDir);
		return GradleRunner.create()
			.withPluginClasspath()
			.withProjectDir(projectDir.toFile())
			.withArguments("--info", "--stacktrace")
			.forwardOutput();
	}
	
	private String slug() {
		return UUID.randomUUID().toString().replace("-", "").substring(16);
	}
	
	@Override
	public void close() throws Exception {
		if(true) return; //No actually I want to look at the files
		
		Files.walkFileTree(projectDir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(@NotNull Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}
			
			@Override
			public FileVisitResult postVisitDirectory(@NotNull Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
