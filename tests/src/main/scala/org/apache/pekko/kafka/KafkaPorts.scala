/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.kafka

/**
 * Ports to use for Kafka and Zookeeper throughout integration tests.
 * Zookeeper is Kafka port + 1 if nothing else specified.
 */
object KafkaPorts {

  val AssignmentTest = 9082
  val ProducerExamplesTest = 9112

}
