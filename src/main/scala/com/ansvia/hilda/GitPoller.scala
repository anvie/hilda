package com.ansvia.hilda

import java.io.IOException


case class GitPoller(gitUri:String, branch:String)
        extends IPoller
        with Executor
        with CacheManager
        with Logger {

    override val POLLER_NAME = "(Git) " + gitUri


    var newCommit:String = ""
    var localCommit:String = ""
    var cachedUpdateAvailable = false

    override def setWorkingDir(workDir:String):Int = {
        super.setWorkingDir(workDir)
        var rv = WorkDirState.WORK_DIR_EXISTS
        if(!workingDir.exists()){
            //throw new Exception("Working dir `" + workDir + "` does not exists")
            val cli = new jline.ConsoleReader()
            val createNew = cli.readLine("    Working dir `" + workDir + "` does not exists, create new? [y/n] ")
            createNew.toLowerCase match {
                case "y" =>
                    status("Cloning...")
                    exec("git clone " + gitUri + " " + workDir)
                    rv = WorkDirState.WORK_DIR_JUST_CREATED
            }
        }
        rv
    }

    def updateAvailable():Boolean = {

        if(cachedUpdateAvailable == true) return true

        var rv:Boolean = false
        try {

            exec("git checkout " + branch)
            exec("git fetch origin " + branch)
            newCommit = exec("git rev-list -n1 FETCH_HEAD").trim()
            localCommit = exec("git rev-list -n100 " + branch).trim()

            val revs = localCommit.split("\n").map(z => z.trim())

            if(newCommit.compare(localCommit) != 0){
                rv = !revs.contains(newCommit)
            }

            cachedUpdateAvailable = rv

        } catch {
            case e: IOException =>
                e.printStackTrace()
        }

        rv
    }

    def getNewChanges():String = {
        if(newCommit.length == 0){
            updateAvailable()
        }
        newCommit
    }

    def updateWorkTree():Boolean = {

        if(updateAvailable() == false) return false

        try {

            exec("git checkout " + branch)
            exec("git pull " + gitUri + " " + branch)

        } catch {
            case e: IOException =>
                e.printStackTrace()
                return false
        }

        true
    }

    def getCurrentBranch = {
        val rv = exec("git status")
        rv.split("\n")(0).split(" ")(3).trim()
    }

    def setBranch(br:String, cached:Boolean=true){
        val branch = getCurrentBranch
        if (branch != br){
            var rv = exec("git branch")
            var notFound = true
            rv.split("\n").foreach { b =>
                if(!b.contains("*") && b.endsWith(" " + br)) {
                    rv = exec("git checkout " + br)
                    println(rv)
                    notFound = false
                }
            }
            if(notFound){

                println("Branch doesn't exists `" + br + "` for `" + this.mod.getName() + "`")

                lazy val cacheKey = "chbr-last-fetch-" + this.mod.getName()

                if (cached) {
                    // check from cache

                    cacheGet(cacheKey).getOrElse(None) match {
                        case x:CacheItem if !x.isExpired =>
                            info("No fetch, skiped by cache")
                            return // skip
                        case _ =>
                            fetch(true)
                            checkout(br, false)
                            cacheSet(cacheKey, POLLER_NAME)
                    }
                }else{
                    fetch(true)
                    checkout(br, false)
                    cacheSet(cacheKey, POLLER_NAME)
                }

            }
        }else{
            status("Already on branch `" + br + "`")
        }
    }

    def checkout(branch:String, force:Boolean=false){
        
        val rv = if (force)
                exec("git checkout -f " + branch)
            else
                exec("git checkout " + branch)

        if (rv.contains("error")) {
            println("Branch `%s` not found on remote server".format(branch))
        }

    }
    
    def fetch(all:Boolean, remote:String="origin", branch:String="master"){
        println("Trying to fetch from remote server...")
        
        val rv = if (all)
                exec("git fetch --all")
            else
                exec("git fetch %s %s".format(remote, branch))
        
        println(rv)

    }

    def push(origin:String, br:String){
        var rv:String = "success"
        val branch = getCurrentBranch
        if (branch != br){
            // checkout first
            rv = exec("git checkout " + br)
        }

        if (!rv.toLowerCase.contains("error")){
            rv = exec("git push %s %s".format(origin, br))
            println(rv)
        }
    }

    def pull(origin:String, br:String){
        var rv:String = "success"

        val branch = getCurrentBranch

        if (branch != br){
            // checkout first
            rv = exec("git checkout " + br)
        }

        if (!rv.toLowerCase.contains("error")){
            rv = exec("git pull %s %s".format(origin, br))
            println(rv)
        }
    }

    def getCurrentStatus():String = {
        // bellow line used for test only, show some environment variable
        // inside of /tmp/test.sh: echo $SOME_VAR
        //println(exec(Array("sh","/tmp/test.sh")))
        val rv = exec("git status")
        val modified = rv.contains("modified")
        val branch = rv.split("\n")(0).split(" ")(3)
        "branch: %s, modified: %s".format(branch, if(modified) "true" else "false")
    }



}
