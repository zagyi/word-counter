package com.github.zagyi.wordcounter

import java.net.InetSocketAddress
import java.nio.charset.{ Charset, StandardCharsets }

import com.github.zagyi.wordcounter.Model.Window
import com.github.zagyi.wordcounter.Repository.Repository
import com.github.zagyi.wordcounter.config.Config
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import uzhttp.Request.Method.GET
import uzhttp.server.Server
import uzhttp.{ Response, Status }
import zio._
import zio.blocking.Blocking
import zio.clock.Clock

object UZServer {

  case class WindowResourceWrapper(window: WindowResource)
  case class WindowResource(start: Long, length: Long, wordCountMap: Window)

  implicit val windowInfoEncoder: Encoder[WindowResource]               = deriveEncoder
  implicit val windowInfoWrapperEncoder: Encoder[WindowResourceWrapper] = deriveEncoder

  type UZServer = Has[Server]

  def awaitUp = ZIO.service[Server].map(_.awaitUp)

  def serverLayer(port: Int): ZLayer[
    Config with Repository with Blocking with Clock,
    Throwable,
    Has[Server]
  ] =
    ZLayer.fromManaged(
      Server
        .builder(new InetSocketAddress("127.0.0.1", port))
        .handleSome { case req if req.method == GET => getAllWindows }
        .serve
    )

  private val getAllWindows =
    for {
      windowSize <- Config.windowSize
      windows    <- Repository.get
    } yield {
      val response =
        windows.map {
          case (start, window) =>
            WindowResourceWrapper(
              WindowResource(start, windowSize, window)
            )
        }
      json(response.asJson.toString())
    }

  private def json(
    body: String,
    status: Status = Status.Ok,
    headers: List[(String, String)] = Nil,
    charset: Charset = StandardCharsets.UTF_8
  ): Response =
    Response.const(
      body.getBytes(charset),
      status,
      contentType = s"application/json; charset=${charset.name()}",
      headers = headers
    )

}
