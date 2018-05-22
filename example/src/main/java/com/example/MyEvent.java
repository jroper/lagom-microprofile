package com.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTagger;
import com.lightbend.lagom.serialization.Jsonable;

public class MyEvent implements AggregateEvent<MyEvent>, Jsonable {
  private final String value;

  @JsonCreator
  public MyEvent(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  @Override
  public AggregateEventTagger<MyEvent> aggregateTag() {
    return AggregateEventTag.of(MyEvent.class);
  }
}
