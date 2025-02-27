/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.kafka.benchmarks

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.kafka.ConsumerMessage.CommittableMessage
import org.apache.pekko.kafka.benchmarks.app.RunTestCommand
import org.apache.pekko.kafka.scaladsl.Consumer
import org.apache.pekko.kafka.scaladsl.Consumer.Control
import org.apache.pekko.kafka.{ ConsumerSettings, Subscriptions }
import org.apache.pekko.stream.scaladsl.Source
import org.apache.kafka.clients.consumer.{ ConsumerConfig, ConsumerRecord }
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, StringDeserializer }

case class ReactiveKafkaConsumerTestFixture[T](topic: String,
    msgCount: Int,
    source: Source[T, Control],
    numberOfPartitions: Int)

object ReactiveKafkaConsumerFixtures extends PerfFixtureHelpers {

  private def createConsumerSettings(kafkaHost: String)(implicit actorSystem: ActorSystem) =
    ConsumerSettings(actorSystem, new ByteArrayDeserializer, new StringDeserializer)
      .withBootstrapServers(kafkaHost)
      .withGroupId(randomId())
      .withClientId(randomId())
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  def plainSources(c: RunTestCommand)(implicit actorSystem: ActorSystem) =
    FixtureGen[ReactiveKafkaConsumerTestFixture[ConsumerRecord[Array[Byte], String]]](
      c,
      msgCount => {
        fillTopic(c.filledTopic, c.kafkaHost)
        val settings = createConsumerSettings(c.kafkaHost)
        val source = Consumer.plainSource(settings, Subscriptions.topics(c.filledTopic.topic))
        ReactiveKafkaConsumerTestFixture(c.filledTopic.topic, msgCount, source, c.numberOfPartitions)
      })

  def committableSources(c: RunTestCommand)(implicit actorSystem: ActorSystem) =
    FixtureGen[ReactiveKafkaConsumerTestFixture[CommittableMessage[Array[Byte], String]]](
      c,
      msgCount => {
        fillTopic(c.filledTopic, c.kafkaHost)
        val settings = createConsumerSettings(c.kafkaHost)
        val source = Consumer.committableSource(settings, Subscriptions.topics(c.filledTopic.topic))
        ReactiveKafkaConsumerTestFixture(c.filledTopic.topic, msgCount, source, c.numberOfPartitions)
      })

  def noopFixtureGen(c: RunTestCommand) =
    FixtureGen[ReactiveKafkaConsumerTestFixture[ConsumerRecord[Array[Byte], String]]](
      c,
      msgCount => {
        ReactiveKafkaConsumerTestFixture("topic", msgCount, null, c.numberOfPartitions)
      })

}
