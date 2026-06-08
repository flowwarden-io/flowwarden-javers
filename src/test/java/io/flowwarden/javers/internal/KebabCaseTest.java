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
