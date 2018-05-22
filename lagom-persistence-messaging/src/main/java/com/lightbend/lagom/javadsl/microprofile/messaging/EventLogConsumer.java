package com.lightbend.lagom.javadsl.microprofile.messaging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
public @interface EventLogConsumer {
  /**
   * The read side ID, if unset, will be inferred from the annotated method name.
   */
  String readSideId() default "";

  /**
   * The name of the tag, if unset, will be inferred from the event type.
   *
   * Ignored if {@link #eventTagProvider()} is set.
   */
  String tagName() default "";

  /**
   * The number of shards used, if unset, the tag will assumed to not be sharded.
   *
   * Ignored if {@link #eventTagProvider()} is set.
   */
  int numShards() default -1;

  /**
   * The event tag provider. If unset, will use {@link #tagName()} and {@link #numShards()} to determine the event tags.
   */
  Class<? extends EventTagProvider> eventTagProvider() default EventTagProvider.class;
}