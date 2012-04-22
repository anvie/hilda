
package com.ansvia.hilda

import java.io.File
import scala.xml._
import scala.collection.immutable.Map
import org.fud.optparse.OptionParser
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.LoggerContext
//import sun.misc.Signal
//import sun.misc.SignalHandler

object Hilda {

    val VERSION = "0.0.10"
    val BANNER = """
Hilda v""" + VERSION + """
Copyright (C) 2011 Ansvia Inc.
Internal Ansvia modules updater.
"""
    private val HILDA_HOME = System.getProperty("user.home") + "/.hilda"
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
        if(!hdh.isDirectory()){
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
        val context = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]
        val configurator = new JoranConfigurator()
        configurator.setContext(context);
        context.reset();
        val logConfigFile = new File(HILDA_HOME + "/log-config-simple.xml")
        if (logConfigFile.exists()) {
            configurator.doConfigure(logConfigFile)
        }

        val log = LoggerFactory.getLogger(getClass)

        var options = Map[Symbol, Any]('modules -> false,
            'help -> false,
            'asRouter -> false,
            'routerPort -> 4912,
            'routerName -> "hilda",
            'node -> false,
            'hildaHome -> HILDA_HOME,
            'hildaVersion -> false)

        val cli = new OptionParser()
        cli.banner = BANNER
        cli.flag("-m", "--modules", "Show registered modules.") { () => options += 'modules -> true }
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

        cli.flag("","--version", "Show Hilda version and exit") { () =>
            options += 'hildaVersion -> true
        }

        try {
            cli.parse(args)
        }catch{case e =>
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

        var rv = Error.UNKNOWN_ERROR

        val engine = new Updater()
        engine.ensureConfig()

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
                    args.length match {
                        case 1 => rv = engine.doAction()
                        case 2 =>
                            val modNames = args.toList(1).split(",")
                            println("Updating only for these modules:")
                            println("=> " + modNames.reduce(_ + ", " + _))
                            engine.doAction(modNames)
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
