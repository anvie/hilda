package com.ansvia.hilda

import java.io.FileWriter
import java.io.File
import java.io.IOException
import org.slf4j.LoggerFactory


case class GitPoller(gitUri:String, branch:String) extends IPoller with Executor {
	
	override val POLLER_NAME = "(Git) " + gitUri

	private val log = LoggerFactory.getLogger(getClass)
	
	var newCommit:String = ""
	var localCommit:String = ""
	var cachedUpdateAvailable = false
	
	override def setWorkingDir(workDir:String):Int = {
		super.setWorkingDir(workDir)
		var rv = WorkDirState.WORK_DIR_EXISTS
		if(!workingDir.exists()){
			//throw new Exception("Working dir `" + workDir + "` does not exists")
			var cli = new jline.ConsoleReader() 
			var createNew = cli.readLine("    Working dir `" + workDir + "` does not exists, create new? [y/n] ")
			createNew.toLowerCase() match {
				case "y" =>
					status("Cloning...")
					exec("git clone " + gitUri + " " + workDir)
					rv = WorkDirState.WORK_DIR_JUST_CREATED
			}
		}
		return rv
	}
		
	def updateAvailable():Boolean = {
		
		if(cachedUpdateAvailable == true) return true
		
		var rv:Boolean = false
		try {
			
			exec("git checkout " + branch)
			exec("git fetch origin " + branch)
			newCommit = exec("git rev-list -n1 FETCH_HEAD").trim()
			localCommit = exec("git rev-list -n100 " + branch).trim()
			
			var revs = localCommit.split("\n").map(z => z.trim())
			
			if(newCommit.compare(localCommit) != 0){
				rv = !revs.contains(newCommit)
			}
			
			cachedUpdateAvailable = rv
			
		} catch {
			case e: IOException =>
				e.printStackTrace()
		}
		
		return rv
	}
	
	def getNewChanges():String = {
		if(newCommit.length == 0){
			updateAvailable()
		}
		return newCommit
	}
	
	def updateWorkTree():Boolean = {
		
		if(updateAvailable() == false) return false
		
		try {
		
			exec("git checkout " + branch)
			exec("git pull " + gitUri + " " + branch)
			
		} catch {
			case e: IOException =>
				e.printStackTrace()
				return false
		}
		
		return true
	}
	
	def setBranch(br:String){
	  var rv = exec("git status")
	  val branch = rv.split("\n")(0).split(" ")(3).trim()
	  if (branch != br){
	    rv = exec("git branch")
	    var notFound = true
	    rv.split("\n").foreach { b =>
	      if(!b.contains("*") && b.endsWith(" " + br)) {
	        rv = exec("git checkout " + br)
	        println(rv)
	        notFound = false
	      }
	    }
	    if(notFound){
	      println("! Branch doesn't exists `" + br + "` for `" + this.mod.getName() + "`")
	      println("Trying to fetch from remote server...")
	      rv = exec("git fetch --all")
	      println(rv)
	      rv = exec("git checkout " + br)
	      println(rv)
	    }
	  }else{
	    status("Already on branch `" + br + "`")
	  }
	}

	def getCurrentStatus():String = {
		val rv = exec("git status")
		val modified = rv.contains("modified")
		val branch = rv.split("\n")(0).split(" ")(3)
		"branch: %s, modified: %s".format(branch, if(modified) "true" else "false")
	}
		
		
	
}

