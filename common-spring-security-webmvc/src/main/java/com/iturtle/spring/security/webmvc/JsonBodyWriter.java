package com.iturtle.spring.security.webmvc;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 객체를 JSON 으로 쓴다. Spring Boot 3 는 Jackson 2, Boot 4 는 Jackson 3 을 쓰므로 Jackson 타입을 직접 드러내지 않는다.
 * 자동 설정이 서비스에 있는 Jackson 으로 등록하며, 같은 타입의 빈을 등록하면 대체된다.
 */
@FunctionalInterface
public interface JsonBodyWriter {

    void write(OutputStream out, Object body) throws IOException;
}
