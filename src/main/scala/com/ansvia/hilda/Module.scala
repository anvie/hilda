package com.ansvia.hilda
import scala.collection.mutable.Stack
import org.slf4j.LoggerFactory
import scala.collection.mutable.HashMap
import scala.actors.remote.RemoteActor


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
	def getName():String
	def selfUpdate():Unit
	def executeTarget(targetName:String):Unit
	def getState():String
	def getPoller:IPoller
}

abstract class HildaModule(name:String) extends IHildaModule {
	def status(msg: String) {
		println(" + [Mod][" + name + "]: " + msg)
	}
}


case class RemoteModule(name:String, nodeName:String, nodeHost:String) extends HildaModule(name) {
	
	private val log = LoggerFactory.getLogger(getClass)
	
	RemoteActor.classLoader = getClass().getClassLoader()
	
	def getName():String = name
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
	override def toString(): String = {
		var rv = name + " (remote)\n\n"
		return rv
	}
	def getState() = "Remote module doesn't support get state"
}

case class StandardModule(updater: Updater,
	name: String, depends: Array[String],
	poller: IPoller, workDir: String, hooks: Array[Hook]) 
		extends HildaModule(name) 
		with Translator {

	private val log = LoggerFactory.getLogger(getClass)
	poller.setModule(this)
	
	val workDirState = poller.asInstanceOf[Executor].setWorkingDir(workDir)

	var targets = List[Target]()

	val dataSet = HashMap[String, String](
		"name" -> name,
		"depends" -> (if (depends.length > 0) depends.reduce(_ + ", " + _); else ""),
		"work.dir" -> workDir)
		
	setDataset(dataSet)
	
	/**
	 * Initialize targeted hooks
	 */
	hooks.filter(_.isInstanceOf[TargetedHook]).foreach(h => h.asInstanceOf[TargetedHook].setModule(this))

	def getName() = name
	def getWorkDir() = workDir
	def getPoller = poller

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
		
		if (targets.filter(t => t.getName().compare(targetName) == 0).length == 0) {
			log.error("Module `" + name + "` has no target name `" + targetName + "`")
			println("Available target for this module is:")
			targets foreach { t => println(" * " + t.getName() + "\n") }
			return
		}

		var alreadyExecutedTargets: Array[String] = Array[String]()

		def notAlreadyExecuted(t: String): Boolean = !alreadyExecutedTargets.contains(t)

		targets foreach { t =>
			if (t.getName().compare(targetName) == 0 && notAlreadyExecuted(t.getName())) {
				t.execute()
			}
		}
	}
	
	def getState() = poller.getCurrentStatus()

	def selfUpdate() {
	  
		log.info("selfUpdate(): " + name);

		if (DgModuleSyncronizer.isAlreadyExecuted(name)) {
			return
		}

		if (depends.length > 0) {
			for (mn <- depends) {
				var mod = updater.getModule(mn)
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

			var changes: String = poller.getNewChanges()
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

	def addTarget(target: Target) { targets :+= target }

	override def toString(): String = {
		var rv = name + "\n"
		rv += "   Source: " + poller + "\n"
		rv += "   Working dir: " + workDir + "\n"
		if (targets.length > 0)
			rv += "   Targets: " + targets.map(t => t.toString()).reduce(_ + ", " + _)
		rv += "\n"
		return rv
	}
}


object Module {
	private val updater = new Updater()
	updater.ensureConfig()
	def getModules():Array[IHildaModule] = {
		return updater.getModules()
	}
}


