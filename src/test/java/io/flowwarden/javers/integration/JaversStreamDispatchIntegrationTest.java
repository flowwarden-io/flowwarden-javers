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
import io.flowwarden.javers.annotation.OnTerminal;
import io.flowwarden.javers.annotation.OnUpdate;
import io.flowwarden.javers.test.SharedMongoContainer;
import io.flowwarden.javers.test.fixture.Product;
import io.flowwarden.javers.test.fixture.ProductRepository;
import io.flowwarden.stream.annotation.EnableFlowWarden;
import io.flowwarden.stream.core.FlowWardenStreamManager;
import org.javers.core.metamodel.object.SnapshotType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = JaversStreamDispatchIntegrationTest.TestApp.class)
@ActiveProfiles("test")
class JaversStreamDispatchIntegrationTest {

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", SharedMongoContainer.MONGO::getReplicaSetUrl);
    }

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductAuditHandler handler;

    @Autowired
    FlowWardenStreamManager streamManager;

    @BeforeEach
    void awaitStreamRunning() {
        handler.reset();
        await().atMost(Duration.ofSeconds(15))
                .until(() -> streamManager.isRunning("product-audit-handler"));
    }

    @Test
    void onInitialReceivesNewProductWithDeserializedEntityAndContext() throws Exception {
        String id = "p-" + UUID.randomUUID();
        productRepository.save(new Product(id, "Widget", 10));

        Product received = handler.initialEntities.poll(15, java.util.concurrent.TimeUnit.SECONDS);
        JaversChangeContext<Product> ctx = handler.initialContexts.poll(1, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.getId()).isEqualTo(id);
        assertThat(received.getName()).isEqualTo("Widget");
        assertThat(received.getPrice()).isEqualTo(10);

        assertThat(ctx).isNotNull();
        assertThat(ctx.getSnapshotType()).isEqualTo(SnapshotType.INITIAL);
        assertThat(ctx.getVersion()).isEqualTo(1L);
        assertThat(ctx.getEntityId()).contains(id);
        assertThat(ctx.getCommitMetadata()).isNotNull();
    }

    @Test
    void onUpdateReceivesEntityWithChangedProperties() throws Exception {
        String id = "p-" + UUID.randomUUID();
        productRepository.save(new Product(id, "Widget", 10));
        handler.initialEntities.poll(15, java.util.concurrent.TimeUnit.SECONDS);

        Product updated = productRepository.findById(id).orElseThrow();
        updated.setPrice(20);
        productRepository.save(updated);

        Product received = handler.updateEntities.poll(15, java.util.concurrent.TimeUnit.SECONDS);
        JaversChangeContext<Product> ctx = handler.updateContexts.poll(1, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.getPrice()).isEqualTo(20);

        assertThat(ctx).isNotNull();
        assertThat(ctx.getSnapshotType()).isEqualTo(SnapshotType.UPDATE);
        assertThat(ctx.getVersion()).isEqualTo(2L);
        assertThat(ctx.getChangedProperties()).contains("price");
    }

    @Test
    void onTerminalReceivesContextOnDelete() throws Exception {
        String id = "p-" + UUID.randomUUID();
        productRepository.save(new Product(id, "Widget", 10));
        handler.initialEntities.poll(15, java.util.concurrent.TimeUnit.SECONDS);

        productRepository.deleteById(id);

        JaversChangeContext<Product> ctx = handler.terminalContexts.poll(15, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(ctx).isNotNull();
        assertThat(ctx.getSnapshotType()).isEqualTo(SnapshotType.TERMINAL);
        assertThat(ctx.getEntityId()).contains(id);
    }

    @SpringBootApplication(scanBasePackageClasses = {
            JaversStreamDispatchIntegrationTest.class,
            ProductRepository.class
    })
    @EnableFlowWarden
    @EnableMongoRepositories(basePackageClasses = ProductRepository.class)
    @Import(ProductAuditHandler.class)
    static class TestApp {
    }

    @JaversStream(entityType = Product.class)
    static class ProductAuditHandler {

        final BlockingQueue<Product> initialEntities = new LinkedBlockingQueue<>();
        final BlockingQueue<JaversChangeContext<Product>> initialContexts = new LinkedBlockingQueue<>();
        final BlockingQueue<Product> updateEntities = new LinkedBlockingQueue<>();
        final BlockingQueue<JaversChangeContext<Product>> updateContexts = new LinkedBlockingQueue<>();
        final BlockingQueue<JaversChangeContext<Product>> terminalContexts = new LinkedBlockingQueue<>();

        @OnInitial
        void onInitial(Product product, JaversChangeContext<Product> ctx) {
            initialEntities.add(product);
            initialContexts.add(ctx);
        }

        @OnUpdate
        void onUpdate(Product product, JaversChangeContext<Product> ctx) {
            updateEntities.add(product);
            updateContexts.add(ctx);
        }

        @OnTerminal
        void onTerminal(JaversChangeContext<Product> ctx) {
            terminalContexts.add(ctx);
        }

        void reset() {
            initialEntities.clear();
            initialContexts.clear();
            updateEntities.clear();
            updateContexts.clear();
            terminalContexts.clear();
        }
    }
}
