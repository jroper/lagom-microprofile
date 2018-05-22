package com.lightbend.lagom.javadsl.server.cdi;

import com.lightbend.lagom.javadsl.api.Service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the implementation of a Lagom service.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LagomService {
  Class<? extends Service> descriptor() default Service.class;
}
