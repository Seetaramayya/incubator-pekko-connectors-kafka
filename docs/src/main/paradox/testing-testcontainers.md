---
project.description: Alpakka Kafka provides Testcontainers support for running a Kafka cluster locally using Docker containers.
---
# Testing with a Docker Kafka cluster

The [Testcontainers](https://www.testcontainers.org/) project contains a nice API to start and stop Apache Kafka in Docker containers. 
This becomes very relevant when your application code uses a Scala version which Apache Kafka doesn't support so that *EmbeddedKafka* can't be used.
Testcontainers also allow you to create a complete Kafka cluster (using Docker containers) to simulate production use cases.

## Settings

You can override testcontainers settings to create multi-broker Kafka clusters, or to finetune Kafka Broker, ZooKeeper and Schema Registry configuration, by updating @apidoc[KafkaTestkitTestcontainersSettings] in code or configuration.
The @apidoc[KafkaTestkitTestcontainersSettings] type can be used to perform actions such as:

* Set the docker image and tag of Kafka, ZooKeeper, and Schema Registry version to use (a recent Confluent Platform version is used by default)
* Define number of Kafka brokers
* Overriding container settings and environment variables (i.e. to change default Broker config)
* Apply custom docker configuration to the Kafka and ZooKeeper containers used to create a cluster

To change defaults for all settings update the appropriate configuration in `akka.kafka.testkit.testcontainers`.

@@ snip [snip](/testkit/src/main/resources/reference.conf) { #testkit-testcontainers-settings }

You can override all these defaults in code and per test class. 
By applying settings in code you can also configure the Kafka and ZooKeeper containers themselves.

For example, the following demonstrates creating a 3 Broker Kafka cluster and disables the automatic topic creation broker configuration using environment variables.

Scala
: @@snip [snip](/tests/src/test/scala/org/apache/pekko/kafka/scaladsl/SpecBase.scala) { #testkit #testcontainers-settings }

Java
: @@snip [snip](/tests/src/test/java/docs/javadsl/TestkitTestcontainersTest.java) { #testcontainers-settings }

<!-- NOTE: Can't get this working with paradox..
To see what options are available for configuring testcontainers using `configureKafka` and `configureZooKeeper` in @apidoc[KafkaTestkitTestcontainersSettings] see the API docs for @apidoc[PekkoConnectorsKafkaContainer] and @apidoc[org.testcontainers.containers.GenericContainer]. 
-->

To see what options are available for configuring testcontainers using `configureKafka` and `configureZooKeeper` in @apidoc[KafkaTestkitTestcontainersSettings] see the API docs for @apidoc[PekkoConnectorsKafkaContainer] and [`GenericContainer`](https://www.javadoc.io/static/org.testcontainers/testcontainers/$testcontainers.version$/org/testcontainers/containers/GenericContainer.html).

### Testing with Schema Registry

If you use Confluent's Schema Registry in your client code you may optionally run a Schema Registry container as well.
The following snippet demonstrates overriding a test class to use the container, or you can enable it for all tests in your `application.conf`.

Scala
: @@snip [snip](/tests/src/test/scala/docs/scaladsl/SchemaRegistrySerializationSpec.scala) { #schema-registry-settings }

Java
: @@snip [snip](/tests/src/test/java/docs/javadsl/SchemaRegistrySerializationTest.java) { #schema-registry-settings }

You can retrieve the Schema Registry URL in your test configuration by calling `getSchemaRegistryUrl()` or `schemaRegistryUrl`.

## Testing with a Docker Kafka cluster from Java code

The Alpakka Kafka testkit contains helper classes to start Kafka via Testcontainers. Alternatively, you may use just Testcontainers, as it is designed to be used with JUnit and you can follow [their documentation](https://www.testcontainers.org/modules/kafka/) to start and stop Kafka. To start a single instance for many tests see [Singleton containers](https://www.testcontainers.org/test_framework_integration/manual_lifecycle_control/).

The Testcontainers dependency must be added to your project explicitly.

@@dependency [Maven,sbt,Gradle] {
  group=org.testcontainers
  artifact=kafka
  version=$testcontainers.version$
  scope=test
}

The example below shows skeleton test classes for JUnit 4 and JUnit 5. The Kafka broker will start before the first test and be stopped after all test classes are finished.

Java JUnit 4
: @@snip [snip](/tests/src/test/java/docs/javadsl/AssignmentTest.java) { #testkit }

Java JUnit 5
: @@snip [snip](/tests/src/test/java/docs/javadsl/ProducerTest.java) { #testkit }


## Testing with a Docker Kafka cluster from Scala code

The Testcontainers dependency must be added to your project explicitly.

@@dependency [Maven,sbt,Gradle] {
  group=org.testcontainers
  artifact=kafka
  version=$testcontainers.version$
  scope=test
}

To ensure proper shutdown of all stages in every test, wrap your test code in @apidoc[assertAllStagesStopped]((javadsl|scaladsl).StreamTestKit$). This may interfere with the `stop-timeout` which delays shutdown for Alpakka Kafka consumers. You might need to configure a shorter timeout in your `application.conf` for tests.

### One cluster for all tests

By mixing in @scaladoc[TestcontainersKafkaLike](org.apache.pekko.kafka.testkit.scaladsl.TestcontainersKafkaLike) the Kafka Docker cluster will be started before the first test and shut down after all tests are finished.

Scala
: @@snip [snip](/tests/src/test/scala/org/apache/pekko/kafka/scaladsl/SpecBase.scala) { #testkit #testcontainers}

With this `TestcontainersSampleSpec` class test classes can extend it to automatically start and stop a Kafka broker to test with.

### One cluster per test class

By mixing in @scaladoc[TestcontainersKafkaPerClassLike](org.apache.pekko.kafka.testkit.scaladsl.TestcontainersKafkaPerClassLike) a specific Kafka Docker cluster will be started for that test class and stopped after its run finished.
