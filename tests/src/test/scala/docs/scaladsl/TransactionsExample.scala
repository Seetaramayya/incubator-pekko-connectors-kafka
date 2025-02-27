/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl

import java.util.concurrent.atomic.AtomicReference

import org.apache.pekko.Done
import org.apache.pekko.kafka.scaladsl.Consumer.{ Control, DrainingControl }
import org.apache.pekko.kafka.scaladsl.{ Consumer, Transactional }
import org.apache.pekko.kafka.testkit.scaladsl.TestcontainersKafkaLike
import org.apache.pekko.kafka.{
  ConsumerSettings,
  ProducerMessage,
  ProducerSettings,
  Repeated,
  Subscriptions,
  TransactionsOps
}
import org.apache.pekko.stream.RestartSettings
import org.apache.pekko.stream.scaladsl.{ Keep, RestartSource, Sink }
import org.apache.pekko.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Await
import scala.concurrent.duration._

class TransactionsExample extends DocsSpecBase with TestcontainersKafkaLike with TransactionsOps with Repeated {

  override def sleepAfterProduce: FiniteDuration = 10.seconds

  "Transactional sink" should "work" in assertAllStagesStopped {
    val consumerSettings = consumerDefaults.withGroupId(createGroupId())
    val producerSettings = txProducerDefaults
    val sourceTopic = createTopic(1)
    val sinkTopic = createTopic(2)
    val transactionalId = createTransactionalId()
    // #transactionalSink
    val control =
      Transactional
        .source(consumerSettings, Subscriptions.topics(sourceTopic))
        .via(businessFlow)
        .map { msg =>
          ProducerMessage.single(new ProducerRecord(sinkTopic, msg.record.key, msg.record.value), msg.partitionOffset)
        }
        .toMat(Transactional.sink(producerSettings, transactionalId))(DrainingControl.apply)
        .run()

    // ...

    // #transactionalSink
    val testConsumerGroup = createGroupId(2)
    val (control2, result) = Consumer
      .plainSource(withProbeConsumerSettings(consumerSettings, testConsumerGroup), Subscriptions.topics(sinkTopic))
      .toMat(Sink.seq)(Keep.both)
      .run()

    awaitProduce(produce(sourceTopic, 1 to 10))
    control.drainAndShutdown().futureValue should be(Done)
    control2.shutdown().futureValue should be(Done)
    // #transactionalSink
    control.drainAndShutdown()
    // #transactionalSink
    result.futureValue should have size 10
  }

  it should "support `withOffsetContext`" in assertAllStagesStopped {
    val consumerSettings = consumerDefaults.withGroupId(createGroupId())
    val producerSettings = txProducerDefaults
    val sourceTopic = createTopic(1)
    val sinkTopic = createTopic(2)
    val control =
      Transactional
        .sourceWithOffsetContext(consumerSettings, Subscriptions.topics(sourceTopic))
        .via(businessFlow)
        .map { record =>
          ProducerMessage.single(new ProducerRecord(sinkTopic, record.key, record.value))
        }
        .toMat(Transactional.sinkWithOffsetContext(producerSettings, createTransactionalId()))(DrainingControl.apply)
        .run()

    val testConsumerGroup = createGroupId(2)
    val (control2, result) = Consumer
      .plainSource(probeConsumerSettings(testConsumerGroup), Subscriptions.topics(sinkTopic))
      .toMat(Sink.seq)(Keep.both)
      .run()

    awaitProduce(produce(sourceTopic, 1 to 10))
    control.drainAndShutdown().futureValue shouldBe Done
    control2.shutdown().futureValue shouldBe Done
    result.futureValue should have size 10
  }

  "TransactionsFailureRetryExample" should "work" in assertAllStagesStopped {
    val consumerSettings = consumerDefaults.withGroupId(createGroupId())
    val producerSettings = txProducerDefaults
    val sourceTopic = createTopic(1)
    val sinkTopic = createTopic(2)
    val transactionalId = createTransactionalId()
    // #transactionalFailureRetry
    val innerControl = new AtomicReference[Control](Consumer.NoopControl)

    val stream = RestartSource.onFailuresWithBackoff(
      RestartSettings(
        minBackoff = 1.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2)) { () =>
      Transactional
        .source(consumerSettings, Subscriptions.topics(sourceTopic))
        .via(businessFlow)
        .map { msg =>
          ProducerMessage.single(new ProducerRecord(sinkTopic, msg.record.key, msg.record.value), msg.partitionOffset)
        }
        // side effect out the `Control` materialized value because it can't be propagated through the `RestartSource`
        .mapMaterializedValue(c => innerControl.set(c))
        .via(Transactional.flow(producerSettings, transactionalId))
    }

    stream.runWith(Sink.ignore)

    // Add shutdown hook to respond to SIGTERM and gracefully shutdown stream
    sys.ShutdownHookThread {
      Await.result(innerControl.get.shutdown(), 10.seconds)
    }
    // #transactionalFailureRetry
    val testConsumerGroup = createGroupId(2)
    val (control2, result) = Consumer
      .plainSource(probeConsumerSettings(testConsumerGroup), Subscriptions.topics(sinkTopic))
      .toMat(Sink.seq)(Keep.both)
      .run()

    awaitProduce(produce(sourceTopic, 1 to 10))
    innerControl.get.shutdown().futureValue should be(Done)
    control2.shutdown().futureValue should be(Done)
    result.futureValue should have size 10
  }

//  "Partitioned transactional sink" should "work" in {
//    val consumerSettings = consumerDefaults.withGroupId(createGroupId())
//    val producerSettings = txProducerDefaults
//    val maxPartitions = 2
//    val sourceTopic = createTopic(1, maxPartitions, 1)
//    val sinkTopic = createTopicName(2)
//    val transactionalId = createTransactionalId()
//    // #partitionedTransactionalSink
//    val control =
//      Transactional
//        .partitionedSource(consumerSettings, Subscriptions.topics(sourceTopic))
//        .mapAsyncUnordered(maxPartitions) {
//          case (tp, source) =>
//            source
//              .via(businessFlow)
//              .map { msg =>
//                ProducerMessage.single(new ProducerRecord(sinkTopic, msg.record.key, msg.record.value),
//                                       msg.partitionOffset)
//              }
//              .runWith(Transactional.sink(producerSettings, transactionalId))
//        }
//        .toMat(Sink.ignore)(DrainingControl.form)
//        .run()
//    // ...
//
//    // #partitionedTransactionalSink
//    val testConsumerGroup = createGroupId(2)
//    val (control2, result) = Consumer
//      .plainSource(probeConsumerSettings(testConsumerGroup), Subscriptions.topics(sinkTopic))
//      .toMat(Sink.seq)(Keep.both)
//      .run()
//
//    awaitProduce(produce(sourceTopic, 1 to 10))
//    control.drainAndShutdown().futureValue should be(Done)
//    control2.shutdown().futureValue should be(Done)
//    // #partitionedTransactionalSink
//    control.drainAndShutdown()
//    // #partitionedTransactionalSink
//    result.futureValue should have size (10)
//  }

  def probeConsumerSettings(groupId: String): ConsumerSettings[String, String] =
    withProbeConsumerSettings(consumerDefaults, groupId)

  override def producerDefaults: ProducerSettings[String, String] =
    withTestProducerSettings(super.producerDefaults)

  def txProducerDefaults: ProducerSettings[String, String] =
    withTransactionalProducerSettings(super.producerDefaults)
}
