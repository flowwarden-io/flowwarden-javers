/*
 * Copyright 2026 FlowWarden
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flowwarden.javers.internal;

import io.flowwarden.javers.JaversChangeContext;
import io.flowwarden.javers.annotation.JaversStream;
import io.flowwarden.javers.annotation.OnInitial;
import io.flowwarden.javers.annotation.OnTerminal;
import io.flowwarden.javers.annotation.OnUpdate;
import io.flowwarden.stream.ChangeStreamContext;
import io.flowwarden.stream.DeploymentMode;
import io.flowwarden.stream.FullDocumentMode;
import io.flowwarden.stream.annotation.Checkpoint;
import io.flowwarden.stream.annotation.DeadLetterQueue;
import io.flowwarden.stream.annotation.MongoDlqOptions;
import io.flowwarden.stream.annotation.RestartPolicy;
import io.flowwarden.stream.annotation.RetryPolicy;
import io.flowwarden.stream.core.ContextHandler;
import io.flowwarden.stream.registration.CheckpointSpec;
import io.flowwarden.stream.registration.DeadLetterQueueSpec;
import io.flowwarden.stream.registration.MongoDlqOptionsSpec;
import io.flowwarden.stream.registration.RestartPolicySpec;
import io.flowwarden.stream.registration.RetryPolicySpec;
import io.flowwarden.stream.registration.StreamDefinitionContributor;
import io.flowwarden.stream.registration.StreamRegistration;
import io.flowwarden.stream.registration.StreamSpec;
import org.bson.Document;
import org.javers.core.Javers;
import org.javers.core.metamodel.object.CdoSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Declares every {@code @JaversStream} bean as a FlowWarden stream through the public
 * registration API — one {@code StreamSpec} per bean, contributed once at bootstrap.
 *
 * <p>Javers writes every snapshot (INITIAL / UPDATE / TERMINAL) as a new document in the
 * snapshot collection, so at the change stream level it is always an {@code INSERT}: the
 * stream registers a single {@code onInsert} handler that filters by entity type and routes
 * to the bean's {@code @OnInitial} / {@code @OnUpdate} / {@code @OnTerminal} methods. The
 * stream-core annotations found on the class ({@code @Checkpoint}, {@code @RetryPolicy},
 * {@code @RestartPolicy}, {@code @DeadLetterQueue}, {@code @MongoDlqOptions}) are mapped to
 * their spec equivalents; {@code handlerNames} reports the Javers handlers to monitoring
 * instead of a misleading {@code INSERT}.</p>
 *
 * <p>Only public stream-core API is used — no dependency on
 * {@code io.flowwarden.stream.internal}.</p>
 */
