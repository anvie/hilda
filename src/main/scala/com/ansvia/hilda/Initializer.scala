package com.ansvia.hilda
import java.text.MessageFormat
import java.io.File
import java.io.FileWriter
import org.slf4j.LoggerFactory
import org.apache.commons.io.FileUtils

object Initializer {
	
	private val log = LoggerFactory.getLogger(getClass)
	private val DEFAULT_INSTALL_PREFIX = "/usr/local"
	private val DEFAULT_LIB_DIR = DEFAULT_INSTALL_PREFIX + "/lib"
	private val DEFAULT_BIN_DIR = DEFAULT_INSTALL_PREFIX + "/bin"
	
	class Module(name:String, depends:String, repo:String, branch:String, workDir:String) {
		def toXML():String = {
            val tmpl = MessageFormat.format("""
		<module name="{0}" depends="{1}">
			<source type="git">{2}#{3}</source>
			<work dir="{4}" />
			<!-- Currently support `pre-update` and `post-update` hooks -->
			<!-- Just uncomment this block to create hook
			<hook when="post-update">
				#!/bin/sh
				echo "making {0}..."
				echo "make all"
				cd {4} &amp;&amp; make
			</hook>
			-->
		</module>
""", name, depends, repo, branch, workDir)
			tmpl
		}
	}
		
	def genModules(modules:Set[Module]):String = {
        val tmpl = MessageFormat.format("""
<hilda>
	<modules>
		{0}
	</modules>
</hilda>
""", modules.map(z => z.toXML()).reduceLeft(_ + _))
		tmpl
	}
	
	def CoverError(fragileOp: => Unit) {
	  try { fragileOp }catch{
	    case e:Exception =>
            System.err.println("Cannot install hilda. " + e.getMessage)
	  }
	}
	
	def ensureHildaHome(){
		val home = new File(Hilda.getHildaHome)
		
		if(!home.exists()){
			home.mkdir()
		}
	}
	
	def HildaHome(block: () => Unit) {
		if(!(new File(Hilda.getHildaHome)).exists()){
			log.error("Hilda home does not exitss, please run `install` first")
		}else{
			try{
				block()
			}catch{
				case e:Exception =>
					log.error("Cannot install Hilda.")
					e.printStackTrace()
				case e =>
					log.error("Cannot install Hilda. " + e.toString)
			}
			
		}
	}
	
