package com.ansvia.hilda
import scala.collection.mutable.Stack
import org.slf4j.LoggerFactory
import scala.collection.mutable.HashMap
import scala.actors.remote.RemoteActor
import collection.mutable


object DgModuleSyncronizer {
	val executedModules: Stack[String] = new Stack[String]
	def isAlreadyExecuted(modName: String): Boolean = {
		this.synchronized {
			return executedModules.contains(modName)
		}
	}
	def markAlreadyExecuted(modName: String) {
		this.synchronized {
			executedModules.push(modName)
		}
	}
}


trait IHildaModule {
	def getName:String
	def selfUpdate()
	def executeTarget(targetName:String)
	def getState:String
	def getPoller:IPoller
    def getVersion:String
    def setQuiet(state:Boolean)
    def isQuiet:Boolean
}

abstract class HildaModule(name:String) extends IHildaModule with MutableLogger {
	def status(msg: String) {
		log.print(" + [Mod][" + name + "]: " + msg + "\n")
	}

    private var quiet = false

    def setQuiet(state: Boolean) {
        quiet = state
    }

    def isQuiet = quiet

}


case class RemoteModule(name:String, nodeName:String, nodeHost:String) extends HildaModule(name) {
	
	//private val log = LoggerFactory.getLogger(getClass)

    if (log == null){
        log = new Slf4jLogger {}
    }
	
	RemoteActor.classLoader = getClass().getClassLoader()
	
	def getName:String = name
	def getPoller:IPoller = null
	
	def selfUpdate() {
		status("selfUpdate()...")
		
		val Array(host, port) = Config.router.split(":")
		
		status("Connecting to remote module `" + Config.router + "`")
		
		val router = RouterUtil.getRemoteRouter(host, port.toInt, "hilda")

		router ! msg.Update(nodeName, name)
		
		//log.info(result().toString())
	}
	def executeTarget(targetName:String){
		status("executeTarget()...")
	}
	override def toString: String = {
        val rv = name + " (remote)\n\n"
		rv
	}
	def getState = "Remote module doesn't support get state"

    // @TODO(robin): implement this
    def getVersion:String = "-"
}

/**
 * Standard / local module.
 * @param updater the Updater engine.
 * @param name Module name
 * @param depends depends on (module name list).
 * @param poller Poller
 * @param workDir Workdir
 * @param hooks Hooks engine.
 * @param version Module version getter.
 */
