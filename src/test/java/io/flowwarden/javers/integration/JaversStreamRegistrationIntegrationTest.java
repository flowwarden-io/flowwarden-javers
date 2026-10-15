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
package io.flowwarden.javers.integration;

import io.flowwarden.javers.JaversChangeContext;
import io.flowwarden.javers.annotation.JaversStream;
import io.flowwarden.javers.annotation.OnInitial;
import io.flowwarden.javers.annotation.OnUpdate;
import io.flowwarden.javers.test.SharedMongoContainer;
import io.flowwarden.javers.test.fixture.Product;
import io.flowwarden.stream.FlowWardenMetrics;
import io.flowwarden.stream.annotation.EnableFlowWarden;
import io.flowwarden.stream.spi.BackpressureAction;
import io.flowwarden.stream.spi.ChangeEventMetadata;
import io.flowwarden.stream.spi.StreamConfiguration;
import io.flowwarden.stream.spi.StreamMetricsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The {@code @JaversStream} bean is registered through the public registration API and
 * started like any stream — observed through public API only: the {@code onStreamStarted}
 * configuration snapshot, which is what the reporter and the Console see (including the
 * Javers handler names).
 */
@SpringBootTest(classes = JaversStreamRegistrationIntegrationTest.TestApp.class)
@ActiveProfiles("test")
class JaversStreamRegistrationIntegrationTest {

    private static final RecordingMetrics METRICS = new RecordingMetrics();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", SharedMongoContainer.MONGO::getReplicaSetUrl);
    }

    @BeforeAll
    static void recordStartSignals() {
        // Installed before the context starts the streams: onStreamStarted fires once per stream.
        FlowWardenMetrics.setProvider(METRICS);
    }

    @AfterAll
    static void resetProvider() {
        FlowWardenMetrics.setProvider(StreamMetricsProvider.noOp());
    }

    @Test
    void javersStreamHandlerIsRegisteredAndStartedAtStartup() {
        await().atMost(Duration.ofSeconds(10))
                .until(() -> METRICS.started.containsKey("product-audit-handler"));

        StreamConfiguration config = METRICS.started.get("product-audit-handler");
        assertThat(config.collection()).isEqualTo("jv_snapshots");
        assertThat(config.handlers())
                .as("the Javers handlers are what monitoring sees, not a bare INSERT")
                .containsExactly("JAVERS_OnInitial", "JAVERS_OnUpdate");
    }

    @SpringBootApplication
    @EnableFlowWarden
    @Import(ProductAuditHandler.class)
    static class TestApp {
    }

    @JaversStream(entityType = Product.class)
    static class ProductAuditHandler {

        @OnInitial
        void onInitial(Product product, JaversChangeContext<Product> ctx) {}

        @OnUpdate
        void onUpdate(Product product, JaversChangeContext<Product> ctx) {}
    }

    static final class RecordingMetrics implements StreamMetricsProvider {
        final Map<String, StreamConfiguration> started = new ConcurrentHashMap<>();

        @Override
        public void onStreamStarted(String streamName, StreamConfiguration config) {
            started.put(streamName, config);
        }

        @Override public void onEventReceived(String streamName, ChangeEventMetadata metadata) {}
        @Override public void onEventProcessed(String streamName, long durationNanos, boolean success) {}
        @Override public void onEventError(String streamName, Throwable error, boolean willRetry,
                                           int attemptNumber, ChangeEventMetadata metadata) {}
        @Override public void onCheckpoint(String streamName, String resumeToken) {}
        @Override public void onBufferStatus(String streamName, int currentSize, int maxSize) {}
        @Override public void onBackpressure(String streamName, BackpressureAction action) {}
        @Override public void onEventSentToDlq(String streamName) {}
        @Override public void onOplogStats(double logLengthHours, String status) {}
    }
}