	def installScript(quiet:Boolean=false):Int = {
		var rv = Error.UNKNOWN_ERROR
		
		CoverError {
		  
			val curJarFile = new File(getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
			
			log.debug("Copying `" + curJarFile + "` into `" + DEFAULT_LIB_DIR + "`")
			
			val libDir = new File(DEFAULT_LIB_DIR)
			
			if(!libDir.exists()){
				libDir.mkdirs()
			}
			
			val outJarFile = new File(libDir.getAbsolutePath + "/hilda-" + Hilda.VERSION + ".jar")
			
			// just skip it when target is already in /usr/local/lib
			if(curJarFile.getAbsolutePath.compare(outJarFile.getAbsolutePath) != 0){
			   FileUtils.copyFile(curJarFile, outJarFile)
			}

			
			val tmpl = """
#!/bin/sh
java -Xmx512M -jar %s/hilda-%s.jar $*
""".format(DEFAULT_LIB_DIR, Hilda.VERSION)
			
			val binDir = new File(DEFAULT_BIN_DIR)
			
			if(!binDir.exists()){
				binDir.mkdirs()
			}
			val hildaBin = binDir.getAbsolutePath + "/hilda"
			
			val f = new FileWriter(hildaBin)
			f.write(tmpl)
			f.close()
			
			Runtime.getRuntime.exec(Array("chmod", "+x", hildaBin))
			
			println("Hilda executable placed on `" + binDir.getAbsolutePath + "`")
			println("Making sure `" + binDir.getAbsolutePath + "` is in your PATH environment variable.")
			
			rv = Error.SUCCESS
			
		}
		
		rv
	}
	
	def uninstallScript(): Int = {
		var rv = Error.UNKNOWN_ERROR
		try {
			// remove wrapper
			val hildaBinWrapper = new File(DEFAULT_BIN_DIR + "/hilda")
			if(hildaBinWrapper.exists()){
				hildaBinWrapper.delete()
			}
			
			// remove hilda home
			val dir = new File(Hilda.getHildaHome)
			if(dir.exists()){
				val cli = new jline.ConsoleReader()
				cli.readLine("Remove also `" + Hilda.getHildaHome + "` ? (this action cannot be undone!) [y/N]:").toLowerCase match {
					case "y" => 
						FileUtils.deleteDirectory(dir)
				}
			}
			
			// remove hilda bin
			val hildaBin = new File(DEFAULT_LIB_DIR + "/hilda-" + Hilda.VERSION + ".jar")
			if(hildaBin.exists()){
			  hildaBin.delete();
			}
			
			
			rv = Error.SUCCESS
		} catch {
		  	case e:Exception =>
                  log.error("Cannot install Hilda. " + e.toString)
		}
		rv
	}

	def generateModuleXml() {
		println("")
		println("========| Module wizard")
		var modules:Set[Module] = Set()
		var again:String = null
        val cli = new jline.ConsoleReader()
		
		val hildaModules = Hilda.getHildaHome + "/modules.xml"
		
		val home = new File(Hilda.getHildaHome)
		
		if(!home.exists()){
			home.mkdir()
		}
		
		if(cli.readLine("Create new module? [y/n]: ").toLowerCase.compare("y")!=0){
			println("You can create module manually by editing `" + hildaModules + "`")
			return
		}
		
		do{
			val name = cli.readLine("name: ")
			val depends = cli.readLine("depends (separated by whitespace): ")
			val repo = cli.readLine("repo (a git uri): ")
			var branch = cli.readLine("branch [master]:")
			val workDir = cli.readLine("where is working dir?: (ex: /opt/local/digaku.core) ")
			if(branch.trim().length == 0) branch = "master"
			modules += new Module(name, depends, repo, branch, workDir)
			again = cli.readLine("Add another module? [y/n]: ")
		}while(again.compare("y")==0)
			
		val data = genModules(modules)
		
		val f = new File(hildaModules)
		
		if(f.exists()){
			val input = cli.readLine("File `" + hildaModules + "` already exists, overwrite it? [y/n] ")
			input match {
				case "y" => 
					if(!f.delete()){
						System.err.println("Cannot delete file `" + hildaModules + "`")
					}
				case _ => 
					println("Aborted.")
					return
			}
		} 
		
		val f2 = new FileWriter(hildaModules)
		f2.write(data)
		f2.close()
		
		println("Hilda modules created `" + hildaModules + "`")
	}
	
	def generateLogConfigXML() {
		val tmpl = """
<configuration debug="false"> 

  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender"> 
    <!-- encoders are  by default assigned the type
         ch.qos.logback.classic.encoder.PatternLayoutEncoder -->
    <encoder>
      <pattern>[%-5level] - %msg%n</pattern>
    </encoder>
  </appender>

  <root level="error">
    <appender-ref ref="STDOUT" />
  </root>
</configuration>
"""
		val f = new FileWriter(Hilda.getHildaHome + "/log-config-simple.xml")
		f.write(tmpl)
		f.close()
	}
	
	def generateConfigXML(){
		println("")
		println("========| Configuration")
		val tmpl = """
<hilda>
	<router>localhost:4912</router>
	<node-name>AnlabMac</node-name>
	<verbosity>1</verbosity>
</hilda>
"""
        //val cli = new jline.ConsoleReader()
		//val asMaster = cli.readLine("As master? [y/n]: ")
		val f = new FileWriter(Hilda.getHildaHome + "/config.xml")
		f.write(tmpl)
		f.close()
	}

    /**
     * to upgrade hilda.
     */
    def upgrade(){

        try {
            val binDir = new File(DEFAULT_BIN_DIR)
            val hildaBin = binDir.getAbsolutePath + "/hilda"

            (new File(hildaBin)).delete()
        }catch{
            case e:Exception =>
                e.printStackTrace()
        }

        this.installScript()

    }

}
