/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.kafka.scaladsl

import org.apache.pekko.actor.{ ActorSystem, ActorSystemImpl, ClassicActorSystemProvider }
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.discovery.{ Discovery, ServiceDiscovery }
import org.apache.pekko.kafka.{ ConsumerSettings, ProducerSettings }
import org.apache.pekko.util.JavaDurationConverters._
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure

/**
 * Scala API.
 *
 * Reads Kafka bootstrap servers from configured sources via [[org.apache.pekko.discovery.Discovery]] configuration.
 */
object DiscoverySupport {

  // used for initial discovery of contact points
  private def discovery(config: Config, system: ActorSystem): ServiceDiscovery =
    config.getString("discovery-method") match {
      case "pekko.discovery" =>
        Discovery(system).discovery

      case otherDiscoveryMechanism =>
        Discovery(system).loadServiceDiscovery(otherDiscoveryMechanism)
    }

  /**
   * Use Apache Pekko Discovery to read the addresses for `serviceName` within `lookupTimeout`.
   */
  private def bootstrapServers(
      discovery: ServiceDiscovery,
      serviceName: String,
      lookupTimeout: FiniteDuration)(implicit system: ActorSystem): Future[String] = {
    import system.dispatcher
    discovery.lookup(serviceName, lookupTimeout).map { resolved =>
      resolved.addresses
        .map { target =>
          val port = target.port
            .getOrElse(throw new IllegalArgumentException(s"port missing for $serviceName ${target.host}"))
          s"${target.host}:$port"
        }
        .mkString(",")
    }
  }

  /**
   * Internal API.
   *
   * Expect a `service` section in Config and use Apache Pekko Discovery to read the addresses for `name` within `lookup-timeout`.
   */
  @InternalApi
  private[kafka] def bootstrapServers(config: Config)(implicit system: ActorSystem): Future[String] = {
    checkClassOrThrow(system.asInstanceOf[ActorSystemImpl])
    val serviceName = config.getString("service-name")
    if (serviceName.nonEmpty) {
      val lookupTimeout = config.getDuration("resolve-timeout").asScala
      bootstrapServers(discovery(config, system), serviceName, lookupTimeout)
    } else throw new IllegalArgumentException(s"value for `service-name` in $config is empty")
  }

  /**
   * Expects a `service` section in the given Config and reads the given service name's address
   * to be used as `bootstrapServers`.
   */
  def consumerBootstrapServers[K, V](
      config: Config)(
      implicit system: ClassicActorSystemProvider): ConsumerSettings[K, V] => Future[ConsumerSettings[K, V]] = {
    val sys: ActorSystem = system.classicSystem
    import sys.dispatcher
    settings =>
      bootstrapServers(config)(sys)
        .map { bootstrapServers =>
          settings.withBootstrapServers(bootstrapServers)
        }
  }

  // kept for bin-compatibility
  def consumerBootstrapServers[K, V](
      config: Config)(system: ActorSystem): ConsumerSettings[K, V] => Future[ConsumerSettings[K, V]] = {
    implicit val sys: ClassicActorSystemProvider = system
    consumerBootstrapServers(config)
  }

  /**
   * Expects a `service` section in the given Config and reads the given service name's address
   * to be used as `bootstrapServers`.
   */
  def producerBootstrapServers[K, V](
      config: Config)(
      implicit system: ClassicActorSystemProvider): ProducerSettings[K, V] => Future[ProducerSettings[K, V]] = {
    val sys: ActorSystem = system.classicSystem
    import sys.dispatcher
    settings =>
      bootstrapServers(config)(sys)
        .map { bootstrapServers =>
          settings.withBootstrapServers(bootstrapServers)
        }
  }

  // kept for bin-compatibility
  def producerBootstrapServers[K, V](config: Config)(
      system: ActorSystem): ProducerSettings[K, V] => Future[ProducerSettings[K, V]] = {
    implicit val sys: ClassicActorSystemProvider = system
    producerBootstrapServers(config)
  }

  private def checkClassOrThrow(system: ActorSystemImpl): Unit =
    system.dynamicAccess.getClassFor("org.apache.pekko.discovery.Discovery$") match {
      case Failure(_: ClassNotFoundException | _: NoClassDefFoundError) =>
        throw new IllegalStateException(
          s"Apache Pekko Discovery is being used but the `pekko-discovery` library is not on the classpath, it must be added explicitly. See https://pekko.apache.org/docs/pekko/current/discovery/index.html")
      case _ =>
    }

}
