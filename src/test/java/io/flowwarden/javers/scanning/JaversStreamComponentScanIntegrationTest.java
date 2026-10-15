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
package io.flowwarden.javers.scanning;

import io.flowwarden.javers.test.SharedMongoContainer;
import io.flowwarden.stream.FlowWardenMetrics;
import io.flowwarden.stream.spi.BackpressureAction;
import io.flowwarden.stream.spi.ChangeEventMetadata;
import io.flowwarden.stream.spi.StreamConfiguration;
import io.flowwarden.stream.spi.StreamMetricsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A class annotated only with {@code @JaversStream}, sitting in a scanned package, is a
 * bean and is registered as a stream at startup. This is the README declaration; before
 * {@code @JaversStream} became a stereotype it was silently ignored. Observed through
 * public API only: the {@code onStreamStarted} signal.
 */
@SpringBootTest(classes = ScanTestApp.class)
@ActiveProfiles("test")
class JaversStreamComponentScanIntegrationTest {

    private static final RecordingMetrics METRICS = new RecordingMetrics();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", SharedMongoContainer.MONGO::getReplicaSetUrl);
    }

    @BeforeAll
    static void recordStartSignals() {
        FlowWardenMetrics.setProvider(METRICS);
    }

    @AfterAll
    static void resetProvider() {
        FlowWardenMetrics.setProvider(StreamMetricsProvider.noOp());
    }

    @Autowired
    ApplicationContext context;

    @Test
    void handlerWithoutComponentIsPickedUpByComponentScanning() {
        assertThat(context.getBeanNamesForType(ScannedProductAuditHandler.class))
                .as("scanned as a bean without an explicit @Component")
                .hasSize(1);

        // Registered and started as a stream.
        await().atMost(Duration.ofSeconds(10))
                .until(() -> METRICS.started.containsKey("scanned-product-audit-handler"));
        assertThat(METRICS.started.get("scanned-product-audit-handler").collection())
                .isEqualTo("jv_snapshots");
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
