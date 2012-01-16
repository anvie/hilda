package com.ansvia.hilda
import org.slf4j.LoggerFactory
import java.io.FileWriter
import scala.xml.Node


sealed trait Target extends StatusWriter {
	def execute()
	def getName():String
}

sealed abstract class ModuleTarget(mod: StandardModule) extends Target with Executor {
	private val log = LoggerFactory.getLogger(getClass)
	override def status(msg: String) {
		println("[" + mod.getName() + ":" + ID + "]: " + msg)
	}
}

sealed class InlineTarget(mod: StandardModule, name: String, script: String, workDir: String)
	extends ModuleTarget(mod)
	with Beautifier {
	
	override val ID = name
	val TMP_EXECUTOR_FILE = "/tmp/hilda-inline-target.sh"
	def execute() {
		status("Executing `" + name + "`...")
		val readyScript = beautifyContent(mod.translateAll(script))
		val f = new FileWriter(TMP_EXECUTOR_FILE)
		f.write(readyScript)
		f.close()

		setWorkingDir(mod.translate(workDir, true))

		exec(Array("sh", TMP_EXECUTOR_FILE))
		
		status("Done.")
	}
	override def toString() = name
	def getName():String = name
}

sealed class ExternalScriptTarget(mod: StandardModule, name: String, scriptLoc: String, workDir: String)
	extends ModuleTarget(mod)
	with Beautifier {
	
	override val ID = name
	def execute() {
		status("Executing...")
		setWorkingDir(mod.translate(workDir, true))
		exec(Array("sh", Hilda.getHildaHome() + "/target_script/" + scriptLoc + ".sh"))
	}
	override def toString() = name
	def getName():String = name
}


object TargetUtil {
	def nodeToTarget(mod: StandardModule, node: Node): Target = {
		val name = (node \ "@name").text
		val scriptTag = (node \ "@script").text
		var tWorkDir = (node \ "@workdir").text
		if (tWorkDir.length == 0) {
			tWorkDir = mod.getWorkDir()
		}
		if (scriptTag.length > 0) {
			return new ExternalScriptTarget(mod, name, scriptTag, tWorkDir)
		}
		val script = (node).text
		return new InlineTarget(mod, name, script, tWorkDir)
	}
}
