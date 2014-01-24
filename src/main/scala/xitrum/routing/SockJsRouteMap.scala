package xitrum.routing

import scala.collection.mutable.{Map => MMap}

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.esotericsoftware.reflectasm.ConstructorAccess

import xitrum.{Config, Log, SockJsAction}

/**
 * "websocket" and "cookieNeeded" members are named after SockJS options, ex:
 * {"websocket": true, "cookie_needed": false, "origins": ["*:*"], "entropy": 123}
 *
 * - websocket: true means WebSocket is enabled
 * - cookieNeeded: true means load balancers needs JSESSION cookie
 */
class SockJsClassAndOptions(
    val actorClass:   Class[_ <: SockJsAction],
    val websocket:    Boolean,
    val cookieNeeded: Boolean
) extends Serializable

class SockJsRouteMap(map: Map[String, SockJsClassAndOptions]) extends Log {
  def logRoutes() {
    // This method is only run once on start, speed is not a problem

    if (!map.isEmpty) {
      val (pathPrefixMaxLength, handlerClassNameMaxLength, websocketOptionMaxLength) =
        map.toList.foldLeft((0, 0, "websocket: true,".length)) {
            case ((pmax, hmax, wmax), (pathPrefix, sockJsClassAndOptions)) =>
          val plen  = pathPrefix.length
          val hlen  = sockJsClassAndOptions.actorClass.getName.length
          val pmax2 = if (pmax < plen) plen else pmax
          val hmax2 = if (hmax < hlen) hlen else hmax
          val wmax2 = if (sockJsClassAndOptions.websocket) wmax else "websocket: false,".length
          (pmax2, hmax2, wmax2)
        }
      val logFormat = "%-" + pathPrefixMaxLength + "s  %-" + handlerClassNameMaxLength + "s  %-" + websocketOptionMaxLength + "s %s"

      val strings = map.map { case (pathPrefix, sockJsClassAndOptions) =>
        logFormat.format(
          pathPrefix,
          sockJsClassAndOptions.actorClass.getName,
          "websocket: " + sockJsClassAndOptions.websocket + ",",
          "cookie_needed: " + sockJsClassAndOptions.cookieNeeded
        )
      }
      log.info("SockJS routes:\n" + strings.mkString("\n"))
    }
  }

  /** Creates actor attached to Config.actorSystem. */
  def createSockJsAction(pathPrefix: String): ActorRef = {
    createSockJsAction(Config.actorSystem, pathPrefix)
  }

  /** Creates actor attached to the given context. */
  def createSockJsAction(context: ActorRefFactory, pathPrefix: String): ActorRef = {
    val sockJsClassAndOptions = map(pathPrefix)
    val actorClass            = sockJsClassAndOptions.actorClass
    context.actorOf(Props(ConstructorAccess.get(actorClass).newInstance()))
  }

  /** @param sockJsHandlerClass Normal SockJsHandler subclass or object class */
  def findPathPrefix(sockJsActorClass: Class[_ <: SockJsAction]): String = {
    val kv = map.find { case (k, v) => v.actorClass == sockJsActorClass }
    kv match {
      case Some((k, v)) => "/" + k
      case None         => throw new Exception("Cannot lookup SockJS URL for class: " + sockJsActorClass)
    }
  }

  def lookup(pathPrefix: String) = map(pathPrefix)
}
