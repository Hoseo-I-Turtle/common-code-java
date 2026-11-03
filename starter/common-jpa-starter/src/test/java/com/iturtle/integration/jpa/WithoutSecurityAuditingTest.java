package com.iturtle.integration.jpa;

import com.iturtle.jpa.autoconfigure.CommonJpaAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Security 가 없는 서비스에서도 자동 설정이 실패하지 않고, ~At 은 기록되며 ~By 는 비워 두는지 검증한다.
 */
class WithoutSecurityAuditingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("org.springframework.security"))
            .withConfiguration(AutoConfigurations.of(
                    // Boot 3 와 4 에서 패키지가 달라 이름으로 찾는다
                    firstPresent("org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"),
                    firstPresent("org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"),
                    firstPresent("org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"),
                    CommonJpaAutoConfiguration.class))
            .withUserConfiguration(EntityPackage.class);

    private static Class<?> firstPresent(String boot3Name, String boot4Name) {
        return ClassUtils.resolveClassName(ClassUtils.isPresent(boot3Name, null) ? boot3Name : boot4Name, null);
    }

    @Test
    void auditingWorksWithoutSecurity() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AuditorAware.class);

            TestOrderRepository repository = context.getBean(TestOrderRepository.class);
            TestOrder order = repository.saveAndFlush(new TestOrder("order"));
            order.delete();
            TestOrder deleted = repository.saveAndFlush(order);

            assertThat(deleted.getCreatedAt()).isNotNull();
            assertThat(deleted.getUpdatedAt()).isNotNull();
            assertThat(deleted.getDeletedAt()).isNotNull();
            assertThat(deleted.getCreatedBy()).isNull();
            assertThat(deleted.getUpdatedBy()).isNull();
            assertThat(deleted.getDeletedBy()).isNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackageClasses = TestOrder.class)
    static class EntityPackage {
    }
}
