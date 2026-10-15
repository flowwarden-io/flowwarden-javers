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
import io.flowwarden.javers.test.fixture.Product;
import io.flowwarden.stream.registration.StreamRegistration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HandlerSignatureResolutionTest {

    private JaversStreamContributor contributor;

    @BeforeEach
    void setUp() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getEnvironment()).thenReturn(new MockEnvironment());
        contributor = new JaversStreamContributor();
        contributor.setApplicationContext(ctx);
    }

    private void contribute(Object bean, String beanName) {
        contributor.contribute(new StreamRegistration(), beanName, bean);
    }

    @Test
    void entityAndContextSignatureIsAccepted() {
        assertThatCode(() -> contribute(new EntityAndCtx(), "b")).doesNotThrowAnyException();
    }

    @Test
    void entityOnlySignatureIsAccepted() {
        assertThatCode(() -> contribute(new EntityOnly(), "b")).doesNotThrowAnyException();
    }

    @Test
    void contextOnlySignatureIsAccepted() {
        assertThatCode(() -> contribute(new CtxOnly(), "b")).doesNotThrowAnyException();
    }

    @Test
    void twoParamsWithoutJaversChangeContextRejected() {
        assertThatThrownBy(() -> contribute(new TwoParamsWrong(), "b"))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("invalid signature");
    }

    @Test
    void threeParamsRejected() {
        assertThatThrownBy(() -> contribute(new ThreeParams(), "b"))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("invalid signature");
    }

    @Test
    void noParamsRejected() {
        assertThatThrownBy(() -> contribute(new NoParams(), "b"))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("invalid signature");
    }

    // ---- Beans ----

    @JaversStream(entityType = Product.class)
    static class EntityAndCtx {
        @OnInitial
        void h(Product p, JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class)
    static class EntityOnly {
        @OnInitial
        void h(Product p) {}
    }

    @JaversStream(entityType = Product.class)
    static class CtxOnly {
        @OnInitial
        void h(JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class)
    static class TwoParamsWrong {
        @OnInitial
        void h(Product p, String wrong) {}
    }

    @JaversStream(entityType = Product.class)
    static class ThreeParams {
        @OnInitial
        void h(Product p, JaversChangeContext<Product> ctx, String extra) {}
    }

    @JaversStream(entityType = Product.class)
    static class NoParams {
        @OnInitial
        void h() {}
    }
}
