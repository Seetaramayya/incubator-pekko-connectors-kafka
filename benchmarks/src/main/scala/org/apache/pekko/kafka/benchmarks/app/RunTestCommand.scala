/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.kafka.benchmarks.app

import org.apache.pekko.kafka.benchmarks.PerfFixtureHelpers.FilledTopic

case class RunTestCommand(testName: String, kafkaHost: String, filledTopic: FilledTopic) {

  val msgCount = filledTopic.msgCount
  val msgSize = filledTopic.msgSize
  val numberOfPartitions = filledTopic.numberOfPartitions
  val replicationFactor = filledTopic.replicationFactor

}
