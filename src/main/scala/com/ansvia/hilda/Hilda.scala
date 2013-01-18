
package com.ansvia.hilda

import java.io._
import scala.collection.immutable.Map
import org.fud.optparse.OptionParser
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.LoggerContext

object Hilda {

    val VERSION = "0.1.0"
    val BANNER = """
Hilda v""" + VERSION + """
Copyright (C) 2011 Ansvia Inc.
Internal Ansvia modules updater.
                       """
    private lazy val HILDA_HOME = {
        val envHome = System.getenv().get("HILDA_HOME")
        if (envHome != null)
            envHome
        else
            "/etc/hilda"
    }
    private val INSTALL_PREFIX = "/usr/local"
    private var CUSTOM_HILDA_HOME:String = null

    def getHildaHome:String =
        if(CUSTOM_HILDA_HOME != null) CUSTOM_HILDA_HOME
        else HILDA_HOME

    def setHildaHome(hildaHome:String):Int = {
        val hdh = new File(hildaHome)
        if(!hdh.exists()){
            System.err.println("[ERROR] Directory `%s` does not exists".format(hildaHome))
            return Error.INVALID_PARAMETER
        }
        if(!hdh.isDirectory){
            System.err.println("[ERROR] `%s` isn't a directory".format(hildaHome))
            return Error.INVALID_PARAMETER
        }
        CUSTOM_HILDA_HOME = hildaHome
        Error.SUCCESS
    }


    def showUsageAndExit(cli: OptionParser) {
        println(cli.help)
        println("")
        println("    To install run with `install` command. Ex: $ java -jar hilda.jar install")
        println("    To uninstall run with `uninstall` command. Ex: $ hilda uninstall")
        println("    Start action with `update` command. Example: $ hilda update")
        println("    To execute a target run with `execute` command. Example: $ hilda execute some_module:clean")
        println("    Read `README.md` for more specific details")
        println("")
        System.exit(0)
    }

