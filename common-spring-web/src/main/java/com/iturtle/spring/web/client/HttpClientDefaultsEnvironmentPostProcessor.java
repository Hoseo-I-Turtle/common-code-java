package com.iturtle.spring.web.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Spring Boot 가 만드는 RestClient / RestTemplate 의 기본 타임아웃을 지정한다. 기본값이 없으면 응답 없는 외부 서버에
 * 스레드가 무기한 묶이기 때문이다. 우선순위가 가장 낮으므로 서비스에서 같은 키를 설정하면 그 값이 사용된다.
 * <pre>
 * spring.http.client.connect-timeout: 3s
 * spring.http.client.read-timeout: 10s
 * </pre>
 */
public class HttpClientDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "iturtleHttpClientDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(
                "spring.http.client.connect-timeout", "3s",
                "spring.http.client.read-timeout", "10s"
        )));
    }
}
