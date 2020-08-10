package com.github.zagyi.wordcounter

import zio._

object config {

  type Config = Has[Config.Service]

  object Config {
    trait Service {
      def windowSizeInSec: Long
    }

    case class Config0(windowSizeInSec: Long) extends Service

    def windowSize: URIO[Config, Long] =
      ZIO.service[Service].map(m => m.windowSizeInSec)

    def layer(windowSizeInSec: Long): Layer[Any, Config] =
      ZLayer.succeed(
        Config0(windowSizeInSec)
      )
  }
}
