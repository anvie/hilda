package com.ansvia.hilda

trait Commander {
	def processCommand(args:Array[String]):Int
}

class BaseCommander(protected val prefix:String) extends Commander {
  
  /**
   * method processCommand should override by subclass
   */
  def processCommand(args:Array[String]):Int = Error.NOT_IMPLEMENTED
  def getCmd(cmd:String) = prefix + ":" + cmd
}


object GitCommander extends BaseCommander("git") {
  
  lazy final val CHBR = getCmd("chbr")
  lazy final val HELP = getCmd("help")
  
  override def processCommand(args:Array[String]):Int = {
    var rv:Int = Error.UNKNOWN_ERROR
    val cmds = args.toList
    
	cmds(0) match {
		case CHBR =>
		    if (args.length > 1){
		      
				Module.getModules().foreach { mod =>
				  val br = cmds(1).trim()
				  println("Set branch `%s` for `%s`...".format(br, mod.getName()))
				  mod.getPoller match {
				    case p:GitPoller => p.setBranch(br)
				    case _ => println(mod.getPoller) 
				  }
				}
				
				rv = Error.SUCCESS
		    }
		case HELP =>
		  val info = "Hilda Git Commander\n" +
				  	 "Usage:\n" +
				  	 "   git:chbr [BRANCH-NAME] --- Change branch for all modules to :BRANCH-NAME.\n" +
				  	 "   git:help --- Show this help and exit.\n"
		  println(info)
		  rv = Error.SUCCESS
	}
    return rv
  }
}

object Commander {
  def processCommand(args:Array[String]):Int = {
    val rv = GitCommander.processCommand(args)
    return rv
  }
}


