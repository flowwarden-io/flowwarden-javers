package io.flowwarden.javers.internal;

import io.flowwarden.javers.JaversChangeContext;
import io.flowwarden.javers.annotation.JaversStream;
import io.flowwarden.javers.annotation.OnInitial;
import io.flowwarden.javers.annotation.OnTerminal;
import io.flowwarden.javers.annotation.OnUpdate;
import io.flowwarden.javers.test.fixture.Product;
import io.flowwarden.stream.OperationType;
import io.flowwarden.stream.internal.discovery.ChangeStreamDefinition;
import io.flowwarden.stream.internal.discovery.StreamRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JaversStreamBeanPostProcessorTest {

    private ApplicationContext applicationContext;
    private StreamRegistry registry;
    private MockEnvironment environment;
    private JaversStreamBeanPostProcessor bpp;

    @BeforeEach
    void setUp() {
        applicationContext = mock(ApplicationContext.class);
        registry = mock(StreamRegistry.class);
        environment = new MockEnvironment();
        when(applicationContext.getBean(StreamRegistry.class)).thenReturn(registry);
        when(applicationContext.getEnvironment()).thenReturn(environment);

        bpp = new JaversStreamBeanPostProcessor();
        bpp.setApplicationContext(applicationContext);
    }

    @Test
    void beanWithoutJaversStreamAnnotationIsReturnedUntouched() {
        Object plainBean = new Object();

        Object result = bpp.postProcessAfterInitialization(plainBean, "plainBean");

        assertThat(result).isSameAs(plainBean);
        verifyNoInteractions(registry);
    }

    @Test
    void beanWithoutAnyHandlerMethodThrows() {
        Object bean = new EmptyHandler();

        assertThatThrownBy(() -> bpp.postProcessAfterInitialization(bean, "emptyHandler"))
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("at least one handler method");
    }

    @Test
    void validBeanIsRegisteredWithStreamRegistry() {
        Object bean = new ValidHandler();

        bpp.postProcessAfterInitialization(bean, "validHandler");

        ArgumentCaptor<ChangeStreamDefinition> captor = ArgumentCaptor.forClass(ChangeStreamDefinition.class);
        verify(registry).register(captor.capture());

        ChangeStreamDefinition def = captor.getValue();
        assertThat(def.streamName()).isEqualTo("valid-handler");
        assertThat(def.collection()).isEqualTo("jv_snapshots");
        assertThat(def.database()).isEmpty();
        assertThat(def.bean()).isSameAs(bean);
        assertThat(def.onChangeHandler()).isNull();
        assertThat(def.typedHandlers()).containsOnlyKeys(OperationType.INSERT);

        @SuppressWarnings("unchecked")
        List<String> handlerNames = (List<String>) def.metadata().get("handlers");
        assertThat(handlerNames)
                .containsExactlyInAnyOrder("JAVERS_OnInitial", "JAVERS_OnUpdate", "JAVERS_OnTerminal");
    }

    @Test
    void explicitStreamNameOverridesKebabCase() {
        Object bean = new NamedHandler();

        bpp.postProcessAfterInitialization(bean, "namedHandler");

        ArgumentCaptor<ChangeStreamDefinition> captor = ArgumentCaptor.forClass(ChangeStreamDefinition.class);
        verify(registry).register(captor.capture());
        assertThat(captor.getValue().streamName()).isEqualTo("my-explicit-stream");
    }

    @Test
    void explicitDatabaseIsPropagated() {
        Object bean = new DatabaseScopedHandler();

        bpp.postProcessAfterInitialization(bean, "dbHandler");

        ArgumentCaptor<ChangeStreamDefinition> captor = ArgumentCaptor.forClass(ChangeStreamDefinition.class);
        verify(registry).register(captor.capture());
        assertThat(captor.getValue().database()).isEqualTo("my-db");
    }

    // ---- Snapshot collection resolution priority ----

    @Test
    void explicitSnapshotCollectionWinsOverEverything() {
        environment.setProperty("javers.snapshotCollectionName", "from-property");

        bpp.postProcessAfterInitialization(new ExplicitCollectionHandler(), "explicit");

        ArgumentCaptor<ChangeStreamDefinition> captor = ArgumentCaptor.forClass(ChangeStreamDefinition.class);
        verify(registry).register(captor.capture());
        assertThat(captor.getValue().collection()).isEqualTo("custom_snapshots");
    }

    @Test
    void javersPropertyIsUsedWhenAnnotationIsEmpty() {
        environment.setProperty("javers.snapshotCollectionName", "from-property");

        bpp.postProcessAfterInitialization(new ValidHandler(), "valid");

        ArgumentCaptor<ChangeStreamDefinition> captor = ArgumentCaptor.forClass(ChangeStreamDefinition.class);
        verify(registry).register(captor.capture());
        assertThat(captor.getValue().collection()).isEqualTo("from-property");
    }

    @Test
    void defaultsToJvSnapshotsWhenNoOverride() {
        bpp.postProcessAfterInitialization(new ValidHandler(), "valid");

        ArgumentCaptor<ChangeStreamDefinition> captor = ArgumentCaptor.forClass(ChangeStreamDefinition.class);
        verify(registry).register(captor.capture());
        assertThat(captor.getValue().collection()).isEqualTo("jv_snapshots");
    }

    @Test
    void emptyJaversPropertyFallsBackToDefault() {
        environment.setProperty("javers.snapshotCollectionName", "");

        bpp.postProcessAfterInitialization(new ValidHandler(), "valid");

        ArgumentCaptor<ChangeStreamDefinition> captor = ArgumentCaptor.forClass(ChangeStreamDefinition.class);
        verify(registry).register(captor.capture());
        assertThat(captor.getValue().collection()).isEqualTo("jv_snapshots");
    }

    // ---- Test beans ----

    @JaversStream(entityType = Product.class)
    static class EmptyHandler {
    }

    @JaversStream(entityType = Product.class)
    static class ValidHandler {
        @OnInitial
        void onInit(Product p, JaversChangeContext<Product> ctx) {}

        @OnUpdate
        void onUpd(Product p, JaversChangeContext<Product> ctx) {}

        @OnTerminal
        void onTerm(JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class, name = "my-explicit-stream")
    static class NamedHandler {
        @OnInitial
        void onInit(JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class, database = "my-db")
    static class DatabaseScopedHandler {
        @OnInitial
        void onInit(JaversChangeContext<Product> ctx) {}
    }

    @JaversStream(entityType = Product.class, snapshotCollection = "custom_snapshots")
    static class ExplicitCollectionHandler {
        @OnInitial
        void onInit(JaversChangeContext<Product> ctx) {}
    }
}
