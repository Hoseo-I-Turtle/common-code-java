package com.iturtle.spring.web.client;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Spring Boot 가 만드는 RestClient / RestTemplate 의 기본 타임아웃을 지정한다. 기본값이 없으면 응답 없는 외부 서버에
 * 스레드가 무기한 묶이기 때문이다. 우선순위가 가장 낮으므로 서비스에서 같은 키를 설정하면 그 값이 사용된다.
 * <pre>
 * # Boot 3
 * spring.http.client.connect-timeout: 3s
 * spring.http.client.read-timeout: 10s
 * # Boot 4
 * spring.http.clients.connect-timeout: 3s
 * spring.http.clients.read-timeout: 10s
 * </pre>
 * Boot 3 와 4 에서 EnvironmentPostProcessor 의 패키지가 다르므로, 양쪽에 같은 이름으로 있는 이벤트로 구현한다.
 * 두 버전의 키를 모두 넣어도 해당 버전이 쓰지 않는 키는 무시된다.
 */
public class HttpClientDefaultsListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    static final String PROPERTY_SOURCE_NAME = "iturtleHttpClientDefaults";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        applyDefaults(event.getEnvironment());
    }

    static void applyDefaults(ConfigurableEnvironment environment) {
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(
                "spring.http.client.connect-timeout", "3s",
                "spring.http.client.read-timeout", "10s",
                "spring.http.clients.connect-timeout", "3s",
                "spring.http.clients.read-timeout", "10s"
        )));
    }
}
