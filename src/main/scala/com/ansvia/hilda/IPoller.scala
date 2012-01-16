package com.ansvia.hilda
import java.io.File



trait IPoller {
	val POLLER_NAME = "IPoller"

	def updateAvailable():Boolean
	def updateWorkTree():Boolean
	def getNewChanges():String
	def updateInfo() = {
		//status("New update available for `" + name + "` on branch `" + br + "`")
		//status("New commit: " + poller.getNewChanges(workDir))
		//status("Updating...")
	}
	def status(msg:String) {
		println("    [" + POLLER_NAME + "]: " + msg)
	}
	override def toString():String = POLLER_NAME
}


