/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.kafka.internal

import org.apache.pekko.actor.ActorRef
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.kafka.ManualSubscription
import org.apache.pekko.stream.SourceShape

import scala.concurrent.Future

/**
 * Internal API.
 *
 * Single source logic for externally provided [[KafkaConsumerActor]].
 */
@InternalApi private abstract class ExternalSingleSourceLogic[K, V, Msg](
    shape: SourceShape[Msg],
    _consumerActor: ActorRef,
    val subscription: ManualSubscription) extends BaseSingleSourceLogic[K, V, Msg](shape) {

  final override protected def logSource: Class[_] = classOf[ExternalSingleSourceLogic[K, V, Msg]]

  final val consumerFuture: Future[ActorRef] = Future.successful(_consumerActor)

  final def createConsumerActor(): ActorRef = _consumerActor

  final def configureSubscription(): Unit =
    configureManualSubscription(subscription)

  final override def performShutdown(): Unit = {
    super.performShutdown()
    completeStage()
  }

}
