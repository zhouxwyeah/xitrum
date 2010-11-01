package xt.framework

import java.lang.reflect.Method
import scala.collection.mutable.{Map, HashMap}

import org.jboss.netty.channel.Channel
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse}

import xt.middleware.App

/**
 * This app should be put behind middlewares:
 * Static -> ParamsParser -> MethodOverride -> Dispatcher -> Failsafe -> XTApp
 */
class XTApp extends App {
  def call(remoteIp: String, channel: Channel, request: HttpRequest, response: HttpResponse, env: Map[String, Any]) {
    val controller = env("controller").asInstanceOf[Controller]
    val action     = env("action").asInstanceOf[Method]

    // setRefs
    val paramsMap = env("params").asInstanceOf[java.util.Map[String, java.util.List[String]]]
    val atMap = new HashMap[String, Any]
    controller.setRefs(remoteIp, channel, request, response, env, paramsMap, atMap)

    if (controller.beforeFilter) action.invoke(controller)
  }
}
