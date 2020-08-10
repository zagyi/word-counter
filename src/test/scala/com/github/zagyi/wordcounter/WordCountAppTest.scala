package com.github.zagyi.wordcounter

import com.github.zagyi.wordcounter.Repository.repositoryLayer
import com.github.zagyi.wordcounter.WordCountAppTest.Generators.{ positiveInt, timestamp, windowNumber, windowSize }
import com.github.zagyi.wordcounter.config.Config
import sttp.client._
import sttp.client.asynchttpclient.zio.{ AsyncHttpClientZioBackend, SttpClient }
import sttp.model.StatusCode
import zio.Chunk
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test._
import zio.test.environment.{ testEnvironment, TestConsole }

object WordCountAppTest extends DefaultRunnableSpec {

  object Generators {
    val windowSize   = Gen.long(1, 100)
    val windowNumber = Gen.long(-100, 100)
    val timestamp    = Gen.long(100_000, 1_000_000)
    val positiveInt  = Gen.anyLong.map(_.abs)
  }

  val windowStart =
    testM("compute window start") {
      check(windowSize, windowNumber, timestamp, positiveInt) { (size, n, t0, t) =>
        // pick a random window start (might be before or after t0)
        val windowStart = t0 + n * size

        // pick a random timestamp within the selected window
        val timestamp = windowStart + t % size

        // assert that given t0, the window size and the timestamp,
        // we can compute the window start
        assert(
          WordCounter.windowStart(t0, size, timestamp)
        )(
          equalTo(windowStart.toLong)
        )
      }
    }

  val wordCountTest =
    testM("count words") {
      assertM(
        ZStream
          .fromIterable(
            Seq(
              """{ "event_type": "et1", "data": "a b", "timestamp": 17 }""",
              """{ "event_type": "et1", "data": "b c", "timestamp": 18 }""",
              """noizzzzz""",
              """{ "event_type": "et2", "data": "x y", "timestamp": 19 }""",
              """{ "event_type": "et2", "data": "z",   "timestamp": 19 }""",
              """{ "event_type": "et3", "data": "i",   "timestamp": 57 }""",
              """more noizzzzzzzzz""",
              """{ "event_type": "et4", "data": "j",   "timestamp":  3 }"""
            )
          )
          .via(WordCounter.countWords(startTimeEpochSec = 30, windowSizeInSec = 10)(_))
          .runCollect
      )(
        equalTo(
          Chunk(
            Map(10L -> Map("et1" -> Map("a" -> 1, "b" -> 1))),
            Map(10L -> Map("et1" -> Map("b" -> 2, "c" -> 1, "a" -> 1))),
            Map(10L -> Map("et1" -> Map("b" -> 2, "c" -> 1, "a" -> 1), "et2" -> Map("x" -> 1, "y" -> 1))),
            Map(10L -> Map("et1" -> Map("b" -> 2, "c" -> 1, "a" -> 1), "et2" -> Map("x" -> 1, "y" -> 1, "z" -> 1))),
            Map(
              10L -> Map("et1" -> Map("b" -> 2, "c" -> 1, "a" -> 1), "et2" -> Map("x" -> 1, "y" -> 1, "z" -> 1)),
              50L -> Map("et3" -> Map("i" -> 1))
            ),
            Map(
              10L -> Map("et1" -> Map("b" -> 2, "c" -> 1, "a" -> 1), "et2" -> Map("x" -> 1, "y" -> 1, "z" -> 1)),
              50L -> Map("et3" -> Map("i" -> 1)),
              0L  -> Map("et4" -> Map("j" -> 1))
            )
          )
        )
      )
    }

  val wordCountIntegrationTest =
    testM("count words end-to-end integration") {
      for {
        _ <- TestConsole.feedLines(
              """???""",
              """{ "event_type": "1", "data": "a b", "timestamp": 17 }""",
              """{ "event_type": "1", "data": "b c", "timestamp": 18 }""",
              """{ "event_type": "2", "data": "x y", "timestamp": 19 }""",
              """grrrrrh.... ^%#$*^&%@""",
              """{ "event_type": "2", "data": "z",   "timestamp": 19 }""",
              """{ "event_type": "4", "data": "j",   "timestamp":  3 }""",
              """{ "event_type": "3", "data": "i",   "timestamp": 57 }"""
            )
        _        <- WordCountApp.main.fork
        _        <- UZServer.awaitUp
        _        <- Repository.get.doUntil(_.size > 1)
        response <- SttpClient.send(basicRequest.get(uri"http://localhost:8080/")).orDie
      } yield (assert(response.code)(equalTo(StatusCode.Ok)) &&
        assert(response.body)(isRight(anything)) &&
        assert(response.body)(
          isRight(
            equalTo(
              """[
                |  {
                |    "window" : {
                |      "start" : 0,
                |      "length" : 30,
                |      "wordCountMap" : {
                |        "1" : {
                |          "a" : 1,
                |          "b" : 2,
                |          "c" : 1
                |        },
                |        "2" : {
                |          "x" : 1,
                |          "y" : 1,
                |          "z" : 1
                |        },
                |        "4" : {
                |          "j" : 1
                |        }
                |      }
                |    }
                |  },
                |  {
                |    "window" : {
                |      "start" : 30,
                |      "length" : 30,
                |      "wordCountMap" : {
                |        "3" : {
                |          "i" : 1
                |        }
                |      }
                |    }
                |  }
                |]""".stripMargin
            )
          )
        ))
    }.provideCustomLayer(
        AsyncHttpClientZioBackend.layer() ++
          ((Config.layer(windowSizeInSec = 30) ++ repositoryLayer ++ testEnvironment) >+>
            (WordCounter.live ++ UZServer.serverLayer(8080)))
      )
      .mapError(TestFailure.fail)

  override def spec =
    suite("all")(
      windowStart,
      wordCountTest,
      wordCountIntegrationTest
    )
}