public class JaversStreamContributor implements StreamDefinitionContributor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(JaversStreamContributor.class);
    private static final String DEFAULT_SNAPSHOT_COLLECTION = "jv_snapshots";

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void contribute(StreamRegistration registration) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(JaversStream.class);
        beans.forEach((beanName, bean) -> contribute(registration, beanName, bean));
    }

    void contribute(StreamRegistration registration, String beanName, Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        JaversStream annotation = AnnotationUtils.findAnnotation(targetClass, JaversStream.class);
        if (annotation == null) {
            return;
        }

        Map<String, List<JaversHandlerMethod>> handlers = discoverHandlers(targetClass, beanName);
        if (handlers.isEmpty()) {
            throw new BeanCreationException(beanName,
                    "@JaversStream class " + targetClass.getName()
                            + " must have at least one handler method (@OnInitial, @OnUpdate, or @OnTerminal)");
        }

        String snapshotCollection = resolveSnapshotCollection(annotation);
        String streamName = annotation.name().isEmpty()
                ? toKebabCase(targetClass.getSimpleName())
                : annotation.name();
        Class<?> entityType = annotation.entityType();

        ContextHandler<Document> dispatchHandler = ctx -> dispatch(bean, ctx, entityType, handlers);

        StreamSpec.Builder<Document> spec = registration.stream(streamName, Document.class)
                .collection(snapshotCollection)
                .database(annotation.database())
                .autoStart(annotation.autoStart())
                .fullDocument(FullDocumentMode.UPDATE_LOOKUP)
                .deploymentMode(DeploymentMode.ALL_INSTANCES)
                .onInsert(dispatchHandler)
                .handlerNames(handlers.keySet().stream()
                        .map(k -> "JAVERS_" + k)
                        .sorted()
                        .toList());

        Checkpoint cp = AnnotationUtils.findAnnotation(targetClass, Checkpoint.class);
        if (cp != null) {
            spec.checkpoint(checkpointSpec(cp));
        }
        RetryPolicy retry = AnnotationUtils.findAnnotation(targetClass, RetryPolicy.class);
        if (retry != null) {
            spec.retryPolicy(retryPolicySpec(retry));
        }
        RestartPolicy restart = AnnotationUtils.findAnnotation(targetClass, RestartPolicy.class);
        if (restart != null) {
            spec.restartPolicy(restartPolicySpec(restart));
        }
        DeadLetterQueue dlq = AnnotationUtils.findAnnotation(targetClass, DeadLetterQueue.class);
        if (dlq != null) {
            spec.deadLetterQueue(deadLetterQueueSpec(dlq));
        }
        MongoDlqOptions mongoDlq = AnnotationUtils.findAnnotation(targetClass, MongoDlqOptions.class);
        if (mongoDlq != null) {
            spec.mongoDlqOptions(MongoDlqOptionsSpec.builder().collection(mongoDlq.collection()).build());
        }

        log.info("Discovered Javers Stream '{}' on collection '{}' for entity {} (handlers: {})",
                streamName, snapshotCollection, entityType.getSimpleName(),
                String.join(", ", handlers.keySet()));
    }

    // --- stream-core annotations → spec equivalents ---

    static CheckpointSpec checkpointSpec(Checkpoint cp) {
        return CheckpointSpec.builder()
                .saveEveryN(cp.saveEveryN())
                .saveIntervalSeconds(cp.saveIntervalSeconds())
                .idleHeartbeatIntervalSeconds(cp.idleHeartbeatIntervalSeconds())
                .stuckAfterProbes(cp.stuckAfterProbes())
                .startPosition(cp.startPosition())
                .onHistoryLost(cp.onHistoryLost())
                .build();
    }

    static RetryPolicySpec retryPolicySpec(RetryPolicy rp) {
        return RetryPolicySpec.builder()
                .maxAttempts(rp.maxAttempts())
                .initialDelay(rp.initialDelay())
                .maxDelay(rp.maxDelay())
                .multiplier(rp.multiplier())
                .retryOn(List.of(rp.retryOn()))
                .noRetryOn(List.of(rp.noRetryOn()))
                .jitter(rp.jitter())
                .build();
    }

    static RestartPolicySpec restartPolicySpec(RestartPolicy rp) {
        return RestartPolicySpec.builder()
                .maxAttempts(rp.maxAttempts())
                .initialDelay(rp.initialDelay())
                .maxDelay(rp.maxDelay())
                .multiplier(rp.multiplier())
                .jitter(rp.jitter())
                .build();
    }

    static DeadLetterQueueSpec deadLetterQueueSpec(DeadLetterQueue dlq) {
        return DeadLetterQueueSpec.builder()
                .enabled(dlq.enabled())
                .retentionDays(dlq.retentionDays())
                .includeOriginalDocument(dlq.includeOriginalDocument())
                .includeStackTrace(dlq.includeStackTrace())
                .build();
    }

    // --- Dispatch ---

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
            // Javers stores entity state under Java field names; Spring Data MongoConverter
            // resolves the @Id field via "_id". Hydrate it from globalId.cdoId so the primary
            // key round-trips back into the deserialized entity.
            Document stateWithId = new Document(state);
            if (!stateWithId.containsKey("_id")) {
                Object cdoId = globalId.get("cdoId");
                if (cdoId != null) {
                    stateWithId.put("_id", cdoId);
                }
            }
            try {
                MongoConverter converter = applicationContext.getBean(MongoConverter.class);
                entityInstance = converter.read(entityType, stateWithId);
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
