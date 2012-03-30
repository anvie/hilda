package com.ansvia.hilda


trait IPoller {
	val POLLER_NAME = "IPoller"
    var silent:Boolean = false
	  
	var mod:IHildaModule = null
	
	def setModule(mod:IHildaModule) { this.mod = mod }

	def updateAvailable():Boolean
	def updateWorkTree():Boolean
	def getNewChanges():String
	def updateInfo() {
		//status("New update available for `" + name + "` on branch `" + br + "`")
		//status("New commit: " + poller.getNewChanges(workDir))
		//status("Updating...")
	}
	def getCurrentStatus:String
	def status(msg:String) {
		if (!silent)
            println("    [" + POLLER_NAME + "]: " + msg)
	}
	override def toString:String = POLLER_NAME
    def setSilent(state:Boolean){
        silent = state
    }
}


