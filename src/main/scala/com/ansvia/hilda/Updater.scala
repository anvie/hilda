package com.ansvia.hilda
import org.slf4j.LoggerFactory
import java.io.{FileWriter, File}
import scala.xml.Elem
import scala.xml.XML
import scala.xml.Node


class Updater(modulesFile:String) {


    private val log = LoggerFactory.getLogger(getClass)


    case class InitDefItem(key:String, value:String)

    case class InitDef(node:Node) {

        lazy val definitonCount = {
            (node \\ "definitions" \\ "def").length
        }
        lazy val definitions = {
            (node \ "definitions" \\ "def") map { n =>
                val key = (n \ "@name").text
                val defaultValue = (n \ "@default").text
                var value = Console.readLine("%s [%s]: ".format(key, defaultValue))
                if (value == null || value.length == 0)
                    value = defaultValue
                InitDefItem(key, value)
            }
        }
    }

    implicit var initDef:InitDef = null

    case class WithTransform(str:String){
        def transform(implicit initDef:InitDef) = {
            var rv = str
            if (!initMode){
                rv
            }else{
                for ( d <- initDef.definitions ){
                    val repl = java.util.regex.Pattern.quote("""%%{%s}""".format(d.key))
                    rv = rv.replaceAll(repl, d.value)
                }
                rv
            }

        }
    }

    implicit def stringWithTransform(str:String) = WithTransform(str)

    var initialized: Boolean = false
//    val modulesFile = Hilda.getHildaHome + "/modules.xml"
    var cachedModules: Array[IHildaModule] = null
    private var quiet = false

    var initMode = false

    def setInitMode(state: Boolean){
        initMode = state
        initialized = state
    }


    def setQuiet(state: Boolean){
        quiet = state
    }

    def saveModules(){
        val f = new FileWriter(new File("/tmp/modules.xml"))
        val data = loadData()
        val z = (data \ "init")
        val m = (data \ "init" \ "definitions") map { n =>
            (n \ "@value")
        }
        f.write(data.toString())
    }


    def ensureConfig() {
        val dirExists = (new File(Hilda.getHildaHome)).exists()
        if (!dirExists) {
            println("Directory `" + Hilda.getHildaHome + "` not exists")
            return
        }
        initialized = true
    }

    private def loadData(): Elem = {
        if (!(new File(modulesFile)).exists()) {
            log.error("modules.xml file not found `" + modulesFile + "`")
            return null
        }
        var data: Elem = null

        try {
            data = XML.loadFile(modulesFile)
        } catch {
            case e: org.xml.sax.SAXParseException =>
                log.error("Cannot parse xml")
                log.error(e.toString)
        }

        data
    }