    def main(args: Array[String]) {

        /**
         * Setup logger.
         */
        val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
        val configurator = new JoranConfigurator()
        configurator.setContext(context)
        context.reset()

        val logConfigFile = new File(HILDA_HOME + "/log-config-simple.xml")
        if (logConfigFile.exists()) {
            configurator.doConfigure(logConfigFile)
        }

        val log = LoggerFactory.getLogger(getClass)

        var options = Map[Symbol, Any](
            'modules -> false,
            'help -> false,
            'asRouter -> false,
            'routerPort -> 4912,
            'routerName -> "hilda",
            'node -> false,
            'hildaHome -> HILDA_HOME,
            'hildaVersion -> false,
            'quietMode -> false,
            'init -> None)

        val cli = new OptionParser()
        cli.banner = BANNER
        cli.flag("-m", "--modules", "Show registered modules.") { () => options += 'modules -> true }
        cli.reqd[String]("-i", "--init <module-file>", "Using specific module.xml file.") { v =>
            options += 'init -> Some(v)
        }
        cli.flag("", "--router", "Run as router."){() =>
            options += 'asRouter -> true
            println("Running as router")
        }
        cli.reqd[Int]("", "--port <port>", "The router port, specified with `--router`."){ v =>
            options += 'routerPort -> v
        }
        cli.reqd[String]("", "--router-name <name>", "The router name, specified with `--router`."){ v=>
            options += 'routerName -> v
        }
        cli.flag("","--node", "Run as Hilda distributed node."){ () =>
            if(options('asRouter) == true){
                throw new Exception("Cannot run as router and as node at the same time! fuck you!")
            }
            options += 'node -> true
            println("Running as node")
        }

        cli.reqd[String]("","--hilda-home", "Use non standard hilda home."){ v =>
            options += 'hildaHome -> v
            setHildaHome(v)
        }

        cli.flag("","--version", "Show Hilda version and exit.") { () =>
            options += 'hildaVersion -> true
        }

        cli.flag("","--quiet", "Quiet mode, never ask for anything.") { ()=>
            options += 'quietMode -> true
        }

        try {
            cli.parse(args)
        }catch{case e:Exception =>
            System.err.println(e.getMessage)
            System.exit(Error.INVALID_PARAMETER)
        }

        if (args.length < 1) {
            showUsageAndExit(cli)
            return
        }

        if(options('hildaVersion) == true){
            println(VERSION)
            return
        }

        var init = false
        val engine =
            if (options('init).asInstanceOf[Option[String]].isDefined){
                init = true
                val moduleInitFile = options('init).asInstanceOf[Option[String]].get
                //          println("deployFile: " + deployFile)
                new Updater(moduleInitFile)
            }else{
                new Updater(getHildaHome + "/modules.xml")
            }


        if (init){
            engine.setInitMode(true)
            engine.doAction()
            engine.saveModules()
            return
        }


        var rv = Error.UNKNOWN_ERROR

        engine.ensureConfig()

        //        println("options('quietMode): " + (options('quietMode) == true))
        engine.setQuiet(options('quietMode) == true)

        log.info("Using hilda home: `%s`".format(getHildaHome))

        if(args.toList(0) != "install" && args.toList(0) != "configure"){
            if(!Config.setConfig(getHildaHome + "/config.xml")){
                return
            }
        }

        if(options('asRouter) == true){
            RouterUtil.startRouter(options('routerName).asInstanceOf[String], options('routerPort).asInstanceOf[Int])
            return

        }else if(options('node) == true){
            log.info("router: " + Config.router)
            log.info("node: %s@%s:%s".format(Config.node.getName, Config.node.getHost, Config.node.getPort))
            if(Config.router.length() == 0 || !Config.nodeConfigured){
                log.error("Please configure `config.xml` first by adding `router` and `node`")
                return
            }
            val Array(host, port) = Config.router.split(":")
            val node = NodeUtil.startNode(host, port.toInt, "hilda", Config.node.getName)

            // register node

            node ! "register"

            /*
            Signal.handle(new Signal("INT"), new SignalHandler(){
              def handle(sig:Signal){
                node ! "unregister"
                node !! "stop"
                System.exit(0)
                //Runtime.getRuntime().halt(0)
              }
            })
            */

            return
        }

        if (args.length > 0) {
            args.toList(0) match {
                case "update" =>
                    println("Updating modules...")
                    args.filter(_.startsWith("--")==false).length match {
                        case 1 => rv = engine.doAction()
                        case e:Int if e >= 2 =>
                            val modNames = args.toList(1).split(",")
                            println("Updating only for these modules:")
                            println("=> " + modNames.reduce(_ + ", " + _))
                            engine.doAction(modNames)
                    }
                    rv = Error.SUCCESS

                case "upgrade" =>

                    val force = {
                        if (args.length > 1){
                            args(1) == "force"
                        }else false
                    }
                    var oldVersion = ""
                    val versionFile = new File(HILDA_HOME + "/version")
                    if (versionFile.exists()){
                        oldVersion = (new BufferedReader(new FileReader(versionFile))).readLine()
                        if (oldVersion != null){
                            oldVersion = oldVersion.trim
                        }else{
                            oldVersion = ""
                        }
                    }

                    if (oldVersion.length == 0){
                        oldVersion = "unknown"
                    }

                    if (oldVersion == VERSION && !force){
                        println("You already have the latest version of Hilda (" + VERSION + ")")
                        println("Add parameter `force` to force upgrade.")
                    }
                    else {
                        println("Upgrading hilda version " + oldVersion + " to " + VERSION)

                        Initializer.upgrade()


                        val versionWriter = new FileWriter(versionFile)
                        versionWriter.write(VERSION)
                        versionWriter.flush()
                        versionWriter.close()
                    }
                    rv = Error.SUCCESS

                case "execute" =>
                    args.length match {
                        case 2 =>
                            val modNames = args.toList(1).split(",")
                            rv = engine.executeTargets(modNames)
                        case _ =>
                            showUsageAndExit(cli)
                    }

                case "install" =>
                    if ((new File(HILDA_HOME + "/modules.xml")).exists() &&
                        (new File(INSTALL_PREFIX + "/bin/hilda")).exists())
                    {
                        println(" Hilda already installed")
                        println(" Edit `~/.hilda/modules.xml` if you want to customize modules.")
                        println(" If not yet configured please run `hilda configure` using your account (not root)")
                        println("")
                        System.exit(1)
                    }

                    println("")
                    println("Installing Hilda v" + VERSION)

                    rv = Initializer.installScript()

                    return

                case "configure" =>

                    if((new File("/usr/local/bin")).exists() == false){
                        log.error("Hilda is not installed. Please install it first using `install` parameter.")
                        rv = Error.NOT_INITIALIZED
                    }
                    else {

                        if (args.length > 1){
                            if (args(1).startsWith("--hilda-home=")){
                                setHildaHome(args(1).substring(13))
                            }
                        }

                        println("Hilda Home set to: " + getHildaHome)

                        Initializer.ensureHildaHome()

                        Initializer.CoverError {
                            Initializer.generateConfigXML()
                            Initializer.generateModuleXml()
                            Initializer.generateLogConfigXML()

                            println("Installation completed.")
                            println("Or type `hilda` for other usage details.")

                            rv = Error.SUCCESS
                        }


                    }

                case "state" =>

                    val mods = Module.getModules
                    mods foreach { mod =>
                        println(" [ " + mod.getName + " ] " + mod.getState)
                    }

                    rv = Error.SUCCESS

                case "ui" =>
                    val mainUi = new ui.MainWindow()

                    mainUi.main(args)

                    rv = Error.SUCCESS

                    return

                case "uninstall" =>
                    rv = Initializer.uninstallScript()

                /*
                case "serve" =>
                  try {
                    val server = HttpServerFactory.create("http://localhost:8080/")
                    server.start()

                        println("Server running")
                        println("Visit: http://localhost:8080/")
                        println("Hit return to stop")
                        System.in.read()
                        server.stop(0)
                        println("Server stopped")

                        rv = Error.SUCCESS
                  }catch{
                    case _ =>
                  }
                */

                case _ =>
                    // invoke internal / custom poller command
                    // just work like plugins
                    rv = Commander.processCommand(args)
            }
        }

        if (rv == Error.UNKNOWN_ERROR) {
            if (options('modules) == true) {
                println(BANNER)
                engine.printModules()
                System.exit(Error.SUCCESS)
            } else {
                showUsageAndExit(cli)
            }
        }

        println("")

        System.exit(rv)

    }

}
