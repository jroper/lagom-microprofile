package com.lightbend.lagom.internal.microprofile.messaging.kafka

import java.util.concurrent.{CompletableFuture, CompletionStage}

import com.lightbend.lagom.internal.api.UriUtils
import com.lightbend.lagom.internal.broker.kafka.{KafkaConfig, NoKafkaBrokersException}
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.lightbend.microprofile.reactive.messaging.kafka.KafkaInstanceProvider
import com.typesafe.config.Config
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject
import org.slf4j.LoggerFactory

import scala.compat.java8.FutureConverters._
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

@ApplicationScoped
class LagomKafkaProvider @Inject() (serviceLocator: ServiceLocator, config: Config)(implicit ec: ExecutionContext) extends KafkaInstanceProvider {

  private val log = LoggerFactory.getLogger(getClass)

  private val kafkaConfig = KafkaConfig(config)

  override def bootstrapServers(): CompletionStage[String] = {
    kafkaConfig.serviceName match {
      case Some(name) =>
        log.debug("Looking up Kafka service named {} from Lagom service locator", name)
        serviceLocator.locateAll(name).toScala.map {
          case uris if uris.isEmpty =>
            throw new NoKafkaBrokersException(name)
          case uris =>
            val endpoints = UriUtils.hostAndPorts(uris.asScala)
            log.debug("Connecting to Kafka service named {} at {}", name: Any, endpoints)
            endpoints
        }.toJava
      case None =>
        log.debug("Using Lagom configured message brokers {}", kafkaConfig.brokers)
        CompletableFuture.completedFuture(kafkaConfig.brokers)
    }
  }

}
