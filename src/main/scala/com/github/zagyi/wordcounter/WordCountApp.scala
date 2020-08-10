package com.github.zagyi.wordcounter

import java.util.concurrent.TimeUnit.SECONDS

import com.github.zagyi.wordcounter.Repository.repositoryLayer
import com.github.zagyi.wordcounter.WordCounter._
import com.github.zagyi.wordcounter.config.Config
import zio._
import zio.clock.currentTime
import zio.console.getStrLn
import zio.stream.{ Stream, ZStream }

object WordCountApp extends App {

  val managedApp =
    for {
      startTime <- currentTime(SECONDS).toManaged_
      _ <- ZStream
            .mergeAllUnbounded()(
              ZStream.fromEffect(UZServer.awaitUp),
              Stream
                .repeatEffect(getStrLn)
                .via(countWords(startTime)(_))
                .drain
            )
            .runDrain
            .toManaged_
    } yield ()

  val main = managedApp.use_(ZIO.unit)

  val configLayer = Config.layer(windowSizeInSec = 30)

  val appLayer =
    (configLayer ++ repositoryLayer ++ ZEnv.any) >>>
      WordCounter.live ++ UZServer.serverLayer(8080)

  override def run(args: List[String]) =
    main
      .provideCustomLayer(appLayer)
      .exitCode
}
