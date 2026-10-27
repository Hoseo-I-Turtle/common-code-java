package com.iturtle.infra.autoconfigure;

import com.iturtle.core.exception.InvalidConfigurationException;
import com.iturtle.infra.objectstorage.StorageProperties;
import org.springframework.util.StringUtils;

/**
 * 스토리지 자동 설정의 설정값 검사. 잘못된 설정은 애플리케이션 시작 시 실패시킨다.
 */
final class StoragePropertiesChecks {

    private StoragePropertiesChecks() {
    }

    static String required(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidConfigurationException("iturtle.infra.storage." + name + " 이 필요하다");
        }
        return value;
    }

    /**
     * 클라이언트 빈이 이미 있으면 클라이언트 설정은 그 빈이 정한다. 두 곳에서 설정하지 않도록 막는다.
     */
    static void rejectClientProperties(StorageProperties properties, String clientType) {
        if (StringUtils.hasText(properties.getRegion()) || StringUtils.hasText(properties.getEndpoint())
                || StringUtils.hasText(properties.getAccessKey()) || StringUtils.hasText(properties.getSecretKey())) {
            throw new InvalidConfigurationException(clientType + " 빈이 이미 있으므로 iturtle.infra.storage 의 region / "
                    + "endpoint / access-key / secret-key 는 설정할 수 없다. 클라이언트 설정은 한 곳에서만 한다");
        }
    }
}
