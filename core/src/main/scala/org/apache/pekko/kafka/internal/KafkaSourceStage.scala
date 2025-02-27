/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.kafka.internal

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.kafka.scaladsl.Consumer._
import org.apache.pekko.stream._
import org.apache.pekko.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue }

/**
 * INTERNAL API
 */
@InternalApi
private[kafka] abstract class KafkaSourceStage[K, V, Msg](stageName: String)
    extends GraphStageWithMaterializedValue[SourceShape[Msg], Control] {
  protected val out = Outlet[Msg]("out")
  val shape = new SourceShape(out)

  override protected def initialAttributes: Attributes = Attributes.name(stageName)

  protected def logic(shape: SourceShape[Msg]): GraphStageLogic with Control
  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
    val result = logic(shape)
    (result, result)
  }
}
