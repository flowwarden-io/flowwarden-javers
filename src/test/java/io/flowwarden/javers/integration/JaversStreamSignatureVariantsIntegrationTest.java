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
import io.flowwarden.javers.test.SharedMongoContainer;
import io.flowwarden.javers.test.fixture.Product;
import io.flowwarden.javers.test.fixture.ProductRepository;
import io.flowwarden.stream.annotation.EnableFlowWarden;
import io.flowwarden.stream.core.FlowWardenStreamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = JaversStreamSignatureVariantsIntegrationTest.TestApp.class)
@ActiveProfiles("test")
class JaversStreamSignatureVariantsIntegrationTest {

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", SharedMongoContainer.MONGO::getReplicaSetUrl);
    }

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityAndContextHandler entityAndCtxHandler;

    @Autowired
    ContextOnlyHandler ctxOnlyHandler;

    @Autowired
    EntityOnlyHandler entityOnlyHandler;

    @Autowired
    FlowWardenStreamManager streamManager;

    @BeforeEach
    void awaitAllStreamsRunning() {
        await().atMost(Duration.ofSeconds(15)).until(() ->
                streamManager.isRunning("entity-and-context-handler")
                        && streamManager.isRunning("context-only-handler")
                        && streamManager.isRunning("entity-only-handler"));
        entityAndCtxHandler.records.clear();
        ctxOnlyHandler.records.clear();
        entityOnlyHandler.records.clear();
    }

    @Test
    void allThreeSignatureVariantsAreInvokedOnSave() throws Exception {
        String id = "p-" + UUID.randomUUID();
        productRepository.save(new Product(id, "Widget", 10));

        Object[] entityAndCtx = entityAndCtxHandler.records.poll(15, TimeUnit.SECONDS);
        Object[] ctxOnly = ctxOnlyHandler.records.poll(15, TimeUnit.SECONDS);
        Object[] entityOnly = entityOnlyHandler.records.poll(15, TimeUnit.SECONDS);

        assertThat(entityAndCtx).isNotNull();
        assertThat(entityAndCtx[0]).isInstanceOf(Product.class);
        assertThat(((Product) entityAndCtx[0]).getId()).isEqualTo(id);
        assertThat(entityAndCtx[1]).isInstanceOf(JaversChangeContext.class);

        assertThat(ctxOnly).isNotNull();
        assertThat(ctxOnly[0]).isInstanceOf(JaversChangeContext.class);

        assertThat(entityOnly).isNotNull();
        assertThat(entityOnly[0]).isInstanceOf(Product.class);
        assertThat(((Product) entityOnly[0]).getId()).isEqualTo(id);
    }

    @SpringBootApplication(scanBasePackageClasses = {
            JaversStreamSignatureVariantsIntegrationTest.class,
            ProductRepository.class
    })
    @EnableFlowWarden
    @EnableMongoRepositories(basePackageClasses = ProductRepository.class)
    @Import({EntityAndContextHandler.class, ContextOnlyHandler.class, EntityOnlyHandler.class})
    static class TestApp {
    }

    @JaversStream(entityType = Product.class, name = "entity-and-context-handler")
    static class EntityAndContextHandler {
        final BlockingQueue<Object[]> records = new LinkedBlockingQueue<>();

        @OnInitial
        void onInitial(Product p, JaversChangeContext<Product> ctx) {
            records.add(new Object[]{p, ctx});
        }
    }

    @JaversStream(entityType = Product.class, name = "context-only-handler")
    static class ContextOnlyHandler {
        final BlockingQueue<Object[]> records = new LinkedBlockingQueue<>();

        @OnInitial
        void onInitial(JaversChangeContext<Product> ctx) {
            records.add(new Object[]{ctx});
        }
    }

    @JaversStream(entityType = Product.class, name = "entity-only-handler")
    static class EntityOnlyHandler {
        final BlockingQueue<Object[]> records = new LinkedBlockingQueue<>();

        @OnInitial
        void onInitial(Product p) {
            records.add(new Object[]{p});
        }
    }
}
