package com.ansvia.hilda

/**
 * Copyright (C) 2011-2012 Ansvia Inc.
 * User: robin
 * Date: 3/23/12
 * Time: 7:59 PM
 *
 */

import org.slf4j.LoggerFactory


trait Logger {
    private final val log = LoggerFactory.getLogger(getClass)
    def info(msg:String) = log.info(msg)
    def error(msg:String) = log.error(msg)
    def debug(msg:String) = log.debug(msg)
    def warn(msg:String) = log.warn(msg)
}

