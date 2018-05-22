package com.lightbend.lagom.javadsl.microprofile.messaging;

import com.lightbend.lagom.javadsl.persistence.AggregateEvent;
import com.lightbend.lagom.javadsl.persistence.AggregateEventTag;
import org.pcollections.PSequence;

/**
 * Provides the event tags for an {@link EventLog} subscriber.
 *
 * This must be instantiable using the noarg constructor.
 */
public interface EventTagProvider<T extends AggregateEvent<T>> {
  /**
   * Get the event tags for an {@link EventLog} subscriber to subscribe to.
   */
  PSequence<AggregateEventTag<T>> eventTags();
}
