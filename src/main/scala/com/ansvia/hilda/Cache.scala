package com.ansvia.hilda

import java.util.Date
import xml.{XML, Elem, Node}
import org.slf4j.LoggerFactory
import java.io.{FileWriter, File}

/**
 * Copyright (C) 2011-2012 Ansvia Inc.
 * User: robin
 * Date: 3/22/12
 * Time: 9:19 AM
 *
 */

case class CacheItem(name:String, value:String, maxAge:Long, var lastTouch:Long=0) {

    private val log = LoggerFactory.getLogger(getClass)


    def touch(){
        this.lastTouch = (new Date()).getTime
        this.lastTouch
    }
    
    def isExpired = {
        val now = (new Date()).getTime
        //println("%d < %d".format(this.lastTouch + this.maxAge, now))
        (this.lastTouch + this.maxAge) < now
    }

    def toXmlNode:Node = {
        <cache lastTouch={this.lastTouch.toString} name={this.name}
               expired={this.maxAge.toString}>{this.value}</cache>
    }

    def save(fileCache:String):Boolean = {
        try {

            val data = XML.loadFile(fileCache)
            var rawCaches = (data \\ "hilda" \\ "caches" \\ "cache")

            if (rawCaches.length > 0){
                val existing = rawCaches.filter(x => (x \ "@name").text == this.name)
                if (existing.length == 0){
                    rawCaches +:= this.toXmlNode
                }else {
                    rawCaches = Cache.caches.map { ch =>
                        if (ch.name == this.name)
                            this.toXmlNode
                        else
                            ch.toXmlNode
                    }.toSeq
                }
            }else{
                rawCaches = this.toXmlNode
            }

            val f = new FileWriter(fileCache)
            f.write("<hilda>\n  <caches>\n%s\n  </caches>\n</hilda>".format(rawCaches.toString()))
            f.close()
            
        }catch{
            case e: org.xml.sax.SAXParseException =>
                log.error("CacheItem::Save = Cannot load file `" + fileCache + "`. e: " + e)
                false
        }
        true
    }

    def invalidate(fileCache:String):Boolean = {
        
        // remove cache

        try {

            val data = XML.loadFile(fileCache)
            var rawCaches = (data \\ "hilda" \\ "caches" \\ "cache")

            // remove item by filtering inside of seq
            rawCaches = rawCaches.filter( x => (x \ "@name").text != this.name )

            val f = new FileWriter(fileCache)
            f.write("<hilda>\n  <caches>\n%s\n  </caches>\n</hilda>".format(rawCaches.toString()))
            f.close()

        }catch{
            case e: org.xml.sax.SAXParseException =>
                log.error("CacheItem::Save = Cannot load file `" + fileCache + "`. e: " + e)
                false
        }
        true
        
    }
}

trait CacheManager {

    def cacheSet(cacheKey:String, value:String, expired:Long=((6e6 * 60 * 60) * 5).toLong){
        val newCh = CacheItem(cacheKey, value, expired) // 6 seconds in micro
        newCh.touch()
        Cache.save(newCh)
    }

    def cacheGet(cacheKey:String) = Cache.getCache(cacheKey)

}


object Cache {

    private val log = LoggerFactory.getLogger(getClass)
    
    var caches = Array[CacheItem]()
    var initialized = false
    var data:Elem = null
    lazy final val filePath = Hilda.getHildaHome + "/caches.xml"
    
    def init_():Unit = {

        if (!initialized){

            if (!(new File(filePath)).exists()){
                println("File not found: " + filePath)
                println("Create first")
                
                var fileCreated = false
                
                try {
                    val fp = new FileWriter(filePath)
                    fp.write("<hilda>\n  <caches>\n  </caches>\n</hilda>")
                    fp.close()
                    fileCreated = true
                } catch {
                    case e => 
                        log.error(e.getMessage)
                    
                }
                
                if (!fileCreated)
                    return
            }
            
            try {
                this.data = XML.loadFile(filePath)
                initialized = true
            }catch{
                case e:org.xml.sax.SAXParseException =>
                    log.error("Cannot parse `%s`".format(filePath))
                    log.error(e.getMessage)
            }
            
            if (initialized){
                caches = (this.data \\ "hilda" \\ "caches" \\ "cache")
                    .map( ch => CacheItem( (ch \ "@name").text,
                        ch.text,
                        (ch \ "@expired").text.toLong,
                        (ch \ "@lastTouch").text.toLong ) ).toArray
            }
        }
    }

    def reloadCache() {
        initialized = false
        init_()
    }
    
    def getCache(name:String):Option[CacheItem] = {
        
        init_()
        
        for( c <- caches ){
            if (c.name == name){
                return Some(c)
            }
        }

        None
    }

    def save(ch:CacheItem) = ch.save(filePath)

    def invalidate(ch:CacheItem) = ch.invalidate(filePath)

}
