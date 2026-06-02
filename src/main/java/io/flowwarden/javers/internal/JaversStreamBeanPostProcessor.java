package io.flowwarden.javers.internal;

import io.flowwarden.javers.JaversChangeContext;
import io.flowwarden.javers.annotation.JaversStream;
import io.flowwarden.javers.annotation.OnInitial;
import io.flowwarden.javers.annotation.OnTerminal;
import io.flowwarden.javers.annotation.OnUpdate;
import io.flowwarden.stream.ChangeStreamContext;
import io.flowwarden.stream.DeploymentMode;
import io.flowwarden.stream.FullDocumentBeforeChangeMode;
import io.flowwarden.stream.FullDocumentMode;
import io.flowwarden.stream.annotation.Checkpoint;
import io.flowwarden.stream.annotation.DeadLetterQueue;
import io.flowwarden.stream.annotation.RetryPolicy;
import io.flowwarden.stream.core.ContextHandler;
import io.flowwarden.stream.internal.discovery.ChangeStreamDefinition;
import io.flowwarden.stream.internal.discovery.ErrorHandlerResolver;
import io.flowwarden.stream.internal.discovery.HandlerMethod;
import io.flowwarden.stream.internal.discovery.StreamConfig;
import io.flowwarden.stream.internal.discovery.StreamRegistry;
import org.bson.Document;
import org.javers.core.Javers;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.javers.core.metamodel.object.SnapshotType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers beans annotated with {@link JaversStream} and registers them as
 * FlowWarden Change Stream definitions watching the Javers snapshot collection.
 */
