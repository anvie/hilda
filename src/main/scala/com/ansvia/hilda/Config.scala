package com.ansvia.hilda

import scala.xml.Node
import scala.xml.XML
import org.slf4j.LoggerFactory

object Config {
	private val log = LoggerFactory.getLogger(getClass)
	private var data:Node = null
	private var initialized = false
	private var nodeInfo:NodeInfo = null
	
	def setConfig(confFile:String):Boolean = {
		try {
			
			data = XML.loadFile(confFile)
			
			val hilda = (data \\ "hilda")
			nodeInfo = new NodeInfo((hilda \ "node" \ "name").text, (hilda \ "node" \ "host").text)
			
			initialized = true
			
		} catch {
			case e: org.xml.sax.SAXParseException => 
				log.error("Cannot parse xml", e)
			case e =>
				System.err.println("Cannot load config file. " + e.getMessage)
		}
		initialized
	}
	
	def router:String = (data \\ "hilda" \ "router").text
	def verbosity:Int = (data \\ "hilda" \ "verbosity").text.toInt
	def node:NodeInfo = nodeInfo
	def nodeConfigured:Boolean = (nodeInfo != null && (nodeInfo.getHost != null || nodeInfo.getName != null))
}

sealed class NodeInfo(name:String, host:String) {
	
	private val rv = host.split(":")
	private val _host = rv(0)
	private val port = if(rv.length > 1) rv(1).toInt else 4912 
	
	def getName = name
	def getHost = _host
	def getPort = port
}

