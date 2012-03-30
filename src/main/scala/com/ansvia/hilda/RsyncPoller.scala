package com.ansvia.hilda
import java.io.IOException

abstract class RsyncSource {
	def kind:String = null
	def getShellCommand(dest:String):Array[String]
}

case class RsyncHostedSource(host:String, port:Int) extends RsyncSource {
	override def kind:String = "hosted"
	def getShellCommand(dest:String) = Array("rsync", "-avzrh", host + ":" + port, dest)
}

case class SSHHostedSource(host:String, port:Int) extends RsyncSource {
	override def kind:String = "ssh-hosted"
	def getShellCommand(dest:String) = Array("rsync", "-avzrh", "--rsh=ssh -p" + port, host, dest)
}

case class RsyncLocalSource(src:String) extends RsyncSource {
	override def kind:String = "local"
	def getShellCommand(dest:String) = Array("rsync", "-avzrh", src, dest)
}

case class RsyncPoller(source:RsyncSource) extends IPoller with Executor {
	
	override val POLLER_NAME = "Rsync"
	
	def updateAvailable():Boolean = true
	def updateWorkTree():Boolean = {
		
		try {
		
			exec(source.getShellCommand(workingDir.getAbsolutePath()))
			
		} catch {
			case e: IOException =>
				e.printStackTrace()
				return false
		}
		
		return true
	}
	def getNewChanges():String = null
	def getCurrentStatus = "Rsync poller doesn't support get current status"
}
