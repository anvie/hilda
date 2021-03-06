package com.ansvia.hilda

import java.io.IOException

/**
 * Git poller engine.
 * @param gitUri git URI.
 * @param branch git branch.
 */
case class GitPoller(gitUri:String, branch:String)
        extends IPoller
        with Executor
        with CacheManager
        with MutableLogger {

    override val POLLER_NAME = "(Git) " + gitUri


    var newCommit:String = ""
    var localCommit:String = ""
    var cachedUpdateAvailable = false
    private final var forceUpdate = false
//    var quiet = false

    if (!isLoggerReady){
        // if no any logger binded yet use Slf4jLogger as default.
        object slf4log extends Slf4jLogger {}
        log = slf4log
    }

    /**
     * set current poller working dir
     * if directory doesn't exists then create it first
     * directory creation is recursive.
     * @param workDir working directory path.
     * @return
     */
    override def setWorkingDir(workDir:String):Int = {
        super.setWorkingDir(workDir)
        var rv = WorkDirState.WORK_DIR_EXISTS
        if(!workingDir.exists()){
            log.info("log file doesn't exists: " + workingDir.getAbsolutePath)
            if (mod.isQuiet){
                status("Cloning...")
                exec("git clone " + gitUri + " " + workDir)
                rv = WorkDirState.WORK_DIR_JUST_CREATED
            }else{
                val cli = new jline.ConsoleReader()
                val createNew = cli.readLine("    Working dir `" + workDir + "` does not exists, create new? [y/n] ")
                createNew.toLowerCase match {
                    case "y" =>
                        status("Cloning...")
                        exec("git clone " + gitUri + " " + workDir)
                        rv = WorkDirState.WORK_DIR_JUST_CREATED
                    case _ =>
                        status("Aborted.")
                }
            }
        }
        rv
    }


    /**
     * Check whether update is available from
     * git repository.
     * @return
     */
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

    /**
     * Get latest changes.
     * @return
     */
    def getNewChanges():String = {
        if(newCommit.length == 0){
            updateAvailable()
        }
        newCommit
    }

    /**
     * Set to force update.
     * @param state state
     */
    def setForceUpdate(state:Boolean){
        this.forceUpdate = state
    }

    /**
     * Update current working tree.
     * @return boolean
     */
    def updateWorkTree():Boolean = {

        if(updateAvailable() == false) return false

        try {

            var rv:String = ""
            if (this.forceUpdate){
                rv = exec("git checkout -f " + branch)
                rv = exec("git reset --hard HEAD")
            }else
                rv = exec("git checkout " + branch)

            if (rv.contains("error")){
                log.error("Cannot update working tree, cannot checkout. " + rv)
                return false
            }
            rv = exec("git pull " + gitUri + " " + branch)

            if (rv.contains("error")){
                log.error("Cannot update working tree, cannot pull. " + rv)
                return false
            }

        } catch {
            case e: IOException =>
                e.printStackTrace()
                return false
        }

        true
    }

    /**
     * Get current active branch on current module.
     * @return
     */
    def getCurrentBranch = {
        val rv = exec("git status")
        rv.split("\n")(0).split(" ")(3).trim()
    }

    /**
     * Set active branch.
     * @param br branch name.
     * @param cached if true then don't fetch from remote repo if no branch exists.
     */
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

                println("Branch doesn't exists `" + br + "` for `" + this.mod.getName + "`")

                lazy val cacheKey = "chbr-last-fetch-" + this.mod.getName

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

    def getCurrentStatus:String = {
        // bellow line used for test only, show some environment variable
        // inside of /tmp/test.sh: echo $SOME_VAR
        //println(exec(Array("sh","/tmp/test.sh")))
        val Seq(branch, modified) = getCurrentStatusList
        "branch: %s, modified: %s".format(branch, modified)
    }

    def getCurrentStatusList:Seq[String] = {
        info("Getting module status for `%s`...".format(this.mod.getName))
        val rv = exec("git status")
        val modified = if (rv.contains("modified"))
            "true"
        else
            "false"
        val branch = rv.split("\n")(0).split(" ")(3)
        Seq(branch, modified)
    }

}
