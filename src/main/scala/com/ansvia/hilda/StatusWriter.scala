package com.ansvia.hilda

import org.slf4j.LoggerFactory


trait StatusWriter {
	val ID = "StatusWriter"
	private val log = LoggerFactory.getLogger(getClass)
	def status(msg:String) {
		log.info("[" + ID + "]: " + msg)
	}
}