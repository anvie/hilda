package com.ansvia.hilda

import java.io.File

trait WorkingDir {
	var workingDir:File = null
	
	def setWorkingDir(workDir:String):Int = { 
		workingDir = new File(workDir)
		return if (workingDir.exists()) WorkDirState.WORK_DIR_EXISTS else WorkDirState.WORK_DIR_UNINITIALIZED
	}
}

object WorkDirState {
	val WORK_DIR_EXISTS = 0
	val WORK_DIR_JUST_CREATED = 1
	val WORK_DIR_UNINITIALIZED = 2
}

