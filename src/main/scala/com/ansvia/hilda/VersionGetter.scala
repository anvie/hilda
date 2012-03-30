package com.ansvia.hilda

import java.io.FileWriter

/**
 * Copyright (C) 2011-2012 Ansvia Inc.
 * User: robin
 * Date: 3/30/12
 * Time: 2:25 PM
 *
 */

trait IVersionGetter {
    def get:String
}

case class ProgramaticVersionGetter(script:String, executor:Executor, logger:ILogger)
        extends MutableLoggerSet(logger)
        with IVersionGetter
        with Beautifier {

    protected val TMP_FILE = "/tmp/hilda-programatic-version-getter.sh"
    private var cachedVersion = "-"
    
    def get:String = {
        if (cachedVersion != "-")
            return cachedVersion
        
        val f = new FileWriter(TMP_FILE)
        f.write(beautifyContent(script))
        f.close()

        val rv = executor.exec(Array[String]("sh", TMP_FILE))

        cachedVersion = rv.stripLineEnd

        cachedVersion
    }
}

case class DefaultVersionGetter(default:String="-") extends IVersionGetter {
    def get = default
}

case class ModUiVersionGetter() extends IVersionGetter {
    private var mod:ui.IModuleUi = null
    def get = {
        mod.getVersion
    }
    def setUiModule(mod0:ui.IModuleUi){
        mod = mod0
    }
    def getUiModule = mod
    def isSet = mod != null
}



