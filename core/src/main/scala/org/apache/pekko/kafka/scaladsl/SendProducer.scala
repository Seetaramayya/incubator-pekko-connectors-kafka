/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.kafka.scaladsl

import org.apache.pekko.Done
import org.apache.pekko.actor.{ ActorSystem, ClassicActorSystemProvider }
import org.apache.pekko.kafka.ProducerMessage._
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.util.JavaDurationConverters._
import org.apache.kafka.clients.producer.{ Callback, ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future, Promise }

/**
 * Utility class for producing to Kafka without using Apache Pekko Streams.
 * @param settings producer settings used to create or access the [[org.apache.kafka.clients.producer.Producer]]
 */
final class SendProducer[K, V] private (val settings: ProducerSettings[K, V], system: ActorSystem) {

  private implicit val ec: ExecutionContext = system.dispatchers.lookup(settings.dispatcher)
  private final val producerFuture = settings.createKafkaProducerAsync()(ec)

  /**
   * Send records to Kafka topics and complete a future with the result.
   *
   * It publishes records to Kafka topics conditionally:
   *
   * - [[org.apache.pekko.kafka.ProducerMessage.Message Message]] publishes a single message to its topic, and completes the future with [[org.apache.pekko.kafka.ProducerMessage.Result Result]]
   *
   * - [[org.apache.pekko.kafka.ProducerMessage.MultiMessage MultiMessage]] publishes all messages in its `records` field, and completes the future with [[org.apache.pekko.kafka.ProducerMessage.MultiResult MultiResult]]
   *
   * - [[org.apache.pekko.kafka.ProducerMessage.PassThroughMessage PassThroughMessage]] does not publish anything, and completes the future with [[org.apache.pekko.kafka.ProducerMessage.PassThroughResult PassThroughResult]]
   *
   * The messages support passing through arbitrary data.
   */
  def sendEnvelope[PT](envelope: Envelope[K, V, PT]): Future[Results[K, V, PT]] = {
    producerFuture.flatMap { producer =>
      envelope match {
        case msg: Message[K, V, PT] =>
          sendSingle(producer, msg.record, Result(_, msg))

        case multiMsg: MultiMessage[K, V, PT] =>
          val promises = multiMsg.records.map(record => sendSingle(producer, record, MultiResultPart(_, record)))
          Future.sequence(promises).map(MultiResult(_, multiMsg.passThrough))

        case passThrough: PassThroughMessage[K, V, PT] =>
          Future.successful(PassThroughResult(passThrough.passThrough))

      }
    }
  }

  /**
   * Send a raw Kafka [[org.apache.kafka.clients.producer.ProducerRecord]] and complete a future with the resulting metadata.
   */
  def send(record: ProducerRecord[K, V]): Future[RecordMetadata] = {
    producerFuture.flatMap { producer =>
      sendSingle(producer, record, identity)
    }
  }

  private def sendSingle[R](producer: org.apache.kafka.clients.producer.Producer[K, V],
      record: ProducerRecord[K, V],
      success: RecordMetadata => R): Future[R] = {
    val result = Promise[R]()
    producer.send(
      record,
      new Callback {
        override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
          if (exception == null)
            result.success(success(metadata))
          else
            result.failure(exception)
        }
      })
    result.future
  }

  /**
   * Close the underlying producer (depending on the "close producer on stop" setting).
   */
  def close(): Future[Done] = {
    if (settings.closeProducerOnStop) producerFuture.map { producer =>
      // we do not have to check if producer was already closed in send-callback as `flush()` and `close()` are effectively no-ops in this case
      producer.flush()
      producer.close(settings.closeTimeout.asJava)
      Done
    }
    else Future.successful(Done)
  }

  override def toString: String = s"SendProducer($settings)"
}

object SendProducer {
  def apply[K, V](settings: ProducerSettings[K, V])(implicit system: ClassicActorSystemProvider): SendProducer[K, V] =
    new SendProducer(settings, system.classicSystem)

  // kept for bin-compatibility
  @deprecated("use the variant with ClassicActorSystemProvider instead", "2.0.5")
  def apply[K, V](settings: ProducerSettings[K, V], system: ActorSystem): SendProducer[K, V] =
    new SendProducer(settings, system)
}
