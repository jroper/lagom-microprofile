package com.lightbend.lagom.internal.microprofile.messaging

import java.lang.reflect.Type

import akka.actor.ActorSystem
import com.fasterxml.jackson.databind.{ObjectReader, ObjectWriter}
import com.lightbend.lagom.internal.jackson.JacksonObjectMapperProvider
import com.lightbend.microprofile.reactive.messaging.spi.{MessageDeserializer, MessageSerializer, SerializationSupport}
import javax.enterprise.context.ApplicationScoped
import javax.inject.Inject

@ApplicationScoped
class JacksonSerializationSupport @Inject() (system: ActorSystem) extends SerializationSupport {

  private val objectMapper = JacksonObjectMapperProvider(system).objectMapper

  override def serializerFor[T](`type`: Type): MessageSerializer[T] =
    new JacksonMessageSerializer[T](objectMapper.writerFor(objectMapper.constructType(`type`)))

  override def deserializerFor[T](`type`: Type): MessageDeserializer[T] =
    new JacksonMessageDeserializer[T](objectMapper.readerFor(objectMapper.constructType(`type`)))
}

class JacksonMessageSerializer[T](writer: ObjectWriter) extends MessageSerializer[T] {
  override def toBytes(message: T): Array[Byte] = writer.writeValueAsBytes(message)
}

class JacksonMessageDeserializer[T](reader: ObjectReader) extends MessageDeserializer[T] {
  override def fromBytes(bytes: Array[Byte]): T = reader.readValue(bytes)
}
