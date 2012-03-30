package com.ansvia.hilda

import scala.actors.Actor
import scala.actors.Actor._
import scala.actors.remote.RemoteActor
import scala.actors.remote.RemoteActor._
import org.slf4j.LoggerFactory
import scala.actors.remote.Node
import scala.actors.AbstractActor
import scala.actors.OutputChannel
import scala.Serializable

object msg {
	
	sealed abstract class Message
	case class Update(nodeName:String, name:String) extends Message
	case class Rollback(modName:String) extends Message
	case class Reply(status:String) extends Message {
		override def toString = status
	}
	case class Register(newNode:NodeRef) extends Message {
		override def toString = "[Node: " + newNode.getName + "]"
	}
	case class Unregister(nodeName:String, nodeHost:String, nodePort:Int) extends Message {
		override def toString = "[Node: " + nodeName + "]"
	}
	case class Command(cmd:String) extends Message

}

case class NodeRef(name:String, host:String, port:Int, modules:Array[String]) { 
	def getName = name
	def getHost = host
	def getPort = port
	override def toString:String = "<Node:[%s@%s:%d]>".format(name, host, port)
}

case class Router(ID:String, port:Int) extends Actor {
	private val log = LoggerFactory.getLogger(getClass)
	
	private var nodes = List[NodeRef]()
	
	RemoteActor.classLoader = getClass().getClassLoader()
	def act() {
		alive(4912)
		register(Symbol(ID), self)
		
		loop {
			react {
				case msg.Update(nodeName:String, modName:String) => 
					log.info("Will update `" + nodeName + "` module: `" + modName + "`")
					
					// broadcast to all nodes
					
					log.info("nodes.length = " + nodes.length.toString)
					
					nodes
					.filter(n => n.getName == nodeName && n.modules.contains(modName))
					.foreach { n =>
						log.info("sending update command to `" + n + "`")
						val remoteNode = NodeUtil.getRemoteNode(n.getHost, n.getPort, n.getName)
						//remoteNode ! msg.Update(nodeName, modName)
						remoteNode ! "ping"
					}

					// remove node
					//nodes = nodes.filter(_.getName != name)
					
					//reply(new msg.Reply("ok"))
					
				case msg.Register(newNode:NodeRef) =>
					
					//val newNode = new NodeRef(name, host, port, modules)
					nodes ::= newNode
					log.info("New node registered `" + newNode + "`")
					
					log.info("Node modules:")
					newNode.modules.foreach(n=>log.info("   * " + n))
					
					reply(new msg.Reply("ok"))
				
				case msg.Unregister(name:String, host:String, port:Int) =>
					nodes = nodes.filterNot{n => 
						n.getName != name &&
						n.getHost != host &&
						n.getPort != port
					}
					log.info("Node unregistered `" + name + "`")
					reply(new msg.Reply("ok"))
				
				case msg.Command(cmd:String) =>
					log.info("Received command `" + cmd + "`")
					cmd match {
						case "ls-node" =>
							reply(new msg.Reply( if(nodes.length > 0) nodes.map(_.name.toString).reduce(_+ ", " + _) else "<empty>" ))
						case "listen" =>
							log.info("some node is now listening.")
					}
					
			}
		}
	}
}


case object RouterUtil extends StatusWriter {
	override val ID = "Router"
		
	var cached = Map[String, AbstractActor]() 
	
	def startRouter(name:String, port:Int){
		val router = new Router(name, port)
		router.start()
		status("started.")
	}
	
	def getRemoteRouter(host:String, port:Int, name:String):AbstractActor = {
		
		val cacheKey = "%s-%d-%s".format(host, port, name)
		if(cached.contains(cacheKey)){
			return cached(cacheKey)
		}
		
		val peer = Node(host, port)
		val router = select(peer, Symbol(name))
		//link(router)
		
		//val rv = router.asInstanceOf[Proxy]
		
		cached += cacheKey -> router
		
		router
	}

	
	
}

