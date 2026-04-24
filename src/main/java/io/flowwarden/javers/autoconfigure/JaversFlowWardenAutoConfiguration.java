package io.flowwarden.javers.autoconfigure;

import io.flowwarden.javers.internal.JaversStreamBeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that enables {@code @JaversStream} annotation processing
 * when both FlowWarden and Javers are on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.javers.core.Javers")
public class JaversFlowWardenAutoConfiguration {

    @Bean
    public static JaversStreamBeanPostProcessor javersStreamBeanPostProcessor() {
        return new JaversStreamBeanPostProcessor();
    }
}