    /**
     * Get hilda module by xml node.
     * @param m xml node of module.
     * @return HildaModule or null if fail.
     */
    def getModule(m: Node): IHildaModule = {
        val name = (m \ "@name").text

        if(name.trim().length == 0){
            log.error("Invalid module, no name.")
            return null
        }

        val type_ = (m \ "@type").text
        if(type_ == "remote"){
            return new RemoteModule(name, (m \ "node" \ "name").text, (m \ "node" \ "host").text)
        }

        val dependsText = (m \ "@depends").text
        var depends: Array[String] = Array[String]()
        if (dependsText.trim().length() > 0) {
            depends = dependsText.split(" ").map(_.trim())

            // validate dependencies
            if(depends.contains(name)){
                log.error("Invalid module dependencies `" + name + "`, depends on it self? Are you kidding me?")
                return null;
            }

        }

        var poller: IPoller = null
        (m \\ "source" \ "@type").text match {
            case "git" =>

                val src_l = (m \\ "source").text.split("#")
                val uri = src_l(0)
                if (src_l.length > 1) {
                    poller = GitPoller(uri, src_l(1))
                } else {
                    poller = GitPoller(uri, "master")
                }

                poller.asInstanceOf[GitPoller].setForceUpdate( (m \\ "source" \ "@forceupdate").text == "true" )
                //println( name + """ > (m \\ "source" \ "@forceupdate").text == """ + ((m \\ "source" \ "@forceupdate").text == "true").toString)

            case "rsync" =>

                var rsyncSource: RsyncSource = null
                val m_src = (m \\ "source" \\ "src")
                (m_src \ "@type").text match {
                    case "ssh" => rsyncSource = SSHHostedSource((m_src \\ "@host").text, (m_src \\ "@port").text.toInt)
                    case "local" => rsyncSource = RsyncLocalSource((m_src \\ "@dir").text)
                }
                poller = RsyncPoller(rsyncSource)

            case _ =>
                System.err.println("Module `"+ name +"` missing source key")
                return null
        }

        val workDir = (m \ "work" \ "@dir").text.transform

        val hooks: Array[Hook] = (m \\ "hook").map {
            z =>
                if ((z \ "@target").text.length > 0) {
                    new TargetedHook((z \ "@when").text, (z \ "@target").text.split(" "))
                }
                else {
                    new ScriptedHook((z \ "@when").text, z.text)
                }
        }.toArray

        // get version
        val version = (m \ "version" \ "@type").text match {
            case "programatic" =>
                val script = (m \ "version").text
                ProgramaticVersionGetter(script,
                    new Executor { setWorkingDir(workDir) },
                    new OffensiveLogger{})
            case _ =>
                poller match {
                    case p:GitPoller =>
                        ModUiVersionGetter()
                    case _ =>
                        DefaultVersionGetter("-")
                }
        }

        val mod = new StandardModule(this, name, depends, poller, workDir,
            hooks, version, (m \ "@quiet").text == "true" || quiet)

        val targets = (m \ "targets" \ "target")
        targets.map(t => TargetUtil.nodeToTarget(mod, t))
                .filter(_ != null)
                .foreach(t => mod.addTarget(t))

        mod
    }

    def getModule(modName: String):IHildaModule = {
        val modules = getModules
        for (m <- modules) {
            if (m.getName == modName) {
                return m
            }
        }
        null
    }

    def moduleExists(modName:String):Boolean = { getModule(modName) != null }

    def getModules: Array[IHildaModule] = {

        if (cachedModules != null) {
            return cachedModules
        }

        val data = loadData()

        if (data == null) {
            log.error("Cannot load data from `" + modulesFile + "`")
            return null
        }

        val modules = (data \\ "hilda" \\ "modules" \\ "module")

        val rv: Array[IHildaModule] = modules.iterator.map(z => getModule(z)).filter(_!=null).toArray

        cachedModules = rv

        rv
    }

    def printModules() {
        val modules = getModules
        if (modules == null || modules.length == 0) {
            println("No one modules found")
            println("If this is first time you are using Hilda, please install it first by typing:")
            println("    $ java -jar hilda.jar install")
            println("")
            return
        }
        println("Registered modules:\n")
        modules foreach (m => println(" * " + m))
    }



    def doAction(modName:String): Int = {

        if(initMode){
            val data = loadData()
            if(data == null){
                return Error.DATA_NOT_LOADED
            }

            initDef = InitDef((data \ "init").apply(0))

        }

        val mod = getModule(modName)
        if(mod != null){
            mod.selfUpdate()
            Error.SUCCESS
        }
        else {
            Error.UNKNOWN_ERROR
        }
    }

    def doAction(mods: Array[String] = null): Int = {
        if (!initialized) {
            log.error("Hilda engine not initialized")
            return Error.NOT_INITIALIZED
        }

        if(initMode){
            val data = loadData()
            if(data == null){
                return Error.DATA_NOT_LOADED
            }

            initDef = InitDef((data \ "init").apply(0))

        }

        val modules = getModules

        if (modules == null || modules.length == 0) {
            log.error("No one modules loaded")
            return Error.NOT_INITIALIZED
        }

        if (mods == null) {
            modules.foreach(z => z.selfUpdate())
        } else {
            modules.filter(z => mods.contains(z.getName))
                    .foreach(z => z.selfUpdate())
        }

        Error.SUCCESS
    }

    def executeTargets(modsNTargets: Array[String]): Int = {
        modsNTargets foreach { mt =>
            val mts = mt.split(":")
            val modName = mts(0)
            if (mts.length > 1) {
                val target = mts(1)
                val mod = getModule(modName)
                if (mod != null) {
                    mod.executeTarget(target)
                }
            }
        }
        Error.SUCCESS
    }

}