package io.flowwarden.javers.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler for Javers {@code UPDATE} snapshots — subsequent
 * modifications to an entity already tracked by Javers.
 *
 * <p>Supported signatures:</p>
 * <ul>
 *   <li>{@code void handle(T entity, JaversChangeContext<T> ctx)}</li>
 *   <li>{@code void handle(JaversChangeContext<T> ctx)}</li>
 *   <li>{@code void handle(T entity)}</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnUpdate {
}
