package com.iturtle.integration.jpa;

import com.iturtle.jpa.autoconfigure.CommonJpaAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Security 가 없는 서비스에서도 자동 설정이 실패하지 않고, ~At 은 기록되며 ~By 는 비워 두는지 검증한다.
 */
class WithoutSecurityAuditingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withClassLoader(new FilteredClassLoader("org.springframework.security"))
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    JpaRepositoriesAutoConfiguration.class,
                    CommonJpaAutoConfiguration.class))
            .withUserConfiguration(EntityPackage.class);

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
