package com.lightbend.lagom.javadsl.microprofile.messaging;

import org.eclipse.microprofile.reactive.messaging.MessagingProvider;

/**
 * Lagom persistent entity event log messaging provider.
 */
public class EventLog implements MessagingProvider {
  private EventLog() {}
}