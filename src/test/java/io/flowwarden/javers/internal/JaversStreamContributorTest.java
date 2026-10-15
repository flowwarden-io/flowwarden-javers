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
import io.flowwarden.javers.test.fixture.Product;
import io.flowwarden.stream.DeploymentMode;
import io.flowwarden.stream.FullDocumentMode;
import io.flowwarden.stream.OnHistoryLost;
import io.flowwarden.stream.OperationType;
import io.flowwarden.stream.annotation.Checkpoint;
import io.flowwarden.stream.annotation.DeadLetterQueue;
import io.flowwarden.stream.annotation.MongoDlqOptions;
import io.flowwarden.stream.annotation.RestartPolicy;
import io.flowwarden.stream.annotation.RetryPolicy;
import io.flowwarden.stream.registration.StreamRegistration;
import io.flowwarden.stream.registration.StreamSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The contributor declares every {@code @JaversStream} bean as a {@code StreamSpec}
 * through the public registration API — asserted on the spec, never on stream-core
 * internals.
 */
class JaversStreamContributorTest {

    private ApplicationContext applicationContext;
    private MockEnvironment environment;
    private JaversStreamContributor contributor;
    private StreamRegistration registration;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        environment = new MockEnvironment();
        when(applicationContext.getEnvironment()).thenReturn(environment);

