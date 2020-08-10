package com.github.zagyi.wordcounter

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import scala.collection.immutable.SortedMap

object Model {

  case class Event(
    event_type: String,
    data: String,
    timestamp: Long
  )

  object Event {
    implicit val eventDecoder: Decoder[Event] = deriveDecoder
  }

  type WordCount = SortedMap[String, Int]
  type Window    = SortedMap[String, WordCount]
  type Windows   = SortedMap[Long, Window]

  object WordCount { def empty = SortedMap.empty[String, Int]       }
  object Window    { def empty = SortedMap.empty[String, WordCount] }
  object Windows   { def empty = SortedMap.empty[Long, Window]      }
}
