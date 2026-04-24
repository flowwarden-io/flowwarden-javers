package io.flowwarden.javers.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a Javers audit stream handler for a specific entity type.
 *
 * <p>This annotation watches the Javers snapshot collection ({@code jv_snapshots} by default)
 * for changes to the specified entity type, and dispatches events to methods annotated with
 * {@link OnInitial}, {@link OnUpdate}, or {@link OnTerminal}.</p>
 *
 * <p>The handler receives the deserialized domain entity and Javers metadata
 * via {@link io.flowwarden.javers.JaversChangeContext}.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * @JaversStream(entityType = Product.class)
 * @Checkpoint(saveEveryN = 1)
 * public class ProductAuditHandler {
 *
 *     @OnInitial
 *     void onCreated(Product product, JaversChangeContext<Product> ctx) {
 *         log.info("Product created: {} by {}", product.getName(),
 *             ctx.getCommitMetadata().getAuthor());
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JaversStream {

    /**
     * The entity type to watch for Javers audit changes.
     * FlowWarden will filter snapshots by this type's fully qualified class name.
     */
    Class<?> entityType();

    /**
     * Optional stream name. If empty, auto-generated from the handler class name.
     */
    String name() default "";

    /**
     * Javers snapshot collection name. If empty, resolved from:
     * <ol>
     *   <li>Javers property {@code javers.snapshotCollectionName}</li>
     *   <li>Default: {@code "jv_snapshots"}</li>
     * </ol>
     */
    String snapshotCollection() default "";

    /**
     * MongoDB database name. If empty, uses the Spring default database.
     */
    String database() default "";

    /**
     * Whether to start the stream automatically on application startup.
     */
    boolean autoStart() default true;
}
