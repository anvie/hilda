package com.ansvia.hilda

trait Commander {
    def processCommand(args:Array[String]):Int
}

class BaseCommander(protected val prefix:String) extends Commander {

    lazy final val HELP = getCmd("help")

    /**
     * method processCommand should override by subclass
     */
    def processCommand(args:Array[String]):Int = Error.NOT_IMPLEMENTED
    def getCmd(cmd:String) = prefix + ":" + cmd
}

object Commander {
    def processCommand(args:Array[String]):Int = {
        val rv = GitCommander.processCommand(args)
        return rv
    }
}


