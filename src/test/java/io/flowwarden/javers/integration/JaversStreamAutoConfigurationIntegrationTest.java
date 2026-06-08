package io.flowwarden.javers.integration;

import io.flowwarden.javers.autoconfigure.JaversFlowWardenAutoConfiguration;
import io.flowwarden.javers.internal.JaversStreamBeanPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class JaversStreamAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JaversFlowWardenAutoConfiguration.class));

    @Test
    void registersBeanPostProcessorWhenJaversIsOnClasspath() {
        runner.run(context -> assertThat(context).hasSingleBean(JaversStreamBeanPostProcessor.class));
    }

    @Test
    void doesNotRegisterBeanPostProcessorWhenJaversIsAbsent() {
        runner.withClassLoader(new FilteredClassLoader("org.javers.core"))
                .run(context -> assertThat(context).doesNotHaveBean(JaversStreamBeanPostProcessor.class));
    }
}
