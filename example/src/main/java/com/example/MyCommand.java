package com.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.Jsonable;

public class MyCommand implements PersistentEntity.ReplyType<Integer>, Jsonable {
  private final String value;

  @JsonCreator
  public MyCommand(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}