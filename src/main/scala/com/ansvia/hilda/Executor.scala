package com.ansvia.hilda

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.File
import org.slf4j.LoggerFactory;

trait Executor extends WorkingDir {
	
	private val log = LoggerFactory.getLogger(getClass)
    private lazy val SystemEnv = System.getenv()
	protected lazy val ENV_VARS:Array[String] =
            (Array[String]("PATH=/bin:/usr/bin:/usr/local/bin:/opt/bin:/opt/local/bin") ++
            SystemEnv.keySet().toArray.map { key =>
                "%s=%s".format(key, SystemEnv.get(key))
            });

	def exec(cmd:String):String = exec(cmd.split(" "))
	
	def exec(cmd:Array[String]):String = {
		val rt = Runtime.getRuntime()
		
		if(workingDir == null){
			log.error("Executor.workDir not initialized")
			return "Aborted"
		}

        log.info("ENVIRONMENT VARIABLES:")
        log.info(ENV_VARS.foldLeft("")(_ + _ + "\n"))
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

		result
	}
}