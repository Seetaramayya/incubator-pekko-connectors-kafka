/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.kafka.benchmarks

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.kafka.ConsumerMessage.{ Committable, CommittableMessage }
import org.apache.pekko.kafka.ProducerMessage.Envelope
import org.apache.pekko.kafka.benchmarks.app.RunTestCommand
import org.apache.pekko.kafka.scaladsl.Consumer.{ Control, DrainingControl }
import org.apache.pekko.kafka.scaladsl.{ Committer, Consumer, Producer }
import org.apache.pekko.kafka._
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{ Keep, Sink, Source }
import com.codahale.metrics.Meter
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{
  ByteArrayDeserializer,
  ByteArraySerializer,
  StringDeserializer,
  StringSerializer
}

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future, Promise }
import scala.util.Success

case class PekkoConnectorsCommittableSinkTestFixture[SOut, FIn](sourceTopic: String,
    sinkTopic: String,
    msgCount: Int,
    source: Source[SOut, Control],
    sink: Sink[FIn, Future[Done]])

object PekkoConnectorsCommittableSinkFixtures extends PerfFixtureHelpers {
  type Key = Array[Byte]
  type Val = String
  type Message = CommittableMessage[Key, Val]
  type ProducerMessage = Envelope[Key, Val, Committable]

  private def createConsumerSettings(kafkaHost: String)(implicit actorSystem: ActorSystem) =
    ConsumerSettings(actorSystem, new ByteArrayDeserializer, new StringDeserializer)
      .withBootstrapServers(kafkaHost)
      .withGroupId(randomId())
      .withClientId(randomId())
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  private def createProducerSettings(
      kafkaHost: String)(implicit actorSystem: ActorSystem): ProducerSettings[Array[Byte], String] =
    ProducerSettings(actorSystem, new ByteArraySerializer, new StringSerializer)
      .withBootstrapServers(kafkaHost)

  def producerSink(c: RunTestCommand)(implicit actorSystem: ActorSystem) =
    FixtureGen[PekkoConnectorsCommittableSinkTestFixture[Message, ProducerMessage]](
      c,
      msgCount => {
        fillTopic(c.filledTopic, c.kafkaHost)
        val sinkTopic = randomId()

        val source: Source[Message, Control] =
          Consumer.committableSource(createConsumerSettings(c.kafkaHost), Subscriptions.topics(c.filledTopic.topic))

        val sink: Sink[ProducerMessage, Future[Done]] =
          Producer.committableSink(createProducerSettings(c.kafkaHost), CommitterSettings(actorSystem))

        PekkoConnectorsCommittableSinkTestFixture[Message, ProducerMessage](c.filledTopic.topic,
          sinkTopic,
          msgCount,
          source,
          sink)
      })

  def composedSink(c: RunTestCommand)(implicit actorSystem: ActorSystem) =
    FixtureGen[PekkoConnectorsCommittableSinkTestFixture[Message, ProducerMessage]](
      c,
      msgCount => {
        fillTopic(c.filledTopic, c.kafkaHost)
        val sinkTopic = randomId()

        val source: Source[Message, Control] =
          Consumer.committableSource(createConsumerSettings(c.kafkaHost), Subscriptions.topics(c.filledTopic.topic))

        val sink: Sink[ProducerMessage, Future[Done]] =
          Producer
            .flexiFlow[Key, Val, Committable](createProducerSettings(c.kafkaHost))
            .map(_.passThrough)
            .toMat(Committer.sink(CommitterSettings(actorSystem)))(Keep.right)

        PekkoConnectorsCommittableSinkTestFixture[Message, ProducerMessage](c.filledTopic.topic,
          sinkTopic,
          msgCount,
          source,
          sink)
      })
}

object PekkoConnectorsCommittableSinkBenchmarks extends LazyLogging {
  import PekkoConnectorsCommittableSinkFixtures.{ Message, ProducerMessage }

  val streamingTimeout: FiniteDuration = 30.minutes
  type Fixture = PekkoConnectorsCommittableSinkTestFixture[Message, ProducerMessage]

  def run(fixture: Fixture, meter: Meter)(implicit mat: Materializer): Unit = {
    logger.debug("Creating and starting a stream")
    val msgCount = fixture.msgCount
    val sinkTopic = fixture.sinkTopic
    val source = fixture.source

    val promise = Promise[Unit]()
    val logPercentStep = 1
    val loggedStep = if (msgCount > logPercentStep) 100 else 1

    val control = source
      .map { msg =>
        ProducerMessage.single(new ProducerRecord[Array[Byte], String](sinkTopic, msg.record.value()),
          msg.committableOffset)
      }
      .map { msg =>
        meter.mark()
        val offset = msg.passThrough.partitionOffset.offset
        if (offset % loggedStep == 0)
          logger.info(s"Transformed $offset elements to Kafka (${100 * offset / msgCount}%)")
        if (offset >= fixture.msgCount - 1)
          promise.complete(Success(()))
        msg
      }
      .toMat(fixture.sink)(DrainingControl.apply)
      .run()

    Await.result(promise.future, streamingTimeout)
    control.drainAndShutdown()(mat.executionContext)
    logger.debug("Stream finished")
  }
}
