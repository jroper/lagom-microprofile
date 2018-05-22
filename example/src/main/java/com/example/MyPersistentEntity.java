package com.example;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.javadsl.persistence.cdi.PersistenceScoped;

import java.util.Optional;

@PersistenceScoped
public class MyPersistentEntity extends PersistentEntity<MyCommand, MyEvent, Integer> {

  @Override
  public Behavior initialBehavior(Optional<Integer> snapshotState) {
    BehaviorBuilder builder = newBehaviorBuilder(snapshotState.orElse(0));

    builder.setCommandHandler(MyCommand.class, (cmd, ctx) ->
          ctx.thenPersist(new MyEvent(cmd.getValue()), e -> ctx.reply(state()))
        );
    builder.setEventHandler(MyEvent.class, myEvent -> state() + 1);

    return builder.build();
  }
}
