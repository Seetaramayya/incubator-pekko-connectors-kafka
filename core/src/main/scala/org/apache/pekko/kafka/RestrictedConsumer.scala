/*
 * Copyright (C) 2014 - 2016 Softwaremill <https://softwaremill.com>
 * Copyright (C) 2016 - 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.kafka

import org.apache.pekko.annotation.ApiMayChange
import org.apache.kafka.clients.consumer.{ Consumer, OffsetAndMetadata, OffsetAndTimestamp }
import org.apache.kafka.common.TopicPartition

/**
 * Offers parts of the [[org.apache.kafka.clients.consumer.Consumer]] API which becomes available to
 * the [[org.apache.pekko.kafka.scaladsl.PartitionAssignmentHandler]] callbacks.
 */
@ApiMayChange
final class RestrictedConsumer(consumer: Consumer[_, _], duration: java.time.Duration) {

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#assignment]]
   */
  def assignment(): java.util.Set[TopicPartition] = consumer.assignment()

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#beginningOffsets()]]
   */
  def beginningOffsets(tps: java.util.Collection[TopicPartition]): java.util.Map[TopicPartition, java.lang.Long] =
    consumer.beginningOffsets(tps, duration)

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#commitSync(Map,java.time.Duration)]]
   */
  def commitSync(offsets: java.util.Map[TopicPartition, OffsetAndMetadata]): Unit =
    consumer.commitSync(offsets, duration)

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#committed(TopicPartition,java.time.Duration)]]
   */
  @deprecated("use `committed(java.util.Set[TopicPartition])`", "2.0.5")
  def committed(tp: TopicPartition): OffsetAndMetadata = consumer.committed(tp, duration)

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#committed(java.util.Set[TopicPartition],java.time.Duration)]]
   */
  def committed(partitions: java.util.Set[TopicPartition]): java.util.Map[TopicPartition, OffsetAndMetadata] =
    consumer.committed(partitions, duration)

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#endOffsets(java.util.Collection[TopicPartition],java.time.Duration)]]
   */
  def endOffsets(tps: java.util.Collection[TopicPartition]): java.util.Map[TopicPartition, java.lang.Long] =
    consumer.endOffsets(tps, duration)

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#offsetsForTimes(java.util.Map[TopicPartition,Long],java.time.Duration)]]
   */
  def offsetsForTimes(
      timestampsToSearch: java.util.Map[TopicPartition, java.lang.Long])
      : java.util.Map[TopicPartition, OffsetAndTimestamp] =
    consumer.offsetsForTimes(timestampsToSearch, duration)

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#position(TopicPartition, java.time.Duration)]]
   */
  def position(tp: TopicPartition): Long = consumer.position(tp, duration)

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#seek(TopicPartition, Long)]]
   */
  def seek(tp: TopicPartition, offset: Long): Unit = consumer.seek(tp, offset)

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#seekToBeginning(java.util.Collection[TopicPartition])]]
   */
  def seekToBeginning(tps: java.util.Collection[TopicPartition]): Unit = consumer.seekToBeginning(tps)

  /**
   * See [[org.apache.kafka.clients.consumer.KafkaConsumer#seekToEnd(java.util.Collection[TopicPartition])]]
   */
  def seekToEnd(tps: java.util.Collection[TopicPartition]): Unit = consumer.seekToEnd(tps)
}
