package com.ansvia.hilda
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException

object HookEvent {
	val WHEN_TO_UPDATE = "pre-update"
	val WHEN_FINISH_UPDATE = "post-update"
}

trait Hook {
	private val ID = "Hook"
	private val log = LoggerFactory.getLogger(getClass)
	
	def is(a: String):Boolean
	def run()
	
	def status(msg: String) {
		log.info("  + [Hook]: " + msg)
	}
	def statusScript(msg: String) {
		log.info("         > " + msg)
	}
	override def toString():String = ID
}

class TargetedHook(when:String, targetNames:Array[String]) extends Hook {
	private val ID = "TargetedHook"
	private var mod:StandardModule = null
	def is(a: String): Boolean = {
		this.when == a
	}
	def run() {
		if(mod == null){
			throw new Exception("mod not initialized on TargetedHook")
		}
		targetNames.foreach(t => mod.executeTarget(t))
	}
	def setModule(module:StandardModule) { mod = module }
}

class ScriptedHook(when:String, content:String) extends Hook with Beautifier {
	private val ID = "ScriptedHook"
		
	val HILDA_HOOK_SCRIPT = "/tmp/hilda-hook-script.sh"
	
	def is(a: String): Boolean = {
		this.when == a
	}
	def run() {
		status("Running hook `" + when + "`")

		try {

			val f = new FileWriter(HILDA_HOOK_SCRIPT)
			f.write(beautifyContent(content))
			f.close()

			val rt = Runtime.getRuntime()
			val proc = rt.exec("sh " + HILDA_HOOK_SCRIPT)
			val stdOut = new BufferedReader(new InputStreamReader(proc.getInputStream()))
			val stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()))

			Stream.continually(stdOut.readLine()).takeWhile(_ != null).foreach(statusScript(_))
			Stream.continually(stdErr.readLine()).takeWhile(_ != null).foreach(statusScript(_))

		} catch {
			case e: IOException =>
				e.printStackTrace()
		}

		status("Done.")
	}
}