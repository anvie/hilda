package com.ansvia.hilda

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.File
import org.slf4j.LoggerFactory;

trait Executor extends WorkingDir {
	
	private val log = LoggerFactory.getLogger(getClass)
	protected val ENV_VARS = Array[String]("PATH=/bin:/usr/bin:/usr/local/bin:/opt/bin:/opt/local/bin")
	
	def exec(cmd:String):String = exec(cmd.split(" "))
	
	def exec(cmd:Array[String]):String = {
		val rt = Runtime.getRuntime()
		
		if(workingDir == null){
			log.error("Executor.workDir not initialized")
			return "Aborted"
		}
		
		log.info("Executing `" + cmd.reduceLeft(_+ " " + _) + "`")
		
		var proc:Process = null
		
		if(workingDir.exists()){
			proc = rt.exec(cmd, ENV_VARS, workingDir)
		}
		else {
			proc = rt.exec(cmd, ENV_VARS)
		}
		
		
		val stdOut = new BufferedReader(new InputStreamReader(proc.getInputStream()))
		val stdErr = new BufferedReader(new InputStreamReader(proc.getErrorStream()))
		
		var result = ""
			
		Stream.continually(stdOut.readLine()).takeWhile(_ != null).foreach(z => result += z + "\n")
		Stream.continually(stdErr.readLine()).takeWhile(_ != null).foreach(z => result += z + "\n")
		
		if(result.length > 512){
			log.info(result.substring(0, 512) + "...[has more]...")
		}else{
			log.info(" > " + result)
		}
		
		
		
		return result
	}
}