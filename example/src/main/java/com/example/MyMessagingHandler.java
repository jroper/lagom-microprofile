package com.example;

import com.lightbend.lagom.javadsl.microprofile.messaging.EventLog;
import com.lightbend.microprofile.reactive.messaging.kafka.Kafka;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.ReactiveStreams;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MyMessagingHandler {

  @Incoming(topic = "my.messages.in1", provider = Kafka.class)
  @Outgoing(topic = "my.messages.out1", provider = Kafka.class)
  public ProcessorBuilder<String, String> processMessage() {
    return ReactiveStreams.<String>builder()
        .map(msg -> {
          System.out.println("Got this message: " + msg);
          return "I processed " + msg;
        });
  }

  @Incoming(provider = EventLog.class)
  @Outgoing(topic = "my.published.messages", provider = Kafka.class)
  public String publishEventLog(MyEvent myEvent) {
    System.out.println("Publishing event " + myEvent.getValue());
    return "published event " + myEvent.getValue();
  }
}
