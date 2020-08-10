package com.github.zagyi

import com.github.zagyi.wordcounter.Model._
import com.github.zagyi.wordcounter.Repository.Repository
import com.github.zagyi.wordcounter.config.Config
import io.circe.parser.parse
import zio.stream.ZStream
import zio.{ Has, Ref, ZLayer }

import scala.collection.immutable.SortedMap

package object wordcounter {

  type WordCounter = Has[WordCounter.Service]

  object WordCounter {

    trait Service {
      def countWords[R, E](
        startTimeEpochSec: Long
      )(
        stream: ZStream[R, E, String]
      ): ZStream[R, E, Windows]
    }

    def countWords[R, E](
      startTimeEpochSec: Long
    )(
      stream: ZStream[R, E, String]
    ): ZStream[R with WordCounter, E, Windows] =
      ZStream.accessStream(
        _.get[Service]
          .countWords(startTimeEpochSec)(stream)
      )

    def live: ZLayer[Config with Repository, Nothing, WordCounter] =
      ZLayer.fromServices[Config.Service, Ref[Windows], Service] {
        case (config, repository) =>
          new Live(config.windowSizeInSec, repository)
      }

    class Live(windowSizeInSec: Long, repository: Ref[Windows]) extends Service {
      def countWords[R, E](
        startTimeEpochSec: Long
      )(
        stream: ZStream[R, E, String]
      ): ZStream[R, E, Windows] =
        WordCounter
          .countWords(startTimeEpochSec, windowSizeInSec)(stream)
          .tap(repository.set)
    }

    def countWords[R, E](
      startTimeEpochSec: Long,
      windowSizeInSec: Long
    )(
      stream: ZStream[R, E, String]
    ): ZStream[R, E, Windows] =
      stream
        .map(parse(_).flatMap(_.as[Event]))
        .collect { case Right(event) => event }
        .mapAccum(Windows.empty) {
          case (windows, event) =>
            import cats.implicits._
            import com.softwaremill.quicklens._

            val eventWordCount =
              SortedMap.from(
                event.data.split("\\s+").groupMapReduce(identity)(_ => 1)(_ + _)
              )

            val eventWindow =
              windowStart(startTimeEpochSec, windowSizeInSec, event.timestamp)

            val windowsUpdated =
              windows
                .modify(
                  _.atOrElse(eventWindow, Window.empty)
                    .atOrElse(event.event_type, WordCount.empty)
                )
                .using(_ |+| eventWordCount)

            (windowsUpdated, windowsUpdated)
        }

    /**
     * Computes the start of the window that the timestamp `t` falls into.
     * For example, when the start time (`t0`) is 100 and the window size (`w`)
     * is 20, then the window of timestamp 131 started at 120, while the window
     * of timestamp 99 started at 80.
     *
     * @param t0 start of the first window
     * @param w  size of the window
     * @param t  timestamp (may be less than t0)
     * @return start of the window where `t` belongs to
     */
    def windowStart(t0: Long, w: Long, t: Long): Long = {
      val elapsed        = t - t0
      val positiveModulo = (elapsed % w + w) % w
      t - positiveModulo
    }
  }
}
