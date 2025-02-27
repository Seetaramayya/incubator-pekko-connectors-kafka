/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.kafka.ConsumerMessage.CommittableOffset
import org.apache.pekko.kafka.scaladsl.{ Committer, Consumer }
import org.apache.pekko.kafka.{ CommitterSettings, ConsumerMessage, ProducerMessage }
import org.apache.pekko.stream.scaladsl.{ Flow, Keep, Source }
import org.apache.pekko.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import org.apache.pekko.testkit.TestKit
import org.apache.pekko.{ Done, NotUsed }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TestkitSamplesSpec
    extends TestKit(ActorSystem("example"))
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with IntegrationPatience {

  override protected def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "Without broker testing" should "be possible" in assertAllStagesStopped {
    val topic = "topic"
    val targetTopic = "target-topic"
    val groupId = "group1"
    val startOffset = 100L
    val partition = 0
    val committerSettings = CommitterSettings(system)

    // #factories
    import org.apache.pekko.kafka.testkit.scaladsl.ConsumerControlFactory
    import org.apache.pekko.kafka.testkit.{ ConsumerResultFactory, ProducerResultFactory }

    // create elements emitted by the mocked Consumer
    val elements = (0 to 10).map { i =>
      val nextOffset = startOffset + i
      ConsumerResultFactory.committableMessage(
        new ConsumerRecord(topic, partition, nextOffset, "key", s"value $i"),
        ConsumerResultFactory.committableOffset(groupId, topic, partition, nextOffset, s"metadata $i"))
    }

    // create a source imitating the Consumer.committableSource
    val mockedKafkaConsumerSource: Source[ConsumerMessage.CommittableMessage[String, String], Consumer.Control] =
      Source(elements).viaMat(ConsumerControlFactory.controlFlow())(Keep.right)

    // create a source imitating the Producer.flexiFlow
    val mockedKafkaProducerFlow: Flow[ProducerMessage.Envelope[String, String, CommittableOffset],
      ProducerMessage.Results[String, String, CommittableOffset], NotUsed] =
      Flow[ProducerMessage.Envelope[String, String, CommittableOffset]]
        .map {
          case msg: ProducerMessage.Message[String, String, CommittableOffset] =>
            ProducerResultFactory.result(msg)
          case other => throw new Exception(s"excluded: $other")
        }

    // run the flow as if it was connected to a Kafka broker
    val (control, streamCompletion) = mockedKafkaConsumerSource
      .map(msg =>
        ProducerMessage.Message(
          new ProducerRecord[String, String](targetTopic, msg.record.value),
          msg.committableOffset))
      .via(mockedKafkaProducerFlow)
      .map(_.passThrough)
      .toMat(Committer.sink(committerSettings))(Keep.both)
      .run()
    // #factories

    streamCompletion.futureValue should be(Done)
  }
}
