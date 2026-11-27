package com.iturtle.spring.core.stereotype;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class FacadeTest {

    @Test
    void scansFacadesUsingDefaultOrExplicitBeanNames() {
        try (var context = new AnnotationConfigApplicationContext(FacadeTest.class.getPackageName())) {
            assertThat(context.getBean(DefaultFacade.class)).isSameAs(context.getBean("facadeTest.DefaultFacade"));
            assertThat(context.getBean(NamedFacade.class)).isSameAs(context.getBean("exampleFacade"));
        }
    }

    @Facade
    static class DefaultFacade {
    }

    @Facade("exampleFacade")
    static class NamedFacade {
    }
}
