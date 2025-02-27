/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.scaladsl

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.adapter._
import org.apache.pekko.actor.typed.{ ActorSystem, Behavior }
import org.apache.pekko.cluster.sharding.external.ExternalShardAllocationStrategy
import org.apache.pekko.cluster.sharding.typed.ClusterShardingSettings
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import org.apache.pekko.kafka.cluster.sharding.KafkaClusterSharding
import org.apache.pekko.kafka.scaladsl.Consumer
import org.apache.pekko.kafka.{ ConsumerRebalanceEvent, ConsumerSettings, Subscriptions }
import org.apache.pekko.stream.scaladsl.{ Flow, Sink }
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, StringDeserializer }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

/**
 * This is compile-only code meant for documentation purposes.
 * A full sample application exists in the akka-samples repository:
 *
 * https://github.com/akka/akka-samples/tree/2.6/akka-sample-kafka-to-sharding-scala
 */
object ClusterShardingExample {
  implicit val system = ActorSystem(Behaviors.empty, "ClusterShardingExample")
  val kafkaBootstrapServers = "localhost:9092"

  implicit val ec = system.executionContext

  def userBehaviour(): Behavior[User] = Behaviors.empty[User]
  def userBusiness[T](): Flow[T, T, NotUsed] = Flow[T]

  // #user-entity
  final case class User(id: String, name: String)
  // #user-entity

  // #message-extractor
  // automatically retrieving the number of partitions requires a round trip to a Kafka broker
  val messageExtractor: Future[KafkaClusterSharding.KafkaShardingNoEnvelopeExtractor[User]] =
    KafkaClusterSharding(system.toClassic).messageExtractorNoEnvelope(
      timeout = 10.seconds,
      topic = "user-topic",
      entityIdExtractor = (msg: User) => msg.id,
      settings = ConsumerSettings(system.toClassic, new StringDeserializer, new StringDeserializer)
        .withBootstrapServers(kafkaBootstrapServers))
  // #message-extractor

  // #setup-cluster-sharding
  // create an Apache Pekko Cluster Sharding `EntityTypeKey` for `User` for this Kafka Consumer Group
  val groupId = "user-topic-group-id"
  val typeKey = EntityTypeKey[User](groupId)

  messageExtractor.onComplete {
    case Success(extractor) =>
      ClusterSharding(system).init(
        Entity(typeKey)(createBehavior = _ => userBehaviour())
          .withAllocationStrategy(new ExternalShardAllocationStrategy(system, typeKey.name))
          .withMessageExtractor(extractor)
          .withSettings(ClusterShardingSettings(system)))
    case Failure(ex) => system.log.error("An error occurred while obtaining the message extractor", ex)
  }
  // #setup-cluster-sharding

  // #rebalance-listener
  // obtain an Apache Pekko classic ActorRef that will handle consumer group rebalance events
  val rebalanceListener: org.apache.pekko.actor.typed.ActorRef[ConsumerRebalanceEvent] =
    KafkaClusterSharding(system.toClassic).rebalanceListener(typeKey)

  // convert the rebalance listener to a classic ActorRef until Apache Pekko Connector Kafka supports Apache Pekko Typed
  import org.apache.pekko.actor.typed.scaladsl.adapter._
  val rebalanceListenerClassic: org.apache.pekko.actor.ActorRef = rebalanceListener.toClassic

  val consumerSettings =
    ConsumerSettings(system.toClassic, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(kafkaBootstrapServers)
      .withGroupId(typeKey.name) // use the same group id as we used in the `EntityTypeKey` for `User`

  // pass the rebalance listener to the topic subscription
  val subscription = Subscriptions
    .topics("user-topic")
    .withRebalanceListener(rebalanceListenerClassic)

  // run & materialize the stream
  val consumer = Consumer
    .plainSource(consumerSettings, subscription)
    .via(userBusiness())
    .runWith(Sink.ignore)
  // #rebalance-listener
}
