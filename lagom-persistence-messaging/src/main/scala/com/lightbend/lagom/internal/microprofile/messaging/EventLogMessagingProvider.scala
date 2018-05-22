package com.lightbend.lagom.internal.microprofile.messaging

import java.io.Closeable
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

import akka.{Done, NotUsed, japi}
import akka.japi.function.Creator
import akka.stream.javadsl.{Flow, Sink}
import akka.stream.scaladsl
import com.google.common.reflect.TypeToken
import com.lightbend.lagom.internal.javadsl.persistence.{Context, OffsetAdapter, ReadSideImpl}
import com.lightbend.lagom.javadsl.microprofile.messaging.{EventLog, EventLogConsumer, EventTagProvider}
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor.ReadSideHandler
import com.lightbend.lagom.javadsl.persistence._
import com.lightbend.lagom.spi.persistence.{OffsetDao, OffsetStore}
import com.lightbend.microprofile.reactive.messaging.spi._
import javax.enterprise.context.ApplicationScoped
import javax.enterprise.event.Observes
import javax.enterprise.inject.spi.{AnnotatedMember, BeforeBeanDiscovery, DeploymentException, Extension}
import javax.inject.Inject
import org.eclipse.microprofile.reactive.messaging.{Message, MessagingProvider}
import org.pcollections.{PSequence, TreePVector}

import scala.concurrent.ExecutionContext
import scala.compat.java8.FutureConverters._

@ApplicationScoped
class EventLogMessagingProvider @Inject() (readSide: ReadSideImpl, offsetStore: OffsetStore)(implicit ec: ExecutionContext) extends LightbendMessagingProvider {
  override def providerFor(): Class[_ <: MessagingProvider] = {
    classOf[EventLog]
  }

  override def validatePublishingStream[T](stream: PublishingStream[T]): ValidatedPublishingStream[T] = {
    throw new DeploymentException(stream.annotated() + " is attempting to publish to the Lagom event log as an outgoing message destination, but this is not supported.")
  }

  override def validateSubscribingStream[T](stream: SubscribingStream[T]): ValidatedSubscribingStream[T] = {
    if (stream.wrapperType().isPresent) {
      if (TypeToken.of(stream.wrapperType().get()).getRawType != classOf[Message[_]]) {
        throw new DeploymentException(s"Unsupported wrapper type ${stream.wrapperType().get()} for incoming messages in " + stream.annotated())
      }
    }

    if (!TypeToken.of(stream.messageType()).isSubtypeOf(classOf[AggregateEvent[_]])) {
      throw new DeploymentException(s"EventLog subscribers can only subscribe to subclasses of ${classOf[AggregateEvent[_]]}, but ${stream.annotated()} is subscribing to ${stream.messageType}")
    }

    val castStream = stream.asInstanceOf[SubscribingStream[DummyEvent]]

    val validated = doValidateSubscribingStream(castStream)

    validated.asInstanceOf[ValidatedSubscribingStream[T]]
  }

  private def doValidateSubscribingStream[E <: AggregateEvent[E]](stream: SubscribingStream[E]): ValidatedSubscribingStream[E] = {
    if (stream.wrapperType().isPresent) {
      if (TypeToken.of(stream.wrapperType().get()).getRawType != classOf[Message[_]]) {
        throw new DeploymentException(s"Unsupported wrapper type ${stream.wrapperType().get()} for incoming messages in " + stream.annotated())
      }
    }

    val eventLogConsumer = stream.annotated().getAnnotation(classOf[EventLogConsumer])

    val eventClass: Class[E] = stream.messageType() match {
      case clazz: Class[E] => clazz
      case other => throw new DeploymentException(s"Cannot determine concrete message class from $other for incoming messages in ${stream.annotated()}")
    }
    val tags = {
      if (eventLogConsumer != null) {
        if (eventLogConsumer.eventTagProvider() != classOf[EventTagProvider[E]]) {
          eventLogConsumer.eventTagProvider().newInstance().asInstanceOf[EventTagProvider[E]].eventTags()
        } else {
          val tagName = if (eventLogConsumer.tagName() == "") {
            eventClass.getName
          } else {
            eventLogConsumer.tagName()
          }
          if (eventLogConsumer.numShards() > 0) {
            AggregateEventTag.sharded(eventClass, tagName, eventLogConsumer.numShards()).allTags
          } else {
            TreePVector.singleton(AggregateEventTag.of(eventClass, tagName))
          }
        }
      } else {
        TreePVector.singleton(AggregateEventTag.of(eventClass))
      }
    }
    val id = if (eventLogConsumer != null && eventLogConsumer.readSideId() != "") {
      eventLogConsumer.readSideId()
    } else {
      stream.annotated().asInstanceOf[AnnotatedMember[_]].getJavaMember.getName
    }

    new DistributedEventLogSubscribingStream[E](tags, readSide, offsetStore, id)
  }

