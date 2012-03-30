package com.ansvia.hilda.ui

import xml.{XML, Elem}
import com.ansvia.hilda._

/**
 * Copyright (C) 2011-2012 Ansvia Inc.
 * User: robin
 * Date: 3/30/12
 * Time: 12:36 PM
 *
 */


trait IModuleUi {

    def getVersion:String
    def getName:String
    def getState:Seq[String]
    def getRawModule:IHildaModule
}

case class GitModule(mod:IHildaModule) extends IModuleUi with MutableLogger {
    
    protected var manifestData:Elem = null
    protected var state = Seq[String]()
    private var manifestFile = ""
    private var poller:GitPoller = null
    
    // use module poller logger
    mod.getPoller match {
        case p:GitPoller =>
            poller = p

            setLogger(p.getLogger)
            
            manifestFile = p.getWorkingDir + "/hilda.xml"
            
            loadManifest(manifestFile)
    }
    
    def getName = mod.getName

    protected def loadManifest(manifestFile:String):Option[Elem] = {
        
        if (manifestData != null)
            return Some(manifestData)

        try {
            val data = XML.loadFile(manifestFile)
            manifestData = data
            Some(data)
        } catch {
            case e: org.xml.sax.SAXParseException =>
                error("Cannot parse xml")
                error(e.toString)
                None
            case e: java.io.FileNotFoundException =>
                warn("Cannot open file " + manifestFile)
                None
            case e =>
                error(e.getMessage)
                None
        }

    }

    def getVersion:String = {
        var modVersion = "-"
        loadManifest(manifestFile) match {
            case Some(d:Elem) =>
                modVersion = (d \\ "hilda" \ "version" \ "@type").text match {
                    case "programatic" =>
                        val script = (d \\ "hilda" \ "version").text
                        val executor = new Executor { setWorkingDir(poller.getWorkingDir) }
                        val pvg = new ProgramaticVersionGetter(script, executor, log)
                        pvg.get
                    case _ =>
                        if ((d \\ "hilda" \ "version").text.length() > 0)
                            (d \\ "hilda" \ "version").text
                        else
                            "-"
                }
            case None =>

                modVersion = "-"

        }
        modVersion
    }

    def getState:Seq[String] = {
        if (state.length > 0){
            return state
        }
        mod.getPoller match {
            case p:GitPoller =>
                state = p.getCurrentStatusList
            case _ =>
                state = Seq[String]()
        }
        state 
    }

    def getRawModule = mod
}

/*
object Module {
    def toUi(mod:IHildaModule) = GitModule(mod)
}
*/
