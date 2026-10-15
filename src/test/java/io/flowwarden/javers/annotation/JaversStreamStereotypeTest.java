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
package io.flowwarden.javers.annotation;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @JaversStream} must be a Spring stereotype: discovery is bean-based, so a
 * handler that is not a bean is silently ignored. Component scanning is what makes
 * the README declaration (no {@code @Component}) work.
 */
class JaversStreamStereotypeTest {

    @Test
    void javersStreamIsMetaAnnotatedWithComponent() {
        assertThat(AnnotatedElementUtils.hasAnnotation(JaversStream.class, Component.class))
                .as("@JaversStream carries @Component, like @ChangeStream in stream-core")
                .isTrue();
    }

    @Test
    void aClassAnnotatedOnlyWithJaversStreamIsAComponentCandidate() {
        assertThat(AnnotatedElementUtils.hasAnnotation(OnlyJaversStream.class, Component.class))
                .isTrue();
    }

    @JaversStream(entityType = Object.class)
    static class OnlyJaversStream {
    }
}