case class StandardModule(updater: Updater,
	name: String, depends: Array[String],
	poller: IPoller, workDir: String, hooks: Array[Hook],
    version:IVersionGetter,
    quietMode:Boolean=false)
		extends HildaModule(name) 
		with Translator
        with MutableLogger {

	//private val log = LoggerFactory.getLogger(getClass)
    
    if (log == null){
        log = new Slf4jLogger {}
    }
    
	poller.setModule(this)
    setQuiet(quietMode)
	
	val workDirState = poller.asInstanceOf[Executor].setWorkingDir(workDir)

	var targets = List[Target]()

	val dataSet = mutable.HashMap[String, String](
		"name" -> name,
		"depends" -> (if (depends.length > 0) depends.reduce(_ + ", " + _); else ""),
		"work.dir" -> workDir)
		
	setDataset(dataSet)
	
	/**
	 * Initialize targeted hooks
	 */
	hooks.filter(_.isInstanceOf[TargetedHook]).foreach(h => h.asInstanceOf[TargetedHook].setModule(this))

	def getName = name
	def getWorkDir = workDir
	def getPoller = poller
    def getVersion:String = {
        version match {
            case v:ProgramaticVersionGetter => v.get
            case v:DefaultVersionGetter => v.get
            case v:ModUiVersionGetter => 
                if (!v.isSet){
                    poller match {
                        case p:GitPoller =>
                            // we using ui module to exploit version getter routine
                            // @TODO(robin): needs some enhancement
                            v.setUiModule(ui.GitModule(this))
                        case _ =>
                            return "-"
                    }
                }
                v.get
            case _ =>
                "-"
        }
    }

	private def executeHook(when: String) {
		val hooks_ = hooks.filter(_.is(when))
		if (hooks_.length > 0) {
			status("Executing " + when + " hooks...")
			for (h <- hooks_) {
				h.run()
			}
			status("Done.")
		}
	}

	def executeTarget(targetName: String) {
		if (targets.length == 0) {
			log.error("Module `" + name + "` has no any target")
			return
		}
		
		if (targets.filter(t => t.getName.compare(targetName) == 0).length == 0) {
			log.error("Module `" + name + "` has no target name `" + targetName + "`")
			println("Available target for this module is:")
			targets foreach { t => println(" * " + t.getName + "\n") }
			return
		}

        val alreadyExecutedTargets: Array[String] = Array[String]()

		def notAlreadyExecuted(t: String): Boolean = !alreadyExecutedTargets.contains(t)

		targets foreach { t =>
			if (t.getName.compare(targetName) == 0 && notAlreadyExecuted(t.getName)) {
				t.execute()
			}
		}
	}
	
	def getState = {
        val state = poller match {
            case p:GitPoller =>
                val Seq(branch, modified) = p.getCurrentStatusList
                "branch: %s, version: %s, modified: %s".format(branch, getVersion, modified)
            case _ =>
                "**unknown**".format(getName)
        }
        state
    }


    /**
     * Do self update action.
     * this method called when `hilda update`
     */
	def selfUpdate() {
	  
		//log.info("selfUpdate(): " + name);

		if (DgModuleSyncronizer.isAlreadyExecuted(name)) {
			return
		}

		if (depends.length > 0) {
			for (mn <- depends) {
                val mod = updater.getModule(mn)
				if (mod == null) {
					throw new Exception("Invalid dependency module `" + mn + "`, cannot execute depens module")
				}
				mod.selfUpdate()
			}
		}

		executeHook(HookEvent.WHEN_TO_UPDATE)

		status("Checking for new available update...")

		var updateAvailable = false

		if (poller.updateAvailable()) {

			updateAvailable = true

            val changes: String = poller.getNewChanges
			if (changes != null) {
				status("Update available. rev: `%s`.".format(changes))
				status("Updating now...")
			}

			poller.updateWorkTree()
		}

		status("Done.")

		if (updateAvailable == true || workDirState == WorkDirState.WORK_DIR_JUST_CREATED) {
			executeHook(HookEvent.WHEN_FINISH_UPDATE)
		}

		DgModuleSyncronizer.markAlreadyExecuted(name)

	}

    def selfUpdateCallback(callback:(String) => Unit) {

        //log.info("selfUpdate(): " + name);

        if (DgModuleSyncronizer.isAlreadyExecuted(name)) {
            return
        }

        if (depends.length > 0) {
            for (mn <- depends) {
                val mod = updater.getModule(mn)
                if (mod == null) {
                    throw new Exception("Invalid dependency module `" + mn + "`, cannot execute depens module")
                }
                mod match {
                    case x:StandardModule =>
                        x.selfUpdateCallback(callback)
                    case _ =>
                        mod.selfUpdate()
                }
            }
        }

        executeHook(HookEvent.WHEN_TO_UPDATE)

        callback("Checking for new available update...")

        var updateAvailable = false

        if (poller.updateAvailable()) {

            updateAvailable = true

            val changes: String = poller.getNewChanges
            if (changes != null) {
                callback("Update available. rev: `%s`.".format(changes))
                callback("Updating now...")
            }

            poller.updateWorkTree()
        }

        callback("Done.")

        if (updateAvailable == true || workDirState == WorkDirState.WORK_DIR_JUST_CREATED) {
            executeHook(HookEvent.WHEN_FINISH_UPDATE)
        }

        DgModuleSyncronizer.markAlreadyExecuted(name)

    }
    
	def addTarget(target: Target) { targets :+= target }

	override def toString: String = {
		var rv = name + "\n"
		rv += "   Source: " + poller + "\n"
		rv += "   Working dir: " + workDir + "\n"
		if (targets.length > 0)
			rv += "   Targets: " + targets.map(t => t.toString).reduce(_ + ", " + _)
		rv += "\n"
		rv
	}
}


object Module {
	private val updater = new Updater(Hilda.getHildaHome + "/modules.xml")
	updater.ensureConfig()
	def getModules:Array[IHildaModule] = {
		updater.getModules
	}
}