        contributor = new JaversStreamContributor();
        contributor.setApplicationContext(applicationContext);
        registration = new StreamRegistration();
    }

    private StreamSpec<?> contribute(String beanName, Object bean) {
        when(applicationContext.getBeansWithAnnotation(JaversStream.class)).thenReturn(Map.of(beanName, bean));
        contributor.contribute(registration);
        assertThat(registration.streams()).hasSize(1);
        return registration.streams().get(0);
    }

    @Test
    void noJaversStreamBean_contributesNothing() {
        when(applicationContext.getBeansWithAnnotation(JaversStream.class)).thenReturn(Map.of());

        contributor.contribute(registration);

        assertThat(registration.streams()).isEmpty();
    }

    @Test
    void beanWithoutAnyHandlerMethodThrows() {
        when(applicationContext.getBeansWithAnnotation(JaversStream.class))
                .thenReturn(Map.of("emptyHandler", new EmptyHandler()));

        assertThatThrownBy(() -> contributor.contribute(registration))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("at least one handler method");
    }

    @Test
    void validBeanIsDeclaredAsAnInsertStreamWithJaversHandlerNames() {
        StreamSpec<?> spec = contribute("validHandler", new ValidHandler());

        assertThat(spec.name()).isEqualTo("valid-handler");
        assertThat(spec.collection()).isEqualTo("jv_snapshots");
        assertThat(spec.database()).isEmpty();
        assertThat(spec.documentType()).isEqualTo(org.bson.Document.class);
        assertThat(spec.fullDocument()).isEqualTo(FullDocumentMode.UPDATE_LOOKUP);
        assertThat(spec.deploymentMode()).isEqualTo(DeploymentMode.ALL_INSTANCES);
        assertThat(spec.autoStart()).isTrue();
        assertThat(spec.onChangeHandler()).isEmpty();
        assertThat(spec.typedHandlers()).containsOnlyKeys(OperationType.INSERT);
        assertThat(spec.handlerNames())
                .containsExactly("JAVERS_OnInitial", "JAVERS_OnTerminal", "JAVERS_OnUpdate");
        assertThat(spec.checkpoint()).isEmpty();
        assertThat(spec.retryPolicy()).isEmpty();
        assertThat(spec.restartPolicy()).isEmpty();
        assertThat(spec.deadLetterQueue()).isEmpty();
        assertThat(spec.mongoDlqOptions()).isEmpty();
    }

    @Test
    void explicitStreamNameOverridesKebabCase() {
        assertThat(contribute("namedHandler", new NamedHandler()).name()).isEqualTo("my-explicit-stream");
    }

    @Test
    void explicitDatabaseIsPropagated() {
        assertThat(contribute("dbHandler", new DatabaseScopedHandler()).database()).isEqualTo("my-db");
    }

    @Test
    void autoStartIsPropagated() {
        assertThat(contribute("manual", new ManualStartHandler()).autoStart()).isFalse();
    }

    @Test
    void streamCoreAnnotationsAreMappedToTheirSpecs() {
        StreamSpec<?> spec = contribute("policies", new FullyConfiguredHandler());

        assertThat(spec.checkpoint()).hasValueSatisfying(cp -> {
            assertThat(cp.saveEveryN()).isEqualTo(5);
            assertThat(cp.saveIntervalSeconds()).isEqualTo(2);
            assertThat(cp.idleHeartbeatIntervalSeconds()).isEqualTo(60);
            assertThat(cp.stuckAfterProbes()).isEqualTo(2);
            assertThat(cp.onHistoryLost()).isEqualTo(OnHistoryLost.RESUME_FROM_NOW);
        });
        assertThat(spec.retryPolicy()).hasValueSatisfying(rp -> {
            assertThat(rp.maxAttempts()).isEqualTo(4);
            assertThat(rp.initialDelay()).isEqualTo("200ms");
            assertThat(rp.retryOn()).containsExactly(IllegalStateException.class);
            assertThat(rp.jitter()).isFalse();
        });
        assertThat(spec.restartPolicy()).hasValueSatisfying(rp -> {
            assertThat(rp.maxAttempts()).isEqualTo(10);
            assertThat(rp.maxDelay()).isEqualTo("2m");
            assertThat(rp.jitter()).isTrue();
        });
        assertThat(spec.deadLetterQueue()).hasValueSatisfying(dlq -> {
            assertThat(dlq.retentionDays()).isEqualTo(7);
            assertThat(dlq.includeStackTrace()).isFalse();
        });
        assertThat(spec.mongoDlqOptions()).hasValueSatisfying(o ->
                assertThat(o.collection()).isEqualTo("javers_dlq"));
    }

    // ---- Snapshot collection resolution priority ----

    @Test
    void explicitSnapshotCollectionWinsOverEverything() {
        environment.setProperty("javers.snapshotCollectionName", "from-property");

        assertThat(contribute("explicit", new ExplicitCollectionHandler()).collection())
                .isEqualTo("custom_snapshots");
    }

    @Test
    void javersPropertyIsUsedWhenAnnotationIsEmpty() {
        environment.setProperty("javers.snapshotCollectionName", "from-property");

        assertThat(contribute("valid", new ValidHandler()).collection()).isEqualTo("from-property");
    }

    @Test
    void defaultsToJvSnapshotsWhenNoOverride() {
        assertThat(contribute("valid", new ValidHandler()).collection()).isEqualTo("jv_snapshots");
    }

    @Test
    void emptyJaversPropertyFallsBackToDefault() {
        environment.setProperty("javers.snapshotCollectionName", "");

        assertThat(contribute("valid", new ValidHandler()).collection()).isEqualTo("jv_snapshots");
    }

    // ---- Test beans ----

    @JaversStream(entityType = Product.class)
    static class EmptyHandler {
    }

    @JaversStream(entityType = Product.class)
    static class ValidHandler {
        @OnInitial
        void onInit(Product p, JaversChangeContext<Product> ctx) {}

        @OnUpdate
        void onUpd(Product p, JaversChangeContext<Product> ctx) {}

        @OnTerminal
        void onTerm(JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class, name = "my-explicit-stream")
    static class NamedHandler {
        @OnInitial
        void onInit(JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class, database = "my-db")
    static class DatabaseScopedHandler {
        @OnInitial
        void onInit(JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class, autoStart = false)
    static class ManualStartHandler {
        @OnInitial
        void onInit(JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class, snapshotCollection = "custom_snapshots")
    static class ExplicitCollectionHandler {
        @OnInitial
        void onInit(JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class)
    @Checkpoint(saveEveryN = 5, saveIntervalSeconds = 2, idleHeartbeatIntervalSeconds = 60,
            stuckAfterProbes = 2, onHistoryLost = OnHistoryLost.RESUME_FROM_NOW)
    @RetryPolicy(maxAttempts = 4, initialDelay = "200ms", retryOn = IllegalStateException.class, jitter = false)
    @RestartPolicy(maxAttempts = 10, maxDelay = "2m", jitter = true)
    @DeadLetterQueue(retentionDays = 7, includeStackTrace = false)
    @MongoDlqOptions(collection = "javers_dlq")
    static class FullyConfiguredHandler {
        @OnInitial
        void onInit(JaversChangeContext<Product> ctx) {}
    }
}
