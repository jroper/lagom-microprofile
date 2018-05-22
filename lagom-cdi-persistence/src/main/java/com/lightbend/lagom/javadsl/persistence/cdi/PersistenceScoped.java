package com.lightbend.lagom.javadsl.persistence.cdi;

import javax.enterprise.context.NormalScope;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Scope for any beans that are used with Lagom persistence.
 *
 * Persistent entities and ReadSides that have this scope will automatically be registered with Lagom.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
@NormalScope
public @interface PersistenceScoped {
}
