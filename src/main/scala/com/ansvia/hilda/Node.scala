package com.ansvia.hilda

import scala.actors.Actor
import scala.actors.Actor._
import scala.actors.remote.RemoteActor
import scala.actors.remote.RemoteActor._
import org.slf4j.LoggerFactory
import scala.actors.remote.Node
import scala.actors.AbstractActor

case class HildaNode(routerHostName:String, port:Int, routerName:String, nodeName:String) extends Actor {
	private val log = LoggerFactory.getLogger(getClass)
	RemoteActor.classLoader = getClass().getClassLoader()
	def act(){
		alive(Config.node.getPort)
		register(Symbol(nodeName), self)
		
		val router = select(Node(routerHostName, port), Symbol(routerName))
		link(router)
		
		var rv:Future[Any] = null

		loop {
			react {
				case "register" =>
					
					log.info("registering node...")
					// Register node to router
					
					if(!Config.nodeConfigured){
						throw new Exception("Node not configured, please set it first in `config.xml` file.")
					}
					
					val me = new NodeRef(
								Config.node.getName,
								Config.node.getHost,
								Config.node.getPort,
								Module.getModules.map(_.getName)
							)
					
					rv = (router !! msg.Register(me))
					log.info("status: " + rv().toString)
					
					//router ! msg.Command("listen")
				
				case "unregister" =>
					log.info("unregistering node...")
					// test unregister
					rv = (router !! msg.Unregister(Config.node.getName, Config.node.getHost, Config.node.getPort))
					log.info("status: " + rv().toString)
					
				case "listening" =>
					router ! msg.Command("listen")
					
					
				case "ls-node" =>
					log.info("listing registered nodes...")
					// list nodes
					rv = (router !! msg.Command("ls-node"))
					log.info("nodes: " + rv().toString)
				
				case "stop" =>
					log.info("stoping node `" + nodeName + "`")
					reply(new msg.Reply("ok"))
					exit()
					
				case "ping" =>
					log.info("got ping")
					reply("pong")
				
				case msg.Update(nodeName:String, modName:String) =>
					log.info("in msg.Update(): receiving remote update for `" + modName + "`")
					val engine = new Updater()
					
					engine.ensureConfig()
					
					if(engine.moduleExists(modName)){
						log.info("receiving remote update for `" + modName + "`")
						//engine.doAction(modName)
					}
					//self ! "register"
			}
		}

		
	}
	def getNodeName:String = nodeName
}

case object NodeUtil extends StatusWriter {
	override val ID = "Node"
		
	def startNode(routerHostName:String, routerPort:Int, routerName:String, nodeName:String): HildaNode = {
		
		if(!Config.nodeConfigured){
			throw new Exception("Node not configured, please set it first in `config.xml` file.")
		}
		
		val node = new HildaNode(routerHostName, routerPort, routerName, nodeName)
		node.start()
		
		// register to router
		
		val router = RouterUtil.getRemoteRouter(routerHostName, routerPort, routerName)
		
		status("started.")
		
		node
	}
	
	def getRemoteNode(nodeHost:String, nodePort:Int, nodeName:String):AbstractActor = {
		select(Node(nodeHost, nodePort), Symbol(nodeName))
	}
}
