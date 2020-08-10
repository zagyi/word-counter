package com.github.zagyi.wordcounter

import com.github.zagyi.wordcounter.Model.Windows
import zio.{ Has, Ref, ZIO }

object Repository {
  type Repository = Has[Ref[Windows]]

  val repositoryLayer = Ref.make(Windows.empty).toLayer

  def get = ZIO.service[Ref[Windows]].flatMap(_.get)

  def set(windows: Windows) = ZIO.service[Ref[Windows]].flatMap(_.set(windows))
}
