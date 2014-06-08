package xitrum

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{ChannelInitializer, ChannelPipeline}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.ResourceLeakDetector

import xitrum.handler.{
  DefaultHttpChannelInitializer,
  FlashSocketPolicyServer,
  NetOption,
  SslChannelInitializer
}

import xitrum.handler.inbound.Dispatcher
import xitrum.metrics.MetricsManager
import xitrum.sockjs.{SockJsAction => SA}

object Server extends Log {
  /**
   * Starts with the default ChannelInitializer provided by Xitrum.
   */
  def start() {
    start(new DefaultHttpChannelInitializer)
  }

  /**
   * Starts with your custom ChannelInitializer. For an example, see
   * xitrum.handler.DefaultHttpChannelInitializer.
   * SSL codec handler will be automatically prepended for HTTPS server.
   */
  def start(httpChannelInitializer: ChannelInitializer[SocketChannel]) {
    Config.xitrum.loadExternalEngines()

    // Trick to start actorRegistry on startup
    SA.entropy()

    if (Config.xitrum.metrics.isDefined) MetricsManager.start()

    Config.routes.logAll()

    // By default, ResourceLeakDetector is enabled and at SIMPLE level
    // http://netty.io/4.0/api/io/netty/util/ResourceLeakDetector.Level.html
    if (!Config.productionMode) ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)

    // Lastly, start the server(s) after necessary things have been prepared
    val portConfig = Config.xitrum.port
    if (portConfig.http.isDefined)  doStart(false, httpChannelInitializer)
    if (portConfig.https.isDefined) doStart(true,  httpChannelInitializer)

    // Flash socket server may reuse HTTP port
    if (portConfig.flashSocketPolicy.isDefined) {
      if (portConfig.flashSocketPolicy != portConfig.http) {
        FlashSocketPolicyServer.start()
      } else {
        log.info("Flash socket policy file will be served by the HTTP server")
      }
    }

    if (Config.productionMode)
      log.info("Xitrum started in production mode")
    else
      log.info(s"Xitrum started in development mode; routes and classes in directory ${Dispatcher.DEVELOPMENT_MODE_CLASSES_DIR} will be reloaded")

    // This is a good timing to warn
    Config.warnOnDefaultSecureKey()
  }

  private def doStart(https: Boolean, nonSslChannelInitializer: ChannelInitializer[SocketChannel]) {
    val channelInitializer =
      if (https)
        new SslChannelInitializer(nonSslChannelInitializer)
      else
        nonSslChannelInitializer

    val bossGroup   = new NioEventLoopGroup(1)
    val workerGroup = new NioEventLoopGroup
    val bootstrap   = new ServerBootstrap
    bootstrap.group(bossGroup, workerGroup)
             .channel(classOf[NioServerSocketChannel])
             .childHandler(channelInitializer)

    val portConfig      = Config.xitrum.port
    val (service, port) = if (https) ("HTTPS", portConfig.https.get) else ("HTTP", portConfig.http.get)

    NetOption.setOptions(bootstrap)
    NetOption.bind(service, bootstrap, port, bossGroup, workerGroup)

    Config.xitrum.interface match {
      case None =>
        log.info(s"$service server started on port $port")

      case Some(hostnameOrIp) =>
        log.info(s"$service server started on $hostnameOrIp:$port")
    }
  }
}
