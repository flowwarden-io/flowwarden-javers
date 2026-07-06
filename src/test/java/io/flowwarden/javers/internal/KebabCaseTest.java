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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KebabCaseTest {

    @Test
    void convertsSimplePascalCase() {
        assertThat(JaversStreamBeanPostProcessor.toKebabCase("ProductAuditHandler"))
                .isEqualTo("product-audit-handler");
    }

    @Test
    void convertsTwoWordPascalCase() {
        assertThat(JaversStreamBeanPostProcessor.toKebabCase("SimpleClass"))
                .isEqualTo("simple-class");
    }

    @Test
    void singleLowercaseWordStaysUnchanged() {
        assertThat(JaversStreamBeanPostProcessor.toKebabCase("lowercase"))
                .isEqualTo("lowercase");
    }

    @Test
    void singleUppercaseWordBecomesLowercase() {
        assertThat(JaversStreamBeanPostProcessor.toKebabCase("Product"))
                .isEqualTo("product");
    }

    @Test
    void consecutiveCapitalsProduceOneDashPerCapital() {
        // Current contract: every uppercase letter past index 0 is preceded by a dash
        assertThat(JaversStreamBeanPostProcessor.toKebabCase("ABCHandler"))
                .isEqualTo("a-b-c-handler");
    }

    @Test
    void emptyStringReturnsEmpty() {
        assertThat(JaversStreamBeanPostProcessor.toKebabCase(""))
                .isEqualTo("");
    }
}
