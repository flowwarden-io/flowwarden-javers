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
import io.flowwarden.stream.annotation.EnableFlowWarden;
import io.flowwarden.stream.internal.discovery.ChangeStreamDefinition;
import io.flowwarden.stream.internal.discovery.StreamRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = JaversStreamRegistrationIntegrationTest.TestApp.class)
@ActiveProfiles("test")
class JaversStreamRegistrationIntegrationTest {

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", SharedMongoContainer.MONGO::getReplicaSetUrl);
    }

    @Autowired
    StreamRegistry streamRegistry;

    @Test
    void javersStreamHandlerIsRegisteredAtStartup() {
        Optional<ChangeStreamDefinition> def = streamRegistry.findByName("product-audit-handler");

        assertThat(def).isPresent();
        assertThat(def.get().collection()).isEqualTo("jv_snapshots");
        assertThat(def.get().database()).isEmpty();

        @SuppressWarnings("unchecked")
        List<String> handlers = (List<String>) def.get().metadata().get("handlers");
        assertThat(handlers).containsExactlyInAnyOrder("JAVERS_OnInitial", "JAVERS_OnUpdate");
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
}
