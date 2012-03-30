package com.ansvia.hilda

/**
 * Copyright (C) 2011-2012 Ansvia Inc.
 * User: robin
 * Date: 3/23/12
 * Time: 7:59 PM
 *
 */

import org.slf4j.LoggerFactory

trait ILogger {
    def info(msg:String)
    def error(msg:String)
    def debug(msg:String)
    def warn(msg:String)
}

trait MutableLogger extends ILogger {
    protected var log:ILogger = null

    def isLoggerReady = log != null
    def setLogger(log0:ILogger){ log = log0 }
    def getLogger = log
    def info(msg:String) { log.info(msg) }
    def error(msg:String) { log.error(msg) }
    def debug(msg:String) { log.debug(msg) }
    def warn(msg:String) { log.warn(msg) }
}

trait Slf4jLogger extends ILogger {
    private final val log = LoggerFactory.getLogger(getClass)
    def info(msg:String) = log.info(msg)
    def error(msg:String) {
        log.error(msg)
    }
    def debug(msg:String) {
        log.debug(msg)
    }
    def warn(msg:String) {
        log.warn(msg)
    }
}

trait OffensiveLogger extends ILogger {
    def info(msg: String) { println("[INFO] " + msg) }
    def error(msg: String) { println("[ERROR] " + msg) }
    def debug(msg: String) { println("[DEBUG] " + msg) }
    def warn(msg: String) { println("[WARN] " + msg) }
}

class MutableLoggerSet(log0:ILogger) extends MutableLogger { log = log0 }
