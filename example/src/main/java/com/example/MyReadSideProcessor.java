package com.example;

import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.ReadSideProcessor;
import com.lightbend.lagom.javadsl.persistence.cassandra.CassandraReadSide;
import com.lightbend.lagom.javadsl.persistence.cdi.PersistenceScoped;
import org.pcollections.PSequence;
import org.pcollections.TreePVector;

import javax.inject.Inject;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

@PersistenceScoped
public class MyReadSideProcessor extends ReadSideProcessor<MyEvent> {

  private final CassandraReadSide cassandraReadSide;

  @Inject
  public MyReadSideProcessor(CassandraReadSide cassandraReadSide) {
    this.cassandraReadSide = cassandraReadSide;
  }

  @Override
  public ReadSideHandler<MyEvent> buildHandler() {
    return cassandraReadSide.<MyEvent>builder("my-read-side")
        .setEventHandler(MyEvent.class, evt -> {
          System.out.println("I'm handling a " + evt.getValue() + " event!");
          return CompletableFuture.completedFuture(Collections.emptyList());
        }).build();
  }

  @Override
  public PSequence<AggregateEventTag<MyEvent>> aggregateTags() {
    return TreePVector.singleton(AggregateEventTag.of(MyEvent.class));
  }
}
