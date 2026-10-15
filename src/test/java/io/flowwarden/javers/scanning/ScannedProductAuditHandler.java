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

import io.flowwarden.javers.JaversChangeContext;
import io.flowwarden.javers.annotation.JaversStream;
import io.flowwarden.javers.annotation.OnInitial;
import io.flowwarden.javers.test.fixture.Product;

/**
 * Handler declared with {@code @JaversStream} alone — no {@code @Component}, no
 * {@code @Import} anywhere. It must still become a bean through component scanning.
 */
@JaversStream(entityType = Product.class)
class ScannedProductAuditHandler {

    @OnInitial
    void onInitial(Product product, JaversChangeContext<Product> ctx) {
    }
}
