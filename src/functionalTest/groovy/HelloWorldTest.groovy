import spock.lang.AutoCleanup
import spock.lang.Specification

class HelloWorldTest extends Specification {
	@AutoCleanup ToyProject toy
	
	def setup() {
		toy = new ToyProject()
	}
	
	def "no artifacts"() {
		when:
		def result = toy.makeGradleRunner().build()
		
		then:
		result.output.contains("Applying InstallerToolsRuntime to")
		result.output.contains("did not have any artifacts")
	}
	
	def "installer artifact"() {
		given:
		toy.buildGradle << """
			dependencies {
				neoforgeInstaller "net.neoforged:neoforge:21.5.75:installer"
			}
		"""
		
		when:
		def result = toy.makeGradleRunner().build()
		
		then:
		result.output.contains("Applying InstallerToolsRuntime to")
		result.output.contains("Found installation manifest for Minecraft 1.21.5")
	}
}