  private trait DummyEvent extends AggregateEvent[DummyEvent]
}

class DistributedEventLogSubscribingStream[T <: AggregateEvent[T]](tags: PSequence[AggregateEventTag[T]],
  readSide: ReadSideImpl, offsetStore: OffsetStore, id: String)(implicit ec: ExecutionContext) extends ValidatedSubscribingStream[T] {

  private val started = new AtomicBoolean()

  private def checkNotStarted(): Unit = {
    if (!started.compareAndSet(false, true)) {
      throw new IllegalStateException("Distributed event log subscribing stream with id " + id + " must only be subscribed to once.")
    }
  }

  override def runFlow(createConsumer: Creator[Flow[Message[T], Message[_], NotUsed]]): Closeable = {
    checkNotStarted()
    readSide.registerFactory[T](Context.noContext(() =>
      new EventLogFlowSubscriberReadSideProcessor[T](offsetStore, createConsumer, tags, id)
    ), classOf[EventLogFlowSubscriberReadSideProcessor[T]])
    () => ()
  }

  override def runSink(createConsumer: Creator[Sink[Message[T], NotUsed]]): Closeable = {
    checkNotStarted()
    readSide.registerFactory[T](Context.noContext(() =>
      new EventLogSinkSubscriberReadSideProcessor[T](offsetStore, createConsumer, tags, id)
    ), classOf[EventLogSinkSubscriberReadSideProcessor[T]])
    () => ()
  }
}

abstract class EventLogSubscriberReadSideProcessor[T <: AggregateEvent[T]](
  offsetStore: OffsetStore,
  override val aggregateTags: PSequence[AggregateEventTag[T]],
  override val readSideName: String)(implicit ec: ExecutionContext) extends ReadSideProcessor[T] {

  abstract class EventLogReadSideHandler[T <: AggregateEvent[T]] extends ReadSideHandler[T] {
    @volatile
    protected var offsetDao: OffsetDao = _

    override def prepare(tag: AggregateEventTag[T]): CompletionStage[Offset] = {
      offsetStore.prepare(readSideName, tag.tag).map { offsetDao =>
        this.offsetDao = offsetDao
        OffsetAdapter.offsetToDslOffset(offsetDao.loadedOffset)
      }.toJava
    }
  }
}

class EventLogFlowSubscriberReadSideProcessor[T <: AggregateEvent[T]](
  offsetStore: OffsetStore, createConsumer: Creator[Flow[Message[T], Message[_], NotUsed]],
  tags: PSequence[AggregateEventTag[T]], id: String)(implicit ec: ExecutionContext) extends EventLogSubscriberReadSideProcessor[T](offsetStore, tags, id) {

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[T] = new EventLogReadSideHandler[T] {
    override def handle(): Flow[japi.Pair[T, Offset], Done, _] = {
      scaladsl.Flow[japi.Pair[T, Offset]]
        .map(pair => new EventLogMessage(offsetDao, pair.second, pair.first))
        .via(createConsumer.create())
        .mapAsync(1)(_.ack().toScala.map(_ => Done))
        .asJava
    }
  }
}

class EventLogSinkSubscriberReadSideProcessor[T <: AggregateEvent[T]](
  offsetStore: OffsetStore, createConsumer: Creator[Sink[Message[T], NotUsed]],
  tags: PSequence[AggregateEventTag[T]], id: String)(implicit ec: ExecutionContext) extends EventLogSubscriberReadSideProcessor[T](offsetStore, tags, id) {

  override def buildHandler(): ReadSideProcessor.ReadSideHandler[T] = new EventLogReadSideHandler[T] {
    override def handle(): Flow[japi.Pair[T, Offset], Done, _] = {
      scaladsl.Flow[japi.Pair[T, Offset]]
        .map(pair => new EventLogMessage(offsetDao, pair.second, pair.first))
        .via(Flow.fromSinkAndSourceCoupled(createConsumer.create(), scaladsl.Source.maybe))
        .asJava
    }
  }
}

class EventLogMessage[T](offsetDao: OffsetDao, offset: Offset, override val getPayload: T) extends Message[T] {
  override def ack(): CompletionStage[Void] = {
    offsetDao.saveOffset(OffsetAdapter.dslOffsetToOffset(offset)).toJava.thenApply(done => null)
  }
}

class EventLogMessagingExtension extends Extension {
  def registerEventLogMessagingProvider(@Observes beforeBeanDiscovery: BeforeBeanDiscovery): Unit = {
    beforeBeanDiscovery.addAnnotatedType(classOf[EventLogMessagingProvider], classOf[EventLogMessagingProvider].getName)
  }
}