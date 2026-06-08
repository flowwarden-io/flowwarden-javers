package io.flowwarden.javers.internal;

import io.flowwarden.javers.JaversChangeContext;
import io.flowwarden.javers.annotation.JaversStream;
import io.flowwarden.javers.annotation.OnInitial;
import io.flowwarden.javers.test.fixture.Product;
import io.flowwarden.stream.internal.discovery.StreamRegistry;
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

    private JaversStreamBeanPostProcessor bpp;

    @BeforeEach
    void setUp() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBean(StreamRegistry.class)).thenReturn(mock(StreamRegistry.class));
        when(ctx.getEnvironment()).thenReturn(new MockEnvironment());
        bpp = new JaversStreamBeanPostProcessor();
        bpp.setApplicationContext(ctx);
    }

    @Test
    void entityAndContextSignatureIsAccepted() {
        assertThatCode(() -> bpp.postProcessAfterInitialization(new EntityAndCtx(), "b"))
                .doesNotThrowAnyException();
    }

    @Test
    void entityOnlySignatureIsAccepted() {
        assertThatCode(() -> bpp.postProcessAfterInitialization(new EntityOnly(), "b"))
                .doesNotThrowAnyException();
    }

    @Test
    void contextOnlySignatureIsAccepted() {
        assertThatCode(() -> bpp.postProcessAfterInitialization(new CtxOnly(), "b"))
                .doesNotThrowAnyException();
    }

    @Test
    void twoParamsWithoutJaversChangeContextRejected() {
        assertThatThrownBy(() -> bpp.postProcessAfterInitialization(new TwoParamsWrong(), "b"))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("invalid signature");
    }

    @Test
    void threeParamsRejected() {
        assertThatThrownBy(() -> bpp.postProcessAfterInitialization(new ThreeParams(), "b"))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("invalid signature");
    }

    @Test
    void noParamsRejected() {
        assertThatThrownBy(() -> bpp.postProcessAfterInitialization(new NoParams(), "b"))
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
