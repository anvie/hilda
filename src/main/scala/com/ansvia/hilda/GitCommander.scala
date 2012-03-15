package com.ansvia.hilda

/**
 * Copyright (C) 2011-2012 Ansvia Inc.
 * User: robin
 * Date: 3/15/12
 * Time: 9:26 PM
 */

object GitCommander extends BaseCommander("git") {

  lazy final val CHBR = getCmd("chbr")
  lazy final val PUSH = getCmd("push")

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
      case PUSH =>
        if (args.length > 2){

          Module.getModules().foreach { mod =>
            val origin = cmds(1).trim()
            val br = cmds(2).trim()
            println("[%s] push `%s` -> `%s`...".format(mod.getName(), origin, br))
            mod.getPoller match {
              case p:GitPoller => p.push(origin, br)
              case _ => println(mod.getPoller)
            }
          }

          rv = Error.SUCCESS
        }
      case HELP =>
        val info = "Hilda Git Commander\n" +
          "Usage:\n" +
          "   git:chbr [BRANCH] -- Change branch for all modules to :BRANCH.\n" +
          "   git:push [REMOTE] [BRANCH] -- Push all modules to :REMOTE :BRANCH\n" +
          "   git:help -- Show this message and exit.\n"
        println(info)
        rv = Error.SUCCESS
      case _ =>
    }
    return rv
  }
}