package com.ansvia.hilda

import scala.xml.Node
import scala.xml.XML
import scala.collection.mutable.HashMap
import org.slf4j.LoggerFactory

trait Translator extends {
	private var data:Node = null
	private var initialized = false
	private var dataSet:HashMap[String, String] = null
	private val log = LoggerFactory.getLogger(getClass)

	def setDataset(data:HashMap[String, String]) = dataSet = data
	protected def validValue(value:String):Boolean = { value.startsWith("${") && value.endsWith("}") }
	def translate(value:String, safe:Boolean=false):String = {
		
		if(dataSet == null) {
			if(safe==true) return value
			throw new Exception("dataSet is empty")
		}
		
		if(!validValue(value)){
			if(safe==true) return value
			throw new Exception("Invalid value to translate `" + value + "`")
		}
		
		// extract
		var key = value.substring(2, value.length() - 1)
		val rv = dataSet(key)
		return rv
	}
	
	/**
	 * @TODO: make it better
	 */
	def translateAll(input:String):String = {
		if(dataSet == null)
			throw new Exception("dataSet is empty")
		
		var rv = Array[String]()
		
		input.split("\n").foreach { line =>
			var replaced = line
			dataSet.foreach { case (k, v) =>
				replaced = replaced.replace("${%s}".format(k), v)
			}
			rv :+= replaced.trim()
		}
		
		return rv.reduce(_+ "\n" +_)
	}
}
