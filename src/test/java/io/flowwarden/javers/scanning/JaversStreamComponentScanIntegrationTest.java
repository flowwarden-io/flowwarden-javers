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
import io.flowwarden.stream.internal.discovery.StreamRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A class annotated only with {@code @JaversStream}, sitting in a scanned package, is a
 * bean and is registered as a stream at startup. This is the README declaration; before
 * {@code @JaversStream} became a stereotype it was silently ignored.
 */
@SpringBootTest(classes = ScanTestApp.class)
@ActiveProfiles("test")
class JaversStreamComponentScanIntegrationTest {

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", SharedMongoContainer.MONGO::getReplicaSetUrl);
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    StreamRegistry streamRegistry;

    @Test
    void handlerWithoutComponentIsPickedUpByComponentScanning() {
        assertThat(context.getBeanNamesForType(ScannedProductAuditHandler.class))
                .as("scanned as a bean without an explicit @Component")
                .hasSize(1);
        assertThat(streamRegistry.findByName("scanned-product-audit-handler"))
                .as("registered as a stream at startup")
                .isPresent();
    }
}