public class JaversStreamBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JaversStreamBeanPostProcessor.class);
    private static final String DEFAULT_SNAPSHOT_COLLECTION = "jv_snapshots";

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        JaversStream annotation = AnnotationUtils.findAnnotation(targetClass, JaversStream.class);
        if (annotation == null) {
            return bean;
        }

        // Discover typed handlers
        Map<String, List<JaversHandlerMethod>> handlers = discoverHandlers(targetClass, beanName);

        if (handlers.isEmpty()) {
            throw new BeanCreationException(beanName,
                    "@JaversStream class " + targetClass.getName()
                            + " must have at least one handler method (@OnInitial, @OnUpdate, or @OnTerminal)");
        }

        // Resolve snapshot collection name
        String snapshotCollection = resolveSnapshotCollection(annotation);

        // Resolve stream name
        String streamName = annotation.name().isEmpty()
                ? toKebabCase(targetClass.getSimpleName())
                : annotation.name();

        // Get FlowWarden annotations that should still work
        Checkpoint cpAnnotation = AnnotationUtils.findAnnotation(targetClass, Checkpoint.class);
        RetryPolicy retryAnnotation = AnnotationUtils.findAnnotation(targetClass, RetryPolicy.class);
        DeadLetterQueue dlqAnnotation = AnnotationUtils.findAnnotation(targetClass, DeadLetterQueue.class);

        // Build the internal dispatch handler
        Class<?> entityType = annotation.entityType();
        ContextHandler<?> dispatchHandler = ctx -> {
            dispatch(bean, ctx, entityType, handlers);
        };

        HandlerMethod onChangeHandler = HandlerMethod.fromContextHandler(dispatchHandler);

        StreamConfig config = new StreamConfig(
                true,
                annotation.autoStart(),
                Document.class,
                "",
                FullDocumentMode.UPDATE_LOOKUP,
                FullDocumentBeforeChangeMode.OFF,
                DeploymentMode.ALL_INSTANCES
        );

        // Pass discovered Javers handler names as metadata for the reporter
        Map<String, Object> metadata = Map.of(
                "handlers", handlers.keySet().stream()
                        .map(k -> "JAVERS_" + k)
                        .sorted()
                        .toList()
        );

        ChangeStreamDefinition definition = new ChangeStreamDefinition(
                streamName,
                snapshotCollection,
                annotation.database(),
                "",
                bean,
                onChangeHandler,
                Collections.emptyMap(),
                config,
                null,  // pipeline — handled via internal filtering
                null,  // filter
                cpAnnotation,
                retryAnnotation,
                dlqAnnotation,
                new ErrorHandlerResolver(List.of()),
                metadata
        );

        // Register with FlowWarden's StreamRegistry
        StreamRegistry registry = applicationContext.getBean(StreamRegistry.class);
        registry.register(definition);

        log.info("Discovered Javers Stream '{}' on collection '{}' for entity {} (handlers: {})",
                streamName, snapshotCollection, entityType.getSimpleName(),
                String.join(", ", handlers.keySet()));

        return bean;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatch(Object bean, ChangeStreamContext<?> ctx, Class<?> entityType,
                          Map<String, List<JaversHandlerMethod>> handlers) {
        Document fullDoc = ((ChangeStreamContext<Document>) (ChangeStreamContext) ctx)
                .getFullDocument(Document.class).orElse(null);
        if (fullDoc == null) {
            return;
        }

        // Filter by entity type
        Document globalId = fullDoc.get("globalId", Document.class);
        if (globalId == null) {
            return;
        }
        String entity = globalId.getString("entity");
        if (entity == null || !entity.equals(entityType.getName())) {
            return;
        }

        String type = fullDoc.getString("type");
        if (type == null) {
            return;
        }

        // Deserialize the entity state
        Document state = fullDoc.get("state", Document.class);
        Object entityInstance = null;
        if (state != null) {
            try {
                MongoConverter converter = applicationContext.getBean(MongoConverter.class);
                entityInstance = converter.read(entityType, state);
            } catch (Exception e) {
                log.warn("Failed to deserialize Javers state for entity {}: {}",
                        entityType.getSimpleName(), e.getMessage());
            }
        }

        // Build CdoSnapshot if Javers is available
        CdoSnapshot snapshot = null;
        try {
            Javers javers = applicationContext.getBean(Javers.class);
            String json = fullDoc.toJson();
            snapshot = javers.getJsonConverter().fromJson(json, CdoSnapshot.class);
        } catch (Exception e) {
            log.debug("Could not deserialize CdoSnapshot via Javers converter: {}", e.getMessage());
        }

        // Build JaversChangeContext
        DefaultJaversChangeContext<?> javersCtx;
        if (snapshot != null) {
            javersCtx = new DefaultJaversChangeContext<>(snapshot, ctx);
        } else {
            javersCtx = buildFallbackContext(fullDoc, ctx);
        }

        // Dispatch to matching handlers
        String handlerKey = switch (type) {
            case "INITIAL" -> "OnInitial";
            case "UPDATE" -> "OnUpdate";
            case "TERMINAL" -> "OnTerminal";
            default -> null;
        };

        if (handlerKey == null) {
            return;
        }

        List<JaversHandlerMethod> matchingHandlers = handlers.get(handlerKey);
        if (matchingHandlers == null || matchingHandlers.isEmpty()) {
            return;
        }

        for (JaversHandlerMethod handler : matchingHandlers) {
            try {
                handler.invoke(bean, entityInstance, javersCtx);
            } catch (Exception e) {
                throw new RuntimeException("Error invoking @" + handlerKey + " handler: " + e.getMessage(), e);
            }
        }
    }

    private DefaultJaversChangeContext<?> buildFallbackContext(Document fullDoc, ChangeStreamContext<?> ctx) {
        // Fallback when Javers converter is not available — build a minimal snapshot-like context
        // This shouldn't happen in normal operation since Javers must be on the classpath
        log.warn("Building fallback JaversChangeContext without CdoSnapshot deserialization");
        return new DefaultJaversChangeContext<>(null, ctx);
    }

    // --- Handler discovery ---

    private Map<String, List<JaversHandlerMethod>> discoverHandlers(Class<?> targetClass, String beanName) {
        Map<String, List<JaversHandlerMethod>> result = new HashMap<>();

        discoverAnnotatedMethods(targetClass, beanName, OnInitial.class, "OnInitial", result);
        discoverAnnotatedMethods(targetClass, beanName, OnUpdate.class, "OnUpdate", result);
        discoverAnnotatedMethods(targetClass, beanName, OnTerminal.class, "OnTerminal", result);

        return result;
    }

    private void discoverAnnotatedMethods(Class<?> targetClass, String beanName,
                                           Class<? extends Annotation> annotationType, String key,
                                           Map<String, List<JaversHandlerMethod>> result) {
        for (Method method : targetClass.getDeclaredMethods()) {
            if (AnnotationUtils.findAnnotation(method, annotationType) == null) {
                continue;
            }

            JaversHandlerMethod.SignatureStyle style = resolveSignatureStyle(method, targetClass, beanName, annotationType);
            method.setAccessible(true);
            result.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new JaversHandlerMethod(method, style));
        }
    }

    private JaversHandlerMethod.SignatureStyle resolveSignatureStyle(Method method, Class<?> targetClass,
                                                                      String beanName,
                                                                      Class<? extends Annotation> annotationType) {
        Class<?>[] params = method.getParameterTypes();
        String annName = "@" + annotationType.getSimpleName();

        if (params.length == 1) {
            if (JaversChangeContext.class.isAssignableFrom(params[0])) {
                return JaversHandlerMethod.SignatureStyle.CONTEXT_ONLY;
            }
            return JaversHandlerMethod.SignatureStyle.ENTITY_ONLY;
        }
        if (params.length == 2) {
            if (!JaversChangeContext.class.isAssignableFrom(params[1])) {
                throw new BeanCreationException(beanName,
                        annName + " method " + targetClass.getName() + "#" + method.getName()
                                + " has invalid signature. Expected: void method(T entity, JaversChangeContext ctx)");
            }
            return JaversHandlerMethod.SignatureStyle.ENTITY_AND_CONTEXT;
        }

        throw new BeanCreationException(beanName,
                annName + " method " + targetClass.getName() + "#" + method.getName()
                        + " has invalid signature. Supported: (JaversChangeContext), (T entity), or (T entity, JaversChangeContext)");
    }

    // --- Collection resolution ---

    private String resolveSnapshotCollection(JaversStream annotation) {
        // 1. Explicit annotation value
        if (!annotation.snapshotCollection().isEmpty()) {
            return annotation.snapshotCollection();
        }

        // 2. Javers property
        if (applicationContext != null) {
            String fromProperty = applicationContext.getEnvironment()
                    .getProperty("javers.snapshotCollectionName");
            if (fromProperty != null && !fromProperty.isEmpty()) {
                return fromProperty;
            }
        }

        // 3. Default
        return DEFAULT_SNAPSHOT_COLLECTION;
    }

    // --- Utilities ---

    static String toKebabCase(String className) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < className.length(); i++) {
            char c = className.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Encapsulates a discovered Javers handler method with its invocation style.
     */
    static final class JaversHandlerMethod {

        enum SignatureStyle {
            CONTEXT_ONLY,
            ENTITY_ONLY,
            ENTITY_AND_CONTEXT
        }

        private final Method method;
        private final SignatureStyle style;

        JaversHandlerMethod(Method method, SignatureStyle style) {
            this.method = method;
            this.style = style;
        }

        void invoke(Object bean, Object entity, JaversChangeContext<?> ctx) throws Exception {
            try {
                switch (style) {
                    case CONTEXT_ONLY -> method.invoke(bean, ctx);
                    case ENTITY_ONLY -> method.invoke(bean, entity);
                    case ENTITY_AND_CONTEXT -> method.invoke(bean, entity, ctx);
                }
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception ex) throw ex;
                throw new RuntimeException(cause);
            }
        }
    }
}
